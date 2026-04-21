# Smart Campus Sensor & Room Management API

A RESTful API built with **JAX-RS (Jersey)** and an embedded **Grizzly** HTTP server for managing campus rooms and IoT sensors.

---

## Project Structure

```
src/main/java/com/smartcampus/
├── Main.java                          # Entry point — starts Grizzly server on port 8080
├── SmartCampusApplication.java        # @ApplicationPath("/api/v1")
├── model/
│   ├── Room.java
│   ├── Sensor.java
│   └── SensorReading.java
├── service/
│   └── DataStore.java                 # Singleton in-memory store (ConcurrentHashMap)
├── resource/
│   ├── DiscoveryResource.java         # GET /api/v1/discovery
│   ├── RoomResource.java              # /api/v1/rooms
│   ├── SensorResource.java            # /api/v1/sensors
│   └── SensorReadingResource.java     # /api/v1/sensors/{id}/readings (sub-resource)
├── exception/
│   ├── RoomNotEmptyException.java             + Mapper → 409 Conflict
│   ├── LinkedResourceNotFoundException.java   + Mapper → 422 Unprocessable Entity
│   ├── SensorUnavailableException.java        + Mapper → 403 Forbidden
│   └── GlobalExceptionMapper.java             → 500 catch-all
└── filter/
    └── ApiLoggingFilter.java          # Logs every request and response
```

---

## How to Build and Run

### Prerequisites
- Java 11 or higher
- Maven 3.6+
- NetBeans IDE (recommended) or any IDE with Maven support

### Build
```bash
mvn clean package
```
This produces `target/smart-campus-api-1.0-SNAPSHOT.jar` (a fat JAR with all dependencies).

### Run
```bash
java -jar target/smart-campus-api-1.0-SNAPSHOT.jar
```
The server starts at: **http://localhost:8080/api/v1/**

Press `ENTER` in the terminal to stop the server.

### Run in NetBeans
1. Open project in NetBeans (File → Open Project)
2. Right-click project → Clean and Build
3. Right-click project → Run
4. Select `com.smartcampus.Main` as the main class

---

## API Endpoints Summary

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/v1/discovery | API discovery / metadata |
| GET | /api/v1/rooms | List all rooms |
| POST | /api/v1/rooms | Create a room |
| GET | /api/v1/rooms/{id} | Get one room |
| DELETE | /api/v1/rooms/{id} | Delete a room (409 if has sensors) |
| GET | /api/v1/sensors | List all sensors (optional ?type= filter) |
| POST | /api/v1/sensors | Register a sensor (422 if roomId invalid) |
| GET | /api/v1/sensors/{id} | Get one sensor |
| GET | /api/v1/sensors/{id}/readings | Get reading history |
| POST | /api/v1/sensors/{id}/readings | Add a new reading (403 if MAINTENANCE) |

---

## Sample curl Commands

### 1. Discovery endpoint
```bash
curl -X GET http://localhost:8080/api/v1/discovery
```

### 2. Get all rooms
```bash
curl -X GET http://localhost:8080/api/v1/rooms
```

### 3. Create a new room
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"LAB-205","name":"AI Research Lab","capacity":20}'
```

### 4. Get a specific room
```bash
curl -X GET http://localhost:8080/api/v1/rooms/LIB-301
```

### 5. Delete a room with sensors (expect 409 Conflict)
```bash
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301
```

### 6. Get all sensors
```bash
curl -X GET http://localhost:8080/api/v1/sensors
```

### 7. Get sensors filtered by type
```bash
curl -X GET "http://localhost:8080/api/v1/sensors?type=Temperature"
```

### 8. Register a sensor with invalid roomId (expect 422)
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"BAD-001","type":"CO2","status":"ACTIVE","currentValue":0,"roomId":"FAKE-999"}'
```

### 9. Register a valid sensor
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-002","type":"Temperature","status":"ACTIVE","currentValue":22.0,"roomId":"LAB-205"}'
```

### 10. Post a reading to MAINTENANCE sensor (expect 403)
```bash
curl -X POST http://localhost:8080/api/v1/sensors/OCC-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":5}'
```

### 11. Post a valid reading (updates parent sensor currentValue)
```bash
curl -X POST http://localhost:8080/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":99.9}'
```

### 12. Get reading history for a sensor
```bash
curl -X GET http://localhost:8080/api/v1/sensors/TEMP-001/readings
```

---

## Postman Testing Guide

Import and test the following requests in Postman:

1. **GET** `http://localhost:8080/api/v1/discovery` — API metadata
2. **GET** `http://localhost:8080/api/v1/rooms` — List all rooms
3. **POST** `http://localhost:8080/api/v1/rooms` with JSON body — Create room (201)
4. **DELETE** `http://localhost:8080/api/v1/rooms/LIB-301` — 409 Conflict (has sensors)
5. **POST** `http://localhost:8080/api/v1/sensors` with fake roomId — 422 Unprocessable
6. **POST** `http://localhost:8080/api/v1/sensors/OCC-001/readings` — 403 Forbidden (MAINTENANCE)
7. **POST** `http://localhost:8080/api/v1/sensors/TEMP-001/readings` with value — 201 Created
8. **GET** `http://localhost:8080/api/v1/sensors?type=Temperature` — Filtered list
9. **GET** `http://localhost:8080/api/v1/sensors/TEMP-001` — Verify currentValue updated to 99.9
10. **GET** `http://localhost:8080/api/v1/sensors/TEMP-001/readings` — Reading history

---

## Report: Answers to Coursework Questions

### Part 1.1 — JAX-RS Resource Lifecycle

By default, JAX-RS creates a new instance of every resource class for each incoming HTTP request (request-scoped). This is the per-request lifecycle defined in the JAX-RS specification.

This has a direct impact on in-memory data management. Because each request gets a fresh resource object, you cannot store your data inside instance fields of the resource class — it would be lost after the request ends. The solution used in this project is a singleton DataStore class accessed via DataStore.getInstance(). The data lives in the singleton (not in the resource), so it persists across all requests.

Furthermore, since multiple requests can arrive simultaneously and each gets its own resource instance accessing the same singleton, thread-safety is critical. This project uses ConcurrentHashMap instead of a plain HashMap. ConcurrentHashMap allows concurrent reads and uses segment-level locking for writes, preventing race conditions and data corruption without requiring manual synchronized blocks.

---

### Part 1.2 — HATEOAS

HATEOAS (Hypermedia As The Engine Of Application State) means that API responses include hyperlinks to related actions and resources, not just data. For example, the discovery endpoint returns links to rooms and sensors collections.

This benefits client developers because the client does not need to hard-code URLs or consult external documentation to navigate the API. The API is self-describing — the client simply follows the links provided in the response. This reduces coupling between the client and server, meaning the server can change URLs without breaking clients that navigate via links rather than hard-coded paths.

---

### Part 2.1 — Returning IDs vs Full Objects

Returning only IDs in a list minimises the response payload, which reduces network bandwidth. However, it forces the client to make a separate GET request for each ID to retrieve the full details, resulting in N+1 requests and increased latency.

Returning full objects in the list provides all data in one request, reducing round trips. The trade-off is a larger payload size per response. For collections with many items, this can be significant. The best practice is to return full objects for small collections and consider pagination or summary objects for large ones.

---

### Part 2.2 — Idempotency of DELETE

The DELETE operation in this implementation is idempotent in terms of server state. The first DELETE call on an existing room removes it and returns 204 No Content. A second identical DELETE call returns 404 Not Found because the room no longer exists. The server state is the same after both calls (the room is gone), which satisfies the idempotency requirement of the HTTP specification. What matters is the resource state, not the status code.

---

### Part 3.1 — @Consumes and Content-Type Mismatch

When a resource method is annotated with @Consumes(MediaType.APPLICATION_JSON), JAX-RS checks the Content-Type header of the incoming request. If a client sends text/plain or application/xml instead of application/json, JAX-RS cannot find a matching resource method and returns HTTP 415 Unsupported Media Type automatically. The request never reaches the resource method body. This is handled entirely by the JAX-RS runtime.

---

### Part 3.2 — @QueryParam vs Path Parameter for Filtering

Using a query parameter (GET /sensors?type=CO2) is superior for filtering collections. Query parameters are optional by design — their absence simply returns the full unfiltered list. They clearly communicate intent: the path identifies the resource, while the query string refines how that collection is returned.

Using a path parameter (/sensors/type/CO2) implies that type/CO2 is a distinct resource, which is semantically incorrect for filtering. It would also require a separate route definition for every possible filter combination, making the API harder to maintain.

---

### Part 4.1 — Sub-Resource Locator Pattern

The sub-resource locator pattern delegates handling of a nested path to a separate class. In this project, SensorResource has a method annotated with @Path("/{sensorId}/readings") that returns an instance of SensorReadingResource rather than handling the request itself.

The architectural benefit is separation of concerns. SensorResource is responsible for sensor collection management; SensorReadingResource is responsible for reading history. As the API grows, this prevents any single class from becoming unmanageably large. Each sub-resource class can be tested, modified, and extended independently.

---

### Part 5.1 — HTTP 422 vs HTTP 404

When a client sends a valid JSON payload that contains a reference to a resource that does not exist (e.g., a roomId that is not in the system), a 404 Not Found is semantically misleading. 404 implies the requested URL/endpoint was not found, but the endpoint /api/v1/sensors was found correctly — the problem is with the content of the request body.

HTTP 422 Unprocessable Entity is more accurate because it signals that the server understood the request format and content type, but could not process the instructions due to a semantic error within the payload. It tells the client: your JSON arrived correctly, but one of the values inside it is invalid.

---

### Part 5.2 — Security Risks of Exposing Stack Traces

Exposing Java stack traces to external API consumers is a significant security risk:

1. Internal path disclosure — Stack traces reveal full file system paths of source files, giving attackers a map of the application structure.
2. Library and version disclosure — Stack traces include names and versions of third-party libraries. Attackers can look up known CVEs for those exact versions.
3. Business logic disclosure — The sequence of method calls reveals how the application processes data internally.
4. Database schema hints — Failed queries may reveal table names, column names, or query structures.

The GlobalExceptionMapper in this project logs the full trace server-side while returning only a generic 500 Internal Server Error message to the client.

---

### Part 5.3 (Filter) — Why Filters for Cross-Cutting Concerns

Inserting Logger.info() calls manually into every resource method violates the DRY (Don't Repeat Yourself) principle. If a new endpoint is added and the developer forgets to add logging, that endpoint is silently unobserved.

A JAX-RS ContainerRequestFilter / ContainerResponseFilter is applied automatically to every single request and response by the runtime, regardless of which resource method handles it. This guarantees consistent coverage with zero risk of omission.
