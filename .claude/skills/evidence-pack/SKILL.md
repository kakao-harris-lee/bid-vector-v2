---
name: evidence-pack
description: "slice evidence 패키지(reports/evidence/<milestone>/<slice>/) 작성·점검 규격. scope.md, commands.md, differential.json, golden-manifest.json, rollback.md 작성, evidence 기록, 리뷰 요청 조건 점검 시 반드시 이 스킬을 사용."
---

# Evidence Pack — slice 증적 패키지 규격

각 slice의 `reports/evidence/<milestone>/<slice>/`에 남기는 증적의 규격.
agent-workflow.md 6절이 최소 목록을 정의하며, 이 스킬은 각 파일의 구체 형식과
점검 기준을 제공한다. evidence는 사후에 몰아 쓰지 않고 해당 Phase에서
`reports/evidence/` 경로에 **직접, 즉시** 기록한다 — 기억으로 재구성한 증적은 증적이
아니고, `_workspace/`에 쓴 초안을 나중에 옮기는 방식은 이관 누락을 만든다.
`_workspace/`는 조사 노트·구현 노트·verifier 리포트 전용이다.

## 경로 규약

- evidence: `reports/evidence/<milestone>/<slice>/` — 소문자, 예: `reports/evidence/m0/0a/`
- 워크스페이스: `_workspace/<milestone>-<slice>/` — 예: `_workspace/m0-0a/`
- Phase 0의 재개 판별은 이 규약을 전제로 경로를 탐색한다.

## 파일 규격

### scope.md — slice 계약 (Phase 1에서 작성)

```yaml
milestone: M1
slice: money-rate-basis-kernel
base_sha: <40자 SHA>
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - <모듈/디렉토리>
out_of_scope:
  - <명시적 제외>
acceptance_commands:
  - <명령>
rollback: <이번 slice의 신규 wiring을 비활성화하는 방법>
```

scope 확장이 필요해지면 이 파일을 갱신하고 갱신 사유를 하단에 append한다.

**slice 의 커밋 집합은 range 가 아니라 in_scope 경로의 변경이다 (2026-09-04).** 하네스 레인
(오케스트레이터)은 `CLAUDE.md`·`.claude/**` 를 같은 브랜치에 즉시 커밋하므로 `base..HEAD`
range 에는 slice 산출물이 아닌 하네스 커밋이 반드시 섞인다. 그래서 scope.md 는 **「하네스 레인
변경」 절**을 상시 둔다 — 리뷰 요청 시점마다 `git log --oneline <base>..HEAD -- CLAUDE.md
.claude/` 를 돌려 커밋 목록·경로·목적 한 줄씩을 등재하고 「slice 산출물이 아니며 in_scope
밖, 운영자 승인 하에 같은 range 에 있다」를 명시한다. 목록이 비면 「없음」으로 적는다. 리뷰
range 는 그대로 `base..HEAD` 이고 Codex 는 이 절을 보고 하네스 경로를 slice finding 에서
가른다. **M1/1A Codex 15차 high** — range 를 커밋 집합으로 정의한 채 하네스 커밋 13개가
미선언이었고 rollback 의 range revert 가 그것까지 되돌렸다.

### commands.md — 실행 명령과 종료 코드

명령 실행 직후 즉시 append한다:

```markdown
## 2026-08-22T04:10:33Z
- cmd: `./gradlew :shared-kernel:test`
- exit: 0
- 핵심 결과: 214 tests, 0 failed
```

exit code는 실제 값을 기록한다. 실패했다가 수정 후 통과한 이력도 남긴다 — 실패 이력은
결함이 아니라 검증이 실제로 작동했다는 증거다.

**출력 전문을 붙이지 마라.** `핵심 결과`는 **한 줄**이다. 감사자는 명령을 다시 돌린다.

> **왜 금지인가 (M0 실측, 2026-08-30).** M0가 이 규격을 벗어나 출력을 문서에 박았다.
> 그러자 「이 출력이 아직 참인가」를 재야 했고 → **선언 SHA 규약** → 문서가 바뀔 때마다
> **전 블록 재취득** → 재취득이 맞는지 재는 **축어 대조 검사** → 그 검사가 자기를 못 세어
> **고정 표본** → 비밀값 스캐너가 자기 출력을 스캔해 **재귀 차단**까지 갔다.
> **명령만 남겼으면 이 사슬 전체가 없었다.**
> 결과: evidence **20,373줄** 대 산출물 **8,730줄**(2.3배). 한 slice의 커밋 154개 중
> **108개가 산출물을 한 줄도 건드리지 않았다.**

### 만들지 않는 것

- **라운드 이력 절.** 수정 라운드가 무엇을 했는지 evidence에 서술하지 마라 — 그 서술은
  자기 커밋의 diff를 말하므로 반드시 낡고, 고치면 다음 라운드가 또 쓴다. M0에서 이것이
  **거짓 서술의 최대 공급원**이었고 등재를 멈춘 것이 그 loop를 끝냈다.
  **라운드 기록은 git log와 리뷰 verdict가 이미 갖는다.**
- **자기 검사 하네스.** evidence가 자기를 검사하는 장치(선언 SHA 래칫·전수 스윕·덮개
  전칭·고정 표본)를 만들지 마라. **게이트는 산출물에 건다** — evidence가 자기에 대해 하는
  말은 게이트 항목이 아니다.
- **명령이 내는 셈을 산문에 옮겨 적기.** 셈이 필요하면 그 셈을 내는 명령을 가리켜라.
- **낡는 좌표 — 같은 사실을 두 자리에, 값을 박아 적기.** 개수·목록·SHA·줄 번호는 산출물이
  바뀔 때마다 낡는다. **이 slice 가 편집하는 파일(같은 slice 안에서 줄이 움직일 수 있는
  파일)을 가리킬 때 `file:line` 을 쓰지 않는다** — 인용문(grep 가능한 축어)·절 제목·결정
  ID(`D-6`·`OPEN-ADR-14`·`§3.1`)로 가리킨다. 정본은 한 자리, 나머지는 포인터. 확정돼 이
  slice 가 편집하지 않는 문서의 `file:line` 은 허용하되, 그 파일이 뒤 slice 에서 편집되면
  낡는다는 것을 안다. (M0/0E 와 M1/1A 실측: 선언 SHA → 정책 version → RED 수치 → 줄 번호로
  같은 클래스가 네 번 재발했고, 셋은 「값을 걷고 명령·조건으로」로 닫혔는데 줄 번호만 그
  처리를 받지 않았다. 밀리지 않은 좌표는 전부 줄 번호가 아닌 형태였다.)
  **역방향도 본다** — 이 slice 가 승인 문서에 줄을 넣거나 빼면 **그 파일을 가리키던 다른
  문서의 `file:line`** 이 밀린다(M1/1A: `milestone-1.md` +5줄로 `capability-map.md` 의 M0
  작성 좌표 열둘이 어긋남 — diff 에 안 보여 Codex 가 못 본다). 편집한 파일마다
  `grep -rn '<파일명>:[0-9]' --include='*.md' --include='*.kt' --include='*.properties'`
  로 영향 목록을 내고, 자기 범위면 고치고 범위 밖이면 **알려진 제한에 등재**한다.
  **패턴은 전체 파일명이 아니라 그 파일을 가리키는 모든 축약형을 덮어야 한다** — ADR 은
  `docs/adr/0007:186`·`ADR 0007 §6:172` 처럼 번호만으로 인용되므로 `0007` 같은 stem 으로
  잡는다(M1/1A verifier r12: 전체 파일명 grep 0건 뒤에 축약형 5건이 숨어 있었고 삽입이
  문서 머리라 전부 밀려 있었다). 그리고 「편집이 아래쪽이라 안 밀린다」는 판단은 삽입
  줄 번호와 인용 줄 번호를 **둘 다 명령으로** 낸 뒤에만 쓴다.

### 크기 게이트

**한 slice의 evidence 합계가 그 slice 산출물을 넘으면 멈추고 무엇이 부풀었는지 보고한다.**
evidence는 일이 되었음을 남기는 기록이지 일 자체가 아니다.

### differential.json — Python/V2 차이 판정 (해당 시)

case별로 `{case_id, python_result, v2_result, verdict, evidence}`.
verdict는 `legacy-defect | v2-defect | intentional-redesign | insufficient-evidence` 중
하나. `intentional-redesign`은 ADR 참조 필수, `insufficient-evidence`는 완료 gate 실패.

### golden-manifest.json — 사용 fixture 목록

사용한 fixture id, 출처 분류(authoritative/observed/legacy-behavior), SHA-256.
`fixtures/manifest.yaml`의 entry와 일치해야 한다.

### 공유 워킹트리의 커밋 — **경로를 커밋 명령에 준다 (2026-09-08)**

`git add <경로> && git commit -m ...` 는 **인덱스 전체를 커밋한다.** 다른 레인이 같은 워킹트리에서
파일을 스테이징해 두었으면 그것까지 함께 실린다 — `add` 와 `commit` 을 한 명령으로 묶어도 막히지
않는다(그 규율은 **자기 레인의 add 와 commit 사이**에 다른 레인이 끼는 것을 막을 뿐이다).

**모든 레인이 커밋에 경로를 명시한다**: `git add <경로들> && git commit -m "..." -- <같은 경로들>`.
경로 인자가 있으면 인덱스의 다른 항목은 실리지 않는다. 개별 인자로 넘긴다(변수 하나로 묶으면
pathspec 이 하나가 된다 — 2026-09-03 의 clean-tree 게이트와 같은 함정).

혼입이 이미 생겼으면 **이력을 되쓰지 않는다.** slice 계약의 레인 경계 절에 **사실로 선언한다** —
어느 커밋이 어느 레인의 무엇을 흡수했는지, 그리고 그 커밋 메시지가 내용을 잘못 기술한다는 것까지.

근거: M3/3E — 문서 레인(오케스트레이터)이 `policy-values.md` 를 커밋하는 순간 구현 레인이
스테이징해 둔 7 파일이 함께 실렸다. 커밋 메시지는 「P-7 승인 등재」인데 내용의 대부분이 3E 코드다.
M1/1A(미커밋 편집이 남아 혼입)와 원인이 다르다 — 이번에는 **양쪽 다 규율을 지켰고** 커밋 명령의
기본 동작이 인덱스 전체라는 것이 원인이다.

### rollback.md

되돌리는 flag/route/writer, 예상 복구 시간, 검증 방법.

**되돌림은 range revert 가 아니라 in_scope 경로 한정이다 (2026-09-04).** `git revert
<base>..HEAD` 는 같은 range 의 하네스 레인 커밋까지 되돌린다. 대신 `git restore
--source=<base> --staged --worktree -- <in_scope 경로 개별 인자>` 로 slice 산출물만 base
상태로 되돌린다 — `--source` 에 없는 경로는 삭제되므로 신규 파일에 별도 `git rm` 이 필요
없다. **`git checkout <base> -- <경로>` 는 쓰지 않는다** — base 에 없는 경로마다 pathspec
오류로 exit 1 이고 아무것도 적용되지 않는다(Codex 1A 16차 high — 신규 129/변경 6 인 slice
에서 첫 명령이 실패). 하네스 경로(`CLAUDE.md`·`.claude/**`)와 승인 문서의 하네스 레인 편집은
**되돌리지 않는다**고 명시한다. rollback.md 의 명령은 **임시 clone 에서 실제로 돌려 exit 0
과 D/M 수를 실측**해 commands.md 에 한 줄 남긴다 — dry-run 성립 확인은 명령의 실행 가능성을
증명하지 않는다. 되돌린 뒤 확인 지점은 「in_scope 경로의 `git diff <base> -- <경로>` 가 비어 있고
하네스 경로는 HEAD 그대로」다.

**공유 파일은 「줄 단위」로 끝나지 않는다 — 어느 커밋의 줄인지로 가른다 (2026-09-09).** 두 slice 가 같은
파일을 만지면(예: `config/quality/gate-tests.properties`·`fixtures/manifest.yaml`·승인 문서·conformance 실행자)
`base..HEAD` diff 를 통째로 역적용하는 것은 **다른 slice 의 줄까지 걷는다**. 절차는 셋이다:
① `git log --oneline <base>..HEAD -- <파일>` 로 **그 파일을 만진 커밋을 먼저 나열**한다 — 자기 slice 뿐인지
다른 slice 와 겹치는지가 여기서 갈린다. ② 겹치는 파일은 **자기 커밋 해시로 hunk 를 격리**해 역적용한다
(`git diff <sha>~1..<sha> -- <파일> | git apply -R`). ③ 겹치지 않는 파일만 `base..HEAD` 로 되돌린다.
확인은 「내 줄이 사라졌다」와 **「남의 줄이 남았다」를 둘 다** 실측한다 — 후자를 안 재면 이 결함이 안 보인다.
M4 실측: 4C-1 rollback 이 공유 셋을 전체 복원으로 적어 4B-1 의 `Verdict` 커널 등재가 날아갈 상태였고,
되돌린 트리의 **conformance 82·decision 62**(전체 복원이었다면 74·38)가 보존을 증명한 결정적 수치였다.

**되돌린 트리가 빌드되는지까지 확인한다 (2026-09-08).** 위 세 확인(exit 0 · D/M 수 · diff 비어
있음)은 **파일이 제자리로 갔는지만** 잰다. 코드 slice 에서는 그 셋이 다 통과하면서도 트리가
컴파일되지 않을 수 있다 — 목록이 낡아 파일 하나가 안 돌아가면 남은 호출부가 사라진 시그니처를
가리킨다. **실측에 두 단계를 더한다**: ④ 되돌린 트리에서 **모듈별 compile 명령이 exit 0** ⑤ 그
트리의 **test 가 초록**. 결과를 rollback.md 에 한 줄씩 적는다.

**목록은 손으로 쓰지 않는다** — `git diff --name-status <base>..HEAD` 에서 기계적으로 낸다
(A = 삭제 대상, M = base 로 restore). **라운드마다 파일이 늘면 이 절차를 다시 돌린다**는 문장을
rollback.md 에 남긴다 — 목록이 낡는 것이 이 결함의 실제 원인이다.

근거: M3/3B-2 verifier r2 high — 수정 라운드가 파일 셋을 더 고쳤는데 restore 목록이 갱신되지
않아, 문서의 명령을 임시 clone 에서 그대로 실행하면 **exit 0 인데 `:adapters:compileKotlin` 이
exit 1**(`Too many arguments` · `Unresolved reference`)이고 base 대비 48줄이 남았다. 「exit 0 =
성공」으로 적은 것이 그 라운드의 차단 사유가 됐다. M1/1A 16차 high(명령이 아예 실패)의 다음
단계 — 이번에는 **명령이 성공하면서 결과가 미달**이다.

### codex-review-*.json

codex-reviewer 에이전트만 작성한다 (`codex-review-gate` 스킬). 다른 에이전트는 이
파일을 생성·수정하지 않는다.

**코드 slice 에서 리뷰 라운드의 reviewer 메타데이터 정본은 이 JSON**(`reviewer`·`commands_run`),
**preflight 정본은 레인이 쓰는 형제 파일 `codex-review-<UTC>.preflight.json`** 이다
(`residual_risks` 는 Codex 소유 필드라 레인이 쓰지 않는다). `commands.md` 에 라운드마다 preflight 행을
등재하지 않는다 — M0/0E·M1/1A 에서 그 등재 커밋이 라운드마다 하나씩 붙었고 값은 JSON 과
중복이었다(운영자 결정 2026-09-02). `commands.md` 는 acceptance 와 게이트 실측만 갖는다.

## 리뷰 요청 조건 점검 (verifier용)

Codex 리뷰를 요청하기 전에 전부 충족해야 한다 (프로젝트 CLAUDE.md 기준):

- [ ] 구현 diff가 커밋되어 base/head 고정 —
      `git status --porcelain -- <scope.md의 in_scope 경로>` 결과 없음.
      scope 밖 로컬 파일은 판정에 넣지 않는다 (clean-tree 게이트 정의는 이 한 곳이
      기준이다). **경로는 개별 인자로 넘긴다 — 목록을 변수 하나에 담지 마라.** zsh 는
      변수를 단어 분리하지 않아 목록 전체가 pathspec 하나가 되고, 아무것도 매치하지 않아
      「결과 없음·exit 0」이 된다 — 더러운 트리와 구별이 안 된다(M1/1A 구현 레인 실측,
      2026-09-03; codex-review-gate §4b 스모크의 변수 확장 함정과 같은 클래스). 판정을
      남길 때 **양성 대조 한 번**(in_scope 파일 하나를 일부러 건드려 잡히는지)을 같이 남긴다.
      **되돌릴 때 `git checkout -- <파일>` 을 쓰지 않는다 — 심은 줄만 비파괴로 절삭한다**(예: `head -n <원래 줄수>`
      로 덮어쓰기, 또는 심기 전 사본을 만들어 복원). `checkout --` 은 그 파일의 **커밋되지 않은 편집 전부**를
      되돌리므로, 다른 레인이나 자기 자신이 아직 커밋하지 않은 작업이 있으면 그것까지 지운다.
      M4/4A 실측(2026-09-09): 구현 레인이 종결 반영 중 양성 대조로 `checkout --` 을 돌려 같은 파일의 미커밋
      KDoc 편집 둘이 소실됐다(즉시 복구·검증했으나 검증 절차가 산출물을 파괴한 것은 절차의 결함이다)
- [ ] scope.md의 acceptance_commands가 전부 exit 0으로 commands.md에 기록됨
- [ ] test/lint/type/architecture/contract 관련 명령 통과
- [ ] 변경된 fixture와 정책 version의 근거가 기록됨
- [ ] 알려진 제한과 rollback 방법이 기록됨
- [ ] secret 스캔 통과 — evidence 디렉토리와 커밋 diff에 대해 최소한 다음을 실행하고
      결과를 commands.md에 기록:
      `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/<m>/<s>/`
      (매치 없음 = 통과). Telegram id·사업자 정보는 패턴 스캔으로 못 잡으므로 육안
      확인을 병기한다

## 문서 slice (M0)의 evidence

acceptance command 대신 **이번 slice가 담당하는** 완료 조건 항목을 commands.md 위치에
`checklist.md`로 기록한다. 항목별로 충족 근거(파일 경로)와 잔여 `OPEN` 수를 남기고,
다른 slice 소관 항목은 `pending`으로 분리한다.

문서 slice에서 성립하지 않는 위 체크리스트 항목(acceptance_commands, rollback,
differential.json, golden-manifest.json)은 **`N/A + 사유`로 기록하면 충족**으로
판정한다. 예: `rollback: N/A — 문서 산출물은 git revert로 복구`. N/A 항목을 이유 없이
비워 두는 것은 미충족이다.
