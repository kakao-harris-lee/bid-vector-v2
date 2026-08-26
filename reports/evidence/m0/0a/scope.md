# Slice 계약 — M0 / 0A capability map

```yaml
milestone: m0
slice: 0a-capability-map
base_sha: 3dc7d26333e9f3699c3fd1149651fe54500b6f27
head_sha: 6dee91a24d70865efb8881ec1e13c3f98c6fa666  # 수정 라운드 5 최종 산출물 커밋. 아래 갱신 이력 참조
in_scope:
  - docs/discovery/capability-map.md
  - reports/evidence/m0/0a/
out_of_scope:
  - Kotlin/Spring/Python 애플리케이션 코드 (M0 금지 사항)
  - 다른 M0 산출물: regression-ledger(0B), data-dictionary(0C), ADR(0D), fixtures
  - 운영 DB query, 실제 외부 API 호출
  - bid-vector/ symlink 아래 기존 저장소 수정 (읽기 전용)
acceptance_commands:
  - "N/A — 문서 slice. milestone-0.md 완료 조건 중 0A 담당 항목을 checklist.md로 대조:
     (1) V2 필수 capability마다 사용자 가치와 acceptance scenario 존재,
     (2) Python 파일/endpoint를 그대로 옮기는 작업 항목 없음 (Kotlin service 범위),
     (3) 각 capability가 V2 필수/후속/폐기/근거 부족 중 하나로 분류됨,
     (4) 모든 분류에 근거 파일/commit 인용 존재"
rollback: "N/A — 문서 산출물은 git revert로 복구"
```

작성: 2026-08-22, v2-slice-pipeline Phase 1

## 갱신 이력

### 2026-08-22 — head 기입 (spec-writer)

`head_sha`에 산출물 커밋 `e6dcbba`(`docs/discovery/capability-map.md` +
`reports/evidence/m0/0a/`)를 기입했다.

커밋은 자기 자신의 SHA를 담을 수 없으므로 amend 대신 이 갱신을 별도 커밋으로 남긴다.
**Codex 리뷰 range는 `3dc7d26...<현재 HEAD>`이며**, 현재 HEAD는 이 scope 갱신 커밋이다.
`head_sha` 필드가 가리키는 `e6dcbba`는 그 직전 커밋으로, 실제 산출물 diff 전부를
담고 있다. 두 커밋 사이의 차이는 이 파일의 `head_sha` 한 줄과 이 절뿐이다.

### 2026-08-22 — 수정 라운드 1 (Codex `request_changes` 대응, spec-writer)

Codex 독립 리뷰(`codex-review-20260822T061532Z.json`, verdict `request_changes`,
blocker 0 / high 2 / medium 2)의 finding을 finding별 커밋으로 반영했다.

| 커밋 | 대응 | 요지 |
| --- | --- | --- |
| `87a2417` | high #1 | OPEN이 미해결인 동작을 확정하던 acceptance 6곳을 조건부 시나리오로 재서술(Codex 지목 4 + 전수 grep 2). §0.5에 규약 신설 |
| `1fef079` | high #2 | SET-06 시간축 침묵 fallback 제거. `OPEN-SET-10` 신설 |
| `0dbef95` | medium #3·#4 + verifier low 5건 + evidence | STR-13 이중 분류를 STR-13/STR-15로 분리, 분류 검증기 교체, ML-07 acceptance 관측 가능화, `commands.md` 신설, Codex 리뷰 JSON 커밋 |

- `head_sha`를 `e6dcbba` → `0dbef95`(수정 라운드 1 최종 산출물 커밋)로 갱신했다.
- **재리뷰 range는 `3dc7d26...<현재 HEAD>`이며**, 현재 HEAD는 이 scope 갱신 커밋이다.
  라운드 0과 같은 이유로(커밋이 자기 SHA를 담을 수 없다) `head_sha`는 그 직전 커밋을
  가리키며, 두 커밋의 차이는 이 파일의 `head_sha` 한 줄과 이 절뿐이다.
- range 안에 이 slice와 무관한 커밋 `1f8e57c`(harness 스키마)가 하나 포함된다 —
  `.claude/` 하위 파일이며 in_scope 밖이다. 이 slice가 만들지 않았다.
- 산출물 변화: capability 93 → 94(STR-15 신설), `근거 부족` 10 → 11,
  활성 `OPEN` 64 → 65(`OPEN-SET-10` 신설), `capability-map.md` 2,403 → 2,518줄.
- 미처리 1건: verifier F-5(디렉터리 없는 파일명의 다중 해석 가능성). 사유와 권고는
  `checklist.md` §7에 있다.

### 2026-08-22 — 재리뷰 전 잔존 2건 정리 (verifier L-1·L-2, spec-writer)

재검증 판정은 `ready-for-review`였으나, Codex finding #1과 같은 계열의 잔존 사례 2건이
재리뷰에서 다시 걸릴 위험이 있어 재리뷰 전에 정리했다
(`_workspace/m0-0a/03_verifier_report_round1.md` L-1·L-2).

| 커밋 | 대응 | 요지 |
| --- | --- | --- |
| `df78c77` | L-1 · L-2 | DEC-03 acceptance의 주어를 기관 유형 → 하한 모델 적용 가능성으로 교체(결정 무관화). OPS-09의 `unknown` 비재시도 기본값이 작성자 판단임을 근거와 함께 명시하고, `OPEN-OPS-01` 행에 정책 질문을 추가해 §12.1 통합 기록과 일치시킴 |

- `head_sha`를 `0dbef95` → `df78c77`로 갱신했다.
- **재리뷰 range는 `3dc7d26...<현재 HEAD>`이며**, 현재 HEAD는 이 scope 갱신 커밋이다.
  이전 라운드와 같은 이유로 `head_sha`는 그 직전 커밋을 가리킨다.
- 지시 범위대로 위 두 곳만 수정했다. 집계는 불변이다 — capability 94, 활성 `OPEN` 65,
  분류 줄 형식 위반 0, `V2 필수` 63건의 사용자 가치·acceptance 결측 0. `checklist.md`와
  `commands.md`는 수치가 그대로여서 갱신하지 않았다.
- verifier L-3~L-6은 미처리다(재검증 판정이 `ready-for-review`이고 지시 범위 밖).

### 2026-08-22 — 수정 라운드 2 (Codex 재리뷰 `request_changes` 대응, spec-writer)

재리뷰(`codex-review-20260822T065525Z.json`, verdict `request_changes`,
blocker 0 / high 4 / medium 1)의 신규 finding을 finding별 커밋으로 반영했다. 라운드 1의
finding 4건은 재리뷰에서 해소로 확인됐다.

| 커밋 | 대응 | 요지 |
| --- | --- | --- |
| `adf584e` | high #1 · #2 | OPS-09 acceptance를 §0.5 조건부 형식으로 전환. OPS-04에서 측정 불가의 초록 변환을 제거하고 정상/임계 초과/측정 불가 3-상태로 분리 |
| `8f3a0b8` | high #3 | NOTI-04 사용자 가치와 acceptance 정렬. 금액-선도착 경로의 가치 공백을 명시하고 재통지 여부를 `OPEN-NOTI-08`로 신설·조건부화 |
| `0b48eaa` | high #4 + evidence | OPS-13 acceptance에 크기 외 결합도 축 6종 추가. 라운드 2 evidence(`commands.md` D1~D5, checklist §8, 리뷰 JSON) |

- `head_sha`를 `df78c77` → `0b48eaa`로 갱신했다.
- **재리뷰 range는 `3dc7d26...<현재 HEAD>`이며**, 현재 HEAD는 이 scope 갱신 커밋이다.
  이전 라운드와 같은 이유로 `head_sha`는 그 직전 커밋을 가리킨다.
- finding #5(strict/정본 verdict 스키마의 `line` 계약 불일치)는 `.claude/skills/` 하위
  하네스 소관이며 이 slice의 in_scope 밖이다. 팀 리드가 별도로 해소했다.
- 산출물 변화: capability 94(불변), 활성 `OPEN` 65 → 66(`OPEN-NOTI-08` 신설),
  `capability-map.md` 2,518 → 2,591줄. 분류 집계 63/14/6/11 불변.

### 2026-08-26 — 수정 라운드 3 (verifier not-ready M-1 대응, spec-writer)

verifier 재검증(`_workspace/m0-0a/04_verifier_report_round2.md`) 판정 `not-ready`.
Codex 라운드 2 finding 4건은 verifier가 전부 충족으로 확인했고, `not-ready` 사유는 M-1
한 건이었다. 사용자가 M-1 + low 3건 전부 수정을 승인해 finding별 커밋으로 반영했다.
이번 라운드에 Codex 리뷰 요청은 없었다.

| 커밋 | 대응 | 요지 |
| --- | --- | --- |
| `f29fa53` | M-1 (medium) | OPS-00 acceptance의 "측정 불가 → 초록 + 사유"를 "정상과 구별되는 측정 불가(중립)"로 교체. 3-상태 정의와 표시 계층 제약의 소유가 OPS-04임을 참조로 명시 — 라운드 2에 Codex high #2로 고친 OPS-04, OPS-21 B-13과 정렬 |
| `fb832dd` | L-1 · L-2 · L-3 + evidence | `OPEN-NOTI-08` 근거 서술을 원본 대조로 정밀화(구현 의도는 docstring에 명시, 없는 것은 운영자 합의 근거). 전수 스윕 정의를 "acceptance 절을 가진 모든 블록"으로 재정의하고 재실행(63 → 67 블록). §0.5에 acceptance 절 제목 형식 3종 규약 추가. checklist §9 · commands E1~E7 |

- `head_sha`를 `0b48eaa` → `fb832dd`로 갱신했다.
- **재리뷰 range는 `3dc7d26...<현재 HEAD>`이며**, 현재 HEAD는 이 scope 갱신 커밋이다.
  이전 라운드와 같은 이유로(커밋이 자기 SHA를 담을 수 없다) `head_sha`는 그 직전 커밋을
  가리키며, 두 커밋의 차이는 이 파일의 `head_sha` 한 줄과 이 절뿐이다.
- **리뷰 range의 in_scope 밖 변경 — 라운드 1 절의 선언을 여기서 갱신한다**(verifier N-4).
  라운드 1 절은 "무관 커밋 `1f8e57c` **하나**, `.claude/` 하위"라고 적었으나 지금은 두
  군데가 부정확하다. 낡은 서술은 감사 추적을 위해 지우지 않고 이 절이 대체한다.

  ```
  $ git diff --name-only 3dc7d26 HEAD | grep -v '^docs/discovery/\|^reports/evidence/'
  .claude/skills/codex-review-gate/SKILL.md
  .claude/skills/codex-review-gate/references/codex-output.strict.schema.json
  .claude/skills/codex-review-gate/references/codex-verdict.schema.json
  CLAUDE.md
  ```

  실제로는 **무관 커밋 2건**(`1f8e57c` harness strict 스키마 추가, `0c7eeff` 정본 verdict
  스키마의 `line` null 허용)이고 **파일 4개**이며, `CLAUDE.md`는 `.claude/` 하위가 아니라
  **저장소 루트**다. 넷 다 이 slice의 `in_scope` 밖이고 **이 slice가 만든 커밋이 아니다** —
  하네스 소관으로 팀 리드가 따로 넣었다. 리뷰는 `docs/discovery/capability-map.md`와
  `reports/evidence/m0/0a/`만 대상으로 한다.

  이 선언이 필요한 이유: 라운드 2 Codex finding #5가 정확히 이 조건에서 나왔다 — range에
  섞인 `codex-output.strict.schema.json`을 읽고 스키마 계약 문제를 지적했고 사후에 "범위
  밖"으로 처리됐다. 이번 range에는 그 finding의 **수정 커밋**(`0c7eeff`)까지 들어 있어
  같은 일이 반복될 조건이 갖춰져 있다.
- 산출물 변화: capability **94 불변**, 분류 **63/14/6/11 불변**, 활성 `OPEN` **66 불변**
  (id 집합 대조 결과 소멸 0 · 신규 0), `capability-map.md` 2,591 → **2,608줄**.
- L-1은 근거 부재의 *서술*만 정밀화했고 `OPEN-NOTI-08`의 판단은 유지했다. 이번 라운드에
  임의로 해소한 `OPEN`은 없다.
- 재정의한 스윕이 새로 편입한 4블록(OPS-00 · STR-11 · STR-15 · NOTI-08) 중 M-1 계열
  잔존은 OPS-00 하나뿐이었고 `f29fa53`에서 처리했다. 신규 발견 0건.
- 미처리 이월: 라운드 1 verifier L-4~L-6(QUAL-03 항목 배치, SET-06 신규 근거의 파일:라인
  부재, checklist A1의 실패 이력 누락)과 라운드 0의 F-5(디렉터리 없는 파일명 표기 규약).
  이번 지시 범위 밖으로 명시됐다. 사유와 권고는 `checklist.md` §7·§9에 있다.

### 2026-08-26 — 라운드 3 evidence 정정 (verifier N-1~N-4, spec-writer)

라운드 3 재검증 판정은 **`ready-for-review`**이고 신규 발견 4건은 전부 low이며 산출물이
아니라 **evidence 기록의 정확도**에 한정된다(`_workspace/m0-0a/05_verifier_report_round3.md`
§6). 리뷰 요청 전에 4건 전부를 한 커밋으로 반영했다.

| 커밋 | 대응 | 요지 |
| --- | --- | --- |
| `bd43fb7` | N-1 · N-2 · N-3 · N-4 | 위 라운드 3 절의 range 선언 갱신(N-4), 라운드 3 절 제목 날짜 2026-08-22 → **2026-08-26**(N-3, `scope.md`·`commands.md`), `checklist.md` 머리말의 작성 이력·줄 수 2,591 → 2,608·라운드 2 리뷰 JSON 열거(N-2), `commands.md` E4 grep 출력을 `head_sha` 기준으로 재실행 교체와 E1의 `:1907` → `:1908`(N-1). `checklist.md` §9.1에 finding별 처리 기록 |

- `head_sha`를 `fb832dd` → `bd43fb7`로 갱신했다.
- **재리뷰 range는 `3dc7d26...<현재 HEAD>`이며**, 현재 HEAD는 이 scope 갱신 커밋이다.
  이전 라운드와 같은 이유로(커밋이 자기 SHA를 담을 수 없다) `head_sha`는 그 직전 커밋을
  가리키며, 두 커밋의 차이는 이 파일의 `head_sha` 한 줄과 이 절뿐이다.
- **`docs/discovery/capability-map.md`는 한 줄도 바뀌지 않았다** — 2,608줄, capability 94,
  분류 63/14/6/11, 활성 OPEN 66 전부 라운드 3 산출물 그대로다. 이 정정은 evidence 3파일에
  한정된다.
- range의 in_scope 밖 변경은 위 라운드 3 절의 선언 그대로다(무관 커밋 2건, 파일 4개).
  이 정정 커밋과 이 scope 갱신 커밋은 `reports/evidence/m0/0a/` 안에만 있다.

### 2026-08-26 — 수정 라운드 4 (Codex 3차 `request_changes` 대응, spec-writer)

3차 리뷰 정본(`codex-review-20260825T235415Z.json`, verdict `request_changes`,
blocker 0 / **high 1**)의 finding을 반영했다. 같은 scope를 유지했다.

| 커밋 | 대응 | 요지 |
| --- | --- | --- |
| `0b0c8aa` | high #1 | STR-08 무조건 acceptance의 전제를 "직전 run이 정상 완료된 경우"로 좁히고, 실패·취소 경로를 조건부 소관으로 이관. `legacy 형태 처리`의 확정 범위를 "억제가 payload 존재라는 부작용으로 정해지는 형태는 `폐기`"까지로 한정하고 run 상태 포함 여부는 조건부로 이관. §0.5 형식대로 대체 시나리오 기입 |
| `23b9c2a` | 신규 스윕 축 + 동일 계열 + evidence | 전수 스윕에 축 **F1**(조건부 보유 블록의 무조건 항목이 같은 OPEN의 쟁점을 확정하는가)·**F2**(같은 블록의 확정 서술 절이 그러한가)를 추가·실행. F2가 **ML-03**에서 동일 계열 위반 1건을 신규 적출해 함께 수정. Codex JSON 2건 append-only 커밋, checklist §10·commands F1~F5 |

- `head_sha`를 `bd43fb7` → `23b9c2a`로 갱신했다.
- **재리뷰 range는 `3dc7d26...<현재 HEAD>`이며**, 현재 HEAD는 이 scope 갱신 커밋이다.
  이전 라운드와 같은 이유로(커밋이 자기 SHA를 담을 수 없다) `head_sha`는 그 직전 커밋을
  가리키며, 두 커밋의 차이는 이 파일의 `head_sha` 한 줄과 이 절뿐이다.
- **`required_fix`는 두 선택지를 줬고 사용자가 첫 번째(제외)를 택했다.** `OPEN-STR-08`은
  해소하지 않았다. `OPEN-ML-03`도 마찬가지다.
- 산출물 변화: capability **94 불변**, 분류 **63/14/6/11 불변**, 활성 `OPEN` **66 불변**
  (`6c6b3a2` 대조 결과 소멸 0 · 신규 0), `capability-map.md` 2,608 → **2,621줄**
  (STR-08 +11, ML-03 +2).
- **리뷰 재현성**: 같은 range·CLI·모델에서 `model_reasoning_effort`만 달라 판정이 갈렸다
  (high `request_changes` / medium `approve`). **두 실행 JSON을 모두 커밋했고 high를 정본으로
  채택했다.** 근거는 `checklist.md` §10.1에 있다. 하네스는 이 발견으로
  `codex-review-gate`에 `effort=high`를 고정했다(`CLAUDE.md` 변경 이력 2026-08-26).
- **리뷰 range의 in_scope 밖 변경**: 라운드 3 절의 선언에 더해, 하네스가 이번에
  `CLAUDE.md` 변경 이력 1행을 추가했다. 무관 커밋과 파일 목록은 리뷰 직전에
  `git diff --name-only 3dc7d26 HEAD`로 재확인해야 한다. 넷 다 이 slice의 in_scope 밖이고
  **이 slice가 만든 커밋이 아니다.**
- 미처리 이월(변동 없음): 라운드 1 verifier L-4~L-6, 라운드 0 F-5. `checklist.md` §7·§9 참조.

### 2026-08-26 — 수정 라운드 5 (verifier `not-ready` H-1·M-1·M-2 대응, spec-writer)

라운드 4 재검증 판정 `not-ready`. Codex high #1(STR-08)에 대한 **수정 이행 자체는 충족**
으로 확인됐고 불변 8종·secret·범위·JSON 무결성도 전부 통과했다. 발견 5건 전부를 처리했다.

| 커밋 | 대응 | 요지 |
| --- | --- | --- |
| `0581e97` | M-1 · M-2 | `OPEN-STR-08`의 쟁점을 선점하는 잔존 2건. NOTI-02 `경계`(교차 블록)를 STR-08 본문과 같은 어법으로 교체, STR-08 `사용자 가치`(절 화이트리스트 밖)에 "직전 run이 정상 완료된 흐름에서" 한정어와 OPEN 참조 추가 |
| `6dee91a` | H-1 · L-2 + 축 보강 + evidence | F1의 "수정 전 적출" 주장을 **실측으로 교체하고 철회**. 스윕 스크립트 전문 기입. F2' 화이트리스트 확장 + **구조적 버그 수정**, X(교차 블록) 축 신설. checklist §11, commands G1~G7 |

- `head_sha`를 `23b9c2a` → `6dee91a`로 갱신했다.
- **재리뷰 range는 `3dc7d26...<현재 HEAD>`이며**, 현재 HEAD는 이 scope 갱신 커밋이다.
  이전 라운드와 같은 이유로(커밋이 자기 SHA를 담을 수 없다) `head_sha`는 그 직전 커밋을
  가리키며, 두 커밋의 차이는 이 파일의 `head_sha` 한 줄과 이 절뿐이다.
- **H-1은 은폐하지 않고 정정했다.** 라운드 4에 적은 "F1이 Codex 정본 finding을 독립
  재현했다"는 **실행된 적 없는 측정**이었다. 같은 스크립트를 수정 전 문서에 실제로 돌린
  결과 **2 / 2**(임계 3 미만, 적출 안 됨)였고 기록된 5는 수정 **후** 값이었다. 주장을
  철회하고 F1의 성격을 triage 필터로 정정했으며, 이 주장에 의존하던 `checklist.md`
  §10.1의 정본 채택 근거 (2)도 철회해 (1)·(3)만으로 재작성했다. **정본이 high라는 결론은
  유지된다.** 상세는 `checklist.md` §11.1, `commands.md` G1.
- **축의 한계를 evidence에 명시했다**(`commands.md` G4, `checklist.md` §11.3). F1·F2'·X는
  전부 대리 지표 기반 **triage 필터**이며 계열 A의 부재를 증명하지 않는다. 적출 0건은
  "임계 위에서 읽을 후보가 없었다"는 뜻이지 "위반이 없다"는 뜻이 아니다.
- 산출물 변화: capability **94 불변**, 분류 **63/14/6/11 불변**, 활성 `OPEN` **66 불변**
  (`744fbfd` 대조 결과 소멸 0 · 신규 0), `capability-map.md` 2,621 → **2,624줄**
  (STR-08 `사용자 가치` +1, NOTI-02 `경계` +2). `OPEN-STR-08`·`OPEN-ML-03` 유지.
- **리뷰 range의 in_scope 밖 변경 — 라운드 3·4 절의 "무관 커밋 2건"을 여기서 갱신한다**
  (verifier L-1). 실제는 **3건**이다 — `1f8e57c`, `0c7eeff`, 그리고 라운드 4 이후 추가된
  `d7b1c10`(codex 호출에 `model_reasoning_effort=high` 고정; `SKILL.md` +9행,
  `CLAUDE.md` +1행). 파일은 여전히 **4개**(`.claude/skills/codex-review-gate/` 3개 +
  저장소 루트 `CLAUDE.md`)로 불변이다. 셋 다 하네스 소관이며 **이 slice가 만든 커밋이
  아니다.** 재확인 명령과 출력은 `commands.md` G7.
- 미처리 이월(변동 없음): 라운드 1 verifier L-4~L-6, 라운드 0 F-5. `checklist.md` §7·§9.
