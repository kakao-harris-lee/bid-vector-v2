# Slice 계약 — M3 / 3B · OpenAPI adapter — **초안, 구현 전**

> **지위**: M2 진행 중에 세션 모델이 쓴 **계약 초안**. 착수는 3A 승인 뒤, 그리고 **M2 2A 가 `adapters/build.gradle.kts`·`adapters/src/test`
> 편집을 끝낸 뒤**(경로 겹침 — 내용 의존은 없음). 그때 `base_sha` 재고정, `prep/m3-prep.md` D-M3-1·2·5 답 수령, 착수 문단.

```yaml
milestone: m3
slice: 3b-openapi-adapter
base_sha: 040ab9d   # 초안 시점 앵커 — **3A 승인·2A 머지 뒤 착수 시 재고정**
head_sha: 리뷰 시점의 HEAD
in_scope:
  - adapters/src/main/kotlin/bidvector/adapters/koneps/**   # port 구현: HTTP client·envelope 검증·pagination·parse·회계 산출·정책 소비
  - adapters/src/test/kotlin/bidvector/adapters/koneps/**   # contract mock server test·골든 응답 재생·429/timeout/partial/duplicate 시나리오
  - adapters/src/test/resources/koneps/**                    # 골든 응답(fixture 에서 복사가 아니라 참조 — `contracts/testdata` 와 같은 원칙: 정본은 fixtures)
  - adapters/build.gradle.kts                                # implementation(project(":procurement")) · resilience4j-kotlin · (D-M3-2 (a) 시) mockwebserver test 의존 — **2A 가 넣은 줄과 병합**
  - config/quality/gate-tests.properties                     # 조건부 — `gate.tests.adapters` 에 3B test 추가(2A·2D 가 만든 키에 병합)
  - milestone-3.md                                           # 「Slice 3B」 착수 문단
  - reports/evidence/m3/3b/**
out_of_scope:
  - procurement/**                                           # 3A 산출물 — 필요한 port·타입 부재 시 멈추고 보고
  - 실제 KONEPS 호출·서비스 키·운영 설정값                        # 사용자 승인 사항. test 는 mock server 만
  - DB write(3D)·LLM(3C)·스케줄·lease(OPS-01/02, M4)
  - 브라우저 크롤 어댑터(COL-09)·fallback mock(COL-10)
  - adapters 의 ML client·persistence·notification 하위 패키지   # M4 4D·3D·4E
  - M2 경로(`contracts/**`·`ml-contract/**`·`ml-engine/**`·`build-logic/**`)
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1
  - "./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'"                                 # S-2 — mock server 위 전 시나리오(네트워크 0 — 소켓은 loopback in-process)
  - "./gradlew :adapters:moduleDependencyGate"                                                        # S-3 — 도메인 방향(adapters → procurement 만)
  - "./gradlew qualityBaseline"                                                                        # S-4
rollback: |
    **정본은 `reports/evidence/m3/3b/rollback.md`**(착수 시). `adapters/.../koneps/**` 와 build 의존 줄을 걷으면 3A 상태. 2A 가 넣은 줄은 되돌리지 않는다.
```

작성: 2026-09-07, 세션 모델 단독. 근거: `milestone-3.md` 3B·완료 조건 · `capability-map.md` COL-01·03·04·06·07 · `ADR 0005` D-6·D-11 · `data-dictionary.md`
§5.3 · `OPEN-COL-01/02/05` · `data-extract.md` 「KONEPS 수집」 골든.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **port 구현** — 3A 의 `NoticeSourcePort`·`OpeningResultSourcePort` 를 `KonepsOpenApiAdapter` 가 구현. HTTP client 는 D-M3-1(JDK `HttpClient` + 코루틴), 각 호출에 deadline·취소 전파 | 3B 「비동기 HTTP client, timeout」 |
| ② | **quota·bounded retry/backoff·rate limit** — Resilience4j 데코레이터 **한 계층**(ADR 0005 D-11: 외부 호출의 재시도는 Resilience4j 한 곳, 스케줄러는 재실행 안 함). 값은 `adapters` 정책 데이터(ADR 0010 D-1 과 같은 배관). 429 는 재시도 대상이되 백오프 필수, quota 초과는 회계에 `backoffSkipped`/`quotaExceeded` 로 | 3B 「quota, bounded retry/backoff」 · COL-03 acceptance 「429 로 실패해도 나머지 수집은 계속되며 회계에 실린다」 · D-M3-5 |
| ③ | **envelope·resultCode 검증** — 응답 봉투의 `resultCode` 를 3A 정책 데이터(17 범주)로 분류, 미지 코드는 `Unclassified` 로 회계·실패(조용히 성공 아님). 성공 코드라도 `items` 부재·형태 불일치는 parse 실패 | 3B 「result code/envelope validation」 · `OPEN-COL-02` 확정 |
| ④ | **pagination·runaway 백스톱** — `totalCount` 부재 또는 같은 페이지 반복 시 **유한 페이지 안에 종료** + `truncated=true`. 종료 조건은 정책값(최대 페이지·동일 페이지 감지) | COL-06 acceptance 둘째 · 3B 「pagination」 |
| ⑤ | **partial response·duplicate item** — 페이지 안 중복은 `duplicate` 로 계수(항등식 유지), 부분 응답은 받은 만큼 정규화 + `truncated`/사유 | 3B 「partial response, duplicate item 처리」 |
| ⑥ | **parse 실패는 명시적 결과** — 숫자(콤마)/날짜(타임존 없음)/인코딩 실패는 예외가 아니라 `ParseFailure(kind, rawKey, raw)` 로 항목 drop + 회계. 원문 unit 기록(percent → fraction 은 필드 계약 `scale` 이 지시할 때만) | 3B 「parse failure 를 명시적 결과로 반환」 · §4.1 · R-RATE-01 |
| ⑦ | **contract mock server test** — 골든 응답(`data-extract.md` KONEPS fixture)을 in-process 서버가 서빙, 시나리오: 정상 4건 중 1건 공고번호 없음 → 3건 + `dropped=1`(COL-01), 429 연속 n회 뒤 성공(bounded retry), timeout, `totalCount` 없음 + 같은 페이지 반복, 미지 resultCode, 미지 raw 키(COL-07) | 3B 「contract mock server test」 · COL-01·06·07 acceptance |
| ⑧ | **자격 원문·예비가격 서브콜** — 표적조회(`inqryDiv=2`)+license-limit 서브콜은 **`bidNtceOrd` 필수**(누락 = resultCode 08), 업종제한 플래그 `N` 이면 서브콜 0회, 예비가격 이미 있으면 상세 호출 0회(호출 여부의 도메인 판단은 3A 의 「조회 가치」 술어, 3B 는 실행) | COL-03·COL-04 acceptance |

**만들지 않는 것**: 서비스 키 3-variant 재시도(D-M3-5) · 마감일시 `or` 폴백 · 4겹 게이트를 한 함수에(COL-03 legacy 형태) · 백오프 상태를 데이터 테이블 컬럼에 · 실제 호출.

---

## 운영자 결정 필요 — 착수 전(D-3B-1) · 계약 고정(D-3B-2~5)

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-3B-1** | **골든 응답의 정본 자리** — `fixtures/input/koneps/**`(curator 관리) vs `adapters/src/test/resources` | (a) **fixtures 가 정본, test 는 경로 참조**(1B-c 관례 — 복사본 금지) (b) test 리소스로 복사 | **(a)** — 두 자리에 같은 바이트가 있으면 갈린다 | 착수 전 |
| **D-3B-2** | 정책값(timeout·retry 상한·백오프·rate·최대 페이지)은 `adapters` 정책 데이터, 초기값 「보수적 + 관측 갱신」(D-M3-5) | — | 계약 고정 |
| **D-3B-3** | 재시도 계층은 **Resilience4j 하나** — 스케줄러(M4)는 3B 호출을 재실행하지 않는다(ADR 0005 D-11 표 「외부 호출」 행) | — | 계약 고정 |
| **D-3B-4** | mock server 는 loopback in-process — verifier 로컬 실측 가능, Codex 레인은 코드 slice 비대상. CI 러너에서 loopback 소켓 허용 여부는 착수 시 확인 | — | 계약 고정 |
| **D-3B-5** | 인증(서비스 키)은 설정에서 주입, 로그·evidence·회계에 **절대 노출 금지**(secret 스캔 대상) | — | 계약 고정 |

---

## 위협 모델 — 3B 고유 경계

**방어한다**: (a) 무한 pagination(④ 백스톱 test) (b) 429 폭주(② rate limiter + bounded retry test) (c) parse 실패의 조용한 0/None(⑥ 결과 타입) (d) 미지 resultCode 의 성공 취급(③) (e) 서비스 키 노출(D-3B-5 스캔) (f) 재시도 이중화(D-3B-3 — 스케줄러 배선은 M4 리뷰 항목).
**방어하지 않는다**: KONEPS 의 의미 변경(3A 계약 레지스트리가 관측) · 실제 rate limit 수치의 옳음(관측) · 스케줄·중복 실행 · 네트워크 장애의 트랜잭션 파급(3D 「KONEPS 장애가 transaction 을 반쯤 commit 하지 않음」은 3D 가 test).

**우회 후보(≥5)**: (1) 429 를 catch 해 빈 배치로 성공 → 회계 `quotaExceeded` 필수 + 완료 조건 test (2) `totalCount` 를 무시하고 고정 페이지 수 → 백스톱 test 가 `truncated` 를 요구 (3) parse 실패를 `0` 으로 → ⑥ 결과 타입(`Money` 는 `0` 을 값으로 받지만 provenance 가 `Undeclared` 라 권위 없음 — 3A ⑥) (4) 재시도를 client 와 Resilience4j 양쪽에 → 리뷰 항목 + fake 지연 test 의 호출 횟수 단언 (5) 서비스 키를 URL 로그에 → secret 스캔 (6) 골든을 test 리소스에 복사 → D-3B-1.

---

## 조사 결과 — 이 slice 에 영향을 주는 것

- 조사 (a): **legacy 에 재시도·backoff 없음**(401 키 variant 순회만, 429·5xx 는 예외) → ② 는 이식이 아니라 Resilience4j 신설 · **429 의 원인은 동시성, 회복 약 2분** → 정책 데이터의 1차 축은
  동시성 상한(rate limiter)·회복 대기, 총량 예산은 2차 · `resultCode` 부재 → `"00"` 잔존(R-COL-01) → ③ 부재 = `Unclassified` 실패 · 진행 보장 검사 없음(R-COL-04) → ④ · parse 실패 `None → 0.0` → ⑥ ·
  단위 크기 판별(`PERCENT_SCALE_THRESHOLD`) → ⑥ 은 계약 `scale` 만 · 골든 4건 중 3건이 「기초금액 키 없어 예산 키 폴백」 상태(COL-07 관찰) — 표본 편향 주의.
- 조사 (f): 재사용 — 식별자 canonicalization·`normalize_notice_number` 규칙은 3A 값 객체로(이식 가치), HTTP 라이브러리는 JDK 로 충분(D-M3-1). `OPEN-COL-01/05` 는 3B 를 막지 않음(조사 (h)).

---

## OPEN — 수령·신설

| OPEN | 3B 처리 |
| --- | --- |
| `OPEN-COL-01` | D-M3-5 (a) 닫는 후보 |
| `OPEN-COL-05` | 정책값 + 관측 갱신 — 값 확정은 운영 관측 뒤 |
| `OPEN-OPS-08`(호출 예산·throttle) | 3B 는 Resilience4j 배선까지, 예산 정책은 그 OPEN |
