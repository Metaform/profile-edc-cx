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

import org.eclipse.dataspacetck.cx.usecase.schema.JsonSchemas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the checked-in CCM schemas and certificate fixtures against each other, with no connector
 * involved. This is what keeps the suite honest: the fixtures are what the TCK puts on the wire, so if
 * they drift out of conformance the TCK would be asserting a system under test accepts a message that
 * CX-0135 never sanctioned.
 */
class CcmSchemaTest {

    private static final String CERTIFICATE_SCHEMA = "/ccm/v240/schema/business-partner-certificate-3.1.0-schema.json";
    private static final String FIXTURES = "/ccm/v240/fixtures/";

    @ParameterizedTest
    @ValueSource(strings = {"set1-iso9001.json", "set3-mixed-sites.json", "set4-expired.json", "set5-no-expiry.json"})
    void certificateFixturesSatisfyTheSemanticModel(String fixture) {
        assertThat(JsonSchemas.violations(CERTIFICATE_SCHEMA, resource(FIXTURES + fixture)))
                .as("fixture %s against BusinessPartnerCertificate 3.1.0", fixture)
                .isEmpty();
    }

    @Test
    void theMalformedFixtureIsRejected() {
        // proves the schema check has teeth; without this a broken validator would pass everything above
        assertThat(JsonSchemas.violations(CERTIFICATE_SCHEMA, resource(FIXTURES + "invalid-malformed.json")))
                .as("the deliberately malformed fixture must not validate")
                .isNotEmpty();
    }

    @Test
    void pushEnvelopeValidatesAcrossSchemaDialects() {
        // the envelope schemas are 2020-12 while the vendored certificate model is draft-04, so this also
        // proves the registry honours each document's own dialect rather than forcing one
        var push = """
                {
                  "header": {
                    "context": "CompanyCertificateManagement-CCMAPI-Push:1.0.0",
                    "messageId": "urn:uuid:e4da568b-8cf1-4f5f-a96a-cf26265b2c72",
                    "senderBpn": "BPNL0000000001AB",
                    "receiverBpn": "BPNL0000000002CD",
                    "sentDateTime": "2024-10-07T10:15:00Z",
                    "version": "3.1.0",
                    "senderFeedbackUrl": "https://example.com/api/dsp"
                  },
                  "content": %s
                }
                """.formatted(resource(FIXTURES + "set1-iso9001.json"));

        assertThat(JsonSchemas.violations("/ccm/v240/schema/certificate-push-schema.json", push)).isEmpty();
    }

    @Test
    void pushEnvelopeWithBadHeaderIsRejected() {
        var push = """
                {
                  "header": {
                    "context": "CompanyCertificateManagement-CCMAPI-Push:1.0.0",
                    "messageId": "not-a-uuid",
                    "senderBpn": "NOT-A-BPNL",
                    "sentDateTime": "2024-10-07T10:15:00Z",
                    "version": "3.1.0"
                  },
                  "content": %s
                }
                """.formatted(resource(FIXTURES + "set1-iso9001.json"));

        // a malformed messageId, a malformed senderBpn and a missing receiverBpn
        assertThat(JsonSchemas.violations("/ccm/v240/schema/certificate-push-schema.json", push)).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void noSchemaReferencesRemoteDocument() {
        // a remote $ref would make validation depend on an external host being reachable, which an
        // in-cluster run may not be able to do at all
        try (var files = Files.walk(Path.of("src/main/resources/ccm/v240/schema"))) {
            var offenders = files.filter(Files::isRegularFile)
                    .filter(path -> read(path).contains("\"$ref\": \"http"))
                    .map(Path::toString)
                    .toList();
            assertThat(offenders).as("schemas must not reference remote documents").isEmpty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String resource(String location) {
        try (var stream = CcmSchemaTest.class.getResourceAsStream(location)) {
            if (stream == null) {
                throw new IllegalStateException("Missing classpath resource: " + location);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
