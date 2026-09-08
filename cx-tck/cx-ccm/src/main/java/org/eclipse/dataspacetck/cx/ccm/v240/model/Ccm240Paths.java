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

/**
 * The paths CX-0135 v2.4.0 defines, relative to the base URL of the counterparty's notification API.
 * <p>
 * The four endpoints are split across two EDC assets by role: a Certificate Provider offers
 * {@link #REQUEST} and {@link #STATUS}, a Certificate Consumer offers {@link #PUSH} and
 * {@link #AVAILABLE}.
 */
public final class Ccm240Paths {

    /** Offered by a Certificate Provider. */
    public static final String REQUEST = "/companycertificate/request";

    /** Offered by a Certificate Provider. */
    public static final String STATUS = "/companycertificate/status";

    /** Offered by a Certificate Consumer. */
    public static final String PUSH = "/companycertificate/push";

    /** Offered by a Certificate Consumer. */
    public static final String AVAILABLE = "/companycertificate/available";

    private Ccm240Paths() {
    }
}
