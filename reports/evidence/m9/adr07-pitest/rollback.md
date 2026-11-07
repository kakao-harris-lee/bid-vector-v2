# M9/adr07-pitest rollback

착수 base **`df4976cc`** · **복원 원천 `56bd33ea`**(이 브랜치가 마지막으로 합친 main 의
**고정 SHA** = 병합 커밋 `dccb87af` 의 둘째 부모) · **실측 HEAD `dccb87af`**(이 slice 의 마지막
산출물 커밋 = 현 판정 SHA).

**원천은 이제 §1 블록이 파생한다** — 문면에 적은 SHA 가 한 병합 뒤처져 「남의 머지분을 되돌리는
목록」을 만든 적이 있다(v r4 M-1). 아래 머리말·§2·§4 의 수는 **그 파생이 낸 값으로 실측한 기록**
이다.

**원천을 세 번 더 옮겼다** — `3ad4f3f6`(둘째) · `97ed49c4`(셋째) · `56bd33ea`(넷째). 매번 그
병합 커밋의 둘째 부모로 고정하고 ①~③ 을 다시 쟀다.

**수정 라운드 3 에서 복원 목록이 움직였다** — main 소스 하나(`MutationReportLocation.kt`)가
늘어 **26 → 27** 경로가 됐다. 그래서 ①~③ 과 표지 계수를 그 커밋에서 **다시** 쟀다. 원천은
그대로다(그 뒤로 더 합치지 않았다).

**원천이 착수 base 가 아니다.** 두 번 합쳤다 — main `e12c0485`(#130~#133) 뒤 main `3ad4f3f6`(#135).
base 에서 복원하면 **그 남의 변경까지 지운다** — 실물이 있다: main 이 `config/quality/gate-tests.properties`
의 `app` 블록을 **+3** 했고 그 파일은 내 복원 목록 안에도 있다(내가 `build-logic` 블록을 고쳤다).
`df4976cc` 에서 복원하면 그 셋이 사라진다. 그래서 ⓐ 복원 목록은 **내 커밋만**(병합 제외)에서
산출하고 ⓑ 원천은 합친 main 의 고정 SHA 다 — 움직이는 `origin/main` 을 쓰지 않는다.
둘째 병합에서 그 규칙을 그대로 적용해 원천을 `3ad4f3f6` 으로 옮기고 ①~③ 을 **다시** 쟀다.
또 합치면 또 옮긴다.

**겹치는 공유 파일 셋**의 합집합을 산술로 댔다 — 「충돌 0」은 본문이 접히지 않았다는 증거가
아니다(저장소 기억 「병합 합집합 함정」). **첫 병합**은 세 파일이 겹쳐 base + 양쪽 증분과 댔고
(아래 표), **둘째 병합**은 겹치는 파일이 **0** 이라 집합 등식 셋으로 댔다(`commands.md` §11.4:
`HEAD ↔ 3ad4f3f6` 차이 == 내 경로 · main 증분 14 파일 차이 0 · 내 파일 30 의 병합 전후 차이 0).

| 파일 | base | 내 증분 | main 증분 | 병합 뒤 | 기대 |
| --- | --- | --- | --- | --- | --- |
| `ci.yml` 줄 | 1026 | +52 | +4 | **1082** | 1082 |
| `capability-map.md` 줄 | 3858 | +6 | +113 | **3977** | 3977 |
| `gate-tests` `build-logic` 등재 | 27 | +4 | 0 | **31** | 31 |
| `gate-tests` `app` 등재 | 121 | 0 | +3 | **124** | 124 |

형태도 함께 쟀다 — CI job **5**(`mutation` 포함) · step id 15 **중복 0** · 매달린 `steps.<id>`
**0** · 등록부 **중복 0**, `build-logic` 블록 **사전순** · capability-map G6 행 **10**(base 8 + 내
2)에 표 파이프 수 **일정** · `### 12.3`(main) 과 `§14.2` 의 내 해소 행이 **둘 다** 있고 취소선 없는
`OPEN-ADR-07` 행은 **0**.

**집계 머리말은 겹치지 않았다.** main 쪽 hunk 는 3518·3793·3815 이고 내 것은 3218·3376·3607 이라
세 자리 모두 떨어져 있다. 더구나 main 의 §12.3 은 스스로 *"이 절의 OPEN 은 §12 머리의 활성 집계에
들어가지 않는다"* 고 선언해 머리의 수를 움직이지 않았다 — 그래서 활성 수는 양쪽 다 **51** 이고
증감 줄은 내 **+2** 하나만 더해졌다(규약: 각 레인은 자기 증감만, 합산은 머지하는 쪽).

evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」가 아니다 — 대조해야 하는 것은
**그 사이에 되돌림 대상이 움직였는가**이고 그 명령은 §4 다.

## 1. 되돌리는 법

**range revert 가 아니라 in_scope 경로 한정 복원**이다. 목록은 손으로 적지 않고 기계가 낸다.

```
# 원천을 **좌표로 적지 않고 파생한다.** 문면에 SHA 를 적었더니 한 병합 뒤처졌고, 그 상태로
# 실행하면 목록이 부풀어 **남의 머지된 PR** 을 되돌린다(v r4 M-1 에서 실측 — 27 → 35 경로,
# 결과 트리가 adapters 넷·등록부 주석·남의 evidence 넷만큼 어긋났다). 머지 **전**에만 유효하다 —
# 머지 뒤 되돌림은 PR revert 다(하네스 규율).
MAIN=$(git rev-parse "$(git rev-list --merges -1 HEAD)^2")   # 마지막 병합의 둘째 부모 = 합친 main
git merge-base --is-ancestor "$MAIN" HEAD || { echo "원천이 이 브랜치의 조상이 아니다: $MAIN"; exit 1; }
echo "복원 원천: $MAIN"

EXCL=':(exclude)reports/evidence/m9/adr07-pitest'   # 자기 자신은 되돌릴 수 없다

mapfile -t MINE < <(git log --no-merges --format= --name-only $MAIN..HEAD -- . "$EXCL" | sort -u)
mapfile -t MODIFIED < <(for f in "${MINE[@]}"; do git cat-file -e "$MAIN:$f" 2>/dev/null && echo "$f"; done)
mapfile -t ADDED    < <(for f in "${MINE[@]}"; do git cat-file -e "$MAIN:$f" 2>/dev/null || echo "$f"; done)

git restore --source=$MAIN --staged --worktree -- "${MODIFIED[@]}"
git rm -q -f -- "${ADDED[@]}"
```

「있음/없음」을 `--diff-filter` 가 아니라 **`git cat-file -e`** 로 가른다: 필터는 범위의 양 끝을
비교하므로 병합이 끼면 남의 변경을 M 으로 센다. 원천에 그 경로가 있는지만 물으면 병합과 무관하다.

`git rm -f`(단순 `rm` 이 아니라)인 이유: 색인까지 지워야 복원 뒤 색인이 원천과 같아진다.

## 2. 실측 — 버릴 clone(`git clone --no-hardlinks`), HEAD `dccb87af`(넷째 병합 뒤)

| 단계 | 실측 |
| --- | --- |
| ① 명령 종료 코드 | `restore` **0** · `git rm` **0** |
| ② 계수 | 내 커밋 경로 **27**(evidence 제외) = 복원 **15**(M) · 삭제 **12**(A) — 기계 목록과 같다. 수정 라운드들이 셋을 더했다(회귀 test 하나 · `tools/workflow-step-refs-check.sh` · 리포트 자리 헬퍼) |
| ③ 결과 트리 == `56bd33ea` 트리 | 복원 대상 27 경로의 차이 **0**(작업 트리·색인 둘 다) · 미추적 **0**. 트리 전체로 보면 남는 차이는 ⑦ 의 내 evidence 넷뿐이다 |
| ④⑤⑥ 복원 트리의 컴파일·test·게이트 | **트리 동일성으로 갈음** — 아래 |
| ⑦ 원천과 남는 차이 | `reports/evidence/m9/adr07-pitest/` **넷**뿐(의도된 제외 — `EXCL` 이 디렉터리 전체를 제외한다). 유효성은 §4 의 술어가 되돌림 대상에서 evidence 를 빼므로 흔들리지 않는다 |

### ④⑤⑥ 의 갈음 — 「HEAD 초록」이 아니라 트리 동일성

복원한 트리는 **`56bd33ea` 의 트리와 파일 단위로 같다**(③). 그 커밋은 병합된 main(PR #137 머지
커밋)이고 CI 가 초록으로 통과한 자리다. 그래서 복원 트리에서 `check` 를 다시 돌리는 것은 같은
트리를 다시 재는 일이다.

**같지 않은 입력을 이름 붙인다**: ⑦ 의 evidence 파일들이다. 그것들은 빌드 입력이 **맞다** —
`LeakPatternGateTask` 가 `reports/evidence` 를 스캔한다. 그 축은 이 slice 의 HEAD(그 파일들을
**포함한** 상위집합)에서 초록으로 실측돼 있다.

### 표지 계수 — 「내 줄 사라짐」과 「남의 줄 남음」 둘 다

복원 뒤 저장소에서 센 **파일 수**다. 종료 코드가 아니라 이 계수가 완료의 증거다.

**세는 범위에서 evidence 디렉터리를 뺀다** — 복원이 일부러 건드리지 않는 자리이고(⑦), 그
파일들이 내 표지 어휘를 **설명하느라** 담고 있다. 앞 라운드에 이 제외 없이 0 이 나온 것은
그때 남은 evidence 가 `scope.md` 하나였고 그 파일에 우연히 그 어휘가 없었기 때문이다 —
**건전성의 증거가 아니었다.** 지금 술어는 제외를 명시하고 통제군을 붙인다.

| 내 표지(0 이어야 한다 · evidence 제외) | 복원 뒤 |
| --- | --- |
| `mutation-conventions` · `mutationCheck` · `mutationReportGate` · `mutationCoverageGate` · `MutationPolicy` · `PERCENT_DENOMINATOR` · `OPEN-ADR07-` · `pitest-gradlePlugin` · `pitest-junit5-plugin` · `mutation-policy.properties` | **전부 0** |
| 수정 라운드 1 이 들여온 둘 — `MutationReportGateUpToDateTest` · `max-workers=1` | **0** |
| 수정 라운드 2 의 표지 — 게이트 task 의 리포트 property 이름 | **0** |
| 수정 라운드 3 의 표지 — 리포트 자리 헬퍼의 함수 이름 · 그 파일 이름 | **0** · **0** |
| capability-map 전용 — `닫혔다 — M9/adr07-pitest` | **0** |
| 같은 행의 **반대 방향** — 취소선 없는 `OPEN-ADR-07` 행(되살아나야 한다) | **1** |

**표지로 쓸 수 없던 것 하나를 적어 둔다** — 수정 라운드 2 가 쓰는 Gradle 입력 annotation 이름은
build-logic 의 다른 선언 열넷이 같이 쓰므로 세면 남의 줄까지 센다(`pitest` 를 뺀 것과 같은 사유).
그래서 이 라운드의 표지는 그 property 이름이다.

**술어 자체를 한 번 틀렸다가 고쳤다.** 계수 명령을 다시 쓰면서 `--`(옵션 끝) 뒤에 제외 옵션을
두어 grep 이 그것을 **파일 이름으로** 읽었고, 그 판은 내 표지가 「3 파일에 남았다」고 보고했다
(실제로는 전부 내 evidence 였다). 옵션 순서를 고치고 통제군과 함께 다시 쟀다 — 아래가 그 수다.

**통제군**: 제외 없이 세면 `mutationCheck` 가 **3 파일**에서 잡히고 **그 셋 전부가**
`reports/evidence/m9/adr07-pitest/` 아래다(그 디렉터리 밖은 **0** 개). 제외가 지우는 것이
evidence 뿐이고 코드·설정에 남은 자리가 없다는 뜻이다 — 제외가 실제 잔존을 가리고 있지 않다.

| 남의 표지(남아 있어야 한다 · 같은 제외) | 복원 뒤 |
| --- | --- |
| **둘째 병합분**(#135) — `opening-axis` | 8 |
| **첫 병합분** — `### 12.3`(#133) · `OPEN-7E3B-TIME-DISPLAY` · `money-vocab-cross-lang`(#132) | 1 · 4 · 2 |
| **main 머지분의 등록부 증분** — `gate-tests` `app` 블록 등재 | **124**(내 복원이 같은 파일을 만지는데도 그 셋이 남는다. 같은 파일의 `build-logic` 블록은 내 **+4** 가 빠져 원천의 **27** 로 돌아간다 — 「내 줄 사라짐 · 남의 줄 남음」이 한 파일 안에서 갈린다) |
| `web/config/gate-tests.txt`(main 만 만지는 등록부) | 108 줄, 원천과 동일 |
| **겹친 등록부의 main 쪽 증분**(#137 이 더한 등재) | `adapters` **190** · `app` **125** 가 남고 내 `build-logic` +4 는 사라진다(27 로 복귀) — 한 파일 안에서 갈린다 |
| **겹친 capability-map 의 main 쪽 줄**(#137 이 닫은 행) | 취소선 **1** · 내 증감 줄 **0** · 줄 수 **3972**(원천과 같다) |
| #137 의 evidence · 마이그레이션 | 파일 **5** · `V25` **1** |
| `pitest-engine`(이 slice 가 **지운** 카탈로그 alias — 되살아나야 한다) | 1 |
| `conventionCoverageGate`(1A) · `cpdCheckObserved`(1C) · `leakPatternGate` | 1 · 4 · 10 |
| `OPEN-ADR-16`(같은 ADR 의 다른 절) | 9 |
| `MIN_JOBS`(`workflow-step-refs-check.sh` — 주석만 고쳤고 하한은 남는다) | 1 |
| `OPEN-ADR-15`(capability-map G6 의 이웃 행) · `OPEN-ADR-08`(내 행이 본뜬 **선례 행**) | 1 · 3 |

표지로 맨 `pitest` 를 쓸 수 없다 — base 의 카탈로그가 이미 그 좌표를 들고 있어 **남의 줄까지
세게 된다**. 그래서 이 slice 가 처음 들여온 이름들로 골랐다. `pitest-engine` 행은 **방향이 반대인
대조**다: 내가 지운 것이 복원으로 돌아오는지를 본다.

공유 파일 쪽에서는 **줄 수**가 보존의 대조다 — HEAD 의 `capability-map.md` 는 원천 **3971** 줄에
내 **+6**(G6 두 행 + 머리말 증감 네 줄)을 더한 **3977** 줄이고, 복원 뒤 다시 **3971** 이다. 내
변경이 「1:1 행 교체 + 추가 6」이므로 복원 뒤 수가 3971 이 아니면 다른 행이 함께 움직였다는 뜻이다.
`ci.yml` 도 같다 — HEAD **1082** = 원천 **1030** + 내 **+52**, 복원 뒤 **1030**.

## 3. 코드를 되돌리지 않고 끄는 법

둘 있고 **둘 다 diff 에 보인다**(그것이 설계 목표다).

1. **CI job 만 끈다** — `.github/workflows/ci.yml` 의 `mutation` job 을 지운다. `check` 는
   그 job 과 무관하므로(§4 의 S-1) 나머지 게이트가 그대로 선다. 이 slice 가 `check` 에 아무것도
   걸지 않았기 때문에 이 되돌림이 가능하다.
2. **대상 집합을 비운다** — `mutation.modules` 를 줄인다. 다만 **빈 값은 정책 오류로 거부된다**
   (`MutationPolicy.modules` 가 거부한다, `MutationPolicyTest`). 전부 끄려면 모듈마다
   `bidvector.mutation-conventions` 적용도 함께 빼야 하고, 그 둘이 어긋나면 `mutationCoverageGate`
   가 붉는다 — 「반쪽만 끄는」 상태가 조용히 서지 못한다.

**DB·운영 상태를 건드리지 않는다.** 이 slice 는 빌드·CI 배선과 문서뿐이고 마이그레이션도 런타임
스위치도 더하지 않았다.

## 4. 실측 HEAD 와 판정 SHA 사이가 움직였는가

evidence 커밋은 실측 뒤에 오므로 둘은 다르다. 유효성은 **되돌림 대상이 그 사이에 움직였는가**로
본다:

```
git diff --name-only dccb87af..<판정 SHA> -- \
  $(git log --no-merges --format= --name-only 56bd33ea..dccb87af \
      -- . ':(exclude)reports/evidence/m9/adr07-pitest' | sort -u)
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 **미검증**이다(그때 ①~③ 을 다시 잰다).
동결 보고가 그 확인 결과를 적는다.
