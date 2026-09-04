---
name: v2-slice-pipeline
description: "bid-vector V2의 마일스톤/slice 작업 전체를 조율하는 오케스트레이터. 마일스톤 시작·재개, slice 구현, capability map/regression ledger/ADR/데이터 사전 작성을 위한 기존 코드 조사, fixture 추출, Kotlin 모듈·빌드 골격 구축과 구현, Python ML 이식·튜닝, acceptance 검증·테스트 재실행, Codex 리뷰 요청, request_changes 수정 라운드, 재리뷰, evidence 작성 요청 시 반드시 이 스킬을 사용. 후속 작업 — slice 다시 실행, 이전 slice 산출물의 수정·보완·업데이트·개선, 리뷰 finding 반영, 특정 slice 산출물만 재작성 — 에도 반드시 사용. 다음은 이 스킬을 쓰지 않는다: 기준 문서(지침서·milestone·ADR)에 대한 질문, 기존 bid-vector 코드에 대한 단순 설명 요청, 하네스(.claude/) 자체의 수정."
---

# V2 Slice Pipeline — 마일스톤/slice 오케스트레이터

bid-vector V2의 slice 단위 작업(`Claude 구현 → 검증 → Codex 독립 리뷰 → 사용자 승인`)을
조율한다. 기준 문서: `v2-지침서.md`, `agent-workflow.md`, 현재 `milestone-N.md`,
`data-extract.md`. 이 스킬은 절차를 조율할 뿐, 도메인 판정 기준은 항상 위 문서가 우선한다.

조율자가 직접 지켜야 하는 절대 규칙 (README.md, data-extract.md 6절):

- 기존 Python의 출력과 같다는 이유만으로 V2 동작을 승인하지 않는다. 코드 재활용과
  기대값 판정은 별개다.
- differential 차이는 `legacy-defect / v2-defect / intentional-redesign /
  insufficient-evidence`로 판정하고 근거를 남긴다. 판정 근거 없는 diff만 실패다.
- 승인되지 않은 Python 출력으로 V2 golden을 자동 생성하지 않는다.

## 운영자 방침 (2026-08-22)

- V2의 두 갈래 전략: **service/업무 레이어**는 Kotlin으로 재작성한다 — 기존 Python
  service가 방대하고 회귀가 많기 때문이며, 목적은 유지보수성 향상과 회귀 감소다.
  **ML 레이어**는 Python 구현이 완성 단계이므로 재활용이 기본 전략이다 — M5는
  from-scratch 재작성이 아니라 새 경계(M2 계약, serving 순수성)로의 이식·정리·튜닝이다.
- 이 목적에 기여하지 않는 재작성은 범위가 아니다.
- 모든 구현 slice의 Phase 2 조사에는 기존 라이브러리·기존 구현 재사용 후보 조사를
  포함한다. 바퀴 재발명 금지.

## 실행 모드: 하이브리드 (서브 에이전트 파이프라인 + 격리 리뷰 레인)

| Phase | 모드 | 이유 |
|-------|------|------|
| 조사 (Phase 2) | 서브 에이전트, 병렬 | 주제별 독립 조사, 통신 불필요 |
| 구현 (Phase 3) | 서브 에이전트, 순차 | slice당 단일 구현자 원칙 |
| 검증 (Phase 4) | 서브 에이전트 | 저작-검증 분리 (자기 승인 금지) |
| Codex 리뷰 (Phase 5) | 격리 서브 에이전트 | agent-workflow.md의 리뷰 독립성 요구 — 구현 대화를 공유하면 안 되므로 팀 통신이 구조적으로 금지됨 |

에이전트 팀 모드를 쓰지 않는 이유: 이 워크플로우의 품질은 팀원 간 협의가 아니라
**레인 간 격리**(구현자↔검증자↔심판)에서 나온다. 데이터는 파일로만 전달한다.

## 에이전트 구성

| 에이전트 | 역할 | 주 사용 단계 | 출력 |
|---------|------|------------|------|
| legacy-scout | 기존 bid-vector 읽기 전용 조사 | M0, slice preflight | `_workspace/{slice}/NN_scout_*.md` |
| spec-writer | discovery 문서·ADR·데이터 사전 | M0 | `docs/discovery/`, `docs/adr/` |
| fixture-curator | corpus·manifest (data-extract.md) | M0~M5 | `fixtures/`, `expected/` |
| kotlin-implementer | Kotlin slice TDD 구현 | M1~M4, M6 | 커밋된 diff |
| ml-implementer | Python ML engine 구현 | M5 | 커밋된 diff |
| verifier | acceptance 재실행·evidence 점검 | 전 단계 | `_workspace/{slice}/NN_verifier_report.md` |
| codex-reviewer | Codex CLI 독립 리뷰 실행 | 전 단계 | `reports/evidence/**/codex-review-*.json` |

모델은 각 에이전트 frontmatter 의 `model:` 이 단일 출처다(호출부에서 덮어쓰지 않는다).
구현 레인(`kotlin-implementer`·`ml-implementer`)은 **sonnet**, 판정·검증·설계 레인
(`verifier`·`codex-reviewer`·`deep-reasoner`)은 **opus** — 운영자 결정 2026-09-02(진짜
결함은 검증 레인이 잡았고 구현의 기계적 수정에 opus 단가가 필요 없었다).
관련 스킬: `evidence-pack`(구현자·verifier), `codex-review-gate`(codex-reviewer).

## 워크플로우

### Phase 0: 컨텍스트 확인

실행 모드를 판별한다:

1. 현재 마일스톤 판별 — `reports/evidence/` 아래 최신 approve된 slice, `docs/discovery/`
   존재 여부, git log로 진행 상태 확인. 기준 문서와 실제 저장소 상태가 어긋나면(예:
   문서는 "git 저장소 아님"인데 실제로는 저장소) 사용자에게 보고한다.
   Codex 리뷰가 예정된 slice라면 `codex --version`으로 CLI 가용성을 preflight한다 —
   Phase 1~4를 다 태운 뒤 리뷰 불가를 발견하지 않기 위해서다.
2. 사용자 요청 분류:
   - **새 slice 시작** → Phase 1부터 전체 실행. 단, 해당 마일스톤/slice가 사용자와
     Codex 승인 범위인지 먼저 확인 (M0 승인 전 애플리케이션 코드 금지)
   - **진행 중 slice 재개** → `_workspace/{slice}/`와 evidence를 읽고 마지막 완료
     Phase 다음부터 재개
   - **Codex finding 수정 라운드** → Phase 3(수정)로 진입, 같은 scope 유지
   - **부분 재실행** (특정 산출물만 수정) → 해당 에이전트만 재호출, 이전 산출물 경로를
     프롬프트에 포함
3. `_workspace/{slice}/`가 없으면 생성. 중간 산출물은 삭제하지 않는다 (감사 추적).

### Phase 1: slice 계약 고정

1. base SHA 확인 (`git rev-parse HEAD`), in/out scope, acceptance command, rollback을
   agent-workflow.md 2절의 YAML 형식으로 정리
2. `reports/evidence/<milestone>/<slice>/scope.md`에 기록 (`evidence-pack` 스킬 규격)
3. scope가 지침서·milestone 문서와 어긋나거나 승인되지 않은 범위면 사용자에게 확인
   요청 후 중단. 작업 중 scope 확장 요구가 생기면 slice를 멈추고 계약을 갱신한다.

### Phase 2: 조사 (필요 시)

**실행 모드:** 서브 에이전트, 병렬 (한 메시지 안에서 Agent 도구를 여러 번 호출)

주제별로 legacy-scout를 병렬 스폰한다. M0은 milestone-0.md의 capability 8축
(공고 수집·정규화 / 회사·운영자 전략 / 면허·지역·실적 자격 / 추천 입력·ML 추론 /
투찰 판단과 근거 / 알림·보고서 / 개찰 대사·정산 / 운영·증적) 기준, 구현 slice는
preflight 조사 1건. 각 scout에 앵커 파일 목록과 출력 경로를 프롬프트로 전달한다.
이미 유효한 조사 노트가 있으면 이 Phase를 건너뛴다.

구현 slice의 preflight 조사에는 재사용 조사를 포함한다: (a) 같은 문제를 푸는 기존
라이브러리, (b) 기존 bid-vector의 재활용 가능한 성숙 코드(특히 Python ML), (c) 이미
구현된 V2 코드와의 중복 여부. 조사 없이 새로 만드는 것을 허용하지 않는다.

### Phase 2.5: 설계 검토 — 게이트·계약형 slice 는 필수 (운영자 채택 2026-09-02)

**실행 모드:** 서브 에이전트 1명 (`deep-reasoner`, 읽기 전용)

산출물의 수용 기준이 「우회할 수 없는가」·「구성상 닫히는가」인 slice(아키텍처 게이트·래칫·
fixture 계약·정책 데이터 분리·타입 불변식)는 **구현 전에** 설계 검토를 받는다. 검토자는
조사 노트와 slice 계약을 읽고 (1) 제안 설계가 열거(deny-list)에 기대는지 구성상 닫히는지,
(2) 우회 경로를 최소 다섯 고안해 설계가 각각을 어떻게 막는지, (3) 승인 문서가 요구하지 않는
과잉·미달을 판정해 `_workspace/{slice}/NN_design-review.md` 에 남긴다. 구현 레인은 그
노트를 입력으로 받는다. **왜 필수인가:** M1/1A 에서 deny-list 게이트 설계를 Codex 3라운드·
verifier 7라운드로 발견했다 — 같은 판정을 구현 전 검토 1회가 낸다. 도메인 코드 slice 는
계약(1B 의 타입 불변식 등)이 있으면 받고, 순수 구현 slice 는 생략 가능하다.

**(0) 위협 모델 경계 문장이 (1)·(2) 보다 먼저다 (운영자 채택 2026-09-03).** 검토자는
「이 산출물이 방어하는 것 / 방어하지 않는 것」을 한 문단으로 쓰고, 승인 문서의 요구
문면과 대조해 경계가 요구 축소가 아님을 보인다. 경계 없이 「우회 ≥5」만 물으면 우회
집합이 무한이라 라운드마다 새 경로가 나온다 — M1/1A 실측: 설계 검토 뒤에도 Codex 5·7·8차가
같은 계열(신뢰 앵커 ↔ 게이트 입력 불일치)로 이어졌고, 3차 설계 검토가 「진짜 뿌리는
디렉터리 신뢰가 아니라 위협 모델 부재」로 진단했다. 게이트가 같은 트리의 빌드 스크립트인
한 빌드 스크립트를 임의로 쓰는 저자를 모델에 두면 어떤 게이트도 닫히지 않는다(`-x`·
`enabled=false` 한 줄). 경계는 milestone 문서·ADR 에 운영자 결정으로 등재하고, 이후 경계 밖
finding 은 수정이 아니라 경계 참조로 답한다.

### Phase 3: 구현

**실행 모드:** 서브 에이전트, 순차 (slice당 구현자 1명)

- M0 문서 slice → spec-writer / fixture-curator
- M1~M4, M6 코드 slice → kotlin-implementer
- M5 → ml-implementer

프롬프트에 포함: slice 계약 경로, 조사 노트 경로, 관련 명세·fixture 경로, RED 우선 지시,
`evidence-pack` 스킬로 `reports/evidence/`에 직접 evidence 기록 지시, **완료 시 리뷰
가능한 단위로 git commit하라는 지시**(로컬 커밋은 사용자 승인 대상이 아님 — 승인
대상은 push/merge/배포). 수정 라운드에서는 Codex finding JSON 경로를 추가로 전달하고
finding별 별도 커밋을 지시한다.

**스테이징 규율 — 공유 working tree 에서 레인이 병렬로 산다.** 구현 레인은 `git add
<in_scope 경로>` 만 쓰고 `-A`·`-a`·`.` 을 쓰지 않는다; 커밋 전 `git diff --cached
--name-status` 로 in_scope 를 대조한다. 오케스트레이터(하네스 레인)도 `.claude/`·`CLAUDE.md`
편집을 **미커밋 상태로 두지 않는다** — 편집 즉시 커밋한다. M1/1A 에서 하네스 레인의 미커밋
스킬 편집이 구현 레인 커밋(`e733cfa`)에 혼입됐다(2026-09-02). 혼입이 생기면 이력을 되쓰지
않고 evidence 의 레인 경계 검사에 사실로 선언한다.

**하네스 커밋은 slice range 에 섞이되 scope.md 가 가른다 (2026-09-04).** 즉시 커밋 규율의
귀결로 `base..HEAD` 에는 하네스 레인 커밋이 늘 섞인다. slice 의 커밋 집합은 range 가 아니라
**in_scope 경로의 변경**이고, 하네스 커밋은 scope.md 의 상시 「하네스 레인 변경」 절에
리뷰 요청 시점마다 등재한다(규격은 `evidence-pack`). Phase 5 진입 전 오케스트레이터가 그
절을 갱신했는지 확인한다 — rollback 도 range revert 가 아니라 in_scope 경로 한정이다.
M1/1A Codex 15차 high 가 이 구조를 미선언 scope 확장으로 읽었다.

### Phase 4: 검증

**실행 모드:** 서브 에이전트 (verifier 1명)

**slice 끝에 한 번 돌린다.** 한 파일 고칠 때마다 부르지 않는다 — 구현자가 자기 acceptance를
통과시킨 뒤, 그 수정이 만든 것까지 스스로 훑은 뒤에 부른다.
**게이트는 산출물이다** — evidence가 자기에 대해 하는 말은 게이트 항목이 아니다.

**차단 문턱 (운영자 채택 2026-09-02):** `not-ready` 는 **산출물(코드·게이트·계약)의 blocker/high**
에만 낸다. 장부층(evidence 수치·좌표·문면·상호 포인터) finding 과 산출물 low 는 리포트에
등재하되 라운드를 막지 않는다 — 구현 레인이 다음 Codex 전 **한 커밋**으로 일괄 처리하고,
verifier 는 그 커밋을 재검증하지 않는다(Codex 가 본다). M1/1A 실측: verifier 8라운드 중
not-ready 5 가운데 셋이 장부층이었다.
**예외 — 게이트 술어를 바꾸는 커밋은 severity 와 무관하게 표적 재검증을 거친다 (운영자 채택
2026-09-04).** 판정 로직(순수 함수·정책 해석·순회 조건)을 손대는 medium/low 수정은 오탐을
닫으려다 미탐을 여는 방향으로 기울기 쉽다 — M1/1A verifier r19 M-2(inner class 오탐)를 「같은
파일 선언 이름」으로 닫은 일괄 커밋이 재검증 없이 Codex 14차로 갔고, `val java = 1` 한 줄로
모든 `java.*` 참조를 버리는 high 가 됐다. 게이트 술어에서는 오탐이 미탐보다 낫고, 그 방향성은
verifier 만 실측으로 판정한다.

**`evidence-pack`의 크기 게이트를 여기서 확인한다.**

verifier에 slice 계약, base/head, evidence 경로를 전달한다.
- `ready-for-review` → Phase 5로 진행
- `not-ready` → 보고서를 첨부해 Phase 3 구현자에 수정 지시

verifier 의 최고 가치 활동은 **우회 고안·실행 재현**이다 — 축어·좌표 대조는 명령이 내게 하고 verifier 의 시간을 거기에 쓰지 않는다.

**재작업 상한(절대값): 5회.** verifier not-ready와 Codex request_changes를 합산해
slice 전체에서 구현 재작업이 **5회에 이르면 멈춘다.** 카운터는 라운드마다 리셋되지 않는다.

**상한에 닿았을 때 할 일은 「한 라운드 더 돌릴까요」를 묻는 것이 아니다.**
**그 자체를 결함 신호로 읽고 작업을 되짚는다** — 무엇이 라운드마다 되풀이되는지,
산출물의 구조나 접근 자체가 틀린 것은 아닌지, 고치는 방식이 새 결함을 만들고 있지는
않은지. **진단을 붙여 보고하고 사용자 판단을 받는다.** 라운드 연장을 기본값으로 두지
않는다 — 다섯 번 고쳐서 안 끝났으면 고치는 방법이 틀린 것이다.

### Phase 5: Codex 독립 리뷰

**실행 모드:** 격리 서브 에이전트 (codex-reviewer)

**리뷰는 의미 있는 단위가 완료된 뒤에 건다 — 증분마다 걸지 않는다.**
Codex 라운드는 비싸고 느리며, 잦은 호출은 심판을 「다음 할 일 목록 생성기」로 만든다.
**한 finding을 고치고 바로 다시 거는 것을 금지한다.** finding을 전부 닫고, 자체 검증으로
게이트를 통과시키고, 그 수정이 만든 것까지 훑은 뒤에 한 번에 건다.
**같은 이유로 자체 검증(Phase 4)도 한 줄 고칠 때마다 돌리지 않는다** — 묶어서 돌린다.

**Codex 는 Phase 2.5 설계 검토와 Phase 4 를 통과한 뒤에만 건다.** 게이트형 slice 에서 설계
검토 없이 Codex 를 걸면 열거 누락 finding 이 라운드마다 하나씩 온다(M1/1A 3라운드 실측).

1. 구현 diff가 커밋되어 base/head가 고정되었는지 확인 — 판정 기준은
   `git status --porcelain -- <scope.md의 in_scope 경로>` (evidence-pack 스킬과 동일
   정의). in_scope에 미커밋 변경이 있으면 Phase 3으로.
2. codex-reviewer에 milestone/slice, base/head SHA, evidence 경로만 전달한다.
   **구현 과정의 판단·자체 평가를 전달하지 않는다.** 리뷰는 codex-review-gate 절차에
   따라 저장소 밖 clean worktree에서 실행된다 — `_workspace/`의 자체 평가 산출물이
   Codex에 노출되지 않게 하기 위해서다.
3. verdict 처리:
   - `approve` → Phase 6
   - `request_changes` → Phase 3 수정 라운드 → Phase 4 재검증 → Phase 5 재리뷰.
     **두 번의 수정 라운드 후에도 blocker가 남으면 자동 반복을 중단하고 사용자에게
     보고한다.** (Phase 4의 재작업 상한 총 5회와 병행 적용 — 먼저 걸리는 쪽이 우선)

**장부층 정지선 (운영자 채택 2026-09-01 · Codex B8 high 반영으로 2026-09-02 개정).**
finding 을 두 층으로 갈라 읽는다 — **도메인·계약층**(명세 의미, fixture 기대값, 계약이
잠그는 것)과 **장부층**(evidence 문서 사이의 정합성: 개수·좌표·현재형 서술·상호 포인터).
재리뷰 verdict 가 도메인·계약층 finding 없이 **장부층 non-blocker 만** 담으면 자동 수정
라운드를 돌리지 않는다 — 그러나 **그것이 slice 를 닫지도 않는다. `request_changes` 는
slice 를 닫지 못한다.** 그 지점에서 하는 일은 finding 등재 + **미승인 상태로 정지·보고**
뿐이며, 다음 라운드를 돌릴지는 운영자가 그때마다 결정한다. 종결의 조건은 이 스킬이
정하지 않는다 — `milestone-0.md` 의 「Codex approve 와 사용자 승인」이 그대로 정본이고,
운영자가 그 조건을 바꾸려면 스킬 안의 예외가 아니라 **`agent-workflow.md` 와 milestone
계약을 별도 명시 결정으로 개정**한다(Codex B13 high 반영, 2026-09-02 — B8 개정이
남겼던 「approve 없는 종결 예외」 절은 차단을 선언한 자리에서 곧바로 풀어 자기모순이었다).
**상한 도달도 같다** — finding 의 재분류가 아니라 미승인 상태의 정지·보고다. 반루프
취지는 유지된다: M0/0E 실측에서 B4 이후 다수 finding 이 수정 커밋 자신이 낡게 만든
장부층이었고, 「approve = 장부의 완벽성」으로 두면 루프가 정의상 끝나지 않는다. 이
정지선은 자동 라운드를 멈출 뿐 리뷰의 차단력을 건드리지 않는다 — agent-workflow.md 의
finding 임의 축소 금지도 그대로 정본이다. 도메인·계약층 finding 이 하나라도 있으면 층
구분 없이 일반 절차를 따른다.

### Phase 6: 사용자 보고

1. 요약 보고: slice 이름, base/head, acceptance 결과, Codex verdict, 잔여 위험,
   evidence 경로
2. **`OPEN` 항목 에스컬레이션**: 이번 slice 산출물에 남은 `OPEN` 항목을 모아 각각의
   결정 필요 사항과 선택지를 사용자에게 제시한다. milestone 완료 조건(`OPEN` 0개 또는
   사용자 명시 승인)은 이 절차 없이는 충족될 수 없다.
3. 다음은 테스트와 Codex `approve`가 있어도 자동 승인되지 않는다 — 사용자 결정만
   가능 (agent-workflow.md 1절): merge/push, 운영 DB write·backfill·schema writer 전환,
   실제 외부 API/LLM 호출과 Telegram/email 발송, 운영 credential 조회·로그 출력,
   외부 공개·feature 활성화·release 전환, 이전 runtime/table/branch/worktree 삭제.
4. 하네스 개선 피드백 기회를 제공한다 (강요하지 않음).

## 데이터 흐름

```
scope.md ─→ [legacy-scout ×N] ─→ 조사 노트 ─→ [구현자 1명] ─→ 커밋 + evidence 초안
                                                    │
                              not-ready ←── [verifier] ── ready-for-review
                                  │                              │
                                  └──→ 구현자 수정 ←──┐          ▼
                                                     │   [codex-reviewer]
                                request_changes (≤2회) ──────────┤
                                                                 ▼ approve
                                                          [사용자 보고]
```

전달은 전부 파일 기반: `_workspace/{slice}/NN_{agent}_{artifact}.md` +
`reports/evidence/<milestone>/<slice>/`.

## 에러 핸들링

| 상황 | 전략 |
|------|------|
| 에이전트 1회 실패 | 같은 입력으로 1회 재시도, 재실패 시 누락 명시하고 사용자 보고 |
| 조사 scout 일부 실패 | 성공한 노트로 진행, 보고서에 미조사 영역 명시 |
| 구현 재작업 총 5회 도달 (not-ready + request_changes 합산) | 자동 반복 중단, 진단을 붙여 사용자에게 판단 요청 |
| Codex blocker 잔존 (수정 2라운드 후) | 자동 반복 중단, 사용자에게 판단 요청 |
| codex CLI 실행 불가 | 자체 판정으로 대체하지 않음. 리뷰 불가 사실을 보고하고 중단 |
| scope 밖 변경 필요 발견 | slice 중단, 계약 갱신을 사용자에게 제안 |
| 상충 근거 | 삭제하지 않고 출처 병기, `OPEN` 처리 |

## 테스트 시나리오

### 정상 흐름 (M0 Slice 0A)
1. 사용자: "M0 시작해" → Phase 0에서 초기 실행 판별, Phase 1에서 scope.md 고정
2. Phase 2: legacy-scout 6명 병렬 조사 → `_workspace/m0-0a/` 노트 6건
3. Phase 3: spec-writer가 `docs/discovery/capability-map.md` 작성 후 커밋
4. Phase 4: verifier가 milestone-0 완료 조건 중 0A 담당 항목만 대조 → ready-for-review
5. Phase 5: codex-reviewer가 clean worktree에서 리뷰 실행, `approve` JSON 저장
6. Phase 6: 사용자에게 승인 요청 보고

### 에러 흐름 (request_changes)
1. Phase 5에서 Codex가 blocker 1건으로 `request_changes`
2. Phase 3: 구현자에 finding JSON 전달, 별도 커밋으로 수정
3. Phase 4 재검증 → Phase 5 재리뷰 → `approve`
4. 2라운드 후에도 blocker가 남으면: 자동 반복 중단, finding 원문과 함께 사용자 보고
