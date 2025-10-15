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

import org.eclipse.edc.iam.identitytrust.spi.scope.ScopeExtractor;
import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.Set;

class DataAccessCredentialScopeExtractor implements ScopeExtractor {
    public static final String DATA_PROCESSOR_CREDENTIAL_TYPE = "DataProcessorCredential";
    private static final String DATA_ACCESS_CONSTRAINT_PREFIX = "DataAccess.";
    private static final String CREDENTIAL_TYPE_NAMESPACE = "org.eclipse.edc.vc.type";

    private final Monitor monitor;
    
    public DataAccessCredentialScopeExtractor(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public Set<String> extractScopes(Object leftValue, Operator operator, Object rightValue, RequestPolicyContext context) {
        
        //monitor.debug("DEBUG: ScopeExtractor - leftValue: " + leftValue);
        
        // Provider connector evaluates its policies and include scopes required to Consumer connector.
        // In this case, if leftOperand starts with "DataAccess." includes "DataProcessorCredential".
        if (leftValue instanceof String leftOperand) {
            if (leftOperand.startsWith(DATA_ACCESS_CONSTRAINT_PREFIX)) {
                //monitor.debug("DEBUG: ScopeExtractor - Found DataAccess constraint, requesting DataProcessor credential");
                
                String dataProcessorScope = "%s:%s:read".formatted(CREDENTIAL_TYPE_NAMESPACE, DATA_PROCESSOR_CREDENTIAL_TYPE);
                //monitor.debug("DEBUG: ScopeExtractor - Generated scope: " + dataProcessorScope);
                
                return Set.of(dataProcessorScope);
            }
        }
        
        //monitor.debug("DEBUG: ScopeExtractor - No DataAccess constraint found, returning empty");
        return Set.of();
    }
}
