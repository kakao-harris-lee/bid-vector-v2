# M9/STR12 — 되돌림

## 0. 복원 원천이 base 가 아니다 (팀장 공지 2026-11-05, PR #124 verifier 실측)

이 브랜치에는 팀장 레인이 `origin/main` 을 합쳤다(병합 커밋의 둘째 부모 =
**`6c38fb3210c0c2cc38606df2dd2ef4060efb78d9`**, PR #122 `feature/m9-prod-tools` 병합).

복원 원천을 slice base(`79f3e5c4`)로 두면 **남의 변경까지 지운다.** 겹치는 경로를 실측으로 특정했고
**한 개**다:

```
comm -12 <(git diff --name-only 79f3e5c4 6c38fb32 | sort) <(내 경로 집합 | sort)
→ docs/runbook/m7-7f-production.md
```

그 파일은 양쪽이 고쳤다 — main 쪽은 반입 preflight 절(§3), 이쪽은 §13(발송 활성화). base 에서
복원하면 **main 쪽 §3 변경이 함께 사라진다.** 나머지 39 경로는 main 이 건드리지 않아 두 원천의 내용이
같다(그 경로에서는 선택이 무차별이다).

**복원 원천은 고정 SHA `6c38fb32` 다.** 움직이는 `origin/main` 을 쓰지 않는다.

## 1. 복원 목록 — 기계 산출 (두 단계다)

### 1.1 경로 집합 — 병합을 제외한 내 커밋에서

```
git log --no-merges --format=%H 79f3e5c4..HEAD \
  | while read -r s; do git log -1 --format=%s "$s" | grep -q m9-str12 && git show --format= --name-only "$s"; done \
  | sort -u
```

**40 경로.** 병합 커밋을 포함하면 남의 변경이 목록으로 들어오므로 반드시 `--no-merges` + 제목 필터다.
그 40 이 전부 `scope.md` in_scope 의 부분집합임은 `commands.md` §5 가 든다(`OUT:` 0 건).

### 1.2 상태(A/M/D) — **복원 원천 대비로** 다시 낸다

```
git diff --name-status 6c38fb32 <판정 SHA> -- <위 40 경로>
→ A 24 · M 15 · D 1   (합 40)
```

**커밋별 `--name-status` 를 모아 상태를 정하면 틀린다**(이 라운드에서 실측). `sort -u -k2` 가 경로당
한 줄만 남기므로, **추가된 뒤 수정된** 파일이 `M` 으로 분류된다 — 그 라운드의 산출은 `A 14 · M/D 29`
였고 **10 건이 오분류**였다(그중 셋은 마지막 커밋에서 생긴 evidence 세 파일).

거동은 우연히 옳았다 — `git restore --source` 는 원천에 없는 경로를 **지우므로**, `M` 으로 잘못
분류된 신설 파일도 결과적으로 사라진다(§3 ③ 이 트리 동일성으로 확인). 그러나 **문서의 목록이 틀린
것은 사실**이고, 상태는 §1.2 처럼 원천 대비로 내는 것이 맞다.

## 2. 명령

```bash
MAIN=6c38fb3210c0c2cc38606df2dd2ef4060efb78d9

# M·D 경로: 그 main 커밋의 내용으로 되돌린다(삭제된 자리지킴 셋이 되살아난다).
git restore --source=$MAIN --staged --worktree -- <M·D 경로 16>

# A 경로: 그 main 커밋에 없으므로 복원이 아니라 삭제다.
git rm -f --ignore-unmatch -- <A 경로 24>
```

경로는 **개별 인자**로 준다. `-A`·`.` 를 쓰지 않는다 — 공유 작업 트리에서 남의 미커밋 변경을 함께
끌어가는 자리다.

## 3. 실측 — 버릴 clone 에서 ①~⑥

**실측 HEAD: `faa9f663`** (이 slice 의 마지막 산출물 커밋 — evidence 네 파일을 담은 커밋)

판정 축은 종료 코드가 아니라 **계수와 트리 동일성**이다:

| # | 재는 것 | 결과 |
| --- | --- | --- |
| ① | 명령 exit | `git restore` **0** · `git rm` **0** |
| ② | 되돌린 계수 | `git status --porcelain` → `D 27 · M 15 · A 1`. `A 1` 은 되살아난 자리지킴 셋(`UnavailableNotificationDelivery.kt`) |
| ③ | 복원 대상 경로의 트리 | `git diff --quiet 6c38fb32 -- <40 경로>` → **빈 diff** |
| ④ | **내 표지 0** | **0 파일** (엄격 패턴, 아래) |
| ⑤ | **남의 표지 보존** | `m7-7f-production.md` 의 main 쪽 §3 문구(`반입 트리가 그 release 를 만든 커밋`) → **1 건 남음** |
| ⑥ | 게이트 | **트리 동일성으로 갈음** — 아래 |

### ④ 의 술어가 한 번 틀렸다 (양성/음성 대조를 둔 이유)

첫 술어에 `adapters\.notification` 을 넣었더니 **19 파일이 잡혔다**. 전부 오탐이다 —
`bidvector.adapters.notificationinbox` 가 그 패턴의 **접두 일치**다(7C③ 이 만든 기존 패키지).
느슨한 필터가 **거짓 빨강**을 냈다.

엄격 패턴으로 다시 쟀다:

```
m9-str12|STR12|TelegramBotCredential|TelegramChatId|RefusedMissingChannelConfig|
telegramDeliveryResult|ConfiguredRouteDirectory|NoticeMessageRenderer|
TelegramNotificationSender|FakeTelegramServer
```

| 트리 | 표지 파일 수 |
| --- | ---: |
| 되돌린 clone | **0** |
| 원본(`faa9f663`) — **양성 대조** | **57** |

양성 대조가 없으면 「0 건」이 **방어의 증거**인지 **패턴이 아무것도 안 잡는다는 증거**인지 갈리지
않는다.

### ⑥ 게이트 — 트리 동일성으로 갈음

```
git write-tree           → 22a860391efadf7f4f21bb1542c403993379549e
git rev-parse 6c38fb32^{tree} → 22a860391efadf7f4f21bb1542c403993379549e
```

되돌린 트리가 그 main 커밋의 트리와 **바이트 동일**하다(전체 트리 — 복원 대상 경로만이 아니다).
`6c38fb32` 는 `main` 의 커밋이고 그 CI 는 초록이었으므로, 게이트를 다시 돌리지 않는다. 하네스 규율
그대로 — **갈음은 「HEAD 초록」이 아니라 트리 동일성으로만**이고, 여기서는 그 동일성이 해시 등식이다.

## 4. 머지 뒤

PR revert 다. 이 문서의 경로 한정 복원은 **머지 전** 수단이고, 머지 뒤에는 `main` 의 이력을 되쓰지
않는다.

## 5. 실측 기록

| 항목 | 값 |
| --- | --- |
| 복원 원천 | `6c38fb3210c0c2cc38606df2dd2ef4060efb78d9` (고정, 병합된 main) |
| 실측 HEAD | **`faa9f663`** |
| 판정 SHA | **이 문서를 담은 커밋** (실측 뒤 `rollback.md` 하나만 움직였다) |
| 그 사이에 복원 대상이 움직였는가 | **`rollback.md` 한 파일**뿐 — `git diff --name-only faa9f663..<판정 SHA> -- <40 경로>` 가 그 한 줄을 낸다 |

verifier 가 대조하는 것은 「실측 HEAD == 판정 SHA」가 **아니다**(evidence 커밋은 언제나 뒤에 온다) —
그 사이에 **되돌림 대상이 움직였는가**다. 여기서는 이 문서 자신 한 줄이고, 그것이 되돌림의 **거동**을
바꾸지 않는다(문서다). 그 한 줄까지 0 으로 만들려면 측정 결과를 evidence 밖(PR 조치 코멘트)에 두어야
하고, 이 slice 는 **전건 CI 결과만** 그렇게 했다(`commands.md` §6).
