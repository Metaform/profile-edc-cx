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

package org.eclipse.dataspacetck.cx.system.assembly;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jwt.JWTClaimsSet;
import org.eclipse.dataspacetck.core.spi.system.ServiceConfiguration;
import org.eclipse.dataspacetck.core.spi.system.ServiceResolver;
import org.eclipse.dataspacetck.dcp.system.assembly.BaseAssembly;
import org.eclipse.dataspacetck.dcp.system.assembly.ServiceAssembly;
import org.eclipse.dataspacetck.dcp.system.generation.JwtCredentialGenerator;
import org.eclipse.dataspacetck.dcp.system.model.vc.VcContainer;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.time.Instant.now;
import static java.util.UUID.randomUUID;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.BPN_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.GOV_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.MEMBERSHIP_CREDENTIAL_TYPE;

public class CxDcpAssembly extends ServiceAssembly {


    private static final List<String> ADDITIONAL_CONTEXT = List.of(
            "https://w3id.org/catenax/credentials/v1.0.0"
    );

    public CxDcpAssembly(BaseAssembly baseAssembly, ServiceResolver resolver, ServiceConfiguration configuration) {
        super(baseAssembly, resolver, configuration);
    }

    public void issueCxCredentials(BaseAssembly baseAssembly, List<String> types, String holderIdentifier, String contractVersion) {
        var issuerDid = baseAssembly.getIssuerDid();
        var credentialGenerator = new JwtCredentialGenerator(issuerDid, baseAssembly.getIssuerKeyService());

        var holderDid = baseAssembly.getHolderDid();


        Map<String, Object> membershipClaims = Map.of(
                "id", holderDid,
                "holderIdentifier", holderIdentifier, "memberOf", "Catena-X");

        var membershipContainer = createVcContainer(issuerDid, holderDid, credentialGenerator, ADDITIONAL_CONTEXT, MEMBERSHIP_CREDENTIAL_TYPE, membershipClaims);

        Map<String, Object> bpn = Map.of(
                "id", holderDid,
                "bpn", holderIdentifier);
        var bpnContainer = createVcContainer(issuerDid, holderDid, credentialGenerator, ADDITIONAL_CONTEXT, BPN_CREDENTIAL_TYPE, bpn);

        Map<String, Object> gov = Map.of(
                "id", holderDid,
                "holderIdentifier", holderIdentifier,
                "contractVersion", Optional.ofNullable(contractVersion).orElse("1.0")
        );
        var govContainer = createVcContainer(issuerDid, holderDid, credentialGenerator, ADDITIONAL_CONTEXT, GOV_CREDENTIAL_TYPE, gov);

        var correlation = baseAssembly.getHolderPid();

        var claimSet = new JWTClaimsSet.Builder()
                .issuer(issuerDid)
                .audience(holderDid)
                .subject(issuerDid)
                .jwtID(randomUUID().toString())
                .issueTime(new Date())
                .expirationTime(Date.from(now().plusSeconds(600)))
                .build();

        var token = baseAssembly.getIssuerKeyService().sign(Collections.emptyMap(), claimSet);

        List<VcContainer> containers = Stream.of(membershipContainer, bpnContainer, govContainer)
                .filter(container -> types.contains(container.credentialType()))
                .toList();

        try {
            sendCredentialMessage(baseAssembly, correlation, token, containers.toArray(new VcContainer[0]));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
