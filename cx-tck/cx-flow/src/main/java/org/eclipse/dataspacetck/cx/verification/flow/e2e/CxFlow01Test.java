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

package org.eclipse.dataspacetck.cx.verification.flow.e2e;

import org.eclipse.dataspacetck.api.system.MandatoryTest;
import org.eclipse.dataspacetck.api.system.TestSequenceDiagram;
import org.eclipse.dataspacetck.cx.dcp.annotation.ContractVersion;
import org.eclipse.dataspacetck.cx.dcp.annotation.DcpScope;
import org.eclipse.dataspacetck.cx.dsp.catalog.client.CxDspCatalogClient;
import org.eclipse.dataspacetck.cx.runtime.CxRuntime;
import org.eclipse.dataspacetck.cx.verification.flow.AbstractCxFlowTest;
import org.eclipse.dataspacetck.dcp.system.annotation.IssueCredentials;
import org.eclipse.dataspacetck.dsp.verification.cn.ProviderActions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNullElseGet;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.BPN_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.BPN_SCOPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.GOV_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.GOV_SCOPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.MEMBERSHIP_CREDENTIAL_TYPE;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.MEMBERSHIP_SCOPE;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractAgreementId;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractDataAddress;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractEndpoint;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractOfferId;
import static org.eclipse.dataspacetck.dsp.system.api.message.DcatConstants.DCAT_PROPERTY_DATASET_EXPANDED;
import static org.eclipse.dataspacetck.dsp.system.api.message.catalog.CatalogFunctions.createCatalogRequest;
import static org.eclipse.dataspacetck.dsp.system.api.statemachine.ContractNegotiation.State.AGREED;
import static org.eclipse.dataspacetck.dsp.system.api.statemachine.ContractNegotiation.State.FINALIZED;
import static org.eclipse.dataspacetck.dsp.system.api.statemachine.TransferProcess.State.STARTED;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * CX_FLOW_01: Catena-X end-to-end flow tests combining DSP exchange (catalog, contract negotiation, transfer process)
 * with a DCP self-issued identity. The connector under test is the provider; the TCK drives the flow as the consumer.
 */
@Tag("cx-flow")
@Tag("base-compliance")
@DisplayName("CX_FLOW_01: catalog -> negotiation -> transfer with DCP identity")
public class CxFlow01Test extends AbstractCxFlowTest {

    @MandatoryTest
    @DisplayName("CX_FLOW:01-01: catalog request, contract negotiation finalized, transfer completed")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer)
            participant CUT as Connector Under Test (provider)
            
            TCK->>CUT: CatalogRequestMessage (Authorization: DCP self-issued token)
            CUT-->>TCK: Catalog (dataset + offer)
            
            TCK->>CUT: ContractRequestMessage (offer from catalog)
            CUT-->>TCK: ContractNegotiation
            CUT->>TCK: ContractOfferMessage
            TCK->>CUT: ContractNegotiationEventMessage:accepted
            CUT->>TCK: ContractAgreementMessage
            TCK->>CUT: ContractAgreementVerificationMessage
            CUT->>TCK: ContractNegotiationEventMessage:finalized
            
            TCK->>CUT: TransferRequestMessage (agreement from negotiation)
            CUT-->>TCK: TransferProcess
            CUT->>TCK: TransferStartMessage
            CUT->>TCK: TransferCompletionMessage
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    public void cx_flow_01_01(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        // seed the in-memory catalog (used when running in local self-test mode)
        providerConnector.getCatalogManager().addDataset(seedDataset(datasetId));

        // 1) fetch the catalog and extract the real offer published for the dataset
        var catalog = catalogClient.getCatalog(createCatalogRequest());
        assertThat(catalog.get(DCAT_PROPERTY_DATASET_EXPANDED)).isNotNull();
        var offerId = extractOfferId(catalog, datasetId);

        // 2) negotiate the extracted offer through to FINALIZED, capturing the provider-issued agreement id.
        // The pipeline goes contract request -> agreement directly (no separate offer exchange), so the provider
        // agrees on the request and finalizes after verification.
        var agreementIdRef = new AtomicReference<String>();
        negotiationMock.recordContractRequestedAction(ProviderActions::postAgreed);
        negotiationMock.recordVerifiedAction(ProviderActions::postFinalized);

        negotiationPipeline
                .sendRequestMessage(datasetId, offerId)
                .expectAgreementMessage(agreement -> {
                    agreementIdRef.set(extractAgreementId(agreement));
                    consumerConnector.getConsumerNegotiationManager().handleAgreement(agreement);
                })
                .thenWaitForState(AGREED)
                .expectFinalizedEvent(event -> consumerConnector.getConsumerNegotiationManager().handleFinalized(event))
                .sendVerifiedEvent()
                .thenWaitForState(FINALIZED)
                .execute();
        negotiationMock.verify();

        // 3) run a transfer against the negotiated agreement, capturing the data address from the start message
        var agreementId = requireNonNullElseGet(agreementIdRef.get(), () -> randomUUID().toString());
        var dataAddressRef = new AtomicReference<Map<String, Object>>();
        transferProcessMock.recordTransferRequestedAction(AbstractCxFlowTest::postStartWithDataAddress);

        transferProcessPipeline
                .expectStartMessage(start -> {
                    dataAddressRef.set(extractDataAddress(start));
                    return consumerConnector.getConsumerTransferProcessManager().handleStart(start);
                })
                .sendTransferRequest(agreementId, format)
                .thenWaitForState(STARTED)
                .execute();
        transferProcessMock.verify();

        // the provider's TransferStartMessage carries the data address the consumer pulls data from
        var dataAddress = dataAddressRef.get();
        assertThat(dataAddress).as("transfer start data address").isNotNull();
        assertThat(extractEndpoint(dataAddress)).as("data address endpoint").isNotBlank();
    }

    @MandatoryTest
    @DisplayName("CX_FLOW:01-02: contract negotiation terminated when the dataset's contract policy is not fulfilled")
    @TestSequenceDiagram("""
            participant TCK as Technology Compatibility Kit (consumer)
            participant CUT as Connector Under Test (provider)
            
            TCK->>CUT: CatalogRequestMessage (Authorization: DCP self-issued token)
            CUT-->>TCK: Catalog (dataset + offer)
            
            TCK->>CUT: ContractRequestMessage (offer from catalog, non-matching Contract Version in DataGovernance Policy)
            CUT-->>TCK: 401 Unauthorized
            """)
    @IssueCredentials({MEMBERSHIP_CREDENTIAL_TYPE, BPN_CREDENTIAL_TYPE, GOV_CREDENTIAL_TYPE})
    @DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE})
    @ContractVersion("not-matching-version")
    public void cx_flow_01_02(@DcpScope({MEMBERSHIP_SCOPE, BPN_SCOPE, GOV_SCOPE}) CxDspCatalogClient catalogClient) {
        // The in-memory connector does not evaluate contract policy, so it cannot reject the request with a 401. This
        // negative case is only meaningful against a real connector under test.
        assumeFalse(CxRuntime.isLocalConnector(), "contract-policy rejection requires a real connector under test");

        providerConnector.getCatalogManager().addDataset(seedDataset(datasetId));

        var catalog = catalogClient.getCatalog(createCatalogRequest());
        assertThat(catalog.get(DCAT_PROPERTY_DATASET_EXPANDED)).isNotNull();
        var offerId = extractOfferId(catalog, datasetId);

        negotiationPipeline
                .sendRequestMessage(datasetId, offerId, true)
                .execute();

        negotiationMock.verify();
    }
}
