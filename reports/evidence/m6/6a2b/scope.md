# M6/6A-2b — 세션 편집 endpoint: 운영자가 HTTP 로 전략을 바꾸고, 모든 쓰기는 편집 세션을 지난다 (2026-09-26)

6A-1 이 D-6A1-1 로 6A-2 에 넘긴 자리의 뒤 절반이다(앞 절반 6A-2a 앱 이미지·health 는 PR #47 로 닫혔다). 전략을 **읽는** 길(`GET /api/strategy`)은
있지만 **쓰는** 길이 없다. `EditStrategyWorkflow` 의 호출자는 test 뿐이다. 6F-9 실수집 때 관심 업종을 dev DB 에 직접 넣었다 되돌린 것이 이 공백의 실측이다.

- base: `git merge-base HEAD origin/main`(고정 SHA 아님). 착수 실측 `6f0b21f0`(PR #47 6A-2a 머지).
- worktree `bid-vector-v2-m6-6a2b`, 브랜치 `m6-6a2b/2026-09-26`.

## 운영자 지시·결정 (2026-09-26)

- 사용자가 다음 slice 로 **6A-2b 를 Codex 없이** 진행하기로 골랐다. 인증 필터·자격증명 파일은 **편집하지 않는다**(D-6A2b-5). 새 endpoint 는 기존 인증 체인
  **아래에** 선다. `verifier` + `code-reviewer`(sonnet) + `privacy-gate`(범용 대행) + `contract-keeper`(OpenAPI, 범용 대행)로 닫는다. 마이그레이션이 생기면
  (D-6A2b-2 가 피하려 한다) 계약을 멈추고 `migration-reviewer` 를 더한다.
- 같은 날 내린 다른 결정 셋(보존 90일 · 큐 초과 시 오래된 것 폐기 · 실 LLM·ML gRPC 승인과 비교 패턴)은 **이 slice 의 범위가 아니다.** `milestone-6.md`
  착수 문단에 등재만 한다.

## 착수 실측 (`6f0b21f0`)

| 자산 | 상태 |
|---|---|
| use case | `EditStrategyWorkflow`(`begin`·`provideValue`·`confirm`·`requestEdit`·`cancel`·`expire`). 커널(`beginSession`·`apply`·`expireIfDue`)은 `internal` 이다. 저장 인자 `AppliedStrategy` 는 `internal constructor` 다 — `workflow` 밖에서는 이 use case 를 거치지 않고 전략을 저장할 수 없다 |
| 포트 구현 | `JdbcStrategyRepository`(6F-1) · `JdbcEditSessionRepository`(6B-1, 낙관적 동시성 — 0행이면 `EditSessionConflictException`) · `SystemClock` · `OutboxEventSink`(workflow) + `JdbcOutboxPort`(6F-7). **production 조립은 0** 이다 |
| 원자성 | 4A 가 알려진 제한으로 남긴 창: `strategies.save` → `events.publish` → `sessions.save` 가 따로 커밋된다. `JdbcOutboxPort` 는 `TransactionBoundary` 안에서만 같은 트랜잭션에 든다 |
| 필드 어휘 | `EditableField` = `Watch(WatchRuleId 일곱)` · `Threshold(ThresholdField)` · `CandidateLimit`. **`maxActiveBids` 자리가 없다**(`OPEN-6A3-MAX-ACTIVE-BIDS-EDIT`). 관심 업종은 `Watch(FocusCategory)` 로 이미 있다 |
| 세션 표 | V8 `edit_session`: 필드는 `state_payload`(JSON) 안에 있다. 필드 어휘에 걸린 CHECK 는 없다 — 새 필드 값이 마이그레이션을 부르지 않을 것으로 본다(구현 레인이 실측) |
| 행위자 | 단일 운영자 토큰(6A-1 결정 ②). 필터는 audit 주체를 상수 `"operator"` 로 적는다. 세션 소유 검사(`ActorMismatch`)는 단일 운영자에서 늘 같은 id 다 |
| HTTP | `StrategyReadController`(GET) · `EvaluationDryRunController`(POST, dry-run) · `ErrorBody` · 필터 둘(audit → 자격). OpenAPI 는 수작성 단일 출처 `openapi/bidvector-operator-api.yaml`, test 가 구현과 대조한다(D-6A1-8) |
| conformance | `app/src/test/.../conformance/StrategyEditExecutors.kt` 가 fake 로 편집 흐름을 돈다 |

## 재사용 조사 (Phase 2)

| 후보 | 판정 | 근거 |
|---|---|---|
| `EditStrategyWorkflow` 그대로 | **채택** | 상태 기계·idempotency·stale revision·만료가 M4 에서 이미 판정됐다. HTTP 는 **어댑터**일 뿐이다(D-M4-1 채널 독립). 재구현 금지 |
| `TransactionBoundary` + 요청마다 조립(`EvaluationDryRunFactory` 형태) | **채택** | 6F-7·6A-3 선례. 트랜잭션의 `ConnectionSource` 위에 저장소 셋을 요청마다 세운다 |
| Spring `@Transactional` | 기각 | 저장소가 Spring 트랜잭션 관리자를 쓰지 않는다(`ConnectionSource` 구조). 두 트랜잭션 체계를 섞지 않는다 |
| Spring 기본 예외 처리(`ResponseEntityExceptionHandler`) | **검토 후 채택 가능** | `OPEN-API-WRONG-METHOD-500` 의 405·415·400 을 표준 처리로 얻을 수 있다. 본문은 `ErrorBody` 형태로 맞춘다 — 구현 레인이 실측해 정한다 |
| ArchUnit(이미 app test 의존) | **채택** | `OPEN-6A3-APP-HTTP-DEPENDENCY-ALLOWLIST` 의 허용 목록 게이트 |

## 결정

| ID | 결정 | 근거 |
|---|---|---|
| **D-6A2b-1** | **endpoint 여섯**(전부 `/api/strategy/edit-sessions` 아래): `POST /`(begin, `{field}` → 201) · `GET /{id}`(세션 상태) · `POST /{id}/value`(`{commandId, field, value}`) · `POST /{id}/confirm`(`{commandId, seenRevision}`) · `POST /{id}/edit`(`{commandId, field}`) · `POST /{id}/cancel`(`{commandId}`). **세션 id 는 서버가 만든다**(UUID). **`commandId` 는 클라이언트가 준다**(재전달 판별이 이 값에 걸려 있다). 응답은 세션 상태·`sessionVersion`·`expiresAt`, 적용이면 새 `revision` | use case 의 command 넷 + begin + 조회가 전부다. 새 동작을 만들지 않는다. 세션 id 를 클라이언트가 고르면 남의 세션 id 를 추측·충돌시킬 여지가 생긴다 |
| **D-6A2b-2** | **필드 값 → 전체 draft 조립은 어댑터(HTTP 쪽)가 한다.** 현재 전략을 draft 로 내보낸 뒤 해당 필드 하나만 바꾼다(`EditCommand.ProvideValue` 문서가 이미 이 분담을 적었다). `EditableField` 에 **`MaxActiveBids`** 를 더한다 → `OPEN-6A3-MAX-ACTIVE-BIDS-EDIT` 닫음. `Watch(FocusCategory)` 경로가 HTTP 로 열리면 `OPEN-6F9-STRATEGY-WRITE-ENDPOINT` 닫음. 세션 스냅숏 codec 은 새 필드를 왕복해야 한다. **마이그레이션은 만들지 않는 것을 목표로 한다** — 필요해지면 멈추고 계약을 갱신한다 | 값 검증은 `validate()` 하나가 한다(편집 경로마다 불변식을 재구현하지 않는다, D-10). 예산 값(`BaseAmount`: 통화·VAT·출처)을 HTTP 에서 만드는 자리는 출처가 **운영자 입력**이어야 한다 — 구조상 막히면 그 필드만 빼고 OPEN 으로 등재한다 |
| **D-6A2b-3** | **적용 경로는 한 트랜잭션이다.** 요청마다 `TransactionBoundary.inTransaction` 을 열고, 그 `ConnectionSource` 위에 전략·세션 저장소와 outbox sink 를 세워 use case 를 조립한다. 전략 저장·이벤트 outbox 등록·세션 전진이 **함께 커밋되거나 함께 롤백된다.** 4A 의 잔여 창(발행 실패 뒤 세션 미전진)이 여기서 닫힌다 — 장애 주입 test 로 잰다(outbox 등록 실패 → 전략 revision 불변·세션 불변) | 트랜잭션 outbox 는 4C 가 설계한 자리이고, 6F-7 이 `JdbcOutboxPort` 를 냈다. 따로 커밋하면 「전략은 바뀌었는데 이벤트가 없다」가 생긴다 |
| **D-6A2b-4** | **외부 effect 0.** `StrategyUpdated` 는 outbox 에 **등록만** 된다. 발송기·소비자를 배선하지 않는다 | 실 알림 발송은 사용자 승인 대상이다(`OPEN-STR-12`) |
| **D-6A2b-5** | **인증·audit 필터 두 파일은 diff 0.** 새 경로는 `/api/**` 라 기존 필터가 그대로 덮는다. 행위자는 `Actor.Operator(OperatorId("operator"))` 상수(필터의 audit 주체와 같은 값)다. HTTP 로 `Actor.System` 을 만들 길은 없다. 요청 본문은 audit 에 싣지 않는다(기존 규칙) | 6A-1 이 인증을 판정했다. 경로 예외·새 필터를 두지 않는다. 필터를 건드리면 되돌리기 어려운 경로로 올라가 Codex 대상이 된다 |
| **D-6A2b-6** | **결과 → 상태 코드는 닫힌 표다.** `Accepted`·`Applied` 200 · begin `Started` 201 · `SessionNotFound` 404 · `Rejected`(사유 일곱)와 `EditSessionConflictException` 409 · 값 검증 실패(`StrategyViolation`)·형식 오류 400. 본문은 `ErrorBody` 에 **구조화 코드**(사유 이름)를 싣고 예외 메시지·스택·SQL 을 싣지 않는다. 사유 → 코드 매핑은 전수 `when`(else 없음)이다 | 새 사유가 생기면 컴파일이 매핑 누락을 잡는다(열거가 아니라 구성) |
| **D-6A2b-7** | **`OPEN-API-WRONG-METHOD-500` 을 닫는다.** API 포트에서 매핑된 경로의 지원하지 않는 메서드 → 405, 지원하지 않는 미디어 타입 → 415, 깨진 JSON·필드 누락 → 400. 전부 `ErrorBody`, 500 0. **게이트는 기계 수집**이다: 등록된 핸들러 매핑 **전부** × 선언되지 않은 메서드 집합을 돌려 500 이 없음을 단언한다(경로 손 목록 금지, 수집 집합 == 매핑 집합). 관리 포트 비 GET 500(6A-2a 관측)도 같은 처분을 시도하되, 관리 표면 잠금(D-6A2a-10·14)을 **넓히지 않는** 방법이 없으면 알려진 제한으로 남긴다 | 이 slice 가 본문을 받는 첫 쓰기 endpoint 들을 연다 — 형식 오류가 500 이면 스택 노출의 문이 된다 |
| **D-6A2b-8** | **`OPEN-6A3-APP-HTTP-DEPENDENCY-ALLOWLIST` 를 닫는다.** ArchUnit 규칙: `app.http` 의 의존 집합 ⊆ {workflow use case·결과·command 타입, 도메인 값 타입, `app.http` DTO·예외, Spring web, Kotlin·Java 표준}. **포트 인터페이스(`StrategyRepository`·`EditSessionRepository`·`OutboxPort` 등)와 `adapters.**` 는 허용 목록 밖**이다 — 컨트롤러는 조립된 실행기만 받는다. 포트 집합은 손 목록이 아니라 **use case 생성자 매개변수 타입에서 도출**한다(집합 등식). 기존 `StrategyReadController` 가 `StrategyRepository` 를 직접 받는 것은 이 규칙에 걸린다 — 읽기 전용 조회기로 감싸 옮긴다 | 6A-3 r3 MEDIUM 의 지름길 셋(어댑터 추가 메서드 · 생성자에서 읽는 어댑터 · `app.http` raw SQL)을 **의존 층**에서 막는다. 구조 층 게이트(D-6A2a 규율: 문자열 술어 금지) |
| **D-6A2b-9** | **`OPEN-6A1-SCAN-FILTER-SIDE-EFFECT` 를 닫는다.** 명시 `@ComponentScan` 에 Boot 기본 `excludeFilters` 둘(`TypeExcludeFilter`·`AutoConfigurationExcludeFilter`)을 되살린다. 6A-2a 가 영향 0 을 실측했다 — 되살린 뒤 빈 집합이 같음을 test 로 잰다 | 새 컨트롤러·조립기가 스캔에 들어오는 slice 라 지금 닫는 것이 싸다 |
| **D-6A2b-10** | **`OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION` 은 수령·재측정 후 유지한다.** 새 표면이 우회 비용을 낮추지 않음을 잰다: `OperatorCredential`·`OperatorCredentialProperties` 를 참조하는 클래스 집합 == {필터, 조립 지점}(ArchUnit 집합 등식, 이미 있으면 재사용). 완전 폐쇄(환경변수 원문 경계를 프레임워크 밖으로)는 범위 밖 — 6E 로 넘긴다 | D-6A1-45 의 뿌리는 구조적이다(원문은 어딘가에 `String` 으로 존재해야 한다) |
| **D-6A2b-11** | **만료는 접근 시점 fold 만.** 주기 sweep 스케줄러를 두지 않는다(`begin`·command 처리가 이미 만료를 먼저 접는다). 세션 TTL 은 기존 `EditSessionPolicyData` 값 | 스케줄러는 새 운영 축이다. 단일 운영자에서 무기한 막힘은 `begin` 의 fold 가 이미 막는다(verifier M-5, M4) |
| **D-6A2b-12** | OpenAPI 수작성 단일 출처에 여섯 경로·요청·응답·오류 코드를 더하고, 기존 대조 test 가 새 경로까지 잰다 | D-6A1-8 |
| **D-6A2b-13** | CI `container` 스모크에 **쓰기 왕복 하나**를 더한다: begin(`CandidateLimit`) → value → confirm → `GET /api/strategy` 에서 새 값·revision+1. 무인증 쓰기 401 도 잰다 | 「뜬 이미지에서 쓰기가 인증 경계 아래에서 끝까지 간다」를 재야 한다. 6A-2a 스모크의 연장이다 |

## 위협 모델 — 6A-2b 고유 경계 (Phase 2.5 (0), 팀장)

**지키는 것**: ① 인증된 운영자만 전략을 바꾼다(필터 무편집, 새 경로 전부 `/api/**`) ② **모든 전략 쓰기가 `EditStrategyWorkflow` 를 지난다** — HTTP 층에서 저장소·
outbox·SQL 로 가는 지름길이 없다 ③ 불변식을 어긴 값은 영속되지 않는다(`validate()` 하나) ④ 전략·이벤트·세션이 함께 커밋되거나 함께 롤백된다 ⑤ 외부 effect 0
⑥ 오류 응답이 내부 정보(예외 메시지·스택·SQL·자격)를 내지 않는다 ⑦ 요청 본문이 audit·로그에 실리지 않는다.

**지키지 않는 것(경계 밖)**: 다중 운영자·RBAC(단일 토큰 결정) · 토큰 탈취자(토큰을 가진 자는 운영자다) · CSRF(API 전용, 헤더 토큰, 쿠키 없음) · 요청률 제한 ·
토큰 교체 · 빌드 스크립트를 임의로 고치는 저자(6C·1A 경계) · 저장 데이터 보존·파기(6B-3, 운영자 결정 90일 — 이 slice 가 만드는 `edit_session`·`api_audit` 행도 그
대상이다).

**요구 축소가 아닌 근거**: milestone-6 6A 요구는 「M0 승인 endpoint · OpenAPI 단일 출처 · 명시적 error body · operator scope/audit · controller 는 workflow use case
만 호출」이다. 다섯 모두 이 slice 가 받는다 — 마지막 줄이 D-6A2b-8 이다. RBAC 가 단일 토큰인 것은 6A-1 운영자 결정 ②다.

### (1) 열거인가 구성인가

- 쓰기 경로 유일성: **구성**. `AppliedStrategy` 가 `internal constructor` 라 `workflow` 밖에서 저장 인자를 만들 수 없다(컴파일). D-6A2b-8 의 의존 규칙은 포트 집합을
  use case 생성자에서 **도출**한다.
- 거부 사유 → 상태 코드: **구성**(전수 `when`, else 없음).
- 잘못된 메서드·형식 오류 500 부재: 핸들러 매핑 **기계 수집** 대 단언(집합 등식). 경로를 손으로 적지 않는다.

### (2) 우회 — 여덟

1. 컨트롤러(또는 새 조립기)가 `StrategyRepository.save` 를 직접 부른다. ← `AppliedStrategy` 생성 불가(컴파일) + `app.http` 가 포트를 참조하면 ArchUnit RED.
2. 같은 `commandId` 에 다른 본문으로 재전달해 값을 바꾼다. ← `IdempotencyConflict` 409(M4 판정 그대로, HTTP 로 재측정).
3. 오래된 revision 을 본 채 confirm 해 남의 변경을 덮는다. ← `StaleRevision` 409.
4. 세션 id 를 골라 남의(또는 진행 중) 세션을 덮는다. ← 서버 생성 id · `begin` 은 활성 세션이 있으면 `SessionAlreadyActive`.
5. outbox 등록이 실패했는데 전략만 바뀐다(부분 커밋). ← 한 트랜잭션(D-6A2b-3) + 장애 주입 test.
6. 깨진 JSON·잘못된 메서드·잘못된 미디어 타입으로 500 과 스택을 받는다. ← D-6A2b-7 기계 수집 게이트.
7. 값으로 불변식을 깬다(음수 상한 · 역전된 임계 · 빈 업종 코드 · 통화 없는 예산). ← `validate()` 400, 영속 0 을 DB 로 확인.
8. HTTP 로 `System` 행위자를 만든다. ← 행위자는 상수 `Operator` — 요청에서 행위자를 받지 않는다.
9. (보조) 두 요청이 같은 세션을 동시에 전진시킨다. ← 낙관적 동시성 `EditSessionConflictException` → 409, 트랜잭션 롤백.

### (2b) 값 획득 축 — 새 public 표면 전수 (구현 뒤 `javap` 로 재전수)

| 표면 | 밖에 허락하는 것 | 판정 |
|---|---|---|
| `EditableField.MaxActiveBids` | 필드 이름을 부를 수 있다. 값은 여전히 `validate()` 를 지난다 | 닫는다(새 권한 없음) |
| 전략 → draft 내보내기 함수(필요하면 `strategy` 모듈) | 현재 전략과 같은 draft 를 얻는다. `StrategyDraft` 는 원래 자유롭게 만들 수 있다 | 닫는다(동치) |
| 요청마다 트랜잭션 안에서 use case 를 조립하는 실행기(`app.wiring`) | **이것이 포트를 쥔다.** 공개 메서드는 command 를 받아 결과를 돌려주는 것뿐이어야 하고, 저장소·`ConnectionSource`·`TransactionBoundary` 를 밖에 내지 않는다 | **경계로 처리** — 실측 대상: 실행기에서 포트를 꺼낼 공개 경로가 0 임을 `javap` 로 잰다 |
| 새 컨트롤러·DTO | HTTP 요청을 command 로 바꾼다. 포트 참조 없음(D-6A2b-8) | 닫는다 |
| 읽기 조회기(기존 `StrategyReadController` 이전) | 현재 전략을 읽는다(원래 GET 이 하던 일) | 닫는다(동치) |
| `TransactionBoundary`(app 컨텍스트 빈, D-6A2b-19 ④ — r1 추가) | 이 빈을 주입받은 app 코드는 트랜잭션 안에서 임의 SQL 을 실행할 수 있다(`jdbcClient`·`jdbcTemplate` 빈과 같은 권한 — 새 권한 아님) | **경계로 처리** — HTTP 로 닿지 않는 app 내부 코드는 빌드 저자 경계 밖. 닿는 길(컨트롤러·`app.http` 의 참조)은 허용 목록 ⊆ 가 막는다. 실측: 변이 MU1·MU2(컨트롤러 참조)·MU2b RED |
| `StrategyEditTransaction`(app 컨텍스트 빈 — r1 추가) | 편집 use case 를 트랜잭션 안에서 실행한다. 모든 쓰기는 여전히 `EditStrategyWorkflow` 를 지난다 | **경계로 처리** — 허용 목록에 정확한 클래스로 올린 실행기만 컨트롤러가 받는다. 실측: 같은 변이 셋 |
| JVM 공개 최상위 함수 둘(`TransitionGuardsKt`·`JsonValueReadersKt`, Kotlin `internal` — r1 크기 분할로 생김) | 판정 순서 술어는 이미 공개 생성자를 가진 결과 타입만 내고 `EditSession`(internal constructor)을 인자로 요구한다. JSON 읽기는 `JsonNode` 만 읽는다 | 닫는다(새 권한 없음) — 알려진 제한 ⑦ |
| `EditCommand.ProvideValue.baseRevision`(r2, D-6A2b-28) | `EditCommand` 는 원래 public 이라 밖에서 기준 값을 실어 만들 수 있다. 지어낸 값은 value 시점 대조가 `StaleRevision` 으로 막고, 남는 것은 현재와 같은 값을 싣는 것뿐(정상 사용과 구별 불가) | 닫는다(새 권한 없음) — 알려진 제한 ⑥ |
| 406 핸들러 `ResponseEntity<Void>`(r2, D-6A2b-29) | 본문 없는 406. 정보 노출 없음 | 닫는다 |
| `EditCommand.ProvideValue.baseRevision` 이 **nullable**(r3) | `null` 로 만든 command 가 value 시점 기준 대조를 **건너뛰는가**가 물음이다. HTTP 실행기는 늘 값을 싣는다. 다른 채널(또는 test·conformance)이 `null` 을 실으면 confirm 시점 `baseRevision` 대조(D-6A2b-18)가 여전히 막는지 | **경계로 처리 — verifier r4 표적**: `null` 기준 command 로 교차 세션 되돌림이 재현되는가 |
| `StrategyReader`(workflow, r4 — `load` 하나, `StrategyRepository` 가 확장) | 현재 전략을 읽는다. 구현해도 쓰기 경로는 생기지 않는다(`save` 는 `StrategyRepository` 에 남고 인자 `AppliedStrategy` 는 `internal constructor`). **r5 정정(N-r5-5)**: 이 타입은 능력 포트가 아니라 제한 층 허용 접두 안이었다 — 도입 직후 컨트롤러가 조회기를 건너뛰고 전략을 직접 읽는 길이 열려 있었다 | 닫는다 — `app.http.denied-types` 가 제한 층의 **직접 참조**를 판다(조회기 경유만). 포트 호출 게이트의 쌍 오른쪽도 이 타입으로 옮겼다(N-r5-4) |
| 읽기 포트 → 쓰기 포트 **다운캐스트**(r4 자체 탐침 P2) | `checkcast` 는 ArchUnit 직접 의존에 잡히지 않는다(OQ-1 과 같은 성질). 쓰기를 시도하면 인자 타입이 ② 층 목록 밖이라 RED 이고 `AppliedStrategy` 를 만들 길이 없다 | **경계로 처리** — 막는 것은 능력 축이 아니라 타입 목록 + `internal constructor`. 알려진 제한 ⑰ · verifier r5 표적 |

### (3) 과잉·미달

- 과잉 아님: 조회 `GET /{id}` 는 클라이언트가 세션을 이어 가려면 필요하다(대화형 편집). 쓰기 스모크는 이미지가 쓰기 경로를 실제로 싣는지 잴 유일한 자리다.
- 미달 경계: 여러 필드를 한 번에 바꾸는 일괄 편집은 없다(use case 가 필드 하나씩이다). 편집 이력 조회 endpoint 도 없다 — 필요하면 별 slice.

## in_scope

착수 계약에 게이트·fixture·build 파일을 처음부터 넣는다(6A-3 교훈).

- `app/src/main/kotlin/bidvector/app/**` · `app/src/main/resources/**` · `app/src/test/**` · `app/build.gradle.kts`
- `workflow/src/main/kotlin/bidvector/workflow/strategy/**` · `workflow/src/test/**`
- `strategy/src/main/kotlin/bidvector/strategy/**` · `strategy/src/test/**`(draft 내보내기가 필요할 때만)
- `adapters/src/main/kotlin/bidvector/adapters/strategy/**` · `adapters/src/main/kotlin/bidvector/adapters/persistence/**` · `adapters/src/test/**`
- `openapi/bidvector-operator-api.yaml`
- `config/quality/**`(게이트 baseline·허용 목록이 바뀔 때)
- `.github/workflows/ci.yml`(D-6A2b-13 스모크)
- `reports/evidence/m6/6a2b/**`
- `milestone-6.md`(착수·종결 문단만)

**out_scope**: 인증·audit 필터 두 파일 · `adapters/src/main/resources/db/migration/**`(생기면 계약 갱신) · outbox 발송기 · ML·LLM 배선 · 6B-3·6B-4 · `docker/**` ·
`ml-engine/**` · `contracts/**`.

## acceptance

CI job 명령 그대로다(부분 게이트 금지).

- `check` job: `./gradlew --no-daemon check` · `./gradlew --no-daemon qualityBaseline` · `./tools/one-command-check.sh`
- `container` job: `ci.yml` 의 해당 job 단계 전부(`bootJar` → 두 이미지 빌드 → 위생 → compose healthy → 스모크(쓰기 왕복 포함) → 실서버 통합 test → 정리)
- `ml-engine` job 은 이 slice 가 Python 을 건드리지 않으므로 CI 초록으로 갈음한다.

## rollback

in_scope 경로 한정 `git restore --source=<base> --staged --worktree -- <경로>`. 목록은 `git diff --name-status <base>..HEAD` 기계 산출, 라운드마다 재산출.
`milestone-6.md` 는 공유 파일이라 커밋 해시 hunk 격리. 실측은 임시 clone 에서 ①~⑥(evidence-pack 규격), `실측 HEAD` 를 `rollback.md` 에 적는다.
비활성화 경로: 새 endpoint 는 조립이 없으면 뜨지 않는다 — 실행기 빈 등록을 빼면 쓰기 경로 전체가 사라지고 읽기·dry-run 은 남는다.

## 계약 갱신 r0 (2026-09-27, 팀장 — 구현 레인 보고 수령, 검증 전)

| ID | 결정 |
|---|---|
| **D-6A2b-14** | **D-6A2b-9 의 전제 「영향 0」(6A-2a 실측)은 거짓이었다.** 되살린 `TypeExcludeFilter` 가 `@TestConfiguration` 을 스캔에서 걷어내자 E2E 둘이 test 전용 빈(기록형 종료·fake ML)을 잃었다. 그러자 production 종료 경로가 test JVM 을 조용히 끝냈고, `gateExecutionGate`(실행 여부를 보는 게이트)가 잡았다. **처분 유지**: 필터를 되돌리지 않는다 — 그 누출이 필터의 존재 이유다. 두 test 가 빈을 명시 source 로 받게 고친다. 6A-2a 의 「영향 0」 실측이 왜 이것을 못 봤는지는 verifier 표적으로 준다 |
| **D-6A2b-15** | 관리 포트 비 GET 은 **405 로 함께 닫혔다**(D-6A2b-7 의 조건부 절 충족). API 포트용 `@RestControllerAdvice` 가 관리 자식 컨텍스트에도 선다. 관리 표면 잠금(D-6A2a-10·14)은 넓히지 않았다고 구현 레인이 보고했다 — **verifier 표적**: 그 advice 가 관리 포트에서 405 말고 다른 표면(본문·경로)을 여는지 |
| **D-6A2b-16** | 의존 게이트(D-6A2b-8)가 **기존 코드**를 잡았다: dry-run 응답 조립이 어댑터 포트 구현을 컨트롤러에 내주고 있었다. 값만 내도록 좁혔다(in_scope `app/**`) |
| **D-6A2b-17** | 신설 OPEN 둘을 수용한다: `OPEN-6A2B-VIOLATION-DETAIL`(값 위반 사유가 응답에 없다) · `OPEN-6A2B-CONCURRENT-SESSION-ADVANCE`(HTTP 층 동시 전진 실측 없음). `SessionAlreadyActive` 가 HTTP 로 도달 불가인 것(서버 생성 id 의 귀결)은 알려진 제한이다 — 동시에 열린 세션 여럿은 `StaleRevision` 이 잡는다 |

## 계약 갱신 r1 (2026-09-27, 팀장 — verifier r1 not-ready F-1·F-2 · code-reviewer r1 HIGH-1·MEDIUM 다섯 · contract C-1~5 · privacy INFO-P1 수령)

재작업 **1/5**. 차단 결함은 둘이다. 둘 다 계약 문면(D-6A2b-8 「허용 목록 ⊆」, 알려진 제한 ① 「조용히 덮이지 않는다」)과 산출물이 어긋난 자리다.

| ID | 결정 |
|---|---|
| **D-6A2b-18** | **(F-2·HIGH-1) draft 의 기준 revision 을 세션에 담는다.** `WaitingForConfirmation` 에 `baseRevision`(draft 를 만든 시점의 전략 revision)을 더하고, `onConfirm` 은 `state.baseRevision != current.revision` 이면 `StaleRevision` 으로 거부한다(`seenRevision` 대조는 유지 — 둘 다 통과해야 적용). `RequestEdit` 뒤 새 value 는 새 기준을 잡는다. 스냅숏 codec 이 왕복하고, **`baseRevision` 이 없는 저장 행은 confirm 에서 fail-closed(`StaleRevision`)** 로 읽는다 — 마이그레이션 0 유지. 응답에 `baseRevision` 을 낸다. 회귀 test 는 교차 세션 시나리오 그대로(두 세션 value → A confirm → 최신 GET 의 revision 으로 B confirm → **409**, A 의 값 보존)를 use case 단위와 **실 DB E2E** 둘 다로. 알려진 제한 ①·D-6A2b-17 의 문면을 고친다. M4 커널(`Transition.kt`) 변경이다 — 기존 conformance fixture 가 이 거동과 충돌하면 멈추고 보고 |
| **D-6A2b-19** | **(F-1) 의존 게이트를 계약 문면대로 다시 세운다.** ① **대상은 패키지 이름이 아니라 구조**: `bidvector.app` 아래 `@Controller`·`@RestController`·`@ControllerAdvice` 가 붙은 모든 클래스 + `app.http` 패키지 전체(합집합). ② **술어는 허용 목록 ⊆**: 의존 집합 ⊆ {workflow use case·결과·command 타입, 도메인 값 타입, `app.http` DTO·예외, 명시된 실행기·조회기 타입(`app.wiring` 의 정확한 클래스 집합, 계약 파일에서 읽음), Spring web, Kotlin·Java 표준(단 `java.sql`·`javax.sql` 제외)}. 금지 열거로 두지 않는다. ③ 모집단은 production 스캔 기준(`bidvector.app`)이다 — `HttpTestApplication`(스캔 `app.http`)을 쓰는 OpenAPI 대조·형식 게이트도 같은 모집단으로 옮긴다. ④ **컨텍스트 빈 경계**: `jdbcClient`·`jdbcTemplate`·`transactionBoundary`·`strategyEditTransaction` 은 app 컨텍스트에서 주입 가능하다. 위협 모델상 **HTTP 로 닿지 않는** app 내부 코드의 SQL 은 경계 밖(빌드 저자 경계와 같은 층)이고, 닿는 길은 ①②가 막는다 — (2b) 표에 두 행(`TransactionBoundary`·`StrategyEditTransaction`)을 「경계로 처리」로 더하고, 실측: verifier 변이 MU1·MU2(컨트롤러가 참조할 때)·MU2b 가 전부 RED. **게이트 술어 변경 → verifier 표적 재검증** |
| **D-6A2b-20** | **(F-3·MEDIUM-3) 형식 게이트 항진식 제거.** 수집 집합은 실제로 탐침한 매핑만 담고, 선언 메서드가 없는 매핑·건너뛴 매핑은 **이유와 함께 별도 집합**으로 단언한다(집합 등식: 탐침 ∪ 명시 제외 == 전체 매핑). 변이 MU4(경로 변수 매핑 제외)가 RED. **(C-5b)** 「OpenAPI 문서 경로·메서드 집합 == production 등록 매핑 집합」 등식을 같은 수집기로 세운다. **(C-5a)** `state` enum 문서 ↔ 구현 등식. 게이트 술어 변경 → 표적 재검증 |
| **D-6A2b-21** | **(MEDIUM-2) `Accept` 협상 실패 → 406 `ErrorBody`.** 매핑표에 더하고 형식 게이트에 `Accept` 축을 더한다. 관리 포트의 actuator 미디어 타입 요청이 여전히 200 인지(잠금 무변경) 함께 잰다 |
| **D-6A2b-22** | **(MEDIUM-4) `toDraft()` 전수성 게이트.** `StrategyDraft` 생성자 매개변수 **전부**에 기본값이 아닌 값을 넣은 전략의 `validate → toDraft` 왕복이 동일함을 단언하고, 매개변수 이름 집합을 리플렉션으로 수집해 test 가 다룬 집합과 등식으로 잠근다(필드가 늘면 RED) |
| **D-6A2b-23** | **(MEDIUM-5) 전략 정책은 조립에서 한 번 해소**해 같은 인스턴스를 실행기·트랜잭션이 공유한다(빈 하나) |
| **D-6A2b-24** | **(MEDIUM-1) 버려진 세션은 이 slice 에서 치우지 않는다.** D-6A2b-11 의 근거 문면(「`begin` 이 만료를 먼저 접는다」)은 서버 생성 id 아래에서 성립하지 않는다 — 문면을 정정하고 **`OPEN-6A2B-ABANDONED-SESSIONS`** 신설(받는 쪽 6B-3 — 운영자 결정 보존 90일의 파기 대상에 `edit_session` 비종단 행을 넣는다). **(C-3)** 도달 불가한 begin 409 `SESSION_ALREADY_ACTIVE` 를 OpenAPI 에서 뺀다 |
| **D-6A2b-25** | 문서·자잘한 것 일괄: **(C-1·C-2)** 세션 조회 GET 에 400·409 선언 · **(C-4)** nullable enum 에 `null` 포함 · **(INFO-P1·LOW)** `StrategyFieldValue` 방어 메시지에서 제출 값 제거 · 알 수 없는 본문 키 400 · 십진수 척도·지수 상한 · **(F-4)** 실 DB E2E 에 무효 값 → 영속 0 · **(LOW)** `onProvideValue` 가 세션 필드와 다른 command 필드를 받는 거동은 **측정 후 처분**(M4 fixture 가 허용을 요구하면 알려진 제한, 아니면 `InvalidTransition`) · **(F-5)** `TRACE` 405 본문은 알려진 제한(base 동일) · 마이그레이션 순서의 빈 생성 부수효과 의존은 알려진 제한 |

**수정 라운드 보고 필수 항목**: 새 public 표면이 생겼는가·밖에 무엇을 허락하는가 · 새 파일 ↔ in_scope 대조 · rollback 재산출·재실측(마지막 산출물 커밋에서).

## 계약 갱신 r2 (2026-09-27, 팀장 — verifier r2 not-ready F-r2-1~5 · code-reviewer r2 H-r2-1·M-r2-1~4 수령)

재작업 **2/5**. r1 의 차단 결함 둘(F-1 변이 셋 · F-2 교차 세션)은 실측으로 닫혔다. 새 차단 결함 F-r2-1 은 **r1 과 같은 계열**이다 — r1 은 금지를 열거했고, r1 수정은 **대상(핸들러 종류)을 열거**했다. `RouterFunction` 빈 · 빈 이름 URL 매핑 `HttpRequestHandler` · 인증 필터보다 앞선 `OncePerRequestFilter` 셋이 목록 밖에서 HTTP 로 SQL 을 실행했다(필터는 자격 없이 200). **대상을 한 종류 더 늘리는 처방은 받지 않는다.** 두 축을 함께 건다 — 하나는 「누가 무엇에 의존하는가」를 기본 제한으로, 하나는 「HTTP 로 무엇이 닿는가」를 출하 조립의 실측 목록으로.

| ID | 결정 |
|---|---|
| **D-6A2b-26** | **(F-r2-1) D-6A2b-19 ① 을 대체한다 — 대상은 `bidvector.app` 전체, 면제는 조립 클래스의 정확한 목록.** 의존 허용 목록 ⊆ 는 `bidvector.app` 의 모든 클래스(중첩·익명·컴패니언 포함, 최상위 소유자로 판정)에 걸리고, 면제는 계약 파일(`config/quality/architecture-policy.properties`)에 **정확한 클래스 이름**으로 적은 조립 클래스뿐이다(`BidVectorApplication`·`*Wiring`·수집 조립 등 오늘 있는 것 — 접두·패턴 금지). 새 클래스는 기본으로 제한된다. **보완 — 면제된 조립 클래스가 진입점을 만드는 길**: 조립 클래스의 `@Bean` 메서드가 `RouterFunction`·`HandlerMapping`·`Filter`·`Servlet`·`HttpRequestHandler`·`Controller` 류를 반환하면 그것은 아래 D-6A2b-27 이 잡는다 |
| **D-6A2b-27** | **(F-r2-1) HTTP 표면 실측 목록 — 출하 조립에서 거둔다.** production 조립 컨텍스트에서 ① 모든 `HandlerMapping` 빈(종류 불문)이 내는 handler 집합 ② 서블릿 컨테이너에 등록된 모든 `Filter`·`Servlet` 등록 집합을 거두어 **계약 파일의 기대 집합과 등식**으로 단언한다(API 포트·관리 포트 각각). 기대 집합에 없는 handler·filter·servlet 하나가 늘면 RED. 문서↔매핑 등식(D-6A2b-20)은 `RequestMappingHandlerMapping` 뿐 아니라 이 전체 handler 집합에서 프레임워크 기본 handler(명시 목록)를 뺀 것과 대조한다. **F-r2-1 의 세 변이가 (26)·(27) 각각에서 RED** 여야 한다. 게이트 술어 변경 → 표적 재검증 |
| **D-6A2b-28** | **(F-r2-3) 기준 revision 은 draft 를 만든 바로 그 읽기에서 온다.** 실행기가 draft 를 만든 전략의 revision 을 `ProvideValue` command 에 실어 보내고(서버 값 — 요청에서 받지 않는다), 커널은 그 값을 `baseRevision` 으로 담는다(다시 읽은 값으로 심지 않는다). value 시점에 command 의 기준 ≠ 현재 revision 이면 `StaleRevision` 으로 거부한다. 회귀 test 는 verifier 의 TOCTOU 재현(두 읽기 사이에 다른 세션 적용 끼우기)을 그대로 |
| **D-6A2b-29** | **(F-r2-4·M-r2-1) 406 은 본문 없음을 계약으로 받는다.** 클라이언트가 JSON 을 받을 수 없다고 말한 요청에 JSON 본문을 내는 것이 오히려 협상 위반이다. D-6A2b-6·7 의 「전부 `ErrorBody`」에 **406 예외 한 줄**을 적고, OpenAPI 의 `NOT_ACCEPTABLE` 코드는 도달 불가라 뺀다(C-3 과 같은 기준 — 도달 불가를 약속하지 않는다). 추적은 audit 행으로 한다(실측 1 증가) |
| **D-6A2b-30** | **(F-r2-2·H-r2-1) 이 slice 의 게이트 test 전부를 `gateExecutionGate` 에 등재**한다(r1·r2 에서 생긴 것 전부 — 대조: `@Disabled` 를 달면 RED) |
| **D-6A2b-31** | 일괄: **(F-r2-5·M-r2-2)** 관리 포트 미디어 타입 축 test(actuator v3·json·`*/*` → 200, xml → 406, 노출 집합 불변) · **(M-r2-4)** `editValue` 칸 배정 진단 순서 되살림 · **(L-r2-1)** OpenAPI 머리말의 삭제된 test 이름 · **(L-r2-3)** 사라진 알려진 제한 복원 · **(L-r2-5)** rollback.md 의 이동 술어를 「되돌림 대상 중 evidence 를 뺀 산출물 경로」로 정정 · **(L-r2-6)** 허용 접두 안의 포트 인터페이스는 알려진 제한. **(M-r2-3) 은 verifier 가 반증했다**(Spring 7 에서 `@Component @RequestMapping` 단독은 handler 로 등록되지 않는다) — 조치 없음 |

### 계약 갱신 r2-b (2026-09-27, 팀장 — 수정 라운드 2 보고 수령, 검증 전)

| ID | 결정 |
|---|---|
| **D-6A2b-32** | **면제를 한 덩어리로 두지 않는다 — 세 층으로 가른다.** ① **부팅·배선**(`BidVectorApplication`·`*Wiring` 등): 전면 면제(어댑터 구체 클래스·JDBC 에 의존해도 된다 — 조립이 일이다). ② **요청 스코프 조립과 그 결과**(`StrategyEditExecutor`·`StrategyQuery`·`EvaluationDryRunFactory`·`EvaluationDryRunRun` 등, **컨트롤러가 허용 목록으로 받는 것**): 면제가 아니라 **별도 허용 목록 ⊆** — workflow use case·포트 **인터페이스**·도메인 값·트랜잭션 경계 타입은 되지만, `java.sql`·`javax.sql`·Spring JDBC(`JdbcClient`·`JdbcTemplate` 등)·`adapters.**` 의 구체 클래스는 안 된다. 이유: 이 층은 컨트롤러에서 닿으므로 여기에 SQL 메서드를 더하면 HTTP 지름길이 된다(6A-3 R3-M1 ⓑ 「어댑터 추가 메서드」와 같은 형태). ③ **수집 레인**(`CollectionRunner` 등): 면제하되 **컨트롤러·② 층이 참조하면 RED**(HTTP 로 닿지 않음을 의존 방향으로 잠근다) — (27) 표면 실측과 함께. 세 층의 목록은 계약 파일에 층별 키로 정확한 이름. 변이: ② 층 클래스에 `JdbcClient` UPDATE 메서드 추가 → RED, 컨트롤러가 ③ 층 참조 → RED. 게이트 술어 변경 → 표적 재검증 |

## 계약 갱신 r3 (2026-09-27, 팀장 — verifier r3 not-ready F-r3-1(변이 여섯) · code-reviewer r3 M-r3-1~6 수령 · **운영자 결정 (A) 구조 재건**)

재작업 **3/5**. 세 라운드가 같은 계열로 뚫렸다 — r1 금지 열거, r2 대상 종류 열거, r3 면제 층·허용 통로. **진단: 위협 모델 경계가 흐렸다.** 「우회 ≥5」를 경계 없이 물으면 면제 층이 있는 한 우회 집합이 무한이다(1A 교훈과 같은 뿌리). 운영자는 (A) 를 골랐다 — 경계를 다시 긋고, 남는 자리를 **의존 방향**으로 닫는다.

**위협 모델 개정(D-6A2b-33).** 「지키는 것」 ② 「HTTP 층에서 저장소·outbox·SQL 로 가는 지름길이 없다」의 주체를 한정한다: **평범한 코드 작성·리팩터링으로 생길 수 있는 지름길**(컨트롤러·요청 스코프 조립이 SQL·어댑터 구체 클래스를 잡는 것, 어댑터 인터페이스에 메서드를 더해 부르는 것, 배선 헬퍼를 부르는 것)을 막는다. **경계 밖**: 부팅·배선 층 코드를 **일부러 비틀어** 서블릿 컨테이너·Spring MVC 확장점에 SQL 을 심는 저자 — 그 저자는 인증 필터 자체를 꺼 버릴 수도 있고, 빌드 스크립트를 임의로 고치는 저자(1A·6C 경계)와 같은 층이다. 다만 그런 확장이 **배선 층에 들어오는 것 자체**는 아래 D-6A2b-34 가 의존 방향으로 드러낸다(경계 밖이라도 조용히 들어오지는 못한다).

| ID | 결정 |
|---|---|
| **D-6A2b-33** | 위 위협 모델 개정. 6F-5-a r3 의 severity 선(평범한 리팩터링 HIGH / 관용을 벗어난 형태 MEDIUM)을 이 게이트의 판정 기준으로 명시한다 |
| **D-6A2b-34** | **(A1·A2·A6) 배선 층은 HTTP 확장 API 에 의존하지 못한다.** ① 층(부팅·배선) 클래스는 HTTP 확장 API(Spring web·Boot web/webmvc/servlet/tomcat·jakarta.servlet·catalina·tomcat 계열 — **정확한 접두 목록은 계약 파일 `config/quality/architecture-policy.properties` 가 정본**, r4-b 정정)에 의존할 수 없다. 예외는 계약 파일의 **정확한 클래스 목록**(필터 등록·관리 표면 잠금처럼 오늘 실제로 그 API 를 쓰는 곳 — 구현 레인이 실측해 최소로)이고, 예외 클래스가 만드는 등록은 (27) 표면 실측이 이미 잰다. 같은 규칙이 ① 층의 **클래스 애너테이션**(`@ControllerAdvice`·`@Controller` 류)도 막는다(애너테이션 의존도 의존이다). 변이 A1(WebMvcConfigurer→interceptor)·A2(Tomcat valve)·A6(①층 `@ControllerAdvice`)이 RED. **보조**: (27) 에 interceptor·advice 집합을 더하는 것은 하지 않는다(종류 열거로 돌아간다) |
| **D-6A2b-35** | **(A3) ② 층 허용은 정확한 클래스 목록.** `tier2.allowed-packages` 의 `bidvector.app.wiring` 통째 허용을 없애고, ② 층이 참조할 수 있는 app 클래스를 계약 파일에 정확한 이름으로 적는다 |
| **D-6A2b-36** | **(A5) 어댑터 인터페이스는 허용된 메서드 호출 쌍으로만.** ② 층·제한 층이 `adapters.**` 인터페이스의 메서드를 호출하는 것은 계약 파일의 **(호출자 클래스, 인터페이스, 메서드) 쌍 목록**(6A-3 호출 쌍 규칙과 같은 형태 — 재사용)에 있을 때만 허용한다. 인터페이스에 메서드를 더하고 부르면 목록에 없어 RED |
| **D-6A2b-37** | **(A4) Throwable 통로는 정확한 예외 목록.** 제한 층·② 층이 참조할 수 있는 `adapters.**` 예외 타입을 계약 파일에 정확한 이름으로 적고, 그 타입의 **인스턴스 메서드 호출**은 `Throwable` 에서 상속한 것만 허용한다(자기 메서드 호출 → RED) |
| **D-6A2b-38** | code-reviewer r3 처분(같은 라운드): **M-r3-1** 게이트 KDoc·test 이름을 실제 술어로 정정 · **M-r3-2** 항진식 단언 제거(실제로 일하는 `exempt − allTopLevel == ∅` 만 남기고 「대상이 조용히 줄면 RED」는 면제 목록 크기 고정 단언으로 대신) · **M-r3-3** ② 층·③ 층 규칙의 **영구 음성 fixture**(`rules()` 가 층 목록을 루트별로 받게 연다) · **M-r3-4** 표면 실측에 method mapping 빈 수 == 1 · `RouterFunction` 부재 단언 · **M-r3-5** `commandBaseRevision` 없는 행은 조회·취소가 되고 confirm 만 fail-closed(상태 쪽과 같은 모양) + 회귀 test · **M-r3-6** 멱등 재전달 판별에서 서버 파생 `baseRevision` 을 빼고, 기준이 달라진 재전달은 `STALE_REVISION` 으로 · LOW 여섯 + verifier L-r3-1~3 일괄. **M-r3-3·4·D-6A2b-34~37 은 게이트 술어 변경 → 표적 재검증** |

**변이 실측 의무(구현 레인)**: verifier r3 의 A1~A6 을 production 소스에 심어 A3·A4·A5·A1·A2·A6 각각이 **어느 규칙으로** RED 인지 표로. A2 는 D-6A2b-34 로 RED 여야 한다(경계 밖이지만 의존 방향이 드러낸다).

### 계약 갱신 r3-b (2026-09-27, 팀장 — 수정 라운드 3 보고 수령, 검증 전)

| ID | 결정 |
|---|---|
| **D-6A2b-39** | 구현 레인 자체 탐침 N3(요청 시점 `ServletContext.addServlet` — 컨테이너가 `IllegalStateException` 으로 막음, 붙어도 인증 필터가 `/*`)·N4(`ServiceLoader` 로 어댑터 타입을 적지 않고 능력 세탁 — 오늘 실행 불가)는 **D-6A2b-33 경계 밖**(평범한 리팩터링으로 생기지 않는 형태)으로 수용한다. **`OPEN-6A2B-LOCATOR-BAN`** 신설(locator·ServiceLoader 금지는 리플렉션 봉쇄 게이트(D-6F8-13)의 자리 — 그 레인으로). N2(허용 타입만으로 새 Filter 등록)는 의존 게이트를 통과하고 표면 실측(27)이 RED — 두 축이 함께 필요하다는 역방향 증명으로 checklist 에 둔다 |
| **D-6A2b-40** | 이 라운드 커밋 넷의 trailer 가 앞 라운드와 다르다(세션 중 하네스 귀속 지시 변경). **이력을 되쓰지 않고** evidence 에 사실로 둔다 |

## 계약 갱신 r4 (2026-09-27, 팀장 — verifier r4 not-ready F-r4-1~4 · code-reviewer r4 N-r4-1~14·OQ-1~3 수령)

재작업 **4/5 — 다음 not-ready 는 상한이다.** 이번 라운드로 닫히지 않으면 라운드를 더 돌리지 않고 진단과 함께 운영자에게 올린다.
차단은 두 갈래다. ① **새 축 — 능력 전달(F-r4-1)**: ② 층이 이미 쥔 `StrategyRepository`(쓰기 가능 포트)로 메모리 세션 저장소·no-op sink 를 붙여
`EditStrategyWorkflow` 를 **스스로 조립**하면 GET 한 번에 전략이 바뀌고 outbox·세션이 0 이다. 오늘 dry-run 조립과 같은 모양이라 평범한 형태다(D-6A2b-33 HIGH).
**D-6A2b-32 가 이 조립을 허용했다 — 계약의 결함이다.** ② **계약 미이행 셋(F-r4-2·N-r4-1·N-r4-2·N-r4-3)**: r3 계약 문면(D-6A2b-36·37)과 구현 술어가 다르다.

| ID | 결정 |
|---|---|
| **D-6A2b-41** | **(F-r4-1) ② 층은 쓰기 능력을 쥐지 못한다.** ① 읽기 쪽(`StrategyQuery`·`EvaluationDryRunFactory` 의 전략 읽기)은 **`save` 가 없는 읽기 전용 포트**만 받는다 — workflow 에 읽기 포트(`load` 하나)를 두고 `StrategyRepository` 가 그것을 확장하게 하거나 동등한 구조로(이름은 구현 레인). ② ② 층의 workflow 허용을 `workflow.strategy` **패키지 통째에서 정확한 타입 목록**으로 좁히고, 그 목록에 쓰기 능력 포트(`StrategyRepository`·`EditSessionRepository`·`EventSink`·outbox 포트 등 — 게이트의 기존 **능력/주변 구조 분류**로 도출, 손 목록 금지)와 **use case 생성자**를 넣지 않는다. ③ 편집 use case 의 생성은 **어댑터 트랜잭션 경계 한 곳**뿐이다(D-6A2b-3 이 이미 그 자리). dry-run 의 `EvaluateCandidatesUseCase` 조립은 `OPEN-6A2B-DRYRUN-ASSEMBLY-IN-APP` 로 정확히 한 호출자(`EvaluationDryRunFactory`)에 고정 — 다른 ② 층 클래스가 use case 생성자를 부르면 RED. 변이: verifier F-r4-1 그대로 RED + 같은 조립을 `EvaluationDryRunFactory` 에서 편집 use case 로 시도 → RED |
| **D-6A2b-42** | **(F-r4-2·N-r4-1) D-6A2b-37 첫 절 이행** — 어댑터 예외는 `isAssignableTo(Throwable)` 이 아니라 **정확 목록 소속**으로 판정(② 층은 여기에 정확 목록의 인터페이스만 추가). 목록 밖 어댑터 예외 참조 → RED. verifier r3 A4(새 예외 타입) RED |
| **D-6A2b-43** | **(N-r4-3) 어댑터 멤버 호출 판정을 fail-closed 로.** `disallowedAdapterCall` 의 「그 밖」 갈래는 허용이 아니라 거부다 — ② 층·제한 층의 어댑터 멤버 호출은 **등재된 쌍만**(오늘 실제 호출 둘: 트랜잭션 실행 · 기록형 알림 조회). 구체 클래스 멤버도 대상 |
| **D-6A2b-44** | **(F-r4-3·N-r4-2) 호출 쌍 좌표에 매개변수 타입.** (호출자, 선언 타입, 메서드 이름, **descriptor**) — 오버로드를 더하면 RED |
| **D-6A2b-45** | **(F-r4-4) 규칙마다 영구 음성 fixture.** D-6A2b-34(① 층 HTTP API) · 36/43/44(호출 쌍) · 37/42(예외) · 41(능력 전달) 각각에 fixture 하나, ③ 층 fixture 는 제한 층 규칙과 **격리**(그 규칙을 끈 층 배정으로도 RED). 대조: 규칙 하나를 항상 공집합으로 바꾸면 **그 규칙의 fixture 가** RED |
| **D-6A2b-46** | 게이트 신뢰도(같은 라운드): **(N-r4-4)** 금지 접두에 `org.springframework.boot.webmvc` 등 형제 패키지 — 접두 판정을 패키지 **경계**(`.` 경계 startsWith)로 유지하되 목록은 계약 파일이 정본이고 scope 는 개수를 적지 않는다 · **(N-r4-6)** 게이트 등재 meta-gate 의 대상을 파일명 문자열이 아니라 **구조**로(JUnit 태그 등 기존 모듈 관례를 따라 — 태그 집합 == 등재 목록 등식), 양성 대조 항진식 제거 · **(N-r4-7)** `gate-tests.properties` 를 `:app:test` 의 선언된 입력으로(workflow 선례) · **(OQ-1)** `checkcast`·`anewarray` 가 ArchUnit 직접 의존에 잡히지 않는다는 사실을 KDoc 에 적는다(verifier r4 실측: 게이트 초록) · **(OQ-2)** ① 층 `@Endpoint`/`@WriteOperation` 이 노출되지 않음을 관리 표면 잠금 test 로 **실측**(잠금이 막는다는 주장 확인, 새 게이트 아님) |
| **D-6A2b-47** | 문면·LOW 일괄: **(N-r4-5)** KDoc 넷 정정(「열거가 아니라 구성」 서술은 실제 규칙 다섯의 모양대로) · **(N-r4-8)** 기준 대조를 actor·전이표 앞에 둔 순서는 **유지**(M-r3-6 요구) — HTTP 로 도달 불가한 상호작용을 알려진 제한 + 전이표 test 로 기록 · N-r4-9~14 · verifier L-r4-1~3 |

**보고 필수(이번이 마지막 자동 라운드)**: 새 public 표면(읽기 포트 추가가 workflow 공개 표면을 넓힌다 — 밖에 무엇을 허락하는가) · 새 파일 ↔ in_scope · 규칙별 음성 fixture 표 · F-r4-1·A4·오버로드 변이 RED · **스스로 고안한 우회 셋 이상(D-6A2b-33 경계 안쪽)** · rollback 재산출·재실측.

### 계약 갱신 r4-b (2026-09-27, 팀장 — 수정 라운드 4 보고 수령, 검증 전)

| ID | 결정 |
|---|---|
| **D-6A2b-48** | 구현 레인 이탈 둘 처분. ① D-6A2b-46 의 게이트 등재 meta-gate 를 JUnit 태그가 아니라 **패키지 전수 + 양방향 등식**으로 세운 것을 **수용**(저장소에 `@Tag` 선례 0 — 기존 관례는 `WorkflowGateRegistrationTest` 의 전수 방식이고, 태그는 「붙이길 잊기」가 「등재를 잊기」와 같은 실패 모양이다). ② D-6A2b-34 의 접두 열거 문면을 계약 파일 정본 참조로 정정(위 D-6A2b-34 행) |

## 계약 갱신 r5 (2026-09-27, 팀장 — verifier r5 not-ready F-r5-1~3·L-r5-1~2 · code-reviewer r5 N-r5-1~14·OQ-r5-1~3 수령 · **운영자 결정: 상한 초과 한 라운드 승인**)

재작업 **5/5 도달 → 운영자가 여섯째 라운드를 승인했다(2026-09-27).** **r6 규칙(사전 등록)**: r6 판정에서 D-6A2b-33 경계 안쪽 HIGH 가 하나라도 나오면
라운드를 더 돌리지 않고 **게이트를 분리해 종결**한다 — 편집 endpoint·원자성·동시성은 머지하고, 남은 주입 경로 하드닝은 별 slice 로 넘긴다.

**진단(상한 보고)**: 다섯 라운드가 매번 「HTTP 층이 무엇을 **이름으로 아는가**」를 좁혔다. 능력은 **주입된 값**으로도 온다 — F-r5-1 은 ① 층 `@Bean` 이 SQL 을
실행하는 함수 값(`() -> Int`)을 내고 컨트롤러가 그것을 생성자로 받는다(컨트롤러는 ① 층 클래스 이름을 한 번도 적지 않는다, `kotlin`·`java.util` 허용 접두 안).
평범한 DI 로 오늘 실행된다. **처방은 축을 바꾸는 것이다 — 「무엇을 아는가」가 아니라 「무엇을 받을 수 있는가」.** 주입 표면은 유한하다(생성자·필드·`@Bean` 메서드 매개변수).

| ID | 결정 |
|---|---|
| **D-6A2b-49** | **(F-r5-1) 주입 표면 정확 목록.** 제한 층(`app.http` 와 컨트롤러 애너테이션 클래스)과 ② 층 클래스의 **생성자 매개변수·필드·세터 주입 타입**(제네릭 인자 포함 — `List<X>`·`ObjectProvider<X>`·함수 타입 `(A) -> B` 의 매개변수·반환 타입 전부 전개)이 계약 파일의 **정확 타입 목록 ⊆** 이어야 한다. 함수 타입·`Runnable`·`Callable`·`Supplier` 같은 **범용 능력 운반 타입은 목록에 올 수 없다**(목록 검증이 거부). 목록은 오늘 실제 주입 타입과 **등식**으로 단언(죽은 항목 금지). 제한 층의 `kotlin`·`java.util` 허용 접두는 **참조**에 대한 것이고 **주입**에는 적용되지 않는다. 변이: verifier F-r5-1 그대로 RED + 같은 클로저를 `Supplier<Int>`·`Runnable`·`java.util.function.IntSupplier`·자작 `fun interface`(app.http) 로 바꾼 넷도 RED. 게이트 술어 변경 → 표적 재검증 |
| **D-6A2b-50** | **(N-r5-1·F-r5-3) D-6A2b-41 ② 이행** — `tier2Allows` 에 능력 포트 거부 갈래(제한 층과 같은 `capabilityPorts` 도출)를 첫 갈래로 넣고, `tier2.allowed-workflow-types` 에서 능력 포트·use case 생성자 타입을 뺀다(뺄 수 없으면 이유와 함께 이탈 등재). **(N-r5-2)** 목록 == 관측 집합 등식 단언 · **(N-r5-3·L-r5-1)** 능력 보유 판정에 제네릭 인자 의존 포함 · **(N-r5-4)** `app.port-call.ports` 에 `StrategyReader` 추가, 허용 쌍 오른쪽 정정 · **(N-r5-5)** `StrategyReader` 는 제한 층에서 **직접 참조 금지**(조회기 경유만) — (2b) 표 행 정정 · **(N-r5-6·12·14)** KDoc·주석·키 이름 정정 · **(F-r5-2·N-r5-10)** meta-gate 발견을 파일명이 아니라 **컴파일된 test 클래스 전수**(JUnit 이 실행할 클래스 — app test 소스 집합의 모든 패키지)로 · N-r5-7~9·11·13 · L-r5-2(⑮ 문면) 일괄 |

**보고 필수**: D-6A2b-49·50 각 문장 ↔ 구현 심볼 대조표(이번에도 이탈이 있으면 **이탈 절에** 적는다 — 대조표에 「이행」으로 적고 실제로 다르면 그 자체가 결함이다) · 새 public 표면 · 새 파일 ↔ in_scope · F-r5-1 과 변형 넷 RED · 자체 고안 우회 셋 이상(주입 표면 축) · rollback 재산출·재실측 · 최종 HEAD.

### 계약 갱신 r5-b (2026-09-27, 팀장 — 수정 라운드 5 보고 수령, 검증 전)

| ID | 결정 |
|---|---|
| **D-6A2b-51** | **조립 근 신뢰 — D-6A2b-33 경계의 명시.** 주입 표면 목록(D-6A2b-49)이 닫는 것은 「HTTP 층이 **어떤 타입**을 받는가」이고, 그 타입 뒤에 **어떤 구현**을 꽂는지는 조립 근(① 층)이 정한다. 조립 근은 인증 필터를 꽂는 그 자리다 — 조립 근을 신뢰하지 않으면 어떤 in-tree 게이트도 서지 않는다(1A 빌드 저자 경계와 같은 뿌리). 그래서 구현 레인 자체 탐침 **R1**(① 층 `@Bean` 이 목록 안 도메인 SAM 몸통에 SQL)·**R8**(① 층이 어댑터 경계 구현을 갈아끼움)은 **경계 밖**이다. F-r5-1 과의 차이: F-r5-1 은 **범용 운반 타입**으로 능력을 몰래 넘겼다(받는 쪽 타입이 능력을 말하지 않는다) — D-6A2b-49 가 닫았다. R1·R8 은 **이름 있는 타입의 구현 선택**이다. **`OPEN-6A2B-COMPOSITION-ROOT-HARDENING`** 신설(① 층 JDBC 멤버 호출 금지 · 도메인 SAM 구현의 어댑터 층 고정 — 별 하드닝 slice). r6 검토는 ① 층 **몸통 선택** 형태를 HIGH 로 올리지 않는다(경계 밖, MEDIUM 이하로 등재) |
| **D-6A2b-52** | 구현 레인 이탈 하나 수용: 주입 운반 타입 금지의 예외 **(RequestAuditFilter, `(ApiAuditRecord) -> Unit`)** 쌍 하나 — 그 파일은 이 slice 가 diff 0 으로 묶었다(D-6A2b-5). 쌍이 오늘 실재하는 주입점임을 단언(죽은 항목 금지). 남는 위험은 D-6A2b-51 과 같은 뿌리(조립 근의 몸통 선택) |

## 종결 결정 r6 (2026-09-27, 팀장 — verifier r6 ready-for-review · code-reviewer r6 REQUEST_CHANGES(HIGH N-r6-1) 수령)

**사전 등록 r6 규칙이 걸렸다.** verifier r6 은 경계 안쪽 HIGH 0 으로 ready-for-review 를 냈으나(배열 형태는 두드리지 않았다), code-reviewer r6 이
경계 안쪽 HIGH 하나를 냈다 — **N-r6-1: 주입 표면 전개가 배열·`vararg` 를 풀지 않고 버린다**(`Array<() -> Int>`·`vararg` 한 겹으로 F-r5-1 이
되살아난다, 같은 파일의 형제 술어는 옳게 푼다 — 고침은 한 줄). 두 판정은 모순이 아니다(다른 형태를 두드렸다). **규칙대로 수정 라운드를 돌리지 않고
게이트를 분리해 종결한다.**

| ID | 결정 |
|---|---|
| **D-6A2b-53** | **게이트 분리 종결.** 편집 endpoint 여섯 · 한 트랜잭션 원자성 · 교차 세션 기준 revision · 오류 매핑 · OpenAPI · 관리 포트 405 는 **머지한다**. 의존·주입 게이트는 **지금 상태 그대로** 남긴다(엄격한 방향, 초록). 남은 주입 경로 하드닝은 별 slice 로 넘긴다 — **`OPEN-6A2B-INJECTION-HARDENING`** 신설, 첫 항목 **N-r6-1**(오늘 재현 가능 · 경계 안 · 한 줄) · 이어 N-r6-3(meta-gate 가 `@Test` 하나만 발견) · N-r6-4(상속 SAM·추상 클래스 운반 타입, 예외 목록 크기 래칫) · N-r6-6(프레임워크 콜백 세터) · L-r6-1(수신 클래스 소유 SAM 한 곳 방어). `OPEN-6A2B-COMPOSITION-ROOT-HARDENING` 에는 verifier r6 **M-r6-1**(① 층이 다른 클래스의 정적 가변 필드에 값을 쓴다 — 가변 정적 필드 금지 또는 ① 층의 타 클래스 정적 필드 쓰기 금지)을 더한다 |
| **D-6A2b-54** | **승인 전 일괄(수정 라운드 아님 — 장부·래칫 복원만)**: **(N-r6-2)** `app.port-call.ports` 에서 조용히 빠진 `CapacityPort`·`NotificationRequestPort` 를 되돌린다(감시 목록의 「오늘 호출 0」은 죽은 항목이 아니라 래칫) · **(N-r6-5)** 규칙 수 「일곱/다섯」 → 여덟 정정(KDoc·checklist·commands) + 여덟째 규칙 공집합 대조 실측 기록 · **(L-r6-2)** tier2 목록에 남은 use case 타입 둘을 이탈 절에 등재, 대조표 개수 정정 · N-r6-7~13 중 문면만인 것. **게이트 술어를 넓히는 변경은 하지 않는다**(N-r6-1 을 여기서 고치지 않는 이유 — 사전 규칙). 복원(N-r6-2)은 술어를 엄격한 쪽으로 되돌리는 것이라 표적 확인 한 번(verifier) |

## 하네스 레인 변경 (상시 절)

구현 레인 checklist 「하네스 레인 변경」 절을 옮긴다(2026-09-27, 판정 SHA 고정 시점).

- `config/quality/architecture-policy.properties` — 포트 호출 쌍 하나가 컨트롤러에서 조회기로 이동, 판정 대상 포트 둘(편집 세션 저장·이벤트 sink) 추가, 새 키 넷(의존 게이트)·둘(자격증명 참조자).
- `.github/workflows/ci.yml` — `container` job 스모크에 쓰기 왕복 절과 405 단언. 다른 job 무편집.
- `adapters/src/test/.../StrategyAdapterDependencyTest.kt` — 허용 루트 둘(이벤트 축). 조립을 어댑터 층에 두기 위해서다.
- `ConstantTimeComparisonStructureTest` 허용 목록 셋(근거 문장 포함).
- `ManagementHealthSurfaceTest` 비 GET 기대 500 → 405(D-6A2b-15).
- 기계 전수 test 둘(`OperatorAuthenticationTest`·`ProductionAssemblyAuthAuditTest`)이 경로 변수를 구체 값으로 치환한다 — 경로 변수 매핑이 처음 생겼다.
- 팀장 문서 커밋: `3750e6a6`(`milestone-6.md` 결정 ④ 개정 — 이 slice 범위 밖 문단).

## OPEN — 수령·신설

| OPEN | 처분 | 근거 |
|---|---|---|
| `OPEN-6F9-STRATEGY-WRITE-ENDPOINT` | **닫는다** | D-6A2b-2 |
| `OPEN-6A3-MAX-ACTIVE-BIDS-EDIT` | **닫는다** | D-6A2b-2 |
| `OPEN-6A3-APP-HTTP-DEPENDENCY-ALLOWLIST` | **닫는다** | D-6A2b-8 |
| `OPEN-API-WRONG-METHOD-500` | **닫는다**(API 포트) · 관리 포트는 D-6A2b-7 조건부 | D-6A2b-7 |
| `OPEN-6A1-SCAN-FILTER-SIDE-EFFECT` | **닫는다** | D-6A2b-9 |
| `OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION` | 수령·재측정·**유지**(→ 6E) | D-6A2b-10 |
| `OPEN-6A2B-VIOLATION-DETAIL` · `OPEN-6A2B-CONCURRENT-SESSION-ADVANCE` | **신설** | D-6A2b-17 |
| `OPEN-6A2B-ABANDONED-SESSIONS` | **신설**(→ 6B-3) | D-6A2b-24 |
| `OPEN-6A2B-COMPOSITION-ROOT-HARDENING` | **신설**(→ 하드닝 slice) — r6 M-r6-1 추가 | D-6A2b-51·53 |
| `OPEN-6A2B-INJECTION-HARDENING` | **신설**(→ 하드닝 slice, 첫 항목 N-r6-1) | D-6A2b-53 |
| `OPEN-6A2B-LOCATOR-BAN` | **신설**(→ 리플렉션 봉쇄 게이트 레인) | D-6A2b-39 |
| `OPEN-6A2B-DRYRUN-ASSEMBLY-IN-APP` | **신설** — 팀장 수용(2026-09-27): dry-run 조립이 어댑터 구체 클래스 셋(기록형·고정 전략·요청 여력 — DB 쓰기 없음)을 app 에서 직접 만든다. 세 이름을 계약 파일에 정확히 고정(다른 구체 클래스 → RED). 편집 경로처럼 어댑터 층으로 옮기는 일은 `adapters/**/evaluation/**` 가 in_scope 밖이라 후속 slice | D-6A2b-32 |

## 리뷰 레인

`verifier`(Phase 4, slice 끝 한 번) + `code-reviewer`(`model: sonnet` 명시, 병렬) + `privacy-gate`(오류 본문·audit·로그의 요청 본문 부재) + `contract-keeper`(OpenAPI ↔
구현). Codex 없음(운영자 결정). 재작업 상한 5.
