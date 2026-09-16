# M5 종결 판정 — slice 계약 (문서 slice, 2026-09-16)

```yaml
milestone: M5
slice: closure
base_sha: 8799e0594496593521691a24a29551ee51b6f147   # PR #20(5E-2) 머지 커밋 = main
head_sha: <PR 요청 시점에 기입>
in_scope:
  - reports/evidence/m5/closure/**          # 이 계약 · checklist(종결 판정 정본) · open-inventory(입력 사본)
  - reports/evidence/m5/5a/policy-values.md  # OPEN-5C-5A-TABLE-REASSIGN 처분 — #8·#31·#32·#33 「소비 예정」 4행 + change_history 1행
  - milestone-5.md                           # 「## 완료 조건」 아래 종결 판정 문단
out_of_scope:
  - ml-engine/**, adapters/**, contracts/**  # 코드·계약 무편집 — 종결 판정은 문서다
  - reports/evidence/m5/{5b,5c,5c2,5d,5d2,5d3,5e,5e2,5e3}/**  # 앞 slice 문서는 편집하지 않는다(처분은 checklist 에 등재)
  - 운영자 결정 다섯의 실행(정책 값 변경·Kotlin 한 줄) — 각각 별도 소규모 slice
acceptance_commands:
  - N/A — 문서 slice. 완료 조건 대조는 checklist.md 로 기록(evidence-pack 「문서 slice 의 evidence」)
  - S-10 (evidence 편집 커밋마다, 하네스 2026-09-16): ./gradlew --no-daemon check   # leakPatternGate 가 reports/evidence 를 스캔
rollback: N/A — 문서 산출물은 git revert 로 복구. 5a/policy-values.md 4행은 이 slice 커밋 하나에만 있으므로 그 커밋 revert 로 원복
```

## 이 slice 가 하는 일

① `milestone-5.md` 완료 조건 열 항목을 slice evidence 와 대조해 **충족 / 범위 좁혀진 충족 / 미측정** 으로 판정한다(checklist §1).
② M5 문서에 등장하는 `OPEN-5*` 식별자 47 전부를 **종결 / 이 문서로 종결 / 운영자 결정 대기 / 이월** 로 배타 처분한다(checklist §2). 입력은 읽기 전용 재고 `open-inventory.md`(문면 기준, 추측 없음).
③ 앞 slice 가 「팀장이 정정」으로 남긴 5A 정책 표 배정 4행을 정정한다(`OPEN-5C-5A-TABLE-REASSIGN`).
④ 종결을 막는 운영자 결정 다섯을 선택지·추천과 함께 한 자리에 모은다(checklist §3).

## 이 slice 가 하지 않는 것

- 앞 slice 문서의 낡은 문면을 고치지 않는다(이력 보존) — 정정은 checklist 의 처분 행이 정본이다.
- 운영자 결정을 대신 내리지 않는다. 결정이 나면 각각 별도 slice(정책 값 한 줄 / Kotlin 한 줄)로 실행한다.
- 5E-3(`m5-5e3/2026-09-16`, 병행 진행 중)의 결과를 선취하지 않는다 — 5E-3 이 닫는 OPEN 셋은 「5E-3 병합 조건부」로 적고, 병합 뒤 한 줄로 확정한다.

## 하네스 레인 변경

`git log --oneline 8799e05..HEAD -- CLAUDE.md .claude/` — **없음**(PR 요청 시점에 재확인).

## 계약 갱신 이력

- 2026-09-16 착수: 초판.
