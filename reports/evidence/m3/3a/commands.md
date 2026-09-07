# 실행 명령과 종료 코드 — M3 / 3A

base `a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f`, 구현 head(r0) `b0b40cd`, 수정 라운드 1 head
`a9383b7`, **수정 라운드 2 head** `74cf7b6f33950ca7c09306f73aad1f3e9081c8b1`(코드·게이트 커밋
기준 — 이 evidence 커밋 자신은 항상 한 칸 뒤진다, N-7). verifier r1 은
`_workspace/m3-3a/02_verifier_report.md`, r2(N-1~N-7)는
`_workspace/m3-3a/03_verifier_report_r2.md` 참고. 출력 전문은 남기지 않는다(evidence-pack
스킬 규격) — 핵심 결과 한 줄만.

## 수정 라운드 2 — finding 처리 한 줄씩

| finding | 처리 | 커밋 |
| --- | --- | --- |
| N-1 high | `KonepsFieldContract.of`·`KonepsFieldContractRegistry.of` 를 `internal` 로 낮춰 F-4 를 실제로 닫는다. 격리 worktree 재실측: `adapters` 에서 같은 호출이 이제 `Cannot access ... it is internal` 로 컴파일 실패 | `b86af3c` |
| N-2 medium | procurement test 전체가 `VatTreatment.UNKNOWN` 표본 하나뿐이라 F-2 가드가 리터럴 회귀와 구별되지 않았다 — `bssAmt=INCLUSIVE`·`presmptPrce=EXCLUSIVE`(curator 표 실값) 두 표본 + 회귀 test 추가. 변이 재현 후 두 test 실패 확인, 원복 | `4e992d2` |
| N-3 medium | `parseSourceZonedInstant` 호출처 0건이던 시각 축을 `instantFrom`(신설, `DateTimeInterpretation.kt`)으로 `DEADLINE_AT`·`OPENING_SCHEDULED_AT` 에 배선. `NoticeCollected`·`Notice` 에 `deadlineAt`(`Notice`)·`openingScheduledAt`(`NoticeCollected`) 필드 추가, 해석값·원문 보존·부재 시 null test 추가 | `4e992d2` |
| N-4 medium | `gate.tests.procurement` 에 `NoticeTest` 등재(11=11), 우회 (1)·(5)·(10) 폐쇄 경계 주석을 실측대로 정정 | `74cf7b6` |
| N-5 low | `NoticeTest`의 오도 test 이름을 실측(같은 모듈은 열림, 다른 모듈만 닫힘)에 맞게 정정 | `4e992d2`(N-2·N-3 과 같은 파일이라 같은 커밋) |
| N-6 low | rollback.md의 "새 Gradle 모듈" 오기 정정(base 에 이미 모듈 골격 있음) + 이번 head(`74cf7b6`)로 임시 clone 재측정 | 이 커밋 |
| N-7 low | 이 문서·checklist.md 의 head 선언을 `74cf7b6`으로 갱신 | 이 커밋 |

## acceptance_commands 재실행(head `74cf7b6`)

### S-0 — 격리 worktree `clean check`
## 2026-09-07T06:58:06Z
- cmd: `git worktree add --detach /tmp/m3-3a-r2-s0 HEAD && (cd /tmp/m3-3a-r2-s0 && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 347 actionable tasks 전부 실행. `git worktree remove --force`로 정리, 잔여 없음 확인.

### S-1 — 저장소 루트 `clean check`
## 2026-09-07T06:58:06Z
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 338 actionable tasks.

### S-2 — 도메인 test
## 2026-09-07T06:58:06Z
- cmd: `./gradlew :procurement:test`
- exit: 0
- 핵심 결과: 70 tests, 0 failed, 0 error. 선언 `@Test`도 70 — F-1 재발 없음.

### S-3 — 도메인 게이트 5종
## 2026-09-07T06:58:06Z
- cmd: `./gradlew :procurement:domainApiTypeGate :procurement:domainSourceReferenceGate :procurement:typeShapeGate :procurement:sizeGate :procurement:cpdCheck`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 5개 게이트 전부 통과.

### S-4 — app conformance (조건부: authoritative ≥ 1 아님 → 기존 corpus 회귀 확인으로 기록)
## 2026-09-07T06:58:06Z
- cmd: `./gradlew :app:test --tests '*Conformance*'`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL` — 기존 다섯 축 회귀 무손상.

### S-5 — quality baseline
## 2026-09-07T06:58:06Z
- cmd: `./gradlew qualityBaseline`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`.

### S-6 — 해당 없음
- `fixtures/manifest.yaml` 미편집(curator 소유).

### 보강 — gate-tests.properties 편집에 대한 표적 재실행(N-4)
## 2026-09-07T06:58:06Z
- cmd: `./gradlew :procurement:gateExecutionGate --rerun`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL` — `NoticeTest` 등재 뒤에도 게이트 초록.

## 리뷰 요청 조건 점검(head `74cf7b6`)

### clean-tree 게이트(in_scope 경로 개별 인자 + 양성 대조)
## 2026-09-07T06:58:06Z
- cmd: `git status --porcelain -- procurement/ shared-kernel/src/main/kotlin/bidvector/sharedkernel/NoticeRound.kt shared-kernel/src/main/kotlin/bidvector/sharedkernel/Provenance.kt shared-kernel/src/main/kotlin/bidvector/sharedkernel/Rate.kt shared-kernel/src/test/kotlin config/quality/gate-tests.properties app/src/test/kotlin/bidvector/app/conformance decision/src/test/kotlin/bidvector/decision/FloorShortfallKernelTest.kt reports/evidence/m3/3a`
- exit: 0, 출력 없음(clean) — 단 이 명령이 감시하지 않는 curator 소유 파일(`policy-values.md`·
  `fixtures-koneps-collection.md`)이 같은 시점 작업 트리에 미커밋 상태로 있었다(다른 레인,
  범위 밖 — 스테이징하지 않았다).
- 양성 대조: `NoticeId.kt`에 한 줄 추가 → `M procurement/...` 관측 → `git checkout --` 복구 → 다시 clean.

### secret 스캔
## 2026-09-07T06:58:06Z
- cmd: `git diff a9383b7..HEAD -- procurement/ config/quality/gate-tests.properties | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
- exit: 1(매치 없음) — 이번 라운드 코드 diff 자체에 벤진 매치조차 없다.

### 병렬 레인 혼입
## 2026-09-07T06:58:06Z
- cmd: `git show --stat b86af3c 4e992d2 74cf7b6 | grep -E '^\s|files? changed'`
- 결과: 세 커밋 모두 `procurement/src/**`·`config/quality/gate-tests.properties` 파일만 담는다 — `fixtures/**`·
  `docs/**`·`build-logic/**`·curator evidence 혼입 0건.

역방향 파급 grep·F-6~F-8의 상세는 라운드 1 절차와 동일해 재실행 결과가 바뀌지 않는다(신규
파일 `DateTimeInterpretation.kt`·`NoticeFacts.kt` 포함 재확인 — 추가 매치 0). 판단이 갈린
지점·알려진 제한·병렬 레인 경계 검사 상세는 `checklist.md`에 있다.
