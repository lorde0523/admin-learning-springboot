import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { adminApi } from '../api/adminApi';

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
  useQuery({ queryKey: queryKeys.auth, queryFn: adminApi.auth.me, retry: false });

export const useUsers = (keyword) =>
  useQuery({ queryKey: queryKeys.users(keyword), queryFn: () => adminApi.users.search(keyword) });

export const useUserMutations = () => ({
  create: useInvalidatingMutation(adminApi.users.create, [['users']]),
  update: useInvalidatingMutation(
    ({ id, request }) => adminApi.users.update(id, request),
    [['users']],
  ),
  remove: useInvalidatingMutation(adminApi.users.remove, [['users']]),
  assignRoles: useInvalidatingMutation(
    ({ id, roleIds }) => adminApi.users.assignRoles(id, roleIds),
    [['users']],
  ),
});

export const useRoles = () =>
  useQuery({ queryKey: queryKeys.roles, queryFn: adminApi.roles.list });

export const useRoleMenus = (roleId) =>
  useQuery({
    queryKey: ['roles', roleId, 'menus'],
    queryFn: () => adminApi.roles.menus(roleId),
    enabled: Boolean(roleId),
  });

export const useRoleMutations = () => ({
  create: useInvalidatingMutation(adminApi.roles.create, [queryKeys.roles]),
  update: useInvalidatingMutation(
    ({ id, request }) => adminApi.roles.update(id, request),
    [queryKeys.roles],
  ),
  remove: useInvalidatingMutation(adminApi.roles.remove, [queryKeys.roles]),
  assignMenus: useInvalidatingMutation(
    ({ id, menuIds }) => adminApi.roles.assignMenus(id, menuIds),
    [queryKeys.roles],
  ),
});

export const useMenuOptions = () =>
  useQuery({ queryKey: queryKeys.menuOptions, queryFn: () => adminApi.menus.list('') });

export const useMenus = (implementation, criteria) =>
  useQuery({
    queryKey: queryKeys.menus(implementation, criteria),
    queryFn: () => adminApi.menus.searchPage(implementation, criteria),
  });

export const useSaveMenuGrid = () =>
  useInvalidatingMutation(adminApi.menus.saveGrid, [['menus']]);

export const useSqlLogs = (uiId, enabled) =>
  useQuery({
    queryKey: queryKeys.sqlLogs(uiId),
    queryFn: () => adminApi.sqlLogs.list(uiId),
    enabled,
    retry: false,
  });

export const useClearSqlLogs = (uiId) =>
  useInvalidatingMutation(() => adminApi.sqlLogs.clear(uiId), [queryKeys.sqlLogs(uiId)]);
