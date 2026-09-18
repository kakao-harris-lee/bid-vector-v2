-- M6/6F-4(D-6F4-1) — 감시 키워드 매칭 입력(공고명) 열. V7 선례(ALTER TABLE ... ADD COLUMN,
-- nullable)와 같은 형태. 제약·인덱스는 이 열 하나뿐이다 — 조회 인덱스는 배선(6A-1 이후)
-- slice의 일이다.
--
-- CHECK 하나가 V7 과 다르다(D-6F4-8) — 기관 열 넷은 매칭 술어의 입력이 아니지만 이 열은
-- 감시 키워드 매칭의 입력이라(D-6F4-3), 빈 문자열이 조용히 들어오면 "아무것도 안 맞는다"가
-- 조용히 성립한다. 「없음」은 센티넬이 아니라 타입(`NoticeTitle.of`)과 이 제약 둘로 닫는다.
ALTER TABLE notice
    ADD COLUMN notice_title TEXT CHECK (notice_title IS NULL OR notice_title <> '');
