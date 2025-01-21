package io.nexyo.edc.connector.edps.services.config;

import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import static java.lang.String.format;

public class ConfigService {
    private ServiceExtensionContext context;

    public ConfigService(ServiceExtensionContext context) {
        this.context = context;
    }

    public String getRequiredConfigValue(String configVariableName) throws EdcException {
        var configValue = context.getSetting(configVariableName, null);
        if (configValue == null || configValue.isBlank()) {
            throw new EdcException(format("%s needs to be defined as a config variable to retrieve identity information", configVariableName));
        }
        return configValue;
    }
}
