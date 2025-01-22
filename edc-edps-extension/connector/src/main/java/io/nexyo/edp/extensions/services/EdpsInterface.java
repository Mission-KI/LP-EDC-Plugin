package io.nexyo.edp.extensions.services;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/edp")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface EdpsInterface {

    @GET
    @Path("/{assetId}/jobs")
    Response getEdpsJob(@PathParam("assetId") String assetId);

    /** creates job on EDP and sends file to EPD */
    @POST
    @Path("/{assetId}/job")
    Response createEdpsJob(@PathParam("assetId") String assetId);

    @GET
    @Path("/{assetId}/job/{jobId}/status")
    Response getEdpsJobStatus(@PathParam("assetId") String assetId,
                                 @PathParam("jobId") String jobId);

    /** Gets EDP Result
     * gets result from edp,
     * stores result file (where?)
     * and creates new asset from result */
    @POST
    @Path("/{assetId}/job/{jobId}/result")
    Response createEdpsJobResultAsset(@PathParam("assetId") String assetId,
                                      @PathParam("jobId") String jobId,
                                      String requestBody);
}
