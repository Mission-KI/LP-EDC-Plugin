package io.nexyo.edp.extensions.services;


import io.nexyo.edp.extensions.models.EdpsJobModel;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
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
    // api client goes here

    public EdpsService(Monitor monitor) {
        this.logger = monitor;
    }

    public EdpsJobModel createEdpsJob(String assetId, String baseUrl) {
        this.logger.info(String.format("Creating EDP job for %s...", assetId));

        Client client = ClientBuilder.newClient();
        Jsonb jsonb = JsonbBuilder.create();

        // Construct API URL
        String apiUrl = baseUrl + "/v1/dataspace/analysisjob";

        var requestBody = new HashMap<String, Object>();
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

        String jsonRequestBody = jsonb.toJson(requestBody);

        // Send HTTP POST request
        Response apiResponse = client.target(apiUrl)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(jsonRequestBody, MediaType.APPLICATION_JSON));

        // Read response as JSON
        String responseBody = apiResponse.readEntity(String.class);
        client.close();

        if (apiResponse.getStatus() == 201 || apiResponse.getStatus() == 200) {
            // Convert JSON response to EdpsJobModel
            return jsonb.fromJson(responseBody, EdpsJobModel.class);
        } else {
            logger.warning("Failed to create EDPS job: " + responseBody);
            throw new RuntimeException("EDPS job creation failed: " + responseBody);
        }
    }
}
