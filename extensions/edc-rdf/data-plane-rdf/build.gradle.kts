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
        //implementation(libs.edc.spi.core)
        implementation(libs.edc.core.spi)
        //implementation(libs.edc.dataplane.spi)
        implementation(libs.edc.data.plane.spi)

        implementation(libs.edc.util.lib)
        implementation(libs.edc.dataplane.util)
        implementation(libs.apache.jena.libs)

        implementation(project(":extensions:edc-rdf:data-plane-rdf-spi"))
        implementation(project(":extensions:edc-rdf:control-plane-rdf"))
}