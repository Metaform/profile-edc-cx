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

package org.eclipse.dataspacetck.cx.usecase;

import java.util.Map;

import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractAccessToken;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractEndpoint;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractEndpointProperties;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions.extractEndpointType;

/**
 * An Endpoint Data Reference: everything needed to call an application API through a data plane, as
 * carried in the data address of a provider's {@code TransferStartMessage}.
 * <p>
 * This is the handover point between the dataspace layer and a use-case layer. A Catena-X use-case
 * standard defines an API - the JSON messages exchanged and the responses expected - but says nothing
 * about how a caller reaches it; that is DSP's job, and it ends here, with an endpoint and a token.
 *
 * @param datasetId     the dataset the EDR was obtained for
 * @param agreementId   the contract agreement the transfer was started against
 * @param endpoint      the URL the application API is reached at
 * @param endpointType  the transport the data address declares, if any
 * @param authorization the value to send as the {@code Authorization} header, if the data address carries one
 * @param properties    every endpoint property the data address declares, keyed by name
 * @param dataAddress   the raw expanded data address, for properties this record does not model
 */
public record Edr(String datasetId,
                  String agreementId,
                  String endpoint,
                  String endpointType,
                  String authorization,
                  Map<String, String> properties,
                  Map<String, Object> dataAddress) {

    /**
     * Reads an EDR out of the expanded data address a provider returned.
     *
     * @param dataAddress the expanded {@code dspace:dataAddress} node
     * @param datasetId   the dataset the transfer was started for
     * @param agreementId the agreement the transfer was started against
     * @return the EDR
     */
    public static Edr fromDataAddress(Map<String, Object> dataAddress, String datasetId, String agreementId) {
        return new Edr(datasetId,
                agreementId,
                extractEndpoint(dataAddress),
                extractEndpointType(dataAddress),
                extractAccessToken(dataAddress),
                extractEndpointProperties(dataAddress),
                dataAddress);
    }
}
