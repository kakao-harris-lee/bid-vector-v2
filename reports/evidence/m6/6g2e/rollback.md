# M6/6G-2e — rollback

> 레인별로 **자기 축만** 적는다. 되돌림은 range revert 가 아니라 **in_scope 경로 한정**이다 —
> 같은 range 에 다른 레인·하네스 커밋이 섞인다. 목록은 손으로 쓰지 않고
> `git diff --name-status <base>..HEAD` 에서 기계로 낸다. **라운드마다 그 라운드의 마지막
> 산출물 커밋에서 다시 낸다**(앞 라운드 실측을 옮기지 않는다).

## Python 축 (D-6G2e-6 · 6b · 10 · 8 Python 몫)

**실측 HEAD: `c1f8b02f`** · base `c63d0d3a`. 아래 ①~⑥ 은 전부 그 HEAD 의 **버릴
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

### ② 공유 파일(runbook) — 순서가 있다

`git log --oneline c63d0d3a..<실측 HEAD> -- docs/runbook/m6-6g-real-collection.md` 로 먼저
**그 파일을 만진 커밋을 나열**한다 — r2 실측 HEAD 에서는 **셋**이다(Python `2e02b35f` ·
Kotlin `dea3e6da` · Kotlin `a67a9162`). 두 레인의 삽입 지점이 0 절 표에서 **인접**하고
(Kotlin 의 범위 상한 행이 내 「백테스트 CLI」 행의 바로 위 context 다) Kotlin 이 5 절에서
줄 하나를 **지웠다**. 그래서 **순서가 결과를 가른다.** 라운드마다 이 목록을 다시 낸다 —
r1 에서는 커밋이 둘이었다.

**(가) 전 slice 를 되돌릴 때 — 나중 커밋부터, 즉 최신 Kotlin → Kotlin → Python.**

```
git diff a67a9162~1..a67a9162 -- docs/runbook/m6-6g-real-collection.md | git apply -R
git diff dea3e6da~1..dea3e6da -- docs/runbook/m6-6g-real-collection.md | git apply -R
git diff 2e02b35f~1..2e02b35f -- docs/runbook/m6-6g-real-collection.md | git apply -R
```

여섯 명령(`--check` 셋 포함) 전부 **exit 0** 이고, 끝난 runbook 은 base 와 **바이트 동일**
(`git diff c63d0d3a -- <runbook>` 0 줄)이다. 수동 해소가 필요하지 않다 — 이 순서에서는
인접 hunk 가 서로의 context 를 깨지 않는다. 역순(Python 먼저)은 아래 (나)와 같은 이유로 선다.

**(나) Python 축만 되돌릴 때 — 수동 절차가 정본.** Kotlin 줄을 남겨야 하므로 (가)를 쓸 수 없고,
내 hunk 단독 역적용은 **exit 1**(`patch does not apply`)이며 `--3way` 는 conflict marker 를
남긴다(`UU`). 되돌리기 전에 conflict 를 치운다:
`git restore --source=<실측 HEAD> --staged --worktree -- docs/runbook/m6-6g-real-collection.md`
(unmerged 상태에서는 `git checkout --` 이 `path is unmerged` 로 선다 — 실측).

Python 몫만 base 문면으로 되돌리고 Kotlin 줄은 **남긴다**:

1. 0 절 표의 「백테스트 CLI」 행을 base 문면(`☐ 없음 — 스크립트 호출(2-4) 또는
   `OPEN-6G-BACKTEST-CLI``, 근거 열 `PR #52`)으로 되돌린다. **범위 상한 행은 건드리지 않는다.**
2. 2-4 절 본문을 base 의 heredoc 블록으로 되돌린다(절 제목은 그대로). 내가 넣은 인자 규칙
   bullet 셋(`--snapshot-uri` 변환 · `--output-dir` 강제 · 종료 코드)을 지우고, 산출 bullet 의
   첫 문장을 base 문면(판정 JSON 이 스냅숏 밖이라는 문장)으로 되살린다.
3. 5 절에 「백테스트 job 에 CLI 가 없다」 줄을 되살린다 — **`OPEN-6G2D-EMPTY-AXIS-REASON`
   줄 바로 뒤**에 넣는다(base 순서가 그렇다). **base 의 5 절을 통째로 되돌리지 않는다**:
   Kotlin 축이 r2 에서 그 절의 31일 차단 줄을 지웠고(`a67a9162`), 통째 복원은 그 줄을
   되살려 남의 작업을 뒤집는다. 앵커가 base 의 이웃 줄에서 **현재 꼬리 줄**로 바뀐 것은
   그 삭제 때문이다 — 라운드마다 이 앵커를 확인한다.
4. 확인은 **셋** 본다: 내 줄 사라짐(`grep -c backtest_cli` = 0) · 남의 줄 남음(Kotlin 축이
   고친 0 절 두 행·2-2 문면 그대로) · **남의 삭제가 되살아나지 않음**
   (`grep -c OPEN-6G-OPENING-RANGE-CAP` = 0).

### ①~⑥ 실측 (버릴 clone, 실측 HEAD `c1f8b02f`)

| # | 항목 | 결과 |
|---|---|---|
| ① | 전용 파일 restore | exit 0 |
| ②가 | runbook 전 slice 순서(Kotlin 둘 → Python) | `--check`·`apply` **여섯 전부 exit 0** · 끝난 runbook 은 base 와 **바이트 동일**(diff 0 줄) |
| ②나 | runbook Python 단독 | 내 hunk 단독 역적용 **exit 1**(`patch does not apply`) → **수동 절차 실행**. 확인 셋: 내 어휘(`backtest_cli`) **0 건** · Kotlin 줄 **셋 다 남음** · Kotlin 의 5 절 삭제가 **되살아나지 않음**(0 건). 되돌린 runbook 의 base 대비 **3+/4-**(= Kotlin 몫 그대로) |
| ③ | `git status --porcelain` 의 D/M 수 · 되돌린 경로의 `git diff c63d0d3a` | **D 3 · M 5** · diff **0 줄** |
| ④ | Python 「compile」 축 — `ruff check .` · `ruff format --check .` · `mypy --strict src/ml_engine` | 전부 exit 0 · 215 files formatted · **96 source files** 오류 0(CLI 한 파일이 빠져 97→96) |
| ⑤ | `python -m pytest tests -q` | exit 0 · **1,350 passed** — 6G-2a 종결 수 그대로(되돌림이 test 수를 정확히 base 로 되돌렸다) |
| ⑥ | 게이트 — `lint-imports` · `design_ratchet --check` · `reuse_provenance_check`(+양성 대조) · 버전 대조 | 전부 exit 0(Contracts 8 kept/0 broken · 위반 0 · 위반 0 · 양성 대조 정상) |

**남의 줄 보존**도 같은 트리에서 실측했다: `git status --porcelain -- adapters workflow app
config/quality reports` 가 **빈 출력**(내 복원이 Kotlin 축·evidence 를 건드리지 않았다)이고,
`git diff c63d0d3a --name-only -- adapters workflow app` 는 **18 파일**(Kotlin 축 작업이 그대로
남았다 — 전체 복원이었다면 0 이 된다).

### 실측의 유효 범위

`docs/runbook/m6-6g-real-collection.md` 는 **두 레인이 만지는 공유 파일**이다. r1 에서
`dea3e6da`, r2 에서 `a67a9162` 가 그 파일을 바꿨고 그때마다 이 축을 다시 쟀다. r3 에서는 그
파일이 바뀌지 않았지만(만진 커밋 여전히 셋) 산출물 커밋이 하나 늘었으므로 같은 규율대로 다시
쟀다 — **이 축의 세 번째 실측**이다. 술어는 그대로다:

```
git diff --name-only a67a9162..<판정 SHA> -- <위 목록의 경로들>
```

**빈 출력이면 유효하다.** 한 줄이라도 나오면 이 축은 통과가 아니라 **미검증**이고, 그 HEAD
에서 ①~⑥ 을 다시 낸다. 앞 라운드 실측(r2 의 `a67a9162` · r1 의 `8b39b4c4` · 그 전의 `caccd807`)은 옮기지 않고
버렸다 — 이 절의 수는 전부 `c1f8b02f` 에서 다시 낸 것이다. 이 공유 파일이 **라운드마다 이
재실측을 요구한다**는 것이 이 축의 구조적 비용이다(r1·r2 에서 각각 한 번 발생).

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

## Kotlin 축 (D-6G2e-1~5 · 7 · 8 · 15 · 17 · 18 · 23 ③~⑥ Kotlin 몫)

**실측 HEAD: `5fddfd1a`** · base `c63d0d3a`. 아래 ①~⑥ 은 전부 그 HEAD 의 **버릴 clone**
(`git clone` → `git checkout 5fddfd1a`)에서 실제로 돌린 결과다.

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

경로는 **개별 인자**다(변수 하나로 묶으면 pathspec 이 하나가 된다). 이 축은 r2 에서 **신규 파일
셋**을 만들었다(test 파일 분할) — `restore --source` 가 그것을 지운다. `git checkout <base> --` 는
쓰지 않는다: base 에 없는 경로에서 pathspec 오류로 **아무것도** 적용되지 않는다(r1 에는 신규 파일이
없어 그 함정이 드러나지 않았다).

### 목록이 전수를 덮는가 (등식 확인, 6G-2d 교훈)

```
git diff --name-only c63d0d3a..HEAD | grep -E '\.kt$' | sort            # 전체 Kotlin 소스
git diff --name-only c63d0d3a..HEAD -- <위 일곱 경로> | sort            # 목록이 덮는 것
comm -23 <앞>.txt <뒤>.txt                                              # 전체 ∖ 목록
```

실측(r2): **19 == 19**, `comm -23` **빈 출력**. 라운드마다 다시 낸다(손으로 쓰지 않는다).

### ② 공유 파일 — Kotlin 몫 hunk 만

`git log --oneline c63d0d3a..<실측 HEAD> -- docs/runbook/m6-6g-real-collection.md milestone-6.md
config/quality` 로 먼저 그 파일들을 만진 커밋을 나열한다. r2 실측 시점: runbook 은 **Kotlin 몫 둘**
(0 절·2-2 와 5 절) · Python 몫 한, `milestone-6.md` 는 착수 문단 한(팀장 레인),
`config/quality/gate-tests.properties` 는 Kotlin 몫 한(그 커밋은 test 파일도 담으므로 **경로를
한정해** 역적용한다).

```
git diff 5fddfd1a~1..5fddfd1a -- config/quality/gate-tests.properties | git apply -R
git diff a67a9162~1..a67a9162 -- docs/runbook/m6-6g-real-collection.md | git apply -R
git diff dea3e6da~1..dea3e6da -- docs/runbook/m6-6g-real-collection.md | git apply -R
git diff 519b9432~1..519b9432 -- milestone-6.md | git apply -R
```

**순서가 둘 있다.** ① 같은 파일의 내 커밋은 **새 것부터** 역적용한다(`a67a9162` → `dea3e6da`).
② `config/quality` hunk 는 **④⑤⑥ 보다 먼저**다 — 되돌림이 `RunStateRecoveryTest` 를 지우는데
목록에 그 이름이 남아 있으면 `gateExecutionGate` 가 「실행되지 않았다」로 떨어진다(⑥ 이 그 자리에서
붉어지고, 그것은 되돌림이 만든 사고다).

**두 레인의 runbook 역적용도 순서가 있다**(r1·r2 두 라운드 실측): 0 절 표에서 두 행이 **인접**하므로
Python 몫 hunk 를 **먼저** 역적용하면 `patch does not apply` 로 선다. **Kotlin 몫을 먼저** 역적용한
뒤에는 Python 몫이 깨끗하게 선다(r2 실측 `--check` exit 0). 전체 되돌림은 그 순서로 한다.

확인은 **둘 다** 본다 — r2 실측:
- 내 줄 사라짐: `grep -c 'D-6G2e-1' docs/runbook/…` = **0** · `grep -c RunStateRecoveryTest
  config/quality/gate-tests.properties` = **0**.
- 남의 줄 남음: `grep -c 'backtest_cli' docs/runbook/…` = **3**(Python 몫 2-4·0 절 그대로).
- `milestone-6.md` 는 역적용 뒤 `git diff c63d0d3a -- milestone-6.md` **0 줄**이고 다른 slice
  문단은 남는다(`6G-2d` 5 · `6G-2a` 5 언급). 역적용 뒤 남은 `6G-2e` 언급 둘은 **base 에 이미
  있던 줄**이다(base 에서도 2 — 로드맵의 다음 slice 표기).

### ①~⑥ 실측 (버릴 clone, 실측 HEAD `5fddfd1a`)

| # | 항목 | 결과 |
|---|---|---|
| ① | 전용 경로 restore | exit 0 |
| ② | 공유 파일 hunk 역적용(config/quality · runbook 둘 · milestone) | 각 exit 0 · conflict 0 |
| ③ | 되돌린 경로의 `git diff --name-status HEAD` D/M 수 · `git diff c63d0d3a -- <경로>` | **M 16 · D 3**(신규 test 파일 셋) · diff **0 줄**(트리 동일성) |
| ④ | `./gradlew --no-daemon compileKotlin compileTestKotlin` | exit 0 |
| ⑤ | `./gradlew --no-daemon check --no-build-cache` 의 test 축 | exit 0 · **2,580** test · 실패 0 · 건너뜀 4 |
| ⑥ | 같은 `check` 의 게이트 축(ktlint · detekt · sizeGate · domainDependencyGate · architecture · contractGate · gateExecutionGate) | **초록** — 보완 경로 불필요 |

⑤ 의 2,580 은 이 slice 가 더한 test 12 를 뺀 수다(HEAD 2,592)이고 r1 실측과 같은 수다. 갈음은
「HEAD 초록」이 아니라 ③ 의 **트리 동일성**으로 한다.

### 되돌리지 않는 것

- `reports/evidence/m6/6g2e/**` — 이 slice 의 기록(두 레인이 각자 절을 쓴다). 축별 되돌림에서는
  이 파일의 **내 절만** 지우고, 전체 되돌림에서는 디렉터리째 지운다.
- `CLAUDE.md`·`.claude/**`(하네스 레인) · `ml-engine/**`(Python 축).
  `config/quality/gate-tests.properties` 는 **되돌린다**(② 의 첫 줄) — r2 에서 이 축이 이름 하나를
  등재했고, 그것을 남기면 되돌린 트리에서 `gateExecutionGate` 가 없는 클래스를 찾는다.

### 비활성화 방법 (되돌리지 않고 끄기)

- **범위 정책(D-1)**: 개찰 갈래를 좁히려면 `OPENING_COLLECTION_RANGE_POLICY` 의 정책 데이터에
  더 짧은 상한을 **새 발효일로** 더한다(31일로 되돌리면 앞 거동과 같아진다). 배선·호출부는
  손대지 않는다. 설정으로 끌 수 있는 값이 아니다 — 그것이 D-1 의 요점이다.
- **조각 되살림(D-3)**: 끌 수 없다(판독의 일부다). 원장 바이트는 무변경이므로 이 축만 base 로
  되돌리면 그 자리가 앞 판 거동(조각을 세기만 함)으로 돌아가고 기존 실행 상태 디렉터리는
  그대로 읽힌다 — 표식 모양과 형식 version 2 가 바뀌지 않았다.
- **A 계수(D-4)**: 계수는 **공시**이고 판정 입력이 아니다(스냅숏 값·스키마 칸 무변경). 되돌려도
  이미 추출한 스냅숏 바이트는 달라지지 않는다.
- **복구 순서·가드(D-5 · D-17)**: 끌 수 없다(기동 순서다). 되돌리면 앞 판 순서로 돌아가며, 그 사이에
  만들어진 실행 상태 디렉터리는 양쪽 순서에서 모두 읽힌다(장부 칸·형식 version 무변경). 되돌림의
  손해는 「거부될 디렉터리의 원장 바이트가 바뀐다」 하나이고 내용 손실은 없다(조각이 표식 안에 원문
  그대로 남는다).
- **A 결손 계수의 정본(D-15)**: D-4 와 같다 — 계수는 공시이고 `a_value` 값·스키마 칸은 무변경이다.
  되돌려도 이미 추출한 스냅숏 바이트는 달라지지 않는다.
