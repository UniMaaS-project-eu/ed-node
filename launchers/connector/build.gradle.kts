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
    //Controlplane
//    runtimeOnly(project(":extensions:did-example-resolver")) --> NOT USED REPLACED BY "vault-ini.sh"
    runtimeOnly(project(":extensions:dcp-impl")) // some patches/impls for DCP
//    runtimeOnly(project(":extensions:catalog-node-resolver")) // to trigger the federated catalog

    // TrustFramework GAIA-X --> 20250929
    runtimeOnly(project(":extensions:common:trust-framework-policies:trust-framework-policies-core"))
    runtimeOnly(project(":extensions:common:trust-framework-policies:trust-framework-policies-gaiax-participant"))
//    runtimeOnly(project(":extensions:common:trust-framework-policies:trust-framework-policies-jsonld"))

    // ################### Checkpoint http://localhost:9191/api/check/health ###################
    implementation(libs.edc.api.observability)

    // --------------------------------------------- 1-start  ---------------------------------------------------------------
    // --------------------------------------------- 1-origin ---------------------------------------------------------------
    //runtimeOnly(libs.edc.bom.controlplane)
    // --------------------------------------------- 1-new    ---------------------------------------------------------------

    // ################### Core EDC dependencies ( Control plane + data plane capabilities) ###################
    implementation(libs.edc.boot)
    implementation(libs.edc.control.api.configuration)
    implementation(libs.edc.control.plane.api.client)
    implementation(libs.edc.control.plane.api)
    implementation(libs.edc.control.plane.core)
    implementation(libs.edc.management.api)
    //implementation(libs.edc.connector.core)

    // ################### Identity & Trust ###################
    implementation(libs.edc.spi.identity.did)
    implementation(libs.edc.dcp.core)
    runtimeOnly(libs.edc.dcp)

    // ################### DID resolvers ###################
    implementation(libs.edc.did.core)
    implementation(libs.edc.did.web)

    // ################### Common dependencies ###################
    implementation(libs.edc.bom.controlplane.base)
    // --------------------------------------------- 1-end    ---------------------------------------------------------------

    // --------------------------------------------- 2-start  ---------------------------------------------------------------

    // --------------------------------------------- 2-origin ---------------------------------------------------------------

    runtimeOnly(libs.edc.api.secrets)

    // For TokenDecorator and message interception
    implementation(libs.edc.token.spi)    
    implementation(libs.edc.core.spi)

    // --------------------------------------------- 2-end    ---------------------------------------------------------------

    // --------------------------------------------- 3-start  ---------------------------------------------------------------

    // --------------------------------------------- 3-origin ---------------------------------------------------------------

    //Dataplane
    //runtimeOnly(libs.edc.bom.dataplane)
    //runtimeOnly(libs.edc.dataplane.v2)

    // --------------------------------------------- 3-new    ---------------------------------------------------------------

    // ################### Data plane ###################
    implementation(libs.edc.data.plane.selector.api)
    implementation(libs.edc.data.plane.selector.core)
    implementation(libs.edc.data.plane.self.registration)
    implementation(libs.edc.data.plane.signaling.api)
    implementation(libs.edc.data.plane.signaling.client)
    implementation(libs.edc.data.plane.core)
    implementation(libs.edc.data.plane.http)
    implementation(libs.edc.data.plane.iam)
    //implementation(libs.edc.data.plane.public.api)
    implementation(libs.edc.data.plane.spi)

    // ################### Transfer capabilities (to act as consumer) ###################
    implementation(libs.edc.transfer.data.plane.signaling)
    implementation(libs.edc.edr.cache.api)
    implementation(libs.edc.edr.store.core)
    implementation(libs.edc.edr.store.receiver)

    // --------------------------------------------- 3-end    ---------------------------------------------------------------

    //Persistence
    //if (project.properties.getOrDefault("persistence", "false") == "true") {
    //    runtimeOnly(libs.edc.vault.hashicorp)
    //    runtimeOnly(libs.edc.bom.controlplane.sql)
    //    println("This runtime compiles with a remote STS client, Hashicorp Vault and PostgreSQL. You will need properly configured Postgres and HCV instances.")
    //}
    
    // ################### Common dependencies ###################
    // Token handling
    implementation(libs.edc.token.core)
    implementation(libs.edc.dsp)
    implementation(libs.edc.http)
    //implementation(libs.edc.configuration.filesystem)
    implementation(libs.edc.validator.data.address.http.data)
    //implementation(libs.edc.monitor.jdk.logger)

    // --------------------------------------------- 4-start  ---------------------------------------------------------------

    // --------------------------------------------- 4-origin ---------------------------------------------------------------
    //runtimeOnly(libs.edc.bom.controlplane.sql)
    //runtimeOnly(libs.edc.bom.dataplane.sql)

    // --------------------------------------------- 3-new    ---------------------------------------------------------------

    // ################### SQL persistence for dataplane ###################
    implementation(libs.edc.sql.dataplane.instancestore)

    // ################### PostgreSQL SQL persistence (optional) ###################
    // Transaction support - MUST GO BEFORE sql-core
    implementation(libs.edc.transaction.local)
    // SQL core
    implementation(libs.edc.sql.core)
    //implementation(libs.edc.sql.schema) // Optional, for schema validation (fails, does not exist)
    // Connection pool
    implementation(libs.edc.sql.pool.apache.commons)
    // PostgreSQL driver
    implementation(libs.postgresql.driver)
    // For automatic schema migration (optional but recommended)
    implementation(libs.edc.sql.bootstrapper)
    implementation(libs.edc.asset.index.sql)
    // SQL dependencies    
    implementation(libs.edc.contract.definition.store.sql)
    implementation(libs.edc.policy.definition.store.sql)
    implementation(libs.edc.contract.negotiation.store.sql)
    implementation(libs.edc.transfer.process.store.sql)

    // --------------------------------------------- 4-end    ---------------------------------------------------------------

    // For full IAM support (remote STS client)
    //implementation(libs.edc.iam.mock)
    // ################### OAuth2 Client - REQUIRED for STS Remote Client ###################
    implementation(libs.edc.oauth2.client)
    implementation(libs.edc.sts.remote.client)
    //implementation(libs.edc.iam.mock)

    // For Hashicorp Vault support
    runtimeOnly(libs.edc.vault.hashicorp)

    println("This runtime compiles with a remote STS client, Hashicorp Vault and PostgreSQL. You will need properly configured Postgres and HCV instances.")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    exclude("**/pom.properties", "**/pom.xml")
    mergeServiceFiles()
    archiveFileName.set("connector.jar")
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

edcBuild {
    publish.set(false)
}
