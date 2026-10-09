# OmniContext — Future Updates & Production Roadmap

This document outlines the architectural enhancements, security hardening, resilience patterns, and DevOps configurations required to make **OmniContext** production-ready.

---

## 1. Security & Access Control (High Priority)

### 1.1 API Authentication & Authorization
- **JWT / Session Authentication**: Protect web dashboard endpoints (`/api/contexts/**`, `/api/stats`, etc.) with user identity.
- **API Key Service**: Support persistent, revocable API keys (`X-OmniContext-Key`) for automated agents:
  - Browser Extension background script
  - Watcher CLI (`watcher-exe`)
  - External CI/CD pipelines
- **Role-Based Access Control (RBAC)**: Distinguish between context *Owners*, *Collaborators*, and *Public Readers*.

### 1.2 Secrets & Credential Management
- **Externalize Sensitive Properties**:
  - Remove plaintext credentials from `application.properties`.
  - Use environment variables with sensible defaults:
    - `MONGO_URI=${MONGO_URI:mongodb://localhost:27017/omnicontext_db}`
    - `OPENAI_API_KEY=${OPENAI_API_KEY:}`
    - `APP_SECRET_KEY=${APP_SECRET_KEY:}`
- **Support Secrets Managers**: Integrate AWS Secrets Manager, HashiCorp Vault, or Docker Secrets for production containers.

### 1.3 Rate Limiting & Abuse Prevention
- **Token Bucket Rate Limiter**:
  - Implement **Bucket4j** (or Redis-backed distributed rate limiter) on high-cost endpoints:
    - `POST /api/contexts/preview-compression`: 10 requests / minute per IP or API key.
    - `POST /api/contexts`: 30 requests / minute.
- **Request Payload Guardrails**:
  - Limit `rawContent` ingestion to a maximum of 50 KB / ~15,000 tokens per request to prevent memory denial-of-service (DOS) and prompt injection abuse.

### 1.4 Production CORS Policy
- Restrict `WebConfig.java` to explicit, authorized domains:
  - Production web domain (`https://app.omnicontext.io`)
  - Extension origins: `chrome-extension://<id>`
  - Disallow wildcard `*` with credentials enabled.

---

## 2. Backend Robustness & Observability

### 2.1 Spring Boot Actuator & Health Probes
- Add `spring-boot-starter-actuator` to `pom.xml`.
- Expose standardized liveness and readiness endpoints:
  - `/actuator/health/liveness` (Tomcat & JVM process state)
  - `/actuator/health/readiness` (MongoDB ping + AI provider readiness)
- Expose Prometheus metrics at `/actuator/prometheus` for Grafana dashboards (measuring distillation latency, compression ratio, error rates).

### 2.2 Global Exception Handling (RFC 7807)
- Add `@RestControllerAdvice` implementing Spring 6 `ProblemDetail`:
  - Consistent error schema across all clients (Extension, Watcher, Frontend).
  - Include unique error correlation IDs (`traceId`) in responses and server logs.
  - Graceful handling for `ResourceNotFoundException`, `RateLimitExceededException`, `AiProviderTimeoutException`.

### 2.3 MongoDB Production Hardening
- **Compound & Search Indexes**:
  - Add `@Indexed(unique = true)` on `shareSlug`.
  - Add compound index on `(project, createdAt)` for rapid dashboard filtering.
  - Add text search index on `(title, compressedContent, tags)` for full-text search.
- **Connection Pool Tuning**:
  - Set `min-pool-size=10`, `max-pool-size=50`, and socket timeouts in production properties.
  - Add replica set & retryable writes support for MongoDB Atlas (`retryWrites=true&w=majority`).

### 2.4 Asynchronous Background Distillation
- For large transcripts (>5,000 words), offload LLM distillation to background worker jobs:
  - Use Spring `@Async` thread pool or Redis/RabbitMQ queue.
  - Return `202 Accepted` with a task ID and stream progress via Server-Sent Events (SSE) or WebSockets.

---

## 3. Frontend Production Readiness (Angular)

### 3.1 Environment Separation
- Eliminate hardcoded `http://localhost:8085/api` from `context.service.ts`.
- Introduce Angular environment files:
  - `src/environments/environment.ts`: `apiUrl: 'http://localhost:8085/api'`
  - `src/environments/environment.prod.ts`: `apiUrl: '/api'` (relative path behind Nginx reverse proxy).

### 3.2 Global HTTP Error Interceptor
- Implement Angular `HttpInterceptorFn`:
  - Catch 401 Unauthorized (redirect to login).
  - Catch 429 Too Many Requests (show rate limit warning toast).
  - Catch 503 / 504 Gateway Timeouts (inform user that AI distillation fell back to rule-based NLP).

### 3.3 Production Build Optimization
- Production command: `ng build --configuration production`.
- Enable Gzip / Brotli compression.
- Implement HTML5 History routing fallback (`try_files $uri $uri/ /index.html;`) in Nginx.

---

## 4. Containerization & DevOps (Docker Architecture)

### 4.1 Deployment Architecture

```
                       +----------------------------------+
                       |   HTTPS Traffic (:443 / :80)     |
                       +----------------------------------+
                                        |
                                        v
                       +----------------------------------+
                       |      Nginx Reverse Proxy         |
                       +----------------------------------+
                           /                       \
                          /                         \
                 (Static Assets)                   (/api/*)
                        v                             v
           +-----------------------+     +-------------------------+
           | Angular SPA Container |     | Spring Boot API (:8085) |
           |     (Alpine Nginx)    |     |  (Java 21 Distroless)   |
           +-----------------------+     +-------------------------+
                                                      |
                                                      v
                                         +-------------------------+
                                         |    MongoDB Container    |
                                         |    (Persistent Volume)  |
                                         +-------------------------+
```

### 4.2 Multi-Stage Dockerfiles
- **Backend `Dockerfile`**:
  - Stage 1: Maven build (`mvn package -DskipTests`).
  - Stage 2: Eclipse Temurin 21 JRE Alpine image with non-root user execution.
- **Frontend `Dockerfile`**:
  - Stage 1: Node 20 build (`npm run build`).
  - Stage 2: Nginx Alpine container serving `/dist` assets.
- **`docker-compose.yml`**:
  - Orchestrates MongoDB, Backend, and Frontend containers with healthchecks and restart policies.

---

## 5. AI Engine Flexibility (LM Studio / Ollama / OpenAI)

- **Configurable Provider Enum**:
  - Allow explicit selection via `AI_PROVIDER=OPENAI` or `AI_PROVIDER=LOCAL_LLM` or `AI_PROVIDER=AUTO`.
- **Ollama / vLLM Support**:
  - Add native support for self-hosted GPU containers running Ollama or vLLM in cloud deployments where LM Studio is not installed locally.
