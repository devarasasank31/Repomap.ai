# RepoMap.ai

**AI-powered codebase understanding and architecture exploration.**

RepoMap.ai lets you import a source-code repository, parse it, build a dependency
graph, explore the architecture visually, search symbols, run impact analysis, and
ask natural-language questions about the codebase — grounded in real source
references.

> **Status:** active development. See [Roadmap](#roadmap) for the current phase.

---

## Why

Developers joining an unfamiliar repository spend hours answering the same
questions:

- What is the structure of this project?
- Where are the API endpoints defined?
- What depends on this file if I change it?
- Which service talks to which repository?
- Where is authentication implemented?

RepoMap.ai answers those questions automatically.

## Roadmap

| Phase | Scope | Status |
| ----- | ----- | ------ |
| 1 | Project foundation (Spring Boot, React, PostgreSQL, Flyway, Docker Compose) | in progress |
| 2 | Repository import (ZIP upload, secure extraction, file inventory) | pending |
| 3 | Source code parsing (Java/Spring, JS/TS) | pending |
| 4 | Dependency graph engine + impact analysis | pending |
| 5 | Search and codebase indexing | pending |
| 6 | Optional AI codebase assistant | pending |
| 7 | Frontend product experience | pending |
| 8 | Quality, security and performance | pending |
| 9 | Docker, CI and documentation | pending |
| 10 | Final end-to-end verification and release | pending |

## Repository layout

```
Repomap.ai/
├── backend/     Java 21 · Spring Boot 3 · PostgreSQL · Flyway
├── frontend/    React · Vite · TypeScript · Tailwind CSS · React Flow
├── docker-compose.yml
└── README.md
```

## Quick start (development)

```bash
# 1. Start PostgreSQL
docker compose up -d postgres

# 2. Backend (http://localhost:8080)
cd backend
./mvnw spring-boot:run

# 3. Frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```

Detailed setup, environment variables and API documentation will be expanded as
the phases land.

## License

MIT
