# M6/6F-8 — 수집 배선 + 공고명 수집 + 한 달 실수집으로 기능 완료 확인 (2026-09-24)

수집 쪽 부품은 전부 있는데 **잇는 자리가 없다**. 이 slice 는 그 자리를 세우고, 실제 KONEPS 최근 30일치를 수집해
**수집 → 정규화 → 영속 → 평가 dry-run** 이 끝까지 도는지를 확인한다.

- base: `git merge-base HEAD origin/main`(고정 SHA 아님). 착수 실측 `74616367`(PR #44 머지 커밋).
- worktree `bid-vector-v2-m6-6f8`, 브랜치 `m6-6f8/2026-09-24`.

## 운영자 지시·결정 (2026-09-24)

- 지시: 「관련한 배선 진행하고, 수집은 최근 한달 이내의 데이터만 수집해서 기능 완료 확인」.
- 결정 셋(선택지 + 추천, 전부 추천안): ① KONEPS 서비스 키는 legacy `../bid-vector/.env` 의 키를 **환경변수로만** 주입한다
  (로그·evidence·커밋·PR 어디에도 값을 싣지 않는다, legacy 파일은 읽기만) ② 수집 DB 는 **V2 전용 로컬 Postgres 신설**
  (docker, 버려도 되는 개발 DB — 다른 프로젝트 컨테이너를 건드리지 않는다) ③ 업종은 **공사 + 용역**(legacy 운영 설정과 같다).
- 이 지시가 **실 외부 호출(KONEPS 읽기 전용)** 과 **개발 DB 쓰기**의 승인이다. 운영 DB·알림 발송·LLM·ML 호출은 여전히 범위 밖.

## 착수 조사가 확정한 것

- 있는 것: `NoticeSourcePort` + `KonepsOpenApiNoticeSource`(조회일 기준 목록, 페이지 커서, 회계) · `RawObservationStore`·
  `JdbcRawObservationStore` · `canonicalize(observation, policy)`(유일한 변환 지점) · `NoticeRepository.persist`·
  `JdbcNoticeRepository` · `CollectionRunStore.record`·`JdbcCollectionRunStore` · 운영 정책 `KONEPS_COLLECTION_POLICY`
  (필드 계약 행 포함) · V14 `notice_title` 열과 `NoticeCollected.title` 슬롯.
- **없는 것**: ① 이들을 순서대로 부르는 **수집 use case** — workflow 에 수집 패키지가 0 ② app 의 **실행 진입점** — 컨트롤러 둘뿐
  ③ 필드 계약에 **공고명 원시 키**(`bidNtceNm`)와 그것을 `title` 슬롯으로 옮기는 정규화 — `OPEN-6F4-TITLE-INGEST`.
- 그래서 V2 DB 에는 공고가 0건이고, 6A-3+6F-3 의 평가 endpoint 는 빈 후보로만 돌아 왔다.

## 계약 고정 결정

**D-6F8-1 — 수집 use case 는 `workflow.collection.CollectNoticesUseCase` 하나다.** 조회일 범위(`from..to`, 날짜 단위)와
업종별 `NoticeSourcePort` 목록을 받아, 조회일 × 업종마다 커서가 끝날 때까지 페이지를 읽고, 항목마다 **원문 저장 →
`canonicalize` → `NoticeRepository.persist`** 를 한 번씩 부르고, 조회일 × 업종마다 `CollectionRunStore.record` 로 회계를
남긴다. **정규화를 복제하지 않는다**(변환 지점은 `canonicalize` 하나 — 게이트로 잠근다). 결과는 조회일 × 업종별 회계 목록.
- 탈락(`Dropped`)은 사유 코드별로 세고 조용히 버리지 않는다. 중복(같은 관측 키)은 `duplicate` 로 센다.
- **부분 실패 규칙**: 한 조회일 × 업종이 전송 실패·쿼터 초과로 끝나면 그 회계에 `truncated`·원인을 싣고 **다음 조회일로
  넘어간다**(한 날의 실패가 한 달 전체를 버리지 않는다). 쿼터 초과는 남은 조회일을 **멈춘다**(더 부르면 계속 실패하고 한도를
  태운다) — 멈춘 사실과 남은 조회일을 결과에 싣는다.
- 트랜잭션: 항목 단위(원문 저장 + 영속이 한 트랜잭션). 기존 `TransactionBoundary` 를 쓴다 — 새 트랜잭션 관리자를 만들지 않는다.

**D-6F8-2 — 공고명은 필드 계약 데이터가 나른다(D-6F4-6 제약 이행, `OPEN-6F4-TITLE-INGEST` 닫음).** `FieldConcept.NOTICE_TITLE`
신설 + `KONEPS_COLLECTION_POLICY` 필드 계약 행에 `bidNtceNm`(공고 목록 오퍼레이션) 추가, `canonicalize` 가 그 계약에서 공고명을
읽어 `NoticeCollected.title` 에 싣는다. **어댑터·use case 에 키 문자열을 박지 않는다**(게이트 — 원시 키 리터럴 부재). 공백·
부재는 `null`(D-6F4-8: 값이 없으면 없다, 센티넬 금지). 정책 데이터 변경은 **운영자 지시(위)가 승인**이고, 정본 문서
(`docs/discovery/` 의 필드 계약 표 — 구현 레인이 위치 실측)도 같은 커밋 계열에서 갱신한다(「정본은 코드가 아니라 문서」 관례).
- `ntceNm`(legacy 가 쓰던 다른 키)은 **이 오퍼레이션 응답에 있을 때만** 추가 — 실수집 원문으로 실측해 결정하고 evidence 에 적는다.

**D-6F8-3 — 실행 진입점은 기본 꺼진 일회성 러너다.** `app.collection.CollectionRunner`(Spring `ApplicationRunner`)는 속성
`bidvector.collection.mode=once` 일 때만 켜지고(기본값 없음 = 꺼짐), `bidvector.collection.from`·`to`(ISO 날짜)·
`bidvector.collection.categories`(`construction,service`)를 받아 use case 를 한 번 돌리고 회계 요약(건수만)을 로그로 남긴 뒤
끝난다. **HTTP 로 수집을 여는 endpoint 는 만들지 않는다**(외부 호출을 HTTP 로 열지 않는다 — D-6A1-4 정신). 스케줄러 없음.
- 범위 상한: `to - from ≤ 31일`, `to ≤ 오늘` — 넘으면 기동 실패(설정 오류를 조용히 자르지 않는다).
- 서비스 키는 속성 `bidvector.koneps.service-key`(환경변수 바인딩, 기본값 없음). 러너가 꺼져 있으면 키가 없어도 앱이 뜬다
  (평가 endpoint 만 쓰는 배포를 막지 않는다) — 러너 켜짐 + 키 없음 = 기동 실패.
- 업종 → 오퍼레이션 경로: 공사 `getBidPblancListInfoCnstwk`, 용역 `getBidPblancListInfoServc`(베이스 URL 은 legacy 운영 설정과
  같은 `https://apis.data.go.kr/1230000/ad/BidPublicInfoService`) — **설정 데이터로 둔다**(코드 리터럴 아님, 정책 파일 또는
  `@ConfigurationProperties` 기본 매핑표 — 구현 레인이 선례 따라 선택).

**D-6F8-4 — 비밀값.** `ServiceKey` 는 이미 `toString()` 가림 타입이다. 새 코드는 키를 **URL 문자열째 로그에 남기지 않는다**
(요청 URI 에 키가 실린다 — 전송 실패 예외 메시지·로깅 경로 전수, `KonepsIdentifierMasking` 선례). 러너 로그는 건수·조회일·
업종·원인 코드만. privacy-gate 표적.

**D-6F8-5 — 실수집 절차(acceptance 의 일부, 팀장이 실행).** 코드가 검증된 뒤(verifier ready-for-review):
1. `docker run` 으로 `bid-vector-v2-dev` Postgres(포트는 비어 있는 것) 신설 — 기존 컨테이너 무관.
2. 러너를 `mode=once`, `from = 오늘-30일`, `to = 오늘`, `categories=construction,service` 로 한 번 실행. 키는 legacy `.env` 에서
   읽어 **그 프로세스 환경에만** 넘긴다(셸 history·파일에 남기지 않는다).
3. 확인(evidence 에는 **건수·비율만**, 공고 원문·키 없음): 조회일 × 업종 회계(수신·정규화·중복·탈락 사유·페이지·truncated) ·
   `notice` 행 수 · `notice_title` 비어 있지 않은 비율 · `collection_run` 행 수 = 조회일 × 업종 · KONEPS 호출 수(쿼터 대비).
4. 같은 DB 로 앱을 띄워 `maxActiveBids` 가 있는 전략(키워드 규칙 하나 포함)을 넣고 `POST /api/evaluation-dry-runs` —
   `candidateCount > 0`, 네 결과 배열의 분포, `wouldNotify == bidNow`(ML 자리지킴이라 `bidNow` 는 0 이 정상), 키워드 규칙이
   **공고명으로 실제 매칭되는 후보가 있는지**(공고명 배선의 거동 확인).
5. 멱등: 같은 범위를 한 번 더 돌리면 새 `notice` 행 0, `duplicate` 가 1차 정규화 수와 같다.

## 위협 모델 — 6F-8 고유 경계

지키는 것: **① 수집은 원문을 잃지 않고 조용히 버리지 않는다**(수신 = 정규화 + 중복 + 탈락, 사유별) **② 정규화 지점은 하나다**
(use case·러너·어댑터가 필드를 직접 뽑지 않는다) **③ 서비스 키는 어디에도 새지 않는다** **④ 수집은 명시적으로 켤 때만 돈다**
(기본 꺼짐, HTTP 로 열리지 않음, 범위 상한) **⑤ 쿼터를 태우지 않는다**(쿼터 초과 시 멈춤).

경계 밖: 개찰 결과·예비가격·면허 요건 수집(`OpeningResultSourcePort` 등 — 이 slice 는 공고 목록만) · 공고 본문
(`OPEN-6F4-NOTICE-BODY-SOURCE`) · 운영 스케줄링 · 운영 DB · 커넥션 풀.

### 우회 — 일곱

1. use case 가 `canonicalize` 를 건너뛰고 원시 맵에서 필드를 뽑는다. ← 게이트: `workflow.collection` 의 procurement 참조 집합 ⊆
   허용(포트·`canonicalize`·결과 타입) — 원시 키 접근 API 참조 0.
2. 공고명 키를 어댑터·use case·러너에 문자열로 박는다. ← 게이트: `bidNtceNm` 리터럴이 `procurement` 정책 파일 밖 main 에 0
   (바이트코드 상수 풀, 6F-4-w 방식) — 계약 데이터 경유만.
3. 탈락·중복을 세지 않고 버린다. ← 회계 등식 test(수신 = 정규화 + 중복 + 탈락) + 사유별 합 == 탈락.
4. 서비스 키가 예외 메시지·로그로 샌다. ← 전송 실패 경로 test(키를 담은 URI 로 실패 → 메시지·로그에 키 부재 단언) + privacy.
5. 러너가 기본으로 켜진다 / 범위 상한 없이 돈다. ← 속성 없음 → 러너 빈 부재 test · 32일 → 기동 실패 test.
6. 쿼터 초과 뒤에도 계속 부른다. ← fake source 가 쿼터 초과를 내면 남은 조회일 호출 0 test(계수).
7. 신설 게이트 미등재. ← `gate-tests.properties` 등재 + 기존 등재 게이트가 덮는지 확인.

### (2b) 값 획득 축

구현 레인이 새 public 표면 전수 표를 낸다(use case·결과 타입·러너·설정 속성·`FieldConcept.NOTICE_TITLE`). 「경계로 처리」 행도
실측. 특히 **use case 결과 타입이 원문·키를 나르지 않는가**.

## in_scope (게이트·fixture·build·정책 문서를 처음부터 넣는다 — 6A-3+6F-3 교훈)

```yaml
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/collection/**          # D-6F8-1 use case
  - workflow/src/test/kotlin/bidvector/workflow/collection/**
  - procurement/src/main/kotlin/bidvector/procurement/FieldContract.kt  # NOTICE_TITLE
  - procurement/src/main/kotlin/bidvector/procurement/CollectionPolicy.kt  # bidNtceNm 계약 행
  - procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt   # title 슬롯 채움
  - procurement/src/main/kotlin/bidvector/procurement/KonepsPresentInSets.kt  # presentIn 집합이 필드를 열거하면
  - procurement/src/test/kotlin/bidvector/procurement/**
  - adapters/src/main/kotlin/bidvector/adapters/koneps/**               # 키 가림·업종 배선에 필요할 때만
  - adapters/src/test/kotlin/bidvector/adapters/koneps/**
  - adapters/src/main/kotlin/bidvector/adapters/persistence/**          # title 영속 경로가 비어 있으면
  - adapters/src/test/kotlin/bidvector/adapters/persistence/**
  - app/src/main/kotlin/bidvector/app/collection/**                     # D-6F8-3 러너
  - app/src/main/kotlin/bidvector/app/wiring/**                         # 수집 빈 배선
  - app/src/test/kotlin/bidvector/app/**                                # 러너·배선 test, 아키텍처 게이트
  - app/src/test/kotlin/bidvector/archfixture/violating/**              # 게이트 양성 대조
  - app/build.gradle.kts                                                # 의존이 필요하면
  - workflow/build.gradle.kts
  - config/quality/gate-tests.properties                                # 공유 파일 — 추가만
  - config/quality/architecture-policy.properties                       # 공유 파일 — 추가만
  - config/quality/member-effects*.properties                           # workflow 효과 도출이 새 멤버를 잡으면
  - docs/discovery/**                                                   # 필드 계약 정본 표(구현 레인이 파일 실측)
  - fixtures/**                                                         # 공고명 필드가 corpus 기대값에 닿으면 — 근거 기록
  - expected/**
  - reports/evidence/m6/6f8/**
  - milestone-6.md                                                      # 착수·종결 문단(팀장) — 공유 파일
out_of_scope:
  - adapters/src/main/resources/db/migration/**    # 마이그레이션 없음 예상 — 필요하면 멈추고 보고
  - ml-engine/**
  - 개찰·예비가격·면허 요건 수집
  - 운영 DB·스케줄러·알림 발송
```

## acceptance

- CI job 명령 그대로(버릴 worktree, `--rerun-tasks` 캐시 우회 한 번): Kotlin `check` · `qualityBaseline` · `one-command-check.sh`.
- 변이 실측(구현 레인이 명령·exit): use case 가 `canonicalize` 대신 원시 키 직접 추출 · 공고명 키 리터럴을 어댑터에 박기 ·
  탈락 한 건 조용히 버리기 · 쿼터 초과 뒤 계속 부르기 · 러너 기본 켜짐 · 서비스 키를 예외 메시지에 싣기 — 전부 RED.
- **D-6F8-5 실수집 확인**(팀장, verifier ready-for-review 뒤).

## rollback

`in_scope` 경로 한정 restore(`<base>` = merge-base), 목록 기계 산출. 공유 파일 셋(`gate-tests`·`architecture-policy`·
`milestone-6.md`)은 커밋 해시 hunk 격리. **개발 DB 컨테이너는 `docker rm -f bid-vector-v2-dev`(볼륨 포함)로 버린다** — 운영
데이터 아님. 임시 worktree ①~⑥, `실측 HEAD`.

## 하네스 레인 변경 (상시 절)

착수 시점 없음.

## OPEN — 수령·신설

| ID | 방향 | 내용 |
|---|---|---|
| `OPEN-6F4-TITLE-INGEST` | **수령·닫음** | D-6F8-2 |
| `OPEN-6F4-NOTICE-BODY-SOURCE` | 변경 없음 | 본문은 상세 URL 뒤 |
| `OPEN-6F8-COLLECTION-SCHEDULE` | **신설** | 운영 스케줄링(주기·리스·동시 실행 방지) — 이 slice 는 일회성 러너만 |
| `OPEN-6F8-OPENING-COLLECTION` | **신설** | 개찰 결과·예비가격·면허 요건 수집 배선(포트는 있다) |

## 리뷰 레인

`verifier`(opus) + `code-reviewer`(**`model: sonnet` 명시**) 병렬 + **`privacy-gate`**(서비스 키·원문 로그 — 이 slice 의 핵심
위협) — 전용 정의가 없으면 범용 에이전트 대행. 마이그레이션 없음 → `migration-reviewer` 해당 없음(생기면 붙인다). 공개 HTTP
계약 변경 없음 → `contract-keeper` 해당 없음. **Codex 없음**(되돌리기 어려운 경로·계약 파일 아님). 종결은 `verifier
ready-for-review` + D-6F8-5 실수집 확인 + 사용자 승인.
