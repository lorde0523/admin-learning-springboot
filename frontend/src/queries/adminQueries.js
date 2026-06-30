import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  assignRoleMenus,
  assignUserRoles,
  clearSqlLogs,
  createRole,
  createUser,
  deleteRole,
  deleteUser,
  getCurrentUser,
  getMenus,
  getRoleMenus,
  getRoles,
  getSqlLogs,
  saveMenuGrid,
  searchMenus,
  searchUsers,
  updateRole,
  updateUser,
} from '../api/adminApi';

export const queryKeys = {
  auth: ['auth', 'me'],
  users: (keyword = '') => ['users', keyword],
  roles: ['roles'],
  menus: (implementation, criteria) => ['menus', implementation, criteria],
  menuOptions: ['menus', 'options'],
  sqlLogs: (uiId) => ['sql-logs', uiId],
};

const useInvalidatingMutation = (mutationFn, keys) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => keys.forEach((key) => queryClient.invalidateQueries({ queryKey: key })),
  });
};

export const useAuth = () =>
  useQuery({ queryKey: queryKeys.auth, queryFn: getCurrentUser, retry: false });

export const useUsers = (keyword) =>
  useQuery({ queryKey: queryKeys.users(keyword), queryFn: () => searchUsers(keyword) });

export const useUserMutations = () => ({
  create: useInvalidatingMutation(createUser, [['users']]),
  update: useInvalidatingMutation(
    ({ id, request }) => updateUser(id, request),
    [['users']],
  ),
  remove: useInvalidatingMutation(deleteUser, [['users']]),
  assignRoles: useInvalidatingMutation(
    ({ id, roleIds }) => assignUserRoles(id, roleIds),
    [['users']],
  ),
});

export const useRoles = () =>
  useQuery({ queryKey: queryKeys.roles, queryFn: getRoles });

export const useRoleMenus = (roleId) =>
  useQuery({
    queryKey: ['roles', roleId, 'menus'],
    queryFn: () => getRoleMenus(roleId),
    enabled: Boolean(roleId),
  });

export const useRoleMutations = () => ({
  create: useInvalidatingMutation(createRole, [queryKeys.roles]),
  update: useInvalidatingMutation(
    ({ id, request }) => updateRole(id, request),
    [queryKeys.roles],
  ),
  remove: useInvalidatingMutation(deleteRole, [queryKeys.roles]),
  assignMenus: useInvalidatingMutation(
    ({ id, menuIds }) => assignRoleMenus(id, menuIds),
    [queryKeys.roles],
  ),
});

export const useMenuOptions = () =>
  useQuery({ queryKey: queryKeys.menuOptions, queryFn: () => getMenus('') });

export const useMenus = (implementation, criteria) =>
  useQuery({
    queryKey: queryKeys.menus(implementation, criteria),
    queryFn: () => searchMenus(implementation, criteria),
  });

export const useSaveMenuGrid = () =>
  useInvalidatingMutation(saveMenuGrid, [['menus']]);

export const useSqlLogs = (uiId, enabled) =>
  useQuery({
    queryKey: queryKeys.sqlLogs(uiId),
    queryFn: () => getSqlLogs(uiId),
    enabled,
    retry: false,
  });

export const useClearSqlLogs = (uiId) =>
  useInvalidatingMutation(() => clearSqlLogs(uiId), [queryKeys.sqlLogs(uiId)]);
