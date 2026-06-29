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
  it('adds the current route UI ID to every GET request', async () => {
    const client = createHttpClient({
      getPathname: () => '/users',
      adapter: echoAdapter,
    });

    const response = await client.get('/api/jpa/users');

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
    expect(response.config.headers.get('X-Sql-Capture-Paused')).toBe('true');
  });

  it('does not add a UI ID header to mutation requests', async () => {
    const client = createHttpClient({
      getPathname: () => '/menus',
      adapter: echoAdapter,
    });

    const response = await client.post('/api/jpa/menus/grid-save', {});

    expect(response.config.headers.has('X-Ui-Id')).toBe(false);
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

  it('reports GET timing when the backend returns a request ID', async () => {
    const onTiming = vi.fn();
    const adapter = async (config) => ({
      data: [],
      status: 200,
      statusText: 'OK',
      headers: { 'x-request-id': '2de4a7d7-1453-4652-85dd-ef8ddfa57467' },
      config,
    });
    const client = createHttpClient({
      getPathname: () => '/roles',
      adapter,
      onTiming,
      now: vi.fn().mockReturnValueOnce(10).mockReturnValueOnce(35),
    });

    await client.get('/api/jpa/roles');

    expect(onTiming).toHaveBeenCalledWith({
      requestId: '2de4a7d7-1453-4652-85dd-ef8ddfa57467',
      uiId: 'roles-workbench',
      clientApiElapsedMillis: 25,
      clientTotalElapsedMillis: 25,
    });
  });
});
