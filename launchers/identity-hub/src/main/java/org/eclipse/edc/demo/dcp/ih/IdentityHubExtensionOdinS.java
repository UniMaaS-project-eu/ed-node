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
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.demo.dcp.ih;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VerifiableCredentialResource;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.store.CredentialStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

@Extension("DCP Demo: Core Extension for IdentityHub with HTTP Credential Issuer and Keycloak Integration")
public class IdentityHubExtensionOdinS implements ServiceExtension {

    // Issuer Service Link
    @Setting(key = "unimaas.issuersigner.protocol", description = "Protocol for the credential issuer service", defaultValue = "http")
    private String issuerProtocol;

    @Setting(key = "unimaas.issuersigner.host", description = "Host for the credential issuer service", defaultValue = "localhost")
    private String issuerHost;

    @Setting(key = "unimaas.issuersigner.port", description = "Port for the credential issuer service", defaultValue = "8500")
    private String issuerPort;

    @Setting(key = "unimaas.issuersigner.path", description = "Path for the credential issuer service", defaultValue = "/api/v1/issue-credential")
    private String issuerPath;

    @Setting(key = "unimaas.issuersigner.did", description = "DID of the issuer service", defaultValue = "did:web:localhost%3A9876")
    private String issuerDID;

    // Configuration for the participant's DID (who receives the credential)
    @Setting(key = "edc.participant.id", description = "DID of the participant", required = true)
    private String participantDid;

    // Keycloak OIDC configurations
    @Setting(key = "unimaas.oidc.idp.protocol", description = "Protocol for Keycloak IDP", defaultValue = "https")
    private String oidcProtocol;

    @Setting(key = "unimaas.oidc.idp.host", description = "Host for Keycloak IDP", required = true)
    private String oidcHost;

    @Setting(key = "unimaas.oidc.idp.port", description = "Port for Keycloak IDP", defaultValue = "8080")
    private String oidcPort;

    @Setting(key = "unimaas.oidc.idp.path", description = "Path for Keycloak token endpoint", defaultValue = "/realms/unimaas/protocol/openid-connect/token")
    private String oidcPath;

    @Setting(key = "unimaas.oidc.idp.client.secret", description = "Client secret for Keycloak", required = true)
    private String oidcClientSecret;

    @Setting(key = "unimaas.oidc.idp.client.id", description = "Client ID for Keycloak", required = true)
    private String oidcClientId;

    @Inject
    private CredentialStore store;

    @Inject
    private TypeManager typeManager;

    private Monitor monitor;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor().withPrefix("CREDENTIAL-ISSUER");
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1) // Forze HTTP/1.1 to avoid error 400
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        objectMapper = typeManager.getMapper(JSON_LD);

        monitor.info("Initialized IdentityHub Extension with credential issuer: %s://%s:%s%s"
                .formatted(issuerProtocol, issuerHost, issuerPort, issuerPath));
        monitor.info("Keycloak OIDC endpoint: %s://%s:%s%s"
                .formatted(oidcProtocol, oidcHost, oidcPort, oidcPath));
    }

    @Override
    public void start() {
        try {
            // First create/store the default scope credential (MembershipCredential) that will always be required (defaultscope, see DcpPatchExtension)
            storeDefaultScopeCredential();
            
            // Second create the Credential for connector (DataprocessorCredential) from JWT of Keycloak.
            storeKeyCloakCredential();
        } catch (Exception e) {
            monitor.severe("Error in startup process", e);
            throw new RuntimeException("Failed to complete startup process", e);
        }
    }

    private void storeDefaultScopeCredential() throws IOException, InterruptedException {
        // Create the default credential request payload
        ObjectNode credentialRequest = storeMembershipCredential();

        // Make the request
        VerifiableCredentialResource credential = requestCredentialFromIssuer(credentialRequest);

        if (credential != null) {
            store.create(credential);
            monitor.info("Successfully stored default scope for participant: %s".formatted(participantDid));
        }
    }

    private void storeKeyCloakCredential() throws IOException, InterruptedException {
        monitor.info("Starting Keycloak authentication...");
        
        // Construct Keycloak Token Endpoint URL
        String keycloakTokenEndpoint = "%s://%s:%s%s".formatted(
                oidcProtocol, oidcHost, oidcPort, oidcPath);

        monitor.debug("Keycloak token endpoint: %s".formatted(keycloakTokenEndpoint));

        // Create the request body with the required parameters
        String requestBody = String.format("grant_type=client_credentials&client_id=%s&client_secret=%s",
                URLEncoder.encode(oidcClientId, StandardCharsets.UTF_8),
                URLEncoder.encode(oidcClientSecret, StandardCharsets.UTF_8));

        // Create the HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(keycloakTokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        // Make the request
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        monitor.debug("Keycloak response status: %d".formatted(response.statusCode()));
        monitor.debug("Keycloak response body: %s".formatted(response.body()));

        if (response.statusCode() == 200) {
            try {
                // Parse the JSON response
                JsonNode responseJson = objectMapper.readTree(response.body());
                String accessToken = responseJson.get("access_token").asText();
                String tokenType = responseJson.get("token_type").asText();
                
                monitor.info("Successfully authenticated with Keycloak. Token type: %s".formatted(tokenType));
                
                // Decode the JWT and display its contents
                decodeJWTAndStoreVC(accessToken);
                
            } catch (Exception e) {
                monitor.severe("Error parsing Keycloak token response: %s".formatted(e.getMessage()), e);
            }
        } else {
            monitor.warning("Failed to authenticate with Keycloak. Status: %d, Response: %s"
                    .formatted(response.statusCode(), response.body()));
        }
    }

    private void decodeJWTAndStoreVC(String jwt) {
        try {
            monitor.info("Decoding JWT token...");

            String[] jwtParts = jwt.split("\\.");
            if (jwtParts.length != 3) {
                monitor.warning("Invalid JWT format. Expected 3 parts, got %d".formatted(jwtParts.length));
                return;
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(jwtParts[1]), StandardCharsets.UTF_8);
            monitor.info("JWT Payload: %s".formatted(payloadJson));

            JsonNode payload = objectMapper.readTree(payloadJson);

            // Extract roles recursively
            Set<String> roles = extractRolesRecursive(payload);

            // Create credential of type DataProcessor
            ObjectNode dataProcessorRequest = createDataProcessorCredentialRequest(payload, roles);

            // Request the signed credential from the issuer
            VerifiableCredentialResource credential = requestCredentialFromIssuer(dataProcessorRequest);

            if (credential != null) {
                store.create(credential);
                monitor.info("Successfully stored DataProcessorCredential for participant: %s".formatted(participantDid));
            }

        } catch (Exception e) {
            monitor.severe("Error decoding JWT: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * Recursively traverses the payload looking for "roles" arrays and accumulating unique values.
     */
    private Set<String> extractRolesRecursive(JsonNode node) {
        Set<String> roles = new java.util.HashSet<>();

        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if ("roles".equals(entry.getKey()) && entry.getValue().isArray()) {
                    entry.getValue().forEach(roleNode -> roles.add(roleNode.asText()));
                } else {
                    roles.addAll(extractRolesRecursive(entry.getValue()));
                }
            });
        } else if (node.isArray()) {
            node.forEach(element -> roles.addAll(extractRolesRecursive(element)));
        }

        return roles;
    }

    /**
     * Constructs the DataProcessor credential request JSON.
     */
    private ObjectNode createDataProcessorCredentialRequest(JsonNode jwtPayload, Set<String> roles) {
        ObjectNode request = objectMapper.createObjectNode();

        // DIDs
        request.put("participantDid", participantDid);
        request.put("holderDid", participantDid);

        ObjectNode credential = objectMapper.createObjectNode();

        // @context
        ArrayNode context = objectMapper.createArrayNode()
                .add("https://www.w3.org/2018/credentials/v1")
                .add("https://w3id.org/security/suites/jws-2020/v1")
                .add("https://www.w3.org/ns/did/v1");
        ObjectNode unimaasContext = objectMapper.createObjectNode()
                .put("unimaas-credentials", "https://w3id.org/unimaas/credentials/")
                .put("contractVersion", "unimaas-credentials:contractVersion")
                .put("roles", "unimaas-credentials:roles");
        context.add(unimaasContext);
        credential.set("@context", context);

        // dynamic id
        credential.put("id", "http://org.yourdataspace.com/credentials/" + Instant.now().toEpochMilli());

        // type
        credential.set("type", objectMapper.createArrayNode()
                .add("VerifiableCredential")
                .add("DataProcessorCredential"));

        // issuer
        credential.put("issuer", issuerDID);

        // issuanceDate
        credential.put("issuanceDate", ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));

        // credentialSubject
        ObjectNode credentialSubject = objectMapper.createObjectNode();
        //credentialSubject.put("id", jwtPayload.get("sub").asText());
        credentialSubject.put("id", participantDid);
        credentialSubject.put("contractVersion", "1.0.0");

        ArrayNode rolesArray = objectMapper.createArrayNode();
        roles.forEach(rolesArray::add);
        credentialSubject.set("roles", rolesArray);

        credential.set("credentialSubject", credentialSubject);

        request.set("credential", credential);
        return request;
    }

    private ObjectNode storeMembershipCredential() {
        ObjectNode request = objectMapper.createObjectNode();

        // Participant and holder DIDs
        request.put("participantDid", participantDid);
        request.put("holderDid", participantDid);

        // Create the default credential structure (Membership Credential)
        ObjectNode credential = objectMapper.createObjectNode();

        // @context
        ArrayNode context = objectMapper.createArrayNode()
                .add("https://www.w3.org/2018/credentials/v1")
                .add("https://w3id.org/security/suites/jws-2020/v1")
                .add("https://www.w3.org/ns/did/v1");
        ObjectNode unimaasContext = objectMapper.createObjectNode()
                .put("unimaas-credentials", "https://w3id.org/unimaas/credentials/")
                .put("membershipType", "unimaas-credentials:membershipType");
        context.add(unimaasContext);
        credential.set("@context", context);

        // id
        credential.put("id", "http://org.yourdataspace.com/credentials/" + Instant.now().toEpochMilli());

        // type
        credential.set("type", objectMapper.createArrayNode()
                .add("VerifiableCredential")
                .add("MembershipCredential"));

        // issuer
        credential.put("issuer", issuerDID);

        // issuanceDate (dynamic)
        credential.put("issuanceDate", ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));

        // credentialSubject
        ObjectNode credentialSubject = objectMapper.createObjectNode();
        credentialSubject.put("id", participantDid);
        ObjectNode membership = objectMapper.createObjectNode();
        membership.put("membershipType", "UniMaaSMember");
        credentialSubject.set("membership", membership);
        credential.set("credentialSubject", credentialSubject);

        request.set("credential", credential);

        return request;
    }

    private VerifiableCredentialResource requestCredentialFromIssuer(ObjectNode credentialRequest)
            throws IOException, InterruptedException {

        // Build the sending service URL
        String issuerServiceEndpoint = "%s://%s:%s%s".formatted(
                issuerProtocol, issuerHost, issuerPort, issuerPath);

        monitor.debug("Requesting credential from: %s".formatted(issuerServiceEndpoint));
        monitor.debug("Request payload: %s".formatted(credentialRequest.toString()));

        // Create the HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(issuerServiceEndpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(credentialRequest), StandardCharsets.UTF_8))
                .build();

        // Make the request
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        monitor.debug("Response status: %d".formatted(response.statusCode()));
        monitor.debug("Response body: %s".formatted(response.body()));

        if (response.statusCode() == 200) {
            try {
                // Parse the JSON response
                JsonNode responseJson = objectMapper.readTree(response.body());

                // Convert the response to a VerifiableCredentialResource
                return objectMapper.treeToValue(responseJson, VerifiableCredentialResource.class);

            } catch (Exception e) {
                monitor.severe("Error parsing credential response: %s".formatted(e.getMessage()), e);
                return null;
            }
        } else {
            monitor.warning("Failed to obtain credential. Status: %d, Response: %s"
                    .formatted(response.statusCode(), response.body()));
            return null;
        }
    }

    @Override
    public void shutdown() {
        if (httpClient != null) {
            monitor.debug("IdentityHub Extension shutting down");
        }
    } 
}