package io.nexyo.edc.connector.edps;

import org.eclipse.edc.runtime.metamodel.annotation.Setting;

import static io.nexyo.edc.connector.edps.NexyoEdpsConstants.DID_API_ID;

// @Setting is an annotation that is used to declare a setting variable name that can be configured in the config.properties file.
public interface NexyoEdpsSettings {
    @Setting
    String VAULT_KEYPAIR_IDENTIFIER = "edc.nexyodataspace.did.keypair.id"; // public/private keypair of your connectors DID will be stored in the vault under this identifier
    @Setting
    String DID_API_PATH = "web.http." + DID_API_ID + ".path"; // base path of the did api (where the did document is served)
    @Setting
    String EDC_HOSTNAME = "edc.hostname"; // FQDN of your connector
    @Setting
    String DID_URL = "edc.identity.did.url"; // your connector's DID
    @Setting
    String ORGANIZATION_NAME = "edc.nexyodataspace.organization.name"; // used in the organization info service
    @Setting
    String ORGANIZATION_DESCRIPTION = "edc.nexyodataspace.organization.description"; // used in the organization info service
    @Setting
    String ORGANIZATION_WEBSITE = "edc.nexyodataspace.organization.website"; // used in the organization info service
    @Setting
    String DSP_CALLBACK_ADDRESS = "edc.dsp.callback.address"; // base url of your connector's dsp api
    @Setting
    String DID_WEB_USE_HTTPS = "edc.iam.did.web.use.https"; // specifies wether to use https or http to resolve DIDs
}
