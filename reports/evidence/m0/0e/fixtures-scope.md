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

**같은 날 운영자가 판정 기준 하나를 더 정했다 — 물음과 답을 그대로 옮긴다(대신 판단하지 않는다).**
물음: *"결정 기록의 「기록자 bullet」이 `classification_policy.authoritative` ②의 「커밋된 운영자 결정
기록」에 드는가?"* — 답: ***"든다"***.
그러므로 **②는 「운영자 발화」로 좁혀지지 않는다** — 커밋된 결정 기록 절 안의 **기록자 bullet 문면도
②의 근거로 선다.** 이 결정으로 전수 재판정이 내렸던 `floor-applicability-001` 이 `authoritative` 로
복원됐고(그 case 가 재는 문면은 `0a2/decisions.md:317` 의 `OPEN-DEC-09` 결정 절 본문 bullet 이다),
기록자 bullet 에 기댄 다른 case 들의 유지도 같은 잣대로 정당화된다. **다만 그 문면은 커밋된 결정
기록 안에 있어야 한다** — M0 산출 문서가 스스로 쓴 acceptance·검증 방법은 결정 기록이 아니므로 이
기준으로 서지 않는다. 정본은 `manifest.yaml` 의 `classification_policy.authoritative` 가 갖는다.

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
provenance) · `money-basis`(`Unknown` 과세 금액의 산술 비교 차단). **재판정은 앞선 넷 중 하나의
「덮개」 서술도 뒤집었다** — `koneps-collection` 은 그 축에 `authoritative` 로 남는 case 가 있다고
적었으나 **전건 내려갔다.** `floor-applicability` 는 `-001` 이 `authoritative` 로 남아 그 덮개
서술이 선다(위 기록자 bullet 결정). 원래 막혀 있던 축은 그대로 남는다.
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
