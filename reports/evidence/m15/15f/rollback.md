# M15 / 15f — 되돌리기

**range revert 가 아니다** — in_scope 경로 한정 복원이다. 같은 range 에 다른 레인의 커밋이 섞일 수
있고, 그때 range revert 는 남의 것을 함께 지운다.

- **복원 원천**: `5a2f98ba7402971facacdabbe98f8782b5268ab0`(착수 base · 현재 `main` 의 머리였다).
- **실측 HEAD**: `7f85497ecdc40a06172488a2ff598219134c5e7a`(이 slice 의 **마지막 산출물 커밋** —
  뒤따르는 커밋은 `reports/evidence/m15/15f/` 전용이고 복원 목록 밖이다).

## 명령

```
git restore --source=5a2f98ba7402971facacdabbe98f8782b5268ab0 --staged --worktree -- <아래 17 경로>
```

## 복원 목록 — **기계 산출**이다

```
git diff --name-status 5a2f98ba7402971facacdabbe98f8782b5268ab0..<실측 HEAD> -- . ':!reports/evidence/m15/15f'
```

**17 경로 · A 0 · M 17 · D 0.** 모두 base 에 이미 있던 파일이라 복원은 전부 「되돌림」이고
「삭제」가 없다. 이 slice 가 만든 **신설 파일은 `reports/evidence/m15/15f/` 셋뿐**이고, 그 셋은
복원 목록에서 제외한다 — 되돌린 뒤에도 **무엇을 왜 되돌렸는지가 남아야** 한다.

목록과 slice 계약의 **집합 등식**도 기계로 쟀다: `scope.md` 의 in_scope(15f evidence 제외) 17 ≡ 위
변경 17, `comm -23` · `comm -13` **양쪽 빈 출력**.

| # | 경로 |
| --- | --- |
| 1 | `app/src/main/kotlin/bidvector/app/evaluation/EvaluationCommitRunner.kt` |
| 2 | `app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRotationTest.kt` |
| 3 | `app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRunnerFixtures.kt` |
| 4 | `app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRunnerTest.kt` |
| 5 | `docs/discovery/capability-map.md` |
| 6 | `docs/runbook/m6-6e-operations.md` |
| 7 | `reports/evidence/m15/15b/commands.md` |
| 8 | `reports/evidence/m15/15c/commands.md` |
| 9 | `reports/evidence/m15/15c/rollback.md` |
| 10 | `reports/evidence/m15/15c/scope.md` |
| 11 | `web/e2e/journey/onboarding.spec.ts` |
| 12 | `web/e2e/journey/privateValues.ts` |
| 13 | `web/e2e/journey/settings.spec.ts` |
| 14 | `web/src/display/profileDisplay.test.ts` |
| 15 | `web/src/display/profileDisplay.ts` |
| 16 | `web/src/screens/ProfileDetailsCard.tsx` |
| 17 | `web/src/screens/SettingsScreen.test.tsx` |

## 실측 — 버릴 clone 에서 ①~⑥

공유 트리는 체크아웃도 수정도 하지 않았다. `git clone --no-checkout` 으로 뜬 clone 에서 실측
HEAD 를 체크아웃하고(`git status --porcelain` 빈 출력 확인) 위 명령을 돌렸다. 끝나고 지웠다.

| # | 축 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **0** |
| ② | 복원 뒤 상태 계수 | **M 17** · A 0 · D 0 (목록과 일치) |
| ③ | 복원 경로의 **원천 대비** diff | **빈 출력**(0 줄) |
| ③b | **전 트리** 원천 대비 diff(`':!reports/evidence/m15/15f'`) | **빈 출력**(0 줄) — 되돌린 트리가 내 evidence 밖에서 원천과 **같은 트리**다 |
| ③c | **내 표지 0** | 이 slice 전용 어휘 여섯(`clearBusinessRegistrationNumber` · `PRIVATE_VALUE_PROBLEM_PLANTED` · 새 test 이름 둘 · runbook 의 인터럽트 처방 문장 · 새 `stopCauseFor` 시그니처)이 **전부 0 파일** |
| ③d | **남의 표지 보존** | 15A `SignUpRequest` **4 파일** · 15C `BusinessRegistrationNumber` **13 파일** · V30 마이그레이션 **2 파일** · 15B `STOP_CAUSE_INTERRUPTED` **2 파일**(넷 다 `reports/evidence/m15/15f/` 를 제외한 계수다 — 남아 있는 내 evidence 가 그 어휘를 담아 계수를 부풀린다) |
| ④⑤⑥ | 컴파일 · test · 게이트 | **트리 동일성으로 갈음한다**(아래) |

**③c 의 표지는 상류 어휘 밖에서 골랐다** — 처음 쓴 `cause=interrupted` 는 15B 가 이미 쓰는 값이라
복원 뒤에도 3 파일이 나왔다(미등재가 아니라 **상류의 것**이다). 그 어휘를 버리고 이 slice 가
처음 만든 이름·문장으로 바꿔 다시 셌다. 같은 이유로 `aria-live="polite"` 도 쓰지 않았다(web 의
다른 자리가 이미 쓴다 — 복원 뒤 4 파일).

## ④⑤⑥ 갈음 — 「HEAD 초록」이 아니라 **트리 동일성**이다

되돌린 트리는 ③b 로 원천 `5a2f98ba7` 과 **같은 트리**다(내 evidence 디렉터리 밖에서 0 줄 차이).
그리고 `git diff 5a2f98ba7 e2e61a57c` 가 **0 줄**이다 — 원천은 PR #189 의 머지 커밋이고 그 트리는
PR head `e2e61a57` 와 **바이트로 같다**. 그 SHA 의 CI(run `44985593752`)는 다섯 job
(`check` · `web` · `container` · `ml-engine` · `mutation`) 전부 success 다.

즉 되돌린 트리에서 돌 컴파일·test·게이트는 **이미 초록으로 측정된 트리의 것**이고, 갈음의 근거는
그 실행 결과가 아니라 **두 트리가 같다는 등식**이다. 이 slice 의 evidence 셋은 어느 게이트의
입력도 아니므로(`leakPatternGate` 는 그 디렉터리를 스캔하되 이 파일들에서 매치 0 이다) 남아 있어도
판정을 바꾸지 않는다.

## 이 실측이 유효한가 — verifier 가 재는 술어

```
git diff --name-only 7f85497ec..<판정 SHA> -- <위 복원 목록의 17 경로>
git rev-list --merges 7f85497ec..<판정 SHA>
```

둘 다 **빈 출력**이면 유효하다. 실측 HEAD 뒤의 커밋은 `reports/evidence/m15/15f/` 전용이고 복원
목록은 그 디렉터리를 제외하므로 첫 술어가 빈 출력으로 선다. 그 뒤에 병합도 없다.

**셋째 단언은 재지 않아도 된다** — 복원 원천을 바꾸지 않았다(착수 base 그대로다).

## 라운드마다 다시 한다

수정 라운드가 파일을 늘리거나 main 을 다시 병합하면 위 재산출 명령을 **그 라운드의 마지막 산출물
커밋에서** 다시 돌리고 원천·실측 HEAD·목록·갈음 근거를 함께 갱신한다. 앞 라운드의 실측을 옮기지
않는다.
