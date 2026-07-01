# 9단계: 공통 UI 설계

## 현재 계층

이 프로젝트는 다음 방향으로 의존한다.

```text
atoms → molecules → organisms → templates → pages
```

- atom: Button, input처럼 가장 작은 UI
- molecule: SearchBar처럼 작은 UI들의 의미 있는 조합
- organism: EntityGrid처럼 독립적인 업무 영역
- template: 페이지의 배치와 영역
- page: route, query, 화면 흐름 연결

오른쪽 계층이 왼쪽을 사용할 수 있지만, atom이 특정 page를 import하면 재사용성과 의존 방향이 깨진다.

## 좋은 공통 컴포넌트

공통 컴포넌트는 여러 화면의 **같은 의미와 동작**을 한곳에 모은다.

```jsx
function Button({
  type = 'button',
  disabled = false,
  children,
  ...props
}) {
  return (
    <button type={type} disabled={disabled} {...props}>
      {children}
    </button>
  );
}
```

버튼은 클릭 가능 여부와 기본 type 같은 버튼의 책임을 가진다. 사용자 저장 API를 직접 호출하면 특정 업무에 묶이므로 `onClick`을 props로 받는다.

## 공통화 판단 질문

1. 두 코드가 모양만 비슷한가, 의미와 변경 이유도 같은가?
2. props가 지나치게 많아 특정 화면의 모든 차이를 숨기고 있지는 않은가?
3. 컴포넌트 이름만 보고 책임을 예상할 수 있는가?
4. 소비자가 내부 구현을 몰라도 사용할 수 있는가?

복사된 코드가 두 번 보였다는 이유만으로 즉시 추상화하지 않는다. 변경 방향이 같다는 증거가 생긴 뒤 공통화한다.

## 접근성과 상태

- icon-only 버튼에는 `aria-label`을 제공한다.
- 오류 문구에는 필요에 따라 `role="alert"`를 사용한다.
- 비활성 동작은 CSS만 흐리게 하지 말고 실제 `disabled`를 사용한다.
- input과 label을 연결한다.
- 로딩 중 사용자가 같은 요청을 중복 실행하지 않도록 한다.

## 모바일 연습

다음 중 공통 `Button`이 직접 알아야 할 정보는?

1. 버튼이 disabled인지
2. 사용자 저장 API URL
3. 버튼 안에 표시할 내용
4. 현재 페이지의 선택 행

<details>
<summary>정답과 상세 해설</summary>

1번과 3번이다. 저장 URL과 선택 행은 페이지 또는 기능 영역의 업무 정보다. 공통 Button은 이를 모르고 `disabled`, `children`, `onClick` 같은 일반적인 인터페이스로 동작해야 한다.

</details>

## 세 문장 요약

1. 공통 UI는 같은 의미와 변경 이유를 가진 표현을 재사용한다.
2. 낮은 계층은 페이지나 서버 통신 같은 업무 세부사항을 알지 않는다.
3. 재사용성뿐 아니라 접근성, disabled, 오류와 로딩 상태도 공통 계약에 포함한다.
