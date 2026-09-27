# M6/6A-2b — 되돌림

`base`: `6f0b21f0`(PR #47 6A-2a 머지). **실측 HEAD: `f063fa30`** — 이 slice 의 마지막 산출물 커밋.

라운드마다 재산출·재실측한다. 공유 파일 `milestone-6.md` 는 **이 slice 밖의 커밋으로도 움직이므로**
되돌림 대상이 이동하면 앞선 실측은 미검증이 된다 — 그래서 산출물 커밋이 늘 때마다 다시 돈다.

검증자가 볼 것은 실측 HEAD 와 판정 SHA 의 일치가 아니라(evidence 커밋은 언제나 뒤에 온다) **그 사이에
되돌림 대상이 움직였는가**다. 술어의 경로는 **되돌림 대상에서 evidence 를 뺀 것**이다(L-r2-5 정정) —
`reports/evidence/m6/6a2b/**` 는 1단계 복원 목록에 들지만 evidence 커밋이 늘 뒤에 오므로, 그 경로를
술어에 넣으면 출력이 영원히 비지 않아 문면이 구조적으로 모순이 된다. 그 경로의 이동은 되돌림 결과를
바꾸지 않는다(base 에 없던 파일이라 어느 시점에서든 「삭제」로 같다):

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- <아래 목록에서 reports/evidence/ 를 뺀 경로들>
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 다시 재산출해야 한다.

## 되돌리는 경로 (기계 산출, 라운드마다 재산출)

```
git diff --name-status 6f0b21f0..HEAD
```

실측 HEAD 기준 **80** 이다(추가 36 · 수정 44). 그 가운데 `milestone-6.md` 하나가 **공유 파일**이라 따로
다루고(2단계), 나머지 79 는 in_scope 경로 한정 복원 한 번으로 돌아간다. range revert 가 아니다.

## 절차

**1단계 — in_scope 경로 한정 복원(공유 파일 제외).**

```
base=6f0b21f0
mapfile -t paths < <(git diff --name-only $base..HEAD | grep -v '^milestone-6.md$')
git restore --source=$base --staged --worktree -- "${paths[@]}"
```

이 한 번이 **추가 파일 삭제와 수정 파일 복원을 함께** 한다(추가 파일도 index 에 있으므로 pathspec 이
맞는다 — 실측에서 `D` 36 · `M` 43 이 한 번에 나왔다).

**2단계 — 공유 파일은 수동 문단 삭제.**

**커밋 해시 hunk 격리는 이제 쓸 수 없다 — 실측으로 깨졌다.** 이 slice 가 `milestone-6.md` 에 더한 것은 착수 커밋의
**문단 둘**(운영자 결정 넷 · 6A-2b 착수)과 종결 커밋의 **문단 넷**인데, 그 사이에 다른 축의 커밋이
자기 문단을 끼워 넣었다. 그래서 `git diff <착수 커밋>~1..<착수 커밋> -- milestone-6.md | git apply -R` 은
`patch does not apply` 로 실패한다(실측). `--3way` 는 exit 0 이지만 **충돌 표식을 남긴 채**(`UU`) 끝나
갈음이 되지 않는다(실측) — 그대로 커밋하면 문서가 깨진다.

수동 절차(실측 통과): **이 slice 가 공유 파일에 더한 커밋 전부**의 줄을 뽑아 **문단 단위로** 지운다.
오늘 그것은 둘이다 — 착수(`789aabf1`)와 종결(`3df31402`). 어느 하나만 지우면 다른 하나가 남으므로
목록을 `git log` 로 기계 산출한다. 각 문단이 파일에 정확히 한 번 나오는지 먼저 단언한다 — 두 번
이상이면 멈추고 눈으로 본다.

```
base=6f0b21f0
mapfile -t mine < <(git log --format=%H --author-date-order $base..HEAD -- milestone-6.md \
  | while read -r sha; do git show --format=%s -s "$sha" | grep -qiE '6a-?2b' && echo "$sha"; done)
for sha in "${mine[@]}"; do
  git show "$sha" -- milestone-6.md | sed -n '/^@@/,$p' | grep '^+' | grep -v '^+++' | sed 's/^+//'
  echo
done > /tmp/added.txt
```

이어서 아래를 임시 스크립트로 돌린다(저장소에 파일을 남기지 않는다). **문자열 치환이 아니라 줄
블록 삭제**다 — 문단을 문자열로 지우면 뒤따르는 빈 줄을 함께 먹거나 남겨 **다른 축의 빈 줄 하나가
어긋난다**(실측: 잔여가 8 줄이 아니라 7 줄). 각 문단의 줄 서열을 찾아 **그 앞의 빈 줄 하나까지**
지우면 남는 문단 사이의 구분이 정확히 보존된다.

```
import pathlib
lines = pathlib.Path("milestone-6.md").read_text().split("\n")
paras = [p for p in pathlib.Path("/tmp/added.txt").read_text().split("\n\n") if p.strip()]
for para in paras:
    block = [l for l in para.split("\n") if l != ""]
    hits = [i for i in range(len(lines) - len(block) + 1) if lines[i:i + len(block)] == block]
    assert len(hits) == 1, "문단이 한 번이 아니다 — 멈추고 눈으로 본다"
    i = hits[0]
    start = i - 1 if i > 0 and lines[i - 1] == "" else i
    del lines[start:i + len(block)]
pathlib.Path("milestone-6.md").write_text("\n".join(lines))
```

**3단계 — 확인은 두 방향이다.** 「내 줄이 사라졌다」와 「남의 줄이 남았다」를 둘 다 본다. 둘째 방향은
비어 있지 않다 — 다른 축의 문단이 실재한다.

```
grep -c '6A-2b 착수' milestone-6.md              # 0 (착수 문단)
grep -c '6A-2b 종결' milestone-6.md              # 0 (종결 문단)
grep -c '6A-2a 머지 뒤 잔여 점검' milestone-6.md   # 0
git diff 6f0b21f0 -- milestone-6.md              # 남은 것은 다른 축 커밋들의 순효과뿐
```

마지막 확인은 **줄 단위 동일성**이다 — 잔여 diff 가 다른 축 커밋들의 순효과와 같아야 한다.

```
diff <(git diff 6f0b21f0 -- milestone-6.md | grep -E '^[+-]' | grep -vE '^(\+\+\+|---)') \
     <(git diff 3750e6a6~1 dd7b3133 -- milestone-6.md | grep -E '^[+-]' | grep -vE '^(\+\+\+|---)')
```

## 실측 (버릴 clone, `실측 HEAD` 에서)

임시 clone 에서 위 절차를 그대로 돌렸다. 갈음은 「HEAD 초록」이 아니라 **트리 내용**이다.

| # | 항목 | 결과 |
|---|---|---|
| ① | 1단계 명령 exit | 0 |
| ② | D/M 수 | `D` 36 · `M` 43 (+ 2단계로 공유 파일 1) |
| ③ | base 대비 잔여 | `milestone-6.md` **한 파일, 8 줄** — 그 8 줄이 다른 축 커밋의 추가분과 **줄 단위로 동일**함을 확인했다. 그 밖의 경로는 잔여 0 |
| ④ | 컴파일 | `check` 안에서 9 모듈 전건 통과 |
| ⑤ | test | 같은 명령 — 전건 통과 |
| ⑥ | 게이트 | `./gradlew --no-daemon check` exit 0(보완 경로 불필요) |

③ 이 「빈 출력」이 아니다 — 그것이 **옳다**. 되돌린 트리는 base 트리가 아니라 **base + 이 slice
밖의 커밋**이어야 하고, 잔여가 정확히 그 커밋들의 **순효과**와 같다는 것이 「내 것만 지웠다」의 증거다.
공유 파일을 만진 다른 축 커밋은 이제 **둘**이고(뒤 커밋이 앞 커밋의 문단을 고쳤다) 대조는 그 둘의
순효과(`git diff <첫 커밋>~1 <마지막 커밋> -- milestone-6.md`)와 견준다.

공유 파일이 움직이기 전이라면 ③ 은 빈 출력이어야 한다. 지금은 아니고, 잔여가 정확히 다른 축 커밋의
추가분과 **줄 단위로 같다**는 것이 그 자리를 대신한다.

## 부분 비활성화 (전체 되돌림이 과할 때)

쓰기 경로만 끄려면 컨트롤러와 편집 실행기 빈을 **함께** 뺀다 — 컨트롤러만 남기면 의존을 찾지 못해
기동이 실패한다. 그 뒤 읽기(`GET /api/strategy`)와 dry-run 은 그대로 뜬다. 스키마 변경이 없으므로
(이 slice 는 마이그레이션을 만들지 않았다) 저장 층은 되돌릴 것이 없다.
