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
import org.eclipse.dataspacetck.cx.verification.usecase.AbstractCxUseCaseTest;
import org.eclipse.dataspacetck.dsp.system.api.connector.catalog.Dataset;
import org.junit.jupiter.api.Tag;

import java.util.List;

import static java.util.UUID.randomUUID;
import static org.eclipse.dataspacetck.dsp.system.api.connector.IdGenerator.offerIdFromDatasetId;

/**
 * Base class for Catena-X end-to-end flow tests. The connector under test acts as the <strong>provider</strong>; the
 * TCK drives the exchange as the consumer. A single DCP self-issued identity token (installed by the
 * {@code CxSystemLauncher} from the method-level {@code @DcpScope}) authorizes every DSP message across catalog,
 * contract negotiation and transfer process.
 * <p>
 * The injected DSP pipelines, the DSP message-schema validators and the catalog-to-transfer exchange itself are
 * inherited from {@link AbstractCxUseCaseTest}, which the use-case suites share.
 */
@Tag("cx-flow")
public abstract class AbstractCxFlowTest extends AbstractCxUseCaseTest {

    @ConfigParam
    protected String datasetId = randomUUID().toString();

    @ConfigParam
    protected String format = "HTTP-PULL";

    /**
     * Seeds a dataset into the in-memory catalog for the local self-test. The offer id follows the
     * {@code offer<datasetId>} convention the in-memory {@code TckConnector} uses to map an offer back to its dataset
     * during negotiation ({@code IdGenerator.datasetIdFromOfferId}); against a real connector this seed is unused and the
     * offer published by the connector under test drives the negotiation instead.
     *
     * @param datasetId the dataset id to seed
     * @return the seeded dataset
     */
    protected static Dataset seedDataset(String datasetId) {
        var offer = new Dataset.Offer(offerIdFromDatasetId(datasetId), List.of(new Dataset.Permission("http://www.w3.org/ns/odrl/2/use")));
        var distribution = new Dataset.Distribution("HttpData", new Dataset.DataService(randomUUID().toString(), "https://example.com"));
        return new Dataset(datasetId, List.of(offer), List.of(distribution));
    }
}
