package io.nexyo.edp.extensions.services;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/daseen")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DaseenInterface {

    /** Publish edp asset (is an EDP result) */
    @POST
    @Path("/{edpAssetId}")
    Response publishEdpAssetToDaseen(@PathParam("edpAssetId") String edpAssetId);
}
