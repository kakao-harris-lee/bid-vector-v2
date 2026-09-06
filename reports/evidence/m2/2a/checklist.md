# M2/2A checklist.md

## 리뷰 요청 조건 점검

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain <in_scope 24개 경로>`
      빈 출력, 양성 대조 통과(commands.md 「clean-tree 게이트」).
- [x] scope.md의 acceptance_commands(S-0~S-7) 전부 exit 0 — commands.md.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — S-1(`--no-build-cache clean
      check`)이 ktlint_official·detekt·ArchUnit·moduleDependencyGate·buf(간접, S-2가
      별도)를 전부 포함해 재확인.
- [x] 변경된 fixture와 정책 version의 근거 기록 — `contracts/testdata/*.binpb`는 fixture가
      아니라 계약 canonical 표본(round-trip 축, `fixtures/**`와 별개 축 — scope.md
      out_of_scope). 정책 version 신규 없음(2A는 값이 아니라 계약 형태만 고정).
- [x] 알려진 제한과 rollback 기록 — 아래 「알려진 제한」·`rollback.md`.
- [x] secret 스캔 통과 — commands.md.

## ①~⑧ 대응표 — scope.md 「이 slice 가 하는 일」과 그것을 증명하는 게이트/테스트

| # | 항목 | 증명 |
| --- | --- | --- |
| ① | `Money` 다섯 성분, basis·provenance UNSPECIFIED 거부 | `common.proto` Money 메시지 + `ContractRoundTripTest`(`Money 는 basis/provenance 가 UNSPECIFIED 면 거부된다`, 2건) + `test_contract_roundtrip.py`(`test_money_default_instance_is_unspecified_and_must_be_rejected`) |
| ② | `Rate.fraction` 정규형(scale 보존, 지수 표기 거부) | `ContractRoundTripTest`(`Rate fraction 정규형은 scale 을 보존한다`·`지수 표기 fraction 은 정규형이 아니다`) + Python `test_rate_round_trips_and_normal_form_matches_kotlin` — 양쪽 다 `"0.8700"` |
| ③ | enum(Basis·VatTreatment shared-kernel 미러, provenance 축 둘 분리) | `common.proto`의 `Basis`·`VatTreatment`·`AmountProvenanceKind`·`BaseAmountProvenanceLabel` 네 enum이 각각 별도 선언(한 enum에 접지 않음) — commands.md 변이 실측 2(어휘 비겹침) |
| ④ | 봉투 둘(RequestEnvelope 필수, PredictionEnvelope 전용) + selector oneof | `common.proto` 메시지 정의 + `ContractRoundTripTest`(`RequestEnvelope` 빈 문자열 거부 2건, `PredictionEnvelope` selector 미지정 거부 1건) |
| ⑤ | 결과 봉투 패턴(`Unmeasurable`·`ApplicationFailure`, `FailureCode` 단독 소유) | `error.proto` + `ContractRoundTripTest`(`Unmeasurable reason 이 UNSPECIFIED 면 거부된다` 등) — `FailureCode` 7값·`UnmeasurableReason` 3값 어휘 비겹침(commands.md 변이 2) |
| ⑥ | fail-closed(UNSPECIFIED·정의 밖 정수 거부, 제3 변환 금지는 client 집행 — `OPEN-2A-RELEASE-CHECK-4D`) | `ContractRoundTripTest`(`Money 는 정의 밖 enum 정수(currency/basis)를 거부한다` — `UNRECOGNIZED` 실측) + Python 대칭. 제3 변환 금지 자체의 client 집행은 M4 몫(scope.md ⑥ 본문이 이미 그렇게 인계) |
| ⑦ | round-trip(canonicalization 후 바이트 일치) | `ContractRoundTripTest` 6건(Kotlin) + `test_contract_roundtrip.py` 6건(Python) — 같은 `contracts/testdata/*.binpb` 여섯을 양쪽 다 deterministic serialization으로 원본과 대조 |
| ⑧ | lint(buf standard + 패키지·java_package 규칙) | S-2(`buf lint && buf build`) + 변이 실측 1(enum 순서 위반 시 buf lint가 실제로 잡음) |

## 위협 모델 「방어한다」 목록과의 대응(scope.md)

(a) `Unmeasurable`이 transport error나 0으로 접힘 → `Unmeasurable`을 `error.proto`의 독립
메시지로 두고(oneof 결과 봉투에 실릴 자리는 2B·2C가 짓는다 — 알려진 제한 7) round-trip
test가 실제 파싱을 검증. (b) 미지 enum 정수·UNSPECIFIED 통과 → 변이 실측 있음.
(c) 율의 double 유출·scale 손실 → `Rate`는 `string fraction` 하나뿐(타입에 double 없음) +
정규형 test. (d) basis·provenance 없는 금액 → 위 ①. (e) 생성물 수동 편집 → S-4·S-5.
(f) 도메인이 계약 타입을 import → T-A(`package.allowed.subtree=bidvector`)가 구조적으로
거부(이 slice가 실측하지 않음 — 알려진 제한 참고). (g) 다른 release의 답 → 계약 형태
(`ModelReleaseSelector`)까지, 실제 client 집행은 `OPEN-2A-RELEASE-CHECK-4D`(M4). (h)
provenance 두 축 혼용 → `AmountProvenanceKind`/`BaseAmountProvenanceLabel` 별도 enum,
변이 실측 2가 어휘 비겹침을 확인. (i) 게이트 밖 빌드에 손 소스 → S-5.

## 알려진 제한

1. **`OPEN-2A-CANONICAL-FORM`** — protobuf deterministic serialization을 canonical form으로
   **제안**했고 이 slice의 round-trip test 열두 건(Kotlin 6 + Python 6)이 그 위에서
   성립함을 실측했다. map 필드가 없고 unknown field도 없는 이 testdata 범위에서는
   deterministic serialization이 필드 번호 오름차순으로 결정적이다 — map 필드가 도입되는
   2B 이후에는 이 제안을 재검토해야 한다(map 순서는 deterministic 모드에서도 삽입 순서
   보존이 아니라 별도 정렬 규칙을 따른다). **등재 완료(verifier r1 F-3)** —
   `capability-map.md` §14.3에 세션 모델이 신설했다(`ddd82fb`).
2. **`OPEN-2A-INCLUDED-BUILD`** — `ml-contract`는 게이트 가족 밖의 빌드다. S-5(무소스
   단언)가 이 slice의 유일한 방어이고, 2D가 이것을 `contractGate`로 정식화해야 한다.
3. **`OPEN-2A-RELEASE-CHECK-4D`** — ⑥의 제3 변환 금지(다른 release 응답을 client가
   거부)는 fake 위의 test까지가 M2 몫이고 실제 client 집행은 M4 4D다. 2A는 `.proto`
   형태(oneof selector, `exact_release`가 지목하는 release_id)까지만 낸다.
4. **변이 실측 미실행 항목**(commands.md 하단) — `java_package`를 `bidvector.*`로
   되돌렸을 때 ArchUnit 등식이 실패하는 경로는 `app`의 classpath 배선(M4)이 있어야
   재현되므로 이 slice에서 실행하지 않았다. 논리적 근거(D-2A-0b, T-A 허용 subtree가
   `bidvector`뿐)는 성립하되 실측이 아니라 설계 근거임을 명시한다.
5. **S-6은 Gradle `check` 밖이다** — CI에 Python 툴체인이 없다(scope.md 명시). Python
   round-trip은 이 slice의 로컬 실행(commands.md)으로만 증명됐고, CI 자동화는 범위 밖.
6. **`ml-engine`의 패키지 구조·import 경계는 미완성** — `pyproject.toml` + `tests/`만
   있고 실제 `ml_engine` 패키지 코드는 없다(D-M2-3 (a), M5 5A가 완성).
7. **결과 봉투 `oneof result { Success, Unmeasurable, ApplicationFailure }`는 2A에 없다**
   (verifier r1 F-2) — 2A가 실제로 내는 것은 `Unmeasurable`·`ApplicationFailure` 두
   **독립 메시지**와 그 필드·enum 규칙뿐이다. 그 둘을 감싸는 `oneof result`는 실제 RPC
   응답 메시지(`CalculateOptimalBid`·`GetModelMetadata`의 응답, 2B 소유 / training job
   RPC 응답, 2C 소유)가 짓는다 — 그 메시지 자체가 2A 범위 밖(out_of_scope: `prediction.proto`·
   `training.proto`, scope.md)이기 때문이다. 위협 모델 (a)의 「oneof + test」 방어는
   **그 oneof가 실제로 존재하는 2B·2C에서 성립**하고, 2A는 oneof의 두 가지(variant) 타입과
   fail-closed 규칙까지만 낸다. **2B·2C 인계 항목**: 응답 메시지의 `oneof result`에
   `Unmeasurable`을 다른 모델의 성공값으로 접지 않는다는 test(제3 변환 금지, ⑥)를
   같은 자리에서 검증해야 한다 — `OPEN-2A-RELEASE-CHECK-4D`(항목 3)와 같은 인계 축이다.
8. **끝머리 미지 필드는 양쪽(Kotlin·Python)에서 조용히 통과한다**(verifier r1 F-4, info) —
   proto3 unknown field 보존은 표준 동작이고 이 slice가 막을 규칙이 아니다. breaking
   change 증명(필드 재사용·reserved 위반 탐지)은 2D의 breaking gate 소관.

## S-1b 관련 — r4 N-11 앵커 우려의 사후 확인

Phase 2.5 설계 검토(prep 리뷰 r4)가 `external=<개수>` 형식일 가능성 때문에 앵커가
어긋날 수 있다고 우려했다. **실측 결과 우려는 현실화되지 않았다** — `external=<개수>`는
요약 줄 하나뿐이고 그 아래 각 좌표는 `group:name:version` 한 줄씩이라 scope.md의
`^bidvector:ml-contract` 앵커가 정확히 그 줄에 매치한다(commands.md S-1b). scope.md의
acceptance 명령 문면을 정정할 필요가 없었다.

## 사용자 승인 — 2026-09-07, slice 2A 종결

verifier r1 `ready-for-review`(head `a78959f`, 잔여 일괄 `d3708f2`·`cbfb180`, 세션 모델 OPEN 등재 `ddd82fb`) 위에서
**사용자 승인 2026-09-07**. 재작업 0/5. 게이트 술어 정정(`ResolvedDependencies` 한 분기)은 변이 7 표적 재검증으로 미탐 없음.
알려진 제한(결과 봉투 oneof 는 2B·2C 소유 · 끝머리 미지 필드는 2D · Python 툴체인은 Gradle check 밖 · `ml-contract` 무소스
단언이 유일 방어)은 등재 유지. verifier 레인의 보고서 파일은 그 레인의 hook 이 막아 오케스트레이터가 메시지를
`_workspace/m2-2a/02_verifier_report.md` 로 옮겨 적었다. 다음 slice 2B 착수 지시 같은 날(D-2B-1~4 전부 추천안).

## 커밋 목록

**Phase 3 최초 구현**(base `040ab9d`) — 2A 자신의 커밋 7개(순서대로): `15ee75d`(contracts/) ·
`667d9bd`(ml-contract/ 골격 + §4b 컴파일 스모크) · `e307395`(ResolvedDependencies 분류
정정 + test) · `45d550b`(round-trip Kotlin 23건 + testdata) · `87ccf86`(ktlint/detekt
정정) · `28c845a`(ml-engine/ 골격 + Python round-trip 9건) · `48e9980`(§4b 런타임
스모크) · `a78959f`(evidence pack 최초본).

**verifier r1 수정**(`02_verifier_report.md` F-1·F-2·F-5) — `ddd82fb`(세션 모델,
capability-map §14.3 OPEN 등재, F-3) · `d3708f2`(alias 정합 — protoc·grpc-kotlin 플러그인
좌표를 카탈로그 alias 로, `grpc-netty-shaded` 제거, F-5) · 이 evidence 커밋
자신(head_sha 관례 정정 F-1 + oneof 인계 등재 F-2 + F-4 한 줄). 이 목록은 열린
목록이다 — base/head를 하나의 값으로 고정하지 않는다(verifier r1 F-1과 같은 이유).

이 range의 나머지 커밋(M2 착수 기록·M3/M4/M5 준비 초안)은 병행 레인 산출물이며
commands.md 「하네스 레인 변경」 절이 목록과 사유를 갖는다.

## 판단이 갈린 지점

1. **`ml-contract`의 protobuf/grpc 의존을 `implementation`에서 `api`로 정정** —
   최초 시도(`implementation`)는 `adapters:compileTestKotlin`이
   `Cannot access 'GeneratedMessageV3' which is a supertype of 'Money'`로 즉시
   실패해 발견했다(45d550b 커밋 메시지에 기록). 생성 message/stub 타입이 그 상위
   타입을 컴파일 시그니처에 노출하므로 소비자 classpath에도 있어야 한다는 것을
  판단이 아니라 컴파일 오류가 직접 확정했다.
2. **§4b 런타임 스모크에 `io.grpc:grpc-inprocess`가 필요하다는 사실** — 처음엔
   `grpc-core`면 충분하다고 가정했으나 `Unresolved reference 'InProcessServerBuilder'`로
   실패, jar 내용 실측(`unzip -l`)으로 `io.grpc.inprocess.*`가 `grpc-core`가 아니라
   별도 좌표 `grpc-inprocess`에 있음을 확인했다(48e9980).
3. **S-1b 앵커 사전 우려는 실측으로 기각** — 위 절.
