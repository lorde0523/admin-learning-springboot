# 5단계: useEffect와 외부 시스템

## effect의 역할

`useEffect`는 렌더링 결과를 React 바깥의 시스템과 동기화할 때 사용한다. 타이머, 브라우저 이벤트, 외부 위젯, 수동 네트워크 연결 등이 예다.

```jsx
useEffect(() => {
  document.title = `선택: ${name}`;
}, [name]);
```

렌더링이 화면에 반영된 뒤 effect가 실행된다. `name`이 이전 렌더링과 달라졌을 때 다시 실행된다.

Vue의 `watch`와 닮았지만 “값이 바뀌면 아무 코드나 실행”보다 **외부 시스템과 동기화**한다는 기준으로 이해하는 편이 안전하다.

## 의존성 배열

- `[name]`: `name`이 바뀔 때 실행
- `[]`: 마운트 후 실행하고 언마운트 때 cleanup
- 배열 생략: 모든 렌더링 뒤 실행

의존성은 실행 시점을 마음대로 최적화하는 목록이 아니다. effect 내부에서 읽는 반응형 값은 원칙적으로 포함한다.

## cleanup

```jsx
useEffect(() => {
  const id = setInterval(tick, 1000);
  return () => clearInterval(id);
}, [tick]);
```

cleanup은 다음 effect가 실행되기 전과 컴포넌트가 사라질 때 수행된다. 이전 타이머나 구독을 남기지 않기 위해 필요하다.

## effect를 사용하지 말아야 할 때

렌더링 중 계산할 수 있는 값은 바로 계산한다.

```jsx
const visibleUsers = users.filter(matchesKeyword);
```

이를 effect로 계산해 state에 저장하면 렌더링이 한 번 더 발생하고 동기화 오류가 생길 수 있다. 사용자 클릭으로 실행해야 하는 저장 요청도 이벤트 핸들러에서 처리하는 것이 자연스럽다.

이 프로젝트의 서버 조회는 직접 effect에서 Axios를 호출하기보다 React Query hook을 사용한다. 로딩, 오류, 캐시, 재요청을 이미 관리하기 때문이다.

## 무한 렌더링

```jsx
useEffect(() => {
  setCount(count + 1);
}, [count]);
```

effect가 `count`를 바꾸고, 변경된 `count`가 effect를 다시 실행하므로 반복된다. setter가 있다는 사실보다 “effect가 자신의 의존성을 계속 변경하는가?”를 확인한다.

## 모바일 연습

다음 effect의 cleanup은 언제 실행될까?

```jsx
useEffect(() => {
  window.addEventListener('resize', handleResize);
  return () => window.removeEventListener('resize', handleResize);
}, [handleResize]);
```

<details>
<summary>정답과 상세 해설</summary>

`handleResize`가 바뀌어 effect를 다시 연결하기 직전과 컴포넌트가 화면에서 사라질 때 실행된다. 같은 함수 참조로 이벤트를 제거해야 하므로 등록과 해제를 한 effect 안에 함께 작성한다.

</details>

## 현재 프로젝트 연결

`adminQueries.js`의 query hook은 서버 상태 동기화를 담당하므로 페이지가 직접 `useEffect + Axios + loading state`를 반복하지 않게 한다.

## 세 문장 요약

1. effect는 렌더링 결과를 React 바깥의 시스템과 동기화한다.
2. cleanup은 이전 구독, 타이머, 연결을 정리한다.
3. 렌더링 중 계산하거나 이벤트에서 처리할 수 있는 작업에는 effect가 필요하지 않다.
