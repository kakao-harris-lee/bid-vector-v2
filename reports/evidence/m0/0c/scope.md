# Slice 계약 — M0 / 0C 도메인 명세와 데이터 사전

```yaml
milestone: m0
slice: 0c-data-dictionary
base_sha: 2b05684d0e2ed8bac391d0eea4429e05f582a47b   # 착수 시점의 HEAD
review_base: aff62abfca85ca873932fedcad1e65c29ab35730   # 이 slice 첫 커밋의 부모. 아래 「head_sha와 range」
head_sha: 6af4179   # 이 파일을 쓴 가장 최근 커밋의 직전 커밋. 「head_sha와 range」 절 참조
in_scope:
  - docs/discovery/data-dictionary.md   # 신설
  - reports/evidence/m0/0c/             # 이 패키지 (decisions-2026-08-28.md 포함)
out_of_scope:
  - docs/discovery/capability-map.md      # 0A/0A2/0A3 Codex approve로 확정 — 읽기 전용
  - docs/discovery/regression-ledger.md   # 0B Codex approve로 확정 — 읽기 전용
  - docs/adr/                             # 0D 소관. 이 slice와 동시에 작업 중이다
  - reports/evidence/m0/0a/ · 0a2/ · 0a3/ · 0b/ · 0d/   # 다른 slice의 evidence — 읽기 전용
  - docs/discovery/legacy-reference-map.md # 존재하지 않는다. 생성 여부는 ADR 0009의 OPEN
  - fixtures/                             # fixture-curator 소유
  - 활성 OPEN — 해소하지 않는다
      # capability-map.md §12 활성분 + regression-ledger.md §9 OPEN-REG.
      # 예외는 운영자 결정 2026-08-28이 이미 닫은 네 건이며, 그것도 「새로 결정」이
      # 아니라 「결정을 근거와 함께 등재」로만 닫는다 — 아래 「이 slice가 닫는 것」.
  - Kotlin/Spring/Python 애플리케이션 코드   # M0은 문서 slice다
  - .claude/ 하네스
  - bid-vector/ symlink 아래 기존 저장소 수정 (읽기 전용)
acceptance_commands:
  - "N/A — 문서 slice. 아래 A1~A8을 checklist.md로 대조"
rollback: "N/A — 문서 산출물은 git revert로 복구. 애플리케이션 코드·설정·스키마 변경이 없다"
```

작성: 2026-08-28, spec-writer (v2-slice-pipeline).

---

## 이 slice가 만드는 것

`milestone-0.md` §"Slice 0C"가 요구하는 **6축**을 덮는 데이터 사전 하나
(`docs/discovery/data-dictionary.md`)와 그 evidence 패키지.

| 축 | `milestone-0.md`의 문구 | 사전의 절 |
| --- | --- | --- |
| 1 | 용어, 단위, basis, nullable 의미 | §1 |
| 2 | aggregate와 상태 전이 | §2 |
| 3 | rule의 입력/출력/reason code | §3 |
| 4 | 정책 version과 effective date | §4 |
| 5 | canonical KONEPS fact와 derived fact 구분 | §5 |
| 6 | ML feature와 업무 판단의 경계 | §6 |

**같은 문서의 금지 사항도 계약이다** — *"모호한 항목은 기존 Python 구현을 정답으로 채우지
말고 `OPEN`으로 남겨 사용자 결정을 요청한다"*. 이 slice의 `OPEN`은 사전 §9에
`OPEN-DIC-NN`으로 등록한다.

### 입력

| 입력 | 위치 | 성격 |
| --- | --- | --- |
| 선행 조사 | `_workspace/m0-0c/01_scout_data_dictionary_preflight.md` | 축별 47항목. **정의를 확정하지 않는 조사 노트** |
| 계약 초안 | `_workspace/m0-0c/00_draft_scope.md` | 이 파일이 대체한다 |
| 운영자 결정 (2026-08-26) | `reports/evidence/m0/0a2/decisions.md` | 커밋됨 |
| 운영자 결정 (2026-08-28, U-1~U-10) | `reports/evidence/m0/0c/decisions-2026-08-28.md` | **이 slice가 커밋한 사본** — 아래 참조 |
| 확정된 상류 산출물 | `docs/discovery/capability-map.md` · `regression-ledger.md` | 읽기 전용 |

**2026-08-28 결정을 evidence로 커밋한 이유**: 원본이 `_workspace/`에 있고 그 디렉터리는
gitignore 대상이라(`.gitignore` 2행) **Codex 리뷰어의 clean worktree에서 재현되지 않는다.**
A3("결정을 재조사하지 않고 인용한다")이 확인 불가능해지므로, **0A2가 2026-08-26 묶음에
대해 한 것과 같은 처리**를 한다 — 원본 절을 문장 단위로 그대로 옮긴 사본을
`reports/evidence/m0/0c/decisions-2026-08-28.md`로 커밋한다. 원본과의 일치는
`commands.md` **C-1**이 대조한다.

---

## `head_sha`와 range

`head_sha`의 정본은 이 파일 머리의 yaml 하나다 — 값을 두 곳에 두지 않는다.

- 이 파일은 **자기 커밋의 diff를 서술하지 않는다.** 커밋이 자기 SHA를 담을 수 없으므로
  yaml의 `head_sha`는 **이 파일을 쓴 가장 최근 커밋의 직전 커밋**을 가리킨다.
- **0D가 이 slice와 같은 시각에 작업했고 실제로 섞였다.** `base_sha`는 착수 시점
  HEAD이지만 착수 후 이 slice가 첫 커밋을 올리기 전에 **0D가 세 커밋을 올렸다.**
  그래서 `base_sha..HEAD`에는 0D의 커밋이 들어 있다.
  - **`review_base`는 이 slice 첫 커밋의 부모**이며 **리뷰 range는 `review_base...HEAD`**다.
    그 range에는 이 slice의 커밋만 있다.
  - `base_sha`는 **계약이 고정된 시점**의 기록으로 남긴다 — 두 값이 다르다는 사실 자체가
    병행 작업의 증적이다.
  - 이 slice의 커밋 목록은
    `git log --format='%h %s' <base_sha>..HEAD -- docs/discovery/data-dictionary.md reports/evidence/m0/0c/`
    가 낸다.
- `in_scope` 밖 경로를 이 slice가 건드리지 않았음은 `commands.md` **C-2**가 낸다.
  그 검사는 **이 slice의 커밋 집합을 먼저 고르고 그 커밋이 건드린 경로 전부**를 보므로
  0D의 커밋을 판정하지 않으면서도 혼합 커밋을 놓치지 않는다.
- Codex 리뷰의 `reviewed_head`는 리뷰 요청 시점의 HEAD이며 이 값과 다를 수 있다.

---

## acceptance — A1~A8

| # | 요구 | 출처 | 대조 |
| --- | --- | --- | --- |
| **A1** | `milestone-0.md` §"Slice 0C"의 **6축 전부**를 덮는다 | `milestone-0.md` | `checklist.md` A1 · `commands.md` **C-3** |
| **A2** | **모든 도메인 숫자의 unit/basis/provenance가 정의되거나 `OPEN`**이다. 정의 없는 숫자를 남기지 않는다 | `milestone-0.md` 「완료 조건」 | `checklist.md` A2 · `commands.md` **C-4** |
| **A3** | 확정된 운영자 결정을 **재조사·재확정하지 않고 인용**한다 | 팀 리드 지시, `decisions-2026-08-28.md` | `checklist.md` A3 · `commands.md` **C-1** · **C-5** |
| **A4** | **모호한 것을 legacy로 채우지 않는다** | `milestone-0.md` §"Slice 0C" | `checklist.md` A4 |
| **A5** | **계열 A 금지**(형태 7) — 활성 `OPEN`이 소유한 쟁점을 확정 서술·정의로 선점하지 않는다 | `0a2/checklist.md` §10.1, 0B가 세운 형태 7 | `checklist.md` A5 · `commands.md` **C-6** |
| **A6** | 모든 legacy 인용이 **`ed4b06c`**에서 확인된다. 행 범위는 **블록 경계까지**. **파일명만 쓰지 않는다** | 0B ledger §0.3, 0A3 형태 2 | `commands.md` **C-7** |
| **A7** | 0C가 발견한 미결은 사전 §9에 **`OPEN-DIC-NN`**으로 등록한다. `capability-map.md` §12와 ledger `OPEN-REG`는 **out_of_scope** | 팀 리드 지시 | `checklist.md` A7 · `commands.md` **C-6** |
| **A8** | 불변: 인용 형식 위반 0 · 중복 id 0 · secret 스캔 통과 · `git diff --check` 0 | agent-workflow §6, evidence-pack 스킬 | `commands.md` **C-2** · **C-7** · **C-8** |

---

## capability-map 부정확 — 0C가 정본이다 (운영자 승인 2026-08-27)

0C 선행 조사가 `capability-map.md`에서 부정확 일곱 건을 찾았고, 운영자가 **"0C가 정확한
서술을 자기 문서에 쓰고 capability-map 부정확은 알려진 제한으로 기록"**을 승인했다
(`reports/evidence/m0/0a3/scope.md` 「알려진 제한」).

**이 slice는 `capability-map.md`를 고치지 않는다.** 사전 §10이 일곱 건 각각에 대해
**"capability-map의 해당 서술과 다르며 0C가 정본이다"**를 근거와 함께 명시한다.
그 목록의 재현은 `commands.md` **C-9**다.

| # | capability-map의 서술 | 사전에서의 정본 |
| --- | --- | --- |
| **X-2** | DEC-11 3행이 별도 밴드다 | 밴드 인벤토리 (§1.4) |
| **X-3** | DEC-11 표에 없는 밴드가 최소 2종 | 밴드 인벤토리 (§1.4) |
| **X-4** | 투찰율 클램프 출처가 docstring 언급을 가리킨다 | 밴드 인벤토리 (§1.4) |
| **X-5** | SET-07 S4가 3개 논리합인데 2개로 요약됐다 | "정산됨" 정의 (§2.3) |
| **X-6** | DEC-05가 `bid_target.py`를 디렉터리 없이 인용한다 | 인용 규약 (§0.3) · 투찰가 정의 (§1.2) |
| **O-1** | §13 `R-BASIS-01` 대응 행의 `검증 방법`이 과세/비과세 경계 쌍을 **무조건**으로 요구한다 | **0B ledger가 정본** — 사전은 fixture 요구를 **조건부**로 서술한다 (§1.2 · §11) |
| **O-2** | §6 NOTI-08 · §7 SET-09 F-4의 **X-1 프레이밍** 두 자리 | 두 금액의 과세 처리 (§1.2) |

**승인된 목록 밖에서 한 건을 더 찾았다.** `capability-map.md` COL-07이 legacy 필드 집합의
크기를 **어림수**로 적고 같은 문장이 파일을 **디렉터리 없이** 인용한다(X-6와 같은 부류).
**고치지 않고** 사전 **§10.1 X-7**에 승인된 일곱 건과 **구별해** 적었으며, 실측은
`commands.md` **C-7.5**가 낸다. 이것은 팀 리드 지시("틀렸음이 드러나면 고치지 말고
보고한다")에 따른 보고 대상이다.

**`O-2`의 처리**: 0A3이 두 자리로 좁혔다 — §6 **NOTI-08**의 투찰률 항목과 §7 **SET-09
F-4**. 층은 **`legacy-behavior`**이고 `OPEN-REG-05` 귀속이 필요했다. **U-1·U-1b가 그
관찰을 설명한다**(VAT 포함/제외 혼용). **차이의 나머지 성분은 이 결정이 정하지 않으므로
사전은 잔차를 정량화하지 않는다.**

---

## 이 slice가 새로 하는 것 — 운영자 결정 2026-08-28이 만든 작업 셋

| # | 작업 | 사전의 자리 |
| --- | --- | --- |
| 1 | **U-2b의 "직전"** — 기준일이 공고일인가 투찰일인가. **사용자 결정이 아니라 규칙 도출**이므로 0C가 규칙을 쓰고 근거를 남긴다 | §1.2 「시공능력평가금액」 |
| 2 | **U-9** — **"무엇을 오염으로 볼 것인가"의 정의**를 새로 쓴다. **"66%"는 옮기지 않는다** | §7 |
| 3 | **U-6 전수 확인** — `DEC-03`·`DEC-04`·`DEC-08`·`SET-06` acceptance가 전부 「수치가 바뀌는 판정」에 들어가는지 확인한다. **안 들어가는 것이 있으면 고치지 말고 보고한다** | §4.2 · `checklist.md` A3 |

---

## 선행 조사가 스스로 밝힌 약한 축 — 이 slice가 채우는 방법

- **축 4(정책 version)** — 5항목 중 실물은 `effective_from` 하나뿐이고 **legacy 조사로
  채워지지 않는다.** U-6·U-6b가 그 자리를 채운다. 사전 §4는 legacy에서 옮긴 것과
  결정에서 온 것을 항목마다 구분해 표시한다.
- **축 2의 상태 전이** — 상태 *어휘*는 풍부하나 **전이 규칙·전이표가 legacy에 없다.**
  전이는 각 service의 대입문으로 흩어져 있다. **전이표는 0C가 새로 쓴다**(사전 §2.2).
  새로 쓴 전이표는 legacy 인용이 아니라 **도메인 명세**이며 그 사실을 절 머리에 적는다.

---

## 이 slice가 닫는 것

운영자 결정 2026-08-28이 이미 답을 정했고, **0C가 근거와 함께 사전에 등재해야 실제로
닫힌다**(결정 로그 자신이 그렇게 적는다). 이 slice는 **새로 결정하지 않는다.**

| OPEN | 어디서 닫히는가 | 결정 |
| --- | --- | --- |
| `OPEN-REG-05` (ledger §9) | 사전 §1.2 | U-1 · U-1b |
| `OPEN-QUAL-10` (capability-map §12) | 사전 §1.2 | U-2 · U-2b |
| `OPEN-QUAL-06` (capability-map §12) | 사전 §3.2 | U-5 |
| `OPEN-QUAL-11` (capability-map §12) | 사전 §3.2 | U-8 |

**등재는 사전 안에서만 한다.** `capability-map.md` §12와 `regression-ledger.md` §9의
**행 자체는 고치지 않는다**(out_of_scope) — 중앙 registry 통합은 별도 slice의 몫이고
ledger §10.3이 그렇게 인계했다.

## 이 slice가 닫지 않는 것

- **활성 `OPEN` 전부.** 특히 사전이 문면에서 마주치는 것들 — `OPEN-QUAL-05`(면허 유효기간,
  U-7이 **알려진 제한**으로 돌렸다) · `OPEN-QUAL-09`(시공능력 미달 처리) ·
  `OPEN-ML-05`(정책 값 경계) · `OPEN-SET-04`(재관측 노출) · `OPEN-SET-05`(재공고 대사 대상
  선택 규칙) · `OPEN-SET-06`(embargo 임계) · `OPEN-SET-10`(시간축 대체 출처) ·
  `OPEN-DEC-07`(마진 재유도) · `OPEN-DEC-10`(예규 구간별 차등) · `OPEN-NUM-01`/`OPEN-REG-04`
  (오염 수치). 사전은 각 자리에서 **어느 OPEN이 그 쟁점을 소유하는지** 밝히고 정의를
  붙이지 않는다.
- **fixture 실물.** 사전은 fixture가 만족해야 할 조건만 적고 `fixtures/manifest.yaml`을
  만들지 않는다(fixture-curator 소유).
- **`capability-map.md`·`regression-ledger.md`의 수정.** 부정확이 드러난 일곱 건도
  고치지 않고 §10에 정본을 적는다.

---

## 알려진 제한

1. **`ed4b06c`는 이 저장소 밖(`bid-vector` symlink)이라 Codex 리뷰어 worktree에서
   재현되지 않는다.** 0B가 세운 두 축(기계=경로·행 범위 존재, 사람=내용 일치)을 그대로
   적용하고 `commands.md` C-7에 방법과 출력을 남긴다. 이 slice가 유일한 확인 지점이다.
2. **0D가 병행 중이다.** `base_sha..HEAD`에 0D 커밋이 섞이므로 이 slice의 판정은 전부
   **경로 한정**으로 낸다. 0D가 `docs/adr/`에 무엇을 쓰든 이 slice는 그것을 인용하지
   않는다 — 사전이 ADR을 가리켜야 하는 자리는 **번호가 아니라 결정 이름**으로 가리킨다.
3. **면허 그룹 의미론이 운영에서 검증된 적이 없다.** U-8이 확정한 의미론의 legacy 모듈은
   **소비자가 0개**라고 스스로 적는다. 사전은 이 사실을 한계로 적는다.
4. **`OPEN-QUAL-05`(면허 유효기간)를 U-7이 「다루지 않음」으로 닫았고 그것은 위험을
   남긴다** — 만료된 면허로 자격을 오판정할 수 있다. 사전은 자격 판정 결과에
   "유효기간 미검증"이 드러나도록 요구한다.
5. **정책 version의 날짜 식별(U-6b)은 같은 날 두 번 바뀌는 정책을 구별하지 못한다.**
   사전이 그 한계를 적고, 넓히는 것은 **관측 뒤**로 미룬다.

---

## 라운드 이력

**이 절만이 이 slice의 라운드 이력이다.** `checklist.md`·`commands.md`에는 이력·자기평가
산문을 적지 않는다(0A3의 교훈).

| 커밋 | 무엇을 했는가 |
| --- | --- |
| `8b938d6` | 계약 확정, 2026-08-28 결정 사본, 사전 6축 작성 |
| `6af4179` | 검증 명령·출력(`commands.md`)과 acceptance 대조(`checklist.md`) |
| 이 커밋 | `head_sha` 기입과 이 이력 절. **이 커밋에 대해서는 아무것도 주장하지 않는다** |

**커밋을 나눈 이유는 각 커밋에서 산출물이 자체 정합하기 때문이다** — 첫 커밋의 사전은
자기 안에서 정합하고, 둘째 커밋의 evidence는 첫 커밋의 트리를 선언해 그 트리에서
재현된다.

**파일 목록도 셈도 이 절에 옮기지 않는다** — `git show --stat --format='' <SHA>`와
`git log --format='%h %s' <review_base>..HEAD`가 낸다.

### 이 slice에서 실행이 한 번 끊겼다

플랫폼 오류로 `8b938d6` 직후에 실행이 중단됐고 작업 트리에 미완(`scope.md` 수정분,
untracked `commands.md`)이 남았다. **재개 시 `git status`·`git log`로 실측을 떠서 확인한
뒤 이어 썼다.** 산출물의 내용에 그 중단이 남긴 흔적은 없다 — `commands.md`의 모든 블록을
재개 후 다시 돌려 출력을 맞췄다.

### 불변 — 이 절은 수도 좌표도 옮겨 적지 않는다

| 확인할 것 | 명령 |
| --- | --- |
| 이 slice의 커밋 목록 | `git log --format='%h %s' aff62ab..HEAD -- docs/discovery/data-dictionary.md reports/evidence/m0/0c/` |
| `in_scope` 밖 경로 · 공백 오류 | `commands.md` **C-2** |
| 상류 산출물·0D 산출물 무변경 | `commands.md` **C-6.1** |
| 6축 커버 · 도메인 숫자 전수 | `commands.md` **C-3** · **C-4** |
| legacy 인용 | `commands.md` **C-7** |
| 각 블록이 자기 선언 SHA에서 재현되는가 | 그 SHA를 체크아웃한 worktree에서 그 절의 명령을 돌린다 |

**결과를 여기 적지 않는다.**
