package com.smartcampus.resource;

import com.smartcampus.exception.SensorUnavailableException;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;
import com.smartcampus.service.DataStore;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * Sub-resource class handling all /api/v1/sensors/{sensorId}/readings endpoints.
 * Instantiated by SensorResource via the sub-resource locator pattern.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorReadingResource {

    private final String sensorId;
    private final DataStore store = DataStore.getInstance();

    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    /** GET /api/v1/sensors/{sensorId}/readings - get all readings for this sensor */
    @GET
    public Response getReadings() {
        List<SensorReading> history = store.getReadings()
                .getOrDefault(sensorId, new ArrayList<>());
        return Response.ok(history).build();
    }

    /**
     * POST /api/v1/sensors/{sensorId}/readings - add a new reading.
     * Business rules:
     *  - Sensor must NOT be in MAINTENANCE status (throws 403 SensorUnavailableException)
     *  - On success, updates the parent Sensor's currentValue (side effect)
     */
    @POST
    public Response addReading(SensorReading reading) {
        if (reading == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorBody("Reading body is required."))
                    .build();
        }

        Sensor sensor = store.getSensors().get(sensorId);

        // Check sensor status - MAINTENANCE sensors cannot accept new readings
        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus())) {
            throw new SensorUnavailableException(
                "Sensor '" + sensorId + "' is currently under MAINTENANCE " +
                "and cannot accept new readings."
            );
        }

        // Auto-generate ID and timestamp if not provided
        SensorReading newReading = new SensorReading(reading.getValue());

        // Store the reading
        store.getReadings()
             .computeIfAbsent(sensorId, k -> new ArrayList<>())
             .add(newReading);

        // SIDE EFFECT: update the parent sensor's currentValue
        sensor.setCurrentValue(newReading.getValue());

        return Response.status(Response.Status.CREATED).entity(newReading).build();
    }

    private Map<String, String> errorBody(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        return error;
    }
}
