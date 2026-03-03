# Producer

A Spring Boot service that receives **device updates** (e.g. temperature, status) over **MQTT**, validates **device state transitions**, persists devices in a database, and publishes the current **device-state snapshot** to **Kafka** via an outbox. It also exposes mock and outbox APIs for testing.

## What it does

- **MQTT** – Subscribes to a topic and consumes device update messages.
- **Validation** – New devices must have status `PENDING`; existing devices follow allowed status transitions (e.g. PENDING→ACTIVE, ACTIVE→INACTIVE).
- **Persistence** – Stores devices in an H2 (in-memory) database.
- **Outbox + Kafka** – Writes a full device snapshot to an outbox and publishes it to a Kafka topic so other services can consume device state.

## Run instructions

**Recommended: Docker Compose** (starts MQTT, Kafka, and the app in one go)

From the project root:

```bash
docker compose up -d
```

- App: **http://localhost:8080** (login: `admin` / `admin`)
- Rebuild and start: `docker compose up -d --build`
- Stop: `docker compose down`

---

**Optional: run locally** (Java 21, Maven wrapper)

```bash
./mvnw spring-boot:run
```

On Windows: `mvnw.cmd spring-boot:run`. The app runs on port 8080. For MQTT and Kafka you need brokers running (e.g. start only infra with `docker compose up -d mosquitto kafka` and point the app at `localhost:1883` / `localhost:9094`).
