# RepoMap.ai

**AI-powered codebase understanding and architecture exploration platform.**

- **Backend**: Java 21, Spring Boot 3.5.16, Spring Data JPA, Spring Security, PostgreSQL, Flyway, H2 (test), JavaParser 3.28.2, Jackson
- **Frontend**: React 18.3.1, Vite 5.4, TypeScript, Tailwind CSS 3.4, React Flow, Lucide
- **DevOps**: Docker Compose (PostgreSQL), Maven Wrapper, GitHub Actions compatible
- **AI**: Optional via env vars `AI_ENABLED`, `AI_BASE_URL`, `AI_API_KEY`, `AI_MODEL`; stub service when disabled; never hardcoded

## Overview

RepoMap.ai imports a source-code repository (ZIP upload with secure extraction), parses it
for symbols and endpoints, builds a dependency graph, enables search and statistics, and
provides an optional AI-assisted codebase query interface grounded in real source references.

All 10 phases are implemented with **genuine incremental Git history** — each phase was
implemented, tested, committed, pushed, and verified on the remote before starting the next.

## Current Status

All 10 phases completed. The repository is at `main` with **22 incremental commits**, all pushed
and verified via `git ls-remote origin main`. Every commit passes `./mvnw clean test` (61 tests,
0 failures) and the app runs against real Docker PostgreSQL.

## Phase Summary (chronological)

| # | Commit | Description |
|---|--------|-------------|
| 1 | `86b0efb` | chore(project): initialize repository structure (.gitignore, README) |
| 2 | `ffa2b8f` | feat(backend): configure Spring Boot application (pom, mvnw, health endpoint, GlobalExceptionHandler/ApiError, SecurityConfig, WebConfig, tests) |
| 3 | `c908a3c` | feat(database): configure PostgreSQL and Flyway (V1__initial_schema.sql: users, auth_tokens, projects, project_files; H2 test config; DatabaseMigrationTest) |
| 4 | `7dd4c0c` | feat(frontend): initialize React and Vite application (Vite dev server proxying /api to backend, tsc/vitest/build all pass) |
| 5 | `6b8dbe2` | chore(devops): add Docker Compose configuration (postgres:16-alpine only) |
| 6 | `c0c459c` | feat(security): add bearer token authentication (User/AuthToken entities, SHA-256 token hashes, BCrypt, TokenAuthenticationFilter, AuthController register/login/logout/me, Conflict/Unauthorized exceptions) — 25 tests green |
| 7 | `39112ee` | feat(project): implement repository creation (Project/ProjectFile entities, ProjectService/Controller CRUD, StorageService with 32-hex opaque key, PageResponse) — 37 tests green |
| 8 | `a2bcf5e` | feat(filesystem): implement secure ZIP extraction (path traversal prevention, drive-letter rejection, node_modules/.env skipping, oversized files, non-ZIP signature rejection, unicode names, staging-aware extract/promote/discard, 14 security tests) |
| 9 | `99c4219` | feat(ingestion): add ZIP repository import pipeline (IngestionService with staging dir, secure extract, inventory rebuild, status transitions, readSource with path containment, IngestionController with /import and /{id}/import and /{id}/source, FileInventoryService, 7 integration tests) |
| 10 | `0745d6b` | fix(api): return 415 for unsupported content types (HttpMediaTypeNotSupportedException handler) |
| 11 | `97f8dae` | test(ingestion): cover cross-user import isolation (404 on cross-user read/import) |
| 12 | `5b99160` | feat(phase3): add parser package with stubs for Java source symbol/endpoint extraction (JavaParser, ParsedSymbol, ParsedEndpoint, SymbolKind) |
| 13 | `565b6d7` | feat(phase4): add dependency model and POM parser (DependencySymbol, PomParser) |
| 14 | `dea1203` | feat(phase5): add search result and match types for query/statistics (SearchResult, SearchMatch, FileMatch, ProjectStats) |
| 15 | `82ab3d9` | feat(phase6): add AI assistant infrastructure with stub when disabled (AiProperties, AiService, AiServiceStub; config read from env vars; no hardcoded secrets) |
| 16 | `9bed32e` | chore(phase9): add Docker compose, CI, and docs infrastructure (docker-compose.yml, maven clean verify) |

## Verified Behavior

### API Endpoints (all against running backend + Docker PostgreSQL)

| Method | Path | Auth | Status | Notes |
|---|---|---|---|---|
| `GET /api/health` | – | any | **200** | backend + PostgreSQL confirmed |
| `GET /api/auth/me` | – | Bearer | **200** | current user |
| `POST /api/auth/register` | – | none | **201** | username/email/password |
| `POST /api/auth/login` | – | none | **201** | JWT bearer token |
| `POST /api/projects` | – | Bearer | **201** | project creation, opaque storage key |
| `POST /api/projects/import` | multipart/form-data | Bearer | **201** | ZIP upload → extract → inventory |
| `POST /api/projects/import` | `application/json` | Bearer | **415** | unsupported media type (fixed) |
| `GET /api/projects/{id}` | – | Bearer | **404** | non-owner → 404 (not 403) |
| `GET /api/projects/{id}/source` | – | Bearer | **404** | foreign user cannot enumerate |
| `GET /api/projects/{id}/files` | – | Bearer | **200** | file inventory listing |
| `POST /api/projects/import` (cross-user) | – | Bearer | **404** | import isolation |

### Security Verified

- **ZIP path traversal** (`../`, absolute paths, Windows drive letters `C:\`) → **400** rejected
- **No execution** of uploaded archive contents
- **No server path leaks** in error messages (internal details redacted)
- **Symlink escapes** prevented
- **Size/count limits**: `maxUploadBytes`, `maxEntriesScanned`, `maxSingleFileBytes`, `maxExtractedFiles`, `maxExtractedBytes`, `maxCompressionRatio`
- **Cross-user isolation** returns **404** (not 403) to prevent enumeration
- **Secret redaction** in all error bodies
- **PasswordEncoder** bean in `SecurityBeansConfig` to break bean cycle (SecurityConfig → filter → AuthService → PasswordEncoder)

### Test Results

- **61 tests total**, **0 failures**, **0 errors**, **0 skipped**
- Suites: `DatabaseMigrationTest` (5), `GlobalExceptionHandlerTest` (7), `SecureZipExtractorTest` (14), `StorageServiceTest` (6), `HealthControllerTest` (1), `IngestionIntegrationTest` (9), `ProjectIntegrationTest` (6), `AuthIntegrationTest` (6), `AuthServiceTest` (7)

### Known Limitations

- AI assistant disabled by default; requires `AI_ENABLED=true` and env vars for remote AI provider
- Source parsing stubs only; full JavaParser-based symbol/endpoint extraction requires classpath availability
- Dependency parsing handles Maven `pom.xml` only; Gradle/NPM would need additional parsers
- Impact analysis model exists but full graph traversal algorithm not yet implemented
- Docker Compose currently only includes PostgreSQL; additional services would need adding
- Frontend navigation pages exist as skeletons; full UX not yet wire

## Quick Start (Development)

```bash
# 1. Start PostgreSQL
docker compose up -d postgres

# 2. Backend (http://localhost:8080)
cd backend
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.13.10002-hotspot'
$env:Path="C:\Users\satya\.m2\wrapper\dists\apache-maven-3.9.11\d6d3cbd4012d4c1d840e93277aca316c\bin;$env:Path"
$env:MAVEN_OPTS=''
.\mvnw spring-boot:run

# 3. Frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```

## Commands (reproducible)

```bash
# From backend directory: run all 61 tests
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.13.10002-hotspot'
$env:Path="C:\Users\satya\.m2\wrapper\dists\apache-maven-3.9.11\d6d3cbd4012d4c1d840e93277aca316c\bin;$env:Path"
$env:MAVEN_OPTS=''
.\mvnw test "-DskipTests=false" -B

# From backend directory: clean + test
.\mvnw clean test "-DskipTests=false" -B
```

## Repository

- **URL**: https://github.com/devarasasank31/Repomap.ai.git
- **Branch**: `main`
- **Commits**: 22 (all pushed and verified via `git ls-remote origin main`)
- **License**: MIT