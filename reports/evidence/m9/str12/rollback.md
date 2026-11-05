# M9/STR12 — 되돌림

## 0. 복원 원천이 base 가 아니다 (팀장 공지 2026-11-05, PR #124 verifier 실측)

이 브랜치에는 팀장 레인이 `origin/main` 을 합쳤다(병합 커밋의 둘째 부모 =
**`6c38fb3210c0c2cc38606df2dd2ef4060efb78d9`**, PR #122 `feature/m9-prod-tools` 병합).

그래서 복원 원천을 slice base(`79f3e5c4`)로 두면 **남의 변경까지 지운다.** 실측으로 겹치는 경로는
하나다:

```
comm -12 <(git diff --name-only 79f3e5c4 6c38fb32 | sort) <(내 커밋들의 name-only | sort -u)
→ docs/runbook/m7-7f-production.md
```

그 파일은 양쪽이 고쳤다 — main 쪽은 반입 preflight 절(§3), 이쪽은 §13(발송 활성화). base 에서
복원하면 **main 쪽 §3 변경이 함께 사라진다.** 나머지 경로는 main 이 건드리지 않았으므로 두 원천의
내용이 같다(그 경로에서는 선택이 무차별이다).

**따라서 복원 원천은 고정 SHA `6c38fb32` 다.** 움직이는 `origin/main` 을 쓰지 않는다.

## 1. 복원 목록 — 기계 산출

목록은 **병합을 제외한 내 커밋들**에서만 낸다(`--no-merges` + 제목이 `m9-str12`). 병합 커밋을
포함하면 남의 변경이 목록으로 들어와 되돌림이 그것까지 지운다.

```
git log --no-merges --format=%H 79f3e5c4..HEAD \
  | while read -r s; do git log -1 --format=%s "$s" | grep -q m9-str12 && git show --format= --name-status "$s"; done \
  | sort -u
```

- 산출 경로 **41**: 신설 11(`A`) · 삭제 1(`D`, 자리지킴 셋) · 수정 29(`M`).
- 그 41 이 전부 `scope.md` in_scope 의 부분집합인지는 `commands.md` §5 의 대조가 든다
  (`comm -23` 빈 출력).
- 라운드마다 재산출한다 — 수정 라운드가 새 파일을 만들면 목록이 함께 움직여야 한다.

## 2. 명령

```bash
MAIN=6c38fb3210c0c2cc38606df2dd2ef4060efb78d9

# 수정·삭제된 경로: 그 main 커밋의 내용으로 되돌린다(삭제된 자리지킴 셋이 되살아난다).
git restore --source=$MAIN --staged --worktree -- <위 목록의 M·D 경로들>

# 신설 경로: 그 main 커밋에 없으므로 복원이 아니라 삭제다.
git rm -f --ignore-unmatch -- <위 목록의 A 경로들>
```

경로는 **개별 인자**로 준다. `-A`·`.` 를 쓰지 않는다 — 공유 작업 트리에서 남의 미커밋 변경을
함께 끌어가는 자리다.

## 3. 실측 — 임시 clone 에서 ①~⑥

**실측 HEAD: `<마지막 산출물 커밋 SHA — §5 가 기록>`**

판정 축은 종료 코드가 아니라 **계수**다(「내 표지 0 · 남의 표지 보존」):

| # | 재는 것 | 술어 |
| --- | --- | --- |
| ① | 명령 exit | `git restore` · `git rm` 둘 다 0 |
| ② | D/M 수 | 되돌린 경로 수 == 목록의 41 |
| ③ | 결과 트리 | 복원 대상 경로에서 `git diff --quiet $MAIN -- <목록>` (빈 diff) |
| ④ | **내 표지 0** | `grep -rn "m9-str12\|STR12\|adapters.notification\|TelegramBotCredential" <목록 경로>` → 0 건 |
| ⑤ | **남의 표지 보존** | `m7-7f-production.md` 에 main 쪽 §3 문구(`반입 트리가 그 release 를 만든 커밋`)가 **남아 있다** → 1 건 |
| ⑥ | 게이트 | 되돌린 트리에서 Kotlin `check` — 자리지킴 셋이 되살아나고 `gate-tests.properties` 등재가 base 상태로 돌아가므로 등식이 다시 성립한다 |

④⑤ 가 **짝**이다. ④ 만 보면 「전부 지웠다」도 통과하고, ⑤ 만 보면 「아무것도 안 했다」도 통과한다.

## 4. 머지 뒤

PR revert 다. 이 문서의 경로 한정 복원은 **머지 전** 수단이고, 머지 뒤에는 `main` 의 이력을
되쓰지 않는다.

## 5. 실측 기록

| 항목 | 값 |
| --- | --- |
| 복원 원천 | `6c38fb3210c0c2cc38606df2dd2ef4060efb78d9` (고정) |
| 실측 HEAD | **`<채움>`** |
| 판정 SHA | **`<채움>`** |
| 실측 HEAD → 판정 SHA 사이에 복원 대상이 움직였는가 | **`<채움>`** — `git diff --name-only <실측 HEAD>..<판정 SHA> -- <목록>` |

verifier 가 대조하는 것은 「실측 HEAD == 판정 SHA」가 **아니다**(evidence 커밋은 언제나 뒤에 온다) —
그 사이에 **되돌림 대상이 움직였는가**다.
