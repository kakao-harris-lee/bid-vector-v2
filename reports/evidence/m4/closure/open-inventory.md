# M4 OPEN 항목 재고 (2026-09-18)

> **지위**: M4 종결 판정용 읽기 전용 조사. 코드·문서를 고치지 않았다. 판정을 내리지 않고
> **문면만** 옮긴다 — 어느 문서가 무엇을 적는지와 그 문서의 어느 절이 근거인지만 적는다.
>
> **작업 트리**: `bid-vector-v2-m4cl` (브랜치 `m4-closure/2026-09-18`, HEAD = `8652893`).
> 다른 worktree 는 읽지 않았다.
>
> **스냅숏 시점 주의(팀장 추가)**: 이 스윕은 **종결 slice 착수 직후**에 돌았다. 그래서 §5.1 의
> 첫 항목(「`closure/` 는 빈 디렉터리다」)은 **스윕 시점의 사실이고 지금은 아니다** — 이 slice 가
> 그 디렉터리에 `scope.md`·`checklist.md` 와 이 파일을 넣었다. 그 항목은 기록으로 남기고 고치지
> 않는다(스윕이 무엇을 보았는지가 그 문장의 값이다).

## 0. 조사 범위와 방법

`*.md` 전수에서 `OPEN-` 식별자를 뽑고(심볼릭 링크 `bid-vector/` 제외 — 옛 Python 저장소),
그 가운데 다음 셋을 재고 대상으로 삼았다.

1. **M4 가 신설한 것** — `OPEN-4*` 전수 + M4 하네스 레인(`leak-baseline-coord`)의 `OPEN-LEAK-*`.
2. **지시가 지목한 두 계열 전수** — `OPEN-DIC-*` · `OPEN-STR-*`.
3. **M4 가 물려받은 것** — `capability-map.md` §12·§14 의 「담당」 칸이 M4(또는 4A~4E)를
   지목하거나, `milestone-4.md`·`reports/evidence/m4/**` 가 인용하는 그 밖의 `OPEN`.

### 0.1 활성 registry(정본)는 계열이 정한다

`capability-map.md` §14.0 이 정한 규칙을 그대로 따랐다 — 어느 `OPEN` 이 활성인지는 계열의
정본이 정하고 §14 는 담당만 적는다.

| 계열 | 활성 registry(정본) |
| --- | --- |
| `OPEN-COL/STR/QUAL/ML/DEC/NOTI/SET/OPS/NUM` | `capability-map.md` §12 G1~G6 표 |
| `OPEN-DIC` | `data-dictionary.md` §9 표 |
| `OPEN-ADR` | `docs/adr/*.md` 각 §5 의 취소선 없는 `###` 제목 |
| `OPEN-REG` | `regression-ledger.md` §9 표 |
| `OPEN-4*` | 등재된 것은 `capability-map.md` §14.3, 미등재는 그 slice 의 `scope.md`/`checklist.md` |

### 0.2 갈래 판정 규칙 (이 문서가 쓴 잣대)

- **종결** — 어느 문서가 명시적으로 「종결/닫힘」이라 적는다.
- **이월** — 받는 쪽(마일스톤·slice·역할)이 문면에 적혀 있다.
- **미결** — 열려 있고 닫음 문면도 받는 쪽 문면도 없다.
- **상충** — 두 문서가 **종결 여부**를 다르게 적거나, **받는 쪽을 서로 배타적으로** 적는다.
  양쪽을 병기했다.

**이월이 안전을 뜻하지 않는다.** 받는 쪽으로 적힌 slice·축이 이미 종결했는데 그 항목의
처분 문면이 없는 경우가 여럿이라, §5 에 별도 목록으로 모았다. 그 목록은 갈래가 아니라
**주의 표시**이며 위 넷과 이중 계산하지 않는다.

### 0.3 식별자가 아닌 문자열 (오탐으로 제외)

- `OPEN-4` — `capability-map.md` §12 G1·G3·G4 의 「근거」 칸에 나오는 **축별 scout 문서의
  OPEN 번호**(`qualification OPEN-4`·`collection OPEN-4`·`strategy OPEN-4`·`settlement OPEN-4`)다.
  `OPEN-4` 로 끝나는 독립 식별자는 저장소에 없다.
- `OPEN-4A`·`OPEN-4B2`·`OPEN-4B1-OFF-LADDER`·`OPEN-4B7-CATEGORY`·`OPEN-4B8-CATEGORY`·
  `OPEN-4C1-TX-CONTRACT` — 전부 **줄바꿈으로 잘린 조각**이다(경계 검사에서 뒤에 항상 `-` 가
  붙는다). 예: `milestone-4.md` 4C-2 단락이 `OPEN-4C1-TX-CONTRACT` 로 줄을 끊고 다음 줄에서
  `-UNVERIFIED` 로 이어진다.
- `OPEN-DIC`·`OPEN-STR`·`OPEN-ADR`·`OPEN-NOTI` — 계열 접두사 표기.
- `OPEN-DIC-NN`·`OPEN-ADR-NN`·`OPEN-REG-NN` — 「번호 미정」 자리표.

---

## 1. M4 가 신설한 `OPEN-4*`

| 식별자 | 어디서 생겼나 | 문면상 현재 상태 | 처분 근거 위치 |
| --- | --- | --- | --- |
| `OPEN-4A-WRITE-PATH-GATE` | 4A `scope.md` 「신설 후보」 표 — 「모든 편집 경로가 use case 를 지난다」를 게이트로 표현할 수 있는가 | **종결** — *"port 우회 경로(직접 호출 + 반환값 재사용)는 커널·통로 필드 `internal` 화로 닫혔고, port 자체를 거치지 않는 자체 persistence 는 명시적으로 경계 밖(3D/4C)"* | 4A `checklist.md` 해당 전용 절과 「알려진 제한」 절 · `milestone-4.md` 4A 종결 단락. **`capability-map.md` 에 등재된 적 없다**(신설 후보 상태로 종결) |
| `OPEN-4B1-02` | 4B-1 착수 조사 | **미결** — *"`LicenseVerdict.Ineligible`과 `Verdict`의 관계(참조 vs 복제)는 미정 — 4B-1은 그 축을 건드리지 않는다(out_of_scope)"*. 받는 쪽 문면 없음 | 4B-1 `checklist.md` 「알려진 제한」 · `milestone-4.md` 4B-1 단락. capability-map 미등재 |
| `OPEN-4B1-03` | 4B-1 착수 조사 | **종결** — 4B-2 가 답했다: *"`AnalysisBudgetExhausted`·`SimilarityProjectionNotReady`가 `Verdict` 안인지 밖인지 미정의 답도 함께 나왔다 — 밖이다"* | `milestone-4.md` 4B-2 종결 단락 · 4B-2 `commands.md`. capability-map 미등재 |
| `OPEN-4B1-05` | 4B-1 착수 조사 §10 | **미결** — 용량이 네 번 세는 것의 합쳐진 효과를 *"이 slice 가 재측정하지 않았다 — 재측정이 필요하면 별도 slice"*. 특정 slice 지목 없음 | 4B-1 `checklist.md` 「알려진 제한」. capability-map 미등재 |
| `OPEN-4B1-06` | 4B-1 착수 조사 §10 | **미결** — 하한 override 가 판정을 얼마나 바꾸는가. 위와 같은 문장이 둘을 함께 든다 | 같은 자리 |
| `OPEN-4B1-07` | 4B-1 착수 조사 §7.3 | **미결** — *"`review_required`(ML regime 신호)를 V2 어느 층이 만드는가는 미정"* | 4B-1 `checklist.md` 「알려진 제한」. capability-map 미등재 |
| `OPEN-4B1-LADDER-THRESHOLDS` | 4B-1 `scope.md` 「신설 후보」 표 | **미결** — 운영자 승인 대기. 4B-2 가 *"여전히 운영자 승인 대기다(4B-1에서 이미 열린 것을 그대로 잇는다 — 이 slice가 새로 여는 OPEN이 아니다)"*, 4B-4 는 「경계 밖」. **M4 마지막 slice 까지 승인 문면이 없다** | 4B-1 `checklist.md` · 4B-2 `checklist.md` 「알려진 제한」 4 · 4B-4 `scope.md` OPEN 표. capability-map 미등재 |
| `OPEN-4B1-OFF-LADDER-DROPS` | 4B-1 이 `OPEN-DIC-03` 을 닫으며 신설(사다리 **밖** 드롭 열셋) | **종결** — *"종결(M4/4B-2)"*: 기존 축이 소유한 것은 그대로 싣고 어느 축도 소유하지 않은 넷만 최소 신설 | `capability-map.md` §14.3 `OPEN-DIC` 표의 취소선 행 · `milestone-4.md` 4B-2 종결 단락 |
| `OPEN-4B2-1` | 4B-2 착수 조사 §2.3 | **미결** — 「자격」이 게이트와 점수 축 둘로 들어가는데 어느 쪽이 정본인지 legacy 가 선언하지 않았다. 담당 칸은 slice 가 아니라 **「도메인 명세 판단」**, 지목 성격 「정본 필요」 | `capability-map.md` §14.3 (취소선 없음) · 4B-2 `checklist.md` |
| `OPEN-4B2-2` | 4B-2 착수 조사 §6.5 | **이월 — 받는 쪽 `4C-2`/`3D`** (「실 저장 필요」). 4C-2 `scope.md` 가 이 식별자를 인용하나 닫음 문면은 없다 | `capability-map.md` §14.3 · 4B-2 `checklist.md` · 4C-2 `scope.md`. **§5 주의 목록** |
| `OPEN-4B2-3` | 4B-2 착수 조사 | **종결** — *"설계로 닫은 것(등재하지 않음)"*: `CapacityPort.snapshot()` 이 run 당 한 번만 불려 모든 후보가 같은 스냅샷을 공유하므로 legacy 형태 자체가 없다 | 4B-2 `checklist.md` 해당 절 · `milestone-4.md` 4B-2 단락(신설 여섯 중 다섯만 등재한 사유) |
| `OPEN-4B2-4` | 4B-2 착수 조사 §8.3 | **이월 — 받는 쪽 `4C-2`/`3D`**(실 계수 쿼리 설계가 4C-2 소관) | `capability-map.md` §14.3. **§5 주의 목록** |
| `OPEN-4B2-5` | 4B-2 착수 조사 §5 C-8 | **이월 — 받는 쪽 「운영」(4E/운영 설정)**. 배달 outbox drain 이 실 운영에서 켜져 있는가 | `capability-map.md` §14.3 |
| `OPEN-4B2-6` | 4B-2 착수 조사 §5 C-3 | **이월 — 받는 쪽 `4C-2`/`3D`**(run 상태 영속이 있어야 관측 가능) | `capability-map.md` §14.3. **§5 주의 목록** |
| `OPEN-4B4-CORPUS` | 4B-4 `scope.md` OPEN 표 신설 | **이월 — 받는 쪽 「병합 뒤 curator」**. *"활성 유지"* 명시 | 4B-4 `scope.md` OPEN 표 · `checklist.md` 「알려진 제한」. capability-map 미등재 |
| `OPEN-4B4-POLICY-VALUES` | 4B-4 `scope.md` OPEN 표 신설 (D-4B4-4) | **종결** — 사용자 승인 2026-09-10. 재정규화 가중치 다섯·penalty·offset·`normEpsilon` 확정, 값은 착수 시점에서 무변경 | 정본 4B-4 `policy-values.md` 「사용자 승인」·`change_history` 절 · `scope.md` OPEN 표 취소선 · `milestone-4.md` 4B-4 종결 단락 |
| `OPEN-4B5-COMPETITIVENESS` | 4B-5 가 D-4B5-4 「없으면 멈추고 보고」로 멈춰 신설 | **이월 — 받는 쪽 둘**: *"시장 평균 fact 의 정의·수집은 **M3 후속**(개찰 결과 집계 축) · shared-kernel `Basis` 확장 결정은 **별도 slice**"*. 4B-6b 는 「수령 유지」(`competitivenessNotCollected()`) | `capability-map.md` §14.3 (취소선 없음) · 4B-5 `scope.md` 「계약 갱신 — 2026-09-10」 절 · 4B-6b `scope.md` 수령 표 |
| `OPEN-4B5-CORPUS` | 4B-5 `scope.md` OPEN 표 신설 | **이월 — 받는 쪽 「병합 뒤 curator」** | 4B-5 `scope.md` OPEN 표 · `checklist.md` 「알려진 제한」. capability-map 미등재 |
| `OPEN-4B5-POLICY-VALUES` | 4B-5 `scope.md` OPEN 표 신설 | **종결** — 사용자 승인 2026-09-10. 밴드 넷·가중치 둘·상수 다섯·`budgetCaptureRounding` + 「의도된 갈림」 셋 전부 확정 | 정본 4B-5 `policy-values.md` 「사용자 승인」·`change_history` · `scope.md` OPEN 표 취소선 |
| `OPEN-4B6-PROFILE-SOURCE` | 4B-6a `scope.md` OPEN 표 신설 | **이월 — 받는 쪽 `M6`**(`ProfileFacts` 의 저장·편집·조회). 4B-6b 는 *"수령 유지(M6 — port 소비만)"* | 4B-6a `scope.md` OPEN 표·`checklist.md` · 4B-6b `scope.md` 수령 표. capability-map 미등재 |
| `OPEN-4B6A-POLICY-VALUES` | 4B-6a `scope.md` OPEN 표 신설 | **종결** — 사용자 승인 2026-09-11(합성 규약 version `v1` · 키워드 14 · 상한 4000). 값은 legacy-behavior 와 2E 기존 승인 값 그대로 | 정본 4B-6a `policy-values.md` 「사용자 승인」 절 · `milestone-4.md` 4B-6a 종결 단락 |
| `OPEN-4B6B-POLICY-VALUES` | 4B-6b `scope.md` OPEN 표 신설 (D-4B6B-5) | **상충** — 같은 문서가 둘을 적는다. ① 제목과 「사용자 승인」 절 머리: *"사용자 승인 2026-09-12 — 종결"* ② 같은 문서 「해소 조건」 절: *"위 넷(예산 둘·selector/objective·categoryOffset)의 값 자체가 실측으로 갱신될 때 닫힌다"*, 그리고 「사용자 승인」 절 끝: *"예산 둘은 초기 추정치로 4D-1·4D-2 운영 실측 뒤 갱신 대상(**활성 유지**)"* | 4B-6b `policy-values.md` 제목·「해소 조건」·「사용자 승인」 세 자리 · `checklist.md` 「사용자 승인」 절. capability-map 미등재 |
| `OPEN-4B6B-PREDICTED-RATE` | 4B-6b `scope.md` 갱신 이력 (D-4B6B-6) | **이월 — 받는 쪽 「전략이 시나리오를 고르는 slice 또는 M2 additive 확장」**. alignment 성분이 상수 1 로 고정된다 | 4B-6b `scope.md` D-4B6B-6·OPEN 표 · `checklist.md` 「인계 OPEN」 · `milestone-4.md` 4B-6b 종결 단락. capability-map 미등재 |
| `OPEN-4B6B-BASE-AMOUNT-PROVENANCE` | 4B-6b 구현 뒤 계약 갱신에서 신설 | **이월 — 받는 쪽 「procurement 가 provenance 를 fact 로 나르는 slice」** (`Notice` 가 `BaseAmountProvenance` 축을 나르지 않아 요청 라벨이 `Unknown` 고정) | 4B-6b `scope.md` 갱신 이력 · `checklist.md` 「인계 OPEN」. capability-map 미등재 |
| `OPEN-4B7-QUERY-INDEX` | 4B-7 `scope.md` D-4B7-6 신설 | **이월 — 받는 쪽 `M6`**(운영 규모 산정 뒤 Flyway 인덱스 마이그레이션, migration-reviewer + Codex 범위 승인). 4B-7 은 *"정확성만 보장하고 마이그레이션을 열지 않았다"* | `capability-map.md` §14.3 (취소선 없음) · 4B-7 `scope.md`·`checklist.md` · `milestone-3.md`·3H `scope.md` 도 인용 |
| `OPEN-4B7-POLICY-VALUES` | 4B-7 `scope.md` 신설 | **이월 — 받는 쪽 「M5 5C/5E 실측 뒤 정책 version 갱신」** · provenance 정본 자리는 *"4B 후속이 `decision` 에 둘지 결정"*. 착수 값(창 365일·상한 500건)은 placeholder | `capability-map.md` §14.3 · 정본 4B-7 `policy-values.md` · 4B-8 `policy-values.md`·`scope.md` 도 인용 |
| `OPEN-4B7-TARGET-LABEL` | 4B-7 `scope.md` out_of_scope 신설 | **종결** — *"닫힘 M4/4B-8 — 같은 분류기, 개찰 입력 `null`"* | `capability-map.md` §14.3 취소선 행 · `milestone-4.md` 4B-8 종결 단락 · 4B-8 `scope.md` |
| `OPEN-4B7-CATEGORY-NORMALIZATION` | 4B-7 `scope.md` 갱신 이력 신설 | **종결** — *"닫힘 M4/4B-8 — `CategoryCode.of` 단일 정규화, data-dictionary §6.3.1"* | `capability-map.md` §14.3 취소선 행 · `milestone-4.md` 4B-8 종결 단락 |
| `OPEN-4B8-CATEGORY-BACKFILL` | 4B-8 `scope.md` D-4B8-4 신설 | **이월 — 받는 쪽 `M6`**(배치 백필 또는 재수집, 마이그레이션이면 migration-reviewer). *"현재 운영 데이터 0"* | `capability-map.md` §14.3 (취소선 없음) · 4B-8 `scope.md`·`checklist.md` · `data-dictionary.md` 도 인용 · 3H `scope.md` 가 `OPEN-3H-AGENCY-BACKFILL` 을 *"같은 성격"* 으로 짝지음 |
| `OPEN-4B8-DEAD-DERIVED-RULES` | 4B-8 `scope.md` 신설 | **종결** — *"닫힘 2026-09-16, 문서 판정"*. 결정 27(2026-09-06, M1/1D)의 귀결로 닫혔고 정본은 `data-dictionary.md` 결정 27 귀결 문단 | `capability-map.md` §14.3 취소선 행 · `data-dictionary.md` 결정 27 귀결 문단 · `milestone-4.md` 4B-8 종결 단락 |
| `OPEN-4C1-TX-CONTRACT-UNVERIFIED` | 4C-1 이 「등록이 도메인 write 와 같은 트랜잭션」을 문면으로만 선언하고 신설 | **상충** — ① 종결: *"4C-2 종결 2026-09-10(사용자 승인) — `OPEN-4C1-TX-CONTRACT-UNVERIFIED` 종결"*, `capability-map.md` §14.3 `OPEN-OPS-10` 행의 잔여 ② 도 *"`TransactionBoundary` + 실 DB 실패 주입·crash-after-commit 실측으로 **닫혔다**"* ② 활성: 같은 4C-2 종결 단락의 「알려진 제한(종결 시점)」이 *"`data-dictionary.md` §2.2.5가 아직 `OPEN-4C1-TX-CONTRACT-UNVERIFIED`를 활성으로 말한다(in_scope 밖, 후속 인계)"* — 그 문장은 **오늘도 참이다**(사전 §2.2.5 문면 미갱신) | `milestone-4.md` 4C-2 종결 단락(양쪽을 한 단락에서 적는다) · `capability-map.md` §14.3 `OPEN-OPS-10` 행 · `data-dictionary.md` §2.2.5 · 4C-1/4C-2 `scope.md` |
| `OPEN-4C2-MARK-UNEXERCISED` | 4C-2 `scope.md` OPEN 표 신설 | **이월 — 받는 쪽 「배달 오케스트레이션 slice」**. `markDelivered`/`markFailed`/`markIsolated` 에 production 호출부가 없고 *"통로를 열어 해결하지 않았다"* | 4C-2 `scope.md` OPEN 표·`checklist.md` 인계 표·`commands.md` · `milestone-4.md` 4C-2 「알려진 제한(종결 시점)」. capability-map 미등재 |
| `OPEN-4D-LADDER-SCORE-SOURCE` | 4D-1 `scope.md` 「운영자 결정 필요」 신설 (2026-09-10) | **이월 — 받는 쪽 「M5 provider slice(실 servicer)」**. 결정 (a) 확정 2026-09-10 뒤 **네 조각으로 분해**: ① 계약 `EmbeddingService.EmbedText`(M2/2E, 완료) ② 조합 커널(4B-4, 완료) ③ **실 servicer — M5 provider slice** ④ 실 client 배선(4D-2, 완료). §14.3 행은 **취소선이 없고** *"그때까지 (c) 유지"* 로 적는다 | `capability-map.md` §14.3 (취소선 없음) · 4D-1 `scope.md`/`checklist.md` · M2/2E `scope.md` 분해 근거 · `milestone-4.md` 4D-1 종결 단락과 4C-2 뒤 「남은 M4 slice 셋」 단락 |
| `OPEN-4D-POLICY-VALUES` | 4D-1 `scope.md` OPEN 표 신설 (D-4D-7) | **종결** — 사용자 승인 2026-09-10(`MlCallPolicyData` 값). **단, 승인 뒤 값이 한 번 갱신됐다** — 2026-09-16 운영자 결정 ②(M5/5F-2)가 `featureSchemaVersion` 을 계약 패키지 식별자에서 `award-rate-features-v2` 로 대체해 `OPEN-5E2-FEATURE-SCHEMA-PARITY` 를 닫았다 | 정본 4D-1 `policy-values.md` `change_history`(2026-09-10 승인 · 2026-09-16 갱신 둘을 다 적는다) · `scope.md` OPEN 표 취소선 · `milestone-4.md` 4D-1 종결 단락. capability-map 에 자기 행 없음(`OPEN-M2-DEADLINE-VALUES` 행 안에서 언급) |
| `OPEN-4D2-POLICY-VALUES` | 4D-2 `scope.md` OPEN 표 신설 (D-4D2-2) | **이월 — 받는 쪽 `M5 5E` 실측**(`OPEN-M2-DEADLINE-VALUES` 와 같은 경로). 임베딩 호출 정책 값은 4D-1 과 같은 착수 값 | 4D-2 `scope.md` OPEN 표·「알려진 제한」·`checklist.md` 인계 표. capability-map 미등재 |
| `OPEN-4D2-VECTOR-FORGERY-AT-WIRING` | 4D-2 verifier 표적 1 에서 신설(2026-09-11 정정) | **이월 — 받는 쪽 `4B-6`** — *"4B-6(소비자 배선 slice)이 닫는다"*. **받는 쪽 4B-6a·4B-6b 는 둘 다 종결했고 두 slice 의 evidence 전체에 이 식별자가 0건**이며 4B-6b 종결 단락의 알려진 제한에도 없다 | 4D-2 `scope.md` OPEN 표·「우회 후보 — 값 위조 축」 (6)·`checklist.md` 인계 표 · `milestone-4.md` 4D-2 「알려진 제한(종결 시점)」과 그 뒤 「남은 M4 slice」 단락. capability-map 미등재. **§5 주의 목록** |
| `OPEN-4D2-VECTOR-PERSISTENCE` | 4D-2 `scope.md` OPEN 표 신설 | **이월 — 받는 쪽 「persistence 후속」**(벡터 저장 자리·kNN·차원 고정·재계산 정책) | 4D-2 `scope.md` OPEN 표·`checklist.md` 인계 표. capability-map 미등재 |
| `OPEN-4D3-DIAGNOSTICS-RENDER` | 4D-3 `scope.md` 신설 (2026-09-16) | **종결** — *"닫힘 M4/4D-4 2026-09-16"*. 운영자 결정 (A): sealed `PredictionEvidence` 를 필수 인자로 싣고 순수 함수가 알림 본문용 줄을 낸다 | `capability-map.md` §14.3 취소선 행 · `milestone-4.md` 4D-4 종결 단락 · 4D-4 `scope.md` D-4D4-1~8 |
| `OPEN-4D3-SAMPLE-SUPPLY` | 4D-3 `scope.md` D-4D3-4 신설 (2026-09-16) | **상충** — ① 종결: `milestone-4.md` 4B-7 종결 단락이 *"`OPEN-4D3-SAMPLE-SUPPLY` 닫힘(2계층)"* ② 활성: `capability-map.md` §14.3 행은 **취소선 없이** 담당을 *"운영자 결정 (a) 2026-09-16 → **M4/4B-7 진행 중**"* 으로 적는다(4B-7 은 2026-09-16 종결) | `milestone-4.md` 4B-7 종결 단락 · `capability-map.md` §14.3 행 · 4D-3 `scope.md`/`checklist.md` · 4B-7 `scope.md`/`checklist.md` |
| `OPEN-4D4-CONTENT-REF` | 4D-4 `scope.md` D-4D4-5 신설 | **이월 — 받는 쪽 `6A`**(`ContentRenderer` 구현·`NotificationRequest → NotificationIntent` 다리와 함께 착수 조사에서 결정). 부수 항목: 문구 합성 경계 test 가 파일 하나만 스캔한다 | `capability-map.md` §14.3 (취소선 없음) · 4D-4 `scope.md`/`checklist.md` · `milestone-4.md` 4D-4 종결 단락 |
| `OPEN-4D4-REVIEW-EVIDENCE` | 4D-4 `scope.md` D-4D4-8 신설 | **이월 — 받는 쪽 `6A`**(앱 알림함 기록 경로와 함께 결정). 근거 캐리어가 `BidNow` 경로에만 실린다 | `capability-map.md` §14.3 (취소선 없음) · 4D-4 `scope.md`/`checklist.md` · `milestone-4.md` 4D-4 종결 단락 |
| `OPEN-4E-CORPUS` | 4E `scope.md` OPEN 표 신설 (D-4E-6) | **이월 — 받는 쪽 「`m4` 병합 뒤 curator」**. 2026-09-09 사용자 승인 ④ 에서 *"재확인 … 여전히 열림"*, *"닫히지 않고 … curator 작업으로 유지된다"* | 4E `scope.md` OPEN 표 · `checklist.md` 「사용자 승인」 절과 「알려진 제한」 · `milestone-4.md` 4E 종결 단락. capability-map 미등재 |
| `OPEN-4E-POLICY-VALUES` | 4E `scope.md` OPEN 표 신설 (⑦) | **종결** — *"종결 2026-09-09"*(환경 → 모드 매핑·마스킹 suffix 길이). 값은 착수 placeholder 에서 무변경 | 4E `scope.md` OPEN 표 취소선 · 정본 4E `policy-values.md` · `checklist.md` 「사용자 승인」 절 |

### 1.1 M4 하네스 레인 (`reports/evidence/m4/leak-baseline-coord/`)

slice 가 아니라 하네스 레인이지만 `reports/evidence/m4/**` 안에 있어 재고에 넣는다. 게이트는
저장소의 참조형 패턴 목록(`config/quality/leak-patterns.txt`)을 쓰는 누출 스캔 게이트다.

| 식별자 | 어디서 생겼나 | 문면상 현재 상태 | 처분 근거 위치 |
| --- | --- | --- | --- |
| `OPEN-LEAK-BASELINE-COORD` | M4 PR #7 실측(2026-09-12) — 게이트 baseline 이 좌표 키라 줄 삽입 하나가 CI 를 붉혔다 | **종결** — *"`OPEN-LEAK-BASELINE-COORD` 종결. 승인 셋"*. verifier 2라운드 모두 `ready-for-review`, 재작업 0/5 | `leak-baseline-coord/checklist.md` 「사용자 승인」 절 · `docs/harness/change-history.md` 2026-09-12 행 · `.claude/skills/evidence-pack/SKILL.md` 「낡는 좌표」 절 |
| `OPEN-LEAK-REPORT-KEY-CONSISTENCY` | 같은 레인 verifier low | **이월 — 받는 쪽 「다음에 게이트 코드를 만지는 slice」(특정 slice 미지목)**. *"열어 둔다 — low 이고 현재 결함이 아니다 … 지금 닫으면 게이트 술어 인접 변경이라 표적 재검증 한 라운드가 더 든다"* | `leak-baseline-coord/checklist.md` 「사용자 승인」 절 ② 와 「알려진 제한」 절 |
| `OPEN-LEAK-GATE-REPORT-DURABILITY` | 같은 레인 verifier L-5 | **미결** — 게이트 보고서가 gitignore 대상 디렉터리로 나가 접힘 수 드리프트가 리뷰·CI 어디에도 남지 않는다. *"이 slice 가 닫지 않는다"* 뿐이고 받는 쪽 문면 없음 | `leak-baseline-coord/checklist.md` 「알려진 제한」과 「닫지 않은 것」 |

---

## 2. `OPEN-DIC` 계열 (정본 `data-dictionary.md` §9)

| 식별자 | 어디서 생겼나 | 문면상 현재 상태 | 처분 근거 위치 |
| --- | --- | --- | --- |
| `OPEN-DIC-01` | 0C 데이터 사전 §9 | **종결** — 해소(운영자 결정 2026-09-06, M1/1C 착수, decision 23): 읽기 ② | `data-dictionary.md` §9 해당 행(취소선) · `capability-map.md` §14.2 취소선 행 |
| `OPEN-DIC-02` | 0C §9 (U-2b 잔여) | **이월 — 받는 쪽 `M3 3B`**(게시값 수집 뒤 판정) | `data-dictionary.md` §9 (취소선 없음) · `capability-map.md` §14.3 `OPEN-DIC` 표 |
| `OPEN-DIC-03` | 0C §9 (0C 자체 발견) | **상충** — ① 종결: `data-dictionary.md` §3.6 이 *"`SkipReason` 전수성 — 종결"*, §13.5 가 *"종결(M4/4B-1, 운영자 결정 2026-09-09)"*, `capability-map.md` §14.3 은 취소선 행으로 적는다 ② 활성: **계열의 정본인 `data-dictionary.md` §9 표 그 행은 취소선이 없고** 본문도 갱신 전 그대로다 — *"legacy 사다리를 M1에서 옮길 때 확정된다"* | `data-dictionary.md` §9 행 ↔ 같은 문서 §3.6·§13.5 ↔ `capability-map.md` §14.3. §14.0 이 이 계열의 정본을 §9 표로 지정한다 |
| `OPEN-DIC-04` | 0C §9 (U-1·U-1b 가 금액 둘만 정했다) | **이월 — 받는 쪽 `M1 1B`**(결정 주체 운영자). 2026-09-04 갱신이 명시적으로 *"`OPEN-DIC-04`… 은 이 갱신으로 닫히지 않는다 … §9 표는 그대로 활성으로 남긴다"* | `data-dictionary.md` §9 (취소선 없음)·§11.1 뒤 문단 · `capability-map.md` §14.2. **§5 주의 목록** |
| `OPEN-DIC-05` | 0C §9 (0C 자체 발견) | **종결** — 해소(운영자 결정 2026-09-06, M1/1D, decision 27): 라벨 집합 불변 | `data-dictionary.md` §9 (취소선) · `capability-map.md` §14.2 취소선 행 |
| `OPEN-DIC-06` | 0C §9 (0C 자체 발견) | **이월 — 받는 쪽 `M3 3D`**(persistence adapter) | `data-dictionary.md` §9 · `capability-map.md` §14.3 |
| `OPEN-DIC-07` | 0C §9 (U-3 가 fold 재정의만 정했다) | **이월 — 받는 쪽 `M4 4C`**. 4C-1 `scope.md` OPEN 표는 *"① 봉투가 **수령**"* 이라 적고, M4 착수 문서는 *"`OPEN-DIC-07/09` 는 4C 안에서 닫힘"* 을 **예정**으로 적었다. **4C-1·4C-2 종결 문면 어디에도 닫음 선언이 없고 두 registry 모두 취소선이 없다** | `data-dictionary.md` §9 (취소선 없음) · `capability-map.md` §14.3 (취소선 없음) · `reports/evidence/m4/prep/m4-prep.md` D-M4-4 와 「차단 항목」 문단 · 4C-1 `scope.md` OPEN 표. **§5 주의 목록** |
| `OPEN-DIC-08` | 0C §9 (Codex 리뷰 유래) | **종결** — 운영자 결정 2026-09-04(M1/1B), decision 17 로 ①의 변형으로 조정. §11 표가 *"전건 해소 … 남는 축은 없다"* | `data-dictionary.md` §9 (취소선)·§11 표 · `capability-map.md` §14.2 취소선 행 |
| `OPEN-DIC-09` | 0C §9 (fold 순서) | **이월 — 받는 쪽 `M4 4C`**. `OPEN-DIC-07` 과 같은 자리·같은 문면(봉투가 수령, 닫음 선언 없음) | 위 `OPEN-DIC-07` 과 같은 자리. **§5 주의 목록** |
| `OPEN-DIC-10` | 0C §9 (`RoundingPolicy` 값 축) | **이월 — 받는 쪽 `M1 1B`**(§14.2 가 *"(B) 중 가장 강한 결속"*). 2026-09-04 갱신 문단이 이 항목도 *"닫히지 않는다"* 로 명시. M4 4B-6b `policy-values.md` §4 가 `mode` 를 *"`RoundingPolicy` KDoc 이 명시한 열린 정책값(`OPEN-DIC-10`)"* 으로 인용하며 새 슬롯을 만들었다 | `data-dictionary.md` §9·§11.1 뒤 문단·§12 숫자 인덱스 · `capability-map.md` §14.2 · 4B-6b `policy-values.md` §4. **§5 주의 목록** |
| `OPEN-DIC-11` | M1/1B 조사 A 자체 발견(신설) | **미결** — `AwardRate` 분모의 legacy 다수설이 갈린다. *"이 문서가 그 서술을 정정할지, `AwardRate` 분모를 별도 축 결정으로 미룰지는 이 문서가 판정하지 않는다 — M1/1B 범위 밖(… 계약 갱신이 등재만 하고 판정을 넘긴다)"*. 받는 쪽이 지목되지 않았고 `capability-map.md` §14 에 행이 없다 | `data-dictionary.md` §9 해당 행(취소선 없음) |

---

## 3. `OPEN-STR` 계열 (정본 `capability-map.md` §12)

| 식별자 | 어디서 생겼나 | 문면상 현재 상태 | 처분 근거 위치 |
| --- | --- | --- | --- |
| `OPEN-STR-01` | 0A 전략 축 | **종결** — 운영자 결정 2026-08-26, (c) + 기초금액 | `capability-map.md` §12.1 취소선 행 · §12.2 결정 표 · §13 0B ledger 인계 행 |
| `OPEN-STR-02` | 0A 전략 축 (G5 미측정) | **이월 — 받는 쪽 「실행 검증 — M4/M6 · slice 미지목」**. `reports/evidence/m4/**` 와 `milestone-4.md` 에 이 식별자가 **0건** | `capability-map.md` §12 G5 표·§14.3 `OPEN-STR` 표. **§5 주의 목록** |
| `OPEN-STR-03` | 0A 전략 축 | **종결** — 운영자 결정 2026-08-26, (a) force-bid 유지 + 출처 노출 | `capability-map.md` §12.1·§12.2 · STR-03 블록 |
| `OPEN-STR-04` | 0A 전략 축 (G4) | **미결** — §12 G4 행과 §14.3 행 둘 다 취소선이 없고 담당은 `M4 4A`·결정 주체 운영자다. 4A 는 *"actor 타입만, System 확인 경로 거부"* 로 **형태만** 처리하고 진단(*"없던 것은 승인 게이트가 아니라 actor 기록"*)을 남겼으며, 4A 종결 문면에 이 항목의 닫음·이월 선언이 없다 | `capability-map.md` §12 G4·§14.3 · 4A `scope.md` ③·「수령 OPEN」 표·`m4-prep.md` D-M4-2 |
| `OPEN-STR-05` | 0A 전략 축 | **종결** — 운영자 결정 2026-08-26, (a) 부분문자열 유지 | `capability-map.md` §12.1·§12.2 |
| `OPEN-STR-06` | 0A 전략 축 | **종결** — 운영자 결정 2026-08-26, (a) 채널 배달 게이트 임계를 운영자 전략으로 승격 | `capability-map.md` §12.1·§12.2 |
| `OPEN-STR-07` | 0A 전략 축 | **종결** — 운영자 결정 2026-08-26, 단일 회사. 4A `scope.md` 가 *"(해소) 세션 소유권 검사에만 operator 식별자"* 로 수령 | `capability-map.md` §12.1·§12.2 · 4A `scope.md` 수령 표 |
| `OPEN-STR-08` | 0A 전략 축 | **종결** — 닫힘(연쇄, `OPEN-NOTI-02` 결정에 따라) | `capability-map.md` §12.1·§12.2 · STR-08 블록 |
| `OPEN-STR-11` | 0A 전략 축 (`OPEN-NOTI-03` 통합) | **종결** — 닫힘(연쇄, `OPEN-STR-07` 결정에 따라). **주의**: STR-11 블록의 `근거 부족` 분류는 바뀌지 않으며 채택 여부는 `OPEN-STR-12` 가 승계한다 | `capability-map.md` §12.1·§12.2 · §9.1 상충 표 · STR-11 블록 |
| `OPEN-STR-12` | 0A2 라운드 2 신설(verifier M-3) | **미결** — 명시적으로 열린 채로 유지. D-M4-1 (a) 는 *"채널 독립 use case 만 세우고 Telegram 어댑터는 `후속`(`OPEN-STR-12` **활성 유지** — 상태 기계는 채널 독립)"*. 4A 종결 문면도 *"Telegram 어댑터 없음(`OPEN-STR-12` 활성 유지)"*. 채택 결정 주체는 운영자 | `capability-map.md` §12 G3·§12.1 신설 사유·§14.3 · `m4-prep.md` D-M4-1 · 4A `scope.md` D-M4-1 행·`checklist.md` · `milestone-4.md` 4A 착수·종결 단락 |

---

## 4. M4 가 물려받은 그 밖의 `OPEN`

「담당」 칸이 M4(또는 4A~4E)를 지목하거나 `milestone-4.md`·`reports/evidence/m4/**` 가
인용하는 항목이다. M3·M5 계열 항목의 정본 판정은 그 마일스톤 문서가 갖고 이 재고는 옮기기만 한다.

### 4.1 M4 가 닫은 것

| 식별자 | 어디서 생겼나 | 문면상 현재 상태 | 처분 근거 위치 |
| --- | --- | --- | --- |
| `OPEN-2A-RELEASE-CHECK-4D` | M2/2A `scope.md` OPEN 표 (2026-09-06) | **종결** — *"닫힘(M4/4D-1, 2026-09-10)"*. 제3 변환 금지의 client 집행을 술어 승격 + 같은 호출 안 metadata 대조로 | `capability-map.md` §14.3 취소선 행 · 4D-1 `scope.md` D-4D-3·D-4D-4 |
| `OPEN-2F-DIAGNOSTICS-DOMAIN` | M2/2F `scope.md` OPEN 표 (2026-09-15) | **종결** — *"닫힘 M4/4D-3, 2026-09-16"*. 도메인 타입 + 검증층 짝 술어 fail-closed | `capability-map.md` §14.3 취소선 행 · 4D-3 `scope.md` D-4D3-1~3 · `milestone-4.md` 4D-3 종결 단락 |
| `OPEN-2F-DICT-INTERVAL-SOURCE` | M2/2F `scope.md` OPEN 표 (2026-09-15) | **종결** — *"닫힘 M4/4D-3, 2026-09-16"*. 사전 §6.5 `intervalSource` 행 + §6.5.1 진단 성분 표 신설 | `capability-map.md` §14.3 취소선 행 · `data-dictionary.md` §6.5·§6.5.1 · `milestone-4.md` 4D-3 종결 단락 |
| `OPEN-OPS-10` | 0A 운영 축 | **종결(축 한정)** — *"어휘·전이표 축은 해소(운영자 결정 2026-09-09, M4/4C-1 착수, D-M4-5 (a))"*. **잔여 셋을 같은 행이 명시**: ① backlog 관측은 `OPEN-OPS-03`·`OPEN-OPS-04` 가 별도로 든다 ② claim 가시성 timeout — `OPEN-4C1-TX-CONTRACT-UNVERIFIED` 는 4C-2 가 닫았으나 **죽은 워커의 `Claimed` 잔존 회수(sweep)는 4C-2 범위 밖으로 남아 이 항목의 잔여로 계속 산다** ③ `OPS-06` 자신의 재정의·폐기 판정 행(§10)은 이 편집이 건드리지 않았다 | `capability-map.md` §14.3 취소선 행(잔여 셋을 그 행이 적는다) · `data-dictionary.md` §2.2.5 · 4C-1 `checklist.md` 해당 절 · `milestone-4.md` 4C-1 종결 단락 |
| `OPEN-NOTI-02` | 0A 알림 축 | **종결** — 운영자 결정 2026-08-26 해소. 4E 가 *"(해소) ④ at-most-once 소비"* 로 수령 | `capability-map.md` §12.1·§12.2 · 4E `scope.md` 수령 표 |
| `OPEN-3D-GRANT-PUBLIC-BLINDSPOT` | **M4/4C-2 verifier r2 M-3 에서 신설**(2026-09-10) | **종결** — *"M3/3G 종결, 사용자 승인 2026-09-10"*. 유효 권한 행렬 test 하나가 옛 술어 둘을 대체. 잔여는 래칫 밖 표면(`OPEN-3G-NONTABLE-PRIVILEGE-SURFACES`)으로 **별도 항목**으로 산다 | `capability-map.md` §14.3 취소선 행 · `milestone-3.md` Slice 3G 절 · `milestone-4.md` 4C-2 종결 단락 승인 ② · 4C-2 `checklist.md` 인계 표(옛 포인터) |

### 4.2 M4 를 거쳐 열려 있는 것

| 식별자 | 어디서 생겼나 | 문면상 현재 상태 | 처분 근거 위치 |
| --- | --- | --- | --- |
| `OPEN-2E-TEXT-SYNTHESIS` | M2/2E `scope.md` D-2E-5·OPEN 표 (2026-09-10) | **상충** — ① 닫음: `milestone-4.md` 4B-6a 착수 단락이 *"`OPEN-2E-TEXT-SYNTHESIS`를 fact allow-list 시그니처로 **닫는다**(D-4B6A-1)"* ② 후보: 4B-6a `scope.md` OPEN 표는 *"v1 규약으로 **종결 후보**(②③) — 종결 승인 시 capability-map 갱신은 4B-6b 병합 뒤 일괄"* ③ 활성: `capability-map.md` §14.3 행은 **취소선 없이** 담당을 *"M4 **4B-4**(신설 예정 slice)"* 로 적는다(실제로 다룬 것은 4B-6a). 4B-6a·4B-6b 종결 단락에 종결 승인 문면이 없고 `milestone-4.md` 4D-2 알려진 제한은 여전히 *"`featureSchemaVersion` 은 `OPEN-2E-TEXT-SYNTHESIS`(4B-6) **소유 축**"* 으로 적는다 | `milestone-4.md` 4B-6a 착수 단락 ↔ 4B-6a `scope.md` OPEN 표 ↔ `capability-map.md` §14.3 행 ↔ `milestone-4.md` 4D-2 「알려진 제한(종결 시점)」 · 4D-2 `scope.md`/`checklist.md` 도 「4B-6 소관」으로 적는다 |
| `OPEN-M2-DEADLINE-VALUES` | ADR 0010 신설 (2026-09-06) | **이월 — 받는 쪽 `M5 5E`(실측) → `M4 4D`(정책 version)**. 4D-1 승인 문면이 *"`OPEN-M2-DEADLINE-VALUES`는 5E 실측 뒤 정책 version 갱신 경로로 **활성 유지**(값의 옳음 자체는 이 승인의 대상이 아니다)"* | `capability-map.md` §14.3 (취소선 없음, 정본 `ADR 0010` §5) · 4D-1 `policy-values.md` §4·`change_history`·`checklist.md` · 4D-2 `scope.md`/`checklist.md` |
| `OPEN-ADR-12` | `ADR 0005` §5 (OPS-01 lease 어댑터) | **이월 — 받는 쪽 `4C-2`/후속**. 4C-1 종결 단락의 잔여 셋이 *"`OPEN-ADR-12`(lease 어댑터, 4C-2/후속)"*. ADR 0005 §5 제목에 취소선이 없고 4C-2 종결 문면에 처분이 없다 | `docs/adr/0005-domain-events-and-outbox.md` §5 해당 제목·§3.2·「이 ADR 이 확인하지 않은 것」 · `capability-map.md` §14.3 `OPEN-ADR` 표 · `milestone-4.md` 4C-1 종결 단락. **§5 주의 목록** |
| `OPEN-ADR-13` | `ADR 0005` §5 (db-scheduler 실패 처리 기본값) | **이월 — 받는 쪽 `M4`(§14.3 담당: *"M4 — `milestone-4.md` 4C"*)**. 4C-1 종결 단락은 *"`OPEN-ADR-13`(db-scheduler 실패 기본값, **4C-1에는 스케줄러가 없다**)"* 로 잔여 선언만 하고 다른 slice 를 지목하지 않는다. ADR §5 제목 취소선 없음 | `docs/adr/0005-domain-events-and-outbox.md` §5 해당 제목·D-11·「확인하지 않은 것」 · `capability-map.md` §14.3 · `milestone-4.md` 4C-1 종결 단락. **§5 주의 목록** |
| `OPEN-ML-02` | 0A ML 축 | **이월 — 받는 쪽 `M5 5C·5D` 와 `M4`(opportunity 축)**. §12 정본 행: *"M2 몫은 닫혔다(D-M2-11 (a)) … 남는 물음 「Platt 가 사용자 응답에 도달해야 하는가」는 M5 5C·5D 와 M4(opportunity 축)로 이월"*. M4 쪽 처리는 4B-4 가 **「경계 밖」** 으로 두고 KDoc 에 *"`OPEN-ML-02` 결정 뒤 `calibrated_win_rate`"* 를 적은 것뿐 | `capability-map.md` §14.3 `OPEN-ML` 표 · 4B-4 `scope.md` ⑤·OPEN 표. **§5 주의 목록** |
| `OPEN-NOTI-01` | 0A 알림 축 | **미결** — 4E `scope.md` OPEN 표가 *"⑤ 구조로 닫는 **후보**(의도/버그 판정 불요) — 종결 시 capability-map 갱신은 4E 밖(병합 뒤 일괄)"* 이라 적으나 **4E 종결 문면(`milestone-4.md`·4E `checklist.md`)에 이 식별자가 없고** §12·§14.3 행 둘 다 취소선이 없다 | `capability-map.md` §12 G4·§14.3 · 4E `scope.md` OPEN 표·D-4E-8 |
| `OPEN-NOTI-04` | 0A 알림 축 | **이월 — 받는 쪽 「후속」(특정 slice 미지목)**. D-M4-8 (a): *"메일 라이브 송신은 4E 밖(`후속`)"*. §14.3 담당 칸은 여전히 *"M4 4E(그 전에 범위 결정)"* | `capability-map.md` §14.3 · 4E `scope.md` out_of_scope·착수 가정 문단·OPEN 표 · `checklist.md` 「사용자 승인」 2 |
| `OPEN-NOTI-05` | 0A 알림 축 | **상충(담당)** — ① `capability-map.md` §14.3: 담당 *"M4 4E"*, 결정 주체 운영자 ② 4E D-4E-4: *"읽음 상태 되돌림은 **4E 밖(앱 알림함 소유, 6A)** — 어댑터 계약에 읽음 개념 없음"*. 두 문서가 소유자를 다르게 적고 §14.3 은 갱신되지 않았다 | `capability-map.md` §14.3 ↔ 4E `scope.md` D-4E-4·OPEN 표 · `checklist.md` 「사용자 승인」 2 |
| `OPEN-NOTI-06` | 0A 알림 축 | **상충(담당)** — ① `capability-map.md` §14.3: *"**M4 이후** — V2 운영 관측"*, 결정 주체 「V2 코퍼스·관측」 ② 4E D-4E-8: *"NOTI-01 가치 게이트 임계 4종은 4E 정책 슬롯이 아니다 … **4B 소유**"*, OPEN 표 *"4B 소유로 이관"*. **어느 4B slice 의 evidence 에도 이 식별자가 0건** | `capability-map.md` §14.3 ↔ 4E `scope.md` D-4E-8·out_of_scope·OPEN 표 |
| `OPEN-NOTI-07` | 0A 알림 축 | **이월 — 받는 쪽 「후속」(특정 slice 미지목)**. D-M4-8 (a): 채널 fallback 은 4E 밖. §14.3 담당은 *"M4 4E(그 전에 범위 결정)"* | `capability-map.md` §14.3 · 4E `scope.md` out_of_scope·OPEN 표 |
| `OPEN-NOTI-08` | 0A 알림 축 | **미결** — 4E 가 D-4E-3 으로 **계약을 고정**했다(*"통지 이후 판정 확정은 새 `DeliveryRequest`(새 idempotency key, 기존 메시지 갱신 없음)"*)지만 원 물음(*"다시 알릴 것인가"*, 결정 주체 운영자)의 닫음 문면이 없고 §14.3 행에 취소선이 없다 | `capability-map.md` §14.3 · 4E `scope.md` D-4E-3·OPEN 표 · `checklist.md` 「사용자 승인」 2 |
| `OPEN-OPS-02` | 0A 운영 축 | **이월 — 받는 쪽 `M4 4C`**(스케줄 기본값 정책, 결정 주체 운영자). `reports/evidence/m4/**` 와 `milestone-4.md` 에 **0건** | `capability-map.md` §14.3 `OPEN-OPS` 표. **§5 주의 목록** |
| `OPEN-OPS-03` | 0A 운영 축 | **이월 — 받는 쪽 「`M4` 이후 · V2 운영 관측」**. 정본 G6 행이 *"`OPEN-OPS-04`와 함께 정한다"* 로 짝을 적는다. `OPEN-OPS-10` 잔여 ① 이 이 항목을 backlog 관측 소유자로 지목 | `capability-map.md` §12 G6·§14.3 · 4C-1 `checklist.md` 해당 절 · `m4-prep.md` |
| `OPEN-OPS-04` | 0A 운영 축 | **이월 — 받는 쪽 「`M4` 이후 · V2 운영 관측」**(legacy 임계값 다수 재유도) | `capability-map.md` §14.3 · 4C-1 `checklist.md` · `m4-prep.md` |
| `OPEN-OPS-08` | 0A 운영 축 | **이월 — 받는 쪽 「운영자(범위)」, 자리는 `M4`/`M6`**. *"조사 범위 밖이었다"* | `capability-map.md` §14.3 |
| `OPEN-SET-01` | 0A 정산 축 | **이월 — 받는 쪽 「`M4` 이후 — V2 코퍼스 재유도 · slice 미지목」** | `capability-map.md` §14.3 `OPEN-SET` 표 |
| `OPEN-SET-04` | 0A 정산 축 | **이월 — 받는 쪽 `M4 4C`**(이벤트 재관측 횟수 노출 여부, 결정 주체 운영자 범위). `reports/evidence/m4/**` 와 `milestone-4.md` 에 **0건**. `data-dictionary.md` §12 는 이 항목과 `OPEN-DIC-07` 을 `reobservationCount` 의 두 미결로 짝지어 적는다 | `capability-map.md` §12 G4·§14.3 · `data-dictionary.md` §12 숫자 인덱스. **§5 주의 목록** |
| `OPEN-SET-05` | 0A 정산 축 | **이월 — 받는 쪽 `M4`**(재공고 대사 대상 선택 규칙, 결정 주체 운영자). **0건** | `capability-map.md` §14.3. **§5 주의 목록** |
| `OPEN-SET-06` | 0A 정산 축 | **이월 — 받는 쪽 「`M4` 이후 — V2 코퍼스 재도출」** | `capability-map.md` §14.3 |
| `OPEN-SET-10` | 0A 정산 축 | **이월 — 받는 쪽 「`M4` 이후 — V2 코퍼스 재확인」 → 운영자** | `capability-map.md` §14.3 |
| `OPEN-DEC-03` | 0A 결정 축 → 0A2 라운드 7 **활성 복원** | **이월 — 받는 쪽 「갈림 — `M1 1B·1D` / `M4 4B`」**. 상태는 *"잠정 (b) 미채택 … 예규 원문 확인 후 **재결정 대상**"* 이고 §12.1 은 *"최종 결정이 없는 쟁점을 별도 어휘로만 보존한 형식적 폐쇄"* 였다고 적는다. **0건** | `capability-map.md` §12 G4·§12.1 복원 사유·§12.2·§14.2 (담당 갈림) · `docs/adr/0002` §5. **§5 주의 목록** |
| `OPEN-DEC-10` | 0A 결정 축 | **이월 — 받는 쪽 `M4 4B`**, *"`OPEN-DEC-03`과 같은 예규 확인에 걸린다"*(외부 문서 선행). **0건** | `capability-map.md` §14.3 `OPEN-DEC` 표. **§5 주의 목록** |

### 4.3 M4 가 인용하지만 다른 마일스톤이 소유하는 것 (경계 확인용)

| 식별자 | 문면상 현재 상태 | 처분 근거 위치 |
| --- | --- | --- |
| `OPEN-2B-AGENCY-ID` | **종결** — *"닫힘 M3/3H-2 2026-09-17"*(요청·표본 축이 수요기관코드를 싣고 엔진 교차 실측). M4 4B-7·4B-8·4D-3·4B-6b 가 「기관 계층은 이 뒤」로 인용했다 | `capability-map.md` §14.3 취소선 행 · `milestone-3.md` 3H-2 |
| `OPEN-2E-TEXT-MAX` | **종결** — *"닫힘(2026-09-10)"*, 값 무변경. 4B-6a 가 같은 값을 test 로 대조 | `capability-map.md` §14.3 취소선 행 · M2/2E `policy-values.md` |
| `OPEN-2A-CANONICAL-FORM` | **이월 — `M2 2D`** (M4 문서는 줄 번호 확인 맥락으로만 인용) | `capability-map.md` §14.3 |
| `OPEN-2B-OBJECTIVE-VALUES` | **이월 — `M5 5D`**. 4B-6b 가 `SCENARIO_TRIPLE` 하나를 정책 슬롯 값으로 승인받았다 | `capability-map.md` §14.3 · 4B-6b `policy-values.md` §2 |
| `OPEN-2B-TEST-DISCOVERY-GUARD` | **「게이트가 지킴」** — `testShapeGate`. 4B-6b 가 같은 계열의 Gradle 입력 사각을 실측해 별도 알려진 제한으로 남겼다 | `capability-map.md` §14.3 · 4B-6b `checklist.md` |
| `OPEN-ML-03` | **종결(계약 수준)** — 운영자 결정 2026-09-06(D-M2-8 (a)). M5 구현 축은 5D 승계. 4B-4 가 `ProbabilityScore` KDoc 을 이 결정에 맞춰 정정 | `capability-map.md` §14.3 취소선 행 · 4B-4 `scope.md` ⑤ |
| `OPEN-3A-SOURCE-TZ` | **이월 — 3A(형태·정책 슬롯) → curator/운영자(값 승인)**. 4B-7 이 *"아직 미결"* 로 인용하며 착수 가정 `Asia/Seoul` 을 슬롯 없이 상수로 두고 checklist 에 등재 | `capability-map.md` §14.3 · 4B-7 `scope.md` D-4B7-7·`policy-values.md` |
| `OPEN-3A-AGGREGATE` | **이월 — 문서 slice(문면 정합) · 조립은 `3D`·`M4 4B`**. M4 evidence **0건** | `capability-map.md` §14.3 · `milestone-3.md` 3A. **§5 주의 목록** |
| `OPEN-3B2-TARGETED-OPENING-QUERY` | **종결** — *"닫혔다(2026-09-08) — 문서가 맞고 legacy 실측 서술이 틀렸다"*. 귀결: *"M4 수집 배선이 이 사실 위에 선다"* | `capability-map.md` §14.3 해당 행 |
| `OPEN-3B2-STORAGE-ROW-KEY-COLLISION` | **이월** — §14.3 행 머리는 *"M3/3E 가 닫는 중, verifier 판정 대기"*, 담당 칸은 *"M4 4B 착수 전 또는 3D 후속"*. `milestone-3.md` 3E 절은 *"그 종결은 **어댑터 층 한정**"* 으로 좁힌다. M4 evidence **0건** | `capability-map.md` §14.3 · `milestone-3.md` 3B-2·3E 절. **§5 주의 목록** |
| `OPEN-3C-ATTACHMENT-FIELD-CONTRACT` | **이월 — 받는 쪽 「`3B-2` 또는 `M4 4B` 착수 전」**(curator 표 추가 + 정책 값 한 커밋). `milestone-3.md` 는 이를 3E 뒤 잔여 후속으로도 적는다. M4 evidence **0건** | `capability-map.md` §14.3 · `milestone-3.md` 3B-2·3E 잔여 후속. **§5 주의 목록** |
| `OPEN-3C-ATTACHMENT-SLOT-OVERFLOW` | **이월 — 「운영 관측 뒤(`M4 4B` 또는 `M6`)」**. 처리 문면: *"3C·M4 는 열 칸을 순회하되 가정을 주석·test 로 명시"* | `capability-map.md` §14.3 |
| `OPEN-3E-OPENING-AMOUNT-AUTHORITY-GUARD` | **이월 — 받는 쪽 「`M4 4B` 착수 전」 → 운영자·3D 소관 레인**. 행 문면: *"**M4 4B 가 개찰 fact 를 쓰기 전에 판단해야 한다** — 그 전에는 덮어쓰기 방어가 Kotlin 층에만 있다"*. 4B-7 이 개찰 fact(`opening_result`·예비가격 행)를 표본으로 소비했고 **M4 evidence 에 이 식별자는 0건** | `capability-map.md` §14.3 · `milestone-3.md` 3E 절 · 4B-7 `scope.md`(개찰 입력 소비). **§5 주의 목록** |
| `OPEN-3E-ROW-ORDER-STABILITY` | **이월 — 「운영 관측 뒤(`M4 4B` 또는 `M6`)」** | `capability-map.md` §14.3 |
| `OPEN-3E-RESERVE-FLAG-MISMATCH` | **이월 — 「운영 관측 뒤(`M4` 수집 배선 또는 `M6`)」** | `capability-map.md` §14.3 |
| `OPEN-3F-OPENG-RANK-SEMANTICS` | **이월 — 「운영 관측 뒤(`M4 4B` 또는 `M6`)」** | `capability-map.md` §14.3 |
| `OPEN-3H-AGENCY-BACKFILL` | **이월 — `M6`**(운영 데이터가 생긴 뒤 백필 slice). 행이 `OPEN-4B8-CATEGORY-BACKFILL` 과 *"같은 성격"* 으로 짝지어 적는다 | `capability-map.md` §14.3 · 3H `scope.md` D-3H-5 |
| `OPEN-ADR-11` | **종결** — *"닫혔다 — `ADR 0010` 승인, 운영자 결정 2026-09-06"*. 값의 실측 근거가 신설 `OPEN-M2-DEADLINE-VALUES`(M5 5E → M4 4D) | `capability-map.md` §14.3 취소선 행 |
| `OPEN-5E2-FEATURE-SCHEMA-PARITY` | **종결** — M5/5F-2 가 4D 승인 값을 `award-rate-features-v2` 로 갱신해 닫았다(운영자 결정 2026-09-16 ②). **M4 산출물의 승인된 정책 값을 M5 가 바꾼 자리** | 4D-1 `policy-values.md` §3·「되돌림 경로」·`change_history` 2026-09-16 행 |
| `OPEN-5E2-CROSSLANG-REAL-SERVER` | **이월 — `M6 6C`**. 4D-1 정책 값의 Kotlin↔Python 동일성은 *"문서 대조로 둔다(D-5F2-2)"* 이고 실 교차 언어 대조가 이 항목 몫 | 4D-1 `policy-values.md` §3 |

---

## 5. 주의 — 이월 문면의 받는 쪽이 이미 종결했거나 그 축을 다루지 않았다

**갈래가 아니라 주의 표시다.** 위 표의 갈래 개수와 이중 계산하지 않는다. 공통점은
「이월처가 문면에 있다」와 「그 이월처가 그 항목을 닫지도 다시 넘기지도 않았다」가 동시에
성립한다는 것이다.

| 식별자 | 적힌 받는 쪽 | 그 받는 쪽의 현재 상태 |
| --- | --- | --- |
| `OPEN-4B2-2` · `OPEN-4B2-4` · `OPEN-4B2-6` | `4C-2`/`3D` | 4C-2 는 2026-09-10 종결. 4C-2 `scope.md` 가 `OPEN-4B2-2`·`-5` 를 인용하나 세 항목 어느 것도 닫지 않았다 |
| `OPEN-4D2-VECTOR-FORGERY-AT-WIRING` | `4B-6`(소비자 배선 slice) | 4B-6a(2026-09-11)·4B-6b(2026-09-12) 둘 다 종결. **두 slice 의 evidence 전체에 이 식별자가 0건** |
| `OPEN-DIC-07` · `OPEN-DIC-09` | `M4 4C` | 4C-1·4C-2 둘 다 종결(*"4C 축의 마지막 slice"*). 4C-1 은 「봉투가 수령」까지이고 두 registry 모두 취소선 없음 |
| `OPEN-ADR-12` · `OPEN-ADR-13` | `4C-2`/후속 · `M4 4C` | 같음. ADR 0005 §5 제목 둘 다 취소선 없음 |
| `OPEN-DIC-04` · `OPEN-DIC-10` | `M1 1B` | M1 종결. 사전 §11 문단이 *"이 갱신으로 닫히지 않는다 … §9 표는 그대로 활성으로 남긴다"* 로 명시 |
| `OPEN-STR-02` | 「실행 검증 — M4/M6 · slice 미지목」 | M4 종결 판정 단계. M4 evidence·`milestone-4.md` 에 0건 |
| `OPEN-OPS-02` · `OPEN-SET-04` | `M4 4C` | 4C 축 종결. 두 항목 모두 M4 문서에 0건 |
| `OPEN-SET-05` · `OPEN-DEC-03` · `OPEN-DEC-10` | `M4` · 갈림(`M1`/`M4 4B`) · `M4 4B` | 4B 축 종결(*"4B 축은 이것으로 닫힌다(4B-1~4B-6b)"* 뒤 4B-7·4B-8 까지). 세 항목 모두 M4 문서에 0건 |
| `OPEN-ML-02` | `M5 5C·5D` + `M4`(opportunity 축) | M4 쪽 처리는 4B-4 의 「경계 밖」 선언과 KDoc 문구뿐 |
| `OPEN-3A-AGGREGATE` · `OPEN-3B2-STORAGE-ROW-KEY-COLLISION` · `OPEN-3C-ATTACHMENT-FIELD-CONTRACT` · `OPEN-3E-OPENING-AMOUNT-AUTHORITY-GUARD` | 「조립은 3D·M4 4B」 · 「M4 4B 착수 전 또는 3D 후속」 · 「3B-2 또는 M4 4B 착수 전」 · 「M4 4B 착수 전」 | 넷 모두 4B 착수·종결을 지나갔고 M4 evidence 에 0건. 마지막 항목의 문면은 *"M4 4B 가 개찰 fact 를 쓰기 전에 판단해야 한다"* 이고 4B-7 이 개찰 fact 를 소비했다 |
| `OPEN-4B1-LADDER-THRESHOLDS` | (받는 쪽 없음 — 운영자 승인 대기) | 4B-1 에서 열려 4B-2 가 그대로 이었고 M4 마지막 slice 까지 승인 문면 없음 |

### 5.1 그 밖에 종결 판정에 걸릴 사실 둘

- **`reports/evidence/m4/closure/` 는 빈 디렉터리다.** M4 종결 evidence 가 아직 없다.
- **`OPEN-4D-POLICY-VALUES` 는 종결 뒤 값이 한 번 갱신됐다.** 2026-09-10 사용자 승인으로
  확정된 `featureSchemaVersion` 을 2026-09-16 운영자 결정 ②(M5/5F-2)가 대체했다. 절차는
  정본 문서의 `change_history` 에 남아 있다(코드 경로·타입 무변경).

---

## 6. 갈래별 개수

식별자 **115건**. §1·§1.1·§2·§3·§4.1·§4.2·§4.3 의 행을 한 번씩만 셌고(중복 식별자 0),
§0.1 의 registry 지정 표와 §5 주의 목록은 세지 않았다(§5 는 갈래가 아니라 주의 표시이므로
이미 위 갈래에 든 항목을 다시 든다). §0.3 의 오탐 문자열은 식별자가 아니므로 제외했다.

| 갈래 | 건수 |
| --- | --- |
| **종결** | 36 |
| **이월** | 59 |
| **미결** | 12 |
| **상충** | 7 |
| 참조 상태(「게이트가 지킴」 — `OPEN-2B-TEST-DISCOVERY-GUARD` 하나) | 1 |
| 합 | 115 |

절별로는 이렇게 나뉜다.

| 절 | 종결 | 이월 | 미결 | 상충 | 게이트 | 소계 |
| --- | --- | --- | --- | --- | --- | --- |
| §1 M4 신설 `OPEN-4*` | 13 | 21 | 6 | 3 | — | 43 |
| §1.1 M4 하네스 레인 | 1 | 1 | 1 | — | — | 3 |
| §2 `OPEN-DIC` | 3 | 6 | 1 | 1 | — | 11 |
| §3 `OPEN-STR` | 7 | 1 | 2 | — | — | 10 |
| §4.1 M4 가 닫은 물려받은 것 | 6 | — | — | — | — | 6 |
| §4.2 M4 를 거쳐 열려 있는 것 | — | 17 | 2 | 3 | — | 22 |
| §4.3 다른 마일스톤 소유(경계 확인) | 6 | 13 | — | — | 1 | 20 |
| 합 | 36 | 59 | 12 | 7 | 1 | 115 |

**M4 가 신설한 `OPEN-4*` 43건만 보면 종결 13 · 이월 21 · 미결 6 · 상충 3 이다** — M4 자신이
연 것 가운데 열려 있는 것이 30건이고, 그 중 21건은 받는 쪽이 문면에 적혀 있다.

### 6.1 미결 12건 (전수)

`OPEN-4B1-02` · `OPEN-4B1-05` · `OPEN-4B1-06` · `OPEN-4B1-07` ·
`OPEN-4B1-LADDER-THRESHOLDS` · `OPEN-4B2-1` · `OPEN-LEAK-GATE-REPORT-DURABILITY` ·
`OPEN-DIC-11` · `OPEN-STR-04` · `OPEN-STR-12` · `OPEN-NOTI-01` · `OPEN-NOTI-08`

### 6.2 상충 7건 (전수)

| 식별자 | 갈리는 축 |
| --- | --- |
| `OPEN-DIC-03` | 계열 정본(사전 §9 표)은 취소선 없이 원문 그대로 ↔ 같은 사전 §3.6·§13.5 와 `capability-map.md` §14.3 은 「종결(M4/4B-1)」 |
| `OPEN-4C1-TX-CONTRACT-UNVERIFIED` | 「4C-2 가 종결」(`milestone-4.md`·§14.3) ↔ 사전 §2.2.5 문면은 아직 활성(`milestone-4.md` 자신이 그 사실을 알려진 제한으로 적는다) |
| `OPEN-4D3-SAMPLE-SUPPLY` | 「4B-7 이 닫힘」(`milestone-4.md`) ↔ §14.3 행은 취소선 없이 「4B-7 진행 중」 |
| `OPEN-2E-TEXT-SYNTHESIS` | 「4B-6a 가 닫는다」(`milestone-4.md`) ↔ 「종결 후보」(4B-6a `scope.md`) ↔ 취소선 없이 담당 4B-4(§14.3) ↔ 「4B-6 소유 축」(`milestone-4.md` 4D-2 알려진 제한) |
| `OPEN-4B6B-POLICY-VALUES` | 같은 문서가 「종결」(제목·승인 절)과 「활성 유지」(해소 조건·승인 절 말미)를 함께 적는다 |
| `OPEN-NOTI-05` | 담당 「M4 4E」(§14.3) ↔ 「4E 밖, 앱 알림함 소유 6A」(D-4E-4) |
| `OPEN-NOTI-06` | 담당 「M4 이후 · V2 운영 관측」(§14.3) ↔ 「4B 소유로 이관」(D-4E-8, 받은 4B slice 없음) |
