# M6/6B-1 — checklist.md

정본은 `scope.md`. 각 항목의 근거는 `commands.md`의 명령·종료 코드를 가리킨다(값 재기재 금지).

## 리뷰 요청 조건(CLAUDE.md·evidence-pack 스킬)

- [x] 구현 diff가 커밋되어 base/head 고정 — `commands.md`의 clean-tree 확인(출력 없음) + 양성 대조(파일 한 줄 추가 → dirty 감지 → `git restore` 원복) 항목.
- [x] `scope.md`의 `acceptance_commands` 전부 exit 0 — `commands.md`의 S-10·S-30·S-31·S-20 최종 재실행 항목.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `./gradlew --no-daemon check` 전건(부분 게이트 아님, `commands.md` 참조).
- [x] 변경된 fixture와 정책 version의 근거 — 이 slice는 fixture를 쓰지 않는다(스키마·저장소 코드, N/A). 정책 값(`session_version` 전제조건 형태)은 D-6B1-3·D-6B1-4에 근거를 실었다(`scope.md`).
- [x] 알려진 제한과 rollback 방법 — 아래 「알려진 제한」·`rollback.md`.
- [x] 비밀값 스캔 — `commands.md` 마지막 항목. exit 0(매치 있음)이나 전건 육안 확인 결과 실 비밀값 0건(도메인 상용어 오탐, 선행 slice와 같은 판단).

## (2b) 값 획득 축 — 실측 결과

**verifier r1 MEDIUM-4 시정** — 표의 정본을 `scope.md` 「(2b) 값 획득 축」 절 하나로 좁힌다
(낡는 좌표 회피 — 같은 표를 두 문서에 각자 유지하면 한쪽만 갱신되고 벌어진다, 이번이 그
사례였다). 그 표가 이제 일곱 행(계약 갱신 (2)·(5)가 실제로 내놓은 public 표면 전부)이고
**판정되지 않았던 실질**(`EditSessionSnapshot`이 여는 필드 간 정합 공백, `EditSession`에
`init` 불변식이 없어 `restoreEditSession`이 상태-lastCommand 조합까지는 못 본다)을
판정·문서화했다. 닫으려면 `Transition.kt`가 정합 조합을 재사용 가능한 형태로 내놓아야
하는데 이 slice(D-6B1-7, `workflow/**` 를 스냅숏 배관에 한정) 범위 밖이라 코딩하지
않았다 — `OPEN-6B1-CROSS-FIELD-CONSISTENCY` 로 등재(scope.md OPEN 표).

### 새 public 표면 0 — `javap` 바이트코드 실측(우회 (6))

`javap -p`로 컴파일된 클래스를 직접 읽었다(소스 주석이 아니라 바이트코드 자체):

- `bidvector.workflow.strategy.EditSession`의 생성자와 `bidvector.workflow.strategy.EditSessionRestoreKt.restoreEditSession(...)`는 **바이트코드 수준에서는 `public`이다** — Kotlin의 `internal` 가시성은 컴파일 타임 검사다. **verifier r2 LOW-4 시정** — 이름 맹글링이 안 붙는 이유는 "매개변수·반환 타입이 public"이 아니라 **top-level 함수라서**다(판별자는 top-level 대 클래스 멤버 — 같은 저장소의 `EditSession.copy$bid_vector_workflow`·`OperatorStrategy.copy$bid_vector_strategy`는 타입이 전부 public인 `internal` **멤버**인데도 맹글링된다, verifier r2 실측). 이것은 **이 slice가 만든 새 약점이 아니다** — `AppliedStrategy`·`OperatorStrategy`(M4)의 `internal constructor`도 같은 메커니즘(컴파일 타임 강제)에 의존해 왔다.
  **verifier r2 LOW-5 시정 — 실제 강도.** 「가시성을 넓히지 않는다 / 새 public 표면 0」이 말하는 경계는 **Kotlin 소스 경계 하나**다. 이 저장소에서 그 경계에 두 겹이 더 붙어 있다: ① Kotlin 컴파일러(다른 모듈 Kotlin 소스에서 직접 호출 시도 → 컴파일 오류, 실측) ② `sourceLanguageGate`(Java 소스로 우회 시도 → `check` 안에서 exit 1, verifier가 실제 `.java` 파일을 심어 실측) ③ `friendPaths`·`associate` 설정 0건(r1 실측, 컴파일러 경계를 넓히는 설정이 없음을 확인). **리플렉션은 이 세 겹 전부의 경계 밖이고 막는 장치가 없다** — verifier가 `setAccessible` 없이 리플렉션으로 `EditSession`을 직접 생성해 전이표가 만들 수 없는 조합(`Applied(999999)`+`lastCommand=ProvideValue`+`sessionVersion=0`)을 실제로 찍어냈다(r2). 이 한계는 6B-1 고유가 아니라 **이 저장소의 `internal` 기반 위조 차단 주장 전부**(1E `OperatorStrategy`·M4 `AppliedStrategy`·6B-1 `EditSession`)에 공통이다 — `EditSession`의 주 생성자는 이 slice 이전에도 바이트코드 public이었고 매개변수 타입이 전부 public이라 리플렉션 위조 재료가 이미 다 있었다(한계 증분 0). 우회 (6)이 요구한 "AST로 잰 새 public 시그니처 증분 0"은 성립한다(어댑터 Kotlin 소스로 도달 가능한 새 진입점 0) — **판정: 경계 밖, 깨지지 않음.** 처방은 새 게이트가 아니라 이 문면 정정이다.
- `bidvector.adapters.strategy.EditSessionRow`(소스는 `internal object`)도 바이트코드는 `public final class`다 — 그러나 그 공개 멤버(`encodeStatePayload`·`encodeLastCommand`·`toSnapshot`)는 전부 `EditSessionSnapshot`·`String`만 반환한다. `EditSession`을 반환하는 멤버가 **하나도 없다**(`javap -p -classpath adapters/build/classes/kotlin/main bidvector.adapters.strategy.EditSessionRow` 실측).
- `bidvector.adapters.strategy.JdbcEditSessionRepository`의 공개 멤버는 `load(EditSessionId): EditSessionSnapshot`·`save(EditSession): void`뿐이다 — `EditSessionRepository` port가 요구하는 시그니처와 정확히 같다(신규 증분 없음).
- 결론: **`adapters` Kotlin 소스에서 `EditSession(...)`·`restoreEditSession(...)`·`EditSession.copy(...)`를 호출하는 코드는 컴파일되지 않는다**(`commands.md`의 `friendPaths` 실측과 실제 컴파일 시도가 이미 이것을 보였다). 「새 public 표면 0」의 의미는 바이트코드의 `public` 키워드 개수가 아니라 **어댑터 소스 코드로 도달 가능한 새 진입점이 없다**는 것이다.

## 제약·인덱스 실측표(설계 검토 ②, D-6B1-5)

`postgres:16.4` 빈 컨테이너에 V1~V7 적용 후 카탈로그 실측(`commands.md` 조사 항목). 표 11·인덱스 14·제약 93·트리거 25(V8 적용 후 표 12·인덱스 15·트리거 25·CHECK +5 — `CleanMigration*Test` 값이 정본).

인덱스 공백(FK 있으나 지원 인덱스 없음, 코드 전수 grep 결과 소비 질의도 없음):

| FK 컬럼 | 참조 | 지원 인덱스 | 소비 질의 |
| --- | --- | --- | --- |
| `notice.observation_key` | `raw_observation(observation_key)` | 없음 | 없음 |
| `opening_result.observation_key` | 〃 | 없음 | 없음 |
| `qualification_text.observation_key` | 〃 | 없음 | 없음 |
| `opening_reserve_price.observation_key` | 〃 | 없음 | 없음 |

`opening_reserve_price`의 `(notice_number, notice_round)` FK는 자체 PK(`notice_number, notice_round, reserve_price_sequence`)의 접두어로 이미 지원됨 — 공백 아님.

**처분(D-6B1-5)**: 추가하지 않는다. `OPEN-6B1-INDEX-GAPS`에 등재 — 소비 질의를 가진 slice가 근거와 함께 추가.

## 알려진 제한

1. **`OPEN-6B1-SAVE-OUTCOME`(신설, scope.md 계승)** — 낙관적 충돌은 구체 예외(`EditSessionConflictException`)로만 표현된다. 결과 타입화(재시도·거부 정책)는 두 번째 writer(API·스케줄러)가 생길 때 workflow 도메인 결정과 함께.
2. **`OPEN-6B1-INDEX-GAPS`(신설)** — 위 표의 인덱스 공백 4건. 소비 질의가 생기는 slice가 추가.
3. **마이그레이션 rollback 비대칭** — 코드를 되돌려도 이미 적용된 DB에는 `edit_session` 표가 남는다(`rollback.md` 참고).
4. ~~scope 밖 파일 셋의 기계적 편집~~ — **계약 갱신(6)으로 in_scope 편입**(팀장, 「알려진 제한이 아니라 in_scope다 — slice의 커밋 집합은 in_scope 경로의 변경」): `app/src/test/kotlin/bidvector/app/conformance/**`(fake `load` 반환 타입, D-6B1-7 귀결)·`adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt`(TRUNCATE 목록에 `edit_session` 추가)·`config/quality/gate-tests.properties`(`EditSessionSnapshotTest` 등재). 더 이상 알려진 제한이 아니다 — `scope.md` in_scope 목록 참조.
5. **비밀값 스캔 오탐** — `commands.md` 참조. 도메인 상용어(토큰 문자열 상수 함수명·정책 문서의 일반 서술) 매치이고 실 비밀값 0건.
6. **`StrategyAdapterDependencyTest`(바이트코드 상수 풀 의존 게이트)는 인라인되는 표면을 구조적으로 못 본다(verifier r3 LOW-6)** — 금지 모듈이 `public const val`이나 top-level `public inline fun`을 내놓으면, 그것을 쓰는 코드는 컴파일 시점에 값·본문이 인라인돼 상수 풀에 금지 좌표가 남지 않는다. verifier가 금지 모듈에 그런 표면을 심어 실측했다(게이트 초록, 상수 풀 흔적 0). 지금은 잠재다 — 금지 루트(`procurement`·`decision`·`qualification`·`workflow.event`·`adapters.ml`)에 `public const val`도 top-level `inline fun`도 현재 0건이다. 처방은 게이트 신설이 아니라 이 한계를 적어 두는 것 — 뒤 slice가 금지 모듈에 인라인 표면을 만들 때 이 항목이 걸린다.

## OPEN 처분 — scope.md 승계

| 식별자 | 처분 |
| --- | --- |
| M4/4B 알려진 제한 ②(세션 영속 실 구현·`sessionVersion` 미검증) | **이 slice로 종결** — `JdbcEditSessionRepository`가 실 구현이고 낙관적 동시성 test 7건이 실측한다. |
| `OPEN-5E-JOB-PERSISTENCE`·`OPEN-5E-JOB-QUEUE-BOUND` | 이 slice 밖(6B-4, D-6B1-2) — 무변경. |
| `OPEN-6B1-SAVE-OUTCOME` | 위 「알려진 제한」 1 — 신설, 미결. |
| `OPEN-6B1-INDEX-GAPS` | 위 「알려진 제한」 2 — 신설, 미결. |

## 계약 충돌 이력(구현 레인 정지·보고 셋, scope.md 계약 갱신 이력이 정본)

1. **D-6B1-7** — `EditSession`이 `internal constructor`라 `load()`가 `EditSession`을 직접 반환할 수 없었다. M4/4C-1 선례(`OutboxPort.claim() -> ClaimedOutboxRow`)를 재사용해 `load()`만 원시 스냅숏으로 좁게 재개방.
2. **D-6B1-8** — 계약 초판이 만들려던 clean DB 게이트가 기존 `CleanMigrationTest` 계열(D-3D-6, M3/3D~M4/4C-2)의 재발명이었다. 기존 계열에 `edit_session`을 「추가만」으로 등재하는 것으로 대체.
3. **D-6B1-9** — 세션 어댑터를 `adapters.persistence`에 두면 그 패키지의 workflow 참조 금지 게이트(`PersistenceAdapterDependencyTest`, M3/3D)에 걸렸다. `adapters.event`의 선례를 따라 `adapters.strategy` 신설 패키지 + 전용 게이트로 이동.

세 자리 모두 코드를 짜기 전 또는 짠 직후 멈추고 팀장에게 선택지와 함께 보고했고, 팀장이 계약을 갱신한 뒤 이어갔다(scope.md 「계약 갱신 이력」 표).
