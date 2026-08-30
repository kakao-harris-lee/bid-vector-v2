# Claude 구현 지침

이 저장소에서 애플리케이션 코드는 Claude가 구현한다. Codex는 독립 리뷰어다.

## 시작 전 필수 읽기

1. `README.md`
2. `v2-지침서.md`
3. `agent-workflow.md`
4. `data-extract.md` (fixture/corpus 작업 시 단일 기준 문서)
5. 현재 `milestone-N.md`
6. 관련 `docs/adr/`와 `docs/discovery/`

## 구현 규칙

- 사용자와 Codex 승인을 받은 마일스톤/slice 하나만 작업한다.
- 작업 전 base SHA, in/out scope, acceptance command를 `reports/evidence/`에 기록한다.
- `bid-vector` symlink의 기존 프로젝트는 read-only 참고 자료다. 사용자의 별도 지시 없이
  수정하지 않는다.
- 기존 Python 구조·API·DB schema·출력을 1:1로 복제하지 않는다.
- 기대 동작은 승인된 도메인 명세와 authoritative fixture에서 가져온다.
- 테스트를 먼저 만들고 최소 구현 후 전체 acceptance command를 실행한다.
- 실제 DB/API/LLM/Telegram/email, push/merge/deploy는 사용자 승인 없이 실행하지 않는다.
- Codex review 파일을 수정하거나 자신의 구현을 승인하지 않는다.

## 리뷰 요청 조건

- 구현 diff가 커밋되어 base/head가 고정됨
- 관련 test/lint/type/architecture/contract 명령 통과
- 변경된 fixture와 정책 version의 근거 기록
- 알려진 제한과 rollback 또는 비활성화 방법 기록

Codex가 `request_changes`를 반환하면 같은 scope에서 Claude가 수정하고 다시 검증한다. 두 번의
수정 라운드 뒤에도 blocker가 남으면 자동 반복하지 말고 사용자에게 보고한다.

## 하네스: V2 slice pipeline

**목표:** 모든 마일스톤/slice 작업을 `조사 → 구현 → 검증 → Codex 독립 리뷰 → 사용자 보고`
파이프라인으로 실행하고, 레인 간 격리(구현자/검증자/심판)를 보장한다.

**트리거:** 마일스톤/slice 작업(조사, 명세·ADR·fixture 작성, 구현, 검증, Codex 리뷰,
수정 라운드, 부분 재실행) 요청 시 `v2-slice-pipeline` 스킬을 사용하라. 지침서에 대한
단순 질문은 직접 응답 가능.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-08-22 | 초기 구성 (에이전트 7종, 스킬 3종) | 전체 | - |
| 2026-08-22 | 재활용 우선 방침 반영 — Python ML 재활용, 라이브러리 조사 선행, 재작성 목적(유지보수·회귀 감소) 명시 | ml-implementer, kotlin-implementer, legacy-scout, v2-slice-pipeline | 운영자 지시 |
| 2026-08-22 | 두 갈래 전략 확정 — service만 Kotlin 재작성, ML은 재활용+튜닝 (지침서 greenfield 서술 개정과 동기화) | ml-implementer, v2-slice-pipeline | 운영자 지시로 지침서 개정 |
| 2026-08-22 | 독립 감사 반영 — Codex 리뷰를 저장소 밖 clean worktree로 격리, .gitignore 추가, clean-tree 게이트 단일 정의(in_scope 한정), 커밋 단계·안전 규칙·재작업 상한(총 4회)·OPEN 에스컬레이션·secret 스캔 추가, M0 문서 slice N/A 규칙, verdict JSON schema 번들 | 전 에이전트, 전 스킬, .gitignore | 하네스 독립 감사 blocker 3건·high 9건 수정 |
| 2026-08-22 | Kotlin 코딩 규율 성문화 — TDD 우선, DI 적극, 분기 도배 금지(패턴 대체), 매직넘버 금지(정책 데이터/설정 외부화), 중복·주석 최소화, 회귀 구조적 방지 | v2-지침서 §5, kotlin-implementer | 운영자 지시 |
| 2026-08-22 | codex --output-schema용 strict 변형 스키마 추가 (모든 키 required, line null 허용, reviewer 제외) | codex-review-gate | 첫 리뷰 실행에서 OpenAI strict 제약 위반으로 400 실패 발견 |
| 2026-08-26 | codex 호출에 `model_reasoning_effort=high` 고정 | codex-review-gate | M0/0A 3차 리뷰에서 같은 range가 effort에 따라 medium `approve` / high `request_changes`로 갈려 리뷰 재현성 결여 발견 |
| 2026-08-27 | codex 호출에 `features.memories=false` 추가 + memory 흔적 preflight | codex-review-gate | M0/0B 4차 리뷰에서 codex의 첫 명령이 worktree 밖 `~/.codex/memories/MEMORY.md`를 읽어, worktree 격리가 읽기를 막지 못함이 드러남. **override의 효과는 다음 라운드에서 실증 확인 대기** |
| 2026-08-30 | preflight ②의 `?mode=ro` → `?immutable=1` 교체 + 총 행 수 동반 확인 | codex-review-gate | `mode=ro`가 이 환경에서 반복 실패(`unable to open database file (14)`). 0D가 진동을 관측 다섯으로, 0C 6차 리뷰어가 기제를 진술. **기제는 미확정이고 교체는 「`immutable=1`이 열린다」는 실측에만 기댄다.** `immutable=1`이 동시 writer 부재를 가정하므로 「0행」이 「못 열어서 0」이 아님을 총 행 수로 확인한다 |
