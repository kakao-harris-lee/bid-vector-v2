# Slice 계약 — M3 / 3B · OpenAPI adapter — **종결 2026-09-07(공고 축) · 개찰 축은 3B-2**

> **지위**: M2 진행 중에 세션 모델이 쓴 **계약 초안**. 착수는 3A 승인 뒤, 그리고 **M2 2A 가 `adapters/build.gradle.kts`·`adapters/src/test`
> 편집을 끝낸 뒤**(경로 겹침 — 내용 의존은 없음). 그때 `base_sha` 재고정, `prep/m3-prep.md` D-M3-1·2·5 답 수령, 착수 문단.

```yaml
milestone: m3
slice: 3b-openapi-adapter
base_sha: c9d75632d3acafcaf41f0454e941dc49c62063ea   # 착수 2026-09-07 재고정 = 3A 최종 head(종결 승인 시점). 초안 시점은 040ab9d
head_sha: 리뷰 시점의 HEAD
in_scope:
  - adapters/src/main/kotlin/bidvector/adapters/koneps/**   # port 구현: HTTP client·envelope 검증·pagination·parse·회계 산출·정책 소비
  - adapters/src/test/kotlin/bidvector/adapters/koneps/**   # contract mock server test·골든 응답 재생·429/timeout/partial/duplicate 시나리오
  - adapters/build.gradle.kts                                # implementation(project(":procurement")) · resilience4j-kotlin · (D-M3-2 (a) 시) mockwebserver test 의존 · test 가 `fixtures/input/koneps/**` 를 시스템 프로퍼티(`bidvector.fixtures.koneps`)로 받는 배선(2A 의 `bidvector.contracts.testdata` 관례) — **2A 가 넣은 줄과 병합**. test 리소스 디렉터리에 골든 사본을 두지 않는다(D-3B-1 (a))
  - config/quality/gate-tests.properties                     # 조건부 — `gate.tests.adapters` 에 3B test 추가(2A·2D 가 만든 키에 병합)
  - gradle/libs.versions.toml                                # resilience4j 카탈로그 좌표(구현 레인 발견으로 착수 뒤 추가 — 세션 모델 정정, 3A F-15 와 같은 갈래)
  - procurement/src/main/kotlin/bidvector/procurement/Accounting.kt, procurement/src/test/kotlin/**   # **3A 좁은 확장(운영자 결정 2026-09-07, verifier r1 H-3)** — `CollectionAccounting` 에 truncation 사유(sealed)·`quotaExceeded`·`backoffSkipped` 를 **추가만**(기존 항등식·필드·사용처·corpus 27/27 불변). 다른 procurement 파일 편집 금지
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
  - "./gradlew :adapters:moduleDependencyGate"                                                        # S-3 — adapters 가 상위 층(app)을 참조하지 않음(정책은 아래 층 전부를 허용하고 외부 좌표는 domain 층에서만 검사 — 「procurement 외 domain 참조 없음」은 이 게이트가 재지 않는다)
  - "./gradlew :adapters:test --tests '*KonepsAdapterDependencyTest*'"                                # S-3b — 「koneps 패키지가 참조하는 domain 모듈은 procurement·shared-kernel 뿐」 단언(ArchUnit 또는 import 스캔 test)
  - "./gradlew qualityBaseline"                                                                        # S-4
rollback: |
    **정본은 `reports/evidence/m3/3b/rollback.md`**(착수 시). `adapters/.../koneps/**` 와 build 의존 줄을 걷으면 3A 상태. 2A 가 넣은 줄은 되돌리지 않는다.
```

작성: 2026-09-07, 세션 모델 단독. 근거: `milestone-3.md` 3B·완료 조건 · `capability-map.md` COL-01·03·04·06·07 · `ADR 0005` D-6·D-11 · `data-dictionary.md`
§5.3 · `OPEN-COL-01/02/05` · `data-extract.md` 「KONEPS 수집」 골든.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline c9d7563..HEAD -- CLAUDE.md .claude/` — 착수 시점(2026-09-07) **없음**.

**착수 2026-09-07 — 운영자 결정**: D-3B-1 (a) · D-M3-1 (a)·D-M3-2 (c)·D-M3-5 (a)(M3 착수 시) · D-3B-6(공고 축 먼저, 개찰·예비가격 축은 ScsbidInfoService
참고자료 확보 뒤 — 문서 레인 등재) · D-3B-7(resultCode `03` 세 번째 상태 · quota 신호 `22`·`30`). **착수 시 계약 정정(세션 모델)**: ① 3A 의 port 는 `suspend` 없는
동기 시그니처(`Continuation` 이 domain 허용 밖)이므로 ① 「각 호출에 deadline·취소 전파」는 어댑터 경계 안(JDK `HttpClient` 비동기 + timeout, 코루틴은
어댑터 내부)에서 하고 port 구현은 동기 facade — 3A `Ports.kt` KDoc 이 그렇게 위임한다. ② in_scope 의 `adapters/build.gradle.kts` 병합 주석은 M2 종결로
조건 해제. ③ **범위 분할**: 이 slice 는 `NoticeSourcePort` 구현 + 공통 기반(HTTP client·Resilience4j·envelope·pagination·parse 실패·회계·mock server)까지,
`OpeningResultSourcePort`(⑧ 의 예비가격·개찰) 는 D-3B-6 선행 조건 충족 뒤 **같은 slice 의 2차 커밋 묶음 또는 별도 slice 3B-2** — 착수 시 미정, 참고자료 도착
시 운영자 결정. 정책 값 초기값은 3A `policy-values.md` §3(resultCode 범주)·`adapters` 정책 데이터(timeout·retry·backoff·rate·최대 페이지 — 「보수적 +
관측 갱신」, 출처 조사 a-4·a-5, `legacy-behavior`/`observed` 층 표기).

**수정 라운드 1(2026-09-07) — 운영자 결정**: verifier r1 H-3(quota 회계가 3A 타입에 없음)은 **3A 좁은 확장을 3B scope 예외로**(위 in_scope 행). H-1 의 교훈: `config/quality/gate-tests.properties` 는 하네스 레인과 공유하는 파일이라 경로 한정 `git restore` 가 하네스 커밋(`7ee8fed`) 까지 되돌린다 — rollback 은 그 파일에 한해 **3B 가 넣은 줄만 제거**하는 절차로 기술한다(evidence-pack 경로 한정 규칙의 파일 공유 예외).

**종결 시점(2026-09-07, 최종 head `01ecbba`)** — 하네스 커밋 없음. range 의 `7ee8fed`(하네스 test-discovery-guard 등재)·세션 모델 정정 커밋은 slice 밖.
verifier r1 not-ready → r2 ready-for-review, 재작업 1/5. **범위 분할 확정(운영자 결정)**: 정정 ③ 의 「2차 커밋 묶음 또는 3B-2」 는 **3B-2 별도 slice** 로 —
선행 조건은 ScsbidInfoService 참고자료 확보(curator 필드 계약 추가 → 3B-2 계약). scope ⑧ 행 전체(표적조회 `inqryDiv=2`·license-limit 서브콜·예비가격)가 3B-2 로 간다.

**병렬 레인 경계(공유 working tree)**: 3B 구현 레인은 `adapters/src/{main,test}/kotlin/bidvector/adapters/koneps/**`·`adapters/build.gradle.kts`·
`config/quality/gate-tests.properties`·`reports/evidence/m3/3b/**` 만. `procurement/**`·`fixtures/**`·`docs/**`·`build-logic/**` 은 다른 레인·slice 소유.

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
| **D-3B-1** ✅ (a) 승인 2026-09-07 | **골든 응답의 정본 자리** — `fixtures/input/koneps/**`(curator 관리) vs `adapters/src/test/resources` | (a) **fixtures 가 정본, test 는 경로 참조**(1B-c 관례 — 복사본 금지) (b) test 리소스로 복사 | **(a)** — 두 자리에 같은 바이트가 있으면 갈린다 | 착수 전 |
| **D-3B-2** | 정책값(timeout·retry 상한·백오프·rate·최대 페이지)은 `adapters` 정책 데이터, 초기값 「보수적 + 관측 갱신」(D-M3-5) | — | 계약 고정 |
| **D-3B-3** | 재시도 계층은 **Resilience4j 하나** — 스케줄러(M4)는 3B 호출을 재실행하지 않는다(ADR 0005 D-11 표 「외부 호출」 행) | — | 계약 고정 |
| **D-3B-4** | mock server 는 loopback in-process — verifier 로컬 실측 가능, Codex 레인은 코드 slice 비대상. CI 러너에서 loopback 소켓 허용 여부는 착수 시 확인 | — | 계약 고정 |
| **D-3B-5** | 인증(서비스 키)은 설정에서 주입, 로그·evidence·회계에 **절대 노출 금지**(secret 스캔 대상) | — | 계약 고정 |
| **D-3B-6** ✅ 결정 2026-09-07 (curator 승인 요청 Q-3 P-6, 운영자 「curator 승인」) | **개찰·예비가격 축(`OpeningResultSourcePort` 구현)은 ScsbidInfoService 참고자료 확보를 선행 조건으로 둔다 — 공고 축(`NoticeSourcePort` 구현)은 진행한다.** 근거: legacy 소비 키 60 중 17 이 그 서비스 소관이고 항목크기·단위·과세를 문서로 모른다 — 없이 만들면 개찰 축이 legacy 형태 이식이 된다(`data-extract.md` §1 금지). 사업자 성명·등록번호가 그 축에 있어 masking 정책도 함께 필요. 정본 `reports/evidence/m3/3a/policy-values.md` §6 P-6·§1.7 | (a) 개찰 축만 선행 (b) 3B 전체 선행 (c) 선행 조건 없음 | **(a)** 채택 — 3B 를 외부 문서에 통째로 막지 않는다. 착수 시 ① 의 port 구현 범위를 「공고 축」으로 좁혀 in_scope 를 정정하고, 개찰 축은 문서 확보 뒤 같은 slice 의 후속 커밋 또는 3B-2 로 | 결정됨 |
| **D-3B-7** (착수 전 확인 — curator 발견 셋, `policy-values.md` §3) | ③ envelope 검증이 받는 정책 표가 바뀌었다 — `resultCode` 표 16 코드 + `00`(P-4 승인: 범주 5 · **`03`(No Data) 는 성공도 실패도 아닌 세 번째 상태**, legacy `OK_RESULT_CODES={"00","03"}` 불채택) · quota 가 HTTP 429 뿐 아니라 **`resultCode 22`** 로도 온다(② 의 회계 `quotaExceeded` 는 두 표면을 다 센다) · **`30`** 은 「서비스 키 URL 미인코딩」이라 D-M3-5 (a) 키 variant 불채택의 근거가 「문서 없음」에서 「원인이 인코딩이고 한 번 바르게 하면 사라진다」로 바뀐다(결정 불변) | — | 계약 고정(착수 시 ②③ 문면에 반영) |

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
