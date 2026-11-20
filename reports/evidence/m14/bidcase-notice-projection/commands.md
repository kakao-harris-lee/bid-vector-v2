# M14 / bidcase-notice-projection (14E) — 실행한 명령과 결과

판정 SHA 는 `scope.md` 의 base 위 이 브랜치의 head 다. 아래 수치는 전부 이 worktree 에서
기계로 냈고, 출력 전문은 싣지 않는다(핵심 결과 한 줄씩).

## 착수 실측 (base `af6d9c0f`)

| 잰 것 | 명령의 축 | 결과 |
| --- | --- | --- |
| 읽을 열 셋의 실재 | 마이그레이션 전수 grep | `notice_title`(V14) · `demand_agency_name` · `notice_agency_name`(V7) — 셋 다 nullable |
| 앱 역할의 읽기 권한 | GRANT 전수 grep | V2 가 `notice` 에 `SELECT` 를 이미 준다 → **마이그레이션 불필요** |
| 건 ↔ 공고 결합 | `bid_case` 정의 | 키 **사본** · FK **없음**(보존 파기와 충돌해 의도적으로 뺐다) |
| 목록의 질의 수 | `BidCaseWorkflow.list` 호출 | 오늘 4회 → 투영 포트를 더해 5회. **건 수와 무관** |
| `readBidCase` 의 web 소비자 | 승인 쌍 목록 | **없다** → operation 집합 불변(승인 쌍 등식 안 움직임) |
| `BidCase` 를 짓는 web 자리 | 생성 타입 재생성 뒤 `tsc` | **7 파일 · 9 리터럴**이 멈췄다 |
| `BidCase` 의 키 집합 등식 | 계약 test 전수 | **없었다**(어휘 축만 있었다) → 이 slice 가 세운다 |

## RED → GREEN (TDD)

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| RED | `:workflow:test --tests BidCaseWorkflowTest` | **exit 1** · 3건 실패(`expected:<1> but was:<0>` · `Expected NoticeProjection(…) but actual was null` 둘) |
| GREEN | 같은 명령 | exit 0 |

RED 가 먼저 섰다는 것이 요점이다 — 구현 뒤에 쓴 단언이면 「그 구현이 하는 일」을 적었을 뿐이다.

## acceptance — CI `check` job 의 Kotlin 전건

| 라운드 | 종료 코드 | 붉은 task | 조치 |
| --- | --- | --- | --- |
| r1 | **1** | `:adapters:cpdCheck` | 새 질의가 마감 질의의 조립을 복사했다 → 공용 자리 하나로 모음 |
| r2 | **1** | `:app:ktlintTestSourceSetCheck` | 새 test 가 기존 KDoc 과 그 함수 사이에 끼었다 → 문서를 자기 함수로 되돌림 |
| r3 | **1** | `:workflow:detekt` | 함수 12개(상한 11) → 단건 관용구를 포트 옆 확장으로 옮김 |
| r4 | **1** | `:workflow:ktlintTestSourceSetCheck` | 다인자 서명의 본문 들여쓰기 → `ktlintFormat` 이 고침 |
| r5 | **0** | 없음 | — |

**다섯 모두 산출물의 결함을 게이트가 잡은 것이고, 게이트 술어는 하나도 바꾸지 않았다.**
거동을 바꾼 수정은 r1·r3 의 구조 이동 둘뿐이며 둘 다 거동 0 이다(문장 · 바인딩 · 부르는 식과
결과가 그대로).

### r5 의 초록을 어떻게 읽어야 하는가 — **증분 실행이었다**

r5 는 17초에 끝났고 **test task 전부가 `UP-TO-DATE`** 였다. 종료 코드만 보면 「전건이 돌았다」로
읽히지만 사실이 아니다. 실제로 test 가 돈 것은 **r4** 이고(그때 `:app:test`·`:workflow:test`·
`:adapters:test` 가 실행돼 전부 통과했으며 r4 가 붉은 것은 ktlint 하나였다), r4 → r5 사이의
변경은 test 파일 한 개의 **공백뿐**이라 Gradle 이 입력 불변으로 보아 건너뛴 것이다.

그래서 이 slice 는 초록을 **두 조각으로 실측**해 적는다:

| 조각 | 명령 | 결과 |
| --- | --- | --- |
| 정적 게이트 전부 | `check -x test` | **exit 0** (58초) — ktlint · detekt · cpd · size · architecture · contract 포함 |
| test 축 | 표적 test task `--rerun` | **exit 0** — task 줄에 `UP-TO-DATE` 가 없음을 확인 |

**그 뒤 캐시를 끄고 실제로 돌렸다.** `test --rerun --no-build-cache` 가 판정 SHA 에서 모듈
전부의 test 를 **캐시 표지 없이** 실행했다 — **exit 0 · 4612 test · 실패 0 · 에러 0 · 스킵 0**
(36m37s). 이것이 test 축의 정본이고, 위의 증분 초록들(r5 의 `UP-TO-DATE` 아홉 · `clean check`
의 `FROM-CACHE` 아홉)은 그것을 갈음하지 않는다.

`clean` 만으로는 차가워지지 않는다는 것도 실측했다 — `clean check` 가 33초에 끝났고 test 가
전부 `FROM-CACHE` 였다(`clean` 은 빌드 **캐시**를 비우지 않는다).

### 낡은 리포트 둘 — **로컬 전용**

변이가 소스의 mtime 만 올려 `cpdCheck` 가 `UP-TO-DATE` 가 되고 리포트는 그 전 것이 남았다.
`cpdReportPresenceGate` 가 그것을 **설계대로** 잡았다(`:adapters` · `:app` 각 1회). **CI 는 차가운
체크아웃이라 재현되지 않는다** — 변이 측정이 로컬에 남기는 찌꺼기다. 리포트를 지우고 재생성해
해소했고 중복 계수는 0 이다.

### `leakPatternGate` 는 **실제로 돌렸다**

이 게이트는 앞선 모든 실행에서 `UP-TO-DATE` 였다(= 돌지 않았다). evidence 세 파일이 모두 선
HEAD 에서 `leakPatternGate --rerun` 을 돌려 **task 줄에 접미사가 없음**을 확인했다 — **exit 0**.
evidence 가 누출 패턴 스캔 뿌리에 들므로 이 확인이 없으면 그 축은 미측정이다.

## acceptance — CI `web` job 전건 (W-1 ~ W-13)

| 단계 | 결과 | 핵심 수치 |
| --- | --- | --- |
| W-1 `npm ci` · W-2 lint · W-3 typecheck · W-4 src-tree | 전부 exit 0 | — |
| W-5 `api:types:check` | exit 0 | 생성 타입이 문서와 **바이트까지 같다** |
| W-6 `vitest` | exit 0 | **60 파일 · 1544 test 통과** |
| W-7 `gate:tests` · W-8 build · W-9 boundary-canary | 전부 exit 0 | — |
| W-10 Playwright 설치 · W-11 스모크 | exit 0 | 스모크 **21 통과** |
| W-12 `:app:bootJar` · W-13 여정 e2e | exit 0 | 여정 **22 통과** |

W-6 은 「전부 통과인데 exit 1」 서명이 아니다(통과 계수와 종료 코드를 함께 봤다).

## 변이 — 바꿔치우기 다섯과 음성 대조

전부 **치환**이고(더하기만 한 변이가 아니다), 커밋된 트리에서 걸고 `--rerun` 으로 돌려
cache 적중이 실행을 갈음하지 않게 했다. 변이 전 porcelain 이 비어 있음을 확인했다.

| # | 무엇을 바꿔치웠나 | 무엇이 붉어야 하나 | 결과 |
| --- | --- | --- | --- |
| 1 | `noticeKnown` 을 「제목이 있는가」로 — **두 사실을 접는다** | HTTP 축 | **exit 1** — 세 사실 test 실패 |
| 2 | 목록의 묶음 조회를 **건마다 묻기**로 | N+1 축 | **exit 1** — `expected:<1> but was:<3>` |
| 3 | 문서를 두고 **DTO 에만 칸 추가** | 새 키 집합 등식 | **exit 1** — 「여분 필드가 생기면 깨진다」 실패 |
| 4 | 질의에서 **키 열 둘 제거** | 실 DB 축 | **exit 1** — 행 없음/칸 빔 구분 실패 |
| 5 | 문서 `required` 에서 `noticeKnown` 제거 | W-5 바이트 등식 | **exit 1** — 커밋본 195341 ↔ 재생성 195342 |

**음성 대조**(복원한 트리에서 같은 명령, `--rerun`): 넷 전부 **exit 0** 이고 task 줄에
`UP-TO-DATE` 가 **없다**(실제 실행). W-5 도 exit 0 이며 「바이트까지 같다」를 낸다.
변이 2 의 수(3)가 건 수와 같다는 것이 N+1 축이 **공허하지 않음**의 증거다 — 계수가 아니라
**키 집합까지** 재므로 「한 번 부르고 버리는」 꼴도 지나가지 못한다.

## 누출 패턴

evidence 세 파일이 모두 선 HEAD 에서 `leakPatternGate --rerun` 을 **실제 실행**으로 확인했다
(위 절 — 접미사 없는 task 줄 · exit 0). 이 slice 가 더한 문면에 비밀값 어휘가 없다 — 응답의 자유
문면은 전부 공개 조달 데이터인 공고 칸뿐이고, 운영자 PII 가 실릴 자리를 더하지 않았다.

## clean-tree

`git status --porcelain -- <in_scope 개별 인자>` 가 빈 출력임을 변이 전후로 확인했다.
양성 대조는 변이 다섯이 겸한다(각 변이 적용 시 그 경로가 porcelain 에 떴다).
