# M6/6E-1 — rollback 절차와 실측

실측 HEAD: `9634498d` · base: `80dc33b3`

되돌림 대상은 range 가 아니라 **in_scope 경로의 변경**이다. 이 레인이 되돌리는 것은 다섯 — 신설 넷
(runbook 하나 + evidence 셋)과 공유 파일 **하나**(`.github/workflows/ci.yml`)다.

**되돌리지 않는 것을 선언한다.** 같은 브랜치의 `milestone-6.md`·`reports/evidence/m6/6e1/scope.md`
(팀장 레인)·`docs/discovery/legacy-v2-differences.md`(C-6, 팀장)·
`reports/evidence/m6/6e1/ledger-constraint-trace.md`(C-7, 다른 레인)는 **이 레인 소유가 아니다**.
절차는 그 넷을 건드리지 않고 ③ 의 확인이 그것들이 **남아 있음**을 함께 잰다.

**`rollback.md` 자신은 복원 목록에 없다** — 실측 HEAD 에 존재하지 않는 파일이고, 문서가 자기를 담은
커밋을 가리킬 수 없다. 실측 HEAD 를 「마지막 산출물 커밋」(`bf5850b2`)보다 뒤인 `9634498d` 로 잡은 것은
그 자리가 **이 문서 직전의 마지막 커밋**이어서 복원 목록이 evidence 셋을 모두 덮기 때문이다. 이 뒤에
움직이는 경로는 `rollback.md` 하나뿐이고, 그것은 덮개(`reports/evidence/m6/6e1`) 안이라 ⓪ 의 등식이
유지된다.

## ⓪ 복원 목록 — 기계 산출과 양방향 등식

```sh
BASE=80dc33b3
IN="docs/runbook/m6-6e-operations.md .github/workflows/ci.yml \
    reports/evidence/m6/6e1/acceptance-trace.md reports/evidence/m6/6e1/checklist.md \
    reports/evidence/m6/6e1/commands.md"
git diff --name-status $BASE..HEAD -- $IN
git diff --name-only $BASE..HEAD | sort > all.txt
git diff --name-only $BASE..HEAD -- $IN milestone-6.md \
    docs/discovery/legacy-v2-differences.md reports/evidence/m6/6e1 | sort > covered.txt
comm -23 all.txt covered.txt    # 전체 ∖ 덮개 — 빈 출력
comm -13 all.txt covered.txt    # 덮개 ∖ 전체 — 빈 출력
```

산출: `A` **4건**(runbook · `acceptance-trace.md` · `checklist.md` · `commands.md`) ·
`M` **1건**(`ci.yml`). `comm` **양쪽 다 빈 출력**이다.

**재산출 자리는 `9634498d`** 이고 라운드마다 다시 돌린다 — 목록을 문서에 박지 않는다.

## 공유 파일의 hunk 출처 — `git log` 산출, `--no-merges`

```sh
git log --no-merges --format=%h $BASE..HEAD -- .github/workflows/ci.yml
```

산출은 **한 건**(`2df1da3b`)이다. `--no-merges` 가 필요한 이유: range 에 `main` 병합 커밋이 들어오면
`git diff <sha>~1..<sha>` 가 그 커밋에서 엉뚱한 범위를 낸다(6F-10 교훈).

## 되돌림 절차

1. **신설 4건 제거** — base 에 없던 경로라 `git restore --source` 의 대상이 아니다.

```sh
git rm -f -- docs/runbook/m6-6e-operations.md \
  reports/evidence/m6/6e1/acceptance-trace.md \
  reports/evidence/m6/6e1/checklist.md \
  reports/evidence/m6/6e1/commands.md
```

2. **공유 파일 `ci.yml` — 커밋 해시 hunk 격리.** 파일 전체 복원을 쓰지 않는다(다른 slice 의 줄이
   함께 사라진다).

```sh
git diff 2df1da3b~1..2df1da3b -- .github/workflows/ci.yml | git apply -R
```

**`--3way` 도 자동 해소에 실패할 수 있으므로 수동 절차를 미리 적는다.** 위 역적용이 거부되면
(`error: patch failed`), 그 뒤에 `ci.yml` 의 그 자리를 다른 커밋이 건드린 것이다 — 그때는 ① `git log
--no-merges -p -- .github/workflows/ci.yml` 로 `2df1da3b` 이후의 커밋을 찾고 ② G-4 블록의 경계 두 줄
(`# M6/6E-1 G-4 —` 로 시작하는 주석 줄과 `dry-run 왕복 통과 —` 를 내는 `echo` 줄)을 눈으로 찾아 그
사이 **55 줄을 손으로 지운다** ③ 지운 뒤 아래 ③ 의 blob SHA 대조로 갈음한다. 줄 수는 `git diff
--numstat 2df1da3b~1..2df1da3b -- .github/workflows/ci.yml` 가 낸 값(`55 0`)이다.

3. **확인은 둘 다** — 「내 줄 사라짐」과 「남의 줄 남음」.

```sh
git add -A -- .github/workflows/ci.yml
git diff --cached --name-only 80dc33b3 -- <IN 다섯>         # 빈 출력
git rev-parse 80dc33b3:.github/workflows/ci.yml             # base blob
git hash-object .github/workflows/ci.yml                    # 복원 blob — 위와 같아야 한다
ls docs/discovery/legacy-v2-differences.md reports/evidence/m6/6e1/scope.md \
   reports/evidence/m6/6e1/ledger-constraint-trace.md milestone-6.md   # 넷 다 남아 있어야 한다
```

## 실측 — 버릴 clone 에서 (①~⑥)

```sh
git clone -q --no-hardlinks <이 worktree> <임시 경로> && cd <임시 경로> && git checkout -q 9634498d
```

| # | 무엇 | 결과 |
|---|---|---|
| **①** | 신설 4건 제거 명령 | **exit 0** · 삭제 **4** |
| **②** | `ci.yml` hunk 역적용(`2df1da3b` 격리) | **exit 0** — 거부 없음, 수동 경로 불필요 |
| **③** | base 대조 · **트리 동일성** · 남의 줄 | **빈 출력** · base blob `279b64f3` == 복원 blob `279b64f3` · 신설 넷 전부 **없음** · 다른 레인 산출물 넷 전부 **남음** |
| **④** | compile(`compileKotlin compileTestKotlin`) | **exit 0** (16s) |
| **⑤** | test | **exit 0** — 클래스 **350** · test **2817** · 실패 0 · 오류 0 · skip 4 |
| **⑥** | 게이트(`check` · `qualityBaseline`) | **exit 0** (`check` 9m 23s · `qualityBaseline` 별도 exit 0 · `leakPatternGate` 실행 1회) |

**갈음은 「HEAD 초록」이 아니라 트리 동일성으로 했고, 그 위에서 ④~⑥ 을 실제로 돌렸다.** ③ 의 blob
SHA 대조가 되돌린 `ci.yml` 이 base 와 **바이트 동일**함을 보이고, ④~⑥ 은 그 트리에서 **직접 실행**한
결과다 — 예측으로 대신하지 않았다.

되돌린 트리의 test 수는 되돌리지 않은 트리와 **같다**(350 클래스 · 2817 test). 이 slice 가 더한 것이
문서 넷과 CI step 하나뿐이고 Kotlin 소스·build 파일·`config/quality` diff 가 **0** 이기 때문이다
(`checklist.md` 자기 점검). 그래서 ④~⑥ 의 유일한 위험원은 `ci.yml` 이었고, 그 파일의 되돌림은 ③ 에서
바이트 동일로 확인됐다.

**실측 자리**: 버릴 clone 을 새로 떠서(`--no-hardlinks`) `9634498d` 를 체크아웃하고 ①~③ 을 다시 적용한
뒤 그 트리에서 ④~⑥ 을 돌렸다 — 앞 라운드의 clone 을 재사용하지 않았다.
