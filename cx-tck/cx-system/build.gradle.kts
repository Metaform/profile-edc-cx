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
    api(libs.tck.common.api)
    api(libs.tck.core)

    // DSP exchange protocol: delegate service DI (CatalogClient, Connector, ...) to DspSystemLauncher
    api(libs.dsp.api)
    api(libs.dsp.system)

    // DCP identity: reuse the BaseAssembly / ServiceAssembly fixtures (DID docs, CredentialService, STS)
    api(libs.dcp.api)
    api(libs.dcp.system)
}
