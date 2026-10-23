# M7/7C⑤ — 실행한 명령과 핵심 결과

> 호스트 규율: 모든 Gradle·npm 명령은 `flock ~/.local/bid-vector-build.lock` 아래에서 돈다(레인 여덟이 호스트를 공유한다).
> 점검 셋(`pgrep` · `free -m` · `ps --sort=-rss`)은 명령과 묶지 않고 **별도 호출로 먼저** 돌려 결과를 읽은 뒤 시작했다.

## 0. 호스트 점검 (빌드 시작 전, 매 라운드)

| 시각(KST) | available | Swap free | 다른 레인 빌드 | 판정 |
|---|---:|---:|---|---|
| 11:04 | 11.2 GB | 6.5 GB | 7B 레인 Gradle test 실행 중 | 시작(문턱 6GB·2GB 충족, flock 큐) |
| 11:25 | 12.2 GB | 6.3 GB | 다른 레인 daemon 셋 | 시작 |
| 11:50 | 11.9 GB | 6.2 GB | 다른 레인 다섯 | 시작 |
| 12:15 | 10.7 GB | 6.1 GB | 다른 레인 다섯(락 대기 큐 포함) | 시작 |

## 1. 순수 판정 + 어댑터 (산출물 커밋 전 측정)

```
flock ~/.local/bid-vector-build.lock ./gradlew --no-daemon \
  :workflow:test --tests 'bidvector.workflow.opening.*' \
  :adapters:test --tests 'bidvector.adapters.opening.*'
```

- 핵심 결과: exit **0** · `OpeningResultViewTest` **15** · `JdbcOpeningResultViewTest` **17**(skip 0 · fail 0 · error 0).
- 앞선 실행 하나는 `:workflow:compileTestKotlin` 에서 깨졌다(test 의 `export` 확장 import 누락) — 고친 뒤 위 결과다.

## 2. 생성 타입 재생성 (OpenAPI 와 바이트 등식 게이트의 입력)

```
cd web && flock ~/.local/bid-vector-build.lock bash -c 'npm ci && npm run api:types'
```

- 핵심 결과: exit **0** · `openapi-typescript 7.13.0` 가 문서를 생성 타입으로 옮겼다(88.5ms). 그 산출물은
  산출물 커밋에 함께 들어간다 — 문서만 고치고 생성을 잊은 경로는 `api:types:check`(W-5)가 막는다.

## 2b. 전건 `check` 첫 실행 — style 넷에서 끊겼다

```
flock ~/.local/bid-vector-build.lock ./gradlew --no-daemon check
```

- 핵심 결과: exit **1** · `:adapters:detekt` 2건(`MagicNumber` 바인딩 자리 리터럴 · test `MaxLineLength`) +
  `:adapters:ktlintMainSourceSetCheck` 7건(`when-entry-bracing` 5 · `blank-line-between-when-conditions` 2,
  전부 잠정 1위 복원 `when` 한 자리). **test 는 한 건도 돌기 전에 끊겼다** — 이 실행은 style 축만 잰 것이다.
- 고친 뒤 재실행 결과는 §3 에 적는다(같은 명령).

## 3. (기록 중) acceptance — `ci.yml` 의 `check` job 과 `web` job 명령 그대로

**`ml-engine`·`container` job 생략 근거(계약 문면)** — 그 두 job 의 입력 변경이 **0** 이다:

```
git diff --name-only a61ddff1..HEAD -- ml-engine/ container/ .github/   # 0 파일
git status --porcelain -- ml-engine/ container/ .github/                # 0 파일
```

`check` job 과 `web` job 은 **명령 그대로** 돈다. `check` job 의 S-20(`tools/one-command-check.sh`)은
그 셋을 한 명령으로 묶어 돌리는 step 이라, 이 slice 는 ⓐ `./gradlew --no-daemon check` 로 먼저 신호를 받고
ⓑ S-20 으로 두 job 의 문면을 그대로 기록한다(S-20 안에서 `check`·`qualityBaseline`·web 단계가 같은 순서로 다시 돈다).

## 4. (기록 중) 변이 — 바꿔치우기

각 행은 **더하기가 아니라 바꿔치우기**다(변이 전에 산출물을 커밋해 두고, 변이 뒤 `git checkout --` 로 되돌린다 —
미커밋 산출물이 있으면 그 복원이 함께 지운다). 락은 한 번만 쥐고 여덟 변이를 순서대로 돈다.

| 변이 | 무엇을 무엇으로 | RED 를 기대하는 test | 실측 |
|---|---|---|---|
| M1 | 상호 열을 SELECT 에 더하고 사실·DTO 에 칸을 만든다(세 자리 동시) | 어댑터 표지 부재 · 계약 키 집합 등식 · E2E 본문 표지 | (기록 전) |
| M2 | 읽기 전용 **문장**을 `autoCommit=false` 로 | 어댑터 「모든 문장이 읽기 전용 안」 | (기록 전) |
| M3 | 「승격 전」 갈래를 「수집 전」으로 | 순수 판정 우회 ④ · endpoint 상태 아홉 · E2E 승격 전/수집 전 | (기록 전) |
| M4 | 등재 밖 라벨 → `OPENED` | 순수 판정 우회 ⑥ · endpoint · 계약 어휘 등식 | (기록 전) |
| M5 | **공용 자리**의 반복 판정을 빈 목록으로 | 내 endpoint + **7C① endpoint** 가 함께(D-7C5-8 조건 ②) | (기록 전) |
| M5b | **공용 자리**의 읽기 실패를 빈 값으로 삼킴 | 7C① endpoint(예상) · 내 endpoint 는 **필수 칸 거부가 같은 상태를 내므로 초록일 수 있다** — 그 비대칭을 측정한다 | (기록 전) |
| M6 | 확정 금액 읽기에 잠정 축 폴백 | 어댑터 우회 ③ 둘 | (기록 전) |
| M7 | 마감 경계 `<` → `<=` | 순수 판정 경계 | (기록 전) |
