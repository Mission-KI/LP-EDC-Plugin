package io.nexyo.edp.extensions.services;

import io.nexyo.edp.extensions.controllers.EdpsController;
import io.nexyo.edp.extensions.mappers.EdpsMapper;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.WebService;

/**
 * The EdpServiceExtension class is responsible for initializing the EDP service extension.
 */
public class EdpServiceExtension implements ServiceExtension {

    public static final String NAME = "EdpServiceExtension";

    @Inject
    private TypeTransformerRegistry registry;

    @Inject
    private WebService webService;

    private Monitor logger;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        logger = context.getMonitor();
        logger.info("EdpServiceExtension initialized");

        var transformer = new EdpsMapper();
        registry.register(transformer);

        var edpsController = new EdpsController(logger);
        webService.registerResource(edpsController);
    }


    @Override
    public void shutdown() {
        logger.info("Shutting down EDP extension");
    }

}
