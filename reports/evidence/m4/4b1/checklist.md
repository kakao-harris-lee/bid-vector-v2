# checklist.md — M4/4B-1 리뷰 준비도

## 리뷰 요청 조건 (evidence-pack 스킬 기준)

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 경로>`
      결과 없음(commands.md 「clean-tree 게이트」, 양성 대조 포함).
- [x] scope.md의 acceptance_commands(S-0~S-6) 전부 exit 0으로 commands.md에 기록됨.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `clean check` 전건(ktlint·
      detekt·cpd·sizeGate·domainApiTypeGate·domainSourceReferenceGate·moduleDependencyGate·
      gateExecutionGate·qualityBaseline 포함).
- [x] 변경된 fixture와 정책 version의 근거가 기록됨 — golden-manifest.json(신설 8건) ·
      `VerdictLadderPolicyData`는 legacy-behavior 값을 test 정책에만 쓴다(아래 §3).
- [x] 알려진 제한과 rollback 방법이 기록됨(아래 「알려진 제한」·`rollback.md`).
- [x] secret 스캔 통과 — commands.md 기록.

## 이 slice 고유 확인

### 1. 값 획득 축 판정의 실측(설계 검토 (2)) — 4A 와 결론이 다르다

설계 검토는 「`decision`은 도메인 모듈이라 `app` conformance 실행자가 커널을 직접
부르는 것이 1C·1D 관례라 4A 처럼 `internal`로 내릴 수 없다」고 판단했다 — 이 slice가
실측으로 확인한 것:

| 공개 표면 | 손에 넣은 주체가 할 수 있는 것 | 처분 | 실측 근거 |
| --- | --- | --- | --- |
| `VerdictLadder.judge`(public) | 값을 넣고 판정을 받는다 | **연다** | `VerdictExecutorsTest`(corpus 82 tests, `app` 모듈이 실제로 이 함수를 직접 호출) — 판정을 받는 것 자체가 부작용 0(순수 함수)임을 정상 배선이 증명 |
| `FloorOverrideValidation.validate`(public) | override rate 검증 결과를 받는다 | **연다** | 같은 근거, `verdict-012` |
| `Verdict.BidNow`/`Review`/`Skip` 생성자(`internal`) | 사다리를 지나지 않고 판정을 지어낸다 | **닫는다** | 임시 clone 컴파일 거부 실측(아래 §2) |
| `SkipReason`·`BidNowReason`·`ReviewReason`·`MlUnavailableReason` 하위 타입 생성자(data class 는 `internal`, data object 는 대상 아님) | 우회의 존재·ML 가용성을 거짓으로 주장하는 reason 을 지어낸다 | **닫는다** | 같은 실측(아래 §2) |
| `Verdict`·reason 읽기 프로퍼티 | 결과 소비 | **연다** | `VerdictExecutors.kt`가 필드를 직접 읽어 projection 을 만든다(위조가 아니라 읽기) |
| `VerdictLadderPolicyData`·`LadderInput`·`UnitScore`·`PlausibilityBand`(전부 public 생성자) | 사다리 입력을 구성한다 | **연다**(생성 불변식만 건다) | 입력은 호출부가 통제하는 값이라 통로 보호 대상이 아니다 — [Verdict]와 다른 축(4A `EditSessionPolicyData`도 같은 판단) |

**「`Verdict`를 얻으면 무엇을 할 수 있는가」** — 판정 결과는 읽기 값이다. 이 결과를 들고
저장·발행·알림 등 어떤 부작용 API 도 이 slice 에는 없다(조합 use case 는 4B-2, out_of_scope).
그래서 4A(획득이 저장 권한을 낳음)와 달리 **획득 자체가 위협이 아니고, 위협은 위조뿐**
이라는 설계 검토의 판단이 실측으로 확인된다.

### 2. 값 위조 축 실측 — 임시 clone 컴파일 거부(커밋 뒤 별도 절 참고)

`commands.md`의 「값 획득 축 실측」 절에 다섯 형태(Verdict 세 하위 타입 직접 생성자 +
BidNowReason/ReviewReason 하위 타입 직접 생성자) 전부의 거부 원문을 기록한다.

### 3. 조사의 여섯 실패 형태를 어떻게 뒤집었는가

| # | legacy 실패 형태(조사) | V2 구성형(이 slice) | 코드 근거 |
| --- | --- | --- | --- |
| 1 | 두 `skip`이 대입이 아니라 부작위(N-3) | 모든 분기가 결과를 명시 반환, 가변 변수 없음 | `VerdictLadder.judge`의 guard 함수 체인(`?:`), 각 함수가 `Verdict?`를 명시 반환 |
| 2 | 사유가 문장이고 구분 값이 코드에 없음(N-1·N-2) | `SkipReason`/`BidNowReason`/`ReviewReason` sealed + 값 payload, 문자열 사유 필드 없음 | `SkipReason.kt`·`BidNowReason.kt`·`ReviewReason.kt` |
| 3 | force-bid 우회와 정상 승격이 같은 문장(N-4) | `BidNowReason`을 `PriorityAboveBidNowThreshold`·`ForceBidOverride` 둘로 분리 | `VerdictLadderTest`의 「force-bid 우회」 case, `verdict-008` |
| 4 | ML 부재가 fail-open(N-9·N-10) | 점수 nullable + 부재 시 `Review(MlUnavailable)`, `BidNow`로 가는 경로가 타입에 없음 | `VerdictLadder.judge`의 `priorityScore?.value ?: return Review(...)`·`forceBidOutcome`의 null 가드, `VerdictLadderPropertyTest` negative property |
| 5 | 임계 다섯이 두 출처, 결과에 안 실림(N-5) | 정책 데이터 한 자리(`VerdictLadderPolicyData`) + 각 reason 이 쓴 임계·실제 값을 함께 나름 | `BidNowReason.PriorityAboveBidNowThreshold(priority, threshold)` 등 |
| 6 | `review > bidNow`를 판정 시점에 조용히 수리(N-6) | 생성 불변식으로 그 조합 자체가 구성 불가 | `VerdictLadderPolicyData.init`의 `require(reviewThreshold <= bidNowThreshold)`, `VerdictLadderPolicyDataTest` |

부가로 **R-3**(legacy `probability_score`에 상한이 없었다)도 `UnitScore`의 `[0,1]` 생성
불변식으로 닫았다(`UnitScoreTest`).

### 4. `OPEN-DIC-03` 종결 근거와 신설 `OPEN`

`data-dictionary.md` §3.6·§13.2를 D-M4-6 (a)·조사 §3.1 실측으로 채우고
`capability-map.md`의 `OPEN-DIC-03` 행을 `~~OPEN-DIC-03~~`으로 닫았다(운영자 결정
2026-09-09). **신설 `OPEN-4B1-OFF-LADDER-DROPS`**(`capability-map.md` §14) — 사다리 밖
드롭 열셋(조사 §3.2 D-1~D-13) 중 무엇이 `SkipReason`류이고 무엇이 `Verdict` 밖 결과
타입인지는 4B-2(조합 use case, 「무엇을 판정 대상으로 삼는가」)가 정한다 — 이 slice가
멋대로 좁히지 않았다.

### 5. 역방향 파급 grep

`data-dictionary.md` — §3.6·§13.2 편집으로 +13줄(순증). 아래 좌표를 가리키는 참조 1건
(`reports/evidence/m0/0c/commands.md:223` → `data-dictionary.md:1551,1554`)이 영향권이나
**4B-1 in_scope 밖의 닫힌 slice evidence**이고, 이미 `reports/evidence/m1/1c/checklist.md:148`
이 「닫힌 slice evidence의 좌표가 낡는다」는 사실 자체를 선언해 두었다 — 새로 만드는
낡음이 아니라 그 원칙이 이미 포함하는 사례로 알려진 제한에만 등재한다(commands.md 상세).
`capability-map.md`의 `OPEN-DIC-03` 편집(+1줄)보다 위쪽 좌표만 참조가 있어 무관함을
확인했다.

## 알려진 제한 (이 slice 종료 시점)

1. **사다리 밖 드롭 열셋의 어휘 분류**는 4B-2 소관(`OPEN-4B1-OFF-LADDER-DROPS`, 신설).
2. **임계 다섯의 운영 값**은 legacy-behavior 로만 test 정책에 쓰였다 —
   `OPEN-4B1-LADDER-THRESHOLDS`(운영자 승인 대상).
3. **용량이 네 번 세는 것의 합쳐진 효과**(조사 §7.1)와 **하한 override 가 판정을
   얼마나 바꾸는가**(조사 §6.3)는 이 slice 가 재측정하지 않았다(`OPEN-4B1-05`·
   `OPEN-4B1-06`, 조사 §10) — 재측정이 필요하면 별도 slice.
4. **`review_required`(ML regime 신호, 조사 §7.3)를 V2 어느 층이 만드는가**는 미정
   (`OPEN-4B1-07`) — 이 slice의 `MlUnavailableReason`은 「점수가 없다」만 다루고
   「점수는 있지만 ML 이 review 를 권고한다」는 다루지 않는다.
5. **`LicenseVerdict.Ineligible`과 `Verdict`의 관계**(참조 vs 복제, `OPEN-4B1-02`)는
   미정 — 4B-1은 그 축을 건드리지 않는다(out_of_scope).
6. **`AnalysisBudgetExhausted`·`SimilarityProjectionNotReady`가 `Verdict` 안인지
   밖인지**(`OPEN-4B1-03`) — 이 slice의 `Verdict`에는 그 갈래가 없다(사다리 밖).
7. **verdict-001~004(insufficient-evidence)는 손대지 않았다** — 승격은 운영자 승인
   사항이고 Phase 6에서 팀장이 요청한다(scope.md ⑧).
8. **조합 use case(4B-2)가 아직 없다** — `VerdictLadder.judge`를 실제 파이프라인에
   배선하는 코드는 이 slice 밖이다.
9. **닫힌 slice evidence의 낡은 좌표 1건**(§5 참고, 알려진 제한으로만 등재, 수정하지
   않는다).

## rollback

정본은 `rollback.md`.
