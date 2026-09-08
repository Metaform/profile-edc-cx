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

plugins {
    `java-library`
}

dependencies {
    api(project(":cx-api"))

    // test classes live in src/main so they ship in the jar and are discovered by TckRuntime package scan
    api(libs.tck.common.api)
    api(libs.tck.core)
    api(libs.dsp.api)
    api(libs.dcp.api)

    // schema resources for message validation: catalog + negotiation + transfer JSON schemas
    api(libs.dsp.catalog)
    api(libs.dsp.contract.negotiation)
    api(libs.dsp.transfer.process)
    api(libs.assertj)

    // application payloads are validated against JSON Schema directly rather than through the
    // harness validator, so the helper also works from plain unit tests with no launcher running
    api(libs.schema.validator)

    testImplementation(project(":cx-system"))
    testImplementation(libs.dsp.system)
    testImplementation(libs.tck.runtime)
}
