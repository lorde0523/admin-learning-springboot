import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import App from './App';

vi.mock('./api/adminApi', () => ({
  adminApi: {
    auth: { me: vi.fn().mockResolvedValue({ authenticated: false, principal: null }) },
    users: { search: vi.fn().mockResolvedValue([]) },
    roles: { list: vi.fn().mockResolvedValue([]) },
    menus: {
      list: vi.fn().mockResolvedValue([]),
      searchPage: vi.fn().mockResolvedValue({ content: [], totalPages: 0, last: true }),
    },
    sqlLogs: {
      list: vi.fn().mockResolvedValue({ logs: [] }),
      clear: vi.fn().mockResolvedValue(undefined),
    },
  },
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
