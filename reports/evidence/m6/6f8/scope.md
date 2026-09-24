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

## 계약 갱신 (1) — 검토 라운드 1 판정과 팀장 결정 (2026-09-24, 팀장)

판정 SHA `4ba88f46` — verifier **not-ready**(HIGH 1 · MEDIUM 5 · LOW 2) · code-reviewer **머지 불가**(HIGH 1 · MEDIUM 2 · LOW 9) ·
privacy-gate 위반 0 · 권고 5 · 확인 불가 4. 재작업 **1/5**. 리포트 `_workspace/m6-6f8/1{0,1,2}_*.md`(gitignore) — 판정은 PR 코멘트로.

**D-6F8-6 — 정규화 우회 게이트를 모듈 전체로 넓힌다(verifier F-1 HIGH).** 규칙이 `bidvector.workflow.collection..` 한 패키지만 봐서
원시 키 읽기를 이웃 패키지 헬퍼로 옮긴 변이(M1)가 전체 `check` 초록이었다. **`workflow..`·`app..` production 전체**에서 원문 키 접근
타입 참조·통과 전용 멤버 접근을 금지하고 허용 집합은 정책 파일(`architecture-policy.properties`) 키로. 오늘 그것을 쓰는 곳은 use case
하나뿐이라 production 은 깨지지 않는다. 이웃 패키지 음성 fixture + M1 재적용 RED. 게이트 술어 변경 — 표적 재검증.

**D-6F8-7 — 식별자 형식 위반은 탈락이다(code-reviewer HIGH).** `bidNtceOrd` 가 비어 있지 않지만 세 자리 숫자가 아니면(`"1"`·`"01"`·
`"00A"`·앞뒤 공백) `canonicalize` → `NoticeRound.of` 의 `require` 가 던져 **러너까지 올라가 수집 전체가 죽는다** — 탈락 사유 없이,
그 슬롯 회계 없이, 재실행해도 같은 자리에서. `ParseFailureKind.IDENTIFIER` 는 정의만 있고 main 에서 한 번도 방출되지 않았다(설계
의도와 거동의 갈림). **변환 지점 `canonicalize` 안에서** `NoticeRound.of`(와 같은 형식 검사를 하는 번호 쪽이 있으면 그것도) 실패를
`Dropped(CollectionParseFailure(IDENTIFIER))` 로 접는다 — use case 는 손대지 않는다. 회귀 잠금 세 계층: `CanonicalizeTest`(비정형
차수 넷 → `Dropped(IDENTIFIER)`) · `CollectNoticesUseCaseTest`(섞인 배치에서 등식·사유·원문 저장·다음 항목 계속) · E2E(비정형 항목
하나 → `SUM(dropped)` 반영, exit 0). **같은 계열 전수**: `canonicalize` 경로에서 입력값으로 `require`/`check`/`!!`/파싱 예외가 날 수 있는
자리를 구현 레인이 전수해 표로 내고, 전부 탈락 사유로 접거나 도달 불가 근거를 적는다(실데이터를 처음 붙이는 slice 다).

**D-6F8-1 문면 정정(verifier F-6 · 구현 레인 0단계 4번).** 「원문 저장 + 영속 한 트랜잭션」 → **「원문을 먼저 커밋하고, canonical 영속은
항목 단위 트랜잭션」**. `JdbcNoticeRepository` 는 `TransactionBoundary` 참여자가 아니고, 묶으면 `Rejected` 경로의 rollback 이 원문
append 까지 되돌린다 — 기존 `RawObservationStore` 계약 ⑤(「원문은 이후 실패와 무관하게 남는다」)와 같고 위협 ① 에 더 강하다.

**D-6F8-8 — MEDIUM·LOW 는 같은 라운드에 일괄(승인 전).**
- verifier F-3 · privacy R5: E2E 에 **표준 출력·표준 오류 캡처**를 더하고 예외는 **cause 체인 전체**에서 키 부재를 단언(M4a `System.err` 변이 RED).
- verifier F-5: 「오늘 = KST」 경계 test — KST 와 UTC 날짜가 갈리는 시각을 고정한 clock 으로(M5 UTC 변이 RED).
- verifier F-4: checklist 문면 — 공고명 키 리터럴 게이트는 **보조** 잠금이고 주 잠금은 D-6F8-6 규칙이다.
- verifier F-7(a) `NoticeTitleCanonicalizeTest` 를 `gate-tests.properties` 에 등재 · (b) 멱등 등식을 「재실행 `normalized = 0`, 재실행
  `duplicate = 1차 normalized + 1차 duplicate`」로 정정(D-6F8-5 5번도 같은 등식으로 읽는다).
- privacy R3: `TransportFailed.message`·`Failed.detail` 채널을 예외 클래스 이름만 담도록 접는다(선례 `HttpAttachmentDocumentSource`).
- code-reviewer MEDIUM 2 · LOW 9: 리포트 `11_code_review.md` 전문을 읽고 전부 처분(수정 또는 근거 있는 등재)한다.
- `reports/evidence/m3/3a/policy-values.md` §1.3 공고명 행 + P-15 등재를 **in_scope 에 넣는다**(P-14 선례 — 정본 문서). 구현 레인이 쓴다.

**D-6F8-9 — 실수집 전 조건과 OPEN (privacy R1·R2 · verifier F-2).**
- 실행 명령(팀장, D-6F8-5): 키는 **원문형** 변수를 서브셸에서 파일로부터 읽어 **그 프로세스 환경에만** 넘긴다 — 명령 문자열·argv·셸
  history·에이전트 transcript 에 값이 나타나지 않는다. `export`·`set -x`·`echo`·`-D`·`--args` 금지, `jdk.*` DEBUG 금지.
- **`OPEN-6F8-RAW-PII-RETENTION` 신설 → 6B-3**: `raw_observation.payload` 가 항목 원문을 그대로 저장하므로 KONEPS 담당자 이름·전화·
  이메일 키(`ntceInsttOfcl*`·`dminsttOfcl*`)가 **영속된다**. 이 실수집이 6B-3 대상 표를 늘린다. 개발 DB 는 확인 뒤 `docker rm -f -v`
  로 폐기할 수 있음을 D-6F8-5 에 명시하고, evidence 에는 담당자 키가 있는 행 **수만** 싣는다.
- **`OPEN-6F8-QUOTA-XML-ENVELOPE` 신설**(verifier F-2, 기존 어댑터 분류): data.go.kr 게이트웨이의 XML 오류 봉투로 한도 초과가 오면
  `StructureFailure` 로 분류돼 멈추지 않고 남은 슬롯마다 한 번씩 부른다(최대 조회일 × 업종 호출). 실수집에서 슬롯 원인이 연달아
  `StructureFailure` 로 찍히면 팀장이 러너를 중단한다.
- **`OPEN-6F8-COLLECTION-RUN-CATEGORY` 신설**(구현 레인 제안): `collection_run` 에 업종 열이 없다 — 업종별 회계는 러너 로그·결과 타입에만.
- 저장 `Rejected` 는 회계상 `duplicate` 로 접힌다(사유 어휘가 `Accounting.kt` 에 없다) — 알려진 제한, 건수는 `WriteTally.rejected` 와
  `rejected_write`.
- privacy R4(로거·키 읽기 게이트 사각, 현재 사용 0) — 알려진 제한 등재.

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
  - reports/evidence/m3/3a/policy-values.md                              # D-6F8-8 — §1.3 공고명 행 + P-15(P-14 선례)
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
