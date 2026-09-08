-- M3/3E verifier r1 H-1(high) 뒤 신설 — canonical 왕복에서 provenance 가 조용히 지어지고
-- 권위가 오르는 결함을 닫는다. `V4` 는 곧 적용 이력이 생기므로 고치지 않는다(신규 파일).
--
-- 결함: `JdbcOpeningResultRepository.toOpeningResult` 가 `final_award_amount`·`planned_price`·
-- `opening_base_amount` 셋을 읽을 때 `Provenance.Published(id.round)` 상수를 씌웠다 — `V4` 가
-- 이 셋에 provenance 컬럼을 두지 않아 저장된 값(예: `Undeclared`, 비권위)이 왕복 한 번으로
-- `Published`(권위)가 됐다. `notice` 표의 기존 관례(`ProvenanceCodec`, kind+detail 두 컬럼)를
-- 그대로 적용해 저장한 것을 그대로 복원한다.
--
-- 권위 가드(`guard_authoritative_slot`, `provenance_authority` 참조) 적용 여부는 3D 설계
-- 변경이라 이 slice에서 열지 않는다 — 가드 관례는 `guard_existence_and_freshness` 현행
-- 유지, provenance 축은 기존 트리거의 companion 컬럼으로만 추가한다(존재+신선도만 진다,
-- 권위 승격/강등 판정은 하지 않는다 — 알려진 제한, checklist 「판단이 갈린 지점」).

ALTER TABLE opening_result
    ADD COLUMN final_award_amount_provenance TEXT,
    ADD COLUMN final_award_amount_provenance_detail TEXT,
    ADD COLUMN planned_price_provenance TEXT,
    ADD COLUMN planned_price_provenance_detail TEXT,
    ADD COLUMN opening_base_amount_provenance TEXT,
    ADD COLUMN opening_base_amount_provenance_detail TEXT,
    ADD CONSTRAINT opening_result_final_award_amount_provenance_pair
        CHECK ((final_award_amount_won IS NULL) = (final_award_amount_provenance IS NULL)),
    ADD CONSTRAINT opening_result_planned_price_provenance_pair
        CHECK ((planned_price_won IS NULL) = (planned_price_provenance IS NULL)),
    ADD CONSTRAINT opening_result_opening_base_amount_provenance_pair
        CHECK ((opening_base_amount_won IS NULL) = (opening_base_amount_provenance IS NULL));

-- 기존 트리거(V4)를 같은 이름으로 재정의한다 — TG_ARGV 목록에 새 provenance 축을 더한다.
-- (값·currency·vat 가 그대로여도 provenance만 바뀌면 axis_changed 로 잡혀 신선도 가드가
-- 새 관측을 요구한다 — floor_rate_origin_kind/_detail 과 같은 패턴, 3D P-1 관례.)
DROP TRIGGER guard_opening_result_final_award_amount ON opening_result;
CREATE TRIGGER guard_opening_result_final_award_amount
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness(
        'final_award_amount_won', 'final_award_amount_currency',
        'final_award_amount_provenance', 'final_award_amount_provenance_detail');

DROP TRIGGER guard_opening_result_planned_price ON opening_result;
CREATE TRIGGER guard_opening_result_planned_price
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness(
        'planned_price_won', 'planned_price_currency',
        'planned_price_provenance', 'planned_price_provenance_detail');

DROP TRIGGER guard_opening_result_opening_base_amount ON opening_result;
CREATE TRIGGER guard_opening_result_opening_base_amount
    BEFORE UPDATE ON opening_result
    FOR EACH ROW EXECUTE FUNCTION guard_existence_and_freshness(
        'opening_base_amount_won', 'opening_base_amount_currency', 'opening_base_amount_vat',
        'opening_base_amount_provenance', 'opening_base_amount_provenance_detail');
