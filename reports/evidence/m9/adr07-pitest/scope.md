# M9 / adr07-pitest — mutation 도구 채택 (OPEN-ADR-07)
- base: main `df4976cc` · 공통 규율: `/tmp/claude-1000/-home-deploy-project-bid-vector-v2/b843a5c1-93ae-42d9-82cf-0a5222fc9820/scratchpad/m9-lane-rules.md`
- 사용자 지시 2026-11-06: 「남은 묶음도 지금 같이 병렬로」(2026-11-05 「남은 항목이 없도록 전체 병렬」의 연속). 결정 지점은 추천안으로 진행(전역 규칙), 외부 비용·운영 결정이 필요한 축은 만들지 않고 등재.
- rollback: 공통 규율대로(원천 `df4976cc`, 내 커밋만, 결과 트리 동일성). 하네스 레인 변경: 계약이 명시한 것 외 없음.
- 근거: `docs/adr/0007-test-pyramid-and-ratchet.md` §5 「`OPEN-ADR-07` · mutation 도구」 · M6 종결 §3 ②(표적 변이 갈음은 승인됐고 도구 채택은 후속). 좌표는 `gradle/libs.versions.toml` 에 이미 있다(`pitest-plugin`·`pitest` — 적용 시 `pitestVersion` 명시 필수).

## 결정
- D-1 pitest 를 **순수 도메인 모듈 한정**(예: `domain`·`strategy`·`qualification`·`settlement`·`decision` 중 빌드 비용 실측으로 고름)으로 켠다. **`check` 에 결합하지 않는다** — 별도 task + **별도 CI job**(PR 마다 vs main 만 vs 수동은 비용 실측 뒤 추천안으로).
- D-2 임계는 **절대값 래칫 파일**(`config/quality/` — baseline 이 아니라 모듈별 최소 mutation score; 첫 값은 실측에서, 지어내지 않는다).
- D-3 첫 산출은 **비용 실측**(모듈별 시간·메모리 — 호스트 메모리 규율 준수, 한 모듈씩).
- D-4 ADR 0007 §5 를 결정으로 갱신(OPEN 닫음).

## in_scope
`gradle/libs.versions.toml` · `build-logic/**`(새 convention) · 대상 모듈 `build.gradle.kts` · `.github/workflows/ci.yml`(새 job 만 — 기존 job 무편집) · `config/quality/**` · `docs/adr/0007-test-pyramid-and-ratchet.md` · `docs/discovery/capability-map.md`(**계약 갱신 2026-11-06** — 아래) · evidence

## 계약 갱신
- **2026-11-06 · `docs/discovery/capability-map.md` 추가(팀장 승인).** §12 OPEN 표의 `OPEN-ADR-07` **한 행만** `OPEN-ADR-08` 선례 꼴로 갱신한다(취소선 + 「닫혔다 — M9/adr07-pitest, 2026-11-06」 + 해소 전문은 ADR 0007 §5 가 갖는다는 포인터). 사유: ADR 에서 OPEN 을 닫고 registry 행을 그대로 두면 두 자리가 어긋나고, 그것이 ADR 0007 머리말이 스스로 지적하는 실패(「문면이 사실을 따라가지 않았다」)와 같은 형태다. 이 파일은 **공유 파일**이라 산출물 커밋을 따로 두고 rollback 목록 갱신 커밋과 나눈다.
- `reports/evidence/m6/closure/open-inventory.md` 는 **고치지 않는다** — 그 당시의 사실 기록이다(팀장 확인).
## acceptance
pitest task 가 대상 모듈에서 돌아 리포트 산출 · 래칫 위반(임계 위로 올린 값) → 실패 양성 대조 · CI 새 job 이 PR 에서 돈다(PR CI 로 확인). 로컬 pitest 는 **한 모듈씩만**, 실행 전 메모리 확인.
