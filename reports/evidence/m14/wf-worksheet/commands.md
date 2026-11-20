# M14 / wf-worksheet (14C) — 실행한 명령과 실측

base `056f405c` · 레인 worktree `bid-vector-v2-m14-wf-worksheet`.
출력 전문은 싣지 않는다 — 명령마다 **종료 코드와 핵심 수 한 줄**이다.

## 착수 실측 (계약 행 ↔ 코드)

| 물음 | 실측 | 쓴 명령의 축 |
| --- | --- | --- |
| 와이어프레임의 레일에 무엇이 있나 | 카운트다운은 **머리 행 오른쪽**, 「참가 자격」은 **본문 첫 카드**. 300px 칸에 있는 것은 다음 확인 시점 · 타임라인 · 내려놓기 **셋** | 원천 HTML 의 `.col width:300px` 블록을 읽었다 |
| 머리의 「방식」을 내는 칸이 있나 | **없다.** `NoticeSummary` 는 번호 · 차수 · 상태 · 제목 · 수요기관 · 공고기관 · 기초금액 다섯 칸 · 마감이다 | 생성 타입에서 그 스키마를 읽었다 |
| 「근거 원문」에 닿는 칸 | 공고 원문 **0**. 가장 가까운 것은 `licenseRequirementSourceFields` | 같은 자리 |
| 카운트다운의 단위 | `timeDisplay` 가 내는 것은 **날 단위** 하나(`daysUntil` → `dayCountText`) | 그 모듈의 내보낸 이름 전수 |
| 격자 다섯 줄 가운데 잠긴 것 | **기초금액 한 줄**(13C r4 L-1 이 `closest('dd')` 로 아래층 문면을 센다) | 그 test 의 단언 |
| 워크시트의 터치 타깃 하한 | `minCount: 8`(390px) | 터치 게이트의 화면 표 |
| 배치 게이트가 출하 레일을 자동으로 드나 | **들지 않는다** — 그 spec 은 `/` 한 자리만 열고 그 레인에 백엔드가 없다 | 그 spec 의 `goto` 전수 |
| 「되돌릴 길 기억」을 바꾸는 자리 | 올리는 자리 **1** · 내리는 자리 **1** · 그 사이에 토스트를 내는 자리 **6 더** | 화면 모듈의 `show(` 전수 |

## RED (구현 전)

새 단언 **10** 을 먼저 세우고 돌렸다.

```
npx vitest run src/screens/WorksheetScreen.test.tsx src/components/PageHeader.test.tsx
```

→ **10 실패 · 118 통과**(종료 1). 실패한 것이 정확히 새 단언 열이다.

**열하나를 썼는데 열이 붉었다**: 「건이 입찰함에 없으면 두 단을 쓰지 않는다」는 착수 트리에
두 단 배치 자체가 없어 **공허하게 초록**이었다. 구현 뒤에는 공허하지 않다 — 아래 변이 M6 가
그것을 붉힌다.

## acceptance (ci.yml `web` job 전건 W-1~W-13)

HEAD `0f437b61` 에서 돌렸다. 모두 **종료 0**.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| W-1 | `npm ci` | 0 |
| W-2 | `npm run lint` | 0 (경고 0 — `--max-warnings 0`) |
| W-3 | `npm run typecheck` | 0 |
| W-4 | `npm run gate:src-tree` | 0 |
| W-5 | `npm run api:types:check` | 0 (생성 타입 편집 0) |
| W-6 | `npm run test` | 파일 **70** · test **1680** 통과 |
| W-7 | `npm run gate:tests` | 0 (등록부와 같다) |
| W-8 | `npm run build` | 0 |
| W-9 | `npm run gate:boundary-canary` | 0 |
| W-10 | `npx playwright install chromium` | 0 |
| W-11 | `npm run test:e2e` | 통과 **36** · flaky 0 · spec 4/4 |
| W-12 | `./gradlew --no-daemon :app:bootJar` | 0 |
| W-13 | `npm run test:e2e:journey` | 통과 **22** · flaky 0 · spec 8/8 |

W-11 · W-13 과 캡처는 `flock ~/.local/bid-vector-web-e2e.lock ~/.local/bin/bv-build.sh …` 로
줄 세웠고, Gradle 은 `bv-build.sh` 를 지났다. 호스트 점검(빌드 자리 · 여유 · RSS 상위)은 빌드
명령과 **따로** 먼저 돌렸다.

**늘어난 수의 귀속** — 새 test 파일은 0 이라 파일 수는 base 와 같다. 늘어난 test 는 이 slice 가
더한 `it` **열하나**이고(기계 산출: 두 test 파일의 diff), 기존 단언은 **주장을 고치지 않은 채**
그대로 통과한다 — 13C 의 기능은 옮기기만 했다는 계약이 그 자리에서 측정된다.

**여정 레인이 드는 것**(이 slice 의 축이라 따로 적는다): 머리 다섯 축의 정확 일치 · 터치 타깃
화면별 전수(워크시트 하한 **8**) · 내려놓기 둘의 상자. 셋 다 초록이므로 **계수는 줄지 않았다.**

## 변이 (바꿔치우기 · 커밋 뒤 · 매번 `git checkout -- src/` 로 되돌림)

변이 전 `git status --porcelain` 이 **빈 출력**임을 확인하고 시작했다(미커밋 산출물이 함께
지워지는 자리다).

| 변이 | 무엇을 바꿔치웠나 | 붉은 단언 |
| --- | --- | --- |
| M1 | `PageHeader` 의 `h2` 에서 제목 자리 표지를 뺀다 | 짝 test **둘** + 워크시트 머리 test **하나**(제목 정확 일치) |
| M2 | 되돌릴 길 기억을 effect 유도에서 **손으로 맞추는 옛 꼴**로(내려놓기 성공에서만 올린다) | 「실행 취소 없는 토스트로 바뀌면 그 기억이 비워진다」 |
| M3 | `TwoColumn` 의 본문과 레일을 **맞바꾼다** | 자격의 본문 소속 · 본문 차례 · 레일 차례 **셋** |
| M4 | 기초금액의 출처를 다시 `?? ''` 로 | 「출처가 없으면 빈 괄호를 그리지 않는다」 |
| M5 | 카운트다운 카드를 머리에서 **본문 첫 자리로 옮긴다** | 「마감 카운트다운은 두 단 밖」 |
| M6 | 레일이 비어도 두 단을 쓰게 한다 | 「건이 입찰함에 없으면 두 단을 쓰지 않는다」 |

**M2 는 lint 도 typecheck 도 통과한다**(둘 다 종료 0) — 그 결함을 잡는 것은 **새 test 하나뿐**
이다. 그것이 이 변이를 쓴 이유다.

**M3 가 붉히지 못하는 것**: 「레일은 문서 순서에서 본문 뒤다」는 칸의 **내용**이 아니라 칸의
**차례**를 재므로 맞바꿈에 걸리지 않는다. 그 축을 붉히는 것은 `TwoColumn` 자신의 짝 test 이고,
이 slice 는 그 컴포넌트를 바꾸지 않는다.

## 캡처 (와이어프레임 · 앞 · 뒤)

`npm run capture:usecase` 를 **두 트리에서** 돌렸다 — 이 레인(뒤)과 base `056f405c` 를 체크아웃한
버릴 worktree(앞). 산출물은 in_scope 밖이라 복사한 뒤 트리를 되돌렸고(`git status --porcelain`
빈 출력), 레인 scratchpad 에만 둔다. 앞 트리는 `cp -al` 로 `node_modules` 를 걸고 `:app:bootJar`
를 따로 지었다(그 하네스의 전제다). 캡처 뒤 그 worktree 는 지웠다.

**앞 캡처의 값**: 머리의 다섯 줄 격자 · 한 단 · 근거 다섯 절이 금액 입력 앞에 선 차례.
**뒤 캡처의 값**: 머리 행 + 카운트다운 · 두 단 · 여정 차례. 390px 은 한 단이고 레일이 본문 뒤다.

**390px 에서 한 번 고쳤다**: 첫 뒤 캡처에서 카운트다운 카드가 줄지 않아(`shrink-0`) 제목이 한
글자씩 꺾였다. 좁은 폭에서 카드를 한 줄 통째로 두어 고치고 **다시 찍었다**.

## 누출 패턴 게이트

evidence 를 담은 HEAD 에서 `grep -rniE -f config/quality/leak-patterns.txt` 의 어휘로 스캔하는
Kotlin 게이트(`ktlintCheck detekt cpdCheck leakPatternGate`)를 돌린다. 이 파일들은 그 스캔
뿌리에 들어 있다. **마지막 HEAD 의 결과 정본은 verifier 와 PR 조치 코멘트**다 — evidence 에
적으려는 시도가 커밋을 또 낳기 때문이다.
