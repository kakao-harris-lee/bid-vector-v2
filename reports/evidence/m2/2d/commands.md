# M2/2D — commands.md

base_sha: `458dce7` · head_sha: `9bf762eb83167b2bc1c70241a2639e3ed12b001f`

## Preflight

### 2026-09-07T00:00:00Z
- cmd: `buf --version`
- exit: 0
- 핵심 결과: `1.72.0`(정책 `tool.buf.version`과 일치)

### 2026-09-07T00:05:00Z
- cmd: `buf breaking contracts --against '.git#tag=contracts/v1-approved-2026-09-07,subdir=contracts'` (승인 태그 대비 baseline 확인, mutation 적용 전)
- exit: 0
- 핵심 결과: 승인 태그 대비 현재 계약이 breaking 없음(baseline 정상)

## S-0 — 격리 worktree 전체 check

### 2026-09-07T09:10:00Z
- cmd: `git worktree add --detach <tmp-dir> HEAD && (cd <tmp-dir> && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 332 actionable tasks 전건 executed(캐시 미재사용, 격리 확인)

## S-1 — 전체 clean check(작업 트리)

### 2026-09-07T08:50:00Z
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, contractGate 포함 전건 통과

## S-2 — contractGate

### 2026-09-07T02:30:00Z
- cmd: `./gradlew contractGate`
- exit: 0
- 핵심 결과: `violations=0`(`build/reports/contract-gate/violations.txt`)

### 2026-09-07T02:32:00Z (음성 대조 — 승인 태그를 무효값으로)
- cmd: `sed -i '' 's/^approved.tag=.*/approved.tag=not-a-real-tag/' config/quality/contract-policy.properties && ./gradlew contractGate`
- exit: 1
- 핵심 결과: 이름 규칙 위반 + `buf breaking` 실패(`couldn't find remote ref`) 둘 다 잡힘 — 값 복원 후 재확인 exit 0

## S-3 — breaking mutation 증명

### 2026-09-07T03:00:00Z
- cmd: `(cd contracts && ./tools/breaking-mutations.sh)`
- exit: 0
- 핵심 결과: 11/11 mutation 잡힘 + 양성 대조(호환 변경 넷) 통과 — 결과표 `contracts/testdata/breaking/expected.tsv`

## S-4 — Kotlin 계약 test

### 2026-09-07T09:05:00Z
- cmd: `./gradlew :adapters:test --tests '*Contract*'`
- exit: 0
- 핵심 결과: 113 tests 전건 통과(5회 반복 재실행으로 flake 없음 확인)

### 2026-09-07T04:10:00Z (경계 test 초기 flake 진단)
- cmd: `./gradlew :adapters:test --tests '*Contract*' --rerun` (반복 5회)
- exit: 1(2/5), 이후 수정 뒤 0(5/5)
- 핵심 결과: `ContractMaxPayloadTest`의 초과 표본 거부가 RESOURCE_EXHAUSTED/CANCELLED 로 갈림(실측) — 두 코드 모두 허용하도록 수정 후 안정화

## S-5 — Python 계약 test

### 2026-09-07T05:40:00Z
- cmd: `(cd ml-engine && source .venv/bin/activate && python -m pytest tests -q)`
- exit: 0
- 핵심 결과: 95 passed(3회 반복 재실행 확인)

### 2026-09-07T05:20:00Z (grpcio-testing aio 지원 실측)
- cmd: `./ml-engine/.venv/bin/python -c "import grpc_testing; print(dir(grpc_testing))"`
- exit: 0
- 핵심 결과: `grpc_testing.aio` 모듈 없음 — `OPEN-2D-AIO-INPROCESS` 닫힘(대체안: 실제 loopback 서버)

## S-6 — 교차 언어 socket 스모크

### 2026-09-07T07:15:00Z
- cmd: `./tools/contract-crosslang-smoke.sh`
- exit: 0
- 핵심 결과: Python 서버 기동 + Kotlin client 로 CalculateOptimalBid·StartTraining 각 1회 성공

## S-7 — quality baseline

### 2026-09-07T09:20:00Z
- cmd: `./gradlew qualityBaseline`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`(측정 task, 게이트 아님)

## 회귀 확인 — 게이트가 실제로 잡는지 6종 실측(orchestrator 지시, 전부 원복 후 재확인 exit 0)

### 2026-09-07T10:30:00Z (1 — mutation 스크립트 빈 집합)
- cmd: `sed -i '' 's/^breaking.mutations=.*/breaking.mutations=/' config/quality/contract-policy.properties && (cd contracts && ./tools/breaking-mutations.sh)`
- exit: 1
- 핵심 결과: `0/0 mutation 잡힘, 최소 요구 11` → `breaking.mutations.min` 미달로 실패. **부수 발견**: macOS 기본 `/bin/bash`(3.2.57)가 빈 배열 `"${ARR[@]}"`를 `set -u`에서 "unbound variable"로 오판하는 버그를 밟아 스크립트를 수정(`c67dc28`, `set -uo pipefail` → `set -o pipefail` + `MUTATIONS=()` 명시 초기화)

### 2026-09-07T10:35:00Z (2 — against 를 승인 태그 밖 값으로)
- cmd: `sed -i '' 's/^approved.tag=.*/approved.tag=HEAD/' config/quality/contract-policy.properties && ./gradlew contractGate`
- exit: 1
- 핵심 결과: `approvedTagViolation` — `HEAD`가 정책 이름 규칙(`^contracts/v[0-9]+-approved-...$`)을 만족하지 못해 즉시 위반

### 2026-09-07T10:40:00Z (3 — max_message_bytes 를 한쪽만 내림)
- cmd: `ContractMaxPayloadTest`의 server 쪽 `maxInboundMessageSize`만 `maxMessageBytes - 1000`으로 임시 수정 후 `./gradlew :adapters:test --tests bidvector.adapters.contract.ContractMaxPayloadTest`
- exit: 1
- 핵심 결과: "선언값 바로 아래 표본은 양쪽에서 수용된다" 가 실패 — server·channel 값 불일치를 경계 쌍이 잡음

### 2026-09-07T10:45:00Z (4 — unknown enum 거부를 한쪽만 제거)
- cmd: `PredictionContractTest`의 `isAcceptableCandidateLabel`에서 `UNRECOGNIZED` 비교를 제거한 뒤 `./gradlew :adapters:test --tests bidvector.adapters.contract.PredictionContractTest`
- exit: 1
- 핵심 결과: "CandidateLabel 정의 밖 정수는 거부된다" 실패 — fail-closed 거부 test 가 잡음

### 2026-09-07T10:50:00Z (5 — 도구 버전 리터럴 어긋남)
- cmd: `sed -i '' 's/^tool.buf.version=.*/tool.buf.version=1.71.0/' config/quality/contract-policy.properties && ./gradlew contractGate`
- exit: 1
- 핵심 결과: `toolVersionViolation` — 실측 `1.72.0` 과 정책 값 불일치로 즉시 위반

### 2026-09-07T10:55:00Z (6 — ml-contract 에 손으로 쓴 소스 추가)
- cmd: `mkdir -p ml-contract/src && echo '// sneaky' > ml-contract/src/Foo.kt && ./gradlew contractGate`
- exit: 1
- 핵심 결과: `includedBuildSourcePresenceViolation`("ml-contract/src 가 존재한다") + 작업 트리 청결 위반 둘 다 잡힘 — `OPEN-2A-INCLUDED-BUILD` 게이트화 확인

## secret 스캔

### 2026-09-07T09:55:00Z
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m2/2d/`
- exit: 1(매치 없음 = 통과)
- 핵심 결과: 매치 0건

### 2026-09-07T09:56:00Z
- cmd: `git diff 458dce7..HEAD -- adapters build-logic config contracts gradle ml-engine tools | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
- exit: 1(매치 없음 = 통과)
- 핵심 결과: 매치 0건

## clean-tree 게이트(in_scope 경로, 개별 인자)

### 2026-09-07T10:00:00Z
- cmd: `git status --porcelain -- contracts/buf.yaml contracts/tools contracts/testdata build-logic config/quality/gate-tests.properties config/quality/contract-policy.properties adapters/src/test/kotlin adapters/build.gradle.kts ml-engine/tests ml-engine/pyproject.toml .github/workflows milestone-2.md reports/evidence/m2/2d gradle/libs.versions.toml tools`
- exit: 0
- 핵심 결과: 출력 없음(청결) — 양성 대조로 `contracts/buf.yaml` 에 한 줄 추가 후 같은 명령이 ` M contracts/buf.yaml` 을 잡음을 확인, 복원 후 재확인 exit 0

## rollback 실측(임시 clone)

### 2026-09-07T10:15:00Z
- cmd: `git clone --no-local <repo> <tmp>; cd <tmp>; git checkout 9bf762e; git restore --source=458dce7 --staged --worktree -- <in_scope 경로 개별 인자>`
- exit: 0
- 핵심 결과: `git diff 458dce7 -- <in_scope 경로>` 0줄(base 와 완전 일치)

### 2026-09-07T10:20:00Z
- cmd: (같은 임시 clone) `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: rollback 뒤(2C 상태)에서도 `BUILD SUCCESSFUL` — 게이트를 걷어도 계약(.proto)·2A~2C 산출물은 온전
