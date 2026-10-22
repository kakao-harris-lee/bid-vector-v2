# M7/7F 본편 — rollback

**실측 HEAD: ec953f03** (Phase 2 의 마지막 산출물 커밋). ①~⑥ 은 이력 재작성(D-7F2-3) 전의 같은 커밋에서 냈고, 재작성 전후 그 커밋의
트리는 바이트 동일하다(`git diff` 0줄) — 트리 동일성으로 갈음한다.

**전제 둘** — ① 이 브랜치는 base `bee7c36f` 이후 main 을 병합하지 않았다(`git log --oneline bee7c36f..HEAD --merges`
빈 출력). ② 공유 파일(ci.yml · 위생 정책 · Dockerfile 둘 · 6E runbook · proxy 설정)은 이 range 에서 이 slice 의 커밋만
만졌다 — base 가 그때 main 의 tip 이므로 다른 slice 의 줄은 base 가 담고 있고, 복원이 남의 줄을 지우지 않는다.

## 목록 — 기계 산출

`git diff --name-status bee7c36f..HEAD` 에서 evidence(`reports/evidence/m7/7f2/**`)를 뺀 것이다.

| 상태 | 경로 |
|---|---|
| A (19, 복원 명령이 지운다) | `deploy/nginx/test-fixture/nginx.conf` · `deploy/nginx/test-fixture/sites-enabled/default` · `deploy/nginx/test-fixture/sites-enabled/existing_site` · `deploy/nginx/zz-bidvector_kr.conf.template` · `docker/compose.prod.yaml` · `docker/prod.env.template` · `docker/proxy.prod.Caddyfile` · `docker/proxy.site.caddy` · `docs/runbook/m7-7f-production.md` · `tools/prod-cert-check.sh` · `tools/prod-deploy.sh` · `tools/prod-env-keys-check.sh` · `tools/prod-env.sh` · `tools/prod-health.sh` · `tools/prod-nginx-check.sh` · `tools/prod-nginx-render.sh` · `tools/prod-proxy-sites-check.sh` · `tools/prod-release.sh` · `tools/prod-rollback.sh` |
| M (6, base 로 복원) | `.github/workflows/ci.yml` · `config/quality/image-hygiene-policy-proxy.properties` · `docker/proxy.Caddyfile` · `docker/proxy.Dockerfile` · `docker/proxy.Dockerfile.dockerignore` · `docs/runbook/m6-6e-operations.md` |
| 되돌리지 않음 | `reports/evidence/m7/7f2/**`(계약·evidence) |

## 명령

```
git restore --source=bee7c36f --staged --worktree -- \
  .github/workflows/ci.yml \
  config/quality/image-hygiene-policy-proxy.properties \
  deploy/nginx/test-fixture/nginx.conf \
  deploy/nginx/test-fixture/sites-enabled/default \
  deploy/nginx/test-fixture/sites-enabled/existing_site \
  deploy/nginx/zz-bidvector_kr.conf.template \
  docker/compose.prod.yaml \
  docker/prod.env.template \
  docker/proxy.Caddyfile \
  docker/proxy.Dockerfile \
  docker/proxy.Dockerfile.dockerignore \
  docker/proxy.prod.Caddyfile \
  docker/proxy.site.caddy \
  docs/runbook/m6-6e-operations.md \
  docs/runbook/m7-7f-production.md \
  tools/prod-cert-check.sh \
  tools/prod-deploy.sh \
  tools/prod-env-keys-check.sh \
  tools/prod-env.sh \
  tools/prod-health.sh \
  tools/prod-nginx-check.sh \
  tools/prod-nginx-render.sh \
  tools/prod-proxy-sites-check.sh \
  tools/prod-release.sh \
  tools/prod-rollback.sh
```

경로 스물다섯은 위 표(A 19 + M 6)와 집합이 같다. 경로는 개별 인자다. **머지 뒤**의 되돌림은 이 명령이 아니라
PR 머지 커밋의 revert 다.

## 런타임 비활성화 (코드를 되돌리지 않고)

이 slice 는 **운영 host 에 아무것도 놓지 않았다**(host nginx 쓰기·reload 0, 운영 프로젝트 기동 0). 운영자가 반입한
뒤의 비활성화는 runbook `m7-7f-production.md` §7 이다: 이미지 되돌림(`tools/prod-rollback.sh`) · host nginx 조각
제거(링크 삭제 + `nginx -t` + reload — 기존 site 는 조각 전과 같다).

CI 축만 끄려면 `container` job 의 S-27·S-27b·S-27c 를 지운다 — 앞 step 과 공유하는 상태가 없다(S-27c 는 자기
컨테이너·망·임시 디렉터리를 만들고 종료 경로에서 지운다).

## 임시 clone 실측 ①~⑥

임시 clone 에서 실측 HEAD 를 체크아웃하고 위 명령을 실제로 돌렸다.

| # | 무엇 | 결과 |
|---|---|---|
| ① | 복원 명령 종료 코드 | 0 |
| ② | D/M 수 | `D` 19 · `M` 6 (위 표와 같다) |
| ③ | in_scope 의 base 대비 diff | 빈 출력(0줄). 트리 전체로 넓혀도 base 대비 남는 것은 `reports/evidence/m7/7f2/` 의 계약·evidence 뿐이다 |
| ④ | compile | **N/A — 컴파일 대상 소스 변경 0**(`*.kt`·`*.kts`·`*.py`·`*.proto`·`*.ts`·`*.tsx` 의 base 대비 변경 0). 트리 동일성으로 갈음한다 |
| ⑤ | test | 같은 사유로 N/A |
| ⑥ | 게이트 | (ⓐ) evidence 축 `leakPatternGate` — 되돌린 트리에서 실행(결과는 `commands.md`). (ⓑ) 컨테이너 축 — 되돌린 트리의 `container` job 입력(`ci.yml`·`docker/**`·`tools/**`·`config/quality/**`)이 ③ 으로 base 와 바이트 동일하므로 판정이 base 와 같다(트리 동일성) |

## 리허설 잔여

반입 리허설(프로젝트 `w7f2-prodrehearsal`)은 끝에 `down -v --remove-orphans` 를 돌렸고 그 뒤 컨테이너·볼륨·네트워크가
셋 다 0 이었다. 리허설용 release 태그(`w7f2-src`·`w7f2-rel1`·`w7f2-rel2`)는 지웠고, `:local` 태그 넷의 이미지 ID 는
리허설 전후 같다. nginx 하네스는 매 실행 컨테이너·망·임시 디렉터리를 지웠다(실행 뒤 레인 접두 컨테이너 0 · 임시
디렉터리 0).
