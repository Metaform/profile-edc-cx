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

package org.eclipse.dataspacetck.cx.runtime;

/**
 * Exposes runtime facts about the current TCK run to the test cases. The composing {@code CxSystemLauncher} populates
 * this once at startup; tests read it (e.g. to skip cases that are only meaningful against a real connector).
 */
public final class CxRuntime {

    private static volatile boolean localConnector;

    private CxRuntime() {
    }

    /**
     * Whether the suite is running against the in-memory local connector ({@code dataspacetck.dsp.local.connector=true})
     * rather than a real connector under test.
     */
    public static boolean isLocalConnector() {
        return localConnector;
    }

    public static void setLocalConnector(boolean value) {
        localConnector = value;
    }
}
