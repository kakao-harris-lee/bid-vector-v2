# rollback.md — M4/4C-1

**되돌림은 range revert가 아니라 in_scope 경로 한정이다**(evidence-pack 스킬,
2026-09-04 개정). 하네스 경로(`CLAUDE.md`·`.claude/**`)는 되돌리지 않는다 — 이 slice의
하네스 레인 변경은 착수~이 시점까지 **0건**(scope.md 「하네스 레인 변경」 절, 리뷰 요청
시점에 재확인).

## 목록 산출 (기계적, 손으로 쓰지 않는다)

```
git diff --name-status 4ec4e504db28b743d2cbb0ad4df5e4dbfbc98599..HEAD -- \
  workflow/src/main/kotlin/bidvector/workflow/event \
  workflow/src/test/kotlin/bidvector/workflow/event \
  config/quality/gate-tests.properties \
  docs/discovery/data-dictionary.md \
  docs/discovery/capability-map.md \
  workflow/src/main/kotlin/bidvector/workflow/strategy/Ports.kt \
  workflow/src/main/kotlin/bidvector/workflow/strategy/EditStrategyWorkflow.kt \
  workflow/src/test/kotlin/bidvector/workflow/strategy/EditStrategyWorkflowTest.kt \
  app/src/test/kotlin/bidvector/app/conformance/StrategyEditExecutors.kt \
  milestone-4.md \
  reports/evidence/m4/4a/scope.md \
  reports/evidence/m4/4c1
```

결과(head `13cf0f6`, commands.md에 실측 기록): **A(신규) 19 · M(변경) 8**. 라운드가
늘어 파일이 늘면(4C-1이 새 라운드를 열면) 이 절차를 다시 돌린다.

## 되돌리는 명령

```
git restore --source=4ec4e504db28b743d2cbb0ad4df5e4dbfbc98599 --staged --worktree -- \
  <위 A·M 경로 개별 인자 전체>
```

`--source`에 없는 신규 경로(A 항목)는 이 명령이 삭제한다 — 별도 `git rm` 불필요.
`git checkout <base> -- <경로>`는 쓰지 않는다(base에 없는 신규 경로마다 pathspec 오류로
exit 1, 4A/1A 16차 교훈).

## 확인 지점 (임시 clone에서 실제로 돌려 실측 — dry-run 성립만으로 충분하지 않다)

commands.md에 실행 결과를 한 줄씩 남긴다:

1. 위 restore 명령이 **임시 clone**에서 exit 0.
2. `git status --porcelain`이 A/M 수(18/9)와 일치.
3. M 대상 경로의 `git diff <base> -- <경로>`가 빈 diff.
4. 되돌린 트리에서 **모듈별 compile**이 exit 0 — `:workflow:compileKotlin
   :app:compileTestKotlin`(4A 관례 계승, M3/3B-2 verifier r2 교훈 — exit 0만으로는
   부족하고 compile까지 재야 한다).
5. 되돌린 트리의 **test**가 초록 — `:workflow:test :app:test --tests '*Conformance*'`.

## 되돌린 뒤 남는 것 — 4A로 완전히 복귀

`EventSink.publish(event, actor)` → `publish(event)`로 되돌아가므로(`Ports.kt`·
`EditStrategyWorkflow.kt`·`EditStrategyWorkflowTest.kt`가 M 목록에 있다) 4A 산출물이
사용자 승인 시점(`4ec4e50`)의 정확한 형태로 복귀한다 — `event` 패키지 전체(신규 A)가
사라지므로 `OutboxEventSink`도 함께 없어진다(4A는 `EventSink` 구현 없이도 완결이었다,
2026-09-09 사용자 승인 당시 상태).
