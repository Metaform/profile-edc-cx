/*
 *  Copyright (c) 2026 Metaform Systems, Inc.
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

package org.eclipse.dataspacetck.cx.verification.flow.renewal;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.dataspacetck.api.system.MandatoryTest;
import org.eclipse.dataspacetck.api.system.TestSequenceDiagram;
import org.eclipse.dataspacetck.cx.dcp.annotation.DcpScope;
import org.eclipse.dataspacetck.cx.dsp.catalog.client.CxDspCatalogClient;
import org.eclipse.dataspacetck.cx.runtime.CxRuntime;
import org.eclipse.dataspacetck.cx.token.SelfIssuedTokenProvider;
import org.eclipse.dataspacetck.cx.verification.flow.AbstractCxFlowTest;
import org.eclipse.dataspacetck.dcp.system.annotation.IssueCredentials;
import org.eclipse.dataspacetck.dsp.verification.cn.ProviderActions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.BPN_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.BPN_SCOPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.GOV_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.GOV_SCOPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.MEMBERSHIP_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.MEMBERSHIP_SCOPE;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractAccessToken;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractAgreementId;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractDataAddress;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractOfferId;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractRefreshEndpoint;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractRefreshToken;
import static org.eclipse.dataspacetck.dsp.system.api.message.DcatConstants.DCAT_PROPERTY_DATASET_EXPANDED;
import static org.eclipse.dataspacetck.dsp.system.api.message.catalog.CatalogFunctions.createCatalogRequest;
import static org.eclipse.dataspacetck.dsp.system.api.statemachine.ContractNegotiation.State.AGREED;
import static org.eclipse.dataspacetck.dsp.system.api.statemachine.ContractNegotiation.State.FINALIZED;
import static org.eclipse.dataspacetck.dsp.system.api.statemachine.TransferProcess.State.STARTED;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * CX_RENEWAL_FLOW_01: Catena-X token-renewal flow tests. Each test drives the base end-to-end flow (catalog -> contract
 * negotiation -> transfer process) exactly like {@code CxFlow01Test.cx_flow_01_01}, then exercises the Token Renewal
 * profile: it reads the {@code refreshEndpoint}/{@code refreshToken} carried in the transfer-start data address and
 * performs an OAuth2 refresh-token grant (<a href="https://www.rfc-editor.org/rfc/rfc6749#section-6">RFC 6749 section 6</a>)
 * against the connector under test. The renewal request is authorized with a freshly minted DCP self-issued token (the
 * same identity mechanism that authorizes the DSP exchange), obtained through the injected {@link SelfIssuedTokenProvider}.
 * <p>
 * These tests only run against a real connector under test: the in-memory self-test has no DCP identity and its
 * simulated data address carries no renewal properties, so both methods abort in local mode.
 */
@Tag("cx-flow")
@Tag("cx-renewal-flow")
@Tag("base-compliance")
@DisplayName("CX_RENEWAL_FLOW_01: catalog -> negotiation -> transfer -> OAuth2 token renewal")
public class CxRenewalFlow01Test extends AbstractCxFlowTest {

    private static final List<String> RENEWAL_SCOPES = List.of(MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE);
    private static final MediaType FORM = MediaType.get("application/x-www-form-urlencoded");
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @MandatoryTest
    @DisplayName("CX_RENEWAL_FLOW:01-01: transfer started, then a refresh_token grant yields a new access token")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer)
            participant CUT as Connector Under Test (provider)
            
            TCK->>CUT: CatalogRequestMessage (Authorization: DCP self-issued token)
            CUT-->>TCK: Catalog (dataset + offer)
            
            TCK->>CUT: ContractRequestMessage (offer from catalog)
            CUT-->>TCK: ContractNegotiation
            CUT->>TCK: ContractAgreementMessage
            TCK->>CUT: ContractAgreementVerificationMessage
            CUT->>TCK: ContractNegotiationEventMessage:finalized
            
            TCK->>CUT: TransferRequestMessage (agreement from negotiation)
            CUT-->>TCK: TransferProcess
            CUT->>TCK: TransferStartMessage (dataAddress with refreshEndpoint + refreshToken)
            
            TCK->>CUT: POST refreshEndpoint grant_type=refresh_token (Authorization: DCP self-issued token)
            CUT-->>TCK: 200 { access_token, token_type }
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    public void cx_renewal_flow_01_01(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient,
                                      SelfIssuedTokenProvider tokenProvider) throws IOException {
        // token renewal requires real EDR renewal properties and a real DCP identity; both are absent in local self-test
        assumeFalse(CxRuntime.isLocalConnector(), "token renewal requires a real connector under test");

        var dataAddress = runFlowToStartedDataAddress(catalogClient);

        var refreshEndpoint = extractRefreshEndpoint(dataAddress);
        var refreshToken = extractRefreshToken(dataAddress);
        var accessToken = extractAccessToken(dataAddress);
        assertThat(refreshEndpoint).as("data address refreshEndpoint").isNotBlank();
        assertThat(refreshToken).as("data address refreshToken").isNotBlank();

        var authToken = tokenProvider.mintToken(RENEWAL_SCOPES, accessToken);
        var response = requestRenewal(refreshEndpoint, refreshToken, authToken);

        assertThat(response.successful())
                .as("token renewal HTTP %s: %s", response.code(), response.body()).isTrue();
        var token = MAPPER.readValue(response.body(), Map.class);
        assertThat(token.get("access_token")).as("renewed access_token").isNotNull();
        assertThat(token.get("access_token").toString()).as("renewed access_token").isNotBlank();
        assertThat(token.get("token_type")).as("token_type").isNotNull();
    }

    @MandatoryTest
    @DisplayName("CX_RENEWAL_FLOW:01-02: a refresh_token grant with an invalid refresh token is rejected")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer)
            participant CUT as Connector Under Test (provider)
            
            TCK->>CUT: CatalogRequestMessage (Authorization: DCP self-issued token)
            CUT-->>TCK: Catalog (dataset + offer)
            
            TCK->>CUT: ContractRequestMessage -> ... -> ContractNegotiationEventMessage:finalized
            
            TCK->>CUT: TransferRequestMessage (agreement from negotiation)
            CUT->>TCK: TransferStartMessage (dataAddress with refreshEndpoint + refreshToken)
            
            TCK->>CUT: POST refreshEndpoint grant_type=refresh_token (invalid refresh_token)
            CUT-->>TCK: 4xx (invalid_grant)
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    public void cx_renewal_flow_01_02(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient,
                                      SelfIssuedTokenProvider tokenProvider) throws IOException {
        assumeFalse(CxRuntime.isLocalConnector(), "token renewal requires a real connector under test");

        var dataAddress = runFlowToStartedDataAddress(catalogClient);

        var refreshEndpoint = extractRefreshEndpoint(dataAddress);
        var accessToken = extractAccessToken(dataAddress);

        assertThat(refreshEndpoint).as("data address refreshEndpoint").isNotBlank();

        // present a syntactically valid but unknown refresh token; the authorization server must reject the grant
        var invalidRefreshToken = randomUUID().toString();
        var authToken = tokenProvider.mintToken(RENEWAL_SCOPES, accessToken);
        var response = requestRenewal(refreshEndpoint, invalidRefreshToken, authToken);

        assertThat(response.successful())
                .as("token renewal with an invalid refresh token must be rejected, but got HTTP %s: %s",
                        response.code(), response.body())
                .isFalse();
    }

    /**
     * Runs the base flow (catalog -> contract negotiation FINALIZED -> transfer process STARTED) as
     * {@code cx_flow_01_01} does, returning the data address carried in the provider's {@code TransferStartMessage}.
     */
    private Map<String, Object> runFlowToStartedDataAddress(CxDspCatalogClient catalogClient) {
        // seed the in-memory catalog (used when running in local self-test mode)
        providerConnector.getCatalogManager().addDataset(seedDataset(datasetId));

        // 1) fetch the catalog and extract the real offer published for the dataset
        var catalog = catalogClient.getCatalog(createCatalogRequest());
        assertThat(catalog.get(DCAT_PROPERTY_DATASET_EXPANDED)).isNotNull();
        var offerId = extractOfferId(catalog, datasetId);

        // 2) negotiate the extracted offer through to FINALIZED, capturing the provider-issued agreement id
        var agreementIdRef = new AtomicReference<String>();
        negotiationMock.recordContractRequestedAction(ProviderActions::postAgreed);
        negotiationMock.recordVerifiedAction(ProviderActions::postFinalized);

        negotiationPipeline
                .sendRequestMessage(datasetId, offerId)
                .expectAgreementMessage(agreement -> {
                    agreementIdRef.set(extractAgreementId(agreement));
                    consumerConnector.getConsumerNegotiationManager().handleAgreement(agreement);
                })
                .thenWaitForState(AGREED)
                .expectFinalizedEvent(event -> consumerConnector.getConsumerNegotiationManager().handleFinalized(event))
                .sendVerifiedEvent()
                .thenWaitForState(FINALIZED)
                .execute();
        negotiationMock.verify();

        // 3) run a transfer against the negotiated agreement, capturing the data address from the start message
        var agreementId = requireNonNullElseGet(agreementIdRef.get(), () -> randomUUID().toString());
        var dataAddressRef = new AtomicReference<Map<String, Object>>();
        transferProcessMock.recordTransferRequestedAction(AbstractCxFlowTest::postStartWithDataAddress);

        transferProcessPipeline
                .expectStartMessage(start -> {
                    dataAddressRef.set(extractDataAddress(start));
                    return consumerConnector.getConsumerTransferProcessManager().handleStart(start);
                })
                .sendTransferRequest(agreementId, format)
                .thenWaitForState(STARTED)
                .execute();
        transferProcessMock.verify();

        var dataAddress = dataAddressRef.get();
        assertThat(dataAddress).as("transfer start data address").isNotNull();
        return dataAddress;
    }

    /**
     * Performs an OAuth2 refresh-token grant (RFC 6749 section 6): a form-encoded POST to the refresh endpoint carrying
     * {@code grant_type=refresh_token} and the refresh token, authorized with a DCP self-issued bearer token.
     */
    private RenewalResponse requestRenewal(String refreshEndpoint, String refreshToken, String authToken) throws IOException {
        var form = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refreshToken, UTF_8);
        var request = new Request.Builder()
                .url(refreshEndpoint)
                .header("Authorization", "Bearer " + authToken)
                .post(RequestBody.create(form, FORM))
                .build();
        try (var response = HTTP_CLIENT.newCall(request).execute()) {
            var body = response.body() != null ? response.body().string() : "";
            return new RenewalResponse(response.code(), response.isSuccessful(), body);
        }
    }

    private record RenewalResponse(int code, boolean successful, String body) {
    }
}
