# M3/3B — checklist.md

base `c9d75632d3acafcaf41f0454e941dc49c62063ea` · head — **이 문서를 담은 커밋 자신**(`git rev-parse HEAD`, verifier r2 저low 8건 일괄
수정 커밋). N-6(verifier r2) 재발 방지로 SHA 를 하드코딩하지 않는다 — 자기참조 SHA 를
문면에 박으면 그 문면을 담는 커밋 자체가 새로 계산돼 값이 반영구적으로 어긋난다(실측:
이 라운드 안에서 실제로 한 번 발생 — 아래 참고). 정본
순서: `scope.md` → `_workspace/m3-3b/01_design-review.md` →
`_workspace/m3-3b/02_verifier_report.md` → `_workspace/m3-3b/03_verifier_report_r2.md` →
이 문서.

## 완료 조건 대응표 — scope.md 「이 slice 가 하는 일」

| # | 요구 | 구현 | test |
| --- | --- | --- | --- |
| ① | `NoticeSourcePort` 구현, 동기 facade, deadline·취소 | `KonepsOpenApiNoticeSource`(port impl) + `sendKonepsRequest`(JDK `HttpClient.sendAsync`+`future.get(timeout)`+`cancel(true)`) | 전 시나리오 test 가 이 경로를 통과 |
| ② | Resilience4j 한 계층, 429/quota bounded retry+backoff, **회계에 quotaExceeded/backoffSkipped**(verifier r1 H-3 뒤 문면 그대로 충족) | `buildKonepsRetry`+`buildKonepsRateLimiter`(`KonepsResilientCall.kt`) + `KonepsAttemptCounters`(매 시도 관찰) + `CollectionAccounting.quotaExceeded`/`backoffSkipped`(procurement 좁은 확장) | 429 연속→성공(quotaExceeded=2), resultCode 22→성공(quotaExceeded=1) |
| ③ | envelope·resultCode 검증, 3A 표 재사용 | `parseKonepsEnvelope`가 `KONEPS_COLLECTION_POLICY.resultCodeCategories` 를 읽기만 함 | 미지/부재 resultCode 2건 |
| ④ | pagination 백스톱 | `maxPages`+동일 페이지 해시(`render()`) 반복 감지(직전 한 페이지와만 비교 — 아래 「알려진 제한」) | totalCount 없음+반복 페이지 |
| ⑤ | partial·duplicate | 걷기 전체에 걸친 `NoticeIdentity` dedup(공고번호는 canonical `NoticeNumber`, 차수는 형식이 맞으면 canonical·아니면 원문 — verifier r1 M-2) | M-2 test(canonical 같음·원문 다름 → duplicate) |
| ⑥ | parse 실패는 명시값, 숫자/일시 변환 없음 | `mapRawItem`은 raw 문자열만 옮긴다(`toRawValue`, JSON boolean 도 토큰 텍스트 그대로 — verifier r1 M-1) | COL-07(unknownFields), M-1(boolean byte-identity) |
| ⑦ | mock server, scope 시나리오 | `MockKonepsServer`(JDK `HttpServer`, loopback) | 아래 시나리오 표 |
| ⑧ | 표적조회·서브콜 | **범위 밖**(D-3B-6, 아래 「범위 분할」) | — |

## 시나리오 대응표 — scope.md ⑦(N-8 정정: 문면은 **6 항목**, 표는 D-3B-7 파생 4 항목을 더해 **10 행** — 부재 resultCode·03 NoData·22/30 quota 구분은 scope ⑦ 문면 자체가 아니라 D-3B-7 이 나중에 확정한 것)

| 시나리오(scope 문면) | test 메서드(`KonepsOpenApiNoticeSourceTest`) | 결과 |
| --- | --- | --- |
| 4건 중 1건 공고번호 없음 → 3+dropped=1(COL-01) | `COL-01 — 4건 중 1건 공고번호 없음이면 3건 수집 + dropped 1` | PASS |
| 429 연속 n회 뒤 성공(호출 횟수=서버 카운터) | `429 연속 실패 뒤 성공 — 재시도 상한 안에서 회복, 호출 횟수는 서버 카운터로 단언` | PASS(호출 3회, quotaExceeded=2) |
| timeout | `timeout 이 반복되면 재시도 상한 뒤 truncated 로 종료된다` | PASS(호출 2회 — mock server 는 지연 응답 중에도 재시도 요청을 받을 수 있는 스레드풀 executor 가 필요하다, 「알려진 함정」) |
| totalCount 없음+같은 페이지 반복→truncated | `totalCount 없음 + 같은 페이지 반복이면 유한 페이지에서 truncated 로 끝난다` | PASS(2회 호출, pagesFetched=2 — 반복 페이지도 호출은 나갔으므로 센다, L-8) |
| 미지 resultCode | `미지 resultCode 는 Unclassified 로 비재시도 실패한다` | PASS(1회, truncationCause=Unclassified) |
| 부재 resultCode | `resultCode 자체가 부재하면 Unclassified 로 비재시도 실패한다` | PASS(truncationCause=Unclassified) |
| 미지 raw 키(COL-07 unknownFields) | `계약에 없는 raw 키는 항목을 살리되 unknownFields 로 계수한다 COL-07` | PASS |
| `03` NoData | `resultCode 03 은 실패가 아니라 데이터 없음이다` | PASS(sourceTotal=0, truncated=false) |
| quota `22` | `resultCode 22 는 quota 초과로 재시도 대상이다` | PASS(재시도 뒤 성공, quotaExceeded=1) |
| 등록되지 않은 키 `30` | `resultCode 30 은 등록되지 않은 서비스 키 — 비재시도` | PASS(truncationCause=NotRetryable) |

## verifier finding 재현 test — scope ⑦ 목록 밖(추가 test, 대응표는 아래 「finding 대응」)

r1: `H-2 — 빈 문자열·공백 공고번호도 COL-01 탈락에 걸린다` · `H-3 — 다섯 truncation 사유가
회계에서 바이트 동일하지 않고 서로 구별된다` · `M-1 — JSON boolean 은 Y N 으로 바뀌지
않고 원문 토큰 텍스트 그대로 옮겨진다` · `M-2 — canonical 공고번호가 같으면 원문 표기가
달라도 duplicate 로 계수된다` · `M-5 — cursor 토큰이 숫자가 아니거나 0 이하면 조용히
page 1 로 접지 않고 명시 실패를 낸다` · `L-5 — 항목 안 중복 JSON 키는 마지막 값이
승리한다` · `M-4 — JSON 중첩 깊이가 정책 상한을 넘으면 StructureFailure 로 접히고 예외가
안 샌다`(7건). r2: `N-4 — 재시도로 뚫릴 수 있는 사유(반복 페이지)는 next 를 낸다` +
기존 미지/부재 resultCode·resultCode 30 test 에 `next shouldBe null` 단언 추가 ·
`N-5 — cursor 토큰의 선행 0·부호 기호도 조용히 수용하지 않고 명시 실패를 낸다`(2건 신규
+ 기존 2건 확장). 전건 PASS. `KonepsOpenApiNoticeSourceTest` 총 19 test + `ServiceKeyTest`
3(D-3B-5) + `KonepsAdapterDependencyTest` 1(S-3b) = **23 test**.

## 위협 모델 대응표 — scope.md 「방어한다」

| 방어 항목 | 메커니즘 | 증거 |
| --- | --- | --- |
| (a) 무한 pagination | `maxPages` + 직전 한 페이지와의 내용 해시 비교(연속 반복만 직접 감지 — 비연속 순환은 `maxPages` 백스톱이 닫는다, 아래 「알려진 제한」) | 「totalCount 없음+반복」 test |
| (b) 429 폭주 | `RateLimiter`(자체 quota 보호)+`Retry`(bounded, backoff 시도별 목록), `quotaExceeded`/`backoffSkipped` 계수(H-3) | 429/22 test, `Throttled` 코드 경로(직접 test 없음, 「알려진 제한」) |
| (c) parse 실패의 조용한 0/None | 3B 는 숫자·일시를 파싱하지 않는다(⑥) — 실패할 파싱 자체가 없다. envelope 구조 실패는 `StructureFailure`(페이지 종료, `truncated`, `truncationCause`로 구별) | M-4(깊이 상한), envelope test |
| (d) 미지 resultCode 의 성공 취급 | `classify`가 `00`/`03`/등재 코드 외 전부 `Unclassified`(비재시도) | 미지·부재 resultCode 2 test |
| (e) 서비스 키 노출 | `ServiceKey.toString()` 가림, `urlEncoded` 만 요청 URI 생성에 쓰인다 | `ServiceKeyTest` + secret 스캔(commands.md) |
| (f) 재시도 이중화 | 어댑터 안에 `Retry` 인스턴스 하나(생성자에서 1회 구성) — 스케줄러 재실행 배선은 M4 리뷰 항목(3B 는 만들지 않는다) | 코드 열람(`KonepsOpenApiNoticeSource` 생성자) |

## 우회 후보 대응표 — scope.md 「우회 후보(≥5)」

| # | 우회 | 막는 장치 |
| --- | --- | --- |
| 1 | 429 를 catch 해 빈 배치로 성공 위장 | `isRetryableStep`이 HTTP 429/`resultCode` RETRYABLE·QUOTA_EXCEEDED 를 재시도로만 두고, 소진 시 `Failed(TruncationCause.QuotaExhausted,…)`→`markTruncated` — 회계에 `quotaExceeded` 로 실린다(H-3) |
| 2 | `totalCount` 무시, 고정 페이지 수 | 반복 페이지 백스톱 test 가 `truncated=true`·`truncationCause=RepeatedPage` 를 직접 단언 |
| 3 | parse 실패를 `0` 으로 | 3B 에 숫자 파싱 코드가 없다(`mapRawItem`이 `toRawValue`로 원문만 옮김) — 정적 근거: `KonepsRawItemMapper.kt` 에 `toInt`/`toBigDecimal`/`toDouble` 호출 0건 |
| 4 | client+Resilience4j 이중 재시도 | `sendKonepsRequest` 자체는 재시도하지 않는다(단발 호출) — 재시도는 `fetchPageResilient` 한 곳에서만 감싼다 |
| 5 | 서비스 키를 URL 로그에 | `ServiceKey`는 `urlEncoded`(get-only)로만 원문을 노출하고 `toString()`은 가린다 — 로깅 코드 자체가 없다(3B 는 로그를 남기지 않는다) |
| 6 | 골든을 test 리소스에 복사 | `fixtures/input/koneps/**` 가 없어 참조할 golden 자체가 없다 — self-authored 데이터임을 KDoc·이 문서에 명시, 바이트 교집합 0 실측(commands.md) |

## 범위 분할(D-3B-6) — 이번 묶음이 하지 않는 것

scope.md 「이 slice 가 하는 일」 표의 ⑧행(표적조회·서브콜)은 **착수 시 계약 정정 ③** 이
범위 밖으로 이미 옮겼다 — `OpeningResultSourcePort`·`DocumentSourcePort`·license-limit
서브콜·`inqryDiv=2` 표적조회는 이 커밋 묶음에 없다. `NoticeSourcePort`(`inqryDiv=1`, 날짜
조회)만 구현한다. **L-7(verifier r1)** — 설계 노트(`01_design-review.md` ⑧행)는 「표적조회는
공고 축이므로 포함」이라 적고 정정 ③ 은 `inqryDiv=2` 를 직접 언급하지 않아, 두 문서 문면이
어긋난 채 남아 있다. 이 문서(구현 레인 소유)가 아니라 설계 노트(세션 모델 소유) 쪽의 정정이
필요해 이 slice 가 고치지 않는다 — 오케스트레이터에 등재만 한다.

## verifier r2 산출물 finding 대응 — 한 줄씩

- **N-1** 차수 탈락과 공고번호 탈락이 같은 사유로 계수됨 — 3A 후속(위 「알려진 제한」).
- **N-2** `InputError` 가 서버측 입력오류와 무효 cursor 두 원인을 접음 — 3A 후속(위).
- **N-3** quota 두 표면(HTTP 429·`resultCode 22`)이 구별 안 됨 — 3A 후속(위).
- **N-4** 비재시도 사유(`NotRetryable`·`Unclassified`·`InputError`)에도 `next` 가 붙던 것을
  고쳤다 — `KonepsPageWalkAccumulator.nextCursor()`가 `isResumable(cause)` 로 재시도 가능
  축(백스톱·전송·quota·rate limiter 자체 거부·구조 실패)만 cursor 를 낸다. `StructureFailure`
  는 재개 가능 축에 넣었다(서버가 그 순간 보낸 응답이 무너졌다는 관측이지 입력·구성이
  틀렸다는 판정이 아니라서다) — 판단이 갈린 지점 3 의 연장.
- **N-5** cursor 토큰이 `"007"`·`"+4"` 처럼 선행 0·부호 기호를 관대하게 수용하던 것을
  `[1-9][0-9]*` 정규식으로 좁혔다 — 순수 양의 정수(부호·선행 0 없음)만 통과한다.

## 판단이 갈린 지점 — 계약이 명시하지 않아 이 레인이 정한 것

1. **`fetchNotices` 한 호출 = 조회일 하나의 전체 page-walk.** port 서명(`cursor: PageCursor?`)
   만 보면 「페이지 하나=호출 하나」로도 읽을 수 있으나, scope ⑦의 「totalCount 없음+같은
   페이지 반복 → truncated」 시나리오가 **단일 어댑터 호출**을 겨눈 mock server test 로
   서술돼 있어 내부 다중 페이지 순회로 해석했다. `cursor`는 재개 시작 페이지로만 쓴다.
   verifier r1 이 동의했다(§3 (1)) — 다만 그 해석은 `next` 가 의미 있어야 한다는 의무를
 지는데 초판은 항상 `null` 이었다 → **M-3 로 닫았다**(truncated 시 재개 cursor를 낸다).
2. **dedup 식별자 — verifier r1 M-2 로 절반 정정.** 초판은 원문 텍스트 짝이었다(`NoticeRound
   .of`가 제로패딩 3자리를 강제해 예외 위험이 있다는 이유). 재검증(P6)이 그 근거가
   **차수 축에만** 서고 공고번호 축(`NoticeNumber.of`는 blank 외에 안 던진다)에는 서지
   않음을 실측했다 — H-2 로 blank 를 먼저 걸러 공고번호 축을 canonical 로 좁혔다. 차수
   축은 형식이 맞을 때만 canonical, 아니면 원문 폴백(예외 위험은 남겨 두되 흐르지 않게).
3. **envelope 구조 실패(JSON 파싱 실패·`response`/`body`/`items` 형태 위반)는 페이지 단위
   종료(`StructureFailure`→`truncated`, `truncationCause=StructureFailure`)로 처리했다** —
   설계 검토 KDoc 이 언급한 `ParseFailure(kind=Envelope)`는 3A `ParseFailureKind`
   (NUMERIC/DATE_TIME/IDENTIFIER)에 그 축이 없어 항목 단위 사유로 표현할 수 없었다. 그
   결과 회계에 흔적이 안 남는다는 verifier r1 지적(§3 (3))은 **H-3 의 `truncationCause`
   로 닫혔다** — 구조 실패는 이제 다른 네 사유와 바이트로 구별된다.
4. **quota/429 회계 — verifier r1 H-3 로 해소.** 초판은 `CollectionAccounting.truncated`
   하나로만 표현했다(3A 타입에 `quotaExceeded`/`backoffSkipped` 필드가 없었다). 운영자
   결정 2026-09-07 로 그 두 필드가 3A 좁은 확장으로 추가돼(procurement in_scope 예외)
   scope ② 문면을 그대로 충족한다 — 더 이상 「대체 표현」이 아니다.
5. **JSON 파서를 새로 짰다**(외부 좌표 미도입) — envelope 형태가 좁고, `architecture-policy`
   의 `group.forbidden`(jackson·kotlinx-serialization·gson·org.json)이 domain 층 금지
   결의를 이 adapters 패키지도 자체 적용했다(CLAUDE.md 재사용 우선 방침의 "무거운 도구는
   측정된 필요 없이 도입 안 함" 축). `resilience4j-kotlin`은 카탈로그에만 등재하고
   실제로는 끌어오지 않았다(Java API 로 충분). 파서에 중첩 깊이 상한을 추가했다(M-4).
6. **`gradle/libs.versions.toml` 을 편집했다** — scope.md in_scope 목록에 이 파일이
   명시되지 않았으나 `adapters/build.gradle.kts` 가 요구하는 카탈로그 좌표
   (`resilience4j-ratelimiter`·`resilience4j-kotlin`)의 기계적 선행 조건이라 함께
   편집했다. 세션 모델이 착수 뒤 in_scope 에 추가해 정정됐다(verifier r1 §3 (5), 이의 없음).
7. **업종별(물품/용역/공사/외자) KONEPS 오퍼레이션 선택은 미정이다** — `baseUri` 생성자
   인자로 열어 두고 이 slice 는 어느 것이 옳은지 검증하지 않는다(실제 호출이 범위
   밖이라 검증 수단이 없다).

## 알려진 제한

- **골든 fixture 부재 — 문면을 좁힌다(verifier r1 L-3).** `fixtures/input/koneps-collection-
  *.json` 27개는 **실재한다**(3A 가 만든 curator 승인 corpus). 없는 것은 (a) 실제 KONEPS
  wire envelope 형태(`response.header.resultCode`/`body.items` 를 가진 JSON — 27개 중
  `resultCode` 를 담은 것은 013·027 둘뿐이고 둘 다 문서 발췌 형태다) (b) `fixtures/input/
  koneps/**` 라는 하위 디렉터리(D-3B-1 (a) 가 정한 정본 자리). 시나리오 test 의 envelope
  JSON 은 curator 승인 case 의 바이트 복제가 아니라 이 레인이 authoritative
  필드명(`policy-values.md` §1.1·§1.3·§1.6)으로 직접 지었다 — curator 가 실제 wire-format
  골든을 확보하면 `bidvector.fixtures.koneps` 시스템 프로퍼티 배선(2A `bidvector
  .contracts.testdata` 관례)을 추가해 이 test 들을 그쪽으로 옮기는 후속 작업이 필요하다
  (이번 slice 는 그 배선 자체를 넣지 않았다 — 가리킬 디렉터리가 없는 채로 `inputs.dir`를
  걸면 오해를 부른다고 판단).
- **rate limiter 자체 거부(`Throttled`) 경로에 전용 test 가 없다** — scope ⑦ 목록에
  없고, 결정론적으로 유발하려면 동시 호출이 필요해 단위 test 로 만들면 flaky 해진다.
  코드 경로는 있다(`fetchPageResilient`의 `catch (RequestNotPermitted)` →
  `TruncationCause.SelfThrottled`).
- **반복 페이지 감지는 직전 한 페이지만 본다(verifier r1 L-6).** `lastPageSignature` 는
  가장 최근 페이지 하나와만 비교한다 — 교대 패턴(A,B,A,B…)처럼 비연속 반복은 이 감지기
  단독으로 못 잡는다(R2 probe 실측). `maxPages` 백스톱과 걷기 전체 dedup 이 함께 닫는다
  (교대 페이지가 40장 와도 `maxPages` 에서 종료하고 중복 항목은 `duplicate` 로 계수된다).
- **실제 KONEPS 호출 정확성은 검증하지 않는다**(out_of_scope) — 오퍼레이션 경로·쿼리
  파라미터 이름(`inqryBgnDt`/`inqryEndDt` 등)이 실물과 다를 위험은 실제 네트워크 호출
  승인 뒤에만 닫힌다.
- **표적조회·`OpeningResultSourcePort`·license-limit 서브콜 없음**(D-3B-6, 범위 분할 ③).
- **L-7 미해결** — 위 「범위 분할」 절 참고. 설계 노트 문면 정정은 세션 모델 소관.
- **N-1(verifier r2) 차수(`bidNtceOrd`) 탈락도 `CollectionMissingNoticeNumber` 로 계수된다
  — 3A 후속.** `bidNtceOrd` 가 부재·빈 문자열·공백이어도 사유가 공고번호 축과 같은 코드다.
  3A `CollectionDropReason`(`CollectionMissingNoticeNumber`·`CollectionUnknownField`·
  `CollectionContractViolation`·`CollectionParseFailure`) 에 차수 축을 가리키는 어휘가
  없다 — 이번 라운드는 procurement 추가 편집이 금지돼(팀리드 지시) 새 sealed 변형을
  만들 수 없다. 후속 slice 가 procurement 를 다시 열 때 `CollectionMissingNoticeRound`
  같은 변형을 추가하면 닫힌다.
- **N-2(verifier r2) `TruncationCause.InputError` 가 두 원인을 접는다 — 3A 후속.** 서버가
  낸 입력 오류(`resultCode` 06/07/08/10/11)와 어댑터가 스스로 거부한 무효 cursor(M-5)가
  같은 사유값이다. `Accounting.kt` KDoc 의 「3B(어댑터)만 이 값을 만든다」선언과는
  어긋나지 않으나(둘 다 3B 가 만든다), 회계만 보는 소비자는 「질의가 틀렸다」와 「재개
  토큰이 틀렸다」를 못 가른다. `TruncationCause` 에 축 하나를 더 여는 것은 procurement
  편집이라 이번 라운드 밖이다.
- **N-3(verifier r2) quota 두 표면(HTTP 429·`resultCode 22`)이 `QuotaExhausted` 하나로
  접힌다 — 3A 후속.** D-3B-7(「두 표면을 다 센다」)의 의도한 귀결이라 설계와는 일치하고
  `quotaExceeded` 총량은 정확하지만, 표면 구별이 필요해지면 `TruncationCause` 의 두 번째
  확장 결정이 필요하다(이번 라운드는 procurement 편집 금지).

## 병렬 레인 경계 확인

`git status --porcelain` 최종 상태 — `adapters/src/{main,test}/kotlin/bidvector/adapters/
koneps/**`·`adapters/build.gradle.kts`·`gradle/libs.versions.toml`·`config/quality/
gate-tests.properties`·`procurement/src/main/.../Accounting.kt`·`procurement/src/test/
.../AccountingTest.kt`(H-3 좁은 확장, in_scope)·`reports/evidence/m3/3b/**` 만 변경.
`fixtures/**`·`docs/**`·`build-logic/**`·다른 procurement 파일·다른 세션의 untracked
파일 없음(commands.md clean-tree 절 참고).

## 스테이징 규율

전 커밋이 `git add <in_scope 경로>` 개별 인자(`-A`·`-a`·`.` 미사용) 뒤 `git commit`(같은
메시지 안에서 add+commit 분리 없음 — parallel-lane 오염 회피). 3A `Accounting.kt`·
`AccountingTest.kt` 편집은 H-3 운영자 결정으로 in_scope 예외가 열린 뒤에만, 별도 커밋으로.
