# M7/7C⑤-b 실행 기록 — 내 입찰 결과 조회

base main `003ec91d` · worktree `bid-vector-v2-m7-7c5b` · 브랜치 `m7-7c5b/2026-10-31` · push 0.

## RED 선행

| 층 | RED 확인 | GREEN |
| --- | --- | --- |
| 대사 구성·집계·차이 | `:workflow:compileTestKotlin` 미해결 참조(`reconcileBid`·`ReconciliationVerdict`·`ReconciliationBlocker`) | `:workflow:test --tests 'bidvector.workflow.result.*'` |
| 조회 조립 | 같은 자리에서 `ReadMyBidResults`·포트 셋 미해결 | 같음 |
| 읽기 어댑터 | — (실 DB test 는 어댑터와 같은 커밋) | `:adapters:test --tests 'bidvector.adapters.result.*'` |
| 계약 대조 | — | `:app:test --tests 'bidvector.app.http.OpenApiMyBidResultContractTest'` |

RED 은 **구현 전에** 각 층마다 확인했다(컴파일 실패 = 그 층의 어떤 단언도 아직 서지 않는다).

## 핵심 결과

| 명령 | 결과 |
| --- | --- |
| `:workflow:test --tests 'bidvector.workflow.result.*'` | BUILD SUCCESSFUL |
| `:adapters:test --tests 'bidvector.adapters.result.*'` | BUILD SUCCESSFUL |
| `:app:test --tests 'bidvector.app.http.OpenApiMyBidResultContractTest'` | BUILD SUCCESSFUL |
| `web` 생성 타입 — `npm run api:types` 뒤 `npm run api:types:check` | 바이트 등식 통과 |

Gradle 은 전부 `~/.local/bin/bv-build.sh ./gradlew --no-daemon …` 경유. 호스트 점검 셋(java 대기 ·
`free -m` · RSS 상위)은 빌드와 **다른 호출**로 먼저 돌렸다.

## 무엇이 어디서 잠기는가

| 방어 | 잠그는 자리 |
| --- | --- |
| 판정 불가 사유 **열**이 각각 도달 가능 | `ReconcileBidTest` — `ReconciliationBlocker.entries` 를 돌며 소진 `when` 이 준 입력이 그 사유를 내는지 확인(손으로 든 목록이 아니다) |
| 유찰·재입찰이 판정 불가로 접히지 않음 | 같은 class |
| 개찰 축이 커널 축보다 먼저 | 같은 class |
| 분모 0 이 0% 가 아님 · 「셀 것 없음」↔「전부 제외」 | `MyBidResultTallyTest` |
| 「분자 + 제외 = 표본」 | `AxisTally` 의 파생 + `MyBidResultAggregate` 생성자 단언, `MyBidResultTallyTest` |
| 차이가 잠정으로 채워지지 않음 | `awardGapOf` 시그니처(잠정 자리 없음) + `MyBidResultTallyTest` |
| 통화 축 전제 | `MyBidResultTallyTest` 의 어휘 래칫 |
| 다른 계정 혼입 | 포트 서명 + `JdbcMyBidResultViewTest`(실 DB) + `MyBidResultEndpointTest`(401) |
| 페이퍼 혼입 | `recordedRealBidOrNull` 타입 + 실 DB test |
| 메모·증빙·건너뜀 사유 미노출 | 열 목록 등식 + **메모를 심어** 산출 전문에 없음을 값으로(실 DB test) |
| 낙찰자 상호 미노출 | 개찰 사실 경로의 표지 글자 부재(실 DB) · 응답 전문의 표지 글자 부재(wire) · 계약 칸 이름 금지어 |
| 쓰기 0 | 읽기 전용 증인(조회마다 따로) + 네 표 행 수 불변 + **음성 대조**(쓰기 뒤 계수 변화) |
| 계약 ↔ 타입 키 등식 | `OpenApiMyBidResultContractTest`(최상위 2 · 건 23 · 집계 16, 전부 required) |

## 변이(바꿔치우기) — 버릴 clone 에서

미실행. 계획한 변이는 아래이고, 각각 **더하기가 아니라 바꿔치우기**다.

1. `judgeAwardOutcome` 의 `NotRecorded` 갈래를 `Lost` 로 바꾼다 → 「미공개가 패찰로 접히지 않는다」가 붉어야 한다.
2. `AxisTally.denominator` 에 `undecidedCount` 를 더한다 → 분모 단언과 집계 등식이 붉어야 한다.
3. `awardGapOf` 가 `AwardAbsent` 대신 `Computed(submitted.won, …)` 를 낸다 → 차이 부재 단언이 붉어야 한다.
4. `MyBidResultSql.RECORDED_BID_COLUMNS` 에 `memo` 를 더한다 → 열 목록 등식과 표지 글자 단언이 붉어야 한다.
5. `SELECT_CANONICAL` 에 `final_award_company_name` 을 더한다 → 개찰 사실 표지 단언이 붉어야 한다.
6. `SELECT_RECORDED_BIDS` 의 `c.bid_kind = ?` 를 걷는다 → 페이퍼 배제 단언이 붉어야 한다(타입 층이 먼저 잡으면 그 사실을 적는다).
7. `JdbcRecordedBidView` 가 읽기 전용 트랜잭션을 열지 않는다 → 증인 단언이 붉어야 한다.
8. 응답 조립이 `reconciledAwardRate` 에 분모 0 일 때 `"0"` 을 싣는다 → 「부재」 단언이 붉어야 한다.

## acceptance

미실행 — `ci.yml` 의 Kotlin `check` + `qualityBaseline`, `web` job 전체.

## 알려진 제한

`checklist.md` 의 「알려진 제한」 여섯.
