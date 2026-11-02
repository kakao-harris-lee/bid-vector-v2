# M7 종결 — 계약

- base: main `0479e1ff` (PR #113 병합) · 브랜치 `docs/m7-close`
- 성격: 문서 slice — 코드·게이트·스키마 변경 0. 운영 반입(별도 승인, 2026-11-02 운영자 확인)의 **기록**과
  runbook 을 실제 운영 모양에 맞춘다.

## in_scope

- `docs/runbook/m7-7f-production.md` — §4.7 구매 인증서(운영 현행) · §11 제한 11(헤어핀 NAT) · §12 운영 계정
- `reports/evidence/m7/close/scope.md` · `reports/evidence/m7/close/checklist.md`

## out_scope

- 코드·compose·tools·게이트 · `milestone-7.md`·`README.md` 단계 표(사용자 release 승인 뒤 별도 커밋)
- 후속 OPEN(아래 checklist 「넘기는 것」) — 이 slice 는 등재만 확인한다

## 결정

- D-CLOSE-1 인증서: certbot·LE 대신 운영자 구매 DV 인증서(GoGetSSL). 감시 임계 30일(수동 갱신 여유).
- D-CLOSE-2 계정 부트스트랩 절차의 정본을 7D checklist 에서 runbook §12 로 옮긴다(7D 가 예고한 자리).
- D-CLOSE-3 외부 경로 확인은 host 밖(운영자 휴대폰 LTE)에서 — host 안 공인 IP 경로는 헤어핀으로 닿지 않는다.

## acceptance

- 문서 slice: Kotlin `check` 는 이 diff 로 바뀌는 입력이 없다 — CI 4 job 초록으로 갈음(PR head).
- `git diff --name-only 0479e1ff..HEAD` ⊆ in_scope.
- 판정: verifier 최종 `ready-for-review`(M7 완료 조건 전건 대조) + code-reviewer(sonnet) 병렬 → 사용자 release 승인.

## rollback

머지 전: `git restore --source=0479e1ff --staged --worktree -- docs/runbook/m7-7f-production.md` + `reports/evidence/m7/close/` 삭제.
머지 뒤: PR revert. 운영 스택 되돌림은 runbook §7.

## 하네스 레인 변경

없음.
