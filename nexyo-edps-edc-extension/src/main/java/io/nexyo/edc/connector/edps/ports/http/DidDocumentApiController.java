package io.nexyo.edc.connector.edps.ports.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.nexyo.edc.connector.edps.services.config.ConfigService;
import io.nexyo.edc.connector.edps.services.iam.did.OrganizationInfo;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.HashMap;

@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
@Path("/")
public class DidDocumentApiController {

    private final Monitor monitor;

    private DidDocument connectorDidDocument;

    private ConfigService configService;

    public DidDocumentApiController(Monitor monitor, DidDocument connectorDidDocument, ConfigService configService) {
        this.monitor = monitor;
        this.monitor.info("##### DidDocumentApiController constructor called");
        this.connectorDidDocument = connectorDidDocument;

        this.configService = configService;
    }

    @Path("/did.json")
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getDidDocument() {
        return this.handleGetDidDocument();
    }

    @Path("/.well-known/did.json")
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getWellKnownDidDocument() {
        return this.handleGetDidDocument();
    }

    @GET
    @Path("/organization")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getOrganizationInfo() {
        monitor.info("##### getOrganizationInfo endpoint called");
        var organizationInfo = new OrganizationInfo(configService);

        return Response.ok(organizationInfo).build();
    }

    private Response buildErrorResponse(int statusCode, String errorMessage) {
        if (statusCode <= 0) {
            statusCode = 500;
        }

        var errMessageHelper = new HashMap<String,String>();
        errMessageHelper.put("error", errorMessage);
        var errMessageJson = new Gson().toJson(errMessageHelper);

        return Response.status(statusCode).entity(errMessageJson).build();
    }

    private String generateDidDocumentRes() throws EdcException {
        var objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        String didDocument;
        try {
            didDocument = objectMapper.writeValueAsString(connectorDidDocument);
        } catch (Exception e) {
            throw new EdcException("Error while serializing DID document", e);
        }
        return didDocument;
    }

    private Response handleGetDidDocument() {
        monitor.info("##### getDidDocument endpoint called");

        String didDocument;
        try {
            didDocument = this.generateDidDocumentRes();
        } catch (EdcException e) {
            return this.buildErrorResponse(500, e.getMessage());
        }

        return Response.ok(didDocument).build();
    }
}
