-- M6/6F-9 D-6F9-3 — 업무구분 새 칸 셋. 6F-8 실수집이 드러낸 공백(공고 0건에 업무구분 없음)을 닫는다: 응답에는 업무구분 라벨
-- 키가 없고 **대분류는 수집 오퍼레이션 자체**(공사 목록·용역 목록을 따로 부른다)라, 세 축을 각자 자기 열에 둔다. 기존
-- `business_category_code`·`business_category_label`(코드+라벨 축)에 섞지 않는다(P-7 · `OPEN-COL-03` — 축을 접지 않는다).
--
--   business_division      — 대분류. 문서 열거 어휘 넷(P-7: 물품·용역·공사·외자, 문서 표기 순서), 원천 = 수집 오퍼레이션.
--   service_division       — 용역구분(`srvceDivNm`: 일반용역·기술용역 …), 원문 trim 이름.
--   main_construction_type — 주공종(`mainCnsttyNm`: 전기공사업·건축공사업 …), 공사 응답은 코드가 없어 이름만이다.
--
-- 전부 nullable, DEFAULT 없음(D-6F1-5·V16 관례 「값 지어내기 없음」) — 기존 행은 NULL 로 보존되고 같은 조회일의 재수집이
-- 채운다(그 갱신은 `NoticeRowMerge` 의 존재 가드를 따라 `Updated` 로 센다).
--
-- CHECK: 대분류는 어휘 넷만. 나머지 둘은 V14(`notice_title`)와 **같은 공백류 문자 클래스**다 — 이 열들도 감시 「관심 업종」
-- 집합의 입력이라(D-6F9-4) 빈 문자열이 조용히 들어오면 "아무것도 안 맞는다"가 조용히 성립한다(D-6F4-8). 정규식이 `<> ''`
-- 가 아닌 이유는 V14·V4 주석(DB CHECK 과 Kotlin `String.trim()` 이 같은 입력 집합에 같은 답을 내야 한다)과 같다.
--
-- 되돌림 두 갈래(rollback.md) — 미적용 DB: 이 파일 삭제. 적용된 DB: 새 V 파일의 `ALTER TABLE notice DROP COLUMN ...`
-- (파일 삭제로 되돌리지 않는다, 6F-4·6A-3 규율).
ALTER TABLE notice
    ADD COLUMN business_division TEXT
        CHECK (business_division IS NULL OR business_division IN ('물품', '용역', '공사', '외자')),
    ADD COLUMN service_division TEXT
        CHECK (service_division IS NULL
            OR service_division ~ '[^\u0009-\u000D\u001C-\u001F\u0020\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000]'),
    ADD COLUMN main_construction_type TEXT
        CHECK (main_construction_type IS NULL
            OR main_construction_type ~ '[^\u0009-\u000D\u001C-\u001F\u0020\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000]');
