package io.nexyo.edp.extensions.utils;

import org.eclipse.edc.boot.config.ConfigurationLoader;
import org.eclipse.edc.boot.config.EnvironmentVariables;
import org.eclipse.edc.boot.config.SystemProperties;
import org.eclipse.edc.boot.system.ServiceLocatorImpl;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.configuration.Config;

public class ConfigurationUtils {

    private static Config config;

    ConfigurationUtils() {
        throw new IllegalStateException("Utility class");
    }


    public static void loadConfig() {
        var configurationLoader = new ConfigurationLoader(
                new ServiceLocatorImpl(),
                EnvironmentVariables.ofDefault(),
                SystemProperties.ofDefault()
        );

        var logger = LoggingUtils.getLogger();
        config = configurationLoader.loadConfiguration(logger);
    }

    public static Config getConfig() {
        if (config == null) {
            loadConfig();
        }
        return config;
    }


    public static String readStringProperty(String key, String propertyName) {
        if (config == null) {
            loadConfig();
        }
        if( key == null || propertyName == null) {
            throw new EdcException("Key and propertyName cannot be null");
        }

        return config.getConfig(key).getString(propertyName);
    }

}

