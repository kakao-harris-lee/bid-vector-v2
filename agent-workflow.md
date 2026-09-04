# Claude 구현 · Codex 독립 리뷰 운영 규약

## 1. 역할 분리

### Claude — 구현자

Claude만 작업 branch의 코드를 수정한다.

- 기준 SHA, 마일스톤, 이번 slice의 in/out scope를 먼저 기록한다.
- 기존 코드와 테스트에서 도메인 규칙 후보와 실패 사례를 조사한다.
- 버그/이전 작업은 승인된 기대값으로 실패하는 regression test를 먼저 만든다.
- 필요한 최소 변경만 구현한다.
- 테스트, lint, typecheck, 계약 검사, 회귀 검사를 실행한다.
- 리뷰용 커밋과 evidence 문서를 만든다.
- Codex finding을 임의로 축소하거나 리뷰 JSON을 수정하지 않는다.

Claude는 자신의 구현을 최종 승인할 수 없다.

### Codex — 독립 리뷰어

Codex는 해당 리뷰 라운드에서 코드를 수정하지 않는다.

- 지정된 base와 HEAD 사이의 diff만 리뷰한다.
- 요구사항, 승인된 명세, 테스트, 계약, 실패 경로를 독립적으로 확인한다.
- 테스트 통과 보고를 신뢰하는 데 그치지 않고 중요한 명령을 재실행한다.
- 범위 밖의 기존 부채를 이번 finding으로 섞지 않는다.
- 결론은 `approve` 또는 `request_changes` 중 하나로 남긴다.

Codex가 수정안을 직접 커밋하면 독립성이 깨지므로, 그 라운드는 무효로 하고 새 구현
커밋 이후 다시 리뷰한다.

### 사용자 — 승인권자

다음 행위는 테스트와 리뷰 승인(verifier·Codex)이 있어도 자동 승인되지 않는다.

- merge/push
- 운영 DB write, backfill, schema writer 전환
- 실제 외부 API/LLM 호출, Telegram/email 발송
- 외부 공개, feature 활성화, release 전환
- 이전 runtime/table/branch/worktree 삭제

## 2. slice 단위 작업 계약

각 slice는 시작 전에 아래 여섯 항목을 가진다.

```yaml
milestone: M2
slice: money-rate-basis-kernel
base_sha: <40-char SHA>
in_scope:
  - app/shared-kernel
  - 관련 contract와 property/regression test
out_of_scope:
  - DB schema/write
  - 외부 공개/배포
acceptance_commands:
  - ./gradlew test
  - ./gradlew architectureTest
rollback: 이번 slice의 신규 V2 wiring을 비활성화하는 방법
```

작업 중 scope를 넓혀야 한다면 기존 slice를 멈추고 계약을 갱신한다. “같이 고치면 편하다”는
사유만으로 다른 aggregate나 운영 경로를 포함하지 않는다.

## 3. 표준 실행 순서

1. **Preflight**
   - branch, status, base SHA 확인
   - 관련 기존 코드·호출자·테스트·writer 검색
   - 외부 side effect가 없는 검증 명령 확인
2. **RED evidence**
   - 승인된 도메인 규칙과 회귀 기대값을 고정한 테스트 작성
   - 신규 요구라면 계약 test가 구현 없이 실패함을 확인
3. **구현**
   - 순수 코어와 I/O adapter를 분리
   - 신규 dependency와 모듈 방향을 명시
   - 기존 Python 구조를 복제하지 않고 V2 모듈 경계를 따른다.
4. **검증**
   - 영향 범위 unit/contract/property test
   - 필요한 경우 Python과의 diagnostic differential test와 차이 판정 기록
   - lint/typecheck/architecture/size ratchet
   - 필요한 경우 전체 CI 등가 명령
5. **리뷰 패키지**
   - 커밋된 diff
   - 실행 명령, 종료 코드, 핵심 결과
   - golden 변경 목록과 이유
   - 알려진 제한, rollback 방법
6. **Codex 리뷰 — 운영자 명시 요청 시에만 (2026-09-04 개정)**
   - 코드 slice 의 기본 경로는 5단계 verifier 로 끝난다. Codex 는 운영자가 요청한 경우에만 건다
   - clean worktree에서 `base...HEAD` 리뷰
   - `request_changes`면 Claude가 별도 커밋으로 수정
   - 같은 범위로 재검증·재리뷰
7. **사용자 결정**
   - verifier 판정(Codex 를 걸었으면 그 verdict 도)과 evidence를 함께 보고
   - 사용자가 merge/release 여부 결정

## 4. Codex 판정 계약

권장 구조화 결과:

```json
{
  "verdict": "approve | request_changes",
  "reviewed_base": "<sha>",
  "reviewed_head": "<sha>",
  "findings": [
    {
      "severity": "blocker | high | medium | low",
      "file": "path",
      "line": 1,
      "evidence": "재현 가능한 결함과 영향",
      "required_fix": "승인을 위해 필요한 최소 수정"
    }
  ],
  "commands_run": ["..."],
  "residual_risks": ["..."],
  "reviewer": { "cli_version": "codex-cli x.y.z", "model": "<리뷰에 사용한 모델>" }
}
```

`reviewer` 필드는 리뷰 재현성을 위한 메타데이터다. Codex 출력에 없으면 리뷰 레인이
저장 전에 실측값(`codex --version`, 설정된 모델)으로 채워 넣는다. 이 주입은 verdict와
findings를 변경하지 않는 메타데이터 추가로, 판정 수정 금지 규칙의 예외가 아니라
계약이 위임한 기록 행위다.

다음 중 하나면 `request_changes`다.

- acceptance criterion 또는 필수 명령 미충족
- 승인된 도메인 명세·공식 계약·수작업 판정 기대값 불일치
- Python 결과 차이를 근거 없이 복사하거나 근거 없이 무시함
- 금액/basis/rate/rounding/provenance 의미 손실
- V2 Kotlin과 ML engine 사이에 업무 규칙이 중복되거나 숨은 fallback이 존재함
- 테스트/dry-run 경로가 DB write, 알림, task fan-out을 일으킴
- contract breaking change가 versioning/consumer 증거 없이 포함됨
- 테스트가 실제 production 경로를 통과하지 않거나 mutation으로 우회 가능함
- rollback 절차가 없거나 실제로 되돌릴 수 없음

낮은 중요도의 개선 의견만 있고 acceptance가 충족되면 승인할 수 있다. 이때 잔여 위험을
명시하되, 후속 아이디어를 현재 blocker로 과장하지 않는다.

## 5. 리뷰 독립성 보장

- 구현 대화의 “정답”이나 Claude의 자체 평가를 Codex prompt에 넣지 않는다.
- Codex에는 요구사항, base/head, diff, repository만 제공한다.
- 리뷰 산출물은 append-only 경로에 저장하고 덮어쓰지 않는다.
- 재리뷰는 이전 finding과 새 diff를 모두 확인하되, 새 HEAD 전체를 다시 검증한다.
- 자동 수정과 자동 merge는 금지한다. 여기서 자동 수정 금지란 리뷰 레인(Codex)이
  finding을 스스로 고치는 것을 뜻한다. Claude가 finding을 받아 별도 커밋으로 수정하고
  재검증·재리뷰를 거치는 수정 라운드는 이 금지에 해당하지 않으며, 아래 상한(두 번의
  수정 라운드)을 따른다. merge는 어떤 경우에도 사용자 결정이다.
- 두 번의 수정 라운드 후에도 blocker가 남으면 자동 반복을 중단하고 사용자에게 판단을
  요청한다.

## 6. 마일스톤 공통 evidence

각 slice의 `reports/evidence/<milestone>/<slice>/`에는 최소 다음을 남긴다.

- `scope.md`: base/head, in/out scope, acceptance
- `commands.md`: 실행 명령과 종료 코드
- `differential.json`: 적용 가능한 경우 Python/V2 차이와 명세 기반 판정
- `golden-manifest.json`: 사용 fixture의 출처와 SHA-256
- `codex-review-<timestamp>.json`: 독립 리뷰 결과
- `rollback.md`: 되돌리는 flag/route/writer와 예상 복구 시간

생성 로그에 secret, 원문 사업자 정보, Telegram 식별자, API key를 남기지 않는다.
