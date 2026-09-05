# Rollback — M1 / 1A-b

**비활성화 스위치는 두지 않는다.** 게이트를 끄는 조건 분기를 만드는 것 자체가 이 slice의
위협 모델 우회다(scope.md). 되돌림은 in_scope 경로를 base 상태로 되돌리는 것뿐이다.

## 명령 (임시 clone 실측 완료 — `commands.md` 정책 점검 절 참고)

```sh
BASE=e0aae7f2242f3cffe8f4c32bae2ecec7a3b89b46
git restore --source="$BASE" --staged --worktree -- \
  app/src/test/kotlin/bidvector/app/architecture/ArchitectureGateTest.kt \
  app/src/test/kotlin/bidvector/app/conformance/SharedKernelCorpusConformanceTest.kt \
  build-logic/build.gradle.kts \
  build-logic/src/main/kotlin/bidvector.kotlin-conventions.gradle.kts \
  build-logic/src/main/kotlin/bidvector.quality-baseline.gradle.kts \
  build-logic/src/main/kotlin/bidvector/buildlogic/CpdReportPresenceGateTask.kt \
  build-logic/src/main/kotlin/bidvector/buildlogic/DuplicatePolicy.kt \
  build-logic/src/main/kotlin/bidvector/buildlogic/MeasuredType.kt \
  build-logic/src/main/kotlin/bidvector/buildlogic/QualityBaselineTask.kt \
  build-logic/src/main/kotlin/bidvector/buildlogic/SizeGateTask.kt \
  build-logic/src/main/kotlin/bidvector/buildlogic/TypeShape.kt \
  build-logic/src/main/kotlin/bidvector/buildlogic/TypeShapeGateTask.kt \
  build-logic/src/test/kotlin/bidvector/buildlogic/CpdReportPresenceTest.kt \
  build-logic/src/test/kotlin/bidvector/buildlogic/DuplicatePolicyTest.kt \
  build-logic/src/test/kotlin/bidvector/buildlogic/TestFixturesGateTest.kt \
  build-logic/src/test/kotlin/bidvector/buildlogic/TypeMembersTest.kt \
  build-logic/src/test/kotlin/bidvector/buildlogic/TypeShapeFixtureTest.kt \
  build-logic/src/test/kotlin/bidvector/buildlogic/TypeShapeRatchetPolicyTest.kt \
  build-logic/src/test/kotlin/bidvector/buildlogic/typeshapefixture/TypeShapeFixtures.kt \
  config/quality/architecture-policy.properties \
  config/quality/duplicate-policy.properties \
  config/quality/size-policy.properties \
  gradle/libs.versions.toml \
  shared-kernel/src/test/kotlin/bidvector/sharedkernel/CompileFailureHarnessTest.kt \
  shared-kernel/src/test/resources/compile-fixtures/mutant-13-base-amount-default-less-typo.kt.txt \
  shared-kernel/src/test/resources/compile-fixtures/negative-13-base-amount-default-less.kt.txt \
  shared-kernel/src/test/resources/compile-fixtures/positive-13-base-amount-default-less.kt.txt
```

**신규 파일은 `--source`에 없으므로 이 한 명령이 그대로 삭제한다** — 별도 `git rm` 불필요
(evidence-pack 규격). `git checkout <base> --`는 쓰지 않는다(base 에 없는 신규 경로마다
pathspec 오류로 exit 1).

## 실측 (임시 clone, `commands.md` 정책 점검 절)

- exit: 0
- 결과: 신규 파일 16개 삭제(D) + 기존 파일 10개 복원(M) = 총 26개, `git diff <base> --
  <in_scope 경로>` 는 빈 출력(완전 복원 확인)

## 하네스 경로 — 되돌리지 않는다

`CLAUDE.md`·`.claude/**`: 착수 시점 기준 이 range 안 변경 없음(scope.md 「하네스 레인 변경」
절). 되돌릴 대상이 없다.

## 문서 갱신(세션 모델 소관, 이 slice 산출물 아님)

`docs/discovery/capability-map.md`(⑦, 커밋 `5af47a2`)·`reports/evidence/m1/1a-b/scope.md`
갱신(D-6 등, 커밋 `58004d8`)은 팀 리드/세션 모델이 쓴 것이라 이 rollback 대상이 아니다 —
in_scope 목록에도 없다(구현 레인이 편집한 파일에 한정).

## 예상 복구 시간

명령 1회(초 단위) + `./gradlew --no-daemon clean check` 재검증(약 20~30초, H-0/H-1 실측
기준) — 5분 이내.

## 검증 방법

되돌린 뒤: (1) `git diff <base> -- <in_scope 경로>` 가 비어 있다 (2) `git status --short --
CLAUDE.md .claude/` 가 HEAD 그대로다 (3) `./gradlew --no-daemon --no-build-cache clean
check` 를 재실행해 이 slice 이전 baseline(sizeGate 두 축·typeShapeGate 부재·CPD 미배선·
testFixtures 무방비) 상태로 돌아갔음을 확인한다.
