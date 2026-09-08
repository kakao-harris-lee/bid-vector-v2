# Slice 계약 — M3 / 3F · 개찰완료 축(추첨번호·개찰 1위) — **착수 2026-09-09(운영자 승인)**

> **지위**: M3 의 **마지막 잔여**. 3B-2 가 **D-3B2-9 (a)** 로 밖에 둔 개찰완료 오퍼레이션
> (`getOpengResultListInfoOpengCompt`)을 연다. 3E 가 자식 표(복수예비가격 15행)를 만들었으므로 이제
> **추첨번호와 투찰 축**을 더하면 `COL-03` 문면(「복수예비가격 15개와 **추첨번호**를 확보한다」)이 닫힌다.
> 세션 모델 단독 작성.
> **왜 지금인가**: legacy 의 실현 사정률은 **추첨된 예비가 평균 ÷ 기초금액**이고 추첨번호가 15행의
> **1-기반 인덱스**다(`distribution_extraction`, 읽기 전용 조사). 3E 가 15행에 순번을 기본키로 준 덕에
> 인덱스가 가리킬 대상이 생겼다 — **두 축이 같이 있어야 사정률 분포 입력이 성립한다**(M5 소비).
> **실측으로 근거가 섰다**: 운영자 승인 아래 읽기 전용 호출로 이 오퍼레이션이 `bidNtceNo` 단건으로
> 동작하고 응답 항목 20개(`opengRank`·`prcbdrBizno`·`prcbdrNm`·`prcbdrCeoNm`·`bidprcAmt`·`bidprcrt`·
> `drwtNo1`·`drwtNo2`·`bidprcDt`·평가점수 넷 등)를 준다는 것을 확인했다(`policy-values.md` §1.9.7).
> **먼저 읽어야 하는 것**: `_workspace/m3-3f/01_design-review.md`(Phase 2.5 — **투찰 행의 정체성이
> masking 과 정면으로 부딪친다**. 이 slice 의 중심이다) · `policy-values.md` §1.9.4·§1.9.7·§6b(P-10) ·
> `reports/evidence/m3/3e/{scope,checklist}.md` · `capability-map.md` COL-02·03·SET-01·02.

```yaml
milestone: m3
slice: 3f-opening-complete-axis
base_sha: 69e2afe   # 착수 2026-09-09 = 3E 종결·push 커밋. 착수 시 40자로 재고정
head_sha: 리뷰 시점의 HEAD
in_scope:
  - adapters/src/main/kotlin/bidvector/adapters/koneps/**, adapters/src/test/kotlin/bidvector/adapters/koneps/**   # 개찰완료 오퍼레이션 구현 + 치환 확장 + mock server 시나리오. 3B·3B-2 기존 test 는 편집 없이 초록
  - procurement/src/main/kotlin/bidvector/procurement/{Ports.kt,NoticeFacts.kt,DetailFetch.kt,OpeningResultRepository.kt}, procurement/src/test/kotlin/bidvector/procurement/**   # **추가만** — port 메서드 하나(D-3F-1) · 투찰 행 fact(D-3F-4) · 조회 가치 술어 재사용 판단(D-3F-2). 그 밖의 procurement 편집 금지
  - adapters/src/main/resources/db/migration/V5__opening_complete_axis.sql   # 신규 마이그레이션 **하나** — **부모 컬럼만**(D-3F-3 해소로 투찰자별 자식 표를 만들지 않는다). V1~V4 를 고치지 않는다(**`V4` 는 3E 종결과 함께 push 됐다 — 되쓸 수 없다**)
  - adapters/src/main/kotlin/bidvector/adapters/persistence/**, adapters/src/test/kotlin/bidvector/adapters/persistence/**   # repository + Testcontainers 통합 test
  - adapters/src/test/kotlin/bidvector/adapters/persistence/{CleanMigrationTest.kt,CleanMigrationCheckTest.kt,CleanMigrationTriggerTest.kt}   # **스키마 스냅샷 래칫 — 3E 예외의 선례 적용(D-3F-6)**. 기대값에 **신규 항목을 더하는 편집만**. 완화·삭제 금지, 검증 레인 표적 재검증
  - config/quality/gate-tests.properties
  - milestone-3.md, reports/evidence/m3/3f/**, docs/discovery/capability-map.md   # 문서 레인 전용
out_of_scope:
  - 실현 사정률 계산·추첨 집계·예정가격 역산                    # M5(ML)·DEC 축 소유. 3F 는 **수집·보존만** 한다
  - 실제 KONEPS 운영 수집 배선·스케줄·쿼터 예산                 # M4 4B·OPS-08
  - `RowDiscriminator` 프로덕션 배선                            # M4 4B(3E 알려진 제한)
  - 개찰 금액 권위 가드 개방                                    # `OPEN-3E-OPENING-AMOUNT-AUTHORITY-GUARD`, 3D 설계 변경
  - `policy-values.md`·`fixtures/**`                             # curator 레인 소유(§1.9.4 를 읽기만)
  - 유찰·재입찰 오퍼레이션(`…Failing`·`…Rebid`)·검색군(`…PPSSrch`)
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1
  - "./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'"                                 # S-2 — 신규 시나리오 + 3B·3B-2 무편집 초록
  - "./gradlew :adapters:test --tests 'bidvector.adapters.persistence.*'"                            # S-3 — Testcontainers(Docker 필요, 부재는 붉게)
  - "./gradlew :procurement:test"                                                                     # S-4 — 3A corpus 27/27 불변
  - "./gradlew :adapters:moduleDependencyGate"                                                        # S-5
  - "./gradlew qualityBaseline"                                                                        # S-6
rollback: |
    **정본은 `reports/evidence/m3/3f/rollback.md`.** `evidence-pack` 2026-09-08 규격 — 목록을
    `git diff --name-status <base>..HEAD` 로 기계 산출 · 임시 clone 실행 · **되돌린 트리 compile exit 0 과
    test 초록** · 파일이 늘면 절차 재실행. `V5` 삭제는 적용 이력 있는 DB 에서 되돌림이 아니다(개발 DB
    재생성 절차 병기). 커밋은 `git commit -m … -- <경로들>`.
```

작성: 2026-09-09, 세션 모델 단독. 근거: `milestone-3.md` 3E 종결 문단의 「M3 잔여는 개찰완료 축 하나」 ·
`policy-values.md` §1.9.4·§1.9.7 · `capability-map.md` COL-03 acceptance · 3E `opening_reserve_price` 스키마.

---

## 하네스 레인 변경 · 문서 레인 변경 (상시 절)

`git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 와 `… -- docs/discovery milestone-3.md` 를 리뷰
요청 시점마다 돌려 등재한다. 착수 시점 **없음**.

---

## 이 slice 가 하는 일

| # | 일 | 근거 |
| --- | --- | --- |
| ① | **개찰완료 오퍼레이션 구현** — `getOpengResultListInfoOpengCompt` 를 `bidNtceNo` **필수 단건**으로 호출(`inqryDiv` 가 없는 오퍼레이션군 — §1.9.2). 옵션 `bidNtceOrd`·`bidClsfcNo`·`rbidNo` 는 정책 표가 정한다 | §1.9.1·§1.9.2 · 실측 §1.9.7 |
| ② | **투찰자별 행은 `raw_observation` 까지, canonical 은 셋만**(D-3F-3 해소) — 부모에 ① **개찰 1위 축**(순위 1 행) ② **관측된 추첨번호 집합** ③ 개찰결과구분명. **1위를 특정할 수 없으면**(순위 1 부재·중복) 그 축을 **비우고 명시적 회계** — 투찰금액으로 순위를 재계산하지 않는다(동값이 실재한다) | §1.9.4 · 실측 §1.9.7 · 설계 검토 (2) |
| ③ | **P-10 (a) 를 이 축으로 확장** — `prcbdrBizno`(투찰업체사업자등록번호, **필수**)·`prcbdrCeoNm`(대표자명)은 **어댑터 경계에서 치환**하고 **상호(`prcbdrNm`)만 보존**한다. 3B-2 가 낙찰자 축에 한 것과 **같은 규칙**이며 새 정책이 아니다 | `policy-values.md` §6b P-10 (a) |
| ④ | **추첨번호는 15행의 1-기반 인덱스로 보존** — 값을 그대로 싣고 **범위 검사만** 한다(1..총예가건수). 범위 밖·부재는 조용한 `0`/`null` 이 아니라 명시적 결과로 회계 | legacy `distribution_extraction` 조사 · 3E 자식 표의 순번이 그 인덱스의 대상 |
| ⑤ | **조회 가치 술어** — 공고당 1콜이라 쿼터 축이다. 술어를 거치지 않은 호출이 컴파일되지 않게 한다(3A ⑪·D-3B2-5 와 같은 형태). 재사용/신설은 D-3F-2 | COL-03 「호출 예산은 OPS-08, 도메인 판단만 여기」 |
| ⑥ | **저장** — `V5` 는 **부모 컬럼만** 더한다(자식 표 없음). **3E 의 규율을 그대로 잇는다**: 사라진 행을 지우지 않고 관측 시각으로 구분 · provenance 를 지어내지 않음 · 사업자등록번호·대표자명 **컬럼 없음** | 3E ⑤⑥·D-3E-3 (a) · verifier r1 H-1·H-2 |
| ⑦ | **mock server 시나리오** — 정상 다수 행 · 단일 낙찰자 · **협상 계약(투찰금액·투찰율 없음)** · 추첨번호 부재 · 순위 동값 · 치환 세 변형 · `bidNtceNo` 누락 → `08` 비재시도 | 3B-2 ⑦ 관례 · §1.7.5 의 세 갈래 |

**만들지 않는 것**: 실현 사정률·추첨 집계·예정가격 역산(M5·DEC) · 유찰·재입찰·검색군 오퍼레이션 ·
사업자등록번호·대표자명 슬롯 · 운영 배선·스케줄 · 권위 가드 개방 · `V1~V4` 수정.

---

## 운영자 결정 필요 — 착수 전

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-3F-1** ✅ (a) 승인 2026-09-09 | **port 자리** | (a) **`OpeningResultSourcePort` 에 메서드 하나 추가**(3A 좁은 확장, 추가만) (b) 새 port (c) `fetchOpeningResults` 재사용 | **(a)** — 같은 개찰 축이고 `fetchReservePrices` 와 성질이 같다(공고당 1콜·증거 값 요구) | 승인 |
| **D-3F-2** ✅ (a) 승인 2026-09-09 | **조회 가치 술어** | (a) **`DetailFetchDecision` 재사용** (b) 신설 | **(a)** — 「이미 보유 · 개찰 후 age-gate · recheck-gate」 셋이 이 축에도 같은 뜻이다. `Fetch` 가 어느 축의 증거인지 구별이 필요해지면 그때 가른다 | 승인 |
| **D-3F-3** ✅ **해소 2026-09-09**(운영자 도메인 결정) | **투찰 행의 정체성** — P-10 (a) 가 자연 키(`prcbdrBizno`)를 치환해 없앤다 | (a) 부모+순위 (b) 부모+상호 (c) 부모+순위+상호 (d) 승격 거절 | **물음 자체가 사라졌다.** 운영자 결정: 실현 사정률·예정가격 재현에 **「누가 어느 번호를 골랐는가」는 필요 없고 관측된 추첨번호 집합으로 충분하다.** 따라서 **투찰자별 canonical 표를 만들지 않는다** — 정체성이 필요 없고, masking 이 지우기로 한 상호를 기본키로 굳히지도 않는다. 실측이 (a)·(c)를 이미 죽였다(`opengRank` 전 행 채워지고 유일한 건 **4/15**, 결측·중복 흔함) | 해소 |
| **D-3F-4** ✅ (a) 승인 2026-09-09 | **fact 깊이** | (a) **부모에 싣는다** (b) 별도 fact | **(a)** — D-3F-3 해소로 자식 목록이 없어졌다. 부모가 갖는 것은 ① **개찰 1위 축**(순위 1 행의 상호·투찰금액·투찰율·평가점수) ② **관측된 추첨번호 집합** ③ 개찰결과구분명 | 승인 |
| **D-3F-5** ✅ (a) 승인 2026-09-09 | **평가점수 넷** | (a) **수집·보존** (b) 이번 slice 밖 | **(a)** — 다만 D-3F-3 해소의 귀결로 **투찰자별 평가점수는 `raw_observation` 감사 기록까지**이고 canonical 슬롯은 **1위 행 것만**이다. 해석·판정은 하지 않는다(DEC 축) | 승인 |
| **D-3F-6** ✅ (a) 승인 2026-09-09 | **스키마 스냅샷 래칫 예외** | (a) **3E 와 같은 조건으로 3F 에 적용** (b) 매 slice 개별 (c) 상시 규격 | **(a)** — 기대값에 **신규 항목을 더하는 편집만**, 완화·기존 항목 삭제 금지, 검증 레인 표적 재검증. 장기 구조 개선은 `OPEN-3E-SCHEMA-SNAPSHOT-MAINTENANCE` | 승인 |

**D-3F-3 해소가 이 slice 를 작게 만든다** — 자식 표·정체성·상호 키 위험이 한꺼번에 사라지고 남는 것은
부모 컬럼 하나 묶음과 어댑터다. **되돌릴 수 있다**: 투찰자별 원문이 `raw_observation` 에 온전히 남으므로,
나중에 귀속이 필요해지면 그 감사 기록이 소급 승격의 재료가 된다(3E D-3E-1b 와 같은 성질).

---

## 위협 모델 — 3F 고유 경계

**방어한다**: (a) 투찰업체 사업자등록번호·대표자명이 도메인·저장·원문·로그에 남는 것(③ — **컬럼이 없으면 채울 수 없다**) (b) 술어를 거치지 않은 공고당 1콜(⑤, 컴파일 시점) (c) 추첨번호를 `0`·`null` 로 접는 것(④) (d) 범위 밖 추첨번호를 조용히 통과시키는 것 (e) 사라진 투찰 행을 삭제하는 것(⑥, 3E D-3E-3 (a)) (f) provenance 를 읽기 경로에서 지어내는 것(3E H-1 의 재발) (g) 협상 계약의 **부재 필드**(투찰금액·투찰율)를 0 으로 채우는 것.
**방어하지 않는다**: 실현 사정률·추첨 집계의 옳음(M5) · 순위 동값의 도메인 의미(관측 필요) · 운영 쿼터 예산(OPS-08) · 개찰 금액 권위 가드(`OPEN-3E-OPENING-AMOUNT-AUTHORITY-GUARD`) · 정책·스키마 자신을 고치는 저자(게이트는 **호출부의 실수**를 막는다).

**우회 후보(≥7)**: (1) 치환 없이 원문 item 을 mapper 에 넘김 → 3B-2 의 masked 통로 타입 재사용, 컴파일 차단 (2) `sourceText` 에 원문을 담아 bizno 가 남음 → 걸러진 객체의 `render()` 만 통로, 문자열 검색 test (3) 추첨번호 부재를 `0` 으로 → 「모름」 금지, 타입 + test (4) 범위 밖 인덱스를 그대로 저장 → 범위 검사 + 명시적 회계 (5) 협상 계약의 부재 금액을 0 으로 → 부재를 부재로 나르는 타입 (6) 술어 없이 상세 호출 → 증거 값 `internal` 생성자 (7) 사업자등록번호 컬럼을 「나중에」 만들어 둠 → 정보 스키마 질의로 컬럼 부재 단언 (8) 사라진 투찰 행 삭제 → 3E 와 같은 test.

---

## OPEN — 수령·신설

| OPEN | 3F 처리 |
| --- | --- |
| `COL-03` 추첨번호 문면 | **이 slice 가 닫는 후보** — 3E 의 15행 + 3F 의 추첨번호가 함께 서면 `COL-03` acceptance 가 완성된다 |
| `OPEN-3E-OPENING-AMOUNT-AUTHORITY-GUARD` | 이 slice 가 열지 않는다 — 투찰 금액도 같은 미결에 편입되는지 판단해 등재 |
| `OPEN-3E-SCHEMA-SNAPSHOT-MAINTENANCE` | D-3F-6 가 그 부채를 다시 만난다 — 이 slice 의 경험을 그 행에 보탠다 |
| ~~`OPEN-3F-BID-ENTRY-IDENTITY`~~ | **신설하지 않는다** — D-3F-3 이 해소돼 정체성이 필요 없어졌다 |
| 신설 후보 `OPEN-3F-OPENG-RANK-SEMANTICS` | 순위 동값·부재의 도메인 의미(적격심사 캐스케이드와의 관계) — 관측 필요 |
