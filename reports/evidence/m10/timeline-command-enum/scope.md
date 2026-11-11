# M10 / 10B — timeline-command-enum (계약)

- base: main `8062dcd784cf81905c50a2c8706d8bbd3ea5263e` · 브랜치 `feature/m10-timeline-command-enum`
- 계약 정본: `milestone-10.md`(PR #148) 10B 행 · 공통 규율: 팀장 2026-11-11 M10 레인 규율
- 거동 변경 0 — **wire 값을 바꾸지 않는다**(스키마 강화만). 응답에 실리는 토큰 집합이 바뀌어야
  하는 사실이 나오면 멈추고 보고한다.

## 대상 OPEN 과 원 정의에서 확인한 사실

| OPEN | 원 정의에서 확인한 것 | 처방 |
| --- | --- | --- |
| `OPEN-M9-COMMAND-OPENAPI-ENUM` | `web-followups/checklist.md` §5 가 신설 근거다(PR #132 cr OQ). 형제 칸 `actorKind` 는 계약에 `enum` 이 있어 web 이 생성 타입에서 유니온을 끌어오는데 명령 칸만 `type: string` 이다. 그래서 그 slice 가 ① 손 유니온 ② Kotlin 원문을 **JS 텍스트로 파싱**하는 교차언어 게이트를 세웠고, 그 checklist 는 `openapi/**` 가 자기 in_scope 밖이라 **계약 slice 의 일**이라고 적었다 | 계약의 그 칸을 Kotlin 어휘와 **같은 집합의 `enum`** 으로. ①② 를 걷고 등식을 계약 test 층으로 옮긴다 |
| `OPEN-M9-TIMELINE-COMMAND-WORDING` | capability map §12.3 (b) 에 **열린 채로 있지만, 실제로는 PR #132 가 닫았다** — `web-followups/checklist.md` §1 이 「닫음 — 전수 표 열다섯이 문면을 짓고 DOM 어휘 게이트의 금지 목록이 그 표의 키를 받는다」. §12.3 (b) 는 2026-11-06 에 쓰였고 PR #132 는 그 뒤에 머지됐다 — **장부가 낡은 것**이고 산출물이 모자란 것이 아니다. 원 신설 근거(`screen-display/checklist.md` 제한 ③, PR #125 D-7)가 말한 「명령 어휘의 표가 없다」는 축은 그 표로 사라졌다 | ① §12.3 에 그 종결을 **근거 PR #132 로** 등재한다(내 PR 이 닫은 것으로 적지 않는다) ② 그 종결이 기대고 있던 **텍스트 파싱 방어를 구조 방어로 교체**한다 — 표의 전수성이 손 유니온이 아니라 계약에서 온다 |

## 어휘의 사슬 — 지금과 뒤

어휘 전수는 **전이 명령 열넷 + 건을 여는 명령 하나 = 열다섯**이다. 여는 명령은 출발 상태가 없어
전이표의 행이 될 수 없고 타임라인에는 생성 전이로 남는다.

| 고리 | 지금 | 뒤 |
| --- | --- | --- |
| DB CHECK ↔ Kotlin | adapters 통합 test(실 DB) | 그대로 |
| Kotlin ↔ 계약 | **없다**(계약이 `type: string`) | **새 계약 test** — 그 칸의 `enum` == 전이표 열 키 전수 + 여는 명령 |
| 계약 ↔ 생성 타입 | W-5 바이트 등식 — 그러나 `string` 은 어휘를 나르지 않는다 | 같은 W-5 가 이제 어휘를 나른다 |
| 생성 타입 ↔ web 유니온 | **없다**(web 이 리터럴 열다섯을 손으로 적는다) | 유니온이 생성 타입에서 **유도**된다(컴파일) |
| web 유니온 ↔ web 표 | `satisfies Record<…>`(컴파일) | 그대로 — 이제 전수성의 출처가 계약이다 |
| 위 두 공백의 임시 방어 | JS 가 Kotlin 원문을 **텍스트로 파싱**해 집합을 맞댄다 | **걷는다**(명령 축만). 금액 네 축은 그 파일에 남는다 — 10D 의 것이다 |

**바꿔치우기 변이와 받는 층** — 더하기만 한 변이는 세지 않는다.

| 변이 | 받는 층 |
| --- | --- |
| Kotlin 에 명령을 더하고 계약을 안 고친다 | 새 계약 test(Kotlin `check`) |
| 계약을 고치고 생성 타입을 재생성하지 않는다 | W-5 |
| web 표에서 키를 뺀다 | `satisfies` → W-3 |
| **web 표와 유니온을 함께 줄인다**(앞 판이 `satisfies` 를 지나던 꼴) | **구성상 불가** — 유니온을 손으로 쓸 자리가 없다 |
| 계약의 `enum` 을 걷어 `type: string` 으로 되돌린다 | 새 계약 test(어휘 읽기가 던진다) + web 쪽 **좁음 가드**(유니온이 `string` 으로 넓어지면 컴파일되지 않는다) → W-3 |

## in_scope

- `openapi/bidvector-operator-api.yaml` — 타임라인 명령 칸 하나(그 칸 밖은 만지지 않는다)
- `app/src/test/kotlin/bidvector/app/http/OpenApiBidCaseContractTest.kt`(새) ·
  `app/src/test/kotlin/bidvector/app/http/OpenApiSpecSupport.kt`(어휘 읽기 공용 헬퍼)
- `config/quality/gate-tests.properties` — 새 test 클래스 등재(만드는 커밋에서 같이)
- `web/src/api/generated/operator-api.ts` — 저장소 절차(`npm run api:types`)의 산출물
- `web/src/display/caseDisplay.ts` · `web/src/display/caseDisplay.test.ts`
- `web/scripts/money-vocab-cross-lang.test.mjs` — **명령 축만** 걷는다
- `docs/discovery/capability-map.md` §12.3 의 **새 하위 절**(행을 고치지 않고 덧붙인다)
- `reports/evidence/m10/timeline-command-enum/**`

## out_scope — 범위 밖으로 판정한 것과 사유

- **타임라인의 상태 두 칸(`type: string`)**. 명령과 같은 꼴의 공백이고 화면이 같은 우회
  (표에 없으면 서버 코드 그대로)를 쓴다. 그러나 **대상 OPEN 둘 다 그 축을 말하지 않고**,
  상태 어휘는 건 스키마 쪽에 이미 `enum` 이 있어 「어디서도 붉어지지 않는다」의 성질이 다르다.
  계약을 만지는 slice 라고 해서 인접 칸을 같이 고치지 않는다.
- **금액 어휘 네 축**(`OPEN-M9-MONEY-VOCAB-CROSS-LANG`) — 10D 의 것이다. 같은 파일을 만지므로
  **공유 경로로 보고**한다(아래).
- **어휘 읽기 헬퍼의 기존 사본 셋 통합** — 세 계약 test 클래스가 각자 사본을 갖고 있다. 새 사본을
  만들지 않기 위해 공용 자리에 하나를 세우되, 기존 셋을 거기로 모으는 것은 별 정리다.
- **여정 e2e 의 명령 문면 단언** — `web-followups/checklist.md` §5 가 넘긴 항목이지 이 두 OPEN 이
  아니다.
- **wire 값 변경 0** — 계약의 enum 은 **오늘 관측 가능한 집합과 정확히 같다**(그 칸의 값은 DB
  열에서 오고 그 열의 CHECK 가 Kotlin 어휘와 같다는 것을 adapters 통합 test 가 들고 있다).

## acceptance (CI 워크플로 job 의 명령 그대로)

- `check` job: `./gradlew --no-daemon check`
- `web` job: `npm ci`(W-1) · `npm run lint`(W-2) · `npm run typecheck`(W-3) ·
  `npm run gate:src-tree`(W-4) · `npm run api:types:check`(W-5) · `npm run test`(W-6) ·
  `npm run gate:tests`(W-7) · `npm run build`(W-8) · `npm run gate:boundary-canary`(W-9) ·
  `npx playwright install chromium`(W-10) · `npm run test:e2e`(W-11)
- W-12·W-13(출하 jar + 여정 e2e)와 `check` job 의 S-20 은 PR CI 에서 돈다 — 로컬에서 돌면 결과를
  적고, 전제(버릴 Postgres)가 없으면 그 사실을 적는다.
- 위에 더해 각 변이의 RED 실측(위 표의 다섯 행).

## rollback

공통 규율대로 — 원천 `8062dcd7`, in_scope 경로 한정
`git restore --source=<원천> --staged --worktree --`, 복원 목록은 `git diff --name-status` 기계 산출.
공유 파일(`docs/discovery/capability-map.md` · `config/quality/gate-tests.properties` ·
`web/scripts/money-vocab-cross-lang.test.mjs`)은 머지 전이면 고정된 main 커밋에서 복원한다.
①~⑥ 은 임시 clone 에서 마지막 산출물 커밋으로 실측하고 `실측 HEAD:` 를 적는다.

## 하네스 레인 변경

없다. `.claude/**` 와 CI 워크플로를 만지지 않는다. 게이트 등록부(`config/quality/gate-tests.properties`)
한 줄은 새 test 클래스의 **등재**이고 게이트 술어 변경이 아니다.

## 레인 사이 공유 경로

- `web/scripts/money-vocab-cross-lang.test.mjs` — 10D 가 금액 어휘 축으로 같은 파일을 만진다.
  이 slice 는 **명령 축의 블록과 그 축만 쓰는 읽기 하나**를 걷고 금액 네 축은 손대지 않는다.
- `docs/discovery/capability-map.md` §12.3 · `config/quality/gate-tests.properties` — 레인 넷이
  각자 덧붙인다. 행을 고치지 않고 자기 자리만 더한다.
