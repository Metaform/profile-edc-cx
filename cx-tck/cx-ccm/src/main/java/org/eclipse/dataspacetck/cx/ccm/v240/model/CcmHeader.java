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
 * The header every CX-0135 v2.4.0 message carries.
 * <p>
 * {@code context} discriminates the message type and {@code version} names the semantic model version of
 * the content - it is <em>not</em> the CCM protocol version, which the wire never states.
 *
 * @param context           the message-type discriminator, one of {@link Ccm240Contexts}
 * @param messageId         a UUID uniquely identifying this message, optionally {@code urn:uuid:} prefixed
 * @param senderBpn         the BPNL of the sending party
 * @param receiverBpn       the BPNL of the receiving party
 * @param sentDateTime      the ISO 8601 timestamp at which the message was sent
 * @param version           the semantic model version of the content, e.g. {@code 3.1.0}
 * @param relatedMessageId  the id of a message this one relates to, if any
 * @param senderFeedbackUrl the DSP endpoint the receiver is to send feedback to, if any
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CcmHeader(String context,
                        String messageId,
                        String senderBpn,
                        String receiverBpn,
                        String sentDateTime,
                        String version,
                        String relatedMessageId,
                        String senderFeedbackUrl) {
}
