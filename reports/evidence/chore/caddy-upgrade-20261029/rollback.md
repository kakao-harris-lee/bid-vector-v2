# rollback — chore/caddy-upgrade-20261029

base: `78d97c599c55f0a0cb44e4778a865eb4b8745d6f`
실측 HEAD: `1654f602`(이 slice 의 마지막 산출물 커밋, r1). 그 뒤 커밋은 evidence 전용이다. 확인 술어 —
`git diff --name-only 1654f602..<판정 SHA> -- docker/ tools/local-tls-build.sh config/quality/ .github/ docs/runbook/`
가 빈 출력이면 유효하다. 앞 실측(`b95fdf1e`)은 r1 이 목록을 바꿨으므로 쓰지 않는다.

## 복원 명령 — 경로 한정

```
mapfile -t P < <(git diff --name-only 78d97c59..HEAD \
  | grep -v '^reports/evidence/chore/caddy-upgrade-20261029/')
git restore --source=78d97c59 --staged --worktree -- "${P[@]}"
```

공유 파일(`config/quality/*`·`.github/workflows/ci.yml`·runbook·compose)은 base `78d97c59`(머지된 main, 이 slice 미포함)
에서 복원한다. 다른 레인이 머지 **전에** 같은 파일을 main 에 고치면 그 시점의 병합된 main SHA(고정)에서 복원한다.
머지 뒤 되돌림은 PR revert 다.

## 실측 — 버릴 clone 에서 (2026-10-29, 실측 HEAD)

| 축 | 결과 |
| --- | --- |
| ① 기계 산출 목록 | 17 — `A` 3(`docker/caddy/` 셋) · `M` 14 |
| ② `git restore` 종료 코드 | 0 |
| ③ 복원 뒤 base 와의 diff(evidence 밖) | worktree **0줄** · index **0줄** · `docker/caddy/` 없음 |
| ④ 트리 동일성 | `git ls-tree -r` 이 base 와 같다(evidence 디렉터리 제외 — 복원 대상이 아니다) |
| ⑤ 남의 표지 보존 | `docker/ml-serving.Dockerfile` `FROM` 2 · runbook 1101줄(base 와 같음) |
| ⑥ 내 표지 0 | `caddy-build` 0 · `cd35bb33` 0 · `,go,gofmt` 0 · 「셋째 갈래」 0 · `^allow|` **20** · `000fd431` **3**(base 의 수) |

복원된 트리는 base 와 같으므로 그 판정은 base 실측이다 — web·proxy 취약점 게이트 exit 1(stale `CVE-2026-56857`)과
app 취약점 게이트 exit 1(`libbrotli1`, commands.md 「r1」). 되돌리면 그 빨강과 2026-11-05 만료가 돌아온다.

## 끄기

새 배선은 `local-tls-build.sh` 의 sha256 등식 하나다. 끄기는 위 복원이다(부분 끄기는 두 이미지가 다른
caddy 판으로 갈라질 자리를 연다).
