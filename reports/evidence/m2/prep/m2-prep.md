# M2 준비 — Versioned 계약과 gRPC 경계 · slice 지도와 착수 전 결정 후보 (초안, 구현 전)

> **지위**: M1/1E 병행 중에 세션 모델(Fable 5.1)이 단독으로 쓴 **준비 문서**다(CLAUDE.md 운영자 지시
> 2026-09-04 — 기획 파이프라인 없음). 구현·gradle·의존성 추가·fixture 편집은 하지 않았다.
> **M2 착수는 M1 전체 승인 뒤 운영자 별도 지시**로 한다(`milestone-2.md` 「선행 조건」·`milestone-1.md`
> *"1A~1E 전체가 승인되어야 M2로 진행한다"*). 그때 각 slice 의 `base_sha` 를 재고정하고 「착수 전 결정」의
> 답을 받은 뒤 `milestone-2.md` 에 착수 문단을 쓴다.
>
> 작성 시점 HEAD `c9022d9`(2026-09-06). 근거: `milestone-2.md` · `v2-지침서.md` §3.2·§3.3·§4.1·§5·§9 ·
> `ADR 0003`(계약과 전송) · `ADR 0001` D-6 · `ADR 0006` D-6·D-8 · `ADR 0007` contract 층 · `ADR 0009` ·
> `data-dictionary.md` §6·§13.3 · `capability-map.md` ML-01~03·ML-11·§14.2 `OPEN-ADR-11`·`OPEN-ML-02`·
> `OPEN-ML-03` · `milestone-4.md` 4D · `milestone-5.md` 5A·5B·5E · 조사 노트 둘(`_workspace/m2-prep/`).

---

## 1. M2 가 로드맵에서 서는 자리

| 마일스톤 | gRPC 축의 역할 | 정본 |
| --- | --- | --- |
| M0 | 결정 — `.proto` 단일 출처 + gRPC 전송, 추론 동기/학습 job 계약 분리, 콜백 없음, 이중 구현 금지, 값은 M2 ADR | `ADR 0003` D-1~D-9 |
| M1 | 범위 밖. 도메인은 gRPC·Protobuf 타입을 보지 못한다 | `milestone-1.md` 「범위 밖」 · `ADR 0006` D-6 |
| **M2** | **계약의 본체** — 공통 값·오류(2A) · Prediction RPC(2B) · training job 계약(2C) · 생성·호환성·provider test(2D) | `milestone-2.md` |
| M4 | Kotlin 소비자 — 생성 coroutine client, deadline·cancellation·circuit breaker·bounded retry, fail-safe | `milestone-4.md` 4D |
| M5 | Python 제공자 — 생성 servicer, readiness, status mapping, graceful shutdown | `milestone-5.md` 5E |
| M6 | 인증·전송 암호화·네트워크 배치 | `ADR 0003` §6 · `milestone-6.md` 6C |

**M2 의 산출물은 계약과 게이트이지 클라이언트·서버가 아니다.** M2 가 만드는 실행 코드는 생성 stub, round-trip
test, fake servicer 를 이용한 consumer/provider test 까지다(2D). 실제 client 배선은 4D, 실제 servicer 는 5E.

---

## 2. slice 지도 — 순서와 경계

`milestone-2.md` 의 2A~2D 를 그대로 두되 **생성 배선의 위치**를 하나 정한다(D-M2-1). 기본 순서 2A → 2B → 2C → 2D.
2A 없이는 2B·2C 의 메시지가 값 타입을 참조하지 못하고, 2D 의 게이트는 2A~2C 의 `.proto` 가 있어야 증명 대상이 있다.

| slice | 하는 일(요지) | in_scope 후보 | 산출물 형태 |
| --- | --- | --- | --- |
| **2A 공통 값과 오류 계약** | `Money`(정수 원화 또는 scale 명시 decimal string)·`Rate`(fraction 고정, source·scale)·`Basis`·`VatTreatment`·provenance enum · `request_id`·`correlation_id`·`feature_schema_version`·`model_release_selector`·deadline 정책 필드 · retryable/non-retryable application error · 미지원 enum/schema fail-closed 규칙 | `contracts/proto/bidvector/ml/v1/common.proto`·`error.proto` + (D-M2-1 에 따라) 양쪽 생성 배선 최소 + round-trip test | 계약 정본은 `reports/evidence/m2/2a/scope.md`(초안 있음) |
| **2B Prediction RPC** | `BidPredictionService` — `CalculateOptimalBid`·`GetModelMetadata`. `EstimateShortfall` 은 **제외**(D-M2-4). 입력 = canonical 금액 + floor rate + versioned feature vector(피처마다 `MissingReason`) + 정제된 경쟁 표본 + optimization objective. 출력 = 후보 3(보수/기준/공격, 폭의 출처 구조화) + 점수 + 불확실성 성분(§6.5 세 성분) + model release/checksum/feature schema + diagnostics. 업무 verdict·operator 권한·DB id·알림 여부 없음 | `contracts/proto/bidvector/ml/v1/prediction.proto` + 생성 + fake servicer 대상 consumer test | 계약 정본은 `reports/evidence/m2/2b/scope.md`(초안 있음) |
| **2C 비동기 training 계약** | `TrainingJobService` — `StartTraining`(immutable dataset reference + manifest checksum + request id + idempotency key)·`GetTrainingJob`(accepted/running/succeeded/failed + artifact manifest·evaluation report reference)·`CancelTrainingJob`. transport 는 **별도 job API(같은 gRPC 서버의 unary RPC, Kotlin 이 폴링)** — broker contract 불채택(D-M2-5, ADR 0010 초안) | `contracts/proto/bidvector/ml/v1/training.proto` + 생성 | 계약 정본은 `reports/evidence/m2/2c/scope.md`(초안 있음) |
| **2D 생성·호환성·provider test** | buf lint · breaking-change gate(실제 breaking mutation 을 잡는 증명) · 양쪽 serialization round-trip(canonicalization 후 일치) · unknown field/enum·max payload·deadline·cancellation test · fake servicer consumer/provider test · 생성 코드 수동 편집 금지 게이트 | `build-logic/**`(contractGate)·`contracts/tools/**`·`config/quality/**`·`adapters/src/test/**`·`ml-engine/tests/**` | 계약 정본은 `reports/evidence/m2/2d/scope.md`(초안 있음) |

**M2 전체의 out_of_scope**(모든 slice 공통): 실제 LightGBM/KDE(M5) · 실제 KONEPS/LLM 호출 · public web API · production
broker/DB · Kotlin client 의 circuit breaker·retry 배선(M4 4D — M2 는 규칙만 ADR 로) · Python servicer 의 model preload
·readiness(M5 5E) · `shared-kernel/**`·도메인 모듈 편집(도메인은 계약 타입을 보지 못한다 — 매핑은 `adapters`, M4).

---

> **착수 2026-09-06(운영자 결정)**: `ADR 0010` 승인 · **D-M2-1~14 전부 추천안 채택**(1 (c) included build · 2 (a) · 3 (a) · 4 (a)
> EstimateShortfall 제외 · 5 (a) job API · 6 (a) int64 원/decimal string · 7 (a) proto3 · 8 (a) · 9 (a) · 10 (a) · 11 (a) · 12 (a) ·
> 13 (a) · 14 (a)) · 2A D-2A-0 (c)·D-2A-7 (a). 2A base `040ab9d`(M1 전체 승인 커밋). 정본은 각 slice scope.md 와 `ADR 0010` 머리.

## 3. 착수 전 결정 후보 D-M2-1~14 (운영자 · 이 문서는 묻지 않는다)

> 1E 세션과 운영자가 같으므로 착수 시 한 번에 받는다. 각 행은 「선택지 + 추천 + 근거」로 닫혀 있다.

| ID | 물음 | 선택지 | 추천·근거 |
| --- | --- | --- | --- |
| **D-M2-1** | **`.proto` 와 생성 코드의 자리.** ADR 0006 D-6 은 「도메인에서 보이지 않는다」만 요구하고 별도 빌드 모듈 여부를 M2 에 맡겼다 | (a) 저장소 루트 `contracts/proto/` + Kotlin 생성 전용 **subproject** `ml-contract`(생성물 커밋 또는 `build/`) — **기각**(리뷰 r1·r2: 1A 게이트 가족이 subproject 안의 생성물을 어떤 형태로든 거부) (b) `adapters` 안에 `.proto` 와 생성물 — 기각(단일 출처가 한쪽 언어 모듈 안, 래칫 우회 표면) (c) **저장소 루트 `contracts/proto/`(단일 출처, Gradle 모듈 아님) + `ml-contract` 를 subproject 가 아닌 included build 로, 생성물은 양쪽 빌드 산출물(VCS 밖) + Python 생성물은 pytest 임시 생성** | **(c)** — ① 단일 출처가 어느 한쪽 언어의 모듈 안에 있으면 다른 쪽이 「남의 모듈」을 읽는다. ② 생성물은 크기·복잡도·CPD 래칫을 통과할 수 없고 통과시켜서도 안 된다 — 게이트 가족 **밖의 빌드**라 래칫 대상 자체가 아니며, 그것이 정당한 이유는 손으로 쓴 소스가 0 이기 때문(2A D-2A-6·S-5). ③ 생성물은 **VCS 밖**이고 `ml-contract` 는 **subproject 가 아니라 included build**(`build-logic` 선례) — 2A D-2A-0 (c): 1A 게이트 가족(`SourceLanguageGate`·`SourceSetLayoutGate` 등식 둘·`PackageOwnership.foreignOrigin`·`JarContent`·`expected-source-sets`)은 subproject 안의 생성물을 어떤 형태로든 거부하므로(리뷰 r1·r2 실측) 생성물은 게이트 가족 **밖의 빌드**에서 나와 jar 로만 들어온다. **대가**: 게이트 밖 빌드 하나 — `build-logic` 선례가 자기 게이트를 루트 `check` 에 따로 배선한 것과 달리 잴 소스가 없으므로 손으로 쓴 소스 0 을 2D 가 단언(`OPEN-2A-INCLUDED-BUILD`). **층 신설 없음**, convention·정책 키 편집 없음; 게이트 정의 편집은 `ResolvedDependencies` 의 의존 분류 **정정 한 분기**(composite 치환 의존을 project 가 아니라 external 로 — 리뷰 r3 ⓕ) 뿐. 생성 Java 패키지 루트는 `bidvector` 밖(`contract.bidvector.ml.v1`) — ArchUnit 등식 무접촉, domain 의 import 는 T-A 가 구조적으로 거부(D-2A-0b) |
| **D-M2-2** | **2A 가 생성 배선을 포함하는가.** `milestone-2.md` 는 생성을 2D 에 둔다 | (a) **2A 에 「양쪽 생성 + round-trip」 최소 배선을 선행 흡수**, 2D 는 게이트 **증명**(breaking mutation·unknown field·deadline·cancellation·fake servicer) (b) 문면대로 2A 는 `.proto` 문서만, 생성은 2D | **(a)** — `.proto` 는 컴파일되기 전엔 계약이 아니라 텍스트다. 2A 의 acceptance 가 「round-trip 일치」를 내려면 생성이 있어야 하고, 2B·2C 가 그 위에 선다. 2D 의 정체성은 「게이트가 실제로 잡는다」는 증거(`ADR 0003` D-6)라 생성 자체를 미룰 이유가 없다 |
| **D-M2-3** | **Python 쪽 자리.** `ml-engine/` 은 M5 5A 가 만든다 | (a) **M2 가 `ml-engine/` 최소 골격(`pyproject.toml` + `contracts/` 생성물 + round-trip test)만 세우고 5A 가 나머지 패키지·import boundary 를 완성** (b) M2 는 Python 생성물을 임시 디렉터리에만 두고 5A 가 이전 | **(a)** — (b) 는 이전 누락 위험(ADR 0009 D-3 「`_workspace/` 는 기록 자리가 아니다」와 같은 갈래). 5A 의 선행 조건 문면이 「M2 proto 와 provider contract 승인」이라 M2 가 Python 생성물을 어디엔가 두어야 한다. serving/training dependency 분리(§5)는 5A 소관이고 M2 는 `grpcio`·`protobuf` 런타임만 lock 한다 |
| **D-M2-4** | **`EstimateShortfall` RPC.** `milestone-2.md` 2B: *"이 계산을 Python에 둘 필요가 M0에서 승인된 경우에만 정의"* | (a) **제외** (b) 정의 | **(a)** — M0 은 승인하지 않았고, 1D 가 floor-shortfall 커널을 Kotlin `decision` 모듈에 두었다(`decision/…/FloorShortfallKernel.kt`, decision 28). `v2-지침서.md` §3.2 「Kotlin이 이미 판정한 자격·법정 하한을 Python이 다시 판정하지 않는다」. 조사 노트 01 (e) 실측 — legacy 에서도 이 계산은 ML 경로 밖(`app/domain`+`app/services`)이었다 |
| **D-M2-5** | **2C training transport.** `milestone-2.md` 2C: *"별도 job API 또는 broker contract 중 ADR에서 하나를 선택한다"* | (a) **별도 job API — 같은 gRPC 서버의 unary RPC 셋(Start/Get/Cancel), Kotlin 이 폴링** (b) broker contract (c) 서버 스트리밍으로 상태 push | **(a)** — ADR 0003 D-5(역방향 호출 없음·Kotlin 이 조회)·ADR 0005(브로커 없음)·§7(측정된 필요 없이 무거운 도구 금지). (c) 는 장시간 연결을 붙잡아 D-4 「동기 RPC 로 붙잡지 않는다」의 취지를 형태만 바꿔 어긴다. 상세는 `ADR 0010` 초안 |
| **D-M2-6** | **율·금액의 wire 표현.** 2A 문면: money 「정수 원화 **또는** scale 명시 decimal string」 | (a) **money = `int64` 원(`currency` 필수, KRW 만 허용값) · rate = decimal string + `scale`(`double` 금지)** (b) 둘 다 decimal string (c) rate 를 `double` | **(a)** — 원화는 소수 단위가 없어 정수가 정확하고 `Money.amount` 의 V2 내부 표현과 일치. 율은 `double` 로 나가면 「계약 version 과 artifact 로 모든 추천 재현」(§9)이 부동소수 표현 경계에서 깨지고 도메인 `api.forbidden.types` 가 `Double` 을 막는 취지와 어긋난다. Python 은 5E 의 request validation·Numpy conversion 자리에서 `Decimal → float` 를 **한 번** 한다(변환 지점이 하나) |
| **D-M2-7** | **proto3 vs editions.** | (a) **proto3** (b) edition 2023 | 조사 노트 `02_grpc_stack_compat.md` 결과에 따른다 — 잠정 **(a)**: Kotlin `protoc-gen-grpc-kotlin`·Python `grpcio-tools`·buf 세 도구가 전부 안정 지원하는 교집합이 proto3 이고, editions 의 이점(필드 presence 기본값 제어)은 `optional` 키워드로 충분하다. **enum 은 `_UNSPECIFIED = 0` 필수 + 미지원 값 fail-closed**(open enum 이라 미지의 정수가 파싱을 통과한다 — 2A 규칙) |
| **D-M2-8** | **`OPEN-ML-03` 을 계약이 받는가** — 가격 적합도와 낙찰 확률의 구분을 타입으로 | (a) **계약 수준 타입 분리**: 응답 메시지 이름과 필드 이름에 `probability`·`win_rate` 를 두지 않고 `PriceFitness`(점수)와 `Uncertainty`(§6.5 세 성분 `sample_size`·`dispersion`·`estimate_margin`)만 정의. 낙찰 확률 축은 **필드가 없다** — 필요해지면 breaking 아닌 추가로 별도 메시지 (b) 문서 규율 | **(a)** — `capability-map.md` ML-03 「메커니즘이 선언된 형태로 하나 존재해야 한다」. 계약은 양쪽이 공유하는 유일한 타입 자리라 여기서 갈라 두면 4개 파일 복제 경고가 필요 없다. `OPEN-ML-02`(Platt → 자격 라벨 계약)의 결정 행은 **D-M2-11** |
| **D-M2-9** | **`OPEN-ADR-11` 값의 자리** — deadline·재시도·백오프 | (a) **규칙은 ADR 0010 이 고정, 값은 `adapters` 정책 데이터(versioned)로 두고 초기값은 5E 실측 전까지 「보수적 상한 + 측정 의무」** (b) ADR 에 값을 박음 | **(a)** — legacy 에 gRPC 경로가 없어 이식할 실측이 없다(ADR 0003 §5). 값을 ADR 에 박으면 첫 실측에서 ADR 을 고쳐야 한다. §5 매직넘버 규율(근거와 policy version) |
| **D-M2-10** | **`denominator_source` 피처의 어휘 축.** legacy 피처 값은 `ReliableBaseSource`(선택 결과 넷), V2 판정 라벨은 `BaseAmountProvenance`(다섯) — 다른 축이라 1:1 매핑이 없고, 바꾸면 피처 공간이 바뀌어 **재학습**이 필요하다(§6.3) | (a) **계약은 V2 라벨(`BaseAmountProvenanceLabel`, 2A ③)만 싣고, 피처 공간 전환(재학습)은 M5 5B·5C 소유로 명시** — legacy 어휘를 계약에 넣지 않는다 (b) 계약에 legacy 어휘 enum 을 두고 Kotlin 이 매핑 (c) 두 어휘를 다 실음 | **(a)** — (b) 는 ml-engine 이 「어느 값을 골랐나」라는 Kotlin 판정 과정을 알게 되는 경계 위반이고 매핑이 정보를 만들어 낸다(Unknown ↔ unavailable 만 대응). (c) 는 같은 사실 두 자리. **대가**: 5B 가 피처 공간을 V2 라벨 위에서 다시 정의하고 5C 가 재학습 — `capability-map.md` ML-11.4 F3(백필 상태가 피처였던 대가)와 같은 갈래의 정리. 같은 이유로 `Money.provenance`(금액 획득 축, 여섯)와 이 라벨(판정 축, 다섯)도 **한 enum 에 접지 않는다**(리뷰 r1) |
| **D-M2-11** | **`OPEN-ML-02` — Platt 캘리브레이션의 자격 라벨을 M2 계약에 넣는가.** 정본 행(§14.2)의 결정 주체는 운영자, 대상은 M2. 조사 (b-5): 캘리브레이션은 price prediction 응답엔 없지만 opportunity analysis 의 P(낙찰)로 사용자에게 도달하고 라벨이 **자격 판정 결과**에 의존한다 | (a) **넣지 않는다** — 자격은 Kotlin 1C 소유(§3.2), 계약에 자격 라벨을 실으면 ml-engine 이 업무 판정을 입력으로 받는다. P(낙찰) 축은 계약에 필드가 없다(D-M2-8) (b) 자격 라벨을 피처 fact 로 실어 캘리브레이션을 M5 가 이식 | **(a)** — ML-09 의 「결정 전에 확정되는 제약」(가격 적합도와 확률의 혼동 금지)이 (b) 의 전제를 이미 막는다. 남는 물음 「Platt 가 사용자 응답에 도달해야 하는가」는 M5 5C·5D 와 M4(opportunity 축 소유 slice)로 이월 |
| **D-M2-12** | **`latest_promoted` 선택자를 계약에 두는가**(ADR 0010 §4) | (a) 둔다 — 재현성은 응답의 `release_id` 로, client 는 `GetModelMetadata.promoted` 와 대조(2A ⑥ (b)) (b) `exact_release` 만 | **(a)** — 4D 가 「현재 승격 릴리스」를 알려면 어차피 `GetModelMetadata` 를 불러야 하고, 그 사이 승격이 바뀌는 창은 (b) 에도 있다. (a) 의 구멍(폴백 predictor)은 2A ⑥ (b) 의 대조 규칙이 막는다 |
| **D-M2-13** | **`MODEL_NOT_READY` 의 층**(ADR 0010 §4) | (a) application failure(`retryable=true`) (b) gRPC `UNAVAILABLE` | **(a)** — 프로세스는 살아 있고 계약이 정의한 상태라 D-3 의 층 구분상 application |
| **D-M2-14** | **training job 조회 주기의 자리**(ADR 0010 §4) | (a) `adapters` 정책 데이터 (b) 스케줄러 설정 | **(a)** — D-M2-9 와 같은 이유 |

---

## 4. `OPEN` 처리 후보 (착수 시 `capability-map.md` §14.2 에 반영 — 지금은 1E 가 편집 중이라 손대지 않는다)

| OPEN | M2 처리 |
| --- | --- |
| `OPEN-ADR-11` | **닫는 후보** — ADR 0010 이 규칙·error mapping·재시도 가능 분류·미가용 상태 이름을 고정, 값은 D-M2-9 |
| `OPEN-ML-03` | D-M2-8 (a) 로 **계약 수준에서 닫는 후보**. M5 구현 축은 5D 가 승계 |
| `OPEN-ML-02` | **D-M2-11**(운영자 결정 행) — 추천 (a) 넣지 않음. 남는 물음 「Platt 가 사용자 응답에 도달해야 하는가」는 M5 5C·5D·M4 |
| `OPEN-ML-05`·`OPEN-ML-06` | M5 소유, M2 무관 |
| `OPEN-DIC-03`(`SkipReason`) | DEC 축, 계약에 실리지 않는다(verdict 는 Kotlin) |
| 신설 후보 `OPEN-M2-DEADLINE-VALUES` | D-M2-9 (a) 의 대가 — 초기 deadline·retry 값의 실측 근거를 5E 가 낸 뒤 정책 version 갱신 |
| 신설 후보 `OPEN-2A-RELEASE-CHECK-4D` | 제3 변환 금지(2A ⑥)의 client 집행을 M4 4D 가 인계 — M2 는 fake 위의 test 까지(리뷰 r1) |

---

## 5. 병행 규칙 (M1/1E 진행 중)

- **M2 코드 착수 금지** — 선행 조건 미충족. 이 문서와 `2a/scope.md`·`ADR 0010` 초안·조사 노트까지만.
- 금지 경로: 1E in_scope 전부(`strategy/**`·`config/quality/**`·`app/**`·`fixtures/**`·`milestone-1.md`·
  `reports/evidence/m1/1e/**`) + `docs/discovery/capability-map.md`·`data-dictionary.md`(1E 가 §14.2·§2.2.6 편집 중).
- gradle 미실행. 편집 즉시 pathspec 커밋(`git add <경로>` → `git commit -- <같은 경로>`).
- 착수 시 할 일: 각 slice `base_sha` 재고정 → **`ADR 0010` 승인**(2A 전건) → 착수 전 결정 수령 — **D-M2-1~14**(이 표) ·
  2A D-2A-0·7 · 2B D-2B-1~4 · 2C D-2C-1~2 · 2D D-2D-1·3·4 → `milestone-2.md` 착수 문단 → `capability-map.md` §14.2 반영
  (`OPEN-ADR-11`·`OPEN-ML-02`·`OPEN-ML-03`, H-1 `agency_sample_count` 이름 정정) → 2A 부터(첫 단계 grpc-kotlin 1.5.0 + grpc-java 1.84.0 스모크).

---

## 6. 조사 결과 요약 (`_workspace/m2-prep/` — gitignore, 저장소 밖)

> 조사 레인 둘의 요약을 이 절에 인라인한다. 레인 완료 전에는 「대기」.

- `01_scout_ml_interface.md`(legacy ML 인터페이스 실물, 기준 commit `ed4b06c`, 2026-09-06):
  - legacy 에 `.proto` 0건 — 계약은 이식이 아니라 신규 산출물, 옮길 것은 **값의 형태**뿐.
  - **GBM 피처 5개**(`category`·`log_amount`·`agency_encoding`·`agency_sample_count`·`denominator_source`)의 **이름·순서가 곧 계약**
    (`AWARD_RATE_FEATURE_NAMES`) — 불일치는 이미 fail-closed. 2B 의 피처 벡터는 이 형태(이름 붙은 필드 + schema version)를 계승.
  - 피처 금액은 `float` 원 값이고 **basis·VAT·provenance 태그가 없다** — 축 정합이 주석으로만 보증. 2A ① 의 다섯 성분이 닫는 구멍
    (`won` 만 보내면 R-BASIS-06 이 wire 에서 되살아난다).
  - **응답에 checksum 도 feature schema version 도 없다**(`model_version` 문자열 하나) — §9 「artifact 로 추천 재현」이 legacy 형태로는
    불성립. 2A ④·2B 출력의 release/checksum/schema 필수 근거.
  - 응답 필드 **약 20개가 업무 판정**(`review_required`·guardrail/floor 10·granularity 3·regime 2·후보 선택 2) — 계약에서 전부 제외.
    diagnostics 는 전부 `dict[str, Any]`·자유 문자열 — 2B 는 **타입 있는 diagnostics 만**.
  - **(c-2) 가장 무거운 반례**: 모든 추론 실패가 `except Exception` → historical predictor 의 **값 있는 답**으로 접힌다
    (`orchestration.py:264-273`; 미학습 공종 `ValueError`·아티팩트 손상·lightgbm 부재 전부). 응답 shape 이 성공과 같고 차이는 자유
    문자열 `fallback_reason` 뿐. 「`Unmeasurable` → 다른 모델의 성공값」은 M2 완료 조건 문면(transport error·0)이 못 잡는 **제3 변환**
    → ADR 0010 D-3 에 금지 규칙 추가, 2A ⑥ 에 「응답 release ≠ 요청 `exact_release` 면 client 거부」 추가.
  - `confidence_score` 는 [0.45, 0.95] 클램프라 0 이 될 수 없고 「측정 불가」를 나르는 신뢰도 표현이 없다. 불확실성의 출처(교차검증
    잔차 vs 시간 홀드아웃)는 한국어 설명 문자열에만 — ML-01 판정대로 구조화 필드(2B).
  - 2C 9항목 중 legacy 대응물은 artifact manifest·evaluation report reference **둘뿐**. immutable dataset reference·idempotency key·
    cancel·retry 부재, 상태 어휘는 Celery 이름이고 미지 상태가 `queued` 로 접힘 → 2C 는 전부 신규.
  - `EstimateShortfall`: legacy 에서도 ML 경로 밖(`app/domain`+`app/services`), V2 는 `decision/FloorShortfallKernel.kt` 소유 확정 —
    **D-M2-4 (a) 제외 근거 실측 확인**.
  - `OPEN-ADR-11` 값 근거 legacy 에 **없음**(추론 지연 실측·타임아웃·재시도 전무; Celery 전역 1800/1500초는 다른 축) — D-M2-9 (a) 지지.
  - Platt 캘리브레이션은 price prediction 응답엔 없지만 opportunity analysis 의 P(낙찰)로 사용자에게 도달하고 라벨이 **자격 판정
    결과에 의존** — `OPEN-ML-02` 는 이 조사가 닫지 않음(결정 행 D-M2-11, 사유 보강).
  - 어휘 불일치 13건 중 **H-2** 가 가장 무겁다 — `denominator_source` 값 어휘(`ReliableBaseSource`: clean-base·reserve-estimate·
    base-fallback·unavailable = 「어느 값을 골랐나」)와 `BaseAmountProvenance`(Clean·DerivedYega·DerivedVat·SuspectRatio·Unknown =
    「어떻게 만들어졌나」)는 **다른 축**. 갈아끼우면 피처 공간이 바뀐다(§6.3) → **D-M2-10** 신설.
- `02_grpc_stack_compat.md`(gRPC 스택 버전 호환, 2026-09-06 — Maven Central·PyPI 메타데이터 curl 실측 기준):
  - **고정 후보 조합**: Kotlin `grpc-kotlin-stub`/`protoc-gen-grpc-kotlin` **1.5.0 리터럴**(Maven `latest` 메타데이터가 커밋 해시를
    가리켜 동적 버전은 재현 불가) · grpc-java 계열(`grpc-netty-shaded`·`grpc-protobuf`·`grpc-stub`·`grpc-testing`) **1.84.0** BOM ·
    `protobuf-java` **3.25.9**(grpc-java 1.84.0 의 실제 의존; Protobuf 4.x 전환은 grpc-java 이슈 #11015 open) · Python `grpcio`/
    `grpcio-tools`/`grpcio-testing` **1.83.1** + `protobuf` **7.36.1**(Python 3.12·macOS arm64 wheel 확인) · `buf` CLI **1.72.0**.
  - **D-M2-7 → proto3 확정 근거**: edition 2023 은 grpc-java/grpc-kotlin codegen 지원 미성숙(이슈 #11526 open).
  - **buf 는 lint·breaking 전부 로컬 완결**(BSR 불요) — codex-review-gate 의 네트워크 차단 운영과 맞는다.
  - **위험**: ① grpc-kotlin 1.5.0 POM 이 `grpc-stub:1.62.2` 를 선언해 1.84.0 과 22 마이너 차 — 검증 이력 없음, **2A 착수 시
    컴파일+런타임 스모크 필수**. ② Java `protobuf-java`(3.x/4.x)와 Python `protobuf`(7.x)는 **독립 버전 축** — 「같은 major 라
    호환」 서술 금지, 교차 언어 호환은 wire format(proto3)에서만. ③ gRPC Python 은 fork-불안전(`GRPC_ENABLE_FORK_SUPPORT` 는
    poll/epoll1 에서만) — 2C training 워커의 프로세스 모델을 먼저 확정. ④ Kotlin 취소 → Python `ServicerContext.cancelled()`
    미해결 버그(grpc/grpc#36193) — 2D cancellation test 는 폴링이 아니라 **실제 리소스 해제**로 검증(ADR 0010 D-2 갱신 근거).
  - 미확인: `grpcio-tools` 번들 protoc 정확 버전(명령 한 번으로 해소), `grpcio-testing` 의 `grpc.aio` in-process fake 1급 지원.
