# M7/7F 본편 — 실행 명령과 종료 코드

**acceptance 정의**: `.github/workflows/ci.yml` 의 `container` job 명령 그대로. 이 slice 는 그 job 끝에 S-27·S-27b·S-27c 를 더했다.
`check` job 입력은 evidence(`leakPatternGate`) 하나만 닿는다. `web`·`ml-engine` job 입력 변경 0.
**이번에는 그 job 의 컨테이너 축을 실제로 돌렸다** — `:local` 태그를 덮는 문제가 사라졌기 때문이다(checklist 1.6 ②).
돌리지 않은 것은 app·ml-serving 이미지를 **굽는** step 둘뿐이고, 그 둘의 이미지는 다른 레인이 남긴 것을 그대로 썼다(제한 ⑪).
마지막 HEAD 의 게이트 정본은 verifier 와 PR 조치 코멘트다.

호스트 규칙: 컨테이너·빌드 명령은 전부 빌드 자리 진입점(자리 둘 · 획득 순간 메모리 재확인) 아래에서, 확인 셋(pgrep·free·ps)을
별도 호출로 먼저 돌린 뒤 실행했다. 다른 레인이 자리 1 에서 Gradle `check` 를 도는 동안 자리 2 를 썼다.

## 병합 (새 base main `957587c4`)

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `git merge-tree --write-tree HEAD origin/main`(병합 전 예측, worktree 무변경) | 1 | 실충돌 **1**(`docker/proxy.Dockerfile.dockerignore`) · 자동 병합 4 |
| `git merge origin/main` | 1 → 해소 후 커밋 | 같은 충돌 1. 합집합으로 해소(설정 파일 셋 + caddy 빌드 입력 셋) |
| 자동 병합 넷의 본문 검사(병합 트리에서 직접) | — | ci.yml: 내 step 셋이 끝에 붙고 **라벨 중복 0**(병합 결과의 중복 둘은 main 에 이미 있던 job 간 중복) · 위생 정책: 서로 다른 키 · Dockerfile: 다른 자리 · 6E: 한 줄 |
| `git diff --name-status 957587c4..HEAD` | — | 내 경로 **29**(산출물 25 + evidence 4) — in_scope 와 같다, 새 파일 0 |

## 정합 재측정

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `docker/compose.yaml` base↔main 비교 | — | **변경 0** — 서비스 집합 동일 |
| 두 compose 의 `${VAR}` 참조 집합 base↔main | — | **차이 0** |
| `./tools/prod-env-keys-check.sh` (S-27) | 0 | 키 **18** 등식(병합 뒤에도) |
| `./tools/workflow-step-refs-check.sh .github/workflows/ci.yml` | 0 | job 4 · id 15 · 참조 18 · 매달린 참조 0 |
| 마이그레이션 수 | — | 18 → **21** |
| app 이미지의 JVM 메모리 플래그 | — | 여전히 0 — env 주입 설계 전제 유지 |

## 게이트 재측정

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `./tools/local-tls-build.sh` (S-21c) | 0 | `web:local`·`proxy:local` 재생(둘 다 **부재 상태였다** — 덮은 태그 0) · 두 이미지의 caddy 바이너리 sha256 동일 |
| `./tools/prod-proxy-sites-check.sh --proxy-image bidvector/proxy:local` (S-27b) | 0 | 하위 라우트 4 등식 · 운영 compose 단언 전부 |
| `./tools/prod-nginx-check.sh --proxy-image bidvector/proxy:local` (S-27c) | 0 | 불변 3표지 · N2~N9 |
| `./tools/prod-nginx-check.sh --host-config /etc/nginx --proxy-image …` | 0 | **불변 5표지** · N2~N9 · 사본은 종료 시 삭제 |
| 로컬 site adapt — base 파일 vs 분리 뒤 이미지 | — | JSON **바이트 동일**(양성 대조: 공유 본문 한 줄을 빼면 달라진다) |
| `./tools/image-hygiene-check.sh` proxy · web | 0 · 0 | proxy 는 새 정책(내용 하한 3 · 필수 셋) |
| `./tools/vuln-scan-check.sh` proxy · web | 0 · 0 | **차단후보 0 · 미등재 0 · allowlist 전체 0** · 분석 패키지 181(하한 126) · OS 선언 등식 |
| `./tools/local-tls-smoke.sh` (S-26) | 0 | 우회 ①~⑤ · 헤더 · 관리 경로 404 · 평문 거부 · 비-root · 로그에 자격 값 0. 정리 뒤 잔여 0 |

## 수정 라운드의 고침 — 재현 명령

프로브는 **레인 전용** 프로젝트·포트(`w7f2r2-probe` · 38443)이고 6G 개발 DB·host nginx·`:local` 태그에 닿지 않는다.
되돌리기 어려운 경로의 고침은 **실제 실패 경로**로 쟀다(env 값만 바꾼다 — 값은 운영자 자유 영역이다).

| 고침 | 재현 |
|---|---|
| H-1 | 프로브 프로젝트에 `up -d --wait postgres` → `stop postgres`(볼륨 남김) → `state/current` 기록 → `prod-deploy.sh … --release <다음>` dry-run·apply |
| H-2 | 성공 반입 뒤 env 의 JVM 옵션 키를 없는 플래그로 바꾸고 apply(앱이 즉시 죽는다) → 같은 상태에서 `prod-rollback.sh` dry·apply |
| H-2 우회 | 떠 있는 release 의 manifest 에 `migration=` 한 줄을 더하고(상태 편집) `prod-deploy.sh --release <옛것>` |
| M-1 | `BIDVECTOR_PROXY_HTTPS_PORT=<다른 값> ./tools/prod-deploy.sh …` dry-run · `. tools/local-tls-env.sh` 한 셸에서 같은 명령 |
| F5-C·D | 버릴 clone 에 앞 공백 두 칸짜리 등재를 **커밋**(깨끗한 트리 게이트가 앞에 있다) · allowlist 파일을 `git rm` |
| M-3 | 버릴 clone 에서 위생 정책의 `content.min-entries` 를 99 로 커밋 → `prod-release.sh … --apply` → 같은 태그로 재시도 → 정책을 되돌리고 다시 |
| L-1 | `docker/compose.prod.yaml` 의 app `environment` 에 값 없는 키 한 줄(사본 복원) |
| L-2 | 저장소 밖 디렉터리에 저장소 안 파일을 가리키는 심볼릭 링크를 만들어 `--env-file` 로 넘긴다 |
| L-3 | 조각 템플릿의 80 블록에서 버전 표시 끄기 지시어를 켠다(사본 복원) |
| V-1 | 손으로 `docker volume create <프로젝트>_<볼륨 키>`(라벨 없음) → 두 번 반입(apply) → `backups/` 계수와 DB 의 적용 최대 버전 |
| V-2 | `COMPOSE_PROJECT_NAME=<다른 이름>` 인 셸에서 `--project` 없이 dry-run(운영 스택을 내려 포트 점유 검사를 비킨다) |
| V-3 | 빈 `--state-dir` 에 옛 release 의 manifest 만 두고 반입. 통제군은 같은 state-dir 에 지금 DB 와 맞는 release |
| V-4 | 같은 상태 디렉터리로 두 deploy 를 `&` 로 동시에(마이그레이션이 같은 release 둘로 — 가드가 아니라 잠금을 재려고) |
| N2b | 하네스가 조각만 남긴 변형을 따로 띄운다. 양성 대조는 조각 템플릿의 리다이렉트 목적지를 클라이언트 Host 로 되돌리기(사본 복원) |
| M-1(r3) | 떠 있는 DB 에서 `alter table flyway_schema_history rename to …`(표 숨김) · 별도로 그 표의 행을 비운다 |
| M-2(r3) | 표를 숨긴 채 `stop postgres` → 새 `--state-dir` 로 옛 release `--apply` |
| M-3(r3) | env 파일의 `BIDVECTOR_PG_DB` 를 따옴표로 감싼다 · 음성 대조는 `BIDVECTOR_APP_JDBC_URL` 의 DB 만 다르게 |
| V3-4 | 프로젝트 잠금 파일을 밖에서 `flock -n` 으로 쥔 채 **다른 state-dir** 로 dry-run |
| 주기 러너 | `tools/prod-notification-sweep.sh` 를 반입 기록 없음 / `pending` 있음 / 정상 셋으로 |

결과는 checklist 1.3b 의 「고치기 전 ↔ 고친 뒤」 표다.

## 변이 · 양성 대조

| 묶음 | 결과 |
|---|---|
| 통제군 | 대역 하네스 0 · 키 집합 0 · `zz-` 렌더 0 · S-27b 통과 키 0(항목 23) |
| M1~M6 · R1 | 전부 기대대로 붉다 — 잡은 단언까지 checklist 1.3 |
| K1~K4 | 1 · 1 · 1 · 2 |
| F5-A~D | 1(만료 6일) · 「등재 1 · 199일」 · 1(들여쓴 등재) · 2(파일 부재) |
| V-1~V-4 · N2b | 전부 기대대로 — checklist 1.3b. N2b 의 첫 판은 「측정 불가(2)」로 멈췄고(다른 fixture site 가 기본 대상을 가져갔다) 조각만 남기는 판으로 고쳤다 |
| 1.3b 의 고침별 변이 | 전부 기대대로 — 위 표의 재현으로 |
| 복원 | 매 변이 뒤 사본 복원 + `cmp` 동일 · 저장소 `git status` 빈 출력 |

## 반입 리허설 (개발 host, 외부 effect 0) — 수정 라운드에서 **전건 재실행**

스크립트가 바뀌었으므로 다시 돌렸다. `--accept-no-vuln-scan` 없이 release 마다 게이트 **8 건**. 단계별 결과는
checklist 1.5 — release·deploy·rollback 전부 0, **정지 상태 반입에서 백업 생성**, **그 백업의 복원 + manifest 등식 OK**,
**진짜 헬스 실패 뒤 되돌림 0**, D1·D2·D3·D3b 는 1·1·3·3, 인증 GET 300건 200×300, 정리 뒤 컨테이너·볼륨·망 0,
`:local` 태그 전 4 줄 · 후 4 줄 · ID 동일(계수의 양성 대조를 먼저 통과시킨 비교다).

**앞 라운드 보고의 정정**: 그때의 「`:local` ID 전후 동일」은 측정 하네스의 태그 거르기 술어가 틀려 **빈 집합 둘을
비교한 결과**였다(공허한 초록). 이번 수치가 실제 비교다.

## rollback 실측 (버릴 clone)

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `git restore --source=957587c4 --staged --worktree -- <25 경로>` | 0 | `D` 19 · `M` 6 · in_scope 의 base 대비 diff **0줄** |
| 되돌린 트리에서 `./gradlew --no-daemon -q leakPatternGate` (⑥ⓐ) | 0 | 실행 뒤에도 트리가 되돌린 상태 그대로(19 D · 6 M) |
| 복원원 SHA 에 이 slice 가 없는가 | — | A 19 경로 전부 `957587c4` 에 부재 |

## 병합 (두 번째 — main `5e0617bf`)

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `git merge-tree --write-tree HEAD origin/main` | 0 | **충돌 예측 0** |
| 양쪽이 만진 파일(`comm -12`) | — | **0개** — 겹치는 자리가 없다 |
| `git merge origin/main` | 0 | 충돌 0 · main 쪽 53 파일 수용(7C③-b 알림 트리거) |

## 경계 확인

| 확인 | 결과 |
|---|---|
| 외부 effect | DNS 변경 0 · ACME 실발급 0(테스트는 자체 서명) · 운영 host 반입 0 · public 포트 개방 0. 네트워크는 이미지 pull 과 trivy 바이너리·DB 내려받기뿐 |
| host nginx | 읽기만(`cp` 한 번). `/etc/nginx` 쓰기·reload 0 |
| 6G 개발 스택 | 리허설·스모크 전후 `bid-vector-v2-dev` 기동 유지 · 그 볼륨 보존 |
| 공존 서비스 식별자 | 이 slice 의 diff·커밋 이력에서 대소문자 무시 계수 **0**(D-7F2-2) |
| evidence 비밀값 스캔 | `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m7/7f2/` → 매치 0 |
| clean tree | `git status --porcelain -- <in_scope 25 경로 개별 인자>` → 빈 출력. **양성 대조**(비파괴 절삭): 한 파일에 줄 하나를 더하면 ` M` 이 뜨고, `truncate` 로 되돌리면 다시 빈 출력 · `git hash-object` 전후 동일 |
| 크기 게이트 | evidence 82,189 B ≤ 산출물 326,324 B (판정 SHA 의 정본은 PR 조치 코멘트 — 이 수는 커밋마다 움직인다) |
