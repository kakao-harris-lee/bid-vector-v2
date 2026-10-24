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

**D-G1C03-1 (2026-10-21, 팀장 — Phase 1 · 절반 수령 · 운영자 결정).** 절반 판정 대상은 산출물 `4a0481f6` · evidence `1d17ff47` 이다.
- **막힌 전제**: age 기준 시각(개찰 일시)이 6G 표본틀에 닿지 않는다. `opengDt` 행의 `presentIn` 은 공고 목록 하나뿐이라 개찰결과 목록에서는
  마스킹 매퍼가 그 키를 지운다. `rlOpengDt` 는 6G 가 걷지 않는 낙찰 목록과, 게이트가 막으려는 상세 호출 자신에만 있다. 레지스트리는 같은
  원문 키의 둘째 행을 거부한다.
- **운영자 결정 (나)+(라)**: 이 slice 는 **age 절반을 6G 경로 밖으로 선언**하고 재조회·보유 게이트까지만 세운다. COL-03 은 ⓒ 로 남는다
  (age 절반의 빈 자리를 행에 적는다).
  - age 절반은 **별 slice 「개찰 축 끝점 범위 필드 행」**이 닫는다. 내용은 레지스트리 색인을 `(원문 키, 끝점)` 으로 넓히는 것이다. 같은 키의
    두 행이 끝점을 공유하면 거부한다. 개찰결과 목록 전용 행(「입찰서를 개찰하는 일시」, 항목구분 필수)을 둔다. 금액 해석과 페이로드 인코더의
    파급을 다룬다.
  - `policy-values.md` §1.7.4 의 두 선언 분리를 지킨다. (가) 병합은 채택하지 않는다.
  - **기준 시각 자리는 상수가 아니라 이름 있는 선언으로 둔다**(예: 「6G 표본틀은 기준 시각을 싣지 않는다」를 뜻하는 값). 그 선언이 age 판정을
    건너뛰게 하는 경로를 test 로 잠근다. 그래야 별 slice 가 그 자리를 실값으로 바꿀 때 선언 하나만 바뀐다. 이 선언을 다른 출처와 섞이는
    조용한 null 로 두지 않는다.
- **운영자 결정 — 재조회 창 48h 유지**: 배선 뒤 일시 실패 축의 재시도가 48h 씩 밀린다. 축 재호출 3회 소진이 약 3일에서 6일 이상으로 늘어난다.
  값은 그대로 두고(P-5 「측정 전 잠정」), 이 거동 변경을 evidence 와 runbook 에 적는다. 6G 실수집은 옛 jar 라 지금 영향이 없다.
- **in_scope 추가**: `app/src/main/kotlin/bidvector/app/collection/OpeningCollectionLines.kt` — 마침 줄 끝에 사유별 건너뜀 수를 더한다.
  `cron-6g.sh` 는 `frame=` 이후를 통째로 자르므로 깨지지 않는다(레인 확인). 관련 test 와 runbook 문면도 넣는다.
- **수용한 설계**:
  - 결측 처분은 건너뜀 + 별도 계수다.
  - 계수는 `backoff_skipped` 한 칸이고 사유는 실행 보고가 나른다. 보유는 세지 않는다.
  - 원장 형식은 불변이고, 옛 형식 판독 test 를 둔다.
  - 술어는 (공고, 축)마다 부르는 단일 판정 자리다.
  - 관문 거부와 이번 실행이 닫은 끊긴 라운드는 재조회 창을 열지 않는다(D-6G2d-16 과 같은 근거).
- 남은 것: 기준 시각 선언 · 마침 줄 · 변이 ④⑥(해당되는 것) · 추적표/capability(COL-03 ⓒ 유지 + 재조회·보유 성립 근거 + age 절반 홀더) ·
  `check` · container 재생 · rollback ①~⑥.

**D-G1C03-2 (2026-10-22, 팀장 — check 실패 수령 · 운영자 결정 정정).** 앞 결정의 「재조회 창 48h 유지」를 **0 으로 바꾼다**.
- **발견**: `OpeningBudgetE2ETest` 「한 쪽만 받고 끊긴 축은 다음 기동이 다시 걷는다」(D-6G-58 의 보증)가 붉었다. 재조회 창이 서면 쪽 중간에 끊긴
  축이 다음 날 재개되지 않는다. 끊길 확률은 쪽 수, 곧 **참가자 수**에 비례한다(6G 실측: 1쪽 365 공고 · 2쪽 이상 111 공고). 그래서 결손이
  참가자 많은 공고에 몰린다 — 저장소가 반복해서 막아 온 「참가자 수와 결측의 상관」이다.
- V2 에서 legacy 재조회 게이트의 표적(조회했으나 빈 응답)은 이미 「빈 응답 = 정착」이라 다시 부르지 않는다. 이 경로에서 창이 무는 자리는
  「미완이라 이어 걸어야 하는 축」 하나뿐이고, 그것은 게이트의 원래 뜻이 아니라 이어 돌기와 싸우는 재시도 백오프다.
- **운영자 결정(2026-10-22): `recheckGateHours` = 0.** 배선(실제 관측값 · 네 결과 전수 분기 · 상수 복귀 변이 RED)은 그대로 선다.
  - out_scope 였던 `CollectionPolicy` 의 게이트 **값** 가운데 이 하나만 in_scope 로 연다. 출처는 「운영자 결정 2026-10-22, D-G1C03-2」를 정책
    출처 칸·주석에 적는다. `CollectionPolicyTest` 고정값을 함께 갱신하고, 정책 version 관례가 있으면 따른다. age 24h · 축 재호출 3 은 그대로다.
  - 47/49 시간 측정점 test 는 출하 값 0 의 거동(재조회로 건너뛰는 일이 없다)을 재는 형태로 바꾼다. 「창이 서면 건너뛴다」는 test 정책 값으로
    술어 · 배선 층에서 계속 잰다. 변이 ②(창 10만 h)는 이어 돌기 E2E 를 붉혀야 한다.
  - `OpeningBudgetE2ETest` 의 명제는 **바꾸지 않는다**.
  - 창을 되살리려면 「끊긴 걷기는 창을 열지 않는다」를 쓰기 시점 어휘로 가르는 설계(원장 형식 변경)가 먼저다 — 별 slice 후보로 evidence 에
    적는다.
- 사소한 lint(빈 줄) 수정은 수용한다.

**D-G1C03-3 (2026-10-23, 팀장 — 검토 r1 수령 · 승인 전 일괄 배치).** 판정 대상 산출물 `26beba76` · evidence `7ba7bbf3`. verifier r1
ready-for-review(blocker·high 0, medium 3 · low 5) · code-reviewer r1 APPROVE(medium 3 장부 · low 8). 배치 하나로 닫는다 — 코드·test: 기준 시각
선언의 배선을 구조로 잠금(F-1) · 끊긴 라운드 제외 범위 test 와 잠복 시나리오 처분(F-2) · AXIS 필터·ageGate 계수·마침 줄 사유·멈춘 실행 기록
test(L-1·L-2·F-4·F-5) · 정리(L-4·L-5·L-6·L-7·F-6). 장부: rollback 의 `git rm`(F-3) · M-1~M-3 · L-3 · L-8·F-7 · evidence 크기(F-8). 배치 뒤
새 마지막 산출물 커밋에서 check·변이·rollback 재실측, F-1·F-2·F-3 은 verifier 표적 재검증.

**D-G1C03-4 (2026-10-23, 팀장).** 배치가 app jar 를 바꾸므로 `container` job 을 CI 명령 그대로 다시 잰다(앞 재생 갈음 불가 — 트리 동일성).
13:47 KST d653e8bf(산출물 트리 = 2c3d0e10) 실측 success · 태그 넷 복원. `commands.md` 의 앞 재생 「run 24」는 run 23 + uses 4 의 오기.

**D-G1C03-5 (2026-10-23, 팀장 — r1 표적 재검증 · CR r2 수령, 승인 전 일괄 배치 r2).** verifier ready(F-2·F-3 닫힘, F-1 부분) · CR APPROVE(M4·M5).
- **F-1 잔여(verifier V1·V2 = CR M4)를 구조로 닫는다**: V1 = use case 가 `decideDetailFetch(…, openingObservedAt = null, …)` 직접 호출,
  V2 = `Candidate(…, NotCarriedByFrame)` 재구성. 두 우회가 컴파일 단계나 아키텍처 게이트에서 실패하게 한다. 수단은 레인이 고른다(서명 타입화 ·
  참조 쌍을 앵커 파일로 좁힘 · 생성 자리를 표본틀로 좁힘). 문면을 「하나로 줄였다」로 내리는 대안은 기각. 게이트 술어 변경이 끼면 표적 재검증.
- **M5**: 거부 → 등재(§5 기존 부채 한 줄 — 이 slice 의 +12 줄이 capability-map 역참조 약 19곳을 밀었다, M0 정정은 범위 밖).
- **L9**: F-2 회귀 test 의 구판 RED 를 표적 실행 하나로 측정. **low**: `TruncationCause` 코드와 `INTERRUPTED_ROUND` 의 비충돌을 test 로.
- `commands.md` 「run 24」 정정. 그 뒤 마지막 산출물 커밋에서 check · 변이(V1·V2 포함) · rollback ①~⑥ · container job 재실측 → 동결.

**D-G1C03-6 (2026-10-23, 팀장 — r2 표적 재검증 ready · CR r3 APPROVE 수령, 마지막 일괄 배치 r3).** verifier R2-1 = CR M6: 생성 축 규칙의
자기 면제(`outermostClassName() == type`)로 data class `copy` · 멤버 팩토리 · companion 팩토리가 양쪽에서 안 보인다 — **닫는다**(면제를 좁히고
Candidate 를 돌려주는 Candidate 멤버·copy·companion 팩토리를 규칙으로 막거나 같은 효과의 구조, 레인 선택). 세 우회 표본 RED 를 게이트 test 로.
CR L13(비충돌 목록 크기 단언) 수정 · CR M5 정정 문면(「19 는 부분집합」) · CR L12 와 verifier R2-2(파일 클래스 단위 허용 — 경계 파일에 함수를
더하면 열림)는 §5 등재. 게이트 술어 변경이므로 R2-1 만 표적 재검증.

## 하네스 레인 변경 (상시)

- 없음.
