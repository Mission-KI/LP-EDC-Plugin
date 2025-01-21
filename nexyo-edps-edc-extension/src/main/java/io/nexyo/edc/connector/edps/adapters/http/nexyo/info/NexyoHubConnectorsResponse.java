package io.nexyo.edc.connector.edps.adapters.http.nexyo.info;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NexyoHubConnectorsResponse {
    @JsonProperty("data")
    private String[] connectors;

    public NexyoHubConnectorsResponse() {
    }

    public NexyoHubConnectorsResponse(String[] connectors) {
        this.connectors = connectors;
    }

    public String[] getConnectors() {
        return connectors;
    }

    public void setConnectors(String[] connectors) {
        this.connectors = connectors;
    }
}
