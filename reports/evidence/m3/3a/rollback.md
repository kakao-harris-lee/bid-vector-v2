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
  app/src/test/kotlin/bidvector/app/conformance/CorpusExecutors.kt \
  app/src/test/kotlin/bidvector/app/conformance/StrategyExecutors.kt \
  app/src/test/kotlin/bidvector/app/conformance/ProvenanceFloorExecutors.kt \
  config/quality/gate-tests.properties
```

`--source=<base>`에 없는 신규 경로(`procurement/` 전체, `shared-kernel/.../NoticeRound.kt`)는 `--worktree`가
삭제로 처리한다 — 별도 `git rm`이 필요 없다.

## 임시 clone 실측(재측정, verifier r2 N-6 — head `74cf7b6f33950ca7c09306f73aad1f3e9081c8b1`)

## 2026-09-07T06:54:38Z
- cmd:
  ```bash
  rm -rf /tmp/m3-3a-r2-rollback-check && git clone --no-hardlinks --quiet . /tmp/m3-3a-r2-rollback-check && \
  cd /tmp/m3-3a-r2-rollback-check && git checkout --quiet 74cf7b6f33950ca7c09306f73aad1f3e9081c8b1 && \
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
    app/src/test/kotlin/bidvector/app/conformance/CorpusExecutors.kt \
    app/src/test/kotlin/bidvector/app/conformance/StrategyExecutors.kt \
    app/src/test/kotlin/bidvector/app/conformance/ProvenanceFloorExecutors.kt \
    config/quality/gate-tests.properties && \
  git status --porcelain -- procurement/ shared-kernel/ decision/ app/ config/
  ```
- exit: 0
- 핵심 결과(N-6 정정판 — base 에 이미 있던 `procurement/build.gradle.kts`·`ModuleBoundaryAnchor.kt`는
  **남는다**, 신규 도메인 파일만 삭제된다): `procurement/src/main/.../{Accounting,AmountResolutionOutcome,
  BusinessCategory,Canonicalize,CollectionPolicy,DateTimeInterpretation,DetailFetch,FieldContract,
  NoticeFacts,NoticeId,NoticeStatus,Ports,RawObservation,ResolvedBaseAmount}.kt`(14개)와
  `procurement/src/test/.../*Test.kt`(9개) 삭제(D, 신규 파일이라 `status`에는 잡히지 않고 파일 자체가
  없어짐) + `ModuleBoundaryAnchor.kt`는 그대로 남음. `shared-kernel/.../NoticeRound.kt` 삭제.
  `procurement/build.gradle.kts`·`Provenance.kt`·`Rate.kt`·`gate-tests.properties` 등 base 에 이미 있던
  파일은 base 내용으로 원복(`git diff <base>` 가 그 파일들에서 0줄). clone 은 확인 뒤 폐기.

## 확인 지점

되돌린 뒤 `git diff a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f -- <in_scope 경로>` 가 비어 있고,
`gate.tests.procurement` 키가 없어진 `config/quality/gate-tests.properties` 가 base 와 동일하며,
하네스 경로(`CLAUDE.md`·`.claude/**`)는 손대지 않아 HEAD 그대로다.

## 예상 복구 시간

수 분 이내(코드 삭제 + `./gradlew :app:test --tests '*Conformance*'` 로 나머지 다섯 축 corpus 회귀 재확인).
DB write·외부 API 배선이 없어 데이터 마이그레이션 롤백은 해당 없다.
