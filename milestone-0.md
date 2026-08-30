# 마일스톤 0 — 요구사항 발굴과 아키텍처 동결

## 목표

V2 범위를 선별한다. service 레이어는 Kotlin에서 다시 구현할 기능·도메인 규칙·데이터
계약·실패 사례만 고르고, ML 레이어는 재활용 가능한 코드 범위와 잘라낼 결합을 식별한다.
이 단계는 문서와 검증 fixture 설계 단계이며 애플리케이션 코드를 만들지 않는다.

## 선행 조건

- [`v2-지침서.md`](./v2-지침서.md)와 [`agent-workflow.md`](./agent-workflow.md) 승인
- 기존 [`bid-vector`](./bid-vector) 접근 가능
- 조사 기준 SHA 고정
- 독립 Codex diff review를 위해 이 디렉터리를 별도 Git 저장소로 초기화하고 문서 baseline
  커밋 고정(사용자 승인 후)

## Claude 작업

### Slice 0A — capability map

기존 UI/API/서비스/작업을 파일 목록이 아니라 사용자 capability 기준으로 분류한다.

- 공고 수집·정규화
- 회사/운영자 전략
- 면허·지역·실적 자격
- 추천 입력·ML 추론
- 투찰 판단과 근거
- 알림/보고서
- 개찰 대사·정산
- 운영·증적

각 capability를 `V2 필수`, `후속`, `폐기`, `근거 부족` 중 하나로 표시한다. 기존 endpoint
수나 함수 수를 V2 요구사항 수로 간주하지 않는다.

### Slice 0B — regression ledger

최소 다음 계열을 조사한다.

- 금액 basis: 기초금액/예정가/추정가격/낙찰가
- rate scale: fraction/percent
- VAT/provenance와 파생값 오염
- 법정 하한과 `Unmeasurable`
- 면허 group AND/OR, 별칭, 불확실 판정
- KONEPS 필드 누락·fallback·rate limit
- 비동기 중복·재시도·queue 폭주
- 모델 label/feature의 training-serving skew와 시간 누수

각 항목에는 `관찰`, `사용자 영향`, `V2 예방 제약`, `검증 방법`, `근거 파일/commit`을 쓴다.

### Slice 0C — 도메인 명세와 데이터 사전

- 용어, 단위, basis, nullable 의미
- aggregate와 상태 전이
- rule의 입력/출력/reason code
- 정책 version과 effective date
- canonical KONEPS fact와 derived fact 구분
- ML feature와 업무 판단의 경계

모호한 항목은 기존 Python 구현을 정답으로 채우지 말고 `OPEN`으로 남겨 사용자 결정을
요청한다.

### Slice 0D — ADR

최소 다음 결정을 기록한다.

1. Kotlin modular application + Python ML engine, 그리고 service 재작성 / ML 재활용 경계
2. Gradle module과 의존 방향
3. 금액/rate/basis 표현
4. gRPC/Protobuf 내부 계약
5. DB 및 migration 도구
6. domain event/outbox/notification 방식
7. 테스트 pyramid와 mutation 대상
8. React UI 재사용/재작성/후속 여부
9. ML 재활용 출처 기록 위치 — `docs/discovery/legacy-reference-map.md` 통합 vs slice별
   `reports/evidence/` 기록. 사용자 확인을 받아 결정한다(2026-08-22 지시).

### 추가 조사 항목 — ML 재활용 대상 래칫 사전 조사

legacy-scout 조사 범위에 다음을 포함한다: `app/ai/predictors/`,
`app/services/ml_training/`, `app/services/ml_release/`의 파일·함수 크기와 결합 지점.
기존 ML 코드가 V2 래칫(함수 50줄, 파일 500줄)을 만족하는지 확인하고, 미달 모듈은
M5에서 "이식 시 분해"할지 allowlist 사유를 쓸지 판단할 근거를 남긴다.

## 산출물

```text
docs/discovery/capability-map.md
docs/discovery/regression-ledger.md
docs/discovery/data-dictionary.md
docs/adr/0001-target-architecture.md
docs/adr/0002-money-rate-basis.md
docs/adr/0003-contract-transport.md
docs/adr/0004-persistence-and-events.md
fixtures/manifest.yaml              # schema와 후보 목록만
```

**`docs/discovery/legacy-reference-map.md`를 이 목록에서 뺐다** (**운영자 결정 2026-08-29 ·
Q3**, 집행 2026-08-30 slice 0E). 대체된 이유는 `ADR 0009`의 **(d) 결정**이다 — ML 재활용
출처의 기록을 **통합 단일 문서 하나가 아니라 두 자리로 나눠 갖도록** 확정했다
(`docs/adr/0009-ml-reuse-provenance.md` §2 D-5 · §5).

| 자리 | 담는 것 |
| --- | --- |
| **ml-engine 모듈 안** | 모듈 식별자 · **원본 파일 경로 · 기준 commit** — 현재 상태의 선언 |
| **slice별 `reports/evidence/<milestone>/<slice>/`** | 수행한 튜닝·수정 **내역** + 그 이식이 딛은 모듈 식별자 · 원본 파일 경로 · 기준 commit |

통합 문서는 이 분할이 대체한 대상이며, `ADR 0009` §5가 스스로
*"`milestone-0.md` 「산출물」의 `legacy-reference-map.md` 항목이 이 결정과 어긋난다 …
개정은 운영자 결정이다"*로 개정 필요를 사실로 남겼다. 두 자리가 어긋나는 것을 잡는 검사는
같은 ADR의 D-6·D-6.1이 계약을 적고 **M5가 구현**한다.

## 완료 조건

- V2 필수 capability마다 사용자 가치와 acceptance scenario가 있다.
- Kotlin service 범위에 Python 파일/endpoint를 그대로 옮기는 작업 항목이 없다.
- ML 재활용 대상은 모듈 단위로 식별되고, 잘라낼 결합(ORM, DB, 설정, 업무 판정)이 명시돼
  있다. 재활용은 기대값 판정 근거가 아니라 구현 전략으로만 기록한다.
- 모든 도메인 숫자의 unit/basis/provenance가 정의되거나 `OPEN`이다.
- 기존 회귀마다 V2의 타입·계약·테스트 중 최소 하나의 예방책이 있다.
- Python 결과가 정답이 아니라 참고임을 모든 관련 문서가 일관되게 명시한다.
- `OPEN` 결정이 0개이거나 사용자가 명시적으로 다음 단계 진행을 승인했다.

## Codex 독립 리뷰

Codex는 구현을 제안하는 대신 다음만 판정한다.

- capability가 기존 파일 구조를 요구사항으로 오인하지 않았는가
- 회귀 ledger의 근거가 실제 파일/commit과 맞는가
- 명세가 Python의 버그/fallback을 무비판적으로 채택하지 않았는가
- 아키텍처 경계가 모호하거나 실행 불가능한 부분이 없는가
- acceptance scenario가 관찰 가능한가

Codex `approve`와 사용자 승인이 있어야 M1로 진행한다.

## M0 완료 기록

- **기록 시점**: 2026-08-30 · M0 종료 slice **0E**
- **verdict 원본**: `reports/evidence/m0/<slice>/codex-review-*.json` (append-only)

### 여섯 slice와 각각의 마지막 Codex verdict

| slice | 산출물 | 마지막 verdict | `reviewed_base` … `reviewed_head` | verdict 파일 |
| --- | --- | --- | --- | --- |
| **0A** | `docs/discovery/capability-map.md` (초판) | **`request_changes`** (effort=high, **3차 정본**) — 아래 단서 참조 | `3dc7d263` … `6c6b3a2a` | `0a/codex-review-20260825T235415Z.json` |
| **0A2** | 같은 파일 — 운영자 결정 반영 | **`approve`** | `6af70199` … `f790e193` | `0a2/codex-review-20260827T034348Z.json` |
| **0A3** | 같은 파일 — 정정 | **`approve`** | `48151b9c` … `20f09baf` | `0a3/codex-review-20260828T062714Z.json` |
| **0B** | `docs/discovery/regression-ledger.md` | **`approve`** | `ec115a79` … `7701556c` | `0b/codex-review-20260827T121954Z.json` |
| **0C** | `docs/discovery/data-dictionary.md` | **`approve`** | `aff62abf` … `7cbdc9bb` | `0c/codex-review-20260830T124011Z.json` |
| **0D** | `docs/adr/0001`~`0009` | **`approve`** | `998dc217` … `df056259` | `0d/codex-review-20260829T222217Z.json` |

**0E(이 종료 slice)의 리뷰는 별도이며 그 계약은 `reports/evidence/m0/0e/scope.md`가 갖는다.**

### 단서 — 0A는 이 slice의 리뷰에 포함된다

**0A의 최종 head는 어느 Codex 리뷰 range에도 들지 않았다.**

- 0A의 유일한 `approve`는 head `6c6b3a2a`의 **effort=medium 부수 실행**
  (`0a/codex-review-20260825T235414Z.json`)이고, **같은 head의 effort=high 실행은
  `request_changes`**다. **0A 자신의 evidence가 high 쪽을 「3차 정본」으로 지정**한다
  (`reports/evidence/m0/0a/checklist.md:197`). `CLAUDE.md` 변경 이력의 2026-08-26 행이 같은
  사건을 *"리뷰 재현성 결여"*로 적고 그 뒤로 `model_reasoning_effort=high`를 고정했다.
- 그 `request_changes`에 대응해 **라운드 4·5·6이 돌았고** `capability-map.md`를 다시 고쳤다.
  그 라운드들의 head **`cd5a456`**(`0a/scope.md`의 선언 `head_sha`)는 **0A에 4차 리뷰가 없고**
  0A2 리뷰의 `reviewed_base`가 그 **바로 다음 커밋**(`6af7019`)이라 **어느 range에도 들지
  않는다.**

**그러므로 0E의 Codex 리뷰 range를 `cd5a456` 이전부터 잡아 그 구간을 함께 덮는다.**
계약과 실제 range는 `reports/evidence/m0/0e/scope.md`에 있다.

### 이월 목록 — 「사용자 명시 승인」의 대상

완료 조건은 *"`OPEN` 결정이 0개이거나 사용자가 명시적으로 다음 단계 진행을 승인했다"*이고,
**활성 `OPEN`이 0이 될 수 없다**(다수가 V2 코퍼스나 운영 관측을 선행 조건으로 가져 사용자가
지금 답해도 닫히지 않는다). **그러므로 명시 승인만이 유일한 경로이며, 무엇을 승인하는지는
아래 목록으로 읽는다.**

**→ `docs/discovery/capability-map.md` §14 「M0 이월 목록 — 활성 `OPEN`의 담당」**

그 절이 활성 `OPEN` 전부를 **(A) M1 착수 차단 / (B) M1 진행 중 필요 / (C) M1 이후**로 나누고
각각의 **담당 slice**와 **결정 주체**를 적으며, 지목이 **정본**인지 이 절의 **유도**인지,
근거 둘이 **갈리는지**를 표시한다. **(A)에 남는 활성 `OPEN`은 없다** — 0E가 닫았다.
비-`OPEN` 이월(`fixtures/manifest.yaml` 부재 등)은 같은 절 §14.4가 갖는다.

### 사용자 명시 승인

> **(비어 있음)** — 이 자리는 **사용자가 채운다.** 0E는 승인의 **대상**(§14 이월 목록)과
> **경로**(위 완료 조건)를 갖추기만 했고, 승인 자체를 대신 적지 않는다.

## 금지 사항

- Kotlin/Spring/Python 서비스 코드 작성
- 운영 DB query 또는 외부 API 호출(별도 사용자 승인 제외)
- 기존 golden 출력의 V2 expected 자동 복사
- “향후 결정”으로 핵심 unit/basis/writer를 숨긴 채 완료 처리
