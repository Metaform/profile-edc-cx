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

package org.eclipse.dataspacetck.cx.ccm.v240;

import org.eclipse.dataspacetck.cx.ccm.v240.model.BusinessPartnerCertificate31;
import org.eclipse.dataspacetck.cx.ccm.v240.model.Ccm240Contexts;
import org.eclipse.dataspacetck.cx.ccm.v240.model.CcmCertificatePush;
import org.eclipse.dataspacetck.cx.ccm.v240.model.CcmHeader;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Builds CX-0135 v2.4.0 messages.
 * <p>
 * Every message gets a fresh {@code urn:uuid:} message id and a current timestamp, so successive calls
 * are distinct deliveries rather than retransmissions - a distinction a receiver is entitled to act on,
 * since v2.4.0 has no other way to recognise a duplicate.
 */
public final class CcmMessages {

    /** The semantic model version a v2.4.0 message declares in its header. */
    public static final String SEMANTIC_MODEL_VERSION = "3.1.0";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;

    private CcmMessages() {
    }

    /**
     * Builds a header with a fresh message id and the current time.
     *
     * @param context     the message-type discriminator
     * @param senderBpn   the BPNL of the sender
     * @param receiverBpn the BPNL of the receiver
     * @return the header
     */
    public static CcmHeader header(String context, String senderBpn, String receiverBpn) {
        return header(context, senderBpn, receiverBpn, null, null);
    }

    /**
     * Builds a header with a fresh message id and the current time, correlating it to an earlier message
     * and declaring where feedback is to be sent.
     *
     * @param context           the message-type discriminator
     * @param senderBpn         the BPNL of the sender
     * @param receiverBpn       the BPNL of the receiver
     * @param relatedMessageId  the message this one relates to, or null
     * @param senderFeedbackUrl the DSP endpoint feedback is to be sent to, or null
     * @return the header
     */
    public static CcmHeader header(String context,
                                   String senderBpn,
                                   String receiverBpn,
                                   String relatedMessageId,
                                   String senderFeedbackUrl) {
        return new CcmHeader(context,
                newMessageId(),
                senderBpn,
                receiverBpn,
                TIMESTAMP.format(Instant.now().atOffset(ZoneOffset.UTC)),
                SEMANTIC_MODEL_VERSION,
                relatedMessageId,
                senderFeedbackUrl);
    }

    /**
     * Builds a certificate push.
     *
     * @param senderBpn   the BPNL of the Certificate Provider
     * @param receiverBpn the BPNL of the Certificate Consumer
     * @param certificate the certificate to deliver inline
     * @return the push message
     */
    public static CcmCertificatePush push(String senderBpn, String receiverBpn, BusinessPartnerCertificate31 certificate) {
        return new CcmCertificatePush(header(Ccm240Contexts.PUSH, senderBpn, receiverBpn), certificate);
    }

    /**
     * Builds a certificate push declaring where the consumer is to send its feedback.
     *
     * @param senderBpn         the BPNL of the Certificate Provider
     * @param receiverBpn       the BPNL of the Certificate Consumer
     * @param senderFeedbackUrl the DSP endpoint feedback is to be sent to
     * @param certificate       the certificate to deliver inline
     * @return the push message
     */
    public static CcmCertificatePush push(String senderBpn,
                                          String receiverBpn,
                                          String senderFeedbackUrl,
                                          BusinessPartnerCertificate31 certificate) {
        return new CcmCertificatePush(
                header(Ccm240Contexts.PUSH, senderBpn, receiverBpn, null, senderFeedbackUrl), certificate);
    }

    /**
     * A fresh message id in the {@code urn:uuid:} form the specification's examples use.
     *
     * @return the message id
     */
    public static String newMessageId() {
        return "urn:uuid:" + UUID.randomUUID();
    }
}
