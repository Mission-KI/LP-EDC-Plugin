package io.nexyo.edp.extensions.services;

import io.nexyo.edp.extensions.controllers.DaseenController;
import io.nexyo.edp.extensions.controllers.EdpsController;
import io.nexyo.edp.extensions.utils.ConfigurationUtils;
import io.nexyo.edp.extensions.utils.LoggingUtils;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.services.spi.asset.AssetService;
import org.eclipse.edc.connector.dataplane.selector.spi.DataPlaneSelectorService;
import org.eclipse.edc.connector.dataplane.selector.spi.client.DataPlaneClientFactory;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;

/**
 * The EdpServiceExtension class is responsible for initializing the EDP service extension.
 */
public class EdpServiceExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "EdpServiceExtension";

    @Inject
    private WebService webService;

    private Monitor logger;

    @Inject
    private DataPlaneClientFactory clientFactory;

    @Inject
    private DataPlaneSelectorService dataPlaneSelectorService;

    @Inject
    private AssetService assetService;

    @Inject
    AssetIndex assetIndexer;

    private EdpsService edpsService;

    private DaseenService daseenService;


    @Override
    public String name() {
        return EXTENSION_NAME;
    }



    @Override
    public void initialize(ServiceExtensionContext context) {
        logger = context.getMonitor();
        LoggingUtils.setLogger(logger);
        ConfigurationUtils.loadConfig();
        logger.info("EdpServiceExtension initialized");

        final var dataplaneService = new DataplaneService(dataPlaneSelectorService, clientFactory, assetIndexer);
        this.edpsService = new EdpsService(dataplaneService);
        this.daseenService = new DaseenService(dataplaneService);
        final var edpsController = new EdpsController(edpsService, assetService);
        final var daseenController = new DaseenController(daseenService, assetService);

        webService.registerResource(edpsController);
        webService.registerResource(daseenController);
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down EDP extension");
        this.edpsService.close();
        this.daseenService.close();
    }

}
