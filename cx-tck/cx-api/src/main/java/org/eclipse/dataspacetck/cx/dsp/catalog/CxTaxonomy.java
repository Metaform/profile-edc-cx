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

/**
 * Namespaces and property IRIs used to describe Catena-X assets in a catalog.
 * <p>
 * Catena-X use-case standards identify an offer by the <em>properties</em> an asset carries - its
 * {@code dct:type}, {@code dct:subject} and {@code cx-common:version} - rather than by a dataset id
 * agreed out of band. A conformance test therefore has to discover datasets by matching these IRIs
 * against an expanded catalog; see {@link DatasetQuery}.
 * <p>
 * The {@code cx-taxo} and {@code cx-common} namespaces are those published in the Tractus-X EDC
 * management-API asset documentation; {@code dct} is Dublin Core Terms.
 */
public final class CxTaxonomy {

    /** Dublin Core Terms, the namespace of {@code dct:type} and {@code dct:subject}. */
    public static final String DCT_NAMESPACE = "http://purl.org/dc/terms/";

    /** The Catena-X taxonomy, the namespace of asset-classification terms such as {@code cx-taxo:CCMAPI}. */
    public static final String CX_TAXO_NAMESPACE = "https://w3id.org/catenax/taxonomy#";

    /** The Catena-X common ontology, the namespace of {@code cx-common:version}. */
    public static final String CX_COMMON_NAMESPACE = "https://w3id.org/catenax/ontology/common#";

    /** The Catena-X policy profile, the namespace ODRL left operands are expected to be expanded into. */
    public static final String CX_POLICY_NAMESPACE = "https://w3id.org/catenax/2025/9/policy/";

    /** ODRL, the namespace of the policy vocabulary carried on a catalog offer. */
    public static final String ODRL_NAMESPACE = "http://www.w3.org/ns/odrl/2/";

    /** {@code dct:type} - what kind of thing the asset is (an API, a submodel, ...). */
    public static final String DCT_TYPE = DCT_NAMESPACE + "type";

    /** {@code dct:subject} - what the asset is about, i.e. which use-case API it fronts. */
    public static final String DCT_SUBJECT = DCT_NAMESPACE + "subject";

    /** {@code cx-common:version} - the use-case API version the asset offers. */
    public static final String CX_COMMON_VERSION = CX_COMMON_NAMESPACE + "version";

    /** {@code odrl:hasPolicy} - the offers attached to a dataset. */
    public static final String ODRL_HAS_POLICY = ODRL_NAMESPACE + "hasPolicy";

    /** {@code odrl:permission} - the permission rules of an offer. */
    public static final String ODRL_PERMISSION = ODRL_NAMESPACE + "permission";

    /** {@code odrl:constraint} - the constraints of a permission. */
    public static final String ODRL_CONSTRAINT = ODRL_NAMESPACE + "constraint";

    /** {@code odrl:leftOperand} - the operand a constraint evaluates, e.g. {@code UsagePurpose}. */
    public static final String ODRL_LEFT_OPERAND = ODRL_NAMESPACE + "leftOperand";

    /** {@code odrl:operator} - the comparison a constraint applies, e.g. {@code odrl:eq}. */
    public static final String ODRL_OPERATOR = ODRL_NAMESPACE + "operator";

    /** {@code odrl:rightOperand} - the value a constraint compares against. */
    public static final String ODRL_RIGHT_OPERAND = ODRL_NAMESPACE + "rightOperand";

    /** {@code odrl:and} - the conjunction wrapping a multi-constraint permission. */
    public static final String ODRL_AND = ODRL_NAMESPACE + "and";

    private CxTaxonomy() {
    }

    /**
     * Expands a {@code cx-taxo:} term into its full IRI, e.g. {@code cxTaxo("CCMAPI")}.
     *
     * @param term the taxonomy term, without the prefix
     * @return the expanded IRI
     */
    public static String cxTaxo(String term) {
        return CX_TAXO_NAMESPACE + term;
    }

    /**
     * Expands a {@code cx-policy:} term into its full IRI, e.g. {@code cxPolicy("UsagePurpose")}.
     *
     * @param term the policy term, without the prefix
     * @return the expanded IRI
     */
    public static String cxPolicy(String term) {
        return CX_POLICY_NAMESPACE + term;
    }

    /**
     * Expands an {@code odrl:} term into its full IRI, e.g. {@code odrl("eq")}.
     *
     * @param term the ODRL term, without the prefix
     * @return the expanded IRI
     */
    public static String odrl(String term) {
        return ODRL_NAMESPACE + term;
    }
}
