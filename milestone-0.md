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
| **0A** | `docs/discovery/capability-map.md` (초판) | **`request_changes`** (effort=high, **3차 정본**). 그 뒤 라운드 4~6은 **별도 리뷰 A**가 `approve` — 아래 단서 참조 | `3dc7d263` … `6c6b3a2a` | `0a/codex-review-20260825T235415Z.json` |
| **0A2** | 같은 파일 — 운영자 결정 반영 | **`approve`** | `6af70199` … `f790e193` | `0a2/codex-review-20260827T034348Z.json` |
| **0A3** | 같은 파일 — 정정 | **`approve`** | `48151b9c` … `20f09baf` | `0a3/codex-review-20260828T062714Z.json` |
| **0B** | `docs/discovery/regression-ledger.md` | **`approve`** | `ec115a79` … `7701556c` | `0b/codex-review-20260827T121954Z.json` |
| **0C** | `docs/discovery/data-dictionary.md` | **`approve`** | `aff62abf` … `7cbdc9bb` | `0c/codex-review-20260830T124011Z.json` |
| **0D** | `docs/adr/0001`~`0009` | **`approve`** | `998dc217` … `df056259` | `0d/codex-review-20260829T222217Z.json` |
| **0E** | 개정 셋 · ADR 「상태」 줄 · `capability-map.md` §12·§14 · `fixtures/manifest.yaml` 계약·분류 | **`approve`** (리뷰 **B**. `request_changes` **14회** 뒤 15번째 라운드) | `14686dbf` … `dc5771d2` | `0e/codex-review-20260902T044741Z.json` |

**0E(이 종료 slice)의 리뷰 계약은 `reports/evidence/m0/0e/scope.md`가 갖는다.**
**그 계약은 둘로 나뉜다** — **A**(0A 미리뷰 창) · **B**(0E). 두 range와 나눈 사유·실측은
같은 파일의 「리뷰 range 를 왜 둘로 나누는가」가 **정본**이며 여기 옮겨 적지 않는다.

### 단서 — 0A의 미리뷰 창은 **별도 리뷰 A**가 덮었다

**0A의 최종 head는 0E 착수 시점까지 어느 Codex 리뷰 range에도 들지 않았다.**

- 0A의 유일한 `approve`는 head `6c6b3a2a`의 **effort=medium 부수 실행**
  (`0a/codex-review-20260825T235414Z.json`)이고, **같은 head의 effort=high 실행은
  `request_changes`**다. **0A 자신의 evidence가 high 쪽을 「3차 정본」으로 지정**한다
  (`reports/evidence/m0/0a/checklist.md:197`). `CLAUDE.md` 변경 이력의 2026-08-26 행이 같은
  사건을 *"리뷰 재현성 결여"*로 적고 그 뒤로 `model_reasoning_effort=high`를 고정했다.
- 그 `request_changes`에 대응해 **라운드 4·5·6이 돌았고** `capability-map.md`를 다시 고쳤다.
  그 라운드들의 head **`cd5a456`**(`0a/scope.md`의 선언 `head_sha`)는 **0A에 4차 리뷰가 없고**
  0A2 리뷰의 `reviewed_base`가 그 **바로 다음 커밋**(`6af7019`)이라 **어느 range에도 들지
  않았다.**

**그러므로 그 창을 0E와 묶지 않고 별도 리뷰 A로 받았다.** 계약은 위가 가리킨
`reports/evidence/m0/0e/scope.md`의 A/B 표가 **정본**이다.

**A는 `approve`다** — `reports/evidence/m0/0a/codex-review-20260830T223932Z.json`,
`reviewed_base` `6c6b3a2a` … `reviewed_head` `6af70199`, `findings` 0.
**`cd5a456`는 그 range 안에 든다**(`6c6b3a2`의 자손이자 `6af7019`의 조상). 위 미리뷰 창은
이것으로 닫혔다. **B(0E)도 `approve`로 닫혔다** — 위 표의 0E 행이 그 좌표를 싣는다.

### 승인 상태 — **둘 다 충족**(Codex `approve` · 사용자 승인 2026-09-02)

완료 조건은 **Codex `approve`와 사용자 명시 승인 둘**을 요구하고 **둘 다 갖춰졌다** —
앞은 A·B 두 range 모두 `approve`, 뒤는 아래 2026-09-02 승인이다.

*결정 축어 — 물음과 답을 그대로 옮긴다(대신 판단하지 않는다).*
물음(오케스트레이터의 0E 종결 보고, 2026-09-02): *"Codex B15 approve 확보(range
`14686db`…`dc5771d`), 잔여 제한 셋·B15 low·이월 목록을 포함한 종결 보고 후 — M0 승인 여부를
알려 달라"*
답: ***"승인"***

**앞선 두 승인과의 관계.** 2026-08-30 「M0 승인」과 2026-08-31 ADR 채택 확인이 덮는 범위는 위
「사용자 명시 승인」 절이 열거한다 — **그 둘은 0E의 `approve` 이전 상태까지**이고, **이번 승인이
그 이후 상태를 덮는다**: fixture 분류의 재판정(동결+강등)과 그 결과, 계약 술어의 동결,
`capability-map.md` §14 이월 목록의 갱신분.

**이 승인이 덮지 않는 것** — **push·merge·배포**(어떤 경우에도 별도 사용자 결정),
**case 단위 비준**(전 case의 `approved_by_user`는 여전히 `false`),
**`insufficient-evidence` case의 golden 승격**(추가 승인 또는 M1/M2 계약 설계가 선행한다).
그 셋은 아래 「알려진 제한」이 적는 그대로 남는다.

### 알려진 제한 — 고치지 않고 넘긴다

- **B15 `low`(비차단)** — `fixtures/tools/mutation_sweep_adversarial.py`의 docstring이 갈래를
  「세 갈래」로 적고 사람 판단을 `(a)`뿐이라 한다. **실제는 `(a)`~`(d)`이고 `(d)`도 사람
  판단**이다. **실행에는 영향이 없다**(스윕 결과 불변). **고치지 않는다** — `approve`된 head
  뒤에 실질 변경을 넣지 않는 것이 이 slice의 규율이고, **M1이 그 도구를 손댈 때 함께 고친다.**
- **fixture corpus의 계층** — 63 case 중 `authoritative`와 `insufficient-evidence`의 분포,
  `observed`·`legacy-behavior` 층이 **비어 있다**는 사실, 그리고 후자가 **추가 승인 또는
  M1/M2 계약 설계 전에는 M1 golden이 되지 못한다**는 것. **수와 목록은 여기 적지 않는다** —
  `fixtures/manifest.yaml`의 각 case `classification`이 정본이고 셈은
  `reports/evidence/m0/0e/fixtures-commands.md` **F-7**과 `.../commands.md` **C-15**가 낸다.
- **case 단위 비준은 하나도 없다** — 전 case의 `review.codex_verdict`·`claude_commit`이
  `pending`이고 `approved_by_user`가 `false`다. **이번 `approve`는 range의 acceptance 판정일
  뿐** case별 사용자 승인이 아니다.
- **legacy object-id 교차 확인 미실행** — clean worktree에 `bid-vector` checkout이 없어
  `fixtures/legacy-reference-index.json`의 object id를 실제 legacy git object와 재대조하지
  못했다. 저장소 안의 해시·경로 집합만 검증됐다(같은 evidence의 **F-5**가 절차를 적는다).

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

**2026-08-30 · 운영자 원문: 「M0 승인」**

아래 표의 각 행은 **둘 중 하나**이고, **첫 칸이 어느 쪽인지 밝힌다.**

- **읽기** — 「M0 승인」 다섯 글자가 무엇을 덮는가에 대한 **오케스트레이터의 해석**이다.
  **운영자의 문장이 아니다.** 틀리면 운영자가 그 행을 고친다.
- **직접 답** — 오케스트레이터가 **따로 물어 운영자에게 받은 답**이다. **물음·선택지·답을
  그대로** 싣는다. 그 답으로부터 무엇이 따라오는가는 **운영자의 발화가 아니므로 같은 칸에
  섞지 않고** 「따라오는 것」으로 갈라 적는다.

#### 승인에 드는 것

| 구분 | 대상 | 무엇을 안고 가는가 | 정본 — **셈은 여기서 나온다** |
| --- | --- | --- | --- |
| 읽기 | 활성 `OPEN`을 M1 이후로 이월 | **(A) M1 착수 차단에 남는 것이 없다** — 나머지를 (B) M1 진행 중 필요 · (C) M1 이후로 나눠 담당과 결정 주체를 붙였다 | `docs/discovery/capability-map.md` §14. **셈은 `reports/evidence/m0/0e/commands.md` C-3 이 낸다** |
| 읽기 | fixture가 덮지 못한 축 | 각 축이 **소유 `OPEN`과 함께** 등재되고, 무엇이 대신 덮이는지·무엇이 풀어야 열리는지를 함께 적는다 | `fixtures/manifest.yaml` → `uncovered_axes`. **셈은 `fixtures-commands.md` F-7 이 낸다** |
| 읽기 | `milestone-1.md:12`의 「`authoritative` case 준비」가 **부분 만족**인 채로 M1 진입 | 위 미덮개 축이 그 부분이다. 그 축은 **아래 「분류 강등」 행의 결정으로 늘었다** — 그 결정의 정본은 이 표의 그 행이지 이 칸이 아니다 | `milestone-1.md` 선행 조건 · `fixtures-scope.md` 의 만족도 절 |
| **직접 답** | **`ADR 0001`~`0009` 채택** | **2026-08-31 · 물음** *"「M0 승인」이 `ADR 0001`~`0009` 채택까지 덮는가"* · **선택지** 「덮는다 — ADR 채택 포함」 / 「덮지 않는다 — ADR은 따로」 · **답 「덮는다 — ADR 채택 포함」**.<br>**따라오는 것**(오케스트레이터의 유도, 운영자의 발화가 아니다): `milestone-1.md:11`의 「금액/rate/basis ADR 승인」(대상 `ADR 0002`)이 이것으로 충족된다 | `docs/adr/` 아홉 파일의 「상태」 줄 |
| **직접 답** | **분류 강등** — `m0-derived-rule` 로 분류된 case 를 `authoritative` 에서 내린다 | **2026-08-31 · 물음** *"`m0-derived-rule` 이 `classification: authoritative` 로 등재돼 있는데 manifest 스스로 「공식 문서도 승인된 업무 규칙도 아니다」라 적는다. `data-extract.md` §1·§6 에 걸리고, `license-011` 은 **활성** `OPEN-QUAL-11` 의 임시 기대값인데 authoritative 다"* · **선택지** 「`authoritative` 에서 제외」 / 「그 건들을 명시 승인한다」 · **답 「`authoritative` 에서 제외」**.<br>**따라오는 것**(유도): `authoritative` corpus 가 줄고 미덮개 축이 늘어 `milestone-1.md:12` 만족도가 내려간다 — **그 하락을 선택지에 적어 보인 뒤 받은 답이다** | `fixtures/manifest.yaml` → `classification_policy.insufficient_evidence` · `next_steps` 의 되돌림 경로 |

**이 표는 수를 박지 않는다.** 대신 정본과 명령을 가리킨다 —
`capability-map.md` §14.0 이 *"수를 산문에 박지 않는다. 전수와 셈은 … 명령이 낸다"*로
스스로 정한 규칙이고, 이 자리가 그것을 어기면 다음 registry 변경이 **승인 기록을 조용히
거짓으로 만든다.** 실제로 한 번 그렇게 됐다.

완료 조건은 *"`OPEN` 결정이 0개이거나 사용자가 명시적으로 다음 단계 진행을 승인했다"*이고,
**0이 될 수 없음은 위 §14가 근거와 함께 적는다.** 이 승인이 둘째 경로를 충족시킨다.

#### 승인에 들지 **않는** 것

- **fixture 접근 승인 넷** — 운영 DB read(`A-1`) · 행별 과세 판정 표본(`A-2`) ·
  legacy 순수 함수 실행(`A-3`) · 게시값 관측(`A-4`). 실제 DB·API 접근은 **별도 승인
  사항**이라 자동 포함하지 않는다. 필요해지는 시점(대체로 1B·1C)에 따로 묻는다.
- **push · merge · 배포** — 전부 로컬 커밋 상태로 둔다.
- **0E의 Codex `approve`** — `milestone-1.md:10`의 선행 조건은 *"M0 Codex `approve`**와** 사용자
  승인"*으로 **둘을 묶어** 요구한다. 이 승인은 **둘째만** 충족시킨다 — 첫째는 **B(0E) 리뷰가
  남았다.** 0A 미리뷰 창은 B의 range에 들지 않으며 **별도 리뷰 A가 `approve`로 이미 닫았다**
  (위 「단서」).

## 금지 사항

- Kotlin/Spring/Python 서비스 코드 작성
- 운영 DB query 또는 외부 API 호출(별도 사용자 승인 제외)
- 기존 golden 출력의 V2 expected 자동 복사
- “향후 결정”으로 핵심 unit/basis/writer를 숨긴 채 완료 처리
