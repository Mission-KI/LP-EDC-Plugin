package io.nexyo.edp.extensions.services;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nexyo.edp.extensions.dtos.external.EdpsJobResponseDto;
import io.nexyo.edp.extensions.dtos.internal.EdpsJobDto;
import io.nexyo.edp.extensions.LoggingUtils;
import io.nexyo.edp.extensions.exceptions.EdpException;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataFlowResponse;
import org.eclipse.edc.connector.dataplane.selector.spi.DataPlaneSelectorService;
import org.eclipse.edc.connector.dataplane.selector.spi.client.DataPlaneClientFactory;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.spi.monitor.Monitor;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.bind.Jsonb;

public class EdpsService {

    private final Monitor logger;
    private Client client = ClientBuilder.newClient();
    private String baseUrl;
    private String createEdpsJobUrl;
    private ObjectMapper mapper = new ObjectMapper();
    private DataplaneService dataplaneService;

    public EdpsService(String baseUrl) {
        this.logger = LoggingUtils.getLogger();
        this.baseUrl = baseUrl;
        this.createEdpsJobUrl = baseUrl + "v1/dataspace/analysisjob";
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.dataplaneService = new DataplaneService();
    }

    // todo: EdpsJobDto should be a model
    public void sendAnalysisData(EdpsJobDto edpsJobDto) {
        // 1. get asset by assetUuid
        // 2. download the referenced file (dataplane should do this)
        // 3. dataplane post the file to edps api
    }

    public EdpsJobResponseDto createEdpsJob(String assetId) {
        this.logger.info(String.format("Creating EDP job for %s...", assetId));

        Jsonb jsonb = JsonbBuilder.create();
        var requestBody = createRequestBody(assetId);

        String jsonRequestBody = jsonb.toJson(requestBody);

        Response apiResponse = client.target(createEdpsJobUrl)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(jsonRequestBody, MediaType.APPLICATION_JSON));

        String responseBody = apiResponse.readEntity(String.class);
        client.close();

        if (!(apiResponse.getStatus() >= 200 && apiResponse.getStatus() <= 300)) {
            logger.warning("Failed to create EDPS job: " + responseBody);
            throw new EdpException("EDPS job creation failed: " + responseBody);
        }

        try {
            return this.mapper.readValue(responseBody, EdpsJobResponseDto.class);
        } catch (JsonProcessingException e) {
            throw new EdpException(e.getMessage());
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
}
