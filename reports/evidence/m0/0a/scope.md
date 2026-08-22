# Slice 계약 — M0 / 0A capability map

```yaml
milestone: m0
slice: 0a-capability-map
base_sha: 3dc7d26333e9f3699c3fd1149651fe54500b6f27
head_sha: df78c77c11b3270abc12ed2a174c6bc247f03466  # 재리뷰 전 최종 산출물 커밋. 아래 갱신 이력 참조
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
