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

### 2. Start Infrastructure (Database & Cache)

The project includes a `docker-compose.yml` file to quickly spin up PostgreSQL and Redis.

```bash
# Start PostgreSQL and Redis in the background
docker-compose up -d
```

> **Note**: If you prefer to run Docker containers manually without Compose, ensure they match the credentials in `application.yml`.
> ```bash
> docker run -d --name redis -p 6379:6379 redis
> docker run --name urlshortener-postgres -e POSTGRES_PASSWORD=mysecretpassword -p 5432:5432 -d postgres
> ```

### 3. Build the Application

Compile the code and download Maven dependencies:

```bash
mvn clean install -DskipTests
```

### 4. Start the Application

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
You can run the container, passing in the required environment variables to connect to your PostgreSQL and Redis instances.

```bash
docker run -d \
  --name urlshortener \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<postgres-host>:5432/postgres \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=mysecretpassword \
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
**Solution**: Ensure your PostgreSQL container is running. If you ran it manually instead of using `docker-compose`, ensure the password matches `mysecretpassword` and the database name is `postgres` as configured in `application.yml`.

### Class Not Found / Compilation Errors
**Error**: `error: release version 22 not supported`
**Solution**: Ensure your `JAVA_HOME` is set to Java 21. If using Maven, verify that `java.version` in `pom.xml` matches your installed JDK version.
