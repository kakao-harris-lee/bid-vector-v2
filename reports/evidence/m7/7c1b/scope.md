# M7/7C①b — 제안 목록 API: 감시 통과 후보 표면 (STR-06 · STR-07) (착수 계약, 2026-10-22, 팀장)

- **근거**: 7A 조정안(`reports/evidence/m7/7a/m6-condition3-adjustment-proposal.md` :57-58, :112)은 STR-06 · STR-07 을 G2 로
  M7 7C ① 에 넘겼다. 그러나 7C①(#78)은 검색 절반(STR-16)만 닫았고 evidence 에 STR-06 · STR-07 흔적이 없다(7C④ 레인 B1 보고,
  팀장 확인 2026-10-22). 이 slice 가 그 공백을 메운다. 운영자 지시 2026-10-22 「7C①b 레인도 열어서 병렬로 진행」 — `milestone-7.md`
  선행 조건의 **운영자 예외**(7C① 과 같은 종류).
- **base**: main `bee7c36f` · worktree `bid-vector-v2-m7-7c1b` · 브랜치 `m7-7c1b/2026-10-22`.
- **병렬 레인**(push 0): 7B(V21) · 7C④(잠정 V22) · 7F 본편 · g1c03 · g1set01 · g1dec05(동결·검토 중).
- **입력 정본**:
  - capability map STR-06(:528-538 — 표시 상한과 분석 예산 분리) · STR-07(:540-556 — 전략 저장 → 스냅샷 stale + 재계산, 계산 중 저장 시
    옛 결과 불채택, **모든 편집 경로**).
  - `docs/discovery/m7-screen-data-requirements.md` 화면 B 행 :45(제안 목록 — 「검색과 같은 카드 형태로」), 카드 행 :46-50, QUAL-02 행.
  - 6A 의 `POST /api/evaluation-dry-runs`(`evaluateDryRun`, D-6A3-6 — 결과별 공고 ID 배열만) · `OPEN-6A3-EVALUATION-DETAIL`.
  - 7C① 의 `GET /api/notices` 카드 형태(`reports/evidence/m7/7c1/`).
  - 전략 편집 세션 확정 경로(`/api/strategy/edit-sessions/{id}/confirm`)와 전략 표(V9).

## 산출물

1. **제안 목록 조회 operation**: 현재 전략의 감시를 통과한 후보를 7C① 카드와 **같은 형태**로 낸다(공고명 · 기관 · 금액+provenance ·
   마감 원값 · 자격 판정). 마감 임박 순. 응답에 **신선도**(어느 전략 판(version/updated_at)으로 계산했는가 · 계산 시각 · stale 여부)를 싣는다.
2. **STR-06**: 표시 상한은 운영자 전략 값이고, **분석/계산 예산과 독립**이다(두 값이 따로 관측되는 test). legacy 의 `limit × 12` clamp 결합은
   만들지 않는다.
3. **STR-07**: 전략 저장(편집 세션 확정)이 후보 결과를 stale 로 만든다. 계산 중에 전략이 바뀌면 그 결과를 채택하지 않는다. 「모든 편집 경로」를
   **구조로** 닫는다 — 호출자가 무효화를 부르는 규율이 아니라, 결과가 계산 기준 전략 판을 싣고 조회 시점에 현재 판과 비교하거나, 전략 저장이
   도메인 이벤트를 낳는 방식. 어느 쪽인지는 Phase 1 에서 판정.
4. **QUAL-02 의 한 조각**(가능하면): 카드의 「자격 ✅/❌ + 무엇이 없어서」는 `OPEN-6A3-EVALUATION-DETAIL` 이 홀더다. 이 slice 에서 사유를
   실을 수 있는지, 아니면 「사유 없음 — 미구현」을 정직하게 표시하는지 Phase 1 에서 판정·보고. 지어낸 사유 금지.
5. OpenAPI 수작성 단일 출처 · 생성 타입 재생성(W-5 바이트 등식) · 추적표 STR-06 · STR-07(· QUAL-02) 행과 capability map 갱신(판정은 실측대로).

## Phase 1 — 설계 먼저, 코드·마이그레이션 0 (팀장 승인 뒤 Phase 2)

checklist.md §0 에 쓰고 팀장에게 보고한 뒤 답을 기다린다. 판정할 것:

- **(P1) 계산 시점**: 조회 시 동기 계산(ML 없음 — 오늘 `UnavailableMlAnalysis` 고정이라 비용이 작은지 실측 근거로) vs 영속 스냅샷 + 비동기
  재계산. 영속 스냅샷이면 **마이그레이션이 생긴다** — 그 경우 번호는 7B(V21) · 7C④(잠정 V22) 다음 잠정 V23 이고, 이유를 근거와 함께 적는다.
  동기 계산이면 STR-07 「stale 표시」가 무엇을 뜻하게 되는지(늘 신선 — 그러면 acceptance 를 어떻게 정직하게 판정하는가)도 적는다.
- **(P2) 인증 축**: 전략은 기존 운영자 자격 헤더 경로 소유(V9 싱글턴)다. 7B 는 계정 범위 경로를 세션 전용으로 했다. 이 operation 이 어느 축에
  서는지 근거와 함께. R-2 남은 절반(싱글턴 → 계정)을 이 slice 가 끌어오지 않는다.
- **(P3) 카드 형태 재사용**: 7C① 의 응답 스키마를 공유 컴포넌트로 참조할 수 있는지(OpenAPI `$ref`), 코드 매핑 중복 없이.
- **(P4) STR-07 의 「모든 편집 경로」 열거**: 오늘 V2 에서 전략을 바꾸는 경로를 전수로 찾고(코드 근거), 구조로 닫히는지 보인다.
- **(P5) 범위 밖**: ML 분석 배선(ML-01) · 알림(7C③) · 감시 실행 이력·diff(STR-08) · 화면(7E).

## 설계 검토 (Phase 2.5, 팀장)

- **(0) 위협 모델**: 방어 — 인증 없는 조회 · stale 결과를 신선한 것처럼 표시 · 계산 중 전략 변경 결과 채택 · 표시 상한으로 계산 예산 조작 ·
  사유 지어내기. 방어하지 않음 — 공개 공고 데이터를 인증된 운영자가 읽는 것.
- **(1) 구성**: 새 경로는 7D 기본 거부 필터 아래(새 무인증 경로 0). 바인딩 매개변수만. 상한·정렬은 선언 값.
- **(2) 과잉 금지**: ML · 알림 · 이력 diff · 화면을 만들지 않는다.

## test

- 카드 형태 계약 test(7C① 과 같은 스키마) · 정렬 · 상한 경계.
- STR-06 독립 관측(표시 상한 변경 → 계산 예산 불변).
- STR-07: 전략 저장 → stale(또는 재계산) · 계산 중 저장 → 결과 불채택 · 전 편집 경로 전수.
- 인증 경계가 기존 기계 전수 test 에 자동 포함되는지.
- 마이그레이션이 생기면 `CleanMigration*Test` 관례 등재.
- 각 핵심 성질은 변이에서 RED(변이 표를 evidence 에).

## 검증·동결

- 무거운 빌드 전 확인 셋(pgrep · free · ps)을 별도 호출로 먼저, `flock /home/deploy/.local/bid-vector-build.lock` 으로 직렬, `--no-daemon`.
  available < 6GB 또는 swap free < 2GB 면 시작하지 않는다. 00:30~03:00 KST 금지.
- 완료: `check` exit 0 · 변이 표 · rollback.md(명령으로 목록 도출) · `leakPatternGate` executed 확인 → 판정 SHA 보고 후 동결. push 0.
- 검토: verifier(opus) · code-reviewer(sonnet) 병렬. 마이그레이션이 있으면 migration-reviewer 관점 포함.

## 계약 갱신 이력

D-7C1B-1 ~ D-7C1B-3 의 결정 문면은 `checklist.md` §1 · §10 에 반영돼 있다(앞 팀장 세션).

**D-7C1B-4 (2026-10-24, 팀장 — 검토 r2 수령 · 승인 전 일괄).** verifier r2 ready-for-review(blocker 0 · high 0 · medium 1 · low 4),
code-reviewer r2 approve(blocker 0 · high 0 · medium 1 비차단 · low 8). r1 지적 아홉은 둘 다 「닫힘」으로 판정했다. 승인 전 일괄로 다음을 한다.
- **verifier F-1(medium, test 공백) — 고친다.** 미결 사유 라벨 ↔ 사유의 대응을 사유별 쌍으로 단언한다. 사유 목록은 `sealedSubclasses` 에서 도출한다
  (손 목록 금지). 두 라벨을 맞바꾸는 변이가 RED 인지 실측해 변이 표에 더한다. 위협 모델 「사유 지어내기」 자리다 — 표적 재검증 대상이다.
- **문면 정정(verifier L-1 · CR §6-14)**: `ListSuggestionsUseCase` · `ComputationInputs` KDoc 의 「프로필을 한 번 읽어 고정」을 §1 P4 정정과 맞춘다(주석만).
  §6-14 「대가가 재계산 1회뿐」 → 「재계산 1회이고 연속되면 409」.
- **트리거 명시(verifier L-3)**: §6-4 에 「7C④ 병합이 프로필 쓰기 경로를 열면 프로필 고정 래퍼를 다시 판단한다」를 적는다.
- **test 하네스(CR low)**: probe 무장/해제를 `try/finally` 로 · STR-06 use case test 의 `calledFor` 에 기대 id 목록 한 줄. 나머지 CR low
  (문장 기록 프록시 상주 · 비지 않은 프로필의 실 JDBC 값 등식(verifier L-2와 같음) · fixture SQL 보간 · 네 번째 키 목록 사본 · 동명)는 §6 에 후속 후보로 한 줄씩 적는다.
- **후속 slice 후보(CR medium)**: 프로필 1회 고정 래퍼(식 `5+n+h`, §6-4 A-B-A 동시 해소) + 운영 cap 급 1회 실측. 이 slice 에서 고치지 않는다.
- **rebase**: `998b59dd` 위로. 충돌은 합집합으로 풀고 면제 수·추적표 집계를 재계수한다. 그 뒤 찬 `check`(`--rerun` 또는 `--no-build-cache`) · web job · 변이 표(F-1 포함)
  · rollback ①~⑦ 을 새 base 에서 다시 잰다(앞 실측은 base 가 바뀌어 무효). 판정 SHA 를 다시 고정해 보고한다.
- 새 파일이 생기면 in_scope 와 대조해 보고한다. production 코드 변경은 KDoc 두 자리뿐이어야 한다.

**D-7C1B-5 (2026-10-24, 팀장 — 표적 재검증 r3 수령).** verifier r3 ready-for-review(low 1) · code-reviewer r3 approve(low 6). 산출물 무변경, evidence 만 고친다: §6-18 에 r2 지적의 빠진 절(use case test 의 FlippingProfilePort 는 같은 인스턴스를 두 번 내므로 참조 등식으로 통과 — 값 등식 축은 어디서도 재지 않는다; 값 등식 없는 ProfileFacts 멤버가 들어오면 모든 조회가 재계산→409)을 더하고, CR r3 LOW-2~6 · verifier r3 L-1 을 §6 에 한 줄씩 등재한다. D-7C1B-4 의 「low 8」은 r2 실제 low 9(+medium 1)로 정정한다(본문은 고치지 않고 이 절이 정정).
