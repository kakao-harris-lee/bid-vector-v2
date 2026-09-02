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

### rollback.md

되돌리는 flag/route/writer, 예상 복구 시간, 검증 방법.

### codex-review-*.json

codex-reviewer 에이전트만 작성한다 (`codex-review-gate` 스킬). 다른 에이전트는 이
파일을 생성·수정하지 않는다.

## 리뷰 요청 조건 점검 (verifier용)

Codex 리뷰를 요청하기 전에 전부 충족해야 한다 (프로젝트 CLAUDE.md 기준):

- [ ] 구현 diff가 커밋되어 base/head 고정 —
      `git status --porcelain -- <scope.md의 in_scope 경로>` 결과 없음.
      scope 밖 로컬 파일은 판정에 넣지 않는다 (clean-tree 게이트 정의는 이 한 곳이
      기준이다)
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
