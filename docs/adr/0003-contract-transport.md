# ADR 0003 — Kotlin ↔ Python ML engine 내부 계약과 전송

- **상태**: **결정** — M0 slice 0D가 Codex `approve`로 닫혔다
  (`reports/evidence/m0/0d/codex-review-20260829T222217Z.json`, `reviewed_base` `998dc217` …
  `reviewed_head` `df056259`, 2026-08-29, `model_reasoning_effort=high`). 갱신 시점은
  **2026-08-30 · M0 종료 slice 0E**다 — 0D 종료 뒤에도 이 줄이 *"제안됨 … 대기"*로 남아
  **문면이 사실을 따라가지 않았다.**
  **`milestone-0.md` 완료 조건의 「사용자 명시 승인」을 받았다** — **운영자, 2026-08-31**.
  물음 *"「M0 승인」이 ADR 0001~0009 채택까지 덮는가"*에 **「덮는다 — ADR 채택 포함」**.
  **이 승인은 Codex `approve`가 만든 것이 아니다** — 위 0D `approve`와 별개로 운영자에게
  직접 물어 받았다. 기록의 **정본**은 `milestone-0.md` 「승인에 드는 것」의 ADR 행이며,
  같은 승인의 다른 대상(활성 `OPEN` 이월)은 `docs/discovery/capability-map.md` §14다.
- **작성일**: 2026-08-28
- **대응**: `milestone-0.md` §"Slice 0D" 결정 **4** (gRPC/Protobuf 내부 계약)
- **legacy 기준 commit**: `ed4b06c`
- **관련 ADR**: 0001(두 runtime 경계) · 0005(내부 workflow 큐 — **다른 축**)

---

## 1. 맥락

### 1.1 이 ADR이 다루는 축

`reports/evidence/m0/0a2/decisions.md`의 `OPEN-OPS-05` 절이 두 축을 명시적으로 갈랐다.

> **범위 주의(기록 필수)**: 운영자 답의 gRPC·callback은 **Kotlin ↔ Python ML 호출 경로**를
> 가리킨다. 이 결정이 닫는 것은 그와 별개인 **내부 workflow 큐**(주기 수집, 알림 발송,
> 재시도, outbox)의 기반 선택이다. 두 축을 같은 결정으로 접지 않는다.
> ML 호출 경로의 전송은 ADR 0003(gRPC/Protobuf 내부 계약) 소관이다.

**이 ADR은 앞의 축**(Kotlin ↔ ml-engine 호출 경로)을 소유한다. 뒤의 축(내부 workflow 큐)은
**ADR 0005**가 소유한다.

### 1.2 승인된 기본안

`v2-지침서.md` §3.3:

> - Kotlin↔Python의 기본안은 Protobuf/gRPC다.
> - M2에서 deadline, retry, error mapping, compatibility를 포함한 ADR을 승인한다.
> - 성능 측정이나 운영 제약이 gRPC의 이점을 입증하지 못하더라도, 이미 채택한 계약을 몰래
>   HTTP로 이중 구현하지 않는다. 변경은 ADR로 한다.
> - 장시간 training은 동기 RPC로 붙잡아 두지 않는다. job command/status 계약을 별도로 둔다.
> - Kotlin이 Python 내부 task 이름이나 Celery wire format을 알지 못한다.

### 1.3 legacy에는 이 계약이 없다

```
git -C bid-vector ls-tree -r --name-only ed4b06c | grep -c '\.proto$'   → 0
```

(재현: `commands.md` **C-5.4**)

**계약은 이식 대상이 아니라 신규 산출물이다.** legacy는 service와 ML이 같은 프로세스에서
import로 연결돼 있고(ADR 0001 §1.2), 프로세스를 가르는 경계 자체가 없었다.

### 1.4 상호작용 모델이 계약의 형태를 정했다

**운영자 결정 2026-08-26** (`capability-map.md` §0.7, pull 모델):

- 투찰가 산출은 **on-demand이며 배치 선산출을 하지 않는다.**
- 하류 파급이 그 절에 적혀 있다 — *"M2 계약에서 투찰가 산출이 **request/response**가 된다.
  M5 serving은 배치 선산출 부하가 사라지고 **요청 단위 지연이 설계 기준**이 된다."*

따라서 추론 경로는 **동기 요청/응답**이 자연스러운 형태다.

---

## 2. 결정

### D-1. 계약은 Protobuf 스키마로 선언하고 전송은 gRPC다

`v2-지침서.md` §3.3의 기본안을 채택한다. `.proto` 파일이 **계약의 단일 출처**이고 Kotlin과
Python 양쪽 stub은 그것에서 생성한다. 손으로 쓴 DTO를 계약으로 삼지 않는다.

`v2-지침서.md` §3.2가 ml-engine 패키지에 `contracts/`(generated 또는 Pydantic DTO,
ORM 없음)와 `serving/`(gRPC adapter)을 이미 배치했다 — 이 결정은 그 구조와 정합한다.

### D-2. 계약 어휘는 도메인 어휘도 Python 내부 어휘도 아니다

- **Kotlin은 Python 내부 task 이름·큐 이름·Celery wire format을 모른다**(§1.2).
- **ml-engine은 업무 entity를 모른다** — `v2-지침서.md` §3.2가 serving 프로세스의 금지
  목록에 업무 entity를 넣었다.
- 계약에 실리는 것은 **모델이 필요로 하는 피처 입력**과 **모델이 산출하는 것**뿐이다.

### D-3. 응답은 판정을 담지 않는다

`v2-지침서.md` §3.2: *"Python 응답은 후보·점수·불확실성·모델 근거만 반환한다. 최종
`bid`/`review`/`skip`은 Kotlin이 결정한다."*

응답에는 **어떤 artifact가 답했는지**가 함께 실린다 — `v2-지침서.md` §5가 모델 artifact에
schema version·code version·dataset id·checksum·metric을 요구하고, §9가 *"계약 version과
모델 artifact로 모든 추천을 재현 가능"*을 완료 조건으로 둔다. **추천을 재현하려면 응답이
자기를 만든 artifact를 지목해야 한다.**

### D-4. 추론과 학습은 다른 계약이다

- **추론**: 동기 요청/응답. §1.4의 pull 결정에 따라 요청 단위 지연이 설계 기준이다.
- **학습**: **job command / status 계약을 별도로 둔다**(§1.2). 장시간 training을 동기 RPC로
  붙잡지 않는다.

### D-5. job 상태는 Kotlin이 조회한다 — 역방향 호출을 만들지 않는다

학습 job의 진행 상태는 **Kotlin이 ml-engine에 물어서** 얻는다. ml-engine이 Kotlin을
호출하는 경로(콜백)를 만들지 않는다.

- 근거 ①: 역방향 호출은 ml-engine이 **업무 시스템의 주소·인증·업무 어휘**를 알아야 한다는
  뜻이고, 이는 `v2-지침서.md` §3.2의 경계(업무 entity 없음)와 D-2를 무너뜨린다.
- 근거 ②: 의존 방향이 한 방향이면 배포 순서·장애 격리·테스트 대역이 단순해진다.
  1인 운영에서 양방향 연결의 측정된 필요가 없다(§7).
- **운영자 진술과의 관계**: `OPEN-OPS-05` 절의 운영자 답 원문 취지는 *"요청에 대한 부분은
  **callback 등**으로 처리해 통보하는 형식이면 충분"*이었다. 이 ADR은 그것을
  **"동기 RPC로 붙잡지 말라"는 요구**로 읽고, 그 요구는 상태 조회로도 충족된다고 판단한다.
  **"callback"이라는 방향 지정으로 읽지 않는다** — `decisions.md`가 그 문장을 근거 원문
  취지로 기록했고 확정한 것은 브로커 없음이다. **이 독해가 틀렸다면 그것은 결정의
  변경이며 ADR로 처리한다**(D-7).

### D-6. 호환성은 계약의 성질이다

- 필드 번호를 재사용하지 않고, 제거한 필드는 예약(reserve)한다.
- **breaking change는 versioning과 consumer 증거 없이 들어갈 수 없다** — `agent-workflow.md`
  §4가 그것을 `request_changes` 사유로 열거한다.
- `milestone-2.md`의 완료 조건이 *"compatibility gate가 실제 breaking mutation을 잡는지"*를
  요구한다 — 게이트가 있다는 주장이 아니라 **게이트가 실제로 잡는다는 증거**가 기준이다.

### D-7. 이중 구현을 만들지 않는다

`v2-지침서.md` §3.3: *"성능 측정이나 운영 제약이 gRPC의 이점을 입증하지 못하더라도, 이미
채택한 계약을 몰래 HTTP로 이중 구현하지 않는다. 변경은 ADR로 한다."*
같은 규칙을 D-5의 방향 결정에도 적용한다.

### D-8. ML 장애는 조용한 폴백이 아니다

ADR 0001 D-6이 소유한다. 이 계약 축에서의 귀결만 적는다 — **호출 실패·시간 초과·미가용은
계약이 표현하는 상태**여야 하고, 호출자가 그것을 "값 없음"으로 접어 하류에 흘릴 수 없다.
`milestone-4.md`의 Codex 리뷰 항목이 *"ML 장애가 위험한 추천으로 fail-open하는지"*를
직접 본다.

### D-9. 구체 semantics는 M2 ADR이 확정한다

`v2-지침서.md` §3.3이 그렇게 규정한다 — deadline, retry, error mapping, compatibility를
포함한 ADR을 **M2에서 승인**한다. **이 ADR은 축과 금지 사항을 고정하고 값을 정하지
않는다.** 값의 근거가 아직 없다(§5 `OPEN-ADR-11`).

---

## 3. 대안

| # | 대안 | 판정 | 사유 |
| --- | --- | --- | --- |
| **A-1** | **REST/JSON over HTTP** | **불채택** | `v2-지침서.md` §3.3이 Protobuf/gRPC를 기본안으로 승인했다. 더해서 이 축의 요구는 **계약을 기계가 읽는 단일 출처로 두는 것**인데(D-1·D-6), JSON은 스키마를 별도 산출물로 관리해야 하고 필드 제거/재사용을 계약 수준에서 막지 못한다. **HTTP 자체가 나쁘다는 판정이 아니다** — 이중 구현 금지(D-7)는 이 대안을 **나중에 몰래 되살리는 것**을 막는 규칙이다 |
| **A-2** | **메시지 브로커 경유 비동기 메시징** | **불채택** | ① §1.4의 pull 결정으로 추론이 **요청 단위 응답**이 됐고 브로커는 그 형태에 맞지 않는다. ② `v2-지침서.md` §7 — 측정된 필요 없이 무거운 도구를 추가하지 않는다. ③ 운영 형태가 1인이다. **`OPEN-OPS-05`(브로커 없음)를 근거로 들지 않는다** — 그 결정은 §1.1대로 내부 workflow 큐 축이며 이 축이 아니다 |
| **A-3** | **파일/DB 경유 교환** (배치 파일, 공유 테이블) | **불채택** | ml-engine serving에 DB가 없다는 경계(`v2-지침서.md` §3.2)와 정면으로 충돌한다. 파일 교환은 계약 검증을 런타임 뒤로 미룬다 |
| **A-4** | **같은 프로세스 임베드** (계약 없음) | **불채택** | ADR 0001 A-4가 기각했다 — legacy 실측(상주 메모리 1.07GiB → 172MiB)과 serving 금지 목록의 강제 불가 |
| **A-5** | **역방향 콜백** (ml-engine → Kotlin 통보) | **불채택 (D-5)** | 경계 위반과 양방향 의존. 운영자 진술의 "callback 등"을 방향 지정으로 읽지 않는 근거는 D-5에 적었다 |
| **A-6** | **Avro / Thrift 등 다른 IDL** | **불채택** | `v2-지침서.md` §3.3의 승인된 기본안이 Protobuf다. **이번에 대안 IDL을 조사하지 않았다** — 기각 근거는 승인된 지침이지 비교 측정이 아니다 |

---

## 4. 결과

- `.proto`가 저장소 산출물이 되고, 생성 코드의 소유·빌드 배치가 정해져야 한다 —
  **ADR 0006**(Gradle 모듈)이 그 자리를 지정한다.
- ml-engine의 `serving/`이 gRPC adapter를 갖고 `contracts/`가 생성물을 담는다
  (`v2-지침서.md` §3.2).
- Kotlin 쪽 ML client는 **adapter 계층**에 있다(`v2-지침서.md` §3.1 — `adapters`가 ML
  client를 소유). 도메인은 gRPC 타입을 보지 못한다.
- 테스트는 **계약 consumer/provider test**를 가진다(`v2-지침서.md` §6 3순위). 도구와
  배치는 **ADR 0007**이 소유한다.
- `milestone-2.md`가 이 계약의 마일스톤이며 완료 조건이 compatibility gate의 실증을 요구한다.

---

## 5. 이 ADR이 등록하는 `OPEN`

### `OPEN-ADR-11` · ML 호출의 시간·실패 예산

- **결정 필요 사항**: deadline 값, 재시도 횟수·백오프, 어떤 오류를 재시도 가능으로 볼지,
  ml-engine 미가용의 하류 상태 이름.
- **왜 미결인가**: `v2-지침서.md` §3.3이 그 ADR을 **M2에서 승인**하라고 규정했고, 값을
  정할 근거(요청 단위 지연의 실측, 오류 분포)가 아직 없다. legacy에는 gRPC 경로가 없어
  이식할 실측도 없다(§1.3).
- **관련 활성 `OPEN`**: `OPEN-OPS-03`(큐 깊이 SLO — 임계 미결) · `OPEN-OPS-04`(legacy
  임계값 재유도)가 시간 예산 축을 이미 들고 있다. **이 항목이 그 둘을 대체하지 않는다** —
  저 둘은 내부 workflow 큐 축이고 이것은 ML 호출 축이다.
- **소유**: M2.

---

## 6. 확인하지 않은 것

- **gRPC의 성능 이점을 측정하지 않았다.** 채택 근거는 승인된 지침(§1.2)과 계약을 기계가
  읽는 단일 출처로 두려는 요구이지 벤치마크가 아니다. `v2-지침서.md` §3.3은 성능이
  이점을 입증하지 못해도 몰래 바꾸지 말고 **ADR로 바꾸라**고 규정한다.
- **Kotlin/Python gRPC 라이브러리의 버전 호환을 조사하지 않았다.** `OPEN-OPS-07`의 조사
  대상 9종에 gRPC 스택이 없다. M1/M2에서 실제 고정 버전으로 확인해야 한다 —
  `OPEN-ADR-01`(Boot 세대)과 같은 성격의 미확인이다.
- **인증·전송 암호화·네트워크 배치를 정하지 않았다.** 두 runtime의 배치와 컨테이너 구성은
  `milestone-6.md` 6C가 소유한다.
