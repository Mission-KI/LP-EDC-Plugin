package io.nexyo.edp.extensions.controllers;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;


public class EdpController {

    @GET
    @Path("/create-edp")
    public Response createEdp() {

        return Response.status(Response.Status.OK)
                .entity("EDP created successfully")
                .build();
    }

}
