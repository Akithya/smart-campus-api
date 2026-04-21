package com.smartcampus.resource;

import com.smartcampus.exception.LinkedResourceNotFoundException;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.Room;
import com.smartcampus.service.DataStore;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Path("/sensors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorResource {

    private final DataStore store = DataStore.getInstance();

    /**
     * GET /api/v1/sensors          - list all sensors
     * GET /api/v1/sensors?type=CO2 - filter by type using @QueryParam
     */
    @GET
    public Response getSensors(@QueryParam("type") String type) {
        Collection<Sensor> all = store.getSensors().values();

        if (type != null && !type.isBlank()) {
            List<Sensor> filtered = all.stream()
                    .filter(s -> s.getType().equalsIgnoreCase(type))
                    .collect(Collectors.toList());
            return Response.ok(filtered).build();
        }

        return Response.ok(new ArrayList<>(all)).build();
    }

    /**
     * POST /api/v1/sensors - register a new sensor.
     * Validates that the given roomId actually exists.
     * Throws LinkedResourceNotFoundException (-> HTTP 422) if roomId is invalid.
     */
    @POST
    public Response registerSensor(Sensor sensor) {
        if (sensor == null || sensor.getId() == null || sensor.getId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorBody("Sensor ID is required."))
                    .build();
        }
        if (store.getSensors().containsKey(sensor.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(errorBody("A sensor with ID '" + sensor.getId() + "' already exists."))
                    .build();
        }

        // Validate that the roomId actually exists in the system
        if (sensor.getRoomId() == null || sensor.getRoomId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorBody("A roomId is required when registering a sensor."))
                    .build();
        }

        Room room = store.getRooms().get(sensor.getRoomId());
        if (room == null) {
            // Room doesn't exist - throw 422 Unprocessable Entity
            throw new LinkedResourceNotFoundException(
                "The roomId '" + sensor.getRoomId() + "' does not exist. " +
                "Please create the room first before assigning sensors to it."
            );
        }

        // Default status if not provided
        if (sensor.getStatus() == null || sensor.getStatus().isBlank()) {
            sensor.setStatus("ACTIVE");
        }

        store.getSensors().put(sensor.getId(), sensor);

        // Link the sensor to its room
        room.getSensorIds().add(sensor.getId());

        // Initialise an empty readings list for this sensor
        store.getReadings().put(sensor.getId(), new ArrayList<>());

        return Response
                .created(URI.create("/api/v1/sensors/" + sensor.getId()))
                .entity(sensor)
                .build();
    }

    /** GET /api/v1/sensors/{sensorId} - get one sensor */
    @GET
    @Path("/{sensorId}")
    public Response getSensor(@PathParam("sensorId") String sensorId) {
        Sensor sensor = store.getSensors().get(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(errorBody("Sensor '" + sensorId + "' not found."))
                    .build();
        }
        return Response.ok(sensor).build();
    }

    /**
     * Sub-Resource Locator for readings.
     * GET/POST /api/v1/sensors/{sensorId}/readings
     * Delegates to SensorReadingResource — this is the Sub-Resource Locator Pattern.
     */
    @Path("/{sensorId}/readings")
    public SensorReadingResource getReadingResource(@PathParam("sensorId") String sensorId) {
        // Validate the sensor exists before delegating
        Sensor sensor = store.getSensors().get(sensorId);
        if (sensor == null) {
            throw new NotFoundException("Sensor '" + sensorId + "' not found.");
        }
        return new SensorReadingResource(sensorId);
    }

    private Map<String, String> errorBody(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        return error;
    }
}
