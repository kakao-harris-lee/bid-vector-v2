# M9 / portal-02 — 조달청 입찰공고정보서비스 `02` 개편 대응

- base: main `79f3e5c4` · 브랜치 `feature/m9-portal-02` · worktree `bid-vector-v2-m9-portal-02`
- 근거: data.go.kr 공지 2026-11-03 「[조달청] 조달청_입찰공고정보서비스 OPEN API 서비스 변경안내」(NOTICE_0000000005128, 사용자 전달 2026-11-05) — BASE URL `https://apis.data.go.kr/1230000/ad/BidPublicInfoService02`, 각 오퍼레이션명 뒤 `02`(예 `getBidPblancListInfoCnstwk02`). 응답의 공동도급의무지역·가산지역·부대공종·첨부파일(공고규격서·현장설명서·표준공고서 URL) 항목이 별도 오퍼레이션으로 분리. **기존 URL 은 90일 유지 후 중단**(공지에 날짜 없음 — 공지일 기준 ≈2027-02-01).
- 조사(2026-11-05, 읽기 전용): 우리 production 이 부르는 이 서비스 오퍼레이션은 다섯(공고 목록 공사·용역 · A값 · 기초금액 공사·용역). 분리 대상 필드를 읽는 production 자리 **0**(첨부 키는 `OPEN-3C-ATTACHMENT-FIELD-CONTRACT` 로 미등재·미배선). 원장·원문 행은 `SourceEndpoint` 토큰 키라 경로 이름 변경에 무효화되지 않는다. 운영에서 지금 수집이 돌지 않는다.

## 결정

| ID | 결정 |
| --- | --- |
| D-1 | 순수 이름 전환: `DEFAULT_KONEPS_BASE_URL` → `…/ad/BidPublicInfoService02`, 그 서비스의 기본 오퍼레이션 경로 다섯 → `…02`. **`as/ScsbidInfoService`(낙찰정보) 기본값은 불변**(공지 대상 아님 — test 로 잠근다). 설정으로 옛 값을 다시 줄 수 있는 것(재빌드 없는 되돌림)은 그대로. |
| D-2 | 분리된 네 오퍼레이션은 **부르지 않는다** — production 판독자 0 을 근거로 evidence·capability map 에 기록하고, 첨부 축(`OPEN-3C-ATTACHMENT-FIELD-CONTRACT`)·지역 의무 축이 열릴 때 새 오퍼레이션을 쓴다는 것을 그 OPEN 행에 적는다. |
| D-3 | test mock 의 문자열 접두·접미 라우팅(`MockOpeningKonepsHttp` 의 `startsWith/endsWith`, `NoticeListCorpus` 의 `removePrefix`, `MockKonepsServer` 의 경로 하드코딩)이 `02` 접미에서 **조용히 오라우팅**한다 — 라우팅을 정확 일치 표(오퍼레이션 → 응답)로 바꾸고 표에 없는 경로는 **실패**(404 + test 실패)로. |
| D-4 | 응답 필드가 바뀌었는지(공지가 적지 않은 축)는 **실 호출 없이는 모른다**. 이 slice 는 실 호출을 하지 않는다. 운영 반영 전 1회 스모크(오퍼레이션별 `numOfRows=1` 호출 다섯, 총 5 호출)는 **사용자 확인 뒤** 별도 실행 — runbook 에 절차와 판독 기준(봉투 `resultCode`·필드 계약 12 키 존재)을 적는다. |
| D-5 | fixtures(`fixtures/input/koneps-collection-*.json`)는 2025 참고자료 **문서 인용**이라 고치지 않는다. `fixtures/manifest.yaml` 의 공식 문서 목록에 개편 공지 행을 더하는 것만(필요하면). |

## in_scope

- `app/src/main/kotlin/bidvector/app/collection/CollectionProperties.kt` · `app/src/main/kotlin/bidvector/app/collection/OpeningCollectionProperties.kt`
- `app/src/test/kotlin/bidvector/app/collection/**` · `app/src/test/kotlin/bidvector/app/wiring/**` · `adapters/src/test/kotlin/bidvector/adapters/koneps/**`(mock 라우팅)
- `docs/runbook/m6-6g-real-collection.md` (또는 수집 runbook 의 해당 절) · `docs/discovery/capability-map.md`(공지 등재·첨부 OPEN 행) · `docs/discovery/regression-ledger.md`(필요 시) · `fixtures/manifest.yaml`(문서 행만)
- `config/quality/**` 게이트 등재 · `reports/evidence/m9/portal-02/**`

## out_scope

새 오퍼레이션 호출 · 첨부·지역·공종 축 구현 · 낙찰정보서비스 · 실 API 호출 · 운영 반영.

## acceptance

- CI Kotlin `check` job 명령 그대로 exit 0.
- test: 기본 base·경로 다섯이 `02`(부팅/설정 test) · ScsbidInfoService 기본값 불변 · mock 이 `…Cnstwk02`·`…BsisAmount02` 를 정확히 가르고 미등록 경로는 실패 · 기존 수집 E2E 전부 초록.
- 변이: 기본 경로 하나를 옛 이름으로 → RED · mock 을 접두 라우팅으로 되돌림 → RED(정확 일치가 지키는 것).
- `git grep -n 'BidPublicInfoService"'` 로 옛 base 리터럴이 main 에 0.

## rollback

재빌드 없이: `bidvector.koneps.base-url`·`operations.*.path` 설정으로 옛 값. 코드: in_scope 경로 한정 `git restore --source=79f3e5c4 …`; 목록 기계 산출(`rollback.md`).

## 하네스 레인 변경

없음.

## 계약 갱신 2026-11-05 (팀장)

**더하는 in_scope 경로**

- `web/e2e/harness/fixtures/notice-list.json` — `operation` 값 넷을 `…02` 로.
- `web/e2e/harness/fake-koneps.mjs` — **필요하면**(기존 정확 일치 라우팅을 유지하고 옛 이름은 404 그대로).

**사유.** D-1 의 이름 전환이 **web 여정 e2e 를 붉힌다.** `fake-koneps.mjs` 는 요청 경로의 오퍼레이션
이름을 이 fixture 의 `operation` 값과 **정확 일치**로 라우팅하고 모르는 이름에 404 를 낸다. 그런데
`journey-up.mjs` 의 수집 단계는 `bidvector.koneps.base-url` 만 덮고 **오퍼레이션 경로는 출하 기본값**을
쓴다. 그래서 `02` 기본값에서 수집 요청이 전부 404 가 되고 공고가 한 건도 적재되지 않아 여정 단언이
무너진다. 그 명령이 도는 자리는 둘 — CI `web` job 의 W-13 과 **`check` job 의 S-20**
(`tools/one-command-check.sh` 가 web 전건을 품는다). 즉 `./gradlew --no-daemon check` 는 초록인데
CI 의 `check` job 은 붉다. 고치는 자리가 `web/**` 뿐이라 착수 계약의 in_scope 로는 닫을 수 없었다.

**범위 한정.** 여정은 `bidvector.collection.mode=once` · `categories=construction` 하나만 돌리므로
이 fixture 에 필요한 이름은 **공고 목록 공사 하나**다(개찰 축 A값·기초금액은 이 여정이 부르지 않는다 —
`journey-up.mjs` 에 `opening-collection` 언급 0). `_provenance.sources[].sha256` 은 **출처 문서**를
핀하고 이 fixture 자신의 내용을 핀하지 않으므로(그 값을 재산출하는 스크립트도 없다) `operation` 치환이
그 블록을 무효화하지 않는다.

**acceptance 보강.** 전건 acceptance 의 정본은 **PR CI 의 `check`·`web` job**(같은 명령)이다 — 호스트
빌드 큐 포화로 전건을 로컬에서 반복하지 않는다(팀장 결정 2026-11-05). 변이와 rollback ④⑤⑥ 은 표적
task 로 잰다.
