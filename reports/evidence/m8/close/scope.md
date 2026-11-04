# M8 종결 — 귀결 이행(8G)과 종결 판정 · 계약 (2026-11-04)

- base: main `2d3687f5`(PR #120 = 8A 머지) · 브랜치 `docs/m8-close`
- 성격: 문서 slice — 코드·정책·게이트·스키마 변경 0 · 외부 effect 0.
- 근거: 사용자 결정 2026-11-04(AskUserQuestion 답, 축어) — ML 방향 **「판정 불가 확정·규칙 유지」** · 8A 「승인·머지」.
  사용자 지시 2026-11-04 「M8 종결까지 이어서 진행해」.

## 무엇을 하는가

8A 가 사전 등록 단위로 판정했다: 승인 2,574 · 창 16개 67~207 · ≥483 창 0 · 선택 0 < 3 → `NotEvaluable(UNDERPOWERED)`.
사용자가 그 귀결을 「판정 불가 확정 · 규칙 유지」로 정했다. 이 slice 는 `milestone-8.md` 「귀결」 표의 `NotEvaluable` 행을 이행하고
M8 을 닫는다.

1. **판정 기록** — 계획 T10 의 자리 `reports/evidence/m6/6g/verdict.md` 에 집계만 적는다: 판정 `NotEvaluable(UNDERPOWERED)` ·
   부족한 창 수(선택 0 / 필요 3) · 창 크기 범위 · 필요 표본 수(창당 483) · 승인 폭포 요약 · 정책 version·checksum · 코드 SHA ·
   표본 출처. **전략 채점·낙찰 여부·McNemar 수치는 없다**(실행하지 않았다 — 판정 불가는 실행 전 관문에서 났다).
2. **6G 종결** — `milestone-6.md` 6G 절에 종결 문단 하나(운영자 결정 2026-10-03 이 정한 자리).
3. **선행 조사 후속 절** — `docs/discovery/bid-price-predictability-2026-09-27.md` 「권고와 결정 이력」 절에 2026-11-04 결정 한 항목
   (계획 T10 「선행 조사 문서의 후속 절에서 다음 중 하나를 기록」).
4. **연장 제안** — 귀결 표 `NotEvaluable` 행 문면(「부족한 창·불일치 쌍·필요 표본 수를 근거로 수집 연장을 제안 — 실패로 바꾸지
   않는다」)대로 수치 제안을 verdict.md 에 둔다. **사용자 결정으로 실행하지 않는다** — 제안은 기록이고 승인이 아니다.
5. **M8 종결 판정** — `reports/evidence/m8/close/checklist.md` 에 완료 조건 대조, `milestone-8.md` 종결 문단, README 단계 표 M8 행.

## 완료 조건 대조 규칙

- 8A: PR #120 으로 충족.
- 8B · 8C~8E · 8F: 판정 실행이 사전 등록 관문(표본)에서 멈췄다 — 정책 고정·스냅숏·백테스트·비용 판정은 **실행할 대상이 없다**.
  각 조건을 「해당 없음(사용자 결정 2026-11-04 인용)」 또는 「좁혀짐」으로 가르고 사유를 적는다. `verdict.md` 에 `NotEvaluable`
  이 있다는 것은 8C~8E 조건 문면(「판정은 Passed·Failed·NotEvaluable 중 하나」)의 판정 부분을 채운다.
- 8G: 귀결 표 `NotEvaluable` 행 — 제안 기록(4) + ML 미배선 유지 + 규칙 기반 투찰가 유지로 이행.
- 화면 C 의 3안 표시 문면(「아직 산출 안 함」)은 **바꾸지 않는다** — 귀결 표가 문면 교체를 요구하는 것은 `Failed` 행이다.

## in_scope

- `reports/evidence/m8/close/**` · `reports/evidence/m6/6g/verdict.md`(신설)
- `milestone-8.md`(종결 문단) · `milestone-6.md`(6G 종결 문단) · `README.md`(M8 행)
- `docs/discovery/bid-price-predictability-2026-09-27.md`(결정 이력 한 항목)

## out_scope

- 코드 · 정책 파일 · 화면 · capability-map · ml-serving 배선 · 수집 · 새 호출.

## acceptance

- CI 4 job 초록(PR head).
- `git log --no-merges --format= --name-only 2d3687f5..HEAD ^origin/main | sort -u` ⊆ in_scope.
- `command grep -rniE -f config/quality/leak-patterns.txt` 를 in_scope 문서에 — 새 줄 0 · 식별자 0.
- verifier `ready-for-review` + code-reviewer(sonnet) 병렬 → 사용자 승인.

## rollback

`git restore --source=2d3687f5 --staged --worktree -- milestone-8.md milestone-6.md README.md docs/discovery/bid-price-predictability-2026-09-27.md`
+ `reports/evidence/m8/close/` · `reports/evidence/m6/6g/verdict.md` 삭제.

## 하네스 레인 변경

없음.
