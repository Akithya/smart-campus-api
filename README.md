# Smart Campus Sensor & Room Management API

A RESTful API built with **JAX-RS (Jersey)** and an embedded **Grizzly** HTTP server for managing campus rooms and IoT sensors.

---

## Project Structure

```
src/main/java/com/smartcampus/
├── Main.java                          # Entry point — starts Grizzly server
├── SmartCampusApplication.java        # @ApplicationPath("/api/v1")
├── model/
│   ├── Room.java
│   ├── Sensor.java
│   └── SensorReading.java
├── service/
│   └── DataStore.java                 # Singleton in-memory store (ConcurrentHashMap)
├── resource/
│   ├── DiscoveryResource.java         # GET /api/v1
│   ├── RoomResource.java              # /api/v1/rooms
│   ├── SensorResource.java            # /api/v1/sensors
│   └── SensorReadingResource.java     # /api/v1/sensors/{id}/readings (sub-resource)
├── exception/
│   ├── RoomNotEmptyException.java             + Mapper → 409
│   ├── LinkedResourceNotFoundException.java   + Mapper → 422
│   ├── SensorUnavailableException.java        + Mapper → 403
│   └── GlobalExceptionMapper.java             → 500 catch-all
└── filter/
    └── ApiLoggingFilter.java          # Logs every request and response
```

---

## How to Build and Run

### Prerequisites
- Java 11 or higher
- Maven 3.6+

### Build
```bash
mvn clean package
```
This produces `target/smart-campus-api-1.0-SNAPSHOT.jar` (a fat JAR with all dependencies).

### Run
```bash
java -jar target/smart-campus-api-1.0-SNAPSHOT.jar
```
The server starts at: **http://localhost:8080/api/v1**

Press `ENTER` in the terminal to stop the server.

---

## API Endpoints Summary

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/v1 | Discovery / metadata |
| GET | /api/v1/rooms | List all rooms |
| POST | /api/v1/rooms | Create a room |
| GET | /api/v1/rooms/{id} | Get one room |
| DELETE | /api/v1/rooms/{id} | Delete a room (403 if has sensors) |
| GET | /api/v1/sensors | List all sensors (optional ?type= filter) |
| POST | /api/v1/sensors | Register a sensor |
| GET | /api/v1/sensors/{id} | Get one sensor |
| GET | /api/v1/sensors/{id}/readings | Get reading history |
| POST | /api/v1/sensors/{id}/readings | Add a new reading |

---

## Sample curl Commands

### 1. Discovery endpoint
```bash
curl -X GET http://localhost:8080/api/v1
```

### 2. Create a new room
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"LAB-205","name":"AI Research Lab","capacity":20}'
```

### 3. Get all rooms
```bash
curl -X GET http://localhost:8080/api/v1/rooms
```

### 4. Register a sensor linked to a room
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-002","type":"Temperature","status":"ACTIVE","currentValue":22.0,"roomId":"LAB-205"}'
```

### 5. Get sensors filtered by type
```bash
curl -X GET "http://localhost:8080/api/v1/sensors?type=Temperature"
```

### 6. Post a new reading
```bash
curl -X POST http://localhost:8080/api/v1/sensors/TEMP-002/readings \
  -H "Content-Type: application/json" \
  -d '{"value":23.5}'
```

### 7. Get reading history for a sensor
```bash
curl -X GET http://localhost:8080/api/v1/sensors/TEMP-001/readings
```

### 8. Try to delete a room with sensors (expect 409 Conflict)
```bash
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301
```

### 9. Try to register a sensor with invalid roomId (expect 422)
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"BAD-001","type":"CO2","status":"ACTIVE","currentValue":0,"roomId":"FAKE-999"}'
```

### 10. Try to post a reading to a MAINTENANCE sensor (expect 403)
```bash
curl -X POST http://localhost:8080/api/v1/sensors/OCC-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":5}'
```

---

## Report: Answers to Coursework Questions

---

### Part 1.1 — JAX-RS Resource Lifecycle

By default, JAX-RS creates a **new instance of every resource class for each incoming HTTP request** (request-scoped). This is the per-request lifecycle defined in the JAX-RS specification.

This has a direct impact on in-memory data management. Because each request gets a fresh resource object, you cannot store your data inside instance fields of the resource class — it would be lost after the request ends. The solution used in this project is a **singleton `DataStore` class** accessed via `DataStore.getInstance()`. The data lives in the singleton (not in the resource), so it persists across all requests.

Furthermore, since multiple requests can arrive simultaneously and each gets its own resource instance accessing the same singleton, thread-safety is critical. This project uses `ConcurrentHashMap` instead of a plain `HashMap`. `ConcurrentHashMap` allows concurrent reads and uses segment-level locking for writes, preventing race conditions and data corruption without requiring manual `synchronized` blocks.

---

### Part 1.2 — HATEOAS

HATEOAS (Hypermedia As The Engine Of Application State) means that API responses include hyperlinks to related actions and resources, not just data. For example, a response for a room might include links to its sensors, or a POST response might include a link to the newly created resource.

This benefits client developers because the client does not need to hard-code URLs or consult external documentation to navigate the API. The API is self-describing — the client simply follows the links provided in the response. This reduces coupling between the client and server, meaning the server can change URLs without breaking clients that navigate via links rather than hard-coded paths.

---

### Part 2.1 — Returning IDs vs Full Objects

Returning only IDs in a list (e.g., `["LIB-301", "LAB-101"]`) minimises the response payload, which reduces network bandwidth. However, it forces the client to make a separate GET request for each ID to retrieve the full details, resulting in N+1 requests and increased latency.

Returning full objects in the list provides all data in one request, reducing round trips. The trade-off is a larger payload size per response. For collections with many items, this can be significant. The best practice is to return full objects for small collections and consider pagination or summary objects (a subset of fields) for large ones.

---

### Part 2.2 — Idempotency of DELETE

The DELETE operation in this implementation is **partially idempotent but not fully**. The first DELETE call on an existing room removes it and returns `204 No Content`. A second identical DELETE call returns `404 Not Found` because the room no longer exists. The server state is the same after both calls (the room is gone), which satisfies the idempotency requirement of the HTTP specification. However the response code differs, which some strict interpretations consider non-idempotent. This is the standard real-world behaviour for DELETE — what matters is the resource state, not the status code.

---

### Part 3.1 — @Consumes and Content-Type Mismatch

When a resource method is annotated with `@Consumes(MediaType.APPLICATION_JSON)`, JAX-RS checks the `Content-Type` header of the incoming request. If a client sends `text/plain` or `application/xml` instead of `application/json`, JAX-RS cannot find a matching resource method and returns **HTTP 415 Unsupported Media Type** automatically. The request never reaches the resource method body. This is handled entirely by the JAX-RS runtime — no manual checking is needed inside the method.

---

### Part 3.2 — @QueryParam vs Path Parameter for Filtering

Using a query parameter (`GET /sensors?type=CO2`) is superior for filtering collections for several reasons. Query parameters are **optional by design** — their absence simply returns the full unfiltered list, making the endpoint flexible with no change to the URL structure. They also clearly communicate intent: the path identifies the resource (the sensors collection), while the query string refines how that collection is returned.

Using a path parameter (`/sensors/type/CO2`) implies that `type/CO2` is a distinct resource or sub-resource, which is semantically incorrect for filtering. It would also require a separate route definition for every possible filter combination, making the API harder to maintain and extend.

---

### Part 4.1 — Sub-Resource Locator Pattern

The sub-resource locator pattern delegates handling of a nested path to a separate class. In this project, `SensorResource` has a method annotated with `@Path("/{sensorId}/readings")` that returns an instance of `SensorReadingResource` rather than handling the request itself.

The architectural benefit is **separation of concerns**. `SensorResource` is responsible for sensor collection management; `SensorReadingResource` is responsible for reading history. As the API grows, this prevents any single class from becoming unmanageably large. Each sub-resource class can be tested, modified, and extended independently. In a large production API with dozens of nested resources, placing everything in one controller class would make the code extremely difficult to read and maintain.

---

### Part 5.1 — HTTP 422 vs HTTP 404

When a client sends a valid JSON payload that contains a reference to a resource that does not exist (e.g., a `roomId` that is not in the system), a `404 Not Found` is semantically misleading. 404 implies the requested URL/endpoint was not found, but in this case the endpoint `/api/v1/sensors` was found perfectly — the problem is with the content of the request body.

HTTP `422 Unprocessable Entity` is more accurate because it signals that the server understood the request format and content type, but could not process the instructions due to a semantic error within the payload. It tells the client: "your JSON arrived correctly, but one of the values inside it is invalid." This gives clearer, more actionable feedback to API consumers.

---

### Part 5.2 — Security Risks of Exposing Stack Traces

Exposing Java stack traces to external API consumers is a significant security risk for several reasons:

1. **Internal path disclosure** — Stack traces reveal the full file system paths of source files (e.g., `/home/user/project/src/com/smartcampus/...`), giving attackers a map of the application's structure.
2. **Library and version disclosure** — Stack traces include the names and versions of all third-party libraries in use (e.g., `jersey-server-2.39.1`). An attacker can then look up known CVEs (Common Vulnerabilities and Exposures) for those exact versions and craft targeted exploits.
3. **Business logic disclosure** — The sequence of method calls in a trace reveals how the application processes data internally, exposing logic flows that could be abused.
4. **Database schema hints** — If a database query fails and the exception propagates, the trace may reveal table names, column names, or query structures.

The `GlobalExceptionMapper` in this project logs the full trace **server-side** (for developer debugging) while returning only a generic `500 Internal Server Error` message to the client, eliminating all of the above risks.

---

### Part 5 (Filter) — Why Filters for Cross-Cutting Concerns

Inserting `Logger.info()` calls manually into every resource method violates the **DRY (Don't Repeat Yourself)** principle. If a new endpoint is added and the developer forgets to add logging, that endpoint is silently unobserved. It also clutters business logic with infrastructure code.

A JAX-RS `ContainerRequestFilter` / `ContainerResponseFilter` is applied automatically to **every single request and response** by the runtime, regardless of which resource method handles it. This guarantees consistent coverage with zero risk of omission. It also means logging behaviour can be changed in one place without touching any resource class.
