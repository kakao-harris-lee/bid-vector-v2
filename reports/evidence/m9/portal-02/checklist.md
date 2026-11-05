# M9 / portal-02 — 점검표

- base: main `79f3e5c4` · 브랜치 `feature/m9-portal-02` · **마지막 산출물 자리 `7f51bdc0`**(main 병합)
- 병합한 main: **`319fe212`**(PR #124 머지) — 팀장 지시는 `4953d743` 이었으나 `fetch` 와 `merge` 사이에
  공유 ref 가 움직였다. 아래 「ref 가 움직였다」 항.
- 성격: **이름 전환 + mock 라우팅 구조 교체.** 실 조달청 호출 0 · DB 쓰기 0 · 운영 반영 0 · push/PR 0.
- 계약: 같은 디렉터리의 `scope.md`(D-1~D-5).

## acceptance 대조

| 항목 | 결과 |
| --- | --- |
| CI Kotlin `check` job 명령 그대로 exit 0 | **PR CI run 42028468446 `check` job pass**(판정 SHA `be07aaa1`) — 로컬 전건도 계약 갱신 전 HEAD 에서 exit 0 이었다(33m 30s) |
| 기본 base·경로 다섯이 `02`(부팅/설정 test) | `KonepsEndpointDefaultsTest` — 바인딩을 지난 값으로 열거 셋 + 두 서비스 경로 집합이 갈리는 경계 하나 |
| ScsbidInfoService 기본값 불변 | 같은 test 의 넷째 — base·개찰완료·개찰결과 목록 둘·예비가격 상세 둘. **RED 라운드에서 처음부터 초록**(통제군) |
| mock 이 두 축을 정확히 가르고 미등록 경로는 실패 | `MockKonepsRoutingTest` ⓐ 공고 목록 ↔ 기초금액 분리 · ⓑ 미등재 둘이 404 이고 `close` 가 실패로 올림. 두 이름은 기본 표에서 읽으므로 위 test 가 그 문면을 `…Cnstwk02`·`…BsisAmount02` 로 고정한다 |
| 기존 수집 E2E 전부 초록 | `commands.md` GREEN ② — `bidvector.app.collection.*` 전건 + 두 wiring test, 107 tests |
| 옛 base 리터럴 0 | `commands.md` 마지막 행(일치 0) |

## 변이

| 변이 | 기대 | 결과 |
| --- | --- | --- |
| ① 기본 경로 하나를 옛 이름으로(`…Cnstwk02` → `…Cnstwk`) | RED | 2/5 붉음 — 열거와 경계 양쪽이 잡았다 |
| ② mock 을 접두 라우팅으로 되돌림(정확 일치 → 접두 일치) | RED | 2/7 붉음 — 기초금액이 공고 목록 축으로 갔고 **E2E 「표본에 뽑힌 공고만 상세를 부른다」도 함께** 붉다 |
| ③ web fixture 하나를 옛 이름으로 | 404 | 출하 기본 경로 요청이 **404** — W-13 이 붉어지는 기구를 그 자리에서 쟀다(여정 전건은 CI W-13 이 든다, 팀장 결정) |
| ④ 준비 대기 표지를 안 싣는 서버를 그 포트에 | 준비 판정 false | 표지 없는 남의 `200` → **false**. 준비 술어가 「응답이 왔다」로 느슨해져도 **포트 오점유를 가른다**는 음성 대조다 |

변이 둘 다 **바꿔치우기**다 — 술어를 더한 것이 아니라 그 자리의 술어를 갈았다.

## 알려진 제한

1. ~~**web 여정 e2e 가 이 전환으로 붉어진다 — in_scope 밖이라 고치지 않았다(차단).**~~ **닫혔다 2026-11-05** —
   팀장이 그 경로를 in_scope 에 더했고(`scope.md` 「계약 갱신 2026-11-05」) fixture 의 `operation` 값 넷을
   `…02` 로 바꿨다. `fake-koneps.mjs` 는 손대지 않았다(정확 일치 유지 — 옛 이름 404 가 다음 개편에서도
   같은 자리를 소리내어 잡는다). 아래는 그 차단의 기구 기록이다.
   `web/e2e/harness/fake-koneps.mjs` 는 요청 경로의 오퍼레이션 이름을 fixture 의 `operation` 값과
   **정확 일치**로 라우팅하고 모르는 이름에 404 를 낸다. 그 fixture
   (`web/e2e/harness/fixtures/notice-list.json`)의 `operation` 값 넷이 옛 이름이고,
   `web/e2e/harness/journey-up.mjs` 는 `bidvector.koneps.base-url` 만 덮고 오퍼레이션 경로는 기본값을
   쓴다. 따라서 `02` 기본값에서 수집 요청이 전부 404 가 되어 **공고가 한 건도 DB 에 들어오지 않고**
   여정 단언(공고 카드)이 전부 붉다. 그 명령이 도는 자리는 둘이다 — CI `web` job 의 W-13 과
   **`check` job 의 S-20**(`tools/one-command-check.sh` 가 web 전건을 포함한다). 즉 `./gradlew --no-daemon check`
   는 초록이어도 **CI 의 `check` job 은 붉다.** 고치는 쪽은 그 fixture 의 `operation` 값 넷(또는
   journey 가 옛 경로를 인자로 주는 것)이고 둘 다 `web/**` 였다. `_provenance.sources[].sha256` 은
   **출처 문서**를 핀하고 이 fixture 자신을 핀하지 않으므로(재산출 스크립트도 없다) 값 치환이 그 블록을
   무효화하지 않는다.
2. **응답 필드가 바뀌었는지는 모른다**(D-4) — 공지가 적지 않고 이 slice 는 실 호출을 하지 않는다.
   운영 반영 전 1회 사전 확인 절차는 `docs/runbook/m6-6g-real-collection.md` §8.
3. **분리된 네 오퍼레이션은 구현하지 않았다**(D-2) — 공동도급의무지역·가산지역·부대공종·첨부파일.
   production 판독자 0 이 근거이고, 열릴 자리와 닫히는 조건은 capability map
   `OPEN-M9-PORTAL-02-SPLIT-OPERATIONS` 가 갖는다.
4. **out_scope 파일의 KDoc·문서가 옛 오퍼레이션 이름을 설명 문맥으로 든다** — `procurement/src/main/**`
   (`Ports`·`RawObservation`·`NoticeFacts`·A 축 필드 계약), `adapters/src/main/**`
   (`KonepsOpeningResultSource`·`KonepsLicenseLimitDocumentSource`), `milestone-6.md`, M3·M6 evidence,
   fixtures 입력·기대 파일. 전부 **주석·문서 문면**이고 어느 것도 경로를 짓거나 비교하지 않는다
   (`SourceEndpoint` 는 토큰 키다) — 거동 영향 0. 계약 in_scope 밖이라 손대지 않았다.
5. **`CollectionWiringTest` 의 override 경로 리터럴은 일부러 옛 이름 그대로다** — 「경로에서 업무
   대분류를 짓지 않는다」를 재려고 경로와 대분류를 고의로 어긋나게 주는 자리라 값이 임의여야 하고,
   `02` 를 붙이면 그 test 가 재는 축이 바뀌지 않으면서 「이 이름이 실재한다」는 거짓 함의만 생긴다.
6. **`MockKonepsServer` 의 오퍼레이션 이름은 리터럴이다** — `adapters` 는 의존 방향상 `app` 의 설정
   기본 표를 보지 못한다. 문면 정합은 `KonepsEndpointDefaultsTest` 가 같은 값을 잠그는 것으로만 선다.
7. **`fixtures/manifest.yaml`·`docs/discovery/regression-ledger.md` 는 바꾸지 않았다**(둘 다 in_scope,
   D-5 의 「필요하면」). manifest 의 `official_documents` 항목은 **보유한 문서 파일 + `sha256` 핀**을
   요구하는 형식이고 data.go.kr 공지는 우리가 보유한 체크섬 있는 산출물이 아니다 — 지어낸 핀을 넣는
   것이 비는 행보다 나쁘다. regression ledger 는 **legacy(`ed4b06c`) 회귀 사례** 전용 장부라
   (§0.1 의 다섯 필드가 legacy 근거를 요구한다) 외부 서비스 개편은 그 어휘에 들어가지 않는다.
   개편 사실과 미지의 자리는 capability map 의 OPEN 행이 갖는다.

## PR CI (전건 acceptance 정본)

| 라운드 | HEAD | 결과 |
| --- | --- | --- |
| 1 (run 42025625713) | `bea3a225` | `container`·`ml-engine` **pass** · `web` **fail**(step 20 W-13 — 준비 대기가 영구 404 로 30초 타임아웃) · `check` 중단 |
| 2 (run 42028468446) | **`be07aaa1`**(판정 SHA) | **네 job 전부 pass** — `check` 47m11s(`check`·`qualityBaseline`·S-20 세 step 전부 success) · `web` 2m57s · `container` 7m16s · `ml-engine` 9m34s |

**W-13 이 조용히 초록인 것이 아니다.** step 20 이 43초 실행되고(앞 라운드는 30초 타임아웃 뒤 실패)
로그가 「공고 4건 준비」 → 「수집 러너 끝(exit=0)」 → **여정 11 passed · flaky 0 · 건너뜀 0 ·
spec 4/4(등록부와 같다)** 다. 수집이 실제로 공고를 넣었고 화면 단언이 그것을 봤다.

로컬에서 web 전건(`npm ci` → playwright → docker 여정)을 반복하지 않는다(팀장 결정 2026-11-05, 호스트
빌드 큐 포화). 이 레인이 로컬에서 잰 것은 **fake KONEPS 라우팅 한 자리**이고(`commands.md` 「web fake
KONEPS 라우팅 실측」) 여정 전건은 CI 가 든다. `web/` 에 `node_modules` 가 없어 eslint 는 돌리지 않았다 —
바꾼 것이 JSON 데이터 값 넷이라 eslint 가 보는 축이 아니고, `python3 -c json.load` 로 파싱만 확인했다.

## ref 가 움직였다 (이 라운드의 사실)

`git fetch origin` 직후 `origin/main` 은 `4953d743` 이었고 팀장 지시도 그 SHA 였다. 그런데
`git merge origin/main` 이 병합한 둘째 부모는 **`319fe212`**(PR #124 머지)다 — 공유 저장소의 ref 가
그 사이에 움직였다. **귀결 둘**:

1. **rollback 복원 원천을 `319fe212` 로 바꿨다.** `4953d743` 을 쓰면 #124 의 줄까지 지운다 — 「성공하면서
   뜻을 잃는 명령」이라 종료 코드로는 안 보인다.
2. **복원 목록 산출에 `^319fe212` 가 필요하다.** 병합 뒤에는 `79f3e5c4..HEAD` 범위 diff 가 내 경로를
   내지 못한다. 그 제외를 빼먹은 첫 산출에서 **남의 slice 경로 열아홉**(`m9/budget-clear` 등)이 내 목록에
   섞여 나왔고, 커밋 목록을 눈으로 보고 잡았다. 이 사실을 남겨 둔다 — 등식이 비지 않은 것이 신호였다.

## in_scope 대조

`git log --no-merges --format= --name-only 79f3e5c4..HEAD | sort -u` 의 경로가 전부 in_scope 안이다
(기본값 둘 · `app` test 둘 디렉터리 · `adapters` koneps test · `config/quality` · runbook ·
capability map · **web 여정 하네스 셋**(계약 갱신 1·2차) · 이 evidence 디렉터리). 산출은 **`^319fe212`
를 더한** `git log --no-merges --format= --name-only 79f3e5c4..HEAD ^319fe212 | sort -u` 로 낸다.
in_scope 에 있으나 손대지 않은 것은 위 7항 둘.

**`fake-koneps.mjs` 는 결국 편집했다** — 계약 1차의 「필요하면」이 2차에서 발동했다. 준비 대기가 쓸
표지를 내보내고, 그 이름을 양쪽에 베끼지 않으려 모듈을 **import 안전**하게 했다(직접 실행일 때만 듣는다).

**이 slice 가 만든 새 파일 다섯 ↔ in_scope**: `MockKonepsRoutes.kt`·`SyntheticBidder.kt`·
`MockKonepsEnvelope.kt`·`MockKonepsRoutingTest.kt`(`app/src/test/.../collection/`) ·
`KonepsEndpointDefaultsTest.kt`(`app/src/test/.../wiring/`) — 다섯 다 in_scope 안이다. 뒤 둘은
**게이트 수정 라운드가 만든 것**이라 그 라운드에서 in_scope 와 다시 대조했다. `@Test` 를 가진 둘은 만든 커밋에서
`config/quality/gate-tests.properties` 에 사전순으로 등재했고, 나머지 셋은 discovery 애노테이션이
없어 `gateRegistrationGate` 모집단 밖이다 — 전건 `check` 가 그 등식을 양방향으로 확인했다.

## 하네스 레인 변경

없음 — `base..HEAD` 에 `CLAUDE.md`·`.claude/**` 커밋이 없다.
