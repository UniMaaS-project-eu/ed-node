/*
*  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
*
*  This program and the accompanying materials are made available under the
*  terms of the Apache License, Version 2.0 which is available at
*  https://www.apache.org/licenses/LICENSE-2.0
*
*  SPDX-License-Identifier: Apache-2.0
*
*  Contributors:
*       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - Initial API and Implementation
*
*/

plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.dcp.core)
    implementation(libs.edc.spi.identity.trust)
    implementation(libs.edc.spi.transform)
    implementation(libs.edc.spi.catalog)
    implementation(libs.edc.spi.identity.did)
    implementation(libs.edc.lib.jws2020)
    implementation(libs.edc.lib.transform)

    // usercontext extension
    implementation(libs.edc.web.spi)
    implementation(libs.edc.ih.spi)
    implementation(libs.edc.api.management.config)
    implementation(libs.jakarta.rs.api)
    implementation(libs.jakarta.json.api)
    
    // Verifiable Credentials SPI - for VerifiableCredential y VerifiablePresentation
    implementation(libs.edc.ih.spi.credentials)
    implementation(libs.edc.ih.api.presentation)
    
    // Core SPI - for Result, Monitor, etc.
    implementation(libs.edc.core.spi)

    // HTTP SPI - REQUIRED by HttpRequestParamsProvider
    implementation(libs.edc.http)
        

}
