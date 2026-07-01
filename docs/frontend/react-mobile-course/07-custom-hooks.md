# 7단계: 커스텀 훅

## 커스텀 훅이란?

커스텀 훅은 state와 다른 hook을 사용하는 **상태 로직을 재사용하는 함수**다. UI 자체를 반환하는 공통 컴포넌트와 책임이 다르다.

```jsx
function useToggle(initialValue = false) {
  const [value, setValue] = useState(initialValue);
  const toggle = () => setValue((current) => !current);

  return { value, toggle };
}
```

```jsx
const { value: open, toggle } = useToggle();
```

각 호출은 독립적인 state를 만든다. 커스텀 훅이 여러 컴포넌트의 state를 자동으로 공유하는 것은 아니다. 공유가 필요하면 Context나 외부 store를 검토한다.

## 이름이 use로 시작하는 이유

React와 lint 도구가 hook 규칙을 적용할 함수임을 알아야 하기 때문이다. hook은 컴포넌트나 다른 hook의 최상위에서 같은 순서로 호출해야 한다. 조건문이나 반복문 안에서 호출하면 렌더링마다 hook 순서가 달라질 수 있다.

## 분리할 시점

- 둘 이상의 컴포넌트에서 같은 상태 흐름을 사용한다.
- 컴포넌트가 UI보다 데이터·구독 로직 때문에 읽기 어렵다.
- 독립적으로 이름 붙이고 테스트할 가치가 있다.

한 번만 쓰는 두 줄짜리 state를 미리 범용 hook으로 만들 필요는 없다.

## 객체 반환과 배열 반환

`useState`처럼 의미가 널리 알려지고 이름을 호출자가 정하는 두 값은 배열이 편리하다. 동작이 여러 개거나 의미 있는 필드가 많다면 객체가 읽기 쉽다.

```jsx
return { value, open, close, toggle };
```

## 모바일 연습

다음 중 커스텀 훅으로 분리하기 더 적합한 것은?

1. 모든 페이지에서 반복되는 검색어, 초기화, 공백 제거 흐름
2. 특정 페이지의 제목을 표시하는 `<h1>`

<details>
<summary>정답과 상세 해설</summary>

1번은 상태 로직이므로 커스텀 훅 후보이다. 2번은 UI 표현이므로 컴포넌트이거나 단순 JSX로 남기는 편이 맞다. “재사용”이라는 말만 보고 둘을 같은 방식으로 공통화하지 않는 것이 핵심이다.

</details>

## 세 문장 요약

1. 커스텀 훅은 UI가 아니라 state와 effect를 포함한 상태 로직을 재사용한다.
2. hook은 이름을 `use`로 시작하고 항상 최상위에서 호출한다.
3. 반복과 복잡성이 실제로 드러난 뒤 명확한 책임을 가진 hook으로 분리한다.
