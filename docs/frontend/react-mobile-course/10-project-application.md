# 10단계: 현재 프로젝트 코드 읽기

## 전체 데이터 흐름

이 프로젝트의 일반적인 흐름은 다음과 같다.

```text
사용자 행동
→ page의 handler
→ query hook
→ api 함수
→ httpClient
→ Spring Boot API
→ React Query cache 갱신
→ page 재렌더링
→ 공통 UI에 새 props 전달
```

화면 탭은 서버 데이터가 아니므로 별도 흐름을 가진다.

```text
탭 클릭
→ useTabStore action
→ Zustand state 변경
→ 구독 중인 WorkspaceTabs 재렌더링
```

## 코드를 읽는 순서

처음부터 모든 파일을 읽지 않는다. 하나의 사용자 행동을 선택하고 다음 순서로 추적한다.

1. `pages/UsersPage.jsx`에서 화면 진입점 확인
2. 사용 중인 query hook과 template 확인
3. `queries/adminQueries.js`에서 query key와 API 연결 확인
4. `api/adminApi.js`와 `api/httpClient.js`에서 요청 확인
5. props를 받은 organism, molecule, atom 순서로 UI 확인

이 순서를 따르면 “이 state는 누가 소유하는가?”와 “이 함수는 어디서 실행되는가?”를 놓치기 어렵다.

## 공통 UI와 커스텀 훅 선택

반복되는 것이 JSX와 접근성 규칙이면 공통 컴포넌트를 검토한다.

```text
반복되는 것: 버튼 모양 + disabled + aria 속성
선택: 공통 Button
```

반복되는 것이 state와 이벤트 흐름이면 커스텀 훅을 검토한다.

```text
반복되는 것: 검색어 저장 + 초기화 + 정규화
선택: useSearchInput 같은 커스텀 훅
```

서버 조회와 캐시 흐름이면 기존 React Query hook을 확장한다. 무조건 새 커스텀 훅이나 Zustand store를 만들지 않는다.

## 작은 개선 절차

1. 현재 동작을 예제나 테스트로 확인한다.
2. 반복이나 책임 혼합을 한 문장으로 설명한다.
3. 가장 작은 경계 하나만 분리한다.
4. 기존 소비자의 동작이 유지되는지 테스트한다.
5. 컴포넌트 이름과 props만 보고 사용법이 이해되는지 검토한다.

## 모바일 최종 연습

상황: 사용자·역할·메뉴 페이지에 같은 로딩/오류/빈 결과 표시가 반복된다.

다음 질문에 답해 보자.

1. UI 반복인가, 상태 로직 반복인가?
2. 어느 Atomic 계층이 적절한가?
3. 어떤 props가 필요한가?
4. 페이지와 서버 요청의 책임을 공통 컴포넌트가 가져야 하는가?

<details>
<summary>정답과 상세 해설</summary>

1. 로딩·오류·빈 결과를 어떻게 **표시하는가**가 반복되므로 우선 UI 반복이다.
2. 여러 작은 상태 표현을 묶는 molecule이 적절하다. 이 프로젝트에는 이미 `AsyncState.jsx`가 있으므로 새 컴포넌트보다 기존 구현을 먼저 검토한다.
3. loading 여부, error 정보, 데이터가 비었는지 판단할 값, 정상일 때 표시할 children 정도가 후보이다. 실제 기존 인터페이스를 확인해 맞춰야 한다.
4. 아니다. 공통 컴포넌트는 상태를 표시하고, 페이지와 React Query hook이 요청 및 서버 상태를 소유한다.

</details>

## 수료 확인

다음을 자신의 말로 설명할 수 있으면 기초 과정을 마친 것이다.

- props와 state의 차이
- setter가 필요한 이유
- effect가 필요한 경우와 필요 없는 경우
- 공통 컴포넌트와 커스텀 훅의 차이
- 지역 state, Zustand, React Query의 역할
- 페이지에서 API와 UI까지 코드를 추적하는 순서

## 세 문장 요약

1. 사용자 행동 하나를 page에서 API 또는 store까지 따라가며 데이터 흐름을 읽는다.
2. 반복되는 UI는 컴포넌트, 반복되는 상태 로직은 커스텀 훅으로 분리한다.
3. 새 추상화를 만들기 전에 기존 컴포넌트와 query/store 구조를 먼저 확인하고 작은 변경부터 검증한다.
