# Slice 계약 — M0 / 0A capability map

```yaml
milestone: m0
slice: 0a-capability-map
base_sha: 3dc7d26333e9f3699c3fd1149651fe54500b6f27
head_sha: <리뷰 요청 시점에 기입>
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
