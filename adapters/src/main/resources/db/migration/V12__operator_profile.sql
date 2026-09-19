-- M6/6F-6 D-6F6-1~3 — 운영자 프로필 영속: 업종·면허·지역 어휘 싱글턴 표. `operator_strategy`
-- (V9)와 같은 싱글턴 관례(id=1, 리터럴) — 이 표도 항상 한 행이거나 한 행도 없다(「미설정」).
--
-- licenses_declared·license_names 가 짝을 이뤄 `OperatorLicenses`의 sealed 두 상태
-- (Declared/NotDeclared)를 잃지 않게 별도 열로 나른다 — 이 표 하나가 세 상태를 구분한다:
-- ① 행 없음(「프로필 미설정」, current() 가 null) ② licenses_declared=false(NotDeclared)
-- ③ licenses_declared=true, license_names='{}'(Declared(빈 목록)). ②·③를 열 하나로
-- 납작하게 접으면 `Uncertain` 판정이 「면허 0개 보유」로 바뀐다(scope.md 위협 모델 방어 ①).

CREATE TABLE operator_profile (
    id SMALLINT PRIMARY KEY CHECK (id = 1),

    business_types TEXT[] NOT NULL,
    licenses_declared BOOLEAN NOT NULL,
    license_names TEXT[] NOT NULL,
    region_terms TEXT[] NOT NULL,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- NotDeclared(licenses_declared=false)인데 license_names 에 이름이 실리면 모순이다 —
    -- Declared(빈 목록)과 NotDeclared를 구조로도 가른다(왕복 test 만으로 끝내지 않는다).
    CHECK (licenses_declared OR license_names = '{}')
);

-- 싱글턴 upsert 관례(V9 GRANT와 같은 축) — DELETE·TRUNCATE 는 주지 않는다(보존·파기는 이
-- slice 밖, 위협 모델 「방어하지 않는다」③과 별개로 최소 권한 원칙을 그대로 따른다).
GRANT SELECT, INSERT, UPDATE ON operator_profile TO bidvector_app;
