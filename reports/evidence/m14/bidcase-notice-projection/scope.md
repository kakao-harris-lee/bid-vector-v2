# M14 / bidcase-notice-projection (14E) — slice 계약

```yaml
milestone: M14
slice: bidcase-notice-projection
base_sha: af6d9c0f4c0d83469ecbb1314eb6d30848f485b2   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  # ── 포트와 use case (workflow) ──────────────────────────────────────────
  - workflow/src/main/kotlin/bidvector/workflow/bidding/BiddingPorts.kt   # 읽기 투영 포트 + 값
  - workflow/src/main/kotlin/bidvector/workflow/bidding/BidCaseWorkflow.kt # view() 가 투영을 싣는다
  - workflow/src/test/kotlin/bidvector/workflow/bidding/BidCaseWorkflowTest.kt
  - workflow/src/test/kotlin/bidvector/workflow/bidding/FakeBidCaseStore.kt  # fake 포트(값 없음 ↔ 행 없음)
  # ── 어댑터 (adapters) ───────────────────────────────────────────────────
  - adapters/src/main/kotlin/bidvector/adapters/bidding/BiddingSql.kt        # 묶음 질의 한 문장
  - adapters/src/main/kotlin/bidvector/adapters/bidding/JdbcNoticeProjectionQuery.kt  # 신설
  - adapters/src/test/kotlin/bidvector/adapters/bidding/                     # 실 DB 통합 test(신설)
  # ── HTTP 응답과 배선 (app) ──────────────────────────────────────────────
  - app/src/main/kotlin/bidvector/app/http/BidCaseResponses.kt   # 평탄한 칸 넷
  - app/src/main/kotlin/bidvector/app/wiring/BidCaseWiring.kt    # 새 포트 배선
  - app/src/test/kotlin/bidvector/app/http/BidCaseEndpointTest.kt
  - app/src/test/kotlin/bidvector/app/http/InMemoryBidCaseStore.kt
  - app/src/test/kotlin/bidvector/app/http/OpenApiBidCaseContractTest.kt
  - config/quality/gate-tests.properties   # 새 Kotlin test class 를 **만드는 커밋에서** 등재
  # ── 계약과 생성 타입 ────────────────────────────────────────────────────
  - openapi/bidvector-operator-api.yaml    # `BidCase` 스키마 + 두 operation 의 설명
  - web/src/api/generated/operator-api.ts  # 재생성(바이트 등식 W-5)
  - docs/discovery/capability-map.md       # §12.3 하위 절 하나 — 글자는 머지 차례에 정한다
  - reports/evidence/m14/bidcase-notice-projection/
  # ── 착수 OPEN-1 이 승인되면 더해지는 자리(web fixture, 값만) ─────────────
  #   아래 「착수 보고의 OPEN」 OPEN-1. 승인 전에는 열지 않는다.
out_of_scope:
  - adapters/src/main/resources/db/migration/**   # **마이그레이션 0** — 읽는 열 셋이 V7·V14 에 이미 있다
  - 모든 쓰기 경로(POST/PUT/DELETE 컨트롤러 · `applyTransition` · outbox)  # 쓰기 0
  - 새 외부 호출(조달청 API · LLM · 알림 발송)                              # 호출 0
  - web/src/screens/** · web/src/display/** · web/src/components/** · web/index.css  # 14A·14B·14C·14D
  - web/src/api/queries.ts · web/src/api/approvedOperations.ts   # operation 집합 불변(아래 실측)
  - 기초금액 · 마감 칸의 투영                                    # 계약 14E 행 「쓰는 데가 없어 넣지 않는다」
  - `listSuggestions` · `searchNotices` · 알림함 응답            # 다른 표면의 공고 칸은 이미 있다
  - .github/workflows/ci.yml                                     # acceptance 는 오늘의 job 명령 그대로
acceptance_commands:
  # ci.yml `check` job 의 Kotlin 전건 — 계약 완료 조건 1 「14E 는 백엔드 slice 라 Kotlin check 전건」
  - ./gradlew --no-daemon check
  # ci.yml `web` job 전건 W-1~W-13 (`web/` 에서 돈다) — 생성 타입이 바뀌므로 든다
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
rollback: rollback.md
```

## 이 slice 가 더하는 것

`listBidCases`(`GET /api/bid-cases`)와 `readBidCase`(`GET /api/bid-cases/{caseId}`)의 응답에
**저장된 공고에서 읽어 온 표시 칸 셋**(제목 · 수요기관 · 공고기관)을 싣는다. 와이어프레임 D
(입찰함 카드)와 「이번 주 일정」 레일이 쓰는 칸이 그 셋이다.

**새 쓰기 · 새 외부 호출 · 새 표가 없다.** 읽는 열 셋(`notice_title`(V14) ·
`demand_agency_name` · `notice_agency_name`(V7))이 이미 있고 앱 역할은 그 표에 `SELECT` 를
이미 가진다(V2 의 GRANT). **마이그레이션은 이 slice 에 없다** — 계약 14E 행의 정지 조건에
닿지 않는다.

## 부재의 두 사실을 가른다 (계약 14E 행 · 알림함 `noticeKnown` 꼴)

건은 공고를 **FK 로 참조하지 않는다**(V21 머리말 — 보존 파기와 충돌한다). 그래서 공고 행이
없을 수 있고, 그것은 「행은 있는데 제목 칸이 비었다」와 **다른 사실**이다.

| 사실 | 포트 층 | HTTP 층 |
| --- | --- | --- |
| 공고 행이 없다(파기 또는 미수집 — 둘을 가르지 않는다) | 결과 map 에 키가 **없다** | `noticeKnown = false` · 칸 셋 전부 `null` |
| 행은 있고 제목 칸이 비었다 | 키가 **있고** 값의 `title` 이 `null` | `noticeKnown = true` · `noticeTitle` 만 `null` |

포트 층의 이 약속은 **발명이 아니다** — `NoticeDeadlineQueryPort` 가 같은 표에 같은 키로 같은
구분을 이미 두고 있고(계약 D-7B-4 결정 ⑥), 알림함이 HTTP 층에서 `noticeKnown` 으로 그것을
나르는 꼴을 이미 출하했다. 두 선례를 그대로 따른다.

**값 셋을 nullable 스칼라로만 접지 않는 이유**: 접으면 「파기된 공고」와 「제목 없는 공고」가
같은 세 `null` 이 되어 화면이 사유를 말할 수 없다. 14D 의 카드가 「공고를 찾을 수 없습니다」와
「제목이 없습니다」를 가르려면 이 boolean 이 있어야 한다.

## 착수 실측 — 계약 14E 행을 코드로 다시 잼

base `af6d9c0f` 의 worktree 에서 쟀다. 수치와 그것을 낸 명령은 `commands.md` 가 정본이다.

| 축 | 잰 것 | 결과 |
| --- | --- | --- |
| 제목·기관이 어느 표에 있나 | 마이그레이션 · `NoticeRow` | `notice` 표 — `notice_title`(V14) · `demand_agency_name` · `notice_agency_name`(V7). 셋 다 **nullable** |
| 마이그레이션 필요 여부 | 위 셋의 실재 + 앱 역할의 GRANT | **불필요**. V2 가 `notice` 에 `SELECT` 를 이미 준다 |
| 건 ↔ 공고 결합 | V21 머리말 · `bid_case` 열 | `(notice_number, notice_round)` 의 **사본**이고 FK 가 **없다**(보존 파기와 충돌해 의도적으로 뺐다) |
| 묶음 조회 선례 | `JdbcNoticeDeadlineQuery` · `BiddingSql` | `unnest(?::text[], ?::text[])` 로 **키 집합을 한 문장**에 묶는 선례가 같은 표·같은 키에 이미 있다 |
| 목록의 N+1 위험 | `BidCaseWorkflow.list` | **구조적으로 없다** — 오늘의 마감 조회가 이미 건별이 아니라 **키 집합 한 번**이다(`deadlines(found.map{…}.toSet())`). 같은 꼴을 따르면 건 수와 무관하게 질의 수가 고정이다 |
| 오늘의 질의 수(목록 1회) | `list` 의 호출 | 3 — 판정 후보 · 후보 마감 · 건 목록, 그리고 목록의 마감 조회 1 = 총 4. 투영을 **새 포트**로 두면 5, **마감 포트에 얹으면** 4 |
| `readBidCase` 의 web 소비자 | `approvedOperations.ts` | **없다**. 승인 쌍에 `GET /api/bid-cases/{caseId}` 가 없다 → 이 slice 는 operation 집합을 **바꾸지 않는다**(승인 쌍 등식 불변) |
| 응답 칸 이름의 선례 | 기존 OpenAPI · 컨트롤러 | `noticeTitle` · `demandAgencyName` · `noticeAgencyName` — 세 이름이 이미 출하돼 있다(제안 · 검색 카드). 새 이름을 만들지 않는다 |
| 평탄 계약 | `OpenApiContractTest` | 응답 칸은 스칼라여야 한다(D-6A1-20 ⓑ) → 중첩 object 가 아니라 **평탄한 칸 넷**(`noticeKnown` + 셋) |
| 생성 타입을 쓰는 web 고정물 | `BidCase` 를 짓는 자리 전수 | **7 자리**(아래 OPEN-1). 필수 칸을 더하면 `tsc`(W-3)가 그 일곱을 멈춘다 |

## 설계 제안 (팀장 판정 전 — 추천안)

**읽기 투영을 새 포트 하나로 둔다**(`NoticeDeadlineQueryPort` 의 쌍둥이 꼴).

- 포트: `fun projections(notices: Set<NoticeKey>): Map<NoticeKey, NoticeProjection>`
- 값: `NoticeProjection(title, demandAgencyName, noticeAgencyName)` — 전부 nullable, 키의
  유무가 「행의 유무」다.
- `BidCaseWorkflow.list` · `read` 가 **키 집합으로 한 번** 부른다.

**마감 포트에 세 칸을 얹지 않는 이유**: 질의 하나를 아낄 수 있지만, 그 포트의 값은
`DeadlineSettlement` 가 **판정에 쓰는 값**이다. 표시 칸을 그 값에 섞으면 판정 경로가 자기가
쓰지 않는 데이터를 들고 다니고, 마감만 재는 fake 전부가 표시 값을 지어내야 한다. 질의 하나는
같은 표의 PK 묶음 조회라 비용이 작고, 이 slice 가 지키려는 것은 **읽기 투영이 판정과 섞이지
않는 경계**다.

## 착수 보고의 OPEN (팀장 판정 대기 — 답을 받은 뒤 구현에 들어간다)

**OPEN-1 — 새 칸을 필수로 두면 다른 레인 소유의 web 고정물 일곱이 컴파일에서 멈춘다.**

알림함 선례는 `noticeKnown`·`noticeTitle` 을 **required + nullable** 로 둔다. 같은 꼴을 쓰면
생성 타입의 칸이 비선택이 되고, `BidCase` 를 **짓는** web 자리 일곱이 전부 `tsc` 에서 멈춘다
(값만 더하면 되는 자리다 — 배치·문면을 건드리지 않는다). 그 일곱은 14B·14C·14D 와 공유
게이트가 소유한 test 고정물이다.

- 추천: **required + nullable 을 유지하고, 그 일곱에 칸 넷을 더하는 것만** 이 slice 가 한다.
  선택 칸으로 두면 「행이 없다」를 화면이 조용히 무시할 수 있어 알림함이 세운 정직성이
  이 표면에서만 풀린다.
- 대안(권하지 않음): 칸을 `optional` 로 선언 → 고정물 편집 0, 그러나 계약이 약해진다.
- 머지 순서는 계약이 이미 정했다 — 「14D 는 14E 머지 뒤 그 main 위로 병합한 다음 머지한다」.

**OPEN-2 — 질의 하나를 더할 것인가(위 「설계 제안」).** 추천은 **새 포트**(목록 1회당 질의
4 → 5). 마감 포트에 얹는 쪽이 질의 수로는 싸지만 판정과 표시가 한 값에 섞인다.

**OPEN-3 — `readBidCase` 에도 싣는가.** 계약 14E 행이 둘 다 말하므로 **싣는다**. 다만 오늘
web 은 그 operation 을 부르지 않는다(승인 쌍에 없다) — 소비자는 14D 가 열 때 생긴다. 그
사실을 알려진 제한에 등재한다.

## 알려진 제한 (착수 시점)

1. **`readBidCase` 에 오늘 web 소비자가 없다**(OPEN-3). 그 칸은 계약·test 로만 잠긴다.
2. **공고 행이 없는 사유를 가르지 않는다** — 보존 파기와 미수집이 같은 `noticeKnown = false`
   다. 저장소에 묘비가 없어 구조적으로 가를 수 없다(알림함이 같은 제한을 진다).
3. **기초금액·마감은 투영하지 않는다**(계약 14E 행). 건은 담을 때의 마감 **사본**을 이미
   들고 있고 응답이 그것으로 판정한 `nextCheckpoint*`·`overdue` 를 낸다 — 공고의 라이브
   마감을 한 칸 더 싣는 것은 두 마감이 응답에 함께 서는 일이라 새 결정이다.
4. 투영은 **라이브 값**이다(초안 스냅샷의 사본이 아니다). 공고가 정정돼 제목이 바뀌면 카드의
   제목도 바뀐다 — 마감 조회가 이미 같은 축이다.

## 승인 경계 (계약 「승인 경계」 절의 이 slice 몫)

- **DB 마이그레이션 · 새 외부 호출 · 쓰기 경로** — 착수 실측으로 셋 다 **필요하지 않음**을
  확인했다. 구현 중 필요해지면 멈추고 보고한다.
- 14E 밖의 서버·OpenAPI 변경은 하지 않는다. 새 의존성 0.
- 다른 slice 소유의 web **화면·표시·토큰** 파일은 열지 않는다. OPEN-1 이 승인되면 **test
  고정물의 값 칸만** 연다.
- 기존 게이트의 **술어**를 바꾸지 않는다(14A ④ 의 사전 승인 예외는 이 slice 의 것이 아니다).
- 재작업 상한 5 에 닿으면 보고한다.

## base 확인

`git rev-parse af6d9c0f` → `af6d9c0f4c0d83469ecbb1314eb6d30848f485b2` ·
`git merge-base --is-ancestor af6d9c0f HEAD` 를 착수 커밋에서 기계로 확인했다(위 yaml 의
40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에
줄이 서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에
있다. 귀속은 라운드마다 기계로 재산출해 확인한다.

## 머지 차례

capability map §12.3 의 이 slice 몫은 **머지 차례에 정한다**(계약 「capability map letter」).
상호 참조에는 글자를 쓰지 않는다 — 이 줄이 그 글자를 적는 유일한 자리가 된다.

## 갱신 이력

- 2026-11-20 초판(착수 보고와 함께). OPEN 셋은 팀장 판정을 기다린다.
