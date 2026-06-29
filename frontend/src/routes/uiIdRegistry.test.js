import { describe, expect, it } from 'vitest';

import { getUiId } from './uiIdRegistry';

describe('getUiId', () => {
  it.each([
    ['/', 'dashboard'],
    ['/users', 'users-workbench'],
    ['/roles/12', 'roles-workbench'],
    ['/menus', 'menus-workbench'],
    ['/unknown', 'unknown-page'],
  ])('maps %s to %s', (pathname, expected) => {
    expect(getUiId(pathname)).toBe(expected);
  });
});
