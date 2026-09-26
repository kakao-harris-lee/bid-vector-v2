# M6/6A-2b — 결정별 판정과 남는 것

정본은 `scope.md`(D-6A2b-1~13, 위협 모델, 우회 아홉, (2b) 표). 여기는 **그 각 줄이 무엇으로 닫혔는지**와
**닫히지 않은 것**을 적는다. 명령·종료 코드·변이 결과는 `commands.md`.

## 결정별 판정

| 결정 | 무엇으로 닫혔나 | 남는 것 |
|---|---|---|
| D-6A2b-1 endpoint 여섯 | 컨트롤러 하나(`/api/strategy/edit-sessions` 아래 여섯)가 실행기만 호출. 세션 id 는 서버가 만든다(UUID) | `SessionAlreadyActive` 는 HTTP 로 **도달할 수 없다**(아래 「알려진 제한 ①」) |
| D-6A2b-2 어댑터가 draft 를 조립 | `OperatorStrategy.toDraft()` + 필드 하나만 바꾸는 패치. `EditableField.MaxActiveBids` 추가, 세션 스냅숏 codec 왕복 | 마이그레이션 0(계약의 목표 그대로 — 세션 표의 `state_payload` 는 JSON 이고 필드 어휘에 걸린 CHECK 가 없다) |
| D-6A2b-3 한 트랜잭션 | 어댑터 층의 트랜잭션 경계가 요청마다 use case 를 조립. 장애 주입(DB 트리거로 outbox INSERT 거부) 실측: 전략 revision·세션 버전 불변, outbox 0행 | — |
| D-6A2b-4 외부 effect 0 | 발송기·소비자 배선 없음. 이벤트는 outbox 행으로만 남는다(E2E 가 그 행을 DB 로 확인) | — |
| D-6A2b-5 필터 무편집 | 두 파일 diff 0. 새 경로가 기존 필터 체인에 덮이는 것을 기계 전수 test 가 잰다. 행위자는 실행기의 상수 | 상수 두 자리(필터의 audit 주체 라벨 · 실행기의 행위자)는 **문면이 아니라 실측**으로 묶었다 — E2E 가 audit 주체 == outbox 봉투 actor 를 대조한다 |
| D-6A2b-6 결과 → 상태 코드 | 전수 `when`(`else` 없음) 셋: 거부 사유 → 코드, 상태 → 토큰, 결과 → 응답. 사유 일곱 전부 409 | 값 불변식 위반의 **사유**는 응답에 없다(아래 OPEN 신설) |
| D-6A2b-7 405·415·400 | 등록된 매핑 **기계 수집** × 미선언 메서드 전수 → 500 이 0. 수집 집합 == 매핑 집합(집합 등식) | 관리 포트도 **함께 닫혔다**(아래 「수확 ②」) |
| D-6A2b-8 의존 허용 목록 | 포트 집합을 use case 생성자에서 도출(집합 등식) · 주변/능력 분류는 구조 술어 · 어댑터는 `Throwable` 만 통과 · `java.sql` 차단. 위반 표본 셋으로 음성 대조 | Kotlin `internal` 최상위 함수는 JVM 에서 public 이다(아래 「알려진 제한 ③」) |
| D-6A2b-9 스캔 필터 복원 | Boot 기본 `excludeFilters` 둘 복원 + 애너테이션 집합 등식. 빈 집합은 출하 조립 부팅 test 가 잰다 | 「영향 0」은 **거짓이었다**(아래 「수확 ①」) |
| D-6A2b-10 자격증명 재측정 | 참조자 집합 == {필터, 조립 지점}(ArchUnit 집합 등식). 새 쓰기 표면이 그 집합을 넓히지 않았다 | 완전 폐쇄는 6E(원문은 어딘가에 `String` 으로 존재해야 한다) |
| D-6A2b-11 접근 시점 fold | 조회·`begin`·command 가 만료를 먼저 접는다. 스케줄러 없음 | — |
| D-6A2b-12 OpenAPI | 경로 여섯·스키마 다섯·오류 코드 열둘. 문서 `field` enum ↔ 구현 어휘 **양방향 등식** | 405 는 어느 operation 에도 적지 않는다(operation 의 응답이 아니다 — 문서에 사유를 남겼다) |
| D-6A2b-13 컨테이너 스모크 | `begin → value → confirm → GET 반영` + 무인증 401 + 미선언 메서드 405. 로컬 실측 통과, 정본은 CI | — |

## 우회 아홉 — 무엇이 막는가

| 우회 | 막는 것 | 실측 |
|---|---|---|
| ① 컨트롤러가 전략 저장소를 직접 호출 | `AppliedStrategy` 생성 불가(컴파일) + 의존 게이트 | 변이 M4 가 RED(의존·호출 쌍 둘 다) |
| ② 같은 `commandId` 다른 본문 재전달 | `IdempotencyConflict` 409 | HTTP 층 test |
| ③ stale revision 으로 confirm | `StaleRevision` 409, 이벤트 0 | HTTP 층 test |
| ④ 세션 id 를 골라 남의 세션을 겨냥 | **요청 본문이 id 를 나르지 않는다**(서버 생성) | 본문에 id 를 실어도 무시되고 두 요청이 서로 다른 세션을 연다 |
| ⑤ outbox 등록 실패인데 전략만 바뀐다 | 한 트랜잭션 | DB 트리거 장애 주입 — 전략·세션 불변, outbox 0행. 변이 M2 가 RED |
| ⑥ 깨진 JSON·잘못된 메서드·미디어 타입으로 500 | 기계 수집 게이트 | 변이 M1 이 RED |
| ⑦ 값으로 불변식을 깬다 | `validate()` 하나가 판정, **영속 전에** 400 | 음수 상한·역전 임계 둘 다 400 이고 세션 버전 0 그대로 |
| ⑧ HTTP 로 `System` 행위자 | 요청에서 행위자를 받지 않는다(상수) | 변이 M7 이 RED 6건 |
| ⑨ 두 요청이 같은 세션을 동시에 전진 | 낙관적 동시성 → 409, 트랜잭션 롤백 | 예외 → 상태 코드 매핑은 있다. **동시 실행 실측은 없다**(아래 「알려진 제한 ②」) |

## 이 slice 의 수확 — 계약이 예측하지 못한 자리 둘

① **D-6A2b-9 의 「영향 0」은 더는 참이 아니다.** 되살린 `TypeExcludeFilter` 가 `@TestConfiguration` 을
컴포넌트 스캔에서 걷어내자, 그 스캔에 기대 기록형 종료·fake ML 빈을 얻던 E2E 둘이 production 종료 경로를
타 test JVM 이 **조용히** 죽었다. `gateExecutionGate`(게이트 test 의 **실행**을 확인하는 장치)가
「실행되지 않았다」로 잡았다 — 통과 수가 아니라 실행 자체를 보는 게이트가 없었으면 초록으로 보였을
형태다. 필터를 되돌리지 않았다: **test 전용 빈이 출하 조립의 스캔에 섞이는 것이 바로 그 필터가 막는
것**이고, 그 누출이 실재했다는 사실이 이 OPEN 을 닫아야 할 이유다. 두 test 는 그 빈을 명시 source 로
준다(profile 잠금은 그대로).

② **관리 포트의 비 GET 도 함께 닫혔다.** 6A-2a 가 500 으로 관측하고 조건부로 남긴 자리다. API 포트를
닫으려고 더한 메서드 불일치 핸들러는 `@RestControllerAdvice` 라 **관리 child context 에도 함께 선다** —
새 endpoint·새 빈 없이 405 가 됐다(관리 표면 잠금 D-6A2a-10·14 를 넓히지 않았다). 그 포트의 test 를
500 기대에서 405 기대로 고쳤고 「본문에 우리 쪽 정보가 없다」 단언은 그대로 남겼다.

세 번째로, 새 의존 게이트가 **기존** 코드를 잡았다: dry-run 응답 조립이 어댑터 포트 구현을 컨트롤러에
내주고 있었다. 값(공고 ID 목록)만 내도록 좁혔다 — 변이가 아니라 게이트를 처음 돌린 결과다.

## 알려진 제한

① **`SessionAlreadyActive` 는 HTTP 로 도달할 수 없다.** 세션 id 를 서버가 만들므로(D-6A2b-1) 같은 id 에
두 번째 `begin` 이 올 길이 없고, 따라서 **동시에 열린 세션이 여럿일 수 있다.** 단일 운영자에서 그 결과는
`seenRevision` 대조가 잡는다(두 번째 confirm 이 `StaleRevision`) — 값이 조용히 덮이지 않는다. 409 매핑은
전수 `when` 의 총(total)성 때문에 남아 있고 그 갈래는 test 로 재지 못한다(세션 id 생성기를 test 가 주는
생성자 매개변수는 있으나 출하 조립은 기본값(UUID)을 쓴다).

② **동시 전진(우회 ⑨)의 거동은 실측하지 않았다.** 낙관적 동시성 예외 → 409 매핑과 롤백 경로는 있지만,
두 요청을 실제로 겹쳐 돌린 test 가 없다. 6B-1 이 저장소 층에서 그 예외를 이미 실측했고 이 slice 는 그 위에
상태 코드만 얹었다 — HTTP 층의 경합 실측은 남는다.

③ **Kotlin `internal` 은 JVM 경계가 아니다.** 필드 토큰 표와 초안 패치는 `internal` 이지만 바이트코드에서
`public static` 이다(`javap` 실측). 새 권한은 아니다 — 둘 다 자유롭게 만들 수 있는 값만 변환하고 저장·발행에
닿지 않는다. 의존 게이트가 막는 것은 그 함수가 아니라 **포트·어댑터·SQL** 이다.

④ **값 불변식 위반의 사유가 응답에 없다.** `ErrorBody` 는 세 필드 고정이고 이 slice 는 그 계약을 넓히지
않았다 — 400 은 `STRATEGY_VALUE_INVALID` 하나다. 어느 불변식이 깨졌는지는 운영자가 값으로 되짚어야 한다.

⑤ **`EditStrategyWorkflow` 의 「값 무효 → 같은 필드로 accepted」 갈래는 production 에서 죽은 분기다.**
실행기가 같은 `validate()` 로 **먼저** 거부해 command 를 만들지 않기 때문이다(그래야 400 으로 옮길 자리가
생긴다). 심층 방어로 남기고, use case 자신의 test 가 그 갈래를 계속 잰다.

⑥ **컨테이너 축의 정본은 CI 다.** 로컬에서 전 단계를 실측했지만(이미지 둘·위생 둘·거부 스모크·compose
healthy·쓰기 스모크·실서버 통합·정리), 러너 환경이 다르면 결과도 다를 수 있다.

## 하네스 레인 변경 (이 slice 가 만진 게이트·CI)

- `config/quality/architecture-policy.properties` — 포트 호출 쌍 하나가 컨트롤러에서 조회기로 옮겨졌고,
  판정 대상 포트 둘(편집 세션 저장·이벤트 sink)이 늘었다. 새 키 넷(의존 게이트)·둘(자격증명 참조자).
- `.github/workflows/ci.yml` — `container` job 스모크에 쓰기 왕복 절과 405 단언을 더했다. 다른 job 무편집.
- `adapters/src/test/.../StrategyAdapterDependencyTest.kt` — 허용 루트 둘(이벤트 축)을 넓혔다. 사유는
  「조립을 `app` 이 아니라 어댑터 층에 두기 위해서」이고, 그 대안(앱의 outbox 참조 금지 게이트를 넓히는 것)
  보다 좁다.
- `ConstantTimeComparisonStructureTest` 허용 목록에 셋(근거 문장 포함, `javap` 로 자격증명 좌표 0 확인).
- `ManagementHealthSurfaceTest` 의 비 GET 기대가 500 → 405(위 「수확 ②」).
- 기계 전수 test 둘(`OperatorAuthenticationTest`·`ProductionAssemblyAuthAuditTest`)이 경로 변수를 구체
  값으로 치환한다 — 경로 변수를 가진 매핑이 이 slice 에서 처음 생겼다.

## OPEN 처분

| OPEN | 처분 |
|---|---|
| `OPEN-6F9-STRATEGY-WRITE-ENDPOINT` | **닫는다** — 관심 업종을 HTTP 로 바꾸고 조회로 확인하는 test |
| `OPEN-6A3-MAX-ACTIVE-BIDS-EDIT` | **닫는다** — 필드 어휘·codec 왕복·HTTP 토큰·어휘 게이트 |
| `OPEN-6A3-APP-HTTP-DEPENDENCY-ALLOWLIST` | **닫는다** — 도출 포트 집합 × 구조 술어 분류 × 위반 표본 셋 |
| `OPEN-API-WRONG-METHOD-500` | **닫는다** — API 포트는 기계 전수로, 관리 포트는 같은 조언이 함께 서서 |
| `OPEN-6A1-SCAN-FILTER-SIDE-EFFECT` | **닫는다** — 필터 복원 + 집합 등식. 실제 누출이 있었음을 실측(위 「수확 ①」) |
| `OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION` | 수령·재측정·**유지**(→ 6E) — 참조자 집합 불변 |
| `OPEN-6A2B-VIOLATION-DETAIL`(신설) | 값 불변식 위반의 사유를 응답에 싣지 않는다(알려진 제한 ④) |
| `OPEN-6A2B-CONCURRENT-SESSION-ADVANCE`(신설) | HTTP 층의 동시 전진 실측 부재(알려진 제한 ②) |

## 비활성화 경로

편집 쓰기 경로 전체는 조립 하나에 달려 있다 — 편집 실행기 빈을 빼면 컨트롤러가 의존을 찾지 못해 기동이
실패하므로, 끄려면 **컨트롤러와 실행기 빈을 함께** 뺀다(그 뒤 읽기·dry-run 은 그대로 뜬다). 되돌림 전체는
`rollback.md`.
