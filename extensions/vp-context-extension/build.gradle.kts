plugins {
    `java-library`
}

dependencies {
    // EDC Core Dependencies
    implementation("org.eclipse.edc:runtime-metamodel:0.14.0")
    implementation("org.eclipse.edc:core-spi:0.14.0")
    implementation("org.eclipse.edc:web-spi:0.14.0")
    implementation("org.eclipse.edc:management-api:0.14.0")
    implementation("org.eclipse.edc:catalog-spi:0.14.0")
    implementation("org.eclipse.edc:contract-spi:0.14.0")
    implementation("org.eclipse.edc:transfer-spi:0.14.0")
    implementation("org.eclipse.edc:identity-did-spi:0.14.0")
    
    // Jakarta REST API
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")
    
    // JSON Processing
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testImplementation("org.mockito:mockito-core:5.3.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

//java {
//    toolchain {
//        languageVersion.set(JavaLanguageVersion.of(17))
//    }
//}