# M6/6B-1 — checklist.md

정본은 `scope.md`. 각 항목의 근거는 `commands.md`의 명령·종료 코드를 가리킨다(값 재기재 금지).

## 리뷰 요청 조건(CLAUDE.md·evidence-pack 스킬)

- [x] 구현 diff가 커밋되어 base/head 고정 — `commands.md`의 clean-tree 확인(출력 없음) + 양성 대조(파일 한 줄 추가 → dirty 감지 → `git restore` 원복) 항목.
- [x] `scope.md`의 `acceptance_commands` 전부 exit 0 — `commands.md`의 S-10·S-30·S-31·S-20 최종 재실행 항목.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `./gradlew --no-daemon check` 전건(부분 게이트 아님, `commands.md` 참조).
- [x] 변경된 fixture와 정책 version의 근거 — 이 slice는 fixture를 쓰지 않는다(스키마·저장소 코드, N/A). 정책 값(`session_version` 전제조건 형태)은 D-6B1-3·D-6B1-4에 근거를 실었다(`scope.md`).
- [x] 알려진 제한과 rollback 방법 — 아래 「알려진 제한」·`rollback.md`.
- [x] 비밀값 스캔 — `commands.md` 마지막 항목. exit 0(매치 있음)이나 전건 육안 확인 결과 실 비밀값 0건(도메인 상용어 오탐, 선행 slice와 같은 판단).

## (2b) 값 획득 축 — 실측 결과(scope.md 표 대응)

| 표면 | 판정 | 실측 |
| --- | --- | --- |
| `JdbcEditSessionRepository`(public class) | 경계로 처리 | 생성자는 `DataSource` 하나뿐(`adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcEditSessionRepository.kt`) — 도메인 값을 만들지 않는다. `save`는 이미 완성된 `EditSession`을 받아 `toSnapshot()`(안전한 방향)으로 내릴 뿐이다. |
| `EditSessionRow`의 도메인 복원 | 닫는다(D-6B1-6) | `EditSessionRow`는 `EditSessionSnapshot`(원시 필드)만 만든다 — `EditSession` 생성 없음. 유일한 복원 경로는 `workflow` 안 `internal fun restoreEditSession`이고 `adapters`에서 호출 자체가 컴파일되지 않는다(`commands.md` friendPaths 실측). |
| `config/quality/schema-baseline.properties` | 폐기(D-6B1-8) | 계약 갱신 (3)으로 삭제 — 스키마 기대치는 `CleanMigration*Test` 코드 자체가 정본(외부 정책 파일 없음, `OPEN-6C-POLICY-GATE-STRUCTURAL` 계열 표면을 만들지 않는다). |

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
4. **scope 밖 파일 셋의 기계적 편집** — 계약 in_scope에 명시되지 않았으나 포트 시그니처·패키지 이동의 필연적 결과로 편집한 파일: `app/src/test/kotlin/bidvector/app/conformance/StrategyEditExecutors.kt`(fake `load` 반환 타입, D-6B1-7 파급), `adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt`(TRUNCATE 목록에 `edit_session` 추가), `config/quality/gate-tests.properties`(`EditSessionSnapshotTest` 등재). 셋 다 단언·시나리오 무편집의 기계적 추가이고 M4/4C-1의 같은 계열 선례(port 시그니처 변경의 파급)와 동형이다.
5. **비밀값 스캔 오탐** — `commands.md` 참조. 도메인 상용어(토큰 문자열 상수 함수명·정책 문서의 일반 서술) 매치이고 실 비밀값 0건.

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
