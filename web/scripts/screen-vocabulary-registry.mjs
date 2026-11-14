// 화면 어휘 게이트의 **장부** — 씨앗과 분류다. 판정 코드는 `screen-card-coverage.mjs` 에 있고
// 이 파일은 데이터만 든다(사람이 읽고 고치는 자리와 기계가 세는 자리를 가른다).
//
// **왜 열거가 남는가**: 「무엇이 금액·시각·명령 어휘인가」는 사람의 판단이다. 그래서 열거를
// 없애는 대신 **열거가 뒤처지는 축마다** 구조 의무를 건다 — 판정 코드의 다섯 버킷이 그것이고,
// 그 가운데 `contradicted` 가 이 표의 **모든** 행에 붙는다: 폐포가 그 키에 닿으면 「어휘가
// 아니다」는 구조적으로 거짓이고 게이트가 붉어진다.

/**
 * 표시 어휘를 화면에 내는 함수들 — **원 선언의 자리**로 적는다(`<src 기준 상대경로>#<이름>`).
 * 이 가운데 하나라도(직접이든 표시층을 지나서든) 부르면 그 카드는 금액·시각·명령 문면을
 * 그린다는 뜻이고, 따라서 「원문 0」 단언의 대상이다.
 *
 * 호출 지점의 이름이 아니라 **선언**을 적는 것이 M9 판과의 차이다: 선언은 그 함수의 유일한
 * 신원이라 별칭·재수출·네임스페이스 접근으로 바뀌지 않는다.
 *
 * **M12/12B 가 더한 둘.** `sentValueView` — 씨앗을 지나지 않고 자기 금액 문면을 만든다(등식
 * 모집단이 어휘 모듈 셋이던 동안 보이지 않았다). `commandDisplayOf` — DOM 게이트가 금지하는
 * 셋에 **명령 토큰**이 있는데 씨앗은 전부 금액·시각이었다(그래서 명령만 그리는 카드가 의무를
 * 내지 않았다). 둘 다 마운트 의무를 늘리지 않는다(실측).
 */
export const VOCABULARY_DECLARATIONS = [
  'display/caseDisplay.ts#commandDisplayOf',
  'display/editSessionDisplay.ts#sentValueView',
  'display/moneyDisplay.ts#amountBasisDisplayOf',
  'display/moneyDisplay.ts#amountProvenanceDisplayOf',
  'display/moneyDisplay.ts#amountQualifierTextOf',
  'display/moneyDisplay.ts#currencyDisplayOf',
  'display/moneyDisplay.ts#vatTreatmentDisplayOf',
  'display/noticeDisplay.ts#amountDisplayOf',
  'display/timeDisplay.ts#instantDisplayOf',
];

/** 표시층 내부의 조립자 — 화면이 **직접** 부르지 않는다는 사실에 구조 의무가 붙는다. */
export const INTERNAL_ASSEMBLER = 'INTERNAL_ASSEMBLER';
/** 이 게이트가 금지하는 셋(금액 토큰·명령 토큰·시각 원문)이 아닌 축. */
export const OTHER_AXIS = 'OTHER_AXIS';

/**
 * 표시층이 내보내는 **부를 수 있는 것** 가운데 어휘가 아닌 것과 그 사유.
 *
 * 모집단은 `src/display/**` **디렉터리** 전수다(M12/12B — 앞 판은 씨앗 목록에서 유도한 모듈
 * 셋이었고, 그래서 새 모듈을 만들면 등식 밖이었다). 폐포가 닿는 것은 **구조가 설명하므로**
 * 이 표에 적지 않는다 — 여기 있는 것은 「닿지 않는데 부를 수 있는 것」뿐이다.
 */
export const VOCABULARY_MODULE_EXCLUSIONS = [
  {
    key: 'display/moneyDisplay.ts#currencyUnitOf',
    kind: INTERNAL_ASSEMBLER,
    why: '통화 코드 → 단위 글자. 표시층이 금액 글자를 조립할 때 쓰는 속이고 화면이 직접 부르지 않는다(그 사실만 구조로 잰다)',
  },
  {
    key: 'display/moneyDisplay.ts#amountTextOf',
    kind: INTERNAL_ASSEMBLER,
    why: '숫자와 단위를 한 글자로. 위와 같은 속이고 화면이 직접 부르지 않는다. 표시층 다른 모듈을 지나 화면에 닿는 경로는 이제 폐포가 본다',
  },
  {
    key: 'display/noticeDisplay.ts#noticeStatusDisplayOf',
    kind: OTHER_AXIS,
    why: '공고 상태 축 — 이 게이트가 금지하는 어휘(금액 토큰·명령 토큰·시각 원문)가 아니다',
  },
  { key: 'display/timeDisplay.ts#daysUntil', kind: OTHER_AXIS, why: '달력일 차를 수로 낸다. 글자를 만들지 않는다' },
  { key: 'display/timeDisplay.ts#dayCountText', kind: OTHER_AXIS, why: 'D-day 문면. 시각 원문을 그리는 자리가 아니다' },
  {
    key: 'display/timeDisplay.ts#localInputToIso',
    kind: OTHER_AXIS,
    why: '쓰기 입력 → ISO 변환. 화면 글자가 아니라 요청 본문으로 간다',
  },
  {
    key: 'display/timeDisplay.ts#readClock',
    kind: OTHER_AXIS,
    why: '지금을 얻는 승인된 한 입구. 값을 그리지 않는다(D-1)',
  },

  // ── 입찰함 축 ──────────────────────────────────────────────────────────────
  { key: 'display/bidBoxDisplay.ts#tabOf', kind: OTHER_AXIS, why: '상태 → 탭 id. id 는 선택 상태의 글자이고 표시 어휘가 아니다' },
  { key: 'display/bidBoxDisplay.ts#isBidBoxTabId', kind: OTHER_AXIS, why: '탭 id 의 타입 가드. 글자를 만들지 않는다' },
  {
    key: 'display/bidBoxDisplay.ts#bidBoxActionOf',
    kind: OTHER_AXIS,
    why: '상태 → 그 상태에서 가능한 동작 행. 문면은 상태·명령 표에서 오고 이 함수가 금액·시각 글자를 만들지 않는다',
  },

  // ── 사건 축 ────────────────────────────────────────────────────────────────
  {
    key: 'display/caseDisplay.ts#stateDisplayOf',
    kind: OTHER_AXIS,
    why: '사건 상태 축의 표 조회 — 상태는 이 게이트가 금지하는 셋이 아니다',
  },
  {
    key: 'display/caseDisplay.ts#nextActionOf',
    kind: OTHER_AXIS,
    why: '상태 → 다음 동작 안내. 금액·시각 글자가 없다',
  },

  // ── 편집 세션 축 ───────────────────────────────────────────────────────────
  {
    key: 'display/editSessionDisplay.ts#termsOf',
    kind: OTHER_AXIS,
    why: '운영자가 적은 한 줄을 어휘 목록으로 쪼갠다. 입력 쪽이고 화면 문면을 만들지 않는다',
  },
  {
    key: 'display/editSessionDisplay.ts#valueBodyOf',
    kind: OTHER_AXIS,
    why: '입력 → 요청 본문. 화면 글자가 아니라 서버로 간다',
  },

  // ── 내 투찰 결과 축 ────────────────────────────────────────────────────────
  {
    key: 'display/myBidResultDisplay.ts#awardGapReading',
    kind: OTHER_AXIS,
    why: '차이 금액을 **값**으로 읽어 올린다(글자가 아니다). 그 값을 문면으로 바꾸는 것은 등재된 어휘이고, 그것을 그리는 카드는 그 어휘로 이미 의무다',
  },
  {
    key: 'display/myBidResultDisplay.ts#caseResultsById',
    kind: OTHER_AXIS,
    why: '결과를 사건 id 로 묶는다. 묶기는 구조이고 글자를 만들지 않는다',
  },
  {
    key: 'display/myBidResultDisplay.ts#rateAxesOf',
    kind: OTHER_AXIS,
    why: '집계의 비율 축 둘을 고른다. 비율 표기는 이 게이트가 금지하는 셋이 아니다',
  },

  // ── 알림 축 ────────────────────────────────────────────────────────────────
  {
    key: 'display/notificationDisplay.ts#groupNotifications',
    kind: OTHER_AXIS,
    why: '알림을 묶는다. 묶기는 구조이고 글자를 만들지 않는다',
  },
  {
    key: 'display/notificationDisplay.ts#notificationItemTestId',
    kind: OTHER_AXIS,
    why: 'test id 를 만든다. 보이는 글자가 아니다',
  },

  // ── 시작하기 축 ────────────────────────────────────────────────────────────
  {
    key: 'display/onboardingDisplay.ts#itemRowsOf',
    kind: OTHER_AXIS,
    why: '체크리스트 행 묶음. 문면은 항목 표에서 오고 금액·시각 글자가 없다',
  },
  {
    key: 'display/onboardingDisplay.ts#progressOf',
    kind: OTHER_AXIS,
    why: '완료/전체를 수로 낸다. 글자를 만들지 않는다',
  },
  {
    key: 'display/onboardingDisplay.ts#nextStepWithPending',
    kind: OTHER_AXIS,
    why: '다음 단계 id. 글자를 만들지 않는다',
  },

  // ── 개찰 결과 축 ───────────────────────────────────────────────────────────
  {
    key: 'display/openingResultDisplay.ts#confirmedAwardReading',
    kind: OTHER_AXIS,
    why: '확정 낙찰 금액을 **값**으로 읽어 올린다(위 차이 금액과 같은 관례 — 문면은 등재된 어휘가 만든다)',
  },
  {
    key: 'display/openingResultDisplay.ts#provisionalRankOneReading',
    kind: OTHER_AXIS,
    why: '가(假)1순위 금액·비율을 **값**으로 읽어 올린다. 같은 관례다',
  },

  // ── 프로필 축 ──────────────────────────────────────────────────────────────
  {
    key: 'display/profileDisplay.ts#qualificationStateOf',
    kind: OTHER_AXIS,
    why: '자격 선언 상태 축. 금액·시각·명령 어휘가 아니다',
  },
  {
    key: 'display/profileDisplay.ts#isHeadquartersRegion',
    kind: OTHER_AXIS,
    why: '본사 지역 코드의 타입 가드. 글자를 만들지 않는다',
  },
  {
    key: 'display/profileDisplay.ts#businessDetailsBodyOf',
    kind: OTHER_AXIS,
    why: '입력 → 요청 본문. 서버로 간다',
  },
  { key: 'display/profileDisplay.ts#capacityBodyOf', kind: OTHER_AXIS, why: '입력 → 요청 본문. 서버로 간다' },

  // ── 결과 단계 축 ───────────────────────────────────────────────────────────
  { key: 'display/resultDisplay.ts#isResultStage', kind: OTHER_AXIS, why: '결과 단계인가를 참·거짓으로 낸다' },

  // ── 표 조회의 틀 ───────────────────────────────────────────────────────────
  {
    key: 'display/stateDisplay.ts#displayOf',
    kind: OTHER_AXIS,
    why: '어느 표든 받는 조회의 **틀**이다. 자기 글자를 만들지 않고 물린 표의 문면을 그대로 낸다',
  },
  {
    key: 'display/stateDisplay.ts#displayOrUnknown',
    kind: OTHER_AXIS,
    why: '위 틀 + 표 밖 값의 보이는 폴백. **그 폴백은 서버가 준 코드를 그대로 보인다**(배포 비틀림을 조용한 빈칸으로 만들지 않으려는 설계) — 금액 표를 물리면 그 자리에 토큰 원문이 설 수 있고, 오늘 DOM fixture 는 그 분기를 그리지 않는다. 그 축은 신설 OPEN 으로 등재했다',
  },
  {
    key: 'display/stateDisplay.ts#unknownStateDisplay',
    kind: OTHER_AXIS,
    why: '위 폴백의 문면을 만드는 자리. 같은 등재를 가리킨다',
  },
];
