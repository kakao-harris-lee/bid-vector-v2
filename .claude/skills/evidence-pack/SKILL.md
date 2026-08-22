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
