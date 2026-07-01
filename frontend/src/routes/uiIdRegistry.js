/**
 * Defines the stable backend trace identifier for every route.
 * The registry is kept outside pages so the HTTP layer can apply headers centrally.
 */
export const workspaceScreens = [
  { id: 'users', title: '사용자', path: '/users', prefix: '/users', uiId: 'users-workbench' },
  { id: 'roles', title: '역할', path: '/roles', prefix: '/roles', uiId: 'roles-workbench' },
  { id: 'menus', title: '메뉴', path: '/menus', prefix: '/menus', uiId: 'menus-workbench' },
  { id: 'dashboard', title: '대시보드', path: '/', prefix: '/', uiId: 'dashboard', pinned: true },
];

export const getWorkspaceScreen = (pathname) =>
  workspaceScreens.find(({ prefix }) =>
    prefix === '/' ? pathname === '/' : pathname.startsWith(prefix),
  ) ?? null;

/**
 * Resolves a browser pathname to the identifier used by SQL trace storage.
 */
export const getUiId = (pathname) => {
  return getWorkspaceScreen(pathname)?.uiId ?? 'unknown-page';
};
