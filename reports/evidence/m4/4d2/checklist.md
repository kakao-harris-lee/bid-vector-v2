# 4D-2 — 종결 체크리스트

## 사용자 승인 — 2026-09-11

**slice 4D-2 종결 승인.** 승인 경로는 운영자가 2026-09-11 에 지정한 체인이다 —
**verifier `ready-for-review` → 독립 코드 리뷰 `approve` → 종결 승인 → push → PR**.

산출물: `workflow/embedding/**`(port·결과 갈래·값 타입·미가용 사유 10값) ·
`adapters/ml` 임베딩 gateway 와 구조 검증층 · 4D-1 골격 제네릭화.
정본 서술은 `milestone-4.md` 4D 절의 4D-2 문단.

## 검증 상태

| 패스 | 결과 |
| --- | --- |
| verifier r1 | `not-ready` — F-1(high, 비유한 float 예외 누출) |
| verifier r2 | `ready-for-review` |
| **독립 코드 리뷰 r1** | **`request_changes`** — F-A(high, 차가운 JVM 에서 결정적 실패) · medium 4 |
| verifier r3 | `ready-for-review` |
| **독립 코드 리뷰 r2** | **`approve`** |

**재작업 카운터 2/5** — verifier `not-ready` 1 + 코드 리뷰 `request_changes` 1.
acceptance S-0~S-7 전건 exit 0(기준 head 는 `commands.md` 가 명시한다). verifier r3 와 코드 리뷰 r2 가
각각 `0a32273` 에서 독립 재실행했다.

## 이 slice 가 남긴 방법론

- **레인 셋이 값을 했다.** 구현·검증 두 레인이 초록으로 본 test 를 **셋째 레인이 빨갛게** 만들었다.
  차이는 gradle 데몬 온도였고 **CI 러너는 항상 차갑다** — PR 을 먼저 열었다면 거기서 났을 결함이다.
- **「test 무편집」은 동작 불변 증거의 절반이다.** 정적 본문 비교 + **differential 실행 대조**를
  병행해야 기존 test 의 사각(여기서는 metadata RPC 실패 경로 셋)이 드러난다.
- **게이트는 변이로 확인한다.** 이 slice 에서 값 고정·대소·사유 매핑 test 전부 변이를 주입해
  「깨뜨리면 잡는다」를 실측했다 — test 수 증가는 근거가 아니다.

## 인계

| 항목 | 받는 곳 |
| --- | --- |
| `OPEN-4D2-VECTOR-FORGERY-AT-WIRING` | **4B-6**(소비자 배선 시점에 닫는다) |
| `OPEN-2E-TEXT-SYNTHESIS`(`featureSchemaVersion` 값) | **4B-6** — 규약을 옮기면 `ml-engine` fake servicer 까지 세 자리가 함께 움직여야 한다 |
| `OPEN-4D2-POLICY-VALUES` · `OPEN-M2-DEADLINE-VALUES` | **M5 5E** 실측 |
| `OPEN-4D2-VECTOR-PERSISTENCE`(벡터 저장·kNN) | persistence 후속 |
| 실 servicer | **M5 provider slice** |
| `ManagedChannel`·TLS·인증 | **M6** |

## PR #5 게이트 시정 — 2026-09-11 (종결 뒤 추가)

이 slice 는 2026-09-11 에 종결됐으나, 같은 날 PR #5 의 게이트 셋이 세 건을 냈고 **같은
브랜치에서 시정**했다(PR 이 아직 안 닫혀 병합 전이다).

| 지적 | 낸 곳 | 처리 |
| --- | --- | --- |
| **차단** — 2E 승인 문면 **D-2E ②**(응답 `dimension` 을 `GetEmbeddingMetadata` 와 대조)가 production 에 미배선. 규칙은 test 안 순수 함수로만 있고 호출부 0 | `contract-keeper` | `latest_promoted` 경로에서 **실제로 대조**하게 배선(추가 RPC 없이 기존 metadata 호출 하나로 release·dimension 을 함께 받는다). 순수 함수를 test → main 승격. 불일치는 `Unavailable(ReleaseMismatch)`. 게이트가 변이로 재확인 → **통과** |
| RouteKey 검증 실패 예외가 **거부된 원문을 메시지에 실음** | `privacy-gate` | `length=` 만 남긴다. 회귀 test 셋(채팅id·봇비밀값·메일주소 모양) |
| `leak-patterns.txt` 가 **gradle 에 배선돼 있지 않음**(아무도 안 돌리는 게이트) | `privacy-gate` | `LeakPatternGateTask` 신설, 루트 `check` 배선. 스캔 범위는 `reports/evidence/` |

**`exact_release` 경로의 잔여** — `latest_promoted` 가 아닌 선택자에서는 metadata 를 조회하지
않으므로 dimension 대조가 **적용되지 않고, 그것을 메우는 다른 검사도 없다**. **정정
(Codex 2차 medium, 2026-09-11)**: 이 문단의 앞선 판은 근거를 「`release_id`+`checksum` 이
아티팩트를 못 박아 같은 checksum 이 다른 `dimension` 을 낼 수 없다」로 적었으나 **구현이
집행하지 않는 보장이었다** — `exact_release` 분기는 문자열만 비교하고, Codex 가 반례를 만들었다
(release·schema 를 유지한 채 `dimension` 4 → 1 로 바꾼 응답이 현재 release·shape·schema 검증을
전부 통과한다). 남는 방어는 `values.size == dimension`(자기정합성)뿐이다. 4D-1 이 같은
관례를 쓴다 — **관례를 이어받은 알려진 제한**이지 보장이 아니다. 지금까지 이 판단이 **KDoc 에만** 있고 evidence 에 없었다(`contract-keeper` 권고) —
여기에 등재한다. **새로 생긴 표면이 아니라 4D-1 부터 있던 설계를 이어받은 것**이다.

**알려진 제한 추가**: leak-pattern 게이트는 **`reports/evidence/` 만** 스캔한다 — 저장소 전체
소스로 넓히면 정당한 `Bearer` 헤더 생성·`KtTokens.PRIVATE_KEYWORD` 같은 자기매치와 광범위하게
겹쳐 게이트가 무의미해진다는 실측 때문이다. **그 귀결로 소스 코드의 유출은 이 게이트가 보지
않는다.** 그리고 배선 시점의 기존 매치 286건을 baseline 으로 grandfather 했다 — 그 목록의
실유출 여부는 `privacy-gate` 의 독립 판정에 맡겼다.
