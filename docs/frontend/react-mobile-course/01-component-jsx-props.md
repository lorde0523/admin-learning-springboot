# 1단계: 컴포넌트, JSX, props

## 1. 컴포넌트란?

React 컴포넌트는 **화면의 한 부분을 설명하는 JavaScript 함수**다. 함수는 React가 화면을 그리는 데 사용할 JSX를 반환한다.

```jsx
function Greeting() {
  return <h1>안녕하세요</h1>;
}
```

`Greeting`을 호출해 문자열을 바로 출력하는 것이 아니다. React가 `<Greeting />`을 만나면 함수를 실행하고, 반환된 `<h1>`을 실제 DOM과 비교해 필요한 화면을 반영한다.

컴포넌트는 다음 조건을 만족할 때 유용하다.

- 같은 UI를 여러 곳에서 사용한다.
- 화면 일부가 독립적인 책임을 갖는다.
- 부모가 값을 전달하면 그 값에 맞춰 표시가 달라진다.

단순한 `<span>` 하나를 무조건 컴포넌트로 만들 필요는 없다. 이름을 붙였을 때 의미가 명확해지거나 재사용되는 경우에 분리한다.

## 2. Vue와 비교

Vue의 SFC는 보통 `<template>`, `<script>`, `<style>` 영역으로 역할을 나눈다. React 함수 컴포넌트에서는 JavaScript 안에서 JSX를 반환한다.

```vue
<template>
  <h1>{{ title }}</h1>
</template>
```

```jsx
function Heading({ title }) {
  return <h1>{title}</h1>;
}
```

Vue 템플릿의 `{{ title }}`처럼 JSX에서는 `{title}`로 JavaScript 값을 넣는다. JSX는 HTML처럼 보이지만 JavaScript 문법의 일부다.

## 3. JSX가 HTML과 다른 점

JSX의 대표적인 차이는 다음과 같다.

- CSS 클래스는 `class`가 아니라 `className`이다.
- 모든 태그를 닫아야 한다. `<input />`처럼 작성한다.
- JavaScript 표현식은 `{}` 안에 넣는다.
- 여러 최상위 요소를 반환하려면 하나의 부모나 Fragment로 감싼다.

```jsx
function Profile({ name, isAdmin }) {
  return (
    <>
      <h2 className="profile">{name}</h2>
      <p>{isAdmin ? '관리자' : '일반 사용자'}</p>
    </>
  );
}
```

`{}` 안에는 값으로 계산되는 표현식을 넣는다. `if` 문은 값이 아니므로 바로 넣을 수 없지만, 삼항 연산자는 결과값을 만들기 때문에 넣을 수 있다.

## 4. props란?

props는 부모 컴포넌트가 자식 컴포넌트에 전달하는 입력값이다.

```jsx
function Badge({ label }) {
  return <span>{label}</span>;
}

function App() {
  return <Badge label="사용 중" />;
}
```

실행 순서는 다음과 같다.

1. React가 `App`을 실행한다.
2. `<Badge label="사용 중" />`을 발견한다.
3. React가 `Badge`에 `{ label: '사용 중' }`을 전달한다.
4. `Badge`는 `<span>사용 중</span>`을 반환한다.

props는 자식이 직접 변경하지 않는다. 다른 값이 필요하면 부모가 새 props를 전달하거나, 이후 단계에서 배울 state를 사용한다.

Vue의 props도 부모에서 자식으로 흐른다는 점은 같다. 차이는 React에서 컴포넌트 함수의 매개변수로 props 객체를 직접 받는다는 점이다.

## 5. 자주 하는 실수

### 컴포넌트 이름을 소문자로 작성

```jsx
function greeting() {
  return <h1>안녕</h1>;
}
```

React는 소문자로 시작하는 JSX 태그를 HTML 태그로 해석한다. 사용자 컴포넌트 이름은 대문자로 시작한다.

### 함수를 실행해 버림

```jsx
<button>{getMessage()}</button>
```

화면에 함수의 결과를 표시하려는 목적이라면 맞다. 하지만 클릭할 때 실행하려는 함수를 `onClick={save()}`처럼 작성하면 렌더링 중 즉시 실행된다. 이벤트는 다음 단계에서 자세히 다룬다.

### props를 변경

```jsx
function Price({ amount }) {
  amount = amount + 1000;
  return <p>{amount}</p>;
}
```

지역 변수 재할당 자체가 항상 오류를 내는 것은 아니지만, 전달받은 입력의 의미를 바꿔 코드를 혼란스럽게 한다. 별도의 계산값을 만든다.

```jsx
const priceWithFee = amount + 1000;
```

## 6. 모바일 연습

### 문제 1: 화면 예측

```jsx
function Welcome({ name }) {
  return <p>{name}님 환영합니다.</p>;
}

function App() {
  return <Welcome name="민수" />;
}
```

화면에는 어떤 문장이 보일까?

### 문제 2: 빈칸

```jsx
function Title({ text }) {
  return <h1>___</h1>;
}
```

`text` 값을 표시하도록 빈칸을 채워 보자.

### 문제 3: 잘못된 코드 찾기

```jsx
function userCard({ name }) {
  return <div class="card">{name}</div>;
}
```

React 관점에서 고쳐야 할 부분 두 개는 무엇일까?

<details>
<summary>정답과 상세 해설</summary>

### 문제 1

`민수님 환영합니다.`가 표시된다. 부모 `App`이 문자열 `"민수"`를 `name` props로 전달하고, `Welcome`이 그 값을 JSX 안에 넣는다.

### 문제 2

```jsx
return <h1>{text}</h1>;
```

JSX에서 JavaScript 변수의 값을 표시하려면 중괄호가 필요하다. `<h1>text</h1>`라고 쓰면 변수의 값이 아니라 글자 `text`가 표시된다.

### 문제 3

```jsx
function UserCard({ name }) {
  return <div className="card">{name}</div>;
}
```

사용자 컴포넌트 이름은 대문자로 시작하고, JSX의 CSS 클래스 속성은 `className`을 사용한다. Vue 템플릿에 익숙하면 `class`를 쓰는 실수가 특히 자연스럽다.

</details>

## 현재 프로젝트 연결

`frontend/src/components/atoms/Button.jsx`는 버튼이라는 작은 UI 책임을 가진 컴포넌트다. 페이지는 이 컴포넌트에 `children`, `type`, `variant` 같은 props를 전달하고, 버튼은 전달받은 값에 맞는 JSX를 반환한다.

## 세 문장 요약

1. React 컴포넌트는 화면을 설명하는 JSX를 반환하는 함수다.
2. props는 부모가 자식에게 전달하는 읽기 전용 입력값이다.
3. JSX는 HTML과 비슷하지만 `className`, `{표현식}`, 닫는 태그 같은 JavaScript 규칙을 따른다.
