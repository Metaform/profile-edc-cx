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

package org.eclipse.dataspacetck.cx.verification.usecase;

import org.eclipse.dataspacetck.core.api.system.Inject;
import org.eclipse.dataspacetck.cx.dsp.catalog.client.CxDspCatalogClient;
import org.eclipse.dataspacetck.cx.runtime.CxRuntime;
import org.eclipse.dataspacetck.cx.usecase.Edr;
import org.eclipse.dataspacetck.dsp.system.api.connector.Connector;
import org.eclipse.dataspacetck.dsp.system.api.connector.Consumer;
import org.eclipse.dataspacetck.dsp.system.api.connector.Provider;
import org.eclipse.dataspacetck.dsp.system.api.mock.ProviderNegotiationMock;
import org.eclipse.dataspacetck.dsp.system.api.mock.tp.ProviderTransferProcessMock;
import org.eclipse.dataspacetck.dsp.system.api.pipeline.ProviderNegotiationPipeline;
import org.eclipse.dataspacetck.dsp.system.api.pipeline.tp.ProviderTransferProcessPipeline;
import org.eclipse.dataspacetck.dsp.system.api.statemachine.TransferProcess;
import org.eclipse.dataspacetck.dsp.system.api.verification.AbstractVerificationTest;
import org.eclipse.dataspacetck.dsp.verification.cn.ProviderActions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractAgreementId;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractDataAddress;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractOfferId;
import static org.eclipse.dataspacetck.dsp.system.api.http.HttpFunctions.postJson;
import static org.eclipse.dataspacetck.dsp.system.api.message.DcatConstants.DCAT_PROPERTY_DATASET_EXPANDED;
import static org.eclipse.dataspacetck.dsp.system.api.message.MessageSerializer.registerValidator;
import static org.eclipse.dataspacetck.dsp.system.api.message.catalog.CatalogFunctions.createCatalogRequest;
import static org.eclipse.dataspacetck.dsp.system.api.message.tp.TransferFunctions.createStartRequest;
import static org.eclipse.dataspacetck.dsp.system.api.message.tp.TransferFunctions.dataAddress;
import static org.eclipse.dataspacetck.dsp.system.api.statemachine.ContractNegotiation.State.AGREED;
import static org.eclipse.dataspacetck.dsp.system.api.statemachine.ContractNegotiation.State.FINALIZED;
import static org.eclipse.dataspacetck.dsp.system.api.statemachine.TransferProcess.State.STARTED;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Base class for tests that verify a Catena-X <strong>use-case</strong> standard rather than the dataspace
 * protocol itself.
 * <p>
 * A use-case standard - CX-0135 Company Certificate Management, for instance - defines an application API
 * and the messages exchanged over it, but leaves reaching that API to DSP. So every such test has the same
 * shape: drive catalog, contract negotiation and transfer to obtain an {@link Edr}, then exercise the
 * application API through it. {@link #establishEdr} is that first half, and it is the same exchange
 * {@code CX_FLOW:01-01} verifies in its own right.
 * <p>
 * The connector under test acts as the <strong>provider</strong> and the TCK drives the exchange as the
 * consumer, exactly as in the flow tests; a single DCP self-issued identity token authorizes every DSP
 * message.
 */
@Tag("cx-usecase")
public abstract class AbstractCxUseCaseTest extends AbstractVerificationTest {

    @Inject
    @Consumer
    protected Connector consumerConnector;

    // seeds the in-memory catalog when running the local self-test (dataspacetck.dsp.local.connector=true)
    @Inject
    @Provider
    protected Connector providerConnector;

    @Inject
    protected ProviderNegotiationPipeline negotiationPipeline;

    @Inject
    protected ProviderNegotiationMock negotiationMock;

    @Inject
    protected ProviderTransferProcessPipeline transferProcessPipeline;

    @Inject
    protected ProviderTransferProcessMock transferProcessMock;

    @BeforeAll
    static void registerDspValidators() {
        // catalog
        registerValidator("CatalogRequestMessage", forSchema("/catalog/catalog-request-message-schema.json"));
        registerValidator("Catalog", forSchema("/catalog/catalog-schema.json"));
        registerValidator("Dataset", forSchema("/catalog/dataset-schema.json"));
        // contract negotiation
        registerValidator("ContractRequestMessage", forSchema("/negotiation/contract-request-message-schema.json"));
        registerValidator("ContractOfferMessage", forSchema("/negotiation/contract-offer-message-schema.json"));
        registerValidator("ContractAgreementMessage", forSchema("/negotiation/contract-agreement-message-schema.json"));
        registerValidator("ContractAgreementVerificationMessage", forSchema("/negotiation/contract-agreement-verification-message-schema.json"));
        registerValidator("ContractNegotiationEventMessage", forSchema("/negotiation/contract-negotiation-event-message-schema.json"));
        registerValidator("ContractNegotiationTerminationMessage", forSchema("/negotiation/contract-negotiation-termination-message-schema.json"));
        registerValidator("ContractNegotiation", forSchema("/negotiation/contract-negotiation-schema.json"));
        // transfer process
        registerValidator("TransferRequestMessage", forSchema("/transfer/transfer-request-message-schema.json"));
        registerValidator("TransferStartMessage", forSchema("/transfer/transfer-start-message-schema.json"));
        registerValidator("TransferProcess", forSchema("/transfer/transfer-process-schema.json"));
    }

    /**
     * Provider action that starts a transfer while embedding a data address in the {@code TransferStartMessage} (as a
     * real HTTP-PULL provider would). Used by the local self-test so the data-address extraction has an address to read;
     * against a real connector the connector under test supplies the actual data address instead.
     *
     * @param transferProcess the transfer process being started
     */
    protected static void postStartWithDataAddress(TransferProcess transferProcess) {
        var message = createStartRequest(transferProcess.providerPid(), transferProcess.consumerPid(), dataAddress());
        transferProcess.transition(TransferProcess.State.STARTED);
        var url = format("%s/transfers/%s/start", transferProcess.getCallbackAddress(), transferProcess.getCorrelationId());
        try (var response = postJson(url, message)) {
            if (!response.isSuccessful()) {
                throw new AssertionError("Unexpected response posting transfer start: " + response.code());
            }
        }
    }

    /**
     * Aborts the calling test unless it is running against a real connector.
     * <p>
     * The in-memory connector of the local self-test speaks DSP but serves no application API and holds no
     * DCP identity, so a use-case test has nothing to exercise against it.
     */
    protected static void assumeRealConnector() {
        assumeFalse(CxRuntime.isLocalConnector(), "use-case verification requires a real connector under test");
    }

    /**
     * Fetches the catalog and drives catalog to contract negotiation to transfer, returning the EDR the
     * provider handed back.
     *
     * @param catalogClient the catalog client bound to this test's DCP identity
     * @param datasetId     the dataset to negotiate
     * @param format        the transfer distribution format to request
     * @return the EDR carried in the provider's {@code TransferStartMessage}
     */
    protected Edr establishEdr(CxDspCatalogClient catalogClient, String datasetId, String format) {
        var catalog = catalogClient.getCatalog(createCatalogRequest());
        assertThat(catalog.get(DCAT_PROPERTY_DATASET_EXPANDED)).as("catalog datasets").isNotNull();
        return establishEdr(catalog, datasetId, format);
    }

    /**
     * Drives contract negotiation and transfer for a dataset of an already-fetched catalog.
     * <p>
     * Use this rather than {@link #establishEdr(CxDspCatalogClient, String, String)} when one catalog
     * response feeds several negotiations, so the catalog is not re-fetched per dataset.
     *
     * @param catalog   an expanded catalog response
     * @param datasetId the dataset to negotiate
     * @param format    the transfer distribution format to request
     * @return the EDR carried in the provider's {@code TransferStartMessage}
     */
    protected Edr establishEdr(Map<String, Object> catalog, String datasetId, String format) {
        var offerId = extractOfferId(catalog, datasetId);

        // negotiate the extracted offer through to FINALIZED, capturing the provider-issued agreement id.
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

        // run a transfer against the negotiated agreement, capturing the data address from the start message
        var agreementId = requireNonNullElseGet(agreementIdRef.get(), () -> randomUUID().toString());
        var dataAddressRef = new AtomicReference<Map<String, Object>>();
        transferProcessMock.recordTransferRequestedAction(AbstractCxUseCaseTest::postStartWithDataAddress);

        transferProcessPipeline
                .expectStartMessage(start -> {
                    dataAddressRef.set(extractDataAddress(start));
                    return consumerConnector.getConsumerTransferProcessManager().handleStart(start);
                })
                .sendTransferRequest(agreementId, format)
                .thenWaitForState(STARTED)
                .execute();
        transferProcessMock.verify();

        var dataAddress = dataAddressRef.get();
        assertThat(dataAddress).as("transfer start data address").isNotNull();
        return Edr.fromDataAddress(dataAddress, datasetId, agreementId);
    }
}
