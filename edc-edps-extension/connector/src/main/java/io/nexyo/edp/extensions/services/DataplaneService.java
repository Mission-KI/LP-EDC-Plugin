package io.nexyo.edp.extensions.services;

import io.nexyo.edp.extensions.utils.LoggingUtils;
import io.nexyo.edp.extensions.exceptions.EdpException;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataFlowResponse;
import org.eclipse.edc.connector.dataplane.selector.spi.DataPlaneSelectorService;
import org.eclipse.edc.connector.dataplane.selector.spi.client.DataPlaneClientFactory;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.spi.types.domain.transfer.FlowType;
import org.eclipse.edc.spi.types.domain.transfer.TransferType;

import java.util.UUID;


public class DataplaneService {

    private DataPlaneClientFactory clientFactory;

    private DataPlaneSelectorService selectorService;

    private AssetIndex assetIndexer;

    private Monitor logger;

    protected DataplaneService() {
    }

    public DataplaneService(DataPlaneSelectorService dataPlaneSelectorService, DataPlaneClientFactory clientFactory, AssetIndex assetIndexer) {
        this.selectorService = dataPlaneSelectorService;
        this.clientFactory = clientFactory;
        this.assetIndexer = assetIndexer;
        this.logger = LoggingUtils.getLogger();
    }


    private DataPlaneInstance getDataplane(DataAddress dataAddress) {
        var selection = selectorService.select(dataAddress, "HttpData-PUSH", null);
        var dataPlaneInstance = selection.getContent();

        if (dataPlaneInstance == null) {
            throw new EdpException("No data plane instance found");
        }
        return dataPlaneInstance;
    }

    public void start(String assetId, DataAddress destinationAddress) {
        var sourceAddress = this.assetIndexer.resolveForAsset(assetId);

        if (sourceAddress == null) {
            this.logger.severe("No source address found for asset id: " + assetId);
            throw new EdpException("No source address found for asset id: " + assetId);
        }

        var dataplaneInstance = getDataplane(sourceAddress);
        this.logger.info("Data flow starting with dataplane id: " + dataplaneInstance.getId());

        var dataFlowRequest = createDataFlowRequest(assetId, sourceAddress, destinationAddress);

        var result = clientFactory.createClient(dataplaneInstance)
                .start(dataFlowRequest)
                .map(it -> DataFlowResponse.Builder.newInstance()
                        .dataAddress(it.getDataAddress())
                        .dataPlaneId(dataplaneInstance.getId())
                        .build()
                );
        this.logger.info("Data flow response is: " + result);
    }


    public void start(DataAddress sourceAddress, DataAddress destinationAddress) {
        var dataplaneInstance = getDataplane(sourceAddress);
        this.logger.info("Data flow starting with dataplane id: " + dataplaneInstance.getId());

        var dataFlowRequest = createDataFlowRequest(null, sourceAddress, destinationAddress);

        var result = clientFactory.createClient(dataplaneInstance)
                .start(dataFlowRequest)
                .map(it -> DataFlowResponse.Builder.newInstance()
                        .dataAddress(it.getDataAddress())
                        .dataPlaneId(dataplaneInstance.getId())
                        .build()
                );
        this.logger.info("Data flow response is: " + result);
    }

    private DataFlowStartMessage createDataFlowRequest(String assetId, DataAddress sourceDataAddress, DataAddress destinationDataAddress) {
        TransferType transferType = new TransferType("HttpData", FlowType.PUSH);

        return DataFlowStartMessage.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .assetId(assetId)
                .sourceDataAddress(sourceDataAddress)
                .destinationDataAddress(destinationDataAddress)
                .processId("<not-needed>")
                .participantId("<not-needed>")
                .agreementId("<not-needed>")
                .transferType(transferType)
                //.callbackAddress(callbackUrl != null ? callbackUrl.get() : null)
                //.properties(propertiesResult.getContent())
                .build();
    }
}
