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

package org.eclipse.edc.demo.dcp.policy;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.spi.result.Result;

import java.util.List;

public class AbstractCredentialEvaluationFunction {
    private static final String VC_CLAIM = "vc";
    protected static final String MVD_NAMESPACE = "https://w3id.org/mvd/credentials/";
    protected static final String UNIMAAS_NAMESPACE = "https://w3id.org/unimaas/credentials/";

    protected Result<List<VerifiableCredential>> getCredentialList(ParticipantAgent agent) {
        System.out.println("=== DIAGNOSTIC: Getting credential list ===");
        System.out.println("DIAGNOSTIC: Agent claims keys: " + agent.getClaims().keySet());
        
        var vcListClaim = agent.getClaims().get(VC_CLAIM);
        
        if (vcListClaim == null) {
            System.out.println("DIAGNOSTIC: PROBLEM - No 'vc' claim found");
            return Result.failure("ParticipantAgent did not contain a '%s' claim.".formatted(VC_CLAIM));
        }
        
        System.out.println("DIAGNOSTIC: Found vc claim of type: " + vcListClaim.getClass());
        
        if (!(vcListClaim instanceof List)) {
            System.out.println("DIAGNOSTIC: PROBLEM - vc claim is not a List");
            return Result.failure("ParticipantAgent contains a '%s' claim, but the type is incorrect. Expected %s, received %s.".formatted(VC_CLAIM, List.class.getName(), vcListClaim.getClass().getName()));
        }
        
        var vcList = (List<VerifiableCredential>) vcListClaim;
        System.out.println("DIAGNOSTIC: Found " + vcList.size() + " verifiable credentials");
        
        if (vcList.isEmpty()) {
            System.out.println("DIAGNOSTIC: PROBLEM - vc list is empty");
            return Result.failure("ParticipantAgent contains a '%s' claim but it did not contain any VerifiableCredentials.".formatted(VC_CLAIM));
        }
        
        // Debug
        for (int i = 0; i < vcList.size(); i++) {
            var vc = vcList.get(i);
            System.out.println("DIAGNOSTIC: Credential " + i + " types: " + vc.getType());
            System.out.println("DIAGNOSTIC: Credential " + i + " has " + vc.getCredentialSubject().size() + " subjects");
            
            // Debug subjects credential
            for (int j = 0; j < vc.getCredentialSubject().size(); j++) {
                var subject = vc.getCredentialSubject().get(j);
                System.out.println("DIAGNOSTIC: Credential " + i + ", Subject " + j + " ID: " + subject.getId());
            }
        }
        
        System.out.println("DIAGNOSTIC: SUCCESS - Credentials found and validated");
        return Result.success(vcList);
    }
}