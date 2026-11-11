# M10 / 10B — timeline-command-enum (계약)

- base: main `8062dcd784cf81905c50a2c8706d8bbd3ea5263e` · 브랜치 `feature/m10-timeline-command-enum`
- 계약 정본: `milestone-10.md`(PR #148) 10B 행 · 공통 규율: 팀장 2026-11-11 M10 레인 규율
- 거동 변경 0 — **wire 값을 바꾸지 않는다**(스키마 강화만). 응답에 실리는 토큰 집합이 바뀌어야
  하는 사실이 나오면 멈추고 보고한다.
- 사슬·변이 분석의 정본은 `checklist.md` §2 다(크기 게이트 때문에 이 문서에서 걷었고, 계약 요소는
  하나도 걷지 않았다).

## 대상 OPEN — 원 정의에서 확인한 사실과 처방

| OPEN | 확인한 사실 | 처방 |
| --- | --- | --- |
| `OPEN-M9-COMMAND-OPENAPI-ENUM` | 신설 근거는 `reports/evidence/m9/web-followups/checklist.md` §5(PR #132 cr OQ). 형제 칸 `actorKind` 는 계약에 `enum` 이 있어 web 이 생성 타입에서 유니온을 끌어오는데 명령 칸만 `type: string` 이다. 그래서 그 slice 가 ① 손 유니온 ② Kotlin 원문을 **JS 에서 텍스트로 파싱**하는 교차언어 게이트를 세웠고, `openapi/**` 가 자기 in_scope 밖이라 **계약 slice 의 일**이라고 적었다 | 계약의 그 칸을 Kotlin 어휘와 같은 집합의 `enum` 으로. ①② 를 걷고 등식을 계약 test 층으로 옮긴다 |
| `OPEN-M9-TIMELINE-COMMAND-WORDING` | capability map §12.3 (b) 에 열린 채 있지만 **PR #132 가 이미 닫았다**(그 slice 의 checklist §1). (b) 는 2026-11-06 에 쓰였고 #132 는 그 뒤에 머지됐다 — **장부가 낡은 것**이고 산출물이 모자란 것이 아니다 | ① §12.3 에 그 종결을 **근거 PR #132 로** 등재한다(내 PR 이 닫은 것으로 적지 않는다) ② 그 종결이 기대던 텍스트 파싱 방어를 **구조 방어로 교체**한다 |

## 결정 (팀장 2026-11-11 — PR #148 cr r2 이월)

**D-10B-1 — 이 slice 가 만지는 계약 문서는 CLAUDE.md 의 「계약 파일」이 아니다. Codex 대상이
아니다.** 그 운영자 지시(2026-09-11)가 Codex 심판에 올리는 것은 되돌리기 어려운 경로(인증·인가·
암호화·DB 마이그레이션·데이터 파기)와 **`contracts/**` 의 proto·승인 태그 정책**이다. `openapi/**`
는 그 열거에 없다 — `buf`(lint·breaking·버전 대조)가 보는 `contracts/` proto 와 다른 파일이고
승인 태그 정책을 갖지 않는다. 그래서 이 slice 의 판정 레인은 **Claude 측 verifier +
code-reviewer(sonnet)** 다.

**D-10B-2 — 명령 칸을 닫힌 `enum` 으로 올리는 데 전방 호환 창이 없다.** 셋이 **함께** 선다:
① 그 칸의 클라이언트가 **저장소 안 web 하나**다(외부 소비자 0) ② 생성 타입을 **같은 PR 에서
재생성**하므로 계약과 번들이 한 커밋에서 함께 움직인다 ③ **Kotlin 어휘 ↔ enum 등식 test** 가
명령 추가 시 계약 갱신을 강제한다(Kotlin 에만 더하면 `check` 가 붉다 — 변이 1 실측). 그래서
「서버가 enum 밖 값을 내는」 창이 없고, 닫는 것이 응답을 거절하는 자리를 만들지 않는다.
런타임 조회가 자유 문자열을 계속 받는 것은 이 창 때문이 **아니라 배포 비틀림**(계약·번들이 함께
움직였어도 브라우저가 옛 번들을 쥔 경우) 때문이고, 그 처리는 유지한다(`checklist.md` 제한 ②).
같은 판단을 `OPEN-M10-TIMELINE-STATE-OPENAPI-ENUM` 의 **받는 자리**가 물려받도록 capability map
§12.3 (h) 에 적었다 — 셋 중 하나라도 깨지면 그 slice 에서 다시 서야 한다.

## in_scope

- `openapi/bidvector-operator-api.yaml` — 타임라인 명령 칸 하나
- `app/src/test/kotlin/bidvector/app/http/OpenApiBidCaseContractTest.kt`(새) ·
  `app/src/test/kotlin/bidvector/app/http/OpenApiSpecSupport.kt`(어휘 읽기 공용 헬퍼)
- `config/quality/gate-tests.properties` — 새 test 클래스 등재(만드는 커밋에서 같이)
- `web/src/api/generated/operator-api.ts` — 저장소 절차(`npm run api:types`)의 산출물
- `web/src/display/caseDisplay.ts` · `web/src/display/caseDisplay.test.ts`
- `web/scripts/money-vocab-cross-lang.test.mjs` — **명령 축만** 걷는다
- `docs/discovery/capability-map.md` §12.3 의 새 하위 절(행을 고치지 않고 덧붙인다)
- `reports/evidence/m10/timeline-command-enum/**`

## out_scope — 범위 밖으로 판정한 것과 사유

- **타임라인의 상태 두 칸(`type: string`)** — 같은 꼴의 공백이지만 **대상 OPEN 둘 다 그 축을 말하지
  않고**, 상태 어휘는 건 스키마 쪽에 이미 `enum` 이 있어 성질이 다르다. 계약을 만진다고 인접 칸을
  같이 고치지 않는다. 팀장 결정 2026-11-11 로 **등재만** 한다(`OPEN-M10-TIMELINE-STATE-OPENAPI-ENUM`).
- **금액 어휘 네 축**(`OPEN-M9-MONEY-VOCAB-CROSS-LANG`) — 10D 의 것(아래 공유 경로).
- **어휘 읽기 헬퍼의 기존 사본 셋 통합** — 넷째 사본을 안 만들려 공용 자리에 하나를 세우되, 기존
  셋을 모으는 것은 별 정리다.
- **여정 e2e 의 명령 문면 단언** — `web-followups/checklist.md` §5 가 넘긴 항목이고 이 둘이 아니다.

## acceptance (CI 워크플로 job 의 명령 그대로)

- `check` job: `./gradlew --no-daemon check`
- `web` job: `npm ci`(W-1) · `npm run lint`(W-2) · `npm run typecheck`(W-3) ·
  `npm run gate:src-tree`(W-4) · `npm run api:types:check`(W-5) · `npm run test`(W-6) ·
  `npm run gate:tests`(W-7) · `npm run build`(W-8) · `npm run gate:boundary-canary`(W-9) ·
  `npx playwright install chromium`(W-10) · `npm run test:e2e`(W-11)
- W-12·W-13(출하 jar + 여정 e2e)와 `check` job 의 S-20 은 **PR CI** 에서 돈다(팀장 승인
  2026-11-11 — 버릴 Postgres 전제가 이 레인에 없다).
- 위에 더해 각 변이의 RED 실측.

## rollback

in_scope 경로 한정 `git restore --source=<원천> --staged --worktree --`, 목록은
`git diff --name-status` 기계 산출. 원천·전제·①~⑥ 실측은 `rollback.md` 가 정본이다.

## 하네스 레인 변경

없다 — `git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 가 빈 출력이다. CI 워크플로도
만지지 않는다. 게이트 등록부 한 줄은 새 test 클래스의 **등재**이고 게이트 술어 변경이 아니다.

## 레인 사이 공유 경로

- `web/scripts/money-vocab-cross-lang.test.mjs` — 10D 가 주석 걷기 축으로 같은 파일을 만진다.
  이 slice 는 명령 축의 블록과 그 축만 쓰는 읽기 하나만 걷었다.
- `docs/discovery/capability-map.md` §12.3 · `config/quality/gate-tests.properties` — 레인 넷이
  각자 덧붙인다. 행을 고치지 않고 자기 자리만 더했다.
