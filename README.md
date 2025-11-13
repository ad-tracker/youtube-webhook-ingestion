# YouTube Webhook Ingestion Service

A production-ready Spring Boot 4.0.0-RC2 microservice for receiving and processing YouTube webhook notifications. Built with Java 25, this service ingests webhook events, validates them, persists to PostgreSQL, and publishes to RabbitMQ for downstream processing.

## Features

- **Modern Stack**: Spring Boot 4.0.0-RC2 with Java 25
- **Virtual Threads**: Leverages Java 25 virtual threads for improved scalability
- **Event Streaming**: RabbitMQ integration for async event processing
- **Persistence**: PostgreSQL with JPA/Hibernate
- **Security**: Spring Security with configurable authentication
- **Observability**: Actuator endpoints with Prometheus metrics
- **Production Ready**: Docker support, health checks, and comprehensive logging
- **High Test Coverage**: >80% code coverage with comprehensive unit tests

## Technology Stack

- **Framework**: Spring Boot 4.0.0-RC2
- **Language**: Java 25 (LTS with virtual threads support)
- **Build Tool**: Gradle 8.14
- **Database**: PostgreSQL 16
- **Message Queue**: RabbitMQ 3.13
- **Containerization**: Docker with multi-stage builds

## Architecture

```
┌─────────────┐
│   YouTube   │
└──────┬──────┘
       │ Webhook
       ▼
┌─────────────────────────────────┐
│  Webhook Ingestion Service      │
│                                  │
│  ┌──────────────────────────┐   │
│  │  WebhookController       │   │
│  └───────────┬──────────────┘   │
│              ▼                   │
│  ┌──────────────────────────┐   │
│  │  WebhookIngestionService │   │
│  └───────────┬──────────────┘   │
│              ▼                   │
│  ┌──────────────────────────┐   │
│  │  PostgreSQL              │   │
│  └──────────────────────────┘   │
│              │                   │
│              ▼                   │
│  ┌──────────────────────────┐   │
│  │  RabbitMQ Publisher      │   │
│  └──────────────────────────┘   │
└─────────────────────────────────┘
       │
       ▼
┌─────────────────────┐
│  Downstream         │
│  Processing         │
└─────────────────────┘
```

## Prerequisites

- **Java 25** (JDK with virtual threads support)
- **Docker** (for containerized deployment)
- **PostgreSQL 16+** (for local development)
- **RabbitMQ 3.13+** (for local development)

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/youtube-webhook-ingestion.git
cd youtube-webhook-ingestion
```

### 2. Configure Environment Variables

Create a `.env` file or export the following environment variables:

```bash
# Database Configuration
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=adtracker
export DB_USERNAME=postgres
export DB_PASSWORD=your_password

# RabbitMQ Configuration
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=guest
export RABBITMQ_PASSWORD=guest
```

### 3. Setup Database Schema

```sql
-- Connect to PostgreSQL and create schema
CREATE SCHEMA IF NOT EXISTS webhook_ingestion;

-- Create webhook_events table
CREATE TABLE webhook_ingestion.webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id VARCHAR(50) NOT NULL,
    channel_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    source_ip VARCHAR(45),
    user_agent VARCHAR(500),
    processed BOOLEAN NOT NULL DEFAULT false,
    processing_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_webhook_events_video_id ON webhook_ingestion.webhook_events(video_id);
CREATE INDEX idx_webhook_events_channel_id ON webhook_ingestion.webhook_events(channel_id);
CREATE INDEX idx_webhook_events_processed ON webhook_ingestion.webhook_events(processed);
CREATE INDEX idx_webhook_events_created_at ON webhook_ingestion.webhook_events(created_at);
```

### 4. Build the Application

```bash
./gradlew clean build
```

### 5. Run the Application

#### Using Gradle

```bash
./gradlew bootRun
```

#### Using Java

```bash
java -jar build/libs/youtube-webhook-ingestion-0.0.1-SNAPSHOT.jar
```

#### Using Docker

```bash
# Build the Docker image
docker build -t youtube-webhook-ingestion:latest .

# Run the container
docker run -p 8080:8080 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=adtracker \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=your_password \
  -e RABBITMQ_HOST=host.docker.internal \
  youtube-webhook-ingestion:latest
```

## API Endpoints

### Webhook Ingestion

**POST** `/api/v1/webhooks/youtube`

Receive YouTube webhook notifications.

**Request Body:**
```json
{
  "videoId": "dQw4w9WgXcQ",
  "channelId": "UCuAXFkgsw1L7xaCfnd5JJOw",
  "eventType": "VIDEO_PUBLISHED",
  "content": "{\"title\":\"Sample Video\"}",
  "timestamp": 1699564800000
}
```

**Response (202 Accepted):**
```json
{
  "eventId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "ACCEPTED",
  "message": "Webhook event processed successfully",
  "receivedAt": "2025-11-12T10:30:00Z"
}
```

### Health Check

**GET** `/api/v1/webhooks/health`

Simple health check for the webhook receiver.

**Response (200 OK):**
```
Webhook receiver is healthy
```

### Actuator Endpoints

- **GET** `/actuator/health` - Application health status
- **GET** `/actuator/info` - Application information
- **GET** `/actuator/metrics` - Application metrics
- **GET** `/actuator/prometheus` - Prometheus-formatted metrics

## Configuration

### Application Properties

Key configuration properties in `application.yml`:

```yaml
spring:
  application:
    name: youtube-webhook-ingestion

webhook:
  ingestion:
    max-payload-size: 1048576  # 1MB
    validation:
      enabled: true
    queue:
      name: youtube.webhooks.raw
      exchange: youtube.webhooks
      routing-key: webhook.received
```

### Profiles

- **default**: Development profile with debug logging
- **prod**: Production profile with optimized settings

Activate a profile:
```bash
./gradlew bootRun --args='--spring.profiles.active=prod'
```

## Testing

### Run All Tests

```bash
./gradlew test
```

### Run Tests with Coverage

```bash
./gradlew test jacocoTestReport
```

Coverage reports are generated in `build/reports/jacoco/test/html/index.html`

### Verify Coverage Threshold

```bash
./gradlew jacocoTestCoverageVerification
```

The project enforces a minimum of 80% code coverage.

## CI/CD

### GitHub Actions Workflows

#### Test Workflow

Triggered on pull requests to `main`:
- Runs all unit tests
- Generates coverage reports
- Verifies >80% coverage threshold
- Comments coverage on PR

#### Docker Build Workflow

Triggered on push to `main`:
- Builds Docker image
- Pushes to GitHub Container Registry (GHCR)
- Tags with `latest` and commit SHA

## Docker Deployment

### Pull from GHCR

```bash
docker pull ghcr.io/yourusername/youtube-webhook-ingestion:latest
```

### Docker Compose

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: adtracker
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest

  webhook-ingestion:
    image: youtube-webhook-ingestion:latest
    ports:
      - "8080:8080"
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: adtracker
      DB_USERNAME: postgres
      DB_PASSWORD: postgres
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
    depends_on:
      - postgres
      - rabbitmq

volumes:
  postgres_data:
```

Run with:
```bash
docker-compose up
```

## Monitoring

### Prometheus Metrics

Scrape endpoint: `http://localhost:8080/actuator/prometheus`

Example Prometheus configuration:

```yaml
scrape_configs:
  - job_name: 'webhook-ingestion'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

### Health Checks

- **Liveness**: `/actuator/health/liveness`
- **Readiness**: `/actuator/health/readiness`

## Security

- CSRF disabled for webhook endpoints (stateless API)
- Actuator endpoints protected with authentication
- Webhook payload validation enabled by default
- Source IP and User-Agent logging for audit trail
- Secure-by-default configuration

## Troubleshooting

### Application Won't Start

1. Verify Java 25 is installed: `java -version`
2. Check database connectivity
3. Verify RabbitMQ is running
4. Review logs in `logs/application.log`

### Tests Failing

1. Ensure H2 database dependency is present
2. Check test resources configuration
3. Run with `--stacktrace` for detailed errors

### Database Connection Issues

1. Verify PostgreSQL is running
2. Check schema exists: `SELECT schema_name FROM information_schema.schemata;`
3. Verify credentials and permissions

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit changes: `git commit -am 'Add new feature'`
4. Push to branch: `git push origin feature/my-feature`
5. Submit a pull request

## License

This project is part of the Ad Tracker system. All rights reserved.

## Contact

For questions or support, please contact the development team or open an issue on GitHub.

## Related Projects

- [Ad Tracker Main Repository](https://github.com/yourusername/ad-tracker)
- [YouTube Data Processing Service](https://github.com/yourusername/youtube-data-processor)

---

**Built with Spring Boot 4.0.0-RC2 and Java 25 LTS**
