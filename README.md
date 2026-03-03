# Producer

A Spring Boot service that receives **device updates** (e.g. temperature, status) over **MQTT**, validates **device state transitions**, persists devices in a database, and publishes the current **device-state snapshot** to **Kafka** via an outbox. It also exposes mock and outbox APIs for testing.

## What it does

- **MQTT** – Subscribes to a topic and consumes device update messages.
- **Validation** – New devices must have status `PENDING`; existing devices follow allowed status transitions (e.g. PENDING→ACTIVE, ACTIVE→INACTIVE).
- **Persistence** – Stores devices in an H2 (in-memory) database.
- **Outbox + Kafka** – Writes a full device snapshot to an outbox and publishes it to a Kafka topic so other services can consume device state.

## Run instructions

**Requirements:** Java 21, Maven (or use the included Maven wrapper).

1. **From the project root:**

   ```bash
   ./mvnw spring-boot:run
   ```

   On Windows:

   ```bash
   mvnw.cmd spring-boot:run
   ```

2. The app starts on **port 8080**. Default login for protected endpoints: `admin` / `admin`.

3. **Optional:** For MQTT and Kafka to work, run a broker and Kafka (e.g. locally or via Docker). Defaults in `application.properties`:
   - MQTT: `tcp://localhost:1883`, topic `devices/updates`
   - Kafka: `localhost:9094`, topic `device-state`

**Docker:**

```bash
docker build -t producer .
docker run -p 8080:8080 producer
```

Set `SPRING_KAFKA_BOOTSTRAP_SERVERS` and MQTT URL if your brokers are not on localhost.

**Docker Compose** (MQTT + Kafka + producer):

From the project root, start Mosquitto, Kafka, and the producer together:

```bash
docker compose up -d
```

- **Mosquitto** (MQTT): port 1883  
- **Kafka**: 9092 (internal), 9094 (external)  
- **Producer**: http://localhost:8080  

The producer is built from the Dockerfile and gets `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092` and `MQTT_BROKER_URL=tcp://mosquitto:1883` from the compose file. It starts after Kafka and Mosquitto are healthy.

To rebuild and start:

```bash
docker compose up -d --build
```

To stop:

```bash
docker compose down
```
