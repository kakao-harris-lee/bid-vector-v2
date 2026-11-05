# M9 / prod-tools — 반입 트리 ↔ manifest 묶기 · 일회 컨테이너 잔존 확인 (7F 장부 v r2 L-1 · L-2)

- base: main `79f3e5c4` · 브랜치 `feature/m9-prod-tools` · worktree `bid-vector-v2-m9-prod-tools`
- 근거: `reports/evidence/m7/close/checklist.md` §4 「7F 장부 v r2 L-1(checkout ↔ manifest `git_sha` 묶기) · L-2(timeout 뒤 일회 컨테이너 확인)」. (주의: `7f/scope.md`·`7f2/checklist.md` 의 L-1/L-2 는 다른 번호 계열이고 이미 닫혔다 — evidence 에 계열을 적는다.)
- 사용자 지시 2026-11-05(전체 진행, 추천안). **운영 host 에서 실행하지 않는다** — 저장소 스크립트·문서와 로컬 검증만.

## 결정

| ID | 결정 |
| --- | --- |
| D-1 | **L-1**: `tools/prod-env.sh` 에 공용 술어 `_prod_git_sha_match <manifest>` — 지금 checkout 의 `git rev-parse HEAD` 와 manifest `git_sha` 가 같고, 추적 파일이 깨끗할 때만 참(`prod-release.sh` 의 더티 검사와 같은 규칙). 기존 `_prod_images_match` 와 같은 자리·같은 오류 변수 관례(`_PROD_*_ERR`). `prod-deploy.sh`·`prod-rollback.sh`·`prod-notification-sweep.sh` 가 부른다 — 불일치면 반입 `exit 1`, 러너 `exit 3`(기존 종료 코드 체계를 runbook §9.4 와 대조해 맞춘다). rollback 은 「되돌릴 릴리스의 manifest」 와 대조 — 되돌림이 그 릴리스 커밋의 checkout 을 요구하게 되는지 실측하고, 요구하면 runbook 절차에 `git checkout <sha>` 단계를 적는다(스크립트가 checkout 을 대신하지 않는다). |
| D-2 | **L-2**: `prod-notification-sweep.sh` 시작 전 같은 compose 프로젝트의 일회 컨테이너(`<project>-app-run-*`) 잔존이 0 이 아니면 `exit 3` + 안내. 실행 중 신호(TERM/INT)로 끝나면 `trap` 이 자기 일회 컨테이너를 치운다(이름을 `--name` 으로 고정해 자기 것만 — 남의 것을 지우지 않는다). systemd 예시 unit(runbook)에 `ExecStopPost=` 정리 경로. |
| D-3 | runbook `docs/runbook/m7-7f-production.md` 의 해당 절(반입·되돌림·러너·종료 코드 목록)을 같은 커밋 계열에서 갱신. §12 운영자 계정 블록처럼 새 술어를 쓰는 자리가 더 있으면 함께. |
| D-4 | 검증은 **가짜 docker/git**(PATH 앞 shim 디렉터리)로 — 실제 compose 를 띄우지 않는다. 테스트 스크립트는 기존 CI 의 `tools/prod-*-check.sh` 자리 관례를 따르고 CI 에 걸 가치가 있으면 `ci.yml` 의 그 job 에 한 줄(그 경우 in_scope 에 `ci.yml` 포함을 evidence 에 선언). `bash -n` + shellcheck(있으면). |

## in_scope

- `tools/prod-env.sh` · `tools/prod-deploy.sh` · `tools/prod-rollback.sh` · `tools/prod-notification-sweep.sh` · 새 `tools/prod-*-check.sh`(검증 스크립트) 와 그 fixture(`tools/testdata/**` 등)
- `docs/runbook/m7-7f-production.md` · `.github/workflows/ci.yml`(검증 한 줄을 걸 때만)
- `reports/evidence/m9/prod-tools/**`

## out_scope

운영 host 실행·systemd 설치 · `prod-release.sh` 의 manifest 형식 변경(필요하면 멈추고 보고) · Kotlin.

## acceptance

- 새 검증 스크립트 exit 0 + 각 술어의 양성 대조(불일치 manifest · 더티 트리 · 잔존 컨테이너 · trap 경로가 남의 컨테이너를 안 지움) 각 한 줄.
- CI 의 해당 job 명령(`tools/prod-env-keys-check.sh` 등이 있는 job)을 로컬에서 그대로 — docker 이미지가 필요한 줄은 이미지가 있으면 돌리고, 없으면 그 사실을 적는다.
- 변이: 술어를 항상 참으로 · trap 제거 — 각각 검증 스크립트 RED.

## rollback

`git restore --source=79f3e5c4 --staged --worktree -- <in_scope 경로>` + 새 파일 삭제; 목록 기계 산출(`rollback.md`).

## 하네스 레인 변경

없음.
