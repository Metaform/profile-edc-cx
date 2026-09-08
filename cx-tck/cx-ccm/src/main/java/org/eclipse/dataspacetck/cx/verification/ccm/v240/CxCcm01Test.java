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
import org.eclipse.dataspacetck.cx.ccm.CcmTaxonomy;
import org.eclipse.dataspacetck.cx.dcp.annotation.DcpScope;
import org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions;
import org.eclipse.dataspacetck.cx.dsp.catalog.CxPolicies;
import org.eclipse.dataspacetck.cx.dsp.catalog.DatasetQuery;
import org.eclipse.dataspacetck.cx.dsp.catalog.client.CxDspCatalogClient;
import org.eclipse.dataspacetck.dcp.system.annotation.IssueCredentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.BPN_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.BPN_SCOPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.GOV_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.GOV_SCOPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.MEMBERSHIP_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.MEMBERSHIP_SCOPE;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.CX_COMMON_VERSION;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.DCT_SUBJECT;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.DCT_TYPE;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.cxPolicy;

/**
 * CX_CCM_01: the asset and policy a Certificate Consumer must publish so a Certificate Provider can
 * deliver to it, per CX-0135 sections 2.1.4 and 2.1.7.
 * <p>
 * These are catalog-only checks - no negotiation, no transfer, no application call - which makes them the
 * cheapest signal in the suite and the first thing to look at when a push test fails: if the asset is not
 * classified as CX-0135 requires, nothing downstream can work.
 */
@Tag("base-compliance")
@DisplayName("CX_CCM_01: Company Certificate Management asset and policy conformance")
public class CxCcm01Test extends AbstractCcm240Test {

    @MandatoryTest
    @DisplayName("CX_CCM:01-01: the notification API asset is classified as CX-0135 requires")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (certificate provider)
            participant CUT as Connector Under Test (certificate consumer)

            TCK->>CUT: CatalogRequestMessage (Authorization: DCP self-issued token)
            CUT-->>TCK: Catalog
            Note over TCK: exactly one dataset carries dct:type cx-taxo:CCMAPI,<br/>dct:subject cx-taxo:CompanyCertificateManagementNotificationApi<br/>and cx-common:version
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    public void cx_ccm_01_01(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        var catalog = fetchCatalog(catalogClient);

        // findSingleDataset reports which clause each candidate failed, so a mis-typed taxonomy term or a
        // missing version is named rather than surfacing as "no dataset found"
        var asset = findNotificationApiAsset(catalog);

        assertThat(CxFunctions.idValues(asset, DCT_TYPE))
                .as("dct:type of the notification API asset").contains(CcmTaxonomy.CCM_API_TYPE);
        assertThat(CxFunctions.idValues(asset, DCT_SUBJECT))
                .as("dct:subject of the notification API asset").contains(CcmTaxonomy.NOTIFICATION_API_SUBJECT);
        assertThat(CxFunctions.propertyValues(asset, CX_COMMON_VERSION))
                .as("cx-common:version of the notification API asset").isNotEmpty();
    }

    @MandatoryTest
    @DisplayName("CX_CCM:01-02: the notification API offer carries the CCM usage purpose and framework agreement")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (certificate provider)
            participant CUT as Connector Under Test (certificate consumer)

            TCK->>CUT: CatalogRequestMessage (Authorization: DCP self-issued token)
            CUT-->>TCK: Catalog (dataset + offer)
            Note over TCK: the offer constrains UsagePurpose isAnyOf cx.ccm.base:1<br/>and FrameworkAgreement eq DataExchangeGovernance:1.0
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    public void cx_ccm_01_02(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        var asset = findNotificationApiAsset(fetchCatalog(catalogClient));
        var constraints = CxPolicies.constraintsOf(asset);

        assertThat(constraints)
                .as("the notification API offer states no usage constraints at all")
                .isNotEmpty();

        // the usage purpose is the value most often wrong in a deployment: another use case's purpose
        // (cx.pcf.base:1 and friends) is easy to copy across, and nothing else in the exchange notices
        assertThat(CxPolicies.hasConstraint(asset, cxPolicy("UsagePurpose"), CcmTaxonomy.CCM_USAGE_PURPOSE))
                .as("CX-0135 section 2.1.7 requires UsagePurpose '%s'; the offer states %s",
                        CcmTaxonomy.CCM_USAGE_PURPOSE, constraints)
                .isTrue();
        assertThat(CxPolicies.hasConstraint(asset, cxPolicy("FrameworkAgreement"), CcmTaxonomy.FRAMEWORK_AGREEMENT))
                .as("CX-0135 section 2.1.7 requires FrameworkAgreement '%s'; the offer states %s",
                        CcmTaxonomy.FRAMEWORK_AGREEMENT, constraints)
                .isTrue();
    }

    @MandatoryTest
    @DisplayName("CX_CCM:01-03: at most one CCM API asset per subject and version is offered")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (certificate provider)
            participant CUT as Connector Under Test (certificate consumer)

            TCK->>CUT: CatalogRequestMessage (Authorization: DCP self-issued token)
            CUT-->>TCK: Catalog
            Note over TCK: no two CCM API datasets share a (dct:subject, cx-common:version) pair
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    public void cx_ccm_01_03(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        var ccmApiAssets = CxFunctions.findDatasets(fetchCatalog(catalogClient),
                DatasetQuery.create().id(DCT_TYPE, CcmTaxonomy.CCM_API_TYPE));

        var bySubjectAndVersion = new LinkedHashMap<String, List<Object>>();
        for (var asset : ccmApiAssets) {
            var key = CxFunctions.idValues(asset, DCT_SUBJECT) + " @ " + CxFunctions.propertyValues(asset, CX_COMMON_VERSION);
            bySubjectAndVersion.computeIfAbsent(key, ignored -> new ArrayList<>()).add(asset.get("@id"));
        }

        var duplicates = bySubjectAndVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .toList();

        // CX-0135 section 2.1.4.1 states the rule across all connectors of one BPNL; a TCK observes one
        // catalog, so this verifies the rule within the catalog under test only
        assertThat(duplicates)
                .as("CX-0135 section 2.1.4.1 allows only one asset per API subject and version")
                .isEmpty();
    }
}
