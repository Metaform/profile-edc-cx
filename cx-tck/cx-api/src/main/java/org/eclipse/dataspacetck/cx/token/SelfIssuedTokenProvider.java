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

package org.eclipse.dataspacetck.cx.token;

import java.util.List;

/**
 * Mints a DCP self-issued identity token bound to the current test's DCP fixtures, so a test can authorize an
 * out-of-band request (e.g. an OAuth2 refresh-token call) with the same Catena-X identity used to authorize the DSP
 * exchange. Provided by the {@code CxSystemLauncher} and injected into flow tests.
 */
@FunctionalInterface
public interface SelfIssuedTokenProvider {

    /**
     * Returns a freshly minted DCP self-issued (ID) token for the given DCP scopes.
     */
    String mintToken(List<String> scopes, String embeddedToken);
}
