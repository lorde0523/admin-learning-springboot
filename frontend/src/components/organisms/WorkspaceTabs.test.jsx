import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { MemoryRouter, useLocation } from 'react-router-dom';

import { useTabStore } from '../../stores/useTabStore';
import { WorkspaceTabs } from './WorkspaceTabs';

const LocationProbe = () => {
  const location = useLocation();
  return <output aria-label="현재 경로">{location.pathname}</output>;
};

describe('WorkspaceTabs', () => {
  beforeEach(() => {
    useTabStore.getState().reset();
    useTabStore.getState().openTab({
      id: 'users',
      title: '사용자',
      path: '/users',
      uiId: 'users-workbench',
    });
    useTabStore.getState().openTab({
      id: 'roles',
      title: '역할',
      path: '/roles',
      uiId: 'roles-workbench',
    });
  });

  it('selects a tab and navigates to its path', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/roles']}>
        <WorkspaceTabs />
        <LocationProbe />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('tab', { name: '사용자' }));

    expect(useTabStore.getState().activeTabId).toBe('users');
    expect(screen.getByLabelText('현재 경로')).toHaveTextContent('/users');
  });

  it('closes the active tab and navigates to the neighboring tab', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/roles']}>
        <WorkspaceTabs />
        <LocationProbe />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button', { name: '역할 탭 닫기' }));

    expect(screen.queryByRole('tab', { name: '역할' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('현재 경로')).toHaveTextContent('/users');
  });
});
