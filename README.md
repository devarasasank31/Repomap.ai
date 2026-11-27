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

## Do we use AI in it?

**Yes — but it's optional and never required.**

The `.ai` in the name reflects the AI-assisted features we've built on top of a solid
codebase analysis foundation. Here's the detail:

- **AI is a pluggable layer** — it is disabled by default (`AI_ENABLED=false`).
- **No hardcoded secrets** — configuration comes from environment variables:
  `AI_ENABLED`, `AI_BASE_URL`, `AI_API_KEY`, `AI_MODEL`.
- **Stub service when disabled** — if `AI_ENABLED=false`, every query returns a friendly
  message: *"AI assistant is disabled. Set AI_ENABLED=true and provide AI_API_KEY to enable."*
- **Grounded in real code when enabled** — when `AI_ENABLED=true`, the AI answers questions
  about the codebase and always provides citations with exact file paths and line numbers,
  so you can verify the answer yourself.
- **The core value works without AI** — secure ZIP extraction, dependency graph, code search,
  impact analysis, and cross-user isolation all function with the AI turned off.

**Why the `.ai` suffix then?**

Because the AI assistant is a differentiator — it's the feature that lets you ask natural
language questions like *"What endpoint handles user authentication?"* or *"What would break
if I remove this class?"* and get answers rooted in your actual code rather than guessing.

Without the AI, RepoMap.ai is still a powerful codebase analysis tool (secure import, graph,
search, impact analysis). With the AI enabled, it becomes a conversational codebase assistant.

---

**Import a ZIP of any source code and instantly get:**

- ✅ Secure extraction with path-traversal protection
- ✅ Full file inventory with language detection
- ✅ Symbol and endpoint extraction
- ✅ Code search across all files, symbols, and patterns
- ✅ Impact analysis before you change anything
- ✅ Optional AI answers referenced to exact source lines

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