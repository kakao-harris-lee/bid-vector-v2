# M6/6G-2f — 명령과 실측

> 출력 전문을 싣지 않는다(핵심 결과 한 줄). **마지막 HEAD 의 게이트 결과 정본은 이 문서가 아니라
> verifier 와 PR 조치 코멘트**다 — evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다.
> 실 KONEPS 호출 0 · 실행 상태 디렉터리 접근 0(전부 loopback mock).

## acceptance (CI `check` job 의 명령 그대로, HEAD `bf8bbac3`)

| 완료(UTC) | 명령 | exit | 핵심 결과 |
|---|---|---|---|
| 2026-10-03T01:52Z | `./gradlew --no-daemon check` | 0 | 전 모듈 2,607 test · 실패 0(`app` 465) |
| 2026-10-03T01:52Z | `./gradlew --no-daemon qualityBaseline` | 0 | 24 task up-to-date |
| 2026-10-03T01:59Z | `./tools/one-command-check.sh` | 0 | Kotlin 전건 + Python 전건(pytest 1,379 passed · wheel 재수출 1 passed) |

새 test 는 **여섯** — `OpeningPageSizeE2ETest` 셋(wire 축 1 · 거동 축 2) · `OpeningRowsPerPageTest`
셋(기본값 = 상한 · 상한 초과 거부 · 비양수 거부).

### 첫 `check` 라운드가 잡은 것 (exit 1 → 수정 → exit 0)

| 붉은 자리 | 내용 | 수정 |
|---|---|---|
| `:app:ktlintTestSourceSetCheck` | mock 쪽 분할 함수에서 「본문 첫 줄이 서명 줄에 들어간다」 | `87df64e5` |
| `AppGateRegistrationTest` 둘 | 새 test class 둘이 `gate.tests.app` 미등재 — 양방향 등식과 양성 대조(누락)가 함께 붉다 | `bf8bbac3` |

`gate.tests.app` 은 게이트 열거가 아니라 **`app` test 전수 장부**다(그 키의 머리 주석). 게이트 술어는
하나도 바뀌지 않았다(D-6G2f-8).

## TDD — RED 실측 (구현 전, 커밋 `f59b3628`)

설정 칸과 test 를 먼저 두고 배선이 값을 주지 않는 상태에서 쟀다.

| test | 결과 |
|---|---|
| 출하 배선이 개찰 다섯 축의 요청에 설정한 행 수를 싣는다 | `expected:<[999]> but was:<[100]>` |
| 참가 150 행 공고를 쪽 999 는 한 호출로 … | `expected:<[1]> but was:<[2]>` |
| 같은 모집단을 쪽 2 와 쪽 999 로 … 바이트가 같다 | **통과(공허)** |

**ⓑ 는 구현 전에 RED 가 아니다.** 배선이 값을 주지 않으면 어느 판에서도 요청이 100 이라 두 기동이
같은 쪽 크기로 걷는다 — 비교가 참이지만 아무것도 재지 못한다. 그 축의 민감도는 아래 둘째 변이가
든다. 배선 인자를 더한 뒤(`7571f10d`) 여섯 전부 GREEN.

## 변이 실측 (둘, 축이 갈린다)

| 변이 | 지운 것 | 결과 |
|---|---|---|
| 배선이 쪽 크기를 주지 않는다 | `KonepsSourceConfig(numOfRowsPerPage = …)` 인자 | wire 1 + 거동 ⓐ 1 failed(= 위 RED 둘. 두 커밋의 차이가 이 인자 하나뿐이라 RED 실측이 곧 이 변이의 실측이다) |
| 걷기 결과가 쪽 크기에 의존한다 | `applySuccess` 의 완료 판정을 `return WalkStep.STOP` 으로(첫 쪽에서 멈춘다) | 거동 ⓐ·ⓑ 2 failed, **wire 축은 통과** |

둘째 변이가 가른 것: 요청에 실려 나가는 값은 그대로라 wire 축은 초록이고, 쪽이 쌓이지 않으니 표본틀이
쪽 크기만큼만 커져 ⓑ 의 두 바이트열이 갈린다. **「값이 나갔다」와 「그 값이 결과를 바꿨다」가 서로를
대신하지 않는다**는 것이 두 축을 둔 이유다. 변이는 `adapters`(out_scope)에 걸었다 — 쪽 누적이 그
모듈에 있고 변이는 측정이지 산출물이 아니다. 복원은 **사본 덮어쓰기**(`git checkout --` 금지) 뒤
`git diff --quiet` 로 확인.

## 계약 대조 (scope.md 문면과 다르게 한 것 — scope.md 는 고치지 않았다)

1. **ⓐ 를 용역 한 업무로 좁혔다** — 공사 층에는 상세가 비어 오는 공고가 섞여(D-6G-42) 그 공고의
   개찰완료가 쪽 크기와 무관하게 한 호출로 끝난다. 섞으면 「쪽 100 은 두 호출」이 추첨에 따라 갈린다.
2. **「원장 HTTP 줄 1」은 공고당 값** — 표본이 여럿이라 그 축의 원장 줄 수는 공고 수다. test 가
   고정하는 것은 「공고당 1(쪽 999)·2(쪽 100)」과 「원장 줄 = 공고 수 × 그 값」이다.
3. **ⓑ 의 쪽 2 는 기존 고정 2 판이 아니라 요청 존중 판** — 기존 판은 개찰완료 축만 쪼개므로 표본틀이
   한 쪽에 다 와서 ⓑ 가 공허해진다. 표본을 바꿀 수 있는 것은 표본틀 걷기의 쪽 나눔이다.
4. **runbook §5 에 쿼터 줄이 없었다** — 갱신이 아니라 새 줄로 세웠다.
5. **배포 절은 새 §7** — 기존 번호를 밀면 다른 문서가 가리키는 절 번호가 낡는다.
6. `config/quality/gate-tests.properties` 는 scope 의 「baseline 갱신이 필요할 때만, 사유 선언」에
   따라 만졌다(사유는 위 첫 `check` 라운드 표와 그 키의 머리 주석).
7. **착수 실측의 「`numOfRows=` 0건」은 검색 형태 때문이다** — Kotlin 이름 인자는 등호 양옆에 공백을
   두므로 그 형태는 아무것도 매치하지 않는다. 토큰으로 다시 재면 base 에 50여 자리가 있고, **전부
   응답 봉투 fixture**(되돌려 주는 에코)이며 **요청 쪽 단언은 0** 이다. 결론(값 slice 두 축이 비어
   있다)은 그대로이고, 근거를 재는 명령만 바로잡는다.

## 누출 어휘 스캔 (패턴을 축어로 적지 않고 파일 참조로)

- `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m6/6g2f/` → **exit 1**(매치 없음).
- `git diff 9a5aa26a..HEAD -- <in_scope> | grep '^+' | grep -niE -f config/quality/leak-patterns.txt`
  → **exit 1**. 이 slice 가 더한 줄에 매치가 없다.
- in_scope 경로 전체로 넓히면 **19 매치 · exit 0**. 전부 **기존 줄**이고 두 부류다 — (a) Testcontainers
  가 쓰는 test 전용 DB 자격 리터럴 (b) 「그 값이 로그에 새지 않는가」를 재는 기존 test 의 의도적 표식.
  이 slice 가 만들지도, 한 줄도 건드리지도 않았다.
- 육안 확인: 새 test·mock·runbook·evidence 에 실 식별자·기관명·사업자 정보가 없다(mock 은 합성
  공고번호와 `SYN-` 접두 합성 이름만 낸다). 루트 게이트의 scanRoot 는 `reports/evidence` 이고
  `scope.md` 는 제외 대상이라 이 문서와 형제 둘이 스캔 대상이다.

## 낡는 좌표 점검

편집한 파일을 `file:line` 으로 가리키지 않았다(인용문·절 제목·결정 ID·커밋 해시만). 역방향 파급도
쟀다 — 편집한 파일 여섯의 stem 과 축약형으로 `grep -rn '<stem>:[0-9]'` → **전부 0건**.
