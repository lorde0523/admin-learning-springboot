import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import App from './App';

vi.mock('./api/adminApi', () => ({
  getCurrentUser: vi.fn().mockResolvedValue({ authenticated: false, principal: null }),
  searchUsers: vi.fn().mockResolvedValue([]),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  deleteUser: vi.fn(),
  assignUserRoles: vi.fn(),
  getRoles: vi.fn().mockResolvedValue([]),
  createRole: vi.fn(),
  updateRole: vi.fn(),
  deleteRole: vi.fn(),
  getRoleMenus: vi.fn().mockResolvedValue([]),
  assignRoleMenus: vi.fn(),
  getMenus: vi.fn().mockResolvedValue([]),
  searchMenus: vi.fn().mockResolvedValue({ content: [], totalPages: 0, last: true }),
  saveMenuGrid: vi.fn(),
  getSqlLogs: vi.fn().mockResolvedValue({ logs: [] }),
  clearSqlLogs: vi.fn().mockResolvedValue(undefined),
}));

describe('App', () => {
  it('navigates between the backend workbenches', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/');

    render(<App />);

    expect(await screen.findByRole('heading', { name: '백엔드 학습 워크벤치' })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: '메뉴' }));

    expect(await screen.findByRole('heading', { name: '메뉴 워크벤치' })).toBeInTheDocument();
  });
});
