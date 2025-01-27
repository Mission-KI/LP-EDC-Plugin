package io.nexyo.edp.extensions.controllers;

import com.apicatalog.jsonld.StringUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nexyo.edp.extensions.LoggingUtils;
import io.nexyo.edp.extensions.dtos.internal.EdpsJobDto;
import io.nexyo.edp.extensions.exceptions.EdpException;
import io.nexyo.edp.extensions.services.DataplaneService;
import io.nexyo.edp.extensions.services.EdpsInterface;
import io.nexyo.edp.extensions.services.EdpsService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.boot.config.ConfigurationLoader;
import org.eclipse.edc.boot.config.EnvironmentVariables;
import org.eclipse.edc.boot.config.SystemProperties;
import org.eclipse.edc.boot.system.ServiceLocatorImpl;
import org.eclipse.edc.spi.monitor.Monitor;


@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EdpsController implements EdpsInterface {

    private final Monitor logger;
    private final EdpsService edpsService;
    private final String edpsApiUrlKey = "epd.edps.api";
    private final ObjectMapper mapper = new ObjectMapper();

    public EdpsController(DataplaneService dataplaneService) {
        this.logger = LoggingUtils.getLogger();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ConfigurationLoader configurationLoader = new ConfigurationLoader(
                new ServiceLocatorImpl(),
                EnvironmentVariables.ofDefault(),
                SystemProperties.ofDefault()
        );

        var config = configurationLoader.loadConfiguration(this.logger);
        String baseURl = config.getConfig(edpsApiUrlKey).getString("url");

        if (StringUtils.isBlank(baseURl)) {
            throw new EdpException("EDPS API URL is not configured");
        }

        this.edpsService = new EdpsService(baseURl, dataplaneService);
    }


    @Override
    public Response getEdpsJob(String assetId) {
        logger.info("Getting EDP job for asset " + assetId);

        return Response.status(200)
                .entity("all good")
                .build();
    }

    @Override
    public Response createEdpsJob(String assetId) {
        logger.info("Creating EDP job...");
        var edpsJobResponseDto = this.edpsService.createEdpsJob(assetId);
        var edpsJobDto = mapper.convertValue(edpsJobResponseDto, EdpsJobDto.class);
        edpsJobDto.setAssetId(assetId);

        this.edpsService.sendAnalysisData(edpsJobDto);

        return Response.status(Response.Status.OK)
                .entity(edpsJobDto)
                .build();
    }

    @Override
    public Response getEdpsJobStatus(String assetId, String jobId) {
        return null;
    }

    @Override
    public Response createEdpsJobResultAsset(String assetId, String jobId, String requestBody) {
        return null;
    }

}
