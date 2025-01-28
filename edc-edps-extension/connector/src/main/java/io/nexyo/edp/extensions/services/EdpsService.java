package io.nexyo.edp.extensions.services;


import com.apicatalog.jsonld.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nexyo.edp.extensions.LoggingUtils;
import io.nexyo.edp.extensions.dtos.external.EdpsJobResponseDto;
import io.nexyo.edp.extensions.dtos.internal.EdpsJobDto;
import io.nexyo.edp.extensions.dtos.internal.EdpsResultRequestDto;
import io.nexyo.edp.extensions.exceptions.EdpException;
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
    private String createEdpsJobUri;
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

        this.createEdpsJobUri = "v1/dataspace/analysisjob";
    }


    public EdpsJobResponseDto createEdpsJob(String assetId) {
        this.logger.info(String.format("Creating EDP job for %s...", assetId));

        Jsonb jsonb = JsonbBuilder.create();
        var requestBody = createRequestBody(assetId);

        String jsonRequestBody = jsonb.toJson(requestBody);

        var apiResponse = httpClient.target(this.baseUrl + createEdpsJobUri)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(jsonRequestBody, MediaType.APPLICATION_JSON));

        String responseBody = apiResponse.readEntity(String.class);
        httpClient.close();

        if (!(apiResponse.getStatus() >= 200 && apiResponse.getStatus() <= 300)) {
            this.logger.warning("Failed to create EDPS job: " + responseBody + " status: " + apiResponse.getStatus());
            throw new EdpException("EDPS job creation failed: " + responseBody);
        }

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

    public void sendAnalysisData(EdpsJobDto edpsJobDto) {
        // 1. get asset by assetUuid
        // 2. download the referenced file (dataplane should do this)
        // 3. dataplane post the file to edps api
        var destinationAddress = HttpDataAddress.Builder.newInstance()
                .type(FlowType.PUSH.toString())
                .baseUrl("http://localhost:8080/upload")  // todo: change to edps base url  //  String.format("this.baseUrl/%s/%s/data", this.createEdpsJobUri, edpsJobDto.getJobUuid())
                .property("header:upload_file", "data.csv")
                .build();

        this.dataplaneService.start(edpsJobDto.getAssetId(), destinationAddress);
    }

    public void fetchEdpsJobResult(String assetId, String jobId, EdpsResultRequestDto edpResultRequestDto) {
        this.logger.info(String.format("Fetching EDPS Job Result ZIP for asset %s for job %s...", assetId, jobId));

        var sourceAddress = HttpDataAddress.Builder.newInstance()
                .type(FlowType.PULL.toString())
                //.baseUrl(String.format("%s/v1/dataspace/analysisjob/%s/result", this.baseUrl, jobId)) // todo: use this when the API is ready
                .baseUrl("http://localhost:8080/data.zip")
                .build();

        var destinationAddress = HttpDataAddress.Builder.newInstance()
                .type(FlowType.PUSH.toString())
                .baseUrl(edpResultRequestDto.destinationAddress())
                .property("header:X-File-Name", "edps-result-data.zip")
                .build();

        this.dataplaneService.start(sourceAddress, destinationAddress);
    }

    public void publishToDaseen(String assetId) {
        var destinationAddress = HttpDataAddress.Builder.newInstance()
                .type(FlowType.PUSH.toString())
                .baseUrl("http://localhost:8081/")
                //.baseUrl(String.format("%s/create-edp", this.daseenBaseUrl)) // todo: use this when the API is ready
                .build();

        this.dataplaneService.start(assetId, destinationAddress);
    }


}
