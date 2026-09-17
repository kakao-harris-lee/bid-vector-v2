# M6/6B-1 — commands.md

정본은 `scope.md`. 이 문서는 명령과 종료 코드만 남긴다(출력 전문·라운드 이력 금지,
하네스 2026-08-30). 마지막 HEAD 의 재실행 결과는 이 문서가 아니라 완료 보고 메시지가
정본이다(하네스 2026-09-16).

## 조사 — 인덱스·제약 실측(설계 검토 6, D-6B1-5)

## 2026-09-17T00:05:00Z
- cmd: `postgres:16.4` 임시 컨테이너에 `V1__schema.sql`~`V7__notice_agency.sql` 을 `psql -f` 로 순차 적용 후 카탈로그(`pg_indexes`·`pg_constraint`·`pg_trigger`) 질의
- exit: 0
- 핵심 결과: 표 11·인덱스 14·제약 93·트리거 25. 소비 질의 없는 FK 인덱스 공백 4건(`notice`·`opening_result`·`qualification_text`·`opening_reserve_price`의 `observation_key`) — `OPEN-6B1-INDEX-GAPS` 등재, 추가 없음(D-6B1-5).

## 계약 충돌 확인(D-6B1-7)

## 2026-09-17T00:20:00Z
- cmd: `grep -rn "friendPaths\|associate(" --include="*.gradle.kts" .`
- exit: 1
- 핵심 결과: 매치 없음 — `adapters` 가 `workflow`의 `internal` 멤버에 접근할 friend-path 설정이 저장소에 0건, `EditSession` 생성자 호출이 `adapters`에서 컴파일 불가함을 확인.

## D-6B1-7 구현 — EditSessionSnapshot 왕복(workflow)

## 2026-09-17T00:35:00Z
- cmd: `./gradlew --no-daemon :workflow:compileTestKotlin :app:compileTestKotlin`
- exit: 0
- 핵심 결과: `EditSessionRepository.load` 반환 타입 변경 뒤 두 모듈 모두 컴파일 성공(fake 반환 타입만 갱신).

## 2026-09-17T00:36:00Z
- cmd: `./gradlew --no-daemon :workflow:test --tests '*EditSession*' --tests '*EditStrategyWorkflowTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: `EditSessionSnapshotTest` 24·`EditStrategyWorkflowTest` 11·`EditSessionImportBoundaryTest` 5, 전부 0 failed. import 경계 게이트가 새 파일도 통과.

## 2026-09-17T00:38:00Z
- cmd: `./gradlew --no-daemon :app:test --tests '*Conformance*' --rerun-tasks`
- exit: 0
- 핵심 결과: strategy-edit conformance corpus 5 case 회귀 없음.

## 완료 조건 1·6B ① — 스키마 회귀 게이트(D-6B1-8, 재발명 철회 뒤)

## 2026-09-17T01:05:00Z
- cmd: `./gradlew --no-daemon :adapters:test`(V8 반영 뒤, `edit_session` 미등재 상태)
- exit: 1
- 핵심 결과: 기존 `CleanMigrationTest`(축1·5b·9)·`CleanMigrationColumnTest`(축2·3·4) 5 tests 실패 — `edit_session` 미등재. 새 게이트를 만들지 않고 이 계열을 확장하기로 결정(계약 갱신 (3)).

## 2026-09-17T01:20:00Z
- cmd: `./gradlew --no-daemon :adapters:test --tests '*CleanMigration*' --rerun-tasks`(등재 후)
- exit: 0
- 핵심 결과: 축1·2·3·4·5·5b·6·7·8·9 전부 초록.

## 변이 실측 — 축7(트리거)·축8(CHECK) 등재가 실제로 무엇을 잡는가(팀장 요청)

## 2026-09-17T01:40:00Z — 변이(축7)
- cmd: `V8__edit_session.sql`에 임시 트리거(`edit_session_mutation_probe`, `reject_mutation()` 재사용) 추가 후 `./gradlew --no-daemon :adapters:test --tests '*CleanMigrationTriggerTest*' --rerun-tasks`
- exit: 1
- 핵심 결과: `edit_session_mutation_probe` 미등재로 실패 검출. 원복 후 재확인 exit 0.

## 2026-09-17T01:45:00Z — 변이(축8)
- cmd: `edit_session.session_version`에 임시 CHECK(`< 1000000`) 추가 후 `./gradlew --no-daemon :adapters:test --tests '*CleanMigrationCheckTest*' --rerun-tasks`
- exit: 1
- 핵심 결과: `edit_session` CHECK 개수 6(기대 5) 로 실패 검출. 원복 후 재확인 exit 0.

## D-6B1-4 구현 — JdbcEditSessionRepository(adapters.strategy)

## 2026-09-17T02:10:00Z
- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcEditSessionRepositoryTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: 7 tests(왕복·WaitingForConfirmation·Applied·만료·낙관적 충돌·begin 경합·재시작) 전부 0 failed.

## 변이 실측 — 전제조건·예외(팀장 요청 넷 중 둘)

## 2026-09-17T02:15:00Z — 변이 ⓐ(전제조건 WHERE 제거)
- cmd: `Sql.UPSERT_EDIT_SESSION`의 `WHERE` 절 제거 후 `./gradlew --no-daemon :adapters:test --tests '*JdbcEditSessionRepositoryTest*' --rerun-tasks`
- exit: 1
- 핵심 결과: 충돌 test 2건(같은 값 재저장·begin 경합) 정확히 실패. 원복 후 재확인 exit 0.

## 2026-09-17T02:20:00Z — 변이 ⓑ(충돌 예외 삼키는 래퍼)
- cmd: `JdbcEditSessionRepository.save`의 `if (affected == 0) throw ...`를 no-op 로 치환 후 재실행
- exit: 1
- 핵심 결과: 같은 충돌 test 2건 정확히 실패(예외 기대했으나 없음). 원복 후 재확인 exit 0.

## 2026-09-17T02:25:00Z — 변이 ⓒ(V8 뒤 기존 게이트 무편집 초록)
- cmd: `./gradlew --no-daemon :adapters:test`(전건, V8+JdbcEditSessionRepository 반영 뒤)
- exit: 0(526/532 초록 시점, 실패 6건 전부 `CleanMigration*` 미등재 — 나중에 D-6B1-8 로 해소)
- 핵심 결과: append-only·provenance 가드 test(`JdbcNoticeRepositoryTest` 등)는 이 시점에도 무편집 초록 — V8 이 기존 가드를 깨지 않음을 확인.

## D-6B1-9 구현 — 패키지 이동(adapters.persistence → adapters.strategy)

## 2026-09-17T02:50:00Z
- cmd: `grep -rln "bidvector.workflow" adapters/src/main/kotlin/bidvector/adapters/persistence/*.kt`(이동 전 실측)
- exit: 0
- 핵심 결과: `JdbcEditSessionRepository.kt`·`EditSessionRow.kt` 둘이 걸림 — `PersistenceAdapterDependencyTest`(procurement·shared-kernel 밖 금지) 위반 확인.

## 2026-09-17T03:00:00Z
- cmd: `./gradlew --no-daemon :adapters:test --tests '*StrategyAdapterDependencyTest*' --rerun-tasks`(이동 후)
- exit: 0
- 핵심 결과: 3 tests(허용 밖 참조 없음·양성 대조 둘) 전부 통과.

## 변이 실측 — 신설 게이트 자신이 걸리는가

## 2026-09-17T03:02:00Z — 변이
- cmd: `EditSessionRow.kt`에 `import bidvector.procurement.NoticeId`(미사용) 임시 추가 후 재실행
- exit: 1
- 핵심 결과: 허용 밖 참조로 실패 검출. 원복 후 재확인 exit 0.

## 2026-09-17T03:10:00Z
- cmd: `./gradlew --no-daemon :adapters:test`(패키지 이동 뒤 전건)
- exit: 0
- 핵심 결과: 기존 `PersistenceAdapterDependencyTest`·`EventAdapterDependencyTest` 포함 회귀 없음.

## S-10 — 전건 check(파일 분할·줄 길이 정리 전)

## 2026-09-17T03:20:00Z
- cmd: `./gradlew --no-daemon check`
- exit: 1
- 핵심 결과: `:workflow:detekt` TooManyFunctions(`EditSessionSnapshot.kt` 22>11)·`:adapters:detekt` TooManyFunctions(`EditSessionRow.kt` 12>11)+MaxLineLength·`:adapters:ktlintMainSourceSetCheck` MaxLineLength.

## 2026-09-17T03:40:00Z(파일 분할·포맷·gate-tests 등재 뒤)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: 전건 초록(337 tasks, 88 executed 249 up-to-date).

## S-30·S-31·S-20 — 최종 acceptance 재확인(파일 분할 뒤)

## 2026-09-17T03:45:00Z
- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcEditSessionRepositoryTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: 7 tests 0 failed(캐시 우회).

## 2026-09-17T03:46:00Z
- cmd: `./gradlew --no-daemon :adapters:test --tests '*CleanMigration*' --rerun-tasks`
- exit: 0
- 핵심 결과: 여덟 축 전부 0 failed(캐시 우회, S-31 정정 반영).

## 2026-09-17T03:50:00Z
- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: Kotlin `check` + Python `ml-engine` 전건 통과.

## 2026-09-17T04:00:00Z
- cmd: `./gradlew --no-daemon :workflow:test :adapters:test :app:test --rerun-tasks`
- exit: 0
- 핵심 결과: ktlint 자동 포맷(줄바꿈만)이 로직에 영향 없음을 재확인.

## 리뷰 요청 조건 — clean-tree·비밀값 스캔

## 2026-09-17T04:05:00Z
- cmd: `git status --porcelain -- <in_scope 경로 개별 인자 전부>`
- exit: 0(출력 없음)
- 핵심 결과: in_scope 미커밋 변경 없음.

## 2026-09-17T04:06:00Z — 양성 대조
- cmd: `V8__edit_session.sql`에 한 줄 추가 후 위 `git status --porcelain` 재실행 → `git restore`로 원복 후 재확인
- exit: 0(추가 직후 dirty 감지, 원복 후 clean)
- 핵심 결과: clean-tree 판정이 실제로 변경을 잡음을 확인.

## 2026-09-17T04:07:00Z
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로 개별 인자 전부> reports/evidence/m6/6b1/`
- exit: 0(매치 있음)
- 핵심 결과: 매치 전부 육안 확인 — 도메인 코드의 일반 영단어(토큰 문자열 상수 함수명·정책 문서의 절차 설명)이고 실 비밀값 0건. `4c1`·`m3-3g` 등 선행 slice의 같은 판단과 동일한 근거(패턴이 넓게 걸리는 상용어라는 것은 기존에도 알려진 특성).

## D-6B1-6 — 새 public 표면 0 실측(우회 (6), 팀장 요청 — AST/바이트코드)

## 2026-09-17T04:15:00Z
- cmd: `javap -p -classpath workflow/build/classes/kotlin/main bidvector.workflow.strategy.EditSession`
- exit: 0
- 핵심 결과: 생성자가 바이트코드 수준에서는 `public`(Kotlin `internal`은 컴파일 타임 검사 — M4 `AppliedStrategy`도 같은 메커니즘, 이 slice가 만든 약점 아님). checklist.md 「새 public 표면 0」 절 참조.

## 2026-09-17T04:16:00Z
- cmd: `javap -p -classpath workflow/build/classes/kotlin/main bidvector.workflow.strategy.EditSessionRestoreKt`
- exit: 0
- 핵심 결과: `restoreEditSession`만 `public static`(반환 타입·매개변수가 이미 public 이라 이름 맹글링 없음), 나머지 10개 함수는 전부 `private static`.

## 2026-09-17T04:17:00Z
- cmd: `javap -p -classpath adapters/build/classes/kotlin/main bidvector.adapters.strategy.JdbcEditSessionRepository bidvector.adapters.strategy.EditSessionRow`
- exit: 0
- 핵심 결과: `JdbcEditSessionRepository`의 공개 멤버는 `load(...): EditSessionSnapshot`·`save(...): void`뿐. `EditSessionRow`(바이트코드는 public class)의 공개 멤버 전부 `EditSessionSnapshot`·`String`만 반환 — `EditSession`을 반환하는 멤버 0건.
