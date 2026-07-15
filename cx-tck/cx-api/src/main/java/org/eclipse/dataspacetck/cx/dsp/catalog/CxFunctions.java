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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.eclipse.dataspacetck.dsp.system.api.message.DcatConstants.DCAT_PROPERTY_DATASET_EXPANDED;
import static org.eclipse.dataspacetck.dsp.system.api.message.DspConstants.DSPACE_NAMESPACE;
import static org.eclipse.dataspacetck.dsp.system.api.message.DspConstants.DSPACE_PROPERTY_DATA_ADDRESS_EXPANDED;
import static org.eclipse.dataspacetck.dsp.system.api.message.DspConstants.DSPACE_PROPERTY_ENDPOINT_EXPANDED;
import static org.eclipse.dataspacetck.dsp.system.api.message.DspConstants.DSPACE_PROPERTY_ENDPOINT_PROPERTIES_EXPANDED;
import static org.eclipse.dataspacetck.dsp.system.api.message.DspConstants.DSPACE_PROPERTY_ENDPOINT_PROPERTY_NAME_EXPANDED;
import static org.eclipse.dataspacetck.dsp.system.api.message.DspConstants.DSPACE_PROPERTY_ENDPOINT_PROPERTY_VALUE_EXPANDED;
import static org.eclipse.dataspacetck.dsp.system.api.message.DspConstants.DSPACE_PROPERTY_ENDPOINT_TYPE_EXPANDED;
import static org.eclipse.dataspacetck.dsp.system.api.message.DspConstants.ID;
import static org.eclipse.dataspacetck.dsp.system.api.message.JsonLdFunctions.mapProperty;
import static org.eclipse.dataspacetck.dsp.system.api.message.JsonLdFunctions.stringProperty;

/**
 * Helpers for reading a DSP response (expanded JSON-LD) so a contract negotiation can be driven from a real
 * dataset published by the connector under test rather than a fabricated offer.
 */
public final class CxFunctions {

    // per the DSP 2025-1 JSON-LD context (dsp-2025-1.jsonld), dataset offers are carried under odrl:hasPolicy
    private static final String ODRL_NAMESPACE = "http://www.w3.org/ns/odrl/2/";
    private static final String ODRL_PROPERTY_HAS_POLICY_EXPANDED = ODRL_NAMESPACE + "hasPolicy";
    private static final String DSPACE_PROPERTY_AGREEMENT_EXPANDED = DSPACE_NAMESPACE + "agreement";

    private CxFunctions() {
    }

    /**
     * Returns the expanded dataset map with the given {@code @id} from an expanded catalog response.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> findDataset(Map<String, Object> catalog, String datasetId) {
        var datasets = (List<Map<String, Object>>) catalog.get(DCAT_PROPERTY_DATASET_EXPANDED);
        if (datasets == null) {
            throw new AssertionError("Catalog contained no datasets");
        }
        return datasets.stream()
                .filter(dataset -> datasetId.equals(dataset.get(ID)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Dataset not found in catalog: " + datasetId));
    }

    /**
     * Returns the first offer (expanded {@code odrl:hasPolicy} entry) of the dataset with the given {@code @id}.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractOffer(Map<String, Object> catalog, String datasetId) {
        var dataset = findDataset(catalog, datasetId);
        var offers = (List<Map<String, Object>>) dataset.get(ODRL_PROPERTY_HAS_POLICY_EXPANDED);
        if (offers == null || offers.isEmpty()) {
            throw new AssertionError("Dataset has no offer (odrl:hasPolicy): " + datasetId);
        }
        return offers.get(0);
    }

    /**
     * The {@code @id} of an expanded offer or agreement node.
     */
    public static String idOf(Map<String, Object> node) {
        var id = node.get(ID);
        if (id == null) {
            throw new AssertionError("Node has no @id");
        }
        return id.toString();
    }

    /**
     * Extracts the offer {@code @id} of the dataset's first offer, used as the {@code offerId} in the contract request.
     */
    public static String extractOfferId(Map<String, Object> catalog, String datasetId) {
        return idOf(extractOffer(catalog, datasetId));
    }

    /**
     * Extracts the agreement {@code @id} from an expanded {@code ContractAgreementMessage}, so it can be carried into
     * the subsequent transfer request. Returns {@code null} if the agreement node is absent (e.g. in local self-test
     * mode where the simulated provider does not embed one).
     */
    @SuppressWarnings("unchecked")
    public static String extractAgreementId(Map<String, Object> agreementMessage) {
        var agreement = agreementMessage.get(DSPACE_PROPERTY_AGREEMENT_EXPANDED);
        if (agreement instanceof List<?> list && !list.isEmpty()) {
            agreement = list.get(0);
        }
        if (agreement instanceof Map<?, ?> map) {
            var id = ((Map<String, Object>) map).get(ID);
            return id != null ? id.toString() : null;
        }
        return null;
    }

    /**
     * Extracts the expanded {@code dspace:dataAddress} node from a {@code TransferStartMessage}. For an
     * {@code HTTP-PULL} transfer this carries the endpoint the consumer pulls data from and any transport properties
     * (e.g. an authorization token). Returns {@code null} if the start message carries no data address (e.g. a provider
     * that delivers it out of band).
     */
    public static Map<String, Object> extractDataAddress(Map<String, Object> startMessage) {
        return mapProperty(DSPACE_PROPERTY_DATA_ADDRESS_EXPANDED, startMessage, true);
    }

    /**
     * The endpoint URL of an expanded data address.
     */
    public static String extractEndpoint(Map<String, Object> dataAddress) {
        var endpoint = stringProperty(DSPACE_PROPERTY_ENDPOINT_EXPANDED, dataAddress, true);

        // compatibility with old EDC connectors
        if (endpoint == null) {
            var properties = extractEndpointProperties(dataAddress);
            endpoint = Optional.ofNullable(properties.get("https://w3id.org/edc/v0.0.1/ns/endpoint"))
                    .orElseGet(() -> properties.get("endpoint"));
        }
        return endpoint;
    }

    /**
     * The endpoint type (transport) of an expanded data address.
     */
    public static String extractEndpointType(Map<String, Object> dataAddress) {
        return stringProperty(DSPACE_PROPERTY_ENDPOINT_TYPE_EXPANDED, dataAddress);
    }

    /**
     * The endpoint properties (name/value transport parameters, e.g. an authorization token) of an expanded data
     * address, keyed by property name. Empty if the data address declares none.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> extractEndpointProperties(Map<String, Object> dataAddress) {
        var result = new LinkedHashMap<String, String>();
        var properties = dataAddress.get(DSPACE_PROPERTY_ENDPOINT_PROPERTIES_EXPANDED);
        if (properties instanceof List<?> list) {
            for (var property : list) {
                if (property instanceof Map<?, ?> propertyMap) {
                    var entry = (Map<String, Object>) propertyMap;
                    result.put(stringProperty(DSPACE_PROPERTY_ENDPOINT_PROPERTY_NAME_EXPANDED, entry),
                            stringProperty(DSPACE_PROPERTY_ENDPOINT_PROPERTY_VALUE_EXPANDED, entry));
                }
            }
        }
        return result;
    }
}
