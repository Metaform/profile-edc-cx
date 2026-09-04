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

import org.eclipse.dataspacetck.api.system.TestSequenceDiagram;
import org.eclipse.dataspacetck.core.api.system.ConfigParam;
import org.eclipse.dataspacetck.cx.ccm.v240.CcmMessages;
import org.eclipse.dataspacetck.cx.ccm.v240.fixture.CertificateSets;
import org.eclipse.dataspacetck.cx.ccm.v240.model.Ccm240Contexts;
import org.eclipse.dataspacetck.cx.ccm.v240.model.Ccm240Paths;
import org.eclipse.dataspacetck.cx.ccm.v240.model.CcmCertificatePush;
import org.eclipse.dataspacetck.cx.ccm.v240.model.CcmHeader;
import org.eclipse.dataspacetck.cx.dcp.annotation.DcpScope;
import org.eclipse.dataspacetck.cx.dsp.catalog.client.CxDspCatalogClient;
import org.eclipse.dataspacetck.cx.usecase.DataPlaneRequests;
import org.eclipse.dataspacetck.cx.usecase.Edr;
import org.eclipse.dataspacetck.dcp.system.annotation.IssueCredentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.BPN_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.BPN_SCOPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.GOV_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.GOV_SCOPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.MEMBERSHIP_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.MEMBERSHIP_SCOPE;

/**
 * CX_CCM_03: how a Certificate Consumer answers a malformed {@code /companycertificate/push}.
 * <p>
 * <strong>Not mandatory, deliberately.</strong> CX-0135 v2.4.0 documents only 200 and 500 for the push
 * endpoint - 400 appears solely on {@code /companycertificate/request} - so the standard does not
 * actually oblige a receiver to answer a malformed push with any particular status. These tests therefore
 * assert only that a message violating the specification's own schema is <em>not</em> acknowledged as
 * accepted, and report the status that came back. A conformance suite should not invent requirements the
 * standard does not state; the gap is worth reporting to the standardisation body rather than failing an
 * implementation over.
 */
@Tag("cx-ccm-negative")
@DisplayName("CX_CCM_03: CX-0135 v2.4.0 push envelope error handling")
public class CxCcm03Test extends AbstractCcm240Test {

    @ConfigParam
    protected String format = "HttpData-PULL";

    @Test
    @DisplayName("CX_CCM:03-01: a push whose header omits the message id is not accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (certificate provider)
            participant CUT as Connector Under Test (certificate consumer)

            TCK->>CUT: catalog -> negotiation -> transfer
            TCK->>CUT: POST /companycertificate/push (header.messageId absent)
            CUT-->>TCK: not 2xx
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    public void cx_ccm_03_01(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        // messageId is required, and is also the only handle v2.4.0 gives a receiver for recognising a
        // retransmission, so a push without one cannot be deduplicated
        rejectPush(catalogClient, header -> withMessageId(header, null), "a header with no messageId");
    }

    @Test
    @DisplayName("CX_CCM:03-02: a push whose sender BPN is malformed is not accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (certificate provider)
            participant CUT as Connector Under Test (certificate consumer)

            TCK->>CUT: catalog -> negotiation -> transfer
            TCK->>CUT: POST /companycertificate/push (header.senderBpn not a BPNL)
            CUT-->>TCK: not 2xx
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    public void cx_ccm_03_02(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        rejectPush(catalogClient,
                header -> new CcmHeader(header.context(), header.messageId(), "NOT-A-BPNL", header.receiverBpn(),
                        header.sentDateTime(), header.version(), header.relatedMessageId(), header.senderFeedbackUrl()),
                "a malformed senderBpn");
    }

    @Test
    @DisplayName("CX_CCM:03-03: a push carrying the wrong message context is not accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (certificate provider)
            participant CUT as Connector Under Test (certificate consumer)

            TCK->>CUT: catalog -> negotiation -> transfer
            TCK->>CUT: POST /companycertificate/push (header.context is the Status context)
            CUT-->>TCK: not 2xx
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    public void cx_ccm_03_03(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        // the context is the message-type discriminator; honouring a push that declares itself a status
        // report would mean the receiver is not reading it at all
        rejectPush(catalogClient,
                header -> new CcmHeader(Ccm240Contexts.STATUS, header.messageId(), header.senderBpn(),
                        header.receiverBpn(), header.sentDateTime(), header.version(), header.relatedMessageId(),
                        header.senderFeedbackUrl()),
                "the Status context on the push endpoint");
    }

    @Test
    @DisplayName("CX_CCM:03-04: a push carrying no certificate content is not accepted")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (certificate provider)
            participant CUT as Connector Under Test (certificate consumer)

            TCK->>CUT: catalog -> negotiation -> transfer
            TCK->>CUT: POST /companycertificate/push (content absent)
            CUT-->>TCK: not 2xx
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    public void cx_ccm_03_04(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        var edr = establishNotificationApiEdr(catalogClient);
        var push = new CcmCertificatePush(
                CcmMessages.header(Ccm240Contexts.PUSH, senderBpn(), receiverBpn()), null);

        assertResponseRejects(DataPlaneRequests.postJson(edr, Ccm240Paths.PUSH, push).code(), "no content");
    }

    private void rejectPush(CxDspCatalogClient catalogClient,
                            UnaryOperator<CcmHeader> mutation,
                            String description) {
        var edr = establishNotificationApiEdr(catalogClient);
        var valid = CcmMessages.push(senderBpn(), receiverBpn(), CertificateSets.set1Iso9001());
        var malformed = new CcmCertificatePush(mutation.apply(valid.header()), valid.content());

        assertResponseRejects(DataPlaneRequests.postJson(edr, Ccm240Paths.PUSH, malformed).code(), description);
    }

    private void assertResponseRejects(int code, String description) {
        // 404/405 would mean the request never reached the notification API, so it proves nothing here
        assertThat(code)
                .as("a push with %s reached no notification API (HTTP %d); check the asset's data address " +
                        "proxies path, method and body", description, code)
                .isNotIn(404, 405);
        assertThat(code >= 200 && code < 300)
                .as("a push with %s violates the CX-0135 v2.4.0 schema and must not be acknowledged as " +
                        "accepted, but the receiver answered HTTP %d", description, code)
                .isFalse();
    }

    private Edr establishNotificationApiEdr(CxDspCatalogClient catalogClient) {
        var catalog = fetchCatalog(catalogClient);
        var asset = findNotificationApiAsset(catalog);
        return establishEdr(catalog, String.valueOf(asset.get("@id")), format);
    }

    private static CcmHeader withMessageId(CcmHeader header, String messageId) {
        return new CcmHeader(header.context(), messageId, header.senderBpn(), header.receiverBpn(),
                header.sentDateTime(), header.version(), header.relatedMessageId(), header.senderFeedbackUrl());
    }
}
