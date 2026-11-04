# M9 / prod-tools — checklist (L-1 반입 트리↔manifest · L-2 일회 컨테이너 잔존)

계열: **7F 장부 v r2** 의 L-1·L-2(`m7/close/checklist.md` §4). `7f/scope.md`·`7f2/checklist.md` 의
L-1/L-2 는 다른 계열이고 이미 닫혔다.

## D-1 (L-1) — 반입 트리를 manifest 에 묶는다

| 축 | 내용 |
| --- | --- |
| 무엇이 열려 있었나 | manifest 는 **이미지 ID** 만 묶었다. compose 파일 셋·포트·볼륨 이름·`mem_limit`·헬스체크와 `prod-*.sh` 자신은 **checkout 에서** 읽힌다 — 다른 커밋의 트리에서 반입하면 「release 의 이미지」가 「다른 커밋의 배선」으로 뜬다. 러너는 주기마다 그렇게 된다(사본이 아니라 저장소에서 돈다). |
| 술어 | `tools/prod-env.sh` 의 `_prod_git_sha_match <manifest>` — HEAD == manifest `git_sha` **그리고** 추적 파일 변경 0. 자리·오류 변수 관례는 `_prod_images_match` 와 같다(`_PROD_GIT_SHA_ERR`). 트리의 자리는 `BIDVECTOR_PROD_REPO_ROOT`(cwd 에 기대지 않는다). |
| 호출부와 종료 코드 | 반입 `exit 1`(preflight 축) · 되돌림 `exit 2`(사용법·판정 불가 축 — 3 은 마이그레이션 멈춤이 이미 쓴다) · 러너 `exit 3`(전제 실패) · runbook §12 운영자 명령 `exit 3`. 셋 다 기존 체계와 runbook §9.4 목록에 맞춘다. |
| 되돌림이 checkout 을 요구하는가 | **요구한다**(실측: 되돌릴 release 의 manifest 와 대조하므로 새 release 의 트리에서는 exit 2). 그래서 **스크립트가 checkout 을 대신하지 않고** runbook §7.1·§7.2 에 단계를 적었다 — dry-run 의 마지막 줄이 필요한 커밋을 보인다. 되돌린 뒤 트리를 그 커밋에 두는 것이 §9 러너의 전제와도 맞는다(그러지 않으면 다음 주기부터 `exit 3`). |
| 부작용 하나 | checkout 뒤에는 **그 release 와 함께 나간 판의 스크립트**가 돈다(그것이 그 release 의 배선이다). HEAD 가 이미 그 커밋이므로 옛 판에 이 가드가 없어도 불변식은 깨지지 않는다. |

## D-2 (L-2) — 일회 컨테이너 잔존과 신호 경로

| 축 | 내용 |
| --- | --- |
| 무엇이 열려 있었나 | `compose run --rm` 은 **신호로 죽으면** 치우지 못한다(systemd `TimeoutStartSec` 상한이 대표 경우). 운영 DB 자격을 받은 컨테이너가 남고, 다음 run 은 그것을 보지 않았다. |
| 잔존 술어 | `_prod_oneoff_residue <프로젝트>` — `docker ps -a` 를 **프로젝트·서비스 라벨**로 좁히고, 일회 판정은 **합집합**이다: ⓐ `com.docker.compose.oneoff` 라벨 ⓑ 이름 `<프로젝트>-app-run-…`. 교집합이면 라벨 표기나 이름 규칙 한쪽이 바뀌는 날 술어가 **조용히 0** 이 된다. 조회 실패를 「잔존 0」으로 읽지 않는다. |
| 러너 | 시작 전(해석·설정 통로 뒤, `run` 앞) 잔존 ≠ 0 → `exit 3` + 치울 이름·명령. 멈춘 컨테이너까지 센다. |
| 자기 것만 치운다 | 일회 컨테이너 이름을 `--name <프로젝트>-app-run-sweep` 로 **고정**하고, TERM·INT trap 이 그 이름 하나를 `docker rm -f` 한다. 접두·필터로 지우지 않는다 — 그러면 운영자가 §12 로 띄운 일회 컨테이너까지 집는다. 치운 뒤 **그 신호로 죽는다**(종료 코드를 꾸미지 않는다). |
| 겹 하나 더 | runbook §9.2 unit 에 `ExecStopPost=-/usr/bin/docker rm -f bidvector-v2-prod-app-run-sweep`(KILL 로 trap 이 못 돈 경우). `-` 접두라 「없으면」이 실패로 세지 않는다. |

## 측정 (D-4) — 가짜 docker·git

`tools/prod-guards-check.sh` 39 단언, exit 0. **실제 docker·운영 host·컨테이너에 닿지 않는다** —
PATH 앞 shim(`mktemp -d` 아래)과 fixture 상태 디렉터리로만 돈다. 결과 한 줄은 `commands.md`.

- 술어 양성: 같은 커밋·깨끗한 트리 통과 · 잔존 0 통과 · 떠 있는 서비스 컨테이너는 잔존이 아니다 ·
  남의 프로젝트 일회 컨테이너는 세지 않는다.
- 술어 음성: 다른 커밋 · 더티 트리 · manifest 에 `git_sha` 없음 · HEAD 를 못 읽음 · 라벨 축 · 이름 축 ·
  `docker ps` 실패.
- 세 스크립트 거동: 반입이 트리 축에서 `exit 1` 하고 **compose 해석 앞에서** 끝난다 · 되돌림이 `exit 2` 로
  필요한 커밋을 보이고 **반입 호출 앞에서** 끝난다 · 그 커밋이면 통과해 반입으로 넘어가고 반입 쪽 트리 축도
  통과한다 · 러너가 트리·잔존 각각에서 `exit 3` 하고 **`run` 에 닿지 않는다** · 통과하면
  `--rm --pull never --name <프로젝트>-app-run-sweep` 으로 띄운다.
- 신호 경로: `setsid` 로 프로세스 그룹을 만들어 TERM 을 그룹에 보낸다 → exit 143 · trap 의 삭제 호출이
  **하나** 이고 대상이 자기 이름 · 남의 컨테이너 이름은 로그에 없다 · 접두·필터 형태가 아니다.
- 변이 넷(술어 둘을 항상 참으로 · trap 삭제 · `--name` 삭제) 전부 RED — `commands.md` 에 어긋남 수.
- 잠금만 가짜가 없다(고정 절대 경로가 설계다). 검사는 **검사 전용 프로젝트 이름**으로 잡고 끝에 지운다 —
  운영 프로젝트의 잠금 파일은 건드리지 않는다. 자리를 쓸 수 없으면 통과가 아니라 exit 2(판정 불가)이고,
  CI step 이 그 자리를 `install -d` 로 먼저 만든다(runbook §1.4 tmpfiles.d 와 같은 것).

## in_scope 대조 (수정 라운드가 만든 새 파일 포함)

`git log --no-merges --format= --name-only 79f3e5c4..HEAD | sort -u` = 여덟 경로이고 scope.md 의
in_scope 안이다(`tools/prod-{env,deploy,rollback,notification-sweep}.sh` · 새 `tools/prod-guards-check.sh` ·
`docs/runbook/m7-7f-production.md` · `.github/workflows/ci.yml` · `reports/evidence/m9/prod-tools/**`).
`ci.yml` 포함을 **선언한다** — D-4 가 「걸 가치가 있으면」으로 둔 조건을 택했다(한 번만 로컬에서 돈 검사는
뒤따르는 편집을 막지 못한다). `tools/testdata/**` 는 쓰지 않았다 — fixture 를 런타임에 `mktemp -d` 아래
만드므로 저장소에 고정 fixture 가 필요 없다. out_scope 는 지켰다: 운영 host 실행 0 · systemd 설치 0 ·
`prod-release.sh` 의 manifest 형식 변경 0(`git_sha` 는 **이미 있던 줄**을 읽는다) · Kotlin 0.

## 알려진 제한

1. **되돌림이 사람의 `git checkout` 한 단계를 요구한다**(§7.1 — 가장 급한 순간에도). 스크립트가 대신하면
   운영자가 보지 않은 트리 변경이 되돌림에 섞인다.
2. 트리 등식은 **커밋**만 본다 — 같은 커밋의 서로 다른 두 checkout(다른 worktree)은 구별되지 않는다.
   러너가 `<저장소>` 하나에서 돈다는 runbook §9.1 의 전제가 그 자리를 지킨다.
3. 더티 판정은 release 와 같은 규칙(`--untracked-files=no`)이라 **추적되지 않는 파일을 세지 않는다**
   (CR L-10 의 한계를 그대로 물려받는다 — 재현 가능한 provenance 는 깨끗한 clone, runbook §2).
4. 잔존이 남으면 **치울 때까지 주기 훑기가 멈춘다**(러너는 남의 것을 지우지 않는다). 외부 발송이 없어
   journal 이 유일한 창구다 — runbook §11 제한 13 에 등재.
5. `oneoff` 라벨·`-app-run-` 이름 **둘 다** 아닌 일회 컨테이너(`docker run` 수동 기동)는 세지 않는다 —
   「같은 compose 프로젝트의 일회 컨테이너」가 술어의 경계다.
6. 신호 경로의 실측은 `setsid` + 그룹 kill 로 systemd 의 cgroup kill 을 흉내낸 것이다. 그룹 kill 이 안 되는
   환경에서는 가짜 run 이 끝난 뒤 trap 이 도는데, **무엇을 지우는가**는 같은 값으로 잰다.
7. 실제 docker 로 도는 acceptance 줄(S-27b·S-27c)은 이 레인에서 돌리지 않았다 — 운영 host 공유 기계의
   금지 때문이고, 이 slice 는 그 둘이 보는 파일을 편집하지 않는다(`commands.md`).
