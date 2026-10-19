# M7/7F-local — rollback

**실측 HEAD: 9f4e3385** (수정 라운드 r1 의 마지막 산출물 커밋). 아래 목록과 ①~⑥ 은 그 HEAD 에서 낸
것이고, 앞 라운드의 실측을 옮기지 않는다.

**전제 둘** — ① 이 브랜치는 base 이후 **main 을 병합하지 않았다**(`git log --oneline 77850903..HEAD
--merges` 빈 출력). 그래서 복원 기준은 계약의 base 그대로이고, 공유 파일의 다른 slice 줄은 base 가
이미 담고 있으므로 hunk 격리가 필요 없다. ② 되돌림 대상인 **공유 파일 다섯**(ci.yml · 정책 둘 ·
runbook · 위생 스크립트)을 이 slice 밖의 커밋이 이 range 에서 만지지 않았다(r1 에서 파일별로 재확인) — base 가 main 의 그때
tip 이고 그 뒤 이 브랜치의 커밋만 있으므로, 다른 slice 의 줄은 **base 가 이미 담고 있다**(그래서
hunk 격리가 필요 없고, 복원이 남의 줄을 지우지 않는다).

## 목록 — 기계 산출

`git diff --name-status 77850903..HEAD` 에서 낸다. **라운드마다 파일이 늘면 이 절차를 다시 돌린다**
(목록이 낡는 것이 이 결함의 실제 원인이다).

| 상태 | 경로 |
|---|---|
| A (12, 복원 명령이 지운다) | `config/quality/image-hygiene-policy-proxy.properties` · `config/quality/image-hygiene-policy-web.properties` · `docker/compose.local-tls.yaml` · `docker/proxy.Caddyfile` · `docker/proxy.Dockerfile` · `docker/proxy.Dockerfile.dockerignore` · `docker/web.Caddyfile` · `docker/web.Dockerfile` · `docker/web.Dockerfile.dockerignore` · `tools/local-tls-build.sh` · `tools/local-tls-env.sh` · `tools/local-tls-smoke.sh` |
| M (5, base 로 복원) | `.github/workflows/ci.yml` · `config/quality/vuln-allowlist.properties` · `config/quality/vuln-policy.properties` · `docs/runbook/m6-6e-operations.md` · `tools/image-hygiene-check.sh` |
| 되돌리지 않음 | `reports/evidence/m7/7f/**`(이 slice 의 evidence·계약) · 하네스 경로(`CLAUDE.md`·`.claude/**` — 이 range 에 **없다**) |

## 명령

```
git restore --source=77850903 --staged --worktree -- \
  .github/workflows/ci.yml \
  config/quality/image-hygiene-policy-proxy.properties \
  config/quality/image-hygiene-policy-web.properties \
  config/quality/vuln-allowlist.properties \
  config/quality/vuln-policy.properties \
  docker/compose.local-tls.yaml \
  docker/proxy.Caddyfile \
  docker/proxy.Dockerfile \
  docker/proxy.Dockerfile.dockerignore \
  docker/web.Caddyfile \
  docker/web.Dockerfile \
  docker/web.Dockerfile.dockerignore \
  docs/runbook/m6-6e-operations.md \
  tools/image-hygiene-check.sh \
  tools/local-tls-build.sh \
  tools/local-tls-env.sh \
  tools/local-tls-smoke.sh
```

명령의 경로 **열일곱**은 위 표의 되돌림 대상 전부와 집합이 같다(기계 산출 `A` 12 + `M` 5 = 17 —
evidence 네 파일은 일부러 대상이 아니다). r1 에서 `comm -3` 로 직접 대조했고 빈 출력이다(대상 17 ·
목록 17). 이 대조를 라운드마다 다시 돌린다 — r1 이 `tools/local-tls-build.sh`·`tools/local-tls-env.sh`
둘을 새로 만들었고, 그것이 바로 이 절차가 막는 「목록이 낡는다」의 실제 사례다.

경로는 **개별 인자**다(변수 하나로 묶으면 pathspec 이 하나가 된다). `--source` 에 없는 경로는
삭제되므로 신규 파일에 별도 `git rm` 이 필요 없고, `git checkout <base> -- <경로>` 는 쓰지 않는다
(base 에 없는 경로마다 pathspec 오류로 아무것도 적용되지 않는다).

**머지 뒤**의 되돌림은 이 명령이 아니라 그 PR 의 **머지 커밋 revert** 다 — 머지된 main 에서 같은 복원은
아무것도 지우지 않으면서 exit 0 을 낸다(의미가 뒤집힌다).

## 런타임 비활성화 (코드를 되돌리지 않고)

이 slice 가 켜는 것은 **로컬 프로필 하나**다. 끄는 방법은 셋이고 모두 즉시다:

1. **그 프로필을 쓰지 않는다** — `-f docker/compose.local-tls.yaml` 을 빼면 기존 6C 환경 그대로다
   (기본 파일을 고치지 않았다). 뜬 것이 있으면 `docker compose -f docker/compose.yaml -f
   docker/compose.local-tls.yaml down -v`.
2. **CI step 만 끈다** — `container` job 의 S-26 step 하나를 지우거나 `if: false` 를 붙인다. 앞선
   step 들과 공유하는 상태가 없다(S-25 뒤에 서고 자기 스택을 올리고 내린다). 이미지 빌드까지 빼려면
   S-21c 도 같이 끈다 — 그러면 위생·취약점 네 step 이 이미지 부재로 멈추므로 아래 4 와 함께 간다.
3. **이미지만 지운다** — `docker image rm bidvector/web:local bidvector/proxy:local`. 다른 서비스가
   참조하지 않는다.
4. **게이트만 끈다** — `container` job 의 네 step(`S-22b2`·`S-22b3`·`S-22e2`·`S-22e3`)을 지우거나
   `if: false` 로 둔다. 정책 파일은 남아도 아무것도 돌리지 않는다(스크립트는 인자로 받은 정책만
   읽는다). **반대로 정책만 지우면 안 된다** — 그 step 들이 「정책 키를 읽지 못했다」로 붉어진다
   (음성 대조 ②에서 실측한 fail-closed 거동이다).

복구 예상 시간: 명령 하나(초 단위). DB·볼륨·외부 상태를 바꾸지 않으므로 데이터 복구 축이 없다.

## 임시 clone 실측 ①~⑥

임시 clone(버릴 복제본)에서 실측 HEAD 를 체크아웃하고 위 명령을 **실제로** 돌렸다.

| # | 무엇 | 결과 |
|---|---|---|
| ① | 복원 명령 종료 코드 | 0 |
| ② | D/M 수 | `D` 12 · `M` 5 (위 표와 같다) |
| ③ | in_scope 의 base 대비 diff | 빈 출력(0줄) — 복원된 경로가 base 와 **바이트 동일**. 트리 전체로 넓혀도 base 대비 남는 것은 `reports/evidence/m7/7f/` 의 **네 파일**뿐이다(계약과 evidence — 일부러 되돌리지 않는다) |
| ④ | compile | **N/A — 컴파일 대상 소스 변경 0.** `git diff --name-only 77850903..HEAD -- '*.kt' '*.kts' '*.py' '*.proto' '*.ts' '*.tsx'` 가 빈 출력이다. ③ 과 합치면 되돌린 트리의 컴파일 입력은 base 와 동일하다(트리 동일성으로 갈음한다 — 「HEAD 초록」이 아니다) |
| ⑤ | test | 같은 사유로 N/A — test 입력(소스·fixture) 변경 0 |
| ⑥ | 게이트 | **초록 — 되돌린 트리에서 직접 돌렸다.** 축이 둘이다. (ⓐ) **evidence 축**: `reports/evidence/` 를 읽는 게이트는 `leakPatternGate` 하나이고, 되돌린 트리에서 그것을 실행해 `> Task :leakPatternGate` · `BUILD SUCCESSFUL in 13s` · 종료 코드 0 을 받았다. 게이트 리포트의 판정은 **baseline 밖 새 매치 0 · 쓸모없어진 baseline 항목 0** 이다(되돌리지 않는 evidence 가 게이트를 붉히지 않는다는 확인). 실행 직후 트리가 여전히 되돌린 상태임을 다시 셌다(base 대비 남는 것이 evidence 네 파일뿐). (ⓑ) **컨테이너 축**: 이 slice 가 닿는 게이트는 `container` job 이고, 되돌린 트리에서 그 job 의 입력(`ci.yml`·`docker/**`·`tools/**`·`config/quality/**`)이 ③ 으로 base 와 바이트 동일하므로 판정이 base 와 같다 — 트리 동일성으로 갈음하며 「HEAD 초록」으로 갈음하지 않는다 |

## 정리 뒤 잔여

스모크는 종료 경로에서 `down -v --remove-orphans` 를 돌리고 **그 뒤에 센다** — 프로젝트 라벨이 붙은
컨테이너·볼륨·네트워크가 셋 다 0 이어야 통과다(0 이 아니면 스모크가 그 자리에서 실패한다). 변이 실측
여섯 회를 포함해 모든 실행에서 잔여 0 이었다. **다른 프로젝트의 볼륨은 애초에 대상이 아니다** —
`down -v` 는 이 프로젝트 라벨만 지운다(6B-2 의 이름 충돌 교훈이 compose 파일의 `name:` 고정으로
닫혀 있고, 덧쓰기 파일도 같은 이름을 적는다).
