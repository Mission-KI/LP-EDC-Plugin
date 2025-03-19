package io.nexyo.edp.extensions.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for creating mock data.
 */
public class MockUtils {

    /**
     * Private constructor to prevent instantiation.
     */
    private MockUtils() {
    }

    /**
     * Creates a mock request body for creating an EDPS job.
     *
     *
     * @param assetId The unique identifier of the asset to create a job for
     * @return The request body for creating an EDPS job
     */
    public static Map<String, Object> createRequestBody(String assetId) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", "Example Analysis Job");

        // Create assetRef object
        Map<String, Object> assetRef = new HashMap<>();
        assetRef.put("assetId", assetId);
        assetRef.put("assetUrl", "https://example.com/data");
        assetRef.put("assetVersion", "1.0");

        Map<String, String> dataSpace = Map.of("name", "Example Dataspace", "url", "https://dataspace.com");
        assetRef.put("dataSpace", dataSpace);

        Map<String, String> publisher = Map.of("name", "Publisher Name", "url", "https://publisher.com");
        assetRef.put("publisher", publisher);

        assetRef.put("publishDate", "2025-01-22T16:25:09.719Z");

        Map<String, String> license = Map.of("name", "License Name", "url", "https://license.com");
        assetRef.put("license", license);

        // Add assetRef to assetRefs array
        List<Map<String, Object>> assetRefs = new ArrayList<>();
        assetRefs.add(assetRef);
        requestBody.put("assetRefs", assetRefs);

        // Add remaining fields at the root level
        requestBody.put("dataCategory", "Example Category");
        requestBody.put("assetProcessingStatus", "Original Data");
        requestBody.put("description", "Example Description");
        requestBody.put("tags", List.of("tag1", "tag2"));
        requestBody.put("dataSubCategory", "SubCategory");
        requestBody.put("assetTypeInfo", "string");
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
