/**
 * Defines the stable backend trace identifier for every route.
 * The registry is kept outside pages so the HTTP layer can apply headers centrally.
 */
const routeUiIds = [
  { prefix: '/users', uiId: 'users-workbench' },
  { prefix: '/roles', uiId: 'roles-workbench' },
  { prefix: '/menus', uiId: 'menus-workbench' },
  { prefix: '/', uiId: 'dashboard' },
];

/**
 * Resolves a browser pathname to the identifier used by SQL trace storage.
 */
export const getUiId = (pathname) => {
  const match = routeUiIds.find(({ prefix }) =>
    prefix === '/' ? pathname === '/' : pathname.startsWith(prefix),
  );

  return match?.uiId ?? 'unknown-page';
};

