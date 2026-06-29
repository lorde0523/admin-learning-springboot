import { describe, expect, it, vi } from 'vitest';

import { createAdminApi } from './adminApi';

const createClient = () => ({
  get: vi.fn().mockResolvedValue({ data: { content: [] } }),
  post: vi.fn().mockResolvedValue({ data: { id: 1 } }),
  put: vi.fn().mockResolvedValue({ data: { id: 1 } }),
  delete: vi.fn().mockResolvedValue({ data: undefined }),
});

describe('createAdminApi', () => {
  it('keeps user transport details behind domain functions', async () => {
    const client = createClient();
    const api = createAdminApi(client);

    await api.users.search('adm');
    await api.users.assignRoles(7, [1, 3]);

    expect(client.get).toHaveBeenCalledWith('/api/jpa/users', {
      params: { loginKeyword: 'adm' },
    });
    expect(client.put).toHaveBeenCalledWith('/api/jpa/users/7/roles', {
      roleIds: [1, 3],
    });
  });

  it('uses identical page parameters for JPA and MyBatis menu searches', async () => {
    const client = createClient();
    const api = createAdminApi(client);
    const criteria = { nameKeyword: 'admin', page: 2, size: 20, sort: 'sortOrder,asc' };

    await api.menus.searchPage('jpa', criteria);
    await api.menus.searchPage('mybatis', criteria);

    expect(client.get).toHaveBeenNthCalledWith(1, '/api/jpa/menus/page', {
      params: criteria,
    });
    expect(client.get).toHaveBeenNthCalledWith(2, '/api/mybatis/menus/page', {
      params: criteria,
    });
  });
});
