# 2단계: 이벤트, 조건, 목록

## 이벤트 처리

이벤트는 사용자의 행동이 발생했을 때 실행할 함수를 JSX에 연결하는 방식이다.

```jsx
function SaveButton() {
  function handleClick() {
    alert('저장했습니다.');
  }

  return <button onClick={handleClick}>저장</button>;
}
```

`onClick={handleClick}`은 함수 자체를 전달한다. `onClick={handleClick()}`은 렌더링 중 함수를 즉시 실행하므로 목적이 다르다.

Vue의 `@click="handleClick"`과 역할은 같지만 React에서는 JSX 속성에 JavaScript 함수 값을 전달한다.

## 조건부 렌더링

React에는 `v-if` 지시자가 없다. JavaScript 조건식을 사용한다.

```jsx
function LoginMessage({ loggedIn }) {
  return <p>{loggedIn ? '환영합니다.' : '로그인이 필요합니다.'}</p>;
}
```

조건이 참일 때만 표시하려면 `&&`를 사용할 수 있다.

```jsx
{error && <p role="alert">{error}</p>}
```

왼쪽 값이 문자열이나 숫자일 때는 주의한다. `{count && <p>항목 있음</p>}`에서 `count`가 `0`이면 화면에 0이 나타날 수 있다. 명확하게 `{count > 0 && ...}`라고 쓴다.

## 목록 렌더링과 key

Vue의 `v-for` 대신 배열의 `map()`으로 JSX 배열을 만든다.

```jsx
function UserList({ users }) {
  return (
    <ul>
      {users.map((user) => (
        <li key={user.id}>{user.name}</li>
      ))}
    </ul>
  );
}
```

`key`는 React가 이전 목록과 다음 목록의 같은 항목을 식별하는 표식이다. 화면에 출력되는 props가 아니며 형제 사이에서 고유하면 된다. 항목 순서가 바뀌거나 삭제될 수 있다면 배열 index보다 데이터의 고유 ID를 사용한다.

## 사용하지 않아도 되는 경우

- 단순히 모든 항목을 한 문자열로 합칠 목적이면 JSX 목록이 필요하지 않다.
- 컴포넌트가 이미 명확한 boolean props를 받는다면 별도 조건 state를 만들 필요가 없다.
- 클릭 동작이 없다면 의미 없는 빈 이벤트 핸들러를 추가하지 않는다.

## 자주 하는 실수

- `onClick={save()}`로 즉시 실행한다.
- `map()`의 중괄호 본문에서 `return`을 빠뜨린다.
- key로 매번 `Math.random()`을 만들어 React가 매번 새 항목으로 인식하게 한다.
- 조건만 다르다는 이유로 거의 같은 JSX를 두 벌 작성한다.

## 모바일 연습

```jsx
function Menu({ items, visible }) {
  if (!visible) return null;

  return items.map((item) => (
    <button key={item.id}>{item.label}</button>
  ));
}
```

1. `visible`이 `false`면 무엇이 보일까?
2. 항목이 3개면 button은 몇 개 만들어질까?
3. 클릭할 때 `select(item.id)`를 실행하는 속성을 한 줄로 작성해 보자.

<details>
<summary>정답과 상세 해설</summary>

1. `null`은 렌더링할 UI가 없다는 의미이므로 아무것도 보이지 않는다.
2. `map()`은 항목마다 JSX 하나를 반환하므로 button 세 개가 만들어진다.
3. `<button key={item.id} onClick={() => select(item.id)}>`이다. 인자가 필요하므로 새 함수를 전달한다. `select(item.id)`만 전달하면 렌더링 중 실행된다.

</details>

## 현재 프로젝트 연결

`WorkspaceTabs.jsx`는 탭 배열을 목록으로 렌더링하고 각 탭의 ID를 key로 사용한다. 탭 선택과 닫기 동작은 이벤트 핸들러로 연결된다.

## 세 문장 요약

1. 이벤트 속성에는 실행 결과가 아니라 실행할 함수를 전달한다.
2. 조건부 UI는 JavaScript의 `if`, 삼항 연산자, `&&`로 표현한다.
3. 목록의 key는 React가 항목의 정체성을 추적하는 안정적인 값이어야 한다.
