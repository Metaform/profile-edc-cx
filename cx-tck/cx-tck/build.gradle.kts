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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.eclipse.dataspacetck.gradle.tckbuild.extensions.DockerExtension

plugins {
    `java-library`
    application
    alias(libs.plugins.shadow)
}

dependencies {
    api(libs.tck.core)
    api(libs.tck.runtime)

    // the composing launcher and the test cases
    api(project(":cx-system"))
    api(project(":cx-catalog"))
    api(project(":cx-flow"))

    implementation(libs.junit.platform.launcher)
}

application {
    mainClass.set("org.eclipse.dataspacetck.cx.suite.CxTckSuite")
}

configure<DockerExtension> {
    jarFilePath = "build/libs/${project.name}-runtime.jar"
}

tasks.withType<ShadowJar> {
    exclude("**/pom.properties", "**/pom.xml")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    archiveFileName.set("${project.name}-runtime.jar") // should be something other than "cx-tck.jar", to avoid erroneous task dependencies
}
