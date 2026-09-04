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

package org.eclipse.dataspacetck.cx.ccm.v240.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The {@code urn:samm:io.catenax.business_partner_certificate:3.1.0} aspect model, as it appears on the
 * v2.4.0 wire.
 * <p>
 * Modelled here rather than reused from any implementation, so the TCK's notion of a conformant
 * certificate comes from the published semantic model alone. The authority remains the vendored schema
 * this is validated against; this record exists to build payloads conveniently.
 *
 * @param businessPartnerNumber the BPNL of the certified legal entity
 * @param type                  the certificate type and, optionally, its version
 * @param registrationNumber    the registration number as printed on the certificate
 * @param areaOfApplication     the scope the certificate covers
 * @param enclosedSites         additional sites covered, identified by BPNS or BPNA
 * @param validFrom             the date the certificate becomes valid, {@code yyyy-MM-dd}
 * @param validUntil            the date it expires; {@code 9999-12-31} denotes no expiry
 * @param issuer                the issuing authority
 * @param trustLevel            the assurance level of the validation
 * @param validator             the party that validated the certificate, if any
 * @param uploader              the BPNL of the party that supplied the certificate, if any
 * @param document              the certificate document, inline
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BusinessPartnerCertificate31(String businessPartnerNumber,
                                           Type type,
                                           String registrationNumber,
                                           String areaOfApplication,
                                           List<EnclosedSite> enclosedSites,
                                           String validFrom,
                                           String validUntil,
                                           Issuer issuer,
                                           String trustLevel,
                                           Validator validator,
                                           String uploader,
                                           Document document) {

    /**
     * The certificate type, as printed on the document.
     *
     * @param certificateType    the type code, lowercase and alphanumeric, e.g. {@code iso9001}
     * @param certificateVersion the version of the standard, e.g. {@code 2015}
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Type(String certificateType, String certificateVersion) {
    }

    /**
     * A site or address additionally covered by the certificate.
     *
     * @param enclosedSiteBpn   the BPNS or BPNA of the location
     * @param areaOfApplication the scope the certificate covers at this location
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EnclosedSite(String enclosedSiteBpn, String areaOfApplication) {
    }

    /**
     * The authority that issued the certificate.
     *
     * @param issuerName the name of the issuer
     * @param issuerBpn  the BPNL of the issuer, if it has one
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Issuer(String issuerName, String issuerBpn) {
    }

    /**
     * The party that validated the certificate.
     *
     * @param validatorName the name of the validator
     * @param validatorBpn  the BPNL of the validator, if it has one
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Validator(String validatorName, String validatorBpn) {
    }

    /**
     * The certificate document, carried inline as base64.
     *
     * @param creationDate  the ISO 8601 timestamp the document was created at
     * @param documentId    the document id; note the wire name is {@code documentID}
     * @param contentType   the media type of the content
     * @param contentBase64 the document bytes, base64 encoded
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Document(String creationDate,
                           @JsonProperty("documentID") String documentId,
                           String contentType,
                           String contentBase64) {
    }
}
