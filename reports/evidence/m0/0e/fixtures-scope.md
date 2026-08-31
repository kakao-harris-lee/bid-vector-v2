# M0 / 0E — `fixtures/manifest.yaml` 착수 (fixture-curator)

## 기준

| 자리 | 값 |
| --- | --- |
| base_sha | `14686dbf3bff4085203ffdcd931564fd1e36edf0` |
| head_sha | **리뷰 시점의 HEAD.** SHA 를 여기 박으면 커밋이 늘 때마다 낡으므로 range 로 적는다 — 커밋마다 이 값을 옮겨 적는 장치(선언 SHA 래칫)를 두지 않는다. 이 레인의 커밋 집합은 `git log --oneline 14686db..HEAD -- fixtures/ 'reports/evidence/m0/0e/fixtures-*.md'` 가 낸다 |
| 기준 문서 | `data-extract.md` (단일 기준) |
| legacy 기준 SHA | `ed4b06cbb8862c7cf121bb27d7cb962afe42270e`(`ed4b06c`) — read-only. **이 slice 는 legacy Python 을 실행하지 않았다.** `./bid-vector`는 `.gitignore` 된 symlink 라 **clean worktree 에 없다** — pinned SHA 와 `legacy_reference`의 재검증은 `fixtures/legacy-reference-index.json`(저장소 안 불변 산출물)과 **F-5**가 받는다 |
| 착수 근거 | `milestone-1.md:12` M1 선행 조건 · `milestone-0.md:94` 산출물 · `m1-blocking-analysis-v2.md` `N-1`/`T-1` |

## in_scope

- `fixtures/manifest.yaml`
- `fixtures/input/*.json` · `fixtures/expected/*.json` (`cases` 전건 — **분류와 무관하게** 전부 이
  레인이 쓴다. 수는 **F-7** 이 낸다)
- `fixtures/tools/check_legacy_numbers.py` (**F-3**) · `fixtures/legacy-reference-index.json` (**F-5**)
  — `manifest.yaml`의 `layout.other_files`가 둘을 선언한다
- `reports/evidence/m0/0e/fixtures-*.md`

## out_of_scope

- `docs/**` · `reports/evidence/m0/0a`~`0d` · `milestone-*.md` · `v2-지침서.md` — 병행 레인이 소유한다
- `bid-vector/**` — read-only 참고 자료
- legacy Python 실행 · 운영 DB read · KONEPS/LLM/Telegram/email 호출 — **하나도 하지 않았다**
- push · merge · 배포

## 이 slice 가 등재하는 것

**층은 둘이다** — `authoritative` 와 `insufficient-evidence`. `observed` 0건, `legacy-behavior` 0건.

**`insufficient-evidence` 는 운영자 결정 2026-08-31 이 만든 자리다** — `source.kind:
m0-derived-rule` case 를 `authoritative` corpus 에서 **제외**했다. 근거가 M0 산출 문서의 **자체 도출
acceptance** 뿐이라 `data-extract.md` §1의 세 층(`authoritative`·`observed`·`legacy-behavior`)
어디에도 들지 않고, 그 자리의 어휘를 §6이 준다(*"명세가 모호 | `insufficient-evidence`, 완료 gate
실패"*). **case 는 지우지 않았다** — 되돌림 경로는 `manifest.yaml` 의
`classification_policy.insufficient_evidence` 와 `next_steps` 가 적는다.

**같은 날 `source.kind` 자신을 전수 재판정했다.** 그 결정의 첫 적용은 이미 `m0-derived-rule`
이던 자리만 내렸고 **`source.kind` 를 하나도 바꾸지 않았다** — 근거가 실제로는 M0 자체 도출인데
`kind` 가 `operator-decision`·`official-doc`·`approved-spec` 으로 적힌 자리가 남았다.
**`operator-decision` 전건의 결정 문면을 원문으로 열어 대조**하고 `approved-spec`·`official-doc`
도 같은 눈으로 훑었다. 판정 축 셋(**한정어를 지웠는가 · 결정이 축만 정하는가 · 인용한 결정이
실재·해소됐는가**)과 적용 기준(case 가 선언한 `verifies` 를 대상으로 한다)은
`classification_policy.insufficient_evidence` 가 적는다. **활성 `OPEN` 을 결정처럼 인용한 자리는
없었다** — 인용된 결정 id 열넷을 활성 목록(`commands.md` **C-3** 이 세는 그 집합)과 대조해 전부
해소 상태임을 확인했다.

**같은 날 운영자가 판정 기준 하나를 더 정했다.**

*결정 축어 — 물음과 답을 그대로 옮긴다(대신 판단하지 않는다).*
물음: *"결정 기록의 「기록자 bullet」이 `classification_policy.authoritative` ②의 「커밋된 운영자 결정
기록」에 드는가?"* — 답: ***"든다"***.

*이 레인이 그 답을 적용한 범위는 이 레인의 판단이다*(운영자 발화가 아니다). **②는 「운영자 발화」로
좁혀지지 않는다** — 커밋된 결정 기록 절 안의 **기록자 bullet 문면도 ②의 근거로 선다.** 이 결정으로
전수 재판정이 `floor-applicability-001` 에 걸었던 **규칙 축의 강등이 풀렸고**(그 case 가 재는 문면은
`0a2/decisions.md:317` 의 `OPEN-DEC-09` 결정 절 본문 bullet 이다), 기록자 bullet 에 기댄 다른
case 들의 유지도 같은 잣대로 정당화된다. **그 case 자신은 지금 `authoritative` 가 아니다** — 뒤이은
어휘 판정이 다른 축에서 다시 내렸다(아래). **그 문면이 커밋된 결정 기록 안에 있어야 한다는 한계는 이
답이 새로 건 것이 아니라 ② 자신의 문면이 이미 갖는 것이다** — 그래서 M0 산출 문서가 스스로 쓴
acceptance·검증 방법은 이 기준으로 서지 않는다. 정본은 `manifest.yaml` 의
`classification_policy.authoritative` 가 갖는다.

**같은 날 운영자가 판정 대상을 하나 더 넓혔다 — 물음과 답을 그대로 옮긴다(대신 판단하지 않는다).**
물음: *"verdict-001·002가 SkipReason 구체 어휘 `CapacityHold`·`LowPriority`를 authoritative golden으로
고정하고 있다. OPEN-DEC-05는 『Skip 판정 + 사유 코드 의무』라는 축만 정했고 이 두 리터럴 값의 출처는
data-dictionary 자체 도출뿐이다. 어떻게 처리할까?"* — 답: ***"insufficient-evidence 강등"***
(어휘 명시 승인·합성 어휘 재작성 두 선택지를 물리치고 택함).

**이 레인이 그 답을 적용한 범위는 이 레인의 판단이다**(운영자 발화가 아니다). 판정 대상에
**기대값이 도메인 어휘로 고정하는 리터럴 토큰**(enum 값 · 상태/사유 코드 · 판정 어휘 · 고정 문자열 ·
도메인 키 이름)을 넣고, `authoritative` 로 남는 case 전수에 세 출처 문면 대조를 한 번 돌렸다.
fixture 파일의 형식·schema 자체(`data-extract.md` §3가 정하는 것)는 대상에서 뺐다 — 어휘를 재는
것이지 그릇을 재는 것이 아니다. **판정이 갈릴 수 있는 자리는 바꾸지 않고 다섯 가족으로 묶어
운영자에게 올렸다.** 정본은 `manifest.yaml` 의 `classification_policy.insufficient_evidence` 가 갖는다.

**그 에스컬레이션에 운영자가 답했다 — 4문 4답.**

*결정 축어 — 물음과 답을 그대로 옮긴다(대신 판단하지 않는다). 물음은 이 레인이 세운 가족 정의
그대로이고, 네 답 모두 「유지」다 — 분류를 바꾼 답은 없다.*

- **가족 A**(M0 sealed 정의 이름이 기대값에만 실림) — ***"유지 + 강등선 성문화"***. 강등선은
  **「case 의 `verifies` 가 그 이름을 검증 대상으로 잠그는가」**로 확정하고, 기대값이 나르는 미승인
  이름은 **어휘 승인 대상**으로 정책에 기록한다.
- **가족 B**(corpus 자작 결과·사유 코드) — ***"그릇으로 간주, 유지"***. 자작 그릇은 어휘 비준이
  아니며 실제 어휘는 **M1/M2 계약 설계가 정한다**.
- **가족 C**(개념 승인·표기 변형) — ***"개념 기준 통과"***. 표기 통일은 **M1/M2 계약 설계로 이월**.
- **가족 D·E**(legacy·조달청 유래 / 입력 되울림 실값) — ***"둘 다 통과"***.

*이 레인이 그 답을 적용한 범위는 이 레인의 판단이다*(운영자 발화가 아니다). 그 선을 이 corpus 의
case 에 대어 보면 **내려간 넷과 남은 가족들이 한 잣대의 양면이다** — 이름을 `verifies` 가 **잠그면**
내려가고(`verdict-001`·`002`·`floor-applicability-001`, 그리고 기대값이 같은 어휘를 잠근
`verdict-003`), **나르기만 하면** 남는다. **남는 것이 승인된 것은 아니다** — 그 이름들이 어휘 승인
대상이라는 것은 위 답이 정했고, 그 자리를 case 단위 비준과 **다른 축**으로 두는 것은 이 레인이다.
가족별 근거와 강등선 문면은 위 정본이 갖는다.

**같은 날 운영자가 그 강등선을 이름에서 표현으로 넓혔다 — `floor-shortfall` 출력 표현.**

*결정 축어 — 물음과 답을 그대로 옮긴다(대신 판단하지 않는다).*
물음: *"`floor-shortfall-002`·`003`의 `verifies`가 승인된 의미(N/N+1 경계·최소 표본 150·
Unmeasurable·versioned 정책)를 넘어 『임계 사정률·빈도·분자·분모·범위·정책 version』 출력 표현
묶음 전체와 유리수 표현을 authoritative로 잠그고 있다 — 그 묶음을 승인한 결정이 없다. 어떻게
처리할까?"* — 답: ***"verifies 좁혀 유지"*** (「insufficient-evidence 강등」·「출력 묶음을 명시
승인」 두 선택지를 물리치고 택함).

*이 레인이 그 답을 적용한 범위는 이 레인의 판단이다*(운영자 발화가 아니다). 두 case 의
`classification` 은 `authoritative` 그대로 두고 **`verifies` 와 그 case 가 검증을 주장하는 범위
서술만** 좁혔다. 잠금에서 뺀 **출력 표현 묶음 여섯 항목**과 **유리수(분자/분모) 표현**은 가족 A 와
같은 자리로 보내 **어휘·표현 승인 대상**으로 기록했다 — 강등선이 「이름을 잠그는가」에서 「이름·
표현을 잠그는가」로 넓어진 것이고, **나르기만 하면 남는다**는 반대편은 그대로다. **기대값·입력
파일과 해시는 바꾸지 않았다.** 정본은 `manifest.yaml` 의 `classification_policy.insufficient_evidence`
가 갖고, 잠금이 풀린 축은 같은 파일 `uncovered_axes` 의 「승인 부재로 **검증 주장에서 내린** 축」이
받는다.

**같은 날 운영자가 그 선언을 기계가 읽는 계약으로 옮기게 했다 — assertion path 계약화.**

*결정 축어 — 물음과 답을 그대로 옮긴다(대신 판단하지 않는다).*
물음: *"B3 때 택한 『verifies 좁혀 유지』가 불충분하다는 판정이다 — 기대값 JSON 자체에 미승인 출력
표현(임계 사정률·분자/분모·band·policyVersion)이 정확한 golden으로 남아 있고, manifest에 『어느 JSON
경로가 assertion인가』 계약이 없어 산문 선언만으로는 M1 테스트가 전체 파일을 비교하는 것을 못 막는다는
논지다. 어떻게 할까?"* — 답: ***"assertion path 계약화"*** (「expected를 최소 projection으로」·「두 case
강등」을 물리치고 택함). 답이 든 선택지 문면: *"manifest에 M1 소비 테스트가 비교해야 하는 JSON 경로를
case 필드로 명시해 승인된 projection만 계약이 되게 함. 기대값 파일 무변경. 스키마 차원의 변경이라 전
case 적용 설계가 따라옴."*

*이 레인이 그 답을 적용한 범위는 이 레인의 판단이다*(운영자 발화가 아니다). 필드를 `verified_paths` 로
두고 `authoritative` **전건**에 `verifies` 에서 도출한 경로 목록을 달았다. `insufficient-evidence` 전건은
필드를 두지 않았고 **생략은 「계약 없음」이지 「전체 비교」가 아니다** — 그 case 들은 §6 완료 gate 실패라
계약할 assertion 이 없다. 도출 규칙 셋(**값을 주장하면 든다 / 존재·형태만 주장하면 들지 않는다 / 동반
산출·입력 되울림은 들지 않는다**)은 `manifest.yaml` 의 `schema.extensions.verified_paths` 가 갖는다.
**이 필드는 앞선 강등선의 기계적 표현이다** — 「잠그는가」에서 잠그는 쪽이 목록이고 나르는 쪽이 목록
밖이라, 가족 A 의 네 이름과 가족 B 의 자작 그릇이 **경로 밖에 놓이는 것이 기본**이 됐다. 정본은
`manifest.yaml` 의 `classification_policy.insufficient_evidence` 가 갖는다.

**셈을 여기 옮겨 적지 않는다** — case 수 · **분류별 분포** · `source.kind` 분포와 그 교차 ·
`uncovered_axes`/`insufficient_evidence`/`access_approval_required` 수는 전부 **F-7** 이 낸다.
도메인별 건수도 같은 명령이 낸다(분류를 섞지 않으려면 `kind x class` 줄을 함께 본다).

## `milestone-1.md:12`("검증 fixture 중 `authoritative` case 준비")를 어디까지 만족하는가

**부분 만족이고, 2026-08-31 결정과 그날의 전수 재판정으로 만족도가 두 번 내려갔다.**
`authoritative` 로 서는 case 수가 줄었고(F-7 의 `classification` 줄), 그만큼 `data-extract.md`
§4의 도메인별 최소 corpus 를 덮는 몫이 작아졌다. **내려간 case 가 덮던 축은 사라진 것이 아니라
`uncovered_axes` 로 옮겨졌다** — 첫 적용이 만든 넷은 `floor-applicability`(하한 적용 범위·시행일) ·
`koneps-collection`(필드 계약 강제, 코드·라벨 분리) · `ml-boundary`(결측 provenance, 누수 차단
시그니처) · `license`(허용업종 단독 보유의 임시 처리)이고, **전수 재판정이 셋을 더했다** —
`capacity-gate`(금액 capacity 게이트의 무조건 acceptance) · `license`(판정의 「동반 산출」과 요건
provenance) · `money-basis`(`Unknown` 과세 금액의 산술 비교 차단). **이어진 라운드가 `money-basis`
(감시 경로와 검색 경로의 basis 일치) 축을 하나 더 세웠다** — `money-basis-003` 의 유일한 근거가
`regression-ledger.md` R-BASIS-01 의 자체 acceptance 였고, 그것은 커밋된 결정 기록이 아니라 M0
산출 문서라 기록자 bullet 기준으로도 서지 않는다. **어휘 판정이 다시 하나를 세웠다** —
`verdict`(`SkipReason` 구체 어휘). `verdict-001`·`002` 가 `OPEN-DEC-05` 로 떠받쳐지는 것은
「문장이 아니라 사유 코드로 구분된다」는 **규칙**까지이고, 기대값이 함께 고정하는 두 **어휘**
(`CapacityHold`·`LowPriority`)는 `data-dictionary.md` §3.6의 자체 정의에서만 나온다.
**이어진 어휘 전수 스캔이 같은 축에 둘을 더 붙였다** — `verdict-003`(기대값이 그 `LowPriority`
와 `BidNow` 를 잠근다)과 `floor-applicability-001`(`OutOfScope`). 규칙의 근거는 둘 다 성한데
기대값이 미승인 어휘를 잠그는 자리다.
**출력 표현 결정이 축을 하나 더 세웠다 — 이번에는 case 가 내려가서가 아니다.**
`floor-shortfall-002`·`003`은 `authoritative` 로 **남았고** 만족도를 더 내리지 않는다. 대신 두 case 가
자기 `verifies` 에서 **출력 표현 묶음과 유리수 표현**을 내려놓았고, 그 자리를 `uncovered_axes` 의
「승인 부재로 **검증 주장에서 내린** 축」이 받는다. **덮개를 잃은 것과 처음부터 주장하지 말았어야 할
것을 내려놓은 것은 다르다** — 이 축은 뒤쪽이며 그 구별을 그 항목의 `why_blocked` 가 적는다.
**재판정은 앞선 넷 중 하나의
「덮개」 서술도 뒤집었다** — `koneps-collection` 은 그 축에 `authoritative` 로 남는 case 가 있다고
적었으나 **전건 내려갔다.** `floor-applicability` 도 **어휘 판정으로 전건이 됐다** — `-001` 의
**규칙**은 기록자 bullet 결정으로 서지만 그 case 의 `verifies` 가 상태의 이름(`OutOfScope`)까지
함께 고정하고 그 어휘는 `data-dictionary.md` 가 스스로 더한 variant 다. 원래 막혀 있던 축은
그대로 남는다.
그 목록을 비우지 않고 그럴듯하게 채우지도 않았다. **수는 여기 박지 않는다 — F-7 이 낸다.**

**§6 이 이 상태에 완료 gate 실패를 건다.** 그러므로 `milestone-1.md:12` 는 **이 결정이 되돌려지기
전에는 이 corpus 만으로 충족되지 않는다** — 되돌림은 운영자가 그 acceptance 들을 업무 규칙으로
명시 승인하는 것이고, `license-011` 은 그에 더해 활성 `OPEN-QUAL-11` 이 닫혀야 한다.

**분류가 `authoritative`인 것과 승인된 것은 다르다.** `authoritative` 로 남은 것을 포함해 모든
case 의 `review.approved_by_user`가 `false`, `review.codex_verdict`가 `pending`이다.

## 알려진 제한

1. **`observed` 층이 0건이다.** 실제 payload 형태의 함정(누락 필드 조합, 인코딩, 천 단위
   구분자, 실제 키 이름 변형)을 이 corpus 가 재현하지 못한다. 운영 데이터 접근 승인이 선행한다
   (`access_approval_required` **A-1**).
2. **`legacy-behavior` 층이 0건이라 differential 판정(§6)의 한쪽 항이 비어 있다.** legacy 순수
   함수 실행은 승인 대상이며 요청하지 않았다(**A-3**).
3. **numeric discipline 검사가 정수 counts 를 보지 않는다.** 면제 목록과 사유는
   `manifest.yaml`의 `numeric_discipline.check.exempt`가 적는다.
4. **`fixtures/expected/`의 위치가 `data-extract.md` §3 예시와 다르다.** 이 slice 의 쓰기 범위가
   `fixtures/`로 한정된 결과이며 `manifest.yaml`의 `layout.note`가 선언한다.
5. **`license-011`의 기대값은 잠정이다** — `OPEN-QUAL-11` 결정 전 임시 처리이고 `provisional`
   필드가 그 사실을 나른다. **그래서 `authoritative` 가 아니다** — 활성 `OPEN` 의 임시 처리를
   authoritative golden 으로 고정하지 않는다.
6. **legacy 좌표의 재검증이 두 갈래다.** `fixtures/legacy-reference-index.json`이 pinned commit 과
   39개 좌표(§2 목록 15 + `legacy_reference` 24)의 **git object id** 를 저장소 안에 고정한다.
   legacy 체크아웃이 있으면 object id 까지 다시 잰다(**F-5.3**). 없으면 잴 수 있는 것은 **그때
   무엇을 봤는지의 고정과 manifest 와의 일치**뿐이다(**F-5.1**·**F-5.2**) — 그 index 자체가
   legacy 저장소에 대해 옳다는 것은 체크아웃 없이 확인되지 않는다.
7. **`insufficient-evidence` case 는 M1 golden 이 되지 않는다.** 근거가 M0 산출 문서의 **자체
   도출 acceptance** 뿐이고 그것을 세운 운영자 결정·조달청 문서·`v2-지침서.md` 문면이 없는
   case 다. 앞선 라운드는 이것을 **「조건부 `authoritative`」**로 두었고 — 운영자 결정
   2026-08-31 이 그 자리를 닫았다: **`authoritative` 에서 제외한다.** 되돌리기 쉬운 쪽을
   택한 것이며(미승인 규칙이 golden 이 되는 것을 먼저 막는다) case 는 지우지 않았다.
   `source.kind: m0-derived-rule`이 그 자리를 표시하고 근거·되돌림 경로는 `manifest.yaml`의
   `classification_policy.insufficient_evidence`가 적는다. 어느 case 가 드는지와 그 대응이
   전건인지는 **F-7**의 `kind x class` 줄이 낸다.
   **같은 날의 전수 재판정이 이 목록을 늘렸다** — 결정 문면을 원문으로 열어 보니 `kind` 가
   `operator-decision`·`official-doc`·`approved-spec` 이면서 실제 근거는 M0 자체 도출인 자리가
   있었고, 그 case 들의 `source.kind` 를 내리고 `classification` 을 따라 내렸다. 각 case 의
   `change_history` 가 어느 축(한정어 삭제 / 결정이 축만 정함)에 걸렸는지와 실제 근거를 적는다.
   **어휘 판정이 이 목록을 또 늘렸다** — 규칙은 결정이 떠받치는데 기대값이 함께 고정하는
   **리터럴 어휘**의 출처가 M0 자체 도출뿐인 자리다. 그 case 들의 `change_history` 는 어느
   토큰이 어느 문면에 없는지를 적는다.
8. **`insufficient-evidence` 라는 이름이 `manifest.yaml` 안에서 두 자리에 쓰인다.** case 의
   `classification` 값과, **fixture 가 아예 없는 후보** 목록(`insufficient_evidence`)이다.
   둘 다 §6의 어휘를 빌리지만 앞은 `cases` 의 셈에 들고 뒤는 들지 않는다 — manifest 의 그
   목록 머리 주석이 그 구별을 적는다.

## evidence 패키지에서 성립하지 않는 항목 (`N/A + 사유`)

- `differential.json`: **N/A** — Python/V2 차이 판정은 두 항이 있어야 성립하는데 V2 구현이
  아직 없고 `legacy-behavior` case 도 0건이다. legacy 순수 함수 실행은 승인 대상이며 요청하지
  않았다(`access_approval_required` **A-3**). 그 판정은 fixture 를 소비하는 첫 구현 slice 의 몫이다.
- `golden-manifest.json`: **N/A** — 이 slice 는 fixture 를 **소비**하지 않고 **등재**한다. 그 파일이
  담을 것(사용 fixture id · 출처 분류 · SHA-256)을 `fixtures/manifest.yaml` 이 case 마다 이미 갖고
  **F-1** 이 SHA-256 대조를 돌린다. 별도 파일로 복제하면 두 벌이 갈린다.

## rollback

`fixtures/` 디렉터리 전체와 `reports/evidence/m0/0e/fixtures-*.md`를 지우면 base 상태다.
다른 경로에 쓴 것이 없고, 이 slice 는 어떤 실행 코드도 만들지 않았다.
