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

import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.ODRL_AND;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.ODRL_CONSTRAINT;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.ODRL_HAS_POLICY;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.ODRL_LEFT_OPERAND;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.ODRL_NAMESPACE;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.ODRL_OPERATOR;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.ODRL_PERMISSION;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.ODRL_RIGHT_OPERAND;

/**
 * Reads the ODRL constraints a catalog offer carries.
 * <p>
 * Catena-X use-case standards mandate specific usage constraints - a framework agreement and a use-case
 * usage purpose - on every offer of the use-case's assets, so checking a dataset's policy is a recurring
 * conformance question rather than a CX-0135 one.
 */
public final class CxPolicies {

    private static final String ODRL_OR = ODRL_NAMESPACE + "or";
    private static final String ODRL_XONE = ODRL_NAMESPACE + "xone";
    private static final String ODRL_PROHIBITION = ODRL_NAMESPACE + "prohibition";
    private static final String ODRL_OBLIGATION = ODRL_NAMESPACE + "obligation";

    private CxPolicies() {
    }

    /**
     * Every constraint carried by every rule of every offer attached to a dataset, flattened.
     * <p>
     * Logical containers ({@code odrl:and}, {@code odrl:or}, {@code odrl:xone}) are descended into and
     * their operands returned alongside directly-stated constraints, because whether a connector emits a
     * lone constraint or wraps a single-element {@code and} around it is a serialization choice, not a
     * difference in what the policy requires.
     *
     * @param dataset an expanded dataset node
     * @return the constraints, in document order, possibly empty
     */
    public static List<Constraint> constraintsOf(Map<String, Object> dataset) {
        var constraints = new ArrayList<Constraint>();
        for (var offer : CxFunctions.nodeValues(dataset, ODRL_HAS_POLICY)) {
            for (var ruleProperty : List.of(ODRL_PERMISSION, ODRL_PROHIBITION, ODRL_OBLIGATION)) {
                for (var rule : CxFunctions.nodeValues(offer, ruleProperty)) {
                    collect(CxFunctions.nodeValues(rule, ODRL_CONSTRAINT), constraints);
                }
            }
        }
        return List.copyOf(constraints);
    }

    /**
     * Whether the dataset's policy states a constraint with the given left operand and right operand.
     *
     * @param dataset      an expanded dataset node
     * @param leftOperand  the expanded left-operand IRI
     * @param rightOperand the expected right operand
     * @return true if such a constraint is present
     */
    public static boolean hasConstraint(Map<String, Object> dataset, String leftOperand, String rightOperand) {
        return constraintsOf(dataset).stream()
                .anyMatch(constraint -> leftOperand.equals(constraint.leftOperand()) &&
                        rightOperand.equals(constraint.rightOperand()));
    }

    private static void collect(List<Map<String, Object>> nodes, List<Constraint> constraints) {
        for (var node : nodes) {
            var nested = false;
            for (var container : List.of(ODRL_AND, ODRL_OR, ODRL_XONE)) {
                var operands = CxFunctions.nodeValues(node, container);
                if (!operands.isEmpty()) {
                    nested = true;
                    collect(operands, constraints);
                }
            }
            if (!nested) {
                var leftOperand = single(CxFunctions.propertyValues(node, ODRL_LEFT_OPERAND));
                if (leftOperand != null) {
                    constraints.add(new Constraint(leftOperand,
                            single(CxFunctions.propertyValues(node, ODRL_OPERATOR)),
                            single(CxFunctions.propertyValues(node, ODRL_RIGHT_OPERAND))));
                }
            }
        }
    }

    private static String single(List<String> values) {
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * One ODRL constraint, as a flattened triple.
     *
     * @param leftOperand  the expanded left-operand IRI
     * @param operator     the expanded operator IRI
     * @param rightOperand the right operand, as a literal or a node reference
     */
    public record Constraint(String leftOperand, String operator, String rightOperand) {
    }
}
