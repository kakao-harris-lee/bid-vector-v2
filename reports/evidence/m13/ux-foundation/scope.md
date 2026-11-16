# M13 / ux-foundation (13B) — slice 계약

```yaml
milestone: M13
slice: ux-foundation
base_sha: a338b55bd10625b68cfe867d6cab688b2adc3270   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/src/components/                 # 공유 Button·Card 신설 + 기존 넷의 버튼·링크 이관
  - web/src/AppShell.tsx                # 하단 탭 · 상단 줄 · 안 읽음 배지 · 로그아웃 · 문서 제목 · 포커스
  - web/src/LoginScreen.tsx             # 색 토큰화 · 로그인 뒤 원래 자리 복귀
  - web/src/RouteError.tsx              # 색 토큰화만(문면은 13A)
  - web/src/ScreenPlaceholder.tsx       # 색 토큰화만(문면은 13A)
  - web/src/routes.tsx                  # 탐색 분류 필드(문자열 0 — 라벨 문면은 13A)
  - web/src/screens/                    # 버튼·카드 마크업 이관(모양만) + 그 선택자를 보는 test
  - web/src/api/approvedOperations.ts   # 승인 쌍 +1 (DELETE /api/session)
  - web/src/api/queries.ts              # signOut() — 쌍과 호출이 같은 커밋
  - web/src/api/queryKeys.ts            # 셸 배지의 질의 키(X-Unread-Count)
  - web/src/index.css                   # md 중단점이 토큰을 요구할 때만
  - web/e2e/journey/                    # 터치 타깃 게이트 spec 신설(+ 필요한 공통 helper)
  - web/config/journey-specs.txt        # 그 spec 을 **같은 커밋에서** 등재
  - web/config/gate-tests.txt           # 새 vitest 게이트 test 파일 등재
  - docs/discovery/capability-map.md    # §12.3 하위 절 하나 — 글자는 머지 차례에 정한다
  - reports/evidence/m13/ux-foundation/
out_of_scope:
  - web/src/display/**                  # 표시 문면·값 — 13A·13C 의 축
  - web/src/components/StateBadge.tsx 의 2층 라벨 거동   # 13E(터치 펼침). 13B 는 이 파일의 버튼·카드 마크업만
  - web/src/screens/Worksheet*.tsx 의 새 기능          # 13C(복사·한글 병기·확인·하한 여유·접기)
  - web/src/screens/BidBoxCard.tsx 의 새 전이          # 13D(/defer·/skip·실행 취소)
  - web/src/screens/TodayNoticesScreen.tsx 의 목록 순서·칩  # 13E
  - 서버(Kotlin)·openapi/**             # 백엔드 변경 0 (승인 경계)
  - package.json 의 dependencies        # 새 UI 의존성 0 (승인 경계)
  - adapters/** · app/** · ml-engine/** # 이 slice 는 web 만 만진다
  - .github/workflows/ci.yml            # acceptance 는 오늘의 web job 명령 그대로
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew check   # evidence 가 누출 패턴 게이트의 스캔 뿌리에 들어 경로가 닿는다 — evidence 편집 커밋의 HEAD 에서
rollback: rollback.md
```

## 받는 항목과 처분

| # | 항목 | 이 slice 의 처분 |
| --- | --- | --- |
| 5 | 모바일 사용감이 약하다 | 착수 보고의 재측정과 설계 검토 판정으로 고정한다 |
| 6 | 공유 버튼·카드가 없다 | 같음 |
| 10 | 셸과 세션 | 같음 — 「로그인 뒤 원래 자리」의 뜻은 팀장 답이 정한다(착수 보고 ⓑ) |

처분 문면은 팀장 답을 받은 뒤 이 절에 고정한다(범위 밖 판정은 팀장만 낸다). 착수 실측의 수치와
그것을 낸 조건은 `commands.md` 가 정본이고 여기 옮겨 적지 않는다.

## 승인 경계 (계약 「승인 경계」 절의 이 slice 몫)

- **승인 쌍 +1**(`DELETE /api/session`)은 계약이 미리 승인했다(13B 행 「로그아웃(`DELETE /api/session`
  승인 쌍 추가)」). 그 밖의 쌍 증가는 멈추고 보고한다.
- **터치 타깃 게이트가 13B 이관 뒤에도 남는 작은 버튼을 찾으면** 멈추고 보고한다(계약 승인 경계).
  자기 소유 자리를 고쳐 초록으로 만드는 것은 이 slice 의 일이다.
- 서버·OpenAPI 변경, 새 의존성, 흐름 순서 변경은 멈추고 보고한다.
- 다른 slice 소유 파일의 **문면**을 바꾸지 않는다 — 13B 가 그 파일을 열더라도 바꾸는 것은
  마크업·색·구조뿐이다.

## base 확인

`git rev-parse a338b55b` · `git merge-base --is-ancestor a338b55b HEAD` 를 착수 커밋에서 기계로
확인했다(위 yaml 의 40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 계약 갱신

(착수 보고에 대한 팀장 답과 설계 검토 판정 뒤에 적는다.)

## 갱신 이력

- 2026-11-16 초판(착수 보고와 함께).
