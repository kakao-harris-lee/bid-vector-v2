# M7/7C⑤-b 되돌리기 — 내 입찰 결과 조회

**기준 둘.** in_scope 산출물은 착수 base **`003ec91d`** 에서, 공유 파일은 **병합된 main 의 고정 SHA
`9463f6e1`** 에서 복원한다(프로젝트 CLAUDE.md rollback 절). 움직이는 `origin/main` 을 쓰지 않는다 —
그 사이 main 이 더 들어오면 되돌림이 남의 것까지 지운다.

되돌림은 **범위 revert 가 아니라 경로 한정 복원**이다: 이 브랜치에는 팀장의 계약 커밋과 main 병합이
섞여 있고, range 로 되돌리면 그것까지 지운다.

## 명령

```
git restore --source=003ec91d --staged --worktree -- <「수정」 14개 중 공유 파일 밖>
git restore --source=9463f6e1 --staged --worktree -- <「공유 파일」 4개>
git rm -f --                                         <「신규」 18개>
```

경로는 **개별 인자**다. evidence(`reports/evidence/m7/7c5b/**`)는 목록에 **없다** — 되돌림의
기록이 자신을 지울 수 없다. 병합이 가져온 main 의 파일(`web/**` 등)도 목록 밖이다.

## 공유 파일 4 — `9463f6e1` 에서 복원

- `config/quality/architecture-policy.properties`
- `config/quality/gate-tests.properties`
- `openapi/bidvector-operator-api.yaml`
- `web/src/api/generated/operator-api.ts`

**전제**: `9463f6e1` 에 이 slice 의 줄이 없다(첫 산출물 커밋이 그 뒤다 — 병합은 main 을 **받아온**
것이지 내 것을 올린 것이 아니다). 그래서 이 복원은 머지 순간 아무것도 지우지 않으며 exit 0 이다.
머지 뒤의 되돌림은 PR revert 가 맡는다.

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

## 수정 14 — 위 기준에 따라 복원

- `adapters/build.gradle.kts`
- `adapters/src/main/kotlin/bidvector/adapters/opening/JdbcOpeningResultView.kt`
- `app/build.gradle.kts`
- `app/src/main/kotlin/bidvector/app/http/AccountScopedRoutes.kt`
- `app/src/main/kotlin/bidvector/app/http/OpeningResultController.kt`
- `app/src/main/kotlin/bidvector/app/wiring/ProfileWiring.kt`
- `app/src/test/kotlin/bidvector/app/architecture/AppHttpDependencyGateTest.kt`
- `app/src/test/kotlin/bidvector/app/http/AccountCarrierStructureTest.kt`
- `app/src/test/kotlin/bidvector/app/http/ConstantTimeComparisonStructureTest.kt`
- `app/src/test/kotlin/bidvector/app/http/HttpTestApplication.kt`
- `config/quality/architecture-policy.properties`  ← 공유
- `config/quality/gate-tests.properties`  ← 공유
- `openapi/bidvector-operator-api.yaml`  ← 공유
- `web/src/api/generated/operator-api.ts`  ← 공유

목록은 `git diff --name-status 003ec91d..HEAD` 에서 병합이 가져온 경로를 뺀 **기계 산출**이고
라운드마다 다시 뽑는다. 손으로 고치지 않는다.

## 되돌린 뒤 남는 것

**없다.** 마이그레이션·쓰기·수집 경로를 건드리지 않았으므로 DB 상태와 외부 호출에 남는 흔적이
0 이다. 되돌리면 `GET /api/bid-results` 가 사라지고 7C⑤-a 의 공고 단위 개찰 조회, 7B 의 입찰함,
7E-3a 의 결과 화면(집계 자리가 빈 채)은 그대로 남는다.

**다른 slice 의 파일 넷**(`JdbcOpeningResultView.kt` · `OpeningResultController.kt` ·
`ProfileWiring.kt` · `ConstantTimeComparisonStructureTest.kt`)은 base 복원이 **이 slice 의 줄만**
지우는지를 ⑥ 에서 계수로 확인한다(「내 표지 0 · 남의 표지 보존」).

## 실측

**실측 HEAD: `67c55f61`** — 버릴 clone 에서 위 명령 그대로. 이 뒤의 커밋은 evidence 뿐이고
evidence 는 복원 목록 밖이라, 되돌림 대상은 실측 뒤로 움직이지 않았다.

| # | 재는 것 | 결과 |
| --- | --- | --- |
| ① | 세 명령의 종료 코드 | `restore(base)`=0 · `restore(main)`=0 · `rm(new)`=0 |
| ② | 지운 수·복원한 수가 목록과 같은가 | D=18(목록 18) · M=14(목록 14) |
| ③ | diff 가 빈 출력인가 | base 기준 **0 줄** · 병합 main 기준 **0 줄** |
| ④ | 컴파일이 서는가 | `workflow`·`adapters`·`app` 종료 0 |
| ⑤ | test 가 도는가 | `:workflow:test` 종료 0 |
| ⑥ | 게이트가 초록인가 | size·cpd·detekt 셋 + 구조 게이트 **50 test 실패 0** |

### ⑥ 「내 표지 0 · 남의 표지 보존」 계수

| 표지 | 되돌린 뒤 | 읽는 법 |
| --- | --- | --- |
| 내 표지(`7C⑤-b`·`MyBidResult`·`bid-results`·`workflow.result`) — 내 파일 | **0 파일** | 산출물이 남지 않았다 |
| 내 표지 — **남의 파일** | 1 파일 | 7E-3a 가 자기 화면 주석에서 이 slice 를 이름 부른다. **남의 줄이라 보존이 맞다** |
| `OnboardingChecklist`(7C④-3) | 11 파일 | 살아 있다 |
| `OpeningResultView`(7C⑤-a) | 18 파일 | 살아 있다 |
| `ResultsScreen`(7E-3a) | 1 파일 | 살아 있다 |

공유 파일 넷과 다른 slice 의 파일 넷에서 **이 slice 의 줄만** 사라졌다 — 되돌림이 남의 등재·
어휘·주석을 함께 지우지 않는다.
