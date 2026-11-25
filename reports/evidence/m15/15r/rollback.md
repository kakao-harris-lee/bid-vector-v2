# M15 / 15R — rollback

**실측 HEAD: `52305f6e2`**. 이 파일을 담는 커밋은 그 뒤에 온다. **이번이 넷째 실측**이다 — 복원 목록
**안**의 파일이 움직일 때마다 다시 쟀다(S-6 이 범위에 들어오면서 · 수정 라운드 1 이 산출물 다섯을 ·
`tools/prod-alert-telegram.sh` 가 목록에 들어오면서 · 라운드 2 가 산출물 넷과 **신설 기대 파일 둘**을).
앞 세 실측을 옮기지 않았다.

## 전제 — 복원 원천은 착수 base 다

`928ac2fa0f8fb1fdb3a6c444eac5bceb756b3d0a`(PR #204 머지 커밋). 이 브랜치는 그 뒤로 main 을 **병합하지
않았다**(`base..HEAD` 의 머지 커밋 0) — 원천을 병합한 main 의 고정 SHA 로 옮기는 규율도, 셋째 단언(두
원천에서 복원 대상 blob 이 같은가)도 이 라운드에 해당하지 않는다.

- **단언 ① 원천이 HEAD 의 조상이다**: `git merge-base --is-ancestor` **예**.
- **단언 ② 원천에 이 slice 의 첫 산출물 커밋(`51f75964a` — 계약)이 없다**: **없다**(실측).
- 신설 파일은 evidence 셋뿐이고 나머지는 전부 수정이다.
- 머지 뒤의 되돌림은 이 문서가 아니라 **그 PR 의 머지 커밋 revert** 다.

**운영 host 에는 되돌릴 것이 없다** — `/etc`·systemd·운영 컨테이너·공유 잠금 자리를 하나도 건드리지
않았고 타이머를 켜지도 끄지도 않았다. 운영자가 §9.5 의 「이미 깔린 경보 unit 을 맞춘다」 토막을 이미
밟았다면 그 되돌림은 host 에서 한다(지운 줄 복구 · `LimitCORE=0` 제거 · `daemon-reload`). **그 host 거동은
이 문서의 복원과 독립이다** — 스크립트가 파일 갈래를 환경 갈래보다 먼저 보므로 어느 순서로 되돌려도
경보는 같은 값을 낸다.

## 목록과 명령

`git diff --name-status 928ac2fa0..<실측 HEAD>` — **M 일곱 · A 다섯**(기계 산출이고 라운드마다 다시 낸다.
수정 라운드가 파일을 더 만들면 이 절차를 다시 돌린다 — 라운드 1 이 `tools/prod-alert-telegram.sh` 를,
라운드 2 가 `config/quality/` 의 기대 파일 둘을 더했다).

```
git restore --source=928ac2fa0f8fb1fdb3a6c444eac5bceb756b3d0a --staged --worktree -- \
  docs/runbook/m6-6e-operations.md docs/runbook/m7-7f-production.md milestone-15.md \
  tools/prod-alert-telegram.sh tools/prod-env.sh tools/prod-guards-check.sh \
  tools/prod-notification-relay.sh
git rm -rq reports/evidence/m15/15r \
  config/quality/runbook-prod-path-lines.txt config/quality/runbook-relay-env-lines.txt
```

hunk 격리는 **필요 없다** — 위 일곱을 `base..HEAD` 에서 만진 커밋은 전부 이 slice 의 것이고
(`git log --oneline 928ac2fa0..HEAD -- <일곱>` 이 그 목록이다), 하네스 레인(`CLAUDE.md`·`.claude/**`)
커밋은 이 range 에 **0** 건이다.

## 임시 clone 실측(실측 HEAD 에서)

- ① 명령 exit: `restore` **0** · `rm` **0**
- ② 스테이징된 변경: **D 5 · M 7**(위 목록과 같은 수다)
- ③ `git diff --cached 928ac2fa0` **빈 출력**
- 트리 동일성: 복원 뒤 `git write-tree` == `928ac2fa0^{tree}` == **`6f3714d875d722152a65f3197711cdf97a32b0b1`**
- **양성 대조**(비파괴 절삭 — `checkout --` 을 쓰지 않았다): 복원 뒤 `tools/prod-env.sh` 에 한 줄을 더하자
  등식이 깨졌고(트리 `a4f05d3dc`) `head -n <원래 줄수>` 절삭으로 `6f3714d87` 로 돌아왔다. 빈 등식이
  건전성의 증거가 아님을 그 한 줄로 가른다.
- **라운드 둘이 산출물을 더 만지고 신설 파일이 둘 늘어도 복원 트리는 같은 객체다** — 복원은 원천의
  blob 을 쓰고 신설 파일은 내용과 무관하게 지우므로, 그 사이의 변경량과 무관하다(네 실측 모두
  `6f3714d87`).
- `git rev-list --merges 928ac2fa0..<실측 HEAD>` **빈 출력** — 원천을 옮길 병합이 없다(위 전제).
- ④ compile · ⑤ test · ⑥ 게이트는 **트리 동일성으로 갈음**한다 — 복원 뒤 트리가 원천의 트리와 **같은
  객체**이고 `928ac2fa0` 의 CI 는 **success** 다(실측). 「HEAD 가 초록이다」나 Gradle cache 적중을 근거로
  쓰지 않았다.

## 유효성 술어

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- \
  docs/runbook/m6-6e-operations.md docs/runbook/m7-7f-production.md milestone-15.md \
  tools/prod-alert-telegram.sh tools/prod-env.sh tools/prod-guards-check.sh \
  tools/prod-notification-relay.sh reports/evidence/m15/15r/ \
  config/quality/runbook-prod-path-lines.txt config/quality/runbook-relay-env-lines.txt
git rev-list --merges <실측 HEAD>..<판정 SHA>
```

둘 다 빈 출력이면 유효하다. 첫 술어에서 **나온 경로가 전부 원천(`928ac2fa0`)에 없는 신설 파일이면 예외로
유효하다** — 이 파일이 그 경우다. 열거와 경로별 원천 부재 확인은 verifier 가 판정 SHA 에서 한다. 예외를
쓰면 ② 의 **D 수는 판정 SHA 에서 다시 센다**(evidence 파일이 늘면 복원이 지우는 수가 그만큼 는다 — 트리
결과는 같다).
