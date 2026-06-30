import { httpClient } from './httpClient';

const data = (response) => response.data;

/**
 * Builds framework-agnostic domain functions around an Axios-compatible client.
 * React Query consumes this object, while components never import it directly.
 */
export const createAdminApi = (client) => ({
  auth: {
    me: () => client.get('/api/auth/me').then(data),
  },
  users: {
    search: (loginKeyword = '') =>
      client.get('/api/jpa/users', { params: { loginKeyword } }).then(data),
    find: (id) => client.get(`/api/jpa/users/${id}`).then(data),
    create: (request) => client.post('/api/jpa/users', request).then(data),
    update: (id, request) => client.put(`/api/jpa/users/${id}`, request).then(data),
    remove: (id) => client.delete(`/api/jpa/users/${id}`).then(data),
    assignRoles: (id, roleIds) =>
      client.put(`/api/jpa/users/${id}/roles`, { roleIds }).then(data),
  },
  roles: {
    list: () => client.get('/api/jpa/roles').then(data),
    find: (id) => client.get(`/api/jpa/roles/${id}`).then(data),
    create: (request) => client.post('/api/jpa/roles', request).then(data),
    update: (id, request) => client.put(`/api/jpa/roles/${id}`, request).then(data),
    remove: (id) => client.delete(`/api/jpa/roles/${id}`).then(data),
    menus: (id) => client.get(`/api/jpa/roles/${id}/menus`).then(data),
    assignMenus: (id, menuIds) =>
      client.put(`/api/jpa/roles/${id}/menus`, { menuIds }).then(data),
  },
  menus: {
    list: (nameKeyword = '') =>
      client.get('/api/jpa/menus', { params: { nameKeyword } }).then(data),
    searchPage: (implementation, criteria) =>
      client
        .get(`/api/${implementation}/menus/page`, { params: criteria })
        .then(data),
    saveGrid: (changeSet) =>
      client.post('/api/jpa/menus/grid-save', changeSet).then(data),
  },
  sqlLogs: {
    list: (uiId) =>
      client.get('/api/sql-logs', { params: { traceType: 'query', uiId } }).then(data),
    clear: (uiId) =>
      client.delete('/api/sql-logs', { params: { traceType: 'query', uiId } }).then(data),
    saveTiming: (request) =>
      client.post('/api/sql-logs/timing', request).then(data),
  },
});

export const adminApi = createAdminApi(httpClient);
