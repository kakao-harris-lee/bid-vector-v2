# M6/G1-T — G1 의 「측정만 없음」 넷을 test 로 닫는다 (착수 계약, 2026-10-16, 팀장)

- **근거**: 운영자 결정 2026-10-15(`milestone-6.md` 「완료 조건」 아래 결정 블록). 완료 조건 3 의 판정 대상은 성립 15 + G1 9 이다. G1 가운데
  COL-02 · COL-03 · DEC-06 · OPS-01 은 조정안 규칙상 「측정만 없음」이라 **test 신설**로 닫는다. 운영자 지시 2026-10-16 「병렬로 진행
  가능한 작업」으로 M7 레인과 병렬로 연다.
- **base**: main `77850903`(PR #76 머지) · worktree `bid-vector-v2-m6-g1t` · 브랜치 `m6-g1t/2026-10-16`.
- **입력**: `reports/evidence/m6/6e1/acceptance-trace.md` 의 네 행(「무엇이 비었는가」 문면이 정본) · `docs/discovery/capability-map.md` 각
  capability 의 acceptance bullet.

## 산출물

| ID | capability | 잴 것(추적표 문면) | 형태 |
|---|---|---|---|
| T-1 | COL-02 | 실 기초금액이 **없는** 개찰 pass 를 흘려도 저장된 기초금액이 **덮이지 않는다**(기초금액 축) | persistence 통합 test(Testcontainers) — 값 있는 관측 → 결측 재관측 → 저장값 불변 |
| T-2 | COL-03 | 상세 조회가 429 로 **재시도 소진** → 그 공고는 예비가격 없이 저장 → **나머지 수집 계속** → 실패 건수·사유가 회계에 실린다 | 수집 경로 test — 소진·저장·계속·회계 넷을 한 시나리오로 잇는다 |
| T-3 | DEC-06 | 같은 공고를 **dry-run 경로와 커밋 경로**로 흘려 액션과 근거(reason code, payload)가 같다 | 두 진입점을 실제로 각각 부르는 대조 test(같은 입력 → 결과 등식) |
| T-4 | OPS-01 | 홀더 세션을 **강제 종료**(`pg_terminate_backend` 또는 연결 끊김)하면 lease 가 즉시 해제된다 | Testcontainers PostgreSQL — 홀더 세션 종료 → 다른 세션이 즉시 획득 |

- 각 test 는 **구판(그 성질을 깨는 변이)에서 RED** 임을 음성 대조로 보인다 — 바꿔치기 변이 + `git diff --numstat` 확인 + 통제군 GREEN.
  변이는 production 코드를 잠깐 바꿔 재고 되돌린다(커밋 전 원복, `checkout --` 금지 — 사본 복원).
- **production 코드는 바꾸지 않는다.** test 가 실제 결함을 드러내면 멈추고 팀장에게 보고한다(그 경우 G1 처분이 「측정만 없음」이
  아니게 되어 계약을 갱신한다).
- 추적표 갱신: 네 행의 판정을 ⓒ → ⓐ 로, 근거 칸에 새 test 의 FQN 을 적는다(좌표 금지). `milestone-6.md` 는 건드리지 않는다.

## in_scope / out_scope

- in: 각 모듈의 `src/test/**` 아래 새 test 파일(경로는 레인이 정하고 evidence 에 기계 목록으로) · `reports/evidence/m6/6e1/acceptance-trace.md`
  의 네 행 · `reports/evidence/m6/g1t/**`.
- out: `src/main/**` 전부 · 마이그레이션 · `.github/**` · `web/**` · `openapi/**` · 다른 행의 추적표 판정.

## acceptance

`.github/workflows/ci.yml` 의 `check` job 명령 그대로(Kotlin). `web`·`ml-engine`·`container` job 은 입력 변경 0 을 path-limited diff 로
보이면 생략한다.

## rollback

새 test 파일 삭제 + 추적표 네 행 복원 — `git restore --source=77850903 --staged --worktree -- <in_scope 개별 경로>`(evidence 디렉터리
제외). 목록은 `git diff --name-status 77850903..HEAD` 기계 산출. 실측은 마지막 산출물 커밋에서 ①~⑥.

## 검토 레인

verifier(opus) · code-reviewer(sonnet) 병렬. 표적: 각 test 가 추적표 문면 그대로를 재는가(이름만 비슷한 test 금지) · 변이에서 RED 인가 ·
T-3 이 정말 **두 진입점**을 부르는가(같은 함수를 두 번 부르는 가짜 대조 금지) · T-4 가 `finally` 경로가 아니라 **세션 종료**를 재는가.
Codex 없음(되돌리기 어려운 경로 아님).

## 호스트 빌드

이번 착수는 레인 넷이 병렬이다. **무거운 빌드는 호스트 전역 잠금으로 직렬화한다**: 모든 Gradle·Testcontainers 명령은
`flock ~/.local/bid-vector-build.lock <명령>` 으로 감싼다. 그 위에 CLAUDE.md 의 점검 셋(pgrep·free·ps)을 별도 호출로 먼저 본다. 00:30~03:00
(6G 수집 창)에는 무거운 빌드를 시작하지 않는다.

## 계약 갱신 이력

**D-G1T-1 (2026-10-16, 팀장 — 착수 preflight 수령).**
- **T-2 처분 (b)+(c).** 실측 결과 production 은 429 재시도가 소진되면 **그 실행을 멈춘다**(`QuotaExhausted` halt · 남은 표본은
  `notAttempted` — D-6G-11·D-6G-44 K6, 기존 test 가 멈춤을 단언). 6G 는 canonical 승격을 하지 않는다(D-6G-1). 그래서 capability map
  COL-03 셋째 bullet 의 「나머지 수집은 계속」은 V2 가 의도적으로 바꾼 거동과 어긋난다. 명세-구현 불일치이고 결함이 아니다.
  - T-2 는 **두 실행 시나리오**로 쓴다. 실행 1: 429 소진 → 그 공고는 raw 만 남고 축 미완 → halt 에 `notAttempted` → 회계에
    `quotaExceeded`·`truncationCause`. 실행 2(쿼터 해제): 시도 원장이 미완 축을 이어받아 남은 수집이 계속된다.
  - 추적표 COL-03 행은 ⓐ 로 올리지 않는다. 「test 성립 · bullet 문면 갱신 필요(운영자 확인)」로 남긴다. capability map 문면 갱신은 팀장이
    운영자 확인 뒤 별도로 한다. 이 slice 는 capability map 을 건드리지 않는다.
- **in_scope 추가**: `config/quality/gate-tests.properties` — 새 test 클래스 FQN 등재만 한다(모듈 키 아래 한 줄씩, 값·정책 version 변경
  없음). `gateRegistrationGate` 의 양방향 등식 때문에 필요하다.

## 하네스 레인 변경 (상시)

- (착수 시점 없음)
