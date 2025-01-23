package io.nexyo.edp.extensions;

import org.eclipse.edc.spi.monitor.Monitor;

public class LoggingUtils {

    private static volatile Monitor LOGGER;

    LoggingUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static synchronized void setLogger(Monitor monitor) {
        LOGGER = monitor;
    }

    public static synchronized Monitor getLogger() {
        if (LOGGER == null) {
            throw new IllegalStateException("Logger not initialized. Call setLogger() first.");
        }
        return LOGGER;
    }
}
