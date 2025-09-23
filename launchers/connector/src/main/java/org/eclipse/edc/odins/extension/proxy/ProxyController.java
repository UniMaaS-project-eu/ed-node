/*
 *  Copyright (c) 2025 Cofinity-X
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Cofinity-X - initial API and implementation
 *
 */

package org.eclipse.edc.odins.extension.proxy;

// 1. THIRD_PARTY_PACKAGE (Jackson, Jakarta, Eclipse EDC)
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAuthorizationService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

// 2. STANDARD_JAVA_PACKAGE (java.*)
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

// 3. STATIC imports
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_ENCODING;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static jakarta.ws.rs.core.MediaType.WILDCARD;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static java.util.Collections.emptyMap;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

/**
 * ProxyController handles incoming requests, authorizes them, and forwards them to the appropriate backend service.
 * It also includes logic for transforming data for InfluxDB, including conversion of CSV to JSON-LD.
 */
@Path("{any:.*}")
@Consumes(WILDCARD)
@Produces(WILDCARD)
public class ProxyController {

    private final DataPlaneAuthorizationService authorizationService;
    private final Monitor monitor;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /*
    private static final String ENV_INFLUXDB_ORG = "INFLUXDB_ORG";
    private static final String ENV_INFLUXDB_BUCKET = "INFLUXDB_BUCKET";
    private static final String ENV_INFLUXDB_ADMIN_TOKEN = "INFLUXDB_ADMIN_TOKEN";
    private static final String ENV_INFLUXDB_PRECISION = "INFLUXDB_PRECISION";

    // Configuraciones en archivo de propiedades
    private static final String INFLUXDB_ORG = "influxdb.org";
    private static final String INFLUXDB_BUCKET = "influxdb.bucket";
    private static final String INFLUXDB_ADMIN_TOKEN = "influxdb.admin.token";
    private static final String INFLUXDB_PRECISION = "influxdb.precision";

    // Valores por defecto
    private static final String DEFAULT_INFLUXDB_ORG = "test_org";
    private static final String DEFAULT_INFLUXDB_BUCKET = "test_org_bucket";
    private static final String DEFAULT_INFLUXDB_ADMIN_TOKEN = "admin_token";
    private static final String DEFAULT_INFLUXDB_PRECISION = "ns";
    */

    private final ObjectMapper objectMapper; // Campo añadido

    /*
    private String influxDbOrg;
    private String influxDbBucket;
    private String influxDbAdminToken;
    private String influxDbPrecision;
    */

    private ServiceExtensionContext context;

    /**
     * Constructs a ProxyController.
     *
     * @param authorizationService The service for authorizing data plane requests.
     * @param context The service extension context.
     * @param monitor The monitor for logging.
     */
    public ProxyController(DataPlaneAuthorizationService authorizationService, ServiceExtensionContext context, Monitor monitor) {
        this.authorizationService = authorizationService;
        this.monitor = monitor;
        this.context = context;
        /*
        this.influxDbOrg = getConfigValue(context, ENV_INFLUXDB_ORG, INFLUXDB_ORG, DEFAULT_INFLUXDB_ORG);
        this.influxDbBucket = getConfigValue(context, ENV_INFLUXDB_BUCKET, INFLUXDB_BUCKET, DEFAULT_INFLUXDB_BUCKET);
        this.influxDbAdminToken = getConfigValue(context, ENV_INFLUXDB_ADMIN_TOKEN, INFLUXDB_ADMIN_TOKEN, DEFAULT_INFLUXDB_ADMIN_TOKEN);
        this.influxDbPrecision = getConfigValue(context, ENV_INFLUXDB_PRECISION, INFLUXDB_PRECISION, DEFAULT_INFLUXDB_PRECISION);
        */
        this.objectMapper = new ObjectMapper(); 
    }

    /**
     * Obtains a configuration value, giving priority to the environment variable.
     * If the environment variable is not defined, searches the system configuration.
     * If there is no configuration, uses the default value.
     */
    private String getConfigValue(ServiceExtensionContext context, String envKey, String configKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return context.getConfig().getString(configKey, defaultValue);
    }

    // Token debugging method - add to ProxyController
    private void debugToken(String token, Monitor monitor) {
        try {
            // Remove "Bearer" if present
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            // Split the JWT into its parts
            String[] parts = cleanToken.split("\\.");
            if (parts.length != 3) {
                monitor.severe("JWT token does not have 3 parts: " + parts.length);
                return;
            }
            
            // Decoding the payload (part two)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            monitor.info("Token Payload: " + payload);
            
            // Parse the JSON payload
            JsonNode payloadJson = objectMapper.readTree(payload);
            
            // Check important fields
            long iat = payloadJson.has("iat") ? payloadJson.get("iat").asLong() : 0;
            long exp = payloadJson.has("exp") ? payloadJson.get("exp").asLong() : 0;
            String iss = payloadJson.has("iss") ? payloadJson.get("iss").asText() : "N/A";
            String aud = payloadJson.has("aud") ? payloadJson.get("aud").asText() : "N/A";
            String sub = payloadJson.has("sub") ? payloadJson.get("sub").asText() : "N/A";
            
            long currentTime = Instant.now().getEpochSecond();
            
            monitor.info("=== DEBUG TOKEN INFO ===");
            monitor.info("Issuer (iss): " + iss);
            monitor.info("Audience (aud): " + aud);
            monitor.info("Subject (sub): " + sub);
            monitor.info("Issued At (iat): " + iat + " (" + Instant.ofEpochSecond(iat) + ")");
            monitor.info("Expires (exp): " + (exp > 0 ? exp + " (" + Instant.ofEpochSecond(exp) + ")" : "NO EXPIRATION"));
            monitor.info("Current time: " + currentTime + " (" + Instant.now() + ")");
            
            // Specific validations
            if (iat > currentTime) {
                monitor.warning("PROBLEM: Token issued in the future (iat > now)");
            }
            if (exp > 0 && exp < currentTime) {
                monitor.warning("PROBLEM: Expired token (exp < now)");
            }
            if (iss.isEmpty() || aud.isEmpty() || sub.isEmpty()) {
                monitor.warning("PROBLEM: Required fields are empty");
            }
            
            monitor.info("=== END DEBUG TOKEN INFO ===");
            
        } catch (Exception e) {
            monitor.severe("Error decoding token for debug: " + e.getMessage(), e);
        }
    }

    private Response proxyRequest(ContainerRequestContext requestContext) {

        //monitor.info(this.influxDbEndpoint);
        //monitor.info(this.influxDbOrg);
        //monitor.info(this.influxDbBucket);
        //monitor.info(this.influxDbAdminToken);

        var token = requestContext.getHeaderString(AUTHORIZATION);
        if (token == null) {
            monitor.severe("Autorization Token NOT included");
            return Response.status(UNAUTHORIZED).build();
        }

        //monitor.info("Token recibido: " + token.substring(0, Math.min(50, token.length())) + "...");

        // for debuging
        //debugToken(token, monitor);

        var tokenXacml = requestContext.getHeaderString("X-SUBJECT-TOKEN");
        if (tokenXacml == null) {
            monitor.warning("XACML Token NOT included");
            //return Response.status(UNAUTHORIZED).build();
            //} else {
            //    monitor.info("XACML Token included");
        }

        //Standard authorization
        var authorization = authorizationService.authorize(token, emptyMap());

        /* DEPRECATED is not supported
        //Standard authorization + including Method (policies that considers methods)
        var contextProperties = new java.util.HashMap<String, Object>();
        contextProperties.put(EDC_NAMESPACE + "httpMethod", requestContext.getMethod());
        var authorization = authorizationService.authorize(token, contextProperties);
        */

        
        if (authorization.failed()) {
            monitor.severe("Standard Authorization failed for token: " + token);

            var failureDetail = authorization.getFailureDetail();
            if (failureDetail != null) {
                monitor.severe("Details authorization failure: " + failureDetail);
            }
            
            var failureMessages = authorization.getFailureMessages();
            if (failureMessages != null && !failureMessages.isEmpty()) {
                monitor.severe("Failure messages: " + String.join(", ", failureMessages));
            }

            return Response.status(FORBIDDEN).build();
        }

        //PDTE_JUAN: Perform XACML token validation (define the function)
        var authorizationXacml = true;
        if (!authorizationXacml) {
            return Response.status(FORBIDDEN).build();
        }

        var sourceDataAddress = authorization.getContent();

        monitor.info("=== sourceDataAddress properties ===");
        Boolean isInfluxDb = false;
        String paramsReq = "";
        String allowedMethods = "";
        String tokenInfluxDb = "";
        String bucketInfluxDb = "";
        for (var entry : sourceDataAddress.getProperties().entrySet()) {
            monitor.info("Clave: " + entry.getKey() + " -> Valor: " + entry.getValue());
            if ("https://w3id.org/edc/v0.0.1/ns/influxdb.isInfluxDb".equalsIgnoreCase(entry.getKey())) {
                if (Boolean.parseBoolean((String) entry.getValue())) {
                    isInfluxDb = true;
                }
            } else if ("https://w3id.org/edc/v0.0.1/ns/params".equalsIgnoreCase(entry.getKey())) {
                paramsReq = (String) entry.getValue();
            } else if ("https://w3id.org/edc/v0.0.1/ns/allowedMethods".equalsIgnoreCase(entry.getKey())) {
                allowedMethods = (String) entry.getValue();
            }  else if ("https://w3id.org/edc/v0.0.1/ns/influxdb.header.authorization".equalsIgnoreCase(entry.getKey())) {
                tokenInfluxDb = (String) entry.getValue();
            } else if ("https://w3id.org/edc/v0.0.1/ns/influxdb.bucket".equalsIgnoreCase(entry.getKey())) {
                bucketInfluxDb = (String) entry.getValue();
            }


        }
        monitor.info("=== END PROPERTIES ===");

        // Validation of allowed methods
        if (!allowedMethods.isEmpty()) {
            String currentMethod = requestContext.getMethod();
            
            // Convert the string of allowed methods to a list, removing spaces
            List<String> allowedMethodsList = Arrays.stream(allowedMethods.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .collect(Collectors.toList());
            
            // Check if the current method is in the list of allowed methods
            if (!allowedMethodsList.contains(currentMethod.toUpperCase())) {
                monitor.warning("Method not allowed: " + currentMethod + ". Allowed methods: " + allowedMethods);
                return Response.status(Response.Status.METHOD_NOT_ALLOWED)
                        .entity("{\"error\": \"Method " + currentMethod + " is not allowed. Allowed methods: " + allowedMethods + "\"}")
                        .build();
            }
            
        }

        try {

            // STEP 1. Get the address to which the forwarding should be performed
            // - Option 1: This option does NOT consider the request parameters when redirecting to the data source API.
            /*
            var targetUrl = sourceDataAddress.getStringProperty(EDC_NAMESPACE + "baseUrl") + "/" + requestContext.getUriInfo().getPath();
            */
            // - Option 2: This option DOES consider the request parameters when redirecting to the data source API.
            var targetUrlBuilder = new StringBuilder(sourceDataAddress.getStringProperty(EDC_NAMESPACE + "baseUrl"));
            // Add this line to log the value of targetUrl
            monitor.info("1 - Proxy target URL: " + targetUrlBuilder);
            var endpointUrl = requestContext.getUriInfo().getPath();
            monitor.info("2 - Endpoint: " + endpointUrl);
            targetUrlBuilder.append("/").append(requestContext.getUriInfo().getPath());
            monitor.info("3 - Proxy target URL: " + targetUrlBuilder);

            // Obtener y añadir los parámetros de la consulta
            var queryParameters = requestContext.getUriInfo().getQueryParameters();
            boolean firstParamAdded = false;

            for (var entry : queryParameters.entrySet()) {
                String key = entry.getKey();
                for (String value : entry.getValue()) {
                    if (!firstParamAdded) {
                        // If it is the first parameter of ALL, the '?' is added.
                        targetUrlBuilder.append("?");
                        firstParamAdded = true;
                    } else {
                        // If the '?' or some other parameter was already added, '&' is added
                        targetUrlBuilder.append("&");
                    }
                    targetUrlBuilder.append(key).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                }
            }
            // --- Logic for adding InfluxDB parameters ---

            if (!"".equals(paramsReq)) {

                if (!firstParamAdded) {
                    // If NO parameters have been added yet and paramsReq is not empty, we start with '?'
                    targetUrlBuilder.append("?");
                    firstParamAdded = true; // We have already added the first separator
                } else {
                    targetUrlBuilder.append("&");
                }

                String cleanParamsReq = paramsReq;

                // Remove the initial '?' or '&' if we have already handled it
                if (cleanParamsReq.startsWith("?") || cleanParamsReq.startsWith("&")) {
                    cleanParamsReq = cleanParamsReq.substring(1);
                }

                targetUrlBuilder.append(cleanParamsReq);
                // Even though it's already there, if cleanParamsReq has content, we now have parameters.
                firstParamAdded = true;

            }

            var targetUrl = targetUrlBuilder.toString();
            monitor.info("4 - Final target URL: " + targetUrl);

            // STEP 2. Prepare the request to be made (headers, params, body...)
            // - Option 1: This option does not consider the request headers when redirecting to the data source API.
            /*
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofInputStream(requestContext::getEntityStream))
                    .build();
            */
            // - Option 2: This option DOES consider the request headers when redirecting to the data source API.
            //var requestBuilder = HttpRequest.newBuilder()
            //        .uri(URI.create(targetUrl))
            //        .method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofInputStream(requestContext::getEntityStream));

            
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl));
            
            InputStream requestBodyStream = requestContext.getEntityStream();
            String originalContentType = requestContext.getHeaderString(CONTENT_TYPE);
            
            // --- Body transformation logic if it is InfluxDB Query and it is JSON ---
            if (isInfluxDb && "query".equalsIgnoreCase(endpointUrl) && originalContentType != null && originalContentType.contains("application/json")) {
                monitor.info("Detected InfluxDB query with JSON body. Attempting conversion to Flux Query.");
                try {
                    // Read the entire input stream into a String (important since it can only be read once)
                    String jsonString = new String(requestBodyStream.readAllBytes(), StandardCharsets.UTF_8);
                    
                    // Generate the Flux query using the generateFluxQuery function
                    String fluxQueryResponse = generateFluxQuery(bucketInfluxDb, jsonString);
                    
                    // Parse the response to verify if it was successful
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode responseNode = objectMapper.readTree(fluxQueryResponse);
                    
                    if (responseNode.get("status").asInt() == 0) {
                        // Conversion successful, use the generated Flux query
                        String fluxQuery = responseNode.get("message").asText();
                        monitor.info("Query Flux: " + fluxQuery);
                        
                        // Use the converted body and set the correct Content-Type for InfluxDB
                        requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofString(fluxQuery, StandardCharsets.UTF_8));
                        requestBuilder.header(CONTENT_TYPE, "application/vnd.flux");
                    } else {
                        // Conversion failed, use original body
                        String errorMessage = responseNode.get("message").asText();
                        monitor.warning("Error: Flux Query Conversion: " + errorMessage + ". Sendind original body.");
                        requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofString(jsonString, StandardCharsets.UTF_8));
                        requestBuilder.header(CONTENT_TYPE, originalContentType);
                    }
                } catch (Exception e) {
                    monitor.severe("Error: Flux Query Conversion (JSON conversion): " + e.getMessage(), e);
                    try {
                        // Fallback: Send the original body if the conversion fails completely
                        String jsonString = new String(requestBodyStream.readAllBytes(), StandardCharsets.UTF_8);
                        requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofString(jsonString, StandardCharsets.UTF_8));
                        requestBuilder.header(CONTENT_TYPE, originalContentType);
                    } catch (IOException ioException) {
                        monitor.severe("Error reading original body as fallback: " + ioException.getMessage(), ioException);
                        requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.noBody());
                    }
                }
            // --- Body transformation logic if it is InfluxDB Write and it is JSON-LD ---
            } else if (isInfluxDb && "write".equalsIgnoreCase(endpointUrl) && originalContentType != null && originalContentType.contains("application/ld+json")) {
                monitor.info("Write detected in InfluxDB with JSON-LD body. Attempting conversion..");
                try {
                    // Read the entire input stream into a String (important since it can only be read once)
                    String jsonLdString = new String(requestBodyStream.readAllBytes(), StandardCharsets.UTF_8);
                        
                    // Parse JSON-LD and convert to InfluxDB Line Protocol
                    JsonNode jsonNode = objectMapper.readTree(jsonLdString);
                    String influxLineProtocolBody = convertJsonLdToInfluxLineProtocol(jsonNode, monitor);

                    if (influxLineProtocolBody != null && !influxLineProtocolBody.isEmpty()) {
                        monitor.info("Line Protocol converted: " + influxLineProtocolBody);
                        // Use the converted body and set the correct Content-Type for InfluxDB
                        requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofString(influxLineProtocolBody, StandardCharsets.UTF_8));
                        requestBuilder.header(CONTENT_TYPE, "text/plain; charset=utf-8");
                    } else {
                        monitor.warning("Converting JSON-LD to InfluxDB resulted in an empty body. Sending the original body if it exists.");
                        //requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(jsonLdString.getBytes(StandardCharsets.UTF_8)))); // Usar el body original como fallback
                        requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofString(jsonLdString, StandardCharsets.UTF_8));
                        requestBuilder.header(CONTENT_TYPE, originalContentType);
                    }
                } catch (Exception e) {
                    monitor.severe("Error converting JSON-LD to InfluxDB: " + e.getMessage(), e);
                    // Fallback: Send the original body if the conversion fails completely
                    requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofInputStream(() -> requestBodyStream));
                    requestBuilder.header(CONTENT_TYPE, originalContentType);
                }
            } else {
                // If it is not an InfluxDB write or it is not JSON-LD, send the original body as is
                requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofInputStream(() -> requestBodyStream));
                // Copiar la cabecera Content-Type original
                if (originalContentType != null) {
                    requestBuilder.header(CONTENT_TYPE, originalContentType);
                }
            }
            // --- End of body transformation logic ---

            // Copy the headers from the original request (except for some you may not want to forward)
            requestContext.getHeaders().forEach((name, values) -> {
                // Here you can add logic to filter which headers to forward.
                //if (!name.equalsIgnoreCase(AUTHORIZATION) && !name.equalsIgnoreCase("Host") && !name.equalsIgnoreCase("Connection") && !name.equalsIgnoreCase("Content-Length")) {  // Ejemplo de exclusión                    
                if (!name.equalsIgnoreCase(AUTHORIZATION) && !name.equalsIgnoreCase("Host") && !name.equalsIgnoreCase("Connection") && !name.equalsIgnoreCase("Content-Length") && !name.equalsIgnoreCase(CONTENT_TYPE)) {  // Ejemplo de exclusión
                    values.forEach(value -> requestBuilder.header(name, value));
                }
            });

            // --- Logic to add the InfluxDB authorization header ---
            if (!"".equals(tokenInfluxDb)) {
                requestBuilder.header(AUTHORIZATION, tokenInfluxDb);
            }

            // --- End of logic for InfluxDB ---


            var request = requestBuilder.build();

            // PASO 3. Forwarding the request to obtain the information
            // 20250618
            //var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            // 20250618
            HttpResponse<InputStream> upstreamResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            // Reads the entire InputStream into a byte array
            byte[] responseBodyBytes;
            try (InputStream is = upstreamResponse.body()) {
                // For Java 9 and higher:
                responseBodyBytes = is.readAllBytes(); 

                // For Java 8 (if you can't use readAllBytes, which was added in Java 9):
                // You would need a helper library like Apache Commons IO:
                // responseBodyBytes = IOUtils.toByteArray(is);
                // Or a manual implementation:
                /*
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[1024]; // Buffer de 1KB
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                responseBodyBytes = buffer.toByteArray();
                */

            } catch (IOException e) {
                // It is crucial to handle any errors when reading the stream.
                // This could occur if the upstream unexpectedly closes the connection.
                monitor.severe("Error reading response from data source: " + e.getMessage());
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("Error processing response from external server.")
                            .build();
            }


            // STEP 4. Return the response from the data source
            // 20250618
            //return Response.status(response.statusCode())
            //        .header(CONTENT_TYPE, response.headers().firstValue(CONTENT_TYPE).orElse(APPLICATION_OCTET_STREAM))
            //        .entity(response.body())
            //        .build();
            
            // 20250618
            //return Response.status(upstreamResponse.statusCode())
            //    // Copia el Content-Type original para asegurar que el cliente sepa qué tipo de datos recibe
            //    .header(CONTENT_TYPE, upstreamResponse.headers().firstValue(CONTENT_TYPE).orElse(APPLICATION_OCTET_STREAM))
            //    // Pasa el array de bytes como la entidad de la respuesta.
            //    // Esto asegura que todos los datos están disponibles para el cliente.
            //    .entity(responseBodyBytes)
            //    .build();

            // 20250618

            if (isInfluxDb && endpointUrl.equalsIgnoreCase("query")) {
                String acceptHeader = requestContext.getHeaderString("Accept");
                if (acceptHeader != null && acceptHeader.toLowerCase().contains("application/ld+json")) {
                    String csvContent = new String(responseBodyBytes, StandardCharsets.UTF_8);
                    if (csvContent.isBlank()) {
                        return Response.status(upstreamResponse.statusCode())
                                    .header(CONTENT_TYPE, "application/ld+json")
                                    .entity("[]")  // JSON-LD vacío
                                    .build();
                    }
                    String jsonLd = convertInfluxCsvToJsonLd(csvContent, monitor);
                    return Response.status(upstreamResponse.statusCode())
                            .header(CONTENT_TYPE, "application/ld+json")
                            .entity(jsonLd)
                            .build(); // Content-Encoding is NOT copied because it no longer applies.
                }
            }

            Response.ResponseBuilder responseBuilder = Response.status(upstreamResponse.statusCode())
                    .header(CONTENT_TYPE, upstreamResponse.headers().firstValue(CONTENT_TYPE).orElse(APPLICATION_OCTET_STREAM));

            // Copy the Content-Encoding header ONLY if it exists in the InfluxDB response
            upstreamResponse.headers().firstValue(CONTENT_ENCODING).ifPresent(encoding -> 
                    responseBuilder.header(CONTENT_ENCODING, encoding)
            );

            // Optional but recommended: Also forward the Vary header if it exists.
            // The Vary header indicates to the client (and any proxies in between)
            // that the response may vary based on certain request headers (such as Accept-Encoding).
            upstreamResponse.headers().firstValue("Vary").ifPresent(vary ->
                    responseBuilder.header("Vary", vary)
            );


            return responseBuilder
                    .entity(responseBodyBytes)
                    .build();
                    

        } catch (IOException | InterruptedException e) {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("{\"error\": \"Failed to contact backend service\"}")
                    .build();
        }
    }

    /**
     * Handles GET requests, proxying them to the target data address.
     *
     * @param requestContext The Jakarta RS ContainerRequestContext.
     * @return The response from the backend service.
     */
    @GET
    public Response proxyGet(@Context ContainerRequestContext requestContext) {
        return proxyRequest(requestContext);
    }

    /**
     * Handles POST requests, proxying them to the target data address.
     *
     * @param requestContext The Jakarta RS ContainerRequestContext.
     * @return The response from the backend service.
     */
    @POST
    public Response proxyPost(@Context ContainerRequestContext requestContext) {
        return proxyRequest(requestContext);
    }

    /**
     * Handles PUT requests, proxying them to the target data address.
     *
     * @param requestContext The Jakarta RS ContainerRequestContext.
     * @return The response from the backend service.
     */
    @PUT
    public Response proxyPut(@Context ContainerRequestContext requestContext) {
        return proxyRequest(requestContext);
    }

    /**
     * Handles PATCH requests, proxying them to the target data address.
     *
     * @param requestContext The Jakarta RS ContainerRequestContext.
     * @return The response from the backend service.
     */
    @PATCH
    public Response proxyPatch(@Context ContainerRequestContext requestContext) {
        return proxyRequest(requestContext);
    }

    /**
     * Handles DELETE requests, proxying them to the target data address.
     *
     * @param requestContext The Jakarta RS ContainerRequestContext.
     * @return The response from the backend service.
     */
    @DELETE
    public Response proxyDelete(@Context ContainerRequestContext requestContext) {
        return proxyRequest(requestContext);
    }

    /*
    Converts a JSON payload to a Flux query string for InfluxDB following the specified rules.
    - "bucket" The name of the InfluxDB bucket (e.g., "test_org_bucket").
    - "jsonPayload" The JSON string containing the query parameters.
    Must contain the keys "id", "measurements" (array), and optionally "start", "stop", and "last".
    A JSON string with a "status" field (0 for success, -1 for error) and a "message" field (the Flux query or a description of the error).
    */

    /*
    String bucketName = "test_org_bucket";

    // Ejemplo 1: Complete Payload JSON
    String jsonPayload1 = "{" +
        "    \"id\": \"urn:ngsi-ld:sensor:01\"," +
        "    \"measurements\": [\"temperature\"]," +
        "    \"start\": \"2023-06-17T15:00:00Z\"," +
        "    \"stop\": \"2025-06-17T17:00:00Z\"," +
        "    \"last\": false" +
    "}";

    System.out.println("--- Example 1: Complete Payload ---");
    String resultJson1 = generateFluxQuery(bucketName, jsonPayload1);
    */

    public static String generateFluxQuery(String bucket, String jsonPayload) {
        StringBuilder queryBuilder = new StringBuilder();
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode responseJson = objectMapper.createObjectNode();

        try {
            JsonNode json = objectMapper.readTree(jsonPayload);

            // 1. from(bucket: "...")
            queryBuilder.append(String.format("from(bucket: \"%s\")", bucket));

            // 2. range(start: ..., stop: ...)
            String startValue = json.has("start") ? json.get("start").asText("1970-01-01T00:00:00Z") : "1970-01-01T00:00:00Z";
            String stopValue = json.has("stop") ? json.get("stop").asText() : null;

            // Ensure that 'Z' is present only if it is not already present in the string
            String formattedStart = startValue.endsWith("Z") ? startValue : startValue + "Z";

            queryBuilder.append("\n    |> range(start: ").append(formattedStart);
            if (stopValue != null && !stopValue.isEmpty()) {
                String formattedStop = stopValue.endsWith("Z") ? stopValue : stopValue + "Z";
                queryBuilder.append(", stop: ").append(formattedStop).append(")");
            } else {
                queryBuilder.append(")");
            }

            // 3. filter(fn: (r) => r.deviceId == bodyJSON.id)
            String entityId = json.has("id") ? json.get("id").asText() : null;
            if (entityId == null || entityId.isEmpty()) {
                responseJson.put("status", -1);
                responseJson.put("message", "Error: The 'id' field is required in the JSON payload.");
                return responseJson.toString();
            }
            queryBuilder.append(String.format("\n    |> filter(fn: (r) => r.deviceId == \"%s\")", entityId));

            // 4. filter(fn: (r) => r._measurement == measurements[0] or ...)
            JsonNode measurementsNode = json.get("measurements");
            if (measurementsNode == null || !measurementsNode.isArray()) {
                responseJson.put("status", -1);
                responseJson.put("message", "Error: The 'measurements' field is required in the JSON payload and must be an array.");
                return responseJson.toString();
            }

            ArrayNode measurementsArray = (ArrayNode) measurementsNode;
            if (measurementsArray.size() > 0) {
                List<String> measurementFilters = new ArrayList<>();
                for (JsonNode measurementNode : measurementsArray) {
                    measurementFilters.add(String.format("r._measurement == \"%s\"", measurementNode.asText()));
                }
                queryBuilder.append(String.format("\n    |> filter(fn: (r) => %s)", String.join(" or ", measurementFilters)));
            }
            // If measurementsArray is empty, no measurement filter is added.

            // 5. last()
            boolean lastFlag = json.has("last") ? json.get("last").asBoolean(false) : false;
            if (lastFlag) {
                queryBuilder.append("\n    |> group(columns: [\"_measurement\"])");
                queryBuilder.append("\n    |> last()");
            }

            // 6. Eliminate fields that do not contribute anything to the output, such as the date range of the query, the name of the field where the value is located, etc., so that the response does not weigh so much.
            queryBuilder.append("\n    |> drop(columns: [\"_start\", \"_stop\", \"_field\"])");

            // 7. yield(name: "data_...")
            //queryBuilder.append(String.format("\n    |> yield(name: \"data_%s\")", entityId));
            queryBuilder.append(String.format("\n    |> yield(name: \"data\")"));

            responseJson.put("status", 0);
            responseJson.put("message", queryBuilder.toString());
            return responseJson.toString();

        } catch (Exception e) {
            responseJson.put("status", -1);
            responseJson.put("message", "Error parsing JSON: " + e.getMessage());
            return responseJson.toString();
        }
    }

    // --- JSON-LD to InfluxDB Line Protocol conversion function ---
    private String convertJsonLdToInfluxLineProtocol(JsonNode jsonNode, Monitor monitor) {
        StringBuilder lineProtocol = new StringBuilder();
        
        // If the top level is an array (multiple observations in a JSON-LD document)
        if (jsonNode.isArray()) {
            for (JsonNode observationNode : jsonNode) {
                String singleLine = parseSingleObservation(observationNode, monitor);
                if (singleLine != null) {
                    lineProtocol.append(singleLine).append("\n");
                }
            }
        } else { // Single observation object
            String singleLine = parseSingleObservation(jsonNode, monitor);
            if (singleLine != null) {
                lineProtocol.append(singleLine).append("\n");
            }
        }
        
        // Remove trailing line break if present
        if (lineProtocol.length() > 0 && lineProtocol.charAt(lineProtocol.length() - 1) == '\n') {
            lineProtocol.setLength(lineProtocol.length() - 1);
        }
        
        return lineProtocol.toString();
    }

    private String parseSingleObservation(JsonNode observationNode, Monitor monitor) {
        try {
            String measurement = extractLocalName(observationNode.get("sosa:observedProperty").get("@id").asText());
            String deviceId = extractLocalName(observationNode.get("sosa:madeBySensor").get("@id").asText());

            // Extract value
            double value = observationNode.get("sosa:hasResult").get("om:hasValue").get("om:hasSimpleValue").asDouble();

            // Extract unit
            String unit = extractLocalName(observationNode
                    .get("sosa:hasResult")
                    .get("om:hasValue")
                    .get("om:hasUnit")
                    .get("@id")
                    .asText());

            // Extract timestamp and nanoseconds conversion
            String timestampIso = observationNode.get("sosa:resultTime").get("@value").asText();
            long timestampNano = Instant.parse(timestampIso).toEpochMilli() * 1_000_000L;

            // Build Line Protocol
            return String.format("%s,deviceId=%s,unit=%s value=%s %d",
                measurement,
                escapeTagValue(deviceId),
                escapeTagValue(unit),
                value,
                timestampNano);

        } catch (Exception e) {
            monitor.warning("Failed to parse an individual W3C observation: " + e.getMessage() + ". Node: " + observationNode.toString());
            return null;
        }
    }

    private String extractLocalName(String uri) {
        if (uri == null) return null;
        
        // If it contains a prefix like 'ex:AirTemperature' or 'qudt:Percent'
        if (uri.contains(":") && !uri.startsWith("http")) {
            return uri.substring(uri.indexOf(':') + 1);
        }

        // If it is a standard URI
        int hashIndex = uri.lastIndexOf('#');
        int slashIndex = uri.lastIndexOf('/');
        if (hashIndex != -1 && hashIndex > slashIndex) {
            return uri.substring(hashIndex + 1);
        }
        return uri.substring(slashIndex + 1);
    }
    
    // InfluxDB Line Protocol requires escaping special characters for tags
    private String escapeTagValue(String value) {
        if (value == null) {
            return "";
        }
        // Escape spaces, commas, equal signs, double quotes, and backslashes
        return value.replace(" ", "\\ ")
                    .replace(",", "\\,")
                    .replace("=", "\\=")
                    .replace("\"", "\\\"")
                    .replace("\\", "\\\\"); // Escape trailing backslashes
    }

    /**
     * Converts InfluxDB CSV content to JSON-LD format.
     * This is the reverse of the JSON-LD to Line Protocol conversion.
     *
     * @param csv The InfluxDB CSV content.
     * @param monitor The monitor for logs.
     * @return A JSON-LD string representing the observations.
     */
    private String convertInfluxCsvToJsonLd(String csv, Monitor monitor) {
        ArrayNode observations = objectMapper.createArrayNode();

        String[] lines = csv.split("\n");
        if (lines.length < 2) return "[]"; // No hay datos o solo cabecera

        String[] headers = lines[0].split(",", -1);

        // Removes empty columns at the beginning (like in ",result,...") also handles if the first field is "result" or similar that we don't want in the headers
        int startIndex = 0;
        if (headers.length > 0 && (headers[0].isBlank() || headers[0].equalsIgnoreCase("#datatype") || headers[0].equalsIgnoreCase("result"))) {
            for (int i = 0; i < headers.length; i++) {
                // We look for the index of the "_time" column or the first header that is not "result" or "#datatype"
                // This ensures that the startIndex is correct for the data.
                if (headers[i].equalsIgnoreCase("_time") || (!headers[i].equalsIgnoreCase("result") && !headers[i].equalsIgnoreCase("#datatype") && !headers[i].isBlank())) {
                    startIndex = i;
                    break;
                }
            }
        }
        // We create a new headers array that only contains the relevant headers starting from the startIndex
        String[] actualHeaders = Arrays.copyOfRange(headers, startIndex, headers.length);

        //Performs cleaning of special characters that can be in column names and removes blank spaces
        for (int i = 0; i < actualHeaders.length; i++) {
            //monitor.warning(actualHeaders[i]);
            actualHeaders[i] = actualHeaders[i].trim().replaceAll("[\\n\\r\\t]", "").replaceAll("\\s+", " ");
        }

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue; // Salta líneas vacías

            String[] values = line.split(",", -1);

            // Adjusts values ​​to match headers (if startIndex > 0)
            if (startIndex > 0 && values.length > startIndex) {
                values = Arrays.copyOfRange(values, startIndex, values.length);
            } else if (startIndex > 0 && values.length <= startIndex) {
                continue; // Not enough values ​​for the fitted headers
            }

            // If the number of values ​​doesn't match the number of headers, something is wrong with the line.
            // This can occur with metadata lines in the CSV that aren't data observations.
            if (values.length != actualHeaders.length) {
                monitor.warning("The CSV line has an inconsistent number of values ​​in the headers, skipping: " + line);
                continue;
            }

            try {
                ObjectNode obs = objectMapper.createObjectNode();
                obs.put("@type", "sosa:Observation");
                // Generate a unique @id for the observation
                String timeValue = values[headersIndex(actualHeaders, "_time")];
                String measurementValue = values[headersIndex(actualHeaders, "_measurement")];
                String deviceIdValue = values[headersIndex(actualHeaders, "deviceId")];
                // Build a more meaningful ID if possible
                obs.put("@id", "ex:Observation_" + measurementValue + "_" + deviceIdValue + "_" + timeValue.replaceAll("[^a-zA-Z0-9]", ""));

                // Define the @context inside each observation to self-contain
                ObjectNode context = objectMapper.createObjectNode();
                context.put("sosa", "http://www.w3.org/ns/sosa/");
                context.put("qudt", "http://qudt.org/vocab/unit/");
                context.put("xsd", "http://www.w3.org/2001/XMLSchema#");
                context.put("ex", "http://example.com/data/sensor#");
                context.put("om", "http://www.opengis.net/ont/om/2.0/");
                obs.set("@context", context);

                // sosa:observedProperty
                obs.set("sosa:observedProperty", objectMapper.createObjectNode().put("@id", "ex:" + values[headersIndex(actualHeaders, "_measurement")]));

                // sosa:madeBySensor
                obs.set("sosa:madeBySensor", objectMapper.createObjectNode().put("@id", "ex:" + values[headersIndex(actualHeaders, "deviceId")]));

                // sosa:resultTime
                ObjectNode resultTime = objectMapper.createObjectNode();
                resultTime.put("@type", "xsd:dateTime");
                resultTime.put("@value", values[headersIndex(actualHeaders, "_time")]);
                obs.set("sosa:resultTime", resultTime);

                // sosa:hasResult
                ObjectNode hasResult = objectMapper.createObjectNode();
                hasResult.put("@type", "sosa:Result");

                ObjectNode hasValue = objectMapper.createObjectNode();
                hasValue.put("@type", "om:Measure");
                hasValue.put("om:hasSimpleValue", Double.parseDouble(values[headersIndex(actualHeaders, "_value")]));

                // Add om:hasUnit if exists
                int unitIndex = headersIndex(actualHeaders, "unit");

                //monitor.warning(String.valueOf(unitIndex));

                if (unitIndex != -1 && unitIndex < values.length) {
                    hasValue.set("om:hasUnit", objectMapper.createObjectNode().put("@id", "qudt:" + values[unitIndex]));
                }

                hasResult.set("om:hasValue", hasValue);
                obs.set("sosa:hasResult", hasResult);

                observations.add(obs);
            } catch (Exception e) {
                monitor.warning("Failed to parse a single InfluxDB CSV line: " + e.getMessage() + ". Line: " + line);
            }
        }

        return observations.toPrettyString();
    }

    /**
    * Finds the index of a key in an array of headers (case-insensitive).
    *
    * @param headers Array of headers.
    * @param key The key to find.
    * @return The index of the key, or -1 if not found.
    */
    private int headersIndex(String[] headers, String key) {
        //System.out.println("headersIndex: '" + key + "' " + key.length());
        for (int i = 0; i < headers.length; i++) {
            //System.out.println("headersIndex - : '" + headers[i] + "' " + headers[i].length());
            if (headers[i].equalsIgnoreCase(key)) return i;
        }
        //System.out.println("headersIndex: Not found");
        return -1;
    }

}
