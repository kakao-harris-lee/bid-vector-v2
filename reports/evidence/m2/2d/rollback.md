# M2/2D — rollback.md

## 되돌리는 것

`contractGate`(build-logic task + 배선) · `contract-policy.properties` · breaking mutation
증명 스크립트(`contracts/tools/breaking-mutations.sh`) · Kotlin 계약 test 여섯(unknown
field·max payload·deadline/cancellation/retry·다중 서비스·교차 언어 스모크 client) ·
`crossLangSmokeTest` task 배선 · Python 계약 test(`test_contract_resilience.py`) ·
`grpcio-testing` dev 의존 · `grpc-netty-shaded` 카탈로그 좌표 · `PresentSpec`(build-logic).

**게이트를 걷는 것은 계약을 걷는 것이 아니다** — `contracts/proto/**`(2A~2C 승인 `.proto`)와
`ml-contract` 생성 배선(2A)은 이 slice 가 만들지 않았고 되돌리지 않는다. 되돌리면 2C 종결
시점(base_sha `458dce7`) 상태로 돌아간다 — `contractGate`·breaking mutation 증명·2D
계약 test 없이 2A~2C 산출물만 남는다.

## 되돌리는 방법 — in_scope 경로 한정(range revert 아님)

```sh
git restore --source=458dce7 --staged --worktree -- \
  contracts/buf.yaml contracts/tools contracts/testdata \
  build-logic \
  config/quality/gate-tests.properties config/quality/contract-policy.properties \
  adapters/src/test/kotlin adapters/build.gradle.kts \
  ml-engine/tests ml-engine/pyproject.toml \
  gradle/libs.versions.toml \
  tools
```

`--source`에 없는 신규 경로(`contract-policy.properties`·`contracts/tools/**`·
`contracts/testdata/breaking/**`·`ContractGateTask.kt` 등)는 이 명령이 자동으로 삭제한다
(2C 시점엔 존재하지 않았다) — 별도 `git rm`이 필요 없다. `git checkout <base> -- <경로>`는
쓰지 않는다(base 에 없는 신규 경로마다 pathspec 오류로 exit 1).

**하네스 경로(`CLAUDE.md`·`.claude/**`)와 이 range 에 섞인 다른 세션의 커밋
(`4695ce8` — M5 discovery 문서, 이 slice 의 in_scope 밖)은 되돌리지 않는다.**

## 확인 지점

- `git diff 458dce7 -- <위 in_scope 경로>` 가 비어 있다(0줄).
- `./gradlew --no-build-cache clean check`가 `BUILD SUCCESSFUL`(2C 상태에서도 통과 — 게이트를
  걷어도 2A~2C 게이트·test 는 그대로 살아 있다).
- `adapters/src/test/kotlin/bidvector/adapters/contract/`에 2D 파일 여섯(Contract
  DeadlineCancellationRetryTest·ContractMaxPayloadTest·ContractPolicySupport·
  ContractRetryRules·ContractUnknownFieldPreservationTest·CrossLangSmokeTest·
  MultiServiceContractTest)이 없다.

## 실측(임시 clone, commands.md 「rollback 실측」 절)

`git clone --no-local` 로 만든 격리 clone 에서 위 명령을 실행해 `git diff 458dce7`가
0줄임을 확인했고, 그 상태에서 `./gradlew --no-build-cache clean check`가 `BUILD
SUCCESSFUL`임을 확인했다(둘 다 실제 실행, dry-run 아님).

## 예상 복구 시간

명령 자체는 수 초. `./gradlew --no-build-cache clean check` 재확인까지 포함하면 이
저장소 기준 약 30~40초(실측, S-0/rollback 재확인 값).
