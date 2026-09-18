# M6 / 6F-4 — 감시 대상 텍스트를 canonical 에 싣는다

- **base_sha**: `48cb072`
- **브랜치/worktree**: `m6-6f4/2026-09-18` / `bid-vector-v2-m6f4`
- **여는 결정**: 운영자 결정 ① (2026-09-18) — `OPEN-6F-WATCH-TEXT-SOURCE` 닫힘. 원문 텍스트를
  canonical 에 열로 싣는다(raw 를 판정 경로에서 읽는 안은 기각 — M3 의 raw/canonical 경계).
- **성격**: 스키마 파일 추가 + 도메인 조립 규칙. `migration-reviewer` 게이트는 붙인다(제약·인덱스
  설계). **「되돌리기 어려운 경로」가 아니다** — 아래 실측 5 참조. 따라서 Codex 유료 심판의
  자격 근거가 없다(그 분류를 착수 시점에 한 번 잘못 적었고, 실측으로 철회했다).

## 왜 (착수 조사 실측, base_sha 기준)

1. **감시 조건이 설 자리가 비어 있다.** canonical `notice` 에 공고명 열이 없다(V1 원표 + V7 이
   더한 기관 열 넷까지 전수 확인). V2 전체에서 KONEPS 공고명 키(`bidNtceNm`·`ntceNm`)와
   「공고명」·`title` 어휘가 **어댑터·워크플로·커널·`contracts/proto` 통틀어 0건**이다.
2. **legacy 는 읽고 있다.** `bid-vector/app/services/koneps/openapi.py` 가
   `raw_item.get("bidNtceNm") or raw_item.get("ntceNm") or notice_number` 로 title 을 세우고,
   `field_contract_spec.py` 의 알려진 키 집합에도 둘 다 등재돼 있다. 즉 **「API 가 안 준다」가
   아니라 「V2 가 안 싣는다」**이다.
3. **본문(`description`)은 공고 본문이 아니다 — 결정문이 남긴 숙제의 답.** legacy
   `opportunity_monitoring/filters.py` 가 키워드 매칭에서 그 필드를 제외하며 사유를 코드에 적어
   뒀다: *KONEPS 수집기가 공고기관·공고번호·URL 같은 **메타데이터**를 거기에 넣으므로 기관명이
   필수 키워드를 거짓으로 만족시킨다.* KONEPS 목록 응답의 알려진 키 집합에도 본문 필드가 없고,
   본문은 `bidNtceDtlUrl`·`ntceSpecDocUrl1` **뒤에** 있다.
4. **그 필드가 실제로 나르던 것은 지역 단서다.** 같은 파일이 지역 매칭에만 전체 텍스트를 쓰는
   이유를 적어 뒀다 — *지역은 그 메타데이터 필드에 정당하게 들어 있을 수 있다*. 그런데 V2 는
   그 단서를 **이미 구조화된 열로** 갖고 있다(V7 의 `demand_agency_name`·`notice_agency_name`).
5. **대상 데이터가 없고, 채울 경로도 없다 — 이 slice 의 범위를 정하는 실측이다.**
   - **DB 인스턴스 0.** 이 머신에 이 프로젝트의 Postgres 볼륨·컨테이너가 없고, 저장소 전체에
     실 DB 를 가리키는 JDBC URL 이 없다(유일한 리터럴은 *일부러 없는 호스트*를 쓰는 test).
     스키마는 `check` 의 **일회성 Testcontainers** 와 CI 의 compose(끝에 볼륨까지 삭제)에만 선다.
   - **실행 진입점 0.** `app` 모듈의 main 소스는 경계 표식 하나뿐이고 `application` 플러그인도
     `mainClass` 도 없다 — 수집을 돌릴 프로세스가 아직 없다(6A-1 이 세운다).
   - **공고명 표본 0.** input fixture 119개 중 이 키를 가진 것은 1개이고, 그 1건의 값도 공고명이
     아니라 「공고번호가 없는 행」을 만들려고 넣은 test 문자열이다.
   - 귀결 둘: (a) V11 은 **파일을 지우면 완전히 되돌아간다** — 적용된 상태가 없으므로 「되돌리기
     어려운 경로」가 아니다. (b) authoritative fixture 가 0건이면 **수집→canonical 배선을 잠그는
     test 를 쓸 근거가 없다** — 만들어 놓고 진짜 값을 한 번도 본 적 없는 코드가 된다.
     그래서 이 slice 는 **열과 조립 규칙까지**만 한다(운영자 결정 2026-09-18).
   - 결정문의 착수 근거 「운영 데이터 0 이라 백필 비용이 지금이 최저」는 참이지만 **공허**하다 —
     프로세스가 생기기 전까지 그 값은 계속 0 이다. 지금 해도 되는 이유이지 지금 해야 하는
     이유는 아니다. 그래도 지금 하는 이유는 **감시 조건의 정본 자리를 먼저 세워 두면 6A-1 이
     만드는 수집 경로가 그 자리에 붙을 뿐**이기 때문이다(반대 순서면 배선이 먼저 서고 정본이
     나중에 와서 정본이 배선을 따라간다).

## 결정

- **D-6F4-1 — `notice` 에 공고명 열 하나를 nullable 로 더한다(V11).** V7 이 기관 열 넷을
  `ADD COLUMN ... TEXT`(nullable)로 더한 것과 **같은 형태**다. 스키마 스냅샷 래칫은 추가만 예외.
- **D-6F4-2 — 본문 열을 만들지 않는다.** legacy 의 그 필드는 공고 본문이 아니라 수집 메타데이터
  덤프이고, legacy 자신이 「키워드 매칭에 쓰면 오탐」이라는 사유로 제외하고 있다. 그 모양을 V2 에
  옮기는 것은 결함을 옮기는 것이다(1:1 복제 금지). `differential.json` 에
  `intentional-redesign` 으로 등재한다.
- **D-6F4-3 — 감시 텍스트 둘을 V2 용어로 다시 정의한다(운영자 결정 A, 2026-09-18 로 개정).**
  - 키워드 매칭 대상 = **공고명 + 공종(`business_category_label`)**.
  - 지역 매칭 대상 = 위 + **기관명 두 열(V7)**. legacy 가 메타데이터 덤프에서 긁던 지역 단서를
    **타입이 있는 열**에서 얻는다 — 덤프가 없으므로 「기관명이 필수 키워드를 만족시키는」 오탐
    경로가 **구조적으로** 생기지 않는다.
  - 본문을 포함하는 제3의 텍스트는 V2 에 **존재하지 않는다**.
- **D-6F4-3b — 요건 축(`qualification_text`)은 키워드 대상에서 뺀다(운영자 결정 A).** 실측 셋:
  (a) legacy 의 `requirements` 는 작업 서술이 아니라 **합성 행정 메타데이터** 다섯 줄이고 그중
  둘이 금액이다. (b) V2 `qualification_text` 는 legacy 의 그 필드가 아니라 **면허제한 오퍼레이션**
  (`getBidPblancListInfoLicenseLimit`)에서 오며 응답 필드는 면허명·허용업종·업종분야다 — 업무
  키워드가 들어올 자리가 아니다. (c) legacy 가 같은 성격의 열(`eligibility_raw`)에 **「현재 소비자
  없음」**을 주석으로 적어 뒀다.
  「키워드에서 빼고 지역 대상으로 옮긴다」는 절충안은 **성립하지 않는다** — 그 텍스트에 지역
  어휘가 없다(위 응답 필드 전수). 그래서 옮김이 아니라 제거다.
  **이 결정은 승인 문면을 줄인다** — capability-map STR-02 의 acceptance 둘째 줄(「같은 키워드가
  제목 **또는 요건**에 있으면 후보」)의 뒷절에 대응할 데이터가 V2 에 없다. 축소를
  **`OPEN-6F4-STR02-REQUIREMENTS-AXIS`** 로 등재하고, 요건 원문을 싣는 축이 생기면 그때 다시 본다.
- **D-6F4-8 — 공고명 갱신은 기존 쓰기 규율에 맡기고, 「없음」은 센티넬이 아니라 타입으로 닫는다
  (운영자 결정 B).** 권위 provenance·덮어쓰기 규칙을 그대로 적용하고 별도 규칙을 만들지 않는다.
  legacy 는 합성 title 을 **센티넬 접두사**로 표시하고 `startswith` 로 되읽어 덮어쓸지를 정했는데,
  그 결과 **진짜 공고명이 한 번 들어오면 정정 공고에도 영구 고정**됐다(실측 확인). 재현하지 않는다
  — 값이 없으면 nullable 로 없다.
- **D-6F4-9 — 도메인 슬롯까지가 이 slice 다.** canonical 열만 세우고 도메인이 그 값을 못 나르면
  복원 경로가 값을 **조용히 버린다**(열은 있는데 왕복은 가짜다). 그래서 `procurement` 의 canonical
  fact 와 그 조립 커맨드에 공고명 슬롯을 **기본값 null 로** 더한다 — D-3H-3 가 기관 축을 더할 때
  쓴 것과 같은 형태이고 production 호출부는 둘뿐이다. 이것이 in_scope 에 `procurement/` 가 있는
  이유다(착수 계약 누락을 실측으로 메운 것).
- **D-6F4-4 — 백필 문제가 성립하지 않는다.** 되돌릴 행도 채울 행도 없다(실측 5). 열은 nullable
  로 서고, 채우는 것은 수집 경로가 생기는 slice 의 일이다.
- **D-6F4-4b — 수집→canonical 배선은 이 slice 가 하지 않는다(운영자 결정 2026-09-18).**
  공고명 표본이 0건이라 배선을 잠그는 test 의 기대값을 authoritative 하게 세울 수 없다. 배선은
  **표본이 생기거나 실행 경로가 생기는 slice** 가 가져간다 — `OPEN-6F4-TITLE-WIRING`.
  이 slice 가 남기는 것은 그 배선이 붙을 **정본 자리**(열 + 조립 규칙 + 그 둘을 고정하는 test)다.
- **D-6F4-5 — 본문이 필요해지면 선행 축이 있다.** 목록 응답에 없고 상세 URL 뒤에 있으므로
  **`OPEN-6F4-NOTICE-BODY-SOURCE`** 를 신설한다. 이 slice 는 그 축을 열지 않는다.
- **D-6F4-6 — 배선이 올 때 공고명 키를 어댑터에 하드코딩하지 않는다.** KONEPS 원시 키는 정책
  데이터가 나르고 매퍼가 그것을 읽는 구조다(생산 쪽 필드 계약을 구성하는 코드는 현재 test 지원
  파일에만 있다 — 실행 경로가 없기 때문이다). 이 결정은 `OPEN-6F4-TITLE-WIRING` 을 가져가는
  slice 에 대한 **선행 제약**으로 여기 남긴다 — 키를 코드에 박으면 수집 계약 변경이 코드 변경이
  된다.
- **D-6F4-7 — 마이그레이션 번호는 V11 이다.** main 은 V9 까지이고 **V10 은 6A-1 계약이 선점**했다
  (`m6-6a/2026-09-17`, 미병합). 번호는 순서일 뿐이므로 이 slice 가 비켜선다. 6A-1 이 번호를
  바꾸면 이 결정을 갱신한다.

## in_scope

- `adapters/src/main/resources/db/migration/V11__notice_title.sql` (신설)
- `adapters/src/main/kotlin/bidvector/adapters/persistence/` — `Sql.kt`, `NoticeRow.kt`,
  `NoticeRowMerge.kt`, `NoticeReconstruction.kt`, `JdbcNoticeRepository.kt`
- `procurement/` — canonical fact 와 조립 커맨드의 공고명 슬롯(기본값 null, D-6F4-9)
- `strategy/` — 감시 텍스트 두 타입의 조립 규칙과 그 문서(현재 KDoc 이 요건 축·본문을 전제해
  D-6F4-3·3b 와 어긋난다. 코드와 계약이 갈린 채 남기지 않는다)
- 감시 텍스트 조립 순수 함수와 그 test — 이 slice 의 실질
- `adapters/src/test/kotlin/bidvector/adapters/persistence/` — `CleanMigrationColumnTest.kt`,
  `CleanMigrationCheckTest.kt`, `NoticeReconstructionTest.kt` 등 스키마 대조 test
- `reports/evidence/m6/6f4/`

## out_of_scope

- **수집→canonical 공고명 배선**(KONEPS 원시 키 정책·매퍼) — `OPEN-6F4-TITLE-WIRING`, D-6F4-4b
- 공고 본문 수집(상세 URL 경로) — `OPEN-6F4-NOTICE-BODY-SOURCE`
- 전략 편집·조회 endpoint(6A-1·6F-1 소유), 감시 알림 채널(6F 다른 slice)
- 기존 59개 slice 의 evidence 문서
- 검색(STR-16) 경로 — 같은 텍스트를 입력으로 쓰지만 이 slice 는 정본 열만 세운다

## acceptance_commands

CI `check` job 의 명령 그대로(`.github/workflows/ci.yml`). 부분 게이트는 안 돌린 것과 같다.

```
./gradlew --no-daemon check
./gradlew --no-daemon qualityBaseline
./tools/one-command-check.sh
```

## rollback

`rollback.md` 에 둔다. **①~⑥ 실측은 이 slice 의 마지막 산출물 커밋에서 돌리고 `실측 HEAD: <sha>`
를 그 문서 첫머리에 적는다**(evidence-pack, 2026-09-18 — verifier 가 판정 대상 SHA 와 대조하고
다르면 미검증으로 처리한다). 목록은 `git diff --name-status <base>..HEAD` 기계 산출이며 라운드마다
재산출한다. 신설 파일은 `git rm`, 수정 파일은 `git restore --source=<base>` 다.

## 하네스 레인 변경 (상시)

착수 시점 없음. 리뷰 요청 시점마다 `git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 로 갱신.
