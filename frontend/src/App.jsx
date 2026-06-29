import { lazy, Suspense } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Route, Routes } from 'react-router-dom';

import { AppLayout } from './components/templates/AppLayout';

const DashboardPage = lazy(() =>
  import('./pages/DashboardPage').then((module) => ({ default: module.DashboardPage })),
);
const UsersPage = lazy(() =>
  import('./pages/UsersPage').then((module) => ({ default: module.UsersPage })),
);
const RolesPage = lazy(() =>
  import('./pages/RolesPage').then((module) => ({ default: module.RolesPage })),
);
const MenusPage = lazy(() =>
  import('./pages/MenusPage').then((module) => ({ default: module.MenusPage })),
);

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

const App = () => (
  <QueryClientProvider client={queryClient}>
    <BrowserRouter>
      <Suspense fallback={<p className="state">화면을 준비하는 중...</p>}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route index element={<DashboardPage />} />
            <Route path="users" element={<UsersPage />} />
            <Route path="roles" element={<RolesPage />} />
            <Route path="menus" element={<MenusPage />} />
          </Route>
        </Routes>
      </Suspense>
    </BrowserRouter>
  </QueryClientProvider>
);

export default App;
