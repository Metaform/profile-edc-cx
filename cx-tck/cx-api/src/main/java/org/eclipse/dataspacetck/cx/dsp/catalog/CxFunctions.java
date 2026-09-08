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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import static org.eclipse.dataspacetck.dsp.system.api.message.DspConstants.VALUE;
import static org.eclipse.dataspacetck.dsp.system.api.message.JsonLdFunctions.mapProperty;
import static org.eclipse.dataspacetck.dsp.system.api.message.JsonLdFunctions.stringIdProperty;
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
    // JSON-LD keyword: EDC expands an array-valued asset property to a single node wrapping an "@list"
    private static final String LIST = "@list";

    // Token Renewal profile endpoint-property names (dspace:name values carried in the data address)
    private static final String REFRESH_ENDPOINT = "refreshEndpoint";
    private static final String REFRESH_TOKEN = "refreshToken";
    private static final String ACCESS_TOKEN = "authorization";

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
        return stringIdProperty(DSPACE_PROPERTY_ENDPOINT_TYPE_EXPANDED, dataAddress);
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

    /**
     * The Token Renewal profile {@code refreshEndpoint} of an expanded data address (the OAuth2 token endpoint the
     * consumer calls to renew its access token), or {@code null} if the data address declares none.
     */
    public static String extractRefreshEndpoint(Map<String, Object> dataAddress) {
        return matchProperty(dataAddress, REFRESH_ENDPOINT);
    }

    /**
     * The Token Renewal profile {@code refreshToken} of an expanded data address (the OAuth2 refresh token presented in
     * the renewal request), or {@code null} if the data address declares none.
     */
    public static String extractRefreshToken(Map<String, Object> dataAddress) {
        return matchProperty(dataAddress, REFRESH_TOKEN);
    }

    /**
     * The Token Renewal profile {@code refreshToken} of an expanded data address (the OAuth2 refresh token presented in
     * the renewal request), or {@code null} if the data address declares none.
     */
    public static String extractAccessToken(Map<String, Object> dataAddress) {
        return matchProperty(dataAddress, ACCESS_TOKEN);
    }

    /**
     * Reads an endpoint property by name, tolerating a namespaced key: a connector under test may emit the property name
     * verbatim ({@code refreshEndpoint}) or namespaced (e.g. {@code https://w3id.org/edc/v0.0.1/ns/refreshEndpoint}).
     */
    private static String matchProperty(Map<String, Object> dataAddress, String name) {
        return extractEndpointProperties(dataAddress).entrySet().stream()
                .filter(entry -> entry.getKey() != null && (entry.getKey().equals(name) ||
                        entry.getKey().endsWith("/" + name) ||
                        entry.getKey().endsWith("#" + name)))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Every dataset of an expanded catalog response, or an empty list when the catalog declares none.
     * <p>
     * Unlike {@link #findDataset}, this never throws: a catalog with no datasets is a legitimate result
     * (a participant may simply publish nothing), so the decision to fail belongs to the test.
     *
     * @param catalog an expanded catalog response
     * @return the dataset nodes, possibly empty
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> datasets(Map<String, Object> catalog) {
        var datasets = catalog.get(DCAT_PROPERTY_DATASET_EXPANDED);
        if (datasets instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(entry -> (Map<String, Object>) entry)
                    .toList();
        }
        if (datasets instanceof Map<?, ?> map) {
            // a catalog carrying exactly one dataset may not be wrapped in an array
            return List.of((Map<String, Object>) map);
        }
        return List.of();
    }

    /**
     * Every dataset matching the query, in catalog order.
     *
     * @param catalog an expanded catalog response
     * @param query   the properties the dataset must carry
     * @return the matching dataset nodes, possibly empty
     */
    public static List<Map<String, Object>> findDatasets(Map<String, Object> catalog, DatasetQuery query) {
        return datasets(catalog).stream().filter(query::matches).toList();
    }

    /**
     * The first dataset matching the query.
     *
     * @param catalog an expanded catalog response
     * @param query   the properties the dataset must carry
     * @return the matching dataset node, or empty if none matches
     */
    public static Optional<Map<String, Object>> findFirstDataset(Map<String, Object> catalog, DatasetQuery query) {
        return findDatasets(catalog, query).stream().findFirst();
    }

    /**
     * The single dataset matching the query, asserting that exactly one does.
     * <p>
     * When nothing matches, the failure message lists each candidate dataset with the clauses it failed, so
     * a near-miss (for example an asset carrying the right {@code dct:type} but the wrong version) is
     * diagnosable from the message alone rather than requiring a catalog dump.
     *
     * @param catalog an expanded catalog response
     * @param query   the properties the dataset must carry
     * @return the matching dataset node
     */
    public static Map<String, Object> findSingleDataset(Map<String, Object> catalog, DatasetQuery query) {
        var matches = findDatasets(catalog, query);
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.isEmpty()) {
            throw new AssertionError("No dataset matched [%s]. Candidates:%s"
                    .formatted(query, describeCandidates(catalog, query)));
        }
        throw new AssertionError("Expected exactly one dataset matching [%s] but found %d: %s"
                .formatted(query, matches.size(), matches.stream().map(dataset -> dataset.get(ID)).toList()));
    }

    /**
     * The {@code @id} values a property carries, i.e. its node references. Empty if the property is absent
     * or carries only literals.
     *
     * @param node        an expanded node
     * @param propertyIri the expanded property IRI
     * @return the node-reference values
     */
    public static List<String> idValues(Map<String, Object> node, String propertyIri) {
        return valuesOf(node, propertyIri, ID);
    }

    /**
     * The {@code @value} values a property carries, i.e. its literals. Empty if the property is absent or
     * carries only node references.
     *
     * @param node        an expanded node
     * @param propertyIri the expanded property IRI
     * @return the literal values
     */
    public static List<String> literalValues(Map<String, Object> node, String propertyIri) {
        return valuesOf(node, propertyIri, VALUE);
    }

    /**
     * Every value a property carries, whether expressed as a node reference or as a literal.
     * <p>
     * Connectors are not consistent about which shape they emit for the same Catena-X asset property, so a
     * conformance check that cares about the value rather than its encoding should use this.
     *
     * @param node        an expanded node
     * @param propertyIri the expanded property IRI
     * @return the values, in document order
     */
    public static List<String> propertyValues(Map<String, Object> node, String propertyIri) {
        return valuesOf(node, propertyIri, null);
    }

    /**
     * The raw entries a property carries, with any {@code @list} wrapper unwrapped and single values
     * normalised to a list. Use this to reach into structured values such as an ODRL constraint.
     *
     * @param node        an expanded node
     * @param propertyIri the expanded property IRI
     * @return the property entries, possibly empty
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> nodeValues(Map<String, Object> node, String propertyIri) {
        return flatten(node.get(propertyIri)).stream()
                .filter(Map.class::isInstance)
                .map(entry -> (Map<String, Object>) entry)
                .toList();
    }

    /**
     * Reads a property's values, optionally restricted to one JSON-LD keyword.
     *
     * @param keyword {@code @id} or {@code @value} to restrict to that shape, or null for both
     */
    private static List<String> valuesOf(Map<String, Object> node, String propertyIri, String keyword) {
        var values = new ArrayList<String>();
        for (var entry : flatten(node.get(propertyIri))) {
            if (entry instanceof Map<?, ?> map) {
                if (keyword != null) {
                    addIfPresent(values, map.get(keyword));
                } else {
                    // prefer @id, fall back to @value, so each entry contributes at most one value
                    var id = map.get(ID);
                    addIfPresent(values, id != null ? id : map.get(VALUE));
                }
            } else if (keyword == null || VALUE.equals(keyword)) {
                // a partially-compacted response may carry a bare scalar where expansion would have
                // produced a {"@value": ...} node
                addIfPresent(values, entry);
            }
        }
        return List.copyOf(values);
    }

    private static void addIfPresent(List<String> values, Object candidate) {
        if (candidate != null) {
            values.add(candidate.toString());
        }
    }

    /**
     * Normalises a property value into a flat list of entries.
     * <p>
     * Expansion produces an array per property, but an array-valued asset property (such as a list of
     * sites) is expanded to a single node wrapping an {@code @list}. Without unwrapping that, matching
     * against such a property silently finds nothing - which reads as a missing property on the system
     * under test rather than as a bug here. Bare scalars and bare maps are tolerated too, for connectors
     * that return partially-compacted output.
     */
    private static List<Object> flatten(Object value) {
        if (value == null) {
            return List.of();
        }
        var flattened = new ArrayList<Object>();
        if (value instanceof List<?> list) {
            list.forEach(entry -> flattened.addAll(flatten(entry)));
        } else if (value instanceof Map<?, ?> map && map.containsKey(LIST)) {
            flattened.addAll(flatten(map.get(LIST)));
        } else {
            flattened.add(value);
        }
        return flattened;
    }

    private static String describeCandidates(Map<String, Object> catalog, DatasetQuery query) {
        var candidates = datasets(catalog);
        if (candidates.isEmpty()) {
            return " the catalog contained no datasets at all";
        }
        return candidates.stream()
                .map(dataset -> "%n  - %s failed: %s".formatted(dataset.get(ID), query.unmatchedClauses(dataset)))
                .reduce("", String::concat);
    }
}
