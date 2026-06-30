import { beforeEach, describe, expect, it, vi } from 'vitest';

const { httpClient } = vi.hoisted(() => ({
  httpClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('./httpClient', () => ({ httpClient }));

import {
  assignUserRoles,
  clearSqlLogs,
  getSqlLogs,
  searchMenus,
  searchUsers,
} from './adminApi';

describe('adminApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls user APIs with async functions and returns response data', async () => {
    httpClient.get.mockResolvedValue({ data: { content: [{ id: 7 }] } });
    httpClient.put.mockResolvedValue({ data: { id: 7 } });

    await expect(searchUsers('adm')).resolves.toEqual({ content: [{ id: 7 }] });
    await expect(assignUserRoles(7, [1, 3])).resolves.toEqual({ id: 7 });

    expect(httpClient.get).toHaveBeenCalledWith('/api/jpa/users', {
      params: { loginKeyword: 'adm' },
    });
    expect(httpClient.put).toHaveBeenCalledWith('/api/jpa/users/7/roles', {
      roleIds: [1, 3],
    });
  });

  it('uses identical page parameters for JPA and MyBatis menu searches', async () => {
    httpClient.get.mockResolvedValue({ data: { content: [] } });
    const criteria = { nameKeyword: 'admin', page: 2, size: 20, sort: 'sortOrder,asc' };

    await searchMenus('jpa', criteria);
    await searchMenus('mybatis', criteria);

    expect(httpClient.get).toHaveBeenNthCalledWith(1, '/api/jpa/menus/page', {
      params: criteria,
    });
    expect(httpClient.get).toHaveBeenNthCalledWith(2, '/api/mybatis/menus/page', {
      params: criteria,
    });
  });

  it('identifies SQL log resources by trace type and UI ID', async () => {
    httpClient.get.mockResolvedValue({ data: [] });
    httpClient.delete.mockResolvedValue({ data: undefined });

    await getSqlLogs('users-workbench');
    await clearSqlLogs('users-workbench');

    expect(httpClient.get).toHaveBeenCalledWith('/api/sql-logs', {
      params: { traceType: 'query', uiId: 'users-workbench' },
    });
    expect(httpClient.delete).toHaveBeenCalledWith('/api/sql-logs', {
      params: { traceType: 'query', uiId: 'users-workbench' },
    });
  });
});
