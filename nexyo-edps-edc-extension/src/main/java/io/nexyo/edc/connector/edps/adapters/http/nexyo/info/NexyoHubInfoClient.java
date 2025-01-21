package io.nexyo.edc.connector.edps.adapters.http.nexyo.info;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nexyo.edc.connector.edps.services.iam.did.DecentralizedIdentityService;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.http.EdcHttpClient;
import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.io.IOException;

public class NexyoHubInfoClient {
    private final EdcHttpClient httpClient;
    private final DecentralizedIdentityService identityService;
    private final ObjectMapper mapper;
    private final Monitor monitor;

    public NexyoHubInfoClient(EdcHttpClient httpClient, DecentralizedIdentityService identityService, ObjectMapper mapper, Monitor monitor) {
        this.httpClient = httpClient;
        this.identityService = identityService;
        this.mapper = mapper;
        this.monitor = monitor;
    }

    public Result<NexyoHubDataSpaceInfoResponse> getDataSpaceInfo(String dataSpaceInfoServiceEndpoint, String dataSpaceControllerDid) {
        Headers headers;
        try {
            monitor.debug("generating headers");
            headers = generateBearerHeader(dataSpaceControllerDid);
        } catch (EdcException e) {
            monitor.debug("Could not generate Bearer Header: " + e.getMessage());
            return Result.failure("Could not generate Bearer Header: " + e.getMessage());
        }

        monitor.debug("headers: " + headers);

        Request getDataSpaceInfoRequest;
        try {
            getDataSpaceInfoRequest = new Request.Builder().url(dataSpaceInfoServiceEndpoint).headers(headers).build();
        } catch (Exception e) {
            return Result.failure("Create Request Exception: " + e.getMessage());
        }

        try {
            var res = httpClient.execute(getDataSpaceInfoRequest);
            if (res.code() != 200) {
                return Result.failure("DataSpaceInfo service returned status code " + res.code());
            }

            var dataSpaceInfoJson = res.body().string();

            // make sure json is of expected type
            try {
                var dsInfo = mapper.readValue(dataSpaceInfoJson, NexyoHubDataSpaceInfoResponse.class);
                return Result.success(dsInfo);
            } catch (Exception e) {
                return Result.failure("DataSpaceInfo service returned unexpected response: " + e.getMessage());
            }

        } catch (IOException e) {
            return Result.failure("Error while calling DataSpaceInfo service: " + e.getMessage());
        }
    }

    private Headers generateBearerHeader(String audienceIdentity) throws EdcException {
        var tokenParameters = TokenParameters.Builder.newInstance()
                .audience(audienceIdentity)
                .build();
        var result = identityService.obtainClientCredentials(tokenParameters);
        if (result.failed()) {
            throw new EdcException("Could not generate Bearer Header: " + result.getFailure().toString());
        }
        var jwt = result.getContent().getToken();

        return new Headers.Builder().add("Authorization", "Bearer " + jwt).build();
    }

    public Result<NexyoHubConnectorsResponse> getHubConnectors(String connectorsServiceEndpoint, String hubDid) {
        Headers headers;
        try {
            monitor.debug("generating headers");
            headers = generateBearerHeader(hubDid);
        } catch (EdcException e) {
            monitor.debug("Could not generate Bearer Header: " + e.getMessage());
            return Result.failure("Could not generate Bearer Header: " + e.getMessage());
        }

        monitor.debug("headers: " + headers);

        Request getHubConnectorsRequest;
        try {
            getHubConnectorsRequest = new Request.Builder().url(connectorsServiceEndpoint).headers(headers).build();
        } catch (Exception e) {
            return Result.failure("Create Request Exception: " + e.getMessage());
        }

        try {
            var res = httpClient.execute(getHubConnectorsRequest);
            if (res.code() != 200) {
                return Result.failure("DataSpaceInfo service returned status code " + res.code());
            }

            var hubConnectorsJson = res.body().string();
            try {
                var hubConnectors = mapper.readValue(hubConnectorsJson, NexyoHubConnectorsResponse.class);
                return Result.success(hubConnectors);
            } catch (Exception e) {
                return Result.failure("DataSpaceInfo service returned unexpected response: " + hubConnectorsJson);
            }

        } catch (IOException e) {
            return Result.failure("Error while calling DataSpaceInfo service: " + e.getMessage());
        }
    }
}
