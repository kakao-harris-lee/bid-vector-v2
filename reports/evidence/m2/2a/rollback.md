# M2/2A rollback.md

되돌리는 대상: `includeBuild("ml-contract")` 한 줄 + `adapters`의 test 의존 다섯 줄 +
`ResolvedDependencies.kt`의 분류 정정 한 분기 + `contracts/`·`ml-contract/`·`ml-engine/`
신규 디렉터리 셋. 생성물(`ml-contract/build/`, `.venv/` 등)은 VCS 밖이라 걷을 것이 없다.

**range revert가 아니라 in_scope 경로 한정이다** — `git revert 040ab9d..HEAD`는 같은
range의 다른 병행 레인 커밋(M2 착수 기록, M3/M4/M5 준비 초안 — commands.md 「하네스 레인
변경」 절)까지 되돌린다. 아래 명령은 **경로 개별 인자**로만 되돌린다.

## 명령 (임시 clone에서 실측 완료 — commands.md 「rollback 실측」)

```sh
git restore --source=040ab9d --staged --worktree -- \
  settings.gradle.kts \
  adapters/build.gradle.kts \
  gradle/libs.versions.toml \
  config/quality/gate-tests.properties \
  .gitignore \
  build-logic/src/main/kotlin/bidvector/buildlogic/ResolvedDependencies.kt \
  contracts/buf.yaml \
  contracts/proto/bidvector/ml/v1/common.proto \
  contracts/proto/bidvector/ml/v1/error.proto \
  contracts/testdata/money.binpb \
  contracts/testdata/rate.binpb \
  contracts/testdata/request_envelope.binpb \
  contracts/testdata/prediction_envelope.binpb \
  contracts/testdata/unmeasurable.binpb \
  contracts/testdata/application_failure.binpb \
  ml-contract/settings.gradle.kts \
  ml-contract/build.gradle.kts \
  ml-contract/gradle.properties \
  build-logic/src/test/kotlin/bidvector/buildlogic/ResolvedDependenciesTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/contract/ContractRoundTripTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/contract/GrpcKotlinStackSmokeTest.kt \
  ml-engine/pyproject.toml \
  ml-engine/tests/conftest.py \
  ml-engine/tests/test_contract_roundtrip.py
```

`--source`에 없는 경로(전부 2A 신규 산출물)는 이 명령이 **삭제**한다 — 별도 `git rm` 불요.
공유 파일 6개(`settings.gradle.kts` 등)는 base 상태로 되돌아간다.

`git checkout 040ab9d -- <경로>`는 쓰지 않는다 — base에 없는 신규 경로마다 pathspec
오류로 exit 1이고 아무것도 적용되지 않는다(evidence-pack 스킬이 이미 경고한 함정,
Codex 1A 16차 high와 같은 클래스 — 이 slice에서는 `git restore`를 처음부터 채택해
그 실패를 재현하지 않았다).

## 확인 지점

1. `contracts/`·`ml-contract/`·`ml-engine/` 디렉터리가 존재하지 않는다.
2. `git diff 040ab9d -- settings.gradle.kts adapters/build.gradle.kts gradle/libs.versions.toml config/quality/gate-tests.properties .gitignore build-logic/src/main/kotlin/bidvector/buildlogic/ResolvedDependencies.kt`
   출력이 빈 문자열.
3. `./gradlew :adapters:moduleDependencyGate` 성공(2A 이전 구조로 복귀해도 기존 게이트가
   깨지지 않음).

## 예상 복구 시간

명령 하나(수 초) + 확인 명령 셋(수십 초). Gradle 전건 재검증까지 포함하면 2분 내외
(`--no-configuration-cache` 최초 1회 기준).

## 부분 되돌림이 필요한 경우

- `ResolvedDependencies.kt`의 분류 정정만 되돌리면 `adapters:moduleDependencyGate`가
  다시 실패한다(이 정정이 없으면 composite 치환 의존이 project 의존으로 오분류되기
  때문 — 원 버그 재현). **이 파일만 단독으로 되돌리는 것은 권장하지 않는다** — 되돌리려면
  `ml-contract` includeBuild와 `adapters`의 test 의존도 함께 걷어야 일관된 상태다.
- `contracts/testdata/*.binpb`만 지우면 `ContractRoundTripTest`·Python `conftest.py`
  round-trip test가 파일 없음으로 실패한다 — round-trip test 여섯 파일과 함께 걷는다.
