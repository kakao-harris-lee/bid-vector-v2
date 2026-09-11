# rollback.md — M4/4C-1

**되돌림은 range revert가 아니라 in_scope 경로 한정이다**(evidence-pack 스킬,
2026-09-04 개정). 하네스 경로(`CLAUDE.md`·`.claude/**`)는 되돌리지 않는다 — 이 slice의
하네스 레인 변경은 **2건**이다(`de99ddb`·`ea79355`, `scope.md` 「하네스 레인 변경」 절
참고 — 이전 판이 이 rollback.md에 「0건」이라 적어 그 절과 어긋났다, verifier B-2 정정).

## 신규 파일(A) — 전체 삭제로 되돌린다

## 공유 파일(M) — **줄 단위로만 되돌린다**(verifier M-1 시정)

다음 셋은 4C-1 **혼자** 만지는 파일이 아니다 — 4B-1(이미 착수)이 같은 파일들에 얹힌다
(`data-dictionary.md`·`capability-map.md`·`gate-tests.properties`). **파일 전체를
base로 복원하면 4B-1이 넣은 줄까지 함께 사라진다**(M3/3B-2가 남긴 교훈과 같은 계열,
verifier M-1) — 그래서 이 rollback은 파일 전체 `git restore`를 쓰지 않는다.

| 파일 | 4C-1이 넣은 것(되돌릴 대상) | 4C-1이 손대지 않은 것(그대로 둔다) |
| --- | --- | --- |
| `docs/discovery/data-dictionary.md` | §2.2.5 블록(어휘·전이표, "어휘 자리만 둔다" → 승인된 값) | §3.6·§13.2(4B-1의 `Verdict`/`SkipReason` 편집) — 물리적으로 다른 절이라 hunk 자체가 겹치지 않는다 |
| `docs/discovery/capability-map.md` | `OPEN-OPS-10` 행(§14.2, 닫힘 표시) | `OPEN-DIC-03` 행(4B-1이 닫음) — 다른 행 |
| `config/quality/gate-tests.properties` | `gate.tests.workflow` 블록 **안**에 4C-1이 끼워 넣은 `event.*` 줄 여섯(`EventBoundaryTest`·`EventEnvelopeTest`·`InboxDedupPropertyTest`·`OutboxEntryTest`·`OutboxEventSinkTest`·`OutboxTransitionTableTest`) — **정정(L-6, verifier r2)**: 이 키·블록 자체는 4A가 만들었다(`git log -p`: 4A 커밋이 `gate.tests.workflow=\` 신설, 실측), 4C-1은 그 블록에 `strategy.*` 줄들 앞에 여섯 줄을 끼워 넣었을 뿐이다 — 「블록 전체(신설 축)」은 잘못된 서술이었다 | `strategy.*` 줄 여섯(4A 몫, 무접촉) · `gate.tests.decision` 블록(4B-1이 신설·확장) — 다른 키 |

되돌리는 절차: 임시 clone에서 `git diff <base> -- <파일>`로 현재 hunk를 확인하고, 위
표의 「되돌릴 대상」 hunk만 `git apply -R`(또는 수동 되돌림, hunk가 물리적으로 분리돼
있어 텍스트 편집으로도 안전)로 걷는다. 파일 전체 `git restore --source=<base>`는
**이 세 파일에는 쓰지 않는다**(다른 A 항목·M 항목에는 그대로 쓴다).

다음 둘은 4C-1이 실제로 편집했고(scope.md `in_scope`) 다른 slice와 겹치지 않는다 —
**전체 복원으로 되돌린다**:

- `app/src/test/kotlin/bidvector/app/conformance/StrategyEditExecutors.kt`
- `workflow/src/main/kotlin/bidvector/workflow/strategy/Ports.kt`
- `workflow/src/main/kotlin/bidvector/workflow/strategy/EditStrategyWorkflow.kt`
- `workflow/src/test/kotlin/bidvector/workflow/strategy/EditStrategyWorkflowTest.kt`
- `reports/evidence/m4/4a/scope.md`

**`milestone-4.md`**(scope.md `in_scope`에 있음)는 이 range의 어느 커밋에서도 4C-1이
실제로 편집한 적이 없다(verifier B-5) — 되돌릴 hunk가 없으므로 이 목록에 없다.

## 목록 산출 (기계적, 손으로 쓰지 않는다)

```
git add -N <신규 파일 후보 경로> 2>/dev/null  # untracked 도 diff에 잡히게(git diff 는 untracked 를 안 봄)
git diff --name-status 4ec4e504db28b743d2cbb0ad4df5e4dbfbc98599 -- \
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

결과(수정 라운드 1, head는 commands.md에 실측 기록): **A(신규) 21 · M(변경) 8**(라운드
1이 `ClaimedOutboxRow.kt`·`OutboxEntryTest.kt` 둘을 A에 더했다 — verifier B-1이 지적한
「내부 두 수치 불일치(18/9 vs 19/8)」는 이번 갱신으로 본문 전체가 하나의 수치만 쓰도록
정리했다). 라운드가 더 늘어 파일이 늘면 이 절차를 다시 돌린다.

## 되돌리는 명령

**A 항목(신규 파일)** — 전체 삭제:

```
git restore --source=4ec4e504db28b743d2cbb0ad4df5e4dbfbc98599 --staged --worktree -- \
  <A 항목 경로 개별 인자>
```

**M 항목 중 공유 파일 셋** — 줄 단위(위 표 참고, 파일 전체 restore 금지).

**M 항목 중 나머지 넷 + `reports/evidence/m4/4a/scope.md`** — 전체 복원(같은 명령, 그
경로만 추가).

`git checkout <base> -- <경로>`는 쓰지 않는다(base에 없는 신규 경로마다 pathspec 오류로
exit 1, 4A/1A 16차 교훈).

## 확인 지점 (임시 clone에서 실제로 돌려 실측 — dry-run 성립만으로 충분하지 않다)

commands.md에 실행 결과를 한 줄씩 남긴다:

1. 위 restore 명령들이 **임시 clone**에서 exit 0.
2. `git status --porcelain`이 **A 21 · M 8**과 일치(A는 완전 삭제, M 중 공유 파일 셋은
   4C-1 몫 hunk만 사라지고 4B-1 몫은 남아 있음 — `grep`으로 `gate.tests.decision`·
   `OPEN-DIC-03`·§3.6 존재 확인).
3. M 대상 중 전체 복원 넷(+4a/scope.md)의 `git diff <base> -- <경로>`가 빈 diff.
4. 되돌린 트리에서 **모듈별 compile**이 exit 0 — `:workflow:compileKotlin
   :app:compileTestKotlin`.
5. 되돌린 트리의 **test**가 초록 — `:workflow:test :app:test --tests '*Conformance*'`.

## 되돌린 뒤 남는 것 — 4A로 완전히 복귀(단, 4B-1 산출물은 그대로)

`EventSink.publish(event, actor)` → `publish(event)`로 되돌아가므로 4A 산출물이 사용자
승인 시점(`4ec4e50`)의 정확한 형태로 복귀한다 — `event` 패키지 전체(신규 A)가 사라지므로
`OutboxEventSink`·`ClaimedOutboxRow`·`OutboxEntry`도 함께 없어진다. 공유 파일 셋은 줄
단위로만 걷으므로 **4B-1의 `Verdict` 커널·`gate.tests.decision` 확장·§3.6/`OPEN-DIC-03`
편집은 그대로 남는다** — 이것이 이번 시정(verifier M-1)의 목적이다.
