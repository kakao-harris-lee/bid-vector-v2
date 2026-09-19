-- M6/6F-4(D-6F4-1) — 감시 키워드 매칭 입력(공고명) 열. V7 선례(ALTER TABLE ... ADD COLUMN,
-- nullable)와 같은 형태. 제약·인덱스는 이 열 하나뿐이다 — 조회 인덱스는 배선(6A-1 이후)
-- slice의 일이다.
--
-- CHECK 하나가 V7 과 다르다(D-6F4-8) — 기관 열 넷은 매칭 술어의 입력이 아니지만 이 열은
-- 감시 키워드 매칭의 입력이라(D-6F4-3), 빈 문자열이 조용히 들어오면 "아무것도 안 맞는다"가
-- 조용히 성립한다. 「없음」은 센티넬이 아니라 타입(`NoticeTitle.of`)과 이 제약 둘로 닫는다.
--
-- **정규식은 `<> ''`가 아니다(verifier r2 MEDIUM-1 뒤)** — 이 저장소는 같은 계열을
-- `V4__opening_result_rows.sql`의 `opening_reserve_price.reserve_price_sequence` CHECK에서
-- 이미 닫았다: DB CHECK과 Kotlin 쪽 공백 판정(`NoticeTitle.of`의 `String.trim()`, JDK
-- `Char.isWhitespace()` = `Character.isWhitespace()` OR `Character.isSpaceChar()`의 합집합)이
-- **같은 입력 집합**에 같은 답을 내야 한다. `<> ''`는 그 부분집합만 막는다 — 공백류
-- (U+00A0 NBSP·U+3000 전각 공백 등)는 통과시키면서 Kotlin은 그것을 `null`로 읽어 저장은
-- non-null인데 복원은 부재인 비대칭을 만든다. `btrim()`도 ASCII 공백만 깎아 같은 틈을
-- 남긴다(V4 주석). 그 선례가 쓴 해법을 그대로 따른다 — 새 방식을 발명하지 않는다.
ALTER TABLE notice
    ADD COLUMN notice_title TEXT
        CHECK (notice_title IS NULL
            OR notice_title ~ '[^\u0009-\u000D\u001C-\u001F\u0020\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000]');
