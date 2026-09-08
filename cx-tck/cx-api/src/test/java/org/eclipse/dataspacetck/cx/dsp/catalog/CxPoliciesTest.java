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

package org.eclipse.dataspacetck.cx.dsp.catalog;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.DCT_SUBJECT;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.ODRL_CONSTRAINT;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.ODRL_HAS_POLICY;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.ODRL_LEFT_OPERAND;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.ODRL_PERMISSION;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.ODRL_RIGHT_OPERAND;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.cxPolicy;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.cxTaxo;

/**
 * Verifies constraint extraction from an offer, including the {@code odrl:and} nesting a connector wraps
 * multi-constraint policies in.
 */
class CxPoliciesTest {

    private final Map<String, Object> catalog = loadCatalog();

    @Test
    void readsConstraintsOutOfAnAndWrappedPermission() {
        var asset = CxFunctions.findSingleDataset(catalog,
                DatasetQuery.create().id(DCT_SUBJECT, cxTaxo("CompanyCertificateManagementNotificationApi")));

        assertThat(CxPolicies.constraintsOf(asset))
                .containsExactlyInAnyOrder(
                        new CxPolicies.Constraint(cxPolicy("FrameworkAgreement"),
                                CxTaxonomy.odrl("eq"), "DataExchangeGovernance:1.0"),
                        new CxPolicies.Constraint(cxPolicy("UsagePurpose"),
                                CxTaxonomy.odrl("isAnyOf"), "cx.ccm.base:1"));
    }

    @Test
    void reportsWhetherSpecificConstraintIsStated() {
        var asset = CxFunctions.findSingleDataset(catalog,
                DatasetQuery.create().id(DCT_SUBJECT, cxTaxo("CompanyCertificateManagementNotificationApi")));

        assertThat(CxPolicies.hasConstraint(asset, cxPolicy("UsagePurpose"), "cx.ccm.base:1")).isTrue();
        // the value a deployment is most likely to have wrong: another use case's purpose
        assertThat(CxPolicies.hasConstraint(asset, cxPolicy("UsagePurpose"), "cx.pcf.base:1")).isFalse();
    }

    @Test
    void readsConstraintStatedDirectlyWithoutWrapper() {
        // a single-constraint policy may be emitted without a logical container; the requirement it
        // states is identical, so extraction must not depend on the wrapper being there
        var dataset = Map.<String, Object>of(ODRL_HAS_POLICY, List.of(Map.of(
                ODRL_PERMISSION, List.of(Map.of(
                        ODRL_CONSTRAINT, List.of(Map.of(
                                ODRL_LEFT_OPERAND, List.of(Map.of("@id", cxPolicy("UsagePurpose"))),
                                ODRL_RIGHT_OPERAND, List.of(Map.of("@value", "cx.ccm.base:1")))))))));

        assertThat(CxPolicies.hasConstraint(dataset, cxPolicy("UsagePurpose"), "cx.ccm.base:1")).isTrue();
    }

    @Test
    void returnsNoConstraintsForDatasetWithoutPolicy() {
        assertThat(CxPolicies.constraintsOf(Map.of())).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadCatalog() {
        try (var stream = CxPoliciesTest.class.getResourceAsStream("/catalog/ccm-catalog-expanded.json")) {
            return new ObjectMapper().readValue(stream, Map.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
