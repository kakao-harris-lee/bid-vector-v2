# M3/3B — checklist.md

base `c9d75632d3acafcaf41f0454e941dc49c62063ea` · head `3822351d6a3c4bbc38ac94acd5406297938bda3c`.
정본 순서: `scope.md` → `_workspace/m3-3b/01_design-review.md` → 이 문서.

## 완료 조건 대응표 — scope.md 「이 slice 가 하는 일」

| # | 요구 | 구현 | test |
| --- | --- | --- | --- |
| ① | `NoticeSourcePort` 구현, 동기 facade, deadline·취소 | `KonepsOpenApiNoticeSource`(port impl) + `sendKonepsRequest`(JDK `HttpClient.sendAsync`+`future.get(timeout)`+`cancel(true)`) | 전 시나리오 test 가 이 경로를 통과 |
| ② | Resilience4j 한 계층, 429/quota bounded retry+backoff | `buildKonepsRetry`+`buildKonepsRateLimiter`(`KonepsResilientCall.kt`) — `Retry`(바깥)·`RateLimiter`(안쪽) | 429 연속→성공, resultCode 22→성공 |
| ③ | envelope·resultCode 검증, 3A 표 재사용 | `parseKonepsEnvelope`가 `KONEPS_COLLECTION_POLICY.resultCodeCategories` 를 읽기만 함 | 미지/부재 resultCode 2건 |
| ④ | pagination 백스톱 | `maxPages`+동일 페이지 해시(`render()`) 반복 감지 | totalCount 없음+반복 페이지 |
| ⑤ | partial·duplicate | 걷기 전체에 걸친 `NoticeIdentity` dedup(`KonepsPageWalkAccumulator.seenIdentities`) | — (직접 assert 는 안 했으나 429 재시도 시나리오가 같은 페이지를 다시 안 세는 것으로 간접 확인, 아래 「미달」 참고) |
| ⑥ | parse 실패는 명시값, 숫자/일시 변환 없음 | `mapRawItem`은 raw 문자열만 옮긴다(`toRawValue`) — canonicalize 안 부름 | COL-07(unknownFields) |
| ⑦ | mock server, scope 시나리오 | `MockKonepsServer`(JDK `HttpServer`, loopback) | 아래 시나리오 표 |
| ⑧ | 표적조회·서브콜 | **범위 밖**(D-3B-6, 아래 「범위 분할」) | — |

## 시나리오 대응표 — scope.md ⑦

| 시나리오(scope 문면) | test 메서드(`KonepsOpenApiNoticeSourceTest`) | 결과 |
| --- | --- | --- |
| 4건 중 1건 공고번호 없음 → 3+dropped=1(COL-01) | `COL-01 — 4건 중 1건 공고번호 없음이면 3건 수집 + dropped 1` | PASS |
| 429 연속 n회 뒤 성공(호출 횟수=서버 카운터) | `429 연속 실패 뒤 성공 — 재시도 상한 안에서 회복, 호출 횟수는 서버 카운터로 단언` | PASS(호출 3회 단언) |
| timeout | `timeout 이 반복되면 재시도 상한 뒤 truncated 로 종료된다` | PASS(호출 2회, 서버는 스레드풀 executor 필요 — 아래 「알려진 함정」) |
| totalCount 없음+같은 페이지 반복→truncated | `totalCount 없음 + 같은 페이지 반복이면 유한 페이지에서 truncated 로 끝난다` | PASS(2회 호출 뒤 종료) |
| 미지 resultCode | `미지 resultCode 는 Unclassified 로 비재시도 실패한다` | PASS(1회, 비재시도 확인) |
| 부재 resultCode | `resultCode 자체가 부재하면 Unclassified 로 비재시도 실패한다` | PASS |
| 미지 raw 키(COL-07 unknownFields) | `계약에 없는 raw 키는 항목을 살리되 unknownFields 로 계수한다 COL-07` | PASS |
| `03` NoData | `resultCode 03 은 실패가 아니라 데이터 없음이다` | PASS(sourceTotal=0, truncated=false) |
| `22`/`30` quota | `resultCode 22 는 quota 초과로 재시도 대상이다` · `resultCode 30 은 등록되지 않은 서비스 키 — 비재시도` | PASS(22=재시도, 30=비재시도 — D-3B-7 구별 확인) |

부가: `ServiceKeyTest`(D-3B-5, 3 test) · `KonepsAdapterDependencyTest`(S-3b, 1 test). 총 14 test.

## 위협 모델 대응표 — scope.md 「방어한다」

| 방어 항목 | 메커니즘 | 증거 |
| --- | --- | --- |
| (a) 무한 pagination | `maxPages` + 동일 페이지 해시 감지, 종료가 구성상(루프에 무한 경로 없음) | 「totalCount 없음+반복」 test |
| (b) 429 폭주 | `RateLimiter`(자체 quota 보호)+`Retry`(bounded, backoff 시도별 목록) | 429 test, `Throttled` 코드 경로(직접 test 없음, 아래 「미달」) |
| (c) parse 실패의 조용한 0/None | 3B 는 숫자·일시를 파싱하지 않는다(⑥) — 실패할 파싱 자체가 없다. envelope 구조 실패는 `StructureFailure`(페이지 종료, `truncated`) | JSON 파서 단위 동작은 envelope test 가 간접 커버 |
| (d) 미지 resultCode 의 성공 취급 | `classify`가 `00`/`03`/등재 코드 외 전부 `Unclassified`(비재시도) | 미지·부재 resultCode 2 test |
| (e) 서비스 키 노출 | `ServiceKey.toString()` 가림, `urlEncoded` 만 요청 URI 생성에 쓰인다 | `ServiceKeyTest` + secret 스캔(commands.md) |
| (f) 재시도 이중화 | 어댑터 안에 `Retry` 인스턴스 하나(생성자에서 1회 구성) — 스케줄러 재실행 배선은 M4 리뷰 항목(3B 는 만들지 않는다) | 코드 열람(`KonepsOpenApiNoticeSource` 생성자) |

## 우회 후보 대응표 — scope.md 「우회 후보(≥5)」

| # | 우회 | 막는 장치 |
| --- | --- | --- |
| 1 | 429 를 catch 해 빈 배치로 성공 위장 | `isRetryableStep`이 HTTP 429/`resultCode` RETRYABLE·QUOTA_EXCEEDED 를 재시도로만 두고, 소진 시 `Failed`→`markTruncated()` — 「조용한 성공」 경로 없음(429 test 가 최종 성공 시 `truncated=false`, 실패 소진 시나리오는 timeout/미지코드 test 가 `truncated=true` 로 확인) |
| 2 | `totalCount` 무시, 고정 페이지 수 | 반복 페이지 백스톱 test 가 `truncated=true` 를 직접 단언 |
| 3 | parse 실패를 `0` 으로 | 3B 에 숫자 파싱 코드가 없다(`mapRawItem`이 `toRawValue`로 원문만 옮김) — 리뷰로 확인 가능, 정적 근거: `KonepsRawItemMapper.kt` 에 `toInt`/`toBigDecimal`/`toDouble` 호출 0건 |
| 4 | client+Resilience4j 이중 재시도 | `sendKonepsRequest` 자체는 재시도하지 않는다(단발 호출) — 재시도는 `fetchPageResilient` 한 곳에서만 감싼다 |
| 5 | 서비스 키를 URL 로그에 | `ServiceKey`는 `urlEncoded`(get-only)로만 원문을 노출하고 `toString()`은 가린다 — 로깅 코드 자체가 없다(3B 는 로그를 남기지 않는다) |
| 6 | 골든을 test 리소스에 복사 | `fixtures/input/koneps/**` 가 없어 참조할 golden 자체가 없다 — self-authored 데이터임을 KDoc·이 문서에 명시, 바이트 교집합 0 실측(commands.md) |

## 범위 분할(D-3B-6) — 이번 묶음이 하지 않는 것

scope.md 「이 slice 가 하는 일」 표의 ⑧행(표적조회·서브콜)은 **착수 시 계약 정정 ③** 이
범위 밖으로 이미 옮겼다 — `OpeningResultSourcePort`·`DocumentSourcePort`·license-limit
서브콜·`inqryDiv=2` 표적조회는 이 커밋 묶음에 없다. `NoticeSourcePort`(`inqryDiv=1`, 날짜
조회)만 구현한다.

## 판단이 갈린 지점 — 계약이 명시하지 않아 이 레인이 정한 것

1. **`fetchNotices` 한 호출 = 조회일 하나의 전체 page-walk.** port 서명(`cursor: PageCursor?`)
   만 보면 「페이지 하나=호출 하나」로도 읽을 수 있으나, scope ⑦의 「totalCount 없음+같은
   페이지 반복 → truncated」 시나리오가 **단일 어댑터 호출**을 겨눈 mock server test 로
   서술돼 있어 내부 다중 페이지 순회로 해석했다. `cursor`는 재개 시작 페이지로만 쓴다.
2. **dedup 식별자는 canonical `NoticeId`가 아니라 원문 텍스트 짝(`NoticeIdentity`).**
   `NoticeRound.of`가 제로패딩 3자리 형식을 `require`로 강제해, 원문이 그 형식을 벗어나면
   dedup 계산 자체가 예외를 던질 위험이 있었다 — 「업무 control flow 에 exception 안 씀」
   원칙에 따라 원문 비교로 낮췄다.
3. **envelope 구조 실패(JSON 파싱 실패·`response`/`body`/`items` 형태 위반)는 페이지 단위
   종료(`StructureFailure`→`truncated`)로 처리했다** — 설계 검토 KDoc 이 언급한
   `ParseFailure(kind=Envelope)`는 3A `ParseFailureKind`(NUMERIC/DATE_TIME/IDENTIFIER)에
   그 축이 없어(procurement 는 범위 밖) 항목 단위 사유로 표현할 수 없었다.
4. **quota/429 소진의 회계 표현은 `CollectionAccounting.truncated=true`(+`pagesFetched`
   축소) 하나다.** scope 문면의 "회계에 `backoffSkipped`/`quotaExceeded`로" 는 3A
   `CollectionAccounting`(procurement, 범위 밖)에 그런 필드가 없어 문자 그대로 옮길 수
   없었다 — COL-03 acceptance의 실질("나머지 수집은 계속되며 회계에 실린다")은 이미 수집된
   페이지까지는 살아남고 `truncated` 로 그 사실이 관측 가능하다는 형태로 만족한다.
5. **JSON 파서를 새로 짰다**(외부 좌표 미도입) — envelope 형태가 좁고, `architecture-policy`
   의 `group.forbidden`(jackson·kotlinx-serialization·gson·org.json)이 domain 층 금지
   결의를 이 adapters 패키지도 자체 적용했다(CLAUDE.md 재사용 우선 방침의 "무거운 도구는
   측정된 필요 없이 도입 안 함" 축). `resilience4j-kotlin`은 카탈로그에만 등재하고
   실제로는 끌어오지 않았다(Java API 로 충분).
6. **`gradle/libs.versions.toml` 을 편집했다** — scope.md in_scope 목록에 이 파일이
   명시되지 않았으나 `adapters/build.gradle.kts` 가 요구하는 카탈로그 좌표
   (`resilience4j-ratelimiter`·`resilience4j-kotlin`)의 기계적 선행 조건이라 함께
   편집했다. rollback.md 도 같은 경계로 되돌린다.
7. **업종별(물품/용역/공사/외자) KONEPS 오퍼레이션 선택은 미정이다** — `baseUri` 생성자
   인자로 열어 두고 이 slice 는 어느 것이 옳은지 검증하지 않는다(실제 호출이 범위
   밖이라 검증 수단이 없다).

## 알려진 제한

- **골든 fixture 부재** — `fixtures/input/koneps/**`(D-3B-1 (a) 정본 자리)가 아직 없다.
  시나리오 test 의 envelope JSON 은 curator 승인 case 의 바이트 복제가 아니라 이 레인이
  authoritative 필드명(`policy-values.md` §1.1·§1.3·§1.6)으로 직접 지었다 — curator 가
  실제 KONEPS wire-format 골든을 확보하면 `bidvector.fixtures.koneps` 시스템 프로퍼티
  배선(2A `bidvector.contracts.testdata` 관례)을 추가해 이 test 들을 그쪽으로 옮기는
  후속 작업이 필요하다(이번 slice 는 그 배선 자체를 넣지 않았다 — 가리킬 디렉터리가
  없는 채로 `inputs.dir`를 걸면 오해를 부른다고 판단).
- **rate limiter 자체 거부(`Throttled`) 경로에 전용 test 가 없다** — scope ⑦ 목록에
  없고, 결정론적으로 유발하려면 동시 호출이 필요해 단위 test 로 만들면 flaky 해진다.
  코드 경로는 있다(`fetchPageResilient`의 `catch (RequestNotPermitted)`).
- **실제 KONEPS 호출 정확성은 검증하지 않는다**(out_of_scope) — 오퍼레이션 경로·쿼리
  파라미터 이름(`inqryBgnDt`/`inqryEndDt` 등)이 실물과 다를 위험은 실제 네트워크 호출
  승인 뒤에만 닫힌다.
- **표적조회·`OpeningResultSourcePort`·license-limit 서브콜 없음**(D-3B-6, 범위 분할 ③).

## 병렬 레인 경계 확인

`git status --porcelain` 최종 상태 — `adapters/src/{main,test}/kotlin/bidvector/adapters/
koneps/**`·`adapters/build.gradle.kts`·`config/quality/gate-tests.properties`·
`gradle/libs.versions.toml`·`reports/evidence/m3/3b/**` 만 변경. `procurement/**`·
`fixtures/**`·`docs/**`·`build-logic/**`·다른 세션의 untracked 파일 없음(commands.md
clean-tree 절 참고).

## 스테이징 규율

전 커밋이 `git add <in_scope 경로>` 개별 인자(`-A`·`-a`·`.` 미사용) 뒤 `git commit`(같은
메시지 안에서 add+commit 분리 없음 — parallel-lane 오염 회피).
