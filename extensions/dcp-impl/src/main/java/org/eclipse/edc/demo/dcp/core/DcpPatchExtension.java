/*
 *  Copyright (c) 2024 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.edc.demo.dcp.core;

import org.eclipse.edc.iam.identitytrust.spi.scope.ScopeExtractorRegistry;
import org.eclipse.edc.iam.identitytrust.spi.verification.SignatureSuiteRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.VcConstants;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestVersionPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.security.signature.jws2020.Jws2020SignatureSuite;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;

import java.util.Map;
import java.util.Set;

import static org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry.WILDCARD;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

public class DcpPatchExtension implements ServiceExtension {
    @Inject
    private TypeManager typeManager;

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private SignatureSuiteRegistry signatureSuiteRegistry;

    @Inject
    private TrustedIssuerRegistry trustedIssuerRegistry;

    @Inject
    private ScopeExtractorRegistry scopeExtractorRegistry;
    @Inject
    private TypeTransformerRegistry typeTransformerRegistry;

    @Inject
    private Monitor monitor;

    @Override
    public void initialize(ServiceExtensionContext context) {

        System.out.println("DEBUG: Initializing DCP Patch Extension with scope extra support");

        var issuerDID = context.getConfig().getString("unimaas.issuerservicevc.did");
        System.out.println("DEBUG: unimaas.issuerservicevc.did: " + issuerDID);

        boolean enableDataProcessorForCatalog = context.getConfig().getBoolean("unimaas.enable.extra.vp.catalogrequest");
        System.out.println("DEBUG: unimaas.enable.extra.vp.catalogrequest: " + enableDataProcessorForCatalog);

        // register signature suite
        var suite = new Jws2020SignatureSuite(typeManager.getMapper(JSON_LD));
        signatureSuiteRegistry.register(VcConstants.JWS_2020_SIGNATURE_SUITE, suite);

        // register dataspace issuerservice-vc --> connector needs publickey of issuerservice for credential validations.
        trustedIssuerRegistry.register(new Issuer(issuerDID, Map.of()), WILDCARD);

        // IMPORTANT: Create a DefaultScopeMappingFunction that:
        // 1. Includes the basic default scopes for catalog access
        // 2. Captures and adds extra scopes from the Connector
        // 3. Maintains compatibility with DataAccessCredentialScopeExtractor
        //var defaultScopes = Set.of("org.eclipse.edc.vc.type:MembershipCredential:read", "org.eclipse.edc.vc.type:DataProcessorCredential:read");
        var defaultScopes = Set.of("org.eclipse.edc.vc.type:MembershipCredential:read");
        
        // OPTION 1: Use the complex version with policy analysis (for debugging)
        var scopeMappingFunction = new DefaultScopeMappingFunction(defaultScopes, enableDataProcessorForCatalog);

        // OPCIÓN 2: Use the simplified version that always adds both credentials to the catalog
        //var scopeMappingFunction = new SimpleScopeMappingFunction(defaultScopes);

        // Register as PostValidator for all contexts
        // IMPORTANT ORDER: PostValidator runs AFTER ScopeExtractor
        // This allows DataAccessCredentialScopeExtractor to add its scopes first
        // and then DefaultScopeMappingFunction to add the default + extra scopes
        System.out.println("DEBUG: Registering DefaultScopeMappingFunction for all policy contexts");
        
        policyEngine.registerPostValidator(RequestCatalogPolicyContext.class, scopeMappingFunction::apply);
        policyEngine.registerPostValidator(RequestContractNegotiationPolicyContext.class, scopeMappingFunction::apply);
        policyEngine.registerPostValidator(RequestTransferProcessPolicyContext.class, scopeMappingFunction::apply);
        policyEngine.registerPostValidator(RequestVersionPolicyContext.class, scopeMappingFunction::apply);

        // register scope extractor
        // DataAccessCredentialScopeExtractor adds policy-based scopes (DataAccess.*)
        System.out.println("DEBUG: Registering DataAccessCredentialScopeExtractor");
        scopeExtractorRegistry.registerScopeExtractor(new DataAccessCredentialScopeExtractor(monitor));

        // register type transformer
        typeTransformerRegistry.register(new JsonValueToGenericTypeTransformer(typeManager, JSON_LD));
        
        System.out.println("DEBUG: DCP Patch Extension initialization completed");
        System.out.println("DEBUG: Enhanced flow order:");
        System.out.println("DEBUG: 1. DataAccessCredentialScopeExtractor adds policy-based scopes (backup)");
        System.out.println("DEBUG: 2. DefaultScopeMappingFunction analyzes policy constraints directly");
        System.out.println("DEBUG: 3. DefaultScopeMappingFunction adds default + DataProcessor + extra scopes");
        System.out.println("DEBUG: 4. Final combined scopes are used for VP request");
        System.out.println("DEBUG: Expected scopes: MembershipCredential:read + DataProcessorCredential:read");
    }
}
