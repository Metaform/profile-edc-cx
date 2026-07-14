/*
 *  Copyright (c) 2025 Metaform Systems, Inc.
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

package org.eclipse.dataspacetck.cx.verification.catalog;

import org.eclipse.dataspacetck.api.system.MandatoryTest;
import org.eclipse.dataspacetck.api.system.TestSequenceDiagram;
import org.eclipse.dataspacetck.core.api.system.ConfigParam;
import org.eclipse.dataspacetck.core.api.system.Inject;
import org.eclipse.dataspacetck.cx.dcp.annotation.BpnNumber;
import org.eclipse.dataspacetck.cx.dcp.annotation.DcpScope;
import org.eclipse.dataspacetck.cx.dsp.catalog.client.CxDspCatalogClient;
import org.eclipse.dataspacetck.dcp.system.annotation.IssueCredentials;
import org.eclipse.dataspacetck.dsp.system.api.connector.Connector;
import org.eclipse.dataspacetck.dsp.system.api.connector.Provider;
import org.eclipse.dataspacetck.dsp.system.api.verification.AbstractVerificationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import java.util.List;
import java.util.Map;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.BPN_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.BPN_SCOPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.GOV_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.GOV_SCOPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.MEMBERSHIP_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.MEMBERSHIP_SCOPE;
import static org.eclipse.dataspacetck.dsp.system.api.message.DcatConstants.DCAT_PROPERTY_DATASET_EXPANDED;
import static org.eclipse.dataspacetck.dsp.system.api.message.DspConstants.DSPACE_CATALOG_ERROR;
import static org.eclipse.dataspacetck.dsp.system.api.message.DspConstants.ID;
import static org.eclipse.dataspacetck.dsp.system.api.message.DspConstants.TYPE;
import static org.eclipse.dataspacetck.dsp.system.api.message.MessageSerializer.registerValidator;
import static org.eclipse.dataspacetck.dsp.system.api.message.catalog.CatalogFunctions.createCatalogRequest;
import static org.eclipse.dataspacetck.dsp.system.api.message.catalog.CatalogFunctions.createDataset;

/**
 * CX_CAT_01: verifies a single Catena-X catalog request.
 * <p>
 * The exchange uses the <strong>DSP</strong> protocol (a {@code CatalogRequestMessage} to the connector under test),
 * while the request is authorized with a <strong>DCP</strong> self-issued identity token attached transparently by the
 * {@code CxSystemLauncher}. This is the smallest end-to-end flow that exercises the Catena-X combination of DSP
 * (exchange) + DCP (identity).
 */
@Tag("cx-cat")
@Tag("base-compliance")
@DisplayName("CX_CAT_01: Catalog request with DCP identity")
public class CxCatalog01Test extends AbstractVerificationTest {

    @Inject
    @Provider
    protected Connector providerConnector;

    @ConfigParam
    protected String datasetId = randomUUID().toString();


    @BeforeAll
    static void setUp() {
        registerValidator("CatalogRequestMessage", forSchema("/catalog/catalog-request-message-schema.json"));
        registerValidator("Catalog", forSchema("/catalog/catalog-schema.json"));
        registerValidator("Dataset", forSchema("/catalog/dataset-schema.json"));
    }

    @SuppressWarnings("unchecked")
    @MandatoryTest
    @DisplayName("CX_CAT:01-01: Verify catalog request authorized with a DCP identity")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer)
            participant CUT as Connector Under Test (provider)
            
            TCK->>CUT: CatalogRequestMessage (Authorization: DCP self-issued token)
            CUT->>TCK: DCP PresentationQueryMessage (resolve holder CredentialService)
            TCK-->>CUT: VerifiablePresentation (MembershipCredential, BPNCredential, DataExchangeGovernanceCredential)
            CUT-->>TCK: Catalog
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    public void cx_cat_01_01(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        // seed the local connector (used when running in local self-test mode)
        providerConnector.getCatalogManager().addDataset(createDataset(datasetId));

        var catalog = catalogClient.getCatalog(createCatalogRequest());

        assertThat(catalog).isNotNull();
        assertThat(catalog.get(DCAT_PROPERTY_DATASET_EXPANDED)).isNotNull();

        List<Map<String, Object>> datasets = (List<Map<String, Object>>) catalog.get(DCAT_PROPERTY_DATASET_EXPANDED);

        assertThat(datasets).isNotEmpty()
                .anySatisfy(dataset -> assertThat(dataset.get(ID)).isEqualTo(datasetId));
    }

    @MandatoryTest
    @DisplayName("CX_CAT:01-02: Verify catalog request authorized with a DCP identity with wrong scopes")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer)
            participant CUT as Connector Under Test (provider)
            
            TCK->>CUT: CatalogRequestMessage (Authorization: DCP self-issued token)
            CUT->>TCK: DCP PresentationQueryMessage (resolve holder CredentialService)
            TCK-->>CUT: VerifiablePresentation (BPNCredential)
            CUT-->>TCK: 401 ERROR
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    public void cx_cat_01_02(@DcpScope({BPN_SCOPE}) CxDspCatalogClient catalogClient) {
        providerConnector.getCatalogManager().addDataset(createDataset(datasetId));

        var error = catalogClient.getCatalog(createCatalogRequest(), true);

        assertThat(error).containsEntry(TYPE, List.of(DSPACE_CATALOG_ERROR));

    }

    @MandatoryTest
    @DisplayName("CX_CAT:01-03: Verify catalog request authorized with a DCP identity missing required credentials")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer)
            participant CUT as Connector Under Test (provider)
            
            TCK->>CUT: CatalogRequestMessage (Authorization: DCP self-issued token)
            CUT->>TCK: DCP PresentationQueryMessage (resolve holder CredentialService)
            TCK-->>CUT: VerifiablePresentation (BPNCredential)
            CUT-->>TCK: 401 ERROR
            """)
    @IssueCredentials({BPN_CREDENTIAL_TYPE})
    public void cx_cat_01_03(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        providerConnector.getCatalogManager().addDataset(createDataset(datasetId));

        var error = catalogClient.getCatalog(createCatalogRequest(), true);

        assertThat(error).containsEntry(TYPE, List.of(DSPACE_CATALOG_ERROR));

    }


    @SuppressWarnings("unchecked")
    @MandatoryTest
    @DisplayName("CX_CAT:01-04: Verify catalog request authorized with a DCP identity containing BPN access restriction dataset")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer)
            participant CUT as Connector Under Test (provider)
            
            TCK->>CUT: CatalogRequestMessage (Authorization: DCP self-issued token)
            CUT->>TCK: DCP PresentationQueryMessage (resolve holder CredentialService)
            TCK-->>CUT: VerifiablePresentation (MembershipCredential, BPNCredential, DataExchangeGovernanceCredential)
            CUT-->>TCK: Catalog
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    public void cx_cat_01_04(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        providerConnector.getCatalogManager().addDataset(createDataset(datasetId));

        var catalog = catalogClient.getCatalog(createCatalogRequest());

        assertThat(catalog).isNotNull();
        assertThat(catalog.get(DCAT_PROPERTY_DATASET_EXPANDED)).isNotNull();

        List<Map<String, Object>> datasets = (List<Map<String, Object>>) catalog.get(DCAT_PROPERTY_DATASET_EXPANDED);

        assertThat(datasets).isNotEmpty()
                .anySatisfy(dataset -> assertThat(dataset.get(ID)).isEqualTo(datasetId));
    }

    @SuppressWarnings("unchecked")
    @MandatoryTest
    @DisplayName("CX_CAT:01-05: Verify catalog request authorized with a DCP identity filter BPN access restriction dataset")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer)
            participant CUT as Connector Under Test (provider)
            
            TCK->>CUT: CatalogRequestMessage (Authorization: DCP self-issued token)
            CUT->>TCK: DCP PresentationQueryMessage (resolve holder CredentialService)
            TCK-->>CUT: VerifiablePresentation (MembershipCredential, BPNCredential, DataExchangeGovernanceCredential)
            CUT-->>TCK: Catalog
            """)
    @BpnNumber("FAKEBPN123456789")
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    public void cx_cat_01_05(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        providerConnector.getCatalogManager().addDataset(createDataset("non-matching-dataset-id"));

        var catalog = catalogClient.getCatalog(createCatalogRequest());

        assertThat(catalog).isNotNull();
        assertThat(catalog.get(DCAT_PROPERTY_DATASET_EXPANDED)).isNotNull();

        List<Map<String, Object>> datasets = (List<Map<String, Object>>) catalog.get(DCAT_PROPERTY_DATASET_EXPANDED);

        assertThat(datasets).isNotEmpty()
                .allSatisfy(dataset -> assertThat(dataset.get(ID)).isNotEqualTo(datasetId));
    }
}
