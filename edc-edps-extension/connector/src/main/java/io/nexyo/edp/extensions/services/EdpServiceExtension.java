package io.nexyo.edp.extensions.services;

import io.nexyo.edp.extensions.LoggingUtils;
import io.nexyo.edp.extensions.controllers.EdpsController;
import io.nexyo.edp.extensions.mappers.EdpsMapper;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.dataplane.selector.spi.DataPlaneSelectorService;
import org.eclipse.edc.connector.dataplane.selector.spi.client.DataPlaneClientFactory;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
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

    @Inject
    private DataPlaneClientFactory clientFactory;

    @Inject
    private DataPlaneSelectorService dataPlaneSelectorService;

    @Inject
    AssetIndex assetIndexer;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        logger = context.getMonitor();
        LoggingUtils.setLogger(logger);
        logger.info("EdpServiceExtension initialized");

        var transformer = new EdpsMapper();
        registry.register(transformer);

        var dataplaneService = new DataplaneService(dataPlaneSelectorService, clientFactory, assetIndexer);
        var edpsController = new EdpsController(dataplaneService);
        webService.registerResource(edpsController);
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down EDP extension");
    }

}
