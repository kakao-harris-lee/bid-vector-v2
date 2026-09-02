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
| 2026-08-30 | 재작업 상한을 **5회**로 명시하고 「상한 도달 = 접근 재검토」로 성문화, 리뷰·검증을 증분마다 걸지 않도록 Phase 4·5 규정 | v2-slice-pipeline | 운영자 지시 — M0/0C에서 검증·수정 루프를 스무 라운드 넘게 돌렸다. 상한이 있었으나 「한 라운드 더」를 매번 여쭙는 방식으로 무력화됐다 |
| 2026-08-30 | evidence 규격을 금지로 명시 — **출력 전문 금지**(핵심 결과 한 줄) · **라운드 이력 절 금지** · **자기 검사 하네스 금지** · **크기 게이트**(evidence ≤ 산출물) | evidence-pack, v2-slice-pipeline | 운영자 지시 — M0 실측에서 evidence 20,373줄 대 산출물 8,730줄(2.3배), 0C 커밋 154개 중 108개가 산출물 무접촉. 출력을 문서에 박은 것이 선언 SHA 래칫·재취득·축어 대조·고정 표본을 차례로 불렀다 |
| 2026-08-31 | 잔존 「총 4회」 둘(Phase 5 병기·에러 표)을 「총 5회」로 정합 — 2026-08-30 상한 개정의 전파 누락 | v2-slice-pipeline | Codex 0E 재리뷰 B2 finding #2 (medium) — 한 워크플로우에 상한이 둘 |
| 2026-09-01 | codex 바이너리 경로(nvm `v22.21.1/bin/codex`)·버전(0.151.0) 고정 + 실행 전 버전 assertion(불일치 = preflight 미충족) | codex-review-gate | B5 리뷰 레인 실측 — 바이너리 둘 공존(homebrew 0.148.0 / nvm 0.151.0)으로 PATH 순서가 라운드마다 심판 버전을 조용히 결정(B3·B4 는 0.149.0, B5 는 0.148.0). effort 고정과 같은 재현성 사유. 핀 값 0.151.0 은 운영자 지정(사용 중인 최신) — **B6 부터 B5 와 엔진이 다름**을 스킬에 명기 |
| 2026-09-01 | Phase 5 에 **장부층 종결선** 신설 — 재리뷰가 도메인·계약층 finding 없이 장부층(evidence 정합성) non-blocker 만 내면 수정 라운드 없이 「알려진 제한」 등재 + 사용자 승인으로 종결 | v2-slice-pipeline | 운영자 채택 — 0E finding 계보 분석: B4 이후 전 finding 이 수정 커밋 자신이 낡게 만든 장부층(B5 high 는 B4 수정이, r9 F-1·F-2 는 B5 수정이 생성). 「approve = 장부의 완벽성」이면 루프가 정의상 안 끝남. 도메인층 finding 이 하나라도 있으면 미적용 |
| 2026-09-02 | 누출 검사 **판독 규칙** 추가 — diff 가 스킬 자신·CLAUDE.md 이력을 담는 라운드는 grep 에 상시 false-positive 바닥, developer 구간 매치만 주입으로 판독 | codex-review-gate | B6 실측 — `dc27007` 이 주입 패턴 표를 리뷰 diff 에 넣어 매치 9 발생, 전부 diff hunk 안·developer 구간 0. 핀 도입(2026-09-01)이 만든 부작용의 마무리 |
| 2026-09-02 | 장부층 종결선을 **정지·보고형으로 개정** — request_changes 는 slice 를 닫지 못하고, 장부층-only·상한 도달 = 자동 라운드 없이 **미승인 상태로 정지·보고**, 종결은 운영자 개별 명시 결정(예외 기록) | v2-slice-pipeline | Codex B8 high — 원 규칙이 리뷰의 차단력을 제거해 agent-workflow.md(임의 축소 금지)·milestone-0.md:133(approve+사용자 승인)과 충돌. 운영자 결정으로 반루프 취지(자동 라운드 금지)만 남기고 재분류 권한은 철회 |
| 2026-09-02 | 검증 6단계에 **종말 verdict 확인** 추가(`commands_run` 빈약한 approve = 미완 실행) + 위협 모델에 **hooks 표면** 등재(stdout 쓰는 hook = preflight 미충족) | codex-review-gate | B8 리뷰 레인 실측 — codex 가 작업 전 조기 approve 를 내뱉음(raw 136행, `-o` 종말 캡처로 사고 면함) · 한 라운드 262 hook 이벤트가 두 플래그로 안 닫힘(현 orca hook 은 stdout 무기록·no-op 확인) |
| 2026-09-02 | hooks 검사를 **실행 실측**으로 규정(정적 grep 금지 — 3경로 실행 stdout 실측) · 조기 verdict 를 **체계적 거동**으로 갱신(B8·B9 연속) | codex-review-gate | B9 레인 실측 — 정적 스캔이 curl 파이프·`/dev/null` 리다이렉트된 printf 를 오탐해 정상 라운드를 떨어뜨릴 뻔함 · 조기 approve 가 B9 에서도 재현(raw 138행) |
| 2026-09-02 | 조기 verdict 가 **실행 중 어느 지점에서든** 나옴을 명기 — B10 에서 한 라운드 verdict 6회, 명령 0건 approve 가 끝자락(7684행) | codex-review-gate | B10 레인 실측 — 조기 방출이 앞부분에 국한되지 않음이 확인돼 종말 verdict 확인의 근거를 갱신. 캡처가 조금만 일렀어도 가짜 승인이 저장될 뻔함 |
| 2026-09-02 | **직전 행을 정정** — 「끝자락 7684행 approve」는 codex 가 jq 로 읽은 0A verdict JSON 의 stdout(방출 아님), 「6회」는 템플릿·파일 출력 혼입(실제 방출 4회, 조기 approve 2회는 140·3150행 — 둘 다 종말 이전). **방출 계수 규칙**(codex 표지 기준 — 뜬 텍스트는 방출 아님)을 스킬에 성문화 | codex-review-gate | 구현 레인이 C-19 등재 중 원문 대조로 오독 발견(파일 읽기 출력을 방출로 — 누출 검사와 같은 갈래). B10 의 실제 신사실은 「한 라운드 조기 approve 2회」이고 종말 확인의 필요성은 불변 |
| 2026-09-02 | 계수 규칙에 **CLI echo 절** 추가 — `tokens used` 블록이 앞서면 `codex exec` 의 종료 시 재출력이지 방출 아님 | codex-review-gate | 리뷰 레인이 성문화된 규칙을 재실측해 발견 — echo(B8 10027 · B9 9181 · B10 7704행, 세 라운드 전부)가 「codex 표지」 규칙을 통과해 라운드마다 +1 과대계수. 보정 계수: B8 2 · B9 2 · B10 4(조기 approve 2 — 2%·41% 지점, 중반 방출 실재) |
| 2026-09-02 | 정지선 문단의 **「Codex approve 없는 종결 예외」 절 삭제** — 정지선은 미승인 정지·보고뿐, 종결 조건은 milestone-0(approve+사용자 승인)이 정본, 조건 변경은 agent-workflow.md·milestone 계약의 별도 명시 개정으로 | v2-slice-pipeline | Codex B13 high — B8 개정이 차단을 선언한 자리에서 곧바로 예외로 풀어 같은 문단의 「차단력 불변」과 자기모순, 13라운드 request_changes 인 slice 를 완료로 표시할 문이 남아 있었음. 운영자 결정 2026-09-02 |
