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

package org.eclipse.edc.trustframework.policy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A generic function for evaluating credential subject claims of <a href="https://www.w3.org/TR/vc-data-model/#credentials">W3C credential</a>.
 */
public abstract class CredentialClaimsEvaluationFunction<CLAIMS_SHAPE> implements AtomicConstraintRuleFunction<Permission, PolicyContext> {

    private final Monitor monitor;
    private final ObjectMapper mapper;
    private final Function<CLAIMS_SHAPE, Object> navigation;
    
    private static volatile Method getParticipantAgentMethod = null;

    protected CredentialClaimsEvaluationFunction(Monitor monitor, ObjectMapper mapper,
                                                 Function<CLAIMS_SHAPE, Object> navigation) {
        this.monitor = monitor;
        this.mapper = mapper;
        this.navigation = navigation;
    }

    protected abstract Predicate<VerifiableCredential> credentialFilter();

    protected abstract Class<CLAIMS_SHAPE> getClaimsShapeType();

    @Override
    public boolean evaluate(Operator operator, Object rightValue, Permission rule, PolicyContext context) {
        monitor.debug("Evaluating GAIA-X policy constraint");
        try {
            var left = leftOperand(context);
            var right = rightOperand(rightValue);
            var result = evaluateConstraint(left, right, operator);
            var logMessage = String.format(
                    "Policy evaluation result: %s (left=%s, right=%s, operator=%s)",
                    result, left, right, operator);
            
            monitor.info(logMessage);
            return result;
        } catch (Exception e) {
            monitor.warning("Function evaluation failed: " + e.getMessage(), e);
            return false;
        }
    }

    private Set<Object> leftOperand(PolicyContext context) {
        monitor.debug("Extracting credentials from PolicyContext");

        ParticipantAgent participantAgent = null;
        try {
            Method method = context.getClass().getMethod("participantAgent");
            participantAgent = (ParticipantAgent) method.invoke(context);

        } catch (NoSuchMethodException e) {
            monitor.warning("Cannot find participantAgent() on concrete PolicyContext type: " + context.getClass().getName());
            return Collections.emptySet();
        } catch (InvocationTargetException | IllegalAccessException | ClassCastException e) {
            monitor.warning("Error invoking participantAgent() via reflection: " + e.getMessage());
            return Collections.emptySet();
        }

        if (participantAgent == null) {
            monitor.warning("No ParticipantAgent found in PolicyContext");
            return Collections.emptySet();
        }

        var claims = participantAgent.getClaims();
        if (claims == null || claims.isEmpty()) {
            monitor.debug("ParticipantAgent has no claims");
            return Collections.emptySet();
        }

        monitor.debug("Found " + claims.size() + " claims in ParticipantAgent");

        return claims.values().stream()
            .flatMap(this::toCredentialStream)
            .filter(Objects::nonNull)
            .filter(credentialFilter())
            .peek(cred -> {
                monitor.debug("Processing GAIA-X credential: " + cred.getType());
                monitor.debug("CredentialSchema count: " +
                        (cred.getCredentialSchema() != null ? cred.getCredentialSchema().size() : 0));
            })
            .map(credential -> {
                var credSubjects = credential.getCredentialSubject();
                if (credSubjects == null || credSubjects.isEmpty()) {
                    monitor.warning("Credential has no credentialSubject");
                    return null;
                }
                return credSubjects.get(0).getClaims();
            })
            .filter(Objects::nonNull)
            .map(this::shapeClaims)
            .map(navigation)
            .filter(Objects::nonNull)
            .flatMap(this::flatten)
            .collect(Collectors.toSet());
    }

    private Stream<Object> flatten(Object obj) {
        if (obj instanceof Collection<?>) {
            return ((Collection<?>) obj).stream().map(o -> (Object) o);
        }
        return Stream.of(obj);
    }

    private CLAIMS_SHAPE shapeClaims(Map<String, Object> claims) {
        return mapper.convertValue(claims, getClaimsShapeType());
    }

    private VerifiableCredential toCredential(Object object) {
        try {
            //return (VerifiableCredential) object;

            VerifiableCredential vc = (VerifiableCredential) object;
            monitor.debug("VC received - Type: " + vc.getType() + ", ID: " + vc.getId());
            return vc;
            
        } catch (Exception e) {
            monitor.warning("Cast to VerifiableCredential failed: " + e.getMessage());
            return null;
        }
    }

    private Stream<VerifiableCredential> toCredentialStream(Object claim) {
        if (claim == null) {
            return Stream.empty();
        }

        monitor.debug("Processing claim object: " + claim.getClass().getName());
        monitor.debug("Claim content: " + claim.toString());

        if (claim instanceof VerifiableCredential vc) {
            monitor.debug("Single VC detected - Type: " + vc.getType() + ", ID: " + vc.getId());
            return Stream.of(vc);
        } else if (claim instanceof Iterable<?> iterable) {
            // Caso lista de VCs
            return StreamSupport.stream(iterable.spliterator(), false)
                    .filter(Objects::nonNull)
                    .flatMap(c -> {
                        if (c instanceof VerifiableCredential v) {
                            monitor.debug("List VC detected - Type: " + v.getType() + ", ID: " + v.getId());
                            return Stream.of(v);
                        } else {
                            monitor.warning("Unexpected element in list: " + c.getClass());
                            return Stream.empty();
                        }
                    });
        } else {
            monitor.warning("Unknown claim type: " + claim.getClass());
            return Stream.empty();
        }
    }


    private static Set<Object> rightOperand(Object rightValue) {
        if (rightValue instanceof Collection<?>) {
            return new HashSet<>((Collection<?>) rightValue);
        }
        return Collections.singleton(rightValue);
    }

    private static boolean evaluateConstraint(Set<Object> left, Set<Object> right, Operator operator) {
        return switch (operator) {
            //case EQ -> left.equals(right);
            case EQ -> {
                if (right.size() == 1 && left.size() > 1) {
                    yield left.containsAll(right);
                }
                yield left.equals(right);
            }           

            case IS_ANY_OF -> {
                yield !Collections.disjoint(left, right);
            }

            case IS_ALL_OF -> {
                yield left.containsAll(right);
            }

            case IS_NONE_OF -> {
                yield Collections.disjoint(left, right);
            }

            case NEQ -> !left.equals(right);

            case IN -> !Collections.disjoint(right, left);

            default -> false;
        };
    }
}