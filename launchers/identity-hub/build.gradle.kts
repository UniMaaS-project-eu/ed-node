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
    id("application")
    alias(libs.plugins.shadow)
}

dependencies {
    runtimeOnly(project(":extensions:superuser-seed"))
//    runtimeOnly(project(":extensions:did-example-resolver")) --> REPLACED BY "vault-ini.sh"

    implementation(libs.edc.ih.spi) // needed in the extensions here
    implementation(libs.edc.ih.spi.credentials) // needed in the extensions here

    //implementation(libs.edc.http.spi)
    //implementation(libs.edc.ext.http)

    runtimeOnly(libs.edc.bom.identityhub)
    //if (project.properties.getOrDefault("persistence", "false") == "true") {
    //    runtimeOnly(libs.edc.vault.hashicorp)
    //    runtimeOnly(libs.edc.bom.identityhub.sql)
    //    println("This runtime compiles with a remote STS, Hashicorp Vault and PostgreSQL. You will need properly configured STS, Postgres and HCV instances.")
    //}
    runtimeOnly(libs.edc.vault.hashicorp)
    runtimeOnly(libs.edc.bom.identityhub.sql)
    println("This runtime compiles with a remote STS, Hashicorp Vault and PostgreSQL. You will need properly configured STS, Postgres and HCV instances.")

    // API/Presentation: habilitan /api/credentials/* y /presentations/query
    runtimeOnly(libs.edc.ih.api.credentials)     // expone /api/credentials/*
    runtimeOnly(libs.edc.ih.api.presentation)    // expone /api/credentials/presentations/*
    runtimeOnly(libs.edc.ih.api.offerhandler)    // handler para flujo DCP
    //runtimeOnly(libs.edc.ih.api.watchdog)        // opcional: renovar credenciales

    //testImplementation(libs.edc.spi.identity.did)
    //testImplementation(libs.edc.lib.crypto)
    //testImplementation(libs.edc.lib.keys)
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    exclude("**/pom.properties", "**/pom.xml")
    mergeServiceFiles()
    archiveFileName.set("identity-hub.jar")
}

edcBuild {
    publish.set(false)
}
