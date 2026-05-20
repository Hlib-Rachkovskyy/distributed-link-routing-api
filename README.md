# distributed-link-routing-api

A high-performance URL shortening backend service built with Spring Boot, PostgreSQL, and Redis. It provides rapid URL shortening, lightning-fast redirections via Redis caching, API rate limiting to prevent abuse, and an asynchronous analytics tracking system for URL clicks.

## Key Features

- **URL Shortening**: Generates a secure, 6-character Base62 short code for any valid URL.
- **Lightning-Fast Redirects**: Uses Redis caching to resolve short codes instantly without hitting the database.
- **API Rate Limiting**: Protects the shortening endpoint by limiting requests to 10 per minute per IP address.
- **High-Performance Analytics**: Tracks URL clicks in real-time using Redis atomic operations and batch-syncs the totals to PostgreSQL every 10 minutes to ensure the database is not bottlenecked by high traffic.

---

## Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.2.5
- **Persistence**: PostgreSQL 15
- **Caching & Analytics**: Redis 7
- **Build Tool**: Maven
- **Libraries**: Spring Data JPA, Spring Data Redis, Lombok, Spring Boot Validation

---

## Prerequisites

Before you begin, ensure you have the following installed:
- Java 21 or higher
- Maven 3.8+
- Docker & Docker Compose (for running PostgreSQL and Redis locally)

---

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/Hlib-Rachkovskyy/distributed-link-routing-api.git
cd distributed-link-routing-api/UrlShortenerService
```

### 2. Configure Environment Variables (Supabase & Upstash Redis)

The application connects directly to your cloud infrastructure (**Supabase** for PostgreSQL and **Upstash** for Redis).

1. Copy the template `.env.example` file to create your local `.env` file:
   ```bash
   cp .env.example .env
   ```
2. Open your `.env` file and replace the placeholders with your actual cloud connection details:

   #### A. Supabase (PostgreSQL)
   Retrieve connection details from the **Supabase Dashboard** (Settings > Database > Connection Pooling or Direct Connection):
   ```env
   SPRING_DATASOURCE_URL=jdbc:postgresql://db.xxxx.supabase.co:6543/postgres?sslmode=require
   SPRING_DATASOURCE_USERNAME=postgres.xxxx
   SPRING_DATASOURCE_PASSWORD=your_supabase_password
   ```
   *(Optional)* Add client credentials if you plan to use Supabase REST APIs or SDKs:
   ```env
   SUPABASE_URL=https://xxxx.supabase.co
   SUPABASE_KEY=your-anon-key
   SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
   ```

   #### B. Upstash (Redis)
   Retrieve connection details from the **Upstash Console**. Since Upstash requires TLS/SSL, make sure to set `SPRING_DATA_REDIS_SSL_ENABLED=true`:
   ```env
   SPRING_DATA_REDIS_HOST=xxxx-xxxx-32001.upstash.io
   SPRING_DATA_REDIS_PORT=32001
   SPRING_DATA_REDIS_PASSWORD=your_upstash_redis_password
   SPRING_DATA_REDIS_SSL_ENABLED=true
   ```
   *(Optional)* If you plan to make REST requests or use Upstash REST SDKs:
   ```env
   UPSTASH_REDIS_REST_URL=https://xxxx-xxxx-32001.upstash.io
   UPSTASH_REDIS_REST_TOKEN=your_upstash_rest_token
   ```

3. To load the environment variables from `.env` in your terminal when running the application, you can:
   - **Using Git Bash / Linux / macOS**:
     ```bash
     export $(grep -v '^#' .env | xargs)
     ```
   - **Using Windows PowerShell**:
     ```powershell
     Get-Content .env | ForEach-Object {
       if ($_ -notmatch "^#" -and $_ -like "=*") {
         $name, $value = $_ -split '=', 2
         [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
       }
     }
     ```
   - **Using an IDE**: Install plugins like the **EnvFile** plugin for IntelliJ IDEA, or configure standard environment variables in the Run/Debug Configurations.


### 4. Build the Application

Compile the code and download Maven dependencies:

```bash
mvn clean install -DskipTests
```

### 5. Start the Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`.

---

## Architecture

### Directory Structure

```text
├── src/
│   ├── main/
│   │   ├── java/com/project/urlshortener/
│   │   │   ├── config/          # Redis and Interceptor configs
│   │   │   ├── controller/      # REST API Controllers
│   │   │   ├── dto/             # Data Transfer Objects
│   │   │   ├── exception/       # Global error handling
│   │   │   ├── model/           # JPA Entities
│   │   │   ├── repository/      # Spring Data JPA Repositories
│   │   │   ├── service/         # Business logic & Analytics Sync
│   │   │   ├── util/            # Base62 Encoder utility
│   │   │   └── UrlShortenerApplication.java
│   │   └── resources/
│   │       └── application.yml  # Application properties
├── docker-compose.yml           # Infrastructure setup
└── pom.xml                      # Maven dependencies
```

### Request Lifecycle

**1. Shortening a URL (`POST /api/v1/urls`)**
- Request hits `UrlShortenerController`.
- Passes through `RateLimitInterceptor` which checks Redis to ensure the client IP hasn't exceeded 10 requests per minute.
- `UrlShortenerService` generates a unique Base62 string and saves the mapping to PostgreSQL.

**2. Resolving a URL (`GET /{shortCode}`)**
- Request hits `UrlShortenerController`.
- The controller queries `UrlShortenerService.resolveUrl()`.
- Spring checks Redis for the key (`urls::{shortCode}`). If found, it returns the original URL instantly (Cache Hit).
- If not found, it queries PostgreSQL, caches the result in Redis for 24 hours, and returns it.
- **Analytics**: During the redirect, the controller sends an `INCR` command to Redis to track the click and adds the short code to a `sync_click_keys` set.

**3. Analytics Sync (Background Job)**
- `AnalyticsSyncService` runs every 10 minutes.
- Pops keys from `sync_click_keys`, retrieves the click count via an atomic `GETSET` (resetting it to 0), and updates PostgreSQL in bulk.

### Database Schema

```text
url_mappings
├── id (bigint, PK, auto-increment)
├── shortCode (varchar 20, unique, indexed)
├── originalUrl (varchar 2048, not null)
├── clickCount (bigint, default 0)
└── createdAt (timestamp, not null)
```

---

## API Documentation

### 1. Shorten URL
Create a new shortened URL. Rate limited to 10 requests per minute per IP.

**Request**
```http
POST /api/v1/urls
Content-Type: application/json

{
  "originalUrl": "https://example.com/very/long/url/path"
}
```

**Response (201 Created)**
```json
{
  "originalUrl": "https://example.com/very/long/url/path",
  "shortUrl": "http://localhost:8080/aB3x9Z",
  "createdAt": "2026-05-13T20:00:00.000000"
}
```

### 2. Redirect
Redirect to the original URL.

**Request**
```http
GET /aB3x9Z
```

**Response (302 Found)**
```http
Location: https://example.com/very/long/url/path
```

---

## Available Commands

| Command | Description |
|---|---|
| `docker-compose up -d` | Start local Postgres and Redis instances |
| `docker-compose down` | Stop local infrastructure |
| `mvn clean compile` | Compile the source code |
| `mvn spring-boot:run` | Start the Spring Boot application locally |
| `docker build -t urlshortener:latest .` | Build the optimized application Docker image |
| `docker run -p 8080:8080 ... urlshortener:latest` | Run the application in a Docker container |

---

## Production Deployment (Docker)

This repository includes a production-ready, multi-stage `Dockerfile` optimized for security and performance.

### 1. Build the Image
The image uses a multi-stage build to compile the app without leaving build dependencies in the final image.
```bash
docker build -t urlshortener-service:latest .
```

### 2. Run the Container
You can run the container by passing your `.env` file directly, or by specifying individual environment variables to connect to your PostgreSQL and Redis instances.

**Option A: Using the `.env` file**
```bash
docker run -d \
  --name urlshortener \
  -p 8080:8080 \
  --env-file .env \
  urlshortener-service:latest
```

**Option B: Specifying environment variables manually**
```bash
docker run -d \
  --name urlshortener \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<postgres-host>:5432/url_shortener \
  -e SPRING_DATASOURCE_USERNAME=admin \
  -e SPRING_DATASOURCE_PASSWORD=password \
  -e SPRING_DATA_REDIS_HOST=<redis-host> \
  -e SPRING_DATA_REDIS_PORT=6379 \
  urlshortener-service:latest
```

> **Security Note:** The Docker container is hardened. It runs as a non-root user (`springuser` with UID `1001`) and relies on a `wget` based `HEALTHCHECK` using Spring Boot Actuator (`/actuator/health`).

---

## Troubleshooting

### "Too Many Requests" Error
**Error**: `429 Too Many Requests` when trying to shorten a URL.
**Solution**: You have hit the rate limit (10 requests per minute). Wait 60 seconds for the Redis TTL on your IP address to expire and try again.

### Database Connection Refused
**Error**: `Connection refused: localhost:5432`
**Solution**: Ensure your PostgreSQL container is running. If running manually (or using custom ports/credentials), verify that your `.env` settings (or fallback environment variable defaults in `application.yml`) match your PostgreSQL credentials (default database: `url_shortener`, user: `admin`, password: `password`).

### Class Not Found / Compilation Errors
**Error**: `error: release version 22 not supported`
**Solution**: Ensure your `JAVA_HOME` is set to Java 21. If using Maven, verify that `java.version` in `pom.xml` matches your installed JDK version.
