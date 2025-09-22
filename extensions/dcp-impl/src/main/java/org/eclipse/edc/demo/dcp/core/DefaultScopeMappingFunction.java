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

package org.eclipse.edc.demo.dcp.core;

/*
=======
import org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequestMessage;
import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.edc.policy.model.Policy;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DefaultScopeMappingFunction implements PolicyValidatorRule<RequestPolicyContext> {
    private final Set<String> defaultScopes;

    public DefaultScopeMappingFunction(Set<String> defaultScopes) {
        this.defaultScopes = defaultScopes;
    }

    @Override
    public Boolean apply(Policy policy, RequestPolicyContext requestPolicyContext) {
        var requestScopeBuilder = requestPolicyContext.requestScopeBuilder();
        var rq = requestScopeBuilder.build();
        var existingScope = rq.getScopes();
        
        System.out.println("DEBUG: Existing scopes: " + existingScope);
        
        var newScopes = new HashSet<>(defaultScopes);
        newScopes.addAll(existingScope);
        
        System.out.println("DEBUG: Final scopes: " + newScopes);
        
        requestScopeBuilder.scopes(newScopes);
        return true;
    }


}
*/

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.spi.iam.ClaimToken;

import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DefaultScopeMappingFunction implements PolicyValidatorRule<RequestPolicyContext> {
    private final Set<String> defaultScopes;
    private final boolean enableDataProcessorForCatalog;

    // Nuevas constantes para DataProcessor credential
    private static final String DATA_PROCESSOR_CREDENTIAL_TYPE = "DataProcessorCredential";
    private static final String DATA_ACCESS_CONSTRAINT_PREFIX = "DataAccess.";
    private static final String CREDENTIAL_TYPE_NAMESPACE = "org.eclipse.edc.vc.type";

    public DefaultScopeMappingFunction(Set<String> defaultScopes, boolean enableDataProcessorForCatalog) {
        this.defaultScopes = defaultScopes;
        this.enableDataProcessorForCatalog = enableDataProcessorForCatalog;
    }
    /*
    @Override
    public Boolean apply(Policy policy, RequestPolicyContext requestPolicyContext) {

        System.out.println("DEBUG: ===== DefaultScopeMappingFunction STARTED =====");
        System.out.println("DEBUG: Policy object: " + policy);

        var requestScopeBuilder = requestPolicyContext.requestScopeBuilder();
        var currentScopes = requestScopeBuilder.build().getScopes();

        // 1️⃣ Crear un set con los scopes por defecto
        var newScopes = new HashSet<>(defaultScopes);

        // 2️⃣ Añadir los scopes actuales (ya procesados por ScopeExtractor)
        newScopes.addAll(currentScopes);

        // 3️⃣ NUEVO: Analizar la política para detectar constraints DataAccess.*
        Set<String> policyScopesFromConstraints = extractScopesFromPolicyConstraints(policy);
        newScopes.addAll(policyScopesFromConstraints);

        // 4️⃣ Extraer scopes del token y añadirlos
        Set<String> tokenJWTScopes = extractScopesFromTokenJWT(requestPolicyContext);
        newScopes.addAll(tokenJWTScopes);

        // 5️⃣ Actualizar requestScopeBuilder
        requestScopeBuilder.scopes(newScopes);

        // DEBUG: imprimir todos los scopes
        System.out.println("DEBUG: === SCOPE MAPPING FUNCTION ===");
        System.out.println("DEBUG: Default scopes: " + defaultScopes);
        System.out.println("DEBUG: Existing scopes (from ScopeExtractor): " + currentScopes);
        System.out.println("DEBUG: Policy constraint scopes: " + policyScopesFromConstraints);
        System.out.println("DEBUG: JWT extra scopes: " + tokenJWTScopes);
        System.out.println("DEBUG: Final combined scopes: " + newScopes);
        System.out.println("DEBUG: Context type: " + requestPolicyContext.getClass().getSimpleName());

        return true;
    }*/

    /**
    * Detecta si es una solicitud de catálogo
    */
    private boolean isCatalogRequest(RequestPolicyContext context) {
        String className = context.getClass().getSimpleName();
        return className.contains("RequestCatalogPolicyContext") || 
            className.contains("CatalogRequest") ||
            className.contains("Catalog");
    }

    /**
    * Agrega scopes comunes que pueden necesitarse para solicitudes de catálogo
    */
    private void addCommonCatalogScopes(Set<String> scopes) {
        // Puedes agregar aquí otros tipos de credenciales que normalmente se necesitan
        // para visualizar diferentes tipos de assets en el catálogo
            
        // Ejemplo: Si tienes otros tipos de credenciales específicas para ciertos assets
        // scopes.add(String.format("%s:%s:read", CREDENTIAL_TYPE_NAMESPACE, "OtherCredentialType"));
            
        //System.out.println("DEBUG: Common catalog scopes added (if any)");
    }


    @Override
    public Boolean apply(Policy policy, RequestPolicyContext requestPolicyContext) {
        System.out.println("DEBUG: ===== DefaultScopeMappingFunction STARTED =====");
        System.out.println("DEBUG: Context type: " + requestPolicyContext.getClass().getSimpleName());

        var requestScopeBuilder = requestPolicyContext.requestScopeBuilder();
        var currentScopes = requestScopeBuilder.build().getScopes();

        // 1️⃣ Crear un set con los scopes por defecto
        var newScopes = new HashSet<>(defaultScopes);

        // 2️⃣ Añadir los scopes actuales (ya procesados por ScopeExtractor)
        newScopes.addAll(currentScopes);

        // 3️⃣ NUEVO: Estrategia basada en el tipo de contexto
        if (enableDataProcessorForCatalog && isCatalogRequest(requestPolicyContext)) {
            // Para solicitudes de catálogo, agregar DataProcessorCredential preventivamente
            String dataProcessorScope = String.format("%s:%s:read", CREDENTIAL_TYPE_NAMESPACE, DATA_PROCESSOR_CREDENTIAL_TYPE);
            newScopes.add(dataProcessorScope);
            System.out.println("DEBUG: Added DataProcessorCredential scope for catalog request");
            
            // También agregar otros scopes comunes que podrían necesitarse para visualizar assets
            // Esto es configurable según tus necesidades
            addCommonCatalogScopes(newScopes);
        }

        // 4️⃣ Analizar la política para detectar constraints DataAccess.* (para otros contextos)
        Set<String> policyScopesFromConstraints = extractScopesFromPolicyConstraints(policy);
        newScopes.addAll(policyScopesFromConstraints);

        // 5️⃣ Extraer scopes del token y añadirlos
        Set<String> tokenJWTScopes = extractScopesFromTokenJWT(requestPolicyContext);
        newScopes.addAll(tokenJWTScopes);

        // 6️⃣ Actualizar requestScopeBuilder
        requestScopeBuilder.scopes(newScopes);

        // DEBUG: imprimir todos los scopes
        System.out.println("DEBUG: === SCOPE MAPPING FUNCTION ===");
        System.out.println("DEBUG: Default scopes: " + defaultScopes);
        System.out.println("DEBUG: Existing scopes (from ScopeExtractor): " + currentScopes);
        System.out.println("DEBUG: Policy constraint scopes: " + policyScopesFromConstraints);
        System.out.println("DEBUG: JWT extra scopes: " + tokenJWTScopes);
        System.out.println("DEBUG: Final combined scopes: " + newScopes);

        return true;
    }

    /**
     * NUEVA FUNCIÓN: Analiza las políticas para encontrar constraints DataAccess.*
     * y genera los scopes apropiados para DataProcessorCredential
     */
    private Set<String> extractScopesFromPolicyConstraints(Policy policy) {
        Set<String> scopesFromConstraints = new HashSet<>();

        System.out.println("DEBUG: === POLICY ANALYSIS START ===");
        
        System.out.println("DEBUG: Analyzing policy constraints for DataAccess patterns...");
        System.out.println("DEBUG: Policy type: " + (policy != null ? policy.getClass().getSimpleName() : "null"));
        
        if (policy == null) {
            System.out.println("DEBUG: Policy is null, cannot analyze constraints");
            return scopesFromConstraints;
        }
        
        try {
            // DEBUG: Imprimir información general de la política
            System.out.println("DEBUG: Policy toString: " + policy.toString());
            
            // Analizar permisos de la política
            var permissions = policy.getPermissions();
            System.out.println("DEBUG: Policy permissions: " + (permissions != null ? permissions.size() : "null"));
            
            if (permissions != null && !permissions.isEmpty()) {
                for (int i = 0; i < permissions.size(); i++) {
                    Permission permission = permissions.get(i);
                    System.out.println("DEBUG: Permissions count: " + (permissions != null ? permissions.size() : "null"));
                    System.out.println("DEBUG: Permission " + i + ": " + permission);
                    
                    var constraints = permission.getConstraints();                    
                    System.out.println("DEBUG: Permission " + i + " constraints count: " + (constraints != null ? constraints.size() : "null"));
                    
                    if (constraints != null) {
                        for (int j = 0; j < constraints.size(); j++) {
                            Constraint constraint = constraints.get(j);
                            System.out.println("DEBUG: Processing constraint " + j + ": " + constraint);
                            
                            // Usar reflection para obtener leftOperand ya que los métodos pueden variar
                            String leftOperand = getConstraintLeftOperandSafely(constraint);
                            System.out.println("DEBUG: Extracted leftOperand: " + leftOperand);


                            if (leftOperand != null) {
                                System.out.println("DEBUG: Found constraint leftOperand: " + leftOperand);
                                
                                // Si el constraint empieza con "DataAccess.", necesitamos DataProcessorCredential
                                if (leftOperand.startsWith(DATA_ACCESS_CONSTRAINT_PREFIX)) {
                                    String dataProcessorScope = String.format("%s:%s:read", CREDENTIAL_TYPE_NAMESPACE, DATA_PROCESSOR_CREDENTIAL_TYPE);
                                    scopesFromConstraints.add(dataProcessorScope);
                                    System.out.println("DEBUG: ✅ ADDED DataProcessor scope for constraint: " + leftOperand);
                                } else {
                                    System.out.println("DEBUG: ❌ Constraint does not match DataAccess pattern: " + leftOperand);
                                }
                            } else {
                                System.out.println("DEBUG: Could not extract leftOperand from constraint: " + constraint);
                                
                            }
                        }
                    }
                }
            }
            
            // También analizar deberes (duties) si los hay
            // Usar reflection para obtener duties de forma segura
            var duties = getPolicyDutiesSafely(policy);
            System.out.println("DEBUG: Policy duties: " + (duties != null ? duties.size() : "null"));
            
            if (duties != null) {
                duties.forEach(duty -> {
                    System.out.println("DEBUG: Processing duty: " + duty);
                    var dutyConstraints = getDutyConstraintsSafely(duty);
                    if (dutyConstraints != null) {
                        dutyConstraints.forEach(constraint -> {
                            String leftOperand = getConstraintLeftOperandSafely(constraint);
                            if (leftOperand != null) {
                                System.out.println("DEBUG: Found duty constraint leftOperand: " + leftOperand);
                                
                                if (leftOperand.startsWith(DATA_ACCESS_CONSTRAINT_PREFIX)) {
                                    String dataProcessorScope = String.format("%s:%s:read", CREDENTIAL_TYPE_NAMESPACE, DATA_PROCESSOR_CREDENTIAL_TYPE);
                                    scopesFromConstraints.add(dataProcessorScope);
                                    System.out.println("DEBUG: Added DataProcessor scope due to duty constraint: " + leftOperand);
                                }
                            }
                        });
                    }
                });
            }
            
//            // NUEVA ESTRATEGIA: Si no encontramos nada, buscar en el contexto
//            // porque la política del catálogo puede no contener los constraints de assets específicos
//            if (scopesFromConstraints.isEmpty()) {
//                System.out.println("DEBUG: No constraints found in policy, applying fallback strategy");
//                System.out.println("DEBUG: Adding DataProcessorCredential scope as fallback for catalog requests");
//                
//                // Para solicitudes de catálogo, siempre incluir DataProcessorCredential
//                // porque los assets pueden tener constraints DataAccess.*
//                String dataProcessorScope = String.format("%s:%s:read", CREDENTIAL_TYPE_NAMESPACE, DATA_PROCESSOR_CREDENTIAL_TYPE);
//                scopesFromConstraints.add(dataProcessorScope);
//                System.out.println("DEBUG: Added DataProcessorCredential scope as catalog fallback");
//            }
            
        } catch (Exception e) {
            System.err.println("DEBUG: Error analyzing policy constraints: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("DEBUG: Total scopes from policy constraints: " + scopesFromConstraints);
        return scopesFromConstraints;
    }
    
    /**
     * Obtiene el leftOperand de un constraint de forma segura usando reflection
     */
    private String getConstraintLeftOperandSafely(Constraint constraint) {
        try {

            System.out.println("DEBUG: Constraint class: " + constraint.getClass().getName());
            System.out.println("DEBUG: Constraint toString: " + constraint.toString());

            // Listar todos los métodos disponibles
            java.lang.reflect.Method[] methods = constraint.getClass().getMethods();
            for (java.lang.reflect.Method method : methods) {
                if (method.getName().contains("left") || method.getName().contains("Left") || 
                    method.getName().contains("operand") || method.getName().contains("Operand")) {
                    System.out.println("DEBUG: Available method: " + method.getName());
                }
            }

            // Intentar diferentes nombres de método que podrían existir
            String[] methodNames = {"getLeftOperand", "leftOperand", "getLeft", "left"};
            
            for (String methodName : methodNames) {
                try {
                    java.lang.reflect.Method method = constraint.getClass().getMethod(methodName);
                    Object result = method.invoke(constraint);
                    if (result != null) {
                        return result.toString();
                    }
                } catch (Exception ignored) {
                    // Continuar con el siguiente método
                }
            }
            
        } catch (Exception e) {
            System.err.println("DEBUG: Error getting constraint leftOperand: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Obtiene los duties de una policy de forma segura usando reflection
     */
    @SuppressWarnings("unchecked")
    private java.util.List<org.eclipse.edc.policy.model.Duty> getPolicyDutiesSafely(Policy policy) {
        try {
            // Intentar diferentes nombres de método que podrían existir
            String[] methodNames = {"getDuties", "duties", "getObligations", "obligations"};
            
            for (String methodName : methodNames) {
                try {
                    java.lang.reflect.Method method = policy.getClass().getMethod(methodName);
                    Object result = method.invoke(policy);
                    if (result instanceof java.util.List) {
                        return (java.util.List<org.eclipse.edc.policy.model.Duty>) result;
                    }
                } catch (Exception ignored) {
                    // Continuar con el siguiente método
                }
            }
            
        } catch (Exception e) {
            System.err.println("DEBUG: Error getting policy duties: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Obtiene los constraints de un duty de forma segura usando reflection
     */
    @SuppressWarnings("unchecked")
    private java.util.List<Constraint> getDutyConstraintsSafely(org.eclipse.edc.policy.model.Duty duty) {
        try {
            // Intentar diferentes nombres de método que podrían existir
            String[] methodNames = {"getConstraints", "constraints", "getConstraint", "constraint"};
            
            for (String methodName : methodNames) {
                try {
                    java.lang.reflect.Method method = duty.getClass().getMethod(methodName);
                    Object result = method.invoke(duty);
                    if (result instanceof java.util.List) {
                        return (java.util.List<Constraint>) result;
                    }
                } catch (Exception ignored) {
                    // Continuar con el siguiente método
                }
            }
            
        } catch (Exception e) {
            System.err.println("DEBUG: Error getting duty constraints: " + e.getMessage());
        }
        return null;
    }

    /**
     * Extrae los scopes del token 
     * Compatible con EDC 0.14.0 usando la API disponible
     */
    private Set<String> extractScopesFromTokenJWT(RequestPolicyContext context) {
        System.out.println("DEBUG: Extracting scopes from JWT token...");
        
        try {
            // En EDC 0.14.0, necesitamos usar diferentes estrategias basadas en la API disponible
            
            // Estrategia 1: Buscar en el ClaimToken del contexto
            Set<String> scopesFromClaimToken = extractScopesFromClaimToken(context);
            if (!scopesFromClaimToken.isEmpty()) {
                System.out.println("DEBUG: Found scopes in ClaimToken: " + scopesFromClaimToken);
                return scopesFromClaimToken;
            }

            // Estrategia 2: Intentar acceder al participantAgent via reflection (cuidadoso)
            Set<String> scopesFromParticipantAgent = extractScopesFromParticipantAgentSafe(context);
            if (!scopesFromParticipantAgent.isEmpty()) {
                System.out.println("DEBUG: Found scopes in participantAgent: " + scopesFromParticipantAgent);
                return scopesFromParticipantAgent;
            }

            // Estrategia 3: Buscar en propiedades del contexto que puedan estar expuestas
            Set<String> scopesFromContextProperties = extractScopesFromContextProperties(context);
            if (!scopesFromContextProperties.isEmpty()) {
                System.out.println("DEBUG: Found scopes in context properties: " + scopesFromContextProperties);
                return scopesFromContextProperties;
            }

            System.out.println("DEBUG: No extra scopes found from JWT Token");
            return Set.of();
            
        } catch (Exception e) {
            System.err.println("DEBUG: Error extracting scopes from JWT Token: " + e.getMessage());
            return Set.of();
        }
    }

    /**
     * Busca ClaimToken en el contexto usando métodos disponibles en EDC 0.14.0
     */
    private Set<String> extractScopesFromClaimToken(RequestPolicyContext context) {
        try {
            // En EDC 0.14.0, el ClaimToken podría estar disponible a través de diferentes métodos
            // Intentamos acceder de forma segura usando reflection
            
            java.lang.reflect.Method[] methods = context.getClass().getMethods();
            for (java.lang.reflect.Method method : methods) {
                String methodName = method.getName();
                
                // Buscar métodos que puedan devolver ClaimToken
                if ((methodName.contains("Claim") || methodName.contains("Token") || 
                     methodName.contains("Agent") || methodName.contains("Identity")) &&
                    method.getParameterCount() == 0) {
                    
                    try {
                        Object result = method.invoke(context);
                        if (result instanceof ClaimToken) {
                            ClaimToken claimToken = (ClaimToken) result;
                            return extractScopesFromClaimTokenObject(claimToken);
                        }
                    } catch (Exception ignored) {
                        // Continuar con el siguiente método
                    }
                }
            }
            
            return Set.of();
        } catch (Exception e) {
            System.err.println("DEBUG: Error in extractScopesFromClaimToken: " + e.getMessage());
            return Set.of();
        }
    }

    /**
     * Extrae scopes de un ClaimToken usando la API de EDC 0.14.0
     */
    private Set<String> extractScopesFromClaimTokenObject(ClaimToken claimToken) {
        try {
            // En EDC 0.14.0, ClaimToken podría tener diferentes métodos para acceder al token
            // Intentamos varios nombres posibles
            
            java.lang.reflect.Method[] methods = claimToken.getClass().getMethods();
            for (java.lang.reflect.Method method : methods) {
                String methodName = method.getName();
                
                if ((methodName.equals("getToken") || methodName.equals("token") || 
                     methodName.equals("getJwt") || methodName.equals("jwt") ||
                     methodName.equals("getValue") || methodName.equals("value")) &&
                    method.getParameterCount() == 0) {
                    
                    try {
                        Object tokenValue = method.invoke(claimToken);
                        if (tokenValue instanceof String) {
                            return extractScopesFromJwtString((String) tokenValue);
                        }
                    } catch (Exception ignored) {
                        // Continuar con el siguiente método
                    }
                }
            }

            // Intentar acceder a los claims directamente
            Map<String, Object> claims = claimToken.getClaims();
            if (claims != null) {
                // Buscar scope en los claims
                Object scopeClaim = claims.get("scope");
                if (scopeClaim != null) {
                    return parseScopeString(scopeClaim.toString());
                }
                
                Object extraScopeClaim = claims.get("extra_scope");
                if (extraScopeClaim != null) {
                    return parseScopeString(extraScopeClaim.toString());
                }
                
                Object scopesClaim = claims.get("scopes");
                if (scopesClaim != null) {
                    if (scopesClaim instanceof String) {
                        return parseScopeString(scopesClaim.toString());
                    } else if (scopesClaim instanceof java.util.Collection) {
                        @SuppressWarnings("unchecked")
                        java.util.Collection<String> scopes = (java.util.Collection<String>) scopesClaim;
                        return new HashSet<>(scopes);
                    }
                }
            }
            
            return Set.of();
        } catch (Exception e) {
            System.err.println("DEBUG: Error extracting scopes from ClaimToken object: " + e.getMessage());
            return Set.of();
        }
    }

    /**
     * Intenta acceder al participantAgent de forma segura usando reflection
     */
    private Set<String> extractScopesFromParticipantAgentSafe(RequestPolicyContext context) {
        try {
            // Buscar métodos que puedan devolver el participantAgent
            java.lang.reflect.Method[] methods = context.getClass().getMethods();
            for (java.lang.reflect.Method method : methods) {
                String methodName = method.getName();
                
                if (methodName.contains("participantAgent") || methodName.contains("ParticipantAgent")) {
                    try {
                        Object participantAgent = method.invoke(context);
                        if (participantAgent != null) {
                            return extractScopesFromParticipantAgentObject(participantAgent);
                        }
                    } catch (Exception ignored) {
                        // Continuar
                    }
                }
            }
            
            return Set.of();
        } catch (Exception e) {
            System.err.println("DEBUG: Error in extractScopesFromParticipantAgentSafe: " + e.getMessage());
            return Set.of();
        }
    }

    /**
     * Extrae scopes del objeto participantAgent
     */
    private Set<String> extractScopesFromParticipantAgentObject(Object participantAgent) {
        try {
            System.out.println("DEBUG: Found participantAgent of type: " + participantAgent.getClass().getSimpleName());

            // Si es un ClaimToken
            if (participantAgent instanceof ClaimToken) {
                return extractScopesFromClaimTokenObject((ClaimToken) participantAgent);
            }

            // Si es un Map
            if (participantAgent instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> agentMap = (Map<String, Object>) participantAgent;
                
                Object scopeClaim = agentMap.get("scope");
                if (scopeClaim != null) {
                    return parseScopeString(scopeClaim.toString());
                }
                
                Object extraScopeClaim = agentMap.get("extra_scope");
                if (extraScopeClaim != null) {
                    return parseScopeString(extraScopeClaim.toString());
                }
            }

            // Intentar acceder a propiedades via reflection
            java.lang.reflect.Method[] methods = participantAgent.getClass().getMethods();
            for (java.lang.reflect.Method method : methods) {
                String methodName = method.getName();
                
                if ((methodName.contains("scope") || methodName.contains("Scope")) &&
                    method.getParameterCount() == 0) {
                    
                    try {
                        Object result = method.invoke(participantAgent);
                        if (result != null) {
                            if (result instanceof String) {
                                return parseScopeString(result.toString());
                            } else if (result instanceof java.util.Collection) {
                                @SuppressWarnings("unchecked")
                                java.util.Collection<String> scopes = (java.util.Collection<String>) result;
                                return new HashSet<>(scopes);
                            }
                        }
                    } catch (Exception ignored) {
                        // Continuar
                    }
                }
            }

            return Set.of();
        } catch (Exception e) {
            System.err.println("DEBUG: Error in extractScopesFromParticipantAgentObject: " + e.getMessage());
            return Set.of();
        }
    }

    /**
     * Busca scopes en propiedades del contexto usando reflection cuidadosa
     */
    private Set<String> extractScopesFromContextProperties(RequestPolicyContext context) {
        try {
            System.out.println("DEBUG: Attempting to find context properties...");
            
            // Buscar métodos que puedan exponer datos del contexto
            java.lang.reflect.Method[] methods = context.getClass().getMethods();
            for (java.lang.reflect.Method method : methods) {
                String methodName = method.getName();
                
                // Buscar métodos getter que puedan contener información del contexto
                if (methodName.startsWith("get") && method.getParameterCount() == 0) {
                    try {
                        Object result = method.invoke(context);
                        
                        // Si el resultado es un Map, buscar scopes
                        if (result instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = (Map<String, Object>) result;
                            
                            Set<String> scopes = extractScopesFromMap(map);
                            if (!scopes.isEmpty()) {
                                System.out.println("DEBUG: Found scopes in " + methodName + ": " + scopes);
                                return scopes;
                            }
                        }
                        
                        // Si el resultado contiene scope information
                        if (result != null && result.toString().contains("scope")) {
                            System.out.println("DEBUG: Found potential scope info in " + methodName + ": " + result);
                        }
                        
                    } catch (Exception ignored) {
                        // Continuar silenciosamente
                    }
                }
            }
            
            return Set.of();
        } catch (Exception e) {
            System.err.println("DEBUG: Error in extractScopesFromContextProperties: " + e.getMessage());
            return Set.of();
        }
    }

    /**
     * Busca scopes en un Map
     */
    private Set<String> extractScopesFromMap(Map<String, Object> map) {
        String[] scopeKeys = {"scope", "scopes", "extra_scope", "extraScope", "additionalScope"};
        
        for (String key : scopeKeys) {
            Object value = map.get(key);
            if (value != null) {
                if (value instanceof String) {
                    return parseScopeString(value.toString());
                } else if (value instanceof java.util.Collection) {
                    @SuppressWarnings("unchecked")
                    java.util.Collection<String> scopes = (java.util.Collection<String>) value;
                    return new HashSet<>(scopes);
                }
            }
        }
        
        return Set.of();
    }

    /**
     * Extrae scopes de un string JWT
     */
    private Set<String> extractScopesFromJwtString(String jwtToken) {
        if (jwtToken == null || jwtToken.trim().isEmpty()) {
            return Set.of();
        }

        try {
            JWT jwt = JWTParser.parse(jwtToken);
            
            Object scopeClaim = jwt.getJWTClaimsSet().getClaim("scope");
            if (scopeClaim != null) {
                return parseScopeString(scopeClaim.toString());
            }

            Object scopesClaim = jwt.getJWTClaimsSet().getClaim("scopes");
            if (scopesClaim != null) {
                if (scopesClaim instanceof String) {
                    return parseScopeString(scopesClaim.toString());
                } else if (scopesClaim instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<String> scopesListFromJwt = (java.util.List<String>) scopesClaim;
                    return new HashSet<>(scopesListFromJwt);
                }
            }

            Object extraScopeClaim = jwt.getJWTClaimsSet().getClaim("extra_scope");
            if (extraScopeClaim != null) {
                return parseScopeString(extraScopeClaim.toString());
            }

            return Set.of();
            
        } catch (ParseException e) {
            System.err.println("DEBUG: Error parsing JWT token: " + e.getMessage());
            return Set.of();
        }
    }

    /**
     * Parsea un string de scopes separados por espacios
     */
    private Set<String> parseScopeString(String scopeString) {
        if (scopeString == null || scopeString.trim().isEmpty()) {
            return Set.of();
        }
        
        return new HashSet<>(Arrays.asList(scopeString.trim().split("\\s+")));
    }
}