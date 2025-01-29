package io.nexyo.edp.extensions.services;


import com.apicatalog.jsonld.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nexyo.edp.extensions.dtos.external.EdpsJobResponseDto;
import io.nexyo.edp.extensions.dtos.internal.EdpsJobDto;
import io.nexyo.edp.extensions.dtos.internal.EdpsResultRequestDto;
import io.nexyo.edp.extensions.exceptions.EdpException;
import io.nexyo.edp.extensions.utils.LoggingUtils;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.edc.connector.dataplane.http.spi.HttpDataAddress;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.types.domain.transfer.FlowType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EdpsService {

    private final Monitor logger;
    private final Client httpClient = ClientBuilder.newClient();
    private String baseUrl;
    private String daseenBaseUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final DataplaneService dataplaneService;

    public EdpsService(Config config, DataplaneService dataplaneService) {
        this.logger = LoggingUtils.getLogger();
        initRoutes(config);

        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.dataplaneService = dataplaneService;
    }

    private void initRoutes(Config config) {
        final String edpsApiUrlKey = "epd.edps.api";
        final String daseenApiUrlKey = "edp.daseen.api";

        String confBaseUrl = config.getConfig(edpsApiUrlKey).getString("url");
        if (StringUtils.isBlank(confBaseUrl)) {
            throw new EdpException("EDPS API URL is not configured");
        }
        this.baseUrl = confBaseUrl;

        String confDaseenBaseUrl = config.getConfig(daseenApiUrlKey).getString("url");
        if (StringUtils.isBlank(confDaseenBaseUrl)) {
            throw new EdpException("Daseen API URL is not configured");
        }
        this.daseenBaseUrl = confDaseenBaseUrl;
    }


    public EdpsJobResponseDto createEdpsJob(String assetId) {
        this.logger.info(String.format("Creating EDP job for %s...", assetId));

        Jsonb jsonb = JsonbBuilder.create();
        var requestBody = createRequestBody(assetId);

        String jsonRequestBody = jsonb.toJson(requestBody);

        var apiResponse = httpClient.target(String.format("%s%s", this.baseUrl, "/v1/dataspace/analysisjob"))
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(jsonRequestBody, MediaType.APPLICATION_JSON));


        if (!(apiResponse.getStatus() >= 200 && apiResponse.getStatus() <= 300)) {
            this.logger.warning("Failed to create EDPS job for asset id: " + assetId + ". Status was: " + apiResponse.getStatus());
            throw new EdpException("EDPS job creation failed for asset id: " + assetId);
        }

        String responseBody = apiResponse.readEntity(String.class);
        httpClient.close();

        this.logger.info("EDPS job created successfully for asset id: " + assetId + ". Edps Server responded: " + responseBody);

        try {
            return this.mapper.readValue(responseBody, EdpsJobResponseDto.class);
        } catch (JsonProcessingException e) {
            throw new EdpException("Unable to map response to DTO ", e);
        }
    }

    private Map<String, Object> createRequestBody(String assetId) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("assetId", assetId);
        requestBody.put("name", "Example Analysis Job");
        requestBody.put("url", "https://example.com/data");
        requestBody.put("dataCategory", "Example Category");

        Map<String, String> dataSpace = Map.of("name", "Example Dataspace", "url", "https://dataspace.com");
        requestBody.put("dataSpace", dataSpace);

        Map<String, String> publisher = Map.of("name", "Publisher Name", "url", "https://publisher.com");
        requestBody.put("publisher", publisher);

        requestBody.put("publishDate", "2025-01-22T16:25:09.719Z");

        Map<String, String> license = Map.of("name", "License Name", "url", "https://license.com");
        requestBody.put("license", license);

        requestBody.put("assetProcessingStatus", "Original Data");
        requestBody.put("description", "Example Description");
        requestBody.put("tags", List.of("tag1", "tag2"));
        requestBody.put("dataSubCategory", "SubCategory");
        requestBody.put("version", "1.0");
        requestBody.put("transferTypeFlag", "static");
        requestBody.put("immutabilityFlag", "immutable");
        requestBody.put("growthFlag", "KB");
        requestBody.put("transferTypeFrequency", "second");
        requestBody.put("nda", "NDA text");
        requestBody.put("dpa", "DPA text");
        requestBody.put("dataLog", "Data Log Entry");
        requestBody.put("freely_available", true);

        return requestBody;
    }

    public EdpsJobResponseDto getEdpsJobStatus(String jobId) {
        this.logger.info(String.format("Fetching EDPS Job status for job %s...", jobId));

        var apiResponse = this.httpClient.target(String.format("%s/v1/dataspace/analysisjob/%s/status", this.baseUrl, jobId))
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

    public void sendAnalysisData(EdpsJobDto edpsJobDto) {
        var destinationAddress = HttpDataAddress.Builder.newInstance()
                .type(FlowType.PUSH.toString())
                .baseUrl(String.format("%s/v1/dataspace/analysisjob/%s/data", this.baseUrl, edpsJobDto.getJobUuid()))
                .property("header:upload_file", "data.csv")
                .build();

        this.dataplaneService.start(edpsJobDto.getAssetId(), destinationAddress);
    }

    public void fetchEdpsJobResult(String assetId, String jobId, EdpsResultRequestDto edpResultRequestDto) {
        this.logger.info(String.format("Fetching EDPS Job Result ZIP for asset %s for job %s...", assetId, jobId));

        var sourceAddress = HttpDataAddress.Builder.newInstance()
                .type(FlowType.PULL.toString())
                .baseUrl(String.format("%s/v1/dataspace/analysisjob/%s/result", this.baseUrl, jobId))
                .build();

        var destinationAddress = HttpDataAddress.Builder.newInstance()
                .type(FlowType.PUSH.toString())
                .baseUrl(edpResultRequestDto.destinationAddress())
                .build();

        this.dataplaneService.start(sourceAddress, destinationAddress);
    }

    public void publishToDaseen(String assetId) {
        var destinationAddress = HttpDataAddress.Builder.newInstance()
                .type(FlowType.PUSH.toString())
                .baseUrl(String.format("%s/create-edp", this.daseenBaseUrl))
                .build();

        this.dataplaneService.start(assetId, destinationAddress);
    }

}
