import { beforeEach, describe, expect, it } from 'vitest';

import { useTabStore } from './useTabStore';

const dashboard = { id: 'dashboard', title: '대시보드', path: '/', uiId: 'dashboard' };
const users = { id: 'users', title: '사용자', path: '/users', uiId: 'users-workbench' };

describe('useTabStore', () => {
  beforeEach(() => {
    localStorage.clear();
    useTabStore.getState().reset();
  });

  it('opens a tab and makes it active', () => {
    useTabStore.getState().openTab(dashboard);

    expect(useTabStore.getState().tabs).toEqual([dashboard]);
    expect(useTabStore.getState().activeTabId).toBe('dashboard');
  });

  it('selects an existing path instead of creating a duplicate tab', () => {
    useTabStore.getState().openTab(dashboard);
    useTabStore.getState().openTab(users);
    useTabStore.getState().openTab({ ...users, id: 'users-copy' });

    expect(useTabStore.getState().tabs).toHaveLength(2);
    expect(useTabStore.getState().activeTabId).toBe('users');
  });

  it('activates the neighboring tab when the active tab closes', () => {
    useTabStore.getState().openTab(dashboard);
    useTabStore.getState().openTab(users);
    useTabStore.getState().closeTab('users');

    expect(useTabStore.getState().tabs).toEqual([dashboard]);
    expect(useTabStore.getState().activeTabId).toBe('dashboard');
    expect(useTabStore.getState().getActiveTab()).toEqual(dashboard);
  });

  it('persists tabs and the active tab without serializing actions', () => {
    useTabStore.getState().openTab(dashboard);
    useTabStore.getState().openTab(users);

    const persisted = JSON.parse(
      localStorage.getItem('admin-learning-workspace-tabs'),
    );

    expect(persisted.state).toEqual({
      tabs: [dashboard, users],
      activeTabId: 'users',
    });
    expect(persisted.state.openTab).toBeUndefined();
  });
});
