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

        // `bindPermissionFunction` se usa para ligar una función llamada MembershipCredentialEvaluationFunction a los permisos.
        // - Esta función de evaluación revisa si el consumidor de datos tiene una credencial de membresía válida. 
        // - En este caso se usa en los contextos de TransferProcess, ContractNegotiation y Catalog.
        // - Funcionalidad: Antes de que se te permita negociar un contrato, catalogar un activo, o iniciar una transferencia, 
        //                  el sistema de políticas ejecutará esta función para determinar si tienes el permiso de membresía necesario. 
        //                  Si la función devuelve un resultado negativo, la operación es denegada. Esta es una evaluación de permiso

        bindPermissionFunction(MembershipCredentialEvaluationFunction.create(), TransferProcessPolicyContext.class, TransferProcessPolicyContext.TRANSFER_SCOPE, MEMBERSHIP_CONSTRAINT_KEY);
        bindPermissionFunction(MembershipCredentialEvaluationFunction.create(), ContractNegotiationPolicyContext.class, ContractNegotiationPolicyContext.NEGOTIATION_SCOPE, MEMBERSHIP_CONSTRAINT_KEY);
        bindPermissionFunction(MembershipCredentialEvaluationFunction.create(), CatalogPolicyContext.class, CatalogPolicyContext.CATALOG_SCOPE, MEMBERSHIP_CONSTRAINT_KEY);

        registerDataAccessLevelFunction();

        registerDataAccessRolesFunction();

        // Resumen:
        // - bindPermissionFunction es la puerta de entrada. Determina si eres digno de pasar. Tu código usa MembershipCredentialEvaluationFunction para verificar si tienes la membresía requerida 
        //   antes de permitirte continuar con un proceso (negociación, transferencia, etc.).
        // - bindDutyFunction es la lista de tareas que debes completar una vez que has pasado la puerta. Tu código usa DataAccessLevelFunction para ejecutar una acción obligatoria, 
        //   como asegurar un cierto nivel de acceso a los datos, después de que se ha otorgado el permiso.

        // `bindDutyFunction` se utiliza para ligar una función diferente, DataAccessLevelFunction, a los deberes.
        // - Esta función se activa cuando se debe cumplir una obligación. Por ejemplo, si una política de datos establece que debes anonimizar los datos (DataAccess.level = "anonimizado") después de usarlos, 
        //   esta función se encargará de ejecutar esa lógica.
        // - Funcionalidad: A diferencia del permiso, que determina si la acción es posible, el deber define una acción que debe ser realizada como parte de un acuerdo. 
        //                  El sistema no deniega la operación, sino que exige que se complete esta tarea.

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
