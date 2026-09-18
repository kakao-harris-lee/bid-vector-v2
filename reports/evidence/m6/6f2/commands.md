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
- 핵심 결과: `:adapters:ktlintMainSourceSetCheck` FAILED —
  `UuidCorrelationIdFactory.kt:3:1 Imports must be ordered in lexicographic order`
  (`workflow.event` vs `workflow.evaluation` 순서). 별도 커밋으로 수정.

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

**마지막 HEAD(evidence 문서 자체를 포함한 커밋 이후)의 `check` 재실측 정본은 이 문서가 아니라
verifier와 PR 조치 코멘트다** — evidence 편집이 leakPatternGate의 자기 매치를 만들 수 있어
(CLAUDE.md 「비밀값 스캔 어휘 축어 금지」) 마지막 칸은 이 규격에 따라 여기 담지 않는다.
