package io.nexyo.edp.extensions;

import org.eclipse.edc.boot.system.DefaultServiceExtensionContext;
import org.eclipse.edc.boot.system.runtime.BaseRuntime;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.jetbrains.annotations.NotNull;

public class Runner extends BaseRuntime {

    public static void main(String[] args) {
        new Runner().boot(true);
    }

    @Override
    protected @NotNull ServiceExtensionContext createContext(Monitor monitor, Config config) {
        return new SuperCustomExtensionContext(monitor, config);
    }

    private static class SuperCustomExtensionContext extends DefaultServiceExtensionContext {
        SuperCustomExtensionContext(Monitor monitor, Config config) {
            super(monitor, config);
        }
    }
}