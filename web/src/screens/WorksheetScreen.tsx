import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState } from 'react';
import { useParams } from 'react-router';

import {
  addBidCase,
  deferCase,
  draftBid,
  listBidCases,
  readTimeline,
  recordBid,
  requestBidPrice,
  resumeCase,
  searchNotices,
  skipCase,
  type BidCase,
  type BidPriceRequestResponse,
} from '../api/queries';
import { bidCasesQueryKey } from '../api/queryKeys';
import { AbsentValue } from '../components/AbsentValue';
import { Button, LinkButton } from '../components/Button';
import { cardClass } from '../components/Card';
import { PageHeader } from '../components/PageHeader';
import { EmptyFace, ErrorFace, Skeleton } from '../components/ScreenFaces';
import { StateBadge } from '../components/StateBadge';
import { KeyValueRow } from '../components/KeyValueRow';
import { ToastHost, useToast } from '../components/Toast';
import { TwoColumn } from '../components/TwoColumn';
import { isUnauthorized, SignedOutFace, WriteFailureFace } from '../components/WriteFailure';
import { exitsOf, type BidBoxAction, type ExitActionKind } from '../display/bidBoxDisplay';
import { NEXT_CHECKPOINT_DISPLAY, nextActionOf, type NextActionKind } from '../display/caseDisplay';
import { floorMarginOf, type FloorMargin } from '../display/floorMargin';
import { koreanAmountTextOf } from '../display/moneyDisplay';
import { amountDisplayOf, noticeStatusDisplayOf } from '../display/noticeDisplay';
import { displayOrUnknown } from '../display/stateDisplay';
import { dayCountText, daysUntil, instantDisplayOf, localInputToIso, readClock } from '../display/timeDisplay';
import { useSessionCsrf } from '../session/useSessionCsrf';

import { ActionForm, type ActionPayload } from './BidBoxCard';
import { InstantText } from './InstantText';
import { WorksheetGrounds, WorksheetQualification } from './WorksheetGrounds';
import {
  ActiveBidsDeclaration,
  BidAmountFields,
  EMPTY_BID_FORM,
  KonepsHandoff,
  parseCount,
  parseDrawNumbers,
  parseWon,
  type BidAmountForm,
  type VatTreatment,
} from './WorksheetRecord';
import { CaseStateBadge, WorksheetTimeline } from './WorksheetTimeline';

const BID_KIND: BidCase['bidKind'] = 'REAL';

/**
 * 내려놓기 하나를 보낸다 — **열린 종류 전수의 `switch`** 다(`ExitActionKind` 가 13D 의 표에서
 * 유도된 타입이라, 그 표에 종류가 늘면 `never` 단언이 **여기서 컴파일을 멈춘다**).
 *
 * 쿼리도 표도 13D 의 것을 그대로 쓴다 — 이 함수가 더하는 것은 「워크시트의 두 축을 그 쿼리에
 * 잇는다」뿐이다. `datetime-local` 값의 변환 자리는 저장소에 하나(`timeDisplay`)이고, 읽히지
 * 않으면 **보내지 않는다**(쓰기 실패 얼굴이 받는다 — 오류 경계로 보내지 않는다).
 */
function sendExit(caseId: number, payload: { readonly kind: ExitActionKind; readonly value: string }): Promise<BidCase> {
  switch (payload.kind) {
    case 'DEFER': {
      const iso = localInputToIso(payload.value);
      if (iso === null) {
        throw new Error('시각을 읽지 못했다 — 날짜와 시각을 다시 적으십시오.');
      }
      return deferCase(caseId, { remindAt: iso });
    }
    case 'SKIP':
      // 사유는 **적힌 그대로** 싣는다 — 다듬으면 기록에 남는 말이 운영자가 본 말과 달라진다.
      // 빈 값·상한 초과는 폼이 보내기 전에 거른다(13D 의 표시 층 판정).
      return skipCase(caseId, { reason: payload.value });
    default: {
      const unreachable: never = payload.kind;
      throw new Error(`모르는 내려놓기 종류: ${JSON.stringify(unreachable)}`);
    }
  }
}

/**
 * 다음 행동 하나가 **어느 카드에 서는가**(M14/14C · 와이어프레임의 자리).
 *
 * 앞 판은 전부 머리에 섰다. 와이어프레임은 그 버튼을 **그 행동이 사는 카드**에 둔다 —
 * 「투찰가 요청」·「이 금액으로 진행」은 투찰가 카드, 「제출함」은 기록 카드다. 버튼은 그대로
 * 하나이고(§6.5) 자리만 상태를 따른다.
 *
 * **전수 표**다: 다음 행동 종류가 늘면 `satisfies` 가 여기서 컴파일을 멈춘다 — 새 종류가
 * 조용히 머리로 떨어지지 않는다.
 */
const PRIMARY_SLOT = {
  REQUEST_PRICE: 'price',
  DRAFT_BID: 'price',
  RECORD_BID: 'record',
  RESUME: 'head',
} as const satisfies Record<NextActionKind, 'head' | 'price' | 'record'>;

/**
 * **받은 투찰가 응답이 그 화면에서만 사는 사실**(v H-1 high · cr D14 low).
 *
 * 계약에 응답을 **다시 읽는 operation 이 없고**(`requestBidPrice` 하나다), 상태를 민 뒤에는
 * 요청 경로도 없다. 그래서 초안을 만든 날과 나라장터에 내는 날이 다르면 — 그것이 보통
 * 흐름이다 — 다시 연 화면에는 자격도 하한 여유도 없다. 앞 판은 그 자리를 **빈칸으로 접었고**,
 * 운영자는 「여유 표시가 없다」를 「문제없다」로 읽는다(해를 입는 자리가 투찰 금액이다).
 *
 * 그래서 빈 자리마다 **사유**를 세운다. 사유는 **다음 전이의 표에서 유도한다** — 상태 집합을
 * 이 화면이 한 벌 더 적지 않고, 상태가 늘면 `satisfies` 가 여기서 멈춘다.
 *
 * **전제 한 문장을 세 자리가 나눠 쓴다.** 자격 · 3안 · 하한 여유는 **서로 다른 빈 자리**이고
 * 각자 자기 축을 말해야 한다(「곁의 카드를 보라」는 폭이 바뀌면 거짓이 되는 꼴이다 — 같은
 * 리뷰가 그 꼴을 둘 지적했다). 전제가 겹치는 것은 그 셋이 한 원인을 갖기 때문이다.
 */
/**
 * **주어가 「받은 응답」이 아니다**(cr r1 LOW · v F5). 앞 판은 「**받은** 투찰가 응답은 …」이라
 * 적어, 응답을 한 번도 받은 적 없는 건(담아 두고 바로 「나중에」로 내려놓은 건)에서도 받았다고
 * 전제했다. 이것은 **그 표면의 성질**을 말하는 문장이라 어느 건에서나 참이다.
 */
const PRICE_ONLY_IN_SESSION = '투찰가 응답은 받은 화면에서만 보입니다';

/**
 * 투찰가를 물을 길이 **이 화면에 없을 때** — 주어가 **화면**이다(아래 표의 KDoc).
 *
 * **「다시」를 쓰지 않는다**(r2 v L1): 그 낱말은 **전에 한 번 쟀다**를 전제한다. 관심에서 바로
 * 건너뛴 건이나 마감이 지난 건은 응답을 받은 적이 없고, 화면은 **그 사실을 알 수 없다**
 * (`BidCase` 에 「응답을 받았다」는 칸이 없고, 초안이 있다는 것도 응답을 받았다는 뜻은 아니다).
 * 그래서 **받았든 아니든 참인 말**로 적는다.
 *
 * **수출한다**(r2 cr LOW): 렌더 층 문면 게이트가 이 사유를 모집단에 넣으려면 글자를 먹여야
 * 하는데, 그 글자를 손으로 적으면 **사본**이 된다(이 자리가 바뀌어도 게이트는 옛 글자를 본다).
 * 조립자를 공유하면 그 갈림이 생기지 않는다.
 */
export const cannotAskHere = (axis: string): string =>
  `${PRICE_ONLY_IN_SESSION} — 이 화면에서는 투찰가를 요청할 수 없어 ${axis}.`;

/** 다음 걸음이 아예 없는 상태 — 요청을 **하지 않는** 것이지 못 하는 것이 아니다. */
const settled = (axis: string): string =>
  `${PRICE_ONLY_IN_SESSION} — 이 상태에서는 투찰가를 요청하지 않으므로 ${axis}.`;

/**
 * 응답이 손에 없을 때 **빈 자리 셋이 각자 드는 사유**(v H-1 · cr D14 · cr r1 HIGH).
 *
 * **세 자리가 한 표에서 나온다.** 앞 판은 자격 축만 다음 행동별 표를 쓰고 나머지 둘은 한 문장을
 * 공유했다 — 그래서 「나중에」 건의 한 화면에서 윗줄이 「되돌린 뒤 요청하면 온다」고 하고 아랫줄
 * 둘이 「다시 요청할 수 없다」고 **서로를 부정했다**(cr r1 HIGH 의 실측). 세 자리가 한 원인을
 * 가지므로 **한 표**가 맞다.
 *
 * **주어를 화면으로 낮췄다.** 「이 **상태에서는** 요청할 수 없어」는 거짓일 수 있다 —
 * 「나중에 → 다시 보기」 두 걸음이 요청 경로로 데려가는 상태가 있다. 「이 **화면에서는**」은 네
 * 갈래 전부에서 참이다: 그 상태의 화면에 투찰가 요청이 없다는 말이고, 그것이 운영자가 지금
 * 보는 사실이다.
 *
 * **`null` 은 「이 자리에 사유를 두지 않는다」**이고 그 뜻은 둘 가운데 하나다 — 아직 묻지
 * 않았거나(요청 버튼이 이 화면에 있다), 한 걸음 뒤에 물을 수 있다(「다시 보기」가 이 화면에
 * 있다). 두 경우 모두 **자격 자리만** 다음 걸음을 말하고 나머지 둘은 자리 자체를 만들지 않는다 —
 * 묻지 않은 축을 「재지 못함」으로 그리면 화면이 묻지 않은 것에 답한다(13C 의 판단).
 */
interface PriceAbsenceCopy {
  readonly qualification: string;
  readonly price: string | null;
  readonly floorMargin: string | null;
}

const PRICE_ABSENCE = {
  REQUEST_PRICE: {
    qualification: '투찰가를 요청하면 자격 판정이 함께 옵니다.',
    price: null,
    floorMargin: null,
  },
  // 「다시 보기」가 **이 화면의 다음 행동**이다 — 한 번 누르면 요청 경로가 열린다.
  RESUME: {
    qualification: '다시 보기로 되돌린 뒤 투찰가를 요청하면 자격 판정이 함께 옵니다.',
    price: null,
    floorMargin: null,
  },
  DRAFT_BID: {
    qualification: cannotAskHere('자격 판정을 잴 수 없습니다'),
    price: cannotAskHere('투찰가 3안과 그 근거를 볼 수 없습니다'),
    floorMargin: cannotAskHere('하한 대비 여유를 잴 수 없습니다'),
  },
  RECORD_BID: {
    qualification: cannotAskHere('자격 판정을 잴 수 없습니다'),
    price: cannotAskHere('투찰가 3안과 그 근거를 볼 수 없습니다'),
    floorMargin: cannotAskHere('하한 대비 여유를 잴 수 없습니다'),
  },
} as const satisfies Record<NextActionKind, PriceAbsenceCopy>;

/**
 * 다음 전이가 **없는** 상태의 같은 셋. 표 밖이라 따로 적는다 — 그 상태에서는 요청 경로가 아예
 * 없고 「요청하면 온다」가 거짓 안내가 된다. **세 자리가 같은 주장을 한다**(cr r1 LOW: 앞 판은
 * 자격 축만 이 갈래를 쓰고 투찰가 카드는 「요청할 수 없어」를 재사용해, 결과는 같은데
 * 주장이 갈렸다).
 */
const PRICE_ABSENCE_SETTLED: PriceAbsenceCopy = {
  qualification: settled('자격 판정을 잴 수 없습니다'),
  price: settled('투찰가 3안과 그 근거를 볼 수 없습니다'),
  floorMargin: settled('하한 대비 여유를 잴 수 없습니다'),
};

/**
 * 기록할 금액을 **다시 적어야 하는** 사실. `BidCase` 에 초안 금액 칸이 없어 화면이 그 값을
 * 되읽을 수 없다(그 알려진 제한이 지금까지 **코드 주석에만** 있었고 화면은 빈 입력 위에
 * 「초안과 기록이 같은 입력을 씁니다」를 세웠다).
 *
 * **초안이 있는 자리에서만 선다**(v F5): 다음 행동이 「제출함」이라는 것이 곧 초안이 있다는
 * 뜻이다(상태 표의 `why` — 「초안이 있고 아직 제출 기록이 없다」). 앞 판은 「다시 요청할 수 없는」
 * 자리 전부에 세워, 초안을 만든 적 없는 「나중에」 건에도 「**다시** 적으십시오」라고 말했다.
 */
const DRAFT_AMOUNT_NOT_READABLE = '기록할 금액은 다시 적으십시오 — 초안 금액을 되읽는 표면이 계약에 없습니다.';

/** 내려놓기의 자리 표지 — 화면 D 의 카드와 **다른 이름**이다(한 여정이 두 화면을 지난다). */
const EXIT_TEST_ID = {
  DEFER: 'worksheet-exit-DEFER',
  SKIP: 'worksheet-exit-SKIP',
} as const satisfies Record<ExitActionKind, string>;

/**
 * **보내기 전 확인**(§6.2 C 3 — 「안을 고르면 확인이 뜨고, 확인하면 초안이 만들어진다」).
 *
 * 확인에 **보낼 값을 담는다**. 지금 입력을 다시 읽으면 확인한 수와 보내는 수가 갈릴 수 있고,
 * 이 화면의 축이 바로 투찰 금액이다. 입력이 바뀌면 이 확인은 **닫힌다**(다른 수를 가리키는
 * 확인을 들고 있지 않는다).
 */
interface BidConfirmation {
  readonly amountWon: number;
  readonly vat: VatTreatment;
}

/**
 * 화면 C — 공고 워크시트(§6.2 C). ②③④를 한 화면에 둔다: 머리 → 자격·근거 → 나라장터 이동 →
 * 투찰 기록 → 타임라인.
 *
 * **공고 단건 조회 operation 이 계약에 없다**(요구표 ⓑ, 담당 7C①) — 그래서 검색으로 한 건을
 * 고른다(`q=<공고번호>` 뒤 번호·차수 일치). 알려진 제한으로 등재하고 7C① 후속으로 보고한다.
 */
export function WorksheetScreen() {
  const params = useParams<{ noticeNumber: string; noticeRound: string }>();
  const noticeNumber = params.noticeNumber ?? '';
  const noticeRound = params.noticeRound ?? '';
  const queryClient = useQueryClient();
  // 쓰기를 하는 화면이다 — 새로고침 뒤에도 CSRF 값을 쥐게 한다(7A 알려진 제한 L-1).
  const { signedIn } = useSessionCsrf();
  const { toast, show, dismiss } = useToast();
  /**
   * 지금 떠 있는 토스트가 **실행 취소를 들었는가**(cr L-1 · 13D 가 같은 자리에 둔 예외).
   * 뒤이은 쓰기가 실패해도 **성공한 내려놓기의 되돌릴 길**은 걷지 않는다 — 되돌릴 수 있는데
   * 되돌릴 길이 화면에서 사라지는 것이 가장 비싼 손해다.
   *
   * **이 기억은 그려진 토스트에서 유도된다**(M13 잔여 §12.3 (x) ⓒ · v N-1). 앞 판은 값을
   * 올리는 자리가 하나, 내리는 자리가 하나였고 그 사이에 토스트를 내는 자리가 **여섯 더**
   * 있었다 — 실행 취소가 없는 토스트가 덮은 뒤에도 기억이 참으로 남아, 뒤이은 실패가 그 성공
   * 문면을 실패 얼굴 옆에 남겼다. 이제 **맞출 자리가 없다**: 토스트가 바뀌면 그다음 commit 에서
   * 기억이 따라오므로, 토스트를 내는 자리가 몇이든 둘이 갈리지 않는다.
   *
   * **ref 인 이유는 비동기 `onError` 가 읽기 때문**이다 — 그 자리가 보아야 하는 것은 요청을
   * 보내던 렌더의 closure 가 아니라 **지금 그려져 있는 것**이다. 쓰는 자리를 effect 하나로 둔
   * 이유도 같다: 렌더 중에 ref 를 만지면 그 값을 쥔 객체가 전부 「렌더 중 ref 접근」이 된다.
   */
  const exitUndoable = useRef(false);
  useEffect(() => {
    exitUndoable.current = toast?.undo !== undefined;
  }, [toast]);
  const [form, setForm] = useState<BidAmountForm>(EMPTY_BID_FORM);
  // 진행 중 투찰 건수의 **선언** — 기본값이 없다(CR H-1). 빈 칸이면 요청 경로가 막힌다.
  const [activeBids, setActiveBids] = useState('');
  const [price, setPrice] = useState<BidPriceRequestResponse | null>(null);
  const [blocked, setBlocked] = useState<string | null>(null);
  const [activeBidsBlocked, setActiveBidsBlocked] = useState<string | null>(null);
  const [confirming, setConfirming] = useState<BidConfirmation | null>(null);
  const confirmTitleId = `bid-confirm-title-${useId()}`;
  /** 내려놓기 폼의 자리 — 13D 의 폼이 `aria-controls` 로 쓰는 그 id 와 같은 몫이다. */
  const exitFormId = `worksheet-exit-form-${useId()}`;
  /** 카운트다운의 라벨 id — 값 노드가 `aria-labelledby` 로 그것을 든다(cr L-2). */
  const deadlineLabelId = `worksheet-deadline-label-${useId()}`;
  // **한 번 읽고 내린다**(M9 D-1) — 렌더마다 다시 읽으면 같은 화면의 두 칸이 서로 다른
  // 「지금」으로 날을 센다. 벽시계를 읽는 자리는 `timeDisplay` 하나다.
  const [now] = useState(readClock);

  const notices = useQuery({
    queryKey: ['notice', noticeNumber, noticeRound],
    queryFn: () => searchNotices({ q: noticeNumber, size: 100 }),
  });
  const cases = useQuery({ queryKey: bidCasesQueryKey(BID_KIND), queryFn: () => listBidCases(BID_KIND) });

  const notice = notices.data?.rows.find(
    (row) => row.noticeNumber === noticeNumber && row.noticeRound === noticeRound,
  );
  const bidCase = (cases.data ?? []).find(
    (row) => row.noticeNumber === noticeNumber && row.noticeRound === noticeRound,
  );

  // CR L-5 — 가짜 id(`?? 0`)를 인코딩하지 않는다. 건이 정해진 분기에서 **그 값을 잡아** 넘기면
  // 「0번 건을 조회하는」 자리가 아예 없다(`enabled` 를 훗날 건드려도 그렇다).
  const caseId = bidCase?.caseId;
  const timeline = useQuery({
    queryKey: ['timeline', caseId],
    queryFn: caseId === undefined ? skipToken : () => readTimeline(caseId),
  });

  async function refreshCase(): Promise<void> {
    await queryClient.invalidateQueries({ queryKey: bidCasesQueryKey(BID_KIND) });
    await queryClient.invalidateQueries({ queryKey: ['timeline'] });
  }

  /**
   * 입력이 바뀌면 **열어 둔 확인은 닫는다** — 확인한 금액과 보낼 금액이 갈리지 않게 한다.
   * 어느 칸이 바뀌어도 닫는다: 과세 구분도 보낼 진술의 일부다.
   */
  function changeForm(next: BidAmountForm): void {
    setForm(next);
    setConfirming(null);
  }

  /**
   * **그 명령이 이미 서버에 닿았는가** — 재시도의 공통 전제다(v r3 M-3).
   *
   * 하네스로 서버 거동을 직접 쟀다: `BID_RECORDED` 인 건에 같은 본문을 다시 보내면 200 이
   * 돌아오고 **기록 행이 하나 더 생긴다**(`record_seq` 0·1·2 — 금액이 달라도 받는다). 초안도
   * 같다(`draft_seq` 0·1·2). 되돌림만 서버가 409 로 막는다. 즉 **재전송은 멱등이 아니다.**
   *
   * 실패 얼굴을 내기 전에 건을 다시 읽으므로(`onError`), 그때의 `caseVersion` 이 보낼 때보다
   * 앞서 있으면 그 쓰기는 닿은 것이다 — 다시 보내지 않는다. 다른 행위자가 건을 움직인
   * 경우에도 막히는데, 그쪽이 안전한 방향이다(중복을 만들지 않고 운영자가 상태를 다시 본다).
   */
  function alreadyLanded(sentFromVersion: number | undefined): boolean {
    return sentFromVersion !== undefined && bidCase !== undefined && bidCase.caseVersion > sentFromVersion;
  }

  /** 재시도는 **보낸 그 진술**을 다시 보낸다 — 단, 이미 닿았으면 보내지 않는다. */
  function writeRetry<Sent extends { readonly fromVersion: number }>(mutation: {
    readonly variables: Sent | undefined;
    readonly mutate: (sent: Sent) => void;
    readonly reset: () => void;
  }): () => void {
    return () => {
      const sent = mutation.variables;
      if (sent === undefined) {
        return;
      }
      if (alreadyLanded(sent.fromVersion)) {
        mutation.reset();
        // 문면은 **술어가 세운 것만** 말한다(CR r4 L-4). `alreadyLanded` 는 「건이 움직였다」를
        // 세고, 그것이 내 쓰기였는지 다른 행위자였는지는 가리지 않는다 — 「이미 반영됐습니다」는
        // 운영자가 「내 기록이 남았다」로 읽을 수 있고, 이 slice 의 축이 바로 투찰 기록 장부다.
        show({
          text: '건이 그 사이 움직였습니다 — 중복을 만들지 않으려고 보내지 않았습니다. 아래 상태를 확인하십시오.',
        });
        return;
      }
      mutation.mutate(sent);
    };
  }

  const askPrice = useMutation({
    // 선언된 값만 wire 에 닿는다 — 호출 자리에 리터럴이 없다(CR H-1 · v M-5).
    mutationFn: (payload: { readonly currentActiveBids: number; readonly fromVersion: number }) =>
      requestBidPrice({ noticeNumber, noticeRound, currentActiveBids: payload.currentActiveBids }),
    onSuccess: (result) => {
      setPrice(result);
    },
    // 투찰가 요청도 **서버 상태를 움직일 수 있다**(건이 `PRICE_RECEIVED` 로 간다). 실패 얼굴이
    // 「다시 읽었습니다」라고 말하므로 그 말이 사실이어야 한다(CR r3 M-2).
    onError: async () => {
      dismiss();
      await refreshCase();
    },
  });

  /**
   * 투찰가 요청은 **POST 인데 옛 읽기 얼굴**을 쓰고 있었다(CR r2 L-5) — 세션이 중간에 끝나면
   * 그 자리에서 영원히 재시도한다. 쓰기 넷과 같은 얼굴·같은 401 갈래로 모은다.
   */
  const priceFailure = askPrice.isError
    ? {
        what: '투찰가를 요청하지',
        error: askPrice.error,
        /**
         * **지금 화면에 보이는 선언값**을 보낸다(CR r3 M-3) — 누를 때의 값을 다시 보내면
         * 운영자가 그 사이 고친 수와 보낸 수가 갈린다. 선언은 「그때 적었던 것」이 아니라
         * 「지금 적혀 있는 것」이다.
         */
        retry: () => {
          if (alreadyLanded(askPrice.variables?.fromVersion)) {
            askPrice.reset();
            show({ text: '건이 그 사이 움직였습니다 — 중복 요청을 만들지 않으려고 보내지 않았습니다.' });
            return;
          }
          sendPriceRequest();
        },
      }
    : null;

  const makeDraft = useMutation({
    /**
     * **시각을 payload 로 받는다**(CR r2 M-3). `mutationFn` 안에서 만들면 「다시 시도」가 같은
     * 진술을 보내지 않는다 — 네트워크가 실패했다고 운영자가 제시한 시각이 바뀔 이유가 없다.
     */
    mutationFn: (payload: {
      readonly caseId: number;
      readonly amountWon: number;
      readonly vat: 'INCLUSIVE' | 'EXCLUSIVE' | 'UNKNOWN';
      readonly at: string;
      readonly fromVersion: number;
    }) =>
      draftBid(payload.caseId, {
        amountWon: payload.amountWon,
        vatTreatment: payload.vat,
        presentedAt: payload.at,
      }),
    onSuccess: async () => {
      show({ text: '투찰 예정 초안을 만들었습니다.' });
      await refreshCase();
    },
    // 실패는 성공 토스트를 걷고 **서버 상태를 다시 읽은 뒤** 얼굴을 낸다(CR H-2).
    onError: async () => {
      dismiss();
      await refreshCase();
    },
  });

  const makeRecord = useMutation({
    mutationFn: (payload: {
      readonly caseId: number;
      readonly amountWon: number;
      readonly vat: 'INCLUSIVE' | 'EXCLUSIVE' | 'UNKNOWN';
      readonly drawNumbers: number[];
      readonly memo: string;
      readonly at: string;
      readonly fromVersion: number;
    }) =>
      recordBid(payload.caseId, {
        amountWon: payload.amountWon,
        vatTreatment: payload.vat,
        submittedAt: payload.at,
        drawNumbers: payload.drawNumbers,
        ...(payload.memo.trim() === '' ? {} : { memo: payload.memo.trim() }),
      }),
    onSuccess: async () => {
      show({ text: '투찰 기록을 남겼습니다.' });
      await refreshCase();
    },
    // 실패는 성공 토스트를 걷고 **서버 상태를 다시 읽은 뒤** 얼굴을 낸다(CR H-2).
    onError: async () => {
      dismiss();
      await refreshCase();
    },
  });

  /**
   * 열어 둔 내려놓기 폼 — **열린 집합은 표가 정한다**(화면이 상태를 보고 고르지 않는다).
   */
  const [exiting, setExiting] = useState<BidBoxAction | null>(null);
  /**
   * 쓰기가 날아가 있는가 — **동기 가드**다(13D cr MEDIUM 실측의 선례). 버튼의 `disabled` 만으로는
   * 같은 틱의 연타를 막지 못한다(`isPending` 이 올린 재렌더보다 손가락이 빠르다). 내려놓기 둘은
   * 되돌릴 수 없거나(건너뜀) 되돌리는 데 전이가 하나 더 드는 자리라(나중에) 중복이 비싸다.
   */
  const exitSending = useRef(false);

  const resume = useMutation({
    mutationFn: (payload: { readonly caseId: number; readonly fromVersion: number }) =>
      resumeCase(payload.caseId),
    onSuccess: async () => {
      show({ text: '다시 보기로 돌렸습니다.' });
      await refreshCase();
    },
    // 실패는 성공 토스트를 걷고 **서버 상태를 다시 읽은 뒤** 얼굴을 낸다(CR H-2).
    onError: async () => {
      dismiss();
      await refreshCase();
    },
  });

  /**
   * **내려놓기**(계약 받는 항목 3 의 워크시트 자리 — 13D 머지 뒤 팀장 호출로 섰다).
   *
   * 「나중에」는 역전이가 있어 **되돌릴 길을 토스트에 함께** 낸다(그 전이는 이 화면이 이미 가진
   * `resume` 이다 — 사본이 생기지 않는다). 「건너뜀」은 유일한 종단이라 실행 취소가 없고, 그 대신
   * 폼이 보내기 전에 한 번 더 묻는다(13D 의 입력 꼴 표에 그 문면이 있다).
   *
   * 실패는 **서버 상태를 다시 읽고** 쓰기 실패 얼굴을 낸다 — 다른 쓰기들과 같은 관례다.
   */
  const exit = useMutation({
    mutationFn: (payload: {
      readonly caseId: number;
      readonly kind: ExitActionKind;
      readonly value: string;
      readonly fromVersion: number;
    }) => sendExit(payload.caseId, { kind: payload.kind, value: payload.value }),
    onSuccess: async (updated, sent) => {
      // **가드를 여기서 푼다** — 실행 취소가 살아나는 시점과 같아야 한다(13D 의 그 자리와 같은 사유).
      exitSending.current = false;
      /*
       * 되돌릴 길의 **기억은 여기서 손대지 않는다**(§12.3 (x) ⓒ). 그 값은 그려진 토스트에서
       * 유도되므로 「실행 취소를 붙였는데 기억은 안 바꿨다」가 설 자리가 없다 — 앞 판은 그
       * 둘을 손으로 맞췄고, 그래서 **뒤에 오는 다른 토스트**가 기억을 묵은 참으로 남겼다.
       */
      show(
        sent.kind === 'DEFER'
          ? {
              text: '나중에 보기로 했습니다.',
              undo: {
                label: '실행 취소',
                // 되돌릴 건과 버전은 **서버가 준 응답**에서 온다 — 화면이 쥔 값이 아니다.
                run: () => resume.mutate({ caseId: updated.caseId, fromVersion: updated.caseVersion }),
              },
            }
          : { text: '건너뛰었습니다.' },
      );
      await refreshCase();
    },
    onSettled: () => {
      exitSending.current = false;
    },
    onError: async () => {
      // **실행 취소가 붙은 토스트는 걷지 않는다**(cr L-1): 실패는 쓰기 실패 얼굴이 따로 말하고,
      // 실행 취소가 없는 토스트만 걷는다(그것이 실패 얼굴 옆에 남으면 무엇이 성공인지 흐려진다).
      if (!exitUndoable.current) {
        dismiss();
      }
      await refreshCase();
    },
  });

  /**
   * 내려놓기로 가는 **한 입구** — 폼의 보내기와 **쓰기 실패 얼굴의 다시 시도**가 이 가드를 지난다
   * (cr M-1 — 앞 판은 다시 시도가 가드 밖이었고, 같은 틱의 두 누름이 요청 셋을 냈다. 실측:
   * 1 → 3). 입구가 둘이면 가드가 하나여도 소용이 없다.
   *
   * **토스트의 실행 취소는 이 가드 밖이다** — 그 버튼은 한 번 누르면 토스트가 닫히는 **단발
   * 입구**이고(ToastHost 가 그 자리에서 토스트를 걷는다), 그래서 같은 틱의 둘째 누름이 닿을
   * 요소가 없다. 되돌림 전이는 서버가 409 로 막는 쪽이기도 하다.
   */
  function submitExit(caseId: number, fromVersion: number, payload: ActionPayload): void {
    if (exitSending.current) {
      return;
    }
    if (payload.kind !== 'DEFER' && payload.kind !== 'SKIP') {
      return;
    }
    exitSending.current = true;
    setExiting(null);
    exit.mutate({ caseId, kind: payload.kind, value: payload.value, fromVersion });
  }

  /**
   * **담기**(계약 받는 항목 3·7 의 워크시트 자리) — 이 공고가 입찰함에 없으면 그 자리에서 담는다.
   * 앞 판은 「오늘의 공고에서 담으십시오」라고만 말해, 워크시트까지 온 운영자를 되돌려 보냈다.
   *
   * 서버가 같은 (공고, 종류)에 건을 **하나만** 두고 이미 담긴 건이면 그 건을 돌려주므로, 재시도가
   * 중복 행을 만들지 않는다(그래서 다른 쓰기들과 달리 버전 가드가 필요 없다).
   */
  const addCase = useMutation({
    mutationFn: () => addBidCase({ noticeNumber, noticeRound, bidKind: BID_KIND }),
    onSuccess: async () => {
      show({ text: '입찰함에 담았습니다.' });
      await refreshCase();
    },
    onError: async () => {
      dismiss();
      await refreshCase();
    },
  });

  /**
   * 내려놓기의 **다시 시도** — 가드를 지나는 입구로 보낸다(cr M-1). 버전 가드는 공용 `writeRetry`
   * 를 그대로 쓰고(틱을 건넌 재전송을 막는 축) 보내는 자리만 `submitExit` 으로 바꾼다.
   *
   * 조립을 **누를 때** 하는 이유: 그 조립이 `submitExit` 을 담고 그것이 가드 ref 를 읽으므로,
   * render 중에 만들면 「render 중 ref 접근」이 된다(lint 가 그 자리를 든다).
   */
  function retryExit(): void {
    writeRetry({
      variables: exit.variables,
      reset: exit.reset,
      mutate: (sent) => submitExit(sent.caseId, sent.fromVersion, { kind: sent.kind, value: sent.value }),
    })();
  }

  /** 금액·과세가 **둘 다** 있어야 전이를 부른다 — 없으면 멈추고 무엇이 없는지 말한다. */
  function amountOrBlock(): { readonly amountWon: number; readonly vat: 'INCLUSIVE' | 'EXCLUSIVE' | 'UNKNOWN' } | null {
    const amountWon = parseWon(form.amount);
    if (amountWon === null) {
      setBlocked('투찰 금액을 원 단위 정수로 적으십시오.');
      return null;
    }
    if (form.vat === '') {
      setBlocked('과세 구분을 고르십시오.');
      return null;
    }
    setBlocked(null);
    return { amountWon, vat: form.vat };
  }

  /** 투찰가 요청 한 자리 — 첫 누름과 재시도가 **같은 길**로 간다(CR r3 M-3). */
  function sendPriceRequest(): void {
    if (bidCase === undefined) {
      return;
    }
    const declared = parseCount(activeBids);
    // 사유를 그 자리에서 말한다(CR r3 L-4) — 「적지 않았다」와 「범위를 넘었다」는 다른 답이다.
    if (declared.kind === 'not-declared') {
      setActiveBidsBlocked('진행 중 투찰 건수를 0 이상의 정수로 선언하십시오 — 이 수는 지어내지 않습니다.');
      return;
    }
    if (declared.kind === 'out-of-range') {
      setActiveBidsBlocked(`선언할 수 있는 건수는 ${declared.max.toLocaleString('ko-KR')} 까지입니다.`);
      return;
    }
    setActiveBidsBlocked(null);
    askPrice.mutate({ currentActiveBids: declared.value, fromVersion: bidCase.caseVersion });
  }

  function runPrimary(kind: NextActionKind, caseId: number, fromVersion: number): void {
    if (kind === 'REQUEST_PRICE') {
      sendPriceRequest();
      return;
    }
    if (kind === 'RESUME') {
      resume.mutate({ caseId, fromVersion });
      return;
    }
    const amount = amountOrBlock();
    if (amount === null) {
      return;
    }
    if (kind === 'DRAFT_BID') {
      // **초안 앞에만 확인이 선다**(§6.2 C 3). 「제출함」은 §6.2 C 5 가 「한 번으로 ④ 완료」로
      // 둔 자리라 같은 단계를 더하지 않는다 — 운영자는 이미 나라장터에서 제출을 끝내고 돌아온다.
      setConfirming({ amountWon: amount.amountWon, vat: amount.vat });
      return;
    }
    const drawNumbers = parseDrawNumbers(form.drawNumbers);
    if (drawNumbers === null) {
      setBlocked('추첨번호는 정수로 적으십시오.');
      return;
    }
    makeRecord.mutate({
      caseId,
      ...amount,
      drawNumbers,
      memo: form.memo,
      at: readClock().toISOString(),
      fromVersion,
    });
  }

  /** 확인에 담긴 **그 진술**을 보낸다 — 보내는 순간 확인은 닫힌다. */
  function acceptConfirmed(caseId: number, fromVersion: number): void {
    if (confirming === null) {
      return;
    }
    setConfirming(null);
    makeDraft.mutate({
      caseId,
      amountWon: confirming.amountWon,
      vat: confirming.vat,
      at: readClock().toISOString(),
      fromVersion,
    });
  }

  // CR r2 L-6 — 마운트 시점의 `signedIn` 뿐 아니라 **읽기 401**(떠 있는 화면에서 세션이
  // 끝난 경우)도 같은 답이다. 그 재시도는 영원히 실패한다.
  // `??` 연쇄는 **첫 오류 하나**만 봤다(CR r3 L-2). 둘 가운데 하나라도 401 이면 그 답이다.
  if (signedIn === false || [notices.error, cases.error].some(isUnauthorized)) {
    return (
      <section data-testid="screen-worksheet" className="mx-auto max-w-3xl p-4">
        <SignedOutFace />
      </section>
    );
  }
  if (notices.isPending || cases.isPending) {
    return (
      <section data-testid="screen-worksheet" className="mx-auto max-w-3xl p-4">
        <Skeleton testId="worksheet-loading" />
      </section>
    );
  }
  if (notices.isError || cases.isError) {
    return (
      <section data-testid="screen-worksheet" className="mx-auto max-w-3xl p-4">
        <ErrorFace
          testId="worksheet-error"
          onRetry={() => {
            void notices.refetch();
            void cases.refetch();
          }}
        />
      </section>
    );
  }
  if (notice === undefined) {
    return (
      <section data-testid="screen-worksheet" className="mx-auto max-w-3xl p-4">
        <EmptyFace
          testId="worksheet-missing"
          display={{ label: '그 공고를 찾지 못했습니다', why: '수집된 공고 가운데 이 번호·차수가 없습니다', tone: 'absent' }}
          // 이유만 말하고 멈추지 않는다 — 여기서 할 수 있는 다음 걸음은 목록으로 돌아가는 것이다.
          action={
            <LinkButton to="/" testId="worksheet-missing-next">
              오늘의 공고로 가기
            </LinkButton>
          }
        />
      </section>
    );
  }

  /**
   * 쓰기 실패 얼굴 하나 — primary 버튼이 하나이므로 한 번에 하나의 쓰기만 돈다. 어느 전이가
   * 실패했는지는 **그 전이의 이름**으로 말한다(CR H-2).
   *
   * **얼굴은 그 행동이 사는 칸에 선다**(cr M-2). 배치를 두 칸으로 가르기 전에는 내려놓기와 그
   * 실패가 같은 카드 둘레에 붙어 있었다. 이제 내려놓기는 레일에 살고, 얼굴을 본문에 두면
   * 1280px 에서는 **반대쪽 칸**, 390px 에서는 몇 화면 위에 선다 — 그 얼굴 안의 「다시 시도」가
   * 내려놓기 가드를 지나는 **유일한 입구**라 값이 큰 자리다. 그래서 자리를 값으로 들고 다닌다.
   */
  const writeFailure = makeDraft.isError
    ? {
        where: 'body' as const,
        what: '투찰 예정 초안을 만들지',
        error: makeDraft.error,
        // **실제로 다시 보낸다**(CR r2 M-3) — 앞 판은 `reset()` 이라 얼굴만 사라졌고, 운영자는
        // 「다시 시도」를 누르고 메시지가 사라지는 것을 **성공으로 읽을 수 있었다**.
        retry: writeRetry(makeDraft),
      }
    : makeRecord.isError
      ? {
          where: 'body' as const,
          what: '투찰 기록을 기록하지',
          error: makeRecord.error,
          retry: writeRetry(makeRecord),
        }
      : resume.isError
        ? {
            where: 'body' as const,
            what: '다시 보기로 되돌리지',
            error: resume.error,
            retry: writeRetry(resume),
          }
        : addCase.isError
          ? {
              where: 'body' as const,
              what: '입찰함에 담지',
              error: addCase.error,
              // 담기는 **멱등**이라(서버가 이미 담긴 건을 돌려준다) 보낸 진술을 그대로 다시 보낸다.
              retry: () => addCase.mutate(),
            }
          : exit.isError
            ? {
                /* 어느 내려놓기였는지 **그 전이로** 말한다. 이 문면은 실패를 말하는 문장의
                   조각이라 표의 라벨(「나중에」·「건너뜀」)과 모양이 다르다 — 표를 여기 끼우면
                   「나중에를 하지 못했습니다」가 되어 한국어가 아니다. 다시 보내기는 다른 쓰기와
                   같은 버전 가드를 지난다: 그 사이 건이 움직였으면 보내지 않는다(내려놓기는
                   멱등이 아니다). */
                /* 내려놓기는 **레일에서 보낸다** — 그 결과도 레일에서 말한다(cr M-2). */
                where: 'rail' as const,
                what: `${exit.variables?.kind === 'SKIP' ? '건너뛰지' : '나중에 보기로 돌리지'}`,
                error: exit.error,
                /* 이 갈래의 다시 시도는 **가드를 지나는 입구**이고 그 입구가 가드 ref 를 읽는다.
                   그래서 이 객체에 담지 않고 **그리는 자리에서** 건다 — 담으면 이 객체 자체가
                   「render 중에 읽는 ref 값」이 되어 그 자리를 보는 lint 가 든다. */
                retry: null,
              }
            : null;

  const amount = amountDisplayOf(notice);
  // 마감은 **절대 시각과 남은 날이 한 칸**이다 — 둘 다 `timeDisplay` 를 지난다(M9 D-1·D-2).
  const deadline = instantDisplayOf(notice.deadlineAt);
  const deadlineDays = dayCountText(daysUntil(notice.deadlineAt, now));
  /**
   * 다음 확인 시점의 표기. **레일이 서는 전제는 「건이 있는가」다**(cr L-3) — 앞 판은 이 값의
   * null 여부로 레일을 가렸는데, 오늘 그 둘은 동치지만 뒤 라운드가 이 값을 「없으면 null」로
   * 바꾸는 순간 레일 **전체**(다음 확인 시점 · 타임라인 · 내려놓기 · 열린 폼)가 조용히 사라진다.
   */
  const checkpointDisplay = instantDisplayOf(bidCase?.nextCheckpointAt);
  const action = bidCase === undefined ? null : nextActionOf(bidCase.state, price !== null);
  /** 이 상태에서 열린 내려놓기 — **표에서 유도된다**(화면이 상태를 보고 고르지 않는다). */
  const exits = bidCase === undefined ? [] : exitsOf(bidCase.state);
  const amountWon = parseWon(form.amount);
  /**
   * **응답이 손에 없는 자리 셋이 드는 사유** — 한 표에서 온다(cr r1 HIGH). 응답이 손에 있으면
   * 셋 다 사유가 없다(그 자리에 **값**이 선다).
   */
  const priceView: { readonly kind: 'present'; readonly price: BidPriceRequestResponse } | { readonly kind: 'absent'; readonly copy: PriceAbsenceCopy } =
    price === null
      ? { kind: 'absent', copy: action === null ? PRICE_ABSENCE_SETTLED : PRICE_ABSENCE[action.kind] }
      : { kind: 'present', price };
  const absence = priceView.kind === 'absent' ? priceView.copy : null;
  const priceCardAbsence = absence === null ? null : absence.price;
  const marginAbsence = absence === null ? null : absence.floorMargin;
  /**
   * 초안 금액을 되읽지 못한다는 사실은 **초안이 있는 자리에서만** 선다(v F5) — 다음 행동이
   * 「제출함」이라는 것이 곧 초안이 있다는 뜻이다.
   */
  const draftAmountUnreadable = price === null && action !== null && action.kind === 'RECORD_BID';
  /**
   * 하한 대비 여유 — **투찰가 응답이 있어야** 하한율이 있다. 요청 전에는 자리 자체를 만들지
   * 않는다(없는 축을 「재지 못함」으로 그리면 화면이 묻지 않은 것에 답한다).
   */
  const margin: FloorMargin | null =
    price === null
      ? null
      : floorMarginOf({
          groundsState: price.floorRateGroundsState,
          rateFraction: price.publishedFloorRateFraction,
          baseAmountWon: price.baseAmountWon,
          baseAmountVatTreatment: price.baseAmountVatTreatment,
          bidAmountWon: amountWon,
          bidVatTreatment: form.vat,
        });


  /**
   * 기초금액의 **출처 꼴**(14A cr L-5). 앞 판은 `?? ''` 라 출처가 없을 때 `1,234,000 ()` 를
   * 그렸다 — 빈 괄호는 「괄호 안의 무언가를 못 읽었다」로 보이지만, 사실은 **적을 것이 없다**.
   */
  const amountText = !amount.present
    ? null
    : amount.provenance === null
      ? amount.amount
      : `${amount.amount} (${amount.provenance})`;
  /** 마감 한 칸 — 절대 시각과 남은 날이 **같은 노드**다(M9 D-1·D-2). */
  const deadlineText =
    deadline.kind === 'absent' ? null : `${deadline.text}${deadlineDays === null ? '' : ` (${deadlineDays})`}`;
  /**
   * 발주기관 — **부재 판정을 글자 기준으로** 한다(cr M-1).
   *
   * `??` 만 두면 `''` 와 공백뿐인 값이 비켜 가 **조용한 빈칸**이 선다. 앞 판에서 이 값을 받던
   * `KeyValueRow` 는 그 꼴을 부재로 접었고(14A cr L-1 이 정확히 이 때문에 넣은 분기다), 머리를
   * 직접 그리면서 그 가드가 사라졌다 — 이 자리가 그것을 다시 세운다.
   *
   * **`PageHeader` 의 빈 자리 술어는 여기까지 보지 못한다**: 요약이 **요소**면 그 술어가 거짓을
   * 내고 빈 문단이 선다(그 컴포넌트가 자기 알려진 제한으로 적어 둔 구멍이고, 호출부가 닫는다).
   */
  const agency =
    [notice.demandAgencyName, notice.noticeAgencyName].find((one) => (one ?? '').trim() !== '') ?? null;
  /** 다음 행동 하나 — 자리는 아래 표가 정한다. */
  const primarySlot = action === null ? null : PRIMARY_SLOT[action.kind];
  const primaryNode =
    action === null || bidCase === undefined ? null : (
      <Button
        tone="primary"
        size="lg"
        testId="worksheet-primary"
        onClick={() => runPrimary(action.kind, bidCase.caseId, bidCase.caseVersion)}
        disabled={askPrice.isPending || makeDraft.isPending || makeRecord.isPending || resume.isPending}
      >
        {action.label}
      </Button>
    );

  /**
   * 머리 블록(와이어프레임 원천) — 상태 알약 · 공고번호 · 제목 · 발주기관, 그리고 오른쪽에
   * **마감 카운트다운**.
   *
   * 옛 「필드명 / 빈 칸」 격자를 걷었다. 남는 용어 줄은 **기초금액 하나**다 — 그 줄의 아래층
   * 문면이 보조기기와 손가락에 닿는 경로(13C r4 L-1 · 14A M13 잔여 ⓖ)가 거기 걸려 있고, 다른
   * 네 줄의 사실은 알약 · 제목 곁 · 카운트다운 카드 · 레일이 각자 더 가까운 자리에서 말한다.
   *
   * **「방식」 칸은 만들지 않는다**(팀장 판정 ②): `NoticeSummary` 에 낙찰 방식을 내는 칸이 없다.
   * 서버가 가진 적 없는 칸을 부재 값으로 그리면 「이번 답에 빠졌다」로 읽힌다 — 그 사실은
   * §12.3 의 **데이터로 막힘** 행이 말한다.
   */
  const head = (
    <header className={`${cardClass()} flex flex-col gap-3`}>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <StateBadge display={noticeStatusDisplayOf(notice.status)} testId="worksheet-notice-status" />
            {bidCase === undefined ? null : <CaseStateBadge state={bidCase.state} testId="worksheet-case-state" />}
            <span data-testid="worksheet-number" className="text-xs text-ink-soft">
              공고번호 {notice.noticeNumber} · 차수 {notice.noticeRound}
            </span>
          </div>
          <PageHeader
            testId="worksheet"
            title={notice.title ?? notice.noticeNumber}
            summary={
              <span data-testid="worksheet-agency">
                {agency === null ? <AbsentValue reason="notSent" /> : agency}
              </span>
            }
          />
          <dl className="flex flex-wrap items-start gap-x-8 gap-y-2">
            <KeyValueRow
              label="기초금액"
              testId="worksheet-amount"
              hint="투찰 기준 금액. 추정가격과 다르다."
              value={amountText}
            />
          </dl>
        </div>
        {/* **마감 카운트다운은 두 단 밖이다** — 와이어프레임이 그것을 머리 행의 오른쪽에 둔다.
            좁은 화면에서는 머리 아래로 내려온다(줄바꿈 하나, 뒤집지 않는다). */}
        {/* 라벨과 값을 **프로그램적으로** 묶는다(cr L-2 · N-1). 앞 판은 `dt`/`dd` 였고 그 뒤
            판은 값 `<span>` 에 `aria-labelledby` 를 걸었는데, **역할 없는 일반 요소에는 그
            속성이 이름을 세우지 못한다**(이름 계산은 역할을 가진 자리에서만 선다). 카드를
            `group` 으로 두고 그 자리에 이름을 건다 — `dl` 로 되돌리면 머리의 용어 줄이 둘이
            되어 격자 하나 규율과 부딪힌다. */}
        <div
          role="group"
          aria-labelledby={deadlineLabelId}
          className={`${cardClass('sm')} flex w-full flex-col gap-1 md:w-auto md:shrink-0`}
        >
          <span id={deadlineLabelId} className="text-xs text-ink-soft">
            투찰 마감까지
          </span>
          <span data-testid="worksheet-deadline" className="font-numeric text-base font-semibold">
            {deadlineText === null ? <AbsentValue reason="notSent" /> : deadlineText}
          </span>
        </div>
      </div>
      {bidCase === undefined ? (
        /* **다음 걸음을 그 자리에 둔다**(계약 받는 항목 7) — 앞 판은 다른 화면으로 돌려보냈다. */
        <div className="flex flex-col gap-2">
          <p className="text-xs text-ink-soft">
            이 공고는 아직 입찰함에 없습니다 — 담으면 투찰가를 요청하고 투찰을 기록할 수 있습니다.
          </p>
          <div>
            <Button
              tone="primary"
              size="lg"
              testId="worksheet-add-case"
              onClick={() => addCase.mutate()}
              disabled={addCase.isPending}
            >
              입찰함에 담기
            </Button>
          </div>
        </div>
      ) : action === null ? (
        /* **개발 어휘를 쓰지 않는다**(v L-3). 그리고 「끝났다」로도 적지 않는다 — 이 자리의
           상태 집합에는 개찰 대기처럼 **끝나지 않은** 건이 들어 있고, 그 건에 「끝났다」는
           거짓이다. 참인 것은 「이 화면에서 지금 할 걸음이 없다」까지다. */
        <p className="text-xs text-ink-soft">이 화면에서 지금 할 걸음이 없습니다.</p>
      ) : primarySlot === 'head' ? (
        <div>{primaryNode}</div>
      ) : null}
    </header>
  );

  /**
   * 본문 — **여정의 차례**다(계약 C 행): 참가 자격 → 투찰가 → 나라장터 넘김 → 투찰 기록.
   * 앞 판은 근거 다섯 절이 통째로 앞에 서고 금액 입력이 맨 아래였다.
   */
  const body = (
    <>
      {writeFailure === null || writeFailure.where !== 'body' ? null : (
        <WriteFailureFace what={writeFailure.what} error={writeFailure.error} onRetry={writeFailure.retry} />
      )}
      {bidCase === undefined ? null : (
        <>
          {/* **빈 자리는 사유를 든다**(v H-1 · cr D14) — 앞 판은 응답이 없으면 이 카드를
              통째로 걷어, 자격에 대해 **아무 말도 하지 않았다**. 그 침묵이 「판정했고 통과」로
              읽힌다(화면 자신의 「빈 자리 + 사유」 규율과도 어긋난다). */}
          {/* **사유를 표에서 받는다** — 「응답이 없다」와 「사유가 있다」가 한 값이라, 사유 없이
              이 자리를 그리는 길이 타입에 남아 있지 않다(앞 판은 둘이 따로였다). */}
          {priceView.kind === 'present' ? (
            <WorksheetQualification price={priceView.price} />
          ) : (
            <section data-testid="qualification-absent" className={cardClass()}>
              <h3 className="text-sm font-semibold">자격</h3>
              {/* 사유를 **자기 표지**로 집는다 — 카드 전체를 읽으면 제목 글자가 섞여, 문면을
                  축어로 못박는 단언이 제목까지 함께 잠근다. */}
              <p data-testid="qualification-absent-why" className="mt-1 text-xs text-absent-ink">
                {priceView.copy.qualification}
              </p>
            </section>
          )}
          <section data-testid="worksheet-price-card" className={cardClass()}>
            <h3 className="text-sm font-semibold">투찰가</h3>
            {/* 요청의 전제다 — 선언이 없으면 요청이 가지 않는다(CR H-1). */}
            {action !== null && action.kind === 'REQUEST_PRICE' ? (
              <ActiveBidsDeclaration value={activeBids} onChange={setActiveBids} blocked={activeBidsBlocked} />
            ) : null}
            {askPrice.isPending ? <Skeleton testId="price-loading" rows={2} /> : null}
            {priceFailure === null ? null : (
              <WriteFailureFace what={priceFailure.what} error={priceFailure.error} onRetry={priceFailure.retry} />
            )}
            {price === null ? null : (
              <div className="mt-3">
                <WorksheetGrounds price={price} />
              </div>
            )}
            {/* 응답을 **다시 받을 수 없는** 자리의 사유(v H-1). 종단에서는 근거 절도 입력도
                없으므로, 이것이 없으면 이 카드에 제목만 남는다. */}
            {priceCardAbsence === null ? null : (
              <p data-testid="price-card-absent" className="mt-1 text-xs text-absent-ink">
                {priceCardAbsence}
              </p>
            )}
            {/*
              **입력 묶음과 그 한 문장은 다음 전이가 있을 때만 선다**(cr D6 · §12.3 (aa) ⓖ).
              머리가 「할 걸음이 없다」고 말하는 바로 아래에서 「초안과 기록이 같은 입력을
              씁니다」와 입력 넷이 서면 — 누를 자리가 하나도 없는데 — 운영자가 금액을 적고 보낼
              자리를 찾는다. **거짓 약속은 그 입력보다 거기 남은 문장이다**(cr N-4).

              조건은 **다음 전이의 표**에서 온다(넘김·기록 카드와 같은 가드다). 근거 절
              (3안 · 판정 · 고지)은 **읽는 표면**이라 이 가드 밖에 남는다.
            */}
            {action === null ? null : (
              <>
                {/* v L-1 — 이 문장은 **API 상태에서만** 나온다. 앞 판은 요청 전에도 늘 떠 있어
                    「아직 산출 안 함」을 화면이 추론해 말하는 꼴이었다(D-7E-4). */}
                {price !== null && price.bidPriceMenuState === 'NOT_COMPUTED' ? (
                  <p data-testid="amount-self-entry-note" className="mt-3 text-xs text-ink-soft">
                    투찰가 3안이 아직 산출되지 않아 금액은 직접 적습니다(위 근거의 상태 그대로).
                  </p>
                ) : null}
                <p className="mt-1 text-xs text-ink-soft">초안과 기록이 같은 입력을 씁니다.</p>
                {/* 초안 금액을 되읽는 표면이 없다 — 그 사실이 **빈 입력 곁**에서 말한다. */}
                {draftAmountUnreadable ? (
                  <p data-testid="draft-amount-not-readable" className="mt-1 text-xs text-absent-ink">
                    {DRAFT_AMOUNT_NOT_READABLE}
                  </p>
                ) : null}
                <div className="mt-3">
                  <BidAmountFields form={form} onChange={changeForm} blocked={blocked} />
                </div>
              </>
            )}
            {/* **보내기 전 확인**(§6.2 C 3) — 보낼 금액을 숫자와 한글로 함께 보인다. 확인에 담긴
                값이 그대로 가고, 입력이 바뀌면 닫힌다. */}
            {/* **그 상태에서만 선다**(cr L-7). 확인을 열어 둔 사이 건이 움직이면 그 확인은 이미
                다른 상태의 것이고, 그대로 보내면 서버가 409 로 막는다 — 운영자에게는 「확인했는데
                실패했다」로 보인다. 다음 행동이 초안이 아니면 패널 자체를 그리지 않는다. */}
            {confirming === null || action?.kind !== 'DRAFT_BID' ? null : (
              <div
                data-testid="bid-confirm"
                role="group"
                aria-labelledby={confirmTitleId}
                className="mt-3 rounded-md border border-line p-3"
              >
                <p id={confirmTitleId} className="text-sm font-medium">
                  이 금액으로 투찰하시겠습니까?
                </p>
                <p className="mt-2 font-numeric text-base">{confirming.amountWon.toLocaleString('ko-KR')}</p>
                {koreanAmountTextOf(confirming.amountWon) === null ? null : (
                  <p className="mt-1 text-xs text-ink-soft">{koreanAmountTextOf(confirming.amountWon)}</p>
                )}
                <p className="mt-2 text-xs text-ink-soft">
                  이 수로 투찰 예정 초안을 남깁니다. 나라장터 제출은 운영자가 직접 합니다.
                </p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <Button
                    tone="primary"
                    testId="bid-confirm-accept"
                    onClick={() => acceptConfirmed(bidCase.caseId, bidCase.caseVersion)}
                    disabled={makeDraft.isPending}
                  >
                    이 금액으로 초안 만들기
                  </Button>
                  <Button testId="bid-confirm-cancel" onClick={() => setConfirming(null)}>
                    취소
                  </Button>
                </div>
              </div>
            )}
            {/*
              「투찰가 요청」·「이 금액으로 진행」 — 와이어프레임이 그 버튼을 투찰가 카드의
              **마지막 줄**에 둔다.

              **두 종류가 같은 자리를 쓴다.** 종류마다 자리를 나누면 상태가 바뀔 때 React 가
              버튼을 떼었다 다시 붙이고, 그 사이에 잡아 둔 노드를 누르면 **아무 일도 일어나지
              않는다**(이 라운드의 실측 — 13C 의 확인 단계 test 여섯이 그렇게 붉었다). 자리를
              하나로 두면 노드가 살아 있으므로 그 함정이 없다.
            */}
            {primarySlot === 'price' ? <div className="mt-3">{primaryNode}</div> : null}
          </section>
          {/* **앞으로 갈 걸음은 종단에서 그리지 않는다**(cr L-1 · N-2). 이미 낙찰·유찰·
              건너뜀인 건에 「나라장터에서 투찰하기」와 「투찰했나요?」는 둘 다 거짓 안내다 —
              하나만 걷으면 남은 하나가 같은 거짓말을 계속한다. 조건은 **다음 전이의 표에서
              유도한다**: 그 표가 종단에서 `null` 을 내므로 종단 집합을 화면이 한 벌 더 적지
              않고, 상태가 늘어도 이 자리가 따라온다. */}
          {action === null ? null : (
            <>
          <KonepsHandoff
            noticeNumber={notice.noticeNumber}
            amountWon={amountWon}
            margin={margin}
            marginAbsence={marginAbsence}
          />
          <section data-testid="worksheet-record-card" className={cardClass()}>
            <h3 className="text-sm font-semibold">투찰했나요? 기록을 확인해 주세요</h3>
            <p className="mt-1 text-xs text-ink-soft">
              나라장터에서 제출한 뒤 이 자리에서 기록합니다 — 금액 · 과세 구분 · 추첨번호는 위에 적은 값을 그대로
              보냅니다.
            </p>
            {primarySlot === 'record' ? <div className="mt-3">{primaryNode}</div> : null}
          </section>
            </>
          )}
        </>
      )}
    </>
  );

  /**
   * 레일(와이어프레임의 300px 칸) — 다음 확인 시점 · 타임라인 · 내려놓기.
   *
   * **내려놓기가 레일의 맨 아래다**: 본문의 「다음 행동 하나」와 섞으면 이 상태에서 행동이
   * 셋이 되고 무엇이 다음 걸음인지 화면이 말하지 못한다(§6.5). 열린 집합은 표가 정하고, 폼은
   * 13D 의 것 하나다 — 입력 꼴 · 빈 값 가드 · 확인 한 단이 두 화면에서 같다.
   */
  const rail =
    bidCase === undefined ? null : (
      <>
        <section className={cardClass()}>
          <h3 className="text-sm font-semibold">다음 확인 시점</h3>
          <p data-testid="worksheet-checkpoint" className="mt-2 flex flex-wrap items-center gap-2 text-sm">
            <StateBadge display={displayOrUnknown(NEXT_CHECKPOINT_DISPLAY, bidCase.nextCheckpointKind)} />
            {/* 시점이 없으면 **자리도 없다** — 옆 배지가 「없음」을 말하므로 같은 사실을
                두 번 적지 않는다(M9 D-1 의 없음 표지는 배지가 없는 자리의 일이다). */}
            {checkpointDisplay.kind === 'absent' ? null : (
              <InstantText display={checkpointDisplay} className="text-xs" />
            )}
            {bidCase.overdue ? <span className="text-xs text-bad-ink">지난 일정</span> : null}
          </p>
        </section>
        {timeline.data === undefined ? null : <WorksheetTimeline entries={timeline.data} />}
        {/* **레일에서 보낸 것의 결과는 레일에서 말한다**(cr M-2) — 그 얼굴 안의 「다시 시도」가
            내려놓기 가드를 지나는 유일한 입구라, 버튼 바로 위가 그 자리다. */}
        {writeFailure === null || writeFailure.where !== 'rail' ? null : (
          <WriteFailureFace what={writeFailure.what} error={writeFailure.error} onRetry={retryExit} />
        )}
        {exits.length === 0 ? null : (
          <div className="flex flex-wrap gap-2">
            {exits.map((one) => (
              <Button
                key={one.kind}
                testId={EXIT_TEST_ID[one.kind as ExitActionKind]}
                onClick={() => setExiting(one)}
                disabled={exit.isPending || resume.isPending}
              >
                {one.label}
              </Button>
            ))}
          </div>
        )}
        {exiting === null ? null : (
          /* **폼의 정체를 행동에 묶는다**(13D cr HIGH): `key` 가 없으면 「닫지 않고 다른 것을
             연다」가 앞 폼의 값과 확인 상태를 그대로 물려받고, 그 입력이 되돌릴 수 없는 전이에
             실려 나간다. */
          <ActionForm
            key={exiting.kind}
            action={exiting}
            formId={exitFormId}
            pending={exit.isPending}
            onCancel={() => setExiting(null)}
            onSend={(payload) => submitExit(bidCase.caseId, bidCase.caseVersion, payload)}
          />
        )}
      </>
    );

  return (
    <section data-testid="screen-worksheet" className="mx-auto flex max-w-5xl flex-col gap-4 p-4">
      {head}
      {/* **레일에 담을 것이 없으면 두 단을 쓰지 않는다**(`TwoColumn` 의 규율): 빈 레일은 배치
          게이트가 「표지는 있는데 잴 것이 없는」 자리로 보게 되는 입구다. */}
      {rail === null ? <div className="flex flex-col gap-4">{body}</div> : <TwoColumn body={body} rail={rail} />}

      <ToastHost toast={toast} onDismiss={dismiss} />
    </section>
  );
}
