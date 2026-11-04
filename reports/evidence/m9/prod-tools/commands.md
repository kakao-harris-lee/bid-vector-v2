# M9 / prod-tools — 실행 명령과 종료 코드

계열 주의: 여기의 L-1·L-2 는 **7F 장부 v r2** 계열이다(`m7/close/checklist.md` §4). `7f/scope.md`·
`7f2/checklist.md` 의 L-1/L-2 는 다른 계열이고 이미 닫혔다.

## 2026-11-05 — 구문·acceptance

- cmd: `bash -n tools/prod-env.sh tools/prod-deploy.sh tools/prod-rollback.sh tools/prod-notification-sweep.sh tools/prod-guards-check.sh`(파일별)
- exit: 0
- 핵심 결과: 다섯 파일 모두 구문 통과. shellcheck 는 이 host 에 없다(`command -v shellcheck` 공집합) — 돌리지 않았다.

- cmd: `./tools/prod-guards-check.sh`
- exit: 0
- 핵심 결과: `통과 39 · 어긋남 0` — 술어 둘의 양성·음성 대조와 세 스크립트의 거절·통과·trap 삭제 대상.

- cmd: `./tools/prod-env-keys-check.sh`  (CI container job 의 S-27 명령 그대로)
- exit: 0
- 핵심 결과: `등식 — 키 18개 (템플릿 == compose 세 파일)` — 이 slice 가 `prod-env.sh` 를 편집하므로 같은 job 에서 다시 측정.

- cmd: `python3 -c "yaml.safe_load(...)"` 로 `.github/workflows/ci.yml` 파싱 + container job step 이름 열거
- exit: 0
- 핵심 결과: S-27 · S-27b · S-27c · **S-27d**(신규) 네 step 이 그 순서로 등재.

### 돌리지 않은 acceptance 줄과 그 사유

- CI container job 의 **S-27b·S-27c** 는 `--proxy-image bidvector/proxy:local` 로 **실제 docker** 를 쓴다.
  이 레인은 운영 host 를 공유하는 기계에서 돌아 **실제 docker 명령을 쓰지 않는다**(팀장 지시 — 읽기만 하는
  `docker ps` 도 포함). 그래서 두 줄은 이 레인에서 돌리지 않았고, 이 slice 는 그 둘이 보는 파일
  (proxy site·host nginx 조각)을 편집하지 않는다. S-27d 는 가짜 docker 로 돌므로 그 제약과 무관하다.
- Kotlin `check` · `ml-engine` job 은 이 slice 의 in_scope 에 코드가 없어 해당 없다(셸·문서·워크플로뿐).

## 2026-11-05 — 변이 (각각 커밋 뒤 적용, 변이 파일만 `git checkout --` 로 원복)

- 변이 ①: `_prod_git_sha_match` 머리에 `return 0`(술어를 항상 참으로) → `./tools/prod-guards-check.sh` exit 1,
  `통과 25 · 어긋남 14` — 세 호출부(반입 exit 0, 되돌림이 반입까지 흘러가 exit 1, 러너 exit 0)가 모두 붉다.
- 변이 ②: `_prod_oneoff_residue` 머리에 `return 0` → exit 1, `통과 32 · 어긋남 7` — 라벨 축·이름 축·조회 실패
  셋과 러너의 `exit 3`·「`run` 에 닿지 않음」이 붉다.
- 변이 ③: 러너의 `trap '_sweep_on_signal …'` 두 줄 삭제 → exit 1, `통과 36 · 어긋남 3` — 삭제 호출 0 개.
  **주의**: 종료 코드 143 은 그대로였다(bash 의 기본 TERM 동작) — 그래서 「무엇을 지웠는가」를 따로 센다.
- 변이 ④: `compose run` 에서 `--name "$ONEOFF_NAME"` 삭제 → exit 1, `통과 38 · 어긋남 1` — 이름 고정이 깨지면
  trap 이 지울 대상을 접두로 고르게 되는 축.
- 원복 뒤 재실행: `./tools/prod-guards-check.sh` exit 0 (`통과 39 · 어긋남 0`), `git status --porcelain` 빈 출력.

## 2026-11-05 — rollback 실측

`rollback.md` 의 ①~⑥ 을 임시 clone 에서 돌렸다 — 명령과 결과는 그 파일에 한 줄씩. `실측 HEAD` 도 거기 있다.
