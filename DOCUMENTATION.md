# Task Execution Console — How It Works

A plain-English guide to the whole project: what each piece does, how the pieces talk to each other, why they were chosen, and how it runs on AWS EC2.

**Live site:** https://3-111-38-220.sslip.io (works only while that EC2 instance is running — see [section 11](#11-known-limitations)).

---

## Contents

1. [What the app does](#1-what-the-app-does)
2. [The big picture](#2-the-big-picture)
3. [Libraries and technologies](#3-libraries-and-technologies)
4. [Is Spring JPA used?](#4-is-spring-jpa-used)
5. [Why multiple containers?](#5-why-multiple-containers)
6. [Why PostgreSQL *and* MongoDB?](#6-why-postgresql-and-mongodb)
7. [How a task gets a thread and runs](#7-how-a-task-gets-a-thread-and-runs)
8. [Algorithms and techniques used](#8-algorithms-and-techniques-used)
9. [Software engineering practices](#9-software-engineering-practices)
10. [How it is deployed on EC2](#10-how-it-is-deployed-on-ec2)
11. [Known limitations](#11-known-limitations)

---

## 1. What the app does

You create a **task** (a name, a type, a priority, and a duration in seconds). When you click **Execute**, the server runs it in the background on its own thread. Many tasks can run **at the same time**. The web page refreshes every 2 seconds so you can watch tasks move through their life:

```
PENDING → RUNNING → COMPLETED
                  ↘ FAILED     (can be retried → back to PENDING)
PENDING / RUNNING → CANCELLED
```

The "work" is simulated with a sleep (`Thread.sleep`). That is deliberate: the project is about *how* work is scheduled and tracked safely, not about what the work is. A task whose type contains "fail" (e.g. `FAIL_TEST`) always fails, so you can see failure handling.

---

## 2. The big picture

```
  Your browser
       │  HTTPS (port 443)
       ▼
  ┌─────────┐       ┌──────────────┐      ┌──────────────────┐      ┌────────────┐
  │  Caddy  │ ───►  │   Frontend   │ ───► │     Backend      │ ───► │ PostgreSQL │
  │ (HTTPS) │       │ nginx+React  │ /api │  Spring Boot     │      │  (tasks)   │
  └─────────┘       └──────────────┘      │  port 8080       │      └────────────┘
                                          │                  │
                                          │  thread pool ⚙   │      ┌──────────────────┐   ┌─────────┐
                                          │                  │ ───► │ Activity-log svc │──►│ MongoDB │
                                          └──────────────────┘ REST │  Spring Boot     │   │ (events)│
                                                                    │  port 8081       │   └─────────┘
                                                                    └──────────────────┘
```

Six containers, all on one private Docker network. **Only Caddy is reachable from the internet.**

| Container | What it is | Job |
|---|---|---|
| `caddy` | Caddy 2 web server | Gets a free HTTPS certificate, terminates HTTPS, forwards to the frontend, adds security headers |
| `frontend` | nginx serving a built React app | Shows the dashboard; forwards `/api/*` to the backend |
| `backend` | Spring Boot (Java 17) | The REST API, the thread pool, the task state machine |
| `activity-log-service` | Spring Boot (Java 17) | Receives and stores task lifecycle events |
| `postgres` | PostgreSQL 16 | Stores tasks |
| `mongo` | MongoDB 7 | Stores events |

### How the pieces are connected

- **Browser → Caddy → nginx → backend.** Everything uses one address (`https://…`). The browser never talks to port 8080 directly. nginx forwards any URL starting with `/api/` to `backend:8080`.
- **Backend → PostgreSQL** over JDBC, using Spring Data JPA.
- **Backend → activity-log-service** over plain REST (`POST /api/events`).
- **activity-log-service → MongoDB** using Spring Data MongoDB.
- Containers find each other **by name** (`postgres`, `mongo`, `backend`…) because Docker Compose gives them DNS names on the shared network.

### Code layout (backend)

A request travels down these layers; each layer has one job:

```
TaskController          ← receives HTTP, validates input, returns JSON
      ↓
TaskServiceImpl         ← business rules ("only PENDING tasks can be executed")
      ↓
TaskExecutionService    ← hands work to the thread pool; tracks running jobs for cancel
      ↓                          ↓
TaskStateService        PriorityThreadPoolExecutor   ← the worker threads
      ↓
TaskRepository  →  PostgreSQL
```

---

## 3. Libraries and technologies

**Backend — task service** (`backend/pom.xml`)

| Library | Why it is here |
|---|---|
| Spring Boot 3 (Java 17) | Application framework, auto-configuration, embedded Tomcat |
| `spring-boot-starter-web` | REST controllers, JSON |
| `spring-boot-starter-data-jpa` (Hibernate) | Talk to PostgreSQL using Java objects |
| `spring-boot-starter-validation` | Rules like `@NotBlank`, `@Size`, `@Max(300)` on request bodies |
| `spring-boot-starter-actuator` | `/actuator/health` for Docker health checks |
| PostgreSQL JDBC driver | Database connection |
| Flyway | Creates and versions the database tables from SQL files |
| Lombok | Removes getter/setter boilerplate |
| JUnit 5, Mockito, AssertJ, Testcontainers | Testing (Testcontainers starts a real PostgreSQL for integration tests) |

**Backend — activity-log service** (`backend/activity-log-service/pom.xml`)

Spring Web, **Spring Data MongoDB**, Actuator, Lombok.

**Frontend** (`frontend/package.json`)

React 18, TypeScript, Vite (build tool), React Router, Axios (HTTP calls).

**Infrastructure**

Docker and Docker Compose, nginx (serves the built frontend), Caddy (HTTPS), AWS EC2, Let's Encrypt (free certificates), sslip.io (free hostname for an IP).

---

## 4. Is Spring JPA used?

**Yes — in the task service. No — in the activity-log service.**

- **Task service:** uses Spring Data JPA with Hibernate on PostgreSQL.
  - `Task` is a JPA `@Entity` mapped to the `tasks` table.
  - `TaskRepository extends JpaRepository<Task, Long>`. Methods like `findAllByStatusOrderByCreatedAtDesc` are written as names only — Spring generates the SQL.
  - One method uses `@Lock(PESSIMISTIC_WRITE)` to produce `SELECT … FOR UPDATE` (see section 8).
  - `ddl-auto: validate` means Hibernate **checks** the tables match the entities but never changes them. **Flyway** owns the schema (`db/migration/V1__create_tasks_table.sql`).
  - `open-in-view: false` keeps database sessions from leaking into the web layer.
- **Activity-log service:** uses **Spring Data MongoDB**, a sibling of JPA. `TaskEvent` is a `@Document`, not an `@Entity`. JPA only works with relational (SQL) databases, so it cannot be used with MongoDB.

---

## 5. Why multiple containers?

1. **One process, one job.** Each container runs a single program. A crash or restart in one does not take the others down (the backend keeps working if the log service is down).
2. **Each tool needs its own environment.** Java 17, Node/nginx, PostgreSQL and MongoDB each need different software. Containers keep them from conflicting.
3. **Same everywhere.** `docker compose up --build` gives the same result on your laptop and on EC2. No "it works on my machine".
4. **Security.** The databases and APIs live on a private network. Only Caddy publishes ports 80 and 443, so there is very little for an attacker to reach.
5. **Startup order and health.** Compose waits for each container's health check before starting the next: Mongo → activity-log → (Postgres) → backend → frontend → Caddy.
6. **Independent scaling and deploys.** The activity-log service has its own `pom.xml` and `Dockerfile`, so it can be changed or scaled without touching the task service.
7. **Persistence.** Database data lives in named volumes (`postgres_data`, `mongo_data`), so recreating a container does not delete your tasks.

---

## 6. Why PostgreSQL *and* MongoDB?

The two kinds of data are very different, so each gets the database that suits it.

| | **PostgreSQL** — tasks | **MongoDB** — events |
|---|---|---|
| Shape of data | Fixed columns, same for every task | Varies: a `COMPLETED` event has `durationMs`, a `FAILED` one has `errorMessage` |
| Needs transactions? | **Yes.** Status changes must never be lost or half-applied | No. A lost log line is acceptable |
| Needs row locks? | **Yes** (`SELECT … FOR UPDATE`) | No |
| Access pattern | Update the same row many times; filter by status/priority | Append only; read by task id |
| Schema changes | Controlled, versioned (Flyway) | Flexible: add fields without a migration |

**In short:** tasks are *state that must be correct* → relational database with strong consistency. Events are *an append-only history of varying shape* → document database.

Honest note: for a project this small, one PostgreSQL database (with a `JSONB` column) could do both jobs. MongoDB was chosen deliberately to show a second microservice with a second kind of datastore, and to keep the log completely separated from task execution.

---

## 7. How a task gets a thread and runs

### The pool

`ThreadPoolConfig` creates **one** thread pool, a `PriorityThreadPoolExecutor` (our subclass of Java's `ThreadPoolExecutor`). Nothing else in the app creates threads for tasks.

| Setting | Default | Meaning |
|---|---|---|
| core-pool-size | 4 | Number of worker threads that normally run |
| max-pool-size | 10 | Upper limit, only used when the queue is full |
| queue-capacity | 50 | How many tasks may wait for a free worker |
| keep-alive-seconds | 30 | How long an extra thread (above core) idles before closing |
| thread name | `task-worker-N` | Easy to spot in logs |

All are environment variables (`TASK_EXECUTOR_*`), so you can resize the pool without changing code.

### Step by step: what happens when you click Execute

1. The browser sends `POST /api/tasks/{id}/execute`.
2. `TaskController` passes it to `TaskServiceImpl.executeTask`. It checks the task is `PENDING`; otherwise it returns **409 Conflict**.
3. `TaskExecutionService.submit` wraps the work in a small object that knows its **priority**, and gives it to the pool. It also saves the returned `Future` in a map (`inFlight`) so the task can be cancelled later.
4. **The HTTP request returns immediately with 202 Accepted.** The work has not run yet.
5. The pool decides what to do with the job:
   - **Fewer than 4 workers exist** → start a new worker thread for it right away.
   - **All 4 are busy** → put the job in the waiting queue (up to 50).
   - **Queue is full (50)** → start extra threads, up to 10 total.
   - **Everything is full** → reject it. The API answers **503 Service Unavailable** (backpressure) rather than silently dropping work.
6. When a worker is free, it runs `runTask`:
   1. `markRunning` — in one short database transaction, lock the row, set `RUNNING`, save the start time.
   2. `simulateWork` — sleep for the duration.
   3. `markCompleted` (or `markFailed` if an exception was thrown) — lock the row again, record the finish time and duration.
7. Each state change also sends an event to the activity-log service (STARTED / COMPLETED / FAILED / CANCELLED).
8. Meanwhile the dashboard polls `GET /api/tasks` and `GET /api/tasks/stats` every 2 seconds and shows the new state.

> **Note about the "4":** the worker-pool bar on the dashboard shows `active / 4`. In practice, with a queue of 50, the pool grows past 4 threads only once 50 tasks are already waiting. This is how Java's `ThreadPoolExecutor` works, not a bug.

### How cancel works

`cancel()` calls `Future.cancel(true)`, which **interrupts** the worker thread. The sleeping worker wakes with an `InterruptedException` and stops. Then `markCancelled` updates the database. If the task finished a split second earlier, `markCancelled` sees the status is already final and the API returns 409 instead of overwriting `COMPLETED` with `CANCELLED`.

### How failures are contained

An exception inside one task is caught in `runTask`, recorded as `FAILED`, and goes no further. Other tasks and the pool are unaffected.

---

## 8. Algorithms and techniques used

### 8.1 Priority scheduling (priority queue)

Waiting tasks sit in a `PriorityBlockingQueue`, which is a **binary heap**. Adding or removing a job costs O(log n).

The ordering rule (in `ComparableFutureTask.compareTo`):

1. **Higher priority first** — `CRITICAL > HIGH > MEDIUM > LOW`.
2. **Same priority → first come, first served.** A counter (`AtomicLong`) numbers each submission, and the lower number wins.

The queue is *bounded* (a custom `BoundedPriorityQueue` refuses new items once it holds 50), because Java's `PriorityBlockingQueue` is unbounded by default and would never trigger the "reject" rule.

Two things to know:
- Priority matters **only for tasks that have to wait.** If a worker is idle, the task starts at once.
- There is **no aging**. If high-priority tasks keep arriving, a `LOW` task could wait indefinitely (starvation).

### 8.2 Thread pool pattern

Reuse a fixed set of threads instead of creating one per task. Creating threads is expensive and unbounded creation could crash the server.

### 8.3 Concurrency control: two layers of locking

The problem: a worker finishing a task and a user clicking Cancel can happen at the same instant. Without protection, the task could end up in a wrong state.

1. **Pessimistic locking (main protection).** Every status change runs in its own transaction and starts with `SELECT … FOR UPDATE`. That locks the row. Whoever gets the lock first wins; the second one then sees the updated status and either does nothing or is refused.
2. **Optimistic locking (safety net).** The `version` column (`@Version`) increases on every update. If some write ever slips past the lock, the version mismatch makes it fail with a 409 rather than silently overwriting data.

### 8.4 State machine

A task can only move along allowed paths (`PENDING→RUNNING→COMPLETED/FAILED`, anything not final → `CANCELLED`, `FAILED→PENDING` on retry). Every method checks the current state before changing it and throws an error otherwise. Illegal moves are impossible.

### 8.5 Backpressure

When the system is full, it says **"503, try later"** instead of accepting unlimited work or blocking the web server's own thread.

### 8.6 Fire-and-forget logging

`ActivityLogClient` sends events and catches every error. If the log service is down, a warning is logged and the task carries on unaffected.

### 8.7 Cooperative cancellation

Threads in Java cannot be killed safely. Instead the thread is *asked* to stop via an interrupt, and it stops at its next blocking call.

---

## 9. Software engineering practices

| Practice | Where you can see it |
|---|---|
| **Layered architecture** | Controller → Service → Repository, each with one responsibility |
| **Single Responsibility** | `TaskStateService` owns *all* status changes; `TaskExecutionService` is the *only* code touching the pool |
| **Dependency injection** | Constructor injection via Spring (`@RequiredArgsConstructor`); makes components easy to test |
| **DTOs** | `TaskCreateRequest` / `TaskResponse` keep the API separate from the database entity |
| **Input validation** | Bean Validation annotations reject bad requests with a 400 |
| **Centralized error handling** | `GlobalExceptionHandler` turns exceptions into one consistent JSON error shape |
| **Database migrations** | Flyway SQL files are versioned and repeatable; Hibernate only validates |
| **Configuration outside code** | Passwords, ports, pool sizes, CORS origins come from environment variables (12-factor style) |
| **Testing** | Unit tests with Mockito, web-layer tests, real multi-thread tests using `CountDownLatch`, and Testcontainers integration tests against real PostgreSQL |
| **Fail-safe design** | Failures isolated per task; logging cannot break execution; backpressure under load |
| **Containerization** | Multi-stage Dockerfiles (build stage + small runtime stage), non-root user for the Java app, health checks on every service |
| **Infrastructure as code** | `docker-compose.yml`, `Caddyfile` and `deploy/aws-ec2-deploy.sh` describe the entire deployment |
| **Security basics** | Private network, only 80/443 public, HTTPS + HSTS, security headers, CORS allow-list, no secrets in source |
| **Microservice boundary** | Task service never touches MongoDB; log service never touches PostgreSQL; they only talk over REST |

---

## 10. How it is deployed on EC2

### What exists in AWS

- **1 EC2 instance:** Amazon Linux 2023, `t3.small` or larger (the Java builds need ~2 GB of RAM), about 20 GB disk, in the Mumbai region (`ap-south-1`).
- **1 key pair** (a `.pem` file) used to SSH in.
- **1 security group** (the firewall):

  | Port | Open to | Why |
  |---|---|---|
  | 22 | Your IP only (set this to **My IP**) | SSH for deploying |
  | 80 | Everyone | HTTP, redirected to HTTPS; also used by Let's Encrypt |
  | 443 | Everyone | HTTPS |

  Ports 8080, 8081, 5432 and 27017 are **not** open. Docker does not publish them either.

### Setup (done once)

1. In the EC2 console, launch the instance with this **User data** (it runs on first boot and installs Docker, rsync, and the Compose plugin):

   ```bash
   #!/bin/bash
   dnf install -y docker rsync
   systemctl enable --now docker
   usermod -aG docker ec2-user
   mkdir -p /usr/local/lib/docker/cli-plugins
   curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose
   chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
   ```

2. Newer Compose needs **buildx ≥ 0.17**. Amazon Linux's Docker does not include it, so install it once:

   ```bash
   ssh -i key.pem ec2-user@<IP> 'sudo curl -fsSL \
     https://github.com/docker/buildx/releases/download/v0.19.3/buildx-v0.19.3.linux-amd64 \
     -o /usr/local/lib/docker/cli-plugins/docker-buildx && sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx'
   ```

3. Create `~/task-platform/.env` **on the server** (it lives only there, not in git). Replace the dots with dashes in the IP:

   ```
   DOMAIN=3-111-38-220.sslip.io
   CORS_ALLOWED_ORIGINS=https://3-111-38-220.sslip.io
   ```

   - `DOMAIN` tells Caddy which name to get a certificate for. [sslip.io](https://sslip.io) is a free service where `3-111-38-220.sslip.io` always points to `3.111.38.220`.
   - `CORS_ALLOWED_ORIGINS` tells the backend which website is allowed to call it. **Without this, creating tasks from the browser fails with 403**, even though curl works, because browsers send an `Origin` header.

### Deploying (every time)

From the project folder on your Mac:

```bash
./deploy/aws-ec2-deploy.sh ec2-user@<EC2-IP> /path/to/key.pem
```

The script does two things:
1. `rsync` copies the project to `~/task-platform/` on the server (skipping `node_modules`, `target`, `.git`).
2. Over SSH it runs `docker compose up --build -d`, which builds the images on the server and starts the six containers in the right order.

### What happens when someone opens the site

1. DNS: `3-111-38-220.sslip.io` → `3.111.38.220`.
2. The request reaches Caddy on port 443. On first start, Caddy proved to Let's Encrypt that it controls the name and received a free certificate. It renews automatically.
3. Caddy adds security headers and forwards to the `frontend` container.
4. nginx returns the React app. When the page calls `/api/tasks`, nginx forwards it to `backend:8080`.

### Checking that it works

```bash
ssh -i key.pem ec2-user@<IP> 'cd ~/task-platform && docker compose ps'   # all should be Up / healthy
curl -I https://3-111-38-220.sslip.io                                    # HTTP/2 200
curl https://3-111-38-220.sslip.io/api/tasks/stats                       # JSON counts
ssh -i key.pem ec2-user@<IP> 'cd ~/task-platform && docker compose logs --tail 50'
```

Then open the URL, click **+ New task**, **Execute**, and watch it complete.

### Stopping billing

EC2 console → Instances → select it → **Instance state → Terminate**. (Stopping only pauses compute charges.)

---

## 11. Known limitations

Honest list of what this project does **not** do:

- **No login.** Anyone with the link can create, run, cancel and delete tasks. Fine for a demo; add Spring Security before real use.
- **IP can change.** Without an Elastic IP, stopping and starting the instance gives a new public IP, which breaks the `sslip.io` name and certificate. (A reboot keeps the IP.) After a change, update `DOMAIN` and `CORS_ALLOWED_ORIGINS` in the server's `.env` and redeploy.
- **Default database password** (`taskpass`). The database is not reachable from the internet, but set `DB_PASSWORD` in the server's `.env` before storing anything that matters. Postgres reads it only when the data volume is first created.
- **No priority aging**, so low-priority tasks can starve under constant high-priority load.
- **Stats read every task** (`findAll`) and count in memory. Fine for hundreds of tasks; use `COUNT … GROUP BY` queries for thousands.
- **Event is sent inside the DB transaction.** `ActivityLogClient.record` is called before the status transaction commits, so a slow log service would hold the row lock longer. Moving it to after commit would fix this.
- **Polling, not push.** The dashboard asks every 2 seconds. WebSockets or Server-Sent Events would update instantly.
- **Single server.** One EC2 instance, one copy of each container; no automatic failover.
- **Work is simulated.** Tasks just sleep. The scheduling, locking and lifecycle are real; the job itself is not.
