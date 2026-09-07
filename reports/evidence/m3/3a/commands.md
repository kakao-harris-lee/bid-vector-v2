# 실행 명령과 종료 코드 — M3 / 3A

base `a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f`, 구현 head(r0) `b0b40cd`, 수정 라운드 1 head
`a9383b7`, 수정 라운드 2 head `74cf7b6f33950ca7c09306f73aad1f3e9081c8b1`, 3A 잔여 일괄(①~④)
head `649866fc55b365c9e786dd5610f8f13b082b8df2`, **3A 잔여 일괄 v2-defect 수정 head**
`04d103c5d0efa332516c9e24061f54be7777c07c`(코드·게이트 커밋 기준 — 이 evidence 커밋 자신은
항상 한 칸 뒤진다). verifier r1 은 `_workspace/m3-3a/02_verifier_report.md`, r2(N-1~N-7)는
`_workspace/m3-3a/03_verifier_report_r2.md` 참고. 출력 전문은 남기지 않는다(evidence-pack
스킬 규격) — 핵심 결과 한 줄만.

## v2-defect 수정 — team-lead 판정(2026-09-07) 뒤 commit 목록

team-lead 가 판정 필요 7건(002·003·004·016·018·023·026) 전부를 v2-defect 로 확정하고
verifier r3 전 production 수정을 지시했다. 「계약 안 항목이라... 값을 맞추려 runner 를
손대지 말고 production 을 고친 뒤 dispatch 예외 목록에서 빼라」는 지시를 그대로 따랐다.

| 커밋 | 내용 |
| --- | --- |
| `3e387b0` | 003·004 — `RawValue`(`Present`/`ExplicitNull`)·`FieldPresence` 신설, `RawNoticeObservation.presenceOf` |
| `2e1b051` | 023 — `NoticeNumber` 정규화를 trim 넘어 ASCII 대문자화·내부 공백→`-` 로 확장 |
| `3b247da` | 002·016·018·026 — `RangeBand`/`ContractViolationAxis.RANGE`·`DocumentedVocabulary`·`FieldScale.DELIMITED_LIST`+`UnnormalizedFigure`·`DateTimePatternId`. 넷을 한 커밋으로 묶은 이유는 커밋 메시지 본문에 있다(공유 정책 인프라 파일, 중간 분할 시 컴파일 불가) |
| `04d103c` | dispatch — `KonepsCollectionDefectFixExecutors.kt` 신설, 27 case 전건 dispatch, `KONEPS_COLLECTION_PENDING_CAPABILITY` 를 빈 집합으로 정정 |

## v2-defect 수정 — acceptance_commands 재실행(head `04d103c`)

### S-0 — 격리 worktree `clean check`
## 2026-09-07T00:00:00Z
- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 347 actionable tasks 전부 실행. `git worktree remove --force`로 정리, 잔여 없음 확인.

### S-1 — 저장소 루트 `clean check`
## 2026-09-07T00:00:00Z
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 338 actionable tasks. 이 재실행에서 `ArchitectureGateTest`
  실패를 잡았다 — `NoticeNumber.of` 의 `String.uppercase()`(인자 없음)가 컴파일된
  바이트코드에서 `java.util.Locale.ROOT` 를 참조해 domain 허용 목록 밖이었다(`java.util`
  허용 예외 62종에 `Locale` 없음). `LicensePolicy.kt` `stripAndLowercase` 와 같은 기법
  (CharArray 직접 조립, ASCII 전용)으로 닫았다 — `:procurement:check`(모듈 단독)는 이
  위반을 못 잡는다(`ArchitectureGateTest` 는 9 모듈이 조합되는 `app` 모듈에만 있다),
  전체 `clean check` 재실행이 왜 필수인지의 실측 사례다.

### S-2 — 도메인 test
## 2026-09-07T00:00:00Z
- cmd: `./gradlew :procurement:test`
- exit: 0
- 핵심 결과: 94 tests, 0 failed, 0 error(v2-defect 6건의 신규 test 20건 포함, 이전
  라운드 78건 대비 증가분).

### S-3 — 도메인 게이트 5종
## 2026-09-07T00:00:00Z
- cmd: `./gradlew :procurement:domainApiTypeGate :procurement:domainSourceReferenceGate :procurement:typeShapeGate :procurement:sizeGate :procurement:cpdCheck`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`.

### S-4 — app conformance
## 2026-09-07T00:00:00Z
- cmd: `./gradlew :app:test --tests '*Conformance*'`
- exit: 0
- 핵심 결과: 69 tests, 0 failed. **koneps-collection 27/27 case dispatch 통과**
  (기존 20 + v2-defect 수정으로 새로 연 7). `KONEPS_COLLECTION_PENDING_CAPABILITY`
  는 이제 빈 집합 — 그 잠금 test 가 그 상태를 잡는다.

### S-5 — quality baseline
## 2026-09-07T00:00:00Z
- cmd: `./gradlew qualityBaseline`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`.

### S-6 — 적대 스윕
## 2026-09-07T00:00:00Z
- cmd: `python3 fixtures/tools/mutation_sweep_adversarial.py`
- exit: 0
- 핵심 결과: 강등 대상 0, 잔존 authoritative 68(manifest 미편집 라운드라 변화 없음).

## 3A 잔여 일괄 — commit 목록(4 커밋 단위, verifier r3 전)

| 단위 | 내용 | 커밋 |
| --- | --- | --- |
| ① 정책 값 채움 | `KONEPS_COLLECTION_POLICY` 를 운영자 승인 2026-09-07 값(필드 계약 10·resultCode 16·해석 순서 둘·gate)으로 채우고 `CollectionPolicyTest`(8 test) 로 승인 표와 대조 | `33acf1a` |
| ② corpus dispatch | `KonepsCollectionExecutors.kt`/`KonepsCollectionAccountingExecutors.kt` 로 20 case dispatch, dispatch 완전성 test·7건 예외 잠금 test 추가 | `dfae9a8`, `a85724f`(case027 REAL_POLICY 대조 보강) |
| ③ manifest `contract_binding` | koneps-collection 27 case 의 `contract_binding` 필드만 pending-3a → 실제 채움(`fixtures/manifest.yaml` 이 필드 외 무편집, 아래 「manifest 편집 범위 검증」) | `27ad8dd` |
| — 게이트 수정 | `clean check`(S-1) 재실행에서 발견한 sizeGate(617줄)·detekt(MaxLineLength 7건)·ktlint 위반을 닫는다(파일 분할 + 줄바꿈 + 자동 포맷) | `649866f` |
| ④ evidence | 이 커밋(commands.md·checklist.md 갱신) | (이 커밋) |

`fixtures/tools/mutation_sweep_adversarial.py`의 `ASSERTED`/`NULL_ASSERTED`/`ADVERSARIAL_VALUE`
표는 curator 커밋 `5acd5f0`(이 배치 이전, 같은 날)이 koneps-collection 27 case 전건을 이미
담고 있어 이 배치는 그 파일을 편집하지 않았다(S-6 재확인만 함).

## manifest 편집 범위 검증(③)
## 2026-09-07T00:00:00Z(시각은 로컬 커밋 순서 참고용 — 정본은 git log)
- cmd: `git show --stat 27ad8dd`
- 핵심 결과: `fixtures/manifest.yaml` 1개 파일만 변경.
- cmd: `git diff 5acd5f0..27ad8dd -- fixtures/manifest.yaml | grep "^[-+].*id: " | grep -v koneps-collection`
- exit: 1(grep 매치 없음 관례), 출력 없음 — koneps-collection 27건 외 case id 가 diff 에 등장하지 않는다.
- cmd: `git diff 5acd5f0..27ad8dd -- fixtures/manifest.yaml | grep -E "^[-+]" | grep -v "^+++\|^---" | grep -iE "sha256|verified_paths|expected_file|input_file|classification:|domain:|^\+    source:|change_history|extracted_at|privacy|normalization"`
- exit: 0, 매치 2건 — 둘 다 신설 `contract_binding` 본문 안에서 "verified_paths"라는 낱말을
  산문으로 인용한 줄이지 실제 `verified_paths:` 필드 변경이 아니다(원문 대조로 확인). 이
  둘을 빼면 `contract_binding` 필드 밖의 다른 필드는 손대지 않았다.

## 정책 값 비교 결과(①)
## 2026-09-07T00:00:00Z
- cmd: `./gradlew --no-daemon :procurement:test --tests 'bidvector.procurement.CollectionPolicyTest'`
- exit: 0
- 핵심 결과: 8 tests, 0 failed — 필드 계약 10건(이 커밋 `33acf1a` 시점 수치. v2-defect 018
  수정(`3b247da`)이 `cnstrtnAbltyEvlAmtList` 를 더해 **현재는 11건** — verifier r3 N3-9
  정정) 항목 수·`presmptPrce`(EXCLUSIVE·WON·ESTIMATED)·
  `asignBdgtAmt`/`bdgtAmt`(UNKNOWN·ALLOCATED_BUDGET·FILLED_FROM_BUDGET_KEY)·`bssamt` 단독
  기초금액 키·해석 순서 둘(`bssamt→asignBdgtAmt→bdgtAmt`, `presmptPrce`)·resultCode 16건
  (`03`→NO_DATA·`08`→INPUT_ERROR·`22`→QUOTA_EXCEEDED·`30`→NOT_RETRYABLE)·일시 두 필드
  `ASSUME_KST`·gate `24h/48h` 를 `policy-values.md` §6 승인 표와 실값으로 대조해 전부 일치.
  기존 `init` 불변식(추정가격 순서에 기초금액 키 없음, 해석 순서 키 전건 등재, resultCode
  중복 없음)도 실값 위에서 재확인(생성 성공 자체가 그 증거).

## 3A 잔여 일괄 — acceptance_commands 재실행(head `649866f`)

### S-0 — 격리 worktree `clean check`
## 2026-09-07T00:00:00Z
- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 347 actionable tasks 전부 실행. `git worktree remove --force`로 정리, 잔여 없음 확인(`git worktree list`).

### S-1 — 저장소 루트 `clean check`
## 2026-09-07T00:00:00Z
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 338 actionable tasks(sizeGate·detekt·ktlint 위반 셋을 이 재실행에서 발견해 `649866f`로 닫았다 — 위 표).

### S-2 — 도메인 test
## 2026-09-07T00:00:00Z
- cmd: `./gradlew :procurement:test`
- exit: 0
- 핵심 결과: 78 tests, 0 failed, 0 error(신설 `CollectionPolicyTest` 8건 포함).

### S-3 — 도메인 게이트 5종
## 2026-09-07T00:00:00Z
- cmd: `./gradlew :procurement:domainApiTypeGate :procurement:domainSourceReferenceGate :procurement:typeShapeGate :procurement:sizeGate :procurement:cpdCheck`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL` — `CollectionPolicy.kt` 의 필드 계약 열을 `FieldContractRow` 위치
  인자 표로 재구성해 `cpdCheck`(named-arg 반복 50토큰 초과 중복)를 닫았다.

### S-4 — app conformance
## 2026-09-07T00:00:00Z
- cmd: `./gradlew :app:test --tests '*Conformance*'`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 62 tests, 0 failed — koneps-collection 20 case 신규 통과 포함,
  기존 다섯 축 회귀 무손상. **case 통과 수: koneps-collection 20/27 dispatch, 27/27 케이스
  분류(20 dispatch 통과 + 7 판정 필요 예외로 명시 등재, `dispatch 표 밖의 authoritative case
  가 없다` 완전성 test 가 그 7건 구성을 잠근다)**.

### S-5 — quality baseline
## 2026-09-07T00:00:00Z
- cmd: `./gradlew qualityBaseline`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`.

### S-6 — 적대 스윕(manifest 편집 전후 둘 다 실행)
## 2026-09-07T00:00:00Z
- cmd: `python3 fixtures/tools/mutation_sweep_adversarial.py`(manifest 편집 `27ad8dd` 전)
- exit: 0
- 핵심 결과: 강등 대상 0, 잔존 authoritative 68.
## 2026-09-07T00:00:00Z
- cmd: `python3 fixtures/tools/mutation_sweep_adversarial.py`(manifest 편집 `27ad8dd` 후, head `649866f`)
- exit: 0
- 핵심 결과: 강등 대상 0, 잔존 authoritative 68(변화 없음 — `contract_binding` 편집은 이
  스윕이 읽는 `ASSERTED`/`verified_paths`/기대값에 영향을 주지 않는다).

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
