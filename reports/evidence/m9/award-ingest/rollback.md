# M9 / award-ingest — rollback

실측 HEAD: `99819849`

## 끄는 법(되돌리지 않고)

`bidvector.award-collection.mode` 를 주지 않으면 이 갈래 전체가 올라오지 않는다 — 러너도, 서비스 키
바인딩도, 어댑터 생성도 없다. 배선 test 「mode 가 없으면 이 갈래는 아예 올라오지 않는다」가 그 한 줄이
실제로 끄는지를 잰다. **적재된 원문은 그대로 남는다**(append-only 표의 삭제는 별도 절차다).

## 되돌리는 법(코드·문서)

`git diff --name-status 79f3e5c4..HEAD -- . ':!reports/evidence'` 가 낸 **19 경로**를 base 로 되돌린다
(A 11 = 삭제 대상 · M 8 = base 로 복원). 목록은 손으로 쓰지 않고 그 명령에서 내며, **라운드마다 다시
산출한다**.

```
git restore --source=79f3e5c4 --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsOpeningResultSource.kt \
  adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsAwardListSourceTest.kt \
  app/src/main/kotlin/bidvector/app/collection/AwardCollectionProperties.kt \
  app/src/main/kotlin/bidvector/app/collection/AwardCollectionRunner.kt \
  app/src/main/kotlin/bidvector/app/wiring/AwardCollectionWiring.kt \
  app/src/test/kotlin/bidvector/app/architecture/ArchitecturePolicy.kt \
  app/src/test/kotlin/bidvector/app/architecture/CollectionArchitectureGateCatchesViolationsTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/CollectionArchitectureGateTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/CollectionDepthGateTest.kt \
  app/src/test/kotlin/bidvector/app/collection/AwardCollectionE2EHarness.kt \
  app/src/test/kotlin/bidvector/app/collection/AwardCollectionE2ETest.kt \
  app/src/test/kotlin/bidvector/app/collection/MockAwardKonepsHttp.kt \
  app/src/test/kotlin/bidvector/app/wiring/AwardCollectionWiringTest.kt \
  config/quality/architecture-policy.properties \
  config/quality/gate-tests.properties \
  docs/discovery/capability-map.md \
  docs/runbook/m9-award-collection.md \
  workflow/src/main/kotlin/bidvector/workflow/collection/CollectAwardListUseCase.kt \
  workflow/src/test/kotlin/bidvector/workflow/collection/CollectAwardListUseCaseTest.kt
```

**hunk 격리가 필요하지 않다 — 전제를 기계로 확인했다.** 이 브랜치에서 위 19 경로를 만진 커밋은 이
slice 의 것뿐이다(`git log --oneline --no-merges 79f3e5c4..HEAD -- <19 경로>` → 네 커밋 전부 이 slice).
공유 파일(`config/quality/**`·`docs/discovery/capability-map.md`)에 다른 slice 의 줄이 이 range 안에
없으므로 단일 복원이 남의 줄을 지우지 않는다. **머지 전을 전제**한다 — 머지 뒤의 되돌림은 그 PR 의
머지 커밋 revert 다(같은 복원은 머지 순간 아무것도 지우지 않으면서 exit 0 을 낸다).

**되돌리지 않는 것**: 하네스 경로(`CLAUDE.md`·`.claude/**` — 이 slice 는 건드리지 않았다)와 이 slice 의
evidence 디렉터리(`reports/evidence/m9/award-ingest/**`). evidence 를 남기는 쪽을 고른 이유는 되돌림
기록 자체가 그 디렉터리에 있기 때문이고, base 에 없는 문서가 남아도 게이트는 초록이다(아래 ⑥).

## 실측 (임시 clone, 실측 HEAD 기준)

| 단계 | 결과 |
| --- | --- |
| ① `git restore …` 종료 코드 | 0 |
| ② 상태 계수 | `D` 11 · `M` 8 — 기계 산출 목록의 A 11 · M 8 과 같다 |
| ③ `git diff 79f3e5c4 -- <19 경로>` 줄 수 | 0 |
| ④ 세 모듈 `compileTestKotlin` | `commands.md` 의 rollback 줄 |
| ⑤⑥ 영향 test 와 이 slice 가 닿은 게이트 | `commands.md` 의 rollback 줄 |

**⑥ 를 전건 `check` 로 재지 않은 이유**: 호스트 빌드 큐 포화로 팀장이 전건 acceptance 를 PR CI 로
옮겼다(2026-11-05 공지). 되돌린 트리의 19 경로는 base 와 **바이트 동일**(③ 0 줄)이므로 그 트리에
남는 차이는 되돌리지 않기로 한 evidence 문서 둘뿐이고, 여기서는 **이 slice 가 닿은 게이트**
(`gateRegistrationGate`·`leakPatternGate`·`sizeGate`·`memberEffectGate` 와 architecture 게이트 test)와
영향 test 를 표적으로 돌렸다. `gateExecutionGate`(등재 class 가 실제로 돌았는가)는 전건 test 실행을
요구해 이 표적 실행에서 제외했다 — 그 축은 PR CI 가 든다.

## 확인 지점

- 「내 줄이 사라졌다」: 위 ③ 이 0 줄.
- 「남의 줄이 남았다」: 되돌리지 않기로 한 evidence 파일 둘이 그대로 있고(②③ 의 `ls` 결과), 공유
  파일에는 애초에 남의 줄이 이 range 안에 없다(위 전제 확인 명령이 그 사실을 낸다).
- 되돌린 트리가 **서고 게이트까지 초록**: ④ 와 ⑤⑥.
