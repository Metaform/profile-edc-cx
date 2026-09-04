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
import org.eclipse.dataspacetck.cx.ccm.v240.CcmMessages;
import org.eclipse.dataspacetck.cx.ccm.v240.fixture.CertificateSets;
import org.eclipse.dataspacetck.cx.ccm.v240.model.BusinessPartnerCertificate31;
import org.eclipse.dataspacetck.cx.usecase.schema.JsonSchemas;
import org.eclipse.dataspacetck.cx.verification.usecase.AbstractCxUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CX_CCM_00: self-verification of the suite's own material, with no system under test involved.
 * <p>
 * This is the only CCM test that runs in the local self-test, and it is what makes the self-test
 * meaningful for this suite: it proves the module is wired into the runtime package scan, and that the
 * certificates the TCK will put on the wire actually conform to the semantic model CX-0135 mandates. A
 * suite that sent non-conformant payloads would be asserting a system under test accepts messages the
 * standard never sanctioned.
 */
@Tag("cx-ccm")
@Tag("cx-ccm-v240")
@Tag("base-compliance")
@DisplayName("CX_CCM_00: suite self-verification")
public class CxCcm00Test extends AbstractCxUseCaseTest {

    private static final String CERTIFICATE_SCHEMA = "/ccm/v240/schema/business-partner-certificate-3.1.0-schema.json";
    private static final String PUSH_SCHEMA = "/ccm/v240/schema/certificate-push-schema.json";

    @MandatoryTest
    @DisplayName("CX_CCM:00-01: the suite's certificates and messages conform to the CX-0135 v2.4.0 schemas")
    public void cx_ccm_00_01() {
        Map<String, BusinessPartnerCertificate31> sets = Map.of(
                "Set 1 (ISO 9001)", CertificateSets.set1Iso9001(),
                "Set 3 (mixed BPNS and BPNA sites)", CertificateSets.set3MixedSites(),
                "Set 4 (expired)", CertificateSets.set4Expired(),
                "Set 5 (no expiry)", CertificateSets.set5NoExpiry());

        sets.forEach((name, certificate) -> {
            assertThat(JsonSchemas.violationsOf(CERTIFICATE_SCHEMA, certificate))
                    .as("%s against BusinessPartnerCertificate 3.1.0", name)
                    .isEmpty();

            var push = CcmMessages.push("BPNL0000000001AB", "BPNL0000000002CD",
                    "https://example.com/api/dsp", certificate);
            assertThat(JsonSchemas.violationsOf(PUSH_SCHEMA, push))
                    .as("a push carrying %s", name)
                    .isEmpty();
        });

        // the fixtures cover the payload dimensions the CX-0135 test cases distinguish
        assertThat(CertificateSets.set4Expired().validUntil()).as("Set 4 must be expired").isLessThan("2024-01-01");
        assertThat(CertificateSets.set5NoExpiry().validUntil()).as("Set 5 never-expires sentinel").isEqualTo("9999-12-31");
        assertThat(sitePrefixes()).as("Set 3 must mix site and address locations").contains("BPNS", "BPNA");

        // and the schema check must be capable of rejecting something, or the assertions above prove nothing
        assertThat(JsonSchemas.violations(CERTIFICATE_SCHEMA, CertificateSets.raw("invalid-malformed.json")))
                .as("the deliberately malformed fixture must not validate")
                .isNotEmpty();
    }

    private static List<String> sitePrefixes() {
        return CertificateSets.set3MixedSites().enclosedSites().stream()
                .map(site -> site.enclosedSiteBpn().substring(0, 4))
                .distinct()
                .toList();
    }
}
