package org.eclipse.edc.demo.dcp.core;

import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.edc.policy.model.Policy;

import java.util.HashSet;
import java.util.Set;

/**
 * Simplified version that always adds the necessary scopes for catalog requests
 */
public class SimpleScopeMappingFunction implements PolicyValidatorRule<RequestPolicyContext> {
    private final Set<String> defaultScopes;

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

        // Create a set with the combined scopes
        var newScopes = new HashSet<>(currentScopes);

        // Add the default scopes
        newScopes.addAll(defaultScopes);

        // DIRECT STRATEGY: For catalog requests, always include both types of credentials
        // because individual assets may have DataAccess constraints.*
        if (requestPolicyContext instanceof RequestCatalogPolicyContext) {
            System.out.println("DEBUG: === SIMPLE SCOPE MAPPING FOR CATALOG REQUEST ===");
            
            // Always add MembershipCredential for basic access
            String membershipScope = String.format("%s:%s:read", CREDENTIAL_TYPE_NAMESPACE, MEMBERSHIP_CREDENTIAL_TYPE);
            newScopes.add(membershipScope);
            
            // Always add DataProcessorCredential for assets with constraints DataAccess.*
            String dataProcessorScope = String.format("%s:%s:read", CREDENTIAL_TYPE_NAMESPACE, DATA_PROCESSOR_CREDENTIAL_TYPE);
            newScopes.add(dataProcessorScope);
            
            System.out.println("DEBUG: Added both credential types for catalog request");
            System.out.println("DEBUG: MembershipCredential scope: " + membershipScope);
            System.out.println("DEBUG: DataProcessorCredential scope: " + dataProcessorScope);
        } else {
            System.out.println("DEBUG: === SIMPLE SCOPE MAPPING FOR NON-CATALOG REQUEST ===");
            System.out.println("DEBUG: Context type: " + requestPolicyContext.getClass().getSimpleName());
            
            // For other requests, keep the existing logic
            newScopes.addAll(defaultScopes);
        }

        // Update the builder with the new scopes
        requestScopeBuilder.scopes(newScopes);

        // DEBUG
        System.out.println("DEBUG: Default scopes: " + defaultScopes);
        System.out.println("DEBUG: Existing scopes: " + currentScopes);
        System.out.println("DEBUG: Final combined scopes: " + newScopes);
        System.out.println("DEBUG: Context type: " + requestPolicyContext.getClass().getSimpleName());

        return true;
    }
}