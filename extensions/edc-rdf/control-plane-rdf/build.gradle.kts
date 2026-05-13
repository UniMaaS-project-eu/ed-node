/*
 *  Copyright (c) 2020, 2021 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *
 */
plugins {
    `java-library`
}

dependencies {
    // Pull in the “core” SPI
    //implementation(libs.edc.spi.core)
    implementation(libs.edc.core.spi)

    // Pull in the “validator” SPI
    implementation(libs.edc.spi.validator)

    //For dealing with Asset class
    implementation(libs.edc.asset.spi)
}