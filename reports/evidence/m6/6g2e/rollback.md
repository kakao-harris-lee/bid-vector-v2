# M6/6G-2e — rollback

> 레인별로 **자기 축만** 적는다. 되돌림은 range revert 가 아니라 **in_scope 경로 한정**이다 —
> 같은 range 에 다른 레인·하네스 커밋이 섞인다. 목록은 손으로 쓰지 않고
> `git diff --name-status <base>..HEAD` 에서 기계로 낸다. **라운드마다 그 라운드의 마지막
> 산출물 커밋에서 다시 낸다**(앞 라운드 실측을 옮기지 않는다).

## Python 축 (D-6G2e-6 · 6b · 10 · 8 Python 몫)

**실측 HEAD: `PLACEHOLDER_SHA`** · base `c63d0d3a`. 아래 ①~⑥ 은 전부 그 HEAD 의 **버릴
clone** 에서 실제로 돌린 결과다.

### 되돌림 목록

| 상태 | 경로 | 되돌림 |
|---|---|---|
| A | `ml-engine/src/ml_engine/app/backtest_cli.py` | ① restore 가 삭제(base 에 없다) |
| A | `ml-engine/tests/app/test_backtest_cli.py` | ① 같음 |
| A | `ml-engine/tests/app/test_seed_count_pinned.py` | ① 같음 |
| M | `ml-engine/src/ml_engine/evaluation/policy.py` | ① base 로 restore |
| M | `ml-engine/src/ml_engine/evaluation/backtest/policy.py` | ① 같음 |
| M | `ml-engine/tests/app/test_backtest_policy_sensitivity.py` | ① 같음 |
| M | `ml-engine/tests/evaluation/test_evaluation_policy.py` | ① 같음 |
| M | `docs/runbook/m6-6g-real-collection.md` | ② **공유 파일** — 커밋 해시로 hunk 격리 |

### ① 전용 파일 — 한 번에

```
git restore --source=c63d0d3a --staged --worktree -- \
  ml-engine/src/ml_engine/app/backtest_cli.py \
  ml-engine/src/ml_engine/evaluation/backtest/policy.py \
  ml-engine/src/ml_engine/evaluation/policy.py \
  ml-engine/tests/app/test_backtest_cli.py \
  ml-engine/tests/app/test_backtest_policy_sensitivity.py \
  ml-engine/tests/app/test_seed_count_pinned.py \
  ml-engine/tests/evaluation/test_evaluation_policy.py
```

경로는 **개별 인자**로 넘긴다(변수 하나로 묶으면 pathspec 이 하나가 된다).
`git checkout <base> -- <경로>` 는 쓰지 않는다 — base 에 없는 신규 파일에서 pathspec 오류로
아무것도 적용되지 않는다.

### ② 공유 파일(runbook) — Python 몫 hunk 만

`git log --oneline c63d0d3a..<실측 HEAD> -- docs/runbook/m6-6g-real-collection.md` 로 먼저
**그 파일을 만진 커밋을 나열**한다. 실측 시점에는 Python 몫 한 커밋뿐이었다.

```
git diff 2e02b35f~1..2e02b35f -- docs/runbook/m6-6g-real-collection.md | git apply -R
```

**Kotlin 축이 같은 파일의 0 절 범위 상한 행·2-2 를 만지면 이 역적용은 conflict 를 낼 수 있다**
(0 절 표에서 두 행이 **인접**한다 — `--3way` 도 자동 해소에 실패하는 형태다). 그때의 수동
해소 절차 — Python 몫만 base 문면으로 되돌리고 Kotlin 줄은 **남긴다**:

1. 0 절 표의 「백테스트 CLI」 행을 base 문면(`☐ 없음 — 스크립트 호출(2-4) 또는
   `OPEN-6G-BACKTEST-CLI``, 근거 열 `PR #52`)으로 되돌린다. **범위 상한 행은 건드리지 않는다.**
2. 2-4 절 본문을 base 의 heredoc 블록으로 되돌린다(절 제목은 그대로). 내가 넣은 인자 규칙
   bullet 셋(`--snapshot-uri` 변환 · `--output-dir` 강제 · 종료 코드)을 지우고, 산출 bullet 의
   첫 문장을 base 문면(판정 JSON 이 스냅숏 밖이라는 문장)으로 되살린다.
3. 5 절에 「백테스트 job 에 CLI 가 없다」 줄을 되살린다.
4. 확인은 **둘 다** 본다: 내 줄 사라짐(`grep -c backtest_cli` = 0)과 남의 줄 남음
   (Kotlin 축이 고친 범위 상한 행·2-2 문면이 그대로).

### ③~⑥ 실측 (버릴 clone, 실측 HEAD)

| # | 항목 | 결과 |
|---|---|---|
| ① | 전용 파일 restore | exit 0 |
| ② | runbook hunk 역적용 | exit 0 · conflict 0(실측 시점에 Kotlin 축이 그 파일을 만지지 않았다) |
| ③ | `git status --porcelain` 의 D/M 수 · 되돌린 경로의 `git diff c63d0d3a` | **D 3 · M 5** · diff **0 줄** |
| ④ | Python 「compile」 축 — `ruff check .` · `ruff format --check .` · `mypy --strict src/ml_engine` | PLACEHOLDER_4 |
| ⑤ | `python -m pytest tests -q` | PLACEHOLDER_5 |
| ⑥ | 게이트 — `lint-imports` · `tools/design_ratchet.py --check` · `tools/reuse_provenance_check.py`(+양성 대조) | PLACEHOLDER_6 |

**남의 줄 보존**도 같은 트리에서 실측했다: `git status --porcelain -- adapters workflow app
config/quality reports` 가 **빈 출력**(내 복원이 Kotlin 축·evidence 를 건드리지 않았다)이고,
`git diff c63d0d3a --name-only -- adapters workflow app` 는 **15 파일**(Kotlin 축 작업이 그대로
남았다 — 전체 복원이었다면 0 이 된다).

### 되돌리지 않는 것

- `reports/evidence/m6/6g2e/**` — 이 slice 의 기록. Python 축 게이트가 evidence 를 스캔하지
  않으므로 ⑥ 에 영향이 없다(evidence 를 스캔하는 누출 게이트는 Kotlin `check` job 소관).
- `CLAUDE.md`·`.claude/**`(하네스 레인) · `milestone-6.md`(팀장 레인) · Kotlin 축 경로.

### 비활성화 방법(되돌리지 않고 끄기)

- CLI 는 **새 진입점**이라 끌 것이 없다 — 부르지 않으면 호출되지 않는다. runbook 2-4 의
  base 문면(heredoc)으로 돌아가면 그 경로로 job 을 계속 부를 수 있다(판정 경로가 무변경이라
  CLI 를 거치든 거치지 않든 같은 바이트가 나온다).
- seed 개수 고정은 **로더의 거부 하나**다. 끄려면 ① 로 로더 둘을 base 로 되돌린다 — 정책
  파일·판정 경로는 바뀌지 않았으므로 되돌림이 출하 판정에 닿지 않는다(출하 두 파일은
  이미 다섯이다).

## Kotlin 축 (D-6G2e-1~5 · 7 · 8 Kotlin 몫)

**실측 HEAD: `29c6c39d`** · base `c63d0d3a`. 아래 ①~⑥ 은 전부 그 HEAD 의 **버릴 clone**
(`git clone` → `git checkout 29c6c39d`)에서 실제로 돌린 결과다.

### ① 전용 경로 restore

```
git restore --source=c63d0d3a --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/snapshot \
  adapters/src/test/kotlin/bidvector/adapters/snapshot \
  app/src/main/kotlin/bidvector/app/wiring \
  app/src/test/kotlin/bidvector/app/wiring \
  procurement/src/main/kotlin/bidvector/procurement \
  workflow/src/main/kotlin/bidvector/workflow/collection \
  workflow/src/test/kotlin/bidvector/workflow/collection
```

경로는 **개별 인자**다(변수 하나로 묶으면 pathspec 이 하나가 된다). 이 축이 만든 **신규 파일은
0** 이라(전부 `M`) `git checkout <base> --` 의 pathspec 함정은 걸리지 않지만, 같은 형태를 쓰지
않는다 — 다음 라운드가 새 파일을 만들면 그때 조용히 아무것도 되돌리지 않는다.

### 목록이 전수를 덮는가 (등식 확인, 6G-2d 교훈)

```
git diff --name-only c63d0d3a..HEAD | grep -E '\.kt$' | sort            # 전체 Kotlin 소스
git diff --name-only c63d0d3a..HEAD -- <위 일곱 경로> | sort            # 목록이 덮는 것
comm -23 <앞>.txt <뒤>.txt                                              # 전체 ∖ 목록
```

실측: **16 == 16**, `comm -23` **빈 출력**. 라운드마다 다시 낸다(손으로 쓰지 않는다).

### ② 공유 파일 — Kotlin 몫 hunk 만

`git log --oneline c63d0d3a..<실측 HEAD> -- docs/runbook/m6-6g-real-collection.md milestone-6.md`
로 먼저 그 파일들을 만진 커밋을 나열한다. 실측 시점: runbook 은 Kotlin 몫 한 · Python 몫 한,
`milestone-6.md` 는 착수 문단 한(팀장 레인).

```
git diff dea3e6da~1..dea3e6da -- docs/runbook/m6-6g-real-collection.md | git apply -R
git diff 519b9432~1..519b9432 -- milestone-6.md | git apply -R
```

**두 레인의 runbook 역적용은 순서가 있다**(실측): 0 절 표에서 두 행이 **인접**하므로 실측 HEAD
에서 Python 몫 hunk 를 **먼저** 역적용하면 `patch does not apply` 로 선다(`--check` exit 1).
**Kotlin 몫을 먼저** 역적용한 뒤에는 Python 몫이 깨끗하게 선다(`--check` exit 0). 전체 되돌림은
그 순서로 한다. Kotlin 몫만 되돌릴 때의 수동 해소는 필요하지 않았다(exit 0).

확인은 **둘 다** 본다 — 실측:
- 내 줄 사라짐: `grep -c 'D-6G2e-1' docs/runbook/…` = **0**.
- 남의 줄 남음: `grep -c 'backtest_cli' docs/runbook/…` = **3**(Python 몫 2-4·0 절 그대로).
- `milestone-6.md` 는 역적용 뒤 `git diff c63d0d3a -- milestone-6.md` **0 줄**이고 다른 slice
  문단은 남는다(`6G-2d` 5 · `6G-2a` 5 언급). 역적용 뒤 남은 `6G-2e` 언급 둘은 **base 에 이미
  있던 줄**이다(base 에서도 2 — 로드맵의 다음 slice 표기).

### ①~⑥ 실측 (버릴 clone, 실측 HEAD `29c6c39d`)

| # | 항목 | 결과 |
|---|---|---|
| ① | 전용 경로 restore | exit 0 |
| ② | 공유 파일 hunk 역적용(runbook · milestone) | 각 exit 0 · conflict 0 |
| ③ | 되돌린 경로의 `git diff --name-status HEAD` D/M 수 · `git diff c63d0d3a -- <경로>` | **M 16 · D 0** · diff **0 줄**(트리 동일성) |
| ④ | `./gradlew --no-daemon compileKotlin compileTestKotlin` | exit 0 |
| ⑤ | `./gradlew --no-daemon check --no-build-cache` 의 test 축 | exit 0 · **2,580** test · 실패 0 · 건너뜀 4 |
| ⑥ | 같은 `check` 의 게이트 축(ktlint · detekt · sizeGate · domainDependencyGate · architecture · contractGate · gateExecutionGate) | **초록** — 보완 경로 불필요 |

⑤ 의 2,580 은 이 slice 가 더한 test 10 을 뺀 수다(HEAD 2,590). 갈음은 「HEAD 초록」이 아니라
③ 의 **트리 동일성**으로 한다.

### 되돌리지 않는 것

- `reports/evidence/m6/6g2e/**` — 이 slice 의 기록(두 레인이 각자 절을 쓴다). 축별 되돌림에서는
  이 파일의 **내 절만** 지우고, 전체 되돌림에서는 디렉터리째 지운다.
- `CLAUDE.md`·`.claude/**`(하네스 레인) · `ml-engine/**`(Python 축) · `config/quality/**`
  (이 축이 손대지 않았다 — `git diff --name-only … -- config/quality` 빈 출력).

### 비활성화 방법 (되돌리지 않고 끄기)

- **범위 정책(D-1)**: 개찰 갈래를 좁히려면 `OPENING_COLLECTION_RANGE_POLICY` 의 정책 데이터에
  더 짧은 상한을 **새 발효일로** 더한다(31일로 되돌리면 앞 거동과 같아진다). 배선·호출부는
  손대지 않는다. 설정으로 끌 수 있는 값이 아니다 — 그것이 D-1 의 요점이다.
- **조각 되살림(D-3)**: 끌 수 없다(판독의 일부다). 원장 바이트는 무변경이므로 이 축만 base 로
  되돌리면 그 자리가 앞 판 거동(조각을 세기만 함)으로 돌아가고 기존 실행 상태 디렉터리는
  그대로 읽힌다 — 표식 모양과 형식 version 2 가 바뀌지 않았다.
- **A 계수(D-4)**: 계수는 **공시**이고 판정 입력이 아니다(스냅숏 값·스키마 칸 무변경). 되돌려도
  이미 추출한 스냅숏 바이트는 달라지지 않는다.
- **복구 순서·가드(D-5)**: 끌 수 없다(기동 순서다). 되돌리면 앞 판 순서로 돌아가며, 그 사이에
  만들어진 실행 상태 디렉터리는 양쪽 순서에서 모두 읽힌다(장부 칸·형식 version 무변경).
