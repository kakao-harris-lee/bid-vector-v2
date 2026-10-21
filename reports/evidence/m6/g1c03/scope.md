# M6/G1-C03 — 6G 출하 경로에 age-gate 를 배선한다 (착수 계약, 2026-10-21, 팀장)

- **근거**: G1-T 결정 D-G1T-4(`reports/evidence/m6/g1t/scope.md`) — verifier 가 COL-03 ⓐ 를 거짓으로 판정했다(6G 의 유일한 production
  호출부가 조회 가치 술어에 상수를 넘겨 age-gate·recheck-gate 가 출하 경로에서 발화하지 않는다). 운영자는 COL-03 을 ⓒ 로 돌리고
  「출하 경로 age-gate 배선」을 G1 후속 production slice 로 등재했다. 운영자 지시 2026-10-21 로 M7 레인(7B · 7C②)과 병렬로 연다.
- **base**: main `bee7c36f`(PR #80 머지) · worktree `bid-vector-v2-m6-g1c03` · 브랜치 `m6-g1c03/2026-10-21`.
- **입력 정본**:
  - `reports/evidence/m6/6e1/acceptance-trace.md` COL-03 행의 후속 칸: 「6G 호출부가 술어에 실제 관측값을 넘기고 건너뛴 건수를 세는 자리를
    만든다」.
  - `docs/discovery/capability-map.md` COL-03 둘째 acceptance bullet: 「개찰 후 age-gate 미만 경과한 공고는 이번 스윕에서 상세 조회를
    하지 않고 `backoff_skipped`로 계수된다. gate를 넘긴 뒤 스윕에서는 정확히 1회 조회된다.」
  - G1-T `checklist.md` 의 판정 문면과 변이 결과(게이트를 꺼도, 값을 10만 h 로 바꿔도 거동 test 가 붉어지지 않았다).

## 산출물

1. **호출부 배선** — `CollectOpeningResultsUseCase` 의 상수 호출(`alreadyHeld=false` · `openingObservedAt=null` · `lastCheckedAt=null`
   과 `as DetailFetchDecision.Fetch` 강제 캐스트)을 없앤다. 술어 `decideDetailFetch` 에 **실제 관측값**을 넘기고 네 결과(AlreadyHeld ·
   AgeGateNotPassed · RecheckGateNotPassed · Fetch)를 모두 분기한다.
   - **age 기준 시각을 계약으로 하나 정한다**: capability 문면이 「개찰 후」이므로 기준은 **개찰 일시**다(필드 계약 `OPENING_SCHEDULED_AT`).
     표본틀(`OpeningSampleFramer`/`Candidate`)이 그 값을 실어 나르게 한다. 레인이 다른 기준이 맞다고 판단하면 근거를 들어 멈추고 보고한다.
   - **기준 시각이 없거나 해석할 수 없는 공고**의 처분을 하나로 선언하고 test 로 잠근다. 조용한 `Fetch` 폴백은 금지다. 선택지와 근거를
     evidence 에 적는다(예: 판정 불가로 건너뛰고 별도 계수).
   - `alreadyHeld` 와 `lastCheckedAt` 은 이미 있는 이어 돌기 입력(`CollectedAxisStore` · 시도 원장의 축 결론과 시각)에서 얻는다. 새 저장소는
     만들지 않는다.
2. **건너뜀 계수** — Skip 된 공고·축을 회계에 싣는다. capability 문면은 `backoff_skipped` 다. 지금 production 의 `backoff_skipped` 는
   rate limiter 허가 거부만 센다. **두 뜻을 한 칸에 섞을지, 사유별로 가를지**를 설계 검토에서 정하고 evidence 에 적는다. 섞는다면
   사유를 잃지 않는 경로(원장 줄의 사유 등)를 함께 둔다. 기존 `collection_run.backoff_skipped` 열을 쓰고 **마이그레이션은 없다**.
3. **원장(시도 원장 파일) 호환** — Skip 을 원장에 적는다면 형식 변경이 이어 돌기 판독을 깨지 않아야 한다. 옛 형식 원장을 새 코드가
   읽는 경로를 test 로 잰다. 6G 실수집은 옛 jar 로 계속 돈다(운영자 결정 2026-10-14 「옛 jar 유지」, 6G 종결 예상 10-29경). 이 slice 는
   실수집의 run-state·DB 를 **건드리지 않는다**.
4. **거동 test**(use case 층 — 지금 없다):
   - age 미만 → 이번 스윕 상세 호출 0 · 계수 1. gate 를 넘긴 다음 스윕 → **정확히 1회** 조회.
   - recheck-gate 의 같은 쌍.
   - AlreadyHeld → 호출 0.
   - 기준 시각 결측 → 선언한 처분.
   - 각 test 는 변이에서 RED 여야 한다. 변이는 ① 호출부를 옛 상수로 되돌림 ② 게이트 값을 10만 h 로 ③ Skip 분기를 Fetch 로. G1-T 에서
     초록이던 바로 그 변이들이다. 바꿔치기 변이 + `git diff --numstat` 확인 + 통제군 GREEN.
5. **추적표·capability** — COL-03 행을 ⓒ → ⓐ 로, 근거 칸에 새 test FQN 을 적는다(좌표 금지). 둘째 bullet 이 출하 배선에서 성립함을
   보인다. 집계(ⓐ·ⓒ·성립 수)를 갱신한다.
6. **정책 값 대조** — `CollectionPolicy` 의 `DetailFetchGates(24, 48, 3)` 가 승인된 값인지 출처를 evidence 에 적는다. fixture 주석은
   legacy 24h/48h 를 「옮기지 않았다」고 적는데 production 정책은 24/48 이다. 출처가 없으면 값을 바꾸지 말고 보고한다(값 결정은 운영자).
7. `milestone-6.md` 를 고치게 되면 G1-T 이월 F-3(「진행」 문단 괄호 문면)을 같이 닫는다. 고치지 않으면 이월을 유지한다.

## 설계 검토 (Phase 2.5, 팀장)

- **(0) 위협 모델**: 방어 — 게이트가 다시 조용히 꺼지는 회귀(상수 주입·캐스트) · 기준 시각 결측이 전수 Fetch 로 새는 것(KONEPS 호출
  예산 낭비·429) · 건너뜀이 회계에서 사라져 「수집이 줄었는데 오류는 0」으로 보이는 것 · 원장 형식 변경이 이어 돌기를 깨는 것.
  방어하지 않음 — 게이트 값 자체의 타당성(운영자 결정 사항).
- **(1) 구성**: 술어의 네 결과를 `when` 으로 전수 분기하고 `else` 를 두지 않는다(sealed 전수). `Fetch` 의 `internal constructor` 와
  port 서명이 「Fetch 만 상세를 부른다」를 타입으로 이미 잠근다 — 그 잠금을 풀지 않는다.
- **(2) 우회 ≥5**: ① 호출부 상수 복귀 ② 게이트 무력화(값 확대) ③ Skip 을 Fetch 로 ④ 기준 시각 결측 → Fetch ⑤ 건너뜀 미계수
  ⑥ 옛 원장 판독 실패. 각각 test 로 RED.
- **(3) 과잉·미달**: 게이트 값 변경 · 새 표 · 실수집 jar 교체는 하지 않는다. 미달 금지 — 「정확히 1회」(두 번째 스윕 조회 0 이 아니라 1).

## in_scope / out_scope

- in: `workflow/.../collection/**`(use case·표본틀) · `procurement/**` 의 술어 주변(서명 변경이 필요할 때만, 근거와 함께) · 원장 어댑터
  (`adapters/.../snapshot/**`, 형식 변경 시) · 회계 집계(`SourceAccountingTally` 등) · 해당 test · `config/quality/*.properties`(새 test 등재,
  넓히는 줄마다 근거) · `reports/evidence/m6/6e1/acceptance-trace.md` COL-03 행과 집계 · `docs/discovery/capability-map.md` COL-03 절 ·
  `reports/evidence/m6/g1c03/**`. 경로는 레인이 기계 목록으로 적는다.
- out: 마이그레이션 · `openapi/**` · `web/**` · `.github/**` · `CollectionPolicy` 의 게이트 **값** · 실수집 run-state·DB·jar · 다른 행의 판정.

## acceptance

`.github/workflows/ci.yml` 의 `check` job 명령 그대로. `web`·`ml-engine` 은 입력 변경 0 을 path-limited diff 로 보이면 생략한다.
`container` 는 app 이미지 입력이 바뀌므로 `if:` 를 평가하는 재생으로 돌리고 ran/skipped 를 적는다.

## rollback

`git restore --source=bee7c36f --staged --worktree -- <in_scope 개별 경로>`(evidence 제외), 새 파일은 삭제. 목록은
`git diff --name-status bee7c36f..HEAD` 기계 산출. 마지막 산출물 커밋에서 ①~⑥ 실측. 원장 형식을 바꿨다면 「새 코드가 쓴 원장을 옛
코드가 읽는가」도 적는다(되돌림 뒤 이어 돌기).

## 검토 레인

verifier(opus) · code-reviewer(sonnet) 병렬. 표적:
- 거동 test 가 **use case 층**에서 재는가(술어 단위 test 의 재포장 금지).
- G1-T 의 세 변이가 이제 RED 인가.
- 기준 시각 결측 처분이 조용한 Fetch 가 아닌가.
- 건너뜀이 회계에 사유와 함께 남는가.
- 원장 호환.

Codex 없음(되돌리기 어려운 경로 아님 — 마이그레이션 0).

## 호스트 빌드

레인 셋이 병렬이다. 모든 Gradle·Testcontainers·docker 명령은 `flock ~/.local/bid-vector-build.lock <명령>` 으로 감싼다. 점검 셋
(pgrep·free·ps)은 별도 호출로 먼저 본다. **00:30~03:00(6G 수집 창)에는 무거운 작업을 시작하지 않는다.** 6G DB(55432)·
`~/.local/bid-vector-run-state`·`bid-vector-v2-collection` 의 jar 는 읽지도 쓰지도 않는다(test 는 임시 디렉터리·Testcontainers 로).
`bidvector/app:local` 태그는 레인끼리 공유하므로 container 재생 뒤 되돌린다.

## 계약 갱신 이력

(없음)

## 하네스 레인 변경 (상시)

- 없음.
