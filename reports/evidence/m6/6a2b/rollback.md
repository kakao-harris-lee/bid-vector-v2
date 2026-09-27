# M6/6A-2b — 되돌림

`base`: `6f0b21f0`(PR #47 6A-2a 머지). **실측 HEAD: `b2c839ba`** — 수정 라운드 2 의 마지막 산출물 커밋.

라운드마다 재산출·재실측한다. 첫 실측(`59a8bf8d`) 뒤 공유 파일 `milestone-6.md` 가 **이 slice 밖의
커밋으로 움직여** 그 실측이 미검증이 됐고, 그때 **원래 절차(커밋 해시 hunk 격리)가 실제로 깨졌다**
(2단계). 지금 실측은 수정 라운드 1 의 마지막 산출물 커밋에서 같은 절차를 다시 돌린 것이다.

검증자가 볼 것은 실측 HEAD 와 판정 SHA 의 일치가 아니라(evidence 커밋은 언제나 뒤에 온다) **그 사이에
되돌림 대상이 움직였는가**다. 술어의 경로는 **되돌림 대상에서 evidence 를 뺀 것**이다(L-r2-5 정정) —
`reports/evidence/m6/6a2b/**` 는 1단계 복원 목록에 들지만 evidence 커밋이 늘 뒤에 오므로, 그 경로를
술어에 넣으면 출력이 영원히 비지 않아 문면이 구조적으로 모순이 된다. 그 경로의 이동은 되돌림 결과를
바꾸지 않는다(base 에 없던 파일이라 어느 시점에서든 「삭제」로 같다):

```
git diff --name-only b2c839ba..<판정 SHA> -- <아래 목록에서 reports/evidence/ 를 뺀 경로들>
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 다시 재산출해야 한다.

## 되돌리는 경로 (기계 산출, 라운드마다 재산출)

```
git diff --name-status 6f0b21f0..HEAD
```

실측 HEAD 기준 **73** 이다(추가 31 · 수정 41 · 삭제 1). 그 가운데 `milestone-6.md` 하나가 **공유 파일**이라 따로
다루고(2단계), 나머지 72 는 in_scope 경로 한정 복원 한 번으로 돌아간다. range revert 가 아니다.

## 절차

**1단계 — in_scope 경로 한정 복원(공유 파일 제외).**

```
base=6f0b21f0
mapfile -t paths < <(git diff --name-only $base..HEAD | grep -v '^milestone-6.md$')
git restore --source=$base --staged --worktree -- "${paths[@]}"
```

이 한 번이 **추가 파일 삭제와 수정 파일 복원을 함께** 한다(추가 파일도 index 에 있으므로 pathspec 이
맞는다 — 실측에서 `D` 23 · `M` 26 이 한 번에 나왔다).

**2단계 — 공유 파일은 수동 문단 삭제.**

**커밋 해시 hunk 격리는 이제 쓸 수 없다 — 실측으로 깨졌다.** 이 slice 의 착수 커밋이 `milestone-6.md` 에
더한 것은 **문단 둘**(운영자 결정 넷 · 6A-2b 착수)인데, 그 뒤 들어온 다른 축의 커밋이 **그 둘 사이에**
자기 문단을 끼워 넣었다. 그래서 `git diff <착수 커밋>~1..<착수 커밋> -- milestone-6.md | git apply -R` 은
`patch does not apply` 로 실패한다(실측). `--3way` 는 exit 0 이지만 **충돌 표식을 남긴 채**(`UU`) 끝나
갈음이 되지 않는다(실측) — 그대로 커밋하면 문서가 깨진다.

수동 절차(실측 통과): 착수 커밋이 더한 줄을 뽑아 **문단 단위로** 지운다. 각 문단이 파일에 정확히 한 번
나오는지 먼저 단언한다 — 두 번 이상이면 멈추고 눈으로 본다.

```
git show 789aabf1 -- milestone-6.md \
  | sed -n '/^@@/,$p' | grep '^+' | grep -v '^+++' | sed 's/^+//' > /tmp/added.txt
```

이어서 아래 다섯 줄을 임시 스크립트로 돌린다(저장소에 파일을 남기지 않는다):

```
import pathlib
target = pathlib.Path("milestone-6.md"); text = target.read_text()
for para in [p for p in pathlib.Path("/tmp/added.txt").read_text().split("\n\n") if p.strip()]:
    chunk = para if para.endswith("\n") else para + "\n"
    assert text.count(chunk) == 1, "문단이 한 번이 아니다 — 멈추고 눈으로 본다"
    text = text.replace(chunk + "\n", "", 1) if (chunk + "\n") in text else text.replace(chunk, "", 1)
target.write_text(text)
```

**3단계 — 확인은 두 방향이다.** 「내 줄이 사라졌다」와 「남의 줄이 남았다」를 둘 다 본다. 이번 실측은
그 둘째 방향이 **처음으로 실제 내용을 가진** 경우다(끼어든 문단이 실재한다).

```
grep -c '6A-2b 착수' milestone-6.md              # 0
grep -c '6A-2a 머지 뒤 잔여 점검' milestone-6.md   # 0
git diff 6f0b21f0 -- milestone-6.md              # 남은 것은 다른 축의 hunk 그 자체뿐
```

## 실측 (버릴 clone, `실측 HEAD` 에서)

임시 clone 에서 위 절차를 그대로 돌렸다. 갈음은 「HEAD 초록」이 아니라 **트리 내용**이다.

| # | 항목 | 결과 |
|---|---|---|
| ① | 1단계 명령 exit | 0 |
| ② | D/M 수 | `D` 31 · `M` 41 (+ 2단계로 공유 파일 1) |
| ③ | base 대비 잔여 | `milestone-6.md` **한 파일, 8 줄** — 그 8 줄이 다른 축 커밋의 추가분과 **줄 단위로 동일**함을 확인했다. 그 밖의 경로는 잔여 0 |
| ④ | 컴파일 | `check` 안에서 9 모듈 전건 통과 |
| ⑤ | test | 같은 명령 — 전건 통과 |
| ⑥ | 게이트 | `./gradlew --no-daemon check` exit 0(보완 경로 불필요) |

③ 이 이번에는 「빈 출력」이 아니다 — 그것이 **옳다**. 되돌린 트리는 base 트리가 아니라 **base + 이 slice
밖의 커밋**이어야 하고, 잔여가 정확히 그 커밋의 추가분과 같다는 것이 「내 것만 지웠다」의 증거다.

첫 실측(`59a8bf8d`, 공유 파일이 움직이기 전)에서는 ③ 이 빈 출력이었다. 그 뒤 세 번은 같은 형태다 —
잔여가 정확히 다른 축 커밋의 추가분과 같다.

## 부분 비활성화 (전체 되돌림이 과할 때)

쓰기 경로만 끄려면 컨트롤러와 편집 실행기 빈을 **함께** 뺀다 — 컨트롤러만 남기면 의존을 찾지 못해
기동이 실패한다. 그 뒤 읽기(`GET /api/strategy`)와 dry-run 은 그대로 뜬다. 스키마 변경이 없으므로
(이 slice 는 마이그레이션을 만들지 않았다) 저장 층은 되돌릴 것이 없다.
