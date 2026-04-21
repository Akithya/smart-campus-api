package com.smartcampus.exception;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable e) {
        // If it's already a JAX-RS exception (404, 405 etc), pass it through correctly
        if (e instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) e;
            int status = wae.getResponse().getStatus();
            Map<String, Object> error = new HashMap<>();
            error.put("status", status);
            error.put("error", "Not Found");
            error.put("message", e.getMessage());
            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(error)
                    .build();
        }

        // Only log truly unexpected errors
        LOGGER.log(Level.SEVERE, "Unexpected error caught by GlobalExceptionMapper", e);

        Map<String, Object> error = new HashMap<>();
        error.put("status", 500);
        error.put("error", "Internal Server Error");
        error.put("message", "An unexpected error occurred. Please contact the system administrator.");

        return Response.status(500)
                .type(MediaType.APPLICATION_JSON)
                .entity(error)
                .build();
    }
}