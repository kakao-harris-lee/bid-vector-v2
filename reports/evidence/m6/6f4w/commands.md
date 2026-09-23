# M6/6F-4-w — commands.md

## acceptance — `.github/workflows/ci.yml` `check` job 원문 대조

job `check`의 관련 step 셋(주석·설명 제외, 실행 명령만):

```yaml
- run: ./gradlew --no-daemon check
- run: ./gradlew --no-daemon qualityBaseline
- name: one-command-check.sh — 완료 조건 1 (S-20)
  run: ./tools/one-command-check.sh
```

### 2026-09-23T03:31Z
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL in 45s`, 337 actionable tasks(98 executed·6 from cache·233 up-to-date) — 전 모듈(`shared-kernel`·`strategy`·`procurement`·`qualification`·`decision`·`workflow`·`adapters`·`app`·`settlement`·`ml-contract`·`build-logic`) `check` 통과.

### 2026-09-23T03:32Z
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, quality-baseline 리포트 산출(측정 task, 실패시키지 않음).

### 2026-09-23T03:34Z
- cmd: `./tools/one-command-check.sh`
- exit: 1
- 핵심 결과: Kotlin `check`+`qualityBaseline` 선행 단계는 `BUILD SUCCESSFUL`. **S-1(Python
  ml-engine `uv sync`)이 PyPI(`pypi.org`) 접속 시도에서 network timeout**으로 실패 —
  이 slice 는 Kotlin 파일만 만지고(`ml-engine/**`는 `in_scope`·변경분 어디에도 없다) 이
  실패는 로컬 실행 환경의 PyPI 접근 제약이지 이 slice 의 산출물과 무관하다. **알려진
  제한**에 등재.

## 1단계 — 포트 매퍼 (TDD RED→GREEN, 부분 재실행)

### 2026-09-23T03:10Z
- cmd: `./gradlew --no-daemon :adapters:compileTestKotlin` (RED — `NoticeWatchSubjectPort` 신설 전)
- exit: 1
- 핵심 결과: `Unresolved reference 'NoticeWatchSubjectPort'` — 기대한 실패.

### 2026-09-23T03:10Z
- cmd: `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.evaluation.NoticeWatchSubjectPortTest"` (GREEN)
- exit: 0
- 핵심 결과: 8 tests, 0 failed.

### 2026-09-23T03:13Z
- cmd: `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.evaluation.EvaluationAdapterDependencyTest"`
- exit: 0
- 핵심 결과: 7 tests, 0 failed(상수 풀 참조·부재 단언 포함).

## 2단계 — 생성 경계 폐쇄 (D-6F4W-7, TDD RED→GREEN)

### 2026-09-23T03:27Z(RED, Text.kt 폐쇄 전)
- cmd: `./gradlew --no-daemon :strategy:test --tests "bidvector.strategy.CompileFailureHarnessTest"`
- exit: 1
- 핵심 결과: 14 tests, **4 failed**(fixture 4·5·6·7 — `expected:<COMPILATION_ERROR> but was:<OK>`,
  기대한 실패). fixture 1·2·3(10건)은 이미 GREEN — fixture 2 는 이 라운드에서 리터럴
  생성자를 `assemble*` 경유로 먼저 이관했고 폐쇄 전에도 값이 같아 영향 없음을 확인.

### 2026-09-23T03:27Z(GREEN, Text.kt 폐쇄 후)
- cmd: `./gradlew --no-daemon :strategy:test`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`. `CompileFailureHarnessTest` 14 tests, 0 failed.

### 2026-09-23T03:24Z — 이관 검증(각 모듈 부분 재실행, 전부 GREEN)
- `./gradlew --no-daemon :strategy:test --tests "bidvector.strategy.WatchRulesTest"` — exit 0
- `./gradlew --no-daemon :strategy:test --tests "bidvector.strategy.WatchTextAssemblyTest"` — exit 0
- `./gradlew --no-daemon :workflow:test --tests "bidvector.workflow.evaluation.*"` — exit 0
- `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.extraction.ExtractionGateTest" --tests "bidvector.adapters.ml.UnavailableMlAnalysisTest"` — exit 0
- `./gradlew --no-daemon :app:test --tests "*conformance*"` — exit 0, `SharedKernelCorpusConformanceTest` 86 tests, 0 failed.

## 비밀값 스캔

### 2026-09-23T03:42Z
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <이 slice 의 in_scope 경로 전부(개별 인자)> reports/evidence/m6/6f4w/`
- exit: 0(매치 있음) — 매치 5건은 전부 `app/.../StrategyExecutors.kt`의 기존(이 slice 착수
  전부터 있던) 헬퍼 함수 이름 하나에 패턴 파일의 범용 낱말이 식별자 부분 문자열로 우연히
  걸린 것이다(값이 아니라 이름). `git diff $(merge-base)..HEAD -- 그 파일`로 대조 — 이
  slice 가 그 함수를 **건드리지 않았음**(diff 밖)을 확인. 이 slice 의 실제 변경 hunk(diff)
  에는 매치가 0건이다 — 전건 오탐, 선행 slice(M3/3A)부터 있던 식별자.

## 캐시 우회 재확인 (6F-7 r2 교훈 — `check` exit 0 이어도 test task 가 FROM-CACHE 일 수 있다)

### 2026-09-23T03:35Z (HEAD `4d22a923`)
- cmd: `./gradlew --no-daemon --rerun-tasks :strategy:test :adapters:test :workflow:test :app:test`
- exit: 0
- 핵심 결과: `50 actionable tasks: 50 executed`(캐시·up-to-date 0 — 전부 실제 실행 확인).
  test 실행 수: strategy 68·adapters 661·workflow 314·app 162, **failures 전부 0**.

## 게이트 술어 변경 변이(버릴 clone `<scratchpad>/r1-mut`, `git worktree add --detach` — 동결
worktree 자체는 변이를 받지 않았다)

### 2026-09-23T05:15Z
- cmd: (이 worktree, 무변이) `javap -p -v`로 `NoticeWatchSubjectPort`·`NoticeWatchSubjectPortKt`
  두 class 의 `Methodref`/`InterfaceMethodref` 상수 풀 추출
- exit: 0
- 핵심 결과: 25건 — `ALLOWED_METHOD_REFERENCES`의 근거.

### 2026-09-23T05:20Z
- cmd: `Text.kt` 두 `private constructor` → `internal constructor`(numstat `2\t2`) 뒤
  `./gradlew --no-daemon :strategy:test --tests "bidvector.strategy.CompileFailureHarnessTest"`
  (수정 전 harness 조각으로)
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL` — 수정 전 harness 는 이 변이를 못 잡는다.

### 2026-09-23T05:24Z
- cmd: 같은 변이 위에 harness 기대 조각을 `"it is private in"`으로 좁힌 뒤 같은 test 재실행
- exit: 1
- 핵심 결과: 14 tests, 4 failed(4·5·6·7).

### 2026-09-23T05:26Z
- cmd: 변이 원복 후 같은 test 재실행
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`.

### 2026-09-23T05:35Z
- cmd: 어댑터 `keywordText` 줄을
  `assembleKeywordScopeText(java.lang.String.join(" ", listOfNotNull(noticeTitle, businessCategoryLabel)), null)`
  로 치환(numstat `1\t1`) 후 `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.evaluation.*'`
- exit: 1
- 핵심 결과: 30 tests, 1 failed(허용 목록 subset test, `String.join`·`listOfNotNull` 2건 검출).

### 2026-09-23T05:38Z
- cmd: 변이 원복 후 같은 test 재실행
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`.

### 2026-09-23T05:50Z
- cmd: `Text.kt`의 `filter(String::isNotBlank)` → `filter { it.trim(' ').isNotEmpty() }`
  (numstat `1\t1`) 후 `./gradlew --no-daemon :strategy:test --tests "bidvector.strategy.WatchTextAssemblyTest"`
- exit: 1
- 핵심 결과: 10 tests, 1 failed(U+3000 단독 조각 반례).

### 2026-09-23T05:52Z
- cmd: 변이 원복 후 같은 test 재실행
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`.

### 2026-09-23T05:53Z
- cmd: `sed -n '27p' strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt | od -c`
- exit: 0
- 핵심 결과: 탭·U+3000(UTF-8 `343 200 200`)·U+00A0(UTF-8 `302 240`) 셋 다 바이트 일치.

## 스타일 게이트

### 2026-09-23T05:58Z (버릴 clone, HEAD `547d6886`)
- cmd: `./gradlew --no-daemon check --rerun-tasks`
- exit: 1
- 핵심 결과: `:adapters:detekt FAILED`(`MaxLineLength` 5건).

### 2026-09-23T06:02Z (이 worktree, HEAD `bc9d98b4`)
- cmd: `./gradlew --no-daemon :adapters:detekt :adapters:ktlintCheck`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`.

## acceptance 재실행 (버릴 clone, HEAD `bc9d98b4`)

### 2026-09-23T06:05Z
- cmd: `./gradlew --no-daemon check --rerun-tasks`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL in 3m 56s`, `337 actionable tasks: 337 executed`(캐시 0).
  test-results XML 집계: strategy 69·adapters 661(skip 4)·workflow 314·app 162, 실패 0.

### 2026-09-23T06:12Z
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`.

### 2026-09-23T06:13Z
- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: `one-command-check: 완료 — Kotlin 전건 + Python 전건 통과`.

## 비밀값 스캔(이 라운드 변경 파일 한정)

### 2026-09-23T06:20Z
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <이 라운드 변경 코드 파일 6개(개별 인자)>`
- exit: 1(매치 없음)
