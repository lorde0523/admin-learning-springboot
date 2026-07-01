# 8단계: Context, Zustand, React Query

## 상태를 종류별로 구분하기

도구를 고르기 전에 값의 소유권을 묻는다.

| 종류 | 예 | 우선 도구 |
| --- | --- | --- |
| 한 컴포넌트의 UI 상태 | 모달 열림, 입력값 | `useState` |
| 가까운 자식에게 전달 | 테마 설정, 폼 문맥 | props 또는 Context |
| 여러 먼 화면이 공유 | 열린 작업 탭 | Zustand |
| 서버가 원본인 데이터 | 사용자 목록 | React Query |

## Context

Context는 멀리 있는 자식에게 값을 전달할 때 중간 컴포넌트의 props 전달을 줄인다. 값이 자주 바뀌는 거대한 전역 store로 무조건 사용하면 많은 소비자가 다시 렌더링되고 책임이 뭉칠 수 있다.

## Zustand

Zustand store는 React 컴포넌트 밖에서도 접근 가능한 클라이언트 상태와 변경 함수를 제공한다.

```jsx
const activeTabId = useTabStore((state) => state.activeTabId);
const selectTab = useTabStore((state) => state.selectTab);
```

필요한 조각을 selector로 구독하면 관련 값이 바뀔 때 컴포넌트가 갱신된다.

## React Query

React Query는 서버 데이터를 가져오고 캐시에 저장하며 로딩, 오류, 재조회 상태를 관리한다.

```jsx
const {
  data = [],
  isPending,
  isError,
} = useUsersQuery(filters);
```

서버 데이터 전체를 Zustand에 복사하면 캐시와 store 중 어느 쪽이 최신인지 불분명해진다. 서버 상태는 React Query에 두고, 선택된 행 ID 같은 UI 상태만 지역 state나 store에 둔다.

## 모바일 연습

다음 값을 어디에 둘지 판단하자.

1. 현재 입력 중인 검색어
2. 서버에서 조회한 역할 목록
3. 여러 페이지에서 공통으로 표시하는 열린 탭
4. 버튼에 마우스를 올렸는지 여부

<details>
<summary>정답과 상세 해설</summary>

1. 검색 컴포넌트나 페이지의 `useState`.
2. React Query 캐시.
3. 이 프로젝트에서는 Zustand의 `useTabStore`.
4. CSS `:hover`로 충분하면 state 자체가 필요 없다. JavaScript 동작이 꼭 필요할 때만 지역 state를 검토한다.

</details>

## 현재 프로젝트 연결

- `api/httpClient.js`: HTTP 전송과 공통 헤더
- `queries/adminQueries.js`: 서버 상태 query/mutation
- `stores/useTabStore.js`: 화면 탭이라는 클라이언트 전역 상태

## 세 문장 요약

1. 상태 도구는 값의 소유권과 생명주기에 따라 선택한다.
2. 서버 데이터는 React Query, 여러 화면의 클라이언트 상태는 필요한 경우 Zustand가 담당한다.
3. 가장 가까운 곳의 `useState`로 해결할 수 있다면 전역 상태로 올리지 않는다.
