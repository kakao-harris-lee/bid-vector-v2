# M9/adr07-pitest rollback

착수 base **`df4976cc`** · **복원 원천 `df4976cc`** · **실측 HEAD `eab07b5b`**.

**원천이 착수 base 와 같다.** 이 브랜치는 `origin/main` 을 **한 번도 합치지 않았다**
(`git log --merges df4976cc..HEAD` 가 0 줄) — 그래서 base 에서 복원해도 남의 변경을 지우지
않는다. 합친 이력이 생기면 원천을 그 병합 커밋의 둘째 부모(고정 SHA)로 옮기고 다시 잰다.

**공유 파일 하나**(`docs/discovery/capability-map.md`, 계약 갱신으로 들어왔다)의 전제를 따로
쟀다 — 그 파일은 머지된 PR #133 이 112줄 **추가**로 바꾼 자리이기도 하다.

| 전제 | 실측 |
| --- | --- |
| 원천에 이 slice 가 없다 | `df4976cc` 판의 그 파일에 `adr07-pitest`·`OPEN-ADR07-` **0 건** |
| 이 브랜치에 #133 추가분이 **없다** | 원천 판과 HEAD 판의 줄 수가 둘 다 **3858**(내 변경이 1:1 행 교체이므로 같아야 한다). 즉 base 에서 복원해도 #133 의 112줄을 지울 수 없다 — 애초에 이 브랜치에 없다 |

**머지 뒤에는 이 §1 이 쓰이지 않는다.** 머지 뒤 되돌림은 **PR revert** 다(그때는 #133 과 내
변경이 같은 트리에 있고, base 복원이 남의 112줄을 지운다). 머지 전에 `origin/main` 을 합치는
라운드가 생기면 원천을 그 병합 커밋의 둘째 부모로 다시 고정하고 ①~③ 을 다시 잰다.

evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」가 아니다 — 대조해야 하는 것은
**그 사이에 되돌림 대상이 움직였는가**이고 그 명령은 §4 다.

## 1. 되돌리는 법

**range revert 가 아니라 in_scope 경로 한정 복원**이다. 목록은 손으로 적지 않고 기계가 낸다.

```
BASE=df4976cc
EXCL=':(exclude)reports/evidence/m9/adr07-pitest'   # 자기 자신은 되돌릴 수 없다

mapfile -t MINE < <(git log --no-merges --format= --name-only $BASE..HEAD -- . "$EXCL" | sort -u)
mapfile -t MODIFIED < <(for f in "${MINE[@]}"; do git cat-file -e "$BASE:$f" 2>/dev/null && echo "$f"; done)
mapfile -t ADDED    < <(for f in "${MINE[@]}"; do git cat-file -e "$BASE:$f" 2>/dev/null || echo "$f"; done)

git restore --source=$BASE --staged --worktree -- "${MODIFIED[@]}"
git rm -q -f -- "${ADDED[@]}"
```

「있음/없음」을 `--diff-filter` 가 아니라 **`git cat-file -e`** 로 가른다: 필터는 범위의 양 끝을
비교하므로 병합이 끼면 남의 변경을 M 으로 센다. 원천에 그 경로가 있는지만 물으면 병합과 무관하다.

`git rm -f`(단순 `rm` 이 아니라)인 이유: 색인까지 지워야 복원 뒤 색인이 원천과 같아진다.

## 2. 실측 — 버릴 clone(`git clone --no-hardlinks`), HEAD `eab07b5b`

| 단계 | 실측 |
| --- | --- |
| ① 명령 종료 코드 | `restore` **0** · `git rm` **0** |
| ② 계수 | 내 커밋 경로 **26**(evidence 제외) = 복원 **15**(M) · 삭제 **11**(A) — 기계 목록과 같다. 수정 라운드가 둘을 더했다(회귀 test 하나 · `tools/workflow-step-refs-check.sh`) |
| ③ 결과 트리 == `df4976cc` 트리 | 작업 트리 **0 파일** · 색인 **0 파일** · 미추적 **0** |
| ④⑤⑥ 복원 트리의 컴파일·test·게이트 | **트리 동일성으로 갈음** — 아래 |
| ⑦ 원천과 남는 차이 | 실측 시점에 `reports/evidence/m9/adr07-pitest/` **넷**뿐(의도된 제외 — `EXCL` 이 디렉터리 전체를 제외한다). 그 수는 evidence 커밋마다 자라고, 유효성은 §4 의 술어가 되돌림 대상에서 evidence 를 빼므로 흔들리지 않는다 |

### ④⑤⑥ 의 갈음 — 「HEAD 초록」이 아니라 트리 동일성

복원한 트리는 **`df4976cc` 의 트리와 파일 단위로 같다**(③ 의 0 파일 셋). 그 커밋은 병합된
main(PR #123 머지 커밋)이고 CI 가 초록으로 통과한 자리다. 그래서 복원 트리에서 `check` 를 다시
돌리는 것은 같은 트리를 다시 재는 일이다.

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
| 수정 라운드가 들여온 둘 — `MutationReportGateUpToDateTest` · `max-workers=1` | **0** |
| capability-map 전용 — `닫혔다 — M9/adr07-pitest` | **0** |
| 같은 행의 **반대 방향** — 취소선 없는 `OPEN-ADR-07` 행(되살아나야 한다) | **1** |

**통제군**: 제외 없이 세면 `mutationCheck` 가 **3 파일**에서 잡히고 그 셋이 전부
`reports/evidence/m9/adr07-pitest/` 아래다 — 제외가 지우는 것이 evidence 뿐이고 코드·설정에
남은 자리가 없다는 뜻이다. 제외가 실제 잔존을 가리고 있지 않다.

| 남의 표지(남아 있어야 한다 · 같은 제외) | 복원 뒤 |
| --- | --- |
| `pitest-engine`(이 slice 가 **지운** 카탈로그 alias — 되살아나야 한다) | 1 |
| `conventionCoverageGate`(1A) | 4 |
| `cpdCheckObserved`(1C) | 6 |
| `leakPatternGate` | 129 |
| `OPEN-ADR-16`(같은 ADR 의 다른 절) | 15 |
| `MIN_JOBS`(`workflow-step-refs-check.sh` — 주석만 고쳤고 하한은 남는다) | 1 |
| `OPEN-ADR-15`(capability-map G6 의 이웃 행) | 4 |
| `OPEN-ADR-08`(capability-map 에서 내 행이 본뜬 **선례 행**) | 11 |

표지로 맨 `pitest` 를 쓸 수 없다 — base 의 카탈로그가 이미 그 좌표를 들고 있어 **남의 줄까지
세게 된다**. 그래서 이 slice 가 처음 들여온 이름들로 골랐다. `pitest-engine` 행은 **방향이 반대인
대조**다: 내가 지운 것이 복원으로 돌아오는지를 본다.

공유 파일 쪽에서는 **줄 수**가 보존의 대조다 — HEAD 의 `capability-map.md` 는 원천 **3858** 줄에
내 **+6**(G6 두 행 + 머리말 증감 네 줄)을 더한 **3864** 줄이고, 복원 뒤 다시 **3858** 이다. 내
변경이 「1:1 행 교체 + 추가 6」이므로 복원 뒤 수가 3858 이 아니면 다른 행이 함께 움직였다는 뜻이다.

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
git diff --name-only eab07b5b..<판정 SHA> -- \
  $(git log --no-merges --format= --name-only df4976cc..eab07b5b \
      -- . ':(exclude)reports/evidence/m9/adr07-pitest' | sort -u)
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 **미검증**이다(그때 ①~③ 을 다시 잰다).
동결 보고가 그 확인 결과를 적는다.
