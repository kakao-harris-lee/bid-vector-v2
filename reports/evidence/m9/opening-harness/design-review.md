# M9/opening-harness — 설계 검토 (구현 전, Phase 2.5)

## (0) 경계 한 문장

이 slice 가 막는 것은 **「하네스가 개찰 축을 다룬다」는 선언이 실제 거동 없이 서는 것**이다 — 아홉 갈래를
낳았는지를 산문이나 fixture 주석이 아니라 **등식 둘**이 진다: 로컬은 「설계가 선언한 상태 집합 == 계약 열거
전수」, CI 는 「살아 있는 API 응답 == 그 설계」. 막지 않는 것: 서버의 상태 판정이 옳은가(그것은 7C⑤-a 의
전수 test 몫) · 수집·승격의 도메인 규칙(6G·7C⑤-0 몫).

## (1) 열거인가 구성인가

**열거다.** 상태는 계약의 닫힌 아홉이고 하네스는 그 전수를 낳아야 한다. 그래서 기대 집합을 하네스 쪽에
리터럴로 적지 않고 **tsc 가 전수를 강제하는 표**(`satisfies Record<OpeningResultState, …>`)의 키에서 끌어온다 —
계약이 열을 더하면 그 표가 먼저 붉어지고(typecheck) 이어 이 등식이 붉어진다. 아홉을 spec 에 베껴 적으면
계약이 늘어난 날 하네스는 조용히 여덟만 낸다.

오퍼레이션 이름도 열거다. 출하 기본값(Kotlin `KonepsOpeningEndpointProperties`)과 하네스 fixture 는 **언어가
달라 사본 둘**이고, 이 slice 는 그 둘을 **집합 등식**으로 잇는다(PR #130 이 「기계 등식이 없다」고 적은 자리).

## (2) 우회 ≥5

| # | 우회 | 무엇이 막는가 |
| --- | --- | --- |
| 1 | 갈래 하나의 fixture 를 지운다 | 로컬 등식 RED — 설계 집합이 계약 열거의 진부분집합이 된다(acceptance 의 변이) |
| 2 | fixture 의 `expectedOpeningState` 만 거짓으로 적는다(데이터는 그대로) | 로컬은 초록, **CI 등식 RED** — 살아 있는 응답이 그 선언과 다르다 |
| 3 | 오퍼레이션 이름을 출하 기본값과 갈라 적는다 | Kotlin 정본 대조 등식 RED(로컬) — 앞 판이면 fake 가 전부 404 를 내고 수집이 **조용히 0건**이었다 |
| 4 | 수집·승격 러너 호출을 `journey-up` 에서 뺀다 | 승격 계수 등식 RED(`promoted=0`) + CI 등식 RED |
| 5 | 승격 계수 등식을 「≥ 1」로 느슨하게 둔다 | 갈래 다섯 중 넷이 사라져도 초록이므로 **정확히 같은 수**로 둔다(계수 등식이 그 선택의 이유다) |
| 6 | 개찰 축 fixture 를 기존 여정 공고에 붙인다 | `NOT_OPENED_YET`·`NOT_COLLECTED` 는 **개찰 축이 없는 공고**로만 서므로 그 둘이 사라지고 등식 RED |
| 7 | 승격을 모든 공고에 돌려 `OBSERVED_NOT_PROMOTED` 를 지운다 | 그 갈래는 **승격 범위 밖 공고**(다른 실행 상태 디렉터리의 확정 표본)로만 서므로 등식 RED |

## (2b) 값 획득 축 — 새 public 표면 전수

| 표면 | 자리 | 값이 어떻게 들어오는가 |
| --- | --- | --- |
| `OPENING_AXIS_OPERATIONS` | `web/e2e/harness/opening-axis.mjs` | 출하 기본값의 사본. 등식 test 가 Kotlin 정본과 대조한다 |
| `shippedOpeningOperationPaths(text)` | 같은 파일 | Kotlin 속성 파일 본문을 받아 경로 리터럴을 낸다(순수 — test 가 주입한다) |
| `openingAxisDesign(fixture)` | 같은 파일 | fixture → 공고별 설계(업무·진행구분·기대 상태) |
| `openingAxisRouteTable(design)` | 같은 파일 | 오퍼레이션 → 행 생성 함수. fake 가 이것만 쓴다(라우팅 술어가 한 자리다) |
| `promotionExpectation(design)` | 같은 파일 | 수집·승격 로그 줄과 맞댈 계수(frame·sampled·groupsRead·promoted·outsideSample) |
| `assertRunnerCounts(line, expected)` | 같은 파일 | 러너 로그 줄 ↔ 계수 등식. 어긋나면 **줄 전문을 담은** 오류를 던진다 |
| `fixtures/opening-axis.json` | 새 fixture | 공고 설계표 + `_provenance`(in-repo 계약·mock 의 sha256) |
| 여정 맥락의 `openingAxis` 칸 | `journey-up.mjs` → `CONTEXT_PATH` | spec 이 읽는 설계표(공고 → 기대 상태). 자격 값이 아니다 |

커널 주입 자리: `opening-axis.mjs` 는 **부수효과 0**(서버·파일 열기 0 — fixture 는 호출자가 읽어 넘긴다)이라
`fake-koneps.mjs` 를 불러도 포트를 요구하지 않는다. PR #130 이 같은 성질을 fake 에 세우는 중이므로 그 구조를
거스르지 않는다.

## (3) 과잉·미달

- **미달(의도)**: 낙찰 목록 축(`OPENING_AWARD_LIST`)은 출하 수집 배선에 오퍼레이션이 **없다** — 확정 낙찰금액·
  낙찰자 상호·낙찰하한율은 그 축의 슬롯이라 승격 뒤에도 부재다. 대사 축이 `UNDECIDABLE` 로 남는 것은 그
  때문이고, 7E-3b 의 단언이 그대로 선다. 그 축을 여는 것은 수집 production 변경이라 이 slice 밖이다.
- **미달(의도)**: 잠정 1위 축은 개찰완료 상세 축에서 오고 그 축은 **연령 게이트**(24h) 뒤에만 불린다 —
  공고마다 갈리므로 spec 은 그 칸을 **응답에서 읽어** 대조한다(리터럴 기대값을 적지 않는다).
- **과잉 아님**: 상세 축 넷을 섬기는 이유는 수집 러너가 표본 공고마다 그 축을 부르고, 404 면 그 축이 실패로
  정착해 러너가 미완 종료 코드를 내 하네스가 죽기 때문이다(섬기지 않는 선택지가 없다).
- **과잉 아님**: 업무 둘(공사·용역)을 쓰는 이유는 `OBSERVED_NOT_PROMOTED` 를 **결정적으로** 세우는 길이
  「승격 범위 밖 공고」 하나뿐이고, 표본 크기로 범위를 좁히면 어느 공고가 빠지는지가 **표본 seed** 가 정해
  설계가 선언할 수 없기 때문이다. 업무로 가르면 실행 상태 디렉터리가 둘이 되고 승격은 하나만 받는다.
