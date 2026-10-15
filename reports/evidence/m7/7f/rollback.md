# M7/7F-local — rollback

**실측 HEAD: 13e9e929** (이 slice 의 마지막 산출물 커밋). 아래 목록과 ①~⑥ 은 그 HEAD 에서 낸 것이고,
앞 라운드의 실측을 옮기지 않는다.

**전제 둘** — ① 이 브랜치는 base 이후 **main 을 병합하지 않았다**(`git log --oneline 77850903..HEAD
--merges` 빈 출력). 그래서 복원 기준은 계약의 base 그대로이고, 공유 파일의 다른 slice 줄은 base 가
이미 담고 있으므로 hunk 격리가 필요 없다. ② 되돌림 대상인 공유 파일 둘을 **이 slice 밖의 커밋이 이
range 에서 만지지 않았다**(파일별 `git log --oneline 77850903..HEAD -- <파일>` 이 각각 이 slice 의 커밋
하나만 낸다 — ci.yml 은 S-26 커밋, runbook 은 §9 커밋과 그 보완 커밋).

## 목록 — 기계 산출

`git diff --name-status 77850903..HEAD` 에서 낸다. **라운드마다 파일이 늘면 이 절차를 다시 돌린다**
(목록이 낡는 것이 이 결함의 실제 원인이다).

| 상태 | 경로 |
|---|---|
| A (8, 복원 명령이 지운다) | `docker/compose.local-tls.yaml` · `docker/proxy.Caddyfile` · `docker/proxy.Dockerfile` · `docker/proxy.Dockerfile.dockerignore` · `docker/web.Caddyfile` · `docker/web.Dockerfile` · `docker/web.Dockerfile.dockerignore` · `tools/local-tls-smoke.sh` |
| M (2, base 로 복원) | `.github/workflows/ci.yml` · `docs/runbook/m6-6e-operations.md` |
| 되돌리지 않음 | `reports/evidence/m7/7f/**`(이 slice 의 evidence·계약) · 하네스 경로(`CLAUDE.md`·`.claude/**` — 이 range 에 **없다**) |

## 명령

```
git restore --source=77850903 --staged --worktree -- \
  .github/workflows/ci.yml \
  docker/compose.local-tls.yaml \
  docker/proxy.Caddyfile \
  docker/proxy.Dockerfile \
  docker/proxy.Dockerfile.dockerignore \
  docker/web.Caddyfile \
  docker/web.Dockerfile \
  docker/web.Dockerfile.dockerignore \
  docs/runbook/m6-6e-operations.md \
  tools/local-tls-smoke.sh
```

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
   step 들과 공유하는 상태가 없다(S-25 뒤에 서고 자기 스택을 올리고 내린다).
3. **이미지만 지운다** — `docker image rm bidvector/web:local bidvector/proxy:local`. 다른 서비스가
   참조하지 않는다.

복구 예상 시간: 명령 하나(초 단위). DB·볼륨·외부 상태를 바꾸지 않으므로 데이터 복구 축이 없다.

## 임시 clone 실측 ①~⑥

임시 clone(버릴 복제본)에서 실측 HEAD 를 체크아웃하고 위 명령을 **실제로** 돌렸다.

| # | 무엇 | 결과 |
|---|---|---|
| ① | 복원 명령 종료 코드 | 0 |
| ② | D/M 수 | `D` 8 · `M` 2 (위 표와 같다) |
| ③ | in_scope 의 base 대비 diff | 빈 출력(0줄) — 복원된 경로가 base 와 **바이트 동일**. 트리 전체로 넓혀도 base 대비 남는 파일은 `reports/evidence/m7/7f/scope.md` **하나**다(일부러 되돌리지 않는 계약 문서) |
| ④ | compile | **N/A — 컴파일 대상 소스 변경 0.** `git diff --name-only 77850903..HEAD -- '*.kt' '*.kts' '*.py' '*.proto' '*.ts' '*.tsx'` 가 빈 출력이다. ③ 과 합치면 되돌린 트리의 컴파일 입력은 base 와 동일하다(트리 동일성으로 갈음한다 — 「HEAD 초록」이 아니다) |
| ⑤ | test | 같은 사유로 N/A — test 입력(소스·fixture) 변경 0 |
| ⑥ | 게이트 | **base 와 같은 판정**(아래 두 축). 두 축으로 잰다 — (ⓐ) **컨테이너 축**: 이 slice 가 닿는 게이트는 `container` job 이고, 되돌린 트리에서 그 job 의 입력(`ci.yml`·`docker/**`·`tools/**`)이 ③ 로 base 와 바이트 동일하므로 판정이 base 와 같다. (ⓑ) **되돌리지 않는 evidence 축**: `reports/evidence/` 를 읽는 게이트는 `leakPatternGate` 하나다. 그 게이트가 보는 입력을 **참조형 패턴 스캔으로 직접 재서 매치 0** 을 확인했고(commands.md 의 비밀값 스캔 둘째 명령), 게이트의 판정은 **baseline 밖의 새 매치**이므로 매치 0 에서는 붉어질 자리가 없다. 되돌린 트리는 그보다 파일이 **줄어든** 집합이고 거기 남는 evidence 파일은 `scope.md` 하나인데 게이트가 그 이름을 제외 목록에 둔다 — 즉 되돌린 트리의 게이트 입력은 base 와 같다. **그 게이트의 Gradle 실행 자체는 다른 레인의 Gradle 세션과 빌드 lock 경합으로 이 시점에 큐에 남았다**(호스트 규율상 끼어들지 않는다) — 마지막 HEAD 의 게이트 결과 정본은 애초에 verifier 와 PR 조치 코멘트다 |

## 정리 뒤 잔여

스모크는 종료 경로에서 `down -v --remove-orphans` 를 돌리고 **그 뒤에 센다** — 프로젝트 라벨이 붙은
컨테이너·볼륨·네트워크가 셋 다 0 이어야 통과다(0 이 아니면 스모크가 그 자리에서 실패한다). 변이 실측
여섯 회를 포함해 모든 실행에서 잔여 0 이었다. **다른 프로젝트의 볼륨은 애초에 대상이 아니다** —
`down -v` 는 이 프로젝트 라벨만 지운다(6B-2 의 이름 충돌 교훈이 compose 파일의 `name:` 고정으로
닫혀 있고, 덧쓰기 파일도 같은 이름을 적는다).
