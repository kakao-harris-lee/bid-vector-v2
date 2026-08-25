# Slice 계약 — M0 / 0A capability map

```yaml
milestone: m0
slice: 0a-capability-map
base_sha: 3dc7d26333e9f3699c3fd1149651fe54500b6f27
head_sha: fb832dd4251597ed064f0e7e776cda5f2aa2a95a  # 수정 라운드 3 최종 산출물 커밋. 아래 갱신 이력 참조
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

### 2026-08-22 — 수정 라운드 3 (verifier not-ready M-1 대응, spec-writer)

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
- 산출물 변화: capability **94 불변**, 분류 **63/14/6/11 불변**, 활성 `OPEN` **66 불변**
  (id 집합 대조 결과 소멸 0 · 신규 0), `capability-map.md` 2,591 → **2,608줄**.
- L-1은 근거 부재의 *서술*만 정밀화했고 `OPEN-NOTI-08`의 판단은 유지했다. 이번 라운드에
  임의로 해소한 `OPEN`은 없다.
- 재정의한 스윕이 새로 편입한 4블록(OPS-00 · STR-11 · STR-15 · NOTI-08) 중 M-1 계열
  잔존은 OPS-00 하나뿐이었고 `f29fa53`에서 처리했다. 신규 발견 0건.
- 미처리 이월: 라운드 1 verifier L-4~L-6(QUAL-03 항목 배치, SET-06 신규 근거의 파일:라인
  부재, checklist A1의 실패 이력 누락)과 라운드 0의 F-5(디렉터리 없는 파일명 표기 규약).
  이번 지시 범위 밖으로 명시됐다. 사유와 권고는 `checklist.md` §7·§9에 있다.
