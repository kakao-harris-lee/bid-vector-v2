# M6/6E-1 — rollback 절차와 실측

실측 HEAD: `24bdc8bd` · base: `80dc33b3` · **수정 라운드 1** 반영.

되돌림 대상은 range 가 아니라 **in_scope 경로의 변경**이다. 여덟이다 — **신설 여섯**(runbook · C-1 · C-6 ·
C-7 · evidence 둘)과 **공유 파일 둘**(`.github/workflows/ci.yml` · `milestone-6.md`).

**r1 이 넓힌 것**(verifier r1 R1-L-1): 앞 판은 C-6(`docs/discovery/legacy-v2-differences.md`)·C-7
(`reports/evidence/m6/6e1/ledger-constraint-trace.md`)·`milestone-6.md` 를 「되돌리지 않음」으로 선언만 하고
절차를 두지 않았다. 그래서 ⓪ 의 `covered` 집합이 그 셋에 대해 **동어반복으로** 닫혔고 slice 단위 되돌림이
산출물 둘을 빠뜨렸다. 셋을 전부 대상으로 올렸다 — **커밋 주체가 팀장·다른 레인이어도 되돌림 절차는 레인
문서가 든다.**

**대상에서 빠지는 둘을 선언한다.**
- `reports/evidence/m6/6e1/scope.md` — 팀장 소유 **계약 문서**다. 되돌림은 산출물을 base 로 되돌리는 일이고
  계약은 그 되돌림을 지시하는 자리라, 계약을 함께 지우면 되돌림의 근거가 사라진다.
- `reports/evidence/m6/6e1/rollback.md`(이 문서) — 자기를 지우는 목록을 들면 되돌림 **대상이 라운드마다
  움직여** 「실측 뒤 대상이 움직였는가」 확인이 영원히 깨진다.

**실측 HEAD 를 `24bdc8bd` 로 잡은 근거**: 그 자리가 **이 문서 직전의 마지막 커밋**이고 산출물·공유 파일의
변경은 그보다 앞에서 멈춘다(runbook `57c571a7` · C-1 `427532a7` · `ci.yml` `1dacc5d7` · C-6·C-7 `b0df65c6` ·
evidence 둘 `24bdc8bd`). 그 뒤에 움직이는 경로는 `rollback.md` 하나뿐이고 대상이 아니므로
`git diff --name-only 24bdc8bd..<판정 SHA> -- <대상 여덟>` 이 **빈 출력**이다 — 이것이 verifier 가 보는
등식이다(「실측 HEAD == 판정 SHA」가 아니다).

## ⓪ 복원 목록 — 기계 산출과 양방향 등식

```sh
BASE=80dc33b3
IN="docs/runbook/m6-6e-operations.md docs/discovery/legacy-v2-differences.md \
    .github/workflows/ci.yml milestone-6.md \
    reports/evidence/m6/6e1/acceptance-trace.md reports/evidence/m6/6e1/checklist.md \
    reports/evidence/m6/6e1/commands.md reports/evidence/m6/6e1/ledger-constraint-trace.md"
git diff --name-status $BASE..HEAD -- $IN
git diff --name-only $BASE..HEAD | sort > all.txt
git diff --name-only $BASE..HEAD -- $IN reports/evidence/m6/6e1 | sort > covered.txt
comm -23 all.txt covered.txt    # 전체 ∖ 덮개 — 빈 출력
comm -13 all.txt covered.txt    # 덮개 ∖ 전체 — 빈 출력
```

산출: `A` **6건** · `M` **2건**. `comm` **양쪽 다 빈 출력**이다. `covered` 가 `IN` 에 더하는 것은
`reports/evidence/m6/6e1` 하나이고 그 안의 비대상은 위에 선언한 둘(`scope.md`·`rollback.md`)뿐이다 —
**그 둘 말고는 덮개가 대상과 같다**(앞 판처럼 「되돌리지 않음」 선언으로 셋을 더 덮지 않는다).

**재산출 자리는 `24bdc8bd`** 이고 라운드마다 다시 돌린다 — 목록을 문서에 박지 않는다.

## 공유 파일의 hunk 출처 — `git log` 산출, `--no-merges`

```sh
for f in .github/workflows/ci.yml milestone-6.md; do
  echo "-- $f"; git log --no-merges --format=%h $BASE..HEAD -- "$f"
done
```

산출 — `ci.yml` **둘**(`1dacc5d7` · `2df1da3b`) · `milestone-6.md` **하나**(`89a0d8fc`).
`--no-merges` 가 필요한 이유: range 에 `main` 병합 커밋이 들어오면 `git diff <sha>~1..<sha>` 가 그
커밋에서 엉뚱한 범위를 낸다(6F-10 교훈).

## 되돌림 절차

1. **신설 6건 제거** — base 에 없던 경로라 `git restore --source` 의 대상이 아니다.

```sh
git rm -f -- docs/runbook/m6-6e-operations.md docs/discovery/legacy-v2-differences.md \
  reports/evidence/m6/6e1/acceptance-trace.md reports/evidence/m6/6e1/checklist.md \
  reports/evidence/m6/6e1/commands.md reports/evidence/m6/6e1/ledger-constraint-trace.md
```

2. **공유 파일 둘 — 커밋 해시 hunk 격리.** 파일 전체 복원을 쓰지 않는다(다른 slice 의 줄이 함께 사라진다).
   `ci.yml` 은 커밋이 둘이므로 **새것부터 역순으로** 역적용한다 — 반대로 하면 뒤 커밋이 앞 커밋의 줄을
   고친 자리에서 패치가 거부된다.

```sh
git diff 1dacc5d7~1..1dacc5d7 -- .github/workflows/ci.yml | git apply -R   # r1 수정분
git diff 2df1da3b~1..2df1da3b -- .github/workflows/ci.yml | git apply -R   # G-4 블록 신설분
git diff 89a0d8fc~1..89a0d8fc -- milestone-6.md          | git apply -R   # 6E 분할·착수 문단
```

**`--3way` 도 자동 해소에 실패할 수 있으므로 수동 절차를 미리 적는다.** 역적용이 거부되면
(`error: patch failed`) 그 뒤에 같은 자리를 다른 커밋이 건드린 것이다 — 그때는 ① `git log --no-merges -p`
로 그 파일의 이후 커밋을 찾고 ② 경계를 눈으로 잡아 손으로 지운다. `ci.yml` 의 G-4 블록 경계는
`# M6/6E-1 G-4 —` 로 시작하는 주석 줄과 `dry-run 왕복 통과 —` 를 내는 `echo` 줄이고, `milestone-6.md` 의
경계는 「Slice 6E」 절의 6E-1 착수 문단이다. ③ 지운 뒤 아래 ③ 의 blob SHA 대조로 갈음한다. 줄 수는
`git diff --numstat <sha>~1..<sha> -- <파일>` 가 낸다.

3. **확인은 둘 다** — 「내 줄 사라짐」과 「남의 줄 남음」.

```sh
git add -A -- .github/workflows/ci.yml milestone-6.md
git diff --cached --name-only 80dc33b3 -- $IN                 # 빈 출력
git rev-parse 80dc33b3:.github/workflows/ci.yml; git hash-object .github/workflows/ci.yml   # 같아야 한다
git rev-parse 80dc33b3:milestone-6.md;          git hash-object milestone-6.md              # 같아야 한다
ls reports/evidence/m6/6e1/                                   # scope.md · rollback.md 둘만 남아야 한다
```

## 실측 — 버릴 clone 에서 (①~⑥)

```sh
git clone -q --no-hardlinks <이 worktree> <임시 경로> && cd <임시 경로> && git checkout -q 24bdc8bd
```

| # | 무엇 | 결과 |
|---|---|---|
| **⓪** | 복원 목록 기계 산출 · `comm` 양방향 | **`A` 6 · `M` 2** · 양쪽 다 빈 출력 |
| **①** | 신설 6건 제거 명령 | **exit 0** · 삭제 **6** |
| **②** | hunk 역적용 셋(`ci.yml` 둘 역순 · `milestone-6.md` 하나) | **전부 exit 0** — 거부 없음, 수동 경로 불필요 |
| **③** | base 대조 · **트리 동일성** 둘 · 남의 줄 | **빈 출력** · `ci.yml` base `279b64f3` == 복원 `279b64f3` · `milestone-6.md` base `fc163048` == 복원 `fc163048` · 되돌린 evidence 디렉터리에 `scope.md`·`rollback.md` 둘만 남음 |
| **④** | compile(`compileKotlin compileTestKotlin`) | **exit 0** |
| **⑤** | test | **exit 0** — 클래스 **350** · test **2817** · 실패 0 · 오류 0 · skip 4(되돌리지 않은 트리와 같은 수) |
| **⑥** | 게이트(`check` · `qualityBaseline`) | **exit 0** (`check` 9m 24s · 359 task 중 209 executed · `leakPatternGate`·`qualityBaseline` 둘 다 **실행** — 되돌린 뒤 남는 `rollback.md`·`scope.md` 를 스캔하고도 초록 · `qualityBaseline` 단독도 exit 0) |

**갈음을 쓰지 않았다** — r0 에서도 ④~⑥ 이 exit 0 이었지만 r1 의 되돌림 목록이 둘(C-6·C-7) 늘고 공유
파일이 하나(`milestone-6.md`) 늘어 되돌린 트리가 r0 의 그것과 다르다. 그래서 **새 clone 에서 ①~⑥ 을
다시 돌렸다**(사용자 결정 `D-6E1-12` 로 swap 문턱이 1.0 GB 로 내려간 뒤, 실측 시점 swap free 5.0 GB).
「HEAD 초록」으로도 「앞 라운드 초록」으로도 갈음하지 않았다.

**③ 과 ⑤ 가 함께 말하는 것**: 두 공유 파일이 base 와 **바이트 동일**하고 제거 대상 여섯이 전부 문서다 —
Kotlin·Python 소스·build 파일·`config/quality` diff **0**(`checklist.md` 자기 점검). 그 트리의 test 수가
되돌리지 않은 트리와 **같다**(350 클래스 · 2817 test)는 것이 그 사실의 확인이다.

**실측 자리**: 버릴 clone 을 새로 떠서(`--no-hardlinks`) `24bdc8bd` 를 체크아웃하고 ①~③ 을 적용한 뒤 그
트리에서 ④~⑥ 을 돌렸다 — 앞 라운드의 clone 을 재사용하지 않았다.
