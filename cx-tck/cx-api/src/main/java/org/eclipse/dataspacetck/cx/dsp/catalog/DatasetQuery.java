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
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A conjunction of property predicates evaluated against an expanded catalog dataset node.
 * <p>
 * Catena-X use-case standards identify an asset by the properties it carries, not by a dataset id, so a
 * conformance test discovers the offer it needs by describing it:
 * <pre>{@code
 * var query = DatasetQuery.create()
 *         .id(DCT_TYPE, cxTaxo("CCMAPI"))
 *         .id(DCT_SUBJECT, cxTaxo("CompanyCertificateManagementNotificationApi"))
 *         .any(CX_COMMON_VERSION);
 * }</pre>
 * <p>
 * All clauses must hold. {@link #id} and {@link #value} are kept distinct because expanded JSON-LD
 * distinguishes a node reference ({@code {"@id": ...}}) from a literal ({@code {"@value": ...}}), and
 * Catena-X asset properties use both: {@code dct:type} is a node reference while
 * {@code cx-common:version} is a literal. {@link #property} accepts either, for connectors that are
 * inconsistent about which they emit.
 * <p>
 * Instances are immutable; each builder method returns a new query.
 */
public final class DatasetQuery {

    private final List<Clause> clauses;

    private DatasetQuery(List<Clause> clauses) {
        this.clauses = clauses;
    }

    /**
     * Creates an empty query, which matches every dataset.
     *
     * @return a new query
     */
    public static DatasetQuery create() {
        return new DatasetQuery(List.of());
    }

    /**
     * Requires the property to carry a node reference ({@code "@id"}) with the given value.
     *
     * @param propertyIri the expanded property IRI
     * @param expected    the expected {@code @id}
     * @return a new query with the clause added
     */
    public DatasetQuery id(String propertyIri, String expected) {
        return with(new Clause("%s has @id '%s'".formatted(propertyIri, expected),
                node -> CxFunctions.idValues(node, propertyIri).contains(expected)));
    }

    /**
     * Requires the property to carry a literal ({@code "@value"}) equal to the given value.
     *
     * @param propertyIri the expanded property IRI
     * @param expected    the expected literal
     * @return a new query with the clause added
     */
    public DatasetQuery value(String propertyIri, String expected) {
        return with(new Clause("%s has @value '%s'".formatted(propertyIri, expected),
                node -> CxFunctions.literalValues(node, propertyIri).contains(expected)));
    }

    /**
     * Requires the property to carry the given value, as either a node reference or a literal. Use this
     * when the connector under test may legitimately emit either shape.
     *
     * @param propertyIri the expanded property IRI
     * @param expected    the expected value
     * @return a new query with the clause added
     */
    public DatasetQuery property(String propertyIri, String expected) {
        return with(new Clause("%s has '%s'".formatted(propertyIri, expected),
                node -> CxFunctions.propertyValues(node, propertyIri).contains(expected)));
    }

    /**
     * Requires the property to be present with at least one non-null value, whatever it is.
     *
     * @param propertyIri the expanded property IRI
     * @return a new query with the clause added
     */
    public DatasetQuery any(String propertyIri) {
        return with(new Clause("%s is present".formatted(propertyIri),
                node -> !CxFunctions.propertyValues(node, propertyIri).isEmpty()));
    }

    /**
     * Requires the property to carry every one of the given values, in any order.
     *
     * @param propertyIri the expanded property IRI
     * @param expected    the values that must all be present
     * @return a new query with the clause added
     */
    public DatasetQuery containsAll(String propertyIri, List<String> expected) {
        return with(new Clause("%s contains all of %s".formatted(propertyIri, expected),
                node -> CxFunctions.propertyValues(node, propertyIri).containsAll(expected)));
    }

    /**
     * Requires at least one of the property's values to satisfy the given predicate.
     *
     * @param propertyIri the expanded property IRI
     * @param description a human-readable description of the predicate, used in failure messages
     * @param predicate   the test applied to each value
     * @return a new query with the clause added
     */
    public DatasetQuery matching(String propertyIri, String description, Predicate<String> predicate) {
        return with(new Clause("%s %s".formatted(propertyIri, description),
                node -> CxFunctions.propertyValues(node, propertyIri).stream().anyMatch(predicate)));
    }

    /**
     * Evaluates every clause against the dataset node.
     *
     * @param dataset an expanded dataset node
     * @return true if all clauses hold
     */
    public boolean matches(Map<String, Object> dataset) {
        return clauses.stream().allMatch(clause -> clause.predicate().test(dataset));
    }

    /**
     * The clauses that did <em>not</em> hold for the given dataset, as human-readable descriptions. Used to
     * explain why an expected dataset was not matched.
     *
     * @param dataset an expanded dataset node
     * @return the descriptions of the failing clauses, empty if the dataset matches
     */
    public List<String> unmatchedClauses(Map<String, Object> dataset) {
        return clauses.stream()
                .filter(clause -> !clause.predicate().test(dataset))
                .map(Clause::description)
                .toList();
    }

    @Override
    public String toString() {
        return clauses.isEmpty() ? "any dataset" : String.join(" and ", clauses.stream().map(Clause::description).toList());
    }

    private DatasetQuery with(Clause clause) {
        var combined = new ArrayList<>(clauses);
        combined.add(clause);
        return new DatasetQuery(List.copyOf(combined));
    }

    private record Clause(String description, Predicate<Map<String, Object>> predicate) {
    }
}
