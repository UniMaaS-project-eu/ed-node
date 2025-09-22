package org.eclipse.edc.demo.dcp.core;

import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.edc.policy.model.Policy;

import java.util.HashSet;
import java.util.Set;

/**
 * Versión simplificada que siempre agrega los scopes necesarios para catalog requests
 */
public class SimpleScopeMappingFunction implements PolicyValidatorRule<RequestPolicyContext> {
    private final Set<String> defaultScopes;

    // Constantes para las credenciales
    private static final String MEMBERSHIP_CREDENTIAL_TYPE = "MembershipCredential";
    private static final String DATA_PROCESSOR_CREDENTIAL_TYPE = "DataProcessorCredential";
    private static final String CREDENTIAL_TYPE_NAMESPACE = "org.eclipse.edc.vc.type";

    public SimpleScopeMappingFunction(Set<String> defaultScopes) {
        this.defaultScopes = defaultScopes;
    }

    @Override
    public Boolean apply(Policy policy, RequestPolicyContext requestPolicyContext) {
        var requestScopeBuilder = requestPolicyContext.requestScopeBuilder();
        var currentScopes = requestScopeBuilder.build().getScopes();

        // Crear un set con los scopes combinados
        var newScopes = new HashSet<>(currentScopes);

        // Agregar los scopes por defecto
        newScopes.addAll(defaultScopes);

        // ESTRATEGIA DIRECTA: Para solicitudes de catálogo, siempre incluir ambos tipos de credenciales
        // porque los assets individuales pueden tener constraints DataAccess.*
        if (requestPolicyContext instanceof RequestCatalogPolicyContext) {
            System.out.println("DEBUG: === SIMPLE SCOPE MAPPING FOR CATALOG REQUEST ===");
            
            // Siempre agregar MembershipCredential para acceso básico
            String membershipScope = String.format("%s:%s:read", CREDENTIAL_TYPE_NAMESPACE, MEMBERSHIP_CREDENTIAL_TYPE);
            newScopes.add(membershipScope);
            
            // Siempre agregar DataProcessorCredential para assets con constraints DataAccess.*
            String dataProcessorScope = String.format("%s:%s:read", CREDENTIAL_TYPE_NAMESPACE, DATA_PROCESSOR_CREDENTIAL_TYPE);
            newScopes.add(dataProcessorScope);
            
            System.out.println("DEBUG: Added both credential types for catalog request");
            System.out.println("DEBUG: MembershipCredential scope: " + membershipScope);
            System.out.println("DEBUG: DataProcessorCredential scope: " + dataProcessorScope);
        } else {
            System.out.println("DEBUG: === SIMPLE SCOPE MAPPING FOR NON-CATALOG REQUEST ===");
            System.out.println("DEBUG: Context type: " + requestPolicyContext.getClass().getSimpleName());
            
            // Para otras solicitudes, mantener la lógica existente
            newScopes.addAll(defaultScopes);
        }

        // Actualizar el builder con los nuevos scopes
        requestScopeBuilder.scopes(newScopes);

        // DEBUG: imprimir información de debug
        System.out.println("DEBUG: Default scopes: " + defaultScopes);
        System.out.println("DEBUG: Existing scopes: " + currentScopes);
        System.out.println("DEBUG: Final combined scopes: " + newScopes);
        System.out.println("DEBUG: Context type: " + requestPolicyContext.getClass().getSimpleName());

        return true;
    }
}