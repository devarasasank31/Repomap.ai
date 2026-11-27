# RepoMap.ai

**AI-powered codebase understanding and exploration.**

Import any repository, parse its structure, build a dependency graph, search symbols,
and run impact analysis — all from a simple web interface.

## Why RepoMap.ai?

Every developer joining an unfamiliar repository wastes hours on the same questions:

- **What is the structure?** — Countless `README.md` files and directory traversals.
- **Where are the endpoints?** — Hunting through layers of Spring controllers or Express routers.
- **What depends on this?** — Guessing which services or files will break after a change.
- **Which code owns that feature?** — Blame messages, outdated wikis, silent assumptions.

RepoMap.ai answers those questions automatically, grounded in real source code rather
than documentation that falls out of date.

**Import a ZIP of any codebase and instantly get:**

- ✅ Secure extraction with path-traversal protection — no execution, no filesystem leaks
- ✅ Full file inventory with language detection — know exactly what's in the repo
- ✅ Symbol and endpoint extraction — classes, methods, HTTP routes, APIs
- ✅ Code search across all files, symbols, and patterns — find anything, anywhere
- ✅ Impact analysis before you change anything — see the ripple effect of your changes
- ✅ Optional AI answers referenced to exact source lines — ask questions and get answers
  with citations

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

Import a ZIP archive of any source code and instantly get the features above.

## Core Features

| Feature | What It Does |
|---|---|
| **Secure Import** | ZIP uploads extracted safely — no path traversal, no code execution, size/count limits enforced |
| **Dependency Graph** | Auto-detect Maven dependencies, classes, methods, HTTP endpoints — visualize the connections |
| **Code Search** | Find symbols, files, and patterns across the entire repository with path and line references |
| **Impact Analysis** | See what breaks before you make a change — trace dependencies and downstream effects |
| **AI Assistant** | Optional AI answers grounded in real source references — ask questions and get citations |
| **Cross-User Isolation** | Access control ensures users only see and import their own projects |

## License

MIT

---

*Understand any repository in minutes, not hours.*