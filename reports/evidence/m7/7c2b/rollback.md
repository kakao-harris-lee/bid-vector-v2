# M7/7C②-b — rollback

실측 HEAD: `703b9157` (병합 라운드의 **마지막 산출물·계약 커밋**. 이 문서와 `commands.md` 는 그
뒤 커밋이라 목록에는 있으나 실측에는 들어갈 수 없다 — 아래 「구조적 꼬리」.)

복원 기준: **`fa5ca096`** (7E-1 PR #102 · 7C④-2b PR #104 가 머지된 main 커밋 = 이 브랜치에
**마지막으로 병합한 main 의 고정 SHA**). 움직이는 `origin/main` 을 쓰지 않는다.
전제 둘을 실측했다 — ⓐ 그 SHA 는 판정 HEAD 의 조상이다(`git merge-base --is-ancestor` exit 0)
ⓑ 그 SHA 에 이 slice 가 없다(`git ls-tree -r fa5ca096 -- reports/evidence/m7/7c2b` **0건**).
**머지 전 전제다** — 머지 뒤의 되돌림은 같은 복원이 아니라 그 PR 의 머지 커밋 revert 다.

앞 라운드의 기준(`78d97c59`)과 그 실측은 **버린다**. base 가 병합으로 움직였으므로 옛 base 로
재산출한 목록은 명령이 exit 0 을 내면서 틀린다.

## 복원 목록 (기계 산출 — `git diff --name-status fa5ca096..703b9157`)

**A 3 · M 14.** `--source` 에 없는 경로(A 셋 = 이 디렉터리의 문서 셋)는 이 명령이 **삭제**하므로
`git rm` 이 따로 필요 없다. **라운드마다 이 절차를 다시 돌린다** — 파일이 늘면 목록이 낡는다.

```
git restore --source=fa5ca096 --staged --worktree -- \
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
  reports/evidence/m7/7c2b/commands.md \
  reports/evidence/m7/7c2b/rollback.md \
  reports/evidence/m7/7c2b/scope.md \
  web/src/api/generated/operator-api.ts \
  web/src/display/worksheetDisplay.test.ts \
  web/src/display/worksheetDisplay.ts \
  web/src/screens/WorksheetScreen.test.tsx
```

하네스 경로(`CLAUDE.md`·`.claude/**`)는 되돌리지 않는다 — 이 range 에 그 경로의 커밋이 **0건**
이라 되돌릴 것도 없다(scope.md 「하네스 레인 변경: 없음」과 같은 사실).

**hunk 격리를 쓰지 않는다.** 공유 파일 여섯(정책·capability-map·OpenAPI·생성 타입·web 표시 표와
그 test)에 다른 slice 의 줄이 있지만, 복원 기준이 **병합한 main 커밋**이라 그 트리에는 남의 줄이
이미 들어 있다 — 「내 줄만 걷고 남의 줄은 남긴다」가 명령 하나로 성립한다. 병합 커밋이 해소한
충돌 줄(정책 파일의 설명 문단 둘)도 그 기준 복원으로 함께 걷힌다.

## 실측 (임시 clone, `703b9157`)

| # | 확인 | 결과 |
| --- | --- | --- |
| ① | 명령 종료 코드 | **0** |
| ② | 변경 계수 | **D 3 · M 14**(목록의 A 3 · M 14 과 맞는다) |
| ③ | 복원 뒤 `git diff fa5ca096 --stat -- <복원 경로들>` | **빈 출력** |
| ④⑤⑥ | compile · test · 게이트 | **트리 동일성으로 갈음**(아래) |

**갈음의 근거는 「HEAD 초록」이 아니라 트리 동일성이다.** 복원 뒤 인덱스의 tree SHA 가 base 의
tree SHA 와 **같다**(`git write-tree` == `git rev-parse fa5ca096^{tree}`, 값 `2da2d964…`), 그리고
worktree 는 인덱스와 차이가 없다(`git diff --stat` 빈 출력). 되돌린 트리는 `fa5ca096` 의 트리와
**바이트까지 같은 트리**다 — 같은 입력을 다시 재는 일이라 ④⑤⑥ 을 다시 돌리지 않는다.

**그 트리에서 초록인 job 의 범위**: `fa5ca096` 의 check-run 조회에서 `container`·`web`·
`ml-engine` **success**, Kotlin `check` 는 조회 시점에 **결론 없음**(`null`). 앞 base
(`78d97c59`)에서 붉던 `container` 는 이 base 에서 초록이다 — main `bb6a187e`(PR #101)가 고쳤다.

**구조적 꼬리**: 이 문서를 담은 커밋은 자기 목록의 실측에 들어갈 수 없다(커밋 전에 자기 해시를
모른다). 그래서 유효성 술어 `git diff --name-only 703b9157..<판정 SHA> -- <복원 경로들>` 는 **이
디렉터리의 문서 둘**을 낸다 — 복원이 그 경로를 **내용과 무관하게 삭제**하므로 결과 트리는
달라지지 않는다. 코드·정책·web 경로가 그 구간에서 움직이면 그때는 실측이 낡은 것이고, 그 확인은
verifier 몫이다.

## 비활성화 (코드를 되돌리지 않고 거동만 끄는 길)

없다 — 이 slice 는 flag·route·writer 를 더하지 않는다. 바꾼 것은 **이미 있는 응답 칸의 값을 어떤
판정에서 짓는가**와 **그 상태의 화면 문면 한 행**이고, 끄는 단위가 되는 배선이 없다. 되돌림은
위의 경로 복원뿐이다.
