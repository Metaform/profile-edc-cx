import org.eclipse.dataspacetck.gradle.tckbuild.extensions.TckBuildExtension

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
    alias(libs.plugins.docker)
    alias(libs.plugins.tck.build) apply false
}

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "org.eclipse.dataspacetck.build.tck-build")

    configure<TckBuildExtension> {
        pom {
            scmConnection = "https://github.com/Metaform/profile-edc-cx.git"
            scmUrl = "scm:git:git@github.com:Metaform/profile-edc-cx.git"
            projectName = project.name
            description = "CX Technology Compatibility Kit"
            projectUrl = "https://github.com/Metaform/profile-edc-cx.git"
        }
    }

    dependencies {
        // libraries shared by every cx-tck module - mirrors the dsp-tck / dcp-tck convention
        implementation(rootProject.libs.json.api)
        implementation(rootProject.libs.parsson)
        implementation(rootProject.libs.jackson.databind)
        implementation(rootProject.libs.jackson.jsonp)
        implementation(rootProject.libs.titanium)
        implementation(rootProject.libs.okhttp)
        implementation(rootProject.libs.nimbus.jwt)
        implementation(rootProject.libs.junit.jupiter)
        implementation(rootProject.libs.junit.platform.engine)
        testImplementation(rootProject.libs.assertj)
    }

    tasks.test {
        useJUnitPlatform()
    }
}
