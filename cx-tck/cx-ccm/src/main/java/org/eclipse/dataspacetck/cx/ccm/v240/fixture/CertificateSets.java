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

package org.eclipse.dataspacetck.cx.ccm.v240.fixture;

import org.eclipse.dataspacetck.cx.ccm.v240.model.BusinessPartnerCertificate31;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The certificates the TCK delivers, as {@code BusinessPartnerCertificate} 3.1.0 documents on the
 * classpath.
 * <p>
 * The official CX-0135 test cases describe their data as numbered "sets" but do not publish them, so
 * these are the TCK's own, chosen to cover the dimensions those cases distinguish: an ordinary
 * certificate, one whose sites mix BPNS and BPNA, an expired one, and one carrying the
 * {@code 9999-12-31} never-expires sentinel.
 * <p>
 * Because the TCK acts as the Certificate Provider, these are what it <em>sends</em>. Nothing has to be
 * seeded into the system under test for the push tests to run.
 */
public final class CertificateSets {

    private static final String FIXTURES = "/ccm/v240/fixtures/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CertificateSets() {
    }

    /**
     * Set 1: an ordinary, currently valid ISO 9001 certificate with one enclosed site.
     *
     * @return the certificate
     */
    public static BusinessPartnerCertificate31 set1Iso9001() {
        return load("set1-iso9001.json");
    }

    /**
     * Set 3: sites mixing one BPNS with two BPNA, the combination 3.1.0 introduced.
     *
     * @return the certificate
     */
    public static BusinessPartnerCertificate31 set3MixedSites() {
        return load("set3-mixed-sites.json");
    }

    /**
     * Set 4: a certificate whose {@code validUntil} is in the past.
     *
     * @return the certificate
     */
    public static BusinessPartnerCertificate31 set4Expired() {
        return load("set4-expired.json");
    }

    /**
     * Set 5: a certificate carrying the {@code 9999-12-31} never-expires sentinel.
     *
     * @return the certificate
     */
    public static BusinessPartnerCertificate31 set5NoExpiry() {
        return load("set5-no-expiry.json");
    }

    /**
     * Reads a fixture as raw JSON.
     *
     * @param name the file name under the fixtures directory
     * @return the file contents
     */
    public static String raw(String name) {
        try (var stream = CertificateSets.class.getResourceAsStream(FIXTURES + name)) {
            if (stream == null) {
                throw new IllegalStateException("Missing certificate fixture: " + FIXTURES + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BusinessPartnerCertificate31 load(String name) {
        return MAPPER.readValue(raw(name), BusinessPartnerCertificate31.class);
    }
}
