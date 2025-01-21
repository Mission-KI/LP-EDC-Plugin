package io.nexyo.edc.connector.edps.services.iam.dataspace;

import io.nexyo.edc.connector.edps.adapters.http.nexyo.info.NexyoHubInfoClient;
import io.nexyo.edc.connector.edps.adapters.repository.file.NexyoDataSpaceMembershipRepository;
import io.nexyo.edc.connector.edps.services.config.ConfigService;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.web.resolution.WebDidResolver;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.Arrays;
import java.util.List;

import static io.nexyo.edc.connector.edps.NexyoEdpsConstants.*;
import static io.nexyo.edc.connector.edps.NexyoEdpsSettings.DID_URL;

public class DataSpaceService {
    private final Monitor monitor;

    private final ConfigService configService;

    private final WebDidResolver webDidResolver;

    private final NexyoHubInfoClient infoClient;

    private final NexyoDataSpaceMembershipRepository membershipRepo;

    public DataSpaceService(Monitor monitor, ConfigService configService, WebDidResolver webDidResolver, NexyoHubInfoClient infoClient) {
        this.monitor = monitor;
        this.webDidResolver = webDidResolver;
        this.infoClient = infoClient;
        this.configService = configService;

        this.membershipRepo = new NexyoDataSpaceMembershipRepository(DATA_SPACE_MEMBERSHIP_FILE_PATH);
    }

    public boolean isOwnConnectorDataSpaceMember(String dataSpaceDid) {
        String[] participantDIDs;
        try {
            participantDIDs = getDataSpaceParticipantList(dataSpaceDid);
        } catch (EdcException e) {
            monitor.debug("Failed to get participant list for dataSpaceDid: " + dataSpaceDid);
            monitor.debug("Reason: " + e.getMessage());
            return false;
        }

        return Arrays.asList(participantDIDs).contains(configService.getRequiredConfigValue(DID_URL));
    }

    public boolean isConnectorDataSpaceMember(String connectorDid, String dataSpaceDid) {
        String[] participantDIDs;
        try {
            participantDIDs = getDataSpaceParticipantList(dataSpaceDid);
        } catch (EdcException e) {
            monitor.debug("Failed to get participant list for dataSpaceDid: " + dataSpaceDid);
            monitor.debug("Reason: " + e.getMessage());
            return false;
        }

        monitor.debug("participantDIDs: " + Arrays.toString(participantDIDs));

        var connectorDidDocumentResult = webDidResolver.resolve(connectorDid);
        if (connectorDidDocumentResult.failed()) {
            monitor.debug("Failed to resolve DID document for connector DID: " + connectorDid);
            monitor.debug("Reason: " + connectorDidDocumentResult.getFailure().toString());
            return false;
        }
        var connectorDidDocument = connectorDidDocumentResult.getContent();

        if (!hasController(connectorDidDocument)) {
            monitor.debug("### connector DID document has no controller, not supported with nexyo data space extension");
            return false;
        }

        monitor.debug("### connector DID has a controller, further checks are required");
        var connectorController = getFirstController(connectorDidDocument);

        if (connectorController.equals(connectorDid)) {
            // check wether the data space participants list contains the connector DID
            monitor.debug("### connector is its own controller. Checking whether the data space participants list: " + participantDIDs + " contains the connector DID " + connectorDid);
            return Arrays.asList(participantDIDs).contains(connectorDid);
        }

        var connectorControllerDidDocumentResult = webDidResolver.resolve(connectorController);
        if (connectorControllerDidDocumentResult.failed()) {
            monitor.debug("Failed to resolve DID document for connector controller hub DID: " + connectorController);
            monitor.debug("Reason: " + connectorControllerDidDocumentResult.getFailure().toString());
            return false;
        }
        var connectorControllerDidDocument = connectorControllerDidDocumentResult.getContent();

        var potentiallyConnectorDidsService = connectorControllerDidDocument.getService().stream().filter(service -> service.getId().equals(connectorControllerDidDocument.getId() + CONNECTORS_SVC_ID_SUFFIX)).findFirst();
        if (!potentiallyConnectorDidsService.isPresent()) {
            monitor.debug("No ConnectorDIDs service found in DID document for DID: " + connectorControllerDidDocument.getId());
            return false;
        }

        var connectorDIDsServiceEndpoint = potentiallyConnectorDidsService.get().getServiceEndpoint();
        monitor.debug("### found connectors service endpoint " + connectorDIDsServiceEndpoint);

        var connectorsResult = infoClient.getHubConnectors(connectorDIDsServiceEndpoint, connectorControllerDidDocument.getId());

        if (connectorsResult.failed()) {
            monitor.debug("Failed to access the hubs endpoint of its connector dids " + connectorControllerDidDocument.getId() + " at " + connectorDIDsServiceEndpoint);
            monitor.debug("Reason: " + connectorsResult.getFailure().toString());
            return false;
        }
        var hubConnectorDIDs = connectorsResult.getContent().getConnectors();
        monitor.debug("### found hub connectors: " + Arrays.toString(hubConnectorDIDs));

        // verify that hubConnectorDIDs contains connectorDid
        monitor.debug("### checking whether the hub connectors list: " + hubConnectorDIDs.toString() + " contains the connector DID " + connectorDid);
        if (!Arrays.asList(hubConnectorDIDs).contains(connectorDid)) {
            monitor.debug("Hub DID " + connectorControllerDidDocument.getId() + " does not contain connector DID " + connectorDid);
            return false;
        }

        // finally check wether the data space participants list contains the controller hub DID
        monitor.debug("### checking whether the data space participants list: " + participantDIDs.toString() + " contains the controller hub DID " + connectorControllerDidDocument.getId());
        return Arrays.asList(participantDIDs).contains(connectorControllerDidDocument.getId());
    }

    private boolean hasController(DidDocument connectorDidDocument) {
        return connectorDidDocument.getVerificationMethod().stream().filter(verificationMethod -> !verificationMethod.getController().isEmpty()).count() > 0;
    }

    private String getFirstController(DidDocument connectorDidDocument) {
        return connectorDidDocument.getVerificationMethod().stream().filter(verificationMethod -> !verificationMethod.getController().isEmpty()).findFirst().get().getController();
    }

    public String[] getDataSpaceParticipantList(String dataSpaceDid) throws EdcException {
        var didDocumentResult = webDidResolver.resolve(dataSpaceDid);
        if (didDocumentResult.failed()) {
            throw new EdcException("Failed to resolve DID document for DID: " + dataSpaceDid);
        }
        var didDocument = didDocumentResult.getContent();


        var potentiallyDataSpaceInfoService = didDocument.getService().stream().filter(service -> service.getId().equals(dataSpaceDid + DS_INFO_SVC_ID_SUFFIX)).findFirst();
        if (!potentiallyDataSpaceInfoService.isPresent()) {
            throw new EdcException("No DataSpaceInfo service found in DID document for DID: " + dataSpaceDid);
        }
        var dataSpaceInfoServiceEndpoint = potentiallyDataSpaceInfoService.get().getServiceEndpoint();

        var dataSpaceInfoResult = infoClient.getDataSpaceInfo(dataSpaceInfoServiceEndpoint, dataSpaceDid);
        if (dataSpaceInfoResult.failed()) {
            throw new EdcException("Failed to access dataSpaceInfo endpoint of did " + dataSpaceDid + " at " + dataSpaceInfoServiceEndpoint + " Reason: " + dataSpaceInfoResult.getFailureMessages().toString());
        }

        return dataSpaceInfoResult.getContent().getParticipantDIDs();
    }

    public List<String> getMemberships() throws EdcException {
        try {
            return membershipRepo.getAllMemberships();
        } catch (Exception e) {
            monitor.debug("Failed to get memberships from file: " + DATA_SPACE_MEMBERSHIP_FILE_PATH);
            monitor.debug("Reason: " + e.getMessage());
            throw new EdcException("Failed to get memberships: " + e.getMessage());
        }
    }

    public void addMembership(String did) throws EdcException {
        try {
            membershipRepo.addMembership(did);
        } catch (Exception e) {
            monitor.debug("Failed to get memberships from file: " + DATA_SPACE_MEMBERSHIP_FILE_PATH);
            monitor.debug("Reason: " + e.getMessage());
            throw new EdcException("Failed to get memberships: " + e.getMessage());
        }
    }

    public void removeMembership(String did) throws EdcException {
        try {
            membershipRepo.deleteDID(did);
        } catch (Exception e) {
            monitor.debug("Failed to remove membership from file: " + DATA_SPACE_MEMBERSHIP_FILE_PATH);
            monitor.debug("Reason: " + e.getMessage());
            throw new EdcException("Failed to remove membership: " + e.getMessage());
        }
    }

    public void removeAllMemberships() throws EdcException {
        try {
            membershipRepo.clear();
        } catch (Exception e) {
            monitor.debug("Failed to remove all memberships from file: " + DATA_SPACE_MEMBERSHIP_FILE_PATH);
            monitor.debug("Reason: " + e.getMessage());
            throw new EdcException("Failed to remove all memberships: " + e.getMessage());
        }
    }

}
