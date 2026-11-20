# M14 / bidcase-notice-projection (14E) — 되돌리기

실측 HEAD: **`d3a8c28b0fb90bcafd41f51a42542640ae8a05d7`**(머지 차례 재실측 · 2026-11-20)
원천: **`af6d9c0f4c0d83469ecbb1314eb6d30848f485b2`** — **고정 SHA** 다(움직이는 `origin/main` 이
아니다). 착수 base 와 **같은 SHA** 이므로 「복원 원천 ↔ 착수 base 의 blob 동일성」(셋째 단언)이
자명하게 성립한다. main 이 그대로라 14E 는 **병합 없이 먼저 머지**한다.

> 앞 판의 실측 HEAD 는 `e0ab5905` 였다. 머지 차례에 **공유 파일**(capability map §12.3 (y))과
> 장부 정정이 들어와 되돌림 대상이 늘었으므로 **전체를 다시 쟀다**. 아래 수치는 전부 재실측이다.

버릴 clone 에서 쟀다. range revert 가 아니라 **in_scope 경로 한정 복원**이다.

## 되돌리는 경로 — `git diff --name-status <base>..HEAD` 기계 산출

**수정된 스물다섯**(복원 대상) — 아래 스물넷 + `docs/discovery/capability-map.md`(공유 파일,
머지 차례에 들어왔다):

```
workflow/src/main/kotlin/bidvector/workflow/bidding/BiddingPorts.kt
workflow/src/main/kotlin/bidvector/workflow/bidding/BidCaseWorkflow.kt
workflow/src/test/kotlin/bidvector/workflow/bidding/BidCaseWorkflowTest.kt
workflow/src/test/kotlin/bidvector/workflow/bidding/BidCaseNotificationTest.kt
adapters/src/main/kotlin/bidvector/adapters/bidding/BiddingSql.kt
adapters/src/main/kotlin/bidvector/adapters/bidding/JdbcNoticeDeadlineQuery.kt
adapters/src/test/kotlin/bidvector/adapters/bidding/BidCaseDbFixture.kt
adapters/src/test/kotlin/bidvector/adapters/bidding/BidCaseOwnershipTest.kt
app/src/main/kotlin/bidvector/app/http/BidCaseResponses.kt
app/src/main/kotlin/bidvector/app/wiring/BidCaseWiring.kt
app/src/test/kotlin/bidvector/app/http/BidCaseEndpointTest.kt
app/src/test/kotlin/bidvector/app/http/HttpTestApplication.kt
app/src/test/kotlin/bidvector/app/http/InMemoryBidCaseStore.kt
app/src/test/kotlin/bidvector/app/http/OpenApiBidCaseContractTest.kt
config/quality/gate-tests.properties
openapi/bidvector-operator-api.yaml
web/src/api/generated/operator-api.ts
web/src/screens/BidBoxCard.test.tsx
web/src/screens/BidBoxScreen.test.tsx
web/src/screens/ResultsScreen.test.tsx
web/src/screens/TodayNoticesScreen.test.tsx
web/src/screens/WorksheetScreen.test.tsx
web/src/screens/screenCopy.test.tsx
web/src/screens/screenVocabulary.test.tsx
```

**신설된 여섯**(원천에 없으므로 복원이 아니라 **삭제**): 어댑터 둘
(`JdbcNoticeProjectionQuery` · `NoticeKeyLookup`) · 어댑터 test 하나(`NoticeProjectionQueryTest`)
· evidence 셋(`scope.md` · `commands.md` · `rollback.md`).

기계 산출: `git diff --name-status af6d9c0f..<실측 HEAD>` → **M 25 · A 6**.

## 절차

```
git restore --source=af6d9c0f --staged --worktree -- <위 스물다섯>
git rm --cached <신설 셋> && rm -f <신설 셋>
rm -rf reports/evidence/m14/bidcase-notice-projection
```

**원천을 `origin/main` 이 아니라 고정 SHA 로 적는다** — 움직이는 참조를 쓰면 나중에 되돌릴 때
이 slice 가 아닌 남의 줄까지 함께 되돌아간다. 머지 뒤 되돌림은 **PR revert** 다.

이 slice 는 **DB 마이그레이션을 만들지 않았으므로 되돌릴 스키마가 없다.** 쓰기 경로 · 외부 호출
도 더하지 않았으므로 되돌림에 데이터 영향이 없다. 되돌리면 응답에서 공고 칸 넷이 사라지고
계약 · 생성 타입이 함께 내려간다 — 그 칸을 읽는 화면이 아직 **없으므로**(14D 가 열 자리다)
소비자 쪽 조치가 필요 없다.

## 실측 ①~⑥

| # | 잰 것 | 결과 |
| --- | --- | --- |
| ① | 복원 명령(`restore --source=af6d9c0f`, M 25)의 종료 코드 | **0** |
| ② | 신설 여섯 제거의 종료 코드 | **0** |
| ③ | 되돌린 뒤 `git diff <base>` **전체** | **0 줄** |
| ④⑤⑥ | compile · test · 게이트 | **트리 동일성으로 갈음**(아래) |

### 공유 파일의 항목 집합 계수 — 「내 표지 0 · 남의 표지 보존」

capability map 은 여러 slice 가 쓰는 **공유 파일**이라 종료 코드로는 되돌림의 완료를 말할 수
없다. 항목 집합으로 센다.

| 축 | 되돌리기 전 | 되돌린 뒤 |
| --- | --- | --- |
| 내 표지 `#### (y)` | 1 | **0** |
| 남의 표지 `(t)`·`(u)`·`(v)`·`(w)`·`(x)` | 각 1 | **각 1(보존)** |
| §12.3 하위 절 총수 | 25 | **24** |
| `bidcase-notice-projection` 언급 | 있음 | **0** |

둘을 **함께** 센 것이 요점이다 — 「내 줄이 사라짐」만 보면 남의 줄을 함께 지운 되돌림도
통과한다.

### ④⑤⑥ 을 트리 동일성으로 갈음한 근거

되돌린 작업 트리를 통째로 기록한 tree 객체가 **base 커밋의 tree 객체와 같은 해시**다.

```
복원 뒤 write-tree : ad0d79704c7b8316cfae8451e43d47637f719e28
base 의 tree       : ad0d79704c7b8316cfae8451e43d47637f719e28
전체 git diff      : 0 줄
```

(머지 차례 재실측에서도 **같은 해시**다 — 되돌림 대상이 25+6 으로 늘었는데도 트리가 base 와
정확히 같다.)

in_scope 한정이 아니라 **저장소 전체**가 base 와 바이트까지 같다는 뜻이다. 그래서 복원한
트리에서 compile · test · 게이트를 다시 도는 것은 **base 에서 도는 것과 같은 입력**이고, 그
결과는 base 의 CI 결과가 그대로 든다. 하네스 규율이 요구하는 갈음은 「HEAD 가 초록이더라」가
아니라 바로 이 **트리 동일성**이다 — 그것을 해시로 실측해 적는다.

이 등식이 성립하지 않았다면(한 줄이라도 남았다면) 갈음이 아니라 ④⑤⑥ 을 실제로 돌려야 한다.

## 실측 뒤 움직인 경로

실측 HEAD `e0ab5905` 뒤에 이 slice 가 더 건드리는 경로는 **evidence 디렉터리 안쪽뿐**이다.

말을 정확히 한다(v L-2). `commands.md` 는 `e0ab5905` 에 **이미 있던 파일**이고 그 뒤에 **갱신**
됐다 — 「신설」이 아니다. 전칭식이 요구하는 것은 「신설 커밋인가」가 아니라 **「되돌림의 원천
(base `af6d9c0f`)에 그 blob 이 없는가」**이고, `commands.md`·`rollback.md` 둘 다 base 에 없으므로
(`git cat-file -e af6d9c0f:<path>` 실패) 예외가 성립한다. 되돌림은 그 디렉터리를 통째로 지우므로
복원 대상이 늘지 않는다.

전칭식으로 적는다: 「`git diff --name-only <실측 HEAD>..<판정 SHA> -- <되돌리는 경로들>` 에
나오는 경로는 **전부 base 에 존재하지 않는 신설 파일**이다.」 열거와 원천 부재 확인은
verifier 가 한다 — 이 식이 깨지면(원천에 있던 파일이 한 줄이라도 나오면) 되돌림은 **미검증**
이고 실측을 다시 해야 한다.

**산출물 경로는 실측 뒤 한 줄도 움직이지 않았다** — 움직인 것은 evidence 뿐이고, 그래서 위
①~⑥ 의 트리 동일성이 판정 SHA 에서도 그대로 성립한다.

`git rev-list --merges af6d9c0f..<판정 SHA>` = **0** · `git rev-list --merges <실측 HEAD>..<판정
SHA>` = **0**.

`git rev-list --merges <실측 HEAD>..<판정 SHA>` 는 빈 출력이어야 한다(이 레인은 아직 main 을
병합하지 않았다). 머지 뒤 되돌림은 PR revert 다.
