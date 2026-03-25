# NEM12 Meter Reading Parser

A Spring Boot service that parses NEM12 meter data files and stores interval readings in PostgreSQL. It's built to handle large files (multi-GB) by streaming the file line-by-line and writing to the database in parallel batches.

You give it a NEM12 file, and it:

- Upserts readings into the `meter_readings` table
- Generates a downloadable `.sql` file with INSERT statements for every reading

Processing happens in the background — you upload, get a job ID, and poll for progress.

## Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL 14+ (or Docker for TimescaleDB)

### Quick start

```bash
docker-compose up -d              # start the database
mvn clean package                 # build
mvn spring-boot:run \
  -Dspring-boot.run.profiles=local  # run on port 8080
```

For large files, bump the heap:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-Xmx4g"
```

## How it works

### High-level flow

```
                         POST /upload
                              │
                              ▼
                    ┌─────────────────┐
                    │ FileUploadService│  Validates file, streams to disk,
                    │                  │  creates job, kicks off async processing
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │Nem12FileProcessor│  Parses file line-by-line
                    │  (async thread)  │  Emits batches of readings
                    └────────┬────────┘
                             │
                        ┌────▼────┐
                        │Semaphore│  batchThrottle — caps in-flight
                        │         │  batches so memory stays bounded
                        └────┬────┘
                             │
                    ┌────────▼─────────┐
                    │ConcurrentBatch   │
                    │   Processor      │
                    │                  │
                    │  DB Workers ──────────► PostgreSQL (parallel upserts)
                    │  SQL Writer ──────────► .sql file (as batches complete)
                    │  Audit Logger ────────► batch_audit_log table
                    └──────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │ GET /jobs/{id}   │  Stats aggregated from audit log
                    │ GET /jobs/{id}/  │
                    │     errors       │  Paginated failed batch details
                    │ GET /jobs/{id}/  │
                    │     download     │  The .sql file
                    └─────────────────┘
```

### Batch processing

The parser reads the file on a single thread — it's stateful, tracking the current NMI from 200-records — and groups readings into batches. A `ConcurrentBatchProcessor` hands those batches off to a thread pool for parallel DB writes.

A semaphore (`batchThrottle`) limits how many batches sit in memory at once. When all slots are full, the parser pauses until a worker finishes. This keeps memory usage roughly the same whether the file is 20MB or 2GB.

Each batch runs in its own transaction. Everything in the batch commits together or rolls back together — no partial writes.

### Deduplication

NEM12 files sometimes have duplicate `(nmi, timestamp)` pairs within the same batch. PostgreSQL's `ON CONFLICT DO UPDATE` can't update the same row twice in one INSERT, so we deduplicate each batch in Java before sending it to the database. Last value wins.

Across batches and re-uploads, `ON CONFLICT DO UPDATE` takes care of it — the most recent consumption value always survives.

### Retry and failure handling

```
DB write
  ├── Success → log to batch_audit_log, continue
  └── Failure → retry once (fresh transaction)
          ├── Retry works → log success, continue
          └── Retry fails → log failure to batch_audit_log,
                             mark batch as FAILED, continue to next batch
```

- A single failed batch doesn't kill the whole job — the rest keep going
- Batches that already committed stay committed
- Every batch outcome (pass or fail) gets recorded in `batch_audit_log`
- Job-level stats come from that table, not from fields on the job row

### Batch audit log

Instead of cramming errors into a JSON column on the job, each batch writes its result to a separate table:

| Column | What it tracks |
|--------|----------------|
| job_id | Which job |
| batch_number | Order within the job |
| reading_count | Readings in the batch |
| readings_persisted | How many actually made it to the DB |
| status | SUCCESS or FAILED |
| error_message | What went wrong (null on success) |

Job-level stats (total batches, total persisted, failures) are aggregated from this table in one query.

### Job lifecycle

```
QUEUED → PROCESSING → COMPLETED   (every batch succeeded)
                    → PARTIAL     (some batches failed, rest succeeded)
                    → FAILED      (fatal error or timed out)
```

A `JobCleanupService` runs on a schedule and marks stuck jobs as FAILED if they haven't made progress within the configured timeout.

### SQL file format

Each batch is written as its own transaction:

```sql
-- Batch 1 (5000 readings)
BEGIN;
INSERT INTO meter_readings (id, nmi, timestamp, consumption) VALUES
  (gen_random_uuid(), 'NEM1201009', '2005-03-01 00:00:00', 0.000),
  (gen_random_uuid(), 'NEM1201009', '2005-03-01 00:30:00', 0.461),
  ...
ON CONFLICT (nmi, timestamp) DO UPDATE SET consumption = EXCLUDED.consumption;
COMMIT;
```

Safe to replay — the upsert means running it twice gives the same result.

## Authentication

All API endpoints (except `/actuator/health`) require an `X-API-Key` header. The key is checked by a servlet filter (`ApiKeyAuthFilter`) that sits in front of Spring Security. If the key matches the one in config, the request goes through. If not, you get a 401.

### Why API key?

This is a backend data-ingestion service, not a user-facing app. The clients are scripts, cron jobs, and internal tools — not humans logging in through a browser. For that kind of machine-to-machine communication, a simple API key works well:

- **No extra infrastructure** — no auth server, no token issuer, no key rotation service. The key lives in config (environment variable in prod, hardcoded in local dev).
- **Simple to integrate** — clients just add one header. No OAuth dance, no token refresh logic, no session management.
- **Fits the scope** — this is a single-service parser, not a multi-tenant platform. One key is enough to gate access.

The tradeoff is real: API keys don't expire on their own, there's no per-user identity, and you can't do fine-grained permissions. But for a focused service like this, that's an acceptable tradeoff over the complexity of a full auth stack.

### How to improve it

If this service grows beyond a single internal tool, here's what I'd change:

- **JWT with an identity provider** — swap the API key for short-lived JWTs issued by something like Keycloak or Auth0. You get token expiry, user identity, and role-based access (e.g., read-only vs. upload) without managing credentials yourself.
- **Per-client keys** — if sticking with API keys, issue a separate key per client so you can revoke one without breaking all consumers. Store hashed keys in the database instead of plain text in config.
- **Key rotation** — support multiple active keys during a rotation window. The current setup requires a redeploy to change the key, which means downtime or coordination.
- **Rate limiting per identity** — right now there's no way to throttle one client without throttling everyone. Per-client keys (or JWTs with a `sub` claim) would make this possible.
- **mTLS for service-to-service** — if this runs in a service mesh, mutual TLS handles auth at the transport layer. No keys or tokens to manage at all.

## API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/nem12/upload` | Upload a NEM12 file |
| GET | `/api/v1/nem12/jobs` | List recent jobs |
| GET | `/api/v1/nem12/jobs/{id}` | Job status and stats |
| GET | `/api/v1/nem12/jobs/{id}/errors` | Paginated failed batches |
| GET | `/api/v1/nem12/jobs/{id}/download` | Download SQL file |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/metrics` | Metrics |

All endpoints except actuator require `X-API-Key` header.

### Usage

**Upload a file:**

```bash
curl -X POST http://localhost:8080/api/v1/nem12/upload \
  -H "X-API-Key: local-dev-key-not-for-production" \
  -F "file=@src/test/resources/sample_nem12.csv"
```

**Check progress:**

```bash
curl http://localhost:8080/api/v1/nem12/jobs/{jobId} \
  -H "X-API-Key: local-dev-key-not-for-production"
```

**View errors:**

```bash
curl "http://localhost:8080/api/v1/nem12/jobs/{jobId}/errors?page=0&size=10" \
  -H "X-API-Key: local-dev-key-not-for-production"
```

**Download the SQL file:**

```bash
curl -O http://localhost:8080/api/v1/nem12/jobs/{jobId}/download \
  -H "X-API-Key: local-dev-key-not-for-production"
```

### Error codes

| Code | HTTP | When |
|------|------|------|
| `INVALID_FILE` | 400 | Bad format, wrong extension, missing 100-record header |
| `FILE_TOO_LARGE` | 413 | Exceeds 10GB upload limit |
| `JOB_NOT_FOUND` | 404 | No job with that ID |
| `JOB_NOT_COMPLETE` | 409 | Tried to download before processing finished |
| `UNAUTHORIZED` | 401 | Missing or invalid API key |

## Tuning for large files

The defaults work fine for most files. For multi-GB files, these are the knobs:

- `nem12.concurrent-batch-workers` — number of DB writer threads. Default 4. Can go up to your connection pool size (HikariCP defaults to 10). More threads = more parallel writes, but watch for lock contention.
- `nem12.max-in-flight-batches` — how far the parser can get ahead of the writers. Default 8. Each slot holds one batch in memory, so this controls your memory ceiling.
- `nem12.batch-size` — readings per batch. Default 5000. Bigger batches = fewer round-trips but more memory per batch.

Start with the defaults, keep an eye on DB CPU, and adjust if needed.

## Profiles

| Profile | Credentials | Logging | Use case |
|---------|------------|---------|----------|
| local | Hardcoded in yml | DEBUG | Dev machine |
| dev | From env vars | DEBUG | Shared dev environment |
| prod | From env vars | INFO | Production |

```bash
SPRING_PROFILES_ACTIVE=prod java -jar target/nem12-parser-1.0.0.jar
```

## Database schema

Three tables, managed by Flyway:

**meter_readings**
```
id          UUID (PK, auto-generated)
nmi         VARCHAR(10)
timestamp   TIMESTAMP
consumption NUMERIC
UNIQUE(nmi, timestamp)
```

**processing_jobs**
```
id              UUID (PK)
filename        VARCHAR(255)
status          VARCHAR(20)
sql_file_path   VARCHAR(500)
started_at, completed_at, created_at, last_updated_at   TIMESTAMP
```

**batch_audit_log**
```
id                 UUID (PK)
job_id             UUID (FK → processing_jobs)
batch_number       INTEGER
reading_count      INTEGER
readings_persisted INTEGER
status             VARCHAR(10)  — SUCCESS or FAILED
error_message      TEXT
created_at         TIMESTAMP
```

## Tests

```bash
mvn test                  # all tests
mvn verify                # tests + coverage check
mvn test jacoco:report    # HTML coverage report at target/site/jacoco/index.html
```

Tests use H2 in PostgreSQL compatibility mode and Mockito. No external database needed.

## Q&A

### Q1. What is the rationale for the technologies used?

**Spring Boot** — standard choice for a Java REST service. Gives you dependency injection, async execution, transaction management, and actuator endpoints without much wiring. Spring Data JPA and Flyway cut down on boilerplate.

**PostgreSQL** — we need `ON CONFLICT` for upsert behavior and a `UNIQUE` constraint on `(nmi, timestamp)`. Postgres handles both natively. TimescaleDB is in the Docker setup for potential time-series query work down the line, but it's not required.

**Flyway** — schema changes are versioned and run automatically on startup. No manual DDL scripts to forget about. Each migration is a plain SQL file, easy to review.

**JdbcTemplate over JPA for bulk inserts** — JPA's `saveAll()` fires one INSERT per entity. At 5000 readings per batch, that's 5000 round-trips. A single multi-row INSERT through `JdbcTemplate` does the same work in one statement. JPA is still used for the job entity where convenience matters more than throughput.

**H2 for tests** — in-memory, no external dependencies, fast. PostgreSQL compatibility mode handles most syntax differences. Tests stay self-contained and run in CI without a database container.

**Micrometer** — ships with Spring Boot. Gives us counters (readings parsed, persisted, failed), timers (batch duration, file processing time), and gauges (active jobs) with minimal setup. Plugs into whatever monitoring backend you use.

### Q2. What would you have done differently with more time?

**Testcontainers for integration tests** — H2 doesn't catch every PostgreSQL quirk (we ran into the `timestamp` reserved word issue, for example). Testcontainers would spin up a real Postgres instance for integration tests and catch these before deployment.

**Re-upload only failed batches** — if 3 out of 800 batches fail, you currently have to re-upload the whole file. It'd be better to let clients retry just the failed ranges.

**WebSocket or SSE for progress** — polling works but isn't ideal for long-running jobs. Push-based updates would be a better experience for large files.

**Rate limiting** — one large upload can hog all the DB workers. Per-client rate limits would keep things fair.

**Read replicas for status queries** — job status checks and batch writes share the same connection pool. Splitting reads to a replica would free up the primary for writes.

**Object storage for SQL files** — the generated SQL files sit on local disk, which doesn't survive pod restarts in Kubernetes. S3 or a shared volume would fix that.

**Table partitioning** — for very large datasets, partitioning `meter_readings` by NMI or date would keep query performance steady as the table grows.

### Q3. What is the rationale for the design choices?

**Streaming instead of load-and-parse** — a multi-GB NEM12 file doesn't fit in memory. The parser reads line-by-line, tracks minimal state (the current NMI from the last 200-record), and flushes in configurable batches. Memory stays flat regardless of file size.

**Concurrent batch writes** — a 2GB file produces hundreds of batches. Writing them one at a time would be bottlenecked by DB round-trips. A thread pool processes batches in parallel, and a semaphore keeps the parser from running too far ahead and blowing up memory.

**Upsert semantics** — NEM12 files get re-uploaded for corrections. `ON CONFLICT DO UPDATE` makes this idempotent — uploading the same file twice gives the same result, no cleanup step needed.

**Async processing with job tracking** — large files take minutes to hours. Holding an HTTP connection open that long is asking for timeouts, especially behind a load balancer. The client gets a job ID right away and polls. Job state lives in the database, so it survives restarts and works across multiple instances.

**Audit log instead of inline errors** — batch results go to their own table rather than a JSON blob on the job row. This keeps the job table small, makes errors queryable and paginated, and doesn't slow down as the number of batches grows.

**Thin controllers** — controllers validate the request and delegate to services. No business logic in there. Keeps them easy to test and puts logic where it belongs.

**Multi-row INSERT over saveAll** — JPA's one-INSERT-per-entity approach doesn't scale for batch ingestion. A single multi-row INSERT gets one parse/plan/execute cycle per batch instead of per row. The tradeoff is hand-written SQL in `MeterReadingService`, but the performance difference at 5000 rows is worth it.

## Project structure

```
src/main/java/com/nem12/
├── config/           # Properties, security, async thread pool
├── controller/       # REST endpoints (delegates to services)
├── exception/        # Custom exceptions + global error handler
├── health/           # Actuator health indicator
├── model/
│   ├── dto/          # Request/response records
│   ├── entity/       # MeterReading, ProcessingJob, BatchAuditLog
│   └── enums/        # JobStatus
├── repository/       # Spring Data repositories
├── security/         # API key filter
└── service/
    ├── parser/       # Streaming NEM12 parser + interval expander
    ├── persistence/  # SQL file writer
    └── validation/   # Upload file validation
```
