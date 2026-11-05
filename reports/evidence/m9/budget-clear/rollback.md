# M9 / budget-clear 되돌림

**실측 HEAD: `be1026dc`**(PR #124 수정 라운드의 마지막 산출물 커밋). base `79f3e5c4`.
앞 라운드의 실측은 `c36a01e1` 에서 돌았고 이 문서는 **그 수치를 옮기지 않는다** — 수정 라운드가 파일을
더했으므로 목록과 수를 **다시 산출해 다시 쟀다**(cr L-6 · v L-1).

## 복원 목록 — 기계 산출

```
git diff --name-only --diff-filter=M 79f3e5c4..HEAD   # 되돌릴 수정
git diff --name-only --diff-filter=A 79f3e5c4..HEAD   # 지울 추가
```

**추가 쪽은 고정 수로 적지 않는다.** 그 목록은 evidence 커밋마다 길어지고, 그래서 되돌리는 명령이
**실행 시점에 재산출**한다. 실측 HEAD `be1026dc` 에서의 값은 **수정 13 · 추가 5** 였다(추가 넷이 이 slice 의
evidence 이고, 이 문서를 담은 evidence 커밋 뒤에는 그 수가 다시 바뀐다 — 절차는 그대로 맞다).

수정 13 — `app/src/main/kotlin/bidvector/app/http/StrategyEditRequests.kt` ·
`app/src/main/kotlin/bidvector/app/wiring/StrategyFieldValue.kt` ·
`app/src/test/kotlin/bidvector/app/architecture/EditableFieldVocabularyGateTest.kt` ·
`config/quality/gate-tests.properties` · `docs/discovery/capability-map.md` · `milestone-6.md` ·
`openapi/bidvector-operator-api.yaml` · `reports/evidence/m6/closure/open-inventory.md` ·
`web/e2e/journey/onboarding.spec.ts` · `web/src/api/generated/operator-api.ts` ·
`web/src/display/editSessionDisplay.test.ts` · `web/src/display/editSessionDisplay.ts` ·
`web/src/screens/SettingsScreen.test.tsx`

추가 — `app/src/test/kotlin/bidvector/app/http/StrategyBudgetClearEndpointTest.kt` 와 이 slice 의 evidence
디렉터리(`reports/evidence/m9/budget-clear/**`, 통째로 지운다).

range revert 를 쓰지 않는다 — 공유 작업 트리에서 다른 레인의 커밋이 범위에 들어온다.

## 되돌리는 명령

```
MOD=$(git diff --name-only --diff-filter=M 79f3e5c4..HEAD)
ADD=$(git diff --name-only --diff-filter=A 79f3e5c4..HEAD)
git restore --source=79f3e5c4 --staged --worktree -- $MOD
rm -f $ADD && rmdir reports/evidence/m9/budget-clear reports/evidence/m9
```

**공유 파일의 복원 원천**: `capability-map.md` · `milestone-6.md` · `open-inventory.md` ·
`gate-tests.properties` · `SettingsScreen.test.tsx` 는 다른 레인도 같은 파일을 편집한다. 아직 머지 전이므로
base 에서 복원하는 것이 맞고, **머지된 뒤에는 base 가 아니라 이 slice 를 담은 병합 커밋의 부모(고정 SHA)**
에서 복원해야 남의 줄을 지우지 않는다. 머지 뒤의 되돌림은 PR revert 가 정답이다.

## 「되돌림 대상이 움직였는가」 — 이 술어를 어떻게 읽는가

verifier 가 보는 것은 `git diff --name-only <실측 HEAD>..<판정 SHA> -- <복원 경로들>` 이다. 복원 목록이
**이 slice 의 evidence 디렉터리를 담으므로** 그 술어를 문면 그대로 적용하면 영원히 비지 않는다 — evidence
커밋은 실측보다 **항상 뒤**에 온다. 그래서 두 쪽을 나눠 적는다.

- **산출물 경로**(evidence 디렉터리 제외): `git diff --name-only be1026dc..<판정 SHA> -- <그 경로들>` 이
  **빈 출력**이어야 하고, 실측 HEAD 가 곧 마지막 산출물 커밋이라 성립한다.
- **evidence 디렉터리**: 이 술어에서 **뺀다**. 되돌림이 그 디렉터리를 통째로 지우므로 내용이 무엇이든 결과가
  같다(`rm` 은 파일 내용을 보지 않는다).

## 임시 clone 실측 — ①~⑥

저장소 밖 임시 clone 을 `be1026dc` 로 체크아웃해 위 명령을 그대로 돌렸다.

| # | 무엇 | 실측 |
| --- | --- | --- |
| ① | 명령 종료 코드 | `git restore` exit 0 · `rm`·`rmdir` exit 0 |
| ② | 되돌린 수 | 수정 **13** · 추가 제거 **5** (기계 목록과 같은 수) |
| ③ | base 대비 diff | `git diff 79f3e5c4 --stat` **빈 출력** · 미추적 잔여 **0** |
| ④ | 컴파일 | **트리 동일성으로 갈음** — 아래 |
| ⑤ | test | **트리 동일성으로 갈음** |
| ⑥ | 게이트 | **트리 동일성으로 갈음** |

**트리 동일성**: 되돌린 트리의 해시가 base 트리와 **같다**
(`git write-tree` == `git rev-parse 79f3e5c4^{tree}` — 둘 다 `80e6c3ba…`). 되돌린 트리가 base 와 **바이트까지
같은 트리**이므로 ④⑤⑥ 을 다시 돌리는 것은 base 를 다시 재는 것과 같다. base `79f3e5c4` 는 `main` 의 병합
커밋이고 그 커밋에서 CI 세 job 이 돌았다. 갈음의 근거는 「HEAD 가 초록이다」가 아니라 **트리가 같다**는 것이다.

**계수 대조**(내 표지 0 · 남의 표지 보존):

- 내 표지 — `m9-budget-clear` 를 담은 소스·설정 파일 **0** · `acceptsClearing` 를 담은 파일 **0**.
- 남의 표지 — 여정 ① 의 `D-7E2b-9` 주석 **2 줄 남음** · `open-inventory.md` 의 「§4 후속」 **2 줄 남음**.
  내가 더한 §5·§6 절은 **0 절**로 사라졌다(그 둘은 내 것이다). 즉 이 slice 가 고친 공유 파일에서 **내 줄만
  사라지고 남의 줄은 남았다**.

## clean-tree 게이트

`git status --porcelain -- <in_scope 경로 개별 인자>` 빈 출력. 양성 대조는 **비파괴 절삭**으로 한 번:
편집 중인 파일 하나에 한 줄을 더해 같은 명령이 그 파일을 내는 것을 보고 그 줄을 지웠다(`git checkout --` 쓰지
않음 — 미커밋 산출물을 함께 지운다).
