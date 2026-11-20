# M14 / wf-worksheet (14C) — slice 계약

```yaml
milestone: M14
slice: wf-worksheet
base_sha: 056f405c9e5659b9a068288f1104f9e04a6de016   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/src/screens/WorksheetScreen.tsx    # 머리 블록 · TwoColumn · 본문 순서 · 「되돌릴 길 기억」 · 빈 괄호
  - web/src/screens/WorksheetGrounds.tsx   # 자격 절을 레일이 함께 쓰도록 — 절 자체의 거동 0
  - web/src/screens/WorksheetRecord.tsx    # 넘김 카드 · 기록 폼의 자리 이동(거동 0)
  - web/src/screens/WorksheetTimeline.tsx  # 타임라인이 레일에 선다(거동 0)
  - web/src/screens/WorksheetScreen.test.tsx  # **새 배치의 새 단언만** 더한다 — 13C 의 기존 주장은 그대로
  - docs/discovery/capability-map.md       # §12.3 하위 절 하나 — 글자는 머지 차례에 정한다((aa) 이후)
  - reports/evidence/m14/wf-worksheet/
in_scope_pending_verdict:   # 팀장 판정 뒤에만 연다(착수 보고 OPEN ④)
  - web/e2e/journey/journey.spec.ts        # 출하 레일을 **상자 좌표로** 재는 단언 한 자리
out_of_scope:
  - web/src/components/**                  # 14A 원시 요소 — 쓰기만 한다(넓히려면 팀장 판정)
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
| M13 잔여 §12.3 (x) ⓒ 「되돌릴 길 기억」 | **닫는다** — 토스트를 내는 자리를 하나로 모아 기억이 늘 그 토스트의 함수가 되게 한다 |
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
| 「되돌릴 길 기억」의 자리 | `exitUndoable` 을 참으로 만드는 자리는 하나, **거짓으로 되돌리는 자리는 토스트 닫힘 하나**다. 그 사이에 토스트를 내는 자리가 여섯 더 있다(초안 · 기록 · 되돌림 · 담기 · 재전송 가드 둘) | 토스트 입구를 하나로 모아 닫는다 |
| 자격·타임라인·다음 확인 시점의 전제 | 자격은 `requestBidPrice` 응답에서만 오고, 타임라인·다음 확인 시점은 **건이 입찰함에 있을 때만** 온다 | 레일의 각 카드가 자기 전제를 사유로 말한다(14A 부재 어휘) |

## 착수 OPEN (팀장 판정 요청)

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

## 갱신 이력

- 2026-11-20 초판(착수 보고와 함께).
