import { useEffect, useState } from 'react';
import { Boxes, GitBranch, Loader2, Search, TriangleAlert } from 'lucide-react';
import { fetchHealth, type HealthResponse } from '../lib/api';

/**
 * Placeholder dashboard for Phase 1: it renders live backend health data and
 * the module tiles that the later phases will turn into real screens.
 * No metric here is hardcoded — everything comes from the API.
 */
export default function DashboardPage() {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    fetchHealth()
      .then((value) => {
        if (!cancelled) setHealth(value);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Backend unreachable');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const tiles = [
    { icon: Boxes, label: 'Projects', hint: 'Phase 2' },
    { icon: GitBranch, label: 'Architecture graph', hint: 'Phase 4' },
    { icon: Search, label: 'Code search', hint: 'Phase 5' },
  ];

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-800/80 bg-slate-950/80 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4">
          <a href="/" className="flex items-center gap-2 text-sm font-semibold">
            <span className="flex h-7 w-7 items-center justify-center rounded-md bg-emerald-500 text-slate-950">
              <GitBranch className="h-4 w-4" aria-hidden />
            </span>
            RepoMap.ai
          </a>
          <span className="text-xs text-slate-500">Dashboard</span>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-10" data-testid="dashboard">
        <h1 className="text-2xl font-semibold tracking-tight">Repository overview</h1>
        <p className="mt-1 text-sm text-slate-400">Import a repository to start mapping its architecture.</p>

        <div className="card mt-6 flex items-center gap-3 p-4" data-testid="health-card">
          {loading ? (
            <>
              <Loader2 className="h-4 w-4 animate-spin text-slate-400" aria-hidden />
              <span className="text-sm text-slate-400">Contacting backend…</span>
            </>
          ) : error ? (
            <>
              <TriangleAlert className="h-4 w-4 text-amber-400" aria-hidden />
              <span className="text-sm text-amber-300">Backend unreachable: {error}</span>
            </>
          ) : (
            <>
              <span className="h-2 w-2 rounded-full bg-emerald-400" aria-hidden />
              <span className="text-sm text-slate-300">
                Backend {health?.status} · {health?.service}
              </span>
              <span className="ml-auto font-mono text-xs text-slate-500">{health?.timestamp}</span>
            </>
          )}
        </div>

        <div className="mt-6 grid gap-4 sm:grid-cols-3">
          {tiles.map((tile) => (
            <div key={tile.label} className="card p-5">
              <tile.icon className="h-5 w-5 text-slate-400" aria-hidden />
              <p className="mt-3 text-sm font-semibold">{tile.label}</p>
              <p className="mt-1 text-xs text-slate-500">{tile.hint}</p>
            </div>
          ))}
        </div>
      </main>
    </div>
  );
}
