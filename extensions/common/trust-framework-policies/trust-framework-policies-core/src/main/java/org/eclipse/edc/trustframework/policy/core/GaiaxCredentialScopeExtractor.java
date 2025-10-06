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

package org.eclipse.edc.trustframework.policy.core;

import org.eclipse.edc.iam.identitytrust.spi.scope.ScopeExtractor;
import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.Set;

public class GaiaxCredentialScopeExtractor implements ScopeExtractor {
    
    private static final String GAIAX_CONSTRAINT_PREFIX1 = "gx-participant:";
    private static final String GAIAX_CONSTRAINT_PREFIX2 = "gx:participant";
    private static final String CREDENTIAL_TYPE_NAMESPACE = "org.eclipse.edc.vc.type";
    private static final String LEGAL_PERSON_CREDENTIAL_TYPE = "LegalPerson";

    private final Monitor monitor;
    
    public GaiaxCredentialScopeExtractor(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public Set<String> extractScopes(Object leftValue, Operator operator, Object rightValue, RequestPolicyContext context) {
        
        monitor.debug("GaiaxScopeExtractor - leftValue: " + leftValue);
        
        if (leftValue instanceof String leftOperand) {
            if (leftOperand.startsWith(GAIAX_CONSTRAINT_PREFIX1) || leftOperand.startsWith(GAIAX_CONSTRAINT_PREFIX2)) {
                monitor.debug("GaiaxScopeExtractor - Found GAIA-X constraint, requesting LegalPerson credential");
                
                String gaiaxScope = "%s:%s:read".formatted(CREDENTIAL_TYPE_NAMESPACE, LEGAL_PERSON_CREDENTIAL_TYPE);
                monitor.debug("GaiaxScopeExtractor - Generated scope: " + gaiaxScope);
                
                return Set.of(gaiaxScope);
            }
        }
        
        monitor.debug("GaiaxScopeExtractor - No GAIA-X constraint found, returning empty");
        return Set.of();
    }
}
