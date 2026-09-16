-- M3/3H-1 — 발주기관 fact 저장(D-3H-4, scope.md 「운영자 결정 2026-09-16 추천대로」 ②
-- migration-reviewer + Codex 범위 승인). V1~V6 를 고치지 않는다 — V5 선례(ALTER TABLE …
-- ADD COLUMN, nullable)와 같은 형태로 notice 에 컬럼 넷만 더한다.
--
-- 제약·인덱스·트리거는 두지 않는다(조회 인덱스는 3H-2/M6 `OPEN-4B7-QUERY-INDEX`와 함께 —
-- D-3H-4). 각 컬럼은 Kotlin `AgencyCode.of`/`AgencyName.of`가 이미 정규화·trim 한
-- 문자열만 받는다(procurement/Agency.kt) — DB 층에서 재정규화하지 않는다(§5.3 규율 1,
-- 「같은 규칙의 두 번째 구현을 두지 않는다」).
--
-- 병합은 `notice`의 기존 관례(존재 가드, `mergeNoticeRow`의 `businessCategoryCode` 축과
-- 같은 형태 — 유입이 있으면 쓰고 없으면 기존을 지킨다)를 그대로 쓴다. 이 축엔
-- provenance 컬럼이 없다(business_category_code·label 과 같은 이유 — D-3H-3).
ALTER TABLE notice
    ADD COLUMN demand_agency_code TEXT,
    ADD COLUMN demand_agency_name TEXT,
    ADD COLUMN notice_agency_code TEXT,
    ADD COLUMN notice_agency_name TEXT;
