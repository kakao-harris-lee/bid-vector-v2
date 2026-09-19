-- M6/6F-5-a D-6F5-2·D-6F5-4 — 자격 요건 영속. 공고별 수집 상태를 담는 헤더
-- (notice_requirement)와 그 아래 행(notice_requirement_row) 둘로 가른다. 도메인
-- `RequirementCollection`의 세 갈래(DataAbsent·CollectionFailed·Collected)를 표가 잃지
-- 않게 옮긴다: 헤더 행이 없으면 DataAbsent, `status='FAILED'`면 CollectionFailed(이
-- 상태에서는 자식 행이 없다), `status='COLLECTED'`면 Collected(자식 행 0개 이상). 「수집을
-- 시도했으나 실패했다」와 「시도 자체가 없다」를 구분하는 것이 이 표의 존재 이유다
-- (scope.md 우회 1의 방어).
--
-- 이 slice는 담을 자리만 만든다 — 채우는 경로(추출 결과 저장)는 6F-5-b
-- (OPEN-6F5-EXTRACTION-FILL). 보존·파기 정책은 이 slice 밖(OPEN-6F5A-RETENTION, 6B-3) —
-- 삭제·갱신 트리거를 두지 않는다.
--
-- `notice(notice_number, notice_round)`로의 FK는 두지 않는다 — `opening_result`·
-- `qualification_text` 관례(이 저장소는 위성 표에서 notice FK를 쓰지 않는다, 실측:
-- `CleanMigrationTest` 축6 FOREIGN KEY 목록에 그런 FK가 없다)를 그대로 따른다.

CREATE TABLE notice_requirement (
    notice_number TEXT NOT NULL,
    notice_round TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('FAILED', 'COLLECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (notice_number, notice_round)
);

-- `RequirementRow`(Parsed·Unparsable) 왕복 — 두 variant 모두 `serialNo`(LmtSno)를 갖고 그것이
-- 자연 키다(R-QUAL-05, 제로패딩 보존을 위해 TEXT). PARSED만 `groupNo`(그 안에서도 nullable,
-- U-8 Ungrouped)·`sourceField`·`licenseNames`를 채운다 — UNPARSABLE은 도메인 타입 자체에 그
-- 필드들이 없으므로 저장도 비운다(값을 지어내지 않는다, D-6F5-2와 같은 원칙).
CREATE TABLE notice_requirement_row (
    notice_number TEXT NOT NULL,
    notice_round TEXT NOT NULL,
    serial_no TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('PARSED', 'UNPARSABLE')),
    group_no TEXT,
    source_field TEXT CHECK (source_field IS NULL OR source_field IN ('LcnsLmtNm', 'PermsnIndstrytyList')),
    license_names TEXT[],
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (notice_number, notice_round, serial_no),
    FOREIGN KEY (notice_number, notice_round)
        REFERENCES notice_requirement (notice_number, notice_round) ON DELETE CASCADE,
    -- `RequirementRow.Parsed`는 `sourceField`·`licenseNames`를 항상 갖고(비-nullable 필드),
    -- `Unparsable`은 둘 다 없다 — kind와 존재 여부를 짝짓는다(RequirementRow.kt sealed 정의와
    -- 같은 불변식, 우회 1 방어 심층).
    CHECK ((kind = 'PARSED') = (source_field IS NOT NULL)),
    CHECK ((kind = 'PARSED') = (license_names IS NOT NULL)),
    -- `Unparsable`은 `groupNo` 필드 자체가 없다 — 값을 지어내지 않는다.
    CHECK (kind = 'PARSED' OR group_no IS NULL),
    -- `RequirementRow.Parsed.licenseNames`는 비어 있을 수 없다(qualification 모듈 init
    -- 블록 불변식, verifier r1 F-2) — 저장 시점에도 같은 방어를 심층으로 둔다.
    CHECK (license_names IS NULL OR cardinality(license_names) > 0)
);

-- GRANT — 헤더는 upsert + 리셋(SELECT·INSERT·UPDATE·DELETE, DELETE는 DataAbsent로 되돌리는
-- save() 경로가 쓴다). 행은 delete-then-insert(SELECT·INSERT·DELETE, UPDATE 없음 — save()가
-- 행을 갱신하지 않고 항상 통째로 교체한다).
GRANT SELECT, INSERT, UPDATE, DELETE ON notice_requirement TO bidvector_app;
GRANT SELECT, INSERT, DELETE ON notice_requirement_row TO bidvector_app;
