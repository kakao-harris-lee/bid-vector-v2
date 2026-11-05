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
| web `lint` · `typecheck` · `test`(vitest) · `api:types:check` · `gate:tests` · `gate:src-tree` | 0 | 로컬 |

판정 SHA 에서 돌린 test 계수: adapters **117** · workflow **132** · app **314** — 실패 0 · skip 0.
| `./gradlew --no-daemon check` · `qualityBaseline` · web job 전문 · container · ml-engine | — | **PR CI 가 정본**(팀장 공지 — 호스트 빌드 큐) |

web 계수: lint **0** · typecheck **0** · vitest **38 파일 / 768 test** · `api:types:check` 바이트
등식(**191758 bytes**) · `gate:tests` 통과 · `gate:src-tree` 통과.

**여정 e2e 는 로컬에서 돌리지 않는다**(공통 규율) — v H-1 의 처방은 그래서 「고쳐서 통과시킨다」가
아니라 **사본을 없애는 것**이다: 행 testId 를 짓는 자리가 `notificationItemTestId` 하나이고 화면과
여정이 그 함수를 부른다. 그 꼴을 vitest 둘이 잠그고(네 칸이 다 들어가는가 · 축만 달라도 갈리는가),
여정의 실행 판정은 CI 가 낸다.

**전건 `check` 를 로컬에서 돌리지 않았다**(레인 공통 규율 — 같은 명령이 CI 에 있다). 표적은 위
세 줄이고, 아키텍처 게이트를 따로 든 것은 `config/quality/architecture-policy.properties` 의
tier2 허용 타입 목록이 **오늘 실제 참조와 등식**이기 때문이다 — 더한 둘이 죽은 항목이면 그
등식이 붉다(초록으로 확인).

생성 타입은 `npm run api:types` 로 **재생**했다(손 편집 0). 바이트 등식이 그 사실을 잠근다.

## §4 변이 아홉 — 바꿔치우기, 커밋 뒤

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

## §6 되돌림 리허설

`rollback.md` — 미적용 DB(파일 삭제 + 경로 한정 복원, 결과 트리 == 원천 트리 0 파일)와 적용
DB(forward-fix 네 걸음, **서면 행 처분이 순서상 필수**임을 거부로 실측)를 버릴 clone 에서 쟀다.
