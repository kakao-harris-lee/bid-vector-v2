# M2/2D — commands.md

base_sha: `e3ac98b`(scope.md 와 정합, verifier r1 F-4) · head_sha: `0a71e71`(verifier r2
수정 라운드 완료 — 커밋 목록은 맨 끝 「verifier r2 수정 라운드 요약」 절)

**verifier r2 F-16** — 이 필드는 항상 "이 문서를 갱신하는 커밋 직전의 마지막 코드
커밋"을 가리킨다(evidence 문서 자신의 커밋은 코드에 영향이 없어 head 로 세지 않는다).
그래서 이 문서를 커밋하는 순간 실제 git HEAD 는 여기 적힌 값보다 하나 앞선다 — 이
어긋남 자체가 매 라운드 재발하는 구조적 성질이므로, 검증은 이 필드가 아니라 각 커밋의
실제 SHA(아래 각 절의 회귀 실측·「수정 라운드 요약」)로 한다.

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

### verifier r1 F-1(high) 재현 + 수정 확인
- cmd: (수정 전 코드로, 격리 사본을 게이트와 같은 방식으로 만들어) `./gradlew --offline --project-dir <isolated>/ml-contract clean generateProto` 두 번 연속
- exit: 0(둘 다)
- 핵심 결과: 두 회차 모두 `> Task :generateProto FROM-CACHE` — 같은 캐시 엔트리 복원본 둘을 비교해 결정성 검사가 구조적으로 실패할 수 없음을 재현 확인
- cmd: (수정 후, 중첩 호출에 `--no-build-cache` 추가) 같은 격리 사본에서 같은 명령 두 번 연속
- exit: 0(둘 다)
- 핵심 결과: 두 회차 모두 `> Task :generateProto`(캐시 표시 없음) — 실제 protoc 실행
- cmd: `./gradlew --offline --project-dir build-logic test --tests bidvector.buildlogic.ContractGateChecksTest`
- exit: 0
- 핵심 결과: 신설 `빈 산출물 집합 둘은 위반이다(F-1)` 포함 전건 통과
- cmd: `./gradlew contractGate`(warm build cache 상태에서)
- exit: 0
- 핵심 결과: `violations=0` — 캐시가 따뜻해도 결정성 검사가 실제 재실행을 강제함을 확인

## S-3 — breaking mutation 증명

### 2026-09-07T03:00:00Z
- cmd: `(cd contracts && ./tools/breaking-mutations.sh)`
- exit: 0
- 핵심 결과: 11/11 mutation 잡힘 + 양성 대조(호환 변경 넷) 통과 — 결과표 `contracts/testdata/breaking/expected.tsv`

### verifier r1 수정 뒤 재실행(F-8·F-9 반영 — `caught` 판정을 exit 100 만으로, `sed_inplace` 이식)
- cmd: `(cd contracts && ./tools/breaking-mutations.sh)`
- exit: 0
- 핵심 결과: 11/11 그대로(exit code 판정 방식만 바뀌었고 실측 결과는 불변), `expected.tsv` diff 없음(결정적 재생성 확인)

### verifier r2 F-14(medium, 게이트 술어) 재현 + 수정 확인
- cmd: (수정 전 코드로) `field-delete`의 `sed_inplace` 패턴을 `s/Currency currency = 2;/@@@ BROKEN @@@/`로 임시 교체 후 `(cd contracts && ./tools/breaking-mutations.sh)`
- exit: 0
- 핵심 결과: `[잡힘] field-delete (exit=100)` — 구문을 깬 mutation 이 거짓으로 "잡힘" 처리됨(verifier 지적 재현 확인)
- cmd: (수정 후, `--error-format=json`의 `"type"` 필드로 `COMPILE` 을 가름) 같은 임시 교체 상태에서 같은 명령
- exit: 2
- 핵심 결과: `buf 도구 오류(exit=100 이지만 컴파일 오류 — breaking 규칙 위반이 아니다)` — 구문 파괴를 정확히 도구 오류로 분류, 즉시 중단(패턴 원복 후 재확인)
- cmd: (원복 뒤) `(cd contracts && ./tools/breaking-mutations.sh)`
- exit: 0
- 핵심 결과: 11/11 그대로, `expected.tsv`에 신설 `rule_type` 열 — 11종 각각 서로 다른 진짜 규칙(`FIELD_NO_DELETE`·`FIELD_SAME_TYPE`·`ENUM_VALUE_NO_DELETE`·`RPC_NO_DELETE`·`RPC_SAME_SERVER_STREAMING`·`FILE_SAME_PACKAGE`·`RESERVED_MESSAGE_NO_DELETE`·`FIELD_SAME_CARDINALITY`), 양성 대조 그대로 `passed 0`

### verifier r2 수정 라운드 최종 S-3 재확인(head `0a71e71`)
- cmd: `(cd contracts && ./tools/breaking-mutations.sh)`
- exit: 0
- 핵심 결과: `11/11 mutation 잡힘, 최소 요구 11` — 격리 worktree(S-0)·작업 트리(S-1) 양쪽 `--no-build-cache clean check`도 이 head 에서 재확인(`BUILD SUCCESSFUL`)

## S-4 — Kotlin 계약 test

### 2026-09-07T09:05:00Z
- cmd: `./gradlew :adapters:test --tests '*Contract*'`
- exit: 0
- 핵심 결과: 113 tests 전건 통과(5회 반복 재실행으로 flake 없음 확인)

### 2026-09-07T04:10:00Z (경계 test 초기 flake 진단)
- cmd: `./gradlew :adapters:test --tests '*Contract*' --rerun` (반복 5회)
- exit: 1(2/5), 이후 수정 뒤 0(5/5)
- 핵심 결과: `ContractMaxPayloadTest`의 초과 표본 거부가 RESOURCE_EXHAUSTED/CANCELLED 로 갈림(실측) — 두 코드 모두 허용하도록 수정 후 안정화

### verifier r1 F-13 확인(protoc-gen-grpc-java 버전 대조 신설)
- cmd: `./gradlew --offline --project-dir build-logic test --tests bidvector.buildlogic.ContractPolicyTest`
- exit: 0
- 핵심 결과: `tool.protoc.gen.grpc.java` 읽기·`protocGenGrpcJavaVersion` 대조 포함 전건 통과
- cmd: `./gradlew contractGate`
- exit: 0
- 핵심 결과: `violations=0` — 카탈로그 `grpc-java`(1.84.0)와 정책 값이 일치

### verifier r2 F-17(low) — S-4 를 build cache 복원이 아니라 실제 실행으로 기록
- cmd: `./gradlew :adapters:test --tests '*Contract*' --rerun-tasks`(head `0a71e71`)
- exit: 0
- 핵심 결과: `27 actionable tasks: 27 executed`(캐시 표시 없음) — 이번 실행이 실제로 test 를 돌렸다는 서술이 성립. 이 명령을 플래그 없이 다시 돌리면 `FROM-CACHE`로 복원될 수 있다(F-2 와 같은 갈래, checklist 알려진 제한 10번) — S-1 이 매번 `--no-build-cache`로 같은 test 를 실제 실행하므로 acceptance 전체로는 덮인다

## S-5 — Python 계약 test

### 2026-09-07T05:40:00Z
- cmd: `(cd ml-engine && source .venv/bin/activate && python -m pytest tests -q)`
- exit: 0
- 핵심 결과: 95 passed(3회 반복 재실행 확인)

### 2026-09-07T05:20:00Z (grpcio-testing aio 지원 실측)
- cmd: `./ml-engine/.venv/bin/python -c "import grpc_testing; print(dir(grpc_testing))"`
- exit: 0
- 핵심 결과: `grpc_testing.aio` 모듈 없음 — `OPEN-2D-AIO-INPROCESS` 닫힘(대체안: 실제 loopback 서버)

### verifier r1 F-6 재현 + 수정 확인(fixture 부작용 의존)
- cmd: (수정 전 코드로) `(cd ml-engine && .venv/bin/python -m pytest tests/test_contract_resilience.py -q -k local_parse_reserialize)`
- exit: 1
- 핵심 결과: `ModuleNotFoundError: No module named 'bidvector'` — module fixture 를 안 받아 생성물이 `sys.path`에 없음(검증자 실측 재현)
- cmd: (수정 후, 함수가 `prediction_pb2` fixture 를 인자로 받음) 같은 명령
- exit: 0
- 핵심 결과: `1 passed, 7 deselected` — 단독 선택도 정상화

### verifier r1 F-7 수정 확인(항진명제 → 실제 wire 스캔)
- cmd: `(cd ml-engine && .venv/bin/python -m pytest tests/test_contract_resilience.py -q)`
- exit: 0
- 핵심 결과: 8 passed — `_top_level_field_numbers`(자체 varint 파서)로 응답의 top-level field number 가 `DESCRIPTOR.fields`의 알려진 집합 안에만 있고 999 가 없음을 직접 관측(이전의 "재직렬화 길이 자기 비교" 항진명제 제거)

### verifier r1 수정 라운드 최종 S-5 재확인
- cmd: `(cd ml-engine && source .venv/bin/activate && python -m pytest tests -q)`
- exit: 0
- 핵심 결과: 95 passed(개수 불변 — F-6·F-7 은 기존 test 를 고쳤을 뿐 추가·삭제 없음)

## S-6 — 교차 언어 socket 스모크

### 2026-09-07T07:15:00Z
- cmd: `./tools/contract-crosslang-smoke.sh`
- exit: 0
- 핵심 결과: Python 서버 기동 + Kotlin client 로 CalculateOptimalBid·StartTraining 각 1회 성공

### verifier r1 F-2 수정 확인(원 결함은 verifier r1 보고서 §5 F-2 실측을 그대로 수령)
- cmd: (수정 후, `--rerun-tasks --no-build-cache` 추가) `./tools/contract-crosslang-smoke.sh` 두 번 연속
- exit: 0(둘 다)
- 핵심 결과: 두 실행 모두 로그에 `> Task :adapters:crossLangSmokeTest`(캐시 표시 없음, `FROM-CACHE` 아님) — 매번 실제 socket 왕복

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

### verifier r1 F-11 — 재현 가능한 형태로 정정
이전 판은 `grep -rniE "..." reports/evidence/m2/2d/`(전체 디렉터리)를 "매치 0건"으로
적었으나, `commands.md`·`checklist.md` 자신이 이 패턴을 명령·finding 설명으로 인용해
자기 참조 매치가 난다(실제 secret 아님, 육안 확인). 재현 가능한 형태로 그 두 파일을
제외한다.

### 2026-09-07T09:55:00Z (자기 참조 포함 원본 스캔 — 참고용)
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m2/2d/`
- exit: 0
- 핵심 결과: 매치 5건, 전부 `commands.md`·`checklist.md`가 이 스캔 자체를 설명하는 줄(육안 확인, 실제 secret 없음)

### 2026-09-07T09:56:00Z (자기 참조 제외 — 정본)
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m2/2d/ --exclude=commands.md --exclude=checklist.md`
- exit: 1(매치 없음 = 통과)
- 핵심 결과: 매치 0건

### 2026-09-07T09:57:00Z
- cmd: `git diff e3ac98b..HEAD -- adapters build-logic config contracts gradle ml-engine tools | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
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

### verifier r1 F-4·F-5 — base_sha 를 `e3ac98b`(scope.md)로 정합 뒤 재실측
- cmd: `git diff e3ac98b 458dce7 --stat`
- exit: 0
- 핵심 결과: `milestone-2.md`·`reports/evidence/m2/2c/checklist.md`·`reports/evidence/m2/2d/scope.md` 3개 문서만 — 코드/게이트 경로 무접촉(두 SHA 중 어느 쪽을 기준으로 잡아도 되돌리기 결과가 같음을 확인)
- cmd: `git clone --no-local <repo> <tmp>; cd <tmp>; git checkout <HEAD>; git restore --source=e3ac98b --staged --worktree -- <in_scope 경로 개별 인자, rollback.md 목록>`
- exit: 0
- 핵심 결과: `git diff e3ac98b -- <경로>` 0줄
- cmd: (같은 임시 clone) `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 331 actionable tasks 전건 executed

## verifier r1 수정 라운드 요약

커밋(전부 `main`, base `e3ac98b`, head `d32a591`):
1. `5ed8049` — F-1(high): 결정성 검사 중첩 호출에 `--no-build-cache` + 빈 집합 위반화
2. `e2afa2a` — F-2(medium): S-6 스크립트에 `--rerun-tasks --no-build-cache`
3. `d32a591` — low 일괄: F-4·F-5·F-6·F-7·F-8·F-9·F-10·F-12·F-13(F-3·F-11 은 문서/스캔
   방식 정정으로 별도 처리, 코드 변경 없음)

### 최종 acceptance 전건 재실행(head `d32a591`)

| # | 명령 | exit | 비고 |
| --- | --- | --- | --- |
| S-0 | 격리 worktree `--no-build-cache clean check` | 0 | 332 tasks 전건 executed |
| S-1 | `./gradlew --no-build-cache clean check`(작업 트리) | 0 | 323 tasks |
| S-2 | `./gradlew contractGate` | 0 | `violations=0`, F-13 신설 대조 포함 |
| S-3 | `(cd contracts && ./tools/breaking-mutations.sh)` | 0 | 11/11, F-8·F-9 반영 |
| S-4 | `./gradlew :adapters:test --tests '*Contract*'` | 0 | 전건 통과 |
| S-5 | `(cd ml-engine && .venv/bin/python -m pytest tests -q)` | 0 | 95 passed |
| S-6 | `./tools/contract-crosslang-smoke.sh` | 0 | F-2 반영, 실제 socket 실행 확인(캐시 표시 없음) |
| S-7 | `./gradlew qualityBaseline` | 0 | — |

### 최종 secret 스캔·clean-tree(head `d32a591`, `commands.md` 자체 편집 중 제외)

- `grep -rniE "..." reports/evidence/m2/2d/ --exclude=commands.md --exclude=checklist.md` → exit 1(매치 0건)
- `git status --porcelain -- <in_scope 경로 개별 인자>` → 출력 없음(이 커밋 전 확인, `commands.md` 자체 편집만 남은 상태)

## verifier r2 수정 라운드 요약

커밋(전부 `main`, base `e3ac98b`):
1. `0a71e71` — F-14(medium, 게이트 술어): `caught` 판정에 `--error-format=json`의 `"type"`
   대조를 더해 컴파일 오류(`COMPILE`)와 진짜 breaking 규칙을 가른다. `expected.tsv`에
   `rule_type` 열 신설.

F-15(in_scope 미선언)는 세션 모델이 `466b191`로 scope.md 를 정정했다(`adapters/
build.gradle.kts`·`gradle/libs.versions.toml` 추가) — 이 라운드에서 코드 변경 없음.
F-16(head 어긋남)·F-17(S-4 캐시 복원 가능)은 이 문서 자체와 위 S-4 절 갱신으로 처리했다.

### 최종 acceptance 전건 재실행(head `0a71e71`)

| # | 명령 | exit | 비고 |
| --- | --- | --- | --- |
| S-0 | 격리 worktree `--no-build-cache clean check` | 0 | 332 tasks 전건 executed |
| S-1 | `./gradlew --no-build-cache clean check`(작업 트리) | 0 | 323 tasks |
| S-2 | `./gradlew contractGate` | 0 | `violations=0` |
| S-3 | `(cd contracts && ./tools/breaking-mutations.sh)` | 0 | 11/11, F-14 반영(rule_type 열) |
| S-4 | `./gradlew :adapters:test --tests '*Contract*' --rerun-tasks` | 0 | 27 tasks 전건 executed(F-17 반영) |
| S-5 | `(cd ml-engine && .venv/bin/python -m pytest tests -q)` | 0 | 95 passed |
| S-6 | `./tools/contract-crosslang-smoke.sh` | 0 | 실제 socket 실행(캐시 표시 없음) |
| S-7 | `./gradlew qualityBaseline` | 0 | — |

### 최종 clean-tree(head `0a71e71`, `commands.md` 자체 편집 중 제외)

- `git status --porcelain -- contracts/buf.yaml contracts/tools contracts/testdata build-logic config/quality/gate-tests.properties config/quality/contract-policy.properties adapters/src/test/kotlin adapters/build.gradle.kts ml-engine/tests ml-engine/pyproject.toml .github/workflows milestone-2.md reports/evidence/m2/2d gradle/libs.versions.toml tools` → 출력 없음(`commands.md` 편집만 남은 상태에서 확인)
