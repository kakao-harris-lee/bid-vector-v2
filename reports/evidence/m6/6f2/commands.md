# M6/6F-2 — commands.md

## 2026-09-18T08:00:12Z
- cmd: `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 신설 main/test 소스 컴파일 확인(RED 전 컴파일 게이트).

## 2026-09-18T08:04:37Z
- cmd: `./gradlew --no-daemon :adapters:test --tests '*CandidateStatusSetTest*' --tests '*SystemClockTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL.

## 2026-09-18T08:07:02Z (RED, 트리거 D-6F2-2 위반형)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcCandidateSourceTest*' --tests '*EvaluationAdapterDependencyTest*' --rerun-tasks`
- exit: 1
- 핵심 결과: `JdbcCandidateSourceTest`(상태 전이) FAILED —
  `guard_existence_and_freshness: status 축 변경은 새 observation_key(새 관측)를 동반해야
  한다`(V2 신선도 가드). test 헬퍼가 같은 관측 내용으로 두 번째 raw를 남겨 파생 키가
  기존과 같았던 것이 원인 — `observedAt`을 하나 미뤄 고쳤다(같은 파일 커밋에 포함, 「알려진
  제한」이 아니라 test 설계 결함이었다).

## 2026-09-18T08:12:20Z (GREEN, 수정 뒤)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcCandidateSourceTest*' --tests '*EvaluationAdapterDependencyTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 12 tests(JdbcCandidateSourceTest 9 + EvaluationAdapterDependencyTest 3).

## 2026-09-18T08:15:03Z (값 획득 축 실측, scope.md (2b) 표 1행)
- cmd: `./gradlew --no-daemon :app:compileTestKotlin` (`app/src/test/.../ScratchPortBoundaryProbe.kt`
  — `CandidateSourcePort { emptyList<Notice>() }`를 `adapters.evaluation`과 무관한 `app`
  모듈에서 람다 구현, 컴파일 확인 뒤 삭제·미커밋)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — `CandidateSourcePort`가 `fun interface`(public)라 이 slice의
  생성자와 무관하게 아무 모듈이나 이미 구현을 지을 수 있다는 「경계로 처리」 판정을 실측으로
  확인(희망이 아니라 컴파일 성공).

## 2026-09-18T08:21:44Z (acceptance ①, RED — ktlint import 순서)
- cmd: `./gradlew --no-daemon check`
- exit: 1
- 핵심 결과: `:adapters:ktlintMainSourceSetCheck` FAILED — `UuidCorrelationIdFactory.kt`의
  import 정렬 위반(`Imports must be ordered in lexicographic order`, `workflow.event` vs
  `workflow.evaluation` 순서). 별도 커밋으로 수정(LEDGER-2 — 좌표를 줄 번호 없이 규칙
  인용으로 정정, verifier r1).

## 2026-09-18T08:33:59Z (acceptance ①, GREEN)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 337 actionable tasks.

## 2026-09-18T08:40:15Z (acceptance ②)
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(UP-TO-DATE, 직전 `check`가 이미 실측).

## 2026-09-18T08:41:20Z (acceptance ③)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcCandidateSourceTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL.

## 2026-09-18T08:42:35Z (acceptance ④)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*CandidateStatusSetTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL.

## 2026-09-18T08:43:41Z (acceptance ⑤)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*EvaluationAdapterDependencyTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL.

## 2026-09-18T08:45:10Z (acceptance ⑥)
- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: `one-command-check: 완료 — Kotlin 전건 + Python 전건 통과`.

## 2026-09-18T08:47:02Z (clean-tree 게이트, 리뷰 요청 조건)
- cmd: `git status --porcelain -- <in_scope 경로 여덟 개 개별 인자>`
- exit: 0
- 핵심 결과: 출력 없음(clean).

## 2026-09-18T08:47:40Z (clean-tree 양성 대조)
- cmd: `UuidCorrelationIdFactory.kt`에 한 줄 추가 → `git status --porcelain -- <같은 경로>` →
  `head -n 15`로 비파괴 절삭(원래 줄 수로 복귀, `checkout --` 미사용) → 재확인
- exit: 0 (양성 대조에서는 M 한 줄이 찍혔고, 복귀 뒤 재확인은 출력 없음)
- 핵심 결과: 게이트가 실제로 변경을 잡는다 — 항상 통과만 하는 회귀가 아님을 확인.

## 2026-09-18T08:48:55Z (비밀값 스캔)
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로 여덟 개 개별 인자>`
- exit: 1
- 핵심 결과: 매치 없음 = 통과.

## 수정 라운드 1(verifier r1 HIGH 2·MEDIUM 1·LOW 1·code-reviewer MEDIUM 1) — 재검증

### 2026-09-18T09:05:11Z (표적 재검증, D-6F2-9·10 반영 뒤)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcCandidateSourceTest*' --tests
  '*CandidateStatusSetTest*' --tests '*EvaluationAdapterDependencyTest*' --tests
  '*EvaluationGateRegistrationTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 신설 test(상태 집합 거동 등식·round 타이브레이커·cap 거부·
  참조 단언·완결성 test) 전부 포함.

### 2026-09-18T09:07:40Z (`SystemClockTest` 회귀 확인)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*SystemClockTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL.

### 2026-09-18T09:09:02Z (acceptance ①, 수정 라운드 1 반영 뒤 GREEN)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 346 actionable tasks(`gateExecutionGate` 포함, 신설 다섯
  등재가 실제로 실행 확인됨).

### 변이 재실측 — 버릴 clone 셋(`git clone --no-hardlinks` 동가, `git clone .`), 전경 실행

verifier r1 이 착수 라운드 HEAD에서 심었던 MUT-1·MUT-7을 이 수정 라운드의 HEAD(`c8d290e`)에
다시 심어 재실행했다. 팀장 지시(D-6F2-9·10이 게이트 술어를 바꾸는 자리라 severity 무관
표적 재검증)에 더해 신설 완결성 test 자신도 잰다(MUT-self).

| 변이 | 조작 | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- | --- |
| MUT-1 | `JdbcCandidateSource.openCandidates()`의 `statement.setTextArray(1, biddableStatuses().map { it.name })`를 `statement.setTextArray(1, listOf("Open", "Renoticed"))`로 되돌림 | `./gradlew --no-daemon check` | **1(FAILED)** | `EvaluationAdapterDependencyTest > JdbcCandidateSource 의 컴파일된 클래스는 biddableStatuses 를 참조한다()` FAILED — D-6F2-9 ②가 닫는다 |
| MUT-7 | `EvaluationAdapterDependencyTest.kt` 파일째 삭제 | `./gradlew --no-daemon check` | **1(FAILED)** | `:adapters:gateExecutionGate` FAILED — 「게이트 test class 가 실행되지 않았다 — bidvector.adapters.evaluation.EvaluationAdapterDependencyTest」. D-6F2-10 등재가 닫는다 |
| MUT-self | `gate-tests.properties`에서 `bidvector.adapters.evaluation.JdbcCandidateSourceTest,\` 등재 행 삭제(properties 문법은 그대로 유효) | `./gradlew --no-daemon check` | **1(FAILED)** | `EvaluationGateRegistrationTest > adapters evaluation 패키지의 모든 Test class 는 gate-tests properties 에 등재된다()` FAILED — 완결성 test가 **자기 패키지의 등재 결손**도 잡는다 |

세 변이 모두 붉어졌다 — D-6F2-9·10이 겨눈 우회가 이번 HEAD에서는 실제로 막힌다. 첫 시도에서
MUT-1 clone에 `sed -i.bak`이 남긴 `.kt.bak` 파일이 `sourceLanguageGate`를 무관하게 실패시켜
(비Kotlin 소스 검출) 재실행했다 — 재현 절차에 남기는 것은 그 잔여 파일 삭제 단계다.

### acceptance 6건 전경 재확인(수정 라운드 1 반영 뒤, 배경 실행 없이 직접 exit 수신)

| # | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| S-50 | `:adapters:test --tests '*JdbcCandidateSourceTest*' --rerun-tasks` | 0 | BUILD SUCCESSFUL |
| S-51 | `:adapters:test --tests '*CandidateStatusSetTest*' --rerun-tasks` | 0 | BUILD SUCCESSFUL |
| S-52 | `:adapters:test --tests '*EvaluationAdapterDependencyTest*' --rerun-tasks` | 0 | BUILD SUCCESSFUL |
| S-11 | `qualityBaseline` | 0 | BUILD SUCCESSFUL(UP-TO-DATE) |
| S-10 | `check` | 0 | BUILD SUCCESSFUL, 337 actionable tasks |
| S-20 | `./tools/one-command-check.sh` | 0 | `one-command-check: 완료 — Kotlin 전건 + Python 전건 통과` |

**마지막 HEAD(evidence 문서 자체를 포함한 커밋 이후)의 `check` 재실측 정본은 이 문서가 아니라
verifier와 PR 조치 코멘트다** — evidence 편집이 leakPatternGate의 자기 매치를 만들 수 있어
(CLAUDE.md 「비밀값 스캔 어휘 축어 금지」) 마지막 칸은 이 규격에 따라 여기 담지 않는다.
