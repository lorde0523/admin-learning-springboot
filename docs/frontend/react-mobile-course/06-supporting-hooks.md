# 6단계: useRef, useMemo, useCallback

## useRef

ref는 렌더링에 사용하지 않는 값을 렌더링 사이에 보관하거나 DOM 요소를 참조한다.

```jsx
function FocusButton() {
  const inputRef = useRef(null);

  return (
    <>
      <input ref={inputRef} />
      <button onClick={() => inputRef.current?.focus()}>포커스</button>
    </>
  );
}
```

`ref.current`를 바꿔도 다시 렌더링되지 않는다. 화면에 표시되어야 하는 값은 state를 사용한다.

## useMemo

```jsx
const sortedRows = useMemo(
  () => expensiveSort(rows),
  [rows],
);
```

`useMemo`는 의존성이 같을 때 이전 계산 결과를 재사용한다. 계산이 실제로 비싸거나, 안정적인 참조가 다른 최적화에 필요할 때 사용한다. 단순한 문자열 결합이나 작은 배열 필터에 습관적으로 쓰면 코드만 복잡해진다.

## useCallback

```jsx
const handleSave = useCallback(() => {
  save(recordId);
}, [recordId]);
```

`useCallback`은 함수의 실행 결과가 아니라 함수 참조를 재사용한다. 메모된 자식에게 전달하거나 다른 hook의 안정적인 의존성으로 필요한 경우가 대표적이다.

## Vue와 비교

- `useRef`의 DOM 참조는 Vue template ref와 유사하다.
- `useMemo`는 목적 면에서 Vue의 `computed`와 닮았지만, React에서는 성능 최적화 수단이며 값의 의미를 위해 꼭 필요한 것은 아니다.
- `useCallback`을 Vue의 일반적인 메서드처럼 모든 함수에 적용할 필요는 없다.

## 모바일 연습

다음 중 가장 알맞은 도구를 고르자.

1. 화면에 표시할 클릭 횟수
2. input DOM에 focus 주기
3. 10만 개 행의 비싼 정렬 결과 재사용
4. 두 짧은 문자열 합치기

<details>
<summary>정답과 상세 해설</summary>

1. `useState`: 값 변경이 화면을 다시 그려야 한다.
2. `useRef`: DOM 노드에 직접 접근한다.
3. `useMemo`: 측정 결과 정렬이 실제 병목일 때 계산을 재사용한다.
4. 일반 변수: 계산이 매우 저렴하므로 메모이제이션이 불필요하다.

</details>

## 세 문장 요약

1. `useRef`는 렌더링을 일으키지 않는 보관함이자 DOM 참조다.
2. `useMemo`와 `useCallback`은 정확성을 위한 기본 도구가 아니라 필요한 경우에만 쓰는 최적화 도구다.
3. 먼저 단순한 코드를 작성하고 실제 문제나 명확한 참조 요구가 있을 때 메모이제이션한다.
