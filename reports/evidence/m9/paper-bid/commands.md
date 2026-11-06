# M9/paper-bid 실행 기록

base `df4976cc` · 브랜치 `feature/m9-paper-bid` · 계약 `scope.md`(D-M9P-1~4 · 계약 갱신
2026-11-06) · 설계 검토 `design-review.md`.

재현은 worktree 뿌리에서. 무거운 Gradle 명령은 `~/.local/bin/bv-build.sh <명령>` 으로 감싼다
(호스트 빌드 자리 둘 · 획득 순간 메모리 재확인).

## §1 착수 조사 — 계약이 담지 못한 호출자

계약의 글로브(`workflow/**/notificationinbox/**`·`bidding/BidCaseWorkflow.kt`)로는 D-2(가드 둘
제거)가 성립하지 않는다. 축이 키에 들어가는 순간 깨지는 호출자가 **셋**이고 그 셋이 전부 계약
밖이었다 — 훑기(질의의 종류 거르기를 걷으면 종류를 실어 나를 자리가 필요하다) · 사상의 자리 ·
**셋째 원천**(투찰가 요청 기록). 셋째는 조사에서 나온 사실이다: 그 경로에는 **건이 없다**(운영자가
공고 하나를 지목한다) — 그래서 어휘가 둘이 아니라 셋이어야 한다는 결론이 여기서 나왔다.

팀장에게 목록·사유를 보내 확장 승인을 받고(열둘) `scope.md` 의 「계약 갱신 2026-11-06」 절에
적었다. 승인 전에는 계약 안의 경로만 편집했다.

## §2 RED 실측

| 라운드 | 명령(요지) | 무엇이 붉었나 |
| --- | --- | --- |
| 화면·계약(구현 전) | `npm run typecheck` | **1** — fixture 에 `bidKind` 가 없어 `NotificationItem` 할당이 거부(이 축을 더하는 일이 wire 계약을 지난다는 신호) |
| 저장 층(V25 없이) | `:adapters:test --tests "*NotificationInboxPersistenceTest*"` | **25/25** — 축이 아예 없으니 알림함 실 DB test 전건이 붉다 |
| 단건 읽음의 wire 축 | `npx vitest run src/screens/InboxScreen.test.tsx` + 변이 W3 | **1/30** — fixture 를 `PAPER` 로 바꾼 뒤 「축을 `'REAL'` 로 고정」 변이가 그 test 하나만 붉다(바꾸기 전에는 **초록**이었다) |
| 잃은 알림 표지 | `:app:test --tests "*NotificationInboxWiringTest*"` + 변이 둘 | **2** / **1** — 축 칸 제거 · 계정 싣기 |

둘째 라운드는 **무딘 RED** 다(열이 없어 전부 붉다) — 「어느 방어막이 무엇을 막는가」는 거동으로
갈라야 하고, 그 축은 §4 의 변이가 진다. 그래서 RED 의 하중을 변이에 두었다.

## §3 acceptance

| 명령 | exit | 어디서 |
| --- | --- | --- |
| `:adapters:test --tests "*NotificationInbox*" --tests "*NotificationSweepCandidatesTest*" --tests "*CleanMigration*" --tests "*RetentionSweepTest*"` | 0 | 로컬 |
| `:workflow:test --tests "*Notification*" --tests "*BidCase*"` | 0 | 로컬 |
| `:app:test --tests "*NotificationInbox*" --tests "*OpenApi*" --tests "*NotificationSweep*" --tests "bidvector.app.architecture.*"` | 0 | 로컬 |
| 세 모듈 `ktlint{Main,Test}SourceSetCheck` + `detekt` + `leakPatternGate` | 0 | 로컬 |
| web `lint` · `typecheck` · `test`(vitest) · `api:types:check` · `gate:tests` · `gate:src-tree` · `gate:boundary-canary` | 0 | 로컬 |
| `./gradlew --no-daemon check` · `qualityBaseline` · web job 전문 · container · ml-engine | — | **PR CI 가 정본**(팀장 공지 — 호스트 빌드 큐) |

**UP-TO-DATE 는 측정이 아니므로** Kotlin 은 강제 실행하고(`--rerun-tasks` · task `--rerun`) 결과
XML 의 mtime 으로 실행을 확인했다.

- adapters **105** · workflow **132** · app **304** — 실패 0 · skip 0. 이 셋은 둘째 병합 커밋에서
  쟀고, **승인 전 일괄 라운드는 그 뒤 두 모듈을 만지지 않았다**(실측: 그 범위에서
  `adapters/`·`workflow/` 변경 **0 파일** — 바뀐 것은 app test 한 class · web 둘 · 문서다).
- 그 바뀐 `NotificationInboxWiringTest` 는 **판정 SHA 에서 다시 돌렸다** — **3/3**, 실패 0.
- 세 모듈 ktlint·detekt exit 0 · `leakPatternGate` 는 **evidence HEAD** 에서 exit 0 — 그 task 의
  `scanRoot` 가 `reports/evidence` 를 **선언 입력**으로 들어 evidence 커밋마다 out-of-date 가
  되고, 보고 파일 mtime 으로 실행을 확인했다.
- web lint **0** · typecheck **0** · vitest **40 파일 / 857 test** · `api:types:check` 바이트
  등식(**191758 bytes**) · `gate:tests` · `gate:src-tree` · `gate:boundary-canary`(#135 가 번들
  그래프 하네스를 만졌다). 판정 SHA 의 web 내용에서 돌렸다(그 뒤 web 파일을 만지지 않았다).
  web 계수에는 main 병합 둘이 더한 분이 들어 있고 **내 몫은 그 중 둘이 새 것**이다.

**adapters·app 계수가 병합 전(117·314)보다 작은 것은 회귀가 아니라 표적 집합의 차이다** — 병합 뒤
실행에서 `--tests "*RetentionSweepTest*"` 와 `--tests "*NotificationSweep*"`(app) 을 뺐다. 빠진
자리는 그 둘뿐이고 세 모듈의 `check` 전문은 CI 가 돈다.

**여정 e2e 는 로컬에서 돌리지 않는다**(공통 규율) — v H-1 의 처방은 그래서 「고쳐서 통과시킨다」가
아니라 **사본을 없애는 것**이다: 행 testId 를 짓는 자리가 `notificationItemTestId` 하나이고 화면과
여정이 그 함수를 부른다. 그 꼴을 vitest 둘이 잠그고(네 칸이 다 들어가는가 · 축만 달라도 갈리는가),
여정의 실행 판정은 CI 가 낸다.

**`web/e2e/**` 는 어느 게이트도 타입 검사하지 않는다**(cr M-1 의 사실 축) — `tsconfig.json` 의
`include` 가 `src` 뿐이고 `e2e/tsconfig*.json` 이 **없다**(실측). 그래서 이 slice 가 e2e 손 타입을
`NotificationTestIdParts & { read }` 로 바꾼 것은 **표류를 줄이는 기록**이고 래칫이 아니다. 그
변경이 성립하는지는 게이트가 아니라 **따로 쟀다**: 그 파일을 `tsc` 로 직접 돌려 **그 파일 오류 0**
(남은 넷은 `shared.ts` 의 `node:*` 와 `client.ts` 의 `import.meta.env` — ad-hoc 플래그에
`@types/node`·`vite/client` 가 없어서이고 이 변경과 무관하다). **양성 대조**: 같은 타입에서
`bidKind` 칸을 뺀 변이는 **호출 자리에서 TS2345** 로 붉다 — 도구가 그 파일을 실제로 본다는
증거이고, 그러므로 「오류 0」이 공허하지 않다. 재발 방지의 하중은 타입이 아니라 **함수가 응답
객체에서 wire 칸 이름을 직접 읽는다**는 성질이 진다(그 KDoc 이 든다).

**전건 `check` 를 로컬에서 돌리지 않았다**(레인 공통 규율 — 같은 명령이 CI 에 있다). 표적은 위
세 줄이고, 아키텍처 게이트를 따로 든 것은 `config/quality/architecture-policy.properties` 의
tier2 허용 타입 목록이 **오늘 실제 참조와 등식**이기 때문이다 — 더한 둘이 죽은 항목이면 그
등식이 붉다(초록으로 확인).

생성 타입은 `npm run api:types` 로 **재생**했다(손 편집 0). 바이트 등식이 그 사실을 잠근다.

## §4 변이 — 바꿔치우기, 커밋 뒤

원복은 변이 파일만(`git checkout --`). 모든 변이는 산출물 커밋 뒤에 돌렸다.

| 변이 | 무엇을 바꿨나 | 붉은 자리 |
| --- | --- | --- |
| ① 축이 열로만 있다 | PK 와 `ON CONFLICT` 를 **넷으로** | **5** — 투찰 종류 거동 **3**(두 항목·단건 읽음·묶음 읽음) + PK 열 집합 래칫 + pkey 인덱스 래칫. **중복 억제 일반 test 는 초록**(신호가 이 축에 귀속된다) |
| ② 짝 CHECK 없음 | 어휘만 닫고 종류↔투찰종류 짝을 안 세움 | **2** — 전수 교차 + CHECK 수 래칫. **어휘 등식은 초록** — 두 축이 **따로 열린다**(V24 변이 ③b 가 detail 축에서 본 것과 같은 자리) |
| ③ 묶음 계수에 축 없음 | 창 `PARTITION BY` 에서 축 제거 | **3** — 묶음 계수 · 공고축 묶음 분리 · 훑기 출하 경로 e2e |
| ④ 정렬에 축 없음 | `ORDER BY` 에서 축 제거 | **처음엔 0** → §5 참조 → 바꿔치운 test 에서 **1**(그 test 하나만) |
| ⑤ 묶음 읽음에 축 없음 | `MARK_NOTICE_READ` 술어에서 축 제거 | **4** — 묶음 읽음 축 분리 + 묶음 읽음 일반 둘 + 권한 test(bind 수까지 걸린다) |
| ⑥ 되비춤 가드 되살림 | `case.asReal() ?: return` 복귀 | **1** — 서면 전이가 항목을 남긴다(그 test 하나만) |
| ⑦ 훑기 리터럴 되살림 | 질의에 `bid_kind = 'REAL'` 복귀 | **2** — 후보 집합 · 출하 경로 e2e. **알림함 문장의 축어 진단은 초록**(그 리터럴이 다른 파일에 있다 — 진단이 모집단으로 덮지 못하는 자리이고, 거동이 그것을 받는다) |
| ⑨ 기본값 남김 | `DROP DEFAULT` 제거 | **1** — 열 래칫의 「기본값 있음」 축(`hasDefault`) |
| ⑩ 사상 접기 | `BidKind.PAPER -> NotificationBidKind.REAL` | **3** — 1:1 등식 · 서면 되비춤 · 훑기 묶음 |
| ⑪ 단건 읽음의 축 고정 | 화면이 `bidKind: 'REAL'` 을 보낸다(verifier 변이 W3) | **1** — `PAPER` fixture 로 바꾼 뒤. 바꾸기 전에는 **초록 30/30**(§5) |
| ⑫ 표지에서 축 제거 | `inboxWriteFailedLine` 의 `bidKind=` 칸 | **2** — 담는 칸 단언 · 두 축이 다른 줄 |
| ⑬ 표지에 계정 싣기 | 같은 함수에 `account=` 추가 | **1** — 음성 대조만(담는 축 test 는 초록) |
| ⑭ 표지에 **대문자** 계정 토큰 | 같은 함수에 `Account=redacted`(값은 싣지 않는다) | **1/3** — 음성 대조 하나만. **값 단언은 지나간다**(계정 수가 없다)·담는 축도 초록이라 갈리는 축이 **대소문자 술어 하나**로 고립된다. 축어 `"account"` 였던 앞 판은 이 변이에 **초록**이었다(cr L-3 의 요점) |

**⑧ backfill 은 변이로 재지 못한다** — 깨끗한 스키마에서는 기존 행이 없어 그 UPDATE 가
무연산이다. `rollback.md` §4 가 그 사실과 간접 측정을 적는다(알려진 제한 4).

## §5 같은 공백이 두 번 나왔다 — 축을 단언하면서 그 축을 재지 못하는 꼴

변이 ④(`ORDER BY` 에서 축 제거)가 **초록이었다.** 앞 판 단언은 두 축의 묶음 최신 시각이 **다른**
모양이라 `group_recent DESC` 가 이미 둘을 갈라 놓았고, 그래서 정렬 축을 지워도 연속이 유지됐다.

술어를 넓히는 대신 **모양을 바꿨다**: 두 축의 묶음 최신 시각을 같게 두면 다음 축이 사실 시각이
되어 네 항목이 축을 넘나들며 선다(`PAPER·REAL·REAL·PAPER`), 축이 들어 있으면
`PAPER·PAPER·REAL·REAL` 이다. 종류를 넷 다르게 둔 것은 동점을 없애 답이 흔들리지 않게 하려는
것이다. 그 test 를 별 커밋으로 넣고 변이 ④ 를 **다시** 돌려 RED 를 실측했다.

**같은 꼴이 화면에서 한 번 더 나왔다**(verifier 변이 W3 · v M-1). 단건 읽음 test 가 `bidKind` 를
단언하는데 fixture 가 `REAL` 행이어서, 축을 `'REAL'` 로 **고정하는** 회귀의 값과 그 행의 값이
우연히 같았다 — 묶음 경로는 이미 `PAPER` 행에서 재고 있었고 단건 경로만 열려 있었다.

**둘의 공통 원인은 「단언한 값이 fixture 의 기본값과 같다」다.** 축을 재는 단언은 그 축이
**실제로 갈리는 값**에서 세워야 한다 — 기본값으로 세우면 고정·무시·접기가 전부 통과한다. 이
slice 가 그 자리를 둘 고쳤고(정렬 모양 · `PAPER` fixture), 세는 방법도 하나 늘었다: 축을 더한
뒤에는 **그 축을 상수로 바꾸는 변이**를 각 소비 자리마다 돌린다(화면 둘·판독 둘·질의 넷).

## §6 main 병합의 합집합 계수

머지 직전에 `origin/main` 을 **두 번** 합쳤다 — `e12c0485`(#130~#133) · `3ad4f3f6`(#135).
**충돌 0 이 「접히지 않았다」를 뜻하지 않으므로** 병합마다 축을 따로 쟀다.

| 축 | `e12c0485` | `3ad4f3f6` |
| --- | --- | --- |
| 내 경로 ∩ main 변경 경로 | **둘** — 등재부 · capability map | **하나** — `web/e2e/journey/results.spec.ts` |
| 충돌 | **하나**(등재부 app 블록에 양쪽이 같은 자리) → **사전순 합집합** 셋 | **0**(그래서 아래 계수가 하중을 진다) |
| 마이그레이션 충돌 | **0** — main 이 더하지 않았다(V25 의 diff 도 0 줄) | **0** — 같음 |
| 내 나머지 경로가 병합 전과 같은가 | 차이 **0 파일** | 차이 **0 파일** |
| main 의 변경 경로가 그 SHA 와 같은가 | 74 중 차이 **둘**(겹친 그 둘) | 14 중 차이 **하나**(겹친 그 하나) |
| `HEAD` vs 그 SHA 의 차이가 내 경로뿐인가 | 그 밖 **0** | 그 밖 **0** |
| main 추가 파일 전수 존재 | — | **없는 것 0**(목록을 돌며 `-e` 로 물었다) |

**겹친 파일마다 양쪽 생존을 따로 셌다.**

| 겹친 파일 | 내 쪽 | 남의 쪽 | 접힘 검사 |
| --- | --- | --- | --- |
| `config/quality/gate-tests.properties` | 등재 **셋** | Koneps 등재 **둘** | 중복 항목 **0**(세 ref 모두) · 사전순 위반 `app` 2·`decision` 1·`procurement` 3 가 **세 ref 에서 동일**(main 기존 상태 — 내가 등재한 `adapters` 블록은 **0**) |
| `docs/discovery/capability-map.md` | 표지 **각 1**(다섯 열 키 문장 · 「제한 셋」) | #133 의 등재 행 **둘** | 중복 제목 **0**(159 중) · 줄 수 **3971** = `e12c0485` 3971 + 내 순증 0(제자리 치환) |
| `web/e2e/journey/results.spec.ts` | import **1** · `InboxItem` 의 축 칸 **1** · 공유 함수로 짓는 행 조회 **1** · 옛 네 조각 템플릿 **0** | `provisional-state-` **1** · 개찰 축 하네스 참조 **4** · `test` 선언 **3** | 중복 import **0** · `type InboxItem` 선언 **1** · 충돌 표지 **0** · 줄 수 **661** = `3ad4f3f6` 650 + 내 순증 **11**(= 518 − 507) — **산수가 맞으므로 접힌 본문이 없다** |

`web/config/gate-tests.txt` 는 **내가 손대지 않았다** — #135 가 더한 뒤 그대로다(항목 37 · 중복
**0** · `3ad4f3f6` 과 diff **0 줄**). 내가 더한 vitest 둘은 **기존 파일 안의 test** 라 그 등록부에
새 줄이 필요 없다(그 게이트는 파일 단위 등재다).

**의미 겹침을 따로 봤다**(텍스트 겹침 0 인 자리). #132 가 `web/src/display/**`·`web/src/screens/**`
를 만졌지만 내 네 파일과는 다른 파일이고, 그쪽이 바꾼 `stateDisplay.ts` 의 `displayOf` 는 **맨 색인
읽기를 `Object.hasOwn` 으로 좁히는** 변경이라 내 표의 소비 방식(알려진 키로 조회)과 어긋나지 않는다.
#132 가 **새 게이트**를 들여왔다(`money-vocab-cross-lang.test.mjs` — 손으로 복제한 wire 어휘의 키
집합을 Kotlin 정본과 대조). 내 축 표(`NOTIFICATION_BID_KIND_DISPLAY`)는 **그 모집단이 아니다**: 그
게이트가 드는 다섯은 **손 복제본**이고, 내 표의 키 타입은 생성 타입에서 **파생된다**
(`NotificationItem['bidKind']`). 그래서 Kotlin → openapi → 생성 타입 → 표가 한 사슬로 닫힌다 —
어느 한 자리만 늘리면 계약 test·바이트 등식·`satisfies` 가 각각 붉다. 같은 성질을 두 기계로 재지
않는 쪽을 골랐고, 이것이 그 게이트에 내 표를 등재하지 **않은** 근거다.

## §7 되돌림 리허설

`rollback.md` — 미적용 DB(파일 삭제 + 경로 한정 복원, 결과 트리 == 원천 트리 0 파일)와 적용
DB(forward-fix 네 걸음, **서면 행 처분이 순서상 필수**임을 거부로 실측)를 버릴 clone 에서 쟀다.
