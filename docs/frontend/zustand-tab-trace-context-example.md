# Zustand 다중탭 SQL 추적 컨텍스트 예제

## 핵심 흐름

```text
메뉴 이동 또는 직접 URL 진입
→ AppLayout이 화면 정의로 탭 생성
→ useTabStore가 activeTabId와 uiId 보관
→ GET 요청 인터셉터가 활성 탭의 uiId 조회
→ X-Ui-Id 헤더 전송
→ 백엔드는 기존 query:{username}:{uiId} Redis 키 사용
```

## 탭 스토어

실제 구현: `frontend/src/stores/useTabStore.js`

```javascript
import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

export const useTabStore = create(
  persist(
    (set, get) => ({
      tabs: [],
      activeTabId: null,

      openTab: (tab) => {
        const existing = get().tabs.find(({ path }) => path === tab.path);
        if (existing) {
          set({ activeTabId: existing.id });
          return existing;
        }

        set((state) => ({
          tabs: [...state.tabs, tab],
          activeTabId: tab.id,
        }));
        return tab;
      },

      selectTab: (tabId) => set({ activeTabId: tabId }),

      getActiveTab: () => {
        const { tabs, activeTabId } = get();
        return tabs.find(({ id }) => id === activeTabId) ?? null;
      },
    }),
    {
      name: "admin-learning-workspace-tabs",
      storage: createJSONStorage(() => localStorage),
      partialize: ({ tabs, activeTabId }) => ({
        tabs,
        activeTabId,
      }),
    },
  ),
);
```

`persist`는 새로고침 후 열린 탭을 복원합니다. `partialize`를 사용해 상태만 저장하고 `openTab`, `selectTab` 같은 액션 함수는 저장하지 않습니다.

React 컴포넌트에서는 selector를 사용해 필요한 상태만 구독합니다.

```javascript
const tabs = useTabStore((state) => state.tabs);
const activeTabId = useTabStore((state) => state.activeTabId);
const openTab = useTabStore((state) => state.openTab);
```

React 외부인 Axios 인터셉터에서는 훅을 호출하지 않고 `getState()`를 사용합니다.

```javascript
const activeTab = useTabStore.getState().getActiveTab();
```

## 화면 정의와 탭 생성

실제 화면 정의: `frontend/src/routes/uiIdRegistry.js`

```javascript
export const workspaceScreens = [
  {
    id: "users",
    title: "사용자",
    path: "/users",
    prefix: "/users",
    uiId: "users-workbench",
  },
  {
    id: "roles",
    title: "역할",
    path: "/roles",
    prefix: "/roles",
    uiId: "roles-workbench",
  },
];
```

`AppLayout`은 현재 URL이 변경될 때 해당 화면을 탭으로 등록합니다.

```javascript
const location = useLocation();
const openTab = useTabStore((state) => state.openTab);

useEffect(() => {
  const screen = getWorkspaceScreen(location.pathname);

  if (screen) {
    openTab(screen);
  }
}, [location.pathname, openTab]);
```

같은 `path`가 이미 열려 있으면 새 탭을 만들지 않고 기존 탭을 활성화합니다.

## 탭을 직접 생성하는 예시

팝업 버튼이나 내부 동작에서 탭을 직접 열어야 한다면 다음과 같이 사용합니다.

```javascript
const openTab = useTabStore((state) => state.openTab);
const navigate = useNavigate();

const openUserScreen = () => {
  const tab = openTab({
    id: "users",
    title: "사용자",
    path: "/users",
    uiId: "users-workbench",
  });

  navigate(tab.path);
};
```

## Axios GET 인터셉터

실제 구현: `frontend/src/api/httpClient.js`

```javascript
client.interceptors.request.use((config) => {
  config.requestStartedAt = performance.now();

  if (config.method?.toLowerCase() !== "get") {
    return config;
  }

  const activeTab = useTabStore.getState().getActiveTab();
  const uiId = activeTab?.uiId ?? getUiId(window.location.pathname);

  // 응답 전에 다른 탭을 선택해도 동일한 화면으로 timing을 기록한다.
  config.traceUiId = uiId;
  config.headers.set("X-Trace-Type", "query");
  config.headers.set("X-Ui-Id", uiId);

  return config;
});
```

Redis 키는 프론트에서 만들지 않습니다. 백엔드는 전달받은 `X-Ui-Id`와 인증 사용자명으로 기존 키를 생성합니다.

```text
query:{username}:{uiId}
```
