# ADR 0010 — ML 호출의 시간·실패 예산, error mapping, 호환성 규칙, training transport

- **상태**: **승인 — 운영자 결정 2026-09-06(M2 착수, 2A 전건).** §4 의 세 물음은 추천안대로 확정됐다 — `latest_promoted`
  선택자를 계약에 둔다(D-M2-12 (a)) · `MODEL_NOT_READY` 는 application failure(`retryable=true`)(D-M2-13 (a)) · training job
  조회 주기는 `adapters` 정책 데이터(D-M2-14 (a)). `OPEN-ADR-11` 은 이 승인으로 닫힌다(§5). 원 지위: 초안 — M2 slice 2A 착수
  전건으로 승인 대상(`v2-지침서.md` §3.3 *"M2에서 deadline, retry, error mapping, compatibility를 포함한 ADR을 승인한다"*). 2A ④(정책 참조)·⑥(제3 변환 금지)과 2D ⑥(deadline·재시도 test)이 이 ADR 의
  D-1·D-2·D-3·D-4 위에 서므로 **2A 보다 먼저 승인**한다 — 2C 가 갱신할 상태가 아니다. M1/1E 병행 중에 세션 모델이 단독으로
  썼다(CLAUDE.md 운영자 지시 2026-09-04). 값은 정하지 않고 규칙을 정한다(§2 D-1). 운영자 결정이 필요한 자리는 §4 이고
  `reports/evidence/m2/prep/m2-prep.md` 의 착수 전 결정 D-M2-12~14 가 그것을 수령한다.
- **작성일**: 2026-09-06
- **대응**: `ADR 0003` D-9(*"구체 semantics는 M2 ADR이 확정한다"*) · `ADR 0003` §5 `OPEN-ADR-11` · `milestone-2.md`
  2A 「deadline 정책」·「retryable/non-retryable application error」 · 2C 「transport는 별도 job API 또는 broker
  contract 중 ADR에서 하나를 선택한다」
- **관련 ADR**: 0001 D-6(ML 미가용은 관측 가능한 상태) · 0003(계약과 전송 — 축 고정) · 0005(내부 workflow 큐 —
  브로커 없음, **다른 축**) · 0006 D-6(생성 stub 은 도메인에 노출되지 않는다) · 0007(contract test 층)
- **legacy 기준 commit**: `ed4b06c` — **legacy 에는 gRPC 경로가 없다**(ADR 0003 §1.3). 이식할 실측이 없다.

---

## 1. 맥락

### 1.1 무엇이 열려 있었나

`ADR 0003` 은 축(Protobuf/gRPC · 추론 동기/학습 job 분리 · 콜백 없음 · 이중 구현 금지 · 조용한 폴백 금지)을 고정하고
**값과 semantics** 를 M2 로 미뤘다. `OPEN-ADR-11` 이 그 미룸의 이름이다 — *"deadline 값, 재시도 횟수·백오프, 어떤 오류를
재시도 가능으로 볼지, ml-engine 미가용의 하류 상태 이름"*.

### 1.2 왜 값을 지금도 정할 수 없나

- 요청 단위 지연의 실측이 없다 — serving(5E)이 없고 legacy 는 같은 프로세스 import 였다.
- 그러나 **규칙은 실측 없이도 선다**: 어떤 오류가 재시도 가능한가는 의미의 문제이고, 재시도가 안전한가는 멱등성의
  문제이며, 값이 어디에 사는가는 `v2-지침서.md` §5(매직넘버는 근거와 policy version)의 문제다.

### 1.3 소비자와 제공자

- Kotlin 소비자: `milestone-4.md` 4D — *"deadline, cancellation, circuit breaker, bounded retry"*, *"ML unavailable을
  `review/unavailable`로 처리하는 fail-safe 정책"*. 완료 조건 *"ML timeout 시 thread/connection이 고갈되지 않고 업무
  결과가 fail-safe"*.
- Python 제공자: `milestone-5.md` 5E — *"deadline/cancellation/status mapping"*, *"graceful shutdown과 bounded
  concurrency"*.
- 이 ADR 은 **둘이 공유하는 규칙**을 적는다. 배선은 각 마일스톤이 한다.

---

## 2. 결정

### D-1. 값은 정책 데이터, 규칙은 이 ADR

deadline·재시도 횟수·백오프·circuit breaker 임계는 **Kotlin `adapters` 의 versioned 정책 데이터**다(`EffectiveDatedPolicy`
관례 — 1C·1D 와 같은 배관). 계약의 요청 봉투는 값이 아니라 **`policy_version` 참조**를 싣는다(2A ④) — 응답을 재현할 때
「어느 예산으로 불렀는가」가 남는다. 초기값은 **보수적 상한 + 측정 의무**로 두고, 5E 가 실측을 낸 뒤 version 을 올린다
(신설 `OPEN-M2-DEADLINE-VALUES`). 이 ADR 에 숫자를 적지 않는다.

### D-2. deadline 은 필수이고 호출자가 정한다

- 모든 RPC 호출은 deadline 을 **가진다**. deadline 없는 호출은 client 배선 test 가 거부한다(4D).
- deadline 은 **업무 요청의 남은 예산**에서 유도한다 — 상위 workflow 가 예산을 갖고, ML 호출은 그 안의 한 구간이다.
  전파는 gRPC deadline 메타데이터로, 봉투에는 정책 참조만(D-1).
- Python servicer 는 `context.is_active()` 를 **긴 계산 앞에서** 확인하고 취소된 요청에 계산을 쓰지 않는다(5E).
  Kotlin coroutine cancellation 은 gRPC cancel 로 전파돼야 한다(4D 의 coroutine client 가 그 성질을 갖는지는 조사 노트
  02 가 확인).

### D-3. 결과 셋과 상태의 분리 — transport error 와 domain result 를 섞지 않는다

| 층 | 표현 | 예 | 재시도 |
| --- | --- | --- | --- |
| **transport** | gRPC status ≠ `OK` | `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `RESOURCE_EXHAUSTED`, `UNAUTHENTICATED` | 상태별(D-4) |
| **application failure** | status `OK` + `oneof result = ApplicationFailure { code, retryable }` | `UNSUPPORTED_SCHEMA`, `UNSUPPORTED_RELEASE`, `INVALID_REQUEST`, `MODEL_NOT_READY` | `retryable` 필드가 정한다 — 계약 소유 |
| **domain result** | status `OK` + `oneof result = Unmeasurable { reason }` 또는 `Success` | `INSUFFICIENT_SAMPLES`, `UNTRAINED_SEGMENT`, `FEATURE_ABSENT` | **재시도 대상이 아니다** — 같은 입력은 같은 답 |

- `Unmeasurable` 은 **성공한 호출의 정직한 답**이다. transport error 로 바꾸지 않고, `0`·빈 후보·기본값으로 접지 않는다
  (`milestone-2.md` 완료 조건 · ADR 0001 D-6).
- **제3 변환 금지 — `Unmeasurable` 을 다른 predictor 의 성공값으로 접지 않는다.** legacy 는 모든 추론 실패를 `except Exception`
  으로 받아 historical predictor 의 값 있는 답으로 바꿨다(조사 노트 01 (c-2), `orchestration.py:264-273` — 응답 shape 이 성공과
  같고 차이는 자유 문자열 `fallback_reason`). 계약은 이것을 형태로 막는다: `Success` 는 **요청이 지목한 release**(`exact_release`)
  로만 답하고, 다른 artifact 가 답하면 `ApplicationFailure(UNSUPPORTED_RELEASE)` 다. **`latest_promoted` 요청에도 구멍이 없다** —
  servicer 는 그 시점의 승격 release 로만 답하고 client 는 응답의 `release_id` 를 `GetModelMetadata.promoted` 와 대조한다
  (불일치 = `UNSUPPORTED_RELEASE` 취급; 승격이 그 사이 바뀐 정상 경우는 재호출로 수렴). legacy 반례는 정확히 요청이 release 를
  지목하지 않는 경로에서 일어났으므로 이 경로에 규칙이 없으면 금지가 비어 있다. predictor 교체는 운영자의 승격 결정(`promoted`
  갱신)이지 요청 처리 중의 폴백이 아니다. `Success` 에 fallback 표지·자유 문자열 사유 필드를 두지 않는다.
- **어휘 소유** — application failure 의 `FailureCode` 는 2A 가 단독 소유하고(`UNSUPPORTED_SCHEMA`·`UNSUPPORTED_RELEASE`·
  `UNSUPPORTED_TRAINING_SPEC`·`INVALID_REQUEST`·`MODEL_NOT_READY`·`IDEMPOTENCY_CONFLICT`·`JOB_NOT_FOUND`), transport status 이름
  (`RESOURCE_EXHAUSTED` 등)과 겹치는 값을 두지 않는다. training job 의 종료 사유는 별도 enum(`JobFailureCode`, 2C) — 층이 다르다.
- servicer 가 `INVALID_ARGUMENT` 같은 gRPC status 로 **계약 위반**을 표현하는 것은 허용하되(예: 파싱 불가), **계약이
  정의한 실패**는 application failure 로 낸다 — 두 층에 같은 실패를 이중으로 두지 않는다. 어느 것이 어느 층인지는 2A
  `error.proto` 의 주석이 표로 갖는다.

### D-4. 재시도는 멱등성과 상태로만 결정된다

- **추론 RPC 는 멱등**이다 — 같은 `request_id`·같은 입력·같은 `exact_release` 는 같은 답. 따라서 transport 재시도가 안전하다.
  `latest_promoted` 선택자는 두 호출 사이에 릴리스가 바뀔 수 있어 **재시도 시 첫 응답의 release 로 고정할 수 없다** —
  재시도는 허용하되 응답의 `release_id` 를 그대로 기록한다(재현성은 응답이 지목한 artifact 로 성립).
- 재시도 가능한 transport 상태: `UNAVAILABLE`, `DEADLINE_EXCEEDED`(남은 예산이 있을 때만), `RESOURCE_EXHAUSTED`(백오프 필수).
  재시도 불가: `INVALID_ARGUMENT`, `FAILED_PRECONDITION`, `UNIMPLEMENTED`, `UNAUTHENTICATED`, `PERMISSION_DENIED`, `INTERNAL`.
- application failure 는 `retryable` 필드대로. `MODEL_NOT_READY` 만 `retryable=true` 후보(기동 직후) — 나머지는 false.
- **횟수 상한과 백오프는 정책 데이터**(D-1). 상한을 넘으면 D-6 의 상태로 간다. `milestone-2.md` 완료 조건 *"fake server
  장애 시 retry 가능한 경우만 제한 횟수로 재시도"* 가 2D 의 test 다.
- **training job RPC 는 `StartTraining` 만 비멱등**이다 — `idempotency_key` 로 멱등화한다(같은 키 = 같은 job 반환). `Get`·
  `Cancel` 은 멱등.

### D-5. circuit breaker 는 소비자 쪽 정책이고 계약이 아니다

열림·반열림 임계는 `adapters` 정책 데이터(D-1, `resilience4j` — `ADR 0005` 가 채택한 라이브러리 재사용). 계약에는 흔적이
없다. 열려 있는 동안의 업무 결과는 D-6.

### D-6. 미가용의 이름은 하나이고 하류는 fail-safe 다

재시도 상한 초과·circuit open·`MODEL_NOT_READY` 지속·deadline 소진은 전부 Kotlin 도메인의 **`MlUnavailable(reason)`** 하나로
들어간다(어댑터가 매핑, M4). 이름은 `data-dictionary.md` 의 판정 어휘가 소유하므로 **M4 4D 착수 시 사전에 등재**한다 —
이 ADR 이 고정하는 것은 「상태가 하나이고, 사유를 나르며, 추천값으로 대체되지 않는다」다. 하류 처리는 `milestone-4.md`
4D 문면 *"`review/unavailable`"* — 추천 없음이지 낮은 추천이 아니다(fail-open 금지).

### D-7. 호환성 규칙 — 계약 version 은 패키지가 나른다

- 패키지 `bidvector.ml.v1`. **breaking change 는 `v2` 패키지**이고 같은 패키지 안에서는 추가만 한다(필드 추가·enum 값 추가
  — 수신 측 fail-closed 규칙(2A ⑥) 때문에 enum 값 추가도 **양쪽 배포 순서**가 필요하다: 제공자 먼저).
- 필드 번호 재사용 금지, 제거는 `reserved` — buf breaking 이 직전 승인 태그 대비로 잡는다(2D 가 실제 breaking mutation 으로
  증명).
- `feature_schema_version` 은 패키지 version 과 **다른 축**이다 — 계약 형태는 같고 피처 집합만 바뀌는 경우를 위한 것.
  servicer 가 아는 집합 밖이면 `UNSUPPORTED_SCHEMA`(fail-closed).
- 생성 코드는 수동 편집하지 않는다 — 생성물은 게이트 밖 included build(`ml-contract`)의 `build/` 에만 있어 구조적으로 불가하고, 생성의 결정성·비커밋·무소스를 2D 가 실측한다(2A D-2A-0 (c)).

### D-8. training transport — 별도 job API, 같은 gRPC 서버, Kotlin 이 폴링

`milestone-2.md` 2C 의 양자택일에서 **별도 job API** 를 택한다.

- `TrainingJobService { StartTraining, GetTrainingJob, CancelTrainingJob }` — 전부 unary. 전이표: `ACCEPTED → RUNNING →
  SUCCEEDED | FAILED` 와 `ACCEPTED | RUNNING → CANCELLED`(취소는 두 비종료 상태 모두에서). 표에 없는 전이는 거부
  (`data-dictionary.md` §2.2 관례와 같은 형태 — 전이표는 계약 주석과 provider test 가 갖는다, 2C ④·⑤ 와 동일 표기).
- Kotlin 이 **주기 조회**한다(ADR 0003 D-5). 조회 주기는 `ADR 0005` 의 DB 기반 스케줄러가 든다 — 브로커 없음.
- 서버 스트리밍 push 는 불채택 — 장시간 연결은 「동기 RPC 로 붙잡지 않는다」(ADR 0003 D-4)의 취지를 형태만 바꿔 어긴다.
- broker contract 는 불채택 — 측정된 필요 없음(§7), 두 runtime 사이에 세 번째 구성요소, ml-engine 이 브로커 어휘를 알아야 함.
- `StartTraining` 입력은 **immutable dataset reference + manifest checksum** 이다. ml-engine 은 DB 를 모르므로 dataset 은
  object/file storage 참조이고(`v2-지침서.md` §3.2 `adapters/` — training 전용 storage adapter), 그 참조를 누가 만드는가는
  M5 5C 소유.

---

## 3. 대안 (요지 — 채택 근거는 §2 각 항)

| # | 대안 | 판정 |
| --- | --- | --- |
| A-1 | deadline·retry 값을 ADR 에 박음 | 불채택 — 실측 없음, 첫 실측마다 ADR 개정 |
| A-2 | `Unmeasurable` 을 gRPC status(`FAILED_PRECONDITION`)로 | 불채택 — transport 와 domain 혼합, 재시도 정책이 도메인 답을 재시도 |
| A-3 | 모든 오류를 retryable 로 두고 횟수만 제한 | 불채택 — 계약 위반을 반복 호출, D-4 |
| A-4 | training 상태를 서버 스트리밍으로 push | 불채택 — D-8 |
| A-5 | training 을 broker contract 로 | 불채택 — D-8 |
| A-6 | 호환성을 `feature_schema_version` 하나로 | 불채택 — 계약 형태와 피처 집합은 다른 축(D-7) |

---

## 4. 운영자 결정이 필요한 자리 (2A 착수 전 — `m2-prep.md` D-M2-12~14 로 수령, 여기서는 묻지 않는다)

| 물음 | 선택지 | 추천 |
| --- | --- | --- |
| `latest_promoted` 선택자를 계약에 두는가 | (a) 둔다(운영 편의, 재현성은 응답의 `release_id` 로) (b) `exact_release` 만 | **(a)** — 4D 가 「현재 승격 릴리스」를 알려면 어차피 `GetModelMetadata` 를 불러야 하고, 그 사이 승격이 바뀌는 창이 (b) 에도 있다 |
| `MODEL_NOT_READY` 를 application failure 로 두는가 gRPC `UNAVAILABLE` 로 두는가 | (a) application failure(`retryable=true`) (b) `UNAVAILABLE` | **(a)** — 프로세스는 살아 있고 계약이 정의한 상태라 D-3 의 층 구분상 application |
| training job 조회 주기의 자리 | (a) `adapters` 정책 데이터 (b) 스케줄러 설정 | **(a)** — D-1 과 같은 이유 |

---

## 5. 이 ADR 이 등록·닫는 `OPEN`

- `OPEN-ADR-11` — **닫는 후보**(승인 시). 규칙은 §2, 값은 정책 데이터.
- 신설 `OPEN-M2-DEADLINE-VALUES` — 초기 deadline·retry·backoff·breaker 값의 실측 근거. 소유 M5 5E(실측) → M4 4D(정책 version).

## 6. 확인하지 않은 것

- Kotlin coroutine cancellation → gRPC cancel 전파와 Python `context.is_active()` 의 실제 거동. 조사 노트 02 가 미해결 버그
  (grpc/grpc#36193 — 콜백 중 `cancelled()` 가 False)를 찾았다 — **2D 의 cancellation test 는 servicer 의 플래그 폴링이 아니라
  취소 뒤 실제 리소스 해제(계산 중단·연결 반환)로 검증한다.** D-2 의 「긴 계산 앞에서 확인」은 유지하되 그것만으로 충분하다고
  보지 않는다.
- gRPC Python 의 fork-불안전(조사 노트 02) — 2C training 워커가 fork 기반이면 servicer 프로세스와 분리해야 한다. 프로세스
  모델은 M5 5A·5E 소유이고 이 ADR 은 「servicer 프로세스 안에서 fork 하지 않는다」만 요구한다.
- `latest_promoted` 와 재시도의 상호작용을 fake servicer 로 재현할 수 있는지(2D).
- 학습 job 의 `FAILED` 사유 어휘 — 5C 가 evaluation report 형태를 낸 뒤 2C 가 enum 을 확정.
