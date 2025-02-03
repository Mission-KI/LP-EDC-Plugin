package io.nexyo.edp.extensions.services;


import com.apicatalog.jsonld.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nexyo.edp.extensions.dtos.external.EdpsJobResponseDto;
import io.nexyo.edp.extensions.dtos.internal.EdpsJobDto;
import io.nexyo.edp.extensions.dtos.internal.EdpsResultRequestDto;
import io.nexyo.edp.extensions.exceptions.EdpException;
import io.nexyo.edp.extensions.utils.ConfigurationUtils;
import io.nexyo.edp.extensions.utils.LoggingUtils;
import io.nexyo.edp.extensions.utils.MockUtils;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.edc.connector.dataplane.http.spi.HttpDataAddress;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.transfer.FlowType;


/**
 * Service class responsible for handling EDPS-related operations.
 */
public class EdpsService {

    private final Monitor logger;
    private final Client httpClient = ClientBuilder.newClient();
    private String edpsBaseUrl;
    private String daseenBaseUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final DataplaneService dataplaneService;

    /**
     * Constructs an instance of EdpsService.
     *
     * @param dataplaneService the service responsible for handling data transfers.
     */
    public EdpsService(DataplaneService dataplaneService) {
        this.logger = LoggingUtils.getLogger();
        initRoutes();

        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.dataplaneService = dataplaneService;
    }

    /**
     * Initializes API routes by reading configuration properties.
     * Throws an exception if the required URLs are not configured.
     */
    private void initRoutes() {
        final String edpsApiUrlKey = "edp.edps.api";
        final String daseenApiUrlKey = "edp.daseen.api";

        final var confBaseUrl = ConfigurationUtils.readStringProperty(edpsApiUrlKey, "url");
        if (StringUtils.isBlank(confBaseUrl)) {
            throw new EdpException("EDPS API URL is not configured");
        }
        this.edpsBaseUrl = confBaseUrl;

        final var confDaseenBaseUrl = ConfigurationUtils.readStringProperty(daseenApiUrlKey, "url");
        if (StringUtils.isBlank(confDaseenBaseUrl)) {
            throw new EdpException("Daseen API URL is not configured");
        }
        this.daseenBaseUrl = confDaseenBaseUrl;
    }

    /**
     * Creates a new EDPS job for the specified asset ID.
     *
     * @param assetId the asset ID for which the job is created.
     * @return the response DTO containing job details.
     * @throws EdpException if the job creation fails.
     */
    public EdpsJobResponseDto createEdpsJob(String assetId) {
        this.logger.info(String.format("Creating EDP job for %s...", assetId));
        Jsonb jsonb = JsonbBuilder.create();
        var requestBody = MockUtils.createRequestBody(assetId);

        String jsonRequestBody = jsonb.toJson(requestBody);

        var apiResponse = httpClient.target(String.format("%s%s", this.edpsBaseUrl, "/v1/dataspace/analysisjob"))
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(jsonRequestBody, MediaType.APPLICATION_JSON));


        if (!(apiResponse.getStatus() >= 200 && apiResponse.getStatus() <= 300)) {
            this.logger.warning("Failed to create EDPS job for asset id: " + assetId + ". Status was: " + apiResponse.getStatus());
            throw new EdpException("EDPS job creation failed for asset id: " + assetId);
        }

        String responseBody = apiResponse.readEntity(String.class);

        this.logger.info("EDPS job created successfully for asset id: " + assetId + ". Edps Server responded: " + responseBody);

        try {
            return this.mapper.readValue(responseBody, EdpsJobResponseDto.class);
        } catch (JsonProcessingException e) {
            throw new EdpException("Unable to map response to DTO ", e);
        }
    }

    /**
     * Retrieves the status of an existing EDPS job.
     *
     * @param jobId the job ID.
     * @return the response DTO containing job status details.
     * @throws EdpException if the request fails.
     */
    public EdpsJobResponseDto getEdpsJobStatus(String jobId) {
        this.logger.info(String.format("Fetching EDPS Job status for job %s...", jobId));

        var apiResponse = this.httpClient.target(String.format("%s/v1/dataspace/analysisjob/%s/status", this.edpsBaseUrl, jobId))
                .request(MediaType.APPLICATION_JSON)
                .get();

        if (apiResponse.getStatus() < 200 || apiResponse.getStatus() >= 300) {
            String errorMessage = apiResponse.readEntity(String.class);
            this.logger.warning("Failed to fetch EDPS job status: " + errorMessage);
            throw new EdpException("Failed to fetch EDPS job status: " + errorMessage);
        }

        String responseBody = apiResponse.readEntity(String.class);

        try {
            return this.mapper.readValue(responseBody, EdpsJobResponseDto.class);
        } catch (JsonProcessingException e) {
            throw new EdpException("Unable to map response to DTO ", e);
        }
    }

    /**
     * Sends analysis data for a given EDPS job.
     *
     * @param edpsJobDto the job DTO containing job details.
     */
    public void sendAnalysisData(EdpsJobDto edpsJobDto) {
        var destinationAddress = HttpDataAddress.Builder.newInstance()
                .type(FlowType.PUSH.toString())
                .baseUrl(String.format("%s/v1/dataspace/analysisjob/%s/data", this.edpsBaseUrl, edpsJobDto.getJobId()))
                .property("header:upload_file", "data.csv")
                .build();

        this.dataplaneService.start(edpsJobDto.getAssetId(), destinationAddress);
    }

    /**
     * Fetches the result of an EDPS job.
     *
     * @param assetId             the asset ID.
     * @param jobId               the job ID.
     * @param edpResultRequestDto the request DTO containing result destination details.
     */
    public void fetchEdpsJobResult(String assetId, String jobId, EdpsResultRequestDto edpResultRequestDto) {
        this.logger.info(String.format("Fetching EDPS Job Result ZIP for asset %s for job %s...", assetId, jobId));

        var sourceAddress = HttpDataAddress.Builder.newInstance()
                .type(FlowType.PULL.toString())
                .baseUrl(String.format("%s/v1/dataspace/analysisjob/%s/result", this.edpsBaseUrl, jobId))
                .build();

        var destinationAddress = HttpDataAddress.Builder.newInstance()
                .type(FlowType.PUSH.toString())
                .baseUrl(edpResultRequestDto.destinationAddress())
                .build();

        this.dataplaneService.start(sourceAddress, destinationAddress);
    }

    /**
     * Publishes the EDPS job result to Daseen.
     *
     * @param assetId the asset ID to be published.
     */
    public void publishToDaseen(String assetId) {
        var destinationAddress = HttpDataAddress.Builder.newInstance()
                .type(FlowType.PUSH.toString())
                .baseUrl(String.format("%s/create-edp", this.daseenBaseUrl))
                .build();

        this.dataplaneService.start(assetId, destinationAddress);
    }

    /**
     * Closes the HTTP client.
     */
    public void close() {
        this.logger.info("Closing HTTP client...");
        this.httpClient.close();
    }

}
