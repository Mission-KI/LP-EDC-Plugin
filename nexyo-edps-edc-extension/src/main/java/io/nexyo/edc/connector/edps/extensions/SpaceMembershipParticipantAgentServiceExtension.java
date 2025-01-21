package io.nexyo.edc.connector.edps.extensions;

import io.nexyo.edc.connector.edps.services.config.ConfigService;
import io.nexyo.edc.connector.edps.services.iam.dataspace.DataSpaceService;
import org.eclipse.edc.spi.agent.ParticipantAgentServiceExtension;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nexyo.edc.connector.edps.NexyoEdpsConstants.NEXYO_SPACE_MEMBERSHIPS_AGENT_ATTRIBUTE;

public class SpaceMembershipParticipantAgentServiceExtension implements ParticipantAgentServiceExtension {
    private final Monitor monitor;
    private final DataSpaceService dataSpaceService;
    private final ConfigService configService;

    public SpaceMembershipParticipantAgentServiceExtension(Monitor monitor, DataSpaceService dataSpaceService, ConfigService configService) {
        System.out.println("##### SpaceMembershipParticipantAgentServiceExtension constructor called");
        this.monitor = monitor;
        this.dataSpaceService = dataSpaceService;
        this.configService = configService;
    }

    @Override
    public @NotNull Map<String, String> attributesFor(ClaimToken claimToken) {
        monitor.debug("##### SpaceMembershipParticipantAgentServiceExtension.attributesFor called");
        monitor.debug("claims: " + claimToken.getClaims().toString());

        var attributes = new HashMap<String, String>();
        List<String> memberships;
        try {
            memberships = dataSpaceService.getMemberships();
        } catch (Exception e) {
            monitor.warning("##### Exception thrown when getting data space memberships: " + e.getMessage());
            return attributes;
        }

        monitor.debug("##### memberships to check against: " + memberships.toString());

        var tokenIssuerDID = claimToken.getClaims().get("client_id").toString();
        if (tokenIssuerDID.isEmpty()) {
            monitor.debug("##### tokenIssuerDID is empty");
            return attributes;
        }

        monitor.debug("##### tokenIssuerDID: " + tokenIssuerDID);

        var verifiedMemberships = new ArrayList<String>();
        memberships.stream().forEach(dataSpaceDID -> {
            monitor.debug("##### checking whether " + tokenIssuerDID + " is a member of data space " + dataSpaceDID);
            var isMember = dataSpaceService.isConnectorDataSpaceMember(tokenIssuerDID, dataSpaceDID);

            if (isMember) {
                monitor.debug("##### " + tokenIssuerDID + " is a member of data space " + dataSpaceDID);
                verifiedMemberships.add(dataSpaceDID);
            } else {
                monitor.debug("##### " + tokenIssuerDID + " is not a member of data space " + dataSpaceDID);
            }
        });

        attributes.put(NEXYO_SPACE_MEMBERSHIPS_AGENT_ATTRIBUTE, String.join(";", verifiedMemberships));

        monitor.debug("##### agent will return the following attributes: " + attributes);
        return attributes;
    }
}
