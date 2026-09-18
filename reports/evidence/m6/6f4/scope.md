# M6 / 6F-4 — 감시 대상 텍스트를 canonical 에 싣는다

- **base_sha**: `48cb072`
- **브랜치/worktree**: `m6-6f4/2026-09-18` / `bid-vector-v2-m6f4`
- **여는 결정**: 운영자 결정 ① (2026-09-18) — `OPEN-6F-WATCH-TEXT-SOURCE` 닫힘. 원문 텍스트를
  canonical 에 열로 싣는다(raw 를 판정 경로에서 읽는 안은 기각 — M3 의 raw/canonical 경계).
- **성격**: DB 마이그레이션(되돌리기 어려운 경로) → `migration-reviewer` 게이트 필수,
  Codex 심판은 운영자가 범위·비용을 승인하면.

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

## 결정

- **D-6F4-1 — `notice` 에 공고명 열 하나를 nullable 로 더한다(V11).** V7 이 기관 열 넷을
  `ADD COLUMN ... TEXT`(nullable)로 더한 것과 **같은 형태**다. 스키마 스냅샷 래칫은 추가만 예외.
- **D-6F4-2 — 본문 열을 만들지 않는다.** legacy 의 그 필드는 공고 본문이 아니라 수집 메타데이터
  덤프이고, legacy 자신이 「키워드 매칭에 쓰면 오탐」이라는 사유로 제외하고 있다. 그 모양을 V2 에
  옮기는 것은 결함을 옮기는 것이다(1:1 복제 금지). `differential.json` 에
  `intentional-redesign` 으로 등재한다.
- **D-6F4-3 — 감시 텍스트 둘을 V2 용어로 다시 정의한다.**
  - 키워드 매칭 대상 = **공고명 + 요건(`qualification_text`) + 공종(`business_category_label`)**
    (capability-map STR-02 의 승인된 조립 규칙 그대로).
  - 지역 매칭 대상 = 위 + **기관명 두 열(V7)**. legacy 가 메타데이터 덤프에서 긁던 지역 단서를
    **타입이 있는 열**에서 얻는다 — 덤프가 없으므로 「기관명이 필수 키워드를 만족시키는」 오탐
    경로가 **구조적으로** 생기지 않는다.
  - 따라서 본문을 포함하는 제3의 텍스트는 V2 에 **존재하지 않는다**. capability-map STR-02 문면과
    이 차이를 evidence 에 대조로 남긴다.
- **D-6F4-4 — 백필하지 않는다.** 열은 nullable 로 서고 수집 경로가 채운다. 운영 데이터 0 이
  이 slice 를 지금 하는 이유다(결정문).
- **D-6F4-5 — 본문이 필요해지면 선행 축이 있다.** 목록 응답에 없고 상세 URL 뒤에 있으므로
  **`OPEN-6F4-NOTICE-BODY-SOURCE`** 를 신설한다. 이 slice 는 그 축을 열지 않는다.
- **D-6F4-6 — 공고명 키를 어댑터에 하드코딩하지 않는다.** KONEPS 원시 키는 현재 정책 데이터가
  나르고 매퍼는 그것을 읽는다. 그 구조를 유지한다 — 키를 코드에 박으면 수집 계약 변경이 코드
  변경이 된다.
- **D-6F4-7 — 마이그레이션 번호는 V11 이다.** main 은 V9 까지이고 **V10 은 6A-1 계약이 선점**했다
  (`m6-6a/2026-09-17`, 미병합). 번호는 순서일 뿐이므로 이 slice 가 비켜선다. 6A-1 이 번호를
  바꾸면 이 결정을 갱신한다.

## in_scope

- `adapters/src/main/resources/db/migration/V11__notice_title.sql` (신설)
- `adapters/src/main/kotlin/bidvector/adapters/persistence/` — `Sql.kt`, `NoticeRow.kt`,
  `NoticeRowMerge.kt`, `NoticeReconstruction.kt`, `JdbcNoticeRepository.kt`
- `adapters/src/main/kotlin/bidvector/adapters/koneps/` — 원시 키 정책과 매퍼(D-6F4-6)
- 감시 텍스트 조립이 서는 도메인 자리(`workflow` 전략 평가 경로) 및 그 test
- `adapters/src/test/kotlin/bidvector/adapters/persistence/` — `CleanMigrationColumnTest.kt`,
  `CleanMigrationCheckTest.kt`, `NoticeReconstructionTest.kt` 등 스키마 대조 test
- `reports/evidence/m6/6f4/`

## out_of_scope

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
