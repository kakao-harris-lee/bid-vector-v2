# M15 / 15Q — rollback

**실측 HEAD: `d004e0095`**(이 slice 의 마지막 산출물 커밋 — 수정 라운드 2 의 commands 커밋). 이 파일을
담는 커밋은 그 뒤에 온다. **앞 라운드들의 실측을 옮기지 않고 그때마다 다시 돌렸다**(이번이 **여섯째**).
앞의 다섯은 자기 검토 · unit 실측 · 수정 라운드 1 · main 병합 뒤 최종 라운드 · 그리고 이번 — 전부 아래
복원 목록 **안**의 파일이 움직인 뒤였다(「측정 뒤 복원 목록 안 파일을 또 커밋하면 재측정이다」).
**복원 원천은 그대로다** — 이 라운드에서 `origin/main` 이 움직이지 않았다(`6eb904c02` 그대로, 실측).

## 전제 — 복원 원천이 바뀌었다

PR 을 연 뒤 `origin/main` 이 움직여(PR #203 M15/15O · #202 M15/15P) 브랜치가 BEHIND 가 됐고,
**머지 커밋으로 들였다**. 공유 파일을 그 뒤에 만졌으므로 **복원 원천은 착수 base 가 아니라 병합한
main 의 고정 SHA `6eb904c0275375a799605a768f423f184d664702`** 다(움직이는 `origin/main` 이 아니다).

- **그 SHA 에 이 slice 의 커밋이 없다**: `git log <착수 base>..6eb904c02` 는 커밋 **30 개**이고 그
  가운데 15Q 를 말하는 것은 **0** 이다(실측).
- **셋째 단언**(복원 원천을 바꾸면 재는 것) — 복원 대상 blob 이 착수 base 와 새 원천에서 같은가:
  **같음 5 · 다름 2**. 다른 둘은 `docs/discovery/capability-map.md`(base `ad103901` ≠ 원천
  `e2068e20`)와 `milestone-15.md`(`ac7a16f7` ≠ `e6b09e83`)이고, 둘 다 **15O·15P 가 만진 파일**이다.
  그래서 아래 복원은 **새 원천 기준**으로 다시 셌다 — 착수 base 에서 복원하면 그 두 slice 의 줄이
  사라진다.
- 신설 다섯(`tools/prod-notification-relay.sh` · `tools/prod-evaluation-commit.sh` ·
  `reports/evidence/m15/15q/` 의 셋)은 base 와 새 원천 **양쪽 모두에 없다** — 복원이 아니라 삭제다.
- **자동 병합이 접지 않았음을 따로 쟀다**(「충돌 0 자동 병합이 같은 본문을 접는다」): 공유 파일
  다섯에서 **내 쪽이 더한 줄 · 저쪽이 더한 줄이 병합 결과에 전부 있고, 겹쳐 는 줄이 0** 이다
  (`milestone-15.md` ours 2 · theirs 3 · 빠짐 0 · 겹침 0 / `capability-map.md` ours 2 · theirs 3 ·
  빠짐 0 · 겹침 0 / 나머지 셋은 theirs 0).
- 머지 뒤의 되돌림은 이 문서가 아니라 **그 PR 의 머지 커밋 revert** 다.

**운영 host 에는 되돌릴 것이 없다** — 이 slice 는 `/etc`·systemd·공유 잠금 자리를 건드리지 않았고,
타이머를 하나도 켜지 않았다. 운영자가 §13.4 를 이미 밟았다면 되돌림은 저장소가 아니라 그 host 에서
한다: 타이머 둘을 끄고 unit·drop-in 여섯을 지운 뒤 `daemon-reload`. 타이머만 꺼도 **행은 `outbox` 에
`PENDING` 으로 남는다**(§13.5).

## 목록과 명령

`git diff --name-status 6eb904c02..<실측 HEAD>` — **M 일곱 · A 다섯**(목록은 기계 산출이고 라운드마다
다시 낸다).

```
git restore --source=6eb904c0275375a799605a768f423f184d664702 --staged --worktree -- \
  docs/discovery/capability-map.md docs/runbook/m6-6e-operations.md \
  docs/runbook/m7-7f-production.md milestone-15.md \
  tools/prod-env.sh tools/prod-guards-check.sh tools/prod-notification-sweep.sh
git rm -rq reports/evidence/m15/15q tools/prod-evaluation-commit.sh tools/prod-notification-relay.sh
```

## 임시 clone 실측(실측 HEAD 에서)

버릴 clone 에서 ①~⑥ 을 쟀다. 저장소 트리는 건드리지 않았다.

- ① 명령 exit: `restore` **0** · `rm` **0**
- ② 스테이징된 변경: **D 5 · M 7**(위 목록과 같은 수다)
- ③ `git diff --cached 6eb904c02` **빈 출력** — 복원 뒤 트리가 원천과 같다
- 트리 동일성을 해시로도 쟀다: 복원 뒤 `git write-tree` == `6eb904c02^{tree}` ==
  **`f1e1b60b5c30d249e285c5e4c512bbac072ef5b5`**
- **양성 대조**(비파괴 절삭 — 버릴 clone 에서만, `checkout --` 를 쓰지 않았다): 복원 뒤
  `docs/discovery/capability-map.md` 에 한 줄을 더하자 등식이 깨졌다(`M docs/discovery/
  capability-map.md` · 트리 **`7cdce23df070dbe785304845da1265644a428629`**). 빈 등식이 건전성의
  증거가 아님을 그 한 줄로 가른다.
- ④ compile · ⑤ test · ⑥ 게이트는 **트리 동일성으로 갈음**한다 — 복원 뒤 트리가 원천의 트리와 **같은
  객체**이고(위 해시 등식), `6eb904c02` 는 PR #202 머지 시점에 CI 가 초록이었다. 「HEAD 가 초록이다」로
  갈음하지 않았다.

## 유효성 술어

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- \
  docs/discovery/capability-map.md docs/runbook/m6-6e-operations.md \
  docs/runbook/m7-7f-production.md milestone-15.md \
  tools/prod-env.sh tools/prod-guards-check.sh tools/prod-notification-sweep.sh \
  tools/prod-evaluation-commit.sh tools/prod-notification-relay.sh \
  reports/evidence/m15/15q/
git rev-list --merges <실측 HEAD>..<판정 SHA>
```

둘 다 빈 출력이면 유효하다. 첫 술어에서 **나온 경로가 전부 원천(`6eb904c02`)에 없는 신설 파일이면
예외로 유효하다** — 이 파일(`reports/evidence/m15/15q/rollback.md`)이 그 경우다. 열거와 원천 부재
확인은 verifier 가 한다.

**머지 커밋은 실측 HEAD 보다 앞에 있다**(`76a287749` → `d004e0095`). 그래서 `--merges` 술어가 보는
구간에는 머지가 없다 — 「main 병합이 마지막 산출물 커밋 뒤에 오면 실측 자리는 그 병합 커밋」이라는
규율의 반대 경우이고, 실측 자리는 규율대로 **마지막 산출물 커밋**이다.
