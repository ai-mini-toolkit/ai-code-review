---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
inputDocuments:
  - '_bmad-output/planning-artifacts/01-product-brief.md'
  - '_bmad-output/implementation-artifacts/02-PRD.md'
  - 'README.md'
workflowType: 'architecture'
project_name: 'ai-code-review'
user_name: 'ethan'
date: '2026-02-04'
lastStep: 8
status: 'complete'
completedAt: '2026-02-05'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements Classification:**
- **Webhook Integration Layer** (FR 1.1): Support for GitHub, GitLab, and AWS CodeCommit with platform-specific signature verification
- **Asynchronous Task Engine** (FR 1.2): Priority queue (High for PR/MR, Normal for Push), concurrent control, 3-retry mechanism with exponential backoff
- **Code Parsing Module** (FR 1.3): Git diff parsing, language detection, regex-based path filtering
- **AI Review Engine** (FR 1.4): 6-dimensional analysis (quality, security, performance, style, bugs, call graph) with multi-provider support
- **Reporting System** (FR 1.5): Structured JSON reports, issue severity classification (Error/Warning/Info), call graph visualization (Mermaid/PlantUML/SVG)
- **Threshold Interception** (FR 1.6): Configurable blocking rules, PR/MR status update via platform APIs
- **Notification System** (FR 1.7): Email, Git platform comments, extensible to DingTalk/Slack
- **Configuration Management** (FR 1.8): Multi-project management, AI model configuration with OpenAPI spec support, review templates, encrypted credential storage
- **Web UI** (FR 1.9): Project CRUD, review history, interactive call graph visualization, template management

**Non-Functional Requirements Driving Architecture:**
- **Performance Targets:**
  - Single review: <30s per 100 lines of code
  - Webhook response: <1s (async task creation)
  - Task processing latency: <5s (enqueue to execution start)
  - Concurrent tasks: ≥10 simultaneous
- **Reliability:**
  - System availability: ≥99%
  - Task retry: 3 attempts with exponential backoff (1s, 2s, 4s)
  - Data persistence: 100%
- **Security:**
  - Webhook signature verification mandatory (platform-specific algorithms)
  - HTTPS encryption for all communication
  - Encrypted storage for sensitive data (API keys, AWS credentials)
  - Basic authentication for Web UI (Phase 1)
- **Scalability:**
  - Horizontal scaling support (stateless API, worker pool)
  - New language support extensibility
  - New Git platform integration extensibility
  - New notification channel extensibility

### Scale & Complexity

- **Project Complexity:** High
- **Primary Technical Domain:** Full-stack (Backend REST API + Frontend SPA + Async Worker Engine)
- **Estimated Architectural Components:** 18-22 core components
  - Backend: 6 modules (API, Service, Repository, Integration, Worker, Common)
  - Frontend: 7 major views + shared components
  - Integration: 3 webhook verifiers + AI providers + notification clients
- **Integration Complexity:**
  - 3 Git platforms (GitHub HMAC-SHA256, GitLab Secret Token, AWS SigV4)
  - Multiple AI providers (OpenAI, Anthropic, custom OpenAPI)
  - Extensible notification channels

### Technical Constraints & Dependencies

**Mandatory Technology Stack:**
- **Backend:** Java 17+, Spring Boot 3.x, Spring Data JPA
- **Frontend:** Vue 3, Vite, Vue-Vben-Admin framework, Element Plus UI components
- **Database:** MySQL or PostgreSQL (PRD analysis strongly suggests PostgreSQL for JSONB support)
- **Message Queue:** RabbitMQ or Redis Queue (decision needed)
- **Caching:** Redis
- **Git Operations:** JGit library
- **Containerization:** Docker + Docker Compose

**External API Dependencies:**
- OpenAI API (GPT-4, GPT-3.5)
- Anthropic Claude API (Claude 3.5 Sonnet/Opus)
- Custom OpenAPI-compatible AI providers
- GitHub/GitLab/AWS CodeCommit APIs

### Cross-Cutting Concerns Identified

- **Authentication/Authorization:** Basic auth for Web UI, API key validation, future RBAC support
- **Error Handling:** Global exception handler, unified response format (`{success, data, error, timestamp}`)
- **Logging/Monitoring:** SLF4J + Logback structured logging, correlation IDs for tracing, Micrometer + Prometheus metrics
- **Configuration Management:** Multi-project isolation, environment variable support (`${VAR_NAME}`), encrypted secret storage
- **API Versioning:** `/api/v1/*` pattern with future version support
- **Data Validation:** Input validation at API controllers, business logic validation in services
- **Caching Strategy:** Redis caching for project configs, AI model configs, webhook secrets

## Starter Template Evaluation

### Primary Technology Domain

**Full-stack application** with:
- Backend REST API + Asynchronous Worker Engine (Java/Spring Boot)
- Frontend SPA (Vue 3/Vite)

Based on project requirements, we have a well-defined tech stack with established frameworks that make significant architectural decisions for us.

### Selected Starters & Their Architectural Foundations

#### Backend: Spring Boot 3.x Multi-Module Maven Project

**Rationale for Multi-Module Structure:**
Given the complexity of our system (18-22 components), a multi-module Maven project provides:
- Clear separation of concerns
- Independent module versioning and deployment
- Reduced coupling between layers
- Easier testing and maintenance
- Future microservices migration path if needed

**Recommended Module Structure:**
```
backend/
├── pom.xml                        (Parent POM, packaging: pom)
├── ai-code-review-api/            (REST API layer, Spring Boot Application)
├── ai-code-review-service/        (Business logic layer)
├── ai-code-review-repository/     (Data access layer, JPA entities)
├── ai-code-review-integration/    (External integrations: Git, AI, Notifications)
├── ai-code-review-worker/         (Async task processor)
└── ai-code-review-common/         (Shared utilities, DTOs, constants)
```

**Architectural Decisions Provided:**

**Dependency Management:**
- Parent POM manages all dependency versions
- Only `ai-code-review-api` declares `spring-boot-starter-parent`
- Other modules depend on specific Spring dependencies (not full starters)
- Prevents dependency bloat in library modules

**Package Organization:**
- Base package: `com.aicodereview`
- Subpackages: `com.aicodereview.api`, `com.aicodereview.service`, etc.
- Ensures Spring component scanning works across modules

**Build & Configuration:**
- Maven for build orchestration
- Spring Boot Maven plugin only in `api` module (generates executable JAR)
- Profile-based configuration: `application-dev.yml`, `application-prod.yml`
- Externalized configuration via environment variables

**Testing Infrastructure:**
- JUnit 5 for unit tests
- Spring Boot Test for integration tests
- Separate test JARs for shared test utilities
- TestContainers for database integration tests

**Development Experience:**
- Spring Boot DevTools for hot reload
- Actuator endpoints for monitoring (`/actuator/health`, `/actuator/metrics`)
- Embedded Tomcat for easy local development

#### Frontend: Vue 3 + Vite + Vue-Vben-Admin

**Rationale for Vue-Vben-Admin:**
Vue-Vben-Admin is a production-ready admin template that provides:
- Enterprise-grade UI components (Element Plus)
- Built-in routing, state management, and API client patterns
- Theme system and responsive layout
- Reduced initial development time

**Initialization Command:**
```bash
# Clone Vue-Vben-Admin template
git clone https://github.com/vbenjs/vue-vben-admin.git frontend
cd frontend

# Install dependencies (requires pnpm)
npm i -g corepack
pnpm install

# Start development server
pnpm dev
```

**Prerequisites:**
- Node.js: Version 20.15.0 or higher
- Package Manager: pnpm only (project-specific requirement)

**Architectural Decisions Provided:**

**Project Structure:**
```
frontend/src/
├── views/          # Page components (feature-based routing)
├── components/     # Reusable UI components
├── api/            # API client modules (Axios-based)
├── stores/         # Pinia state stores
├── router/         # Vue Router configuration
├── utils/          # Utility functions
├── types/          # TypeScript type definitions
└── layouts/        # Layout components (from Vue-Vben-Admin)
```

**UI & Styling:**
- Element Plus component library (pre-integrated)
- Built-in responsive layout system
- Dark mode support out-of-the-box
- CSS/SCSS with scoped styles

**State Management:**
- Pinia for reactive state management
- Modular store pattern (per feature)
- Composition API style

**API Communication:**
- Axios HTTP client pre-configured
- Request/response interceptors for auth headers
- Centralized error handling
- API module pattern (`/api/project.ts`, `/api/review.ts`)

**Build Tooling:**
- Vite for fast development builds (<100ms HMR)
- TypeScript for type safety
- ESLint + Prettier for code quality
- Automatic code splitting by route

**Development Experience:**
- Hot Module Replacement (HMR) via Vite
- TypeScript IntelliSense
- Mock API support for isolated development
- Source maps for debugging

### Architectural Implications

**Backend Multi-Module Benefits:**
- Worker module can be deployed independently for horizontal scaling
- Integration module can be mocked for testing without external dependencies
- Clear dependency graph prevents circular dependencies
- Future-proof for microservices migration

**Frontend Monorepo Structure:**
- All frontend code in single repository
- Shared component library across views
- Consistent API client patterns
- Single build and deployment pipeline

**Cross-Cutting Decisions Made by Starters:**
- **Configuration Management:** Spring profiles + Vue environment files (`.env.development`, `.env.production`)
- **Logging:** SLF4J (backend) + Console (frontend, upgradeable to structured logging)
- **Error Handling:** Spring global exception handler + Axios interceptors
- **Testing:** JUnit 5 + Vitest (Vue-Vben-Admin includes Vitest)

**Note:** Project initialization should be the first implementation task:
1. Create Spring Boot multi-module structure
2. Initialize Vue-Vben-Admin frontend
3. Configure cross-origin resource sharing (CORS)
4. Set up development environment (Docker Compose for PostgreSQL + Redis)

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
1. Database: PostgreSQL (required for JSONB operations)
2. Message Queue: Redis Queue (async task processing)
3. AI Provider Abstraction: Strategy + Factory pattern
4. Webhook Verification: Chain of Responsibility pattern
5. API Response Format: Standardized JSON structure

**Important Decisions (Shape Architecture):**
1. Authentication: JWT with Spring Security 6
2. Call Graph Analysis: JavaParser (Phase 1)
3. Horizontal Scaling Strategy: Stateless design
4. Error Handling: Global exception handlers
5. Caching: Redis for configurations

**Deferred Decisions (Post-MVP):**
1. Advanced RBAC (Phase 1: Basic Auth)
2. Tree-sitter multi-language support (Phase 2)
3. RabbitMQ migration (if Redis Queue insufficient)
4. Advanced monitoring (Phase 1: Basic Micrometer)

### Data Architecture

#### Decision 1.1: Database - PostgreSQL

**Choice:** PostgreSQL 18.x (latest stable: 18.1)

**Rationale:**
- PRD extensively uses JSON columns (6+ tables with JSON/JSONB fields):
  - `project.thresholds`, `project.notifications`, `project.include_patterns`, `project.exclude_patterns`
  - `review_result.issues`, `review_result.call_graph`
  - `ai_model_config.openapi_spec`, `ai_model_config.category`
- PostgreSQL JSONB provides:
  - Efficient binary storage
  - GIN indexing for fast JSON queries
  - Rich JSON operators (`->`, `->>`, `@>`, `?`)
  - JSON aggregation functions
- Required queries: Search issues by severity, filter by category, query call graph relationships
- Superior query optimizer for complex analysis queries
- Better long-term scalability

**Alternative Considered:** MySQL 8.x
- Rejected: JSON support weaker, no GIN indexing, less efficient JSONB operations

**Dependencies:**
- Spring Data JPA with Hibernate 6.x
- PostgreSQL JDBC Driver (org.postgresql:postgresql:42.7.x)
- Flyway for database migrations

**Configuration:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ai_code_review
    driver-class-name: org.postgresql.Driver
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate  # Use Flyway for migrations
```

#### Decision 1.2: Message Queue - Redis Queue

**Choice:** Redis 8.0 (latest stable) with Redis Sorted Sets + Lists

**Rationale (Phase 1 MVP):**
- Redis already required for caching (project configs, AI model configs, webhook secrets)
- Eliminates additional infrastructure component (RabbitMQ)
- Sufficient for Phase 1 requirements:
  - Priority queue via Sorted Sets (score = priority + timestamp)
  - FIFO processing via Lists (LPUSH/BRPOP)
  - Task retry via re-enqueue with incremented retry count
- Lower operational complexity
- Horizontal scaling: Multiple workers can BRPOP from same queue
- Performance: <5ms task processing latency (meets NFR)

**Implementation:**
```java
// Priority Queue: ZADD task:queue {priority_score} {task_id}
// Worker Pool: BRPOP task:queue 0 (blocking pop)
// Retry: Re-ZADD with lower priority score
```

**Migration Path (Phase 2):**
- If scale exceeds Redis capacity (>10k tasks/sec), migrate to RabbitMQ
- Current design: Queue abstraction interface allows transparent swap

**Alternative Considered:** RabbitMQ
- Deferred: Overkill for Phase 1 (<100 concurrent tasks), adds operational overhead

**Dependencies:**
- Spring Data Redis (org.springframework.boot:spring-boot-starter-data-redis)
- Lettuce client (default in Spring Boot)
- Redis Docker image: redis:8.0

#### Decision 1.3: Data Migration Strategy

**Choice:** Flyway for Database Migrations

**Rationale:**
- Version-controlled SQL migrations
- Team collaboration safety (prevents schema conflicts)
- Rollback support
- Integrates seamlessly with Spring Boot

**Migration Naming:**
```
V1__create_project_table.sql
V2__create_review_task_table.sql
V3__add_aws_codecommit_support.sql
```

**Configuration:**
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

#### Decision 1.4: Data Validation Strategy

**Choice:** Jakarta Bean Validation + Custom Validators

**Approach:**
- **Input Validation:** At API layer using `@Valid` annotations
- **Business Logic Validation:** In Service layer
- **Database Constraints:** NOT NULL, UNIQUE, FOREIGN KEY at DB level

**Example:**
```java
public class ProjectCreateRequest {
    @NotBlank(message = "Project name is required")
    @Size(max = 255)
    private String name;

    @NotNull
    @Pattern(regexp = "^(github|gitlab|aws-codecommit)$")
    private String platform;

    @ValidWebhookSecret  // Custom validator
    private String webhookSecret;
}
```

#### Decision 1.5: Caching Strategy

**Choice:** Redis with Layered Caching

**Cache Hierarchy:**
1. **L1 Cache:** Spring @Cacheable in-memory (Caffeine) for hot data
2. **L2 Cache:** Redis for shared cache across instances

**Cached Data:**
- Project configurations (TTL: 10 minutes)
- AI model configurations (TTL: 30 minutes)
- Webhook secrets (TTL: 1 hour, encrypted)
- Review templates (TTL: 1 hour)

**Cache Invalidation:**
- On update: Explicit eviction via `@CacheEvict`
- On delete: Cascade eviction of related entities

**Configuration:**
```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeValuesWith(RedisSerializationContext
                .SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        return RedisCacheManager.builder(factory).cacheDefaults(config).build();
    }
}
```

### Authentication & Security

#### Decision 2.1: Authentication Method

**Choice:** JWT (JSON Web Tokens) with Spring Security 6

**Rationale:**
- Stateless authentication (supports horizontal scaling)
- Self-contained tokens (no server-side session storage)
- Suitable for SPA architecture (Vue frontend)
- Industry standard for API authentication

**Implementation Approach:**
- **Token Generation:** After successful login, issue JWT with user claims
- **Token Storage:** HttpOnly cookies (XSS protection) + SameSite (CSRF protection)
- **Token Validation:** JWT Filter validates signature and expiration on each request
- **Token Expiration:** Access token (1 hour), Refresh token (7 days)

**Security Best Practices:**
- 256-bit secret key (stored in environment variable)
- HS256 signing algorithm
- Short-lived access tokens
- Refresh token rotation
- Token blacklist for logout (Redis Set with TTL)

**Configuration:**
```yaml
jwt:
  secret: ${JWT_SECRET}  # 256-bit key from environment
  access-token-expiration: 3600000  # 1 hour
  refresh-token-expiration: 604800000  # 7 days
```

**Phase 1 Scope:**
- Basic authentication (username/password)
- JWT issuance and validation
- Protected API endpoints

**Phase 2 Extensions:**
- OAuth2 integration (GitHub, Google)
- Role-Based Access Control (RBAC)
- Multi-factor authentication (MFA)

**Alternative Considered:** Session-based auth
- Rejected: Not suitable for horizontal scaling, requires sticky sessions or shared session store

**Dependencies:**
- Spring Security 6 (org.springframework.boot:spring-boot-starter-security)
- JJWT library (io.jsonwebtoken:jjwt-api:0.12.x)

#### Decision 2.2: Webhook Security

**Choice:** Chain of Responsibility Pattern for Platform-Specific Verification

**Rationale:**
- Each platform has different signature mechanisms:
  - **GitHub:** HMAC-SHA256 with X-Hub-Signature-256 header
  - **GitLab:** Secret token comparison
  - **AWS CodeCommit:** AWS Signature Version 4
- Chain pattern allows:
  - Extensibility (add new platforms without modifying existing code)
  - Single responsibility (each verifier handles one platform)
  - Clear failure paths

**Implementation:**
```java
public interface WebhookVerifier {
    boolean supports(String platform);
    boolean verify(WebhookRequest request);
}

@Component
public class GitHubWebhookVerifier implements WebhookVerifier {
    public boolean verify(WebhookRequest request) {
        String signature = request.getHeader("X-Hub-Signature-256");
        String payload = request.getBody();
        String secret = projectRepository.findSecretByRepo(request.getRepo());
        return verifyHmacSha256(payload, secret, signature);
    }
}

// Chain configuration
@Component
public class WebhookVerifierChain {
    private List<WebhookVerifier> verifiers;

    public boolean verify(WebhookRequest request) {
        return verifiers.stream()
            .filter(v -> v.supports(request.getPlatform()))
            .findFirst()
            .map(v -> v.verify(request))
            .orElseThrow(() -> new UnsupportedPlatformException());
    }
}
```

**Security Requirements:**
- Signature verification MUST occur before payload parsing
- Use constant-time comparison (prevents timing attacks)
- Reject requests with invalid or missing signatures (HTTP 401)

#### Decision 2.3: Sensitive Data Encryption

**Choice:** AES-256-GCM for At-Rest Encryption

**Encrypted Fields:**
- AI model API keys (`ai_model_config.api_key_encrypted`)
- AWS credentials (`project.aws_secret_access_key`)
- Webhook secrets (`project.webhook_secret`)

**Approach:**
- Master key stored in environment variable or key management service (AWS KMS, Azure Key Vault)
- Per-field encryption with unique initialization vectors (IVs)
- JPA Attribute Converters for transparent encryption/decryption

**Implementation:**
```java
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    @Override
    public String convertToDatabaseColumn(String plaintext) {
        return aesGcmEncrypt(plaintext, getMasterKey());
    }

    @Override
    public String convertToEntityAttribute(String ciphertext) {
        return aesGcmDecrypt(ciphertext, getMasterKey());
    }
}

@Entity
public class AIModelConfig {
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "api_key_encrypted")
    private String apiKey;
}
```

**Key Management:**
- Development: Environment variable `ENCRYPTION_MASTER_KEY`
- Production: AWS KMS or Azure Key Vault (Phase 2)

### API & Communication Patterns

#### Decision 3.1: API Design Pattern

**Choice:** RESTful API with Resource-Oriented Design

**Rationale:**
- Industry standard for web APIs
- Clear semantics (GET, POST, PUT, DELETE)
- Cacheable responses (GET requests)
- Stateless (supports horizontal scaling)

**API Structure:**
```
/api/v1/projects              # Project resources
/api/v1/tasks                 # Review task resources
/api/v1/results               # Review result resources
/api/v1/ai-models             # AI model config resources
/api/v1/templates             # Template resources
/api/webhook/github           # Webhook endpoints (not versioned, external)
/api/webhook/gitlab
/api/webhook/aws-codecommit
```

**Versioning:**
- URL versioning: `/api/v1/*`, `/api/v2/*`
- Reason: Simple, explicit, cache-friendly
- Alternative (rejected): Header versioning (less discoverable)

#### Decision 3.2: API Response Format (Standardized)

**Choice:** Unified Response Envelope

**Structure:**
```json
{
  "success": true,
  "data": { /* actual response payload */ },
  "error": null,
  "timestamp": "2026-02-05T10:00:00Z"
}
```

**Error Response:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "WEBHOOK_VERIFICATION_FAILED",
    "message": "Invalid webhook signature",
    "details": { "platform": "github", "repo": "owner/repo" }
  },
  "timestamp": "2026-02-05T10:00:00Z"
}
```

**HTTP Status Codes:**
- `200 OK` - Success
- `201 Created` - Resource created
- `400 Bad Request` - Client error (validation failure)
- `401 Unauthorized` - Authentication failure
- `403 Forbidden` - Authorization failure
- `404 Not Found` - Resource not found
- `500 Internal Server Error` - Server error
- `503 Service Unavailable` - AI API unavailable

**Rationale:**
- Consistent structure across all endpoints
- Clear success/failure indication
- Error details for debugging
- Timestamp for audit trails

#### Decision 3.3: Error Handling Strategy

**Choice:** Global Exception Handler with Typed Exceptions

**Exception Hierarchy:**
```java
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
}

public enum ErrorCode {
    WEBHOOK_VERIFICATION_FAILED("WH001", 401),
    AI_PROVIDER_UNAVAILABLE("AI001", 503),
    TASK_PROCESSING_ERROR("TASK001", 500),
    VALIDATION_ERROR("VAL001", 400);

    private final String code;
    private final int httpStatus;
}

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse> handleBusinessException(BusinessException ex) {
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(ApiResponse.error(ex));
    }
}
```

**Rationale:**
- Centralized error handling (DRY principle)
- Consistent error responses
- Typed exceptions for specific error scenarios
- Separates business errors from system errors

#### Decision 3.4: AI Provider Abstraction

**Choice:** Strategy Pattern + Factory Pattern

**Rationale:**
- Multiple AI providers (OpenAI, Anthropic, custom OpenAPI)
- Need for provider-specific behavior encapsulation
- Runtime provider selection based on configuration
- Fallback/failover support

**Interface Design:**
```java
public interface AIProvider {
    String getProviderId();  // "openai", "anthropic", "custom"
    ReviewResult analyze(CodeContext context);
    boolean isAvailable();
    int getPriority();  // For routing decisions
}

@Component
public class OpenAIProvider implements AIProvider {
    @Override
    public ReviewResult analyze(CodeContext context) {
        // Call OpenAI API with context (code diff, language, template)
        // Parse response into ReviewResult
    }
}

@Component
public class AIProviderFactory {
    private List<AIProvider> providers;

    public AIProvider getProvider(String category) {
        return providers.stream()
            .filter(p -> supportsCategory(p, category))
            .filter(AIProvider::isAvailable)
            .min(Comparator.comparing(AIProvider::getPriority))
            .orElseThrow(() -> new AIProviderUnavailableException());
    }
}
```

**Routing Strategy:**
- **Security reviews:** Use "strict" model (e.g., Claude Opus)
- **General reviews:** Use "fast" model (e.g., GPT-4)
- **Performance reviews:** Use specialized model

**Fallback Chain:**
1. Try primary model configured for category
2. If unavailable/rate-limited, try secondary model
3. If all fail, mark task as failed with retry

**Configuration:**
```yaml
ai:
  providers:
    - id: openai-primary
      provider: openai
      model: gpt-4
      category: [general, quality, style]
      priority: 1
    - id: claude-security
      provider: anthropic
      model: claude-3-5-sonnet
      category: [security, performance]
      priority: 1
    - id: openai-fallback
      provider: openai
      model: gpt-3.5-turbo
      category: [general]
      priority: 2
```

### Frontend Architecture

#### Decision 4.1: State Management

**Choice:** Pinia (Vue 3 Recommended)

**Rationale:**
- Official Vue recommendation (replaces Vuex)
- Composition API support
- TypeScript-first design
- Smaller bundle size
- Simpler API (no mutations, actions directly modify state)

**Store Organization:**
```typescript
// stores/project.ts
export const useProjectStore = defineStore('project', () => {
  const projects = ref<Project[]>([])
  const currentProject = ref<Project | null>(null)

  async function fetchProjects() {
    projects.value = await projectApi.getProjects()
  }

  return { projects, currentProject, fetchProjects }
})
```

**Store Modules:**
- `useProjectStore` - Project management
- `useReviewStore` - Review history and details
- `useModelStore` - AI model configurations
- `useAuthStore` - Authentication state

#### Decision 4.2: Component Architecture

**Choice:** Composition API + TypeScript

**Rationale:**
- Vue 3 best practice
- Better TypeScript inference
- More reusable logic (composables)
- Clearer component organization

**Component Structure:**
```vue
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useProjectStore } from '@/stores/project'

const projectStore = useProjectStore()
const projects = computed(() => projectStore.projects)

async function handleCreate(data: ProjectCreateRequest) {
  await projectStore.createProject(data)
}
</script>
```

**Component Organization:**
- **Views:** Page-level components (`/views/project/ProjectList.vue`)
- **Components:** Reusable UI components (`/components/review/IssueList.vue`)
- **Composables:** Shared logic (`/composables/useReviews.ts`)

#### Decision 4.3: API Client Pattern

**Choice:** Axios with Centralized API Modules

**Structure:**
```typescript
// api/project.ts
export const projectApi = {
  getProjects: () => request.get<Project[]>('/api/v1/projects'),
  getProject: (id: string) => request.get<Project>(`/api/v1/projects/${id}`),
  createProject: (data: ProjectCreateRequest) =>
    request.post<Project>('/api/v1/projects', data),
}

// utils/request.ts
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
})

// Request interceptor: Add JWT token
request.interceptors.request.use(config => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Response interceptor: Handle errors
request.interceptors.response.use(
  response => response.data,  // Return ApiResponse.data
  error => {
    if (error.response?.status === 401) {
      // Redirect to login
    }
    return Promise.reject(error)
  }
)
```

### Infrastructure & Deployment

#### Decision 5.1: Containerization Strategy

**Choice:** Docker + Docker Compose

**Rationale:**
- Environment consistency (dev = prod)
- Easy local development setup
- Simplified deployment
- Service orchestration (backend + frontend + DB + Redis)

**Docker Compose Structure:**
```yaml
version: '3.8'
services:
  postgres:
    image: postgres:18.1
    environment:
      POSTGRES_DB: ai_code_review
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:8.0
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data

  backend:
    build: ./backend
    depends_on:
      - postgres
      - redis
    environment:
      SPRING_PROFILES_ACTIVE: ${PROFILE:-dev}
      DATABASE_URL: jdbc:postgresql://postgres:5432/ai_code_review
      REDIS_HOST: redis
    ports:
      - "8080:8080"

  frontend:
    build: ./frontend
    depends_on:
      - backend
    environment:
      VITE_API_BASE_URL: http://backend:8080
    ports:
      - "5173:5173"
```

#### Decision 5.2: Horizontal Scaling Strategy

**Choice:** Stateless Design + Shared State in Redis

**Backend Scaling:**
- **Stateless API:** No session state in application (JWT tokens are self-contained)
- **Shared Queue:** Multiple workers pull from Redis queue
- **Distributed Locking:** Redis locks prevent duplicate task processing
- **Database Connection Pooling:** HikariCP with per-instance pool

**Scaling Triggers:**
- CPU > 70% sustained: Scale API instances
- Queue depth > 100: Scale worker instances
- DB connections exhausted: Scale database (read replicas)

**Implementation:**
```java
@Service
public class TaskWorker {
    @Scheduled(fixedDelay = 1000)
    public void processTask() {
        String taskId = redisQueue.blockingPop("task:queue");
        if (taskId == null) return;

        // Distributed lock prevents duplicate processing
        boolean locked = redisLock.tryLock("task:" + taskId, 300);
        if (!locked) return;

        try {
            processTaskLogic(taskId);
        } finally {
            redisLock.unlock("task:" + taskId);
        }
    }
}
```

#### Decision 5.3: Monitoring & Observability

**Choice:** Micrometer + Prometheus (Phase 1)

**Metrics:**
- **Business Metrics:**
  - Review completion time (histogram)
  - AI provider success rate (counter)
  - Queue depth (gauge)
  - Active tasks (gauge)
- **System Metrics:**
  - HTTP request latency (histogram)
  - Database query time (histogram)
  - Redis operation time (histogram)
  - JVM memory usage (gauge)

**Implementation:**
```java
@Component
public class ReviewMetrics {
    private final MeterRegistry registry;
    private final Timer reviewTimer;

    public ReviewMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.reviewTimer = Timer.builder("review.completion.time")
            .description("Time to complete code review")
            .register(registry);
    }

    public void recordReview(Runnable review) {
        reviewTimer.record(review);
    }
}
```

**Logging:**
- **Structured Logging:** JSON format with Logback
- **Correlation IDs:** MDC for request tracing across services
- **Log Levels:** INFO (default), DEBUG (dev), ERROR (always)

#### Decision 5.4: Call Graph Analysis

**Choice:** JavaParser (Phase 1) → Tree-sitter (Phase 2)

**Phase 1 Rationale:**
- Focus on Java language (primary target)
- JavaParser provides:
  - Full AST parsing
  - Symbol resolution
  - Method call tracking
  - Type inference
- Simpler implementation for MVP
- Proven library (used by many IDEs)

**Implementation:**
```java
@Service
public class CallGraphAnalyzer {
    public CallGraph analyze(List<String> javaFiles) {
        CallGraph graph = new CallGraph();

        for (String file : javaFiles) {
            CompilationUnit cu = StaticJavaParser.parse(file);

            cu.findAll(MethodDeclaration.class).forEach(method -> {
                String methodName = method.getNameAsString();
                graph.addNode(methodName);

                method.findAll(MethodCallExpr.class).forEach(call -> {
                    String calledMethod = call.getNameAsString();
                    graph.addEdge(methodName, calledMethod);
                });
            });
        }

        return graph;
    }
}
```

**Output Format:**
- Mermaid syntax for visualization in frontend
- JSON format for storage in database

**Phase 2 Enhancement:**
- Tree-sitter for multi-language support (Python, TypeScript, Go, etc.)
- More sophisticated analysis (cross-file dependencies, package-level graphs)

**Alternative Considered:** ANTLR
- Rejected: More complex, requires custom grammar maintenance

### Decision Impact Analysis

**Implementation Sequence:**
1. **Foundation:** PostgreSQL + Redis setup
2. **Backend Skeleton:** Spring Boot multi-module structure
3. **Authentication:** JWT + Spring Security
4. **Webhook Integration:** Verifier chain + task creation
5. **AI Provider:** Strategy pattern + OpenAI integration
6. **Worker Engine:** Redis queue consumer + retry logic
7. **Review Logic:** Call graph analyzer + AI review orchestration
8. **Frontend:** Vue-Vben-Admin + API integration
9. **Deployment:** Docker Compose configuration

**Cross-Component Dependencies:**
- **Authentication** → All API endpoints
- **Database** → All persistence operations
- **Redis Queue** → Worker scaling
- **AI Provider** → Review quality
- **Webhook Verification** → Security posture

## Implementation Patterns & Consistency Rules

### Pattern Categories Overview

**Total Conflict Points Identified:** 32 areas where AI agents could make different implementation choices

These patterns are **MANDATORY** for all AI agents to ensure seamless code integration.

### Naming Patterns (MANDATORY)

#### Database Naming Conventions

**Table Naming:**
- **Format:** `snake_case` (all lowercase, words separated by underscores)
- **Plurality:** Singular for entity tables
- **Examples:**
  ```sql
  ✅ CORRECT: project, review_task, review_result, ai_model_config
  ❌ WRONG: Project, ReviewTask, projects, ReviewResults
  ```

**Column Naming:**
- **Format:** `snake_case`
- **Primary Key:** Always `id` (not `{table}_id`)
- **Foreign Key:** `{referenced_table}_id`
- **Boolean Columns:** Prefix with `is_` or `has_`
- **Timestamp Columns:** Use `_at` suffix
- **Examples:**
  ```sql
  ✅ CORRECT: project_id, commit_hash, is_enabled, created_at, updated_at
  ❌ WRONG: projectId, commitHash, enabled, createdDate, updatedDate
  ```

**Index Naming:**
- **Format:** `idx_{table}_{columns}`
- **Unique Index:** `uk_{table}_{columns}`
- **Examples:**
  ```sql
  ✅ CORRECT: idx_review_task_project_id, uk_project_repo_url
  ❌ WRONG: review_task_project_id_idx, project_repo_url_unique
  ```

**Constraint Naming:**
- **Foreign Key:** `fk_{table}_{referenced_table}`
- **Check:** `ck_{table}_{column}_{constraint}`
- **Examples:**
  ```sql
  ✅ CORRECT: fk_review_task_project, ck_project_platform_valid
  ```

#### API Naming Conventions

**REST Endpoint Naming:**
- **Format:** `/api/v{version}/{resources}` (plural resources)
- **Versioning:** URL-based versioning (v1, v2)
- **Resource Naming:** Lowercase, hyphen-separated for multi-word resources
- **Examples:**
  ```
  ✅ CORRECT:
  /api/v1/projects
  /api/v1/review-tasks
  /api/v1/ai-models
  /api/v1/projects/{id}
  /api/v1/tasks/{id}/results

  ❌ WRONG:
  /api/projects (no version)
  /api/v1/project (singular)
  /api/v1/reviewTasks (camelCase)
  /api/v1/ai_models (underscore)
  ```

**Webhook Endpoints:**
- **Format:** `/api/webhook/{platform}` (NOT versioned, external stability)
- **Examples:**
  ```
  ✅ CORRECT: /api/webhook/github, /api/webhook/gitlab, /api/webhook/aws-codecommit
  ```

**Query Parameter Naming:**
- **Format:** `camelCase`
- **Examples:**
  ```
  ✅ CORRECT: ?projectId=123&status=completed&page=1&pageSize=20
  ❌ WRONG: ?project_id=123&Status=completed
  ```

**Path Parameter Format:**
- **Format:** `{parameterName}` (curly braces, camelCase)
- **Examples:**
  ```
  ✅ CORRECT: /api/v1/projects/{projectId}/tasks/{taskId}
  ❌ WRONG: /api/v1/projects/:projectId (colon syntax)
  ```

**HTTP Header Naming:**
- **Custom Headers:** Prefix with `X-` (e.g., `X-Correlation-Id`)
- **Examples:**
  ```
  ✅ CORRECT: X-Correlation-Id, X-Request-Id, Authorization
  ❌ WRONG: CorrelationId, request-id
  ```

#### Java Code Naming Conventions

**Class Naming:**
- **Format:** `PascalCase`
- **Suffixes:**
  - Controllers: `*Controller` (e.g., `ProjectController`)
  - Services: `*Service` (e.g., `ReviewService`)
  - Repositories: `*Repository` (e.g., `ProjectRepository`)
  - Entities: No suffix (e.g., `Project`, `ReviewTask`)
  - DTOs: `*Request`, `*Response`, `*DTO` (e.g., `ProjectCreateRequest`)
  - Exceptions: `*Exception` (e.g., `WebhookVerificationException`)
  - Interfaces: No `I` prefix (e.g., `AIProvider`, not `IAIProvider`)
- **Examples:**
  ```java
  ✅ CORRECT: ProjectController, ReviewService, ProjectRepository,
             WebhookVerifier, AIProvider
  ❌ WRONG: projectController, ReviewServiceImpl (no Impl suffix for single impl),
           IWebhookVerifier (no I prefix)
  ```

**Method Naming:**
- **Format:** `camelCase`
- **Conventions:**
  - CRUD operations: `create`, `update`, `delete`, `findById`, `findAll`
  - Boolean methods: `is*`, `has*`, `can*`
  - Converters: `to*`, `from*`
- **Examples:**
  ```java
  ✅ CORRECT: createProject, findById, isEnabled, verifySignature, toEntity
  ❌ WRONG: CreateProject, find_by_id, getIsEnabled, verify_signature
  ```

**Variable Naming:**
- **Format:** `camelCase`
- **Examples:**
  ```java
  ✅ CORRECT: projectId, commitHash, reviewTask, aiProvider
  ❌ WRONG: project_id, commit_hash, ReviewTask, AI_provider
  ```

**Constant Naming:**
- **Format:** `UPPER_SNAKE_CASE`
- **Examples:**
  ```java
  ✅ CORRECT: MAX_RETRY_COUNT, DEFAULT_TIMEOUT_MS, API_VERSION
  ❌ WRONG: maxRetryCount, Max_Retry_Count, API_version
  ```

**Package Naming:**
- **Format:** All lowercase, no underscores
- **Structure:** `com.aicodereview.{module}.{layer}.{feature}`
- **Examples:**
  ```java
  ✅ CORRECT:
  com.aicodereview.api.controller
  com.aicodereview.service.review
  com.aicodereview.repository.entity
  com.aicodereview.integration.webhook

  ❌ WRONG:
  com.aiCodeReview (camelCase)
  com.aicodereview.API (uppercase)
  com.ai_code_review (underscore)
  ```

#### Vue/TypeScript Naming Conventions

**Component Naming:**
- **Format:** `PascalCase` (both file and export name)
- **Multi-word:** Always use multi-word names (avoid single-word)
- **Examples:**
  ```typescript
  ✅ CORRECT: ProjectList.vue, ReviewDetail.vue, CallGraphChart.vue
  ❌ WRONG: project-list.vue, reviewDetail.vue, Project.vue (single word)
  ```

**File Naming:**
- **Components:** `PascalCase.vue` (match component name)
- **Composables:** `use*.ts` (e.g., `useReviews.ts`)
- **API Modules:** `camelCase.ts` (e.g., `project.ts`, `review.ts`)
- **Utilities:** `camelCase.ts`
- **Types:** `camelCase.ts` or `*.types.ts`
- **Examples:**
  ```
  ✅ CORRECT:
  ProjectList.vue
  useProjects.ts
  project.ts (API module)
  formatDate.ts (utility)
  project.types.ts

  ❌ WRONG:
  project-list.vue
  ProjectsComposable.ts
  ProjectAPI.ts
  format_date.ts
  ```

**Function/Variable Naming:**
- **Format:** `camelCase`
- **Composables:** Prefix with `use`
- **Event Handlers:** Prefix with `handle` or `on`
- **Examples:**
  ```typescript
  ✅ CORRECT: fetchProjects, useProjectStore, handleCreate, onSubmit
  ❌ WRONG: FetchProjects, projectStore, create_handler, OnSubmit
  ```

**Constant Naming:**
- **Format:** `UPPER_SNAKE_CASE` for true constants
- **Format:** `camelCase` for configuration objects
- **Examples:**
  ```typescript
  ✅ CORRECT:
  const MAX_RETRY_COUNT = 3
  const API_BASE_URL = import.meta.env.VITE_API_BASE_URL
  const routeConfig = { path: '/projects', name: 'ProjectList' }
  ```

### Structure Patterns (MANDATORY)

#### Backend Multi-Module Maven Structure

**Module Organization:**
```
backend/
├── pom.xml                                 # Parent POM (packaging: pom)
├── ai-code-review-api/                     # REST API Layer
│   ├── src/main/java/com/aicodereview/api/
│   │   ├── controller/                     # REST Controllers
│   │   ├── dto/                            # Request/Response DTOs
│   │   ├── exception/                      # Global Exception Handlers
│   │   └── config/                         # API-specific Config
│   ├── src/main/resources/
│   │   ├── application.yml                 # Main config
│   │   ├── application-dev.yml             # Dev profile
│   │   └── application-prod.yml            # Prod profile
│   └── pom.xml
├── ai-code-review-service/                 # Business Logic Layer
│   ├── src/main/java/com/aicodereview/service/
│   │   ├── service/                        # Business Services
│   │   ├── domain/                         # Domain Models
│   │   └── strategy/                       # Strategy Pattern Implementations
│   └── pom.xml
├── ai-code-review-repository/              # Data Access Layer
│   ├── src/main/java/com/aicodereview/repository/
│   │   ├── entity/                         # JPA Entities
│   │   ├── repository/                     # Spring Data Repositories
│   │   └── mapper/                         # Entity-Domain Mappers
│   ├── src/main/resources/db/migration/    # Flyway Migrations
│   └── pom.xml
├── ai-code-review-integration/             # External Integrations
│   ├── src/main/java/com/aicodereview/integration/
│   │   ├── webhook/                        # Webhook Verifiers
│   │   ├── git/                            # Git Operations (JGit)
│   │   ├── ai/                             # AI Provider Clients
│   │   └── notification/                   # Notification Clients
│   └── pom.xml
├── ai-code-review-worker/                  # Async Task Worker
│   ├── src/main/java/com/aicodereview/worker/
│   │   ├── consumer/                       # Queue Consumers
│   │   ├── processor/                      # Task Processors
│   │   └── analyzer/                       # Code Analyzers
│   └── pom.xml
└── ai-code-review-common/                  # Shared Utilities
    ├── src/main/java/com/aicodereview/common/
    │   ├── config/                         # Shared Config Classes
    │   ├── util/                           # Utility Classes
    │   └── constant/                       # Constants
    └── pom.xml
```

**Module Dependency Rules:**
- `api` depends on `service`, `common`
- `service` depends on `repository`, `integration`, `common`
- `repository` depends on `common`
- `integration` depends on `common`
- `worker` depends on `service`, `common`
- `common` has NO dependencies on other modules

**Test Organization:**
- Unit tests: `src/test/java/com/aicodereview/{module}/`
- Integration tests: Same location, suffix with `IntegrationTest`
- Test resources: `src/test/resources/`

#### Frontend Vue Structure

**Directory Organization:**
```
frontend/
├── src/
│   ├── views/                              # Page Components (Routes)
│   │   ├── project/
│   │   │   ├── ProjectList.vue
│   │   │   ├── ProjectDetail.vue
│   │   │   └── ProjectForm.vue
│   │   ├── review/
│   │   │   ├── ReviewHistory.vue
│   │   │   └── ReviewDetail.vue
│   │   ├── model/
│   │   │   ├── AIModelList.vue
│   │   │   └── AIModelForm.vue
│   │   └── template/
│   │       ├── TemplateList.vue
│   │       └── TemplateForm.vue
│   ├── components/                         # Reusable Components
│   │   ├── common/                         # Generic Components
│   │   │   ├── DataTable.vue
│   │   │   └── Pagination.vue
│   │   ├── review/                         # Review-specific Components
│   │   │   ├── IssueList.vue
│   │   │   └── CodeSnippet.vue
│   │   └── chart/
│   │       └── CallGraphChart.vue
│   ├── api/                                # API Client Modules
│   │   ├── project.ts
│   │   ├── review.ts
│   │   ├── model.ts
│   │   └── template.ts
│   ├── stores/                             # Pinia Stores
│   │   ├── project.ts
│   │   ├── review.ts
│   │   ├── model.ts
│   │   └── auth.ts
│   ├── router/                             # Vue Router
│   │   └── index.ts
│   ├── composables/                        # Composition Functions
│   │   ├── useReviews.ts
│   │   └── useProjects.ts
│   ├── utils/                              # Utility Functions
│   │   ├── request.ts                      # Axios Instance
│   │   ├── format.ts                       # Formatters
│   │   └── storage.ts                      # LocalStorage Wrapper
│   ├── types/                              # TypeScript Types
│   │   ├── project.ts
│   │   ├── review.ts
│   │   └── model.ts
│   ├── assets/                             # Static Assets
│   │   ├── images/
│   │   └── styles/
│   ├── App.vue                             # Root Component
│   └── main.ts                             # Entry Point
├── public/                                 # Public Static Files
├── .env.development                        # Dev Environment Variables
├── .env.production                         # Prod Environment Variables
├── vite.config.ts                          # Vite Configuration
├── tsconfig.json                           # TypeScript Configuration
└── package.json
```

**Component Organization Rules:**
- **Views:** Feature-based, matches routing structure
- **Components:** By domain (common, review, model, chart)
- **One component per file** (no multi-component files)
- **Co-located styles** (scoped CSS in same .vue file)

**Import Path Rules:**
- Use `@/` alias for `src/` directory
- Relative imports only within same feature directory
- **Examples:**
  ```typescript
  ✅ CORRECT:
  import { useProjectStore } from '@/stores/project'
  import ProjectForm from './ProjectForm.vue' (within same directory)

  ❌ WRONG:
  import { useProjectStore } from '../../stores/project'
  import ProjectForm from '@/views/project/ProjectForm.vue' (use relative for same dir)
  ```

### Format Patterns (MANDATORY)

#### API Response Format (Standardized)

**Success Response:**
```json
{
  "success": true,
  "data": { /* actual response payload */ },
  "error": null,
  "timestamp": "2026-02-05T10:00:00Z"
}
```

**Error Response:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "WEBHOOK_VERIFICATION_FAILED",
    "message": "Invalid webhook signature",
    "details": { "platform": "github", "repo": "owner/repo" }
  },
  "timestamp": "2026-02-05T10:00:00Z"
}
```

**Implementation:**
```java
@Data
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorDetail error;
    private Instant timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message, Object details) {
        ErrorDetail error = new ErrorDetail(code.getCode(), message, details);
        return new ApiResponse<>(false, null, error, Instant.now());
    }
}
```

**All Controllers MUST return `ApiResponse<T>`**

#### Data Exchange Formats

**JSON Field Naming:**
- **API Layer (HTTP):** `camelCase`
- **Database Layer (SQL):** `snake_case`
- **Mapping:** Automatic via Jackson `@JsonProperty` or MapStruct

**Date/Time Format:**
- **API:** ISO 8601 strings (`yyyy-MM-dd'T'HH:mm:ss'Z'`)
- **Database:** TIMESTAMP type
- **Timezone:** Always UTC
- **Examples:**
  ```json
  ✅ CORRECT: "createdAt": "2026-02-05T10:30:00Z"
  ❌ WRONG: "created_at": 1738752600, "createdDate": "2026-02-05"
  ```

**Boolean Representation:**
- **API/Database:** `true`/`false` (JSON boolean, SQL BOOLEAN)
- **Never use:** `1`/`0`, `"yes"`/`"no"`, `"Y"`/`"N"`

**Null Handling:**
- **Rule:** Omit null fields from JSON responses (Jackson: `@JsonInclude(Include.NON_NULL)`)
- **Exception:** Explicitly null fields with semantic meaning

**Pagination Format:**
```json
{
  "success": true,
  "data": {
    "items": [ /* array of results */ ],
    "page": 1,
    "pageSize": 20,
    "totalItems": 150,
    "totalPages": 8
  },
  "error": null,
  "timestamp": "2026-02-05T10:00:00Z"
}
```

### Communication Patterns (MANDATORY)

#### Queue Message Format

**Redis Queue Task Message:**
```json
{
  "taskId": "uuid",
  "type": "code_review",
  "priority": "high",
  "payload": {
    "projectId": "project-uuid",
    "commitHash": "abc123",
    "triggerType": "pr"
  },
  "createdAt": "2026-02-05T10:00:00Z",
  "retryCount": 0
}
```

**Queue Key Naming:**
- **Task Queue:** `task:queue` (Redis Sorted Set)
- **Processing Lock:** `task:lock:{taskId}` (Redis String with TTL)
- **Retry Queue:** `task:retry:queue`

#### Logging Format (Structured)

**Log Format:** JSON (Logback with Logstash Encoder)

**Log Entry Structure:**
```json
{
  "timestamp": "2026-02-05T10:00:00.123Z",
  "level": "INFO",
  "logger": "com.aicodereview.service.ReviewService",
  "message": "Review completed successfully",
  "correlationId": "req-abc123",
  "taskId": "task-uuid",
  "projectId": "project-uuid",
  "duration": 2500,
  "thread": "worker-1"
}
```

**Correlation ID:**
- **HTTP Requests:** Generate UUID, add to MDC, return in `X-Correlation-Id` header
- **Queue Tasks:** Extract from message, add to MDC
- **Purpose:** Trace requests across services

**Log Levels:**
- **ERROR:** Unexpected exceptions, system failures
- **WARN:** Recoverable errors, AI API rate limits, retry attempts
- **INFO:** Business events (task created, review completed)
- **DEBUG:** Detailed flow (enabled in dev only)
- **TRACE:** Not used

#### State Management Patterns (Frontend)

**Pinia Store Pattern:**
```typescript
// stores/project.ts
export const useProjectStore = defineStore('project', () => {
  // State
  const projects = ref<Project[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  // Getters (computed)
  const activeProjects = computed(() =>
    projects.value.filter(p => p.status === 'active')
  )

  // Actions
  async function fetchProjects() {
    loading.value = true
    error.value = null
    try {
      projects.value = await projectApi.getProjects()
    } catch (e) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  return { projects, loading, error, activeProjects, fetchProjects }
})
```

**State Update Rules:**
- **Immutability:** Use `.value` assignment (ref reactivity)
- **No direct mutations:** Always through actions
- **Error handling:** Capture in action, store in state

### Process Patterns (MANDATORY)

#### Error Handling Hierarchy

**Exception Hierarchy:**
```java
BusinessException (RuntimeException)
├── WebhookVerificationException (401)
├── AIProviderException (503)
│   ├── AIProviderUnavailableException
│   └── AIProviderRateLimitException
├── TaskProcessingException (500)
├── ValidationException (400)
└── ResourceNotFoundException (404)
```

**Global Exception Handler:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("Business error: {}", ex.getMessage(), ex);
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "Internal server error", null));
    }
}
```

**Error Handling Rules:**
- **Catch specific exceptions first**
- **Log at error level** (with stack trace)
- **Never expose internal details** in error messages to clients
- **Use error codes** for programmatic handling

#### Retry Strategy Implementation

**Task Retry Pattern:**
```java
@Service
public class TaskRetryService {
    private static final int MAX_RETRY_COUNT = 3;
    private static final int[] BACKOFF_DELAYS_MS = {1000, 2000, 4000}; // Exponential

    public void processWithRetry(ReviewTask task) {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount <= MAX_RETRY_COUNT) {
            try {
                processTask(task);
                return; // Success
            } catch (AIProviderRateLimitException e) {
                lastException = e;
                retryCount++;
                if (retryCount <= MAX_RETRY_COUNT) {
                    int delay = BACKOFF_DELAYS_MS[retryCount - 1];
                    Thread.sleep(delay);
                }
            } catch (ValidationException e) {
                // Don't retry validation errors
                throw e;
            }
        }

        // Max retries exceeded
        throw new TaskProcessingException("Task failed after " + MAX_RETRY_COUNT + " retries", lastException);
    }
}
```

**Retry Rules:**
- **Retryable:** AI API rate limits, network errors, temporary unavailability
- **Non-Retryable:** Validation errors, authentication failures, business logic errors
- **Backoff:** Exponential (1s, 2s, 4s)
- **Max Attempts:** 3 retries (4 total attempts)

#### Loading State Management

**Backend Loading State:**
- **Task Status:** `PENDING` → `RUNNING` → `COMPLETED`/`FAILED`
- **Status Update:** Immediately on state transition
- **Idempotency:** Handle duplicate status updates gracefully

**Frontend Loading State:**
```typescript
// Standardized loading pattern
const loading = ref(false)
const error = ref<string | null>(null)

async function performAction() {
  loading.value = true
  error.value = null
  try {
    await someAsyncOperation()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
```

### Enforcement Guidelines

#### All AI Agents MUST:

1. **Follow naming conventions exactly** as specified (no exceptions)
2. **Use the standardized API response format** (`ApiResponse<T>`) for all endpoints
3. **Implement retry logic** with exponential backoff for retryable errors
4. **Add correlation IDs** to all logs for request tracing
5. **Use the exception hierarchy** for error handling (no generic exceptions)
6. **Follow the module dependency rules** (no circular dependencies)
7. **Write tests** in the specified locations with correct naming
8. **Use TypeScript strict mode** in frontend (no `any` types without justification)
9. **Validate input** at API boundary before business logic
10. **Encrypt sensitive data** using the defined converter pattern

#### Pattern Verification:

- **Code Review Checklist:** Before committing, verify adherence to naming conventions
- **Linting:** ESLint (frontend), Checkstyle (backend) configured with these rules
- **CI/CD:** Automated checks for pattern violations

#### Pattern Updates:

- **Process:** Propose changes via architecture document update
- **Approval:** Requires team consensus
- **Migration:** Update existing code to match new patterns

### Pattern Examples

#### Good Example: Controller Implementation

```java
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getAllProjects(
        @RequestParam(required = false) String status
    ) {
        List<Project> projects = projectService.findAll(status);
        List<ProjectResponse> response = projects.stream()
            .map(ProjectMapper::toResponse)
            .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
        @Valid @RequestBody ProjectCreateRequest request
    ) {
        Project project = projectService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(ProjectMapper.toResponse(project)));
    }
}
```

#### Anti-Pattern: What to Avoid

```java
// ❌ WRONG: Multiple violations
@RestController
@RequestMapping("/api/projects") // Missing version
public class ProjectAPI { // Wrong suffix (should be Controller)
    @GetMapping
    public List<ProjectDTO> getProjects() { // No ApiResponse wrapper
        return service.getAllProjects(); // No error handling
    }

    @PostMapping
    public ProjectDTO create(@RequestBody ProjectDTO dto) { // No @Valid
        // No try-catch, returns different type than GET
        return service.saveProject(dto);
    }
}
```

## Project Structure & Boundaries

### Complete Project Directory Structure

**Root Level:**
```
ai-code-review/
├── README.md                                    # Project documentation
├── docker-compose.yml                           # Local development environment
├── .gitignore                                   # Git ignore rules
├── .env.example                                 # Environment variable template
├── backend/                                     # Java Spring Boot Backend
└── frontend/                                    # Vue 3 Frontend
```

**Backend Structure (Multi-Module Maven):**

```
backend/
├── pom.xml                                      # Parent POM (packaging: pom)
│
├── ai-code-review-api/                          # REST API Layer (Spring Boot Application)
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/aicodereview/api/
│   │   │   │   ├── ApiApplication.java          # Spring Boot Main Class
│   │   │   │   ├── controller/                  # REST Controllers
│   │   │   │   │   ├── WebhookController.java   # POST /api/webhook/{platform}
│   │   │   │   │   ├── ProjectController.java   # CRUD /api/v1/projects
│   │   │   │   │   ├── ReviewController.java    # GET /api/v1/tasks, /api/v1/results
│   │   │   │   │   ├── AIModelController.java   # CRUD /api/v1/ai-models
│   │   │   │   │   └── TemplateController.java  # CRUD /api/v1/templates
│   │   │   │   ├── dto/                          # Data Transfer Objects
│   │   │   │   │   ├── request/
│   │   │   │   │   │   ├── ProjectCreateRequest.java
│   │   │   │   │   │   ├── AIModelConfigRequest.java
│   │   │   │   │   │   └── WebhookRequest.java
│   │   │   │   │   ├── response/
│   │   │   │   │   │   ├── ProjectResponse.java
│   │   │   │   │   │   ├── ReviewTaskResponse.java
│   │   │   │   │   │   ├── ReviewResultResponse.java
│   │   │   │   │   │   └── ApiResponse.java     # Standardized wrapper
│   │   │   │   │   └── mapper/                  # MapStruct mappers
│   │   │   │   │       ├── ProjectMapper.java
│   │   │   │   │       └── ReviewMapper.java
│   │   │   │   ├── exception/                    # Global Exception Handling
│   │   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   │   └── ErrorCode.java
│   │   │   │   └── config/                       # API Configuration
│   │   │   │       ├── SecurityConfig.java       # Spring Security + JWT
│   │   │   │       ├── CorsConfig.java
│   │   │   │       └── JwtConfig.java
│   │   │   └── resources/
│   │   │       ├── application.yml               # Main configuration
│   │   │       ├── application-dev.yml           # Development profile
│   │   │       ├── application-prod.yml          # Production profile
│   │   │       └── logback-spring.xml            # Logging configuration
│   │   └── test/
│   │       ├── java/com/aicodereview/api/
│   │       │   └── controller/                   # Controller tests
│   │       │       ├── ProjectControllerTest.java
│   │       │       └── WebhookControllerIntegrationTest.java
│   │       └── resources/
│   │           └── application-test.yml
│
├── ai-code-review-service/                      # Business Logic Layer
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   └── java/com/aicodereview/service/
│   │   │       ├── service/                      # Business Services
│   │   │       │   ├── ProjectService.java       # Project CRUD logic
│   │   │       │   ├── TaskService.java          # Task management
│   │   │       │   ├── ReviewService.java        # Review orchestration
│   │   │       │   ├── AIModelService.java       # AI model config management
│   │   │       │   ├── TemplateService.java      # Template management
│   │   │       │   ├── ThresholdService.java     # Threshold validation
│   │   │       │   ├── DiffParserService.java    # Git diff parsing
│   │   │       │   └── ReportService.java        # Report generation
│   │   │       ├── domain/                       # Domain Models
│   │   │       │   ├── Project.java
│   │   │       │   ├── ReviewTask.java
│   │   │       │   ├── ReviewResult.java
│   │   │       │   ├── ReviewIssue.java
│   │   │       │   ├── CallGraph.java
│   │   │       │   └── CodeContext.java          # AI input context
│   │   │       └── strategy/                     # Strategy Patterns
│   │   │           ├── AIProvider.java           # Interface
│   │   │           ├── AIProviderFactory.java    # Factory
│   │   │           ├── OpenAIProvider.java       # OpenAI implementation
│   │   │           ├── AnthropicProvider.java    # Claude implementation
│   │   │           └── CustomOpenAPIProvider.java
│   │   └── test/
│   │       └── java/com/aicodereview/service/
│   │           ├── service/
│   │           │   ├── ProjectServiceTest.java
│   │           │   └── ReviewServiceTest.java
│   │           └── strategy/
│   │               └── AIProviderFactoryTest.java
│
├── ai-code-review-repository/                   # Data Access Layer
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/aicodereview/repository/
│   │   │   │   ├── entity/                       # JPA Entities
│   │   │   │   │   ├── ProjectEntity.java
│   │   │   │   │   ├── ReviewTaskEntity.java
│   │   │   │   │   ├── ReviewResultEntity.java
│   │   │   │   │   ├── ReviewIssueEntity.java
│   │   │   │   │   ├── AIModelConfigEntity.java
│   │   │   │   │   └── TemplateEntity.java
│   │   │   │   ├── repository/                   # Spring Data JPA Repositories
│   │   │   │   │   ├── ProjectRepository.java
│   │   │   │   │   ├── ReviewTaskRepository.java
│   │   │   │   │   ├── ReviewResultRepository.java
│   │   │   │   │   ├── AIModelConfigRepository.java
│   │   │   │   │   └── TemplateRepository.java
│   │   │   │   ├── mapper/                       # Entity-Domain Mappers
│   │   │   │   │   ├── ProjectEntityMapper.java
│   │   │   │   │   └── ReviewEntityMapper.java
│   │   │   │   └── converter/                    # JPA Attribute Converters
│   │   │   │       └── EncryptedStringConverter.java  # AES-256-GCM encryption
│   │   │   └── resources/
│   │   │       └── db/migration/                 # Flyway Migrations
│   │   │           ├── V1__create_project_table.sql
│   │   │           ├── V2__create_review_task_table.sql
│   │   │           ├── V3__create_review_result_table.sql
│   │   │           ├── V4__create_review_issue_table.sql
│   │   │           ├── V5__create_template_table.sql
│   │   │           └── V6__create_ai_model_config_table.sql
│   │   └── test/
│   │       └── java/com/aicodereview/repository/
│   │           └── ProjectRepositoryTest.java    # TestContainers integration test
│
├── ai-code-review-integration/                  # External Integrations Layer
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   └── java/com/aicodereview/integration/
│   │   │       ├── webhook/                      # Webhook Verifiers (Chain of Responsibility)
│   │   │       │   ├── WebhookVerifier.java      # Interface
│   │   │       │   ├── WebhookVerifierChain.java # Chain orchestrator
│   │   │       │   ├── GitHubWebhookVerifier.java   # HMAC-SHA256
│   │   │       │   ├── GitLabWebhookVerifier.java   # Secret Token
│   │   │       │   └── AWSCodeCommitWebhookVerifier.java  # AWS SigV4
│   │   │       ├── git/                          # Git Operations
│   │   │       │   ├── GitService.java           # JGit wrapper
│   │   │       │   └── DiffFetcher.java          # Fetch diff from Git platforms
│   │   │       ├── ai/                           # AI Provider Clients
│   │   │       │   ├── OpenAIClient.java         # OpenAI API client
│   │   │       │   ├── AnthropicClient.java      # Claude API client
│   │   │       │   └── OpenAPIClient.java        # Generic OpenAPI client
│   │   │       └── notification/                 # Notification Clients
│   │   │           ├── EmailNotificationService.java
│   │   │           ├── GitCommentService.java    # Post comments to PR/MR
│   │   │           └── DingTalkNotificationService.java
│   │   └── test/
│   │       └── java/com/aicodereview/integration/
│   │           ├── webhook/
│   │           │   └── GitHubWebhookVerifierTest.java
│   │           └── ai/
│   │               └── OpenAIClientTest.java
│
├── ai-code-review-worker/                       # Async Task Worker
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   └── java/com/aicodereview/worker/
│   │   │       ├── WorkerApplication.java        # Standalone worker app (optional)
│   │   │       ├── consumer/                     # Queue Consumers
│   │   │       │   ├── TaskConsumer.java         # Redis queue consumer
│   │   │       │   └── RedisQueueService.java    # Redis queue operations
│   │   │       ├── processor/                    # Task Processors
│   │   │       │   ├── ReviewTaskProcessor.java  # Orchestrates review workflow
│   │   │       │   └── RetryService.java         # Retry logic with backoff
│   │   │       └── analyzer/                     # Code Analyzers
│   │   │           ├── CallGraphAnalyzer.java    # JavaParser-based call graph
│   │   │           ├── SecurityAnalyzer.java     # Security-specific analysis
│   │   │           └── QualityAnalyzer.java      # Code quality metrics
│   │   └── test/
│   │       └── java/com/aicodereview/worker/
│   │           ├── processor/
│   │           │   └── ReviewTaskProcessorTest.java
│   │           └── analyzer/
│   │               └── CallGraphAnalyzerTest.java
│
└── ai-code-review-common/                       # Shared Utilities
    ├── pom.xml
    └── src/
        ├── main/
        │   └── java/com/aicodereview/common/
        │       ├── config/                       # Shared Configuration
        │       │   ├── RedisConfig.java          # Redis connection config
        │       │   └── CacheConfig.java          # Cache manager config
        │       ├── util/                         # Utility Classes
        │       │   ├── JsonUtil.java             # JSON serialization
        │       │   ├── CryptoUtil.java           # AES-256-GCM encryption
        │       │   └── DateUtil.java             # ISO 8601 date handling
        │       └── constant/                     # Constants
        │           ├── ErrorCode.java            # Error code enums
        │           ├── QueueKeys.java            # Redis queue key constants
        │           └── ApiConstants.java         # API version, headers
        └── test/
            └── java/com/aicodereview/common/
                └── util/
                    └── CryptoUtilTest.java
```

**Frontend Structure (Vue 3 + Vite + Vue-Vben-Admin):**

```
frontend/
├── package.json                                 # Dependencies and scripts
├── pnpm-lock.yaml                               # pnpm lock file
├── vite.config.ts                               # Vite configuration
├── tsconfig.json                                # TypeScript configuration
├── tsconfig.node.json                           # Node TypeScript config
├── .env.development                             # Dev environment variables
├── .env.production                              # Prod environment variables
├── .env.example                                 # Environment variable template
├── .gitignore                                   # Git ignore rules
├── index.html                                   # HTML entry point
├── README.md                                    # Frontend documentation
│
├── public/                                      # Public static assets
│   ├── favicon.ico
│   └── assets/
│       └── logo.png
│
└── src/
    ├── main.ts                                  # Application entry point
    ├── App.vue                                  # Root component
    │
    ├── views/                                   # Page Components (Routes)
    │   ├── login/
    │   │   └── Login.vue                        # Login page
    │   ├── dashboard/
    │   │   └── Dashboard.vue                    # Dashboard overview
    │   ├── project/
    │   │   ├── ProjectList.vue                  # Project list page
    │   │   ├── ProjectDetail.vue                # Project detail page
    │   │   └── ProjectForm.vue                  # Create/Edit project form
    │   ├── review/
    │   │   ├── ReviewHistory.vue                # Review history list
    │   │   └── ReviewDetail.vue                 # Review detail (issues, call graph)
    │   ├── model/
    │   │   ├── AIModelList.vue                  # AI model config list
    │   │   └── AIModelForm.vue                  # Create/Edit AI model config
    │   └── template/
    │       ├── TemplateList.vue                 # Review template list
    │       └── TemplateForm.vue                 # Create/Edit template
    │
    ├── components/                              # Reusable Components
    │   ├── common/                              # Generic UI Components
    │   │   ├── DataTable.vue                    # Generic data table
    │   │   ├── Pagination.vue                   # Pagination component
    │   │   ├── SearchBar.vue                    # Search input
    │   │   └── StatusBadge.vue                  # Status badge (pending/running/completed)
    │   ├── review/                              # Review-specific Components
    │   │   ├── IssueList.vue                    # Review issues list
    │   │   ├── IssueCard.vue                    # Single issue card
    │   │   ├── CodeSnippet.vue                  # Syntax-highlighted code
    │   │   └── ThresholdIndicator.vue           # Threshold pass/fail indicator
    │   ├── chart/                               # Chart Components
    │   │   └── CallGraphChart.vue               # Call graph visualization (D3.js/Mermaid)
    │   └── layout/                              # Layout Components (from Vue-Vben-Admin)
    │       └── ...
    │
    ├── api/                                     # API Client Modules
    │   ├── project.ts                           # Project API calls
    │   ├── review.ts                            # Review/Task API calls
    │   ├── model.ts                             # AI model config API calls
    │   ├── template.ts                          # Template API calls
    │   └── auth.ts                              # Authentication API calls
    │
    ├── stores/                                  # Pinia State Stores
    │   ├── project.ts                           # Project state
    │   ├── review.ts                            # Review state
    │   ├── model.ts                             # AI model state
    │   ├── template.ts                          # Template state
    │   └── auth.ts                              # Auth state (JWT token, user info)
    │
    ├── router/                                  # Vue Router Configuration
    │   ├── index.ts                             # Main router configuration
    │   ├── routes.ts                            # Route definitions
    │   └── guards.ts                            # Route guards (auth check)
    │
    ├── composables/                             # Composition Functions
    │   ├── useReviews.ts                        # Review-related logic
    │   ├── useProjects.ts                       # Project-related logic
    │   └── usePagination.ts                     # Pagination logic
    │
    ├── utils/                                   # Utility Functions
    │   ├── request.ts                           # Axios instance + interceptors
    │   ├── format.ts                            # Date/number formatters
    │   ├── storage.ts                           # LocalStorage wrapper
    │   └── validation.ts                        # Form validation helpers
    │
    ├── types/                                   # TypeScript Type Definitions
    │   ├── project.ts                           # Project-related types
    │   ├── review.ts                            # Review-related types
    │   ├── model.ts                             # AI model types
    │   ├── template.ts                          # Template types
    │   ├── api.ts                               # API response types
    │   └── common.ts                            # Common types (Pagination, etc.)
    │
    ├── assets/                                  # Static Assets
    │   ├── images/
    │   │   └── ...
    │   └── styles/
    │       ├── variables.scss                   # SCSS variables
    │       └── global.scss                      # Global styles
    │
    └── __tests__/                               # Vitest Unit Tests
        ├── components/
        │   └── IssueList.spec.ts
        ├── stores/
        │   └── project.spec.ts
        └── utils/
            └── format.spec.ts
```

### Architectural Boundaries

#### API Boundaries

**External API (Public):**
- **Webhook Endpoints:** `/api/webhook/{platform}` (GitHub, GitLab, AWS CodeCommit)
  - No authentication (signature verification only)
  - High throughput, low latency (<1s response)
  - Creates tasks asynchronously

**REST API (Authenticated):**
- **Version:** `/api/v1/*`
- **Authentication:** JWT Bearer token in `Authorization` header
- **Response Format:** Standardized `ApiResponse<T>` wrapper
- **Endpoints:**
  - `/api/v1/projects` - Project management
  - `/api/v1/tasks` - Review task queries
  - `/api/v1/results` - Review result queries
  - `/api/v1/ai-models` - AI model configuration
  - `/api/v1/templates` - Review template management

**Internal API (Module-to-Module):**
- **API → Service:** Direct method calls (same JVM)
- **Service → Repository:** JPA repository interfaces
- **Service → Integration:** Direct method calls for external integrations
- **Worker → Service:** Direct method calls for business logic

#### Component Boundaries

**Backend Layer Boundaries:**
```
Controller (API Layer)
    ↓ [DTOs]
Service (Business Logic)
    ↓ [Domain Models]
Repository (Data Access)
    ↓ [Entities]
Database
```

**Frontend Component Boundaries:**
```
Views (Pages)
    ↓ [Props & Events]
Components (Reusable UI)
    ↓ [Pinia Actions]
Stores (State Management)
    ↓ [API Calls]
API Modules (HTTP Client)
    ↓ [HTTP]
Backend API
```

**Communication Rules:**
- **Controllers** only call **Services** (never Repository directly)
- **Services** orchestrate business logic, call **Repositories** and **Integration** modules
- **Repositories** only access database (no business logic)
- **Integration** modules are stateless adapters for external systems
- **Workers** execute background tasks by calling **Services**

#### Data Boundaries

**Database Access Pattern:**
- **JPA Entities:** Database representation (`*Entity.java`)
- **Domain Models:** Business logic representation (`*.java` in service layer)
- **Mappers:** `*EntityMapper.java` converts Entity ↔ Domain
- **No Entities Outside Repository Module:** Domain models used in Service/API layers

**Caching Boundary:**
- **L1 Cache:** Spring `@Cacheable` in-memory (Caffeine) for hot data
- **L2 Cache:** Redis for shared cache across instances
- **Cache Keys:**
  - `project:{id}` - Project configuration
  - `ai-model:{id}` - AI model configuration
  - `webhook-secret:{projectId}` - Webhook secrets (encrypted)
  - `template:{id}` - Review templates

**Queue Boundary:**
- **Task Queue:** `task:queue` (Redis Sorted Set)
- **Lock Keys:** `task:lock:{taskId}` (Redis String with TTL 300s)
- **Message Format:** JSON with `{taskId, type, priority, payload, createdAt, retryCount}`

### Requirements to Structure Mapping

#### Functional Requirements to Files

**FR 1.1: Webhook Integration →**
- `backend/ai-code-review-api/controller/WebhookController.java`
- `backend/ai-code-review-integration/webhook/*Verifier.java`
- `backend/ai-code-review-integration/git/DiffFetcher.java`

**FR 1.2: Task Management →**
- `backend/ai-code-review-service/service/TaskService.java`
- `backend/ai-code-review-worker/consumer/TaskConsumer.java`
- `backend/ai-code-review-worker/processor/RetryService.java`

**FR 1.4: AI Review Engine →**
- `backend/ai-code-review-service/strategy/AIProvider.java`
- `backend/ai-code-review-integration/ai/*Client.java`
- `backend/ai-code-review-worker/analyzer/*Analyzer.java`
- `frontend/src/views/model/*`

**FR 1.5: Reporting System →**
- `backend/ai-code-review-service/service/ReportService.java`
- `frontend/src/views/review/ReviewDetail.vue`
- `frontend/src/components/review/IssueList.vue`
- `frontend/src/components/chart/CallGraphChart.vue`

**FR 1.8: Configuration Management →**
- `backend/ai-code-review-api/controller/*Controller.java`
- `frontend/src/views/project/*`, `model/*`, `template/*`

### Integration Points

**Webhook → Review → Notification Flow:**
```
Webhook Request → WebhookController
    → TaskService.createTask() → RedisQueue.enqueue()
    [Async Boundary]
RedisQueue.dequeue() → TaskConsumer
    → ReviewTaskProcessor.process()
        → AIProvider.analyze()
        → CallGraphAnalyzer.analyze()
        → ReportService.generate()
        → ThresholdService.validate()
        → NotificationService.notify()
    → TaskService.updateStatus()
```

### Development Workflow

**Full Stack Development:**
```bash
# Start all services
docker-compose up

# Access points:
# - Frontend: http://localhost:5173
# - Backend API: http://localhost:8080
# - PostgreSQL: localhost:5432
# - Redis: localhost:6379
```

**Backend Build:**
```bash
cd backend
mvn clean package
# Output: backend/ai-code-review-api/target/ai-code-review-api-{version}.jar
```

**Frontend Build:**
```bash
cd frontend
pnpm build
# Output: frontend/dist/
```

## Architecture Validation Results

### Coherence Validation ✅

**Decision Compatibility:**
All technological choices are fully compatible and work together seamlessly:
- Java 17 + Spring Boot 3.x + PostgreSQL 18.1 + Redis 8.0: Proven stack
- Vue 3 + Vite + Vue-Vben-Admin + Element Plus: Modern, compatible frontend stack
- JWT + Spring Security 6: Industry-standard authentication
- Docker Compose: Orchestrates all services without conflicts

**Pattern Consistency:**
Implementation patterns directly support architectural decisions:
- Naming conventions enforce consistency across database (snake_case), API (camelCase), and code (PascalCase/camelCase)
- Standardized API response format (ApiResponse<T>) ensures uniform error handling
- Chain of Responsibility pattern for webhooks supports multiple platforms extensibly
- Strategy pattern for AI providers enables flexible provider selection and fallback

**Structure Alignment:**
Project structure fully supports all architectural decisions:
- Multi-module Maven structure enables clear separation and independent scaling of worker module
- Frontend feature-based organization aligns with routing and domain boundaries
- Layered architecture (Controller→Service→Repository) prevents leaky abstractions
- Integration module isolation enables mocking and testing without external dependencies

### Requirements Coverage Validation ✅

**Epic/Feature Coverage:**
All 9 functional requirement categories have complete architectural support:
1. **Webhook Integration**: WebhookController + 3 platform-specific verifiers + DiffFetcher
2. **Task Management**: TaskService + Redis queue + Consumer + Retry logic
3. **Code Parsing**: DiffParserService + JGit wrapper
4. **AI Review Engine**: AIProvider interface + 3 implementations + Factory + Analyzers
5. **Reporting System**: ReportService + Frontend views + Visualization components
6. **Threshold Interception**: ThresholdService + Git status update integration
7. **Notification System**: 3 notification channels (Email, Git, DingTalk)
8. **Configuration Management**: 3 controller sets + corresponding frontend views
9. **Web UI**: Complete view/component/store structure with routing

**Functional Requirements Coverage:**
- ✅ Webhook signature verification: Chain of Responsibility with 3 verifiers (GitHub HMAC-SHA256, GitLab Token, AWS SigV4)
- ✅ Asynchronous processing: Redis queue + worker consumer with priority support
- ✅ Multi-platform support: Abstracted verifier and git service interfaces
- ✅ AI multi-provider: Strategy pattern with factory and fallback chain
- ✅ Call graph analysis: JavaParser (Phase 1) with Tree-sitter migration path (Phase 2)
- ✅ Threshold validation: Configurable rules with PR/MR status update
- ✅ Multi-project management: Isolated project configurations with encrypted secrets

**Non-Functional Requirements Coverage:**
- ✅ **Performance** (<30s review, <1s webhook):
  - Webhook returns immediately after enqueueing (sub-second)
  - Async queue processing decouples review execution
  - Redis caching reduces database queries
  - Stateless API enables horizontal scaling
- ✅ **Reliability** (99% availability, 3 retries):
  - Exponential backoff retry (1s, 2s, 4s) for transient failures
  - Distributed locking prevents duplicate processing
  - Health check endpoints via Spring Actuator
  - Database connection pooling with HikariCP
- ✅ **Security** (verification, encryption, HTTPS):
  - Webhook signature verification before processing
  - AES-256-GCM encryption for sensitive data (API keys, AWS credentials)
  - JWT stateless authentication with HttpOnly cookies
  - Constant-time signature comparison (timing attack prevention)
- ✅ **Scalability** (horizontal, ≥10 concurrent):
  - Stateless API design (JWT, no server-side sessions)
  - Shared Redis queue for worker pool coordination
  - PostgreSQL read replicas for query scaling (future)
  - Redis Cluster for cache scaling (future)

### Implementation Readiness Validation ✅

**Decision Completeness:**
- ✅ **All critical decisions documented**: Database (PostgreSQL 18.1), queue (Redis 8.0), auth (JWT), etc. with specific versions
- ✅ **Rationale provided**: Each decision includes "why" (e.g., PostgreSQL for JSONB, Redis for simplicity)
- ✅ **Alternatives documented**: Rejected options explained (MySQL, RabbitMQ, session-based auth)
- ✅ **Dependencies listed**: Specific Maven/npm packages with versions
- ✅ **Configuration examples**: YAML snippets for Spring, env variables for Vue

**Structure Completeness:**
- ✅ **Complete directory tree**: All 6 backend modules + frontend fully detailed
- ✅ **All files specified**: Controllers, services, repositories, entities, DTOs, views, components
- ✅ **Test organization defined**: Unit tests co-located, integration tests suffixed
- ✅ **Configuration files listed**: application.yml profiles, .env files, Flyway migrations
- ✅ **Build artifacts documented**: JAR output, dist/ directory

**Pattern Completeness:**
- ✅ **32 conflict points addressed**: Naming, structure, format, communication, process patterns
- ✅ **Naming conventions**: 5 contexts (DB, API, Java, Vue, Constants) with examples
- ✅ **Format standards**: API response, date/time, boolean, pagination with JSON examples
- ✅ **Communication patterns**: Queue messages, logging, state management with code samples
- ✅ **Process patterns**: Error handling, retry strategy, loading states with implementations
- ✅ **Examples provided**: Good example (ProjectController) + anti-pattern (ProjectAPI)

### Gap Analysis Results

**Critical Gaps:** None ✅

**Important Gaps:** None ✅

**Minor Enhancements (Non-Blocking):**

1. **CI/CD Pipeline Specifications**
   - **Current State**: Docker Compose defined for local development
   - **Gap**: Detailed CI/CD workflow steps (build → test → deploy) not specified
   - **Priority**: Low
   - **Recommendation**: Defer to implementation phase. GitHub Actions / GitLab CI config can be created as separate story.

2. **Performance Testing Strategy**
   - **Current State**: NFRs specified (<30s review, <1s webhook)
   - **Gap**: Load testing approach (tools, scenarios, metrics) not detailed
   - **Priority**: Low
   - **Recommendation**: Defer to testing phase. Architecture supports testing (stateless, scalable).

3. **Database Index Optimization**
   - **Current State**: Index naming convention defined (`idx_{table}_{columns}`)
   - **Gap**: Specific indexes for query optimization not in Flyway migrations
   - **Priority**: Low
   - **Recommendation**: Add indexes during implementation based on query patterns. Common indexes: `idx_review_task_project_id`, `idx_review_result_task_id`.

**Overall Assessment:** Architecture is production-ready. Minor enhancements are implementation details that don't block development.

### Validation Issues Addressed

**No Critical or Important Issues Found** ✅

The architecture is coherent, complete, and ready for AI agent implementation.

**Minor Observations (Addressed):**

1. **AI Model Routing Specificity**
   - **Observation**: Routing strategy (security → strict model, general → fast model) is conceptual
   - **Resolution**: Strategy pattern provides extensible framework. Implementation agents can define specific routing rules based on issue category, model availability, and fallback chain.
   - **Status**: Acceptable for architecture phase. ✅

2. **Call Graph Output Format Priority**
   - **Observation**: Multiple formats mentioned (Mermaid, PlantUML, SVG, D3.js) without priority
   - **Resolution**: Recommendation: Mermaid for Phase 1 (browser-native, lightweight, Vue-compatible). PlantUML/D3.js for Phase 2 enhancements.
   - **Status**: Clarified. ✅

### Architecture Completeness Checklist

**✅ Requirements Analysis**
- [x] Project context thoroughly analyzed (9 FR categories, 4 NFR types, 18-22 components)
- [x] Scale and complexity assessed (High complexity, full-stack domain)
- [x] Technical constraints identified (Java + Spring Boot, Vue + Vben-Admin)
- [x] Cross-cutting concerns mapped (Auth, error handling, logging, caching, validation)

**✅ Architectural Decisions**
- [x] Critical decisions documented with versions (PostgreSQL 18.1, Redis 8.0, Node 20.15.0+)
- [x] Technology stack fully specified (backend: 6 modules, frontend: complete structure)
- [x] Integration patterns defined (Chain of Responsibility, Strategy, Factory)
- [x] Performance considerations addressed (async queue, caching, stateless design)
- [x] Security measures specified (signature verification, encryption, JWT)
- [x] Scalability strategies defined (horizontal scaling, distributed locking)

**✅ Implementation Patterns**
- [x] Naming conventions established (DB: snake_case, API: camelCase, Classes: PascalCase)
- [x] Structure patterns defined (multi-module Maven, feature-based Vue)
- [x] Format patterns specified (ApiResponse<T>, ISO 8601 dates, JSON logs)
- [x] Communication patterns documented (queue messages, HTTP, state management)
- [x] Process patterns established (error hierarchy, retry backoff, loading states)
- [x] Examples provided (ProjectController good example, ProjectAPI anti-pattern)

**✅ Project Structure**
- [x] Complete directory structure defined (backend: 6 modules with all files, frontend: all directories)
- [x] Component boundaries established (Controller→Service→Repository→DB layering)
- [x] Integration points mapped (Webhook→Task→Review→Notification flow)
- [x] Requirements to structure mapping complete (9 FRs → specific files/modules)
- [x] Development workflow documented (docker-compose up, maven build, pnpm build)

### Architecture Readiness Assessment

**Overall Status:** ✅ **READY FOR IMPLEMENTATION**

**Confidence Level:** **HIGH** ✅

**Rationale:**
- All 9 functional requirements have complete architectural support
- All 4 non-functional requirement categories are addressed
- 32 potential conflict points identified and resolved with patterns
- Complete project structure with 300+ files specified
- Technology versions verified and compatible
- Implementation patterns comprehensive with examples
- No critical or important gaps identified

**Key Strengths:**
1. **Comprehensive Coverage**: Every functional requirement mapped to specific files and modules
2. **Clear Boundaries**: Layered architecture with well-defined component boundaries
3. **Extensibility**: Strategy and Chain patterns enable easy addition of AI providers and Git platforms
4. **Scalability**: Stateless design + Redis queue support horizontal scaling
5. **Consistency**: 32 conflict points addressed with mandatory patterns
6. **Specificity**: Not generic placeholders—actual file names, class names, package structures
7. **Traceability**: Clear mapping from requirements → decisions → structure → files

**Areas for Future Enhancement (Post-MVP):**
1. Advanced RBAC (Phase 2): Current basic auth sufficient for Phase 1
2. Tree-sitter multi-language: JavaParser adequate for Java-focused Phase 1
3. RabbitMQ migration: Redis Queue sufficient for <100 concurrent tasks
4. Advanced monitoring: Basic Micrometer + Prometheus covers Phase 1 needs
5. OAuth2 integration: JWT with username/password adequate for Phase 1

### Implementation Handoff

**AI Agent Guidelines:**

1. **Follow Architectural Decisions Exactly:**
   - Use PostgreSQL 18.1, Redis 8.0, Spring Boot 3.x, Vue 3 as specified
   - Implement Strategy pattern for AI providers, Chain of Responsibility for webhooks
   - Use JWT authentication with specified token expiration (1h access, 7d refresh)

2. **Use Implementation Patterns Consistently:**
   - Database tables: `snake_case`, columns: `snake_case`, PKs: `id`, FKs: `{table}_id`
   - REST endpoints: `/api/v1/{resources}` (plural, hyphen-separated)
   - Java classes: `PascalCase` with suffixes (`*Controller`, `*Service`, `*Repository`)
   - Vue components: `PascalCase.vue`, composables: `use*.ts`, API modules: `camelCase.ts`
   - All API responses: `ApiResponse<T>` wrapper with `{success, data, error, timestamp}`

3. **Respect Project Structure and Boundaries:**
   - Backend: 6 modules (api, service, repository, integration, worker, common) with specified packages
   - Frontend: Feature-based views, domain-based components, centralized stores
   - Controllers call Services only (never Repositories directly)
   - Repositories access database only (no business logic)
   - Workers execute background tasks via Services

4. **Refer to Architecture Document:**
   - For naming conventions: See "Implementation Patterns" section
   - For project structure: See "Project Structure & Boundaries" section
   - For architectural decisions: See "Core Architectural Decisions" section
   - For examples: See "Pattern Examples" section

**First Implementation Priority:**

**Step 1: Project Initialization**
```bash
# Backend: Create Spring Boot multi-module structure
cd backend
# Create parent POM and 6 module directories
# Configure Maven dependencies per module

# Frontend: Initialize Vue-Vben-Admin
git clone https://github.com/vbenjs/vue-vben-admin.git frontend
cd frontend
npm i -g corepack
pnpm install

# Infrastructure: Set up Docker Compose
docker-compose up -d postgres redis
```

**Step 2: Foundation Layer (Database + Config)**
- Create Flyway migration V1 (project table)
- Create ProjectEntity and ProjectRepository
- Configure Spring Data JPA + PostgreSQL connection
- Configure Redis connection

**Step 3: API Layer Skeleton**
- Create ApiApplication.java (Spring Boot main class)
- Create ProjectController with basic CRUD
- Create ApiResponse<T> wrapper
- Configure CORS and basic security

**Step 4: Frontend Integration**
- Create project.ts API module
- Create useProjectStore
- Create ProjectList.vue and ProjectForm.vue
- Test full-stack CRUD flow

**Implementation Sequence After Foundation:**
1. Webhook Integration (WebhookController + Verifiers)
2. Task Management (TaskService + Redis Queue)
3. Worker Engine (TaskConsumer + ReviewTaskProcessor)
4. AI Integration (AIProvider Strategy + OpenAI/Claude clients)
5. Review Logic (Call graph analyzer + Report generation)
6. Notifications (Email + Git comments)
7. Frontend UI (Review views + Visualization)
8. Testing & Deployment (Unit + Integration tests + Docker)

---

## Additional Technical Specifications

### AWS CodeCommit Difference API Detailed Specification

**Context**: AWS CodeCommit integration requires different authentication and API patterns compared to GitHub/GitLab. This section provides complete implementation details for retrieving code differences.

#### Authentication

**IAM Credentials Required**:
- **Access Key ID**: AWS_ACCESS_KEY_ID (stored encrypted in database)
- **Secret Access Key**: AWS_SECRET_ACCESS_KEY (stored encrypted in database)
- **Region**: AWS region where CodeCommit repository is hosted (e.g., us-east-1)

**SDK Configuration**:
```java
import software.amazon.awssdk.services.codecommit.CodeCommitClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

CodeCommitClient client = CodeCommitClient.builder()
    .region(Region.of(project.getAwsRegion()))
    .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create(
            decryptedAccessKeyId,
            decryptedSecretAccessKey
        )
    ))
    .build();
```

#### GetDifferences API Call

**Primary API Method**: `codecommit.getDifferences()`

**Request Parameters**:

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `repositoryName` | String | Yes | CodeCommit repository name | "my-java-app" |
| `beforeCommitSpecifier` | String | Yes | Base commit SHA or branch name | "main" or "abc123def456" |
| `afterCommitSpecifier` | String | Yes | Target commit SHA or branch name | "feature-branch" or "def456abc789" |
| `nextToken` | String | No | Token for pagination (if > 100 files changed) | From previous response |
| `maxResults` | Integer | No | Maximum differences per page (default: 100, max: 100) | 100 |

**Java Implementation**:
```java
@Service
public class AwsCodeCommitDiffService implements GitDiffService {

    @Autowired
    private CodeCommitClient codeCommitClient;

    @Override
    public DiffResult getDifferences(String repoName,
                                     String beforeCommit,
                                     String afterCommit) {
        List<Difference> allDifferences = new ArrayList<>();
        String nextToken = null;

        do {
            GetDifferencesRequest request = GetDifferencesRequest.builder()
                .repositoryName(repoName)
                .beforeCommitSpecifier(beforeCommit)
                .afterCommitSpecifier(afterCommit)
                .maxResults(100)
                .nextToken(nextToken)
                .build();

            GetDifferencesResponse response =
                codeCommitClient.getDifferences(request);
            allDifferences.addAll(response.differences());
            nextToken = response.nextToken();

        } while (nextToken != null);

        return convertToUnifiedDiff(repoName, allDifferences);
    }
}
```

**Response Structure**:
```json
{
  "differences": [
    {
      "changeType": "M",
      "beforeBlob": {
        "blobId": "abc123...",
        "path": "src/main/java/Service.java",
        "mode": "100644"
      },
      "afterBlob": {
        "blobId": "def456...",
        "path": "src/main/java/Service.java",
        "mode": "100644"
      }
    }
  ],
  "nextToken": "eyJzdGFydGluZ1Rva2VuIjogbnVsbCwgIk1heFJlc3VsdHMiOiAxMDAsICJuZXh0VG9rZW4iOiAiYWJjMTIzIn0="
}
```

**Change Types**:
- `A`: Added (new file)
- `M`: Modified (existing file changed)
- `D`: Deleted (file removed)

#### GetBlob API for File Content

**Purpose**: Retrieve full file content for before/after versions to generate unified diff

**API Method**: `codecommit.getBlob()`

**Request Parameters**:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `repositoryName` | String | Yes | Repository name |
| `blobId` | String | Yes | Blob ID from Difference object |

**Java Implementation**:
```java
private String getBlobContent(String repoName, String blobId) {
    GetBlobRequest request = GetBlobRequest.builder()
        .repositoryName(repoName)
        .blobId(blobId)
        .build();

    GetBlobResponse response = codeCommitClient.getBlob(request);
    return new String(
        response.content().asByteArray(),
        StandardCharsets.UTF_8
    );
}
```

#### Unified Diff Generation

**Library**: `java-diff-utils` (io.github.java-diff-utils:java-diff-utils:4.12)

**Implementation**:
```java
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.UnifiedDiffUtils;

private String generateUnifiedDiff(String repoName, Difference diff) {
    // Handle different change types
    switch (diff.changeType()) {
        case A:  // Added file
            String afterContent = getBlobContent(
                repoName, diff.afterBlob().blobId());
            return generateAddDiff(diff.afterBlob().path(), afterContent);

        case D:  // Deleted file
            String beforeContent = getBlobContent(
                repoName, diff.beforeBlob().blobId());
            return generateDeleteDiff(diff.beforeBlob().path(), beforeContent);

        case M:  // Modified file
            String before = getBlobContent(
                repoName, diff.beforeBlob().blobId());
            String after = getBlobContent(
                repoName, diff.afterBlob().blobId());

            List<String> beforeLines = Arrays.asList(before.split("\n"));
            List<String> afterLines = Arrays.asList(after.split("\n"));

            Patch<String> patch = DiffUtils.diff(beforeLines, afterLines);
            List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
                "a/" + diff.afterBlob().path(),
                "b/" + diff.afterBlob().path(),
                beforeLines,
                patch,
                3  // context lines
            );

            return String.join("\n", unifiedDiff);

        default:
            throw new IllegalArgumentException(
                "Unknown change type: " + diff.changeType());
    }
}
```

#### Pagination Handling

**Scenario**: When > 100 files are changed in a single commit/PR

**Strategy**:
1. Make initial GetDifferences request with maxResults=100
2. Check for `nextToken` in response
3. If present, make subsequent requests with `nextToken` parameter
4. Repeat until `nextToken` is null

**Performance Consideration**: Each API call adds ~500ms latency. For 300 files changed, expect ~1.5s for difference retrieval alone.

#### Error Handling

**Common AWS SDK Exceptions**:

| Exception | Meaning | Retry? | Strategy |
|-----------|---------|--------|----------|
| `RepositoryDoesNotExistException` | Invalid repository name | No | Return 404, log error |
| `CommitDoesNotExistException` | Invalid commit SHA | No | Return 404, log error |
| `ThrottlingException` | Rate limit exceeded (burst: 40 req/s, sustained: 10 req/s) | Yes | Exponential backoff: 1s, 2s, 4s |
| `ServiceUnavailableException` | AWS service temporarily down | Yes | Exponential backoff: 1s, 2s, 4s |
| `InvalidRequestException` | Invalid API parameters | No | Return 400, log error |
| `AccessDeniedException` | IAM permissions insufficient | No | Return 403, alert admin |

**Retry Logic with Spring @Retryable**:
```java
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;

@Retryable(
    value = { ThrottlingException.class, ServiceUnavailableException.class },
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
public DiffResult getDifferencesWithRetry(String repoName,
                                          String beforeCommit,
                                          String afterCommit) {
    return getDifferences(repoName, beforeCommit, afterCommit);
}
```

#### Rate Limiting Protection

**AWS CodeCommit Rate Limits**:
- **Burst capacity**: 40 requests/second
- **Sustained rate**: 10 requests/second

**Protection Strategy**: Use Guava RateLimiter to prevent exceeding sustained rate

```java
import com.google.common.util.concurrent.RateLimiter;

@Component
public class CodeCommitRateLimiter {
    // Set to 80% of sustained limit to provide buffer
    private final RateLimiter rateLimiter = RateLimiter.create(8.0);

    public <T> T executeWithRateLimit(Supplier<T> operation) {
        rateLimiter.acquire();  // Blocks until permit available
        return operation.get();
    }
}

// Usage in service
public DiffResult getDifferences(...) {
    return rateLimiter.executeWithRateLimit(() ->
        codeCommitClient.getDifferences(request)
    );
}
```

#### Configuration

**Application Properties**:
```yaml
aws:
  codecommit:
    enabled: ${AWS_CODECOMMIT_ENABLED:false}
    default-region: us-east-1
    api:
      max-retries: 3
      connection-timeout: 10000  # 10 seconds
      request-timeout: 30000     # 30 seconds
      max-results: 100           # pagination size
    rate-limit:
      requests-per-second: 8     # 80% of sustained limit
```

**Security Best Practices**:
1. **Encrypt AWS credentials** using AES-256-GCM before storing in database
2. **Use IAM roles** when deploying to EC2/ECS (avoid hardcoded credentials)
3. **Validate region parameter** to prevent SSRF attacks
4. **Implement circuit breaker** for repeated AWS API failures
5. **Monitor API usage** to avoid quota exhaustion

#### Webhook Signature Verification

**AWS Signature Version 4** (more complex than GitHub HMAC-SHA256)

**Headers to Verify**:
```
Authorization: AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20260205/us-east-1/codecommit/aws4_request, SignedHeaders=host;x-amz-date, Signature=abc123...
X-Amz-Date: 20260205T103000Z
```

**Verification Strategy**: Use AWS SDK's built-in signature verification utilities

```java
@Component
public class AwsCodeCommitWebhookVerifier implements WebhookVerifier {

    @Override
    public boolean verify(String payload, Map<String, String> headers) {
        try {
            String authorization = headers.get("authorization");
            String dateHeader = headers.get("x-amz-date");

            // Extract signature from Authorization header
            String providedSignature = extractSignature(authorization);

            // Calculate expected signature using AWS SigV4 algorithm
            String expectedSignature = calculateAwsSigV4(
                payload, dateHeader, region, secretKey
            );

            // Use constant-time comparison
            return MessageDigest.isEqual(
                providedSignature.getBytes(),
                expectedSignature.getBytes()
            );

        } catch (Exception e) {
            log.error("Failed to verify AWS CodeCommit signature", e);
            return false;
        }
    }

    private String calculateAwsSigV4(String payload, String dateHeader,
                                     String region, String secretKey) {
        // Implement AWS Signature V4 algorithm
        // Reference: https://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html

        // Step 1: Create canonical request
        String canonicalRequest = buildCanonicalRequest(payload, headers);

        // Step 2: Create string to sign
        String stringToSign = buildStringToSign(dateHeader, region, canonicalRequest);

        // Step 3: Calculate signature
        byte[] signingKey = getSigningKey(secretKey, dateHeader, region, "codecommit");
        return HmacSHA256(signingKey, stringToSign);
    }
}
```

**Recommendation**: Due to AWS SigV4 complexity, consider using AWS SDK's `Aws4Signer` utility class for signature verification.

#### Performance Expectations

**Latency Benchmarks** (based on AWS CodeCommit in us-east-1):

| Operation | Typical Latency | Notes |
|-----------|-----------------|-------|
| GetDifferences (< 100 files) | 300-800ms | Depends on file count |
| GetDifferences (pagination) | +500ms per page | For 200 files: ~1.5s total |
| GetBlob (per file) | 100-300ms | Depends on file size |
| Full diff generation (5 files) | 2-3 seconds | Including blob fetching and diff generation |

**Optimization Tips**:
1. **Parallel blob fetching**: Use `CompletableFuture` to fetch multiple file contents concurrently
2. **Cache blob content**: Store blob content in Redis for 5 minutes (if same commit analyzed multiple times)
3. **Skip large files**: Skip binary files or files > 1MB (configurable threshold)

#### Implementation Checklist

- [ ] Add AWS SDK dependency: `software.amazon.awssdk:codecommit:2.20.0`
- [ ] Implement `AwsCodeCommitDiffService` with GetDifferences and GetBlob
- [ ] Implement `AwsCodeCommitWebhookVerifier` with SigV4 verification
- [ ] Add retry logic with `@Retryable` annotation
- [ ] Implement rate limiting with Guava `RateLimiter`
- [ ] Configure AWS credentials encryption in database
- [ ] Add error handling for all AWS SDK exceptions
- [ ] Write unit tests with mocked AWS SDK
- [ ] Write integration tests with real AWS CodeCommit repository
- [ ] Document IAM permissions required (GetRepository, GetCommit, GetDifferences, GetBlob)

---

### AI Degradation Strategy Decision Tree

**Context**: AI API calls may fail due to rate limiting, timeouts, or service unavailability. This section defines a comprehensive degradation strategy to ensure system resilience.

#### Degradation Levels

```
Level 0: Primary AI Model (e.g., GPT-4, Claude Opus)
   ↓ (on failure)
Level 1: Secondary AI Model (e.g., GPT-3.5 Turbo, Claude Sonnet)
   ↓ (on failure)
Level 2: Simplified Review (fast model + reduced dimensions)
   ↓ (on failure)
Level 3: Static Analysis Only (no AI, rule-based checks)
   ↓ (on failure)
Level 4: Review Failed (mark task as failed, notify admin)
```

#### Error Type Classification

**1. Transient Errors (Retryable)**

| Error Code | Description | Retry Strategy | Degradation Trigger |
|------------|-------------|----------------|-------------------|
| **429** | Rate limit exceeded | Exponential backoff: 1s, 2s, 4s, 8s (max 4 retries) | After 4 failed retries → Level 1 |
| **503** | Service unavailable | Exponential backoff: 1s, 2s, 4s (max 3 retries) | After 3 failed retries → Level 1 |
| **Timeout** | Request timeout (> 30s) | Immediate retry once | After 1 failed retry → Level 1 |
| **502/504** | Gateway error | Exponential backoff: 2s, 4s (max 2 retries) | After 2 failed retries → Level 1 |

**2. Permanent Errors (Non-Retryable)**

| Error Code | Description | Retry Strategy | Degradation Trigger |
|------------|-------------|----------------|-------------------|
| **401** | Authentication failed | No retry | Immediate → Level 4 (alert admin) |
| **403** | Quota exceeded (monthly limit) | No retry | Immediate → Level 1 (if secondary available) |
| **400** | Invalid request (bad prompt) | No retry | Immediate → Level 3 (static analysis) |
| **500** | Internal server error | Single retry | After 1 failed retry → Level 1 |

**3. Client Errors (Non-Retryable)**

| Error Type | Description | Retry Strategy | Degradation Trigger |
|------------|-------------|----------------|-------------------|
| **Network error** | Connection refused, DNS failure | Single retry | After 1 failed retry → Level 1 |
| **JSON parse error** | Invalid response format | No retry | Immediate → Level 1 |
| **Token limit exceeded** | Prompt too large | No retry | Reduce prompt size, retry once → Level 2 |

#### Decision Tree (Mermaid Format)

```mermaid
graph TD
    Start[AI Review Request] --> TryPrimary[Try Primary AI Model<br/>GPT-4 / Claude Opus]

    TryPrimary -->|Success| Success[Return Review Result]
    TryPrimary -->|429 Rate Limit| Retry429[Exponential Backoff<br/>1s, 2s, 4s, 8s]
    TryPrimary -->|503 Service Down| Retry503[Exponential Backoff<br/>1s, 2s, 4s]
    TryPrimary -->|Timeout > 30s| RetryTimeout[Immediate Retry 1x]
    TryPrimary -->|401 Auth Failed| AlertAdmin[Alert Admin<br/>Mark Failed]
    TryPrimary -->|403 Quota Exceeded| TrySecondary

    Retry429 -->|Success After Retry| Success
    Retry429 -->|All Retries Failed| TrySecondary[Try Secondary AI Model<br/>GPT-3.5 / Claude Sonnet]

    Retry503 -->|Success After Retry| Success
    Retry503 -->|All Retries Failed| TrySecondary

    RetryTimeout -->|Success After Retry| Success
    RetryTimeout -->|Retry Failed| TrySecondary

    TrySecondary -->|Success| Success
    TrySecondary -->|429/503 Error| Retry2[Exponential Backoff<br/>1s, 2s]
    TrySecondary -->|All Retries Failed| SimplifiedReview[Simplified Review<br/>Fast Model + 3 Dimensions Only]

    Retry2 -->|Success After Retry| Success
    Retry2 -->|All Retries Failed| SimplifiedReview

    SimplifiedReview -->|Success| PartialSuccess[Return Partial Review<br/>with Warning]
    SimplifiedReview -->|Failed| StaticAnalysis[Static Analysis Only<br/>Rule-Based Checks]

    StaticAnalysis -->|Success| MinimalSuccess[Return Minimal Review<br/>with Warning]
    StaticAnalysis -->|Failed| Failed[Mark Review as Failed<br/>Log Error & Notify]

    AlertAdmin --> Failed
```

#### Implementation Strategy

**Service Layer**:
```java
@Service
public class AIReviewOrchestrator {

    @Autowired
    private AIProviderFactory providerFactory;

    @Autowired
    private StaticAnalysisService staticAnalysisService;

    public ReviewResult conductReview(ReviewTask task) {
        ReviewContext context = buildContext(task);

        // Level 0: Try primary AI model
        try {
            return tryAIReview(context, AIModelPriority.PRIMARY);
        } catch (RateLimitException | ServiceUnavailableException e) {
            log.warn("Primary AI model failed: {}, trying secondary",
                     e.getMessage());
        } catch (AuthenticationException e) {
            log.error("Authentication failed for primary model", e);
            notifyAdmin("AI authentication failed", e);
            return ReviewResult.failed(task.getId(),
                                       "Authentication error");
        }

        // Level 1: Try secondary AI model
        try {
            return tryAIReview(context, AIModelPriority.SECONDARY);
        } catch (Exception e) {
            log.warn("Secondary AI model failed: {}, using simplified review",
                     e.getMessage());
        }

        // Level 2: Simplified review (reduce dimensions, use fast model)
        try {
            context.setReviewDimensions(
                List.of(DimensionType.SECURITY,
                        DimensionType.BUGS,
                        DimensionType.PERFORMANCE)
            );
            return tryAIReview(context, AIModelPriority.FAST);
        } catch (Exception e) {
            log.warn("Simplified AI review failed: {}, falling back to static analysis",
                     e.getMessage());
        }

        // Level 3: Static analysis only
        try {
            ReviewResult result = staticAnalysisService.analyze(task);
            result.setWarning("AI review unavailable, showing static analysis results only");
            return result;
        } catch (Exception e) {
            log.error("Static analysis failed", e);
        }

        // Level 4: Complete failure
        return ReviewResult.failed(task.getId(),
                                   "All review methods failed");
    }

    @Retryable(
        value = {RateLimitException.class, ServiceUnavailableException.class},
        maxAttempts = 4,
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 8000)
    )
    private ReviewResult tryAIReview(ReviewContext context,
                                     AIModelPriority priority) {
        AIProvider provider = providerFactory.getProvider(priority);
        return provider.review(context);
    }
}
```

**Error Classification**:
```java
public class AIProviderErrorHandler {

    public void handleError(Exception e, ReviewTask task) {
        if (e instanceof RateLimitException) {
            // Log and retry handled by @Retryable
            log.warn("Rate limit hit for task {}", task.getId());

        } else if (e instanceof AuthenticationException) {
            // Critical error - alert admin immediately
            log.error("AI provider authentication failed", e);
            alertService.sendAlert(
                AlertType.CRITICAL,
                "AI Authentication Failed",
                e.getMessage()
            );

        } else if (e instanceof QuotaExceededException) {
            // Switch to secondary provider immediately
            log.error("AI provider quota exceeded", e);
            alertService.sendAlert(
                AlertType.HIGH,
                "AI Quota Exceeded",
                "Switching to secondary provider"
            );

        } else if (e instanceof TimeoutException) {
            // Single retry, then degrade
            log.warn("AI request timeout for task {}", task.getId());

        } else {
            // Unknown error - log and fail safe
            log.error("Unknown AI provider error", e);
        }
    }
}
```

#### Monitoring and Alerting

**Metrics to Track**:

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `ai.degradation.rate` | Percentage of reviews degraded to Level 1+ | > 10% in 5 minutes |
| `ai.failure.rate` | Percentage of complete failures (Level 4) | > 5% in 5 minutes |
| `ai.retry.count` | Number of retries per review | Average > 2 |
| `ai.provider.latency` | Response time by provider | p95 > 30s |
| `ai.quota.remaining` | Remaining API quota | < 10% of monthly limit |

**Alerting Rules**:
```yaml
alerts:
  - name: HighAIDegradationRate
    condition: ai.degradation.rate > 10%
    duration: 5m
    severity: warning
    action: notify_team

  - name: AIAuthenticationFailure
    condition: ai.authentication.failure
    severity: critical
    action: notify_admin_immediately

  - name: AIQuotaExhausted
    condition: ai.quota.remaining < 10%
    severity: high
    action: notify_admin
```

#### Configuration

**Application Properties**:
```yaml
ai:
  degradation:
    enabled: true
    levels:
      primary:
        provider: openai
        model: gpt-4
        max-retries: 4
        timeout: 30s
      secondary:
        provider: anthropic
        model: claude-3-5-sonnet
        max-retries: 3
        timeout: 30s
      simplified:
        provider: openai
        model: gpt-3.5-turbo
        max-retries: 2
        timeout: 20s
        dimensions: [security, bugs, performance]
    static-analysis:
      enabled: true
      rules: [null-pointer, sql-injection, xss]
```

#### Testing Strategy

**Unit Tests**:
- Mock AI provider errors (429, 503, timeout, 401)
- Verify retry logic with exponential backoff
- Verify degradation path (Primary → Secondary → Simplified → Static)
- Verify alert triggers for critical errors

**Integration Tests**:
- Test with real AI provider rate limiting (use test API keys with low quotas)
- Measure actual latency under degradation scenarios
- Verify end-to-end flow including static analysis fallback

**Load Tests**:
- Simulate 50 concurrent reviews with 30% error rate
- Verify system remains stable under degradation
- Measure impact on overall review throughput

---

### Six-Dimension Review Concurrency Strategy

**Context**: AI Code Review conducts reviews across 6 dimensions (Security, Performance, Maintainability, Correctness, Style, Best Practices). Sequential execution would take 6x longer than parallel execution. This section defines the concurrency orchestration strategy.

#### Concurrency Model

**Pattern**: **Parallel Execution with Concurrency Limiting**

**Why Parallel**:
- 6 dimensions are independent (no data dependencies)
- Reduces total review time from ~180s (6 × 30s) to ~30s (max of 6 parallel calls)
- AI API calls are I/O-bound (network latency dominant)

**Why Concurrency Limiting**:
- Prevent overwhelming AI provider APIs (rate limits)
- Control resource usage (memory, threads)
- Avoid triggering provider-side rate limiters

#### Architecture Diagram

```
ReviewTask
    ↓
ReviewOrchestrator
    ↓
┌─────────────────────────────────────────────┐
│     Dimension Review Executor Pool          │
│     (Semaphore: max 3 concurrent)           │
├─────────────────────────────────────────────┤
│                                             │
│  [Dimension 1: Security]   ──┐             │
│  [Dimension 2: Performance]──┤             │
│  [Dimension 3: Maintainability]─┤ Parallel │
│  [Dimension 4: Correctness]────┤  (max 3)  │
│  [Dimension 5: Style]──────────┤           │
│  [Dimension 6: Best Practices]─┘           │
│                                             │
└─────────────────────────────────────────────┘
    ↓ (wait for all)
ReviewResultAggregator
    ↓
Combined Review Result
```

#### Concurrency Configuration

**Semaphore-Based Limiting**:

| Configuration | Value | Rationale |
|---------------|-------|-----------|
| **Max Concurrent Dimensions** | 3 | Balance between speed and rate limits |
| **Thread Pool Size** | 6 | One thread per dimension (fixed pool) |
| **Timeout per Dimension** | 45s | Longer than typical AI call (30s) + buffer |
| **Total Review Timeout** | 60s | Max time for all 6 dimensions |

**Why max 3 concurrent**:
- OpenAI rate limit: ~500 requests/minute (~8 req/s) - 3 concurrent leaves headroom
- Anthropic rate limit: ~50 requests/minute (~0.8 req/s) - requires rate limiter
- Reduces memory usage (3 concurrent AI responses in memory vs 6)

#### Implementation

**Service Layer**:
```java
@Service
public class SixDimensionReviewOrchestrator {

    private final ExecutorService executorService;
    private final Semaphore concurrencyLimiter;

    @Autowired
    private AIProviderFactory providerFactory;

    @Autowired
    private ReviewResultAggregator aggregator;

    public SixDimensionReviewOrchestrator(
            @Value("${ai.review.max-concurrent-dimensions:3}") int maxConcurrent) {
        this.executorService = Executors.newFixedThreadPool(6);
        this.concurrencyLimiter = new Semaphore(maxConcurrent);
    }

    public ReviewResult conductSixDimensionReview(ReviewTask task) {
        List<DimensionType> dimensions = Arrays.asList(
            DimensionType.SECURITY,
            DimensionType.PERFORMANCE,
            DimensionType.MAINTAINABILITY,
            DimensionType.CORRECTNESS,
            DimensionType.STYLE,
            DimensionType.BEST_PRACTICES
        );

        // Create futures for all dimensions
        List<CompletableFuture<DimensionResult>> futures = dimensions.stream()
            .map(dimension -> CompletableFuture.supplyAsync(
                () -> reviewDimension(task, dimension),
                executorService
            ))
            .collect(Collectors.toList());

        // Wait for all dimensions to complete (with timeout)
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);

            // Aggregate results
            List<DimensionResult> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

            return aggregator.aggregate(task.getId(), results);

        } catch (TimeoutException e) {
            log.error("Review timeout after 60s for task {}", task.getId());
            // Cancel incomplete futures
            futures.forEach(f -> f.cancel(true));
            // Return partial results
            return aggregatePartialResults(task.getId(), futures);

        } catch (Exception e) {
            log.error("Review failed for task {}", task.getId(), e);
            return ReviewResult.failed(task.getId(), e.getMessage());
        }
    }

    private DimensionResult reviewDimension(ReviewTask task,
                                           DimensionType dimension) {
        try {
            // Acquire semaphore permit (blocks if 3 already running)
            concurrencyLimiter.acquire();

            log.debug("Starting {} review for task {}", dimension, task.getId());
            long startTime = System.currentTimeMillis();

            AIProvider provider = providerFactory.getProvider(
                task.getAIModelConfig().getProvider()
            );

            DimensionResult result = provider.reviewDimension(
                task.getCodeContext(),
                dimension,
                task.getPromptTemplate(dimension)
            );

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Completed {} review in {}ms for task {}",
                      dimension, duration, task.getId());

            return result;

        } catch (Exception e) {
            log.error("Failed {} review for task {}", dimension, task.getId(), e);
            return DimensionResult.failed(dimension, e.getMessage());

        } finally {
            // Always release permit
            concurrencyLimiter.release();
        }
    }

    private ReviewResult aggregatePartialResults(
            String taskId,
            List<CompletableFuture<DimensionResult>> futures) {
        List<DimensionResult> completedResults = futures.stream()
            .filter(f -> f.isDone() && !f.isCompletedExceptionally())
            .map(CompletableFuture::join)
            .collect(Collectors.toList());

        if (completedResults.isEmpty()) {
            return ReviewResult.failed(taskId, "No dimensions completed");
        }

        ReviewResult result = aggregator.aggregate(taskId, completedResults);
        result.setWarning(
            String.format("Partial review: %d/%d dimensions completed",
                         completedResults.size(), 6)
        );
        return result;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

#### Error Handling Strategy

**Error Isolation**: One dimension failure does NOT fail entire review

**Failure Scenarios**:

| Scenario | Behavior | Result |
|----------|----------|--------|
| **1/6 dimensions fails** | Continue with other 5 | Return 5 dimension results + warning |
| **3/6 dimensions fail** | Continue with other 3 | Return 3 dimension results + warning |
| **All 6 dimensions fail** | Mark review as failed | Return failure with error details |
| **Timeout (60s)** | Cancel incomplete dimensions | Return completed results + timeout warning |

**Example Partial Result**:
```json
{
  "task_id": "abc-123",
  "status": "partial",
  "warning": "Timeout: 4/6 dimensions completed",
  "dimensions": [
    {
      "type": "security",
      "status": "completed",
      "issues": [...]
    },
    {
      "type": "performance",
      "status": "completed",
      "issues": [...]
    },
    {
      "type": "maintainability",
      "status": "completed",
      "issues": [...]
    },
    {
      "type": "correctness",
      "status": "completed",
      "issues": [...]
    },
    {
      "type": "style",
      "status": "timeout",
      "error": "Review timeout after 60s"
    },
    {
      "type": "best_practices",
      "status": "not_started",
      "error": "Cancelled due to timeout"
    }
  ]
}
```

#### Timeout Strategy

**Per-Dimension Timeout**: 45 seconds

**Total Review Timeout**: 60 seconds

**Rationale**:
- Typical AI API call: 10-30 seconds
- 45s per dimension allows for retries (15s buffer)
- 60s total ensures review doesn't block queue indefinitely
- In practice, with 3 concurrent: 60s allows ~18 sequential calls (3 × 6 dimensions)

**Timeout Implementation**:
```java
CompletableFuture<DimensionResult> future = CompletableFuture
    .supplyAsync(() -> reviewDimension(task, dimension), executorService)
    .orTimeout(45, TimeUnit.SECONDS)
    .exceptionally(ex -> {
        if (ex instanceof TimeoutException) {
            log.warn("Dimension {} timeout for task {}", dimension, task.getId());
            return DimensionResult.timeout(dimension);
        }
        return DimensionResult.failed(dimension, ex.getMessage());
    });
```

#### Performance Monitoring

**Metrics to Track**:

| Metric | Description | Target |
|--------|-------------|--------|
| `review.dimension.latency` | Time per dimension (by type) | p95 < 30s |
| `review.total.latency` | Total review time (all 6 dimensions) | p95 < 35s |
| `review.concurrency.queue` | Threads waiting for semaphore permit | < 3 (no blocking) |
| `review.dimension.failure_rate` | Percentage of dimension failures | < 5% |
| `review.partial.rate` | Percentage of partial reviews (timeout/errors) | < 10% |

**Micrometer Metrics**:
```java
@Timed(value = "review.dimension.latency",
       extraTags = {"dimension", "#p1.name()"})
public DimensionResult reviewDimension(ReviewTask task, DimensionType dimension) {
    // ... implementation
}

@Timed(value = "review.total.latency")
public ReviewResult conductSixDimensionReview(ReviewTask task) {
    // ... implementation
}

@Gauge(name = "review.concurrency.available",
       description = "Available semaphore permits")
public int getAvailablePermits() {
    return concurrencyLimiter.availablePermits();
}
```

#### Configuration Tuning

**Application Properties**:
```yaml
ai:
  review:
    concurrency:
      max-concurrent-dimensions: ${AI_MAX_CONCURRENT:3}
      thread-pool-size: 6
      dimension-timeout: 45s
      total-timeout: 60s
    dimensions:
      enabled: [security, performance, maintainability, correctness, style, best_practices]
      optional: [style, best_practices]  # Can skip if timeout approaching
```

**Dynamic Adjustment** (Future Enhancement):
```java
// Reduce concurrency if high failure rate detected
if (dimensionFailureRate > 0.15) {
    maxConcurrent = Math.max(1, maxConcurrent - 1);
    log.warn("High failure rate detected, reducing concurrency to {}",
             maxConcurrent);
}

// Increase concurrency if consistently fast
if (avgDimensionLatency < 10000 && dimensionFailureRate < 0.05) {
    maxConcurrent = Math.min(6, maxConcurrent + 1);
    log.info("Low latency detected, increasing concurrency to {}",
             maxConcurrent);
}
```

#### Testing Strategy

**Unit Tests**:
- Mock 6 AI provider calls with varying latencies (5s, 10s, 15s, 20s, 25s, 30s)
- Verify max 3 concurrent calls at any time (semaphore enforcement)
- Test timeout scenario (simulate 1 dimension taking > 45s)
- Test failure isolation (simulate 2 dimensions failing)

**Performance Tests**:
- Benchmark sequential vs parallel execution (expect 5-6x speedup)
- Measure actual concurrency level (should not exceed 3)
- Test with 10 concurrent reviews (60 dimension calls total)

**Integration Tests**:
- Test with real AI providers (OpenAI + Anthropic)
- Verify no rate limit errors with max concurrency = 3
- Measure end-to-end latency (expect < 40s for 100 lines of code)

#### Implementation Checklist

- [ ] Create `SixDimensionReviewOrchestrator` service
- [ ] Implement semaphore-based concurrency limiting
- [ ] Add per-dimension timeout (45s)
- [ ] Add total review timeout (60s)
- [ ] Implement partial result aggregation
- [ ] Add Micrometer metrics for latency and concurrency
- [ ] Add configuration properties for concurrency tuning
- [ ] Write unit tests for parallel execution
- [ ] Write integration tests with real AI providers
- [ ] Document concurrency behavior in API docs
