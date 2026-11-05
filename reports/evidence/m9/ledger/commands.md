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
| 내가 더한 줄의 `OPEN-` 식별자 ⊆ (capability map ∪ `milestone-6.md`) | 식별자 **27** 중 **둘이 밖**이다 — `OPEN-7E3A-OPENING-AXIS-HARNESS`·`OPEN-7C3B-PAPER-BID-NOTIFICATION`. 이것은 이 slice 의 결함이 아니라 **등재 공백의 발견**이고 사실로 적었다(capability map §12.3 (a) 의 그 문단) |
| 낡는 좌표 0 | 내가 더한 줄에 `<파일>:<줄번호>` 꼴 **0**(`git diff df4976cc..HEAD | grep '^+' | grep -E '\.(md\|kt\|ts\|…):[0-9]+'` 빈 출력) |

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

**셸 `grep` 은 이 호스트에서 ugrep 래퍼라 `-I`·`--ignore-files` 때문에 `build/`·바이너리에서 0 을 낸다 —
전부 `command grep` 또는 `git grep` 으로 돌렸다.**

## 게이트

`~/.local/bin/bv-build.sh ./gradlew --no-daemon leakPatternGate` → **exit 0**(빌드 자리 대기 뒤 21s).
그 task 의 스캔 뿌리는 `reports/evidence` 이고 `scope.md` 는 제외 대상이라, 이 slice 가 더한
evidence 쪽 세 절(6E-1 추적표 · open-inventory §8 · M7 종결 §6)과 이 디렉터리의 문서가 모두 그 모수 안이다.
`capability-map.md`·`milestone-*.md` 는 그 뿌리 밖이고 다른 어느 게이트의 입력에도 없다(기계 확인:
`acceptance-trace`·`capability-map` 을 입력으로 선언하는 게이트 **0** — 후자의 매치는 전부 KDoc 인용이다).
**전건 acceptance 의 정본은 PR CI 다**(네 job 같은 명령).
