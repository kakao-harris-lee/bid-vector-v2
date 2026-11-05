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

재빌드 없이: 설정 인자로 옛 값 — **목록 정본은 `app/src/test/resources/rollback/koneps-portal-02-rollback-args.txt`** 이고
`operations` 두 축은 **행 전수**여야 한다(한 칸만 주면 기동 실패 — cr H-1, `KonepsRollbackArgumentsTest` 가 잠근다).
코드: in_scope 경로 한정 `git restore --source=<병합한 main SHA> …`; 목록 기계 산출(`rollback.md`).

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

### 계약 갱신 2026-11-05 (팀장, 2차) — 준비 대기의 셋째 사본

**더하는 in_scope 경로**: `web/e2e/harness/journey-up.mjs`.

**사유.** fixture 를 `02` 로 바꾼 뒤에도 CI `web` job 의 W-13 이 붉었다. 떨어진 자리는 수집이 아니라
**fake KONEPS 준비 대기**다 — 그 대기가 경로를 리터럴로 박고 2xx 를 요구해서(`probe.ok`) `02` fixture
아래서 영구 404 를 받고 30초 뒤 죽었다(run 42025625713: 「공고 4건 준비」 → 「30000ms 안에 기다리지
못했다」 → 「요청 75건 받고 종료」). 즉 **오퍼레이션 이름의 사본이 셋**이었다 — 출하 기본값 · fixture ·
이 준비 대기.

**이것은 수정 라운드가 만든 사각이 아니라 착수 조사가 놓친 자리다.** 착수 때 `journey-up.mjs` 는
「`base-url` 만 덮고 오퍼레이션 경로는 기본값을 쓴다」까지만 읽고 **준비 대기의 리터럴을 세지 않았다.**
그래서 차단 보고에도 그 사본이 빠졌다.

**선택 (A) 승인(팀장).** 준비 판정을 **이름에서 떼어낸다** — 묻는 것이 「서버가 응답하는가」이지 「그
오퍼레이션이 있는가」가 아니다. 404 도 서버가 살아 있다는 증거다. 여정의 실제 단언은 뒤의 공고 카드가
지므로 준비 판정이 느슨해져도 잃는 방어가 없고, 이름 사본이 **둘로 줄어** 다음 개편에서 이 자리가 다시
깨지지 않는다. 다만 「**fake 가 아닌 다른 프로세스가 그 포트를 잡은 경우**」를 구별해야 하므로 fake 가
모든 응답에 **고유 헤더 하나**를 싣고 준비 대기가 그 헤더를 확인한다(비용 두 줄) — 그래서
`web/e2e/harness/fake-koneps.mjs` 도 계약의 「필요하면」에 걸려 실제로 편집한다.

**개찰 축 확인(팀장 요청).** 이 여정은 `bidvector.collection.mode=once` 하나만 돌리므로 A값·기초금액
오퍼레이션 이름은 이 하네스에 **없다** — `journey-up.mjs`·`fake-koneps.mjs`·fixture 어디에도
`BsisAmount`·`BidPrceCalclAInfo` 문자열이 없다(계수 0).

**main 병합.** `origin/main` `4953d743` 을 이 브랜치에 병합한다(#122·#125·#126). rollback 의 공유 파일
복원 원천도 그 **고정 SHA** 로 옮긴다 — 움직이는 `origin/main` 을 쓰지 않는다.

### 계약 갱신 2026-11-05 (팀장, 3차) — 되돌림 인자 목록의 자리

**더하는 in_scope 경로**: `app/src/test/resources/rollback/**`.

**사유.** cr H-1 수정의 형태를 팀장이 지정했다 — 「인자 목록을 test 리소스로 두고 runbook 은 그 파일을
가리키게 하면 사본이 하나 줄어든다」. 그 자리가 `app/src/test/kotlin/**` 밖이라 1·2차 갱신 목록에
없었다. 목록은 `koneps-portal-02-rollback-args.txt` 하나이고, 이 파일을 읽는 것은 runbook §8-4(문서)와
`KonepsRollbackArgumentsTest`(거동) 둘이다.

**main 병합 원천은 `319fe212` 에 고정한다.** 이 라운드 중 `origin/main` 이 `31551579`(#129)로 또
움직였으나 **이 브랜치는 그것을 병합하지 않았다** — 복원 원천은 「실제로 병합한 main」이어야 하므로
`319fe212` 그대로다. 병합하지 않은 main 커밋에서 복원하면 이 브랜치가 가진 적 없는 내용이 들어온다.
