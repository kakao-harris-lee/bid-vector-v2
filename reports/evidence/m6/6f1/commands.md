# M6/6F-1 — commands.md

정본: `reports/evidence/m6/6f1/scope.md`(base 값도 그 문서가 정본 — verifier r3 LOW-B:
여기 base를 고정 문자열로 또 적으면 scope.md가 rebase로 갱신될 때 이 문서만 낡는다).
아래 블록은 `48133c9` 시점(패키지 이동 직후)까지 기록이고, 그 뒤 팀장 조율로
`StrategyAdapterDependencyTest` 소유권이 6B-1로 확정돼 그 파일을 철회한 커밋(`d9fda0a`)이
이어졌다 — 「의존 게이트 병합 전 대조」 절이 그 뒤 실측이다. 최종 HEAD의 acceptance
재확인은 evidence가 아니라 완료 보고 메시지가 정본이다(2026-09-16 규율 — 자기 마지막
커밋의 post-state는 evidence가 담을 수 없다).

## acceptance (전건, 최종 HEAD)

## 2026-09-17T00:53:00Z
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: S-10 — 전 모듈 `check` 통과(shared-kernel·strategy·workflow·procurement·app·adapters 포함)

## 2026-09-17T00:14:00Z
- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcStrategyRepositoryTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: S-50 — 6 tests(왕복·개정 증가·정책 불일치·전략 없음·경계값·카탈로그 확인), 0 failed

## 2026-09-17T01:00:00Z
- cmd: `bash tools/one-command-check.sh`
- exit: 0
- 핵심 결과: S-20 — Kotlin 전건(`check`) + Python 전건(ruff·mypy·import-linter·pytest·wheel) 통과

## 설계 검토 — (2) 값 획득 축 실측

### 도메인 생성자 무호출(scope.md (2b) 표 둘째 행 「닫는다」)

## 2026-09-17T00:20:00Z
- cmd: `grep -n "OperatorStrategy(\|WatchVerdict.Passed(\|Score(" adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcStrategyRepository.kt adapters/src/main/kotlin/bidvector/adapters/strategy/StrategyRow.kt`
- exit: 1
- 핵심 결과: 매치 없음(grep 무매치 exit 1) — 어댑터 두 파일에 도메인 생성자 호출 0

### 정책 파일 무직접참조(D-6F1-6)

## 2026-09-17T00:20:30Z
- cmd: `grep -n "^import" adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcStrategyRepository.kt adapters/src/main/kotlin/bidvector/adapters/strategy/StrategyRow.kt`
- exit: 0
- 핵심 결과: `bidvector.strategy.STRATEGY_POLICY` import 없음(policy는 생성자 주입 `Resolution.Resolved`만 받는다)

### 변이 실측 넷(팀장 지시 4-ⓐ~ⓓ, 매 변이 뒤 원복 확인 포함)

## 2026-09-17T00:22:00Z
- cmd: `<probe 파일>OperatorStrategy(...)` 를 `bidvector.adapters.strategy` 패키지에 심고 `./gradlew --no-daemon :adapters:compileKotlin`
- exit: 1
- 핵심 결과: ⓐ 컴파일 거부 — `Cannot access ... it is internal in 'bidvector.strategy.OperatorStrategy'`(D-6F1-2 폐쇄 실측). 원복 후 재컴파일 exit 0 확인.

## 2026-09-17T00:35:00Z
- cmd: `JdbcStrategyRepository.load()`의 `Invalid` 분기를 `validate(StrategyDraft(), revision, policy)` 기본값 대체로 바꾸고 `:adapters:test --tests '*JdbcStrategyRepositoryTest*' --rerun-tasks`
- exit: 1
- 핵심 결과: ⓑ 「정책 불일치」 test 가 예외를 기대했으나 던지지 않아 FAILED(D-6F1-3 회귀 방지 실측). 원복 후 6 tests 0 failed 확인.

## 2026-09-17T00:44:00Z
- cmd: `V9__operator_strategy.sql`의 `operator_strategy.revision` 컬럼에 `DEFAULT 0` 추가(sed) 뒤 `:adapters:test --tests '*JdbcStrategyRepositoryTest*' --rerun-tasks`(Testcontainers 새 컨테이너로 재migrate)
- exit: 1
- 핵심 결과: ⓒ 「카탈로그 확인 — revision 컬럼은 DB 기본값이 없고...」 test가 `column_default`가 `"0"`으로 나와 FAILED(D-6F1-5 회귀 방지 실측). 원복 후 재실행 exit 0 확인.

## 2026-09-17T00:47:00Z
- cmd: `JdbcStrategyRepository.load()`에 `row == null` 분기를 `InvalidStoredStrategyException(emptyList())` throw로 바꾸고 `:adapters:test --tests '*JdbcStrategyRepositoryTest*' --rerun-tasks`
- exit: 1
- 핵심 결과: ⓓ 6 tests 중 5 FAILED(「전략 없음」 test가 예외를 받고, 다른 test들도 초기 load에서 연쇄 실패) — D-6F1-4(없음≠무효) 회귀 방지 실측. 원복 후 6 tests 0 failed 확인.

### 검증 함수가 실제로 무효 입력을 거부하는지((2b) 표 셋째 행)

`정책 불일치` test(JdbcStrategyRepositoryTest.kt) 자체가 이 실측이다 — 허용 policy로 저장한
`minimumMatchScore=0.90`을 더 좁은 policy(`matchScoreMax=0.5`)로 `load()`하면
`bidvector.strategy.validate()`가 실제로 `Invalid(ScoreOutOfRange(MinimumMatchScore))`를
내고, 그 값이 `InvalidStoredStrategyException.violations`로 그대로 전파됨을 `shouldBe`로
단언한다(위 acceptance 2026-09-17T00:14:00Z 실행에 포함).

## 새 public 표면 확인(javap)

## 2026-09-17T00:19:00Z
- cmd: `javap -p adapters/build/classes/kotlin/main/bidvector/adapters/strategy/JdbcStrategyRepository.class adapters/build/classes/kotlin/main/bidvector/adapters/strategy/StrategyRow.class adapters/build/classes/kotlin/main/bidvector/adapters/strategy/StrategyRowKt.class adapters/build/classes/kotlin/main/bidvector/adapters/strategy/InvalidStoredStrategyException.class`
- exit: 0
- 핵심 결과: `JdbcStrategyRepository`(생성자·load·save 공개, scope.md (2b) 표 첫 행과 일치) ·
  `StrategyRow`/`toDraft`/`toRow`/`toStrategyRow`/`bindStrategyRow`는 Kotlin `internal`
  소스 가시성(바이트코드 자체는 JVM 관례상 public — `Score`·`OperatorStrategy` 등 저장소
  전역의 기존 `internal` 타입과 같은 성질, 위 변이 ⓐ가 실제 차단 지점을 이미 컴파일
  단계에서 실측) · `InvalidStoredStrategyException`은 새 공개 진단 타입(값 획득 축 아님).

## 비밀값 스캔

## 2026-09-17T00:59:00Z
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로 열둘 개별 인자> reports/evidence/m6/6f1/`
- exit: 0
- 핵심 결과: 매치 둘 — 전부 `PersistenceTestSupport.kt`의 기존(base `c4d09cc`) Testcontainers
  test-only 상수 줄이고 이 slice의 diff 밖(추가한 것은 truncate 목록 5줄뿐, 그 자리는
  매치 없음). 육안 확인 — 실 자격증명·개인정보 없음.

## Sql.kt 플레이스홀더 수 대조(왕복 버그 조기 발견 — RED 단계 실측)

## 2026-09-17T00:05:00Z
- cmd: `python3 -c "..."`(UPSERT_STRATEGY·INSERT_STRATEGY_REVISION의 VALUES 절 '?' 개수 계산)
- exit: 0
- 핵심 결과: 21·21(StrategyRow 필드 수 21과 일치) — 최초 작성 시 20개였던 것을 이 대조로
  발견해 수정(구현 중 실측, 커밋 전).

## 의존 게이트 병합 전 대조(팀장 조율 — 소유권 6B-1로 확정, checklist.md 참고)

## 2026-09-17T01:12:00Z
- cmd: (임시 clone, HEAD `d9fda0a`) 6B-1 worktree의 `StrategyAdapterDependencyTest.kt`만
  `adapters/src/test/kotlin/bidvector/adapters/strategy/`에 복사한 뒤
  `./gradlew --no-daemon :adapters:test --tests '*StrategyAdapterDependencyTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: 6B-1의 허용 목록(workflow.strategy·strategy·sharedkernel·adapters.persistence)
  이 이 slice의 실제 import를 전부 덮는다 — 3 tests(허용 확인·양성 대조 둘) 0 failed.

## 2026-09-17T01:13:00Z
- cmd: (같은 임시 clone) `./gradlew --no-daemon :adapters:check`
- exit: 0
- 핵심 결과: 이 slice의 코드가 6B-1의 의존 게이트를 포함한 `adapters` 모듈 전건 게이트를
  통과한다(병합 전 사전 확인).
