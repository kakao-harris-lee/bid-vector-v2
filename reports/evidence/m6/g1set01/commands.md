# M6/G1-SET01 commands — 돌린 것과 그 결과

판정 대상 SHA: **`4a52c2fd`**(마지막 산출물 커밋 — OPEN 신설). 산출물 커밋은 넷이다 —
`d7ebdceb`(커널·test·등재) · `8fc84e46`(추적표·capability map) · `70926fcb`(아키텍처 게이트 수정) ·
`4a52c2fd`(`OPEN-SET01-AWARD-NAME-INGEST` 신설, 팀장 지시).

호스트 규율: 무거운 명령은 전부 `flock ~/.local/bid-vector-build.lock` 으로 감쌌다. 점검 셋
(`pgrep` Gradle · `free -m` · `ps` 상위)은 빌드 명령과 **별도 호출로 먼저** 봤다. 00:30~03:00 KST 에는
아무것도 시작하지 않았다. 6G DB · run-state · collection worktree 무접촉.

## 0. 호스트 점검 (각 무거운 명령 전, 별도 호출)

```sh
pgrep -af 'GradleWrapperMain|GradleWorkerMain|GradleDaemon' | grep -v pgrep
free -m
ps -eo pid,rss,comm --sort=-rss | head
```

전건 `check` 직전 실측(23:00 KST): 다른 레인의 Gradle 프로세스 **0**, available **15,870MB**,
swap free **6,963MB**, 상위 RSS 는 다른 세션의 JVM 셋(각 0.5~0.7GB).

## 1. 표적 게이트 — 커밋 **전**(레인 규율: 전건 `check` 전에 싼 것부터)

```sh
flock ~/.local/bid-vector-build.lock ./gradlew --no-daemon \
  :settlement:ktlintMainSourceSetCheck :settlement:ktlintTestSourceSetCheck \
  :settlement:detekt :settlement:sizeGate :settlement:test
```

첫 회차는 **붉었다** — ktlint `when-entry-bracing`·`blank-line-between-when-conditions` 10+3(main),
`argument-list-wrapping` 7(test). `:settlement:ktlintFormat` 으로 고치고 다시 돌려 초록.
그 뒤 모듈 게이트 전건:

```sh
flock ~/.local/bid-vector-build.lock ./gradlew --no-daemon \
  :settlement:sizeGate :settlement:typeShapeGate :settlement:domainApiTypeGate \
  :settlement:domainSourceReferenceGate :settlement:moduleDependencyGate \
  :settlement:packageOwnershipGate :settlement:testShapeGate \
  :settlement:gateRegistrationGate :settlement:sourceLanguageGate :settlement:sourceSetLayoutGate
```

→ **BUILD SUCCESSFUL**. (2 단 sealed 계층이 `typeShapeGate` 를 지난다는 것이 여기서 실측된다 —
`interfaceCount` 는 **직접** 인터페이스만 세고 `inheritanceDepth` 는 superclass 체인만 센다.)

## 2. 컴파일 실패 하네스의 기대 진단 조각 — 추측하지 않고 실측

첫 회차에 `"Too many arguments"`(대문자)로 적었다가 **붉었다**. 실제 출력은
`error: too many arguments for 'fun judgeAwardOutcome(lookup: …): AwardOutcome'.` 이라
함수 이름까지 포함한 소문자 조각으로 고정했다. 가시성 조각은 선례대로 `it is private in` 을 쓴다
(`cannot access` 는 `private` 과 `internal` 을 가르지 못한다 — 6F-4-w 실측).

## 3. 변이 실측

스크립트 둘(scratchpad)이 돌렸다. 각 회차: 사본 백업 → 치환 → `git diff --numstat` 확인 →
`:settlement:test` → JUnit XML 에서 실패 수집 → 사본에서 복원 → `git status --porcelain -- settlement`
빈 출력 확인. 결과 표는 `checklist.md` §2.

```sh
flock ~/.local/bid-vector-build.lock ./gradlew --no-daemon :settlement:test
```

통제군(변이 없음): **34 test · 실패 0**.

**변이는 `4a52c2fd` 에서 다시 쟀다** — 첫 회차 뒤 `CompanyName.kt` 가
바뀌었으므로(`70926fcb`) 그 전의 수치는 판정 트리를 설명하지 못한다. 앞 회차 수치를 그대로 옮기지
않고 열 전부를 재측정했다.

소비자 집합이 `:settlement:test` 로 닫힌다는 전제의 실측:

```sh
grep -rln 'bidvector\.settlement' --include=*.kt */src/main | grep -v '^settlement/'   # 빈 출력
```

## 4. acceptance — `.github/workflows/ci.yml` 의 `check` job 명령 그대로

```sh
flock ~/.local/bid-vector-build.lock ./gradlew --no-daemon check
```

HEAD 를 **시작과 끝 양쪽에서** 찍어 같은지 확인했다(6E-2b 교훈 — 공유 worktree 에서 측정 창 안에
HEAD 가 움직이면 그 exit 는 어느 트리도 설명하지 못한다).

### 회차 1 — `8fc84e46`, **실패**(이 slice 의 결함을 잡았다)

**exit 1 · 10분 10초 · 357 actionable task**(211 실행 · 116 캐시 · 30 up-to-date). HEAD 양 끝 동일.
`:app:test` 의 `ArchitectureGateTest > 도메인 모듈이 허용 목록 밖을 보지 않는다` 가 **659 중 1 실패**
였고 위반 둘 다 `normalizeCompanyName` 이었다(`Appendable.append(char)` · `Locale.ROOT`). 원인·처방·
교훈은 `checklist.md` §4. 고친 커밋이 `70926fcb` 다.

이 회차를 「버린 측정」으로 적지 않는다 — **이 slice 에서 가장 값을 낸 측정**이고, 모듈 게이트 전건이
초록인 상태에서 결함을 잡은 유일한 자리다.

### 회차 1 의 수정 확인 — 표적(00:08 KST 시작, 42초)

```sh
flock ~/.local/bid-vector-build.lock ./gradlew --no-daemon \
  :settlement:ktlintMainSourceSetCheck :settlement:detekt :settlement:sizeGate \
  :settlement:test :app:test --tests '*ArchitectureGateTest*'
```

→ **exit 0**. `:settlement:test` **34 test · 실패 0**, `:app:test`(필터 적용) **37 test · 실패 0**.
`--tests` 는 바로 앞 task(`:app:test`)에만 걸린다 — `:settlement:test` 가 34 를 그대로 돈 것이 그
증거다(필터가 그 task 에도 걸렸다면 「매치 0」으로 실패했을 것이다).

### 회차 2 — `4a52c2fd`, 판정 회차

**exit 0 · BUILD SUCCESSFUL 9분 57초 · 365 actionable task**(67 실행 · 3 캐시 · 295 up-to-date).
HEAD 를 양 끝에서 찍어 같음을 확인했다 — `4a52c2fd686e2811da5b7ba1ff75b2cb8f8bca2f`.
`:settlement:test` 는 이 회차에서도 **34 test · 실패 0**.

벽시계 경과는 19분 55초였고 그중 약 10분은 **`flock` 대기**다(다른 레인의 빌드). 빌드 자체의
경과는 Gradle 이 보고하는 9분 57초다 — 둘을 구별해 적는다.

`--no-daemon` 뒤에도 유휴 daemon 이 남는지는 `pgrep -af GradleDaemon` 으로 확인하고, 남으면
**그 PID 만** 멈춘다. `./gradlew --stop` 은 같은 버전의 daemon 을 전부 멈춰 다른 레인의 빌드를
죽이므로 쓰지 않는다.

### `web` · `ml-engine` · `container` 를 생략한 근거 (path-limited diff)

```sh
git diff --name-only bee7c36f..4a52c2fd -- web ml-engine docker .github contracts openapi
```

→ **빈 출력**. 이 slice 의 diff 는 `settlement/**` · `config/quality/gate-tests.properties` ·
`docs/discovery/capability-map.md` · `reports/evidence/**` 뿐이다.

## 5. evidence 커밋 뒤 `leakPatternGate`

```sh
flock ~/.local/bid-vector-build.lock ./gradlew --no-daemon leakPatternGate
```

PLACEHOLDER_LEAK_RESULT

이 게이트는 `reports/evidence/` 만 훑고 `scope.md` 는 제외한다. 이 slice 의 evidence 는 비밀값 스캔
어휘를 축어로 담지 않는다 — legacy 상수와 V2 정책 필드의 이름에 그 어휘와 겹치는 조각이 있어
**좌표와 부류로만** 적었다(`checklist.md` §0.1 의 블록인용이 그 사유를 든다).

## 6. 돌리지 않은 것

- `qualityBaseline` — 측정이지 게이트가 아니다(CI 가 별도 step 으로 돌리고 실패시키지 않는다).
- `./gradlew --stop` — 다른 레인의 daemon 을 함께 멈추므로 쓰지 않았다. 자기 호출은 전부
  `--no-daemon` 이고, 레인 종료 시 `pgrep -af GradleDaemon` 으로 확인한다.
- DB·compose·외부 호출 — 이 slice 는 순수 커널이라 어느 것도 건드리지 않는다.
