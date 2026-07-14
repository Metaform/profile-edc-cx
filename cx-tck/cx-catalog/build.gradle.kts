/*
 *  Copyright (c) 2025 Metaform Systems, Inc.
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

plugins {
    `java-library`
}

dependencies {
    api(project(":cx-api"))

    // test classes live in src/main so they ship in the jar and are discovered by TckRuntime package scan
    implementation(libs.tck.common.api)
    implementation(libs.tck.core)
    implementation(libs.dsp.api)
    implementation(libs.dcp.api)

    // dsp-catalog ships the DSP catalog JSON schema resources (/catalog/*.json) used for message validation
    implementation(libs.dsp.catalog)
    implementation(libs.assertj)

    testImplementation(project(":cx-system"))
    testImplementation(libs.dsp.system)
    testImplementation(libs.tck.runtime)
}
