package io.nexyo.edp.extensions.controllers;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;


@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/")
public class EdpController {

    @Inject
    private Monitor monitor;

    @GET
    @Path("edp")
    public Response createEdp() {
        monitor.info("Creating EDP");

        return Response.status(Response.Status.OK)
                .entity("EDP created successfully")
                .build();
    }

}
