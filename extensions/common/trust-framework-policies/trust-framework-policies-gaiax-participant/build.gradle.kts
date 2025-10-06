/*
 *  Copyright (c) 2022-2023 Amadeus
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus - initial API and implementation
 *
 */

plugins {
    `java-library`
}

dependencies {
    implementation(project(":extensions:common:trust-framework-policies:trust-framework-policies-core"))

    api(libs.edc.policy.engine.spi)

    api(libs.edc.bom.controlplane.base)

    api(libs.edc.dcp.core)
    api(libs.edc.oauth2.client)

    testImplementation(libs.edc.policy.engine.lib)
    testImplementation(libs.edc.junit)

}