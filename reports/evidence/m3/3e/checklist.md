# M3/3E — checklist.md

## 「이 slice가 하는 일」①~⑥ 대응표

| # | 일 | 닫은 자리 |
| --- | --- | --- |
| ① | 저장 행 키가 복수 행을 구별한다 | `RowDiscriminator`(procurement `RawObservationStore.kt`) + `ObservationKeyDerivation.of`·`JdbcRawObservationStore.append` 확장. `RowDiscriminatorRawKeyTest` — 재료 동일 15행이 Positional discriminator로 15 raw row로 남는다(그 없이는 1행으로 접히는 회귀를 같은 test가 먼저 재현) |
| ② | 재시도 안정성 — 층을 갈라 본다 | 설계 검토 (1)절 전제 정정을 그대로 반영. raw 키는 이미 fetch마다 다르고(observedAt), canonical 기본키(`(notice_number, notice_round)`, 자식은 `+ reserve_price_sequence`)가 「retry 후 canonical effect 하나」를 진다. 별도 신규 코드 없음(기존 PK 제약이 이미 짐) |
| ③ | `OpeningResult` fact 슬롯 | 3B-2가 실제로 수집하는 것만 추가만(최종낙찰금액·최종낙찰업체명·참가업체수·진행구분 + §1.9.7 정정 이후 예정가격·기초금액·총예가건수·실개찰일시는 부모 슬롯). 추첨번호·투찰 축 자리는 만들지 않음(3F) |
| ④ | P-10 (a)가 저장 층까지 선다 | 사업자등록번호·대표자명 컬럼을 만들지 않음. `OpeningReservePriceRepositoryTest`의 컬럼 부재 단언 test(정보 스키마 질의)가 구조적으로 고정 |
| ⑤ | 복수예비가격은 1:N | `opening_reserve_price` 자식 표(V4) — `PRIMARY KEY (notice_number, notice_round, reserve_price_sequence)`, `guard_existence_and_freshness`(3D 관례, provenance 축 없음이라 `guard_authoritative_slot`이 아니다 — 「판단이 갈린 지점」 참조) |
| ⑥ | 멱등 재수집 | `ON CONFLICT ... DO UPDATE ... WHERE EXCLUDED.observed_at >= ...`(부모·자식 공통 관례). `OpeningReservePriceRepositoryTest`의 멱등 test(15행 재수집이 30행이 되지 않음) + D-3E-3 (a) test(15→12가 기존 행을 지우지 않음) |

## §1.9.7 실측 정정 반영(팀리드, 운영자 승인 아래 읽기 전용 호출 9회, 8건 23행 표본, 2026-09-01~09-07 창)

- 순번 공백은 총예가건수(`totRsrvtnPrceNum`)가 1(단수 예가)일 때만 관측됐다. 15행 건 4건은 순번이 전부 채워져 있었다 — COL-03이 요구하는 복수예비가격 축은 이 관측 범위에서 온전하다(표본이 작아 「항상 그렇다」로 승격하지 않는다).
- 그 귀결로 예정가격(`plnprc`)·기초금액(`bssamt`)·총예가건수(`totRsrvtnPrceNum`)·실개찰일시(`rlOpengDt`)를 자식 행이 아니라 `OpeningResult` **부모** 슬롯으로 모델링했다 — 단수 예가(자식 0개) 건에서도 이 값들을 잃지 않는다(`OpeningReservePriceRepositoryTest`의 「단수 예가」 test).
- `rsrvtnPrceFileExistnceYn='Y'`가 상세 0행과 어긋난 사례가 8건 중 2건 있었다 — 이 slice는 그 플래그를 조회 가치 술어 입력으로 쓰지 않는다. `OPEN-3E-RESERVE-FLAG-MISMATCH` 등재는 문서 레인이 완료(`bdf1e11`).

## 우회 여덟 — 무엇이 막는가 (설계 검토 (3)절 대응)

| # | 우회 | 막는 것 | 형태 |
| --- | --- | --- | --- |
| 1 | 행 구별 축을 키 재료에 안 넣음 | `RowDiscriminatorRawKeyTest`(결함 재현 + 수정 확인) | test |
| 2 | 부재 순번을 `0`·`""`로 채움 | `RowDiscriminator.of`가 값 우선/부재 시 위치만 낸다(`0`·`""` 생성 경로 없음) + `RowDiscriminatorTest` | 타입 + test |
| 3 | 값이 있는 행에도 ordinal을 씀 | `RowDiscriminator.of`가 값이 있으면 위치 인자를 무시(`RowDiscriminatorTest` 「위치가 무엇이든 무시」) | test |
| 4 | 순번 부재 행을 canonical 승격 | `opening_reserve_price.reserve_price_sequence NOT NULL` — 스키마가 지어낸 값 없이 삽입 자체를 거부(`OpeningReservePriceRepositoryTest` NULL 삽입 거부 test) | **스키마** |
| 5 | 승격 불가를 조용히 버림 | 3B-2 `rowIdentifierIndeterminate` 회계가 이미 센다(이 slice 신규 아님 — 같은 부재 판정을 공유, `KonepsRawItemMapper.rowDiscriminatorOf`) | 기존 test(3B-2, 편집 없음) |
| 6 | 사업자등록번호 컬럼을 만들어 둠 | 스키마에 컬럼 부재 + 컬럼 부재 단언 test | 스키마 + test |
| 7 | 사라진 행을 삭제로 처리 | D-3E-3 (a) — UPSERT만, DELETE 없음. `OpeningReservePriceRepositoryTest` 15→12 test | test |
| 8 | V4 대신 V1을 고침 | V1~V3 무편집(git diff로 확인) + Flyway `flyway.validate()`(CleanMigrationTest) | 도구 |

## 판단이 갈린 지점

1. **`guard_existence_and_freshness` vs `guard_authoritative_slot`** — `opening_result`·`opening_reserve_price` 신규 컬럼 모두 존재+신선도 가드만 적용했다(provenance 권위 컬럼 없음). 이유: 이 두 표는 3D가 이미 「최신 관측 우선, 권위 계층 없음」(D-M3-7 OPEN-DIC-09)으로 분류했고, 기존 `winning_rate_fraction`·`derived_base_amount_won`도 같은 가드를 쓴다. `guard_authoritative_slot`을 쓰려면 provenance 컬럼을 새로 만들어야 하는데 그 축 자체가 없다.
2. **`ReservePriceCandidateAmount`에서 `provenance` 제거** — 최초 설계(커밋 `1ed99e1`)는 이 값 객체에 `provenance: Provenance` 필드를 뒀으나, Layer B 저장 설계 중 DB 왕복 시 진짜 출처를 알 수 없어(고정 상수도 없다) 지어낸 값을 넣어야 하는 것을 발견해 제거했다(커밋 `43c8aa5`). `vatTreatment`도 `YegaAmount`·`AwardAmount`와 같이 `UNKNOWN` 내부 고정으로 바꿨다.
3. **`finalAwardAmount`·`plannedPrice`·`baseAmount` 읽기 provenance는 `Provenance.Published(id.round)`** — 지어낸 값이 아니다. 이 세 필드는 파생이 아니라 KONEPS 공식 응답에서 직접 온 값이라(D-3A-1 (a) `Notice`와 같은 성격), `noticeRevision`이 실제로 `id.round`에서 유도된다.
4. **부모+자식 upsert가 같은 `observationKey`를 공유** — `OpeningResultRepository.persist(result, observationKey)` 인터페이스가 애초에 하나만 받는다(interface 시그니처 미변경, in-scope 최소 변경). 서로 다른 오퍼레이션(낙찰 목록 vs 예비가격 상세)에서 온 값을 어떻게 하나의 `OpeningResult`로 합성할지는 workflow 배선(M4 4B) 소관이라 이 slice의 범위 밖이다.
5. **자식 표 `won`/`currency`만 저장, `vat` 컬럼 없음** — `ReservePriceCandidateAmount.vatTreatment`가 `UNKNOWN` 고정(내부화)이라 컬럼화하면 항상 같은 값을 저장하는 중복이다. `final_award_amount`·`planned_price`도 같은 이유로 vat 컬럼이 없다(`AwardAmount`·`YegaAmount` 둘 다 vatTreatment 고정).
6. **`CleanMigrationCheckTest` 신설(축8 CHECK을 `CleanMigrationTest`에서 재분리)** — sizeGate 500줄 초과(515줄)를 해소하기 위해, `CleanMigrationTriggerTest`(축7)가 이미 세운 전례 그대로 새 파일로 뺐다. 스키마 스냅샷 래칫 예외(운영자 승인 2026-09-08)는 지정된 두 파일(`CleanMigrationTest`·`CleanMigrationTriggerTest`)의 **편집**을 허용한 것이고, 새 파일 생성은 그 예외가 아니라 scope.md의 일반 in_scope 문구("adapters/src/test/kotlin/bidvector/adapters/persistence/**")로 커버된다 — 두 지정 파일 밖의 **기존 3D test 편집**은 없다(옮긴 것은 내 자신이 이 slice에서 만든 CHECK 기대값이다, 3D가 원래 세운 것이 아니다).
7. **`opening_reserve_price` → `opening_result` FK가 복합(2컬럼)** — `information_schema.key_column_usage`/`constraint_column_usage`가 제약 이름으로만 join되어 컬럼 순서 대응을 보존하지 않는 PostgreSQL 특성상, `CleanMigrationTest`의 FK 질의가 실제 2쌍이 아니라 cross product 4행을 낸다(실측, 스키마 결함 아님) — 기대값에 그 넷을 그대로 반영했다.

## 알려진 제한

- **`OPEN-3E-ROW-ORDER-STABILITY`**(신설 후보, 문서 레인 등재 대상) — KONEPS 응답 안 행 순서의 안정성은 방어하지 않는다(D-3E-1 (a)의 잔여 위험). 층 B 설계(순번 부재 행은 canonical 승격 거절)가 이 미지에 기대지 않도록 이미 짜여 있어 방어가 불필요하다는 것이 설계 검토 (4)절의 결론이다.
- **순번 부재 행의 canonical 부재**(D-3E-1b (a)의 대가) — 단수 예가 건은 자식 표에 행이 0개다. §1.9.7 정정으로 부모의 예정가격·기초금액은 보존되지만, 「이 후보가 몇 번째였는가」 자체는 저장되지 않는다.
- **기존 canonical 행의 backfill 없음** — 이 slice는 신규 수집분에만 적용된다(out_of_scope 명시).
- **추첨번호·투찰 축 부재**(3F 소관) — `getOpengResultListInfoOpengCompt` 미구현.
- **`OPEN-3E-RESERVE-FLAG-MISMATCH`** — `rsrvtnPrceFileExistnceYn`을 조회 가치 술어 입력으로 쓰지 않는다(문서 레인이 이미 등재, `bdf1e11`).
- **`OPEN-3E-SCHEMA-SNAPSHOT-MAINTENANCE`**(신설 후보) — 스키마 스냅샷 기대값을 test 코드 밖 선언으로 빼는 구조 개선은 이 slice 밖(3D 후속 또는 M6 운영 축, scope.md 예외 절이 이미 이 이름으로 열어 둠).
- **부모+자식이 같은 observationKey를 공유**(위 「판단이 갈린 지점」4) — 서로 다른 오퍼레이션에서 온 값의 합성은 M4 4B workflow 배선 소관.

## OPEN 처리 확인

- `OPEN-3B2-STORAGE-ROW-KEY-COLLISION` — 이 slice가 닫는다(①②). capability-map 상태 갱신은 문서 레인 소관(미확인 — 문서 레인 커밋 대기).
- `OPEN-3B2-OPENING-FACT-SLOTS` — 이 slice가 닫는다(③④⑤, 3F 축 제외). 같은 문서 레인 갱신 대기.
- `OPEN-3A-AGGREGATE` — D-3E-4 (a)는 D-3A-1 (a)의 연장이고 이 OPEN을 닫지 않는다(계약 명시 그대로).
