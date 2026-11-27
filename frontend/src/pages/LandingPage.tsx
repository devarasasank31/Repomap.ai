import { useEffect, useState } from 'react';
import { Activity, ArrowRight, GitBranch, Network, Search, Sparkles, Waypoints } from 'lucide-react';
import { fetchHealth, type HealthResponse } from '../lib/api';

const capabilities = [
  {
    icon: Waypoints,
    title: 'Import a repository',
    text: 'Upload a source archive and get a secure, read-only extraction with a full file inventory.',
  },
  {
    icon: Network,
    title: 'Map the architecture',
    text: 'Parse classes, services and endpoints, then render the dependency graph interactively.',
  },
  {
    icon: Search,
    title: 'Search everything',
    text: 'Find symbols, routes and files with path and line-level references.',
  },
  {
    icon: Sparkles,
    title: 'Ask your codebase',
    text: 'Optional AI answers grounded in retrieved source snippets — with references attached.',
  },
];

export default function LandingPage() {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [healthError, setHealthError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchHealth()
      .then((value) => {
        if (!cancelled) setHealth(value);
      })
      .catch((error: unknown) => {
        if (!cancelled) setHealthError(error instanceof Error ? error.message : 'Backend unreachable');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-800/80 bg-slate-950/80 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4">
          <div className="flex items-center gap-2 text-sm font-semibold tracking-tight">
            <span className="flex h-7 w-7 items-center justify-center rounded-md bg-emerald-500 text-slate-950">
              <GitBranch className="h-4 w-4" aria-hidden />
            </span>
            RepoMap.ai
          </div>
          <div
            className="flex items-center gap-2 text-xs"
            data-testid="api-status"
            title={healthError ?? 'Backend health status'}
          >
            <span
              className={`h-2 w-2 rounded-full ${health ? 'bg-emerald-400' : 'bg-amber-400'}`}
              aria-hidden
            />
            <span className="font-mono text-slate-400">
              {health ? `API ${health.status.toLowerCase()}` : healthError ? 'API unreachable' : 'checking API…'}
            </span>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4">
        <section className="py-20 text-center">
          <p className="mx-auto mb-4 inline-flex items-center gap-2 rounded-full border border-slate-800 bg-slate-900 px-3 py-1 text-xs text-slate-400">
            <Activity className="h-3.5 w-3.5 text-emerald-400" aria-hidden />
            AI-powered codebase understanding
          </p>
          <h1 className="mx-auto max-w-3xl text-4xl font-semibold tracking-tight sm:text-5xl">
            Understand any repository in minutes, not hours
          </h1>
          <p className="mx-auto mt-5 max-w-2xl text-slate-400">
            Import a project, explore its architecture as a living graph, search every symbol, run impact
            analysis before you change a file, and ask questions that are answered from real source code.
          </p>
          <div className="mt-8 flex items-center justify-center gap-3">
            <a href="/app" className="btn-primary">
              Open dashboard <ArrowRight className="h-4 w-4" aria-hidden />
            </a>
            <a href="/app" className="btn-ghost">
              Import a repository
            </a>
          </div>
        </section>

        <section className="grid gap-4 pb-20 sm:grid-cols-2 lg:grid-cols-4">
          {capabilities.map((item) => (
            <div key={item.title} className="card p-5">
              <item.icon className="h-5 w-5 text-emerald-400" aria-hidden />
              <h3 className="mt-3 text-sm font-semibold">{item.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-slate-400">{item.text}</p>
            </div>
          ))}
        </section>
      </main>

      <footer className="border-t border-slate-800/80 py-6 text-center text-xs text-slate-500">
        RepoMap.ai — Java 21 · Spring Boot · PostgreSQL · React
      </footer>
    </div>
  );
}
