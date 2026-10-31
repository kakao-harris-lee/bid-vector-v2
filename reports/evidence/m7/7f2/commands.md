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

## 변이 · 양성 대조

| 묶음 | 결과 |
|---|---|
| 통제군 | 대역 하네스 0 · 키 집합 0 · `zz-` 렌더 0 |
| M1~M6 · R1 | 전부 기대대로 붉다 — 잡은 단언까지 checklist 1.3 |
| K1~K4 | 1 · 1 · 1 · 2 |
| F5-A · F5-B | release preflight 1(만료 6일) · 「등재 1 · 199일」 |
| 복원 | 매 변이 뒤 사본 복원 + `cmp` 동일 · 저장소 `git status` 빈 출력 |

## 반입 리허설 (개발 host, 외부 effect 0)

`--accept-no-vuln-scan` 없이 전건. 단계별 결과는 checklist 1.5 — release·deploy·rollback 전부 0, D1·D2·D3 는 1·1·3,
인증 GET 300건 200×300, 정리 뒤 컨테이너·볼륨·망 0, `:local` 의 ID 전후 동일.

## rollback 실측 (버릴 clone)

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `git restore --source=957587c4 --staged --worktree -- <25 경로>` | 0 | `D` 19 · `M` 6 · in_scope 의 base 대비 diff **0줄** |
| 되돌린 트리에서 `./gradlew --no-daemon -q leakPatternGate` (⑥ⓐ) | 0 | 실행 뒤에도 트리가 되돌린 상태 그대로(19 D · 6 M) |
| 복원원 SHA 에 이 slice 가 없는가 | — | A 19 경로 전부 `957587c4` 에 부재 |

## 경계 확인

| 확인 | 결과 |
|---|---|
| 외부 effect | DNS 변경 0 · ACME 실발급 0(테스트는 자체 서명) · 운영 host 반입 0 · public 포트 개방 0. 네트워크는 이미지 pull 과 trivy 바이너리·DB 내려받기뿐 |
| host nginx | 읽기만(`cp` 한 번). `/etc/nginx` 쓰기·reload 0 |
| 6G 개발 스택 | 리허설·스모크 전후 `bid-vector-v2-dev` 기동 유지 · 그 볼륨 보존 |
| 공존 서비스 식별자 | 이 slice 의 diff·커밋 이력에서 대소문자 무시 계수 **0**(D-7F2-2) |
| evidence 비밀값 스캔 | `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m7/7f2/` → 매치 0 |
