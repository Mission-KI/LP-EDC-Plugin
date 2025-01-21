package io.nexyo.edc.connector.edps;

// @Setting is an annotation that is used to declare a setting variable name that can be configured in the config.properties file.
public interface NexyoEdpsConstants {
    String DID_API_ID = "did";
    String MGMT_API_ID = "management";
    String DS_INFO_SVC_ID_SUFFIX = "#DataSpaceInfo"; // used in the DataSpace DID document to identify the DataSpaceInfo service
    String CONNECTORS_SVC_ID_SUFFIX = "#ConnectorsEndpoint"; // used in the nexyo hub DID document to identify the Connector service
    String NEXYO_SPACE_MEMBERSHIP_CONSTRAINT_KEY = "nexyo:spaceMembership";

    String NEXYO_SPACE_MEMBERSHIPS_AGENT_ATTRIBUTE = "spaceMemberships";

    String DATA_SPACE_MEMBERSHIP_FILE_PATH = "/app/space-memberships.txt";
}
