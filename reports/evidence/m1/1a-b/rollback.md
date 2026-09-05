# Rollback — M1 / 1A-b

**비활성화 스위치는 두지 않는다.** 게이트를 끄는 조건 분기를 만드는 것 자체가 이 slice의
위협 모델 우회다(scope.md). 되돌림은 in_scope 경로를 base 상태로 되돌리는 것뿐이다.

## 명령 (임시 clone 실측 — `commands.md` 「rollback 재실측」절 참고, verifier r1 B-2 정정:
이전 판은 정책 점검 절을 잘못 가리켰다)

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
  config/quality/gate-tests.properties \
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

## 실측 (임시 clone)

**결과를 여기 수치로 옮겨 적지 않는다** — evidence-pack 규격("명령이 내는 셈을 산문에
옮겨 적기" 금지). 이전 판이 "16 D + 10 M = 26"을 박아 적었고, HEAD 가 이동한 뒤 verifier
r1(B-3)이 재실행해 "16 D + 11 M = 27"을 얻어 낡은 좌표가 됐다 — 이 round 는 `gate-tests.
properties` 가 목록에 더해져 다시 달라진다. 실제 값은 `commands.md` 「rollback 재실측」절의
명령을 그대로 돌려 확인한다. 판정 기준은 수치가 아니라: exit 0, 그리고 되돌린 뒤
`git diff <base> -- <in_scope 경로>` 가 빈 출력이다.

## 하네스 경로 — 되돌리지 않는다

`CLAUDE.md`·`.claude/**`: 착수 시점 기준 이 range 안 변경 없음(scope.md 「하네스 레인 변경」
절). 되돌릴 대상이 없다.

## 문서 갱신(세션 모델 소관 — 의도적 제외, verifier r1 B-4)

scope.md in_scope 는 아래 문서 셋도 열거하지만(「계약 갱신」행), 이 rollback 목록은 **의도적으로
뺀다** — CLAUDE.md 운영자 지시(계획/명세 문서는 세션 모델 단독 저작)에 따라 구현 레인이 쓴
파일이 아니기 때문이다:

- `docs/discovery/capability-map.md`(⑦, 커밋 `5af47a2`)
- `docs/adr/0007-test-pyramid-and-ratchet.md`(§5 해소 절 포인터, 팀 리드 커밋 `cfcb023`)
- `milestone-1.md`(「Slice 1A-b」 항목, 팀 리드 커밋 `cfcb023`)
- `reports/evidence/m1/1a-b/scope.md`(D-6 등 등재, 팀 리드 커밋 `58004d8`)

**주의(B-4 가 지적한 점) — 위 rollback 명령을 돌리면 코드·게이트 배선은 base 로 돌아가지만
이 넷은 그대로 남는다.** 그러면 ADR 0007 §5·milestone-1 「Slice 1A-b」가 "배선 완료"를
계속 주장하는데 실제 배선은 없는 상태가 된다 — 이 slice 를 완전히 되돌리려면 이 넷도
같은 `git restore --source=<base> --staged --worktree --` 로 별도 되돌려야 하지만, 그 결정은
문서 저자(세션 모델/팀 리드)의 몫이라 이 rollback.md 는 명령에 포함하지 않는다.

## 예상 복구 시간

명령 1회(초 단위) + `./gradlew --no-daemon clean check` 재검증(약 20~30초, H-0/H-1 실측
기준) — 5분 이내.

## 검증 방법

되돌린 뒤: (1) `git diff <base> -- <in_scope 경로>` 가 비어 있다 (2) `git status --short --
CLAUDE.md .claude/` 가 HEAD 그대로다 (3) `./gradlew --no-daemon --no-build-cache clean
check` 를 재실행해 이 slice 이전 baseline(sizeGate 두 축·typeShapeGate 부재·CPD 미배선·
testFixtures 무방비) 상태로 돌아갔음을 확인한다.
