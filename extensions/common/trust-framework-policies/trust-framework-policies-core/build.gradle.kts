/*
 *  Copyright (c) 2023 Amadeus
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
    api(libs.edc.core.spi)
    api(libs.edc.ih.spi)
    api(libs.edc.policy.engine.spi)

    api(libs.edc.participant.spi)

    api(libs.edc.spi.identity.trust)

    testImplementation(libs.edc.policy.engine.lib)
    testImplementation(libs.edc.junit)
}
