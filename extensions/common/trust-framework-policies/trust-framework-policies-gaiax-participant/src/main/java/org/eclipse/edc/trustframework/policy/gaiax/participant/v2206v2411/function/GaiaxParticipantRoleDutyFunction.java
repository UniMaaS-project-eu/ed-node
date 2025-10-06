package org.eclipse.edc.trustframework.policy.gaiax.participant.v2206v2411.function;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GaiaxParticipantRoleDutyFunction<C extends ParticipantAgentPolicyContext> 
        implements AtomicConstraintRuleFunction<Duty, C> {

    private static final String GAIAX_LEGAL_PERSON_TYPE = "LegalPerson";
    private static final String PARTICIPANT_ROLE_CLAIM = "gx:participantRole";
    private static final Set<String> SUPPORTED_CONTEXTS = Set.of(
        "https://registry.gaia-x.eu/v2206/api/shape",
        "https://registry.lab.gaia-x.eu/main/context/2411"
    );

    private final Monitor monitor;

    private GaiaxParticipantRoleDutyFunction(Monitor monitor) {
        this.monitor = monitor;
    }

    public static <C extends ParticipantAgentPolicyContext> GaiaxParticipantRoleDutyFunction<C> create(Monitor monitor) {
        return new GaiaxParticipantRoleDutyFunction<>(monitor);
    }

    @Override
    public boolean evaluate(Operator operator, Object rightOperand, Duty duty, C policyContext) {
        monitor.debug("=== GAIA-X Role Duty Evaluation START ===");
        monitor.debug("Operator: " + operator + ", Required role: " + rightOperand);

        if (!operator.equals(Operator.EQ)) {
            monitor.warning("Unsupported operator: " + operator);
            policyContext.reportProblem("Cannot evaluate operator %s, only %s is supported".formatted(operator, Operator.EQ));
            return false;
        }

        var pa = policyContext.participantAgent();
        if (pa == null) {
            monitor.warning("ParticipantAgent is null");
            policyContext.reportProblem("ParticipantAgent not found on PolicyContext");
            return false;
        }

        monitor.debug("ParticipantAgent found, extracting GAIA-X credentials...");
        
        var claims = pa.getClaims();
        if (claims == null || claims.isEmpty()) {
            monitor.warning("No claims found in ParticipantAgent");
            return false;
        }

        return claims.values().stream()
            .flatMap(this::toCredentialStream)
            .filter(Objects::nonNull)
            .filter(this::isGaiaxLegalPersonCredential)
            .peek(cred -> monitor.debug("Processing GAIA-X credential: " + cred.getType()))
            .anyMatch(credential -> evaluateRoles(credential, rightOperand, policyContext));
    }

    private boolean evaluateRoles(VerifiableCredential credential, Object rightOperand, C policyContext) {
        var credSubjects = credential.getCredentialSubject();
        if (credSubjects == null || credSubjects.isEmpty()) {
            monitor.warning("Credential has no credentialSubject");
            return false;
        }

        var claims = credSubjects.get(0).getClaims();
        var roles = claims.get(PARTICIPANT_ROLE_CLAIM);

        if (roles instanceof List<?>) {
            var roleList = (List<?>) roles;
            String requiredRole = rightOperand.toString();

            monitor.debug("Found roles in credential: " + roleList);
            monitor.debug("Checking for required role: " + requiredRole);

            boolean hasRole = roleList.contains(requiredRole);
            monitor.info("Role evaluation result: " + hasRole);
            
            return hasRole;
        }

        monitor.warning("Roles claim is not a list: " + roles);
        policyContext.reportProblem("gx:participantRole claim is not a list in credential");
        return false;
    }

    private boolean isGaiaxLegalPersonCredential(VerifiableCredential credential) {
        boolean hasCorrectSchema = credential.getCredentialSchema() != null &&
                credential.getCredentialSchema().stream()
                        .anyMatch(schema -> SUPPORTED_CONTEXTS.contains(schema.id()));
        
        boolean hasCorrectType = credential.getType().contains(GAIAX_LEGAL_PERSON_TYPE);
        
        return hasCorrectSchema && hasCorrectType;
    }

    private Stream<VerifiableCredential> toCredentialStream(Object claim) {
        if (claim == null) {
            return Stream.empty();
        }

        if (claim instanceof VerifiableCredential vc) {
            return Stream.of(vc);
        } else if (claim instanceof Iterable<?> iterable) {
            return StreamSupport.stream(iterable.spliterator(), false)
                    .filter(Objects::nonNull)
                    .filter(c -> c instanceof VerifiableCredential)
                    .map(c -> (VerifiableCredential) c);
        }
        
        return Stream.empty();
    }
}