/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.dataspacetck.cx.suite;

import org.eclipse.dataspacetck.core.system.ConsoleMonitor;
import org.eclipse.dataspacetck.cx.system.CxSystemLauncher;
import org.eclipse.dataspacetck.runtime.TckRuntime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dataspacetck.core.api.system.SystemsConstants.TCK_LAUNCHER;

class CxTckSuiteTest {

    @Test
    void verifyTestSuite() {
        var result = TckRuntime.Builder.newInstance()
                .property("dataspacetck.dsp.local.connector", "true")
                .property(TCK_LAUNCHER, CxSystemLauncher.class.getName())
                .addPackage("org.eclipse.dataspacetck.cx.verification")
                .monitor(new ConsoleMonitor(false, true))
                .build().execute();

        assertThat(result.getFailures()).isEmpty();
        // a run that discovered nothing would also report no failures
        assertThat(result.getTestsFoundCount()).as("discovered test cases").isPositive();
    }

    /**
     * Guards the suite's composition. Test cases are discovered by scanning the runtime classpath, so a
     * module missing from either {@code settings.gradle.kts} or the runtime's dependencies contributes
     * nothing at all - the run still passes, just with fewer tests, which is indistinguishable from
     * success in the output. Naming one test class per suite module turns that silent shrinkage into a
     * build failure.
     */
    @Test
    void verifyEverySuiteIsOnTheRuntimeClasspath() {
        var suites = List.of(
                "org.eclipse.dataspacetck.cx.verification.catalog.CxCatalog01Test",
                "org.eclipse.dataspacetck.cx.verification.flow.e2e.CxFlow01Test",
                "org.eclipse.dataspacetck.cx.verification.flow.renewal.CxRenewalFlow01Test",
                "org.eclipse.dataspacetck.cx.verification.ccm.v240.CxCcm00Test",
                "org.eclipse.dataspacetck.cx.verification.ccm.v240.CxCcm01Test",
                "org.eclipse.dataspacetck.cx.verification.ccm.v240.CxCcm02Test",
                "org.eclipse.dataspacetck.cx.verification.ccm.v240.CxCcm03Test");

        for (var suite : suites) {
            assertThatClassIsOnTheClasspath(suite);
        }
    }

    private static void assertThatClassIsOnTheClasspath(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(("%s is not on the runtime classpath, so the package scan will never " +
                    "discover it. Is its module listed in settings.gradle.kts and in the cx-tck runtime " +
                    "dependencies?").formatted(className));
        }
    }
}
