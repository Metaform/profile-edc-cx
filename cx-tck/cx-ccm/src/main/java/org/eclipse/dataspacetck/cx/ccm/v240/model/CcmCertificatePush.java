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

/**
 * The body of {@code POST /companycertificate/push}: a full certificate delivered inline from a
 * Certificate Provider to a Certificate Consumer.
 *
 * @param header  the message header, carrying the push context
 * @param content the certificate
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CcmCertificatePush(CcmHeader header, BusinessPartnerCertificate31 content) {
}
