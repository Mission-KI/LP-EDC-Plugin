package io.nexyo.edp.extensions.controllers;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;


@Path("/edp/daseen")
public interface DaseenInterface {

    /**
     * Publishes an EDPS asset to the Daseen API.
     *
     * @param edpAssetId The unique identifier of the EDPS result asset to be published.
     * @return A {@link Response} indicating the success or failure of the publication process.
     */
    @POST
    @Path("/{edpAssetId}")
    Response publishEdpsAssetToDaseen(@PathParam("edpAssetId") String edpAssetId);


    @PUT
    @Path("/{daseenResourceId}")
    Response update(@PathParam("daseenResourceId") String daseenResourceId);



    @DELETE
    @Path("/{edpAssetId}")
    Response deleteEdpsAssetFromDaseen(@PathParam("edpAssetId") String edpAssetId);


}
