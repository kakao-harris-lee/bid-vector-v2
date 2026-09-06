# Slice 계약 — M2 / 2A · 공통 값과 오류 계약 — **초안, 구현 전**

> **지위**: M1/1E 병행 중에 세션 모델이 쓴 **계약 초안**이다. 구현·gradle·의존성 추가·fixture 편집은 하지 않았다.
> 착수는 M1 전체 승인 뒤 운영자 별도 지시로 하며, 그때 `base_sha` 를 재고정하고 `prep/m2-prep.md` 「착수 전 결정」
> D-M2-1~14 와 아래 D-2A-0·7 의 답을 받고 **`ADR 0010` 을 승인**(2A 착수 전건 — ④·⑥ 이 그 D-1·D-3 위에 선다)한 뒤
> `milestone-2.md` 착수 문단을 쓴다. **빌드 경계(included build 신설)를 열고 게이트 정의 한 파일(`ResolvedDependencies` 의 의존
> 분류 한 분기)을 정정하는 slice 이므로 Phase 2.5 설계 검토 대상**이다(1A·1A-b 와 같은 급). 게이트 정의 편집은 그 한 분기와
> 게이트 등재(`gate-tests` 한 줄)뿐이고 층·convention·`architecture-policy` 편집은 없다.

```yaml
milestone: m2
slice: 2a-common-values-and-errors
base_sha: 040ab9d   # 착수 2026-09-06 재고정(M1 1E 종결 승인 커밋 = M1 전체 승인). 초안 시점은 c9022d9 였다
head_sha: 48e9980d1efdefa774c3f177463015a373fbf84c   # 구현 완료 시점(2026-09-06). Phase 3 커밋 7개 — 아래 checklist.md 「커밋 목록」
in_scope:
  - contracts/proto/bidvector/ml/v1/common.proto      # 값 타입·enum·봉투(RequestEnvelope 필수 둘 + PredictionEnvelope)
  - contracts/proto/bidvector/ml/v1/error.proto       # ApplicationFailure(FailureCode 전체 어휘 — 2B·2C 가 쓰는 값 포함)·Unmeasurable·oneof 결과 봉투 패턴
  - contracts/buf.yaml                                # lint 규칙(proto3, enum UNSPECIFIED=0, 패키지 `bidvector.ml.v1`, `option java_package` 규칙). `buf.lock` 은 BSR 의존이 없으면 생기지 않는다 — 두지 않는다
  - ml-contract/settings.gradle.kts, ml-contract/build.gradle.kts, ml-contract/gradle.properties   # D-2A-0 (c) — **included build**(`build-logic` 과 같은 형태). proto srcDir 는 `../contracts/proto` 를 **srcDir 참조**로(symlink 금지 — `SourceLanguageGate` 가 링크를 따라간다). `src/` 디렉터리 없음
  - settings.gradle.kts                               # `includeBuild("ml-contract")` 한 줄 — `include(...)` 목록(subprojects)에는 넣지 않는다
  - gradle/libs.versions.toml                         # grpc/protobuf/protoc/grpc-kotlin 버전 리터럴 고정(조사 노트 02). included build 는 루트 카탈로그를 자동으로 보지 못하므로 `ml-contract/settings.gradle.kts` 가 `build-logic` 과 같은 `versionCatalogs { from(files("../gradle/libs.versions.toml")) }` 블록을 갖는다
  - adapters/build.gradle.kts                         # `testImplementation("bidvector:ml-contract")` 한 줄(included build 치환 — round-trip test 가 생성 stub 을 본다). main 의존은 M4 4D
  - build-logic/src/main/kotlin/bidvector/buildlogic/ResolvedDependencies.kt   # **게이트 정의 정정 한 분기** — `resolveDependencies` 가 `ProjectComponentIdentifier` 를 전부 `projectPaths` 로 접어 composite 치환 의존(`bidvector:ml-contract`)이 project 의존으로 분류된다(리뷰 r3 ⓕ). 다른 빌드의 project 는 `externalModules`(`group:name`)로 분류하도록 build 신원을 본다 — 예외가 아니라 분류의 정정. Phase 2.5
  - config/quality/gate-tests.properties              # `gate.tests.adapters` — 2A round-trip test 등재(키는 모듈 이름 규약)
  - ml-engine/pyproject.toml, ml-engine/tests/conftest.py(생성 fixture), ml-engine/tests/test_contract_roundtrip.py   # D-M2-3 (a) — 최소 골격. Python 생성물도 VCS 밖(pytest 가 grpc_tools.protoc 로 임시 생성)
  - adapters/src/test/kotlin/**                        # round-trip test(Kotlin 쪽) — 계약 타입 ↔ 도메인 타입 매핑은 **M4**, 여기서는 wire ↔ wire 만. 손으로 쓰는 test 는 전부 여기
  - milestone-2.md                                    # 「Slice 2A」 착수 문단, **착수 시**
  - reports/evidence/m2/2a/**
out_of_scope:
  - build-logic/** (위 `ResolvedDependencies.kt` 한 분기 제외), config/quality/architecture-policy.properties   # 층 신설 없음, convention 편집 없음, 정책 키 편집 없음. 착수 시 실측이 그 밖의 편집을 요구하면 멈추고 계약 갱신
  - app/src/test/kotlin/bidvector/app/architecture/**  # ArchUnit 등식은 `bidvector` 루트만 세고 생성 패키지 루트는 그 밖(D-2A-0b) — 편집 불요
  - shared-kernel/**, decision/**, qualification/**, strategy/**, procurement/**, settlement/**, workflow/**   # 도메인은 계약 타입을 보지 못한다(ADR 0006 D-6)
  - adapters/src/main/**                              # 도메인 ↔ 계약 매핑·client 배선은 M4 4D
  - prediction.proto, features.proto, training.proto  # 2B·2C
  - breaking-change gate 의 **증명**·unknown field/max payload/deadline/cancellation test·fake servicer·`ml-contract` 무소스 단언   # 2D — 2A 는 lint 와 round-trip 까지
  - ml-engine 의 features/training/inference/serving  # M5
  - 인증·TLS·네트워크 배치                              # M6 6C
  - fixtures/**                                       # 2A 의 round-trip 표본은 `contracts/testdata/` 의 canonical 바이트 — fixture corpus 와 별개 축
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1 — 기존 게이트 전건 초록 + included build 가 `adapters` 의 test 의존으로 빌드됨
  - "./gradlew :adapters:moduleDependencyGate && grep -E '^bidvector:ml-contract' adapters/build/reports/module-dependency-gate/resolved.txt && ! grep -E '^projects=.*ml-contract' adapters/build/reports/module-dependency-gate/resolved.txt"   # S-1b — 치환 의존이 external 좌표 줄(`group:name:version` 한 줄씩)에 있고 `projects=` 목록에는 없음(ⓕ 정정의 실측; `external=` 줄은 개수라 앵커로 쓰지 않는다)
  - "git worktree add --detach <dir> HEAD && (cd <dir> && printf '\\ndependencies { implementation(\"bidvector:ml-contract\") }\\n' >> shared-kernel/build.gradle.kts && ! ./gradlew :shared-kernel:moduleDependencyGate)"   # S-1c — 양성 대조: domain 모듈에 같은 의존을 임시 선언하면 `external.allowed.domain` 밖이라 게이트가 **실패해야** 한다(임시 worktree, 원본 무접촉)
  - "(cd contracts && buf lint && buf build)"                                                          # S-2 — lint·컴파일(네트워크 없이 — buf 1.72.0 로컬 완결)
  - "./gradlew :adapters:test --tests '*ContractRoundTrip*'"                                          # S-3 — included build 생성(build/ 아래) + round-trip
  - "./gradlew --project-dir ml-contract build && ./gradlew --project-dir ml-contract build && git status --porcelain -- ml-contract contracts"   # S-4 — 두 번 빌드해도 VCS 무접촉(빈 출력) — 생성물 비커밋 실측
  - "test ! -e ml-contract/src && find ml-contract -maxdepth 1 -type f | sort"                        # S-5 — 손으로 쓴 소스 0(빌드 파일 셋만) — 게이트 밖 빌드의 유일한 내용물이 빌드 스크립트임을 실측
  - "(cd ml-engine && python -m pytest tests/test_contract_roundtrip.py -q)"                          # S-6 — Python 임시 생성 + round-trip(Gradle check 밖 — CI 에 Python 툴체인 없음, 알려진 제한)
  - "./gradlew qualityBaseline"                                                                        # S-7
rollback: |
    **정본은 `reports/evidence/m2/2a/rollback.md`**(착수 시 작성 — 경로 한정 restore + 신규 경로 rm, 임시 clone 실측).
    `includeBuild("ml-contract")` 한 줄과 `adapters` 의 test 의존 한 줄을 걷으면 나머지 빌드는 2A 이전과 같다. 생성물은 VCS 에 없어 걷을 것이 없다.
```

작성: 2026-09-06, 세션 모델(Fable 5.1) 단독. 근거는 `prep/m2-prep.md` 머리의 목록과 같다. **리뷰 r1·r2(PR #2, 2026-09-06) 반영**: 게이트 가족 충돌은
subproject 형태 자체가 원인이라 **included build** 로 재설계(D-2A-0), provenance 축 분리(①·③), `latest_promoted` 구멍(⑥), `FailureCode` 단독 소유(⑤),
`Rate` scale 한 자리(②), `RateSource` → origin(D-2A-7), 봉투 분리(④).

---

## 하네스 레인 변경 (상시 절)

`git log --oneline 040ab9d..HEAD -- CLAUDE.md .claude/` — 착수 시점(2026-09-06) **없음**.

**착수 2026-09-06 — 운영자 결정**: `ADR 0010` 승인(§4 셋 추천안 = D-M2-12~14 (a)) · `prep/m2-prep.md` D-M2-1~11 전부 추천안 ·
**D-2A-0 (c)** included build + `ResolvedDependencies` 분류 정정 한 분기 · **D-2A-7 (a)** 율의 source 는 wire 필드가 아니라
이름·`origin` · 환경 설치(buf 1.72.0 Homebrew, `ml-engine` 가상환경의 grpcio-tools 1.83.1)는 이 세션이 진행. Phase 2.5 는
준비 세션의 리뷰 r1~r4(`_workspace/m2-prep/03~06_review_*.md`, PR #2)가 게이트 가족·의존 분류·봉투·어휘 축을 검토한 것으로
갈음하고, 위협 모델 (0) 경계 문장은 이 문서 「위협 모델」 절이 갖는다. 정본: `ADR 0010` 머리 · `milestone-2.md` 착수 문단 ·
`capability-map.md` §14.2 `OPEN-ADR-11`·`OPEN-ML-02`·`OPEN-ML-03` 행.

---

## 이 slice 가 하는 일

`milestone-2.md` 「Slice 2A」 일곱 항목을 **`.proto` 둘 + 양쪽 생성(VCS 밖) + round-trip** 으로 낸다.

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **`Money`** — `int64 amount_won` · `Currency currency`(허용값 `KRW` 하나) · `Basis basis` · `VatTreatment vat_treatment` · `AmountProvenanceKind provenance`. 다섯 성분은 shared-kernel `AmountRecord` 와 1:1 이고 `provenance` 는 **`Provenance` 여섯 변형의 라벨**(`PUBLISHED`·`DERIVED_FROM_OPENING`·`FILLED_FROM_BUDGET_KEY`·`COPIED_FROM_BASE_AMOUNT`·`OPERATOR_DECLARED`·`UNDECLARED`) — payload 성분(`noticeRevision`·`key`)은 Kotlin 에 남는다(D-2A-2). 소수 표현 없음(D-M2-6). **basis·provenance 가 `UNSPECIFIED` 인 금액은 거부** | 2A 「money」 · `v2-지침서.md` §4.1 「unit/basis/provenance가 없거나 모르는 값은 추측하지 않고 거부」 · ADR 0002 D-1 |
| ② | **`Rate`** — `string fraction` **하나**. 정규형 = `BigDecimal.toPlainString()`(지수 표기 없음, **scale 보존** — `0.8700` 과 `0.87` 은 다른 문자열이고 shared-kernel 의 `Rate` 가 `BigDecimal` scale 을 값의 일부로 나르는 것과 같다). 별도 `scale` 필드 없음(같은 사실 두 자리 금지). 승인 문면 「scale 명시」는 「문자열이 scale 을 잃지 않는다」로 읽는다. percent 표현 없음 — 어댑터가 fraction 으로만 만들고 **원문 unit 의 기록은 Kotlin 어댑터의 provenance**(§4.1)이지 wire 가 아니다. 축은 **필드·메시지 이름**이 나르고(`Candidate.bid_rate`·`CompetitionSample.observed_bid_rate`·`award_rate`), 관측/추천의 구분은 `origin`(D-2A-7) | 2A 「rate: fraction 고정, source와 scale 명시」 · §4.1 「`Rate`는 내부 fraction 표현 하나만」·「원문 unit을 기록한다」 |
| ③ | **enum** — `Basis`·`VatTreatment` 는 shared-kernel enum 값을 **이름까지** 미러(값 추가는 양쪽 동시, 제공자 먼저 배포). **provenance 축은 둘**: `AmountProvenanceKind`(① — 금액이 어떻게 얻어졌나, 여섯) 과 `BaseAmountProvenanceLabel`(기초금액 판정 라벨 다섯 — `CLEAN`·`DERIVED_YEGA`·`DERIVED_VAT`·`SUSPECT_RATIO`·`UNKNOWN`, decision 27, 라벨 집합 불변). 후자는 2B 의 피처 fact(`denominator_source` 자리)이고 `Money` 성분이 아니다 — 두 축을 한 이름에 접지 않는다(D-M2-10 이 legacy 어휘에 대해 지적한 오류를 V2 안에서 반복하지 않는다) | §6.3 · `capability-map.md` §14.2 `OPEN-DIC-05` 닫힘 · shared-kernel `Provenance`·`BaseAmountProvenance` |
| ④ | **봉투 둘** — `RequestEnvelope { request_id, correlation_id }` 는 **모든 RPC 필수**(빈 문자열 = 미지정 거부). `PredictionEnvelope { RequestEnvelope base, feature_schema_version, ModelReleaseSelector model_release_selector, deadline_policy_version }` 는 `CalculateOptimalBid` 전용 — `GetModelMetadata`·training RPC 는 `RequestEnvelope` 만. `deadline_policy_version` 은 **값이 아니라 정책 참조** — 실제 deadline 은 gRPC 메타데이터로(ADR 0010 D-1). `ModelReleaseSelector = oneof { LatestPromoted latest_promoted, ExactRelease exact_release(release_id, artifact_checksum) }` — `PredictionEnvelope` 에서 미지정 거부 | 2A 「`request_id`, `correlation_id`, `feature_schema_version`」·「`model_release_selector`, deadline 정책」 · §9 「계약 version과 모델 artifact로 모든 추천을 재현 가능」 |
| ⑤ | **결과 봉투 패턴** — 모든 응답은 `oneof result { <Success>, Unmeasurable unmeasurable, ApplicationFailure failure }`. `Unmeasurable { UnmeasurableReason reason, string detail_code }` 는 **transport error 가 아니다**. `ApplicationFailure { FailureCode code, bool retryable, string detail_code }`. **`FailureCode` 는 2A 가 단독 소유**하는 application 어휘이고 2B·2C 가 쓰는 값도 여기 등재한다: `UNSUPPORTED_SCHEMA`·`UNSUPPORTED_RELEASE`·`UNSUPPORTED_TRAINING_SPEC`·`INVALID_REQUEST`·`MODEL_NOT_READY`(retryable 후보)·`IDEMPOTENCY_CONFLICT`·`JOB_NOT_FOUND`. transport status(`RESOURCE_EXHAUSTED` 등)와 **이름을 겹치지 않는다** — 2C 의 job 실패 사유는 별도 enum `JobFailureCode`(2C D-2C-4) | 2A 「retryable/non-retryable application error」 · 「설계 규칙」 `oneof` · 완료 조건 「`Unmeasurable`가 transport error나 0으로 변환되지 않음」 · ADR 0001 D-6 · ADR 0010 D-3 |
| ⑥ | **fail-closed** — 모든 enum 은 `*_UNSPECIFIED = 0` 이고 수신 측은 `UNSPECIFIED` 와 **정의 밖 정수**를 거부한다(proto3 open enum). `feature_schema_version` 이 servicer 가 아는 집합 밖이면 `ApplicationFailure(UNSUPPORTED_SCHEMA, retryable=false)`. **제3 변환 금지** — `Success.release` 는 (a) `exact_release` 요청이면 그 release 와 같아야 하고 (b) `latest_promoted` 요청이면 **servicer 가 그 시점의 승격 release 로 답하고 client 는 `GetModelMetadata.promoted` 와 대조**한다(불일치 = `UNSUPPORTED_RELEASE` 취급, 승격이 그 사이 바뀐 경우는 재호출). 어느 경로에서도 다른 predictor 로의 폴백은 계약 위반(ADR 0010 D-3, legacy (c-2) 반례는 정확히 release 를 지목하지 않는 경로에서 일어났다). **필드가 허용하는 enum 부분집합 밖도 거부** — `Basis`·`VatTreatment` 는 shared-kernel 값을 전부 미러하지만 각 필드는 허용 집합을 주석으로 선언하고(예: `FeatureInputs.base_amount.basis ∈ {BASE_AMOUNT}`) 그 밖은 `INVALID_REQUEST`. **client 쪽 집행(⑥ (a)(b) 의 대조)은 M2 에서는 fake 위의 test 로만 있고 실제 client 는 4D — 인계는 `OPEN-2A-RELEASE-CHECK-4D`.** **조용한 기본값 대체 없음** | 2A 「지원하지 않는 enum/schema를 조용히 fallback하지 않는 규칙」 · 완료 조건 「미지원 schema/release가 fail-closed」 |
| ⑦ | **round-trip** — Kotlin 생성물과 Python 생성물이 같은 `contracts/testdata/*.binpb` 를 읽어 canonicalization(deterministic serialization — `OPEN-2A-CANONICAL-FORM`) 후 바이트 동일. `Rate.fraction` 정규형이 양쪽에서 같은 문자열(scale 보존 포함) | 완료 조건 「Kotlin/Python round-trip 결과가 canonicalization 후 일치」 |
| ⑧ | **lint** — buf lint 표준 규칙 + 패키지 `bidvector.ml.v1` + **`option java_package = "contract.bidvector.ml.v1"`**(D-2A-0b — Java 패키지 루트를 `bidvector` **밖**에 둔다) + 필드 번호·`reserved` 규칙은 2D 의 breaking gate 가 증명 | 「설계 규칙」 필드 번호·`reserved` · 1A `package.allowed.subtree=bidvector`(T-A) |

**만들지 않는 것**: 피처 벡터 메시지(2B) · training job(2C) · breaking mutation 증명·fake servicer·무소스 단언 게이트화(2D) · 도메인 ↔ 계약 매핑(M4)
· Python 쪽 validation 구현(5E — 2A 는 규칙을 계약 주석과 test 로만) · 사람이 읽는 오류 문장(`detail_code` 는 코드) · 생성물의 VCS 등재 · 층·convention·정책 키 편집(게이트 정의 편집은 `ResolvedDependencies` 분류 정정 한 분기, 게이트 등재는 `gate-tests` 한 줄뿐).

---

## 운영자 결정 필요 — 착수 전(D-2A-0·7) · 계약 고정(D-2A-0b·1~6, 세션 모델 판단·사후 확인)

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-2A-0** | **생성 코드가 1A 게이트 가족과 공존하는 방식.** 게이트 가족(리뷰 r1·r2 실측)은 subproject 에 대해 ⓐ `ConventionCoverageGate` — 모든 `subprojects` 가 convention 을 적용해야 함 ⓑ `SourceLanguageGate` — `src/` 의 확장자 `kt`·`kts`·`md` 만(디렉터리 symlink 도 따라감) ⓒ `SourceSetLayoutGate` — `main.java` srcDirs 가 비어야 하고 `main.kotlin` 이 `src/main/kotlin` 과 정확히 같아야 하며, source set 파일 집합과 컴파일 입력이 디스크 `src/main/kotlin` walk 와 등식 ⓓ `PackageOwnershipGate.foreignOrigin`·`JarContentGate` — class 의 `SourceFile` 이 `sourceSets["main"].kotlin` 안이어야 함 ⓔ `module.expected-source-sets=main,test`. **어떤 형태로든 subproject 안에서 생성물을 컴파일하면 ⓒ·ⓓ 가 깨진다** — 생성 디렉터리가 `src/` 밖일수록 ⓒ 의 등식은 더 확실히 깨진다 | (a) subproject + 생성물 `build/generated/`(r1 채택안) (b) subproject + 생성물 커밋 + 게이트 예외 셋 (c) **included build** — `ml-contract/` 를 `includeBuild` 로 두고(`build-logic` 선례, `include(...)` 목록 밖) `adapters` 가 `"bidvector:ml-contract"` 로 의존. convention 미적용(ⓐ 는 `subprojects` 만 대조), `src/` 없음(ⓑ·ⓒ·ⓓ·ⓔ 대상 아님) | **(c)** — (a) 는 ⓒ 두 단언·ⓓ 둘에 걸려 예외가 넷 필요하고, (b) 는 ⓑ·ⓔ·java srcDirs 예외 셋 — 둘 다 「게이트 예외 위에 게이트를 세우는」 형태라 같은 근거로 기각. (c) 는 층 신설 0(N-1 소멸)·convention·정책 편집 0 이고 게이트 정의 편집은 **분류 정정 한 분기**(아래 ②)뿐 — 예외가 아니라 버그 수정이라 (a)·(b) 의 예외와 종류가 다르다. **`build-logic` 선례의 정확한 읽기**: 그 included build 는 자기 몫의 게이트 셋을 루트 `check` 에 **따로 배선**했다. `ml-contract` 는 손으로 쓴 소스가 0 이라 잴 것이 없어 **무소스 단언 하나**로 그 배선을 대신한다(S-5 → 2D). **대가와 방어**: ① 게이트 밖 빌드가 하나 생긴다 — 내용물이 빌드 파일 셋뿐임을 S-5 가 실측하고 2D 가 게이트로 올린다(`OPEN-2A-INCLUDED-BUILD`); ② `adapters` 의 의존은 composite 치환이라 `resolveDependencies` 가 **`ProjectComponentIdentifier` 로 받아 `projectPaths` 에 넣는다**(리뷰 r3 ⓕ — r2 는 이 분류를 보지 않았다) → 현 코드로는 `adapters:moduleDependencyGate` 가 실패. 다른 빌드의 project 를 `externalModules` 로 분류하도록 **한 분기 정정**하면 외부 의존 검사는 domain 층에만 걸리므로(`mainExternalViolations`·`groupViolations` 가 `isDomain` 아니면 빈 목록) adapters 는 통과하고 domain 은 `external.allowed.domain` 밖이라 실패(구조) — S-1b 가 실측; ③ included build 의 protoc·플러그인은 Maven 아티팩트라 RO 의존 캐시가 덮는다 — 착수 시 §4b 스모크 | 착수 전 · Phase 2.5 |
| **D-2A-0b** | **생성 Java 패키지의 루트** — (c) 에서 층 배치는 불요하나 ArchUnit 등식(`app` 의 「`bidvector` 루트 아래 1급 패키지 = 승인 모듈 집합」)과 T-A(`package.allowed.subtree=bidvector`)가 남는다 | `option java_package = "contract.bidvector.ml.v1"` — 루트 `contract` 를 `bidvector` **밖**에 둔다. ArchUnit 은 `importPackages("bidvector")` 라 등식이 그대로이고, domain 이 `contract.*` 를 import 하면 T-A(허용 subtree 는 `bidvector` 뿐) 가 **구조적으로** 거부한다 — 층 신설도 `ArchitecturePolicy` 편집도 없다. proto 패키지 `bidvector.ml.v1`(wire·Python 쪽)은 그대로 | — | 계약 고정 |
| **D-2A-7** | **승인 문면 「source 명시」의 읽기.** 두 읽기가 있다 — (i) 율의 **축**(floor/assessment/bid/award) (ii) §4.1 「percent 입력은 adapter 에서 변환하며 **원문 unit 을 기록**」의 unit 출처 | (a) **어느 쪽도 wire 필드로 두지 않는다** — 축은 필드·메시지 이름이 나르고(조사 g-1), 원문 unit 은 Kotlin 어댑터 provenance 소유. 대신 V2 가 실제로 갖는 구분 **`BidRateOrigin { OBSERVED, RECOMMENDED }`** 을 `bid_rate` 가 있는 자리(`CompetitionSample`·`Candidate`)에 `origin` 으로 싣는다(shared-kernel `BidRate.origin` 미러) (b) `RateSource` 축 enum (c) 원문 unit enum | **(a)** — (b) 는 이름이 이미 나르는 사실을 두 자리에 두고 관측/추천 구분을 잃는다(리뷰 r1). (c) 는 ml-engine 이 알 이유가 없는 어댑터 사정 | 착수 전 |
| **D-2A-1** | `Money` 에 **`EstimatedAmount`·`YegaAmount` 를 위한 타입 분리를 wire 에 두지 않는다** — wire 는 `Money + basis` 하나이고 축 분리는 Kotlin 도메인(1B)이 소유. ML 이 받는 금액은 `BASE_AMOUNT` basis 뿐(2B 가 입력 필드 이름으로 고정) | — | §3.2 · ADR 0003 D-2 | 계약 고정 |
| **D-2A-2** | **provenance 의 wire 성분은 라벨뿐** — `Provenance.Published(noticeRevision)`·`FilledFromBudgetKey(key)` 의 payload 는 Kotlin 안. 피처가 요구하는 것은 범주 | — | §6.3 · ADR 0003 D-2 · 조사 g-1 | 계약 고정 |
| **D-2A-3** | **`Unmeasurable` 사유 어휘는 계약 소유 enum**(`UnmeasurableReason { INSUFFICIENT_SAMPLES, UNTRAINED_SEGMENT, FEATURE_ABSENT }`)이고 Kotlin `ReasonCode` 에 더하지 않는다 — 어댑터가 `Measurement.Unmeasurable` 로 매핑(M4). `UNTRAINED_SEGMENT` ≠ `INSUFFICIENT_SAMPLES` | — | ML-02 · 1D `FloorUnmeasurableReason` 선례 | 계약 고정 |
| **D-2A-4** | **결측은 사유** — `MissingReason { UNKNOWN, NOT_APPLICABLE, NOT_COLLECTED_YET }` 을 2A 가 정의, 2B 의 모든 fact 가 `oneof { value, missing }` | — | §6.3 · §13.3 | 계약 고정 |
| **D-2A-5** | **`double` 은 계약 어디에도 없다** — 율·점수·불확실성 성분 전부 decimal string | — | D-M2-6 · §9 · 1E D-1 | 계약 고정 |
| **D-2A-6** | **`ml-contract` 에는 래칫도 게이트도 적용되지 않는다** — included build 라 convention 밖이고, 그것이 정당한 이유는 **손으로 쓴 소스가 0** 이기 때문이다(빌드 파일 셋만 — S-5). 손으로 쓰는 test 는 전부 `adapters/src/test`(래칫·게이트 전건 적용). 「게이트 밖에 소스가 생기는 것」이 이 모듈의 유일한 위험이고 2D 가 무소스 단언을 `contractGate` 에 올린다 | — | ADR 0007 · 1A `ConventionCoverageGate` 의 취지(「게이트의 부재는 조용하다」) | 계약 고정 |

---

## 위협 모델 — 2A 고유 경계 (빌드 경계 신설 slice — Phase 2.5)

**(0) 경계 문장**: 2A 가 방어하는 것은 **계약의 의미 손실**과 **생성물 경계의 침식**(도메인이 계약 타입을 보는 것, 게이트 밖 빌드에 손으로 쓴 소스가 생기는 것)이다. 게이트 정의·`settings.gradle.kts` 를 편집하는 사람은 방어하지 않는다 — `milestone-1.md` 「완료 조건 — 게이트 회피 경계」와 같은 경계.

**방어한다**: (a) `Unmeasurable` 이 transport error 나 `0` 으로 접힘(oneof + test) (b) 미지 enum 정수·`UNSPECIFIED` 통과(양쪽 거부 test) (c) 율의 `double` 유출·scale 손실(타입 부재 + 정규형 test) (d) basis·provenance 없는 금액(`UNSPECIFIED` 거부) (e) 생성물 수동 편집(구조 — VCS 밖, S-4) (f) 도메인이 계약 타입을 import(T-A — `contract.*` 는 허용 subtree 밖, 구조; 의존 선언 쪽은 `external.allowed.domain`) (g) 다른 release 의 답(⑥ — 두 선택자 모두) (h) provenance 두 축의 혼용(enum 둘, `Money` 에는 하나만) (i) 게이트 밖 빌드에 손으로 쓴 소스가 생김(S-5 → 2D 게이트).
**방어하지 않는다**: 어댑터 매핑의 정직성(M4) · Python validation 의 실행(5E) · 정책 데이터 내용 · 네트워크·인증(M6) · 생성 도구의 결함 · `ml-contract/build.gradle.kts` 자체의 내용(리뷰 — 빌드 스크립트는 게이트 대상이 아니다, `build-logic` 과 같음) · `adapters` 가 grpc 를 main 에서 쓰는 방식(M4).

**우회 후보(≥5)**: (1) enum 에 `UNSPECIFIED` 없이 첫 값 0 → buf lint (2) `Unmeasurable` 을 `ApplicationFailure(retryable=true)` 로 → 2D 재시도 test + ADR 0010 「`Unmeasurable` 은 재시도 대상 아님」 (3) `fraction = "1e-3"` → 정규형 거부 (4) `feature_schema_version` 빈 문자열 → 필수 validation (5) 도메인 모듈이 `implementation("bidvector:ml-contract")` 선언 → `external.allowed.domain` 밖이라 `ModuleDependencyGate` 실패; `testImplementation` 은 `mainExternalViolations` 가 main 구성만 보는지 착수 시 실측(1A 관례) (6) `ml-contract/src/main/kotlin` 에 손으로 쓴 클래스 추가 → S-5 무소스 단언(2D 게이트) (7) `KRW` 외 currency 추가 → breaking gate + 계약 version (8) `latest_promoted` 응답을 폴백 predictor 로 → ⑥ (b) 대조 test (9) `java_package` 를 `bidvector.mlcontract.v1` 로 되돌림 → ArchUnit 등식이 `mlcontract` 를 관측해 실패(원치 않는 방향으로도 게이트가 닫힌다).

---

## 조사 결과 — 이 slice 에 영향을 주는 것 (`_workspace/m2-prep/`)

- legacy 인터페이스 실물(`01_scout_ml_interface.md`, `ed4b06c`): 피처 금액은 태그 없는 `float` 원(① 다섯 성분 근거 — `won` 만이면 R-BASIS-06 재발) · 율은 `float`
  fraction(② 근거) · 응답 모델 식별은 `model_version` 문자열 하나(④ 근거) · **모든 추론 실패가 다른 predictor 의 값 있는 답으로 접힘**(`orchestration.py:264-273`)
  → ⑥ 제3 변환 금지 · `confidence` 클램프 [0.45, 0.95] → D-2A-3 · `Provenance` 는 라벨만 wire 로(D-2A-2) · `denominator_source` 어휘는 V2 라벨과 다른 축(D-M2-10).
- gRPC 스택(`02_grpc_stack_compat.md`): grpc-kotlin **1.5.0 리터럴** · grpc-java **1.84.0** · protobuf-java **3.25.9** · grpcio/grpcio-tools **1.83.1** · protobuf(py) **7.36.1** ·
  buf **1.72.0**. proto3 확정. **2A 착수 시 grpc-kotlin 1.5.0 + grpc-java 1.84.0 조합의 컴파일·런타임 스모크가 선행**(POM 선언 `grpc-stub:1.62.2`, 검증 이력 없음).
  Java·Python protobuf 런타임은 독립 버전 축 — 「같은 major」 서술 금지.
- 1A 게이트 구현체 실측(리뷰 r1·r2, `03_review_r1_tail.md`·`04_review_r2.md`): `ConventionCoverageGate` 는 `subprojects` 대조 · `SourceLanguageGate` 는 `src/` walk(symlink 추종) ·
  `SourceSetLayoutGate` 는 `main.java` 빈 srcDirs + `src/main/kotlin` 등식 둘 · `PackageOwnership.foreignOrigin`·`JarContent` 는 `sourceSets["main"].kotlin` 기준 ·
  `ModuleDependencyPolicy` 의 외부·그룹 검사는 domain 층만 · ArchUnit 은 `importPackages("bidvector")` → D-2A-0 (c)·D-2A-0b.

---

## OPEN — 수령·신설

| OPEN | 2A 처리 |
| --- | --- |
| `OPEN-ADR-11` | 봉투의 `deadline_policy_version` 참조까지만 — 규칙·값은 ADR 0010 / D-M2-9 |
| `OPEN-ML-03` | D-M2-8 (a) — 2A 는 「`double` 없음」과 「점수는 축 이름을 가진 메시지」 규칙까지, 메시지는 2B |
| `OPEN-DIC-05`(닫힘) | `BaseAmountProvenanceLabel` 다섯 미러 — 소비만 |
| 신설 후보 `OPEN-2A-CANONICAL-FORM` | round-trip canonicalization 방식(protobuf deterministic serialization vs JSON canonical) — 착수 시 확정 |
| 신설 후보 `OPEN-2A-INCLUDED-BUILD` | 게이트 밖 빌드 하나(`ml-contract`)의 존재. `build-logic` 선례는 자기 몫의 게이트를 루트 `check` 에 따로 배선했고, `ml-contract` 는 손으로 쓴 소스가 0 이라 잴 것이 없어 **무소스 단언 하나**(S-5 → 2D `contractGate`)가 그 배선을 대신한다 — 이 OPEN 이 지키는 것은 「그 빌드에 소스가 생기지 않는다」다. `capability-map.md` §9 소유 축 표·ADR 0006 갱신 후보로 등재(착수 시) |
| 신설 후보 `OPEN-2A-RELEASE-CHECK-4D` | ⑥ 의 제3 변환 금지는 **client 가 집행**하는 규칙인데 실제 client 는 M4 4D 소유 — M2 는 fake 위의 test 까지. 4D 착수 계약이 이 test 를 실제 client 배선에 재사용해야 함을 **활성 OPEN 으로 등재**(선언이 어디에도 강제되지 않는 ML-03 형태를 피한다). `milestone-4.md` 4D 문면 갱신은 4D 착수 시 |
