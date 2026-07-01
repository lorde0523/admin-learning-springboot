import { describe, expect, it, vi } from 'vitest';

import { createHttpClient } from './httpClient';

const echoAdapter = async (config) => ({
  data: { ok: true },
  status: 200,
  statusText: 'OK',
  headers: {},
  config,
});

describe('createHttpClient', () => {
  it('prefers the active tab UI ID over the pathname registry', async () => {
    const client = createHttpClient({
      getPathname: () => '/users',
      getActiveTab: () => ({ uiId: 'role-popup-instance' }),
      adapter: echoAdapter,
    });

    const response = await client.get('/api/jpa/roles');

    expect(response.config.headers.get('X-Ui-Id')).toBe('role-popup-instance');
  });

  it('adds the current route UI ID to every GET request', async () => {
    const client = createHttpClient({
      getPathname: () => '/users',
      adapter: echoAdapter,
    });

    const response = await client.get('/api/jpa/users');

    expect(response.config.headers.get('X-Trace-Type')).toBe('query');
    expect(response.config.headers.get('X-Ui-Id')).toBe('users-workbench');
    expect(response.config.withCredentials).toBe(true);
  });

  it('pauses capture when reading SQL logs', async () => {
    const client = createHttpClient({
      getPathname: () => '/menus',
      adapter: echoAdapter,
    });

    const response = await client.get('/api/sql-logs', {
      params: { uiId: 'menus-workbench' },
    });

    expect(response.config.headers.get('X-Ui-Id')).toBe('menus-workbench');
    expect(response.config.headers.get('X-Trace-Type')).toBe('query');
    expect(response.config.headers.get('X-Sql-Capture-Paused')).toBe('true');
  });

  it('does not add a UI ID header to mutation requests', async () => {
    const client = createHttpClient({
      getPathname: () => '/menus',
      adapter: echoAdapter,
    });

    const response = await client.post('/api/jpa/menus/grid-save', {});

    expect(response.config.headers.has('X-Ui-Id')).toBe(false);
    expect(response.config.headers.has('X-Trace-Type')).toBe(false);
  });

  it('publishes response metadata for the inspector', async () => {
    const onResponse = vi.fn();
    const client = createHttpClient({
      getPathname: () => '/users',
      adapter: echoAdapter,
      onResponse,
      now: vi.fn().mockReturnValueOnce(100).mockReturnValueOnce(128),
    });

    await client.get('/api/jpa/users');

    expect(onResponse).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'GET',
        url: '/api/jpa/users',
        status: 200,
        elapsedMillis: 28,
        data: { ok: true },
      }),
    );
  });

  it('reports client and total timing after rendering when the backend returns apiStartedAt', async () => {
    const onTiming = vi.fn();
    const adapter = async (config) => ({
      data: [],
      status: 200,
      statusText: 'OK',
      headers: { 'x-api-started-at': '2026-07-01T10:30:15.100+09:00' },
      config,
    });
    const client = createHttpClient({
      getPathname: () => '/roles',
      adapter,
      onTiming,
      afterRender: (callback) => callback(),
      now: vi.fn()
        .mockReturnValueOnce(10)
        .mockReturnValueOnce(35)
        .mockReturnValueOnce(50),
    });

    await client.get('/api/jpa/roles');

    expect(onTiming).toHaveBeenCalledWith({
      traceType: 'query',
      apiStartedAt: '2026-07-01T10:30:15.100+09:00',
      uiId: 'roles-workbench',
      clientTimeMillis: 15,
      totalTimeMillis: 40,
    });
  });
});
