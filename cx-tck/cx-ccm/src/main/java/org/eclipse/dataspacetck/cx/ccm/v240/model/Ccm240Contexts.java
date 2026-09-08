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
 * The {@code header.context} values CX-0135 v2.4.0 defines, one per message type.
 */
public final class Ccm240Contexts {

    /** Consumer to provider: request a certificate. */
    public static final String REQUEST = "CompanyCertificateManagement-CCMAPI-Request:1.0.0";

    /** Provider to consumer: deliver a certificate inline. */
    public static final String PUSH = "CompanyCertificateManagement-CCMAPI-Push:1.0.0";

    /** Consumer to provider: report the outcome of consuming a certificate. */
    public static final String STATUS = "CompanyCertificateManagement-CCMAPI-Status:1.0.0";

    /** Provider to consumer: announce that a certificate is available to pull. */
    public static final String AVAILABLE = "CompanyCertificateManagement-CCMAPI-Available:1.0.0";

    private Ccm240Contexts() {
    }
}
