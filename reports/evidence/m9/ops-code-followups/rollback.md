# M9/ops-code-followups rollback

원천 **`c21adb2a`** — PR #144 수정 라운드에서 병합한 main 의 **고정 SHA** 다(움직이는 `origin/main` 이
아니다). 그 SHA 에 이 slice 의 커밋은 **없다**(착수 base 는 `6e6f953c` 였고, 그 뒤 #143 이 머지되며
main 이 여기로 왔다).

## 1. 되돌리는 법

**range revert 가 아니라 in_scope 경로 한정 복원**이다. 목록은 손으로 적지 않고 기계가 낸다.

```
BASE=c21adb2a
EXCL=':(exclude)reports/evidence/m9/ops-code-followups'   # 자기 자신은 되돌릴 수 없다

mapfile -t MINE < <(git log --no-merges --format= --name-only $BASE..HEAD -- . "$EXCL" | sort -u)
mapfile -t MODIFIED < <(for f in "${MINE[@]}"; do git cat-file -e "$BASE:$f" 2>/dev/null && echo "$f"; done)
mapfile -t ADDED    < <(for f in "${MINE[@]}"; do git cat-file -e "$BASE:$f" 2>/dev/null || echo "$f"; done)

git restore --source=$BASE --staged --worktree -- "${MODIFIED[@]}"
git rm -q -f -- "${ADDED[@]}"
```

「있음/없음」을 `--diff-filter` 가 아니라 **`git cat-file -e`** 로 가른다: 필터는 범위의 양 끝을
비교하므로 병합이 끼면 남의 변경을 M 으로 센다. 원천에 그 경로가 있는지만 물으면 병합과 무관하다.

**목록 다섯** — 수정 넷(`AwardCollectionRunner.kt` · E2E test 파일 · 원 slice evidence 의
`checklist.md`·`scope.md`) + 추가 하나(`CommitRunE2EFixture.kt`). 목록의 건전성은 두 산출을 맞대어
잰다: `git log --no-merges` 산출과 `git diff --name-only` 산출이 `comm -23`·`comm -13` **양쪽 빈
출력**이다(한쪽을 다른 쪽에서 재생하지 않았다).

**`--no-merges` 가 이번엔 아무것도 잃지 않는다.** 범위에 병합 커밋이 하나 있지만(`3615fc10`)
`git show --name-only` 가 그 커밋에 대해 **빈 파일 목록**을 낸다 — 충돌 0 이라 손 해소 줄이 없다는
뜻이다. (해소 줄이 있었다면 그것은 `--no-merges` 목록 밖이고, 그때 기대는 자리는 아래 트리 동일성이다.)

**hunk 격리가 필요 없다.** 공유 파일(원 slice 의 evidence 둘)을 **원천 트리에서 통째로 복원**하기
때문이다 — 그 트리는 main 이 만든 것이라 다른 slice 의 줄이 이미 들어 있고 내 줄만 사라진다.

## 2. 실측 — 버릴 clone(`git clone --no-hardlinks`)에서 `c9c19d88` 체크아웃

| 단계 | 실측 |
| --- | --- |
| ① 명령 exit | `git restore` **0** · `git rm` **0** |
| ② D/M 수 | `D` **1** · `M` **4** (목록 다섯과 같다) |
| ③ 원천과의 diff | `git diff $BASE -- <목록 다섯>` **빈 출력** |
| ④⑤⑥ | 아래 「트리 동일성」으로 갈음 |

### ④⑤⑥ 의 갈음 — 「HEAD 초록」이 아니라 트리 동일성

복원 뒤 **트리 전체**를 원천과 비교하면 다른 경로는 **자기 evidence 디렉터리 넷뿐**이다(그 디렉터리는
목록에서 자기 제외했고, 원천에 없던 새 디렉터리라 겹치는 줄이 없다). 그것까지 지우고 재면
`git write-tree` 가 **원천 트리 해시와 같다**(`c089464a…`).

즉 복원된 트리는 **곧 원천 트리**이므로 ④ compile · ⑤ test · ⑥ 게이트를 그 자리에서 다시 돌릴 필요가
없다 — 원천은 머지된 main 이고 그 자리의 CI 가 초록이다. 갈음의 근거는 종료 코드가 아니라 **파일
SHA 의 동일성**이다. 이 등식은 병합이 들여온 #143 의 파일들이 복원 뒤에도 **그대로 남는다**는 사실까지
함께 잰다(복원이 상류를 건드리지 않는다).

### 표지 계수 — 「내 줄 사라짐」과 「남의 줄 남음」 둘 다

표지는 **복원 목록 파일 안에서만** 센다(어휘 grep 을 저장소 전체에 걸면 상류가 같은 낱말을 쓰는
자리까지 세어 대조가 공허해진다 — 이 slice 의 KDoc 어휘가 실제로 그렇다: 「쿼터·차단」은 복원 목록
밖의 production 파일들이 이미 쓰는 말이다).

| 표지 | 복원 전 | 복원 뒤 |
| --- | --- | --- |
| 내 것(새 fixture 이름 · 고친 KDoc 문면 둘 · 정정 주석 머리) | **10**(다섯 파일에 1·2·2·4·1) | **0** · 추가 파일은 **없음** |
| 남의 것 — 원 checklist 의 「여섯째 병합」 | 2 | **2**(보존) |
| 남의 것 — base KDoc 의 원래 문면 | 0 | **1**(되살아남) |

## 3. 코드를 되돌리지 않고 끄는 법

**해당 없음 — 끌 것이 없다.** 이 slice 는 production 거동을 만들지 않는다: test 파일의 구조 ·
production KDoc · evidence 문면뿐이고, 그 소스가 내는 class 둘의 **명령 스트림이 base 와 같다**
(`commands.md` §5). 설정 스위치도, 배선 변경도, 마이그레이션도 없다.

## 4. 실측 HEAD 와 판정 SHA 사이가 움직였는가

**실측 HEAD: `c9c19d88`** (산출물 + 병합 + PR #144 지적 반영 + commands·checklist 까지 담은 커밋).

`git diff --name-only c9c19d88..<판정 SHA> -- <복원 목록 다섯>` **빈 출력** — 이 파일을 담는 커밋은
자기 evidence 디렉터리만 만지고 그 디렉터리는 복원 목록 **밖**이다. 「실측 HEAD == 판정 SHA」를 요구하는
것이 아니다(evidence 커밋은 언제나 뒤에 온다).

**그 빈 출력이 공허하지 않다**: 같은 술어를 원천부터 재면 `git diff --name-only c21adb2a..c9c19d88 --
<같은 다섯>` 이 **다섯 줄**을 낸다. 술어는 이 경로들에 대해 실제로 발화한다.

## 하네스 레인 변경

없다. `git diff --name-only c21adb2a..HEAD -- .claude/ config/ build-logic/ .github/` **빈 출력** —
정책 파일·게이트 로직·CI·스킬을 건드리지 않았다. 병합이 들여온 `config/quality/` 두 파일은 #143 의
것이고 축어로 왔다(`commands.md` §2a). 새 test class 가 생기지 않아
`config/quality/gate-tests.properties` 등재도 늘지 않았고, 그 사실은 `gateRegistrationGate` 의
양방향 계수가 든다(병합 뒤에도 `app` 133 = 133).
