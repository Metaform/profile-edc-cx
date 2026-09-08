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

package org.eclipse.dataspacetck.cx.ccm.v240;

import org.eclipse.dataspacetck.cx.ccm.v240.fixture.CertificateSets;
import org.eclipse.dataspacetck.cx.usecase.schema.JsonSchemas;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that what the builders actually put on the wire is conformant. The fixtures being valid is not
 * sufficient on its own: they are deserialized into records and re-serialized before being sent, so a
 * mapping mistake could still produce a non-conformant message from a conformant fixture.
 */
class CcmMessagesTest {

    private static final String PUSH_SCHEMA = "/ccm/v240/schema/certificate-push-schema.json";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void builtPushIsSchemaValid() {
        var push = CcmMessages.push("BPNL0000000001AB", "BPNL0000000002CD",
                "https://example.com/api/dsp", CertificateSets.set1Iso9001());

        assertThat(JsonSchemas.violationsOf(PUSH_SCHEMA, push)).isEmpty();
    }

    @Test
    void everySetSurvivesRoundTripIntoConformantPush() {
        var certificates = new Object[][]{
                {"set1", CertificateSets.set1Iso9001()},
                {"set3", CertificateSets.set3MixedSites()},
                {"set4", CertificateSets.set4Expired()},
                {"set5", CertificateSets.set5NoExpiry()},
        };
        for (var certificate : certificates) {
            var push = CcmMessages.push("BPNL0000000001AB", "BPNL0000000002CD",
                    (org.eclipse.dataspacetck.cx.ccm.v240.model.BusinessPartnerCertificate31) certificate[1]);
            assertThat(JsonSchemas.violationsOf(PUSH_SCHEMA, push))
                    .as("push carrying %s", certificate[0])
                    .isEmpty();
        }
    }

    @Test
    void theDocumentIdKeepsItsWireSpelling() {
        // 3.1.0 spells it documentID; a plain record component would serialize as documentId and the
        // receiver would silently see no document id at all
        var json = mapper.valueToTree(CertificateSets.set1Iso9001());

        assertThat(json.path("document").has("documentID")).as("wire spelling documentID").isTrue();
        assertThat(json.path("document").has("documentId")).as("must not emit documentId").isFalse();
        assertThat(json.path("document").path("documentID").asString()).isEqualTo("cx-tck-set1-document");
    }

    @Test
    void nullOptionalFieldsAreOmittedRatherThanSentAsNull() {
        // set3 declares no validator; emitting "validator": null would be a different message
        var json = mapper.valueToTree(CertificateSets.set3MixedSites());

        assertThat(json.has("validator")).as("absent optional field must be omitted").isFalse();
    }

    @Test
    void successivePushesAreDistinctDeliveries() {
        var first = CcmMessages.push("BPNL0000000001AB", "BPNL0000000002CD", CertificateSets.set1Iso9001());
        var second = CcmMessages.push("BPNL0000000001AB", "BPNL0000000002CD", CertificateSets.set1Iso9001());

        assertThat(first.header().messageId()).startsWith("urn:uuid:").isNotEqualTo(second.header().messageId());
    }

    @Test
    void pushDeclaresContextAndSemanticModelVersion() {
        var push = CcmMessages.push("BPNL0000000001AB", "BPNL0000000002CD", CertificateSets.set1Iso9001());

        assertThat(push.header().context()).isEqualTo("CompanyCertificateManagement-CCMAPI-Push:1.0.0");
        assertThat(push.header().version()).isEqualTo("3.1.0");
    }
}
