# M6/6E-2c — rollback

실측 HEAD: `c4656329` — 이 slice 의 **마지막 산출물 커밋**이자 팀장이 고정한 판정 SHA 다. 목록도 결과도
그 HEAD 에서 다시 냈다(앞 라운드 실측을 옮기지 않는다).

**전제 — 이 slice 는 아직 main 에 없다.** 머지 뒤의 되돌림은 그 PR 의 머지 커밋 revert 다. 아래 복원은
머지 순간 **아무것도 지우지 않으면서 exit 0** 을 내므로 의미가 뒤집힌다.

되돌림은 **range revert 가 아니라 in_scope 경로 한정**이다 — `git revert e2232a75..HEAD` 는 같은 range 의
팀장 레인 커밋(착수 계약 `scope.md`)까지 되돌린다.

## 복원 원천 — 고정 SHA

`PIN=e2232a75`. 이 브랜치의 base 이면서 **마지막으로 병합한 main 커밋**이다 — 이 브랜치에는 병합 커밋이
**하나도 없다**(`git log --merges e2232a75..HEAD` 빈 출력). 움직이는 `origin/main` 을 쓰지 않는다.

실행 전 단언 둘 —

```
PIN=e2232a75; FIRST=b5d8bdfc
git merge-base --is-ancestor "$PIN" HEAD      || exit 1   # 핀이 HEAD 의 조상
git merge-base --is-ancestor "$FIRST" "$PIN"  && exit 1   # 핀이 이 slice 를 담지 않는다
```

실측에서 둘 다 통과했다.

**hunk 격리가 필요 없다 — 그리고 그 판단에는 전환 조건이 있다.** 병합이 없으므로 `base..HEAD` 의 모든
줄이 이 slice 의 것이고, 공유 파일(`docs/runbook/m6-6e-operations.md`·`config/quality/gate-tests.properties`
·두 위생 정책·`vuln-*`·카탈로그)을 base 에서 통째로 복원하면 다른 slice 의 줄은 **base 가 이미 담고 있어
구조적으로 보존**된다. **이 브랜치가 main 을 흡수하는 순간 이 문장은 거짓이 된다** — 그때는 hunk 절차로
옮기고 「내 표지 0 · 남의 표지 보존」을 다시 센다.

## 되돌리는 것 / 되돌리지 않는 것

목록은 손으로 쓰지 않는다 — `git diff --name-status e2232a75..c4656329` 에서 기계로 낸다(A = 삭제,
M = base 로 복원). **라운드마다 파일이 늘면 이 산출을 다시 돌린다.**

| 상태 | 경로 |
|---|---|
| A (1) | `app/src/test/kotlin/bidvector/app/packaging/BootJarSecurityFloorTest.kt` |
| M (11) | `app/build.gradle.kts` · `config/quality/gate-tests.properties` · `config/quality/image-hygiene-policy-app.properties` · `config/quality/image-hygiene-policy.properties` · `config/quality/vuln-allowlist.properties` · `config/quality/vuln-policy.properties` · `docker/app.Dockerfile` · `docker/ml-serving.Dockerfile` · `docs/runbook/m6-6e-operations.md` · `gradle/libs.versions.toml` · `tools/vuln-scan-check.sh` |

등식 확인: 목록 ∖ `git diff --name-only e2232a75..c4656329`(evidence 제외) = **빈 출력**.

**되돌리지 않는 것** — `reports/evidence/m6/6e2c/**`(이 slice 의 증적, 팀장이 쓴 `scope.md` 포함) ·
하네스 경로(`CLAUDE.md`·`.claude/**`, 이 range 에 커밋 **0**) · `milestone-6.md`(이 slice 가 만지지 않았다).

## 명령

```
PIN=e2232a75
git restore --source="$PIN" --staged --worktree -- \
  app/build.gradle.kts config/quality/gate-tests.properties \
  config/quality/image-hygiene-policy-app.properties config/quality/image-hygiene-policy.properties \
  config/quality/vuln-allowlist.properties config/quality/vuln-policy.properties \
  docker/app.Dockerfile docker/ml-serving.Dockerfile \
  docs/runbook/m6-6e-operations.md gradle/libs.versions.toml tools/vuln-scan-check.sh
git rm --cached -- app/src/test/kotlin/bidvector/app/packaging/BootJarSecurityFloorTest.kt \
  && rm -f app/src/test/kotlin/bidvector/app/packaging/BootJarSecurityFloorTest.kt
```

**소스를 되돌려도 이미지는 되돌아가지 않는다.** 베이스 상향이 Dockerfile 에 있으므로 로컬 이미지는 새
베이스로 남고, 배포물(`app/build/libs/app.jar`)도 상향된 의존으로 남는다. 되돌린 뒤 **`:app:bootJar` →
`docker build` 둘을 다시 돌린다.** 이 함정은 이 slice 의 변이 라운드에서 실제로 겪었다(복원한 소스 위에서
옛 배포물이 그대로 이미지에 들어갔다).

## 임시 clone 실측 ①~⑥ (실측 HEAD `c4656329`)

| 축 | 결과 |
|---|---|
| 실행 전 단언 둘 | 핀이 HEAD 의 조상 **OK** · 핀이 이 slice 의 첫 산출물 커밋 `b5d8bdfc` 를 담지 않음 **OK** |
| ① 명령 exit | `restore`(M 11개) **0** · `git rm` + `rm`(A 1개) **0** |
| ② D/M 수 · 등식 | A **1** · M **11** · 등식(목록 ∖ `base..HEAD` 변경, evidence 제외) **빈 출력** |
| ③ diff 빈 것 · 계수 | 되돌림 대상 `git diff` **0 바이트** · base 대비 **blob 동일 11/11** · A 파일 삭제 확인 · 이 slice 표지 `D-6E2C` **0** · 다른 slice 표지 보존(runbook `6E-2b` 4 · gate-tests `6G-2g` 1) · `reports/evidence/m6/6e2c/` 보존 |
| ④ compile · ⑤ test · ⑥ 게이트 | **`check` 한 번으로 전부 exit 0 — tests 2838 · 실패 0 · skipped 4 · 9m36s** |

**④~⑥ 은 앞 HEAD 에서 실측하고 트리 동일성으로 갈음했다.** 승인 전 일괄이 붙은 뒤 되돌린 트리를 다시
만들어, 앞서 `check` 를 완주시킨(2838 tests, 실패 0) 되돌린 트리와 대조했다 —
`diff -rq --exclude=.git --exclude=build` 의 차이가 **`reports/evidence/m6/6e2c/` 안에만** 있고
**산출물 경로는 blob 동일 11/11 + 신규 1 삭제**다. 산출물이 같으므로 ④ compile·⑤ test 는 그대로 옮겨진다.

**⑥ 은 한 칸 더 본다.** 되돌림이 evidence 를 복원하지 않으므로 ⑥ 의 게이트 가운데 **그 차이를 실제로 읽는
것이 하나 있다** — `leakPatternGate`(scanRoot = `reports/evidence`). 그래서 그 자리는 갈음하지 않고
되돌린 트리에서 직접 쟀다: 이 slice 의 evidence 에 대한 참조형 스캔 **매치 0**(그 경로는 baseline 에도
0건이라 새 매치가 곧 실패다). 나머지 게이트는 산출물만 읽으므로 트리 동일성으로 닫힌다.

갈음의 근거는 「HEAD 가 초록이다」가 아니라 **트리 동일성 + 차이를 읽는 게이트의 직접 측정**이다.

## verifier 가 대조할 것

「실측 HEAD == 판정 SHA」가 아니다(evidence 커밋은 언제나 뒤에 온다). **그 사이에 되돌림 대상이
움직였는가**를 본다 —

```
git diff --name-only c4656329..<판정 SHA> -- <위 표의 A·M 경로 전부>
```

빈 출력이면 이 실측이 유효하다. 한 줄이라도 나오면 미검증이다.
