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

import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Operator;

import java.util.List;
import java.util.Objects;

public class DataAccessRolesFunctionDuty<C extends ParticipantAgentPolicyContext> extends AbstractCredentialEvaluationFunction implements AtomicConstraintRuleFunction<Duty, C> {

    private static final String DATAPROCESSOR_CRED_TYPE = "DataProcessorCredential";

    private DataAccessRolesFunctionDuty() {

    }

    public static <C extends ParticipantAgentPolicyContext> DataAccessRolesFunctionDuty<C> create() {
        return new DataAccessRolesFunctionDuty<>() {
        };
    }

    @Override
    public boolean evaluate(Operator operator, Object rightOperand, Duty duty, C policyContext) {
        System.out.println("=== DIAGNOSTIC: Role Permission Evaluation START ===");
        System.out.println("DIAGNOSTIC: Operator: " + operator + ", Required role: " + rightOperand);

        if (!operator.equals(Operator.EQ)) {
            System.out.println("DIAGNOSTIC: PROBLEM - Unsupported operator: " + operator);
            policyContext.reportProblem("Cannot evaluate operator %s, only %s is supported".formatted(operator, Operator.EQ));
            return false;
        }

        var pa = policyContext.participantAgent();
        if (pa == null) {
            System.out.println("DIAGNOSTIC: PROBLEM - ParticipantAgent is null");
            policyContext.reportProblem("ParticipantAgent not found on PolicyContext");
            return false;
        }

        System.out.println("DIAGNOSTIC: ParticipantAgent found, getting credentials...");
        var credentialResult = getCredentialList(pa);
        if (credentialResult.failed()) {
            System.out.println("DIAGNOSTIC: PROBLEM - Failed to get credentials: " + credentialResult.getFailureDetail());
            policyContext.reportProblem(credentialResult.getFailureDetail());
            return false;
        }

        return credentialResult.getContent()
                .stream()
                .filter(vc -> vc.getType().stream().anyMatch(t -> t.endsWith(DATAPROCESSOR_CRED_TYPE)))
                .flatMap(credential -> credential.getCredentialSubject().stream())
                .anyMatch(credentialSubject -> {
//                    var version = credentialSubject.getClaim(UNIMAAS_NAMESPACE, "contractVersion");
//                    var roles = credentialSubject.getClaim(UNIMAAS_NAMESPACE, "roles");
//
//                    if (roles instanceof List<?>) {
//                        var roleList = (List<?>) roles;
//                        String requiredRole = rightOperand.toString();
//
//                        // Only EQ is supported (contained)
//                        if (operator.equals(Operator.EQ)) {
//                            return version != null && roleList.contains(requiredRole);
//                        } else {
//                            policyContext.reportProblem("Unsupported operator for roles: " + operator);
//                            return false;
//                        }
//                    }

                    var roles = credentialSubject.getClaim(UNIMAAS_NAMESPACE, "roles");

                    if (roles instanceof List<?>) {
                        var roleList = (List<?>) roles;
                        String requiredRole = rightOperand.toString();

                        // Only EQ is supported (contained)
                        if (operator.equals(Operator.EQ)) {
                            return roleList.contains(requiredRole);
                        } else {
                            policyContext.reportProblem("Unsupported operator for roles: " + operator);
                            return false;
                        }
                    }



                    policyContext.reportProblem("Roles claim not a list: " + roles);
                    return false;

                });

    }
}
