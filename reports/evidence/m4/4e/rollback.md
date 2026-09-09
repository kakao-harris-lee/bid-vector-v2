# rollback.md — M4/4E

**되돌림은 range revert가 아니라 in_scope 경로 한정이다**(evidence-pack 스킬,
2026-09-04 개정). 하네스 경로(`CLAUDE.md`·`.claude/**`)는 이 range에 존재하지 않는다
(scope.md 「하네스 레인 변경」 절 — 착수 시 0건, 이 branch는 격리돼 있어 하네스 레인
커밋이 섞일 자리가 없다).

**브랜치 격리(scope.md 「레인 격리」 절).** 이 slice는 `m4-4e/2026-09-09`라는 별도
branch/worktree에서 살고, 다른 진행 중 slice(4A·4B-1·4C-1)는 `m4/2026-09-08`에서
산다. 그래서 이 range(`f600909..HEAD`)에 나타나는 변경은 전부 이 slice 자신의 것이다
— 4C-1 rollback.md가 다뤘던 「공유 파일에 다른 slice 몫이 섞여 있다」 상황이 이
range에는 없다. **병합 전에는 「브랜치를 버린다」가 곧 rollback**(scope.md 원문)이고,
아래는 병합 뒤(또는 병합 전 부분 되돌림이 필요할 때)를 대비한 경로 한정 절차다.

## 목록 산출 (기계적, 손으로 쓰지 않는다)

```
git diff --name-status f600909fcde1c08bb83abaf749cfa681539831a0..HEAD -- \
  workflow/src/main/kotlin/bidvector/workflow/notification \
  workflow/src/test/kotlin/bidvector/workflow/notification \
  config/quality/leak-patterns.txt \
  config/quality/gate-tests.properties \
  reports/evidence/m4/4e
```

결과(2026-09-09, 3커밋 뒤 실측): **A(신규) 20 · M(변경) 2**.

- A 20: `config/quality/leak-patterns.txt` · `workflow/src/main/kotlin/bidvector/workflow/notification/`의
  12개 파일(`Channel.kt`·`DeliveryMode.kt`·`DeliveryPlan.kt`·`DeliveryRequest.kt`·
  `DeliveryResult.kt`·`DispatchNotification.kt`·`MaskedTarget.kt`·
  `NotificationDeliveryPolicyData.kt`·`Ports.kt`·`RenderedContent.kt`·`RouteKey.kt`·
  `RuntimeEnvironment.kt`) · `workflow/src/test/kotlin/bidvector/workflow/notification/`의
  7개 파일(`DeliveryPlanTableTest.kt`·`DispatchNotificationTest.kt`·`MaskedTargetTest.kt`·
  `NotificationBoundaryTest.kt`·`NotificationPolicyDataTest.kt`·`RouteKeyTest.kt`·
  `SenderContractTest.kt`) · `reports/evidence/m4/4e/`의 `commands.md`·`checklist.md`·
  `policy-values.md`·`rollback.md`(이 파일 자신 — 목록에는 잡히나 자기 자신을 되돌리는
  명령의 대상은 아니다, 아래 「되돌리는 명령」 참고).
- M 2: `config/quality/gate-tests.properties`(`gate.tests.workflow` 블록에 M4/4E 문단
  + notification test 일곱 줄 추가, 순수 추가 hunk 둘) · `reports/evidence/m4/4e/scope.md`
  (착수 계약 고정 — base에 있던 초안을 팀장이 잠근 것, 이 slice의 계약 문서 자신).

**라운드가 더 늘어 파일이 늘면 이 절차를 다시 돌린다.**

## 되돌리는 명령

**A 항목(신규 파일)** — 전체 삭제(`rollback.md` 자신은 제외 — 되돌림 절차 문서 자체를
지우면 절차를 설명할 문서가 없어진다, 필요하면 마지막에 수동으로 지운다):

```
git restore --source=f600909fcde1c08bb83abaf749cfa681539831a0 --staged --worktree -- \
  config/quality/leak-patterns.txt \
  workflow/src/main/kotlin/bidvector/workflow/notification/Channel.kt \
  workflow/src/main/kotlin/bidvector/workflow/notification/DeliveryMode.kt \
  workflow/src/main/kotlin/bidvector/workflow/notification/DeliveryPlan.kt \
  workflow/src/main/kotlin/bidvector/workflow/notification/DeliveryRequest.kt \
  workflow/src/main/kotlin/bidvector/workflow/notification/DeliveryResult.kt \
  workflow/src/main/kotlin/bidvector/workflow/notification/DispatchNotification.kt \
  workflow/src/main/kotlin/bidvector/workflow/notification/MaskedTarget.kt \
  workflow/src/main/kotlin/bidvector/workflow/notification/NotificationDeliveryPolicyData.kt \
  workflow/src/main/kotlin/bidvector/workflow/notification/Ports.kt \
  workflow/src/main/kotlin/bidvector/workflow/notification/RenderedContent.kt \
  workflow/src/main/kotlin/bidvector/workflow/notification/RouteKey.kt \
  workflow/src/main/kotlin/bidvector/workflow/notification/RuntimeEnvironment.kt \
  workflow/src/test/kotlin/bidvector/workflow/notification/DeliveryPlanTableTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/notification/DispatchNotificationTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/notification/MaskedTargetTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/notification/NotificationBoundaryTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/notification/NotificationPolicyDataTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/notification/RouteKeyTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/notification/SenderContractTest.kt \
  reports/evidence/m4/4e/commands.md \
  reports/evidence/m4/4e/checklist.md \
  reports/evidence/m4/4e/policy-values.md
```

`git checkout f600909... -- <경로>`는 쓰지 않는다(base에 없는 신규 경로마다 pathspec
오류로 exit 1, 1A 16차 교훈).

**M 항목 — `config/quality/gate-tests.properties`(줄 단위)**: 이 range 안에서는 이
파일을 4E 혼자 만졌으나(브랜치 격리), 병합 뒤에는 `m4` 쪽 4A/4C-1이 이미 쓴 다른 블록
(`gate.tests.strategy`·`gate.tests.workflow`의 기존 event/strategy 줄)과 같은 파일을
공유하게 되므로 **파일 전체 restore는 쓰지 않는다** — hunk 둘(M4/4E 주석 문단, notification
test 일곱 줄)만 되돌린다. diff가 순수 추가(삭제 줄 없음)라 `git apply -R`로 안전하게
걷힌다:

```
git diff f600909fcde1c08bb83abaf749cfa681539831a0..HEAD -- config/quality/gate-tests.properties \
  | git apply -R
```

**M 항목 — `reports/evidence/m4/4e/scope.md`(전체 복원)**: 이 파일은 이 slice 자신의
계약 문서라 다른 slice와 겹치지 않는다(다른 slice는 자기 자신의 `reports/evidence/m4/<slice>/scope.md`를
갖는다) — 전체 복원으로 되돌린다:

```
git restore --source=f600909fcde1c08bb83abaf749cfa681539831a0 --staged --worktree -- \
  reports/evidence/m4/4e/scope.md
```

## 확인 지점 (임시 clone에서 실제로 돌려 실측 — dry-run 성립만으로 충분하지 않다)

commands.md에 실행 결과를 한 줄씩 남긴다:

1. 위 restore 명령들이 **임시 clone**에서 exit 0.
2. `git status --porcelain`이 **A 20(rollback.md 제외 19 삭제 + 자기 자신 1은 그대로
   남음) · M 2 복원**과 일치 — A 목록 19개(파일)는 완전 삭제, `gate-tests.properties`는
   hunk 둘만 사라지고 4A/4C-1 몫(`gate.tests.workflow`의 event·strategy 줄, `gate.tests.strategy`)은
   그대로, `scope.md`는 base의 초안 형태로 복귀.
3. `config/quality/gate-tests.properties`·`reports/evidence/m4/4e/scope.md`의
   `git diff f600909... -- <경로>`가 빈 diff.
4. 되돌린 트리에서 **모듈별 compile**이 exit 0 — `:workflow:compileKotlin
   :workflow:compileTestKotlin`(notification 패키지가 사라졌으니 컴파일 대상 자체가
   준다 — 다른 workflow 파일이 notification을 참조하지 않으므로 깨질 이유가 없다).
5. 되돌린 트리의 **test**가 초록 — `:workflow:test`(notification 패키지 test가
   사라졌으니 나머지 event·strategy test만 남아 통과해야 한다).

## 되돌린 뒤 남는 것

`workflow` 모듈은 4A(`strategy`)·4C-1(`event`) 산출물 그대로 남는다 — 이 slice는
그 둘을 편집하지 않았다(D-4E-7, import만). `gate.tests.workflow`는 event·strategy
test 열여덟만 남긴 base 상태로 복귀한다. `config/quality/leak-patterns.txt`가
사라지므로 이후 어느 slice가 S-3류 스캔을 다시 쓰려면 이 파일을 다시 만들어야 한다
(정책 파일 자체가 이 slice의 신설물이기 때문 — legacy-behavior 층 계승이 아니다).
