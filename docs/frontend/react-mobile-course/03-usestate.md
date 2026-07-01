# 3단계: useState와 다시 렌더링

## state가 필요한 이유

일반 지역 변수는 값을 바꿔도 React에 화면을 다시 그리라고 알리지 못하며, 다음 렌더링 때 유지되지 않는다.

```jsx
import { useState } from 'react';

function Counter() {
  const [count, setCount] = useState(0);

  return (
    <button onClick={() => setCount((current) => current + 1)}>
      {count}
    </button>
  );
}
```

`useState(0)`은 현재 값 `count`와 변경 함수 `setCount`를 반환한다. 버튼을 누르면 React가 새 값을 저장하고 `Counter`를 다시 실행한다. 다시 실행된 함수는 새 `count`를 사용해 다음 JSX를 반환한다.

Vue의 `const count = ref(0)`와 비슷하지만 React에서는 `count.value++`가 아니라 setter로 새 값을 요청한다.

## 이전 값 기반 업데이트

새 값이 이전 값에 의존하면 함수형 업데이트를 사용한다.

```jsx
setCount((current) => current + 1);
```

React는 여러 업데이트를 묶을 수 있다. `setCount(count + 1)`을 연속 호출하면 두 호출이 같은 렌더링의 `count`를 볼 수 있지만, 함수형 업데이트는 순서대로 최신 값을 받는다.

## 객체와 배열

state는 직접 변경하지 않고 새 객체나 배열을 만든다.

```jsx
setUser((current) => ({
  ...current,
  name: '새 이름',
}));
```

```jsx
setItems((current) => [
  ...current,
  newItem,
]);
```

React는 이전 값과 새 값의 참조를 비교한다. 기존 객체를 직접 고치면 변경 추적이 어려워지고 과거 렌더링의 값까지 훼손할 수 있다.

## state가 필요하지 않은 경우

props나 기존 state로 매 렌더링마다 계산할 수 있는 값은 별도 state로 저장하지 않는다.

```jsx
const fullName = `${firstName} ${lastName}`;
```

`fullName`을 state로 두면 원본 두 값과 동기화해야 하는 문제가 생긴다.

## 자주 하는 실수

- `count++`로 값만 바꾸고 화면이 갱신되길 기대한다.
- `setCount` 직후 같은 함수 안에서 `count`가 즉시 바뀌었다고 생각한다.
- 배열에 `push()`하고 같은 배열을 setter에 전달한다.
- 모든 지역 변수를 state로 만든다.

## 모바일 연습

```jsx
function Toggle() {
  const [open, setOpen] = useState(false);

  return (
    <button onClick={() => setOpen((value) => !value)}>
      {open ? '닫기' : '열기'}
    </button>
  );
}
```

1. 처음 보이는 글자는?
2. 한 번 누른 뒤 보이는 글자는?
3. `value => !value`에서 `value`는 무엇인가?

<details>
<summary>정답과 상세 해설</summary>

1. 초기값이 `false`이므로 `열기`가 보인다.
2. setter가 이전 `false`를 `true`로 바꾸고 다시 렌더링하므로 `닫기`가 보인다.
3. React가 해당 업데이트를 처리할 때의 최신 state다. 변수 이름은 자유지만 `previous`나 `current`처럼 의미를 드러내면 좋다.

</details>

## 현재 프로젝트 연결

페이지의 선택 항목이나 편집 폼처럼 특정 화면 안에서만 필요한 값은 `useState` 후보이다. 여러 화면 탭에서 공유하고 새로고침 후에도 유지해야 하는 탭 목록은 지역 state보다 Zustand store가 알맞다.

## 세 문장 요약

1. state는 렌더링 사이에 유지되며 setter 호출은 새 렌더링을 요청한다.
2. 이전 값에 의존하는 변경은 함수형 업데이트를 사용한다.
3. 객체와 배열은 직접 수정하지 말고 새로운 값을 만들어 전달한다.
