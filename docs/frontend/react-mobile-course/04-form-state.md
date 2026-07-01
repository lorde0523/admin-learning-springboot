# 4단계: 입력 폼과 상태 설계

## controlled input

React state가 입력값의 기준이 되는 입력 요소를 controlled input이라고 한다.

```jsx
function NameInput() {
  const [name, setName] = useState('');

  return (
    <input
      value={name}
      onChange={(event) => setName(event.target.value)}
    />
  );
}
```

사용자가 입력하면 브라우저가 change 이벤트를 만들고, 핸들러가 새 문자열을 state에 저장한다. 다시 렌더링된 `value`가 입력창에 표시된다.

Vue의 `v-model`은 값과 이벤트 연결을 간단히 감춘다. React에서는 `value`와 `onChange`를 명시해 데이터 흐름을 드러낸다.

## 제출 처리

```jsx
function SearchForm({ onSearch }) {
  const [keyword, setKeyword] = useState('');

  function handleSubmit(event) {
    event.preventDefault();
    onSearch(keyword.trim());
  }

  return <form onSubmit={handleSubmit}>{/* input과 button */}</form>;
}
```

`preventDefault()`는 브라우저의 기본 폼 제출과 페이지 이동을 막는다. 버튼 클릭뿐 아니라 Enter 제출도 지원하려면 form의 `onSubmit`을 사용한다.

## state 위치 정하기

state는 그 값을 사용하는 컴포넌트들의 가장 가까운 공통 부모에 둔다.

- 한 입력만 사용한다: 입력 컴포넌트 내부
- 검색어와 결과 목록이 함께 사용한다: 둘을 감싼 부모
- 여러 화면이 공유한다: Context나 store 검토
- 서버에서 가져온 데이터다: React Query 검토

## 불필요한 state 피하기

`firstName`과 `lastName`이 state라면 `fullName`은 렌더링 중 계산한다. props를 state로 복사하는 것도 대부분 피한다. 두 개의 진실 원천이 생겨 서로 어긋날 수 있기 때문이다.

## 모바일 연습

다음 빈칸을 채워 입력한 이메일이 `<p>`에 표시되게 하자.

```jsx
const [email, setEmail] = useState('');

<input
  value={email}
  onChange={(event) => __________}
/>
<p>{email}</p>
```

<details>
<summary>정답과 상세 해설</summary>

`setEmail(event.target.value)`이다. `event.target`은 이벤트가 발생한 input이고 `value`는 현재 입력 문자열이다. setter가 새 값을 저장하면 컴포넌트가 다시 렌더링되어 input과 p가 같은 값을 표시한다.

</details>

## 현재 프로젝트 연결

`SearchBar.jsx`는 검색 입력이라는 공통 상호작용을 담당한다. 페이지는 검색 조건과 실제 조회 시점을 소유하고, 공통 컴포넌트는 표시와 사용자 입력 전달에 집중해야 한다.

## 세 문장 요약

1. controlled input은 state를 입력값의 기준으로 삼는다.
2. state는 그 값을 함께 사용하는 컴포넌트들의 가장 가까운 공통 부모에 둔다.
3. 기존 값에서 계산 가능한 값은 별도 state로 저장하지 않는다.
