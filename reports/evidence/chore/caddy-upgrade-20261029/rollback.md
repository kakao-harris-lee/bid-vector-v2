# rollback — chore/caddy-upgrade-20261029

base: `78d97c599c55f0a0cb44e4778a865eb4b8745d6f`
실측 HEAD: `b95fdf1e`(이 slice 의 마지막 산출물 커밋). 그 뒤 커밋은 evidence 전용이다. 확인 술어 —
`git diff --name-only b95fdf1e..<판정 SHA> -- docker/ tools/local-tls-build.sh config/quality/` 가 빈 출력이면 유효하다.

## 복원 명령 — 경로 한정

```
mapfile -t P < <(git diff --name-only 78d97c59..HEAD \
  | grep -v '^reports/evidence/chore/caddy-upgrade-20261029/')
git restore --source=78d97c59 --staged --worktree -- "${P[@]}"
```

`config/quality/vuln-allowlist.properties`·`vuln-policy.properties` 는 공유 파일이다. base `78d97c59` 는 머지된
main 이고 이 slice 를 담지 않는다. 다른 레인이 머지 **전에** 같은 파일을 main 에 고치면 그 시점의 병합된 main
SHA(고정)에서 복원한다. 머지 뒤 되돌림은 PR revert 다.

## 실측 — 버릴 clone 에서 (2026-10-29, 실측 HEAD)

| 축 | 결과 |
| --- | --- |
| ① 기계 산출 목록 | `A` 3(`docker/caddy/` 셋) · `M` 9 |
| ② `git restore` 종료 코드 | 0 |
| ③ 복원 뒤 base 와의 diff | worktree **0줄** · index **0줄** · `docker/caddy/` 없음 |
| ④ 트리 동일성 | `git write-tree` == base tree(`22dc23be…`) |
| ⑤ 남의 표지 보존 | `docker/ml-serving.Dockerfile` `FROM` 2 그대로(목록 밖) |
| ⑥ 내 표지 0 | `caddy-build` 두 Dockerfile 0 · `^allow|` 0 → **20**(base 의 수) |

복원된 트리는 base 와 같으므로 그 판정은 commands.md 「base」 실측이다 — 빌드 exit 0, web·proxy 취약점
게이트 exit 1(stale `CVE-2026-56857`). 되돌리면 base 의 빨강과 2026-11-05 만료가 돌아온다.

## 끄기

새 배선은 `local-tls-build.sh` 의 sha256 등식 하나다. 끄기는 위 복원이다(부분 끄기는 두 이미지가 다른
caddy 판으로 갈라질 자리를 연다).
