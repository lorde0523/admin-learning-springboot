import { NavLink, Outlet } from 'react-router-dom';

import { RequestInspector } from '../organisms/RequestInspector';

const links = [
  ['/', '대시보드'],
  ['/users', '사용자'],
  ['/roles', '역할'],
  ['/menus', '메뉴'],
];

export const AppLayout = () => (
  <div className="app-shell">
    <header className="app-header">
      <div>
        <span className="eyebrow">SPRING BOOT LAB</span>
        <strong>Admin Learning</strong>
      </div>
      <nav aria-label="주 메뉴">
        {links.map(([to, label]) => (
          <NavLink key={to} to={to} end={to === '/'}>{label}</NavLink>
        ))}
      </nav>
    </header>
    <main><Outlet /></main>
    <RequestInspector />
  </div>
);

