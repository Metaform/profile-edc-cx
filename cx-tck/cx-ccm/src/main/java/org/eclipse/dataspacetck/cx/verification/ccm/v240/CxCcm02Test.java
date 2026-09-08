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

package org.eclipse.dataspacetck.cx.verification.ccm.v240;

import org.eclipse.dataspacetck.api.system.MandatoryTest;
import org.eclipse.dataspacetck.api.system.TestSequenceDiagram;
import org.eclipse.dataspacetck.core.api.system.ConfigParam;
import org.eclipse.dataspacetck.cx.ccm.v240.CcmMessages;
import org.eclipse.dataspacetck.cx.ccm.v240.fixture.CertificateSets;
import org.eclipse.dataspacetck.cx.ccm.v240.model.BusinessPartnerCertificate31;
import org.eclipse.dataspacetck.cx.ccm.v240.model.Ccm240Paths;
import org.eclipse.dataspacetck.cx.ccm.v240.model.CcmCertificatePush;
import org.eclipse.dataspacetck.cx.dcp.annotation.DcpScope;
import org.eclipse.dataspacetck.cx.dsp.catalog.client.CxDspCatalogClient;
import org.eclipse.dataspacetck.cx.usecase.DataPlaneRequests;
import org.eclipse.dataspacetck.cx.usecase.DataPlaneResponse;
import org.eclipse.dataspacetck.cx.usecase.Edr;
import org.eclipse.dataspacetck.cx.usecase.schema.JsonSchemas;
import org.eclipse.dataspacetck.dcp.system.annotation.IssueCredentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.BPN_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.BPN_SCOPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.GOV_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.GOV_SCOPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.MEMBERSHIP_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.MEMBERSHIP_SCOPE;

/**
 * CX_CCM_02: the CX-0135 v2.4.0 embedded push, {@code POST /companycertificate/push}.
 * <p>
 * The TCK acts as the Certificate Provider: it discovers the notification API asset the system under test
 * publishes, negotiates a contract for it, starts a transfer, and delivers a full
 * {@code BusinessPartnerCertificate} 3.1.0 inline through the resulting EDR. This is the provider half of
 * the mandatory push test cases; the acceptance report that follows travels the other way, from consumer
 * to provider, and is out of scope here.
 * <p>
 * Each test differs only in the certificate it carries, which is the point: the four payloads span the
 * dimensions the CX-0135 test cases distinguish, so together they show the receiver accepts the whole
 * 3.1.0 shape rather than just the easy case.
 */
@Tag("base-compliance")
@DisplayName("CX_CCM_02: CX-0135 v2.4.0 embedded certificate push")
public class CxCcm02Test extends AbstractCcm240Test {

    private static final String PUSH_SCHEMA = "/ccm/v240/schema/certificate-push-schema.json";

    @ConfigParam
    protected String format = "HttpData-PULL";

    @MandatoryTest
    @DisplayName("CX_CCM:02-01: an ISO 9001 certificate is pushed and accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (certificate provider)
            participant CUT as Connector Under Test (certificate consumer)

            TCK->>CUT: CatalogRequestMessage (Authorization: DCP self-issued token)
            CUT-->>TCK: Catalog (CCM notification API asset)

            TCK->>CUT: ContractRequestMessage (offer from catalog)
            CUT->>TCK: ContractAgreementMessage
            TCK->>CUT: ContractAgreementVerificationMessage
            CUT->>TCK: ContractNegotiationEventMessage:finalized

            TCK->>CUT: TransferRequestMessage
            CUT->>TCK: TransferStartMessage (dataAddress: endpoint + token)

            TCK->>CUT: POST /companycertificate/push (BusinessPartnerCertificate 3.1.0 inline)
            CUT-->>TCK: 200
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    public void cx_ccm_02_01(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        pushAndExpectAccepted(catalogClient, CertificateSets.set1Iso9001(), "Set 1 (ISO 9001)");
    }

    @MandatoryTest
    @DisplayName("CX_CCM:02-02: a certificate whose sites mix BPNS and BPNA is pushed and accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (certificate provider)
            participant CUT as Connector Under Test (certificate consumer)

            TCK->>CUT: catalog -> negotiation -> transfer
            CUT->>TCK: TransferStartMessage (dataAddress)
            TCK->>CUT: POST /companycertificate/push (enclosedSites: 1 BPNS + 2 BPNA)
            CUT-->>TCK: 200
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    public void cx_ccm_02_02(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        // 3.1.0 widened enclosedSites to accept BPNA alongside BPNS, breaking compatibility with 3.0.0;
        // a receiver that only handles BPNS passes 02-01 and fails here
        pushAndExpectAccepted(catalogClient, CertificateSets.set3MixedSites(), "Set 3 (mixed BPNS and BPNA sites)");
    }

    @MandatoryTest
    @DisplayName("CX_CCM:02-03: an expired certificate is pushed and accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (certificate provider)
            participant CUT as Connector Under Test (certificate consumer)

            TCK->>CUT: catalog -> negotiation -> transfer
            CUT->>TCK: TransferStartMessage (dataAddress)
            TCK->>CUT: POST /companycertificate/push (validUntil in the past)
            CUT-->>TCK: 200
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    public void cx_ccm_02_03(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        // expiry is a property of the certificate, not a transport condition: v2.4.0 gives the receiver no
        // way to decline delivery over it, so accepting the message is the conformant behaviour
        pushAndExpectAccepted(catalogClient, CertificateSets.set4Expired(), "Set 4 (expired)");
    }

    @MandatoryTest
    @DisplayName("CX_CCM:02-04: a certificate carrying the 9999-12-31 never-expires sentinel is pushed and accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (certificate provider)
            participant CUT as Connector Under Test (certificate consumer)

            TCK->>CUT: catalog -> negotiation -> transfer
            CUT->>TCK: TransferStartMessage (dataAddress)
            TCK->>CUT: POST /companycertificate/push (validUntil 9999-12-31)
            CUT-->>TCK: 200
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    public void cx_ccm_02_04(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        // a date parser that rejects year 9999 fails here and nowhere else
        pushAndExpectAccepted(catalogClient, CertificateSets.set5NoExpiry(), "Set 5 (no expiry)");
    }

    /**
     * Establishes an EDR against the system under test's notification API asset and delivers a certificate
     * through it, asserting the message was accepted.
     */
    private void pushAndExpectAccepted(CxDspCatalogClient catalogClient,
                                       BusinessPartnerCertificate31 certificate,
                                       String description) {
        var edr = establishNotificationApiEdr(catalogClient);
        var push = CcmMessages.push(senderBpn(), receiverBpn(), certificate);

        // check what is about to be sent, so a defect in the suite's own payload is reported as such
        // rather than as a conformance failure of the system under test
        assertThat(JsonSchemas.violationsOf(PUSH_SCHEMA, push))
                .as("the push the TCK is about to send carrying %s is not conformant", description)
                .isEmpty();

        var response = deliver(edr, push);

        assertThat(response.successful())
                .as("pushing %s must be accepted, but the receiver answered %s", description, response.describe())
                .isTrue();
    }

    /**
     * Discovers the notification API asset and drives catalog, negotiation and transfer to obtain an EDR
     * for it.
     */
    private Edr establishNotificationApiEdr(CxDspCatalogClient catalogClient) {
        var catalog = fetchCatalog(catalogClient);
        var asset = findNotificationApiAsset(catalog);
        var datasetId = String.valueOf(asset.get("@id"));
        return establishEdr(catalog, datasetId, format);
    }

    /**
     * Delivers a push through the EDR.
     * <p>
     * A 404 or a 405 here almost always means the data address behind the asset does not proxy the method,
     * path and body, so the POST never reached the notification API; the failure message says so, because
     * that is a deployment mistake rather than a CX-0135 conformance defect.
     */
    private DataPlaneResponse deliver(Edr edr, CcmCertificatePush push) {
        var response = DataPlaneRequests.postJson(edr, Ccm240Paths.PUSH, push);
        if (response.code() == 404 || response.code() == 405) {
            throw new AssertionError(("The push was answered %s. The CCM asset's data address must set " +
                    "proxyPath, proxyMethod and proxyBody so a POST to %s reaches the notification API; " +
                    "with the defaults the data plane rewrites it as a GET of the base URL.")
                    .formatted(response.describe(), Ccm240Paths.PUSH));
        }
        return response;
    }
}
