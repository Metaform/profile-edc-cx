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

package org.eclipse.dataspacetck.cx.verification.ccm.v240;

import org.eclipse.dataspacetck.cx.ccm.CcmTaxonomy;
import org.eclipse.dataspacetck.cx.dsp.catalog.CxFunctions;
import org.eclipse.dataspacetck.cx.dsp.catalog.client.CxDspCatalogClient;
import org.eclipse.dataspacetck.cx.verification.usecase.AbstractCxUseCaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dataspacetck.core.system.ConfigFunctions.propertyOrEnv;
import static org.eclipse.dataspacetck.cx.dcp.profile.CxProfile.CX_TCK_PREFIX;
import static org.eclipse.dataspacetck.dsp.system.api.message.DcatConstants.DCAT_PROPERTY_DATASET_EXPANDED;
import static org.eclipse.dataspacetck.dsp.system.api.message.catalog.CatalogFunctions.createCatalogRequest;

/**
 * Base class for the CX-0135 v2.4.0 verification suites.
 * <p>
 * In these tests the TCK plays the <strong>Certificate Provider</strong> and the system under test plays
 * the <strong>Certificate Consumer</strong>. That is the direction the harness already supports: the
 * consumer-facing endpoints ({@code /companycertificate/push} and {@code /companycertificate/available})
 * are offered by the system under test behind its own notification API asset, so the TCK discovers that
 * asset, negotiates for it, and delivers through the resulting EDR - driving every step itself.
 * <p>
 * Run-wide values are read with {@code propertyOrEnv} rather than declared as {@code @ConfigParam}
 * fields, because a {@code @ConfigParam} key is derived from the test <em>method</em> name; the system
 * under test's BPN would otherwise have to be repeated once per test.
 */
@Tag("cx-ccm")
@Tag("cx-ccm-v240")
public abstract class AbstractCcm240Test extends AbstractCxUseCaseTest {

    /**
     * The BPNL of the system under test, sent as {@code header.receiverBpn}.
     */
    protected static final String RECEIVER_BPN_KEY = CX_TCK_PREFIX + ".ccm.receiver.bpn";

    /**
     * The BPNL the TCK presents, sent as {@code header.senderBpn}; shared with the DCP identity.
     */
    protected static final String TCK_BPN_KEY = CX_TCK_PREFIX + ".bpn";

    /**
     * The BPNL the TCK sends as {@code header.senderBpn}.
     *
     * @return the TCK's BPN
     */
    protected static String senderBpn() {
        return propertyOrEnv(TCK_BPN_KEY, "BPNL0000000000TC");
    }

    /**
     * The BPNL the TCK sends as {@code header.receiverBpn}, i.e. the system under test's.
     *
     * @return the receiver's BPN
     */
    protected static String receiverBpn() {
        return propertyOrEnv(RECEIVER_BPN_KEY, "BPNL0000000000CU");
    }

    /**
     * Aborts every CCM verification unless a real connector is under test. The in-memory connector of the
     * local self-test publishes no CCM asset and serves no notification API, so there is nothing to
     * verify against it.
     */
    @BeforeEach
    void requireRealConnector() {
        assumeRealConnector();
    }

    /**
     * Fetches the catalog of the connector under test.
     *
     * @param catalogClient the catalog client bound to this test's DCP identity
     * @return the expanded catalog response
     */
    protected Map<String, Object> fetchCatalog(CxDspCatalogClient catalogClient) {
        var catalog = catalogClient.getCatalog(createCatalogRequest());
        assertThat(catalog.get(DCAT_PROPERTY_DATASET_EXPANDED))
                .as("the catalog carried no datasets at all, so no CCM asset can be discovered")
                .isNotNull();
        return catalog;
    }

    /**
     * Finds the notification API asset the system under test must publish for a Certificate Provider to
     * deliver to it, per CX-0135 section 2.1.4.1.
     *
     * @param catalog an expanded catalog response
     * @return the notification API dataset
     */
    protected Map<String, Object> findNotificationApiAsset(Map<String, Object> catalog) {
        return CxFunctions.findSingleDataset(catalog, CcmTaxonomy.notificationApiAsset());
    }
}
