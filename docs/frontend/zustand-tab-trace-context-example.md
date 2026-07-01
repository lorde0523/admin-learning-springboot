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

## 적용 순서

기존 프로젝트에 적용할 때는 아래 순서를 지키는 편이 안전하다.

1. Zustand를 설치한다.
2. 기존 탭 객체에 `uiId`를 추가한다.
3. `useTabStore`에서 `tabs`와 `activeTabId`를 관리한다.
4. `persist`로 두 상태만 저장한다.
5. 메뉴 또는 라우트 이동 시 `openTab`을 호출한다.
6. 탭 클릭 시 `selectTab` 후 해당 경로로 이동한다.
7. Axios GET 인터셉터에서 활성 탭의 `uiId`를 읽는다.
8. 백엔드는 기존 방식대로 `X-Ui-Id`를 읽어 Redis 키를 만든다.
9. 로그아웃이나 로그인 사용자 변경 시 저장된 탭을 초기화한다.

## 1. Zustand 설치

프론트 프로젝트 디렉터리에서 실행한다.

```bash
npm install zustand
```

설치 후 `package.json`의 dependencies에 Zustand가 들어갔는지 확인한다.

```json
{
  "dependencies": {
    "zustand": "^5.0.0"
  }
}
```

## 2. 탭 데이터 계약 결정

각 탭은 최소한 다음 값을 가진다.

```javascript
const tab = {
  id: "users",
  title: "사용자",
  path: "/users",
  uiId: "users-workbench",
  pinned: false,
};
```

각 필드의 책임은 다음과 같다.

| 필드 | 역할 |
| --- | --- |
| `id` | 탭 선택·닫기에 사용하는 프론트 식별자 |
| `title` | 탭에 표시할 이름 |
| `path` | React Router 이동 경로와 중복 탭 판단 기준 |
| `uiId` | `X-Ui-Id`로 전송할 화면 식별자 |
| `pinned` | 메인 화면처럼 닫을 수 없는 탭인지 표시 |

Redis 키에는 `id`나 `path`를 추가하지 않는다. 백엔드 키는 계속 다음 형식을 사용한다.

```text
query:{username}:{uiId}
```

`uiId`는 Redis 키의 화면 구분 값이므로 배포 후 자주 바꾸지 않는 안정적인 문자열을 사용한다.

## 3. 기존 탭 스토어에 적용

이미 `useTabStore`가 있다면 스토어 전체를 교체할 필요는 없다. 다음 항목만 맞추면 된다.

- 탭 객체가 `uiId`를 가진다.
- 선택된 탭 ID를 `activeTabId`로 관리한다.
- React 외부 코드가 사용할 `getActiveTab()`을 제공한다.
- `persist.partialize`에는 `tabs`, `activeTabId`만 포함한다.

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

`localStorage`에는 다음과 유사한 값이 저장된다.

```json
{
  "state": {
    "tabs": [
      {
        "id": "users",
        "title": "사용자",
        "path": "/users",
        "uiId": "users-workbench"
      }
    ],
    "activeTabId": "users"
  },
  "version": 0
}
```

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

인터셉터, 일반 JavaScript 모듈, 이벤트 리스너에서는 `useTabStore()`를 호출하면 안 된다. React Hook 규칙의 적용을 받지 않는 위치이므로 반드시 `useTabStore.getState()`를 사용한다.

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

### 직접 URL로 접근하는 경우

사용자가 `/roles`를 주소창에 직접 입력하면 Zustand 복원이 끝나기 전에 화면 API가 실행될 수 있다. 그래서 두 단계로 방어한다.

1. `AppLayout`이 현재 pathname에 해당하는 탭을 생성한다.
2. Axios 인터셉터는 활성 탭이 아직 없으면 pathname registry의 `uiId`를 사용한다.

```javascript
const activeTab = useTabStore.getState().getActiveTab();
const uiId =
  activeTab?.uiId ??
  getUiId(window.location.pathname);
```

이 fallback 덕분에 첫 조회 요청도 `X-Ui-Id` 없이 전송되지 않는다.

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

`openTab()`이 실제로 선택한 탭을 반환하도록 만들면 기존 탭이 열린 경우에도 올바른 경로로 이동할 수 있다.

## 탭 선택 구현

탭을 클릭하면 Zustand 선택 상태와 Router 경로를 함께 바꾼다.

```javascript
const selectTab = useTabStore((state) => state.selectTab);
const navigate = useNavigate();

const activateTab = (tab) => {
  selectTab(tab.id);
  navigate(tab.path);
};
```

둘 중 하나만 변경하면 다음 문제가 생긴다.

- Zustand만 변경: 탭은 선택됐지만 기존 화면이 그대로 보인다.
- Router만 변경: 화면은 바뀌지만 인터셉터가 이전 탭의 `uiId`를 읽는다.

## 탭 닫기 구현

활성 탭을 닫은 뒤에는 스토어가 선택한 인접 탭으로 이동한다.

```javascript
const closeTab = useTabStore((state) => state.closeTab);
const navigate = useNavigate();

const close = (tabId) => {
  closeTab(tabId);

  const nextTab = useTabStore.getState().getActiveTab();
  navigate(nextTab?.path ?? "/");
};
```

메인 탭을 항상 유지하려면 화면 정의에 `pinned: true`를 넣고 닫기 버튼을 렌더링하지 않는다.

```jsx
{!tab.pinned && (
  <button
    type="button"
    aria-label={`${tab.title} 탭 닫기`}
    onClick={() => close(tab.id)}
  >
    ×
  </button>
)}
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

### 요청 시점의 `uiId`를 config에 저장하는 이유

요청을 보낸 뒤 응답을 받기 전에 사용자가 다른 탭을 선택할 수 있다. 응답 인터셉터에서 Zustand를 다시 읽으면 새로 선택된 탭의 `uiId`로 timing이 저장되는 문제가 생긴다.

따라서 요청 인터셉터에서 사용한 값을 Axios config에 보관한다.

```javascript
config.traceUiId = uiId;
```

응답 인터셉터에서는 현재 활성 탭을 다시 읽지 않고 이 값을 사용한다.

```javascript
onTiming({
  traceType: "query",
  apiStartedAt,
  uiId: response.config.traceUiId,
  clientTimeMillis,
  totalTimeMillis,
});
```

### GET에만 헤더를 추가하는 이유

현재 SQL 추적 정책은 조회 SQL만 기록한다. 따라서 POST, PUT, PATCH, DELETE에는 추적 헤더를 추가하지 않는다.

```javascript
if (config.method?.toLowerCase() !== "get") {
  return config;
}
```

Redis 키는 프론트에서 만들지 않습니다. 백엔드는 전달받은 `X-Ui-Id`와 인증 사용자명으로 기존 키를 생성합니다.

```text
query:{username}:{uiId}
```

## 백엔드 확인 사항

Spring Filter는 GET 요청에서 다음 헤더를 읽어야 한다.

```http
X-Trace-Type: query
X-Ui-Id: users-workbench
```

백엔드 처리 흐름은 다음과 같다.

```text
Spring Security 인증 사용자 조회
→ X-Ui-Id 읽기
→ SQL 캡처 컨텍스트 생성
→ JPA/MyBatis SELECT 실행 기록
→ query:{username}:{uiId}에 Redis 저장
```

프론트에서 `username`을 전송하거나 Redis 키를 완성해서 보내면 안 된다. 사용자명은 신뢰 가능한 Spring Security 세션에서 가져온다.

## 로그아웃과 사용자 변경 처리

`localStorage`는 브라우저를 닫아도 유지된다. 공용 PC나 한 브라우저에서 여러 사용자가 로그인할 수 있다면 이전 사용자의 탭 목록이 다음 사용자에게 보일 수 있다.

로그아웃 성공 시 다음 처리를 실행한다.

```javascript
const clearWorkspaceTabs = () => {
  useTabStore.getState().reset();
  useTabStore.persist.clearStorage();
};
```

SSO 사용자 변경을 감지할 수 있다면 저장 키에 사용자명을 포함하는 방식도 사용할 수 있다.

```javascript
name: `admin-learning-workspace-tabs:${username}`
```

단, 로그인 사용자 정보를 Zustand 스토어 생성 시점에 알 수 없다면 로그아웃 초기화 방식이 더 단순하다.

## 적용 확인 방법

### 1. 탭 상태 확인

브라우저 개발자 도구에서 다음 항목을 확인한다.

```text
Application
→ Local Storage
→ admin-learning-workspace-tabs
```

탭을 열고 선택할 때 `tabs`, `activeTabId`가 변경되어야 한다.

### 2. 요청 헤더 확인

```text
Network
→ 조회 GET 요청 선택
→ Request Headers
```

다음 값이 보여야 한다.

```http
X-Trace-Type: query
X-Ui-Id: users-workbench
```

### 3. 탭 전환 확인

1. 사용자 탭을 선택하고 조회한다.
2. `X-Ui-Id: users-workbench`인지 확인한다.
3. 역할 탭을 선택하고 조회한다.
4. `X-Ui-Id: roles-workbench`인지 확인한다.

### 4. Redis 확인

```redis
KEYS query:*
```

운영 Redis에서는 `KEYS` 대신 `SCAN`을 사용한다.

```redis
SCAN 0 MATCH query:* COUNT 100
```

예상 키:

```text
query:login-user:users-workbench
query:login-user:roles-workbench
```

## 자주 발생하는 문제

### 헤더가 이전 탭의 `uiId`로 전송된다

- 탭 클릭 시 `navigate()`만 호출하지 않았는지 확인한다.
- `selectTab(tab.id)`가 먼저 실행되는지 확인한다.
- 탭 객체에 `uiId`가 실제로 들어 있는지 확인한다.

### 새로고침 직후 `X-Ui-Id`가 없다

- pathname fallback이 있는지 확인한다.
- `uiIdRegistry`에 해당 경로가 등록됐는지 확인한다.
- 동적 경로라면 `pathname.startsWith(prefix)` 방식으로 매칭한다.

### 새로고침 후 탭이 복원되지 않는다

- `persist`가 `create`를 감싸고 있는지 확인한다.
- `createJSONStorage(() => localStorage)`가 설정됐는지 확인한다.
- `partialize`가 `tabs`, `activeTabId`를 반환하는지 확인한다.
- 브라우저 시크릿 모드나 저장소 차단 정책을 확인한다.

### 동일 화면 탭이 여러 개 생긴다

현재 예제는 `path`를 중복 기준으로 사용한다.

```javascript
const existing = get().tabs.find(({ path }) => path === tab.path);
```

같은 화면을 파라미터별로 여러 탭에서 허용하려면 `path` 대신 별도의 인스턴스 키를 사용해야 한다. 이 경우에도 Redis 키를 기존 규칙으로 유지하면 같은 `uiId`의 SQL 로그는 하나의 Redis 키에 누적된다.

## 최종 적용 체크리스트

- [ ] Zustand가 설치되어 있다.
- [ ] 모든 화면 정의에 안정적인 `uiId`가 있다.
- [ ] 탭 객체에 `id`, `title`, `path`, `uiId`가 들어간다.
- [ ] `activeTabId`가 탭 클릭 시 변경된다.
- [ ] `persist`는 `tabs`, `activeTabId`만 저장한다.
- [ ] Axios 인터셉터는 `useTabStore.getState()`를 사용한다.
- [ ] GET 요청에만 `X-Ui-Id`가 추가된다.
- [ ] 요청 시점의 `uiId`가 `config.traceUiId`에 저장된다.
- [ ] 초기 진입을 위한 pathname fallback이 있다.
- [ ] 로그아웃 시 persist 저장소를 비운다.
- [ ] Redis 키가 계속 `query:{username}:{uiId}` 형식이다.
