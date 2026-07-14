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

package org.eclipse.dataspacetck.cx.verification.flow;

import org.eclipse.dataspacetck.core.api.system.ConfigParam;
import org.eclipse.dataspacetck.core.api.system.Inject;
import org.eclipse.dataspacetck.dsp.system.api.connector.Connector;
import org.eclipse.dataspacetck.dsp.system.api.connector.Consumer;
import org.eclipse.dataspacetck.dsp.system.api.connector.Provider;
import org.eclipse.dataspacetck.dsp.system.api.connector.catalog.Dataset;
import org.eclipse.dataspacetck.dsp.system.api.mock.ProviderNegotiationMock;
import org.eclipse.dataspacetck.dsp.system.api.mock.tp.ProviderTransferProcessMock;
import org.eclipse.dataspacetck.dsp.system.api.pipeline.ProviderNegotiationPipeline;
import org.eclipse.dataspacetck.dsp.system.api.pipeline.tp.ProviderTransferProcessPipeline;
import org.eclipse.dataspacetck.dsp.system.api.statemachine.TransferProcess;
import org.eclipse.dataspacetck.dsp.system.api.verification.AbstractVerificationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;

import java.util.List;

import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.eclipse.dataspacetck.dsp.system.api.connector.IdGenerator.offerIdFromDatasetId;
import static org.eclipse.dataspacetck.dsp.system.api.http.HttpFunctions.postJson;
import static org.eclipse.dataspacetck.dsp.system.api.message.MessageSerializer.registerValidator;
import static org.eclipse.dataspacetck.dsp.system.api.message.tp.TransferFunctions.createStartRequest;
import static org.eclipse.dataspacetck.dsp.system.api.message.tp.TransferFunctions.dataAddress;

/**
 * Base class for Catena-X end-to-end flow tests. The connector under test acts as the <strong>provider</strong>; the
 * TCK drives the exchange as the consumer. A single DCP self-issued identity token (installed by the
 * {@code CxSystemLauncher} from the method-level {@code @DcpScope}) authorizes every DSP message across catalog,
 * contract negotiation and transfer process.
 */
@Tag("cx-flow")
public abstract class AbstractCxFlowTest extends AbstractVerificationTest {

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

    @ConfigParam
    protected String datasetId = randomUUID().toString();

    @ConfigParam
    protected String format = "HTTP-PULL";

    /**
     * Seeds a dataset into the in-memory catalog for the local self-test. The offer id follows the
     * {@code offer<datasetId>} convention the in-memory {@code TckConnector} uses to map an offer back to its dataset
     * during negotiation ({@code IdGenerator.datasetIdFromOfferId}); against a real connector this seed is unused and the
     * offer published by the connector under test drives the negotiation instead.
     */
    protected static Dataset seedDataset(String datasetId) {
        var offer = new Dataset.Offer(offerIdFromDatasetId(datasetId), List.of(new Dataset.Permission("http://www.w3.org/ns/odrl/2/use")));
        var distribution = new Dataset.Distribution("HttpData", new Dataset.DataService(randomUUID().toString(), "https://example.com"));
        return new Dataset(datasetId, List.of(offer), List.of(distribution));
    }

    /**
     * Provider action that starts a transfer while embedding a data address in the {@code TransferStartMessage} (as a
     * real HTTP-PULL provider would). Used by the local self-test so the data-address extraction has an address to read;
     * against a real connector the connector under test supplies the actual data address instead.
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

    @BeforeAll
    static void setUp() {
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
}
