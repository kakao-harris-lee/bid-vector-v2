# M9/gates-tests checklist

## 1. 설계 검토 — 술어마다 경계 한 문장 · 우회

### 항목 1 — persistence 의존 게이트 (`OPEN-PERSISTENCE-GATE-PREDICATE-TYPE`)

**경계**: `adapters.persistence` 의 **컴파일된 class** 가 허용 루트 넷 밖의 `bidvector` 좌표를
상수 풀에 두지 않는다. **재지 않는 것**: 다른 패키지·모듈 · 컴파일 시 인라인되는 `const val` ·
값이 흐르는 경로 일반.

**열거인가 구성인가**: 금지 목록(층 밖 domain 루트 여섯 열거) → **허용 목록**. 열거되지 않은
새 domain 모듈이 조용히 통과하는 자리가 사라진다. 새 public 표면 없음(test 내부 술어).

| 우회 | 막히는가 |
| --- | --- |
| import 없이 전체 한정 좌표로 층 밖 domain 참조 | **막힘** — 이 slice 가 닫은 그 자리(변이 실측, commands) |
| 허용 루트의 **이름 접두**를 공유하는 이웃 패키지 | 막힘 — 세그먼트 경계 단언 |
| `Class.forName("bidvector.decision.X")` 류 문자열 좌표 | 막힘 — 정규식이 점 표기도 본다 |
| `const val` 이 호출부로 인라인돼 참조를 남기지 않음 | **안 막힘** — 형제 여덟과 공유하는 등재된 사각(`OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT`) |
| 코드를 persistence 밖 새 패키지로 옮겨 거기서 호출 | 이 test 의 모집단 밖 — 형제가 각자 자기 패키지를 보고, 새 패키지는 `packageOwnershipGate` 와 등재가 받는다 |
| 산출물 디렉터리를 비워 「위반 없음」으로 만들기 | 막힘 — 디렉터리 존재 `check` + 비지 않음 단언 |
| 등재에서 빼기 | 막힘 — `gateRegistrationGate` 양방향 등식 |
| 허용 목록에 루트를 더하기 | **구조적으로 열려 있다** — 게이트 자체를 바꾸는 일이라 리뷰가 받는다 |

### 항목 2 — CHECK 본문 단언 (`OPEN-CHECK-BODY-PRESENCE-ASSERTIONS`)

**경계**: `collection_run` 의 CHECK 본문 **집합**과, `notice_round` 형식 CHECK 를 지닌 **여섯 표**
각각의 그 본문이 정확히 고정된다. **재지 않는 것**: 그 밖의 표의 다른 CHECK(각자의 test) · 거동.

| 우회 | 막히는가 |
| --- | --- |
| 제자리 `OR TRUE` 로 항진명제화 | 막힘 — 본문 문자열 자체가 바뀐다(옛 존재 단언은 통과했다) |
| 문자 클래스·범위를 좁히기 | 막힘 — 같은 이유 |
| 같은 본문을 공유하는 **여섯 중 다섯**을 약화 | **막힘** — 단언 단위를 스키마에서 표로 내렸다(옛 술어는 하나만 살아도 초록) |
| 제약을 삭제 | 막힘 — 집합 등식(`collection_run`) · 0 행 명시 실패(`notice_round`) |
| 제약을 추가 | 막힘 — 집합 등식 + 기존 개수 축 |
| 본문이 같은 CHECK 둘(집합이 하나로 접는다) | 막힘 — 개수 축이 센다(집합 혼자서는 못 센다) |
| 같은 이름 제약을 다른 표에 두고 먼저 오게 하기 | 막힘 — 중복 이름 명시 실패 |
| 마이그레이션이 아니라 기대값을 고치기 | **구조적으로 열려 있다** — 게이트 자체 변경 |

### 항목 4 — 감시 port 필드 축 (`OPEN-6F4W-UNAVAILABLE-PRODUCER`)

**경계**: `NoticeWatchSubjectPort`·같은 파일의 file-class 두 class 의 `Fieldref` **전수**가 허용
목록(원소 하나)과 같다. **재지 않는 것**: 다른 class · 그 밖의 상수 풀 축(메서드 축이 따로 본다).

| 우회 | 막히는가 |
| --- | --- |
| `subjectFor` 가 `Unavailable` 을 반환 | **막힘** — production 파일 변이로 실측. **메서드 축은 같은 변이에 초록**(음성 대조) |
| 사유 상수를 다른 `ReasonCode` 로 교체 | 막힘 — 좌표가 바뀌어 허용 목록 하나를 벗어난다(D-6F4W-4 가 함께 잠긴다) |
| 다른 class 의 factory 를 불러 `Unavailable` 을 얻기 | 막힘 — 그 호출이 메서드 축 허용 목록 밖이다 |
| 같은 패키지에 **새 class** 를 만들어 거기서 생산 | **안 막힘** — 이 두 class 의 모집단 밖이고 패키지 단위 허용 루트는 `workflow.evaluation` 을 이미 허용한다(아래 「알려진 제한」) |
| 산출물 부재 | 막힘 — 파일 존재 `check` |
| 등재에서 빼기 | 막힘 — `gateRegistrationGate` |

## 2. OPEN 처분

| OPEN | 처분 |
| --- | --- |
| `OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` | **닫힘 후보** — 술어를 상수 풀 허용 목록으로 이식, 변이로 양쪽 실측 |
| `OPEN-CHECK-BODY-PRESENCE-ASSERTIONS` | **닫힘 후보(CI 초록 전제)** — 존재 단언 셋이 사라졌고 `any { contains }` 형태가 그 파일에 0 이다. 함께 받은 0 행 자리도 명시 실패로 |
| `OPEN-6F4W-UNAVAILABLE-PRODUCER` | **닫힘 후보 — 「불가」 처분**. 순수 전(total) 매퍼라 이 어댑터가 그 갈래를 낼 길이 없다(D-6F4W-3). 갈래 자체는 **지우지 않는다**: port 의 어휘이고 소비자 둘(후보 탈락·분석 정지)이 실제 분기를 진다. 대신 「낼 수 없다」를 산문에서 **구조**로 옮겼다 |
| `OPEN-VERDICT-CONSTRUCTION-VISIBILITY` | **열린 채 — 미착수**. §3 |
| 신설 OPEN | 없음 |

## 3. 항목 3 미착수 — 처방이 다른 차단과 충돌한다

계약의 처방은 「`LicenseVerdict` subtype 생성자를 `internal` + `@ConsistentCopyVisibility`,
test 조립은 **testFixtures factory** 로」다. `java-test-fixtures` 는 **전 모듈에서 차단**돼 있고
(빌드 규약이 그 플러그인 적용 시 평가를 끊는다 · `module.expected-source-sets` 가 `main,test`
둘 · 레이아웃 게이트의 기대 컴파일 task 둘 · 그 차단 자체의 회귀 test) 그 차단의 근거가
**`OPEN-1BC-TESTFIXTURES-GATE` — 「면제·순수성 규칙을 ADR 로 먼저 정한 뒤에 연다」**다.
즉 ADR 선행이 그 OPEN 자신의 조건이다.

실측 둘 더 — ⓐ `internal` 로 내리면 **in_scope 밖 test 14 파일**이 컴파일 실패한다(`app` 열 ·
`workflow` 넷. 같은 모듈인 `qualification` test 둘은 보인다). ⓑ Kotlin 의 `internal` 은
testFixtures 컴파일에서도 **안 보인다**(module = compilation) — 처방대로 가려면 그 컴파일을
main 과 묶는 배선까지 필요하다. main 생산 지점이 커널 둘뿐이라는 D-6F5-20 실측은 **맞다**.

그래서 ADR + 빌드 규약 + 정책 파일 + 14 test 가 한 묶음이고, 전부 이 slice 의 in_scope 밖이다.
팀장에게 ① 별도 slice 로 분리(추천) ② 범위 확대 승인 중 하나를 요청했다 — 그 판단 전에는
이 항목을 건드리지 않는다.

## 4. 알려진 제한

- **항목 2 의 DB 단언은 이 레인에서 한 번도 실행되지 않았다.** 그 test 는 Testcontainers
  Postgres 를 띄우고 레인 규율이 container 를 로컬에서 금지한다. 기대 본문 문자열의 근거는
  ⓐ 교체 대상 존재 단언 둘의 축어(COL-06·H-3 — 그 둘은 실제 출력의 부분 문자열이었다)와
  ⓑ 같은 파일에서 이미 통과하는 일곱 본문의 정규화 형태다. **첫 실측은 PR CI 다.**
- 항목 1 의 `const val` 인라인 사각은 형제 여덟과 공유하는 등재된 OPEN 이고 이 slice 가
  새로 만든 것이 아니다. 앞 판의 소스 텍스트 축이 그 자리를 **덮고 있었던 것도 아니다**
  (그 축도 import 문만 봤다).
- 항목 4 의 필드 축은 **두 class 의 모집단**이다. 같은 패키지에 새 class 를 세워 거기서
  `Unavailable` 을 생산하는 경로는 이 축 밖이고, 패키지 단위 허용 루트가 그 좌표를 이미
  허용한다. 그 경로를 닫으려면 모집단을 패키지 전수로 넓혀야 하고 그것은 다른 결정이다.
- 등재부 문면 정정 하나를 함께 냈다 — 기존 문단이 `PersistenceAdapterDependencyTest` 를
  이미 「바이트코드 상수 풀 게이트」로 적었는데 그때는 소스 텍스트였다. 이제 참이 되고,
  같은 문단이 함께 드는 `EventAdapterDependencyTest` 는 **여전히 소스 텍스트**다(in_scope 밖).

## 5. in_scope 대조

내 커밋이 건드린 경로 **여섯** 전부 in_scope 안이다(evidence 둘 포함). 한 자리는 계약 문면의
읽기에 달려 있다 — 항목 4 의 필드 축을 더한 파일은 `adapters/src/test/kotlin/**`(계약은
「해당 test」로 적는다) 아래의 **감시 port 게이트 test** 다. 계약이 그 port 파일을 「(+test)」로
들고, 그 test 가 이 항목의 주장을 지는 자리라 그 읽기로 진행했다 — **팀장 확인 대상**으로
보고에 올렸다.
