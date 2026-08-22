---
name: codex-review-gate
description: "Codex CLI로 base...head diff의 독립 리뷰를 clean worktree에서 실행하는 절차. Codex 리뷰 요청, 재리뷰, 리뷰 JSON 저장, verdict 확인 작업 시 반드시 이 스킬을 사용. codex-reviewer 에이전트의 표준 절차."
---

# Codex Review Gate — 독립 리뷰 실행 절차

codex CLI(로컬 `codex`, v0.148 기준)로 slice diff의 독립 리뷰를 실행하고 판정 JSON을
보존한다. 판정 계약과 리뷰 기준은 `CODEX-REVIEW.md`와 `agent-workflow.md` 4~5절이
정의하며, 이 스킬은 실행 mechanics만 다룬다.

## 독립성 규칙 (why)

Codex 리뷰의 가치는 구현자와 판단 맥락을 공유하지 않는 데서 나온다. 구현 대화의 결론,
Claude의 자체 평가(verifier 리포트 포함), "이 부분은 의도된 것"류의 해명을 리뷰 입력에
넣으면 리뷰가 자기 승인이 된다. Codex에는 **요구사항 문서, base/head, diff, 커밋된
repository**만 제공한다.

이를 파일시스템 수준에서 보장하기 위해 리뷰는 **저장소 밖 clean worktree**에서 실행한다
(agent-workflow.md 5절). worktree에는 커밋된 파일만 존재하므로 `_workspace/`의 조사
노트·verifier 리포트·구현 노트가 Codex에 노출되지 않고, untracked `bid-vector` symlink도
없어 기존 저장소 접근이 원천 차단된다.

## 실행 절차

### 1. 입력 검증과 사전 스냅샷

```bash
git rev-parse --verify <base>^{commit}
git rev-parse --verify <head>^{commit}
git status --porcelain -- <scope.md의 in_scope 경로>   # 결과가 있으면 중단하고 반환
git rev-parse HEAD > _workspace/{slice}/pre-review-head.txt   # 사전 스냅샷
```

### 2. clean worktree 생성

```bash
git worktree add ../bid-vector-v2-review-{slice} <head SHA>
```

worktree는 저장소 디렉토리 **밖**에 만든다. 리뷰 종료 후 반드시 정리한다.

### 3. prompt 구성

`_workspace/{slice}/codex-prompt.md`(메인 저장소 쪽, worktree 아님)에 다음 순서로 조립:

1. `CODEX-REVIEW.md` 전문 (수정·요약 금지)
2. 리뷰 대상 명시: milestone/slice 이름, base SHA, head SHA
3. 참조 경로: `v2-지침서.md`, `agent-workflow.md`, 해당 `milestone-N.md`, 관련 ADR,
   `reports/evidence/<milestone>/<slice>/` (커밋된 것만 worktree에 보인다)
4. 출력 지시: agent-workflow.md 4절의 JSON 판정 계약만 출력할 것. schema는
   `references/codex-verdict.schema.json`과 동일 구조.

재리뷰라면 이전 finding JSON 경로(커밋된 evidence)와 새 base/head를 2번에 추가하고,
이전 finding 해소 확인만 하지 말고 **새 HEAD 전체를 다시 검증**하도록 지시한다.

### 4. codex 실행

worktree를 작업 루트로, 명령 재실행(테스트 등)을 위해 workspace-write sandbox를 쓴다 —
쓰기 가능 범위가 폐기 예정인 worktree로 한정되므로 메인 저장소는 보호된다.

```bash
codex --version   # 기록용 — 실행 결과를 리뷰 메타데이터로 남긴다
codex exec -s workspace-write -C ../bid-vector-v2-review-{slice} \
  --output-schema .claude/skills/codex-review-gate/references/codex-output.strict.schema.json \
  -o _workspace/{slice}/codex-verdict.json \
  - < _workspace/{slice}/codex-prompt.md \
  > _workspace/{slice}/codex.raw-output.txt 2>&1
```

`--output-schema`에는 **strict 변형**(`codex-output.strict.schema.json`)을 쓴다 — OpenAI
strict structured output은 모든 property가 required여야 하므로, `line`은 null 허용으로
바꾸고 레인이 주입하는 `reviewer`는 제외한 스키마다. 저장물의 정본 스키마는
`codex-verdict.schema.json`이며(reviewer 포함), 6단계 검증과 저장은 정본 기준이다.

- prompt는 stdin(`-`)으로 전달한다 (ARG_MAX 회피).
- Bash `timeout` 최대치는 10분(600000ms)이다. 리뷰는 보통 이를 초과하므로 **처음부터
  `run_in_background: true`로 실행**하고 완료 알림을 기다린다. 폴링이 필요하면
  `codex-verdict.json` 생성 여부를 확인한다.
- 첫 운영 실행 전에 `codex exec -s read-only "echo ok"` 수준의 스모크 테스트로 비대화
  모드 동작(trust 프롬프트 여부)을 1회 검증한다.

### 5. 무효 라운드 검사

실행 후 worktree의 오염을 확인한다:

```bash
git -C ../bid-vector-v2-review-{slice} status --porcelain
git -C ../bid-vector-v2-review-{slice} rev-parse HEAD   # <head>와 같아야 함
```

수정·신규 파일·새 커밋이 발견되면 그 라운드는 무효다(agent-workflow.md 1절): 무효
사유와 오염 내용을 기록하고, worktree를 삭제 후 새로 만들어 재실행한다. 메인 저장소는
worktree 격리로 인해 영향받지 않는다.

### 6. 판정 JSON 검증

`codex-verdict.json`에서 다음을 검증한다:

- `verdict`가 `approve` 또는 `request_changes`
- `reviewed_base`/`reviewed_head`가 요청한 SHA와 일치
- `request_changes`면 `findings[]`에 severity/file/evidence/required_fix 존재

검증 실패 시 1회 재실행. 재실패 시 raw 출력을 보존하고 실패로 보고한다. JSON을 임의로
보정·생성하지 않는다.

### 7. append-only 저장과 worktree 정리

```bash
cp _workspace/{slice}/codex-verdict.json \
   reports/evidence/<milestone>/<slice>/codex-review-$(date -u +%Y%m%dT%H%M%SZ).json
git worktree remove ../bid-vector-v2-review-{slice}
```

기존 리뷰 파일은 절대 덮어쓰지 않는다. 재리뷰는 새 timestamp 파일로 저장한다 —
리뷰 이력 자체가 감사 증적이다.

저장 전에 판정 JSON에 `reviewer: {cli_version, model}` 필드가 없으면 실측값
(`codex --version` 출력, 설정된 모델)으로 채워 넣는다. 이 주입은 agent-workflow.md
4절 계약이 리뷰 레인에 위임한 메타데이터 기록이며, verdict·findings에는 손대지 않는다.
같은 값을 `commands.md`에도 기록한다.

### 8. 반환

오케스트레이터에 verdict, blocker/high finding 수, 저장 경로를 보고한다. verdict 이후의
처리(수정 라운드, 사용자 보고)는 오케스트레이터의 책임이다.

## 금지

- 자체 판정으로 codex 실행을 대체
- 구현 대화 요약·자체 평가·verifier 리포트를 리뷰 입력에 포함
- 메인 저장소(worktree 아닌 쪽)를 codex의 작업 루트로 지정
- 리뷰 JSON 수정, finding 축소, 파일 덮어쓰기
- `approve`를 merge/push/배포 승인으로 해석
