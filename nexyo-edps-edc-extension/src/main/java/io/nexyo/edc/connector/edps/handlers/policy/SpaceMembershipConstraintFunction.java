package io.nexyo.edc.connector.edps.handlers.policy;

import org.eclipse.edc.policy.engine.spi.AtomicConstraintFunction;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.Arrays;

import static io.nexyo.edc.connector.edps.NexyoEdpsConstants.NEXYO_SPACE_MEMBERSHIPS_AGENT_ATTRIBUTE;


public class SpaceMembershipConstraintFunction implements AtomicConstraintFunction<Permission> {

    private final Monitor monitor;

    public SpaceMembershipConstraintFunction(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public boolean evaluate(Operator operator, Object rightValue, Permission rule, PolicyContext context) {
        monitor.debug("##### SpaceMembershipConstraintFunction.evaluate called");

        if (context.getParticipantAgent() == null) {
            monitor.debug("##### participantAgent is null");
            return false;
        }

        var spaceMemberships = context.getParticipantAgent().getAttributes().get(NEXYO_SPACE_MEMBERSHIPS_AGENT_ATTRIBUTE);
        if (spaceMemberships == null) {
            monitor.debug("##### spaceMembership is null");
            return false;
        }

        var participantMemberships = Arrays.asList(spaceMemberships.split(";"));
        var dataSpaceDID = rightValue.toString();

        var evaluationResult = participantMemberships.contains(dataSpaceDID);
        monitor.debug("##### verifiedMemberships: " + participantMemberships);
        monitor.debug("##### rightValue: " + rightValue);
        monitor.debug("##### returning evaluationResult: " + evaluationResult);

        return evaluationResult;
    }
}
