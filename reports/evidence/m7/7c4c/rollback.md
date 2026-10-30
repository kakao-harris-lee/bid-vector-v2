# M7/7C④-3 되돌리기 — 시작하기 체크리스트 operation

**실측 HEAD: `96aa9f10`**(main `ea91d753` 병합 커밋). 산출물 커밋의 마지막은 `708c9f60` 이고 그 뒤는
병합과 evidence 뿐이라, 되돌림 대상 경로는 이 HEAD 와 판정 SHA 사이에서 움직이지 않는다.

## 복원 기준 — base 가 아니라 **병합된 main 고정 SHA**

base 는 `fa5ca096` 지만 **복원은 `ea91d753`**(이 브랜치에 마지막으로 병합한 main, PR #105 7E-2)에서
한다. `fa5ca096` 로 되돌리면 그 사이 머지분(7C②-b · 7E-1 · 7E-2)까지 걷힌다 — 아래 공유 파일 여섯에
다른 slice 의 줄이 있다.

**전제 둘을 실행 전에 단언한다**(둘 다 실측):
- `git merge-base --is-ancestor ea91d753 HEAD` → **exit 0**(HEAD 의 조상이다).
- `git merge-base --is-ancestor 66530bbe ea91d753` → **exit 1**(그 SHA 에 이 slice 의 **첫 산출물
  커밋이 없다**). 그래서 main 에서 만든 트리이고 남의 줄을 떨어뜨릴 수 없다 — 커밋 해시 목록이 필요
  없고 hunk 역적용도 쓰지 않는다.

**전제**: 이 slice 는 아직 main 에 없다. **머지 뒤의 되돌림은 이 절차가 아니라 그 PR 의 머지 커밋
revert** 다 — 머지 순간 같은 복원은 아무것도 지우지 않으면서 exit 0 을 낸다.

## 목록 — 기계 산출

`git diff --name-status ea91d753..HEAD` 에서 in_scope 경로만 취한다. **라운드마다 재산출한다** —
목록이 낡는 것이 이 결함의 실제 원인이다.

- **M(복원) 12** — `AccountScopedRoutes.kt` · `ProfileWiring.kt` · `AccountCarrierStructureTest.kt` ·
  `ConstantTimeComparisonStructureTest.kt` · `OpenApiContractTest.kt` · `ProfileTestWiring.kt` ·
  `architecture-policy.properties` · `gate-tests.properties` · `capability-map.md` ·
  `m7-screen-data-requirements.md` · `bidvector-operator-api.yaml` · `operator-api.ts`
- **A(삭제) 7** — `OnboardingChecklistController.kt` · `OnboardingChecklistResponses.kt` ·
  `OnboardingChecklistEndpointTest.kt` · `OpenApiFlatnessControlTest.kt` ·
  `OpenApiOnboardingChecklistContractTest.kt` · `OnboardingChecklistUseCase.kt` ·
  `ReadOnboardingChecklistUseCaseTest.kt`

**되돌리지 않는 것**: `reports/evidence/m7/7c4c/**`(이 slice 의 기록) · 하네스 경로(`CLAUDE.md` ·
`.claude/**` — 이 range 에 변경 0).

## 명령

```
git restore --source=ea91d753 --staged --worktree -- <M 12 경로를 개별 인자로>
git rm -f <A 7 경로를 개별 인자로>
```

신규 파일은 `ea91d753` 에 없으므로 `restore` 가 아니라 `rm` 이 지운다(한 명령에 섞지 않는다 —
`restore` 는 source 에 없는 경로를 pathspec 오류로 거부한다).

## 실측 — 버릴 clone(`--no-hardlinks`)에서 ①~⑥

| 단계 | 결과 |
| --- | --- |
| ① 전제 둘 | 조상 exit 0 · 첫 산출물 미포함 exit 1 |
| ② 명령 | `restore` **exit 0** · `rm` **exit 0** |
| ③ 계수 | HEAD 대비 **D 7 · M 12**(= 산출물 19). `ea91d753` 대비 in_scope **잔여 diff 0 줄** |
| ③-b 남의 줄 보존 | 7C②-b 의 표지가 남는다 — 계약 문서의 근거 상태 어휘 **4**, 정책 목록의 운반 타입 **2** |
| ③-c 내 표지 0 | `AwaitingItemResponse` 0 · `onboarding-checklist` 0 · `DEPTH_ONE_OBJECT_KEYS` 0 |
| ④ compile · ⑤ test · ⑥ 게이트 | **전건 `./gradlew --no-daemon check` BUILD SUCCESSFUL 27m 11s · FAILED 0** |

**완료 판정은 종료 코드가 아니라 계수다.** ③-b 와 ③-c 가 「내 표지 0 · 남의 표지 보존」을 둘 다 센다.

`OnboardingChecklist` 라는 이름은 되돌린 트리에 **파일 셋에 남는다** — 커널(`OnboardingChecklist.kt`)과
그 test, 그리고 게이트 등재 한 줄이고 **전부 7C④-2a 의 산출물**이다(`ea91d753` 에 이미 있다는 것을
`git cat-file -e` 로 확인). 이 slice 가 남긴 것이 아니다 — 커널을 걷는 것은 2a 의 되돌림이다.

⑥ 에서 `leakPatternGate` 도 초록이다(스캔 뿌리 `reports/evidence` 에 이 slice 의 기록을 남겨 둔 채).
그 게이트가 되돌리지 않은 evidence 때문에 붉어지는 경우가 선례에 있어 따로 확인했다.

## 비활성화 경로 — 되돌리지 않고 끄는 법

`ProfileWiring` 의 체크리스트 use case 빈 하나를 빼면 **그 경로만** 사라진다(컨트롤러가 use case 를
요구하므로 기동이 실패한다 — 조용히 404 가 되지 않는다). 프로필 operation 여섯은 그대로 산다.
계약 문서에서 그 operation 을 지우면 문서↔매핑 등식이 깨지므로, 끄는 경우 계약·생성 타입도 함께
되돌린다(그것이 위 목록이다).

## 예상 복구 시간

명령 둘은 초 단위다. 확인까지 포함한 실측은 전건 `check` 한 번(약 27분)이 지배한다.
