# M15 / 15V — rollback

**실측 HEAD: `6c1c4846e`**(이 slice 의 r2 장부 커밋 — 복원 목록 안의 **원천에 있던 파일**을 마지막으로
움직인 커밋이다). 이 파일과 `commands.md` 를 담는 커밋은 그 뒤에 오고, 둘 다 원천에 **없는 신설**이라
아래 유효성 술어의 예외에 든다. **라운드마다 다시 쟀다**(이번이 셋째) — r2 가 복원 목록 안의
`tools/prod-guards-check.sh`·`docs/runbook/m7-7f-production.md`·`milestone-15.md` 를 움직였으므로
「측정 뒤 복원 목록 안 파일을 또 커밋하면 재측정이다」가 그대로 적용된다.

**복원 원천은 착수 base 그대로다** — `origin/main` 이 이 레인 동안 움직이지 않았다
(`git rev-parse origin/main` == `9a2ce352b3ea2d39b57d91f94f723cff36150b98` == 계약의 `base_sha` ·
`merge-base --is-ancestor` 참). 그래서 「복원 원천을 바꾸면 재는 셋째 단언」은 해당이 없다.

**운영 host 에는 되돌릴 것이 없다.** 이 slice 는 `/etc`·systemd·운영 compose·운영 DB·조달청 API·공유
잠금 자리를 하나도 건드리지 않았고 `sudo` 를 쓰지 않았다. 운영자가 §14 를 이미 밟았다면 되돌림은
저장소가 아니라 그 host 에서 한다(§14.8 의 타이머 끄기 + unit·drop-in 제거). **실행 상태 디렉터리는
지우지 않는다** — 그 안의 원장이 그 달의 호출 이력이다.

## 목록과 명령

`git diff --name-status 9a2ce352b..<실측 HEAD>` — **M 일곱 · A 셋**(기계 산출. r1 에서 M 이 여섯 → 일곱
(`config/quality/runbook-prod-path-lines.txt` 가 처음 바뀌었다) · A 가 하나 → 셋(evidence 두 파일)).
**r2 는 이 목록을 바꾸지 않았다** — 새 파일 0 이고 만진 것은 이미 목록에 있던 셋뿐이다(재산출로 확인).

```
git restore --source=9a2ce352b3ea2d39b57d91f94f723cff36150b98 --staged --worktree -- \
  config/quality/runbook-prod-path-lines.txt docs/discovery/capability-map.md \
  docs/runbook/m7-7f-production.md milestone-15.md reports/evidence/m15/15u/commands.md \
  tools/prod-collection.sh tools/prod-guards-check.sh
git rm -rq reports/evidence/m15/15v
```

`config/quality/runbook-prod-path-lines.txt` 가 **r1 에서 목록에 들어왔다** — 수집 필터에
`/var/tmp/bidvector` 를 더하면서 새 sink 네 줄이 집합에 등재됐다(cr L-1). `runbook-koneps-env-lines.txt` 는
그대로다(`koneps.env` 축의 줄이 바뀌지 않았다 — 기계 재산출로 확인).

## 임시 clone 실측(실측 HEAD 에서)

버릴 clone 에서 ①~⑥ 을 쟀다. 저장소 트리는 건드리지 않았다.

- ① 명령 exit: `restore` **0** · `rm` **0**
- ② 스테이징된 변경: **D 3 · M 7**(위 기계 목록의 수와 같다)
- ③ `git diff --cached 9a2ce352b` **빈 출력** — 복원 뒤 트리가 원천과 같다
- 트리 동일성을 해시로도 쟀다: 복원 뒤 `git write-tree` == `9a2ce352b^{tree}` ==
  **`7989c86e5d157b2b682c3e7b578bf3da2f143889`**
- **양성 대조**(비파괴 절삭 — 버릴 clone 에서만, `checkout --` 를 쓰지 않았다): 복원 뒤
  `milestone-15.md` 에 한 줄을 더하자 등식이 깨졌다(r2 의 트리
  **`176eabe9cd99ccf483ebf085912e5178ce2bfacf`** — 절삭 대상 파일이 r2 에서 바뀌었으므로 r1 의 해시와
  다른 것이 맞다). 그 줄을 다시 복원해 트리가 `7989c86e5…` 로 돌아오는 것까지 쟀다(라운드마다 다시
  쟀고 복원 트리는 세 라운드 모두 `7989c86e5…`) — 빈 등식이 건전성의 증거가 아님을 그 한 줄로 가른다.
- ④ compile · ⑤ test 는 **트리 동일성으로 갈음**한다 — 복원 뒤 트리가 원천의 트리와 **같은 객체**이고
  (위 해시 등식) `9a2ce352b` 는 PR #209 머지 시점에 CI 가 초록이었다. 「HEAD 가 초록이다」로 갈음하지
  않았다.
- ⑥ **게이트는 직접 돌렸다**: 복원된 트리에서 `./tools/prod-guards-check.sh` 가 **통과 474 · 어긋남 0**
  (15V 착수 전의 수 — 516 이 아니다) · `bash -n` 두 파일 0 · `reports/evidence/m15/15v` 는 복원 뒤
  **없다**(신설이므로 삭제가 맞다 — `ls` 로 확인했다).

## 유효성 술어

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- \
  docs/discovery/capability-map.md docs/runbook/m7-7f-production.md milestone-15.md \
  reports/evidence/m15/15u/commands.md tools/prod-collection.sh tools/prod-guards-check.sh \
  reports/evidence/m15/15v/
git rev-list --merges <실측 HEAD>..<판정 SHA>
```

둘 다 빈 출력이면 유효하다. 첫 술어에서 **나온 경로가 전부 원천(`9a2ce352b`)에 없는 신설 파일이면
예외로 유효하다** — 이 파일과 `commands.md` 가 그 경우다(둘 다 `reports/evidence/m15/15v/` 아래의 신설
이고 `A` 로 들어온다). 열거와 원천 부재 확인은 verifier 가 한다. **r2 에서도 그 둘뿐이다** — 원천에 있던
파일은 실측 HEAD 가 마지막으로 움직였다.

**머지 커밋이 없다** — 이 레인은 `origin/main` 을 들이지 않았으므로 `--merges` 술어가 보는 구간에 머지가
없고, 실측 자리는 규율대로 **마지막 산출물 커밋**이다.
