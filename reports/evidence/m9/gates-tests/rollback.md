# M9/gates-tests rollback

착수 base **`df4976cc`** · **복원 원천 `8b3b610c`** · **실측 HEAD `1438b428`**.

**재산출 3회** — 라운드마다 다시 쟀고 앞 실측값을 옮겨 적지 않았다. ① `23c2031c` → ② `2c6dae54`
→ ③ `25c16d09`(cr MEDIUM-1 이 산출물을 움직였다) → ④ **`1438b428`**(PR #130 머지분 병합).
복원 목록은 네 번 모두 **같은 5 경로**다.

**원천이 착수 base 가 아니다** — 이 브랜치는 PR #130 머지분(main `8b3b610c`)을 **합쳤고**,
base `df4976cc` 에서 복원하면 **그 남의 변경까지 지운다**. 그래서 ⓐ 복원 목록은 **내 커밋만**
(`--no-merges`)에서 산출하고 ⓑ 원천은 **합친 main 의 고정 SHA**(= `HEAD^2`)다 — 움직이는
`origin/main` 을 쓰지 않는다(다음 머지가 그 ref 를 옮기면 다른 트리를 가리킨다).

evidence 커밋은 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」가 아니다 — 대조하는 것은 **그 사이에
되돌림 대상이 움직였는가**이고 명령은 §4 다.

## 1. 되돌리는 법

**range revert 가 아니라 in_scope 경로 한정 복원**이다. 목록은 손으로 적지 않고 기계가 낸다.

```
MAIN=8b3b610c                                        # 합친 main 의 고정 SHA(= HEAD^2)
EXCL=':(exclude)reports/evidence/m9/gates-tests'   # 자기 자신은 되돌릴 수 없다

mapfile -t MINE < <(git log --no-merges --format= --name-only $MAIN..HEAD -- . "$EXCL" | sort -u)
mapfile -t MODIFIED < <(for f in "${MINE[@]}"; do git cat-file -e "$MAIN:$f" 2>/dev/null && echo "$f"; done)
mapfile -t ADDED    < <(for f in "${MINE[@]}"; do git cat-file -e "$MAIN:$f" 2>/dev/null || echo "$f"; done)

git restore --source=$MAIN --staged --worktree -- "${MODIFIED[@]}"
[ ${#ADDED[@]} -eq 0 ] || git rm -q -f -- "${ADDED[@]}"
```

「있음/없음」을 `--diff-filter` 가 아니라 **`git cat-file -e`** 로 가른다: 필터는 범위의 양 끝을
비교해 병합이 끼면 남의 변경을 M 으로 센다. 원천에 그 경로가 있는지만 물으면 병합과 무관하다.
**이번 `ADDED` 는 0** 이지만(산출물은 전부 기존 파일 수정) 분기를 지우지 않는다 — 다음 라운드가
새 파일을 만들면 같은 명령이 받는다.

## 2. 실측 (버릴 clone, `git clone --no-hardlinks`)

| 단계 | 실측 |
| --- | --- |
| ① 명령 종료 코드 | `restore` **0** (`git rm` 은 `ADDED` 0 이라 돌지 않는다) |
| ② 계수 | 내 커밋 경로 **5**(evidence 제외) = 복원 **5**(M) · 삭제 **0**(A) — 기계 목록과 같다 |
| ③ 결과 트리 == `8b3b610c` 트리 | 작업 트리 **0 파일** · 색인 **0 파일** · 미추적 **0** |
| ③′ 남의 변경 보존 | #130 — **겹친 파일**(등재부)의 저쪽 항목 셋이 복원 뒤에도 **각 1**, 등재 합계 **512** 유지 · 복원 목록 밖 저쪽 파일(`MockKonepsServer.kt` · portal-02 evidence 넷) **그대로** |
| ④⑤⑥ 복원 트리의 컴파일·test·게이트 | **트리 동일성으로 갈음** — 아래 |
| ⑦ 원천과 남는 차이 | **`reports/evidence/m9/gates-tests/` 디렉터리 전체**뿐이고 **그 밖은 0** 이다(의도된 제외 — `EXCL` 이 그 디렉터리를 뺀다). **계수를 적지 않는다**(cr LOW): 그 수는 evidence 커밋마다 자라 반드시 낡고, 이 행이 지는 주장은 「evidence **밖**이 0 인가」다. 그 디렉터리의 파일은 `scope.md` 까지 전부 이 브랜치가 새로 만든 것이라 원천에 없다 |

### ④⑤⑥ 의 갈음 — 「HEAD 초록」이 아니라 트리 동일성

복원한 트리는 **`8b3b610c` 의 트리와 파일 단위로 같다**(③ 의 0 파일 셋). 그 커밋은 병합된
main 이고 CI 가 초록으로 통과한 자리라, 복원 트리에서 `check` 를 다시 돌리는 것은 같은 트리를
다시 재는 일이다. **같지 않은 입력을 이름 붙인다**: ⑦ 의 evidence 파일들이다 — 누출 패턴
게이트가 `reports/evidence` 를 스캔하므로 빌드 입력이 맞고, 그 축은 이 slice 의 HEAD(그것들을
**포함한** 상위집합)에서 돌 때 함께 재진다.

### 표지 계수 — 「내 줄 사라짐」과 「남의 줄 남음」 둘 다

복원 뒤 `adapters`·`config` 에서 센 **파일 수**다. 종료 코드가 아니라 이 계수가 완료의 증거다.

| 내 표지(0 이어야 한다) | 복원 뒤 |
| --- | --- |
| `ALLOWED_FIELD_REFERENCES` · `UnavailableProducingPortFixture` · `actualByTable` · `tablesWithNoticeRoundCheck` · 새 persistence test 이름 · 등재부의 이 OPEN 식별자 | **전부 0** |

| 남의 표지(남아 있어야 한다) | 복원 뒤 |
| --- | --- |
| `DOMAIN_IMPORT`(옛 소스 텍스트 술어가 돌아온다 — persistence 포함 **다섯**) | 5 |
| `OUT_OF_LAYER_DOMAIN_ROOTS`(옛 금지 목록) | 3 |
| `ALLOWED_METHOD_REFERENCES`(메서드 축, 내가 지우지 않은 것) | 1 |
| 스키마 전체 본문 목록 헬퍼(내가 지웠던 것이 돌아온다) | 1 |
| `outbox_state_check`(같은 파일의 앞 선례) | 2 |
| 원판 `Found(noticeToWatchSubject` 반환 | 1 |

표지를 고를 때 `ALLOWED_ROOTS`·`BIDVECTOR_INTERNAL_NAME` 은 쓸 수 없다 — 형제 dependency
test 여덟이 그 이름을 이미 들고 있어 **남의 줄까지 세게 된다**. 그래서 이 slice 가 처음
들여온 이름들과 새 test 이름으로 골랐다.

## 3. 적용 DB 되돌림

**해당 없음** — 마이그레이션을 더하지 않는다(바뀐 것은 test 술어 넷 · port KDoc 한 문단 ·
등재부 문면 하나). 변이 실측에서 마이그레이션 셋을 **일시 약화**했지만 커밋하지 않았고 복원
뒤 변이 표지 **0 파일** · 원판 표지 **4 파일**로 확인했다(commands). 컨테이너는 일회용이다.

## 4. 실측 HEAD 와 판정 SHA 사이가 움직였는가

```
git diff --name-only 1438b428..<판정 SHA> -- \
  $(git log --no-merges --format= --name-only 8b3b610c..1438b428 \
      -- . ':(exclude)reports/evidence/m9/gates-tests' | sort -u)
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 **미검증**이다(그때 ①~③ 을 다시 잰다).
동결 보고가 그 확인 결과를 적는다.

## 5. 코드를 되돌리지 않고 끄는 법

**없다 — 기능 스위치도 wiring 도 더하지 않는다.** 산출물은 전부 항상 켜진 게이트 술어다.
가장 가까운 것은 §1 의 경로 한정 복원이고 그러면 세 OPEN 이 적던 약한 술어가 **그대로
돌아온다**(위 「남의 표지」 계수). 등재부에서 test 를 빼는 길은 등재 게이트가 붉힌다.
