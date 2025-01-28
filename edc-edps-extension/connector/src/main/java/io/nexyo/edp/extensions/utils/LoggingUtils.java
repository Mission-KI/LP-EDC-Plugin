package io.nexyo.edp.extensions;

import org.eclipse.edc.spi.monitor.Monitor;

public class LoggingUtils {

    private static Monitor logger;

    LoggingUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static synchronized void setLogger(Monitor monitor) {
        logger = monitor;
    }

    public static synchronized Monitor getLogger() {
        if (logger == null) {
            throw new IllegalStateException("Logger not initialized. Call setLogger() first.");
        }
        return logger;
    }
}
