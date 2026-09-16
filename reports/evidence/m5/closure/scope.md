# M5 종결 판정 — slice 계약 (문서 slice, 2026-09-16)

```yaml
milestone: M5
slice: closure
base_sha: 91f6acb   # PR #25(5F-2) 머지 커밋 = main — 계약 갱신 (4) 로 두 번째 rebase(초판 base PR #20 `8799e05` → (2) PR #21 `845e29b` → (4) 여기). PR range 는 91f6acb..HEAD(이 slice 커밋만)
head_sha: <PR 요청 시점에 기입>
in_scope:
  - reports/evidence/m5/closure/**          # 이 계약 · checklist(종결 판정 정본) · open-inventory(입력 사본)
  - reports/evidence/m5/5a/policy-values.md  # OPEN-5C-5A-TABLE-REASSIGN 처분 — #8·#31·#32·#33 「소비 예정」 4행 + change_history 1행
  - reports/evidence/m5/5c2/policy-values.md # 표제 「승인 대기」→「승인」 한 줄 — 운영자 결정 ④ 등재(계약 갱신 (2)), OPEN-5C2-POLICY-VALUES 종결
  - reports/evidence/m5/5e/policy-values.md  # 표제 「승인 대기」→「잠정 승인(6C/6E 실측 재승인)」 한 줄 — 운영자 결정 ⑤ 등재(계약 갱신 (2)), OPEN-5E-POLICY-VALUES 종결
  - milestone-5.md                           # 「## 완료 조건」 아래 종결 판정 문단
out_of_scope:
  - ml-engine/**, adapters/**, contracts/**  # 코드·계약 무편집 — 종결 판정은 문서다
  - reports/evidence/m5/{5b,5c,5d,5d2,5d3,5e2,5e3}/**  # 앞 slice 문서는 편집하지 않는다(처분은 checklist 에 등재). 5a·5c2·5e 는 각각 policy-values.md 한 파일만 in_scope(승인 문면 정본 자리), 나머지 파일은 무편집
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

`git log --oneline 91f6acb..HEAD -- CLAUDE.md .claude/` — **없음**(두 번째 rebase 뒤 base 기준, PR 요청 시점 재확인).

## 계약 갱신 이력

- 2026-09-16 착수: 초판.
- 2026-09-16 (2) 5E-3 병합(PR #21) 뒤: base 를 `845e29b` 위로 rebase(충돌 0, 커밋 해시 변경 — checklist §4 선언) · 운영자 결정 다섯 수령 · ④⑤ 실행을 이 slice 에 포함(in_scope 에 `5c2/policy-values.md`·`5e/policy-values.md` 표제 한 줄씩 — 승인 문면의 정본 자리가 그 표제라 여기서 적는다) · ①③ → 5F-1, ② → 5F-2 별도 slice · 판정 「조건부 종결」→「종결(결정 완료·실행 slice 둘 대기)」. verifier r1 ready-for-review 뒤 장부층 일괄은 `9bb51bf`(rebase 전 `e3c61d4`).
- 2026-09-16 (4) 5F-1(PR #24)·5F-2(PR #25) 병합 뒤: base 를 `91f6acb` 위로 두 번째 rebase(충돌 0, 해시 재변경 — checklist §4 선언) · §2.4 「확정 — 셋 전부 종결」 · 머리에 「최종 — 무조건 종결(종결 33·이월 14)」 · §2.5 후속 소폭 셋 추가(5F-1 로더 불변식 · golden 어댑터/Python 미러/schema 선언 셋) · `milestone-5.md` 에 5F-1·5F-2 병합 문단 + 「M5 종결」 문단. 판정 「종결(결정 완료·실행 slice 둘 대기)」→ **「무조건 종결」**.
- 2026-09-16 (3) verifier r2 ready-for-review 장부층 일괄(MEDIUM 4·LOW 3): base_sha·하네스 명령 범위를 rebase 뒤 `845e29b` 로 · out_of_scope 글롭에서 5c2·5e 제외(policy-values 한 파일만 in_scope) · 두 policy-values H1 표제도 「승인」/「잠정 승인」으로(지위 줄만 고쳐 H1 과 어긋났었다) + change_history 승인 행 · checklist §3 ③ 확정 값 `10` 명시(후보 표 (a) 40 과 선택지 (a) 의 기호 충돌 해소) · §2.5 에 MEDIUM-2 후속 행 · 계수 30/3/14 를 판정 정본에 · §3 결정 전 문단을 작성 시점 기록으로 표시.
