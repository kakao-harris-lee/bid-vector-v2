# M10 / gates-web — slice 계약

```yaml
milestone: M10
slice: gates-web
base_sha: 8062dcd784cf81905c50a2c8706d8bbd3ea5263e
head_sha: 리뷰 시점의 PR head      # 숫자를 적지 않는다 — 이 파일을 담은 커밋이 그 SHA 라 자기
                                  # 안에 적을 수 없고, 라운드마다 바뀐다(m1 계열과 같은 관례).
in_scope:
  - web/scripts/kotlin-source.mjs                # 신설 — Kotlin 원문 읽기의 공유 한 자리
  - web/scripts/kotlin-source.test.mjs           # 신설 — 그 읽기 술어의 게이트 test
  - web/scripts/money-vocab-cross-lang.test.mjs  # 10B 와 공유 — §공유 파일
  - web/e2e/harness/opening-axis.mjs
  - web/e2e/harness/opening-axis.test.mjs        # r2 추가 — `.kt` 경로 리터럴 넷을 키로 바꾼다
  - web/e2e/harness/journey-up.mjs               # r2 추가 — 게이트가 찾아낸 **셋째** 소비자
  - web/scripts/kotlin-source-ownership.test.mjs # r2 신설 — 읽기 소유권의 AST 게이트
  - web/config/gate-tests.txt
  - docs/discovery/capability-map.md             # §12.3 하위 절 (j) 만
  - reports/evidence/m10/gates-web/
out_of_scope:
  - openapi/bidvector-operator-api.yaml    # 10B 의 편집 대상 — §③
  - web/eslint.config.js                   # ① 은 이미 닫혀 있다 · r2 도 건드리지 않는다(§r2 계약 갱신)
  - web/scripts/screen-card-coverage.mjs   # ② 의 named 축은 이미 닫혀 있다
  - web/src/**                             # 출하 코드를 바꾸지 않는다
  - adapters/**/*AdapterDependencyTest.kt  # Kotlin 쪽 원문 정규식 게이트 — 10C ②
acceptance_commands:   # ci.yml web job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew check   # evidence 가 leak-pattern 게이트의 스캔 뿌리에 들어 경로가 닿는다
rollback: rollback.md
```

## 대상 넷과 처분

| OPEN | 원 정의가 named 한 것 | 처분 |
| --- | --- | --- |
| `OPEN-M9-CLOCK-LINT-SCOPE` | `Intl` 서식·`Temporal.Now`·`performance.timeOrigin`·제외 디렉터리 경유 | **닫힘 등재** — PR #132 가 닫았고 코드 변경 없음 |
| `OPEN-M9-SCREEN-COVERAGE-SYMBOL-GRAPH` | 재수출·별칭으로 부르면 의무 집합에 안 든다 | **닫힘 등재** + 잔여를 `OPEN-M10-SCREEN-COVERAGE-INDIRECT-RENDER` 로 신설 |
| `OPEN-M9-MONEY-VOCAB-CROSS-LANG` | Kotlin 에 값이 늘면 web 이 어디서도 안 붉어진다 | **닫힘 등재** — 「하나의 출처」는 범위 밖(§③) |
| `OPEN-M9-KOTLIN-COMMENT-STRIP-DUP` | 두 벌이고 한 벌이 문자열 안 `//` 를 깬다 | **이 slice 가 구현해 닫는다** |

재측정 수치는 `commands.md` 「착수 실측」이 정본이다 — 셋 다 바꿔치우기 변이로 RED 를 내고,
**간접 렌더만 조용한 초록**이라 그 잔여가 실재한다.

### ③ — 「하나의 출처」를 범위 밖으로 둔다 (팀장 승인 2026-11-11)

openapi `enum` 승격을 하지 않는 사유 셋. ⓐ 그 칸이 응답 스키마에 **스물넷**이고 10B 가 같은 파일을
만진다 ⓑ `enum` 은 네 벌 가운데 **web 유니온 하나만** 지운다 — e2e 축어 라벨은 관례상 산출물에서
끌어오지 않는다(표에서 뽑으면 그 표가 틀려도 대조가 초록이다) ⓒ OPEN 이 named 한 해악은 등식이
이미 닫았고 남은 것은 복제 **수**이지 거동 공백이 아니다.

### ② — 잔여를 새 ID 로 (팀장 결정 2026-11-11)

named 한 축(재수출·별칭)은 닫혔다. 잔여 여섯(간접 렌더·감싸 내보내기·개명 재수출·클래스·함수
참조·새 어휘 함수)은 **다른 축**이다. 같은 ID 를 열린 채 두면 장부가 「심볼 그래프를 안 했다」고
말해 거짓이고, 그냥 닫으면 잔여가 사라진다. 대표 축은 **간접 렌더**(조용한 초록을 재현한 유일한
꼴)이고 받는 자리는 **다음 화면 slice** 다.

### 공유 파일 — `money-vocab-cross-lang.test.mjs` (10B 와 겹친다)

10B 가 같은 파일에서 **명령 축을 걷는다**(타임라인 `command` 가 openapi `enum` 이 되면 그 등식과
Kotlin 원문 읽기가 함께 사라진다). 머지 순서는 **10B → 10D**. 이 slice 의 편집은 **import 한 줄 +
지역 정의 삭제** 둘이고 **호출부를 한 줄도 바꾸지 않는다**(공유 모듈이 같은 이름을 내보낸다) —
10B 가 걷는 영역과 겹치지 않는다. 10B 선행 머지 시 main 을 병합해 해소하고, 접힘 여부를 **충돌
수가 아니라 계수**로 센다.

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **아홉 줄이 선다. 전부 main
것이고 이 slice 의 산출물은 0 이다.** 재산출 때마다 귀속을 기계로 확인한다.

착수 때는 **없음**이었다. main `67ff54b1`(10B #149) 을 병합한 뒤 그 range 에 main 의 하네스 커밋
아홉(PR #147 계열)이 들어왔다 — 「이 절에 줄이 서면 그것은 slice 산출물이 아니며 in_scope 밖이고,
운영자 승인 하에 같은 range 에 있다」는 이 절의 상시 문장이 그 자리다. **귀속을 단언으로 쟀다**:
아홉 전부 `67ff54b1` 의 조상이고(= 병합이 들여온 것), 내 산출물 커밋 가운데 하네스 경로를 만진
것은 **0** 이다. 그래서 이 slice 의 하네스 레인 변경은 여전히 **없음**이다.

## 갱신 이력

- 2026-11-11 초판(착수 보고와 함께).
- 2026-11-11 팀장 답 고정 — ①②③ 의 「#132 머지 `e12c0485` 에서 닫힘(장부 공백) + 이 slice 의
  재측정」 등재 꼴 승인 · ③ 범위 밖 승인 · ② 잔여 ID 신설 · §12.3 하위 절은 **(j)** · 10B 공유
  파일 편집 계획 등재 · 설계 승인.

### r2 계약 갱신 — verifier r1 M-2 (구조로 닫기)

M-2 가 「복제 금지 술어가 텍스트다」를 **변이 둘의 초록으로** 실증했다. 구조로 닫으려면 공유
모듈이 **읽기 자체**를 소유해야 하고, 그래서 **경로 리터럴을 쥐고 있던 소비자 파일**이 범위에
들어온다. 추가 둘:

- `web/e2e/harness/opening-axis.test.mjs` — `.kt` 경로 리터럴 **넷**이 여기 있다(r1 까지 이 파일은
  in_scope 밖이었고, 그래서 M-2 의 우회가 **계약상 보이지 않는 자리**에 있었다).
- `web/scripts/kotlin-source-ownership.test.mjs` — 신설. AST 소유권 술어와 그 **양성 대조**.
  만드는 커밋에서 `web/config/gate-tests.txt` 에 **같이** 등재한다.

**`web/eslint.config.js` 는 out_of_scope 로 남긴다.** 팀장 처방은 ESLint AST 규칙이었고 그쪽이
lint 시점 피드백을 주지만, ① 그 파일은 계약상 범위 밖이고 ② flat config 는 **블록을 더하면 앞
블록의 같은 규칙 옵션이 사라진다**(이 호스트의 등재된 함정) — 범위 밖 파일에서 그 위험을 지는
것보다, **같은 AST 층**을 게이트 test 로 세우는 쪽을 골랐다. 선례가 있다 — 이 레인의 소비자
(`money-vocab-cross-lang.test.mjs`)가 이미 `typescript` 를 직접 들여 AST 를 읽는다. 강제력은
둘 다 CI 전건이다(lint = W-2 · 이 test = W-6·W-7). **팀장이 ESLint 쪽을 원하면 그 파일을 in_scope
로 올리는 계약 갱신이 먼저다.**

**추가 하나는 게이트가 찾아냈다 — 소비자는 둘이 아니라 셋이었다.** 새 AST 술어를 처음 돌리자
`web/e2e/harness/journey-up.mjs` 가 걸렸다. 이 파일이 Kotlin 정본 하나(`CollectOpeningResultsUseCase`)
를 **자기 `readFileSync` 로 열어** 상세 축 수를 세고 있었다. r1 까지의 텍스트 술어는 소비자를
`CONSUMERS` **배열로 열거**했고 그 배열에 이 파일이 없었다 — 즉 r1 의 「구현이 하나 · 소비자 둘이
그것을 쓴다」는 **세 번째 자리를 보지 못한 채** 초록이었다. 열거를 glob 으로 바꾼 첫 실행이
그것을 드러낸 것이고, M-2 가 「구조로 닫으라」고 한 이유가 이 한 줄에 그대로 있다.
