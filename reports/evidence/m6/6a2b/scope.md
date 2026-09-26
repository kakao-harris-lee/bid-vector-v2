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

## 리뷰 레인

`verifier`(Phase 4, slice 끝 한 번) + `code-reviewer`(`model: sonnet` 명시, 병렬) + `privacy-gate`(오류 본문·audit·로그의 요청 본문 부재) + `contract-keeper`(OpenAPI ↔
구현). Codex 없음(운영자 결정). 재작업 상한 5.
