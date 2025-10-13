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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

@Extension("DCP Demo: Core Extension for IdentityHub with HTTP Credential IssuerService-VC and Keycloak Integration")
public class IdentityHubExtensionOdinS implements ServiceExtension {

    // IssuerService-VC Link
    @Setting(key = "unimaas.issuerservicevc.protocol", description = "Protocol for the credential issuerservice-vc", defaultValue = "https")
    private String issuerProtocol;

    @Setting(key = "unimaas.issuerservicevc.host", description = "Host for the credential issuerservice-vc", defaultValue = "localhost")
    private String issuerHost;

    @Setting(key = "unimaas.issuerservicevc.port", description = "Port for the credential issuerservice-vc", defaultValue = "9090")
    private String issuerPort;

    @Setting(key = "unimaas.issuerservicevc.path", description = "Path for the credential issuerservice-vc", defaultValue = "/api/v1/issue-credential")
    private String issuerPath;

    @Setting(key = "unimaas.issuerservicevc.did", description = "DID of the issuerservice-vc", defaultValue = "did:web:localhost%3A9876")
    private String issuerDID;

    @Setting(key = "gaiax.issuerservicevc.port", description = "Port for the GAIA-X 22.06/24.11 credential issuerservice-vc", defaultValue = "9090")
    private String issuerGAIAXPort;

    @Setting(key = "gaiax.issuerservicevc.path", description = "Path for the GAIA-X 22.06/24.11  credential issuerservice-vc", defaultValue = "/api/v1/issue-gaiax-credential-jwt")
    private String issuerGAIAXPath;

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

    // Duplicate handling strategies
    @Setting(key = "unimaas.credentials.membership.duplicate.strategy", 
             description = "Strategy for handling duplicate MembershipCredential: SKIP_IF_EXISTS, REPLACE_ALWAYS, KEEP_NEWEST, KEEP_OLDEST, ALLOW_DUPLICATES", 
             defaultValue = "REPLACE_ALWAYS")
    private String membershipDuplicateStrategy;

    @Setting(key = "unimaas.credentials.dataprocessor.duplicate.strategy", 
             description = "Strategy for handling duplicate DataProcessorCredential", 
             defaultValue = "REPLACE_ALWAYS")
    private String dataProcessorDuplicateStrategy;

    @Setting(key = "unimaas.credentials.gaiax.duplicate.strategy", 
             description = "Strategy for handling duplicate GAIA-X Credential", 
             defaultValue = "REPLACE_ALWAYS")
    private String gaiaxDuplicateStrategy;

    // Periodic Renewal
    @Setting(key = "unimaas.credentials.renewal.check.interval.minutes", 
             description = "Interval in minutes to check credentials for renewal", 
             defaultValue = "1400")
    private String renewalCheckIntervalMinutes;

    @Setting(key = "unimaas.credentials.renewal.expiry.threshold.days", 
             description = "Days before expiration to trigger renewal", 
             defaultValue = "30")
    private String renewalExpiryThresholdDays;

    @Setting(key = "unimaas.credentials.renewal.enabled", 
             description = "Enable automatic credential renewal", 
             defaultValue = "true")
    private String renewalEnabled;


    @Inject
    private CredentialStore store;

    @Inject
    private TypeManager typeManager;

    private Monitor monitor;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private CredentialManager credentialManager;
    private ScheduledExecutorService scheduler;

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

        monitor.info("Initialized IdentityHub Extension with GAIA-X credential issuer: %s://%s:%s%s"
                .formatted(issuerProtocol, issuerHost, issuerGAIAXPort, issuerGAIAXPath));

        monitor.info("Keycloak OIDC endpoint: %s://%s:%s%s"
                .formatted(oidcProtocol, oidcHost, oidcPort, oidcPath));

        monitor.info("Duplicate strategies - Membership: %s, DataProcessor: %s, GAIA-X: %s"
                .formatted(membershipDuplicateStrategy, dataProcessorDuplicateStrategy, gaiaxDuplicateStrategy));

        boolean isRenewalEnabled = Boolean.parseBoolean(renewalEnabled);
        int checkInterval = Integer.parseInt(renewalCheckIntervalMinutes);
        int expiryThreshold = Integer.parseInt(renewalExpiryThresholdDays);
        
        monitor.info("Credential renewal: %s (check every %d minutes, renew %d days before expiry)"
                .formatted(isRenewalEnabled ? "ENABLED" : "DISABLED", checkInterval, expiryThreshold));

        credentialManager = new CredentialManager(store, monitor);
    }

    @Override
    public void start() {
        try {
            // First create/store the default scope credential (MembershipCredential) that will always be required (defaultscope, see DcpPatchExtension)
            storeDefaultScopeCredential();
            
            // Second Obtain JWT of Keycloak.
            String accessToken = obtainJWTKeyCloak();

            // Decode the JWT and Store credential (DataprocessorCredential)
            storeExtraScopeCredential(accessToken);

            // Second create the Credential for connector (DataprocessorCredential) from JWT of Keycloak.
            //storeKeyCloakCredential();

            // Third create the GAIA-X 22.06/24.11 Credential.
            storeGAIAXCredential(accessToken);

            // Start periodic renewal scheduler if enabled
            if (Boolean.parseBoolean(renewalEnabled)) {
                startPeriodicRenewal();
            }

        } catch (Exception e) {
            monitor.severe("Error in startup process", e);
            throw new RuntimeException("Failed to complete startup process", e);
        }
    }

    private void startPeriodicRenewal() {
        int checkInterval = Integer.parseInt(renewalCheckIntervalMinutes);
        scheduler = Executors.newScheduledThreadPool(1);
        
        monitor.info("Starting periodic credential renewal scheduler (every %d minutes)".formatted(checkInterval));
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                monitor.info("========================================");
                monitor.info("Starting periodic credential check");
                monitor.info("========================================");
                checkAndRenewCredentials();
            } catch (Exception e) {
                monitor.severe("Error during periodic credential check", e);
            }
        }, checkInterval, checkInterval, TimeUnit.MINUTES);
    }

    private void checkAndRenewCredentials() {
        try {
            int thresholdDays = Integer.parseInt(renewalExpiryThresholdDays);
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            LocalDateTime thresholdDate = now.plusDays(thresholdDays);

            // Verificar MembershipCredential
            checkAndRenewCredential("MembershipCredential", thresholdDate, this::renewMembershipCredential);

            // Verificar DataProcessorCredential (requiere JWT de Keycloak)
            String accessToken = obtainJWTKeyCloak();
            checkAndRenewCredential("DataProcessorCredential", thresholdDate, 
                    () -> renewDataProcessorCredential(accessToken));

            // Verificar GAIA-X Credential
            checkAndRenewCredential("LegalPerson", thresholdDate, 
                    () -> renewGAIAXCredential(accessToken));

            monitor.info("Periodic credential check completed successfully");

        } catch (Exception e) {
            monitor.severe("Error during credential renewal process", e);
        }
    }

    private void checkAndRenewCredential(String credentialType, LocalDateTime thresholdDate, 
                                         RenewalAction renewalAction) {
        try {
            monitor.info("Checking %s...".formatted(credentialType));
            
            // Buscar credencial existente en el store
            List<VerifiableCredentialResource> credentials = findCredentialsByType(credentialType);

            if (credentials.isEmpty()) {
                monitor.warning("⚠️ No %s found. Creating new credential...".formatted(credentialType));
                renewalAction.execute();
                return;
            }

            // Verificar la fecha de expiración de cada credencial encontrada
            boolean needsRenewal = false;
            for (VerifiableCredentialResource credential : credentials) {
                LocalDateTime expirationDate = extractExpirationDate(credential);
                
                if (expirationDate == null) {
                    monitor.warning("⚠️ %s has no expiration date. Renewing as precaution..."
                            .formatted(credentialType));
                    needsRenewal = true;
                    break;
                }

                if (expirationDate.isBefore(thresholdDate)) {
                    monitor.warning("⚠️ %s expires on %s (within threshold). Renewing..."
                            .formatted(credentialType, expirationDate));
                    needsRenewal = true;
                    break;
                }

                monitor.info("✅ %s is valid until %s".formatted(credentialType, expirationDate));
            }

            if (needsRenewal) {
                renewalAction.execute();
            }

        } catch (Exception e) {
            monitor.severe("Error checking %s: %s".formatted(credentialType, e.getMessage()), e);
        }
    }

    private List<VerifiableCredentialResource> findCredentialsByType(String credentialType) {
        List<VerifiableCredentialResource> result = new ArrayList<>();
        
        try {
            var allCredentials = store.query(
                    org.eclipse.edc.spi.query.QuerySpec.Builder.newInstance().build());
            
            for (var credential : allCredentials.getContent()) {
                if (credential.getVerifiableCredential() != null && 
                    credential.getVerifiableCredential().credential() != null) {
                    
                    var types = credential.getVerifiableCredential().credential().getType();
                    if (types != null && types.contains(credentialType)) {
                        result.add(credential);
                    }
                }
            }
        } catch (Exception e) {
            monitor.warning("Error querying credentials: %s".formatted(e.getMessage()), e);
        }
        
        return result;
    }

    private LocalDateTime extractExpirationDate(VerifiableCredentialResource credential) {
        try {
            Instant expirationInstant = credential.getVerifiableCredential()
                    .credential()
                    .getExpirationDate();
            
            if (expirationInstant == null) {
                return null;
            }

            // Convertir Instant a LocalDateTime en UTC
            return LocalDateTime.ofInstant(expirationInstant, ZoneOffset.UTC);

        } catch (Exception e) {
            monitor.warning("Could not parse expiration date: %s".formatted(e.getMessage()));
            return null;
        }
    }

    private void renewMembershipCredential() throws IOException, InterruptedException {
        monitor.info("Renewing MembershipCredential...");
        storeDefaultScopeCredential();
    }

    private void renewDataProcessorCredential(String accessToken) {
        monitor.info("Renewing DataProcessorCredential...");
        storeExtraScopeCredential(accessToken);
    }

    private void renewGAIAXCredential(String accessToken) {
        monitor.info("Renewing GAIA-X Credential...");
        storeGAIAXCredential(accessToken);
    }

    @FunctionalInterface
    private interface RenewalAction {
        void execute() throws IOException, InterruptedException;
    }




    private void storeDefaultScopeCredential() throws IOException, InterruptedException {
        monitor.info("========================================");
        monitor.info("Processing MembershipCredential");
        monitor.info("========================================");

        // Create the default credential request payload
        ObjectNode credentialRequest = createMembershipCredentialPayloadRequest();

        // Make the request
        VerifiableCredentialResource credential = requestCredentialFromIssuer(credentialRequest, issuerPort, issuerPath);

        if (credential != null) {
            //store.create(credential);
            //monitor.info("Successfully stored default scope for participant: %s".formatted(participantDid));

            // Use credential manager with configured strategy
            CredentialManager.DuplicateStrategy strategy = parseStrategy(membershipDuplicateStrategy);
            boolean stored = credentialManager.storeCredential(credential, strategy);
            
            if (stored) {
                monitor.info("✅ MembershipCredential stored successfully");
            } else {
                monitor.info("ℹ️ MembershipCredential was not stored (duplicate handling)");
            }
        } else {
            monitor.warning("❌ Failed to obtain MembershipCredential from issuer");
        }
    }

    private String obtainJWTKeyCloak() throws IOException, InterruptedException {
        monitor.info("========================================");
        monitor.info("Authenticating with Keycloak");
        monitor.info("========================================");
        
        // Construct Keycloak Token Endpoint URL
        String keycloakTokenEndpoint = "%s://%s:%s%s".formatted(
                oidcProtocol, oidcHost, oidcPort, oidcPath);

        monitor.debug("Keycloak token endpoint: %s".formatted(keycloakTokenEndpoint));

        // Create the request body with the required parameters
        String requestBody = String.format("grant_type=client_credentials&client_id=%s&client_secret=%s",
                URLEncoder.encode(oidcClientId, StandardCharsets.UTF_8),
                URLEncoder.encode(oidcClientSecret, StandardCharsets.UTF_8));

        //monitor.debug("Request payload: " + requestBody);

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
        //monitor.debug("Keycloak response body: %s".formatted(response.body()));

        if (response.statusCode() == 200) {
            try {
                // Parse the JSON response
                JsonNode responseJson = objectMapper.readTree(response.body());
                String accessToken = responseJson.get("access_token").asText();
                String tokenType = responseJson.get("token_type").asText();
                
                monitor.info("Successfully authenticated with Keycloak. Token type: %s".formatted(tokenType));
                
                // Decode the JWT and display its contents
                //storeExtraScopeCredential(accessToken);

                return accessToken;
                
            } catch (Exception e) {
                monitor.severe("Error parsing Keycloak token response: %s".formatted(e.getMessage()), e);
            }
        } else {
            monitor.warning("Failed to authenticate with Keycloak. Status: %d, Response: %s"
                    .formatted(response.statusCode(), response.body()));
        }
        return "";
    }

//    private void storeKeyCloakCredential() throws IOException, InterruptedException {
//        monitor.info("Starting Keycloak authentication...");
//        
//        // Construct Keycloak Token Endpoint URL
//        String keycloakTokenEndpoint = "%s://%s:%s%s".formatted(
//                oidcProtocol, oidcHost, oidcPort, oidcPath);
//
//        monitor.debug("Keycloak token endpoint: %s".formatted(keycloakTokenEndpoint));
//
//        // Create the request body with the required parameters
//        String requestBody = String.format("grant_type=client_credentials&client_id=%s&client_secret=%s",
//                URLEncoder.encode(oidcClientId, StandardCharsets.UTF_8),
//                URLEncoder.encode(oidcClientSecret, StandardCharsets.UTF_8));
//
//        //monitor.debug("Request payload: " + requestBody);
//
//        // Create the HTTP request
//        HttpRequest request = HttpRequest.newBuilder()
//                .uri(URI.create(keycloakTokenEndpoint))
//                .header("Content-Type", "application/x-www-form-urlencoded")
//                .timeout(Duration.ofSeconds(30))
//                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
//                .build();
//
//        // Make the request
//        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//
//        monitor.debug("Keycloak response status: %d".formatted(response.statusCode()));
//        monitor.debug("Keycloak response body: %s".formatted(response.body()));
//
//        if (response.statusCode() == 200) {
//            try {
//                // Parse the JSON response
//                JsonNode responseJson = objectMapper.readTree(response.body());
//                String accessToken = responseJson.get("access_token").asText();
//                String tokenType = responseJson.get("token_type").asText();
//                
//                monitor.info("Successfully authenticated with Keycloak. Token type: %s".formatted(tokenType));
//                
//                // Decode the JWT and display its contents
//                storeExtraScopeCredential(accessToken);
//                
//            } catch (Exception e) {
//                monitor.severe("Error parsing Keycloak token response: %s".formatted(e.getMessage()), e);
//            }
//        } else {
//            monitor.warning("Failed to authenticate with Keycloak. Status: %d, Response: %s"
//                    .formatted(response.statusCode(), response.body()));
//        }
//    }

    private void storeExtraScopeCredential(String jwt) {
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
            //monitor.info("Extracted roles: %s".formatted(roles));

            // Create credential of type DataProcessor
            ObjectNode dataProcessorRequest = createDataProcessorCredentialPayloadRequest(payload, roles);

            // Request the signed credential from the issuer
            VerifiableCredentialResource credential = requestCredentialFromIssuer(dataProcessorRequest, issuerPort, issuerPath);

            if (credential != null) {
                //store.create(credential);
                //monitor.info("Successfully stored DataProcessorCredential for participant: %s".formatted(participantDid));

                // Use credential manager with configured strategy
                CredentialManager.DuplicateStrategy strategy = parseStrategy(dataProcessorDuplicateStrategy);
                boolean stored = credentialManager.storeCredential(credential, strategy);
                
                if (stored) {
                    monitor.info("✅ DataProcessorCredential stored successfully");
                } else {
                    monitor.info("ℹ️ DataProcessorCredential was not stored (duplicate handling)");
                }
            } else {
                monitor.warning("❌ Failed to obtain DataProcessorCredential from issuer");
            }

        } catch (Exception e) {
            monitor.severe("Error decoding JWT: %s".formatted(e.getMessage()), e);
        }
    }

    private void storeGAIAXCredential(String jwt) {
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
            //monitor.info("Extracted roles: %s".formatted(roles));

            // Create credential of type GAIA-X
            ObjectNode dataGAIAXRequest = createGAIAXCredentialPayloadRequest(payload, roles);

            // Request the signed credential from the issuer
            VerifiableCredentialResource credential = requestCredentialFromIssuer(dataGAIAXRequest, issuerGAIAXPort, issuerGAIAXPath);

            if (credential != null) {
                //store.create(credential);
                //monitor.info("Successfully stored GAIA-X Credential for participant: %s".formatted(participantDid));

                // Use credential manager with configured strategy
                CredentialManager.DuplicateStrategy strategy = parseStrategy(gaiaxDuplicateStrategy);
                boolean stored = credentialManager.storeCredential(credential, strategy);
                
                if (stored) {
                    monitor.info("✅ GAIA-X Credential stored successfully");
                } else {
                    monitor.info("ℹ️ GAIA-X Credential was not stored (duplicate handling)");
                }
            } else {
                monitor.warning("❌ Failed to obtain GAIA-X Credential from issuer");
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
    private ObjectNode createDataProcessorCredentialPayloadRequest(JsonNode jwtPayload, Set<String> roles) {
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

    /**
     * Constructs the GAIAX credential request JSON.
     */
    private ObjectNode createGAIAXCredentialPayloadRequest(JsonNode jwtPayload, Set<String> roles) {
        ObjectNode request = objectMapper.createObjectNode();

        // DIDs
        request.put("participantDid", participantDid);
        request.put("legalName", "Odin Solutions S.L.");
        request.put("countryCode", "ES");
        request.put("vatNumber", "ESB73845893");
        request.put("addressCode", "ES-MU");
        request.put("streetAddress", "Calle Palma de Mallorca 2");
        request.put("postalCode", "30009");

        ArrayNode rolesArray = objectMapper.createArrayNode();
        roles.forEach(rolesArray::add);

        request.set("roles", rolesArray);
        
        return request;
    }


    private ObjectNode createMembershipCredentialPayloadRequest() {
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

    private VerifiableCredentialResource requestCredentialFromIssuer(ObjectNode credentialRequest, String portRequest, String pathRequest)
            throws IOException, InterruptedException {

        // Build the sending service URL
        String issuerServiceEndpoint = "%s://%s:%s%s".formatted(
                issuerProtocol, issuerHost, portRequest, pathRequest);

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

    private CredentialManager.DuplicateStrategy parseStrategy(String value) {
        if (value == null || value.isBlank()) {
            monitor.warning("No duplicate strategy provided. Defaulting to REPLACE_ALWAYS.");
            return CredentialManager.DuplicateStrategy.REPLACE_ALWAYS;
        }
        try {
            return CredentialManager.DuplicateStrategy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            monitor.warning("Invalid duplicate strategy '%s'. Defaulting to REPLACE_ALWAYS.".formatted(value));
            return CredentialManager.DuplicateStrategy.REPLACE_ALWAYS;
        }
    }

    @Override
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            monitor.info("Shutting down credential renewal scheduler...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (httpClient != null) {
            monitor.debug("IdentityHub Extension shutting down");
        }
    }
}