package io.nexyo.edp.extensions.services;

import io.nexyo.edp.extensions.LoggingUtils;
import io.nexyo.edp.extensions.exceptions.EdpException;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataFlowResponse;
import org.eclipse.edc.connector.dataplane.selector.spi.DataPlaneSelectorService;
import org.eclipse.edc.connector.dataplane.selector.spi.client.DataPlaneClientFactory;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;


public class DataplaneService {
    private DataPlaneClientFactory clientFactory;

    private DataPlaneSelectorService selectorService;

    private final Monitor logger;

    // todo: remove this, clientFactory and selectorService should be injected in the EdpServiceExtension and pass down via args
    public DataplaneService() {
        this.logger = LoggingUtils.getLogger();
    }

    public DataplaneService(DataPlaneSelectorService dataPlaneSelectorService, DataPlaneClientFactory clientFactory) {
        this.selectorService = dataPlaneSelectorService;
        this.clientFactory = clientFactory;
        this.logger = LoggingUtils.getLogger();
    }


    private DataPlaneInstance getDataplane(DataAddress dataAddress) {
        // todo: pass correct args
        var selection = selectorService.select(dataAddress, "", null);

        var dataPlaneInstance = selection.getContent();

        if (dataPlaneInstance == null) {
            throw new EdpException("No data plane instance found");
        }
        return dataPlaneInstance;
    }

    public void start(String assetId) {
        var sourceAddress = DataAddress.Builder.newInstance()
            .type("GET") // todo: set the correct type
            // todo: add data address
            .build();

        var dataPlaneInstance = getDataplane(sourceAddress);
        this.logger.info("Data flow starting with dataplane id: " + dataPlaneInstance.getId());

        var result = clientFactory.createClient(dataPlaneInstance)
                .start(createDataFlowRequest(assetId))
                .map(it -> DataFlowResponse.Builder.newInstance()
                        .dataAddress(it.getDataAddress())
                        .dataPlaneId(dataPlaneInstance.getId())
                        .build()
                );
        this.logger.info("Data flow response is: " + result);
    }

    private DataFlowStartMessage createDataFlowRequest(String assetId) {
        // todo: create the correct dataflow
        var destinationAddress = DataAddress.Builder.newInstance()
                .type("POST") // todo: set the correct type
                .build();

        return DataFlowStartMessage.Builder.newInstance()
                .assetId(assetId)
                .destinationType("")
                .destinationDataAddress(destinationAddress)
                .build();
    }
}
