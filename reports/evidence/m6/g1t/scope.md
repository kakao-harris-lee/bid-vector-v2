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
- **D-G1T-2 (2026-10-16, 운영자 승인 「고쳐줘」).** capability map COL-03 셋째 bullet 을 V2 거동에 맞춘다. 실행은 멈추고, 다음 실행이 미완 축을
  이어받아 계속한다. 개정 블록도 함께 둔다. 문면은 팀장이 직접 고쳤다(`docs/discovery/capability-map.md` COL-03 절만 in_scope 에 더한다).
  그 결과 T-2 가 성립하면 추적표 COL-03 행을 **ⓐ** 로 올린다(D-G1T-1 의 「갱신 대기」를 대신한다).
- **in_scope 추가**: `config/quality/gate-tests.properties` — 새 test 클래스 FQN 등재만 한다(모듈 키 아래 한 줄씩, 값·정책 version 변경
  없음). `gateRegistrationGate` 의 양방향 등식 때문에 필요하다.

**D-G1T-3 (2026-10-16, 팀장 — 구현 수령 · 동결).**
- 판정 대상은 산출물 `40dded85` · evidence `855c287d` 이다.
- 6E-1 추적표의 **집계 갱신을 수용한다**(ⓐ 13→17 · ⓒ 46→42 · 성립 범위 15→19/61 · 축 표 · 「부분 측정(강함)」 열거 · 「후속이 test 신설인
  ⓒ」 절). 네 행만 바꾸면 그 문서가 자기와 어긋나기 때문이다. 6E-1 등식 재실행에서 남은 DIFF 넷(E-19·E-20a·E-20b·E-25)은 base 에서도
  같다(이 slice 밖의 drift).
- **COL-03 ⓐ 의 전제를 verifier 표적으로 넘긴다**: 6G production 호출부가 조회 가치 술어에 상수(`alreadyHeld=false`·`lastCheckedAt=null`)를
  넘겨 age-gate·recheck-gate 가 출하 경로에서 발화하지 않는다(구현 레인 실측). COL-03 앞 두 bullet 이 ⓐ 술어 ②(출하 배선에서 성립)를
  만족하는지 판정 결과에 따라 COL-03 을 ⓐ 로 둘지 정한다.
- 이 range 의 팀장 커밋(`91c1718c`·`3cc30d49`·`b4ca492c`)은 계약과 capability map(D-G1T-2, in_scope)이다. **하네스 파일(`.claude/**`·
  `CLAUDE.md`) 변경은 없다.**

**D-G1T-4 (2026-10-16, 팀장 — 검토 r1 수령, 재작업 1/5).** verifier r1 은 not-ready 였다(B-1: COL-03 ⓐ 는 거짓 성립). code-review 는
REQUEST_CHANGES 였다(H-1 COL-02 문면 · H-2 COL-03). 운영자 결정은 두 가지다.
- **COL-03 은 ⓒ 로 돌린다.** 6G 의 유일한 production 호출부가 조회 가치 술어에 상수를 넘겨 age-gate·recheck-gate 가 출하 경로에서 발화하지
  않는다(verifier 변이 N-5·N-5b). 둘째 bullet 은 미성립이다. 남은 일은 **출하 경로에 age-gate 를 실제로 배선하는 production 수정**이고,
  G1 후속 slice 로 등재한다. COL-03 은 「측정만 없음」이 아니게 됐다. 첫째 bullet 의 근거는 시도 원장(T-2 실행 2 · 원장 test)으로 고쳐
  적는다. 셋째 bullet(개정)은 T-2 가 성립시킨다.
- **COL-02 는 문면을 재관측으로 개정하고 ⓐ 로 둔다.** 개정 블록은 팀장이 capability map 에 썼다. V2 의 개찰 수집은 공고 행에 쓰지 않는다.
- COL-03 셋째 bullet 문구를 실측에 맞췄다. 「시도 안 함」 수는 멈춤 보고에, 사유·429 수는 회계에 실린다(verifier M-a).
- 수정 라운드 대상: 추적표 COL-03 행(ⓒ · 후속) · 집계(ⓐ 16 · ⓒ 43 · COL 축 · 성립 18/61) · 낡은 수 둘 · T-4 종료 대기 · T-3 빈 목록 단언 ·
  T-3 상한 계층 경계 문면 · COL-03 행의 어댑터 측 인용 · capability map 역방향 좌표 실측 · 후속 등재(6E-1 checklist 수치 · milestone-6 G1
  처분 변화).
- 동결 뒤 레인 커밋 `a5a0b56d`(checklist 변이 표 보강)가 있었다. evidence 만 바뀌었고 이번 라운드에 포함한다.
**D-G1T-5 (2026-10-16, 팀장 — 수정 라운드 1 수령 · 동결).**
- 판정 대상은 산출물 `4bf953d1` · evidence `8d0e5fbc` 다.
- 레인의 후속 두 줄(F-1·F-2)을 팀장이 이 slice 안에서 닫는다. in_scope 에 다음 두 자리를 더한다:
  - `milestone-6.md` 결정 블록 아래 「진행 2026-10-16」 문단: G1 처분 변화와 남은 G1 여섯.
  - `reports/evidence/m6/6e1/checklist.md` 의 「후속 갱신」 인용 블록. 6E-1 종결 수치는 되쓰지 않고 포인터만 단다.
- **COL-03 에 「부분 측정(강함)」 표식을 단다.** bullet 셋 중 둘이 측정되고 하나가 비었다. 단다면 머리 열거와 등식 E-24 를 함께 고친다.
  레인이 한 커밋으로 반영한 뒤 표적 재검증을 받는다.

## 하네스 레인 변경 (상시)

- 없음(이 slice 의 range 에 `.claude/**`·`CLAUDE.md` 변경 0).
