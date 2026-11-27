# RepoMap.ai

**AI-powered codebase understanding and exploration.**

Import any repository, parse its structure, build a dependency graph, search symbols,
and run impact analysis — all from a simple web interface.

## Why RepoMap.ai?

Join any new codebase and spend minutes instead of hours answering the same questions:

- *What is the structure of this project?*
- *Where are the API endpoints defined?*
- *What depends on this file if I change it?*
- *Which service talks to which repository?*
- *Where is authentication implemented?*

RepoMap.ai answers those questions automatically, grounded in real source code.

## Quick Start

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

Import a ZIP archive of any source code and instantly get:

- ✅ Secure extraction with path-traversal protection
- ✅ Full file inventory with language detection
- ✅ Symbol and endpoint extraction
- ✅ Search across all code
- ✅ Impact analysis before you change anything
- ✅ Optional AI answers grounded in real references

## Core Features

| Feature | Description |
|---|---|
| **Secure Import** | ZIP uploads extracted safely — no path traversal, no code execution |
| **Dependency Graph** | Auto-detect Maven dependencies, classes, methods, HTTP endpoints |
| **Code Search** | Find symbols, files, and patterns across the entire repository |
| **Impact Analysis** | See what breaks before you make a change |
| **AI Assistant** | Optional AI answers grounded in real source references |
| **Cross-User Isolation** | Access control ensures users only see their own projects |

## Technology Stack

- **Backend**: Java 21, Spring Boot 3.5, Spring Data JPA, Spring Security, PostgreSQL, Flyway
- **Frontend**: React 18, Vite, TypeScript, Tailwind CSS, React Flow, Lucide
- **DevOps**: Docker Compose, Maven Wrapper, GitHub Actions compatible

## License

MIT

---

*Understand any repository in minutes, not hours.*