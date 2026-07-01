import { useEffect } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';

import { RequestInspector } from '../organisms/RequestInspector';
import { WorkspaceTabs } from '../organisms/WorkspaceTabs';
import { getWorkspaceScreen, workspaceScreens } from '../../routes/uiIdRegistry';
import { useTabStore } from '../../stores/useTabStore';

export const AppLayout = () => {
  const location = useLocation();
  const openTab = useTabStore((state) => state.openTab);

  useEffect(() => {
    const screen = getWorkspaceScreen(location.pathname);
    if (screen) openTab(screen);
  }, [location.pathname, openTab]);

  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <span className="eyebrow">SPRING BOOT LAB</span>
          <strong>Admin Learning</strong>
        </div>
        <nav aria-label="주 메뉴">
          {[...workspaceScreens].reverse().map((screen) => (
            <NavLink key={screen.path} to={screen.path} end={screen.path === '/'}>
              {screen.title}
            </NavLink>
          ))}
        </nav>
      </header>
      <WorkspaceTabs />
      <main><Outlet /></main>
      <RequestInspector />
    </div>
  );
};
