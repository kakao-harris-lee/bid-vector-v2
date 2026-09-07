# Rollback — M3 / 3A

base `a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f`. 되돌림은 **range revert 가 아니라 in_scope 경로 한정**이다
(evidence-pack 스킬, 2026-09-04 규약) — 하네스 경로(`CLAUDE.md`·`.claude/**`)는 이 range 에 없다(scope.md
「하네스 레인 변경」절: 착수 시점 없음, 3A 구현 커밋 5개 모두 in_scope 경로만).

## 되돌리는 대상

**정정(verifier r2 N-6)** — `procurement` 모듈 자체는 base 에 이미 있었다(`procurement/build.gradle.kts`·
`ModuleBoundaryAnchor.kt`, `settings.gradle.kts` 의 `include("procurement")`도 base 에 있다 — M2 단계의
빈 골격). 이 slice 가 신설·수정한 것은 그 골격 **안의 도메인 코드 전부**(`ModuleBoundaryAnchor.kt` 제외한
`procurement/src/**` 신규 파일)와, shared-kernel 의 좁은 타입 교체(`Provenance.Published`·
`FloorRateOrigin.NoticeValue`의 `noticeRevision` 타입) 뿐이다. **활성 배선(스케줄·runtime 등록)은 없다** —
`procurement`는 아직 어떤 어댑터도 구현하지 않고, `app` 모듈은 이 모듈을 compile/test 의존으로도 물지
않았다(dispatch 미등록). 따라서 "끄는 flag"가 아니라 **경로 원복**이 곧 비활성화다.

## 명령(개별 경로 인자)

**3A 잔여 일괄 갱신** — app 쪽 경로를 개별 파일로만 나열해 왔는데, 이번 배치가 그 디렉터리에
새 파일 둘(`KonepsCollectionExecutors.kt`·`KonepsCollectionAccountingExecutors.kt`)을 더하고
`SharedKernelCorpusConformanceTest.kt`(TARGET_DOMAINS·완전성 test·`atDollarPath` 배열 인덱스
지원)를 편집했다 — 개별 나열 방식은 새 파일을 원천적으로 못 담으므로 여기서 아예 그
**디렉터리 전체**로 바꾼다. `app/build.gradle.kts`(scope.md in_scope, `testImplementation(
project(":procurement"))` 한 줄)도 이번 배치에서 처음 추가돼 목록에 없었다 — 더한다.

```bash
git restore --source=a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f --staged --worktree -- \
  procurement/ \
  shared-kernel/src/main/kotlin/bidvector/sharedkernel/NoticeRound.kt \
  shared-kernel/src/main/kotlin/bidvector/sharedkernel/Provenance.kt \
  shared-kernel/src/main/kotlin/bidvector/sharedkernel/Rate.kt \
  shared-kernel/src/test/kotlin/bidvector/sharedkernel/ArithmeticTest.kt \
  shared-kernel/src/test/kotlin/bidvector/sharedkernel/MoneyTest.kt \
  shared-kernel/src/test/kotlin/bidvector/sharedkernel/RateArithmeticTest.kt \
  shared-kernel/src/test/kotlin/bidvector/sharedkernel/UndeclaredProvenanceTest.kt \
  decision/src/test/kotlin/bidvector/decision/FloorShortfallKernelTest.kt \
  app/src/test/kotlin/bidvector/app/conformance/ \
  app/build.gradle.kts \
  config/quality/gate-tests.properties
```

`--source=<base>`에 없는 신규 경로(`procurement/` 전체, `shared-kernel/.../NoticeRound.kt`,
`app/src/test/.../conformance/` 안의 koneps 실행자 둘)는 `--worktree`가 삭제로 처리한다 —
별도 `git rm`이 필요 없다. `CorpusExecutors.kt`·`StrategyExecutors.kt`·
`ProvenanceFloorExecutors.kt`·`SharedKernelCorpusConformanceTest.kt`는 base 에 이미 있던
파일이라(1B~1E 산출물) `--source=base`가 그 파일들을 **base 시점 내용으로 원복**한다 —
넷 다 3A 가 실제로 건드렸다: `StrategyExecutors.kt`·`ProvenanceFloorExecutors.kt`는 착수
Phase 0 의 D-3A-0 파급(`noticeRevision`의 `Int` 접힘을 `NoticeRound`로 정정)을,
`CorpusExecutors.kt`는 같은 파급에 더해 이번 배치의 `VALUE_EXECUTORS` 병합을,
`SharedKernelCorpusConformanceTest.kt`는 이번 배치의 `TARGET_DOMAINS`·완전성 test·
`atDollarPath` 배열 인덱스 지원을 담고 있다 — `--source=base`가 넷 전부를 base 시점
내용(D-3A-0 이전, koneps 배선 이전)으로 되돌린다.

## 임시 clone 실측(재측정, 3A 잔여 일괄 — head `649866fc55b365c9e786dd5610f8f13b082b8df2`)

## 2026-09-07T00:00:00Z
- cmd:
  ```bash
  rm -rf /tmp/m3-3a-rollback-check && git clone --no-hardlinks --quiet . /tmp/m3-3a-rollback-check && \
  cd /tmp/m3-3a-rollback-check && git checkout --quiet 649866fc55b365c9e786dd5610f8f13b082b8df2 && \
  git restore --source=a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f --staged --worktree -- \
    procurement/ \
    shared-kernel/src/main/kotlin/bidvector/sharedkernel/NoticeRound.kt \
    shared-kernel/src/main/kotlin/bidvector/sharedkernel/Provenance.kt \
    shared-kernel/src/main/kotlin/bidvector/sharedkernel/Rate.kt \
    shared-kernel/src/test/kotlin/bidvector/sharedkernel/ArithmeticTest.kt \
    shared-kernel/src/test/kotlin/bidvector/sharedkernel/MoneyTest.kt \
    shared-kernel/src/test/kotlin/bidvector/sharedkernel/RateArithmeticTest.kt \
    shared-kernel/src/test/kotlin/bidvector/sharedkernel/UndeclaredProvenanceTest.kt \
    decision/src/test/kotlin/bidvector/decision/FloorShortfallKernelTest.kt \
    app/src/test/kotlin/bidvector/app/conformance/ \
    app/build.gradle.kts \
    config/quality/gate-tests.properties && \
  git status --porcelain -- procurement/ shared-kernel/ decision/ app/ config/ && \
  git diff a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f -- procurement/ shared-kernel/src/main/kotlin/bidvector/sharedkernel/NoticeRound.kt shared-kernel/src/main/kotlin/bidvector/sharedkernel/Provenance.kt shared-kernel/src/main/kotlin/bidvector/sharedkernel/Rate.kt app/src/test/kotlin/bidvector/app/conformance/ app/build.gradle.kts config/quality/gate-tests.properties | wc -l
  ```
- exit: 0
- 핵심 결과: `git status --porcelain`이 `procurement/src/main/.../{Accounting,AmountResolutionOutcome,
  BusinessCategory,Canonicalize,CollectionPolicy,DateTimeInterpretation,DetailFetch,FieldContract,
  NoticeFacts,NoticeId,NoticeStatus,Ports,RawObservation,ResolvedBaseAmount}.kt`(14개)·
  `procurement/src/test/.../*Test.kt`(10개, `CollectionPolicyTest.kt` 포함)·
  `shared-kernel/.../NoticeRound.kt`·`app/src/test/.../conformance/{KonepsCollectionExecutors,
  KonepsCollectionAccountingExecutors}.kt`(2개)를 `D`(삭제)로, `app/build.gradle.kts`·
  `procurement/build.gradle.kts`·`Provenance.kt`·`Rate.kt`·`gate-tests.properties`·
  `CorpusExecutors.kt`·`StrategyExecutors.kt`·`ProvenanceFloorExecutors.kt`·
  `SharedKernelCorpusConformanceTest.kt`를 `M`(원복)으로 낸다 — `ModuleBoundaryAnchor.kt`는
  base 에 이미 있어 그대로 남는다. 되돌린 뒤 `git diff <base>`(위 in_scope 경로 전체)가
  **0줄** — 복구가 완전함을 확인했다. clone 은 확인 뒤 폐기.

## 확인 지점

되돌린 뒤 `git diff a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f -- <in_scope 경로>` 가 비어 있고,
`gate.tests.procurement` 키가 없어진 `config/quality/gate-tests.properties` 가 base 와 동일하며,
하네스 경로(`CLAUDE.md`·`.claude/**`)는 손대지 않아 HEAD 그대로다.

## 알려진 제한 — `fixtures/manifest.yaml` 은 이 명령의 대상이 아니다(verifier r3 N3-4)

위 `git restore` 목록에 `fixtures/manifest.yaml` 이 없다 — **의도적**이다. `manifest.yaml`
은 curator 소유(scope.md `out_of_scope`)이고, 3A 구현 레인이 편집할 수 있는 것은 그중
`koneps-collection` 27 case 의 `contract_binding` 필드뿐이다(scope.md 명시 예외). base 로
되돌리면 curator 커밋(`5acd5f0`·`aecddbb` 등, 27 case 의 authoritative 승격 자체)까지
되돌아가 이 rollback 의 의도(3A 산출물만 비활성화)를 넘어선다 — 그래서 `manifest.yaml`
을 이 명령의 대상에 넣지 않았다.

**결과**: in_scope 경로를 되돌린 뒤에도 `contract_binding` 산문은 삭제된
`bidvector.procurement.*` 타입·함수(`RangeBand`·`DocumentedVocabulary`·
`parseDelimitedFigureList` 등)를 계속 가리킨다 — 임시 clone 재실측(HEAD 기준)으로
`bidvector.procurement.` 패턴이 되돌린 뒤에도 15건 남는 것을 확인했다. **기능적으로는
깨지지 않는다** — `contract_binding`은 산문 문서 필드이고, 유일한 기계 검사(manifest
`contract_binding.type_path` 의 「fixture N」표기 대 `COMPILE_DELEGATION_FIXTURES`
숫자 대조)는 그 대조 대상 자체가 이번 rollback 으로 되돌아가는 conformance test 안에
있다. `contract_binding` 을 되돌리려면 **수동 절차**가 필요하다 — 해당 27 case 의
`contract_binding` 필드를 `pending-3a` 로 손으로 되돌리거나(3A 배선 이전 상태), curator
승인 자체를 되돌릴지 별도로 판단해야 한다(그 판단은 이 rollback 의 범위 밖이다).

## 예상 복구 시간

수 분 이내(코드 삭제 + `./gradlew :app:test --tests '*Conformance*'` 로 나머지 다섯 축 corpus 회귀 재확인).
DB write·외부 API 배선이 없어 데이터 마이그레이션 롤백은 해당 없다.
