# M15 / 15U — rollback

**실측 HEAD: `68eef120e`**(이 slice 의 마지막 산출물 커밋 — 수정 라운드 1 의 가드 보정). 이 파일을 담는
커밋은 그 뒤에 온다. **라운드마다 다시 쟀다**(이번이 둘째 — 앞 라운드의 실측을 옮기지 않았다): r1 이
복원 목록 **안**의 파일 여섯을 움직였으므로 「측정 뒤 복원 목록 안 파일을 또 커밋하면 재측정이다」가
그대로 적용된다.

**복원 원천은 착수 base 그대로다.** `origin/main` 이 이 레인 동안 움직이지 않았다 —
`git merge-base --is-ancestor origin/main HEAD` 가 참이고 `git rev-parse origin/main` 이
`4f91faf59fb6b34f347e22369a3a4bfb27242101`(계약의 `base_sha`)이다. 그래서 「복원 원천을 바꾸면 재는
셋째 단언」(복원 대상 blob 이 착수 base 와 새 원천에서 같은가)은 **해당이 없다** — 원천이 하나다.

**운영 host 에는 되돌릴 것이 없다.** 이 slice 는 `/etc`·systemd·운영 compose·운영 DB·조달청 API·공유
잠금 자리를 **하나도** 건드리지 않았고 타이머를 하나도 켜지 않았다(로컬 실측은 `prod-guards-check.sh` 의
가짜 `docker`·`git` 앞에서 돌았다). 운영자가 §14.5 를 이미 밟았다면 되돌림은 저장소가 아니라 그 host
에서 한다: 수집 타이머를 끄고(`§14.8`) unit·drop-in 셋을 지운 뒤 `daemon-reload`, 그리고 §14.3 끝의
`After=` 토막이 넣은 줄 둘을 지운다. **실행 상태 디렉터리는 지우지 않는다** — 그 안의 원장이 그 달의
호출 이력이고 지우면 상한 회계가 0 에서 다시 선다(§14.6).

## 목록과 명령

`git diff --name-status 4f91faf59..<실측 HEAD>` — **M 여덟 · A 다섯**(목록은 기계 산출이고 라운드마다
다시 낸다. r1 에서 A 가 셋 → 다섯으로 늘었다 — evidence 두 파일이 들어왔다).

```
git restore --source=4f91faf59fb6b34f347e22369a3a4bfb27242101 --staged --worktree -- \
  config/quality/runbook-prod-path-lines.txt docs/discovery/capability-map.md \
  docs/runbook/m6-6e-operations.md docs/runbook/m7-7f-production.md milestone-15.md \
  tools/prod-env.sh tools/prod-guards-check.sh tools/prod-notification-relay.sh
git rm -rq reports/evidence/m15/15u config/quality/runbook-koneps-env-lines.txt tools/prod-collection.sh
```

## 임시 clone 실측(실측 HEAD 에서)

버릴 clone 에서 ①~⑥ 을 쟀다. 저장소 트리는 건드리지 않았다.

- ① 명령 exit: `restore` **0** · `rm` **0**
- ② 스테이징된 변경: **D 5 · M 8**(위 기계 목록의 수와 같다)
- ③ `git diff --cached 4f91faf59` **빈 출력** — 복원 뒤 트리가 원천과 같다
- 트리 동일성을 해시로도 쟀다: 복원 뒤 `git write-tree` == `4f91faf59^{tree}` ==
  **`d44e2eab030035cb063810a73d72f904a54203dc`**
- **양성 대조**(비파괴 절삭 — 버릴 clone 에서만, `checkout --` 를 쓰지 않았다): 복원 뒤
  `milestone-15.md` 에 한 줄을 더하자 등식이 깨졌다(트리
  **`280ddc47a3198bf1df007945dcfc9767ccaf4799`**). 그 줄을 다시 복원해 트리가 `d44e2eab0` 로 돌아오는
  것까지 쟀다 — 빈 등식이 건전성의 증거가 아님을 그 한 줄로 가른다.
- ④ compile · ⑤ test 는 **트리 동일성으로 갈음**한다 — 복원 뒤 트리가 원천의 트리와 **같은 객체**이고
  (위 해시 등식) `4f91faf59` 는 PR #207 머지 시점에 CI 가 초록이었다. 「HEAD 가 초록이다」로 갈음하지
  않았다.
- ⑥ **게이트는 직접 돌렸다**(갈음하지 않았다): 복원된 트리에서 `./tools/prod-guards-check.sh` 가
  **통과 269 · 어긋남 0**(이 slice 착수 전의 수와 같다 — r1 의 474 가 아니다) · `bash -n tools/prod-env.sh
  tools/prod-notification-relay.sh tools/prod-guards-check.sh` 가 0. `tools/prod-collection.sh` 는 복원
  뒤 **없다**(신설 파일이므로 삭제가 맞다 — `ls` 가 그것을 확인했다).

## 유효성 술어

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- \
  config/quality/runbook-koneps-env-lines.txt config/quality/runbook-prod-path-lines.txt \
  docs/discovery/capability-map.md docs/runbook/m6-6e-operations.md \
  docs/runbook/m7-7f-production.md milestone-15.md \
  tools/prod-collection.sh tools/prod-env.sh tools/prod-guards-check.sh \
  tools/prod-notification-relay.sh reports/evidence/m15/15u/
git rev-list --merges <실측 HEAD>..<판정 SHA>
```

둘 다 빈 출력이면 유효하다. 첫 술어에서 **나온 경로가 전부 원천(`4f91faf59`)에 없는 신설 파일이면
예외로 유효하다** — 이 파일(`reports/evidence/m15/15u/rollback.md`)과 `commands.md` 가 그 경우다. 열거와
원천 부재 확인은 verifier 가 한다.

**머지 커밋이 없다** — 이 레인은 `origin/main` 을 들이지 않았으므로 `--merges` 술어가 보는 구간에 머지가
없고, 실측 자리는 규율대로 **마지막 산출물 커밋**이다. 복원 원천도 그대로다(`origin/main` ==
`4f91faf59` · `merge-base --is-ancestor` 참) — 그래서 「복원 원천을 바꾸면 재는 셋째 단언」은 이
라운드에서도 해당이 없다.
