# M7/7C②-b — rollback

실측 HEAD: `6da69dba` (이 slice 의 **마지막 산출물 커밋**)

**미검증 구간 — 유효성 술어가 빈 출력이 아니다**(CR r1 MEDIUM · verifier r1 L-1).
`git diff --name-only 6da69dba..<판정 SHA> -- <복원 경로들>` 이 **세 줄**을 낸다:

- `reports/evidence/m7/7c2b/scope.md` — 계약 갱신(D-7C2B-6)이 바꿨다.
- `…/http/BidPriceRequestResponse.kt`·`…/http/BidPriceRequestResponseTest.kt` — **이 장부 라운드가**
  바꿨다(KDoc 조건 둘 · 배선 단언 하나, CR r1 LOW 둘).

또 판정 SHA 기준 기계 산출 목록은 **A 3 · M 11** 이다(아래 목록은 `A 1 · M 11` 인 실측 시점 값).

**경계**: 세 경로 모두 base 기준 `A`/`M` 이고 복원은 **내용과 무관하게** base 내용으로 덮거나
지우므로 결과 트리는 달라지지 않는다 — 트리 동일성 결론(아래)은 그대로 선다. verifier r1 이
판정 SHA 당시 상태에서 그 불변성을 실측했다(복원 rc 0 · D1 M11 · evidence 둘을 함께 걷으면
`write-tree` 가 base tree 와 같음). **이 라운드가 더한 두 파일은 그 실측 뒤 편집이라 재실측 대상**
이다 — 거동이 아니라 술어가 낡았다.

**재실측은 다음 라운드다**(팀장 결정 2026-10-29) — 7C④-2b 머지 뒤 main 병합 라운드에서 복원
기준이 그 병합된 main 커밋으로 **어차피 바뀌므로**, 목록·`실측 HEAD`·①~⑥ 을 그때 한 번에 다시 낸다.
이 문서를 담은 커밋은 **자기 목록의 실측에 들어갈 수 없다** — 그 구조적 꼬리가 위 선언이다.

복원 기준: **`78d97c59`** (PR #99 가 머지된 main 커밋, 계약 base 와 같다).
전제 둘을 실측했다 — ⓐ 그 SHA 는 판정 HEAD 의 조상이다(`git merge-base --is-ancestor` exit 0)
ⓑ 그 SHA 에 이 slice 가 없다(`git ls-tree -r 78d97c59 -- reports/evidence/m7/7c2b` **0건**).
**머지 전 전제다** — 머지 뒤의 되돌림은 같은 복원이 아니라 그 PR 의 머지 커밋 revert 다.

## 복원 목록 (기계 산출 — `git diff --name-status 78d97c59..6da69dba`)

A 1 · M 11. **라운드마다 이 절차를 다시 돌린다** — 파일이 늘면 목록이 낡는다.

```
git restore --source=78d97c59 --staged --worktree -- \
  app/src/main/kotlin/bidvector/app/http/BidPriceFactLabels.kt \
  app/src/main/kotlin/bidvector/app/http/BidPriceRequestResponse.kt \
  app/src/main/kotlin/bidvector/app/http/BidPriceResponseStates.kt \
  app/src/main/kotlin/bidvector/app/wiring/BidPriceRequestFactory.kt \
  app/src/test/kotlin/bidvector/app/http/BidPriceRequestEndpointTest.kt \
  app/src/test/kotlin/bidvector/app/http/BidPriceRequestResponseTest.kt \
  app/src/test/kotlin/bidvector/app/http/ConstantTimeComparisonStructureTest.kt \
  config/quality/architecture-policy.properties \
  docs/discovery/capability-map.md \
  openapi/bidvector-operator-api.yaml \
  reports/evidence/m7/7c2b/scope.md \
  web/src/api/generated/operator-api.ts
```

`--source` 에 없는 경로(`scope.md`)는 이 명령이 **삭제**하므로 `git rm` 이 따로 필요 없다.
하네스 경로(`CLAUDE.md`·`.claude/**`)는 되돌리지 않는다 — 이 range 에 그 경로의 커밋이
**0건**이라 되돌릴 것도 없다(scope.md 「하네스 레인 변경: 없음」과 같은 사실).

**hunk 격리를 쓰지 않는다.** 공유 파일 넷(정책·capability-map·OpenAPI·생성 타입)에 다른
slice 의 줄이 있지만, 복원 기준이 **main 커밋**이라 그 트리에는 남의 줄이 이미 들어 있다 —
그래서 「내 줄만 걷고 남의 줄은 남긴다」가 명령 하나로 성립한다. 이 브랜치는 base 이후 main 을
다시 병합하지 않았다(병합 커밋의 충돌 해소 줄 문제가 없다).

## 실측 (임시 clone, `6da69dba`)

| # | 확인 | 결과 |
| --- | --- | --- |
| ① | 명령 종료 코드 | **0** |
| ② | 변경 계수 | **D 1 · M 11**(목록의 A 1 · M 11 과 맞는다) |
| ③ | 복원 뒤 `git diff 78d97c59 --stat -- <복원 경로들>` | **빈 출력** |
| ④⑤⑥ | compile · test · 게이트 | **트리 동일성으로 갈음**(아래) |

**갈음의 근거는 「HEAD 초록」이 아니라 트리 동일성이다.** 복원 뒤 인덱스의 tree SHA 가
base 의 tree SHA 와 **같다**(`git write-tree` == `git rev-parse 78d97c59^{tree}`, 값
`22dc23be…`), 그리고 worktree 는 인덱스와 차이가 없다(`git diff --stat` 빈 출력). 즉 되돌린
트리는 `78d97c59` 의 트리와 **바이트까지 같은 트리**다. 같은 트리에서 compile·test·게이트를 다시
재는 것은 같은 입력을 다시 재는 일이다.

**그 트리에서 초록인 job 의 범위를 정확히 적는다**(verifier r1 L-2): base `78d97c59` 는 main 에
머지된 커밋이고 **Kotlin `check`·`web`·`ml-engine` job 이 초록**이다. `container` job 은 **붉다** —
이 slice 와 무관한 취약점 DB 갱신 때문이고 main `bb6a187e`(PR #101)가 고쳤다(`commands.md` §6 ·
제한 ⑥). 되돌림이 그 job 을 초록으로 만들지 못한다는 뜻이고, 되돌림 절차의 미완이 아니다.

이 slice 는 **되돌리지 않기로 한 것이 없다** — evidence 까지 복원 목록에 들어 있어(복원이 그
경로를 삭제한다) 「되돌리지 않은 문서가 게이트를 붉히는」 알려진 한계가 발생하지 않는다.

**판정 SHA 기준 목록은 기계가 낸다**(CR r1 MEDIUM — 산문 지시로 대체하지 않는다):
`git diff --name-status 78d97c59..<판정 SHA>` 가 **A 3 · M 11** 이고, 위 M 열하나는 그대로이며
A 는 `scope.md` 에 **이 문서와 `commands.md` 가 더해진** 셋이다. 그래서 오늘의 절차는 위
`git restore` 에 그 두 경로를 **인자로 더한 한 번**이다 — 디렉터리를 지우라는 지시가 아니다.

## 비활성화 (코드를 되돌리지 않고 거동만 끄는 길)

없다 — 이 slice 는 flag·route·writer 를 더하지 않는다. 바꾼 것은 **이미 있는 응답 칸의 값을
어떤 판정에서 짓는가**이고, 끄는 단위가 되는 배선이 없다. 되돌림은 위의 경로 복원뿐이다.
