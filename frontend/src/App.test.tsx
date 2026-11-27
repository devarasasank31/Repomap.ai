import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <App />
    </MemoryRouter>,
  );
}

describe('App routing', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the landing page at /', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ status: 'UP', service: 'repomap-backend', timestamp: '2026-01-01T00:00:00Z' }),
      }),
    );

    renderAt('/');
    expect(screen.getByRole('heading', { name: /understand any repository/i })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('api-status')).toHaveTextContent('API up'));
  });

  it('shows an error state on the dashboard when the backend is down', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(new Error('network down')),
    );

    renderAt('/app');
    await waitFor(() =>
      expect(screen.getByTestId('health-card')).toHaveTextContent(/backend unreachable: network down/i),
    );
  });

  it('redirects unknown routes to the landing page', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ status: 'UP', service: 'repomap-backend', timestamp: '2026-01-01T00:00:00Z' }),
      }),
    );

    renderAt('/does-not-exist');
    expect(screen.getByRole('heading', { name: /understand any repository/i })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('api-status')).toHaveTextContent('API up'));
  });
});
