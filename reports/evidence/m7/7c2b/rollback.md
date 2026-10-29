# M7/7C②-b — rollback

실측 HEAD: `6da69dba` (이 slice 의 **마지막 산출물 커밋**. 뒤따르는 evidence 커밋은 되돌림
대상이 아니다 — 아래 목록에 reports/evidence 경로가 **하나뿐**이고 그것은 착수 계약이다.)

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
트리는 `78d97c59` 의 트리와 **바이트까지 같은 트리**이고, 그 커밋은 main 에 머지된 커밋이라
CI 전건이 이미 그 트리를 초록으로 실측했다. 같은 트리에서 compile·test·게이트를 다시 재는
것은 같은 입력을 다시 재는 일이다.

이 slice 는 **되돌리지 않기로 한 것이 없다** — evidence 디렉터리까지 복원 목록에 들어 있어
(`scope.md` 삭제) 「되돌리지 않은 문서가 게이트를 붉히는」 알려진 한계가 발생하지 않는다.
단, 이 문서와 `commands.md` 는 `6da69dba` **뒤** 커밋이라 목록에 없다 — 그 둘을 함께 지우려면
`reports/evidence/m7/7c2b/` 디렉터리를 지운다(`git rm -r --cached` + 파일 삭제).

## 비활성화 (코드를 되돌리지 않고 거동만 끄는 길)

없다 — 이 slice 는 flag·route·writer 를 더하지 않는다. 바꾼 것은 **이미 있는 응답 칸의 값을
어떤 판정에서 짓는가**이고, 끄는 단위가 되는 배선이 없다. 되돌림은 위의 경로 복원뿐이다.
