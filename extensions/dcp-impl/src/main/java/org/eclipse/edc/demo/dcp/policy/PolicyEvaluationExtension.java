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

package org.eclipse.edc.demo.dcp.policy;

import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import static org.eclipse.edc.demo.dcp.policy.MembershipCredentialEvaluationFunction.MEMBERSHIP_CONSTRAINT_KEY;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;

public class PolicyEvaluationExtension implements ServiceExtension {

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private RuleBindingRegistry ruleBindingRegistry;

    @Override
    public void initialize(ServiceExtensionContext context) {

        // `bindPermissionFunction` is used to bind a function called MembershipCredentialEvaluationFunction to permissions.
        // - This evaluation function checks if the data consumer has a valid membership credential.
        // - In this case, it is used in the contexts of TransferProcess, ContractNegotiation, and Catalog.
        // - Functionality: Before you are allowed to negotiate a contract, catalog an asset, or initiate a transfer,
        //                  the policy system will execute this function to determine if you have the necessary membership permission.
        //                  If the function returns a negative result, the operation is denied. This is a permission evaluation

        bindPermissionFunction(MembershipCredentialEvaluationFunction.create(), TransferProcessPolicyContext.class, TransferProcessPolicyContext.TRANSFER_SCOPE, MEMBERSHIP_CONSTRAINT_KEY);
        bindPermissionFunction(MembershipCredentialEvaluationFunction.create(), ContractNegotiationPolicyContext.class, ContractNegotiationPolicyContext.NEGOTIATION_SCOPE, MEMBERSHIP_CONSTRAINT_KEY);
        bindPermissionFunction(MembershipCredentialEvaluationFunction.create(), CatalogPolicyContext.class, CatalogPolicyContext.CATALOG_SCOPE, MEMBERSHIP_CONSTRAINT_KEY);

        registerDataAccessLevelFunction();

        registerDataAccessRolesFunction();

        // Summary:
        // - bindPermissionFunction is the gateway. It determines if you are worthy of passing. Your code uses MembershipCredentialEvaluationFunction to check if you have the required membership
        //   before allowing you to continue with a process (negotiation, transfer, etc.).
        // - bindDutyFunction is the list of tasks you must complete once you have passed through the gate. Your code uses DataAccessLevelFunction to execute a mandatory action,
        //  such as ensuring a certain level of access to data, after permission has been granted.

        // `bindDutyFunction` is used to bind a different function, DataAccessLevelFunction, to duties.
        // - This function is triggered when an obligation must be fulfilled. For example, if a data policy states that you must anonymize data (DataAccess.level = "anonymized") after use,
        //   this function will be responsible for executing that logic.
        // - Functionality: Unlike permission, which determines whether an action is possible, duty defines an action that must be performed as part of an agreement.
        //                  The system does not deny the operation, but rather requires that the task be completed.

    }

    private void registerDataAccessLevelFunction() {
        var accessLevelKey = "DataAccess.level";

        bindDutyFunction(DataAccessLevelFunction.create(), TransferProcessPolicyContext.class, TransferProcessPolicyContext.TRANSFER_SCOPE, accessLevelKey);
        bindDutyFunction(DataAccessLevelFunction.create(), ContractNegotiationPolicyContext.class, ContractNegotiationPolicyContext.NEGOTIATION_SCOPE, accessLevelKey);
        bindDutyFunction(DataAccessLevelFunction.create(), CatalogPolicyContext.class, CatalogPolicyContext.CATALOG_SCOPE, accessLevelKey);

    }

    private void registerDataAccessRolesFunction() {
        var accessRolesKey = "DataAccess.roles";

        bindDutyFunction(DataAccessRolesFunctionDuty.create(), TransferProcessPolicyContext.class, TransferProcessPolicyContext.TRANSFER_SCOPE, accessRolesKey);
        bindDutyFunction(DataAccessRolesFunctionDuty.create(), ContractNegotiationPolicyContext.class, ContractNegotiationPolicyContext.NEGOTIATION_SCOPE, accessRolesKey);
        bindDutyFunction(DataAccessRolesFunctionDuty.create(), CatalogPolicyContext.class, CatalogPolicyContext.CATALOG_SCOPE, accessRolesKey);


        bindPermissionFunction(DataAccessRolesFunctionPermission.create(), TransferProcessPolicyContext.class, TransferProcessPolicyContext.TRANSFER_SCOPE, accessRolesKey);
        bindPermissionFunction(DataAccessRolesFunctionPermission.create(), ContractNegotiationPolicyContext.class, ContractNegotiationPolicyContext.NEGOTIATION_SCOPE, accessRolesKey);
        bindPermissionFunction(DataAccessRolesFunctionPermission.create(), CatalogPolicyContext.class, CatalogPolicyContext.CATALOG_SCOPE, accessRolesKey);


    }

    private <C extends PolicyContext> void bindPermissionFunction(AtomicConstraintRuleFunction<Permission, C> function, Class<C> contextClass, String scope, String constraintType) {
        ruleBindingRegistry.bind("use", scope);
        ruleBindingRegistry.bind(ODRL_SCHEMA + "use", scope);
        ruleBindingRegistry.bind(constraintType, scope);

        policyEngine.registerFunction(contextClass, Permission.class, constraintType, function);
    }

    private <C extends PolicyContext> void bindDutyFunction(AtomicConstraintRuleFunction<Duty, C> function, Class<C> contextClass, String scope, String constraintType) {
        ruleBindingRegistry.bind("use", scope);
        ruleBindingRegistry.bind(ODRL_SCHEMA + "use", scope);
        ruleBindingRegistry.bind(constraintType, scope);

        policyEngine.registerFunction(contextClass, Duty.class, constraintType, function);
    }
}
