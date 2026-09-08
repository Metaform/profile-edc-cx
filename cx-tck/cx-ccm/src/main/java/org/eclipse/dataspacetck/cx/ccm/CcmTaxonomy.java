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

package org.eclipse.dataspacetck.cx.ccm;

import org.eclipse.dataspacetck.cx.dsp.catalog.DatasetQuery;

import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.CX_COMMON_VERSION;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.DCT_SUBJECT;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.DCT_TYPE;
import static org.eclipse.dataspacetck.cx.dsp.catalog.CxTaxonomy.cxTaxo;

/**
 * The Catena-X taxonomy terms and policy constants CX-0135 assigns to Company Certificate Management
 * assets and offers.
 * <p>
 * Version-independent: the asset classification and the usage purpose are the same across CCM protocol
 * versions, so both the 2.4.0 suite and any later one describe their offers with these.
 */
public final class CcmTaxonomy {

    /** {@code cx-taxo:CCMAPI} - the {@code dct:type} of a Company Certificate Management API asset. */
    public static final String CCM_API_TYPE = cxTaxo("CCMAPI");

    /**
     * {@code cx-taxo:CompanyCertificateManagementNotificationApi} - the {@code dct:subject} of the
     * notification API asset, per CX-0135 section 2.1.4.1.
     */
    public static final String NOTIFICATION_API_SUBJECT = cxTaxo("CompanyCertificateManagementNotificationApi");

    /** {@code cx-taxo:CompanyCertificate} - the {@code dct:subject} of a certificate asset. */
    public static final String COMPANY_CERTIFICATE_SUBJECT = cxTaxo("CompanyCertificate");

    /**
     * The usage purpose every CCM offer must carry, per CX-0135 section 2.1.7. This is the value most
     * likely to be wrong in a deployment: {@code cx.pcf.base:1} and other use-case purposes are easy to
     * copy across by mistake.
     */
    public static final String CCM_USAGE_PURPOSE = "cx.ccm.base:1";

    /** The framework agreement every CCM offer must additionally require, per CX-0135 section 2.1.7. */
    public static final String FRAMEWORK_AGREEMENT = "DataExchangeGovernance:1.0";

    private CcmTaxonomy() {
    }

    /**
     * Describes the notification API asset a Certificate Consumer must publish so a Certificate Provider
     * can deliver to it: the classification of CX-0135 section 2.1.4.1, with any declared version.
     *
     * @return the query
     */
    public static DatasetQuery notificationApiAsset() {
        return DatasetQuery.create()
                .id(DCT_TYPE, CCM_API_TYPE)
                .id(DCT_SUBJECT, NOTIFICATION_API_SUBJECT)
                .any(CX_COMMON_VERSION);
    }
}
