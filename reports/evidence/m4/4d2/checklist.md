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
