# M11 / 11B — timeline-state-enum (계약)

- base: main `94ee445b50cafa669af435e651d811cc551c67e8` · 브랜치 `feature/m11-timeline-state-enum`
- 계약 정본: `milestone-11.md`(PR #153) 11B 행 · 공통 규율: 팀장 2026-11-13 M11 레인 지시
- 거동 변경 0 — **wire 값을 바꾸지 않는다**(스키마 강화만). 응답에 실리는 토큰 집합이 바뀌어야
  하는 사실이 나오면 멈추고 보고한다.
- 선례는 10B(`reports/evidence/m10/timeline-command-enum/`)다. 같은 꼴을 재사용하고 복제하지
  않는다 — 어휘 읽기 헬퍼·계약 test 클래스·web 표의 등식 자리가 전부 그 slice 가 세운 것이다.

## 받는 항목 — 원 정의에서 확인한 사실과 처방

| # | 확인한 사실 | 처방 |
| --- | --- | --- |
| 2 `OPEN-M10-TIMELINE-STATE-OPENAPI-ENUM` | 타임라인의 출발·도착 칸이 계약에서 `type: string` 이다. 같은 응답 계열의 건 스키마 상태 칸에는 이미 `enum` 이 있어 **선언이 갈려 있고**, 화면은 표 밖 값을 **서버가 준 코드 그대로** 내는 우회를 쓴다(그 모듈의 한 관례와 어긋난다 — 다른 축은 전부 「보이는 미지 상태」로 낸다) | 두 칸을 Kotlin 상태 어휘와 같은 집합의 `enum` 으로. 출발 칸은 **생성 전이의 `null` 을 어휘에 함께 싣는다**(저장소 선례: 편집 세션의 칸 이름). web 은 생성 타입에서 유니온을 끌어오고, 표 밖 조회를 그 모듈의 한 자리로 되돌린다 |
| 8 KDoc 하드코딩 | 어휘 읽기 공용 헬퍼의 중복 키 금지 KDoc 이 「세 계약 test」라고 수를 박았다. 같은 파일의 머리말·형제 KDoc 은 **이미** 「수를 적지 않는다」를 사유와 함께 적고 있다(앞 라운드가 같은 지적을 받은 자리) | 그 한 줄을 수 없는 문면으로. 사유는 형제 KDoc 을 가리킨다 |

## 전방 호환 판단 세 조건 — 상태 칸에서 다시 쟀다 (착수 요구)

§12.3 (h) 가 받는 자리에 물려준 ①②③ 이다. **셋 다 선다.**

- **① 저장소 안 소비자 하나.** 그 응답을 읽는 자리는 web 의 생성 클라이언트 호출 하나이고,
  여정 e2e 가 같은 JSON 을 **자기 손 표**로 대조한다(그 표는 「화면의 표를 import 하지 않는다」가
  선언된 독립 명세다 — 유도가 아니라 대조의 반대쪽이다). 저장소 밖 소비자 0, 둘째 생성 클라이언트 0.
- **② 같은 PR 재생성.** 생성 타입이 커밋되고 W-5 가 문서와 **바이트 등식**을 본다 — 계약과 번들이
  한 커밋에서 함께 움직인다.
- **③ Kotlin 어휘 ↔ enum 등식 test.** 이 slice 가 세운다. 상태 칸은 명령 칸보다 **더 닫혀 있다** —
  명령은 포트에서 `String` 이라 어휘 밖 값이 타입을 지나갔지만, 상태 두 칸의 wire 값은 포트의
  `BidStateTag`(출발은 `?`)에서 `.name` 으로만 나온다. 그래서 「서버가 enum 밖 값을 내는」 창이
  타입으로 이미 닫혀 있고, 계약 `enum` 은 그 사실을 **문서가 나르게** 하는 것이다.

## 결정

**D-11B-1 — 판정 레인은 Claude 측 verifier + code-reviewer(sonnet)다.** `openapi/**` 는 운영자
지시(2026-09-11)가 Codex 심판에 올리는 열거(되돌리기 어려운 경로 · `contracts/**` proto·승인 태그
정책)에 없다. 10B 의 D-10B-1 과 같은 근거이고 같은 파일이다.

**D-11B-2 — 건 스키마의 상태 칸을 같은 등식에 넣는다.** OPEN 의 문면이 「한 응답 안에서 두 칸의
선언이 갈려 있다」이므로, 갈림을 닫는 것은 **두 선언이 같은 집합임을 재는 것**이다. 그 칸은 오늘
Kotlin 등식 밖이다(어휘 사본 가운데 등식을 가진 것은 대사 쪽 기록 상태 칸 하나뿐이다) — 타임라인
칸만 재면 건 칸이 조용히 어긋날 수 있고, 그러면 web 표의 전수가 그 어긋난 집합을 따라간다.

**D-11B-3 — 표 밖 상태 조회를 그 모듈의 한 자리로 되돌린다.** 타임라인 화면만 표를 `Record<string, …>`
으로 단언해 **맨 색인**으로 읽고 폴백을 자기가 적는다. 그 자리는 모듈이 「폴백을 두지 않는다 ·
조회는 한 자리다」로 닫아 둔 축이고, 맨 색인은 프로토타입 키에서 표 밖 값을 답으로 낸다. 조회를
공용 자리로 옮기면 **표시 문면이 바뀐다**(표 밖 코드 원문 → 「알 수 없는 상태(코드)」) — 닿는
경로는 배포 비틀림 하나뿐이고 wire 는 그대로다. 거동 변경이므로 착수 보고에서 **먼저 물었다**.

## 계약 갱신 2026-11-13 — 착수 보고에 대한 팀장 답

착수 보고(위 세 조건 재측정 · 결정 둘 · 새 OPEN 후보 하나)에 대한 답으로 **셋 다 승인**됐다.

- **D-11B-2 승인** — 건 스키마의 상태 칸을 같은 등식에 넣는다. in_scope 의 계약 test 가 그
  단언을 함께 든다(새 파일 없음).
- **D-11B-3 승인** — 타임라인 상태 조회를 공용 자리로 되돌린다. 표 밖 값의 문면이 바뀌는 것은
  배포 비틀림 때만 닿는 **표시** 변경이고 API 응답 값 변경이 아니라 승인 경계 안이다. 조건:
  그 변경을 test 로 잠그고 evidence·PR 본문에 **「거동 변경: 표 밖 상태의 표시 문면」** 으로
  명시한다.
- **목록 질의 상태 탭 param — 범위 밖 유지**(아래 out_scope).

## in_scope

- `openapi/bidvector-operator-api.yaml` — 타임라인 상태 두 칸
- `app/src/test/kotlin/bidvector/app/http/OpenApiBidCaseContractTest.kt` — 상태 축 등식과 음성 대조
- `app/src/test/kotlin/bidvector/app/http/OpenApiSpecSupport.kt` — `null` 을 함께 싣는 어휘 읽기
  하나(새 사본을 만들지 않는다) · 항목 8 의 KDoc 한 줄
- `web/src/api/generated/operator-api.ts` — 저장소 절차(`npm run api:types`)의 산출물
- `web/src/display/caseDisplay.ts` · `web/src/display/caseDisplay.test.ts`
- `web/src/screens/WorksheetTimeline.tsx` — 표 밖 조회 자리(D-11B-3) ·
  `web/src/screens/WorksheetScreen.test.tsx` — 그 거동 변경의 렌더 층 잠금(기존 파일 · 게이트
  등록부에 이미 있다)
- `docs/discovery/capability-map.md` §12.3 의 새 하위 절 (l)(행을 고치지 않고 덧붙인다)
- `reports/evidence/m11/timeline-state-enum/**`

## out_scope — 범위 밖과 사유

- **목록 질의의 상태 탭 param** — 같은 어휘의 넷째 사본이고 Kotlin 등식 밖이지만 **요청** 쪽
  어휘다(응답 계열이 아니다). 팀장 판정으로 **범위 밖 유지**이고 §12.3 (l) 에 신설 OPEN
  `OPEN-M11-LIST-STATE-PARAM-ENUM` 으로 등재한다 — 받는 자리는 「다음에 그 param 을 만지는
  계약 slice」다.
- **어휘 사본을 계약 안에서 하나로 모으는 것**(`$ref` 공용 스키마) — 금액 어휘 「하나의 출처」와
  같은 부류이고 `milestone-11.md` 가 그것을 범위 밖으로 유지했다. 같은 판단을 상태 축에 적용한다.
- **어휘 읽기 헬퍼의 기존 사본들 통합** — 10B 알려진 제한 ③ 그대로. 또 하나를 만들지 않는 것이
  이 slice 의 몫이고, 모으는 것은 별 정리다.
- **여정 e2e 의 손 상태 표** — 대조의 반대쪽이라 유도로 바꾸면 대조가 공허해진다(그 파일이 그
  사유를 선언한다).

## acceptance (CI 워크플로 job 의 명령 그대로)

- `check` job: `./gradlew --no-daemon check`
- `web` job: `npm ci`(W-1) · `npm run lint`(W-2) · `npm run typecheck`(W-3) ·
  `npm run gate:src-tree`(W-4) · `npm run api:types:check`(W-5) · `npm run test`(W-6) ·
  `npm run gate:tests`(W-7) · `npm run build`(W-8) · `npm run gate:boundary-canary`(W-9) ·
  `npx playwright install chromium`(W-10) · `npm run test:e2e`(W-11)
- W-12·W-13(출하 jar + 여정 e2e)와 `check` job 의 S-20 은 **PR CI** 에서 돈다 — 버릴 Postgres
  전제가 이 레인에 없다(10B 와 같은 자리, 팀장 승인 2026-11-11).
- 위에 더해 각 변이의 RED 실측.

## rollback

in_scope 경로 한정 `git restore --source=<원천> --staged --worktree --`, 목록은
`git diff --name-status` 기계 산출. 원천·전제·①~⑥ 실측은 `rollback.md` 가 정본이다.

## 하네스 레인 변경

**내 편집은 0** 이다 — `git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 가 빈 출력이다.
CI 워크플로도 만지지 않는다. 새 test 클래스가 없어 게이트 등록부도 만지지 않는다(상태 축의
등식은 **이미 등재된** 계약 test 클래스와 web 표 test 파일 안에 선다).

## 레인 사이 공유 경로

- `docs/discovery/capability-map.md` §12.3 — M11 레인 다섯이 각자 (k)~(o) 를 덧붙인다. 행을
  고치지 않고 자기 자리만 더한다. 머지 충돌이 나면 절 순서 (k)…(o) 로 합친다.
- `web/src/display/caseDisplay.ts` 계열은 11D·11E 가 만지는 web 트리와 같은 모듈 묶음에 있다 —
  파일은 겹치지 않는다(확인 뒤 갱신한다).
