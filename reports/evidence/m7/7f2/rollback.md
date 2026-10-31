# M7/7F 본편 — rollback

**실측 HEAD: `57ef1f89`** — 이 slice 의 **마지막 산출물 커밋**이다(수정 라운드 2 에서 다시 냈다). 뒤따르는 evidence
커밋은 아래 복원 목록의 경로를 하나도 건드리지 않는다. 판정 SHA 와 실측 HEAD 가 다른 것은 정상이고, 확인할 것은
**그 사이에 되돌림 대상이 움직였는가**다: `git diff --name-only 57ef1f89..<판정 SHA> -- <아래 25 경로>` 가 빈
출력이면 이 실측은 유효하다.

## 복원 기준은 병합된 main 고정 SHA `5e0617bf` 다

이 브랜치는 main 을 **두 번 병합했다**(`ceb6df59` → `957587c4`, `9b2162c5` → `5e0617bf`). 그래서 복원원은 옛 base 가
아니다 — 옛 base 로 되돌리면 공유 파일에서 그 사이 들어온 다른 레인의 줄을 함께 지운다. 복원원은 **마지막으로 병합한
main 의 고정 SHA `5e0617bf`** 하나다(움직이는 `origin/main` 이 아니다).

**전제 — 실측**

| 전제 | 확인 |
|---|---|
| `5e0617bf` 에 이 slice 가 없다 | A 19 경로 전부 그 SHA 에 **부재**(`git cat-file -e` 전수) → 복원이 그 19 개를 지우는 것이 맞다 |
| 공유 파일 6 개는 그 SHA 에 **남의 내용으로** 있다 | 복원이 내 줄만 지우고 남의 줄을 보존한다 |
| 마지막 병합 뒤의 병합은 없다 | `git log --merges 5e0617bf..HEAD` 빈 출력 |

## 목록 — 기계 산출

`git diff --name-status 5e0617bf..HEAD` 에서 evidence(`reports/evidence/m7/7f2/**`)를 뺀 것이다. **라운드마다 다시 낸다.**

| 상태 | 경로 |
|---|---|
| A (19, 복원 명령이 지운다) | `deploy/nginx/test-fixture/nginx.conf` · `deploy/nginx/test-fixture/sites-enabled/default` · `deploy/nginx/test-fixture/sites-enabled/existing_site` · `deploy/nginx/zz-bidvector_kr.conf.template` · `docker/compose.prod.yaml` · `docker/prod.env.template` · `docker/proxy.prod.Caddyfile` · `docker/proxy.site.caddy` · `docs/runbook/m7-7f-production.md` · `tools/prod-cert-check.sh` · `tools/prod-deploy.sh` · `tools/prod-env-keys-check.sh` · `tools/prod-env.sh` · `tools/prod-health.sh` · `tools/prod-nginx-check.sh` · `tools/prod-nginx-render.sh` · `tools/prod-proxy-sites-check.sh` · `tools/prod-release.sh` · `tools/prod-rollback.sh` |
| M (6, `5e0617bf` 로 복원) | `.github/workflows/ci.yml` · `config/quality/image-hygiene-policy-proxy.properties` · `docker/proxy.Caddyfile` · `docker/proxy.Dockerfile` · `docker/proxy.Dockerfile.dockerignore` · `docs/runbook/m6-6e-operations.md` |
| 되돌리지 않음 | `reports/evidence/m7/7f2/**`(계약·evidence) |

경로 **스물다섯**은 위 표(A 19 + M 6)와 집합이 같다(`comm -23` 빈 출력으로 확인). 경로는 개별 인자다.

## 명령

```
git restore --source=5e0617bf --staged --worktree -- \
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

**머지 뒤**의 되돌림은 이 명령이 아니라 PR 머지 커밋의 revert 다.

## 런타임 비활성화 (코드를 되돌리지 않고)

이 slice 는 **운영 host 에 아무것도 놓지 않았다**(host nginx 쓰기·reload 0, 운영 프로젝트 기동 0 — 리허설은 자기 프로젝트
이름·루프백 포트로 돌고 끝에 `down -v` 로 잔여 0 을 남겼다). 운영자가 반입한 뒤의 비활성화는 runbook `m7-7f-production.md`
§7 이다: 이미지 되돌림(`tools/prod-rollback.sh`) · host nginx 조각 제거(링크 삭제 + `nginx -t` + reload — 기존 site 는
조각 전과 같다).

CI 축만 끄려면 `container` job 의 S-27·S-27b·S-27c 를 지운다 — 앞 step 과 공유하는 상태가 없다(S-27c 는 자기
컨테이너·망·임시 디렉터리를 만들고 종료 경로에서 지운다).

## 임시 clone 실측 ①~⑥

버릴 clone(`scratchpad/w-7f2/clone-rb`)에서 실측 HEAD 를 체크아웃하고 위 명령을 **실제로 돌렸다.**

| # | 무엇 | 결과 |
|---|---|---|
| ① | 복원 명령 종료 코드 | **0** |
| ② | D/M 수 | `D` **19** · `M` **6** (위 표와 같다) |
| ③ | in_scope 의 `5e0617bf` 대비 diff | **빈 출력(0줄)**. 트리 전체로 넓혀도 남는 것은 `reports/evidence/m7/7f2/` 의 계약·evidence 넷뿐이다 |
| ④ | compile | **N/A — 컴파일 대상 소스 변경 0**(`*.kt`·`*.kts`·`*.py`·`*.proto`·`*.ts`·`*.tsx`·`*.java` 의 base 대비 변경 0, 전수 확인). 트리 동일성으로 갈음한다 |
| ⑤ | test | 같은 사유로 N/A |
| ⑥ | 게이트 | (ⓐ) evidence 축 `leakPatternGate` — 되돌린 트리에서 실행 **exit 0**, 실행 뒤에도 트리가 되돌린 상태 그대로(`D` 19 · `M` 6). (ⓑ) 컨테이너 축 — 되돌린 트리의 `container` job 입력(`ci.yml`·`docker/**`·`tools/**`·`config/quality/**`·`deploy/**`)이 ③ 으로 base 와 **바이트 동일**하므로 판정이 base 와 같다(트리 동일성) |

## 리허설 잔여

반입 리허설(프로젝트 `w7f2-prodrehearsal`)은 끝에 `down -v --remove-orphans` 를 돌렸고 그 뒤 컨테이너·볼륨·네트워크가
셋 다 0 이었다. 리허설용 release 태그(`w7f2r1`·`w7f2r2` × 네 서비스)는 지웠고, `:local` 태그의 이미지 ID 는 리허설 전후
같다(`diff` 빈 출력). 로컬 스모크(S-26)도 자기 프로젝트를 `down -v` 로 치워 잔여 0 이었다. nginx 하네스는 매 실행
컨테이너·망·임시 디렉터리를 지웠다. 6G 개발 스택(`bid-vector-v2-dev`)은 전 과정에서 기동 유지·볼륨 보존을 확인했다.
