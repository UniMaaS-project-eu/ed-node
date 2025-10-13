/*
 *  Copyright (c) 2023 Amadeus
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus - initial API and implementation
 *
 */

package org.eclipse.edc.trustframework.policy.gaiax.participant.v2206v2411;

import org.eclipse.edc.iam.identitytrust.spi.scope.ScopeExtractorRegistry;
import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
//import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.trustframework.policy.core.CredentialClaimsEvaluationFunctionFactory;
import org.eclipse.edc.trustframework.policy.core.GaiaxCredentialScopeExtractor;
import org.eclipse.edc.trustframework.policy.gaiax.participant.v2206v2411.claims.GaiaxParticipantClaims;
import org.eclipse.edc.trustframework.policy.gaiax.participant.v2206v2411.function.GaiaxParticipantClaimsEvaluationFunction;
import org.eclipse.edc.trustframework.policy.gaiax.participant.v2206v2411.transformer.JsonObjectToCredentialSchemaTransformer;

import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;

@Extension(value = GaiaxParticipantPoliciesExtension.NAME)
public class GaiaxParticipantPoliciesExtension implements ServiceExtension {

//    private static final String ALL_SCOPE = "*";

    public static final String NAME = "Gaia-X Participant Policies";

    @Setting(key = "unimaas.mockregistry.context.url", 
             description = "Local GAIA-X mock registry service", 
             defaultValue = "http://localhost/main/context/2411")
    private String mockRegistryUrl;

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private RuleBindingRegistry ruleBindingRegistry;

    @Inject
    private ScopeExtractorRegistry scopeExtractorRegistry;

    @Inject
    private TypeTransformerRegistry typeTransformerRegistry;

    @Inject
    private TypeManager typeManager;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {

        // DEPRETACED: JsonObjectToVerifiableCredentialTransformer not working propertly
        // Register transformer for issuanceDate y expirationDate
        //typeTransformerRegistry.register(new JsonObjectToVerifiableCredentialTransformer());

        // Register transformer for CredentialSchema
        typeTransformerRegistry.register(new JsonObjectToCredentialSchemaTransformer());

        // DEPRETACED: PROOF is not fully tested, JWT solution is implemented
        // Register transformer for Proof
        //typeTransformerRegistry.register(new JsonObjectToProofTransformer());

        // register scope extractor GAIA-X
        System.out.println("DEBUG: Registering DataAccessCredentialScopeExtractor");
        var gaiaxScopeExtractor = new GaiaxCredentialScopeExtractor(context.getMonitor());
        scopeExtractorRegistry.registerScopeExtractor(gaiaxScopeExtractor);
    
        var factory = new CredentialClaimsEvaluationFunctionFactory();
        factory.create(GaiaxParticipantClaims.class)
                .forEach((name, navigation) -> {

                    var function = new GaiaxParticipantClaimsEvaluationFunction(
                            context.getMonitor(),
                            typeManager.getMapper(),
                            navigation,
                            mockRegistryUrl);

                    // Permission
                    bindPermissionFunction(function, TransferProcessPolicyContext.class, TransferProcessPolicyContext.TRANSFER_SCOPE, name);
                    bindPermissionFunction(function, ContractNegotiationPolicyContext.class, ContractNegotiationPolicyContext.NEGOTIATION_SCOPE, name);
                    bindPermissionFunction(function, CatalogPolicyContext.class, CatalogPolicyContext.CATALOG_SCOPE, name);

                    // Duty (OPTIONAL, depends on the scenario)
                    //bindDutyFunction(function, TransferProcessPolicyContext.class, TransferProcessPolicyContext.TRANSFER_SCOPE, name);
                    //bindDutyFunction(function, ContractNegotiationPolicyContext.class, ContractNegotiationPolicyContext.NEGOTIATION_SCOPE, name);
                    //bindDutyFunction(function, CatalogPolicyContext.class, CatalogPolicyContext.CATALOG_SCOPE, name);

                });
    }

    private <C extends org.eclipse.edc.policy.engine.spi.PolicyContext>
    void bindPermissionFunction(AtomicConstraintRuleFunction<Permission, ? super C> function, // CAMBIO CLAVE: Usa ? super C
                                Class<C> contextClass,
                                String scope,
                                String constraintType) {

        ruleBindingRegistry.bind("use", scope);
        ruleBindingRegistry.bind(ODRL_SCHEMA + "use", scope);
        ruleBindingRegistry.bind(constraintType, scope);

        @SuppressWarnings("unchecked")
        var castedFunction = (AtomicConstraintRuleFunction<Permission, C>) function;

        policyEngine.registerFunction(contextClass, Permission.class, constraintType, castedFunction);
    }

    // Duty (OPTIONAL, depends on the scenario)
    //private <C extends org.eclipse.edc.policy.engine.spi.PolicyContext>
    //void bindDutyFunction(AtomicConstraintRuleFunction<?, ? super C> function,
    //                    Class<C> contextClass,
    //                    String scope,
    //                    String constraintType) {
    //    ruleBindingRegistry.bind("use", scope);
    //    ruleBindingRegistry.bind(ODRL_SCHEMA + "use", scope);
    //    ruleBindingRegistry.bind(constraintType, scope);
    //
    //    @SuppressWarnings("unchecked")
    //    var castedFunction = (AtomicConstraintRuleFunction<Duty, C>) function;
    //
    //    policyEngine.registerFunction(contextClass, Duty.class, constraintType, castedFunction);
    //}
}

