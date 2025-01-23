package io.nexyo.edp.extensions;

import org.eclipse.edc.spi.monitor.Monitor;

public class LoggingUtils {

    private static Monitor LOGGER;

    LoggingUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static void setLogger(Monitor monitor) {
        LOGGER = monitor;
    }

    public static Monitor getLogger() {
        return LOGGER;
    }
}
