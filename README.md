
## Smart Campus REST API

A RESTful API built with **JAX-RS (Jersey)** and an embedded **Grizzly** HTTP server for managing campus rooms and IoT sensors.

---

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 11 |
| Framework | JAX-RS (Jakarta RESTful Web Services) |
| Implementation | Jersey 2.39.1 |
| Embedded Server | Grizzly HTTP Server |
| JSON Support | Jackson (via jersey-media-json-jackson) |
| Build Tool | Maven 3.6+ |
| Data Storage | ConcurrentHashMap (in-memory, no database) |
| IDE Used | Apache NetBeans IDE 28 |
| API Testing Tool | Postman |

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
- Apache NetBeans IDE (recommended) or any IDE with Maven support
- Postman (for API testing)

### Step 1 — Build
```bash
mvn clean package
```
This produces `target/smart-campus-api-1.0-SNAPSHOT.jar` (a fat JAR with all dependencies bundled).

### Step 2 — Run
```bash
java -jar target/smart-campus-api-1.0-SNAPSHOT.jar
```
The server starts at: **http://localhost:8080/api/v1/**

Press `ENTER` in the terminal to stop the server.

### Run in NetBeans (Recommended)
1. Open NetBeans IDE
2. File → Open Project → select the `smartcampus` folder
3. Right-click project → **Clean and Build** → wait for BUILD SUCCESS
4. Right-click project → **Run**
5. Select `com.smartcampus.Main` as the main class when prompted
6. Server starts on port 8080 — output window shows:
   ```
   INFO: Smart Campus API started at http://localhost:8080/api/v1/
   INFO: Press ENTER to stop the server...
   ```

### Step 3 — Test with Postman
1. Download and install Postman from https://www.postman.com/downloads/
2. Open Postman
3. Create a new request tab
4. Set the HTTP method and URL as shown in the test cases below
5. For POST requests: click Body → raw → select JSON from dropdown
6. Click Send

---

## API Endpoints Summary

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/v1/discovery | API discovery / metadata |
| GET | /api/v1/rooms | List all rooms |
| POST | /api/v1/rooms | Create a room (201 Created) |
| GET | /api/v1/rooms/{id} | Get one room by ID |
| DELETE | /api/v1/rooms/{id} | Delete room (409 if has sensors) |
| GET | /api/v1/sensors | List all sensors (optional ?type= filter) |
| POST | /api/v1/sensors | Register sensor (422 if roomId invalid) |
| GET | /api/v1/sensors/{id} | Get one sensor by ID |
| GET | /api/v1/sensors/{id}/readings | Get reading history |
| POST | /api/v1/sensors/{id}/readings | Add reading (403 if MAINTENANCE) |
| GET | /api/v1/rooms/../../etc | Trigger global error (500 with no stack trace) |

---

## Sample curl Commands (15 Test Cases)

> All tests were verified using **Postman** on **Windows 11**.
> Server: **Grizzly HTTP Server** running on **http://localhost:8080**

### Test 1 — Discovery endpoint
```bash
curl -X GET http://localhost:8080/api/v1/discovery
```
Expected: **200 OK** — returns API metadata with version, contact and resource links

### Test 2 — Get all rooms
```bash
curl -X GET http://localhost:8080/api/v1/rooms
```
Expected: **200 OK** — returns list of all rooms

### Test 3 — Create a new room
```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"HALL-01","name":"Main Hall","capacity":100}'
```
Expected: **201 Created** — returns created room object with Location header

### Test 4 — Get a specific room by ID
```bash
curl -X GET http://localhost:8080/api/v1/rooms/LIB-301
```
Expected: **200 OK** — returns room details with linked sensor IDs

### Test 5 — Delete room WITH sensors (business logic check)
```bash
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301
```
Expected: **409 Conflict** — room has sensors assigned, deletion blocked

### Test 6 — Delete room WITHOUT sensors (success)
```bash
curl -X DELETE http://localhost:8080/api/v1/rooms/HALL-01
```
Expected: **204 No Content** — room deleted successfully

### Test 7 — Get all sensors
```bash
curl -X GET http://localhost:8080/api/v1/sensors
```
Expected: **200 OK** — returns list of all sensors with status and currentValue

### Test 8 — Filter sensors by type
```bash
curl -X GET "http://localhost:8080/api/v1/sensors?type=Temperature"
```
Expected: **200 OK** — returns only Temperature sensors

### Test 9 — Register sensor with invalid roomId
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"BAD-001","type":"CO2","status":"ACTIVE","currentValue":0,"roomId":"FAKE-999"}'
```
Expected: **422 Unprocessable Entity** — roomId does not exist in the system

### Test 10 — Register a valid sensor
```bash
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-099","type":"Temperature","status":"ACTIVE","currentValue":20.0,"roomId":"LAB-101"}'
```
Expected: **201 Created** — sensor registered and linked to room LAB-101

### Test 11 — Get a specific sensor by ID
```bash
curl -X GET http://localhost:8080/api/v1/sensors/TEMP-001
```
Expected: **200 OK** — returns sensor details including currentValue

### Test 12 — Post reading to MAINTENANCE sensor
```bash
curl -X POST http://localhost:8080/api/v1/sensors/OCC-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":5.0}'
```
Expected: **403 Forbidden** — sensor OCC-001 is under MAINTENANCE

### Test 13 — Post a valid reading
```bash
curl -X POST http://localhost:8080/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":99.9}'
```
Expected: **201 Created** — reading stored with UUID and timestamp, parent sensor currentValue updated to 99.9

### Test 14 — Trigger 500 Global Safety Net (prove no stack trace)
```bash
curl -X GET http://localhost:8080/api/v1/rooms/../../etc
```
Expected: **500 Internal Server Error** — clean JSON response with NO Java stack trace exposed

### Test 15 — Get reading history
```bash
curl -X GET http://localhost:8080/api/v1/sensors/TEMP-001/readings
```
Expected: **200 OK** — returns full reading history for TEMP-001

---

## Postman Testing Table

> Tested on: **Postman v11** | Server: **Grizzly embedded HTTP server** | Platform: **Windows 11**

| # | Method | URL | Body | Expected Result |
|---|--------|-----|------|-----------------|
| 1 | GET | /api/v1/discovery | — | 200 OK |
| 2 | GET | /api/v1/rooms | — | 200 OK |
| 3 | POST | /api/v1/rooms | `{"id":"HALL-01","name":"Main Hall","capacity":100}` | 201 Created |
| 4 | GET | /api/v1/rooms/LIB-301 | — | 200 OK |
| 5 | DELETE | /api/v1/rooms/LIB-301 | — | 409 Conflict |
| 6 | DELETE | /api/v1/rooms/HALL-01 | — | 204 No Content |
| 7 | GET | /api/v1/sensors | — | 200 OK |
| 8 | GET | /api/v1/sensors?type=Temperature | — | 200 OK |
| 9 | POST | /api/v1/sensors | `{"id":"BAD-001","roomId":"FAKE-999",...}` | 422 Unprocessable |
| 10 | POST | /api/v1/sensors | `{"id":"TEMP-099","roomId":"LAB-101",...}` | 201 Created |
| 11 | GET | /api/v1/sensors/TEMP-001 | — | 200 OK |
| 12 | POST | /api/v1/sensors/OCC-001/readings | `{"value":5.0}` | 403 Forbidden |
| 13 | POST | /api/v1/sensors/TEMP-001/readings | `{"value":99.9}` | 201 Created |
| 14 | GET | /api/v1/rooms/../../etc | — | 500 Internal Server Error (no stack trace) |
| 15 | GET | /api/v1/sensors/TEMP-001/readings | — | 200 OK |

