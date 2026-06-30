import { httpClient } from './httpClient';

export const getCurrentUser = async () => {
  const response = await httpClient.get('/api/auth/me');
  return response.data;
};

export const searchUsers = async (loginKeyword = '') => {
  const response = await httpClient.get('/api/jpa/users', {
    params: { loginKeyword },
  });
  return response.data;
};

export const getUser = async (id) => {
  const response = await httpClient.get(`/api/jpa/users/${id}`);
  return response.data;
};

export const createUser = async (request) => {
  const response = await httpClient.post('/api/jpa/users', request);
  return response.data;
};

export const updateUser = async (id, request) => {
  const response = await httpClient.put(`/api/jpa/users/${id}`, request);
  return response.data;
};

export const deleteUser = async (id) => {
  const response = await httpClient.delete(`/api/jpa/users/${id}`);
  return response.data;
};

export const assignUserRoles = async (id, roleIds) => {
  const response = await httpClient.put(`/api/jpa/users/${id}/roles`, { roleIds });
  return response.data;
};

export const getRoles = async () => {
  const response = await httpClient.get('/api/jpa/roles');
  return response.data;
};

export const getRole = async (id) => {
  const response = await httpClient.get(`/api/jpa/roles/${id}`);
  return response.data;
};

export const createRole = async (request) => {
  const response = await httpClient.post('/api/jpa/roles', request);
  return response.data;
};

export const updateRole = async (id, request) => {
  const response = await httpClient.put(`/api/jpa/roles/${id}`, request);
  return response.data;
};

export const deleteRole = async (id) => {
  const response = await httpClient.delete(`/api/jpa/roles/${id}`);
  return response.data;
};

export const getRoleMenus = async (id) => {
  const response = await httpClient.get(`/api/jpa/roles/${id}/menus`);
  return response.data;
};

export const assignRoleMenus = async (id, menuIds) => {
  const response = await httpClient.put(`/api/jpa/roles/${id}/menus`, { menuIds });
  return response.data;
};

export const getMenus = async (nameKeyword = '') => {
  const response = await httpClient.get('/api/jpa/menus', {
    params: { nameKeyword },
  });
  return response.data;
};

export const searchMenus = async (implementation, criteria) => {
  const response = await httpClient.get(`/api/${implementation}/menus/page`, {
    params: criteria,
  });
  return response.data;
};

export const saveMenuGrid = async (changeSet) => {
  const response = await httpClient.post('/api/jpa/menus/grid-save', changeSet);
  return response.data;
};

export const getSqlLogs = async (uiId) => {
  const response = await httpClient.get('/api/sql-logs', {
    params: { traceType: 'query', uiId },
  });
  return response.data;
};

export const clearSqlLogs = async (uiId) => {
  const response = await httpClient.delete('/api/sql-logs', {
    params: { traceType: 'query', uiId },
  });
  return response.data;
};

export const saveSqlLogTiming = async (request) => {
  const response = await httpClient.post('/api/sql-logs/timing', request);
  return response.data;
};
