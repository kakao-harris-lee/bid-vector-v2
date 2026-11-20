import type { ReactNode } from 'react';

import type { BidPriceRequestResponse } from '../api/queries';
import { AbsentValue } from '../components/AbsentValue';
import { cardClass } from '../components/Card';
import { StateBadge } from '../components/StateBadge';
import { KeyValueRow } from '../components/KeyValueRow';
import { displayOrUnknown } from '../display/stateDisplay';
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
  OUTCOME_DISPLAY,
  UNJUDGED_AXIS_DISPLAY,
} from '../display/worksheetDisplay';

/**
 * 절의 **요약 줄** 클래스 — 한 자리다. 다섯 절이 각자 적으면 그 가운데 하나가 44px 를 잃는다.
 *
 * 손가락이 누르는 자리라 최소 높이를 든다. 그 토큰은 **공유 컨트롤 표와 같아야** 하고 짝 test
 * 가 두 글자를 맞댄다(여기 적은 토큰이 그 표와 갈리면 붉는다). 상자로도 재려면 390px 여정이 이
 * 절을 **펼친 상태**로 그려야 하는데 그 fixture 가 없다 — 알려진 제한으로 등재한다.
 */
export const SECTION_SUMMARY_CLASS = 'flex min-h-11 cursor-pointer flex-wrap items-center gap-2 text-sm font-semibold';

/**
 * **펼침 표지**(cr M-3). 요약 줄의 display 를 `flex` 로 덮으면 브라우저 기본 마커(`list-item`)가
 * 그려지지 않는다 — 그러면 남는 표지가 커서 하나뿐이고, **이 화면의 축인 모바일 폭에는 커서가
 * 없다**. 그 상태의 요약은 「눌러서 펼치는 줄」이 아니라 「닫힌 카드」로 보인다.
 *
 * 그래서 표지를 직접 그린다. 접힘·펼침은 `<details>` 의 상태라 CSS 로 돌리고(`open` 선택자),
 * 보조기기는 네이티브 역할·확장 상태를 UA 에서 받으므로 이 표지는 **장식**이다(`aria-hidden`).
 */
function DisclosureMarker() {
  return (
    <span
      data-testid="disclosure-marker"
      aria-hidden="true"
      className="inline-block text-ink-soft transition-transform group-open:rotate-90"
    >
      ▸
    </span>
  );
}

/**
 * 점진적 공개의 한 절(계약 받는 항목 11) — **요약 한 줄 → 펼침**이다.
 *
 * 요약이 그 절의 **핵심 상태**를 든다: 접기가 사실을 숨기면 점진적 공개가 아니라 은폐다. 그래서
 * 펼치지 않고도 판정 · 3안 상태 · 면허 판정 · 하한율 상태를 읽을 수 있다.
 *
 * 열린 절을 **기억하지 않는다**. 기억하면 그것이 화면 밖의 숨은 설정이 되어 두 사람이 같은 자리를
 * 다르게 보고, 「접혀 있다」를 전제로 쓴 문면이 어긋난다.
 */
function DisclosureSection({
  testId,
  title,
  summary,
  children,
}: {
  readonly testId: string;
  readonly title: string;
  readonly summary: ReactNode;
  readonly children: ReactNode;
}) {
  return (
    <details data-testid={testId} className={`group ${cardClass()}`}>
      <summary data-testid={`${testId}-summary`} className={SECTION_SUMMARY_CLASS}>
        <DisclosureMarker />
        <span>{title}</span>
        {summary}
      </summary>
      <div className="mt-3">{children}</div>
    </details>
  );
}

/**
 * 투찰가 **3안 자리**(DEC-05 · DEC-02). 계약에 금액 칸이 없으므로 이 자리에 숫자가 들어올
 * 경로가 없다 — 상태와 사유만 보인다. 그것이 「근거 없는 숫자를 내지 않는다」의 구조다.
 */
function BidPriceMenu({ price }: { readonly price: BidPriceRequestResponse }) {
  const menu = displayOrUnknown(BID_PRICE_MENU_DISPLAY, price.bidPriceMenuState);
  return (
    <DisclosureSection testId="bid-price-menu" title="투찰가 3안" summary={<StateBadge display={menu} />}>
      {/* 제목에는 도메인 용어의 숫자가 들어간다(「3안」) — 그래서 **값 자리**를 따로 표시해
          두고 「이 자리에 숫자가 없다」를 그 자리에서만 잰다(test 가 제목을 함께 읽으면 그
          단언이 용어 때문에 붉어진다, 실측). */}
      <div data-testid="bid-price-menu-value">
        <p className="text-xs text-ink-soft">{menu.why}</p>
        <p className="mt-2 text-xs text-ink-soft">
          {displayOrUnknown(BID_PRICE_MENU_REASON_DISPLAY, price.bidPriceMenuNotComputedReason).label}
        </p>
      </div>
    </DisclosureSection>
  );
}

/**
 * 판정 — `outcome` 과 **어디서 왜 멈췄는가**(v L-4). 앞 판은 `OUTCOME_DISPLAY` 가 쓰이지 않는
 * 표였고 그 `why`(「아래 단계·사유가 말한다」)가 가리키는 칸이 화면에 없었다. 단계·사유는
 * 계약이 자유 문자열로 두므로 **서버가 준 코드를 그대로** 보인다(문면을 지어내지 않는다).
 */
function Verdict({ price }: { readonly price: BidPriceRequestResponse }) {
  return (
    <DisclosureSection
      testId="verdict"
      title="판정"
      summary={<StateBadge display={displayOrUnknown(OUTCOME_DISPLAY, price.outcome)} testId="verdict-outcome" />}
    >
      {price.verdict === null || price.verdict === undefined ? null : (
        <p data-testid="verdict-value" className="font-numeric text-xs">
          {price.verdict}
        </p>
      )}
      <dl className="grid gap-3 sm:grid-cols-2">
        <KeyValueRow label="멈춘 단계" testId="drop-stage" value={price.dropStage ?? null} />
        <KeyValueRow label="멈춘 사유" testId="drop-reason" value={price.dropReasonCode ?? null} />
        <KeyValueRow
          label="판정 사유 코드"
          testId="verdict-reasons"
          value={price.verdictReasonCodes.length === 0 ? null : price.verdictReasonCodes.join(', ')}
        />
        <KeyValueRow
          label="걸린 감시 규칙"
          testId="failed-watch-rules"
          value={price.failedWatchRuleIds.length === 0 ? null : price.failedWatchRuleIds.join(', ')}
        />
      </dl>
    </DisclosureSection>
  );
}

/** 근거 — 기초금액 · 게시 하한율 · 하한 미달 빈도 · 신뢰도 · 분석. 전부 상태에서 나온다. */
function Grounds({ price }: { readonly price: BidPriceRequestResponse }) {
  const floorOrigin = price.publishedFloorRateOrigin;
  const mlReason = price.mlUnavailableReason;
  return (
    <DisclosureSection
      testId="grounds-section"
      title="근거"
      // 요약에 **하한율 상태**를 둔다 — 투찰 금액을 정하는 자리에서 가장 먼저 보는 축이다.
      summary={<StateBadge display={displayOrUnknown(FLOOR_RATE_GROUNDS_DISPLAY, price.floorRateGroundsState)} />}
    >
      <dl className="grid gap-4 sm:grid-cols-2">
        <KeyValueRow
          label="기초금액"
          hint="투찰 기준 금액. 추정가격과 다르다."
          testId="base-amount"
          value={price.baseAmountWon === null || price.baseAmountWon === undefined ? null : `${price.baseAmountWon.toLocaleString('ko-KR')}원`}
        />
        <div className="flex flex-col gap-1">
          <dt className="text-xs text-ink-soft">기초금액이 정해진 방식</dt>
          <dd data-testid="base-amount-resolution">
            {price.baseAmountResolution === null || price.baseAmountResolution === undefined ? (
              <AbsentValue reason="notSent" className="text-sm" />
            ) : (
              <StateBadge display={displayOrUnknown(BASE_AMOUNT_RESOLUTION_DISPLAY, price.baseAmountResolution)} />
            )}
          </dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="text-xs text-ink-soft">게시 낙찰하한율</dt>
          <dd data-testid="floor-rate-grounds" className="flex flex-col gap-1">
            <StateBadge display={displayOrUnknown(FLOOR_RATE_GROUNDS_DISPLAY, price.floorRateGroundsState)} />
            {/* 값은 **원문 10진 문자열 그대로** 보인다 — 퍼센트로 고치거나 자리를 맞추지 않는다. */}
            {price.publishedFloorRateFraction === null || price.publishedFloorRateFraction === undefined ? null : (
              <span className="font-numeric text-sm">{price.publishedFloorRateFraction}</span>
            )}
            {floorOrigin === null || floorOrigin === undefined ? null : (
              <StateBadge display={displayOrUnknown(FLOOR_RATE_ORIGIN_DISPLAY, floorOrigin)} />
            )}
          </dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="text-xs text-ink-soft">하한 미달 빈도</dt>
          <dd data-testid="floor-shortfall" className="flex flex-col gap-1">
            <StateBadge display={displayOrUnknown(FLOOR_SHORTFALL_STATE_DISPLAY, price.floorShortfallState)} />
            <span className="text-xs text-ink-soft">
              {displayOrUnknown(FLOOR_SHORTFALL_REASON_DISPLAY, price.floorShortfallNotComputedReason).label}
            </span>
          </dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="text-xs text-ink-soft">신뢰도</dt>
          <dd data-testid="confidence" className="flex flex-col gap-1">
            <StateBadge display={displayOrUnknown(CONFIDENCE_STATE_DISPLAY, price.confidenceState)} />
            <span className="text-xs text-ink-soft">
              {displayOrUnknown(CONFIDENCE_REASON_DISPLAY, price.confidenceNotAvailableReason).label}
            </span>
          </dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="text-xs text-ink-soft">분석</dt>
          <dd data-testid="ml-analysis" className="flex flex-col gap-1">
            <StateBadge display={displayOrUnknown(ML_ANALYSIS_STATE_DISPLAY, price.mlAnalysisState)} />
            {mlReason === null || mlReason === undefined ? null : (
              <span className="text-xs text-ink-soft">
                {displayOrUnknown(ML_UNAVAILABLE_REASON_DISPLAY, mlReason).label}
              </span>
            )}
          </dd>
        </div>
      </dl>
      {/* CR H-1 — **주장한 값을 보인다**. 응답이 되돌려 준 선언값과 여력 상한을 나란히 두어,
          운영자가 「무엇이 주장됐는지」를 화면에서 확인할 수 있게 한다. */}
      <p data-testid="capacity" className="mt-3 text-xs text-ink-soft">
        여력: 진행 중 <span className="font-numeric">{price.currentActiveBids}</span>건으로{' '}
        <strong>선언</strong>함 · 상한 <span className="font-numeric">{price.maxActiveBids}</span>
      </p>
      <p data-testid="inbox-record" className="mt-3 text-xs text-ink-soft">
        알림함: {displayOrUnknown(INBOX_RECORD_STATE_DISPLAY, price.inboxRecordState).label}
        {price.inboxRecordNotRecordedReason === null || price.inboxRecordNotRecordedReason === undefined
          ? ''
          : ` — ${displayOrUnknown(INBOX_RECORD_REASON_DISPLAY, price.inboxRecordNotRecordedReason).label}`}
      </p>
    </DisclosureSection>
  );
}

/**
 * 규칙 판본의 **사용자 문면**(팀장 판정 2026-11-18 · cr N-2 로 갈래가 셋이 됐다).
 *
 * 계약이 이 칸을 **optional 이면서 nullable** 로 둔다. 그 둘은 **다른 사실**이다 —
 * `null` 은 「시행일이라는 값 자체가 없다」(= 최초 판)이고, **부재**는 「서버가 그 칸을 보내지
 * 않았다」(= 판본을 모른다)다. 부재를 최초 판으로 접으면 화면이 **도메인 사실을 단정**하게
 * 되고, 그 자리는 재현 축(QUAL-05)이라 틀린 판본을 믿는 비용이 작지 않다.
 *
 * 서버가 사용자용 판본 이름을 주지 않아 날짜로 말한다(그 표면 부재는 새 OPEN 후보다).
 */
function policyVersionTextOf(effectiveFrom: string | null | undefined): string {
  if (effectiveFrom === undefined) {
    return '판본을 받지 못했습니다';
  }
  return effectiveFrom === null ? '최초 판(시행일 없음)' : `${effectiveFrom} 시행 판`;
}

/** 자격 — **면허 축만** 판정한다. 판정하지 않는 축을 함께 싣는 것이 이 카드의 요점이다. */
function Qualification({ price }: { readonly price: BidPriceRequestResponse }) {
  const reason = price.licenseUncertainReason;
  return (
    <DisclosureSection
      testId="qualification"
      title="자격"
      // 요약에 **면허 판정**을 둔다 — 「판정 불가」가 「미달」로 읽히지 않는 것이 이 카드의 축이고,
      // 접힌 상태에서도 그 사실이 보여야 한다.
      summary={
        <StateBadge display={displayOrUnknown(LICENSE_VERDICT_DISPLAY, price.licenseVerdict)} testId="license-verdict" />
      }
    >
      <p className="flex flex-wrap items-center gap-2">
        {reason === null || reason === undefined ? null : (
          <StateBadge display={displayOrUnknown(LICENSE_UNCERTAIN_REASON_DISPLAY, reason)} testId="license-reason" />
        )}
        <StateBadge display={displayOrUnknown(LICENSE_VALIDITY_DISPLAY, price.licenseValidity)} testId="license-validity" />
      </p>
      <dl className="mt-3 grid gap-3 sm:grid-cols-2">
        <KeyValueRow
          label="요건이 온 필드"
          testId="license-source-fields"
          value={price.licenseRequirementSourceFields.length === 0 ? null : price.licenseRequirementSourceFields.join(', ')}
        />
        <KeyValueRow
          label="공고가 요구한 면허"
          testId="license-required"
          value={
            price.licenseRequiredLicenses === null || price.licenseRequiredLicenses === undefined || price.licenseRequiredLicenses.length === 0
              ? null
              : price.licenseRequiredLicenses.join(', ')
          }
        />
        <KeyValueRow
          label="충족한 제한그룹"
          testId="license-groups-satisfied"
          value={price.licenseGroupsSatisfied.length === 0 ? null : price.licenseGroupsSatisfied.join(', ')}
        />
        <KeyValueRow
          label="미충족 제한그룹"
          testId="license-groups-missing"
          value={price.licenseGroupsMissing.length === 0 ? null : price.licenseGroupsMissing.join(', ')}
        />
        {/* **원문을 그리지 않는다**(팀장 판정 2026-11-18). 서버의 출처 문자열은 QUAL-05 의
            **재현용 값**이라 개발 식별자를 담는다 — 그것은 사용자 문면이 아니다. 운영자가 알아야
            하는 것은 **어느 판으로 판정했는가**이고, 그 사실은 시행일이 말한다. 원문은 API 와
            서버에 그대로 남으므로 재현성은 잃지 않는다. */}
        <KeyValueRow
          label="자격 판정에 쓴 규칙 판본"
          hint="면허 판정에 쓴 규칙표가 어느 판인지. 규칙이 바뀌어도 지난 판정을 그 판으로 되짚을 수 있다."
          testId="license-policy"
          value={policyVersionTextOf(price.licensePolicyEffectiveFrom)}
        />
        <KeyValueRow label="읽지 못한 요건 행" testId="license-unparsable" value={`${price.licenseUnparsableRowCount}`} />
      </dl>
      <p className="mt-2">
        <StateBadge display={displayOrUnknown(LICENSE_MISSING_DETAIL_DISPLAY, price.licenseMissingDetailState)} />
      </p>
      <h4 className="mt-4 text-xs font-semibold text-ink-soft">아직 판정하지 않는 축</h4>
      <p data-testid="unjudged-axes" className="mt-1 flex flex-wrap gap-1">
        {price.unjudgedAxes.map((axis) => (
          <StateBadge key={axis} display={displayOrUnknown(UNJUDGED_AXIS_DISPLAY, axis)} />
        ))}
      </p>
    </DisclosureSection>
  );
}

/** 고지 — **계약이 준 코드 전수**를 문장으로 보인다(문장은 표면이 짓는다). */
function Disclosures({ price }: { readonly price: BidPriceRequestResponse }) {
  return (
    <DisclosureSection
      testId="disclosures-section"
      title="고지"
      // 요약은 **건수**다 — 몇 줄이 접혀 있는지 모르면 접힌 자리가 비어 보인다.
      summary={<span className="text-xs font-normal text-ink-soft">{price.disclosures.length}건</span>}
    >
      <ul data-testid="disclosures" className="flex list-inside list-disc flex-col gap-1 text-xs text-ink-soft">
        {/* CR r2 M-6 — 직접 색인이 남아 있었다. 표 밖 코드에 `undefined.label` 로 터지면 오류
            경계가 **근거 패널 전체**를 먹는다 — 보이는 미지 상태가 조용한 폭발보다 낫다. */}
        {price.disclosures.map((code) => (
          <li key={code}>{displayOrUnknown(DISCLOSURE_SENTENCE, code).label}</li>
        ))}
      </ul>
    </DisclosureSection>
  );
}

/** 투찰가 요청의 결과 전부 — 판정 · 3안 자리 · 자격 · 근거 · 고지. 다섯 절이 접혀서 선다. */
export function WorksheetGrounds({ price }: { readonly price: BidPriceRequestResponse }) {
  return (
    <div className="flex flex-col gap-4">
      <Verdict price={price} />
      <BidPriceMenu price={price} />
      <Qualification price={price} />
      <Grounds price={price} />
      <Disclosures price={price} />
    </div>
  );
}
