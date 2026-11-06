# M9/gates-tests commands

Gradle 은 전부 `~/.local/bin/bv-build.sh ./gradlew --no-daemon …`. **전건 `check`·container·
Playwright 는 로컬에서 돌리지 않는다 — PR CI 가 정본**(M9 레인 공통 규율). **예외**: 팀장 승인으로
`CleanMigrationCheckTest` **한 클래스만 3회**(원판 1 · 변이 2), 매회 메모리 가드.

## 2026-11-06 — 항목 1 (persistence 의존 게이트 술어)

- cmd: `:adapters:compileKotlin`
- exit: 0
- 핵심 결과: 상수 풀 실측 — persistence main 이 참조하는 `bidvector` 루트 **넷**(허용 목록의 근거)

- cmd: `:adapters:test --tests '…persistence.PersistenceAdapterDependencyTest'` + `:adapters:ktlintTestSourceSetCheck`
- exit: 0
- 핵심 결과: `tests="4" skipped="0" failures="0"`

### 바꿔치우기 변이 — 같은 변이, 두 술어

변이: persistence main 에 **import 없이 전체 한정 좌표**로 층 밖 domain 을 부르는 함수 하나
(D-6F5-18 이 적은 그 우회 형태).

- cmd: 새 술어(상수 풀 허용 목록) + 변이 · `--rerun-tasks`
- exit: 1
- 핵심 결과: `4 tests completed, 1 failed` — 위반 좌표 **4**(`WatchSubjectOutcome$Unavailable` 계열)

- cmd: 옛 술어(`df4976cc` 에서 복원한 소스 텍스트 import 정규식) + **같은 변이** · `--rerun-tasks`
- exit: 0
- 핵심 결과: **초록 — 옛 술어는 이 우회를 보지 못한다**(음성 대조, 이 OPEN 의 처방 근거)


## 2026-11-06 — 항목 2 (CHECK 본문 단언)

- cmd: 마이그레이션 전수 실측 — `notice_round` CHECK 보유 표 · `collection_run` CHECK 열거
- exit: 0
- 핵심 결과: **여섯 표**가 같은 본문을 공유(옛 존재 단언이 다섯 약화에도 통과한 이유) · `collection_run`
  **13** = 기존 개수 축 기대값(집합 등식 13 원소 교차 확인), 뒤 마이그레이션은 `GRANT` 하나뿐

- cmd: `:adapters:compileTestKotlin :adapters:ktlint{Main,Test}SourceSetCheck :adapters:detekt :adapters:sizeGate :adapters:testShapeGate`
- exit: 0
- 핵심 결과: 전부 통과 · 파일 **405**줄(한도 500)

기대 본문 문자열은 정적 유도였다 — ⓐ 교체 대상 존재 단언 둘의 축어(COL-06·H-3)와 ⓑ 같은 파일에서
이미 통과하는 일곱 본문의 정규화 형태. **그 뒤 팀장 승인으로 로컬 실측했다 — 아래.**

### DB 실측 — 승인된 예외의 1·2회차

실행 전 available **13.3**·**13.9GB** ≥ 6GB · swap free **5.4GB** ≥ 2GB.

- cmd: `:adapters:test --tests '…persistence.CleanMigrationCheckTest'`
- exit: 0
- 핵심 결과: `tests="11" skipped="0" failures="0" errors="0"` — **정적 유도한 기대 본문이 전부
  글자까지 맞았다**(`collection_run` 집합 13 원소 · `notice_round` 여섯 표). 결과 XML 의 새
  mtime 과 test 이름 전수로 실행을 확인했다(캐시 적중이 아니다)

#### 바꿔치우기 변이 — 제자리 약화

변이 둘을 한 트리에: ⓐ COL-06 항등식에 ` OR TRUE` **제자리** 추가 · ⓑ `notice_round` 형식 CHECK
**여섯 중 다섯**을 `IS NOT NULL`(NOT NULL 열이라 항진명제)로 약화(여섯째는 원판 유지).

- cmd: 변이 + `--rerun-tasks`
- exit: 1
- 핵심 결과: `tests="11" failures="2"` — 붉어진 것은 **이 slice 가 더한 둘뿐**이고
  **개수 축(`축8 CHECK 개수가 테이블별로 기대와 같다`)은 초록**이다. 그 한 줄이 이 OPEN 의
  결함 클래스를 못 박는다 — 제자리 약화는 개수로 보이지 않는다. 기존 단언 여덟도 초록(변이가
  표적이고 통째로 깨는 것이 아니라는 통제군)


- cmd: 실행 뒤 컨테이너·daemon 잔존 확인
- exit: 0
- 핵심 결과: 내 레인 JVM **0** · 내 GradleDaemon **0**(살아 있는 daemon 은 다른 레인 cwd) ·
  내 Testcontainers 컨테이너는 JVM 종료와 함께 사라졌다. 이후 관측된 ryuk·postgres 쌍은 ID 가
  바뀌며 돌고 있어 **다른 레인의 활성 실행**이라 건드리지 않았다. 장수 컨테이너 둘(dev·prod)은 무관

### map 등식의 분해력 — 예외의 3회차(cr MEDIUM-1 뒤 재실측)

루프를 map 등식으로 바꾼 뒤 **다섯 약화 같은 변이**로 다시 쟀다(실행 전 available **16.8GB** ·
swap free **5.4GB**, 변이는 커밋 뒤).

- cmd: 변이(다섯 약화) + `--rerun-tasks`
- exit: 1
- 핵심 결과: `11 tests completed, 1 failed` 이고 실패 메시지 첫 줄이 **`Values differed at keys
  notice, opening_result, qualification_text, opening_reserve_price, bid_case`** — **약화한 다섯을
  한 줄에 전부** 드러낸다. 원판으로 남긴 `notification_inbox` 는 양쪽 map 에서 같은 값으로 나와
  음성 대조가 된다. 앞 판 루프는 같은 변이에서 **첫 표 한 줄**만 붉혔다(cr MEDIUM-1 의 그 지적)


## 2026-11-06 — 항목 4 (감시 port 필드 축)

- cmd: `javap -p -v` 로 두 class 의 `Fieldref` 전수
- exit: 0
- 핵심 결과: **하나**(D-6F4W-4 의 고정 사유 상수) — 허용 목록이 그 하나다

- cmd: `:adapters:test --tests '…evaluation.EvaluationAdapterDependencyTest'` + lint·게이트 다섯
- exit: 0
- 핵심 결과: `tests="12" skipped="0" failures="0" errors="0"` — 새 셋(부분집합·양성·음성) 실행 확인

### 바꿔치우기 변이 — production 파일

변이: `subjectFor` 를 `WatchSubjectOutcome.Unavailable` 반환으로 바꿨다(메서드 참조는 그대로
남기는 가장 적대적인 형태).

- cmd: 변이 + `--rerun-tasks`
- exit: 1
- 핵심 결과: `12 tests completed, 1 failed` — 붉어진 것은 **필드 축 하나**. 메서드 축 부분집합
  검사는 **초록**(이 slice 가 더한 축만 이 변이를 본다)

## 변이 복원 — 네 번 모두 같은 결과

변이는 **전부 커밋 뒤에** 심고 복원은 변이 파일만(`git checkout HEAD -- <그 경로>`). 네 번
(항목 1 · 항목 2 두 번 · 항목 4) 모두 exit **0** · `git status --porcelain` **빈 출력** · 내 변이
표지 **0** · 원판 표지 보존(형식 CHECK **4 파일** · 원판 반환 **1** · 새 술어 **2**).

## 2026-11-06 — main `8b3b610c`(PR #130) 병합 검증

- cmd: `git merge 8b3b610c` → 겹침 계수 · 합집합 등식 · 술어 입력 변경 여부
- exit: 0
- 핵심 결과: 겹침 **1**(등재부 — 저쪽 항목 셋, 이쪽 주석만). **충돌 0 자동 병합**이라 접힘을 계수로
  쟀다: 등재 **512**(=509+3) · **1075**줄(=1066+6+3) · 중복 **0** · 저쪽 셋 각 **1** · 내 주석 **1**.
  사전순 아닌 블록 셋은 base·양쪽 부모·결과가 **모두 같다**(기존 부채). 마이그레이션과
  persistence·evaluation·snapshot main 은 **각 0 파일** — 내 술어 입력이 바이트 동일하다

- cmd: 표적 test 둘 + adapters ktlint 둘 · detekt · sizeGate · testShapeGate
- exit: 0
- 핵심 결과: `4/4` · `12/12`(0 skipped, XML 새 mtime) · 게이트 전부 통과
