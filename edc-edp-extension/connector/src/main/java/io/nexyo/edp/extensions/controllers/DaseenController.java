package io.nexyo.edp.extensions.controllers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nexyo.edp.extensions.dtos.internal.GenericResponseDto;
import io.nexyo.edp.extensions.dtos.internal.Status;
import io.nexyo.edp.extensions.services.EdpsService;
import io.nexyo.edp.extensions.utils.LoggingUtils;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.connector.controlplane.services.spi.asset.AssetService;
import org.eclipse.edc.spi.monitor.Monitor;

public class DaseenController implements DaseenInterface {

    private final Monitor logger;
    private final EdpsService edpsService;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String CALLBACK_INFO = "Check specified dataplane-callback address for updates.";


    public DaseenController(EdpsService edpsService) {
        this.logger = LoggingUtils.getLogger();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.edpsService = edpsService;
    }


    @Override
    public Response publishEdpsAssetToDaseen(String edpAssetId) {
        this.logger.info(String.format( "Publishing EDP asset %s to Daseen ", edpAssetId));
        this.edpsService.publishToDaseen(edpAssetId);

        final var response = new GenericResponseDto(
                "Publishing job for EDP result asset to Daseen dispatched to dataplane. " +
                        CALLBACK_INFO, Status.OK);

        return Response.status(Response.Status.OK)
                .entity(response)
                .build();
    }


    @Override
    public Response update(String daseenResourceId) {
        this.logger.info(String.format("Updating Daseen resource %s", daseenResourceId));

        // todo: update in daseen
        //this.edpsService.updateInDaseen("", daseenResourceId);

        return Response.status(Response.Status.OK)
                .entity(new GenericResponseDto("Update job for Daseen resource dispatched to dataplane. " +
                        CALLBACK_INFO, Status.OK))
                .build();
    }


    @Override
    public Response deleteEdpsAssetFromDaseen(String edpAssetId) {
        // TODO: get daseenJobId from Asset
        var daseenJobId = "";
        this.edpsService.deleteInDaseen(edpAssetId, daseenJobId);
        return null;
    }

}
