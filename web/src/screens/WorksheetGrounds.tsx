import type { BidPriceRequestResponse } from '../api/queries';
import { Field, StateBadge } from '../components/StateBadge';
import { displayOf } from '../display/stateDisplay';
import {
  BASE_AMOUNT_RESOLUTION_DISPLAY,
  BID_PRICE_MENU_DISPLAY,
  BID_PRICE_MENU_REASON_DISPLAY,
  CONFIDENCE_REASON_DISPLAY,
  CONFIDENCE_STATE_DISPLAY,
  DISCLOSURE_SENTENCE,
  FLOOR_RATE_GROUNDS_DISPLAY,
  FLOOR_RATE_ORIGIN_DISPLAY,
  FLOOR_SHORTFALL_REASON_DISPLAY,
  FLOOR_SHORTFALL_STATE_DISPLAY,
  INBOX_RECORD_REASON_DISPLAY,
  INBOX_RECORD_STATE_DISPLAY,
  LICENSE_MISSING_DETAIL_DISPLAY,
  LICENSE_UNCERTAIN_REASON_DISPLAY,
  LICENSE_VALIDITY_DISPLAY,
  LICENSE_VERDICT_DISPLAY,
  ML_ANALYSIS_STATE_DISPLAY,
  ML_UNAVAILABLE_REASON_DISPLAY,
  UNJUDGED_AXIS_DISPLAY,
} from '../display/worksheetDisplay';

/**
 * 투찰가 **3안 자리**(DEC-05 · DEC-02). 계약에 금액 칸이 없으므로 이 자리에 숫자가 들어올
 * 경로가 없다 — 상태와 사유만 보인다. 그것이 「근거 없는 숫자를 내지 않는다」의 구조다.
 */
function BidPriceMenu({ price }: { readonly price: BidPriceRequestResponse }) {
  return (
    <section data-testid="bid-price-menu" className="rounded-lg border border-line bg-card p-4">
      {/* 제목에는 도메인 용어의 숫자가 들어간다(「3안」) — 그래서 **값 자리**를 따로 표시해
          두고 「이 자리에 숫자가 없다」를 그 자리에서만 잰다(test 가 제목을 함께 읽으면 그
          단언이 용어 때문에 붉어진다, 실측). */}
      <h3 className="text-sm font-semibold">투찰가 3안</h3>
      <div data-testid="bid-price-menu-value">
        <p className="mt-2">
          <StateBadge display={displayOf(BID_PRICE_MENU_DISPLAY, price.bidPriceMenuState)} />
        </p>
        <p className="mt-1 text-xs text-ink-soft">
          {displayOf(BID_PRICE_MENU_DISPLAY, price.bidPriceMenuState).why}
        </p>
        <p className="mt-2 text-xs text-ink-soft">
          {displayOf(BID_PRICE_MENU_REASON_DISPLAY, price.bidPriceMenuNotComputedReason).label}
        </p>
      </div>
    </section>
  );
}

/** 근거 — 기초금액 · 게시 하한율 · 하한 미달 빈도 · 신뢰도 · 분석. 전부 상태에서 나온다. */
function Grounds({ price }: { readonly price: BidPriceRequestResponse }) {
  const floorOrigin = price.publishedFloorRateOrigin;
  const mlReason = price.mlUnavailableReason;
  return (
    <section className="rounded-lg border border-line bg-card p-4">
      <h3 className="text-sm font-semibold">근거</h3>
      <dl className="mt-3 grid gap-4 sm:grid-cols-2">
        <Field
          label="기초금액"
          hint="투찰 기준 금액. 추정가격과 다르다."
          testId="base-amount"
          value={price.baseAmountWon === null || price.baseAmountWon === undefined ? null : `${price.baseAmountWon.toLocaleString('ko-KR')}원`}
        />
        <div className="flex flex-col gap-1">
          <dt className="text-xs text-ink-soft">기초금액이 정해진 방식</dt>
          <dd data-testid="base-amount-resolution">
            {price.baseAmountResolution === null || price.baseAmountResolution === undefined ? (
              <span className="text-sm text-absent-ink">값 없음</span>
            ) : (
              <StateBadge display={displayOf(BASE_AMOUNT_RESOLUTION_DISPLAY, price.baseAmountResolution)} />
            )}
          </dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="text-xs text-ink-soft">게시 낙찰하한율</dt>
          <dd data-testid="floor-rate-grounds" className="flex flex-col gap-1">
            <StateBadge display={displayOf(FLOOR_RATE_GROUNDS_DISPLAY, price.floorRateGroundsState)} />
            {/* 값은 **원문 10진 문자열 그대로** 보인다 — 퍼센트로 고치거나 자리를 맞추지 않는다. */}
            {price.publishedFloorRateFraction === null || price.publishedFloorRateFraction === undefined ? null : (
              <span className="font-numeric text-sm">{price.publishedFloorRateFraction}</span>
            )}
            {floorOrigin === null || floorOrigin === undefined ? null : (
              <StateBadge display={displayOf(FLOOR_RATE_ORIGIN_DISPLAY, floorOrigin)} />
            )}
          </dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="text-xs text-ink-soft">하한 미달 빈도</dt>
          <dd data-testid="floor-shortfall" className="flex flex-col gap-1">
            <StateBadge display={displayOf(FLOOR_SHORTFALL_STATE_DISPLAY, price.floorShortfallState)} />
            <span className="text-xs text-ink-soft">
              {displayOf(FLOOR_SHORTFALL_REASON_DISPLAY, price.floorShortfallNotComputedReason).label}
            </span>
          </dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="text-xs text-ink-soft">신뢰도</dt>
          <dd data-testid="confidence" className="flex flex-col gap-1">
            <StateBadge display={displayOf(CONFIDENCE_STATE_DISPLAY, price.confidenceState)} />
            <span className="text-xs text-ink-soft">
              {displayOf(CONFIDENCE_REASON_DISPLAY, price.confidenceNotAvailableReason).label}
            </span>
          </dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="text-xs text-ink-soft">분석</dt>
          <dd data-testid="ml-analysis" className="flex flex-col gap-1">
            <StateBadge display={displayOf(ML_ANALYSIS_STATE_DISPLAY, price.mlAnalysisState)} />
            {mlReason === null || mlReason === undefined ? null : (
              <span className="text-xs text-ink-soft">
                {displayOf(ML_UNAVAILABLE_REASON_DISPLAY, mlReason).label}
              </span>
            )}
          </dd>
        </div>
      </dl>
      <p data-testid="inbox-record" className="mt-3 text-xs text-ink-soft">
        알림함: {displayOf(INBOX_RECORD_STATE_DISPLAY, price.inboxRecordState).label}
        {price.inboxRecordNotRecordedReason === null || price.inboxRecordNotRecordedReason === undefined
          ? ''
          : ` — ${displayOf(INBOX_RECORD_REASON_DISPLAY, price.inboxRecordNotRecordedReason).label}`}
      </p>
    </section>
  );
}

/** 자격 — **면허 축만** 판정한다. 판정하지 않는 축을 함께 싣는 것이 이 카드의 요점이다. */
function Qualification({ price }: { readonly price: BidPriceRequestResponse }) {
  const reason = price.licenseUncertainReason;
  return (
    <section data-testid="qualification" className="rounded-lg border border-line bg-card p-4">
      <h3 className="text-sm font-semibold">자격</h3>
      <p className="mt-2 flex flex-wrap items-center gap-2">
        <StateBadge display={displayOf(LICENSE_VERDICT_DISPLAY, price.licenseVerdict)} testId="license-verdict" />
        {reason === null || reason === undefined ? null : (
          <StateBadge display={displayOf(LICENSE_UNCERTAIN_REASON_DISPLAY, reason)} testId="license-reason" />
        )}
        <StateBadge display={displayOf(LICENSE_VALIDITY_DISPLAY, price.licenseValidity)} testId="license-validity" />
      </p>
      <dl className="mt-3 grid gap-3 sm:grid-cols-2">
        <Field
          label="요건이 온 필드"
          testId="license-source-fields"
          value={price.licenseRequirementSourceFields.length === 0 ? null : price.licenseRequirementSourceFields.join(', ')}
        />
        <Field
          label="공고가 요구한 면허"
          testId="license-required"
          value={
            price.licenseRequiredLicenses === null || price.licenseRequiredLicenses === undefined || price.licenseRequiredLicenses.length === 0
              ? null
              : price.licenseRequiredLicenses.join(', ')
          }
        />
        <Field
          label="충족한 제한그룹"
          testId="license-groups-satisfied"
          value={price.licenseGroupsSatisfied.length === 0 ? null : price.licenseGroupsSatisfied.join(', ')}
        />
        <Field
          label="미충족 제한그룹"
          testId="license-groups-missing"
          value={price.licenseGroupsMissing.length === 0 ? null : price.licenseGroupsMissing.join(', ')}
        />
        <Field label="판정 어휘 version" testId="license-policy" value={price.licensePolicySource} />
        <Field
          label="읽지 못한 요건 행"
          testId="license-unparsable"
          value={`${price.licenseUnparsableRowCount}`}
        />
      </dl>
      <p className="mt-2">
        <StateBadge display={displayOf(LICENSE_MISSING_DETAIL_DISPLAY, price.licenseMissingDetailState)} />
      </p>
      <h4 className="mt-4 text-xs font-semibold text-ink-soft">아직 판정하지 않는 축</h4>
      <p data-testid="unjudged-axes" className="mt-1 flex flex-wrap gap-1">
        {price.unjudgedAxes.map((axis) => (
          <StateBadge key={axis} display={displayOf(UNJUDGED_AXIS_DISPLAY, axis)} />
        ))}
      </p>
    </section>
  );
}

/** 고지 — **계약이 준 코드 전수**를 문장으로 보인다(문장은 표면이 짓는다). */
function Disclosures({ price }: { readonly price: BidPriceRequestResponse }) {
  return (
    <section className="rounded-lg border border-line bg-card p-4">
      <h3 className="text-sm font-semibold">고지</h3>
      <ul data-testid="disclosures" className="mt-2 flex list-inside list-disc flex-col gap-1 text-xs text-ink-soft">
        {price.disclosures.map((code) => (
          <li key={code}>{DISCLOSURE_SENTENCE[code].label}</li>
        ))}
      </ul>
    </section>
  );
}

/** 투찰가 요청의 결과 전부 — 3안 자리 · 근거 · 자격 · 고지. */
export function WorksheetGrounds({ price }: { readonly price: BidPriceRequestResponse }) {
  return (
    <div className="flex flex-col gap-4">
      <BidPriceMenu price={price} />
      <Qualification price={price} />
      <Grounds price={price} />
      <Disclosures price={price} />
    </div>
  );
}
