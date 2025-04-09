package io.nexyo.edc.connector.edps.services.iam.did;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nexyo.edc.connector.edps.services.config.ConfigService;
import org.eclipse.edc.spi.EdcException;

import static io.nexyo.edc.connector.edps.NexyoEdpsSettings.*;

public class OrganizationInfo {
    @JsonProperty("organizationName")
    private String name;
    @JsonProperty("description")
    private String description;
    @JsonProperty("website")
    private String website;

    public OrganizationInfo(ConfigService configService) throws EdcException {
        this.name = configService.getRequiredConfigValue(ORGANIZATION_NAME);
        this.description = configService.getRequiredConfigValue(ORGANIZATION_DESCRIPTION);
        this.website = configService.getRequiredConfigValue(ORGANIZATION_WEBSITE);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getWebsite() {
        return website;
    }
}
