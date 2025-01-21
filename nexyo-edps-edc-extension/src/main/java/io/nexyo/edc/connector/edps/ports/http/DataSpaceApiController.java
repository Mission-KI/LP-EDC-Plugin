package io.nexyo.edc.connector.edps.ports.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.nexyo.edc.connector.edps.adapters.http.nexyo.info.NexyoHubInfoClient;
import io.nexyo.edc.connector.edps.services.iam.dataspace.DataSpaceService;
import io.nexyo.edc.connector.edps.services.iam.did.DecentralizedIdentityService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.iam.did.web.resolution.WebDidResolver;
import org.eclipse.edc.spi.http.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;

import java.net.URI;
import java.util.*;

import static io.nexyo.edc.connector.edps.NexyoEdpsConstants.DS_INFO_SVC_ID_SUFFIX;

@Consumes({MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_JSON})
@Path("/")
public class DataSpaceApiController {

    private final Monitor monitor;

    private final WebDidResolver webDidResolver;


    private final NexyoHubInfoClient nexyoHubInfoClient;

    private final DataSpaceService dataSpaceService;

    public DataSpaceApiController(Monitor monitor, DecentralizedIdentityService identityService, ObjectMapper mapper, EdcHttpClient httpClient, WebDidResolver webDidResolver, DataSpaceService dataSpaceService) {
        this.monitor = monitor;
        this.monitor.info("##### DataSpaceApiController constructor called");
        this.webDidResolver = webDidResolver;
        this.dataSpaceService = dataSpaceService;

        this.nexyoHubInfoClient = new NexyoHubInfoClient(httpClient, identityService, mapper, monitor);
    }

    @GET
    @Path("dataspaces/{didBase64}")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getDataSpace(@PathParam("didBase64") String didBase64) {
        var did = this.decodeDID(didBase64);

        var didDocumentResult = webDidResolver.resolve(did);
        if (didDocumentResult.failed()) {
            return this.buildErrorResponse(500, didDocumentResult.getFailureDetail());
        }
        var didDocument = didDocumentResult.getContent();

        var potentiallyDataSpaceInfoService = didDocument.getService().stream().filter(service -> service.getId().equals(did + DS_INFO_SVC_ID_SUFFIX)).findFirst();
        if (!potentiallyDataSpaceInfoService.isPresent()) {
            return this.buildErrorResponse(404, "no DataSpaceInfo service found in DID document");
        }
        var dataSpaceInfoServiceEndpoint = potentiallyDataSpaceInfoService.get().getServiceEndpoint();

        var dataSpaceInfoResult = nexyoHubInfoClient.getDataSpaceInfo(dataSpaceInfoServiceEndpoint, did);
        if (dataSpaceInfoResult.failed()) {
            return this.buildErrorResponse(500, dataSpaceInfoResult.getFailureDetail());
        }
        var dataSpaceInfo = dataSpaceInfoResult.getContent();

        var participantConnectorDIDs = new ArrayList<String>();
        for (String participantDID : dataSpaceInfo.getParticipantDIDs()) {
            var participantDIDResult = webDidResolver.resolve(participantDID);
            if (participantDIDResult.failed()) {
                continue;
            }
            var participantDocument = participantDIDResult.getContent();

            var potentiallyConnectorService = participantDocument.getService().stream().filter(service -> service.getId().equals(participantDID + "#ConnectorsEndpoint")).findFirst();
            var isHub = potentiallyConnectorService.isPresent();

            if (isHub) {
                var connectorEndpoint = potentiallyConnectorService.get().getServiceEndpoint();
                monitor.debug("CONNECTOR SERVICE ENDPOINT: " + connectorEndpoint);

                var getConnectorsRes = nexyoHubInfoClient.getHubConnectors(connectorEndpoint, participantDID);
                if (getConnectorsRes.failed()) {
                    monitor.debug("GET CONNECTOR FAILED for : "  + participantDID + " : " + getConnectorsRes.getFailureDetail());
                    continue;
                }
                var hubConnectors = getConnectorsRes.getContent();
                monitor.debug("HUB CONNECTORs of Hub " + participantDID + " : " + hubConnectors.toString());
                participantConnectorDIDs.addAll(Arrays.asList(hubConnectors.getConnectors()));
            } else {
                participantConnectorDIDs.add(participantDID);
            }
        }

        var response = new DataSpaceInfoResponse(dataSpaceInfo.getTitle(), dataSpaceInfo.getDescription(), dataSpaceInfo.isRestricted(), participantConnectorDIDs);

        return Response.ok(response).build();
    }

    @POST
    @Path("dataspaceMemberships")
    @Produces({MediaType.APPLICATION_JSON})
    public Response addDataSpaceMembership(DataSpaceMembershipRequest dataSpaceMembershipRequest) {
        var did = dataSpaceMembershipRequest.dataSpaceDID;
        monitor.debug("##### did to add: " + did);

        URI location;
        try {
            this.dataSpaceService.addMembership(did);
            location = new URI("dataSpaceMemberships/" + encodeDID(did));
        } catch (Exception e) {
            return this.buildErrorResponse(500, e.getMessage());
        }

        return Response.created(location).build();
    }

    @DELETE
    @Path("dataspaceMemberships/{didBase64}")
    public Response removeDataSpaceMembership(@PathParam("didBase64") String didBase64) {
        var did = this.decodeDID(didBase64);

        try {
            this.dataSpaceService.removeMembership(did);
        } catch (Exception e) {
            return this.buildErrorResponse(500, e.getMessage());
        }

        return Response.noContent().build();
    }

    @GET
    @Path("dataspaceMemberships")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getDataSpaceMemberships() {
        List<String> memberships;
        try {
            memberships = this.dataSpaceService.getMemberships();
        } catch (Exception e) {
            return this.buildErrorResponse(500, e.getMessage());
        }

        var jsonResult = new Gson().toJson(memberships);
        return Response.ok(jsonResult).build();
    }

    private Response buildErrorResponse(int statusCode, String errorMessage) {
        if (statusCode <= 0) {
            statusCode = 500;
        }

        var errMessageHelper = new HashMap<String, String>();
        errMessageHelper.put("error", errorMessage);
        var errMessageJson = new Gson().toJson(errMessageHelper);

        return Response.status(statusCode).entity(errMessageJson).build();
    }

    private String decodeDID(String didBase64) {
        monitor.info("##### decodeDID called");
        monitor.info("##### didBase64: " + didBase64);
        var decodedDIDBytes = Base64.getDecoder().decode(didBase64);
        var did = new String(decodedDIDBytes);

        monitor.info("##### decodedDID: " + did);
        return did;
    }

    private String encodeDID(String did) {
        monitor.info("##### encodeDID called");
        monitor.info("##### did: " + did);
        var encodedDIDBytes = Base64.getEncoder().encode(did.getBytes());
        var encodedDID = new String(encodedDIDBytes);

        monitor.info("##### encodedDID: " + encodedDID);
        return encodedDID;
    }

    static class DataSpaceMembershipRequest {
        public String dataSpaceDID;
    }

    static class DataSpaceInfoResponse {
        @JsonProperty("title")
        public String title;

        @JsonProperty("description")
        public String description;

        @JsonProperty("isRestricted")
        public Boolean isRestricted;

        @JsonProperty("participantConnectorDIDs")
        public ArrayList<String> participantConnectorDIDs;

        public DataSpaceInfoResponse(String title, String description, Boolean isRestricted, ArrayList<String> participantConnectorDIDs) {
            this.title = title;
            this.description = description;
            this.isRestricted = isRestricted;
            this.participantConnectorDIDs = participantConnectorDIDs;
        }
    }
}
