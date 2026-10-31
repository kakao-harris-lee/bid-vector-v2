# M7/7C⑤-b 되돌리기 — 내 입찰 결과 조회

**기준 하나: 병합된 main 의 고정 SHA `9463f6e1`.** 움직이는 `origin/main` 을 쓰지 않는다 — 그 사이
main 이 더 들어오면 되돌림이 남의 것까지 지운다.

되돌림은 **범위 revert 가 아니라 경로 한정 복원**이다: 이 브랜치에는 팀장의 계약 커밋과 main 병합이
섞여 있고, range 로 되돌리면 그것까지 지운다.

## 왜 기준이 하나인가 (r1 정정)

앞 판은 기준을 **둘**로 두었다(in_scope 는 착수 base `003ec91d`, 공유 파일은 `9463f6e1`). 그 틀은
목록을 「내가 바꾼 것 ∖ main 이 바꾼 것」으로 만들었고, 그래서 **둘 다 바꾼 파일이 목록에서 통째로
빠졌다** — `docs/discovery/m7-screen-data-requirements.md` 가 그것이다(7E-3a 가 고쳤고 이 slice 도
요구표 E 네 행을 고쳤다). 되돌려도 내 줄이 남는다.

기준을 **현재 main 하나**로 두면 그 갈래가 사라진다. 실측으로 안전하다:

- 이 slice 가 바꾼 경로 **36** 개 가운데 main 도 바꾼 것은 **1** 개다.
- 나머지 **35** 개는 `003ec91d` 와 `9463f6e1` 이 **바이트까지 같다**
  (`git diff --quiet 003ec91d 9463f6e1 -- <경로>` 가 전부 종료 0). 그래서 착수 base 에서 복원하는
  것과 결과가 같다.

## 명령

```
git restore --source=9463f6e1 --staged --worktree -- <아래 「수정」 18개>
git rm -f --                                         <아래 「신규」 18개>
```

경로는 **개별 인자**다. evidence(`reports/evidence/m7/7c5b/**`)는 목록에 **없다** — 되돌림의 기록이
자신을 지울 수 없다.

## 신규 18 — 지운다

- `adapters/src/main/kotlin/bidvector/adapters/result/JdbcMyBidResultViews.kt`
- `adapters/src/main/kotlin/bidvector/adapters/result/MyBidResultSql.kt`
- `adapters/src/test/kotlin/bidvector/adapters/result/JdbcMyBidResultViewTest.kt`
- `app/src/main/kotlin/bidvector/app/http/ConfirmedAwardFields.kt`
- `app/src/main/kotlin/bidvector/app/http/MyBidResultController.kt`
- `app/src/main/kotlin/bidvector/app/http/MyBidResultResponses.kt`
- `app/src/main/kotlin/bidvector/app/wiring/MyBidResultWiring.kt`
- `app/src/test/kotlin/bidvector/app/http/FakeMyBidResultPorts.kt`
- `app/src/test/kotlin/bidvector/app/http/MyBidResultEndpointTest.kt`
- `app/src/test/kotlin/bidvector/app/http/OpenApiMyBidResultContractTest.kt`
- `workflow/src/main/kotlin/bidvector/workflow/result/AwardGap.kt`
- `workflow/src/main/kotlin/bidvector/workflow/result/MyBidResultPorts.kt`
- `workflow/src/main/kotlin/bidvector/workflow/result/MyBidResultTally.kt`
- `workflow/src/main/kotlin/bidvector/workflow/result/ReadMyBidResults.kt`
- `workflow/src/main/kotlin/bidvector/workflow/result/ReconciliationVerdict.kt`
- `workflow/src/test/kotlin/bidvector/workflow/result/MyBidResultTallyTest.kt`
- `workflow/src/test/kotlin/bidvector/workflow/result/ReadMyBidResultsTest.kt`
- `workflow/src/test/kotlin/bidvector/workflow/result/ReconcileBidTest.kt`

## 수정 18 — `9463f6e1` 판으로 복원한다

- `adapters/build.gradle.kts`
- `adapters/src/main/kotlin/bidvector/adapters/opening/JdbcOpeningResultView.kt`
- `adapters/src/main/kotlin/bidvector/adapters/opening/OpeningResultViewSql.kt`
- `app/build.gradle.kts`
- `app/src/main/kotlin/bidvector/app/http/AccountScopedRoutes.kt`
- `app/src/main/kotlin/bidvector/app/http/OpeningResultController.kt`
- `app/src/main/kotlin/bidvector/app/wiring/ProfileWiring.kt`
- `app/src/test/kotlin/bidvector/app/architecture/AppHttpDependencyGateTest.kt`
- `app/src/test/kotlin/bidvector/app/http/AccountCarrierStructureTest.kt`
- `app/src/test/kotlin/bidvector/app/http/ConstantTimeComparisonStructureTest.kt`
- `app/src/test/kotlin/bidvector/app/http/HttpTestApplication.kt`
- `config/quality/architecture-policy.properties`
- `config/quality/gate-tests.properties`
- `docs/discovery/capability-map.md`
- `docs/discovery/m7-screen-data-requirements.md`  ← **main 도 바꾼 파일**
- `openapi/bidvector-operator-api.yaml`
- `web/src/api/generated/operator-api.ts`
- `workflow/src/main/kotlin/bidvector/workflow/profile/BusinessProfilePorts.kt`

목록은 `git diff --name-status 9463f6e1..HEAD -- . ':(exclude)reports/evidence/**'` 의 **기계 산출**
이고 라운드마다 다시 뽑는다. 손으로 고치지 않는다.

**전제**: `9463f6e1` 에 이 slice 의 줄이 없다. 근거는 **시각이 아니라 조상 관계**다(r1 CR L-2 —
첫 산출물 커밋은 시각으로는 오히려 앞선다): `git merge-base --is-ancestor <첫 산출물 커밋>
9463f6e1` 이 거짓이고, `9463f6e1` 은 다른 브랜치의 PR #108 병합이라 이 브랜치의 커밋을 담지 않는다.

## 되돌린 뒤 남는 것

**없다.** 마이그레이션·쓰기·수집 경로를 건드리지 않았으므로 DB 상태와 외부 호출에 남는 흔적이 0 이다.
되돌리면 `GET /api/bid-results` 가 사라지고 7C⑤-a 의 공고 단위 개찰 조회, 7B 의 입찰함, 7E-3a 의
결과 화면(집계 자리가 빈 채)은 그대로 남는다.

**다른 slice 의 파일 다섯**(`JdbcOpeningResultView.kt` · `OpeningResultViewSql.kt` ·
`OpeningResultController.kt` · `ProfileWiring.kt` · `ConstantTimeComparisonStructureTest.kt`)과 장부
둘에서, 복원이 **이 slice 의 줄만** 지우는지를 ⑥ 에서 계수로 확인한다.

## 실측

**실측 HEAD: `91ff8aae`** — 버릴 clone 에서 위 명령 그대로. 이 뒤의 커밋은 evidence 뿐이고
evidence 는 복원 목록 밖이라, 되돌림 대상은 실측 뒤로 움직이지 않는다.

| # | 재는 것 | 결과 |
| --- | --- | --- |
| ① | 두 명령의 종료 코드 | `restore`=0 · `rm`=0 |
| ② | 지운 수·복원한 수가 목록과 같은가 | D=18(목록 18) · M=18(목록 18) |
| ③ | `git diff 9463f6e1 -- <목록>` | **0 줄** |
| ④ | 컴파일이 서는가 | `workflow`·`adapters`·`app` 종료 0 |
| ⑤ | test 가 도는가 | `:workflow:test` 종료 0 |
| ⑥ | 게이트가 초록인가 | size·cpd·detekt 셋 + 구조 게이트 **50 test 실패 0** |

### ⑥ 「내 표지 0 · 남의 표지 보존」 계수

| 표지 | 되돌린 뒤 | 읽는 법 |
| --- | --- | --- |
| 내 표지 — **내 파일** | **0** | 산출물이 남지 않았다 |
| 내 표지 — **남의 파일** | 1 | 7E-3a 가 자기 화면 주석에서 이 slice 를 이름 부른다. **남의 줄이라 보존이 맞다** |
| `OnboardingChecklist`(7C④-3) | 11 파일 | 살아 있다 |
| `OpeningResultView`(7C⑤-a) | 18 파일 | 살아 있다 |
| `ResultsScreen`(7E-3a) | 1 파일 | 살아 있다 |
| **요구표의 `7E-3` 줄** | **3** | 살아 있다 — 기준을 하나로 고친 것이 실제로 이 파일을 되돌린다는 증거다(앞 판 목록은 이 파일을 아예 빼고 있었다) |
