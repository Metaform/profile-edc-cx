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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.CX_COMMON_VERSION;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.DCT_SUBJECT;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.DCT_TYPE;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.cxTaxo;

/**
 * Verifies dataset discovery over the expanded JSON-LD shapes a connector may actually emit. The
 * {@code @list} case is the one that matters most: EDC wraps an array-valued asset property in a single
 * node carrying an {@code @list}, and failing to unwrap it makes a present property look absent.
 */
class CxFunctionsDiscoveryTest {

    private static final String DCT_ENCLOSED_SITES = CxTaxonomy.DCT_NAMESPACE + "enclosedSites";
    private static final String DATASETS = "http://www.w3.org/ns/dcat#dataset";

    private final Map<String, Object> catalog = loadCatalog();

    @Test
    void findsTheNotificationApiAssetByItsTaxonomyProperties() {
        var query = DatasetQuery.create()
                .id(DCT_TYPE, cxTaxo("CCMAPI"))
                .id(DCT_SUBJECT, cxTaxo("CompanyCertificateManagementNotificationApi"))
                .value(CX_COMMON_VERSION, "3.0");

        assertThat(CxFunctions.findSingleDataset(catalog, query)).containsEntry("@id", "ccm_api_asset");
    }

    @Test
    void unwrapsListWrappedPropertyValues() {
        var certificateAsset = CxFunctions.findSingleDataset(catalog,
                DatasetQuery.create().id(DCT_SUBJECT, cxTaxo("CompanyCertificate")));

        // the two sites are expanded as a single node wrapping an "@list"
        assertThat(CxFunctions.propertyValues(certificateAsset, DCT_ENCLOSED_SITES))
                .containsExactly(cxTaxo("BPNS000000000001"), cxTaxo("BPNA000000000002"));
        assertThat(DatasetQuery.create()
                .containsAll(DCT_ENCLOSED_SITES, List.of(cxTaxo("BPNA000000000002"), cxTaxo("BPNS000000000001")))
                .matches(certificateAsset)).isTrue();
    }

    @Test
    void distinguishesNodeReferencesFromLiterals() {
        var node = Map.<String, Object>of(
                DCT_TYPE, List.of(Map.of("@id", cxTaxo("CCMAPI"))),
                CX_COMMON_VERSION, List.of(Map.of("@value", "3.0")));

        assertThat(CxFunctions.idValues(node, DCT_TYPE)).containsExactly(cxTaxo("CCMAPI"));
        assertThat(CxFunctions.literalValues(node, DCT_TYPE)).isEmpty();

        assertThat(CxFunctions.literalValues(node, CX_COMMON_VERSION)).containsExactly("3.0");
        assertThat(CxFunctions.idValues(node, CX_COMMON_VERSION)).isEmpty();

        // property() is deliberately shape-agnostic, for connectors inconsistent about which they emit
        assertThat(CxFunctions.propertyValues(node, DCT_TYPE)).containsExactly(cxTaxo("CCMAPI"));
        assertThat(CxFunctions.propertyValues(node, CX_COMMON_VERSION)).containsExactly("3.0");
    }

    @Test
    void toleratesPartiallyCompactedShapes() {
        // a bare scalar where expansion would have produced {"@value": ...}
        var scalar = Map.<String, Object>of(CX_COMMON_VERSION, "3.0");
        assertThat(CxFunctions.propertyValues(scalar, CX_COMMON_VERSION)).containsExactly("3.0");
        assertThat(CxFunctions.literalValues(scalar, CX_COMMON_VERSION)).containsExactly("3.0");

        // a bare map where expansion would have produced a single-element array
        var bareMap = Map.<String, Object>of(DCT_TYPE, Map.of("@id", cxTaxo("CCMAPI")));
        assertThat(CxFunctions.propertyValues(bareMap, DCT_TYPE)).containsExactly(cxTaxo("CCMAPI"));
    }

    @Test
    void returnsNoValuesForAbsentOrStructuralProperties() {
        var node = Map.<String, Object>of(
                DCT_TYPE, List.of(Map.of("http://example.com/nested", "irrelevant")));

        assertThat(CxFunctions.propertyValues(node, "http://example.com/absent")).isEmpty();
        // a nested node carrying neither @id nor @value contributes no value, but is reachable structurally
        assertThat(CxFunctions.propertyValues(node, DCT_TYPE)).isEmpty();
        assertThat(CxFunctions.nodeValues(node, DCT_TYPE)).hasSize(1);
    }

    @Test
    void treatsAnEmptyCatalogAsResultRatherThanError() {
        assertThat(CxFunctions.datasets(Map.of())).isEmpty();
        assertThat(CxFunctions.findDatasets(Map.of(), DatasetQuery.create().any(DCT_TYPE))).isEmpty();
        assertThat(CxFunctions.findFirstDataset(Map.of(), DatasetQuery.create())).isEmpty();
    }

    @Test
    void explainsWhichClauseFailedWhenNothingMatches() {
        var query = DatasetQuery.create()
                .id(DCT_TYPE, cxTaxo("CCMAPI"))
                .value(CX_COMMON_VERSION, "9.9");

        assertThatThrownBy(() -> CxFunctions.findSingleDataset(catalog, query))
                .isInstanceOf(AssertionError.class)
                // the near-miss asset and the clause it failed must both be named, so a version skew is
                // diagnosable without dumping the catalog
                .hasMessageContaining("ccm_api_asset")
                .hasMessageContaining(CX_COMMON_VERSION);
    }

    @Test
    void failsWhenMoreThanOneDatasetMatches() {
        // both the certificate asset and the unrelated asset are Submodels
        var query = DatasetQuery.create().id(DCT_TYPE, cxTaxo("Submodel"));

        assertThat(CxFunctions.findDatasets(catalog, query)).hasSize(2);
        assertThatThrownBy(() -> CxFunctions.findSingleDataset(catalog, query))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("found 2");
    }

    @Test
    void matchesSingleDatasetNotWrappedInArray() {
        var single = Map.<String, Object>of(DATASETS, Map.of("@id", "only", DCT_TYPE, List.of(Map.of("@id", cxTaxo("CCMAPI")))));

        assertThat(CxFunctions.datasets(single)).hasSize(1);
        assertThat(CxFunctions.findSingleDataset(single, DatasetQuery.create().id(DCT_TYPE, cxTaxo("CCMAPI"))))
                .containsEntry("@id", "only");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadCatalog() {
        try (var stream = CxFunctionsDiscoveryTest.class.getResourceAsStream("/catalog/ccm-catalog-expanded.json")) {
            return new ObjectMapper().readValue(stream, Map.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
