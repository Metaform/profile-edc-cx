/*
 *  Copyright (c) 2025 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.dataspacetck.cx.system;

import com.nimbusds.jwt.JWTClaimsSet;
import okhttp3.Interceptor;
import org.eclipse.dataspacetck.core.api.system.CallbackEndpoint;
import org.eclipse.dataspacetck.core.spi.boot.Monitor;
import org.eclipse.dataspacetck.core.spi.system.ServiceConfiguration;
import org.eclipse.dataspacetck.core.spi.system.ServiceResolver;
import org.eclipse.dataspacetck.core.spi.system.SystemConfiguration;
import org.eclipse.dataspacetck.core.spi.system.SystemLauncher;
import org.eclipse.dataspacetck.cx.dcp.annotation.BpnNumber;
import org.eclipse.dataspacetck.cx.dcp.annotation.ContractVersion;
import org.eclipse.dataspacetck.cx.dcp.annotation.DcpScope;
import org.eclipse.dataspacetck.cx.dsp.catalog.client.CxDspCatalogClient;
import org.eclipse.dataspacetck.cx.runtime.CxRuntime;
import org.eclipse.dataspacetck.cx.system.assembly.CxDcpAssembly;
import org.eclipse.dataspacetck.cx.system.dsp.catalog.http.CxDspCatalogHttpClient;
import org.eclipse.dataspacetck.cx.system.dsp.catalog.local.CxDspCatalogLocalClient;
import org.eclipse.dataspacetck.cx.token.SelfIssuedTokenProvider;
import org.eclipse.dataspacetck.dcp.system.annotation.IssueCredentials;
import org.eclipse.dataspacetck.dcp.system.assembly.BaseAssembly;
import org.eclipse.dataspacetck.dcp.system.assembly.ServiceAssembly;
import org.eclipse.dataspacetck.dcp.system.crypto.KeyService;
import org.eclipse.dataspacetck.dcp.system.did.DidDocumentHandler;
import org.eclipse.dataspacetck.dsp.system.DspSystemLauncher;
import org.eclipse.dataspacetck.dsp.system.api.client.catalog.CatalogClient;
import org.eclipse.dataspacetck.dsp.system.api.http.HttpFunctions;
import org.eclipse.dataspacetck.dsp.system.client.catalog.http.HttpCatalogClient;
import org.jspecify.annotations.NonNull;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.time.Instant.now;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static org.eclipse.dataspacetck.core.api.system.SystemsConstants.TCK_PREFIX;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.CX_TCK_PREFIX;
import static org.eclipse.dataspacetck.dcp.system.message.DcpConstants.TOKEN;
import static org.eclipse.dataspacetck.dsp.system.api.message.MessageSerializer.registerDocument;

/**
 * Bootstraps a Catena-X test fixture that combines the DSP exchange protocol with the DCP identity mechanism.
 * <p>
 * Service dependency injection for the DSP protocol (the {@code CatalogClient}, {@code Connector}, negotiation and
 * transfer pipelines) is delegated to the {@link DspSystemLauncher}. The DCP identity layer is provided by reusing the
 * dcp-tck {@link BaseAssembly}/{@link ServiceAssembly} fixtures: the holder DID document, the CredentialService
 * (presentation-query endpoint) and the Secure Token Server are hosted on the shared callback endpoint so that the
 * connector under test can resolve the requester's identity and perform the DCP presentation-query callback.
 * <p>
 * The two protocols are wired together at the single seam exposed by dsp-tck: {@link HttpFunctions the DSP
 * authorization interceptor}. Instead of a static bearer token read from configuration, cx-tck attaches a
 * <em>DCP self-issued token</em> (an ID token signed by the holder key, carrying an STS-issued access token in the
 * {@code token} claim) to every DSP request. This is the mechanism a real Catena-X connector uses to authorize a
 * catalog request.
 */
public class CxSystemLauncher implements SystemLauncher {

    private static final String LOCAL_CONNECTOR_CONFIG = TCK_PREFIX + ".dsp.local.connector";
    private static final String PROVIDER_DID_CONFIG = CX_TCK_PREFIX + ".provider.did";
    private static final long TOKEN_VALIDITY_SECONDS = 600;

    static {
        registerDocument(URI.create("https://w3id.org/catenax/2025/9/policy/context.jsonld"), "cx-policy.jsonld");
        registerDocument(URI.create("https://w3id.org/catenax/2025/9/policy/odrl.jsonld"), "cx-odrl-profile.jsonld");
        registerDocument(URI.create("https://w3id.org/edc/dspace/v0.0.1"), "edc-dspace-profile.jsonld");
    }

    private final DspSystemLauncher dspLauncher = new DspSystemLauncher();
    private final Map<String, CxDcpAssembly> serviceAssemblies = new ConcurrentHashMap<>();
    private final Set<String> authorizedScopes = ConcurrentHashMap.newKeySet();
    private Monitor monitor;
    private BaseAssembly baseAssembly;
    private boolean localConnector;
    private String providerDid;
    private String holderIdentifier;


    /**
     * Derives the HTTP path at which a {@code did:web} document is served, mirroring the resolution logic a did:web
     * resolver applies: a DID with no path segment maps to {@code /.well-known/did.json}, otherwise the (percent-decoded)
     * path segments are joined and suffixed with {@code /did.json}. E.g. {@code did:web:host%3A8083:cx-tck} maps to
     * {@code /cx-tck/did.json}.
     */
    private static String didDocumentPath(String did) {
        var ssp = did.substring("did:web:".length());          // host%3Aport[:seg...]
        var segments = ssp.split(":");
        if (segments.length == 1) {
            return "/.well-known/did.json";
        }
        var path = String.join("/", Arrays.copyOfRange(segments, 1, segments.length));
        return "/" + URLDecoder.decode(path, StandardCharsets.UTF_8) + "/did.json";
    }


    @Override
    public void beforeExecution(ServiceConfiguration configuration, ServiceResolver resolver) {
        // ensure the DCP identity is hosted and a self-issued token is attached before any DSP client is handed to the test
        if (!localConnector) {
            if (hasAnnotation(IssueCredentials.class, configuration)) {
                var bpn = getAnnotation(BpnNumber.class, configuration).map(BpnNumber::value).orElse(holderIdentifier);
                var contractVersion = getAnnotation(ContractVersion.class, configuration).map(ContractVersion::value).orElse(null);
                var credentialTypes = getAnnotation(IssueCredentials.class, configuration).map(IssueCredentials::value).orElse(new String[0]);
                var assembly = getOrInitServiceAssembly(configuration.getScopeId(), configuration, resolver);
                assembly.issueCxCredentials(baseAssembly, Arrays.stream(credentialTypes).toList(), bpn, contractVersion);
            }

            // Contract-negotiation and transfer-process messages are dispatched by the dsp-tck pipelines through the
            // static HttpFunctions authorization interceptor (unlike the catalog client, which carries its own
            // per-client interceptor). A test that drives those flows declares a method-level @DcpScope; register a
            // freshly minted DCP self-issued token as the global DSP authorization header so every negotiation/transfer
            // request is authorized with the Catena-X identity.
            getAnnotation(DcpScope.class, configuration).ifPresent(scope -> {
                ensureIdentity(configuration, resolver);
                HttpFunctions.registerAuthorizationInterceptor(() -> {
                    var idToken = fetchSelfSignedToken(configuration.getScopeId(), configuration, resolver, Arrays.asList(scope.value()));
                    return "Bearer " + idToken;
                });
            });
        }
    }

    @Override
    public void start(SystemConfiguration configuration) {
        monitor = configuration.getMonitor();
        dspLauncher.start(configuration);

        localConnector = configuration.getPropertyAsBoolean(LOCAL_CONNECTOR_CONFIG, false);
        CxRuntime.setLocalConnector(localConnector);
        providerDid = configuration.getPropertyAsString(PROVIDER_DID_CONFIG, null);
        holderIdentifier = configuration.getPropertyAsString(CX_TCK_PREFIX + ".bpn", "cx-tck-holder");

        if (!localConnector) {
            // build the immutable DCP identity fixtures (keys, DIDs, token services)
            baseAssembly = new BaseAssembly(configuration);
        }
    }

    @Override
    public <T> boolean providesService(Class<T> type) {
        return localProvides(type) || dspLauncher.providesService(type);
    }

    private <T> boolean localProvides(Class<T> type) {
        return type.isAssignableFrom(CxDspCatalogClient.class) ||
                type.isAssignableFrom(SelfIssuedTokenProvider.class);
    }

    @Override
    public <T> T getService(Class<T> type, ServiceConfiguration configuration, ServiceResolver resolver) {
        // when talking to a real connector, ensure the DCP identity is hosted and a self-issued token is attached
        // before any DSP client is handed to the test
        if (!localConnector) {
            ensureIdentity(configuration, resolver);
        }
        var localService = localGetService(type, configuration, resolver);
        if (localService != null) {
            return localService;
        }
        return dspLauncher.getService(type, configuration, resolver);
    }

    private <T> T localGetService(Class<T> type, ServiceConfiguration configuration, ServiceResolver resolver) {
        if (type.isAssignableFrom(CxDspCatalogClient.class)) {
            return type.cast(createCatalogClient(configuration.getScopeId(), configuration, resolver));
        }
        if (type.isAssignableFrom(SelfIssuedTokenProvider.class)) {
            // Bind a token provider to this scope so a test can mint the same DCP self-issued token used to authorize
            // the DSP exchange (e.g. to authorize an OAuth2 token-renewal request). Minting is lazy: the delegate only
            // runs when mintToken is invoked, so injecting this in local self-test mode (where baseAssembly is null)
            // does not fail unless the token is actually requested.
            var scopeId = configuration.getScopeId();
            SelfIssuedTokenProvider provider = (scopes, embeddedToken) -> fetchSelfSignedToken(scopeId, configuration, resolver, scopes, embeddedToken);
            return type.cast(provider);
        }
        return null;
    }

    @Override
    public void close() {
        dspLauncher.close();
    }


    /**
     * Materializes the per-scope DCP fixtures (registering the DID/CredentialService/STS handlers on the callback
     * endpoint) and registers a freshly minted holder self-issued token as the DSP authorization header.
     */
    private void ensureIdentity(ServiceConfiguration configuration, ServiceResolver resolver) {
        var scopeId = configuration.getScopeId();

        if (authorizedScopes.add(scopeId)) {
            // ServiceAssembly serves the holder DID document only at the fixed "/holder/did.json" path; mount a handler
            // at the path the *configured* holder DID actually resolves to so the connector under test can fetch the
            // verification key (e.g. a did:web ending in ":cx-tck" is resolved as GET /cx-tck/did.json).
            var endpoint = (CallbackEndpoint) requireNonNull(resolver.resolve(CallbackEndpoint.class, configuration));
            endpoint.registerHandler(didDocumentPath(baseAssembly.getHolderDid()),
                    new DidDocumentHandler(baseAssembly.getHolderDidService(), baseAssembly.getMapper()));
        }
    }

    private String fetchSelfSignedToken(String scopeId, ServiceConfiguration configuration, ServiceResolver resolver, List<String> scopes, String embeddedToken) {
        var assembly = getOrInitServiceAssembly(scopeId, configuration, resolver);

        var audience = providerDid != null ? providerDid : baseAssembly.getVerifierDid();


        if (embeddedToken == null) {
            // obtain an access token from the holder's Secure Token Server, scoped to the membership credential;
            // the connector under test presents this back to the hosted CredentialService during verification
            var accessTokenResult = assembly.getStsClient().obtainReadToken(audience, scopes);
            if (accessTokenResult.failed()) {
                throw new AssertionError("Unable to obtain DCP access token: " + accessTokenResult.getFailure());
            }
            embeddedToken = accessTokenResult.getContent();
        }

        return createSelfIssuedToken(baseAssembly.getHolderDid(), audience, embeddedToken, baseAssembly.getHolderKeyService());
    }

    private String fetchSelfSignedToken(String scopeId, ServiceConfiguration configuration, ServiceResolver resolver, List<String> scopes) {
        return fetchSelfSignedToken(scopeId, configuration, resolver, scopes, null);
    }

    private @NonNull CxDcpAssembly getOrInitServiceAssembly(String scopeId, ServiceConfiguration configuration, ServiceResolver resolver) {
        return serviceAssemblies.computeIfAbsent(scopeId,
                id -> new CxDcpAssembly(baseAssembly, resolver, configuration));
    }

    private CxDspCatalogClient createCatalogClient(String scopeId, ServiceConfiguration configuration, ServiceResolver resolver) {
        if (localConnector) {
            var localClient = dspLauncher.getService(CatalogClient.class, configuration, resolver);
            return new CxDspCatalogLocalClient(localClient);
        }

        Interceptor interceptor = null;
        if (hasAnnotation(DcpScope.class, configuration)) {
            interceptor = chain -> {
                var request = chain.request();
                var scopes = Arrays.asList(getAnnotation(DcpScope.class, configuration).orElseThrow().value());
                var idToken = fetchSelfSignedToken(scopeId, configuration, resolver, scopes);
                var authenticatedRequest = request.newBuilder()
                        .header("Authorization", "Bearer " + idToken).build();
                return chain.proceed(authenticatedRequest);
            };
        }
        var remoteClient = new HttpCatalogClient(dspLauncher.getConnectorProtocolUrl(), monitor, interceptor);
        return new CxDspCatalogHttpClient(remoteClient);
    }

    /**
     * Mints a DCP self-issued (ID) token following the dcp-tck verifier pattern: signed by the bearer's own key with
     * {@code iss == sub == holderDid}, the connector as {@code aud}, and the STS access token carried in the
     * {@code token} claim.
     */
    private String createSelfIssuedToken(String holderDid, String audience, String accessToken, KeyService keyService) {
        var claims = new JWTClaimsSet.Builder()
                .issuer(holderDid)
                .subject(holderDid)
                .audience(audience)
                .issueTime(new Date())
                .expirationTime(Date.from(now().plusSeconds(TOKEN_VALIDITY_SECONDS)))
                .claim(TOKEN, accessToken)
                .build();
        return keyService.sign(emptyMap(), claims);
    }

    private boolean hasAnnotation(Class<? extends Annotation> annotation, ServiceConfiguration configuration) {
        return configuration.getAnnotations().stream().anyMatch(a -> a.annotationType().equals(annotation));
    }

    @SuppressWarnings({"unchecked", "SameParameterValue"})
    private <A extends Annotation> Optional<A> getAnnotation(Class<A> annotation, ServiceConfiguration configuration) {
        return (Optional<A>) configuration.getAnnotations().stream()
                .filter(a -> a.annotationType().equals(annotation))
                .findFirst();
    }

}
