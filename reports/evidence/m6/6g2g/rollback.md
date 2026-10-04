# M6/6G-2g — rollback

**실측 HEAD: `52cbca24`** (이 slice 의 마지막 산출물 커밋). 아래 ①~⑥ 은 전부 그 커밋을 체크아웃한
**버릴 clone** 에서 실제로 실행한 결과다. 앞 라운드의 실측을 옮기지 않는다.

> verifier 가 대조할 것은 「실측 HEAD == 판정 SHA」가 아니다(evidence 커밋은 언제나 뒤에 온다).
> **그 사이에 되돌림 대상이 움직였는가**를 본다:
> `git diff --name-only 52cbca24..<판정 SHA> -- <아래 「되돌리는 경로」 전부>` 가 **빈 출력**이면 유효하다.

## 되돌리는 것과 되돌리지 않는 것

**되돌린다** — in_scope 의 산출물 경로. 목록은 손으로 쓰지 않고
`git diff --name-status 31721008..52cbca24` 에서 기계로 냈다(A 13 = 삭제 대상, D 8·M 21 = base 로 복원).
**디렉터리로 접지 않는다**(vr r1 L-2) — 접으면 목록이 slice 가 만진 파일보다 넓어지고, 파일 그대로 두면
`comm -23 <(기계 산출) <(문서 목록)` 이 **빈 출력**이라는 등식으로 잴 수 있다.
**라운드마다 파일이 늘면 이 절차를 다시 돌린다** — 목록이 낡는 것이 이 결함의 실제 원인이다.

**되돌리지 않는다**:
- 하네스 경로(`CLAUDE.md`·`.claude/**`) — 이 range 에 변경 없음(아래 「하네스 레인 변경」).
- `reports/evidence/m6/6g2g/**` — 이 slice 의 evidence. 되돌리면 복구 상태가 「일이 있었다」는 기록을
  잃는다. **⑥ 에서 그 디렉터리를 남긴 채 게이트가 초록임을 실측했다**(누출 게이트의 scanRoot 가
  evidence 라 여기가 자주 붉는 자리인데, 이 slice 의 evidence 는 패턴 어휘를 축어로 담지 않는다).

## 절차

### 1단계 — in_scope 경로를 base 로 (경로는 **개별 인자**)

```
git restore --source=31721008 --staged --worktree -- \
  adapters/src/test/kotlin/bidvector/adapters/audit/AuditGateRegistrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/contract/RealServerIntegrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationGateRegistrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/EventGateRegistrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/MlGateRegistrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/profile/ProfileGateRegistrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/qualification/QualificationGateRegistrationTest.kt \
  app/build.gradle.kts \
  app/src/test/kotlin/bidvector/app/architecture/AppDependencyConditions.kt \
  app/src/test/kotlin/bidvector/app/architecture/AppGateRegistrationTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/AppHttpDependencyGateTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/AppHttpDependencyRules.kt \
  app/src/test/kotlin/bidvector/app/architecture/ArchitecturePolicy.kt \
  app/src/test/kotlin/bidvector/app/architecture/CollectionArchitectureGateCatchesViolationsTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/CollectionArchitectureGateTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/CollectionArchitectureRules.kt \
  app/src/test/kotlin/bidvector/app/architecture/CollectionDepthGateTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/DepthAxis.kt \
  app/src/test/kotlin/bidvector/app/architecture/DivisionValueRules.kt \
  app/src/test/kotlin/bidvector/app/architecture/TransportSurfaceGateCatchesViolationsTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/TransportSurfaceGateTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/TransportSurfaceRules.kt \
  app/src/test/kotlin/bidvector/archfixture/violating/transport/FileSystemEgressSamples.kt \
  build-logic/src/main/kotlin/bidvector.kotlin-conventions.gradle.kts \
  build-logic/src/main/kotlin/bidvector.quality-baseline.gradle.kts \
  build-logic/src/main/kotlin/bidvector/buildlogic/GateRegistration.kt \
  build-logic/src/main/kotlin/bidvector/buildlogic/GateRegistrationGateTask.kt \
  build-logic/src/main/kotlin/bidvector/buildlogic/TestClassCensus.kt \
  build-logic/src/test/kotlin/bidvector/buildlogic/GateRegistrationCensusTest.kt \
  build-logic/src/test/kotlin/bidvector/buildlogic/MetaAnnotatedTestProbe.kt \
  build-logic/src/test/kotlin/bidvector/buildlogic/MetaAnnotationResolutionTest.kt \
  config/quality/architecture-policy.properties \
  config/quality/gate-tests.properties \
  workflow/build.gradle.kts \
  workflow/src/test/kotlin/bidvector/workflow/WorkflowGateRegistrationTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/collection/SampleSelectionTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityPolicyDataTest.kt
```

`--source` 에 없는 경로는 **삭제**되므로 신규 파일에 별도 `git rm` 이 필요 없다.
`git checkout <base> -- <경로>` 는 쓰지 않는다 — base 에 없는 경로마다 pathspec 오류로 아무것도
적용되지 않는다.

두 정책 파일(`architecture-policy.properties`·`gate-tests.properties`)은 **이 range 에서 이 slice 의
커밋만 만졌다**(`git log --oneline 31721008..52cbca24 -- <파일>` 로 확인 — 전부 `m6-6g2g`). 그래서 hunk 격리가 필요 없고 base 로의 단일 복원이 맞다. **다른 slice 의 커밋이 섞이면
이 판단이 바뀌므로 라운드마다 그 `git log` 를 다시 돌린다.**

### 2단계 — `milestone-6.md` 는 커밋 해시 hunk 격리

이 파일은 여러 slice 가 이어 쓰는 공유 파일이다. **base 로 복원하면 안 된다.** 이 range 에서
이 파일을 만진 커밋은 하나뿐이다(`3e41dff5`, 팀장 레인의 6G-2g 착수 문단).

```
git diff 3e41dff5~1..3e41dff5 -- milestone-6.md | git apply -R -
```

**수동 해소 절차**(자동 적용이 conflict 를 내면): `milestone-6.md` 에서 **「6G-2g 착수 2026-10-04」로
시작하는 문단 하나**를 통째로 지운다. 그 문단만 지우고 이웃한 「6G-2c 종결」 문단은 **남긴다**.
종결 문단이 추가돼 있으면(이 slice 가 종결되면 팀장이 쓴다) 그것도 같은 방식으로 문단 단위로 지운다.

확인은 둘 다 본다 — **내 줄이 사라졌는가**와 **남의 줄이 남았는가**.

## ①~⑥ 실측 (버릴 clone, HEAD `d6c79a99`)

| # | 확인 | 명령 | 결과 |
|---|---|---|---|
| ⓪ | 목록이 기계 산출과 같다 | `comm -23 <(git diff --name-status 31721008..52cbca24 의 산출물 경로) <(이 문서의 목록)` | 빈 출력 |
| ① | 복원 명령이 선다 | 위 `git restore` | exit 0 |
| ② | 복원 규모 | `git status --porcelain` 의 상태 집계 | A 8 · D 9 · M 20 |
| ③ | 복원 경로가 base 와 같다 | `git diff 31721008 -- <복원 경로들>` | 빈 출력 |
| ③b | 공유 파일도 base 로 | 2단계 hunk 역적용 뒤 `git diff 31721008 -- milestone-6.md` | exit 0 · conflict 0 · 빈 출력 |
| ③c | 내 줄 사라짐 · 남의 줄 남음 | 되돌린 `milestone-6.md` 에서 「6G-2g 착수」 문단과 「6G-2c 종결」 문단 | 0 · 1 |
| ④ | 되돌린 트리가 컴파일된다 | `./gradlew --no-daemon compileTestKotlin` | exit 0 |
| ⑤ | 그 트리의 test 가 초록 | 아래 `check` 에 포함 | exit 0 |
| ⑥ | **그 트리에서 게이트가 초록** | `./gradlew --no-daemon check` | exit 0 |

A 8 = 이 slice 가 지웠던 등식 test 여덟이 돌아온 것. D 9 = 이 slice 가 만든 파일 아홉이 지워진 것.
M 20 = base 로 되돌아간 수정 파일. (`git diff --name-status` 쪽 집계는 A 13 · D 8 · M 21 로, evidence
경로 넷과 `milestone-6.md` 를 포함한 수다 — 복원 목록은 그 둘을 빼고 37 경로다.)

수정 라운드 2 에서 산출물 파일 수는 그대로다(새 파일 없음 — 라운드가 더한 것은 기존 파일 안의
fixture 셋과 test 넷이다).

⑥ 이 초록이라는 것은 **되돌린 상태가 CI 를 통과한다**는 뜻이다 — 「HEAD 가 초록이니 되돌려도 초록」이
아니라 그 트리에서 직접 쟀다.

## 비활성화(되돌리지 않고 끄는 법)

이 slice 가 더한 게이트 셋은 전부 **데이터와 task 배선**이라, 전체 되돌림 없이 축 하나만 끄는 길이 있다.
운영 중 사고가 났을 때의 최소 조치이고, **정상 경로는 위 되돌림**이다.

| 끄고 싶은 것 | 최소 조치 |
|---|---|
| 등재 등식 | `bidvector.kotlin-conventions.gradle.kts`·`bidvector.quality-baseline.gradle.kts` 의 `check` 의존에서 두 task 이름을 뺀다 |
| 파일 시스템 출구(⑥) | `collection.transport.surface-packages` 에서 `java.nio.file` 과 `surface-types` 의 `java.io` 여섯을 빼고, 1층 허용 목록에 `java.nio.file`·`java.nio.file.attribute` 를 되돌려 넣고, `file-system` 용도 키와 `collection.transport.purposes` 의 그 낱말을 지운다 |
| 멤버 표면(⑦) | `TransportSurfaceGateTest` 의 3층 test 둘을 지우고 `collection.transport.member-surface` 를 비운다 |
| 3층 전송 서명 갈래 | `TransportSurfaceRules` 의 호출 시그니처 판정을 빼고 `collection.transport.member-surface` 를 그 전 집합으로 되돌린다 |
| 새 목적지 둘(⑥ 후속) | `collection.transport.purposes` 에서 `http-response`·`process-stdio` 와 그 두 키를 지우고, 낱개에서 `PrintWriter`·`PrintStream`·`Formatter` 를 빼고, `app` 허용에 `java.io` 를 되돌려 넣는다 |
| 반사 뿌리 확장(⑤) | `collection.reflection.roots` 를 `workflow`·`app`·`adapters` 로 되돌리고 도출식을 `externalJudgedModules` 로 되돌린다 |
| 수집 깊이(③) | 축 키를 지우고 각 호출 자리에 앞 판의 깊이를 되돌린다 — **가장 넓은 조치**이고, 이 축만 끌 일이면 되돌림이 낫다 |

예상 복구 시간: 되돌림은 명령 둘(위 1·2단계)이라 분 단위다. 확인에 `check` 한 번(약 9분)이 든다.

## 하네스 레인 변경

`git log --oneline 31721008..52cbca24 -- CLAUDE.md .claude/` — **없음**.
이 range 의 팀장 레인 커밋(`3e41dff5` 와 계약 갱신들)은 `milestone-6.md` 와
`reports/evidence/m6/6g2g/scope.md` 만 만졌고, 둘 다 위 절차가 다룬다(2단계 · 되돌리지 않음).
slice 산출물이 아니며 in_scope 안이고, 운영자 승인 아래 같은 range 에 있다.
