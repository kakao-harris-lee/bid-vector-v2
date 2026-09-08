-- M3/3E verifier r1 M-1(medium) 뒤 신설 — `CHECK (reserve_price_sequence <> '')`(V4)는 빈
-- 문자열만 막고 공백(`' '`)은 통과시킨다. 실측: 직접 SQL로 공백 한 칸을 삽입하면 성공했다
-- (Kotlin 경로는 `isNotBlank()`로 이미 닫혀 있어 도달하려면 직접 SQL이 필요했다). `btrim`으로
-- 공백만 겨눈다 — `'0'`은 유효한 순번일 수 있어 막지 않는다(D-3E-1b (a)의 스키마 층 주장과
-- 실물을 맞춘다).
ALTER TABLE opening_reserve_price
    DROP CONSTRAINT opening_reserve_price_reserve_price_sequence_check,
    ADD CONSTRAINT opening_reserve_price_reserve_price_sequence_check
        CHECK (btrim(reserve_price_sequence) <> '');
