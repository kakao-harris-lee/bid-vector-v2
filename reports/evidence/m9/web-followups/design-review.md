# M9 / web-followups — 설계 검토 (게이트·계약형 셋)

다섯 가운데 **셋이 게이트를 바꾼다**(모집단 · lint 범위 · 교차언어 등식). 나머지 둘은 표와 순수
함수의 거동 변경이라 test 로 잠근다. 아래는 공통 규율이 요구하는 짧은 검토다 — 경계 한 문장 ·
새 public 표면 전수 · 우회 ≥5.

## ① 명령 어휘 표 (`OPEN-M9-TIMELINE-COMMAND-WORDING`)

**경계**: 타임라인이 **그린 글자**에 명령 토큰 원문이 0 이고, 명령의 문면은 전수 표
(`satisfies Record<…>`) 에서만 온다.

**새 public 표면**
| 표면 | 자리 |
| --- | --- |
| 명령 어휘의 문자열 유니온(갈래 15) | `web/src/display/caseDisplay.ts` |
| 명령 표시 표(전수) | 같은 파일 |
| 명령 조회 함수 하나 | 같은 파일 — 계약이 그 칸을 `string` 으로 내므로 키 좁힘을 한 자리에 둔다 |

<!-- 새 식별자의 이름을 축어로 적지 않는다: evidence 는 비밀값 스캔의 대상이고 이 어휘가 그
     목록에 걸린다(PR #125 의 같은 자리 — baseline 등재가 아니라 문장을 고치는 쪽이다). -->


**우회**
1. 표를 지나지 않고 `entry.command` 를 그대로 보간 → DOM 게이트의 금지 목록에 **명령 토큰을
   더한다**(표의 키에서 유도). 홀로 선 토큰·잎 글자 등식 둘 다 받는다.
2. 토큰을 다른 글자와 한 요소에 섞어 그리기(`관심 · ADD_TO_BID_BOX`) → 잎 등식은 못 보고
   **홀로 선 토큰** 술어가 잡는다(앞 라운드가 실제로 이 꼴을 보았다 — `_` 경계로 보호된다).
3. 표의 한 행을 지우기 → 표가 그 유니온에 `satisfies` 로 묶여 있어 컴파일에서 멈춘다.
4. 유니온까지 함께 줄이기(③ 을 비켜 간다) → ⑤ 의 교차언어 등식이 Kotlin `BidCommandTag` 와
   맞대 멈춘다.
5. Kotlin 이 명령을 **늘림** → 같은 등식이 붉어진다(앞 판은 web 이 어디서도 붉어지지 않았다).
6. fixture 에서 명령 칸을 안 그리게 바꿔 금지 단언을 공허하게 → **음성 대조**(표의 문면이 같은
   자리에 그려져 있다)가 받는다.

**남는 자리**: 표 밖 토큰이 오면 `displayOrUnknown` 이 `알 수 없는 상태(…)` 로 **보이게** 낸다 —
그 괄호 안의 원문은 의도다(조용한 빈칸 금지). 그래서 금지 단언이 서려면 fixture 의 명령·상태가
**계약 어휘 안**이어야 하고, 이 라운드가 fixture 의 잘못된 값 하나를 고친다(명령 토큰을 상태 칸에
넣은 자리).

## ② KST 달력일 D-day (`OPEN-M9-DDAY-CALENDAR`)

게이트가 아니라 **거동 변경**이다. 24시간 나눗셈을 KST 달력일 번호의 차로 바꾼다.

- 경계: `daysUntil` 은 **KST 달력일의 차**를 센다 — 「남은 시간」이 아니다.
- 새 public 표면 없음(시그니처 동일, 반환 의미만 바뀐다).
- 내림·올림의 비대칭(`Math.floor`/`Math.ceil`)이 **없어진다** — 달력일 차는 정수라 `-0` 자리도
  사라진다. 그 두 분기를 지우는 것이 이 변경의 요점이고, 경계 test 가 양쪽을 잠근다.
- 거동이 갈리는 자리 둘을 test 로 고정한다: ⓐ 23:00 KST 에 이튿날 01:00 KST 마감 → `D-1`
  (앞 판 `D-0`) ⓑ 00:01 KST 에 어제 23:00 KST 마감 → `D+1`(앞 판 `D-0`). ⓑ 는 앞 판 CR L-2 가
  막으려던 「하루 과장」과 **다른 사실**이다: 달력으로 어제이면 `D+1` 이 맞다.

## ③ 모집단을 TS 심볼 그래프로 (`OPEN-M9-SCREEN-COVERAGE-SYMBOL-GRAPH`)

**경계**(PR #132 v M-3 으로 **사실에 맞게 좁혔다** — 앞 판 문면은 `src/screens/**` 전수를
주장했는데 훑기가 그보다 좁았다): `src/screens/**`(재귀 · `.ts`·`.tsx` · test 파일 제외)에서
**함수 선언 또는 화살표·함수 표현식으로 내보낸** 컴포넌트 가운데, **그 본문이 직접 어휘 함수를
호출하는 것**의 전수 == DOM 게이트의 마운트 목록. **「쓴다」의 판정은 호출 지점의 이름이 아니라
해소된 선언**이다.

그 문장이 **주장하지 않는 것**을 아래 「남는 자리」가 적는다 — 내보내는 꼴이 다르거나(감싸는
호출·개명 재수출·클래스), 부르지 않고 **넘기거나**, 비공개 컴포넌트를 **지나 그리는** 자리다.

**새 public 표면**
| 표면 | 비고 |
| --- | --- |
| `VOCABULARY_DECLARATIONS` | `<표시 모듈 상대경로>#<내보낸 이름>` 일곱. 앞 판 `VOCABULARY_CALLS`(호출 이름 일곱)를 **대체**한다 |
| `screenProgram(webRoot)` | 실제 `tsconfig.json` 으로 TS 프로그램을 연다 |
| `vocabularyComponentsOf(program, sourceFile)` | 시그니처가 바뀐다(원문 문자열 → 프로그램+파일) |
| `collectScreenVocabulary(webRoot)` | 인자가 디렉터리 → web 뿌리로 바뀐다 |
| `screenFilesIn(dir)` | PR #132 v M-3 — 화면 파일 훑기(재귀 · `.ts` 포함)를 떼어 낸 자리. 술어가 파일시스템이라 **버릴 디렉터리**로 잰다 |

**우회**
1. 별칭 import — `import { instantDisplayOf as fmt }; fmt(x)` → 해소된 시그니처의 선언이 원
   모듈이라 걸린다(앞 판: 초록).
2. 재수출 모듈 경유 — `export { instantDisplayOf } from './timeDisplay'` → 알리아스 사슬을
   풀어 같은 선언에 닿는다(앞 판: 초록).
3. 네임스페이스 접근 — `import * as display; display.instantDisplayOf(x)` → 멤버 호출도 해소된다
   (앞 판은 **식별자 호출만** 보았다).
4. 지역 변수 경유 — `const f = instantDisplayOf; f(x)` → `getResolvedSignature` 가 원 선언을 낸다.
5. 선언 목록이 썩기(이름을 바꿨는데 목록이 옛 이름) → 목록의 각 항목이 **실제로 그 모듈의 내보낸
   함수인지** 단언한다. 없으면 RED.
6. 마운트 목록에서 카드를 빼기 → 집합 **등식**이 받는다(포함이 아니다 — 앞 판과 같다).
7. 내보내지 않은 내부 컴포넌트 → 의무 밖이다(마운트 대상이 아니므로 의도다 — 화면 test 가 받는다).

8. 하위 디렉터리의 카드 · JSX 를 쓰지 않는 `.ts` 카드 → PR #132 v M-3 으로 **재귀 훑기 + `.ts`
   포함**이 닫는다(앞 판은 한 겹 평면에 `.tsx` 만이었다 — base 부터의 제한). 술어가 파일시스템
   자체라 **버릴 디렉터리**로 잰다(하위 둘 · `.ts` · test 둘 · 다른 확장자).

**남는 자리 — `OPEN-M9-SCREEN-COVERAGE-SYMBOL-GRAPH` 의 잔여**(PR #132 v M-3 · cr L-4 로 전수를
적는다). 아래는 전부 **오늘 `src/screens/**` 에 그런 꼴이 0 이라 실제 공백이 없고**, 의무 5 ==
마운트 5 를 리뷰가 재현했다.

| 잔여 | 왜 밖인가 |
| --- | --- |
| 새 어휘 함수 | 표시 모듈에 어휘를 더해 그것만 쓰면 `VOCABULARY_DECLARATIONS` 밖이다. 「표시 모듈의 내보낸 함수 **전부**」로 넓히는 길은 실측하면 의무 집합이 5 → 13 이 되고 질의 목이 필요한 패널 둘·훅 하나가 섞인다 |
| 감싸 내보내기 | `export const X = memo(…)`·`forwardRef(…)` — 초기값이 화살표가 아니라 **호출**이라 고려 대상 밖 |
| 개명 재수출 | `function Inner…; export { Inner as Card }` — `export` 수식자가 붙은 선언만 본다 |
| 클래스 컴포넌트 | 함수 선언·표현식만 본다 |
| 함수 **참조** 전달 | `ats.map(instantDisplayOf)` — 호출식이 아니라 해소할 호출이 없다. 이 slice 가 닫았다고 한 「쓴다」 축 **안**에 있는 잔여다 |
| 간접 렌더 | 내보낸 `Outer` 가 어휘를 부르는 **비공개 `Inner`** 를 그리면 그 파일이 의무를 하나도 내지 않는다(cr L-4). 판정이 그 컴포넌트 서브트리만 보기 때문이다 |

닫는 길은 파일 안 호출 그래프를 한 겹 따라가고(간접 렌더) 내보내기를 심볼로 해소하는 것
(`getExportsOfModule`)인데, 그러면 의무 집합이 마운트 가능성과 어긋나기 시작한다 — 그 교정은
화면 slice 의 일로 넘긴다.

## ④ 시계 lint 범위 (`OPEN-M9-CLOCK-LINT-SCOPE`)

**경계**: 출하되는 `src/**` 의 어느 파일도 **지금 시각을 스스로 얻지 않는다** — 승인된 한 자리
(`timeDisplay.ts`)를 뺀다. 「`Date` 라는 이름」이 아니라 **지금을 낼 수 있는 표면**이 대상이다.

**새 public 표면**: 없다(설정 상수 둘 — `CLOCK_MEMBERS` · `CLOCK_GLOBALS` — 과 블록 하나).

**우회**(전부 **실측으로 초록임을 먼저 확인**했다)
1. `new Intl.DateTimeFormat().format()` — 인자 없는 `format` 은 **지금**을 쓴다 → `DateTimeFormat`
   속성 이름 + 인자 없는 `format`·`formatToParts` 호출 둘로 닫는다.
2. 서식기를 **props 로 받아** 부르기(`fmt.format()`) — `Intl` 이름을 지나지 않는다 → 인자 없는
   호출 선택자가 받는다.
3. `Temporal.Now.instant()` → `Temporal` 전역 참조 금지 + 속성 이름.
4. `performance.timeOrigin` · `performance.now()` → 같은 둘.
5. **제외 디렉터리 경유** — `src/api/clock.ts` 가 `new Date()` 를 내보내고 화면이 import. 앞 판은
   `src/api/**`·`src/session/**` 가 시계 금지 **밖**이었다(실측 초록) → 그 둘을 제외에서 뺀다.
   `src/api/client.ts`(승인된 네트워크 자리)는 **시계만** 받는 블록으로 따로 둔다.
6. 소유자를 지나 집어 가기(`globalThis['performance']`) → 계산·템플릿 접근 선택자를 이름 목록의
   **구성**으로 만든다(네트워크 금지와 같은 관례 — 소유자를 열거하지 않는다).
7. **구조 분해**(PR #132 v M-1 = cr M-1) — `const { Date: D } = globalThis; new D()` ·
   `const { DateTimeFormat: F } = Intl`. 네트워크 금지는 이 축을 닫아 두었는데 시계 쪽에는 짝이
   없어 **초록**이었다(구조 분해는 `MemberExpression` 이 아니고, 지역 이름은 전역 참조가 아니며,
   `new D()` 는 `callee.name` 이 `Date` 가 아니다). 같은 목록에서 유도한 거울상을 더한다.
8. **빈 인자로 부르는 서식 호출**(v M-2 = cr M-2) — `f.format(undefined)` 은 인자 **개수**가 1
   인데 **지금**을 서식한다. spread 로 빈 배열을 펴는 꼴도 같다. 세 꼴(없음 · `undefined` ·
   spread)을 함께 본다. **인자 꼴은 `type` 과 함께** 봐야 한다 — `name` 만 보면 첫 인자가
   식별자가 아닌 **모든** 호출에 걸린다(리터럴·멤버·`new`·인자 0 개 넷을 실측).

**함께 고치는 것**: 이 블록의 `no-restricted-globals` 가 **앞 블록의 네트워크 전역 목록을
덮어쓰고 있었다** — 그래서 화면 파일의 **맨 `fetch('/x')` 가 초록**이었다(실측). flat config 의
덮어쓰기는 이 slice 가 `no-restricted-syntax` 쪽에서 이미 측정으로 잠근 함정이고, 같은 규칙의
전역 축이 그 측정 밖에 있었다. 목록을 상수로 두고 **둘 다 싣는다**(게이트 test 가 그 사실을 잰다).

**남는 자리**(PR #132 v L-1·L-2 로 전수를 적는다 — 앞 판은 셋만 적었다)

| 잔여 | 성질 |
| --- | --- |
| `document.timeline.currentTime` · `event.timeStamp` · `new File([], 'x').lastModified` · `document.lastModified` | **시계가 아닌 표면이 나르는 시각**. 이름 목록에 없다 |
| 반사 API — `Object.getOwnPropertyDescriptor(globalThis, 'Date')` · 계산으로 만든 이름(`globalThis['Da'+'te']`) | 네트워크 금지의 같은 제한과 **같은 부류**다(그쪽에도 축어로 등재돼 있다) |
| 구조 분해의 **따옴표·계산·템플릿 키** | 같은 리터럴의 다른 표기. 네트워크 쪽 ④ 와 같은 범위(키가 식별자일 때만) |
| **빈 값을 다른 글자로 적은 인자** — `void 0` · TS 문법으로 감싼 `undefined`(`undefined as never` · `undefined!` · `undefined satisfies undefined`) | 첫 인자 꼴 선택자는 `undefined` **식별자**와 spread 둘만 본다. 감싼 꼴은 노드가 `AsExpression`·`NonNullExpression`·`SatisfiesExpression` 이라 지나간다(v-132-r2 L-1). **표기를 열거하지 않는다** — 다음 문법에서 그 목록이 또 뒤처지고, 이 층은 「실수로 쓴 직접 호출」을 잡는 보조 층이다 |
| test 파일 | 고정 fixture 를 짓는 것이 그 일이고 **출하되지 않는다** — 제외가 의도임을 게이트 test 가 잰다 |
| **출하 파일이 test 파일을 import 하는 길**(v L-2) | `src/display/x.test.ts` 가 `new Date()` 를 내보내고 화면이 import 하면 제외를 지난다. 「test 는 출하되지 않는다」는 전제를 **지키는 게이트가 없다** — 번들 경계는 `web/src` 안이면 통과한다 |

마지막 줄이 이 층에서 가장 큰 잔여다. 닫는 길은 「출하 모듈이 `*.test.*` 를 import 하지 않는다」를
모듈 그래프 경계에 더하는 것인데, 그것은 시계 축이 아니라 **번들 경계 게이트의 일**이라 그 slice 로
넘긴다.

## ⑤ 금액·명령 어휘의 교차언어 등식 (`OPEN-M9-MONEY-VOCAB-CROSS-LANG`)

**경계**: wire 어휘를 **손으로 복제한 자리 전부**(web 표 · e2e 라벨 표)의 **키 집합**이 Kotlin 정본의
열거와 같다. 문면은 대상이 아니다 — e2e 의 축어 라벨은 **일부러** 산출물에서 끌어오지 않는다
(그 관례는 유지한다).

**새 public 표면**: `web/scripts/money-vocab-cross-lang.test.mjs` 하나(게이트 test). 산출물 쪽
표면은 늘지 않는다.

OPEN 의 처방 둘 가운데 **등식 test** 를 고른다 — openapi 에 enum 을 열거하는 길은 `openapi/**`·
Kotlin 이 이 slice 의 in_scope 밖이고, 계약 변경은 레인 격리를 깬다.

**우회**
1. Kotlin 이 `Basis`·`ProvenanceKind`·`Currency`·`VatTreatment`·`BidCommandTag` 에 값을 **더함** →
   집합 등식이 RED(앞 판은 web 이 어디서도 붉어지지 않고 「알 수 없는 상태」로 degrade 했다).
2. web 표에서 한 행을 지움 → 같은 등식이 RED(`satisfies` 는 유니온까지 함께 줄이면 지나간다).
3. e2e 라벨 표에서 한 행을 지움 → 같은 등식이 RED(앞 판은 e2e 표가 아무 계약에도 묶이지 않았다).
4. Kotlin 파일의 **열거 꼴을 바꿔** 읽기를 무력화(한 줄 열거 · 주석 삽입 · 뒤 쉼표) → 읽기를
   **닫히게** 짓는다: `enum class X {` 가 정확히 한 번 나와야 하고, 균형 중괄호 본문에서 식별자가
   하나도 안 나오면 **던진다**(조용한 빈 집합 금지). 그 성질 자체를 합성 Kotlin 원문으로 대조한다.
5. 정본 파일을 옮기거나 이름을 바꿈 → 경로를 읽지 못해 던진다(RED).
6. `provenanceLabel` 이 내는 **wire 문자열**이 `ProvenanceKind` 와 갈림 → 그 함수의 문자열
   리터럴 집합도 같은 등식에 넣는다(실제로 wire 에 나가는 것은 그 리터럴이다).
7. **읽기가 못 읽는 항목을 조용히 버림**(PR #132 v M-4 = cr L-2). 앞 판은 패턴에 안 맞는 조각을
   `filter` 로 떨어뜨렸고, 그래서 `@Deprecated("x") NEW_CMD` · 백틱 이름 · 소문자 항목이 **전부
   초록**이었다 — 이 게이트가 막으려는 「Kotlin 이 늘었는데 web 이 안 붉어진다」가 **게이트 안에**
   있었다. 그리고 쉼표로 먼저 쪼갠 뒤 **접두사**로 읽어 `A(FOO, BAR)` 에서 유령 항목 `BAR` 를
   내고 `Won` 을 `W` 로 절삭했다. 이제 인자 목록을 균형 괄호로 **먼저 떼고** 각 조각을 **전체
   일치**로 보며, 비지 않은 조각이 안 맞으면 **던진다**.

   읽기는 이름을 **대문자로 한정하지 않는다** — 소문자 항목도 그대로 내고 **등식**이 갈린 것을
   말한다. 읽기가 어휘 정책을 겸하면 「못 읽음」과 「어휘가 다름」이 한 자리에서 섞인다.

**남는 자리**: Kotlin 원문을 **텍스트로** 읽는다(node 에 Kotlin 파서가 없다). 그래서 술어는 「꼴이
바뀌면 **던진다**」로 닫고, 양성·음성 대조를 합성 원문으로 둔다 — 조용히 초록이 되는 길이 아니라
RED 가 되는 길만 남긴다. 본문을 가진 항목(`A { … }`)도 지금은 **던지는** 쪽이다(그 꼴이 정본에
없고, 읽기를 넓히는 것보다 RED 로 사람을 부르는 쪽이 이 게이트의 성질에 맞는다).

**이 텍스트 파싱 자체가 다음 계약 slice 의 후보다**(cr OQ → `OPEN-M9-COMMAND-OPENAPI-ENUM`).
형제 칸 `actorKind` 는 openapi 에 `enum` 이 있어 web 이 생성 타입에서 유니온을 끌어온다 —
`command` 만 `type: string` 이라 이 slice 가 ① 손 유니온 ② Kotlin 원문의 JS 텍스트 파싱을 세웠다.
`command` 를 enum 으로 올리면 ①② 가 함께 사라진다. `openapi/**` 가 in_scope 밖이라 이 slice 의
일이 아니고, 계약 slice 로 등재한다.
