# M2/2E commands.md

정본은 명령과 exit code, 핵심 결과 한 줄이다. 출력 전문은 재현 명령으로 대신한다.
base `8b50461016232386e456be532fab4eed4299fcfb`, head는 리뷰 시점의 HEAD — 값을 박지
않는다(2A verifier r1 F-1 관례).

## RED — 생성 stub 부재로 컴파일/import 실패

- cmd: `./gradlew --offline :adapters:compileTestKotlin`
- exit: 1(실측)
- 핵심 결과: `EmbeddingContractTest.kt`가 참조하는 `contract.bidvector.ml.v1.EmbeddingServiceGrpcKt`·
  `EmbedTextRequest`·`Embedding` 등 63건의 `Unresolved reference`(embedding.proto 부재로 생성
  안 됨).

- cmd: `(cd ml-engine && .venv/bin/python -m pytest tests/test_embedding_contract.py -q)`
- exit: 1(실측, 전건 error)
- 핵심 결과: `grpc_tools.protoc` 생성 fixture 가 `contracts/proto/bidvector/ml/v1/embedding.proto`
  부재로 `RuntimeError`(생성 실패, exit=1) — 24/24 error.
- **worktree 비고**: 이 worktree(`bid-vector-v2-m4e`)에는 `ml-engine/.venv`가 없어
  `python3.12 -m venv .venv && .venv/bin/python -m pip install -e ".[dev]"`로 새로 만들었다
  (pip 네트워크는 2D 결정으로 이미 승인됨, `pyproject.toml` dev 의존 주석).

## S-2 — buf lint/build (embedding.proto 신설 후)

- cmd: `(cd contracts && buf lint && buf build)`
- exit: 0
- 핵심 결과: lint 위반 0, build 성공.

## `breaking-mutations.sh` — 신설 파일 포함 스윕(조사 §5, 설계 검토 (5) 5)

`buf breaking`은 **승인 태그에 없는 파일**을 비교 기준으로 못 삼는다 — `embedding.proto`는
승인 태그 시점에 존재하지 않아, 11종 mutation 대부분(파일 내부 필드·enum·rpc 변경)을
그 파일 자체에 적용해도 breaking으로 안 잡힌다(원래 없던 것의 형태 변경이라서). 전건
대상인 것은 `package-rename`뿐이다(`FILE_SAME_PACKAGE`가 모듈 안 파일 전체에 거는 규칙).
`apply_mutation`의 package-rename for-loop에 `embedding.proto`를 추가하지 않으면, mutation
적용 뒤 그 파일만 옛 패키지에 남아 **다른 파일이 참조하는 타입을 못 찾는 COMPILE 오류**로
막혀 원래 증명하려던 breaking 규칙 자체가 가려진다 — 반증(추가 전 상태 재현) 실측:

- cmd: (embedding.proto를 package-rename for-loop 밖에 둔 스크래치에서)
  `buf breaking <scratch> --against <HEAD contracts>`
- exit: 0(그러나 finding 5건 전부 `"type":"COMPILE"`, `embedding.proto`가 `PredictionEnvelope`·
  `ModelRelease`·`ApplicationFailure`·`RequestEnvelope`를 못 찾음 — genuine breaking 판정이
  아니라 컴파일 오류)
- 핵심 결과: 이것이 `embedding.proto`를 for-loop 에 추가해야 하는 이유의 직접 증거.

- cmd: (`embedding.proto` 포함 for-loop, 실제 반영분)
  `buf breaking <scratch> --against <HEAD contracts>`
- exit: 0
- 핵심 결과: 6개 파일 전부 `FILE_SAME_PACKAGE`(genuine breaking rule) — `embedding.proto`도
  독립적으로 그 finding 을 낸다(스윕 대상 포함 확인).

## S-3 (사전 확인) — 승인 태그 대비 breaking 0

- cmd: `buf breaking contracts --against '.git#tag=contracts/v1-approved-2026-09-07,subdir=contracts'`
- exit: 0
- 핵심 결과: `embedding.proto` 신설(6번째 파일)이 additive로만 잡힘 — breaking 0.

## S-3 — breaking mutation 스윕(전건)

- cmd: `(cd contracts && ./tools/breaking-mutations.sh)`
- exit: 0
- 핵심 결과: 11/11 mutation 잡힘(`expected.tsv`의 규칙 이름 전부 무변경 — `package-rename`도
  여전히 `FILE_SAME_PACKAGE`, `--update` 불필요), 양성 대조 통과.

## testdata 생성(재현 절차) — `contracts/testdata/embedding/`

- cmd: (cwd `contracts/testdata/embedding`) JSON 원본 5개를 손으로 작성 후
  `buf convert ../.. --type bidvector.ml.v1.<Type> --from <name>.json --to <name>.binpb`
  를 5개 타입(`EmbedTextRequest`·`EmbedTextResponse`×3·`GetEmbeddingMetadataResponse`)에
  대해 실행(2B 관례) — 모듈 루트는 `contracts`(cwd 기준 `../..`), 저장소 루트 아님.
- exit: 0(5/5)
- 핵심 결과: canonical `.binpb` 5개 + JSON 원본 5개 커밋.

## testdata round-trip 자체 검증 — JSON → binpb → JSON 의미 동등

- cmd: `buf convert ../.. --type bidvector.ml.v1.<Type> --from <name>.binpb --to -#format=json`
  후 원본 JSON과 `jq -S` 정렬 비교(5개 전건)
- exit: 0(5/5 의미 동등)
- 핵심 결과: 3개는 완전 일치, 2개(`ApplicationFailure` 실패 표본 둘)는 `retryable: false`
  (bool 기본값)가 canonical JSON 출력에서 생략됨 — 2B 실측과 동일한 proto3 기본값 필드
  elision(표준 동작, 결함 아님).

## GREEN — Kotlin consumer test

- cmd: `./gradlew --offline :adapters:test --tests '*EmbeddingContractTest*' --tests '*MultiServiceContractTest*'`
- exit: 0
- 핵심 결과: `EmbeddingContractTest` 19/19, `MultiServiceContractTest` 1/1(3서비스 공존) 통과.
- **버그 실측 1**: 반복 스칼라 필드(`values: repeated float`)는 protoc-gen-java 가
  `removeXxx(index)`를 생성하지 않는다(메시지 타입 반복 필드에만 생성 — 2B `removeCandidates`
  와 차이). `clearValues()` + `addAllValues(축소 목록)`로 교정.
- **버그 실측 2**: `dimension`(uint32→Kotlin `Int`)에 불필요한 `.toInt()`가
  `-Werror`(Redundant call of conversion method)로 컴파일 실패. 제거.

## S-6 — 교차 언어 socket 스모크(로컬 실측 1회, D-2D-3 (a))

- cmd: `./tools/contract-crosslang-smoke.sh`
- exit: 0
- 핵심 결과: `crosslang_smoke_server.py`가 `EmbeddingService`를 셋째 서비스로 등록(embedding.proto
  추가)해도 기존 `crossLangSmokeTest`(2B/2C 왕복, Kotlin `CrossLangSmokeTest`는 out_of_scope라
  EmbedText 를 부르지 않음)가 그대로 통과 — 서비스 이름 충돌 없음.

## GREEN — Python provider test

- cmd: `(cd ml-engine && .venv/bin/python -m pytest tests/test_embedding_contract.py tests/test_contract_roundtrip.py -q)`
- exit: 0
- 핵심 결과: 33/33 통과(신규 24 + 2A round-trip 9), 첫 실행부터 초록(재작업 없음).

## S-1 — `--no-build-cache clean check`(전 모듈)

- cmd: `./gradlew --no-build-cache clean check` (1차)
- exit: 1
- 핵심 결과: `:adapters:detekt` FAILED — `isAcceptableEmbedding` `ReturnCount`(조기 return 2 +
  최종 1 = 3, 한도 2) 위반. and 체인 단일 return 으로 교정(커밋 `94ef756`).

- cmd: `./gradlew --no-build-cache clean check` (2차)
- exit: 1
- 핵심 결과: `:adapters:ktlintTestSourceSetCheck` FAILED — 체인 호출 4곳
  "Expected newline before '.'". `ktlintTestSourceSetFormat` 자동 교정(커밋 `c43fd9e`).

- cmd: `./gradlew --no-build-cache clean check` (3차)
- exit: 0
- 핵심 결과: 344 tasks(319 executed), `BUILD SUCCESSFUL`. `:adapters:check`·`:contractGate`
  포함 전 모듈 통과.

## S-0 — 격리 worktree 전건(clean check)

- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: (실행 후 기록 — S-1 3차 통과 확인 뒤 HEAD 고정 후 실행)

## secret 스캔

- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m2/2e/`
- exit: (실행 후 기록)
- 핵심 결과: (실행 후 기록)
