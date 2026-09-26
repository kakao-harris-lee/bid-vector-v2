# M6/6A-2b — 되돌림

`base`: `6f0b21f0`(PR #47 6A-2a 머지). **실측 HEAD: `59a8bf8d`** — 이 slice 의 마지막 산출물 커밋.
evidence 커밋은 언제나 그 뒤에 오므로 실측 HEAD 와 판정 SHA 는 영원히 다르다. 검증자가 볼 것은
그 둘의 일치가 아니라 **그 사이에 되돌림 대상이 움직였는가**다:

```
git diff --name-only 59a8bf8d..<판정 SHA> -- <아래 목록의 경로들>
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 미검증이다.

## 되돌리는 경로 (기계 산출, 라운드마다 재산출)

```
git diff --name-status 6f0b21f0..HEAD
```

이 slice 가 만진 경로는 **47** 이다(추가 20 · 수정 27). 그 가운데 `milestone-6.md` 하나가 **공유 파일**이라
따로 다루고(아래 2단계), 나머지 46 은 in_scope 경로 한정 복원 한 번으로 돌아간다. range revert 가 아니다.

## 절차

**1단계 — in_scope 경로 한정 복원(공유 파일 제외).**

```
base=6f0b21f0
mapfile -t paths < <(git diff --name-only $base..HEAD | grep -v '^milestone-6.md$')
git restore --source=$base --staged --worktree -- "${paths[@]}"
```

이 한 번이 **추가 파일 삭제와 수정 파일 복원을 함께** 한다(추가 파일도 index 에 있으므로 pathspec 이
맞는다 — 실측에서 `D` 20 · `M` 26 이 한 번에 나왔다).

**2단계 — 공유 파일은 커밋 해시 hunk 격리.** `milestone-6.md` 에는 이 slice 의 착수 문단 말고 다른 slice 의
줄이 함께 있다. range revert 나 파일 단위 복원은 남의 줄까지 지운다.

```
git diff 789aabf1~1..789aabf1 -- milestone-6.md | git apply -R
```

`--3way` 는 쓰지 않는다(자동 해소에 실패하는 형태가 이 저장소에 이미 있었다). 충돌하면 수동으로:
그 커밋이 더한 착수 문단(6A 절 아래)만 지우고 앞뒤 slice 의 문단은 손대지 않는다. 이 slice 는
`milestone-6.md` 를 **착수 커밋 한 번만** 건드렸으므로 역적용도 한 번이다.

**3단계 — 확인은 두 방향이다.** 「내 줄이 사라졌다」와 「남의 줄이 남았다」를 둘 다 본다:

```
git diff --stat 6f0b21f0        # 빈 출력이어야 한다(트리 동일성)
git log --oneline -3 -- milestone-6.md
```

## 실측 (버릴 clone, `실측 HEAD` 에서)

임시 clone 에서 위 절차를 그대로 돌렸다. 갈음은 「HEAD 초록」이 아니라 **트리 동일성**이다.

| # | 항목 | 결과 |
|---|---|---|
| ① | 1단계 명령 exit | 0 |
| ② | D/M 수 | `D` 20 · `M` 26 (+ 2단계로 공유 파일 1) |
| ③ | `git diff --stat 6f0b21f0` | 빈 출력 — **되돌린 트리가 base 트리와 같다** |
| ④ | 컴파일 | `check` 안에서 9 모듈 전건 통과 |
| ⑤ | test | 같은 명령 — 전건 통과 |
| ⑥ | 게이트 | `./gradlew --no-daemon check` exit 0(보완 경로 불필요) |

④⑤⑥ 은 한 명령(`check`)이 함께 든다 — 되돌린 트리가 base 와 **같으므로** 그 초록은 base 의 초록과
같은 것이다. 그것이 이 실측이 주장하는 전부다: 되돌림이 **부분적이지 않다**.

## 부분 비활성화 (전체 되돌림이 과할 때)

쓰기 경로만 끄려면 컨트롤러와 편집 실행기 빈을 **함께** 뺀다 — 컨트롤러만 남기면 의존을 찾지 못해
기동이 실패한다. 그 뒤 읽기(`GET /api/strategy`)와 dry-run 은 그대로 뜬다. 스키마 변경이 없으므로
(이 slice 는 마이그레이션을 만들지 않았다) 저장 층은 되돌릴 것이 없다.
