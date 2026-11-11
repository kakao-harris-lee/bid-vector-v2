# M10 / gates-web — slice 계약

```yaml
milestone: M10
slice: gates-web
base_sha: 8062dcd784cf81905c50a2c8706d8bbd3ea5263e
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - web/scripts/kotlin-source.mjs          # 신설 — Kotlin 원문 읽기의 공유 한 자리
  - web/scripts/kotlin-source.test.mjs     # 신설 — 그 읽기 술어의 게이트 test
  - web/scripts/money-vocab-cross-lang.test.mjs   # 10B 와 공유 — 아래 「공유 파일」
  - web/e2e/harness/opening-axis.mjs
  - web/config/gate-tests.txt
  - docs/discovery/capability-map.md       # §12.3 의 하위 절 **(j)** 만
  - reports/evidence/m10/gates-web/
out_of_scope:
  - openapi/bidvector-operator-api.yaml    # 10B 의 편집 대상 — 아래 「③ 의 처분」
  - web/eslint.config.js                   # ① 은 이미 닫혀 있다(아래 실측)
  - web/scripts/screen-card-coverage.mjs   # ② 의 named 축은 이미 닫혀 있다(아래 실측)
  - web/src/**                             # 출하 코드를 바꾸지 않는다
  - adapters/**/*AdapterDependencyTest.kt  # Kotlin 쪽 원문 정규식 게이트 — 10C ②
acceptance_commands:
  - npm ci                                     # W-1
  - npm run lint                                # W-2
  - npm run typecheck                           # W-3
  - npm run gate:src-tree                       # W-4
  - npm run api:types:check                     # W-5
  - npm run test                                # W-6
  - npm run gate:tests                          # W-7
  - npm run build                               # W-8
  - npm run gate:boundary-canary                # W-9
  - npx playwright install chromium             # W-10
  - npm run test:e2e                            # W-11
  - ./gradlew --no-daemon :app:bootJar          # W-12
  - npm run test:e2e:journey                    # W-13
  - ./gradlew check                             # evidence 가 leak-pattern 게이트의 스캔 뿌리에 든다
rollback: rollback.md
```

`npm run*` 은 `web/` 에서 돈다(CI 의 `defaults.run.working-directory`). 마지막 줄은 **경로가
닿기 때문에** 든다 — evidence 문서가 `reports/evidence` 를 스캔하는 Kotlin 게이트의 입력이므로,
evidence 를 쓰는 것만으로 Kotlin `check` 가 판정 대상이 된다.

## 대상 넷과 처분

| OPEN | 원 정의가 named 한 것 | 실측 | 이 slice 의 처분 |
| --- | --- | --- | --- |
| `OPEN-M9-CLOCK-LINT-SCOPE` | `Intl` 서식 · `Temporal.Now` · `performance.timeOrigin` · 제외 디렉터리 경유 | **전부 RED** (아래 §실측 ①) | **닫힘 등재** — 근거 PR #132, 코드 변경 없음 |
| `OPEN-M9-SCREEN-COVERAGE-SYMBOL-GRAPH` | 재수출·별칭으로 부르면 의무 집합에 안 든다 | 별칭 호출이 **RED** (§실측 ②) | **닫힘 등재** + 잔여를 `OPEN-M10-SCREEN-COVERAGE-INDIRECT-RENDER` 로 신설 |
| `OPEN-M9-MONEY-VOCAB-CROSS-LANG` | Kotlin 에 값이 늘면 web 이 어디서도 안 붉어진다 | Kotlin +1 이 **RED** (§실측 ③) | **닫힘 등재** — 「하나의 출처」는 범위 밖(사유 아래) |
| `OPEN-M9-KOTLIN-COMMENT-STRIP-DUP` | 주석 걷기가 두 벌이고 한 벌이 문자열 안 `//` 를 깬다 | 두 벌 확인 · 깨짐 **재현** (§실측 ④) | **이 slice 가 구현해 닫는다** |

### ③ 의 처분 — 「하나의 출처」를 범위 밖으로 둔다 (팀장 승인 2026-11-11)

처방 후보였던 openapi `enum` 승격은 셋 때문에 이 slice 가 하지 않는다.

1. **경로가 10B 와 겹친다.** 금액 어휘 칸은 `openapi/bidvector-operator-api.yaml` 의 응답 스키마에
   **스물넷**이고(기계 계수), 10B 가 같은 파일의 타임라인 `command` 칸을 고친다. 두 레인이 한
   파일을 같은 라운드에 만지면 병합 합집합 함정의 자리가 된다.
2. **enum 이 네 벌 가운데 하나만 지운다.** 등식이 묶는 자리는 web 표 · web 유니온 · e2e 라벨 표
   둘이고, **e2e 의 축어 라벨은 일부러 산출물에서 끌어오지 않는다**(저장소 관례 — 표에서 뽑으면
   그 표가 틀려도 대조가 초록이다). enum 은 web 유니온 하나를 생성 타입으로 돌릴 뿐이고 e2e
   표와 Kotlin 읽기는 그대로 남는다.
3. **OPEN 이 named 한 해악은 이미 닫혀 있다**(§실측 ③). 남은 것은 복제 **수**이고 거동 공백이 아니다.

### ② 의 잔여 — `OPEN-M10-SCREEN-COVERAGE-INDIRECT-RENDER` 신설 (팀장 결정 2026-11-11)

원 정의가 named 한 축(재수출·별칭)은 닫혔다. 닫히지 않은 잔여 여섯(간접 렌더 · 감싸 내보내기 ·
개명 재수출 · 클래스 · 함수 참조 · 새 어휘 함수)은 **다른 축**이다. 같은 ID 를 열린 채 두면 장부가
「심볼 그래프를 안 했다」고 말해 거짓이고, 그냥 닫으면 잔여가 장부에서 사라진다. 그래서 ID 를 닫고
잔여를 새 ID 하나로 세운다 — **대표 축은 간접 렌더**이고, 실측에서 **조용한 초록을 재현한 유일한
꼴**이다(내보낸 겉이 비공개 속을 그리면 그 파일이 의무를 하나도 내지 않는다). 받는 자리는 **다음
화면 slice** 다. 그 사실을 §12.3 (j) 의 행이 든다.

### 공유 파일 — `web/scripts/money-vocab-cross-lang.test.mjs` (10B 와 겹친다)

10B(`feature/m10-timeline-command-enum`)가 같은 파일에서 **명령 축을 걷는다**(타임라인 `command` 가
openapi `enum` 이 되면 web 이 생성 타입에서 유니온을 끌어오므로 이 파일의 명령 등식과 Kotlin 원문
읽기가 함께 사라진다). 머지 순서는 **10B → 10D** 다.

이 slice 는 그 파일을 **최소로** 만진다 — 편집 계획은 둘뿐이다.

1. import 블록에 공유 모듈 한 줄을 더한다.
2. 지역 `withoutKotlinComments` 정의(KDoc 포함)를 **지운다**.

**호출부는 한 줄도 바꾸지 않는다** — 공유 모듈이 같은 이름을 내보내므로 `kotlinEnumEntries` ·
`kotlinStringConst` · `kotlinFunctionStringLiterals` 의 본문이 그대로 선다. 10B 가 걷는 영역(명령
등식 `describe` · 명령 쪽 상수)과 이 slice 가 만지는 영역(파일 머리)이 **겹치지 않는다**.

10B 가 먼저 머지되면 이 브랜치가 main 을 병합해 해소하고, 병합이 **어느 쪽 본문도 접지 않았는지**를
충돌 수가 아니라 **계수**로 센다(충돌 0 자동 병합이 같은 본문을 접은 선례가 있다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD -- CLAUDE.md .claude/` — **없음**(착수 시점). 리뷰 요청마다 재산출한다.
이 절에 줄이 서면 그것은 slice 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.

## 실측 — 착수 전 (base `8062dcd7`, web deps 캐시 설치)

판정은 전부 **바꿔치우기**다(더하기만 한 변이는 초록이 되므로 세지 않는다).

① **시계 lint.** 화면·`src/api`·`src/session` 에 탐침 열하나를 두고 lint — `Intl.DateTimeFormat()`
의 인자 없는 `format()` · props 로 받은 서식기의 `format()` · `Temporal.Now` · `performance` 둘 ·
계산 접근 · 구조 분해 · 빈 인자 서식 · 맨 네트워크 전역 · 제외였던 두 디렉터리의 시계 — **14 error**.
목록 자체도 test 가 잠근다: 시계 이름을 하나로 좁히면 **4 failed**, 두 디렉터리를 제외로 되돌리면
**1 failed**.

② **화면 모집단.** 별칭 import 로 어휘를 부르는 카드를 실제 화면 디렉터리에 두면 의무 집합이
자라 등식이 **1 failed**. 같은 자리에서 **간접 렌더**(내보낸 겉이 비공개 속을 그리는 꼴)는
**16 passed** — 잔여가 실재한다.

③ **교차언어 등식.** Kotlin 정본 열거에 값 하나를 더하면 **1 failed**. 되돌리면 19 passed.

④ **주석 걷기.** 두 구현을 같은 입력에 돌려 **같은지**를 센다. 게이트가 오늘 읽는 Kotlin 네 파일은
전부 같고, 하네스가 읽는 파일 **하나가 갈린다** — 줄 단위 정규식 판이 그 파일의 base URL 상수를
`"https:` 로 **절단한다**. 그 꼴이 든 파일은 읽기 집합에 둘이고 `"""` 과 따옴표 든 문자 리터럴은
**0** 이다. 보존판 자신의 실패 꼴도 쟀다 — 설계 검토 §2b 에 표로 둔다.

## 갱신 이력

- 2026-11-11 초판. 착수 보고와 함께.
- 2026-11-11 팀장 답 고정. ①②③ 의 「#132 머지 `fabc496b` 에서 닫힘(장부 공백) + 이 slice 의
  재측정」 등재 꼴 승인(완료 조건 2·3 만족) · ③ 의 범위 밖 판정과 사유 셋 승인 · ② 의 잔여를
  `OPEN-M10-SCREEN-COVERAGE-INDIRECT-RENDER` 로 신설(받는 자리 = 다음 화면 slice) · §12.3 하위
  절 글자는 **(j)**((f) 는 PR #146 M9 종결이 쓴다) · 10B 와 공유하는 파일의 편집 계획 등재.
  설계(단일 패스 주사기 · 던지는 꼴 · 우회 아홉) 승인.
