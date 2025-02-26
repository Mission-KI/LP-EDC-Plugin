package io.nexyo.edp.extensions.services;

import com.apicatalog.jsonld.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nexyo.edp.extensions.dtos.external.DaseenCreateResourceResponseDto;
import io.nexyo.edp.extensions.exceptions.EdpException;
import io.nexyo.edp.extensions.utils.ConfigurationUtils;
import io.nexyo.edp.extensions.utils.LoggingUtils;
import io.nexyo.edp.extensions.utils.MockUtils;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.edc.connector.dataplane.http.spi.HttpDataAddress;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.transfer.FlowType;

/**
 * DaseenService
 */
public class DaseenService {

    private final Monitor logger;
    private final Client httpClient = ClientBuilder.newClient();
    private String daseenBaseUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final DataplaneService dataplaneService;

    /**
     * Constructor for the DaseenService.
     *
     * @param dataplaneService the dataplane service
     */
    public DaseenService(DataplaneService dataplaneService) {
        this.logger = LoggingUtils.getLogger();
        initRoutes();

        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.dataplaneService = dataplaneService;
    }

    /**
     * Initializes the routes.
     */
    private void initRoutes() {
        final String daseenApiUrlKey = "edp.daseen.api";

        final var confDaseenBaseUrl = ConfigurationUtils.readStringProperty(daseenApiUrlKey, "url");
        if (StringUtils.isBlank(confDaseenBaseUrl)) {
            throw new EdpException("Daseen API URL is not configured");
        }
        this.daseenBaseUrl = confDaseenBaseUrl;
    }

    /**
     * Creates a Daseen resource.
     *
     * @param assetId the asset ID to create the resource for.
     * @return the DaseenCreateResourceResponseDto
     */
    public DaseenCreateResourceResponseDto createDaseenResource(String assetId) {
        this.logger.info(String.format("Creating Daseen Resource for Asset: %s...", assetId));

        var apiResponse = httpClient.target(String.format("%s/connector/edp", this.daseenBaseUrl))
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(MockUtils.createRequestBody(assetId), MediaType.APPLICATION_JSON));

        if (apiResponse.getStatus() != 201) {
            this.logger.warning("Failed to create EDP entry in Daseen for asset id: " + assetId + ". Status was: " + apiResponse.getStatus());
            throw new EdpException("EDPS job creation failed for asset id: " + assetId);
        }

        String responseBody = apiResponse.readEntity(String.class);

        try {
            return this.mapper.readValue(responseBody, DaseenCreateResourceResponseDto.class);
        } catch (JsonProcessingException e) {
            throw new EdpException("Unable to map response to DTO ", e);
        }
    }

    /**
     * Publishes the EDPS job result to Daseen.
     *
     * @param assetId the asset ID to be published.
     */
    public void publishToDaseen(String assetId, String daseenResourceId) {
        var destinationAddress = HttpDataAddress.Builder.newInstance()
                .type(FlowType.PUSH.toString())
                .method(HttpMethod.POST)
                .baseUrl(String.format("%s/connector/edp/%s", this.daseenBaseUrl, daseenResourceId))
                .build();

        // todo: add missing args
        //this.dataplaneService.start(assetId, destinationAddress, participantId, aggrementId);
    }

    /**
     * Updates the EDPS job result in Daseen.
     *
     * @param assetId the asset ID to be updated.
     * @param daseenResourceId the Daseen resource ID.
     */
    public void updateInDaseen(String assetId, String daseenResourceId) {
        var destinationAddress = HttpDataAddress.Builder.newInstance()
                .type(FlowType.PUSH.toString())
                .method(HttpMethod.PUT)
                .baseUrl(String.format("%s/connector/edp/%s", this.daseenBaseUrl, daseenResourceId))
                .build();

        // todo: add missing args
        //this.dataplaneService.start(assetId, destinationAddress);
    }

    /**
     * Deletes the EDPS job result in Daseen.
     *
     * @param assetId the asset ID to be deleted.
     * @param daseenJobId the Daseen job ID.
     */ 
    public void deleteInDaseen(String assetId, String daseenJobId) {
        this.logger.info(String.format("Deleting EDP Entry in Daseen for Asset: %s...", assetId));

        var apiResponse = httpClient.target(String.format("%s/connector/edp/%s", this.daseenBaseUrl, daseenJobId))
                .request(MediaType.APPLICATION_JSON)
                .delete();

        if (!(apiResponse.getStatus() == 204 || apiResponse.getStatus() == 200)) {
            this.logger.warning("Failed to delete EDP entry in Daseen for asset id: " + assetId + ". Status was: " + apiResponse.getStatus());
            throw new EdpException("EDPS job creation failed for asset id: " + assetId);
        }
    }

    /**
     * Closes the HTTP client.
     */
    public void close() {
        this.logger.info("Closing HTTP client...");
        this.httpClient.close();
    }

}
