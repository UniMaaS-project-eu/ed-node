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
     * Obtiene un valor de configuración dando prioridad a la variable de entorno.
     * Si la variable de entorno no está definida, busca en la configuración del sistema.
     * Si no hay configuración, utiliza el valor por defecto.
     */
    private String getConfigValue(ServiceExtensionContext context, String envKey, String configKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return context.getConfig().getString(configKey, defaultValue);
    }

    // Método para debugging del token - agregar al ProxyController
    private void debugToken(String token, Monitor monitor) {
        try {
            // Remover "Bearer " si está presente
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            // Dividir el JWT en sus partes
            String[] parts = cleanToken.split("\\.");
            if (parts.length != 3) {
                monitor.severe("Token JWT no tiene 3 partes: " + parts.length);
                return;
            }
            
            // Decodificar el payload (segunda parte)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            monitor.info("Payload del token: " + payload);
            
            // Parsear el payload JSON
            JsonNode payloadJson = objectMapper.readTree(payload);
            
            // Verificar campos importantes
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
            
            // Validaciones específicas
            if (iat > currentTime) {
                monitor.warning("PROBLEMA: Token emitido en el futuro (iat > now)");
            }
            if (exp > 0 && exp < currentTime) {
                monitor.warning("PROBLEMA: Token expirado (exp < now)");
            }
            if (iss.isEmpty() || aud.isEmpty() || sub.isEmpty()) {
                monitor.warning("PROBLEMA: Campos obligatorios vacíos");
            }
            
            monitor.info("=== FIN DEBUG TOKEN INFO ===");
            
        } catch (Exception e) {
            monitor.severe("Error decodificando token para debug: " + e.getMessage(), e);
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
        //Para añadir el método a la autorización
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

        //PDTE_JUAN: Realizar la validación del XACML token (definir la función)
        var authorizationXacml = true;
        if (!authorizationXacml) {
            return Response.status(FORBIDDEN).build();
        }

        var sourceDataAddress = authorization.getContent();

        monitor.info("=== PROPIEDADES DE sourceDataAddress ===");
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
        monitor.info("=== FIN DE LAS PROPIEDADES ===");

        // Validación de métodos permitidos
        if (!allowedMethods.isEmpty()) {
            String currentMethod = requestContext.getMethod();
            
            // Convertir la cadena de métodos permitidos en una lista, eliminando espacios
            List<String> allowedMethodsList = Arrays.stream(allowedMethods.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .collect(Collectors.toList());
            
            // Verificar si el método actual está en la lista de métodos permitidos
            if (!allowedMethodsList.contains(currentMethod.toUpperCase())) {
                monitor.warning("Method not allowed: " + currentMethod + ". Allowed methods: " + allowedMethods);
                return Response.status(Response.Status.METHOD_NOT_ALLOWED)
                        .entity("{\"error\": \"Method " + currentMethod + " is not allowed. Allowed methods: " + allowedMethods + "\"}")
                        .build();
            }
            
            //monitor.info("Método " + currentMethod + " validado correctamente contra métodos permitidos: " + allowedMethods);
        }

        try {

            // PASO 1. Obtiene la dirección donde se ha de realizar el reenvío
            // - Opcion 1: Esta opción NO considera los parámetros de la request a la hora de realizar la redirección a la API de la fuente de datos.
            /*
            var targetUrl = sourceDataAddress.getStringProperty(EDC_NAMESPACE + "baseUrl") + "/" + requestContext.getUriInfo().getPath();
            */
            // - Opcion 2: Esta opción SI considera los parámetros de la request a la hora de realizar la redirección a la API de la fuente de datos.
            var targetUrlBuilder = new StringBuilder(sourceDataAddress.getStringProperty(EDC_NAMESPACE + "baseUrl"));
            // Añade esta línea para loguear el valor de targetUrl
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
                        // Si es el primer parámetro de TODOS, se añade el '?'
                        targetUrlBuilder.append("?");
                        firstParamAdded = true;
                    } else {
                        // Si ya se añadió el '?' o algún otro parámetro, se añade '&'
                        targetUrlBuilder.append("&");
                    }
                    targetUrlBuilder.append(key).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                }
            }
            // --- Lógica para añadir los parámetros de InfluxDB ---

            if (!"".equals(paramsReq)) {

                if (!firstParamAdded) {
                    // Si no se ha añadido NINGÚN parámetro todavía
                    // y paramsReq no está vacío, empezamos con '?'
                    targetUrlBuilder.append("?");
                    firstParamAdded = true; // Ya hemos añadido el primer separador
                } else {
                    targetUrlBuilder.append("&");
                }

                String cleanParamsReq = paramsReq;

                // Eliminar '?' o '&' inicial si ya lo hemos gestionado
                if (cleanParamsReq.startsWith("?") || cleanParamsReq.startsWith("&")) {
                    cleanParamsReq = cleanParamsReq.substring(1);
                }

                targetUrlBuilder.append(cleanParamsReq);
                // Aunque ya esté, si cleanParamsReq tiene contenido, ahora sí tenemos parámetros.
                firstParamAdded = true;

            }

            var targetUrl = targetUrlBuilder.toString();
            monitor.info("4 - Final target URL: " + targetUrl);

            // PASO 2. Prepara la petición a realizar (headers, params, body... )
            // - Opcion 1: Esta opción no considera las cabeceras de la request a la hora de realizar la redirección a la API de la fuente de datos.
            /*
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofInputStream(requestContext::getEntityStream))
                    .build();
            */
            // - Opcion 2: Esta opción SI considera las cabeceras de la request a la hora de realizar la redirección a la API de la fuente de datos.
            //var requestBuilder = HttpRequest.newBuilder()
            //        .uri(URI.create(targetUrl))
            //        .method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofInputStream(requestContext::getEntityStream));

            
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl));
            
            InputStream requestBodyStream = requestContext.getEntityStream();
            String originalContentType = requestContext.getHeaderString(CONTENT_TYPE);
            
            // --- Lógica de transformación del body si es InfluxDB Query y es JSON ---
            if (isInfluxDb && "query".equalsIgnoreCase(endpointUrl) && originalContentType != null && originalContentType.contains("application/json")) {
                monitor.info("Detectada consulta en InfluxDB con body JSON. Intentando conversión a Flux Query.");
                try {
                    // Leer el flujo de entrada completo en un String (importante ya que solo se puede leer una vez)
                    String jsonString = new String(requestBodyStream.readAllBytes(), StandardCharsets.UTF_8);
                    
                    // Generar la consulta Flux usando la función generateFluxQuery
                    String fluxQueryResponse = generateFluxQuery(bucketInfluxDb, jsonString);
                    
                    // Parsear la respuesta para verificar si fue exitosa
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode responseNode = objectMapper.readTree(fluxQueryResponse);
                    
                    if (responseNode.get("status").asInt() == 0) {
                        // Conversión exitosa, usar la consulta Flux generada
                        String fluxQuery = responseNode.get("message").asText();
                        monitor.info("Consulta Flux generada: " + fluxQuery);
                        
                        // Usar el body convertido y establecer el Content-Type correcto para InfluxDB
                        requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofString(fluxQuery, StandardCharsets.UTF_8));
                        requestBuilder.header(CONTENT_TYPE, "application/vnd.flux");
                    } else {
                        // Error en la conversión, usar el body original
                        String errorMessage = responseNode.get("message").asText();
                        monitor.warning("Error en la conversión a Flux Query: " + errorMessage + ". Enviando el body original.");
                        requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofString(jsonString, StandardCharsets.UTF_8));
                        requestBuilder.header(CONTENT_TYPE, originalContentType);
                    }
                } catch (Exception e) {
                    monitor.severe("Error durante la conversión de JSON a Flux Query: " + e.getMessage(), e);
                    try {
                        // Fallback: Enviar el body original si la conversión falla completamente
                        String jsonString = new String(requestBodyStream.readAllBytes(), StandardCharsets.UTF_8);
                        requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofString(jsonString, StandardCharsets.UTF_8));
                        requestBuilder.header(CONTENT_TYPE, originalContentType);
                    } catch (IOException ioException) {
                        monitor.severe("Error al leer el body original como fallback: " + ioException.getMessage(), ioException);
                        requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.noBody());
                    }
                }
            // --- Lógica de transformación del body si es InfluxDB Write y es JSON-LD ---
            } else if (isInfluxDb && "write".equalsIgnoreCase(endpointUrl) && originalContentType != null && originalContentType.contains("application/ld+json")) {
                monitor.info("Detectada escritura en InfluxDB con body JSON-LD. Intentando conversión.");
                try {
                    // Leer el flujo de entrada completo en un String (importante ya que solo se puede leer una vez)
                    String jsonLdString = new String(requestBodyStream.readAllBytes(), StandardCharsets.UTF_8);
                        
                    // Parsear JSON-LD y convertir a InfluxDB Line Protocol
                    JsonNode jsonNode = objectMapper.readTree(jsonLdString);
                    String influxLineProtocolBody = convertJsonLdToInfluxLineProtocol(jsonNode, monitor); // Llamar a la función de conversión

                    if (influxLineProtocolBody != null && !influxLineProtocolBody.isEmpty()) {
                        monitor.info("Line Protocol convertido: " + influxLineProtocolBody);
                        // Usar el body convertido y establecer el Content-Type correcto para InfluxDB
                        requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofString(influxLineProtocolBody, StandardCharsets.UTF_8));
                        requestBuilder.header(CONTENT_TYPE, "text/plain; charset=utf-8");
                    } else {
                        monitor.warning("La conversión de JSON-LD a InfluxDB resultó en un body vacío. Enviando el body original si existe.");
                        //requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(jsonLdString.getBytes(StandardCharsets.UTF_8)))); // Usar el body original como fallback
                        requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofString(jsonLdString, StandardCharsets.UTF_8));
                        requestBuilder.header(CONTENT_TYPE, originalContentType);
                    }
                } catch (Exception e) {
                    monitor.severe("Error durante la conversión de JSON-LD a InfluxDB: " + e.getMessage(), e);
                    // Fallback: Enviar el body original si la conversión falla completamente
                    requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofInputStream(() -> requestBodyStream));
                    requestBuilder.header(CONTENT_TYPE, originalContentType);
                }
            } else {
                // Si no es escritura de InfluxDB o no es JSON-LD, enviar el body original tal cual
                requestBuilder.method(requestContext.getMethod(), HttpRequest.BodyPublishers.ofInputStream(() -> requestBodyStream));
                // Copiar la cabecera Content-Type original
                if (originalContentType != null) {
                    requestBuilder.header(CONTENT_TYPE, originalContentType);
                }
            }
            // --- Fin de la lógica de transformación del body ---

            // Copiar las cabeceras de la solicitud original (excepto algunas que quizás no quieras reenviar)
            requestContext.getHeaders().forEach((name, values) -> {
                // Aquí puedes añadir lógica para filtrar qué cabeceras reenviar
                //if (!name.equalsIgnoreCase(AUTHORIZATION) && !name.equalsIgnoreCase("Host") && !name.equalsIgnoreCase("Connection") && !name.equalsIgnoreCase("Content-Length")) {  // Ejemplo de exclusión                    
                if (!name.equalsIgnoreCase(AUTHORIZATION) && !name.equalsIgnoreCase("Host") && !name.equalsIgnoreCase("Connection") && !name.equalsIgnoreCase("Content-Length") && !name.equalsIgnoreCase(CONTENT_TYPE)) {  // Ejemplo de exclusión
                    values.forEach(value -> requestBuilder.header(name, value));
                }
            });

            // --- Lógica para añadir el header de autorización de InfluxDB ---
            if (!"".equals(tokenInfluxDb)) {
                requestBuilder.header(AUTHORIZATION, tokenInfluxDb);
            }

            /*
            if (isInfluxDb && ("write".equalsIgnoreCase(endpointUrl) || "query".equalsIgnoreCase(endpointUrl))) {
                // Sobrescribe cualquier header Authorization existente o lo añade.
                // InfluxDB usa el formato "Token <token>"

                

                requestBuilder.header(AUTHORIZATION, "Token " + this.influxDbAdminToken);
                //monitor.info("Añadido header de autorización para InfluxDB.");
            }
            */
            // --- Fin de la lógica para InfluxDB ---


            var request = requestBuilder.build();

            // PASO 3. Reenvio de la petición para obtener la información
            // 20250618
            //var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            // 20250618
            HttpResponse<InputStream> upstreamResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            // Lee completamente el InputStream en un array de bytes
            byte[] responseBodyBytes;
            try (InputStream is = upstreamResponse.body()) {
                // Para Java 9 y superior:
                responseBodyBytes = is.readAllBytes(); 

                // Para Java 8 (si no puedes usar readAllBytes, que fue añadido en Java 9):
                // Necesitarías una librería auxiliar como Apache Commons IO:
                // responseBodyBytes = IOUtils.toByteArray(is); 
                // O una implementación manual:
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
                // Es crucial manejar cualquier error al leer el stream.
                // Esto podría ocurrir si el upstream cierra la conexión inesperadamente.
                monitor.severe("Error al leer la respuesta de la fuente de datos: " + e.getMessage());
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("Error al procesar la respuesta del servidor externo.")
                            .build();
            }


            // PASO 4. Devuelve la respuesta de la fuente de datos
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
                            .build(); // NO se copia Content-Encoding porque ya no aplica
                }
            }

            Response.ResponseBuilder responseBuilder = Response.status(upstreamResponse.statusCode())
                    .header(CONTENT_TYPE, upstreamResponse.headers().firstValue(CONTENT_TYPE).orElse(APPLICATION_OCTET_STREAM));

            // Copia la cabecera Content-Encoding SOLO si existe en la respuesta de InfluxDB
            upstreamResponse.headers().firstValue(CONTENT_ENCODING).ifPresent(encoding -> 
                    responseBuilder.header(CONTENT_ENCODING, encoding)
            );

            // Opcional pero recomendado: Reenvía también la cabecera Vary si existe.
            // La cabecera Vary indica al cliente (y a los proxies en medio)
            // que la respuesta puede variar en función de ciertos encabezados de la petición (como Accept-Encoding).
            upstreamResponse.headers().firstValue("Vary").ifPresent(vary ->
                    responseBuilder.header("Vary", vary)
            );


            return responseBuilder
                    .entity(responseBodyBytes) // Los bytes (comprimidos o no)
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
    Convierte un payload JSON en una cadena de consulta Flux para InfluxDB siguiendo las reglas especificadas.
    - "bucket" El nombre del bucket de InfluxDB (ej. "test_org_bucket").
    - "jsonPayload" La cadena JSON que contiene los parámetros de la consulta.
    Debe contener las claves "id", "measurements" (array) y opcionalmente "start", "stop", "last".
    Una cadena JSON con un campo "status" (0 para éxito, -1 para error) y un campo "message" (la consulta Flux o una descripción del error).
    */

    /*
    String bucketName = "test_org_bucket";

    // Ejemplo 1: Payload JSON completo
    String jsonPayload1 = "{" +
        "    \"id\": \"urn:ngsi-ld:sensor:01\"," +
        "    \"measurements\": [\"temperature\"]," +
        "    \"start\": \"2023-06-17T15:00:00Z\"," +
        "    \"stop\": \"2025-06-17T17:00:00Z\"," +
        "    \"last\": false" +
    "}";

    System.out.println("--- Ejemplo 1: Payload Completo ---");
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

            // Asegurar que 'Z' esté presente solo si no lo está ya en la cadena
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
                responseJson.put("message", "Error: El campo 'id' es obligatorio en el payload JSON.");
                return responseJson.toString();
            }
            queryBuilder.append(String.format("\n    |> filter(fn: (r) => r.deviceId == \"%s\")", entityId));

            // 4. filter(fn: (r) => r._measurement == measurements[0] or ...)
            JsonNode measurementsNode = json.get("measurements");
            if (measurementsNode == null || !measurementsNode.isArray()) {
                responseJson.put("status", -1);
                responseJson.put("message", "Error: El campo 'measurements' es obligatorio en el payload JSON y debe ser un array.");
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
            // Si measurementsArray está vacío, no se añade ningún filtro de measurement.

            // 5. last()
            boolean lastFlag = json.has("last") ? json.get("last").asBoolean(false) : false;
            if (lastFlag) {
                queryBuilder.append("\n    |> group(columns: [\"_measurement\"])");
                queryBuilder.append("\n    |> last()");
            }

            // 6. Elimina los campos que no aportan nada en la salida, de rango de fechas de la consulta, nombre del campo donde está el valor,... para que no pese tanto la respuesta
            queryBuilder.append("\n    |> drop(columns: [\"_start\", \"_stop\", \"_field\"])");

            // 7. yield(name: "data_...")
            //queryBuilder.append(String.format("\n    |> yield(name: \"data_%s\")", entityId));
            queryBuilder.append(String.format("\n    |> yield(name: \"data\")"));

            responseJson.put("status", 0);
            responseJson.put("message", queryBuilder.toString());
            return responseJson.toString();

        } catch (Exception e) {
            responseJson.put("status", -1);
            responseJson.put("message", "Error de parseo JSON: " + e.getMessage());
            return responseJson.toString();
        }
    }

    // --- Función de conversión JSON-LD a InfluxDB Line Protocol ---
    private String convertJsonLdToInfluxLineProtocol(JsonNode jsonNode, Monitor monitor) {
        StringBuilder lineProtocol = new StringBuilder();
        
        // Si el nivel superior es un array (múltiples observaciones en un documento JSON-LD)
        if (jsonNode.isArray()) {
            for (JsonNode observationNode : jsonNode) {
                String singleLine = parseSingleObservation(observationNode, monitor);
                if (singleLine != null) {
                    lineProtocol.append(singleLine).append("\n");
                }
            }
        } else { // Objeto de observación único
            String singleLine = parseSingleObservation(jsonNode, monitor);
            if (singleLine != null) {
                lineProtocol.append(singleLine).append("\n");
            }
        }
        
        // Eliminar salto de línea final si está presente
        if (lineProtocol.length() > 0 && lineProtocol.charAt(lineProtocol.length() - 1) == '\n') {
            lineProtocol.setLength(lineProtocol.length() - 1);
        }
        
        return lineProtocol.toString();
    }

    /*
    private String parseSingleObservation(JsonNode observationNode, Monitor monitor) {
        try {
            // Reglas de mapeo basadas en el ejemplo JSON-LD proporcionado (SOSA/OM)
            String measurement = extractLocalName(observationNode.get("sosa:observedProperty").get("@id").asText());
            String deviceId = extractLocalName(observationNode.get("sosa:madeBySensor").get("@id").asText());
            
            // Suponiendo sosa:hasResult -> om:hasValue -> om:hasSimpleValue
            double value = observationNode.get("sosa:hasResult").get("om:hasValue").get("om:hasSimpleValue").asDouble();
            
            // Marca de tiempo
            String timestampIso = observationNode.get("sosa:resultTime").get("@value").asText();
            // Convertir a nanosegundos Unix (InfluxDB por defecto)
            long timestampNano = Instant.parse(timestampIso).toEpochMilli() * 1_000_000L; 

            // Construir Line Protocol: measurement,tagKey=tagValue fieldKey=fieldValue timestamp
            return String.format("%s,deviceId=%s value=%s %d",
                                 measurement,
                                 escapeTagValue(deviceId), // Escapar el valor del tag
                                 value,
                                 timestampNano);

        } catch (Exception e) {
            monitor.warning("Fallo al parsear una observación W3C individual: " + e.getMessage() + ". Nodo: " + observationNode.toString());
            return null; // Devolver null si el parseo falla para una observación
        }
    }
    */

    private String parseSingleObservation(JsonNode observationNode, Monitor monitor) {
        try {
            // Reglas de mapeo basadas en el ejemplo JSON-LD proporcionado (SOSA/OM)
            String measurement = extractLocalName(observationNode.get("sosa:observedProperty").get("@id").asText());
            String deviceId = extractLocalName(observationNode.get("sosa:madeBySensor").get("@id").asText());

            // Extraer valor
            double value = observationNode.get("sosa:hasResult").get("om:hasValue").get("om:hasSimpleValue").asDouble();

            // Extraer unidad
            String unit = extractLocalName(observationNode
                    .get("sosa:hasResult")
                    .get("om:hasValue")
                    .get("om:hasUnit")
                    .get("@id")
                    .asText());

            // Extraer timestamp y convertir a nanosegundos
            String timestampIso = observationNode.get("sosa:resultTime").get("@value").asText();
            long timestampNano = Instant.parse(timestampIso).toEpochMilli() * 1_000_000L;

            // Construir Line Protocol
            return String.format("%s,deviceId=%s,unit=%s value=%s %d",
                measurement,
                escapeTagValue(deviceId),
                escapeTagValue(unit),
                value,
                timestampNano);

        } catch (Exception e) {
            monitor.warning("Fallo al parsear una observación W3C individual: " + e.getMessage() + ". Nodo: " + observationNode.toString());
            return null;
        }
    }

    private String extractLocalName(String uri) {
        if (uri == null) return null;
        
        // Si contiene prefijo tipo 'ex:AirTemperature' o 'qudt:Percent'
        if (uri.contains(":") && !uri.startsWith("http")) {
            return uri.substring(uri.indexOf(':') + 1);
        }

        // Si es una URI estándar
        int hashIndex = uri.lastIndexOf('#');
        int slashIndex = uri.lastIndexOf('/');
        if (hashIndex != -1 && hashIndex > slashIndex) {
            return uri.substring(hashIndex + 1);
        }
        return uri.substring(slashIndex + 1);
    }
    
    // InfluxDB Line Protocol requiere escapar caracteres especiales para tags
    private String escapeTagValue(String value) {
        if (value == null) {
            return "";
        }
        // Escapar espacios, comas, signos de igual, comillas dobles y barras invertidas
        return value.replace(" ", "\\ ")
                    .replace(",", "\\,")
                    .replace("=", "\\=")
                    .replace("\"", "\\\"")
                    .replace("\\", "\\\\"); // Escapar barras invertidas al final
    }

    /**
     * Convierte contenido CSV de InfluxDB a formato JSON-LD.
     * Este es el reverso de la conversión de JSON-LD a Line Protocol.
     *
     * @param csv El contenido CSV de InfluxDB.
     * @param monitor El monitor para logs.
     * @return Una cadena JSON-LD representando las observaciones.
     */
    private String convertInfluxCsvToJsonLd(String csv, Monitor monitor) {
        ArrayNode observations = objectMapper.createArrayNode();

        String[] lines = csv.split("\n");
        if (lines.length < 2) return "[]"; // No hay datos o solo cabecera

        String[] headers = lines[0].split(",", -1);

        // Elimina columnas vacías al principio (como en ",result,...")
        // También maneja si el primer campo es "result" o similar que no queremos en los headers
        int startIndex = 0;
        if (headers.length > 0 && (headers[0].isBlank() || headers[0].equalsIgnoreCase("#datatype") || headers[0].equalsIgnoreCase("result"))) {
            for (int i = 0; i < headers.length; i++) {
                // Buscamos el índice de la columna "_time" o el primer encabezado que no sea "result" o "#datatype"
                // Esto nos asegura que el startIndex es correcto para los datos.
                if (headers[i].equalsIgnoreCase("_time") || (!headers[i].equalsIgnoreCase("result") && !headers[i].equalsIgnoreCase("#datatype") && !headers[i].isBlank())) {
                    startIndex = i;
                    break;
                }
            }
        }
        // Creamos un nuevo array de headers que solo contenga los encabezados relevantes a partir del startIndex
        String[] actualHeaders = Arrays.copyOfRange(headers, startIndex, headers.length);

        //Realiza limpieza de caracteres especiales que puede hacer en el nombre de las columnas y quita espacios en blanco
        for (int i = 0; i < actualHeaders.length; i++) {
            //monitor.warning(actualHeaders[i]);
            actualHeaders[i] = actualHeaders[i].trim().replaceAll("[\\n\\r\\t]", "").replaceAll("\\s+", " ");
        }

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue; // Salta líneas vacías

            String[] values = line.split(",", -1);

            // Ajusta los valores para que coincidan con los headers (si startIndex > 0)
            if (startIndex > 0 && values.length > startIndex) {
                values = Arrays.copyOfRange(values, startIndex, values.length);
            } else if (startIndex > 0 && values.length <= startIndex) {
                continue; // No hay suficientes valores para los headers ajustados
            }

            // Si el número de valores no coincide con el número de encabezados, algo anda mal con la línea.
            // Esto puede ocurrir con líneas de metadata en el CSV que no son observaciones de datos.
            if (values.length != actualHeaders.length) { // Usar actualHeaders aquí
                monitor.warning("La línea CSV tiene un número inconsistente de valores respecto a los encabezados, saltando: " + line);
                continue;
            }

            try {
                ObjectNode obs = objectMapper.createObjectNode();
                obs.put("@type", "sosa:Observation");
                // Generar un @id único para la observación
                String timeValue = values[headersIndex(actualHeaders, "_time")]; // Usar actualHeaders
                String measurementValue = values[headersIndex(actualHeaders, "_measurement")]; // Usar actualHeaders
                String deviceIdValue = values[headersIndex(actualHeaders, "deviceId")]; // Usar actualHeaders
                // Construir un ID más significativo si es posible
                obs.put("@id", "ex:Observation_" + measurementValue + "_" + deviceIdValue + "_" + timeValue.replaceAll("[^a-zA-Z0-9]", ""));

                // Definir el @context dentro de cada observación para auto-contenerse
                ObjectNode context = objectMapper.createObjectNode();
                context.put("sosa", "http://www.w3.org/ns/sosa/");
                context.put("qudt", "http://qudt.org/vocab/unit/");
                context.put("xsd", "http://www.w3.org/2001/XMLSchema#");
                context.put("ex", "http://example.com/data/sensor#");
                context.put("om", "http://www.opengis.net/ont/om/2.0/");
                obs.set("@context", context);

                // sosa:observedProperty
                obs.set("sosa:observedProperty", objectMapper.createObjectNode().put("@id", "ex:" + values[headersIndex(actualHeaders, "_measurement")])); // Usar actualHeaders

                // sosa:madeBySensor
                obs.set("sosa:madeBySensor", objectMapper.createObjectNode().put("@id", "ex:" + values[headersIndex(actualHeaders, "deviceId")])); // Usar actualHeaders

                // sosa:resultTime
                ObjectNode resultTime = objectMapper.createObjectNode();
                resultTime.put("@type", "xsd:dateTime");
                resultTime.put("@value", values[headersIndex(actualHeaders, "_time")]); // Usar actualHeaders
                obs.set("sosa:resultTime", resultTime);

                // sosa:hasResult
                ObjectNode hasResult = objectMapper.createObjectNode();
                hasResult.put("@type", "sosa:Result");

                ObjectNode hasValue = objectMapper.createObjectNode();
                hasValue.put("@type", "om:Measure");
                hasValue.put("om:hasSimpleValue", Double.parseDouble(values[headersIndex(actualHeaders, "_value")])); // Usar actualHeaders

                // Añadir om:hasUnit si existe en los campos CSV
                int unitIndex = headersIndex(actualHeaders, "unit"); // Usar actualHeaders

                //monitor.warning("indice para unit");
                //monitor.warning(String.valueOf(unitIndex));

                if (unitIndex != -1 && unitIndex < values.length) {
                    hasValue.set("om:hasUnit", objectMapper.createObjectNode().put("@id", "qudt:" + values[unitIndex]));
                }

                hasResult.set("om:hasValue", hasValue);
                obs.set("sosa:hasResult", hasResult);

                observations.add(obs);
            } catch (Exception e) {
                monitor.warning("Fallo al parsear una línea CSV de InfluxDB individual: " + e.getMessage() + ". Línea: " + line);
            }
        }

        return observations.toPrettyString();
    }

    /**
     * Busca el índice de una clave en un array de encabezados (case-insensitive).
     *
     * @param headers Array de encabezados.
     * @param key La clave a buscar.
     * @return El índice de la clave, o -1 si no se encuentra.
     */
    private int headersIndex(String[] headers, String key) {
        //System.out.println("headersIndex: '" + key + "' " + key.length());
        for (int i = 0; i < headers.length; i++) {
            //System.out.println("headersIndex - : '" + headers[i] + "' " + headers[i].length());
            if (headers[i].equalsIgnoreCase(key)) return i;
        }
        //System.out.println("headersIndex: No Encuentra");
        return -1;
    }

}
