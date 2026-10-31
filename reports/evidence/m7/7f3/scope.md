# M7/7F-3 — 운영 반입 승인 전 안전 후속 (착수 계약, 2026-10-31, 팀장)

- **근거**: PR #112(7F 저장소 쪽) verifier r4 의 MEDIUM 셋 — 조치 코멘트가 「운영 반입(`--apply`) 승인 전 반드시 닫을 후속」으로 등재. 운영자 지시
  2026-10-31 「7F 착수」의 연장(7F 본편 안의 작은 slice).
- **base**: main **`78b7f384`**(PR #112 머지) · worktree `bid-vector-v2-m7-7f3` · 브랜치 `m7-7f3/2026-10-31`. rollback 기준 `78b7f384`.
- **경계**: 외부 effect 0 — DNS · ACME 실발급 · 운영 host 반입 apply · public 포트 개방 금지. `:local` 태그 · 6G 개발 DB(`bid-vector-v2-dev`) ·
  host `/etc/nginx` 무접촉.
- **레인**: 구현 하나(`kotlin-implementer`). 판정 verifier 표적 + code-reviewer. 반입 안전 경로라 verifier 재검증 생략 없음.

## 범위 (PR #112 r4 지적 그대로)

| ID | 고칠 것 |
| --- | --- |
| **V4-1** | 프로젝트 잠금 파일 경로가 `${TMPDIR:-/tmp}` 에 의존 — 환경변수에서 오지 않는 **고정 절대 경로**(상태 디렉터리 기준 또는 runbook 이 정한 운영 자리). `TMPDIR` 만 바꾼 동시 반입 재현 RED → GREEN. systemd `PrivateTmp` 영향도. |
| **V4-2** | 알림 러너(`tools/prod-notification-sweep.sh`)가 태그 이미지 ID 를 manifest 와 대조하지 않고 `--pull` 정책이 기본(missing) — deploy 와 같은 manifest 대조 + `compose run --pull never`. 태그를 옮긴 재현 RED → GREEN(레지스트리 당김 경로는 실행하지 말고 `--pull never` 로 닫힘을 보인다). |
| **V4-3** | 「app DB == 가드 DB」 가 JDBC URL 마지막 조각만 비교 — URL **전체**(호스트·포트·DB·쿼리 매개변수, 특히 `currentSchema`)를 기대 형태 `jdbc:postgresql://postgres:5432/${PG_DB}` 와 비교하거나 가드가 같은 schema 를 읽게. 다른 호스트 · `?currentSchema=shadow` 재현 RED → GREEN. |
| **LOW** | V4-4 손상된 `current` 에서 러너 exit 1 → 전제 실패 3 · V4-5 runbook §9.4 종료 코드 표에 3 · V4-6 rollback 의 잠금 해제→exec 틈 · V4-7 러너가 잠금을 잡지 않음 — 고치거나 등재. |

## in_scope

- `tools/prod-*.sh` · `docker/prod.env.template`(필요 시) · `docs/runbook/m7-7f-production.md` · CI container step 의 관련 게이트(필요 시) · `reports/evidence/m7/7f3/**`.

## out_scope

- 이미지·위생/취약점 정책 변경 · Kotlin/web 코드 · 실제 반입.

## acceptance

- `ci.yml` container job 의 관련 step(S-27~S-27c) + 반입·되돌림 리허설(deploy/rollback 경로 — 이미지·정책 무변경이면 release 빌드·취약점 게이트 생략 가능, 사유 기록) + `leakPatternGate`.

## rollback

- in_scope 경로 한정 `git restore --source=78b7f384 --staged --worktree --` + 새 파일 삭제, 목록 기계 산출, 임시 clone ①~⑥, `실측 HEAD` 기록.

## 하네스 레인 변경

- 없음.

## 계약 갱신 이력

- 2026-10-31 착수(팀장).
