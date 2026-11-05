# M9 / award-ingest — rollback

실측 HEAD: `3eeeee7e`

## 끄는 법(되돌리지 않고)

`bidvector.award-collection.mode` 를 주지 않으면 이 갈래 전체가 올라오지 않는다 — 러너도, 서비스 키
바인딩도, 어댑터 생성도 없다. 배선 test 「mode 가 없으면 이 갈래는 아예 올라오지 않는다」가 그 한 줄이
실제로 끄는지를 잰다. **적재된 원문은 그대로 남는다**(append-only 표의 삭제는 별도 절차다).

## 되돌리는 법(코드·문서)

원천은 **이 브랜치에 병합한 main 커밋 `df4976cc`** 다(움직이는 `origin/main` 이 아니다). 목록은 손으로
쓰지 않고 `git log --no-merges --format= --name-only HEAD ^df4976cc ^79f3e5c4 | sort -u` 에서 내며
(= 이 slice 의 커밋만), evidence 디렉터리를 뺀 **20 경로**다(복원 뒤 D 11 · M 9). **라운드마다 다시
산출한다** — 병합으로 base 가 움직이면 목록 재산출의 기준도 함께 움직인다.

```
git restore --source=df4976cc --staged --worktree -- \
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
  app/src/test/kotlin/bidvector/app/architecture/AppHttpDependencyGateTest.kt \
  workflow/src/main/kotlin/bidvector/workflow/collection/CollectAwardListUseCase.kt \
  workflow/src/test/kotlin/bidvector/workflow/collection/CollectAwardListUseCaseTest.kt
```

**hunk 격리가 필요하지 않다 — 전제 둘을 기계로 확인했다.** ⓐ `df4976cc` 는 HEAD 의 조상이고
ⓑ 이 slice 의 첫 산출물 커밋을 담지 않는다(`git merge-base --is-ancestor` 둘). 그 커밋은 main 에서
만든 트리라 공유 파일(`config/quality/**`·`docs/discovery/capability-map.md`)에 **다른 레인의 줄은
이미 들어 있고 내 줄은 없다** — 그래서 단일 복원이 남의 줄을 지우지 않는다(아래 표지 계수가 그것을
센다). **머지 전을 전제**한다 — 머지 뒤의 되돌림은 그 PR 의 머지 커밋 revert 다(같은 복원은 머지
순간 아무것도 지우지 않으면서 exit 0 을 낸다).

**되돌리지 않는 것**: 하네스 경로(`CLAUDE.md`·`.claude/**` — 이 slice 는 건드리지 않았다)와 이 slice 의
evidence 디렉터리(`reports/evidence/m9/award-ingest/**`). evidence 를 남기는 쪽을 고른 이유는 되돌림
기록 자체가 그 디렉터리에 있기 때문이고, base 에 없는 문서가 남아도 게이트는 초록이다(아래 ⑥).

## 실측 (임시 clone, 실측 HEAD 기준)

| 단계 | 결과 |
| --- | --- |
| ① `git restore …` 종료 코드 | 0 |
| ② 상태 계수 | `D` 11 · `M` 9 — 기계 산출 20 경로와 같다 |
| ③ `git diff df4976cc -- <20 경로>` 줄 수 | 0 |
| ③b 결과 트리 == 그 main 트리 | evidence 를 뺀 **전수** diff 가 빈 목록 |
| ③c 내 표지 0 · 남의 표지 보존 | 등록부 둘에서 내 표지 0 · main 쪽 표지 1 과 2 |
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
