# Slice 계약 — M0 / 0A3 결정 근거 정정 (X-1) + §10.1 형태 7

```yaml
milestone: m0
slice: 0a3-decision-basis-correction
base_sha: 48151b9   # 0B Codex 4차 approve verdict 등재 직후의 HEAD
head_sha: 03d7240   # 이 scope 커밋의 직전 커밋. 「이 커밋에 대해서는 주장하지 않는다」 참조
in_scope:
  - reports/evidence/m0/0a2/decisions.md      # X-1 정정 한정 + provenance 등재
  - reports/evidence/m0/0a2/checklist.md      # §10.1 형태 7 등재 한정
  - docs/discovery/capability-map.md          # §13 X-1 서술 한정
  - reports/evidence/m0/0a3/
out_of_scope:
  - capability-map.md 의 X-1 외 모든 서술 (X-2~X-6 포함 — 아래 「(A) 결정」 참조)
  - capability-map.md §13 밖의 X-1 프레이밍 (아래 「발견했으나 고치지 않은 것」 O-2)
  - docs/discovery/regression-ledger.md (0B Codex approve로 확정)
  - 다른 M0 산출물: data-dictionary(0C), ADR(0D), fixtures
  - 활성 OPEN 45건 전부 — 해소하지 않는다
  - Kotlin/Spring/Python 애플리케이션 코드
  - .claude/ 하네스
  - bid-vector/ symlink 아래 기존 저장소 수정 (읽기 전용)
acceptance_commands:
  - "N/A — 문서 slice. 아래 A1~A5를 checklist.md로 대조"
rollback: "N/A — 문서 산출물은 git revert로 복구"
```

작성: 2026-08-27, v2-slice-pipeline. 초안은 `_workspace/m0-0a3/00_draft_scope.md`이며
이 파일이 정본이다 — 초안의 `base_sha: TBD`를 **`48151b9`**로 확정하고,
초안에 없던 **A5**(§10.1 형태 7)를 팀 리드 지시로 더했다.

## 왜 이 slice가 필요한가

0C 선행 조사(`_workspace/m0-0c/01_scout_data_dictionary_preflight.md` §0.6 X-1)가
**`decisions.md`의 `OPEN-STR-01` 결정 근거 서술이 legacy와 어긋난다**는 것을 찾았다.

- 서술: `Project.budget_estimate`는 **추정가격(ex-VAT)**이고, 기초금액과의 차이는
  **VAT·사정률만큼 체계적**이다.
- 실측(`git show ed4b06c:`로 직접 열어 확인, 출력은 `commands.md` **C-1**):
  `app/domain/money.py:30`이 `BUDGET_ESTIMATE`를 **"추정가격: 부가세 포함 추정 총액"**으로
  선언하고, `app/schemas/bid_summary.py:69-77`이 **"저장된 추정가격의 부가세 포함 여부가
  이력상 일관되지 않다"**고 legacy 스스로 공시한다.

**이 프레이밍은 팀 리드가 분석 단계에서 만들었고** 산출물과 결정 기록으로 승격됐다
(§10.1 **형태 2**의 사례다).

**결정 자체는 유효하다.** 두 금액이 다른 basis라는 것과 운영자 의도가 기초금액이라는 것은
그대로이고 `legacy-defect` 판정도 그 위에 선다. 무너지는 것은 **"그 차이가 VAT만큼"이라는
정량 서술**뿐이다.

0B ledger의 같은 프레이밍은 commit **`3eb5a6e`**에서 이미 정정됐다. 남은 것은 그때
**out_of_scope였던 두 파일**이다.

두 번째 일은 **§10.1에 형태 7을 등재**하는 것이다. 0B가 그 근거를 세웠으나
**§10.1이 `reports/evidence/m0/0a2/checklist.md`에 있어 0B의 out_of_scope**였고,
0B는 판단만 기록하며 *"표를 고치려면 별도 slice 계약이 필요하다"*고 적었다
(`reports/evidence/m0/0b/commands.md` **C-8.5**). **이 slice가 그 계약이다.**

## acceptance 항목

- **A1.** `decisions.md`의 `OPEN-STR-01` 절과 `capability-map.md` §13에서 **ex-VAT 단정과
  "VAT만큼" 정량 서술이 제거**되고, legacy 실측(`money.py` · `bid_summary.py`)이 근거로
  달린다. **결정 내용(기초금액, `legacy-defect` 판정)은 바뀌지 않는다.**
- **A2. `decisions.md`의 provenance 규약을 지킨다.** 이 파일은 **원본 문장을 지우지 않고**
  취소선 + 인용 블록으로 정정하며, **「원본 절에 대한 slice 변경 — 전수」 목록과
  재현 명령(`git diff -U0 6a4e49b -- reports/evidence/m0/0a2/decisions.md`)에 이 정정을
  등재**한다. 그 목록이 갱신되지 않으면 A2 실패다.
- **A3.** 정량 서술이 사라진 자리에 **그 축을 소유하는 활성 `OPEN`**이 적힌다.
  **새 `OPEN`을 만들지 않는다.**
  > **팀 리드 지시와 어긋난 지점 — 소유자는 `OPEN-QUAL-10`이 아니라 `OPEN-REG-05`다.**
  > 지시 메시지는 *"그 축은 활성 `OPEN-QUAL-10`이 소유한다"*로 왔으나 **0B가 그 귀속을
  > 이미 반증하고 바로잡았다.** `OPEN-QUAL-10`은 **시공능력평가금액 축**(공고 게시 요건
  > `cnstrtnAbltyEvlAmtList` ↔ 운영자 보유액)이고, 기초금액↔추정가격의 과세 처리는
  > **`OPEN-REG-05`**(`docs/discovery/regression-ledger.md` §9, 활성)가 소유한다.
  > 근거는 `reports/evidence/m0/0b/scope.md`의 **verifier F-2** 두 라운드다 —
  > 계열 A 스윕이 `R-BASIS-01`~`OPEN-QUAL-10`을 *"오탐 — 다른 금액 축"*으로 판정했는데
  > 산출물에는 그 귀속이 남아 있었고, `OPEN-REG-05`를 신설해 옮겼다.
  > **지시대로 `OPEN-QUAL-10`을 적으면 0B가 고친 오류를 되살리고 두 문서를 서로
  > 모순되게 만든다**(ledger는 *"`OPEN-QUAL-10`은 … 이 질문의 소유자가 아니다"*라고
  > 적는다). **지시의 취지 — "새 OPEN을 만들지 말고 이미 등록된 소유자를 가리켜라" —
  > 는 `OPEN-REG-05`로 그대로 충족된다.** 두 id를 병기해 어느 쪽이 소유자이고 어느 쪽이
  > 아닌지를 그 자리에 적었다.
  > `OPEN-REG-05`는 ledger의 자체 id 공간이라 **`capability-map.md` §12의 활성 OPEN
  > 집계에 들어가지 않는다** — A4의 불변과 충돌하지 않는다.
- **A4.** 불변: `capability-map.md`의 **X-1 외 서술 무변경**(diff가 §13의 해당 행에 한정),
  활성 OPEN **45**, capability **95**, 분류 **61/17/6/11**, `git diff --check` 0.
- **A5.** §10.1 표에 **형태 7 「미결을 확정으로 쓰기」**가 등재되고 **0B 실물로 근거가
  달린다** — Codex 1차 high(`R-COL-02`) · verifier F-1(`OPEN-DEC-07`) · verifier
  F-2(`R-BASIS-01` 귀속). 인용은 0B evidence를 직접 열어 확인한다.

## (A) 결정 — X-2~X-6은 0C가 정본이 된다 (운영자 승인 2026-08-27)

0C 선행 조사가 `capability-map.md`에서 **부정확 5건**을 더 찾았다. 운영자가
**"0C가 정확한 서술을 자기 문서에 쓰고 capability-map 부정확은 알려진 제한으로 기록"**을
승인했다(2026-08-27).

| # | 내용 | 0C에서의 처리 |
| --- | --- | --- |
| **X-2** | DEC-11 3행이 2행과 같은 밴드다(별도 밴드가 아니라 술어 한 벌) | 0C 밴드 인벤토리가 정본 |
| **X-3** | DEC-11 표에 **없는 밴드가 최소 2종** — `EXCLUDED_ASSESSMENT_BAND`(사정률 1±0.001 표본 제외, **DEC-04 편향 방향의 원인**) · `BID_RATIO_PLAUSIBLE`의 별도 사본 | 0C 밴드 인벤토리가 정본 |
| **X-4** | 투찰율 클램프 출처가 docstring 언급을 가리킨다(실제 리터럴은 다른 파일) | 0C가 실제 위치를 인용 |
| **X-5** | SET-07 S4가 **3개 논리합**인데 2개로 요약됐다(`total_count`가 별도 축) | 0C "정산됨" 정의가 정본 |
| **X-6** | DEC-05가 `bid_target.py`를 **디렉터리 없이** 인용한다 | 0C가 전체 경로로 인용 |

**이 slice는 X-2~X-6을 고치지 않는다.** 0C 계약이 이 표를 입력으로 받아
**"capability-map의 해당 서술은 부정확하며 0C가 정본"**을 명시한다.

**X-1만 예외인 이유**: 그것은 **운영자 결정의 근거 서술**이라 0C 문서로 대체되지 않는다.
나중에 "이 결정이 무엇에 근거했는가"를 되짚을 때 그 자리에 남는다.

## 0A2·0B에서 가져오는 규율 — 처음부터 적용한다

0A2가 열두 라운드를, 0B가 네 번의 Codex 라운드를 태운 지점은 **승인된 문서의 재편집**과
**evidence 자기서술**이었다. 그 재발 지점을 계약에 박는다.

| 규율 | 이 slice에서의 적용 |
| --- | --- |
| **형태 2** — legacy를 열지 않고 인용하기 | X-1 근거 두 자리를 `git show ed4b06c:`로 직접 열었다. **행 범위는 블록 경계까지** — `bid_summary.py`의 `Field(` 호출은 **`:69`에서 시작**한다(0B L-1이 `:70`으로 걸렸다). 출력은 `commands.md` C-1 |
| **형태 5** — 정정을 인용 지점에 전파하지 않기 | X-1 프레이밍을 in_scope 두 파일에서 **패턴으로 전수 훑었다**(C-3). §13 밖에서 나온 것은 **고치지 않고 등재**했다(O-2) |
| **형태 6** — 셈으로 전칭을 주장하기 | **셈을 산문에 옮겨 적지 않는다.** 불변은 「확인할 것 / 명령」 표로 두고 **결과를 여기 적지 않는다.** `decisions.md` 전수 목록의 *"네 자리뿐"* 셈은 값을 고치는 대신 **지웠다**(0B verifier `N-1` 처방) |
| **형태 7** — 미결을 확정으로 쓰기 | A3의 소유 `OPEN` 귀속이 정확히 이 축이다. **소유자를 지목할 때 그 OPEN이 같은 축인지 확인**했다 — 그 확인이 팀 리드 지시와의 불일치를 냈다 |
| **이력 절 규약**(0B) | 이력 절은 **자기가 속한 커밋의 diff를 서술하지 않는다.** 이미 커밋된 SHA만 지목하고 파일 목록은 `git show --stat --format='' <SHA>`에 맡긴다 |
| **줄 번호 자기참조 금지** | legacy 파일 행 범위만 예외. `decisions.md` 전수 표의 훅 번호는 **그 표 자신의 설계**이며, 이 slice가 **HEAD 기준 좌표임을 그 자리에 명시**했다 |
| **절 머리에 표의 마지막 번호 금지** | 형태 7 절 머리는 *"왜 새 형태인가"*이고 표의 마지막 번호를 박지 않는다(0B `N-1`) |

## 커밋 구성

| 커밋 | 내용 |
| --- | --- |
| `b3276cb` | X-1 정정 — `decisions.md` `OPEN-STR-01` + provenance 등재 · `capability-map.md` §13 |
| `03d7240` | §10.1 형태 7 등재 |
| 이 커밋 | evidence — `scope.md` · `checklist.md` · `commands.md` |

**파일 목록도 셈도 여기에 옮기지 않는다** — `git show --stat --format='' <SHA>`가 낸다.

두 커밋으로 나눈 이유는 **각 커밋에서 문서가 자체 정합**하기 때문이다. X-1 정정과 형태 7
등재는 서로 독립이며, 리뷰가 둘을 따로 볼 수 있다.

## 발견했으나 고치지 않은 것 — 계약이 금지한 자리

**계약이 「X-1 외의 것이 눈에 띄면 고치지 말고 보고한다」로 정했다.** 아래는 전부
`capability-map.md` 안이지만 **§13 밖**이라 in_scope 선언(*"§13 X-1 서술 한정"*)을 벗어난다.

| # | 무엇 | 왜 문제인가 | 판단 |
| --- | --- | --- | --- |
| **O-1** | §13 같은 행의 **`검증 방법`**이 *"과세/비과세 공고 경계 쌍"*을 **무조건**으로 요구한다 | 0B ledger의 대응 항목(`R-BASIS-01`)은 같은 요구를 **`OPEN-REG-05` 조건부**로 돌리고 *"지금 만들면 legacy의 미결 과세 의미로 corpus 경계가 굳는다"*고 적는다. 조건 표시 없이 두면 **형태 7(계열 A) 그 자체**이며, 정정된 `사용자 영향`과 **같은 행 안에서 어긋난다** | **고치지 않았다.** 팀 리드가 X-1을 *"ex-VAT 단정과 «VAT만큼» 정량 서술"*로 한정했고 이 문장은 그 둘 어느 쪽도 아니다. **행 안 불일치는 이 slice가 만든 것이 아니라 드러낸 것**이며, 후속 결정 대상으로 올린다 |
| **O-2** | **§5 DEC-01 분류 근거**(*"투찰율이 곱해지는 base는 추정가격(**ex-VAT**)이 아니라 기초금액/사업금액(**과세 공고면 VAT 포함**)"*)와 **§6 NOTI 보고서 계약**(*"기초금액(사업금액)과 추정가격(**부가세 별도**)"*)에 **살아 있는 X-1 프레이밍**이 남아 있다 | 팀 리드 지시는 X-1이 *"두 파일에 남아 있다 — `decisions.md` `OPEN-STR-01` 절과 `capability-map.md` §13"*이라 적었으나 **그 완결 주장이 참이 아니다.** 두 자리 다 legacy 실측과 어긋나는 같은 단정이다 | **고치지 않았다.** in_scope가 *"§13 X-1 서술 한정"*이라 §5·§6을 건드리면 **A4의 구조적 불변(§13 밖 무변경)이 깨진다.** 위치는 `commands.md` **C-3.4**의 스윕 출력이 낸다 |
| **O-3** | `decisions.md` 전수 표의 **훅 번호가 편집마다 낡는다** | 이 slice가 훅 하나를 더하자 뒤따르는 값이 전부 밀렸다. **값을 고치는 「인스턴스 고치기」**이며 다음 편집에 다시 낡는다(0B `F-4`·`N-1`과 같은 뿌리) | **셈만 지우고 번호는 유지**했다. 번호를 없애려면 표의 설계(훅↔행 1:1)를 바꿔야 하고 그것은 이 slice의 위임 범위 밖이다. **그 자리에 「정본은 명령의 출력」임을 명시**했다 |

**활성 OPEN을 해소한 것도, 새로 만든 것도 없다.**

## 이 커밋에 대해서는 주장하지 않는다

**이 slice의 evidence 셋은 한 커밋이고 이 절이 그 커밋에 실린다.** 그래서 **SHA를 적지
않고 이 커밋에 대해 아무것도 주장하지 않는다** — 0B가 세운 「이력 절 규약」이 그렇게 정한다.

- `head_sha` → `03d7240`. 커밋이 자기 SHA를 담을 수 없어 **직전 커밋**을 가리킨다.
- **리뷰 range는 `48151b9...HEAD`**. in_scope 준수는 `git diff --name-only 48151b9...HEAD`로
  확인한다.

## 불변 — 이 절은 수를 옮겨 적지 않는다

**셈을 산문에 옮기지 않는다.** 아래는 **확인할 것과 그것을 내는 명령**이고, **결과를
여기 적지 않는다.** 실행 출력의 자리는 `commands.md`다.

| 확인할 것 | 명령 |
| --- | --- |
| in_scope 준수 | `git diff --name-only 48151b9...HEAD` |
| `capability-map.md` 변경이 §13 한 행인가 | `git diff -U0 48151b9...HEAD -- docs/discovery/capability-map.md \| grep -E '^@@'` · `grep -n '^## 13\. ' docs/discovery/capability-map.md` |
| capability 총수 · 분류 4종 · 활성 OPEN 총수 | `commands.md` **C-4** — 인라인 본문을 marker로 뽑아 stdin으로 실행한다. base와 HEAD 양쪽 |
| 새 `OPEN` 신설 0 | `git diff 48151b9...HEAD -- docs/discovery/capability-map.md \| grep -cE '^\+.*\\\| OPEN-[A-Z]+-[0-9]+ \\\|'` |
| `decisions.md` 전수 표가 훅과 1:1인가 | `git diff -U0 6a4e49b -- reports/evidence/m0/0a2/decisions.md \| grep -E '^@@'` |
| X-1 프레이밍이 in_scope에 살아 있는가 | `commands.md` **C-3**의 스윕 |
| 공백 오류 | `git diff --check 48151b9...HEAD` |
| legacy 인용의 블록 경계 | `commands.md` **C-1** |
| 형태 7이 인용하는 0B 자리가 실재하는가 | `commands.md` **C-5** |

**이 커밋에 대해서는 아무것도 주장하지 않는다.**

## 규격상 N/A 항목 (evidence-pack §"문서 slice")

| 항목 | 처리 |
| --- | --- |
| `acceptance_commands` | N/A — 문서 slice. A1~A5 대조가 대신한다 |
| `differential.json` | N/A — Python/V2 실행 비교가 없다 |
| `golden-manifest.json` | N/A — fixture를 사용하지 않는다 |
| `rollback.md` | N/A — 문서 산출물은 `git revert`로 복구. base `48151b9`로 되돌릴 수 있다 |
| `codex-review-*.json` | **없음 — 아직 리뷰 요청 전이다.** 요청 후 codex-reviewer 레인이 append-only로 등재한다 |
