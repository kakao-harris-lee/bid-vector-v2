# M9 / ledger — 실행 기록

계약 `scope.md`. **코드 0 · 순 추가 slice** — 복원 목록 여섯 경로의 numstat 이 `추가 200 · 삭제 0` 이다
(닫힌 evidence 를 덮지 않고 날짜 붙은 절로 덧붙인다는 계약을 이 수가 센다).

## 근거 수집 — 「닫힘」 주장의 원천

| 주장 | 원천 |
| --- | --- |
| PR 번호 ↔ 머지 커밋 ↔ head | `gh pr view <n> --json number,state,mergedAt,mergeCommit,headRefOid`(읽기만). `gh pr view --comments` 는 이 저장소에서 GraphQL `projectCards` 폐기 오류로 **빈 출력**이라 `gh api repos/<owner>/<repo>/issues/<n>/comments --paginate` 로 읽었다 |
| 각 slice 의 OPEN 처분 | 머지된 evidence 의 처분 절(budget-clear checklist 「OPEN 처분」 · inbox-vocab scope 「계약 갱신 2026-11-05」 · screen-display checklist 제한·「다음으로 넘기는 것」 · prod-tools checklist D-1·D-2 · retention-1y scope D-1·D-5) |
| M9 신설 OPEN 일곱 | 화면 표시 다섯은 screen-display checklist(제한 ③·⑦c·⑦d·⑧·⑨). **CVE apt 둘은 evidence 에 없다** — 유일한 원천이 PR #126 조치 코멘트다(아래 알려진 제한) |
| 6G-2c 잔여 열하나의 모수 | `reports/evidence/m6/6g2c/scope.md` 의 `OPEN-6G-REVIEW-FOLLOWUPS` 행(6G-2a ⑤~⑧ · 6G D-82 ⑥⑦⑨⑩ · 6G-2d D-49 ⑦ · D-53 둘 = 열하나) |

## 기계 대조 (acceptance 첫·둘째 줄)

| 대조 | 결과 |
| --- | --- |
| 종결 등재 식별자 여섯이 처분 자리(capability map)와 근거 자리 양쪽에 있다 | 여섯 전부 성립(근거 자리 4~8 곳) |
| 신설 OPEN 일곱이 처분 자리와 원천 양쪽에 있다 | 일곱 전부 성립. `OPEN-CVE-APT-*` 둘의 「원천」은 이 slice 의 `scope.md` 뿐이다(제한 1) |
| 미머지 PR 몫 둘은 **등재하지 않는다** | `OPEN-STR-12-INTERACTIVE`·`OPEN-M9-PORTAL-02-SPLIT-OPERATIONS` 는 「그 PR 머지 시 확인」 문장 하나로만 나타난다. 두 PR 의 head diff 에 그 식별자가 **이미 있다**(`gh api …/pulls/<n>/files` 의 `+` 줄) — 그래서 등재하면 중복이다 |
| 내가 더한 줄의 `OPEN-` 식별자 ⊆ (capability map ∪ `milestone-6.md`) | **라운드 1 에서 둘이 밖이었다** — `OPEN-7E3A-OPENING-AXIS-HARNESS`·`OPEN-7C3B-PAPER-BID-NOTIFICATION`(등재 공백의 발견). **계약 갱신 D-L-1 이 그 둘을 §14.3 에 행으로 세운 뒤 재검하면 MISSING 0 이다** — 포함이 성립한다 |
| 낡는 좌표 0 | 내가 더한 줄에 `<파일>:<줄번호>` 꼴 **0** — base..HEAD diff 의 `+` 줄만 뽑아 확장자+콜론+숫자 정규식으로 센다(**셀 안에 셸 파이프를 적지 않는다** — 표를 깬다) |

## 오늘 상태 실측 — 처분 후보표가 인용한 것만

| 축 | 명령의 성질 | 결과 |
| --- | --- | --- |
| 등재 등식 게이트가 정책 파일을 **선언된 입력**으로 받는가 | build-logic task 의 속성 애노테이션 판독 + 손으로 건 등식 test 잔존 계수 | `@InputFile` **있음** · `…GateRegistrationTest` 류 클래스 test 소스에 **0** |
| Kotlin production 의 `String.format` | `git grep -c … -- '*/src/main/*'` | **0**(층 표지 Locale 축의 자리가 좁아졌다) |
| `lineCountOf` | 같은 꼴 | 소스 **0**(남은 언급은 6G·6G-2e evidence 문면) |
| `executionDay`·`framePage` | 같은 꼴 | 개찰 표본 framer 에 **그대로** |
| `AxisConclusion.settled` 의 Kotlin 호출자 | `git grep '\.settled' -- '*.kt'` | **0**(파생 속성 선언만 남았다) |
| `mkdtemp` 정리 없는 자리 · seed 하드코딩 | 같은 꼴 | 각각 **둘** · 그 민감도 test 에 **셋** |
| `ml_engine` 정책 모듈 수와 그중 `yaml` 사용 | `git ls-tree` + `git grep -l` | **여섯** 중 **넷**(「네 벌」의 수는 맞지만 같은 파서의 네 벌인지는 재지 않았다) |

**셸 `grep` 은 이 호스트에서 ugrep 래퍼라 `build/`·바이너리에서 0 을 낸다 — 전부 `command grep`·`git grep` 이다.**

## 게이트

`~/.local/bin/bv-build.sh ./gradlew --no-daemon leakPatternGate` → **exit 0 — 라운드마다**(산출물 HEAD ·
evidence HEAD · 계약 갱신 라운드 · v L-1 라운드). 그 task 의 스캔 뿌리는 `reports/evidence`(`scope.md` 제외)라
이 slice 가 더한 evidence 쪽 세 절과 이 디렉터리가 모두 모수 안이다. `capability-map.md`·`milestone-*.md` 는
그 뿌리 밖이고 **다른 어느 게이트의 입력에도 없다**(둘을 입력으로 선언하는 게이트 **0** — 매치는 전부 KDoc 인용).
**전건 acceptance 와 마지막 HEAD 결과의 정본은 PR CI·verifier·조치 코멘트**이고, 그것을 이 절에 다시 적으려는
시도가 커밋을 또 낳는다.

## 계약 갱신 라운드(2026-11-06, D-L-1·D-L-2)의 대조

| 대조 | 결과 |
| --- | --- |
| §14.3 에 끼운 두 행이 그 표의 열 모양과 같다 | 추가 두 행 **파이프 6**(그 표는 5열) · 끼운 자리 바로 아래가 `### 14.4` 경계이고 윗 행(`OPEN-7C4-AWAITING-CHECKLIST`)이 그대로다 |
| §12 머리의 활성 집계가 움직이지 않았다 | 머리 줄 diff **0** — 두 행은 §14.0 이 「담당만 적는다」고 선언한 표에 섰다 |
| 식별자 포함 재검 · cve-apt 두 OPEN 의 자리 | MISSING **0**(위 표 넷째 줄) · 그 slice evidence 에 **0 → 2**(출처 `cr MEDIUM-1`·`-2`, 받는 자리 M9/cve-followups PR #131) |

## v L-1 라운드(PR #133) — 기계 계수

| 축 | 결과 |
| --- | --- |
| §2 「M7 종결이 이미 넘긴 것」 행의 식별자 | **아홉**(7E2B · 7E3B 둘 · 7E3A · `OPEN-7C3B-*` 셋 · 7F 장부 둘) — **STR02 0** |
| STR02 의 실제 자리 | **M7 종결 checklist 4**, 그 목록은 **열하나**(아홉 + STR02 + SET01) |
| 같은 오류의 다른 자리 | **0** — 내가 더한 줄의 `같은 행` 매치 둘 중 나머지는 내 표를 가리키는 올바른 쓰임 |
