# M14 / wf-worksheet (14C) — slice 계약

```yaml
milestone: M14
slice: wf-worksheet
base_sha: 056f405c9e5659b9a068288f1104f9e04a6de016   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/src/screens/WorksheetScreen.tsx    # 머리 블록 · TwoColumn · 본문 순서 · 「되돌릴 길 기억」 · 빈 괄호
  - web/src/screens/WorksheetGrounds.tsx   # 자격 절을 본문 첫 카드로 — 절 자체의 거동 0
  - web/src/screens/WorksheetScreen.test.tsx  # **새 배치의 새 단언만** 더한다 — 13C 의 기존 주장은 그대로
  - web/src/components/PageHeader.tsx      # 판정 ⑤ — 제목 자리 표지 하나(additive)
  - web/src/components/PageHeader.test.tsx # 판정 ⑤ — 짝 test 를 **같은 커밋에서** 넓힌다
  - docs/discovery/capability-map.md       # §12.3 **(aa)** — 머지 차례에 붙였다(그때 main 은 base 에서 움직이지 않았다)
  - reports/evidence/m14/wf-worksheet/
not_touched:   # in_scope 로 열어 두었으나 **편집 0** 으로 끝났다(기계 diff 가 정본이다)
  - web/src/screens/WorksheetRecord.tsx    # 넘김 카드·기록 폼은 자리만 옮겼고 그 자리는 화면 모듈이 정한다
  - web/src/screens/WorksheetTimeline.tsx  # 타임라인도 같다 — 레일에 꽂는 것은 화면 모듈이다
out_of_scope:
  - web/e2e/journey/journey.spec.ts        # 판정 ④ — 레일 상자 축은 14B 소유. 이 레인은 문서 순서만 잠근다
  - web/src/components/ 의 나머지          # 14A 원시 요소 — 쓰기만 한다
  - web/src/display/timeDisplay.ts         # 14A 소유. 분·시 단위 카운트다운은 새 축이다(OPEN ①)
  - web/src/display/noticeDisplay.ts · moneyDisplay.ts · caseDisplay.ts · bidBoxDisplay.ts  # 표시 계약의 표
  - web/src/screens/TodayNoticesScreen.tsx · NoticeCard.tsx · OnboardingScreen.tsx   # 14B
  - web/src/screens/BidBox*.tsx · Result*.tsx                                        # 14D
  - web/src/screens/BidBoxCard.tsx 의 `ActionForm`                                   # 13D 의 폼 — 워크시트는 쓰기만 한다
  - web/src/components/Toast.tsx           # 공유 토스트 — 13D 가 같은 자리에 미해소 항목을 둔다(§12.3 (x) ⓒ)
  - web/src/api/** · openapi/** · adapters/** · app/** · ml-engine/**                # 서버 변경 0
  - web/src/index.css · AppShell.tsx · routes.tsx                                    # 14A
  - .github/workflows/ci.yml               # acceptance 는 오늘의 web job 명령 그대로
  - package.json 의 dependencies           # 새 의존성 0
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew --no-daemon ktlintCheck detekt cpdCheck leakPatternGate   # evidence 가 누출 패턴 게이트의 스캔 뿌리에 든다 — evidence 편집 커밋의 HEAD 에서
rollback: rollback.md
```

## 받는 항목과 처분

| 항목 | 이 slice 의 처분 |
| --- | --- |
| 대조표 C 행 — 머리 블록(상태 알약 · 공고번호 · 제목 · 방식 · 기초금액) | **정렬 + 한 칸 데이터로 막힘** — 「방식」을 내는 칸이 `NoticeSummary` 에 없다(아래 착수 재실측) |
| 대조표 C 행 — 오른쪽 레일 | **정렬(이탈 둘)** — 카운트다운은 **날 단위**(OPEN ①), 자격은 **투찰가 응답이 있어야** 선다(OPEN ②) |
| 대조표 C 행 — 「참가 자격 n/n」 | **이탈(계약이 미리 판정)** — 분모가 선언된 표면이 없다. 「판정 + 미판정 축 목록」으로 그린다 |
| 대조표 C 행 — 본문 순서를 여정 순서로 | **정렬** — 투찰가 자리 → 「이 금액으로 진행」 → 나라장터 넘김 → 「투찰했나요?」 기록 |
| 대조표 C 행 — 「나중에 · 건너뜀」은 아래 | **정렬** |
| 대조표 C 행 — 「필드명 / 값 없음」 격자를 걷는다 | **정렬(한 줄 유지)** — 기초금액 줄은 `KeyValueRow` 로 **남는다**(아래 착수 재실측) |
| 13C 기능(복사 · 병기 · 하한 여유 · 확인 · 접기) | **옮기기만** — 거동 0, 13C 의 기존 단언 무편집 초록 |
| M13 잔여 §12.3 (x) ⓒ 「되돌릴 길 기억」 | **닫는다** — 기억을 **그려진 토스트에서 유도**한다(맞출 자리 0). 착수 때 적은 「입구를 하나로 모은다」보다 강한 기제로 갔고, 그 사유는 계약 갱신 (3) 에 있다 |
| 14A cr L-5 — 워크시트의 `1,234,000 ()` | **닫는다** |

처분의 근거와 잔여는 `checklist.md` 가, 착수 실측의 수치와 조건은 `commands.md` 가 정본이다.

## 착수 재실측 (계약 행 ↔ 코드)

| 계약이 말한 것 | 착수 실측 | 처분 |
| --- | --- | --- |
| 와이어프레임의 레일 = 카운트다운 · 자격 · 다음 확인 시점 · 타임라인 | **원천과 갈린다**: `design/Main.raw.html` 에서 카운트다운 카드는 **머리 행의 오른쪽**이고 「참가 자격」 카드는 **본문의 첫 카드**다. 레일(300px)에 있는 것은 다음 확인 시점 · 타임라인 · 「나중에·건너뜀」 셋이다 | OPEN ② (팀장 판정 요청) |
| 머리 블록의 「방식」 | `NoticeSummary` 의 칸은 번호 · 차수 · 상태 · 제목 · 수요기관 · 공고기관 · 기초금액 다섯 칸 · 마감이다. **낙찰 방식(적격심사 · 복수예가)을 내는 칸이 없다.** `BidCase.bidKind` 는 REAL/PAPER 이고 다른 축이다 | **데이터로 막힘** — 자리를 만들지 않는다(OPEN ③) |
| 레일의 「근거 원문」 | 응답에 공고 원문이 없다. 가장 가까운 칸은 `licenseRequirementSourceFields`(「요건이 온 필드」)이고, `licensePolicySource` 는 **2026-11-18 팀장 판정이 「그리지 않는다」로 닫은** 개발 식별자다 | 「요건이 온 필드」로 그린다(OPEN ②에 함께 보고) |
| 「필드명 / 값 없음」 격자를 걷는다 | 격자의 다섯 줄 가운데 **기초금액 줄만** 잠금이 걸려 있다 — 13C r4 L-1 의 test 가 `worksheet-amount` 의 `closest('dd')` 에서 `aria-describedby` 를 읽고 「추정가격과 다르다」가 그 안에 있음을 센다. 격자를 통째로 걷으면 그 test 가 붉고, 용어 힌트의 **터치 경로**(14A M13 잔여 ⓖ)와 터치 타깃 하나가 함께 사라진다 | 격자는 걷되 기초금액은 `KeyValueRow` 한 줄로 **남긴다** |
| 터치 타깃 하한 | `touch-target.spec.ts` 의 워크시트 자리는 `minCount: 8`(390px). 하한은 **올리기만** | 배치 뒤 계수를 재서 보고 — 줄면 착수 전 판정 대상 |
| 배치 게이트가 출하 레일을 자동으로 드는가 | **들지 않는다**: `e2e/layout.spec.ts` 는 `/` 한 자리만 열고, 그 레인에는 백엔드가 없어 워크시트가 오류 얼굴로 선다. 레일 축은 14A 의 **합성 마운트**가 든다(14A scope 계약 갱신 (2)) | OPEN ④ (팀장 판정 요청) |
| 「되돌릴 길 기억」의 자리 | `exitUndoable` 을 참으로 만드는 자리는 하나, **거짓으로 되돌리는 자리는 토스트 닫힘 하나**다. 그 사이에 토스트를 내는 자리가 여섯 더 있다(초안 · 기록 · 되돌림 · 담기 · 재전송 가드 둘) | **그려진 토스트에서 유도**해 닫는다(계약 갱신 (3) — 입구를 모으는 것보다 강하다: 맞출 자리 자체가 없다) |
| 자격·타임라인·다음 확인 시점의 전제 | 자격은 `requestBidPrice` 응답에서만 오고, 타임라인·다음 확인 시점은 **건이 입찰함에 있을 때만** 온다 | 레일의 각 카드가 자기 전제를 사유로 말한다(14A 부재 어휘) |

## 착수 OPEN의 판정 (팀장 2026-11-20)

| OPEN | 판정 | 이 slice 가 한 것 |
| --- | --- | --- |
| ① 카운트다운 단위 | **추천대로** — 지금 있는 두 값을 카드로 옮긴다(거동 0) | 분·시 축은 **새 OPEN** 으로 등재 |
| ② 레일 모집합 | **와이어프레임 원천을 따른다** | 카운트다운 = 머리 행 오른쪽 · 자격 = 본문 첫 카드 · 레일 = 다음 확인 시점 · 타임라인 · 내려놓기 |
| ③ 머리의 「방식」 | **자리를 만들지 않는다** | §12.3 에 **데이터로 막힘**, 대조표 C 행에 사유 |
| ④ 레일 상자 축 | **14C 는 여정 spec 을 건드리지 않는다** | 문서 순서를 vitest 로 잠그고, 상자 축은 **「14B 머지 전 레일 상자 미측정」** 으로 알려진 제한 |
| ⑤ `PageHeader` | **additive 확장 승인** | `h2` 에 제목 자리 표지 · 짝 test 를 같은 커밋에서 · 변이로 RED 실측 |

「되돌릴 길 기억」(§12.3 (x) ⓒ)과 빈 괄호(14A cr L-5)도 같은 자리에서 승인됐다.

## 착수 OPEN (팀장 판정 요청 — 위 표가 그 판정이다)

**① 마감 카운트다운의 단위.** 와이어프레임은 「1일 22시간 10분」이다. `timeDisplay` 가 내는 것은
**날 단위**(`daysUntil` → `dayCountText`)뿐이고, 그 모듈은 **14A 소유**다. 더 나아가 이 화면은
벽시계를 **한 번만 읽는다**(M9 D-1) — 분 단위 글자를 한 번 그리면 화면이 열려 있는 동안 조용히
낡는다. **추천: 지금 있는 두 값(KST 절대 시각 + D-day)을 레일 카드로 옮긴다**(거동 0). 분·시
축은 새 표시 축 + 틱 소스라 별도 결정으로 올린다.

**② 레일의 모집합 — 계약 문장과 와이어프레임 원천이 갈린다.** 원천은 카운트다운을 **머리 행
오른쪽**에, 「참가 자격」을 **본문 첫 카드**에 둔다. 거기에 더해 우리 자격 데이터는 **투찰가
응답이 있어야** 선다(요청 전에는 그릴 값이 없다) — 레일에 늘 서는 카드로 두면 화면의 절반이
「아직 묻지 않았음」만 말한다. **추천: 와이어프레임 원천을 따른다** — 카운트다운은 머리 행
오른쪽(390px 에서는 머리 아래), 자격은 본문 첫 카드(투찰가 응답이 있을 때), 레일은 다음 확인
시점 · 타임라인 · 「나중에·건너뜀」. 「근거 원문」은 자격 카드 안의 「요건이 온 필드」가 받는다.
계약 문장대로 넷을 레일에 넣으라 하면 그렇게 한다.

**③ 머리의 「방식」 칸.** 내는 표면이 없다(위 실측). **추천: 자리를 만들지 않는다**(「주어지지
않음」으로 그리면, 서버가 그 칸을 가진 적이 없는데 「이번 답에 빠졌다」로 읽힌다). §12.3 새 절에
**데이터로 막힘**으로 등재하고 대조표 C 행의 그 낱말에 사유를 단다.

**④ 출하 레일을 상자 좌표로 재는 자리.** 14A 의 레일 축은 합성 마운트다(그 slice 의 알려진
제한). 출하 워크시트 레일이 실제로 본문 오른쪽에 서는지는 **백엔드가 있는 여정 레인**에서만
잴 수 있다(스모크 레인은 백엔드가 없어 워크시트가 오류 얼굴이다). **추천: 여정 spec 이 이미
워크시트에 서 있는 그 자리에 두 칸 상자 단언 하나를 더한다**(1280px · 추가 왕복 0). 공유 파일을
열지 않는 쪽을 고르면, 레일의 **문서 순서**만 vitest 로 잠그고 상자 축은 알려진 제한으로 남는다.

## 승인 경계 (계약 「승인 경계」 절의 이 slice 몫)

- 서버 · OpenAPI · `api/generated` 변경 0. 새 의존성 0. 새 외부 호출 0.
- **기존 게이트 술어 변경 0.** 13C 의 기존 단언은 무편집이고, 새 배치의 주장은 **새 단언**으로
  더한다.
- **터치 타깃 하한의 계수가 줄어드는 변경**은 멈추고 보고한다.
- 사용자가 보는 **흐름의 순서**는 §6.1 상태 기계가 정하는 것을 바꾸지 않는다 — 이 slice 가
  옮기는 것은 **한 상태 안에서 카드가 서는 차례**다.
- 14A 원시 요소를 **넓혀야** 하면(인자 타입 · 얼굴 변종) 멈추고 보고한다.
- 표시 계약의 표(`bidBoxDisplay`·`caseDisplay`·`noticeDisplay`)를 바꾸지 않는다.

## base 확인

`git rev-parse 056f405c` · `git merge-base --is-ancestor 056f405c HEAD` 를 착수 커밋에서 기계로
확인했다(위 yaml 의 40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 계약 갱신 (구현 중 실측으로 드러난 것 — 팀장 보고 대상)

**(1) `WorksheetRecord.tsx` · `WorksheetTimeline.tsx` 는 편집 0 으로 끝났다.** 두 카드의 **자리**는
화면 모듈이 정하므로 카드 자신은 손대지 않았다. in_scope 에 열어 둔 것은 사실이고, 기계 diff 가
정본이라 위 yaml 에 `not_touched` 로 옮겨 적는다(복원 목록에도 들지 않는다).

**(2) 다음 행동 하나의 두 종류가 투찰가 카드의 「같은 자리」를 쓴다.** 종류마다 자리를 나누면
상태가 바뀔 때 React 가 버튼을 떼었다 다시 붙이고, 그 사이에 잡아 둔 노드를 누르면 **아무 일도
일어나지 않는다** — 13C 의 확인 단계 test 여섯이 이 라운드에서 실제로 그렇게 붉었다(실측). 자리를
하나로 두면 노드가 살아 있다. 와이어프레임도 그 버튼을 카드의 마지막 줄에 둔다.

**(3) 되돌릴 길의 기억을 effect 하나로 유도한다.** 처음에는 토스트를 내는 입구 하나에서 기억을
썼는데, 그러면 그 입구를 닫아 쥔 객체가 전부 「렌더 중 ref 접근」이 되어 **lint 가 여섯 자리를
들었다**(실측). 그래서 쓰는 자리를 effect 로 옮겼다 — 기억은 **그려진 토스트의 함수**이고 비동기
`onError` 가 「지금 그려져 있는 것」을 읽는다.

**(4) 390px 에서 카운트다운 카드가 제목을 꺾었다**(캡처 실측). 좁은 폭에서 카드를 한 줄 통째로
두어 제목이 먼저 한 줄을 쓰게 했다(차례는 그대로 · 별도 커밋).

## 갱신 이력

- 2026-11-20 초판(착수 보고와 함께).
- 2026-11-20 팀장 판정 ①~⑤ 등재 · 계약 갱신 (1)~(4).
