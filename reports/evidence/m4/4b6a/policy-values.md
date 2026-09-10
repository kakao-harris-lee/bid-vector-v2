# M4/4B-6a 정책 값 — `OPEN-4B6A-POLICY-VALUES`

> **지위: 승인 대기.** 값은 legacy-behavior(키워드)와 2E 기존 승인 값(상한) 그대로다 —
> 이 slice가 새로 지어낸 수치는 없다. 정본은 이 문서 — 값을 바꾸려면 이 문서를 먼저
> 갱신한다.

## §1 synthesisVersion

| 항목 | 값 | 근거 |
| --- | --- | --- |
| version | `"v1"` | 이 slice가 정의하는 최초 합성 규약(D-4B6A-1~4). 규약을 바꾸면 이 값과
`TextSynthesisTest` golden을 함께 바꾼다(D-4B6A-3 KDoc). |

## §2 keywords(14)

| 항목 | 값 | legacy 좌표 |
| --- | --- | --- |
| 목록 | `통합·고도화·운영·유지관리·24시간·대규모·다기관·클라우드·센터·실시간·연계·보안·이관·플랫폼` | `bid-vector` 저장소 `app/services/opportunity_analysis/base.py`의 `_OpportunityAnalysisBase.EXECUTION_COMPLEXITY_KEYWORDS`(14개 튜플, verbatim) |
| 매칭 방식 | 소문자 부분 문자열, 중복 출현 1회 | 같은 파일 `opportunity_analysis/scoring.py`
`_estimate_execution_complexity_score`의 `sum(1 for keyword in ... if keyword in
project_text)`(4B-5 D-4B5-5가 인계한 산식) |

## §3 textMaxChars

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 상한 | `4000` | `config/quality/contract-policy.properties`의 `embedding.text.max-chars=4000`
(2E 기존 승인 값)과 **같아야 한다**(D-4B6A-4) — `OpportunityPolicyDataTest`가 그 파일을
직접 읽어 대조한다. 두 자리가 어긋나면 4D-2 gateway가 거부한다(2E 계약). |

## 승인 대상

이 문서 §1~§3 값 전부(`OPEN-4B6A-POLICY-VALUES`) — slice 종결 시 팀장이 사용자 승인
기록을 이 문서에 추가한다.

## change_history

| 일자 | 변경 | 근거 |
| --- | --- | --- |
| 2026-09-11 착수 | §1~§3 값 등재, 승인 대기 | 구현 레인 — legacy 실측(`EXECUTION_COMPLEXITY_KEYWORDS`)·2E 기존 값 대조 |
