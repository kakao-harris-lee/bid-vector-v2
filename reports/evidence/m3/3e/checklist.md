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
| 4 | 순번 부재 행을 canonical 승격 | `opening_reserve_price.reserve_price_sequence NOT NULL` + `CHECK (btrim(...) <> '')`(V6, verifier r1 M-1 뒤 — V4의 `<> ''`는 공백 한 칸을 못 막았다) — 스키마가 지어낸 값(NULL·빈 문자열·공백) 없이 삽입 자체를 거부(`OpeningReservePriceRepositoryTest` 셋 다 거부 test) | **스키마** |
| 5 | 승격 불가를 조용히 버림 | 3B-2 `rowIdentifierIndeterminate` 회계가 이미 센다(이 slice 신규 아님 — 같은 부재 판정을 공유, `KonepsRawItemMapper.rowDiscriminatorOf`) | 기존 test(3B-2, 편집 없음) |
| 6 | 사업자등록번호 컬럼을 만들어 둠 | 스키마에 컬럼 부재 + 컬럼 부재 단언 test | 스키마 + test |
| 7 | 사라진 행을 삭제로 처리 | D-3E-3 (a) — UPSERT만, DELETE 없음. `OpeningReservePriceRepositoryTest` 15→12 test + `OpeningReservePriceRow.observedAt`(verifier r1 H-2 뒤 신설)이 낡음을 읽기 경로에서도 드러낸다 | test |
| 8 | V4 대신 V1을 고침 | V1~V3 무편집(git diff로 확인) + Flyway `flyway.validate()`(CleanMigrationTest) | 도구 |

## 판단이 갈린 지점

1. **`guard_existence_and_freshness` vs `guard_authoritative_slot`** — `opening_result`·`opening_reserve_price` 신규 컬럼 모두 존재+신선도 가드만 적용한다(provenance 권위 컬럼은 V5에서 셋 생겼지만 권위 판정 축은 열지 않았다). 이유: 이 두 표는 3D가 이미 「최신 관측 우선, 권위 계층 없음」(D-M3-7 OPEN-DIC-09)으로 분류했고, `guard_authoritative_slot`을 여는 것은 3D 설계 변경이라 이 slice에서 열지 않는다(팀리드 지시, verifier r1 H-1 수정 라운드). **미결로 등재** — 아래 「알려진 제한」·`OPEN-3E-OPENING-AMOUNT-AUTHORITY-GUARD` 참조.
2. **`ReservePriceCandidateAmount`에서 `provenance` 제거** — 최초 설계(커밋 `1ed99e1`)는 이 값 객체에 `provenance: Provenance` 필드를 뒀으나, Layer B 저장 설계 중 DB 왕복 시 진짜 출처를 알 수 없어(고정 상수도 없다) 지어낸 값을 넣어야 하는 것을 발견해 제거했다(커밋 `43c8aa5`).
3. **`finalAwardAmount`·`plannedPrice`·`baseAmount` provenance — verifier r1 H-1 뒤 정정.** 최초 판(커밋 `8279753`)은 읽기 경로에서 `Provenance.Published(id.round)` 상수를 씌웠는데, `V4`에 provenance 컬럼이 없어 저장한 값(예: `Undeclared`, 비권위)이 왕복 한 번으로 `Published`(권위)가 되는 결함이었다 — `43c8aa5`가 `ReservePriceCandidateAmount`에서 이미 걷어낸 것과 같은 형태가 부모 세 슬롯에 남아 있었다. `V5`가 `notice` 표 관례(`ProvenanceCodec`, kind+detail 두 컬럼)를 그대로 적용해 저장한 것을 그대로 복원하도록 고쳤다(`readAmountProvenance`). `derived_base_amount`는 여전히 상수 복원이다 — `ResolvedBaseAmount.DerivedFromOpeningAmount` 타입이 그 provenance를 구조적으로 보장하는 유일한 자리라 그 관례가 정당하다(H-1 finding이 짚은 구분).
4. **부모+자식 upsert가 같은 `observationKey`를 공유** — `OpeningResultRepository.persist(result, observationKey)` 인터페이스가 애초에 하나만 받는다(interface 시그니처 미변경, in-scope 최소 변경). 서로 다른 오퍼레이션(낙찰 목록 vs 예비가격 상세)에서 온 값을 어떻게 하나의 `OpeningResult`로 합성할지는 workflow 배선(M4 4B) 소관이라 이 slice의 범위 밖이다. verifier r1이 medium(M-3)으로 재확인했고 비차단으로 분류했다.
5. **자식 표 `won`/`currency`만 저장, `vat` 컬럼 없음** — `ReservePriceCandidateAmount.vatTreatment`가 `UNKNOWN` 고정(내부화)이라 컬럼화하면 항상 같은 값을 저장하는 중복이다. `final_award_amount`·`planned_price`도 같은 이유로 vat 컬럼이 없다(`AwardAmount`·`YegaAmount` 둘 다 vatTreatment 고정).
6. **`CleanMigrationCheckTest` 신설(축8 CHECK을 `CleanMigrationTest`에서 재분리)** — sizeGate 500줄 초과(515줄)를 해소하기 위해, `CleanMigrationTriggerTest`(축7)가 이미 세운 전례 그대로 새 파일로 뺐다. verifier r1이 이 판단을 실측으로 확인했다(451+55=506>500, 재분리 없이는 sizeGate 실패).
7. **`opening_reserve_price` → `opening_result` FK가 복합(2컬럼)** — `information_schema.key_column_usage`/`constraint_column_usage`가 제약 이름으로만 join되어 컬럼 순서 대응을 보존하지 않는 PostgreSQL 특성상, `CleanMigrationTest`의 FK 질의가 실제 2쌍이 아니라 cross product 4행을 낸다(verifier r1이 `pg_constraint`로 직접 대조해 실제 FK는 정확히 둘임을 재확인, 스키마 결함 아님) — 기대값에 그 넷을 그대로 반영했다.
8. **`OpeningReservePriceRow.observedAt`을 필수 파라미터로 둠(기본값 없음)** — verifier r1 H-2 뒤 신설. raw 관측이 이미 `observedAt`을 항상 나르므로 「모름」이 아니고, `Instant.EPOCH` 같은 지어낸 기본값을 두면 그 자체가 「모름을 지어내지 않는다」 규율 위반이다. 쓰기 경로는 각 행의 `observedAt`을 그대로 컬럼에 싣는다(부모 `result.observedAt`을 대신 쓰지 않는다) — 자식 행이 자기 관측 시각을 스스로 나른다.
9. **`RowDiscriminator.append` 기본값 `null` 유지(M-2)** — 제거하면 3D 소유 `F2CollisionRegressionTest.kt`·`PersistenceTestSupport.kt`의 단일 인자 호출부를 편집해야 하는데 두 파일 다 「3B·3B-2·3C·3D 기존 test 편집 금지」 대상이다. 비용이 보호 규율과 정면충돌해 기본값을 유지하고 알려진 제한으로 등재한다(아래).

## 알려진 제한

- **`OPEN-3E-ROW-ORDER-STABILITY`**(신설 후보, 문서 레인 등재 대상) — KONEPS 응답 안 행 순서의 안정성은 방어하지 않는다(D-3E-1 (a)의 잔여 위험). 층 B 설계(순번 부재 행은 canonical 승격 거절)가 이 미지에 기대지 않도록 이미 짜여 있어 방어가 불필요하다는 것이 설계 검토 (4)절의 결론이다.
- **순번 부재 행의 canonical 부재**(D-3E-1b (a)의 대가) — 단수 예가 건은 자식 표에 행이 0개다. §1.9.7 정정으로 부모의 예정가격·기초금액은 보존되지만, 「이 후보가 몇 번째였는가」 자체는 저장되지 않는다.
- **기존 canonical 행의 backfill 없음** — 이 slice는 신규 수집분에만 적용된다(out_of_scope 명시).
- **추첨번호·투찰 축 부재**(3F 소관) — `getOpengResultListInfoOpengCompt` 미구현.
- **`OPEN-3E-RESERVE-FLAG-MISMATCH`** — `rsrvtnPrceFileExistnceYn`을 조회 가치 술어 입력으로 쓰지 않는다(문서 레인이 이미 등재, `bdf1e11`).
- **`OPEN-3E-SCHEMA-SNAPSHOT-MAINTENANCE`**(신설 후보) — 스키마 스냅샷 기대값을 test 코드 밖 선언으로 빼는 구조 개선은 이 slice 밖(3D 후속 또는 M6 운영 축, scope.md 예외 절이 이미 이 이름으로 열어 둠).
- **부모+자식이 같은 observationKey를 공유**(위 「판단이 갈린 지점」4) — 서로 다른 오퍼레이션에서 온 값의 합성은 M4 4B workflow 배선 소관.
- **`OPEN-3E-OPENING-AMOUNT-AUTHORITY-GUARD`**(신설 후보, verifier r1 H-1 뒤) — `final_award_amount`·`planned_price`·`opening_base_amount`는 이제 provenance를 왕복시키지만(V5), **권위 가드(`guard_authoritative_slot`) 적용 여부는 미결**이다. 지금은 `guard_existence_and_freshness`만 적용돼 있어 「비권위 유입이 권위 있는 값을 덮는 것」을 막지 않는다 — provenance는 정확히 저장·복원되지만 그 값이 쓰기를 결정하지는 않는다. 권위 축을 여는 것은 3D 설계 변경(`provenance_authority` 참조·Kotlin 쓰기 규칙 신설)이라 이 slice 밖으로 넘긴다.
- **`RowDiscriminator` 프로덕션 호출부 부재(M-2, adapter 층 한정)** — `RawObservationStore.append`에 discriminator를 넘기는 프로덕션 경로가 0건이다(3B 3B-2 koneps 호출부는 목록 오퍼레이션이라 discriminator가 필요 없다). `OPEN-3B2-STORAGE-ROW-KEY-COLLISION`의 종결은 **능력 수준**이지 운영 경로 수준이 아니다 — M4 4B가 예비가격 상세처럼 한 응답에 복수 행을 내는 오퍼레이션을 배선할 때 discriminator 인자를 실제로 넘겨야 종결이 완성된다. 기본값 `null`은 제거하지 않는다(위 「판단이 갈린 지점」9 — 3D 보호 test 편집 필요).
- **`ObservationKeyDerivation.of` 재료 확장이 기존 raw 키를 전부 바꾼다(L-1)** — `rowDiscriminator` 인자가 없어도(`null`) 재료 마지막 칸이 항상 존재해 절대 해시 값 자체는 이 slice 전후로 달라진다(상대적 동등/비동등만 test가 보장하지, 절대 해시 안정성은 보장하지 않는다). 오늘은 영향이 0이다(배포된 DB가 없다) — 실제 배포 뒤 이 변경이 적용되면 기존 raw_observation 행과 재수집 행이 다른 키를 얻어 append-only 감사에 중복 관측처럼 보일 수 있다(값은 같다). backfill이 없다는 사실은 이미 등재돼 있으나, 「키 자체가 바뀐다」는 사실을 여기 별도로 남긴다.
- **`progress_division`에 열거 제약이 없다(L-8)** — §1.7.5가 3값(유찰/개찰완료/재입찰)을 선언하지만 컬럼은 자유 `TEXT`다. 「문서 라벨 원문 그대로 저장」 결정과 정합하지만, 문서 밖 라벨이 와도 거부하지 않는다. CHECK을 추가하지 않기로 한 이유: 실제 KONEPS 응답의 정확한 문자열(대소문자·공백)을 이 slice가 관측하지 못했고, 잘못된 리터럴로 제약을 걸면 정상 데이터를 거부할 위험이 커진다 — `bsnsDivNm`·`ntceKindNm` 등 이 저장소의 다른 라벨 축도 같은 이유로 DB CHECK을 두지 않는다.

## OPEN 처리 확인

- `OPEN-3B2-STORAGE-ROW-KEY-COLLISION` — 이 slice가 닫는다(①②). capability-map 상태 갱신은 문서 레인 소관(미확인 — 문서 레인 커밋 대기).
- `OPEN-3B2-OPENING-FACT-SLOTS` — 이 slice가 닫는다(③④⑤, 3F 축 제외). 같은 문서 레인 갱신 대기.
- `OPEN-3A-AGGREGATE` — D-3E-4 (a)는 D-3A-1 (a)의 연장이고 이 OPEN을 닫지 않는다(계약 명시 그대로).
