# ✂️ QueueCut — Campus Barber Queue Management System

> **A real-time, zero-friction virtual barber queue system engineered for FAST University campus (Muhammad Arslan's Barber Shop).**

---

## 📌 Overview

**QueueCut** eliminates the frustration of long, unpredictable physical wait lines outside campus barber shops. Students can check live queue progress, join the line virtually using their student ID with zero account registration, and track their turn dynamically. Meanwhile, the barber operates a streamlined, distraction-free management portal to advance the line, handle no-shows, and adjust service timings.

---

## 🚀 Key Features

### 👤 Student Portal (Mobile-First)
* **Frictionless Queue Entry**: Join with Full Name and Student Roll ID. No passwords, downloads, or account creation needed.
* **Persistent Opaque Token**: Generates a secure client UUID (`studentToken`) persisted in `localStorage` to resume session across browser tabs or refreshes.
* **Live Ticket Tracking**: Real-time counter showing exact queue number, students ahead, and dynamic estimated wait time in minutes.
* **Smart Audio & Haptic Alerts**: 
  * Ascending two-tone chime & mobile vibration when status becomes **`ALMOST_READY`** (1 student ahead).
  * Fanfare chime & haptic burst when status becomes **`CURRENT`** ("In the chair!").
* **Self-Service Cancellation**: Safely forfeit a spot before being called.

### 💈 Barber Dashboard (`#barber`)
* **Secure Single-Tenant Auth**: Protected by Spring Security and stateless HMAC-SHA256 JWT tokens.
* **Queue Session Controls**: Instant one-tap toggle to `OPEN` or `CLOSE` the daily queue.
* **Chair Management**: Displays the active student in the chair with an elapsed time counter.
* **One-Click Progression**:
  * **Call Next**: Atomically marks current student `COMPLETED`, promotes next waiting candidate to `CURRENT`, and sets the subsequent student to `ALMOST_READY`.
  * **Skip / No-Show**: Flags an absent student as `SKIPPED` without breaking queue continuity.
* **Service Duration Calibration**: Dynamically adjust average haircut duration (5–120 mins) with immediate wait time recalculations.

### ⚡ Real-Time Streaming Architecture (SSE)
* Employs unidirectional **Server-Sent Events (SSE)** on `/api/queue/stream` to broadcast queue shifts in $< 500\text{ ms}$.
* Built-in 25-second heartbeat ping scheduler to keep reverse proxies (Render, Cloudflare, Nginx) from dropping idle connections.

---

## 🛠️ Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Backend** | Java 21+, Spring Boot 3.3.4, Spring Security, JJWT 0.12.6, Spring Data JPA, Hibernate |
| **Database** | PostgreSQL 16+, Flyway Migrations (Schema V1, Seeds V2 & V3) |
| **Frontend** | Vite, Vanilla JavaScript (ES Modules), Vanilla CSS (Custom Design System), Web Audio API |
| **Reverse Proxy & Server** | Nginx Alpine (with unbuffered SSE proxying) |
| **Containerization** | Docker, Docker Compose (Multi-stage builds) |

---

## 📂 Project Structure

```text
QueueCut/
├── backend/
│   ├── src/main/java/com/queuecut/
│   │   ├── config/          # SecurityConfig, DataInitializer
│   │   ├── controller/      # Auth, Queue, Barber, Settings REST controllers
│   │   ├── dto/             # Request/Response data models
│   │   ├── entity/          # JPA Entities (QueueSession, QueueEntry, BarberAccount, Settings)
│   │   ├── exception/       # GlobalExceptionHandler & Domain exceptions
│   │   ├── repository/      # Spring Data JPA Repositories
│   │   ├── security/        # JwtAuthFilter, JwtTokenProvider, BarberUserDetailsService
│   │   └── service/         # QueueService, BarberService, AuthService, SseService
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── db/migration/    # Flyway SQL migrations (V1, V2, V3)
│   ├── src/test/            # Unit & slice test suites
│   ├── Dockerfile           # Multi-stage JDK 21 / JRE Alpine container
│   ├── mvnw / mvnw.cmd      # Cross-platform Maven wrapper
│   └── pom.xml
│
├── frontend/
│   ├── public/              # SVG barber icons & favicon
│   ├── src/
│   │   ├── api.js           # REST API client & SSE reconnection manager
│   │   ├── sound.js         # Web Audio API synthesizer & mobile haptics
│   │   ├── style.css        # Master Obsidian & Amber design system
│   │   └── main.js          # Student & Barber portal view controller
│   ├── index.html           # HTML5 Shell with Google Fonts (Outfit & Inter)
│   ├── nginx.conf           # Nginx unbuffered reverse proxy configuration
│   ├── Dockerfile           # Multi-stage Node / Nginx production container
│   └── vite.config.js       # Vite dev proxy configuration
│
├── docker-compose.yml       # Complete local multi-container orchestration
├── .env.example             # Environment configuration template
└── README.md
```

---

## ⚡ Quick Start with Docker Compose

The fastest way to launch the entire stack (Postgres + Spring Boot + Frontend Nginx) locally:

```bash
# Clone the repository
git clone https://github.com/Zohaibjaani1234/QueueCut.git
cd QueueCut

# Spin up all services
docker compose up --build
```

* **Student Portal**: Open [http://localhost](http://localhost) (or port 80)
* **Barber Dashboard**: Open [http://localhost/#barber](http://localhost/#barber)
  * Default Barber Username: `arslan`
  * Default Barber Password: `arslan123`

---

## 💻 Manual Local Development Setup

### 1. Database Setup
Create a PostgreSQL database named `queuecut`:
```sql
CREATE DATABASE queuecut;
```

### 2. Run the Spring Boot Backend
Navigate to `/backend`:
```bash
cd backend

# Run automated tests
./mvnw test

# Start the dev server (Default port: 8080)
./mvnw spring-boot:run
```

### 3. Run the Frontend
Navigate to `/frontend` in a new terminal:
```bash
cd frontend

# Install dependencies
npm install

# Start Vite dev server (Default port: 5173 with proxy to 8080)
npm run dev
```

Visit `http://localhost:5173` to access the application.

---

## 📡 API Reference Overview

### Public & Student Endpoints
* `GET  /api/queue/status` — Get general queue status, ETA, and who is currently in chair.
* `POST /api/queue/join` — Join today's virtual queue (`{ studentName, studentId }`).
* `GET  /api/queue/my-status/{entryId}` — Track individual ticket position (`X-Student-Token` required).
* `POST /api/queue/{entryId}/cancel` — Cancel personal ticket before turn (`X-Student-Token` required).
* `GET  /api/queue/stream` — Real-time Server-Sent Events stream (`text/event-stream`).
* `GET  /api/settings` — Read current average haircut duration.

### Barber Management Endpoints (`ROLE_BARBER` / Bearer JWT Required)
* `POST /api/auth/login` — Barber authentication (`{ username, password }`).
* `POST /api/barber/session/open` — Open today's queue session.
* `POST /api/barber/session/close` — Close today's queue session.
* `GET  /api/barber/session/current` — View active session details.
* `POST /api/barber/queue/call-next` — Advance queue line to next student.
* `POST /api/barber/queue/{entryId}/complete` — Explicitly complete a haircut.
* `POST /api/barber/queue/{entryId}/skip` — Mark absent student as skipped.
* `GET  /api/barber/queue/entries` — List all entries for session (`?status=ACTIVE`).
* `PUT  /api/settings` — Update average haircut duration in minutes (`{ avgHaircutMin: 25 }`).

---

## 🔒 Concurrency & Data Integrity

1. **Atomic Sequence Generation**:
   Queue numbering is serialized at the PostgreSQL row level via `UPDATE queue_session SET last_queue_number = last_queue_number + 1 ... RETURNING` within atomic transactions, preventing duplicate ticket numbers under simultaneous joins.
2. **Duplicate Active Ticket Prevention**:
   Enforced via a partial unique index:
   ```sql
   CREATE UNIQUE INDEX uq_entry_student_active_per_session
       ON queue_entry (session_id, student_id)
       WHERE status IN ('WAITING', 'ALMOST_READY', 'CURRENT');
   ```
3. **Single Active Chair**:
   The database engine strictly prevents having more than one active `CURRENT` student per session:
   ```sql
   CREATE UNIQUE INDEX uq_entry_one_current_per_session
       ON queue_entry (session_id)
       WHERE status = 'CURRENT';
   ```

---

## 📄 License
MIT License. Developed for FAST University Campus.