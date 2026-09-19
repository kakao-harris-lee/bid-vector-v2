# M6/6F-4 — checklist.md

## 리뷰 요청 조건 (CLAUDE.md 기준)

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 경로들
      개별 인자>` 결과 없음(clean-tree, 아래 확인 명령). 양성 대조: `Sql.kt`에 임시로 한
      줄을 덧붙여 같은 명령이 `M` 한 줄을 잡는 것을 확인한 뒤 `head -n <원래 줄수>`로
      비파괴 절삭 복원(`checkout --` 미사용).
      ```
      git status --porcelain -- \
        adapters/src/main/resources/db/migration/V14__notice_title.sql \
        adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
        adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRow.kt \
        adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRowMerge.kt \
        adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeReconstruction.kt \
        adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcNoticeRepository.kt \
        adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
        adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
        adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeReconstructionTest.kt \
        adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeFindRoundTripTest.kt \
        procurement/src/main/kotlin/bidvector/procurement/NoticeTitle.kt \
        procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt \
        procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt \
        procurement/src/test/kotlin/bidvector/procurement/NoticeTitleTest.kt \
        strategy/src/main/kotlin/bidvector/strategy/Text.kt \
        strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt \
        reports/evidence/m6/6f4/
      ```
      결과: 빈 출력(수정 라운드 산출물 커밋 전 시점 기준 — evidence 커밋 뒤 재확인한다).
- [x] scope.md의 acceptance_commands 전건이 exit 0으로 `commands.md`에 기록됨 — HEAD
      `15da367f`(base_sha 2차 갱신값 `edeaa9a3`) 기준, verifier r2·code-reviewer 수정 라운드
      + 팀장 지적 넷(CHECK 범위·main 2차 흡수·V11→V14 재번호·CHECK 본문 등식) 정정 뒤 재실측.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — 부분 게이트가 아니라
      `./gradlew --no-daemon check`(Kotlin `check` job 전건) + `./tools/one-command-check.sh`
      (Python `ml-engine` job 포함) 전건.
- [x] 변경된 fixture와 정책 version의 근거 — 이 slice는 fixture·정책 값을 바꾸지 않는다
      (스키마 열 추가 + 순수 조립 함수, 정책 표 무변경). fixture 표본 부재 자체가
      scope.md 실측 5(공고명 표본 0건)의 근거이고 D-6F4-4b가 그 실측을 결정으로 옮긴다.
- [x] 알려진 제한과 rollback 방법 — 아래 「알려진 제한」과 `rollback.md`.
- [x] 비밀값 스캔 통과 — `commands.md` 「비밀값 스캔」 절, exit 1(매치 없음).

## 알려진 제한

1. **`OPEN-6F4-TITLE-WIRING`** — `assembleKeywordScopeText`/`assembleFullScopeText`
   (`strategy/Text.kt`)의 실 호출자가 없다. `WatchSubjectPort`(`workflow/evaluation/Ports.kt`)의
   구현체도 여전히 test fake뿐이다(이 slice 착수 전부터 그랬다 — 확인만 했고 바꾸지 않았다).
   수집→canonical 공고명 배선과 함께 후속 slice가 가져간다(D-6F4-4b·6, scope.md).
2. **`OPEN-6F4-STR02-REQUIREMENTS-AXIS`** — capability-map STR-02 acceptance 둘째 줄(「같은
   키워드가 제목 **또는 요건**에 있으면 후보」)의 뒷절에 대응하는 데이터 경로가 V2에 없다
   (D-6F4-3b — `qualification_text`는 면허제한 오퍼레이션 응답이라 업무 키워드가 아니다).
   요건 원문을 싣는 축이 생기면 다시 본다.
3. **`OPEN-6F4-NOTICE-BODY-SOURCE`** — 공고 본문은 목록 응답에 없고 상세 URL 뒤에 있다
   (D-6F4-2·5). 이 slice는 본문 열을 만들지 않는다.
4. **`notice_title` 운영 표본 0건** — scope.md 실측 5. 백필 문제 자체가 성립하지 않는다
   (D-6F4-4).
5. **`KeywordScopeText`·`FullScopeText`는 공개 생성자를 가진 `data class`라 조립 함수
   (`assembleKeywordScopeText`/`assembleFullScopeText`)를 우회할 수 있다**(D-6F4-3c, verifier
   r2 MEDIUM-3 실측 — 요건 텍스트로 `KeywordScopeText`를 직접 만들어 필수 키워드를 만족시키는
   test가 초록이었다). `NoticeTitle`과 달리 이 두 타입은 이 slice가 만든 것이 아니고(base부터
   공개 생성자, 호출부 13곳 이상) 오늘 production 생성 지점은 조립 함수 하나뿐이다. 두 타입의
   생성 지점이 `strategy/src/main` 밖에 54곳이라 이 slice 범위를 넘는다 — 실 호출자가 붙는
   배선 slice(`OPEN-6F4-TITLE-WIRING`)가 test fake까지 함께 옮기며 `NoticeTitle`과 같은 경계
   (비공개 생성자 + 팩토리)로 닫는다.

## 수정 라운드가 만든 새 파일 ↔ in_scope 대조

착수 계약(`37b837e`)에는 없었고 팀장 계약 갱신 (2)(`830252d`)에서 `procurement/`·`strategy/`가
in_scope에 편입된 뒤 이 구현 라운드(과 그 뒤 수정 라운드)가 실제로 만든 신설 파일 넷:

| 신설 파일 | in_scope 등재 근거 |
| --- | --- |
| `procurement/src/main/kotlin/bidvector/procurement/NoticeTitle.kt` | scope.md in_scope 「`procurement/` — canonical fact와 조립 커맨드의 공고명 슬롯(기본값 null, D-6F4-9)」 |
| `strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt` | scope.md in_scope 「감시 텍스트 조립 순수 함수와 그 test」 |
| `adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeFindRoundTripTest.kt`(신설 아님, 기존 파일 수정) | scope.md in_scope 「`CleanMigrationColumnTest.kt`·`CleanMigrationCheckTest.kt`·`NoticeReconstructionTest.kt` **등** 스키마 대조 test」의 「등」이 포괄 — 값 왕복 test는 기존에 이 파일이 담당하던 자리다 |
| `procurement/src/test/kotlin/bidvector/procurement/NoticeTitleTest.kt`(수정 라운드 신설, review MEDIUM) | scope.md in_scope 「`procurement/` — canonical fact와 조립 커맨드의 공고명 슬롯」이 담당 타입(`NoticeTitle`)의 test — `NoticeTitle.kt`와 같은 근거 |

넷 모두 계약 문면 안에 있다 — scope.md 갱신 없이 조용히 넓어진 파일은 없다.

## clean-tree 재확인 (evidence 커밋 이후)

`checklist.md`·`rollback.md` 커밋 뒤 `git status --porcelain -- reports/evidence/m6/6f4/`가
빈 출력이어야 한다 — verifier가 재확인한다(이 문서 자신이 자기 커밋 이후 상태를 담을 수
없다는 것은 evidence-pack 스킬의 「낡는 좌표」 절과 같은 이유다).

## 크기 게이트 재실측(verifier r2 LEDGER-1·review LOW 뒤)

r1 시점 evidence 351줄 대 산출물 315 insertions(+11 삭제, 326줄 변경)로 **위반**이었다. 최종
수치(HEAD `15da367f`, base `edeaa9a3`):

- **evidence**: `wc -l reports/evidence/m6/6f4/*.md` = scope 168 + checklist·commands·
  rollback 나머지 ≈ **519줄**.
- **산출물**: `git diff --shortstat edeaa9a3..15da367f -- . ':!reports/evidence'
  ':!milestone-6.md' ':!.claude'` = 16 files changed, **540 insertions(+), 22 deletions(-)**
  (562줄 변경, insertions만 비교해도 540).

evidence(519) < 산출물(540, 562) — **게이트를 충족한다.**

## V14 CHECK 범위 정정(팀장 2차 지적, 1차 수정)

verifier r2 MEDIUM-1 처방으로 넣은 첫 CHECK 정규식이 V4 선례보다 **좁았다** — 편집 도구가
유니코드 이스케이프 표기를 실제 제어문자로 치환해 버려, 의도한 TAB부터 CR까지(코드포인트
9-13) 범위가 TAB·LF 둘(9-10)로 줄어 VT(11)·FF(12)·CR(13)이 빠졌다. 팀장이 바이트 단위로
디코드해 재지적했다. 정정 방법: V4 파일의 정규식 텍스트를 프로그램으로 그대로 읽어 V14에
재사용(사람이 직접 타이핑하지 않음) — 결과가 V4 bracket 표현과 완전히 동일한 텍스트임을
diff 0으로 확인했고, 파일에 원시 제어문자가 없음도 확인했다. 회귀 test도 손으로 고른 표본
대신 Kotlin의 공백 판정 함수가 참인 코드포인트 전부를 BMP에서 실측으로 유도하도록 다시 써
같은 종류의 누락이 재발할 수 없게 했다. 사후 검증: 옛 좁은 CHECK로 되돌리면 이 새 test가
코드포인트 11(VT)에서 정확히 실패하는 것을 확인했다(`commands.md` 참고).

## main 2차 흡수 · V14 재번호 · CHECK 본문 등식(팀장 지적 셋, 2차 수정)

- **㉮ main 2차 흡수(`edeaa9a3`, 6F-6)**: 팀장이 미리 알려준 다섯 자리(`CleanMigrationTest.kt`·
  `CleanMigrationColumnTest.kt`·`CleanMigrationCheckTest.kt`·`PersistenceTestSupport.kt`·
  `gate-tests.properties`) 전부 자동 병합됐고(수동 충돌 없음), `milestone-6.md`만 수동
  충돌해 두 슬라이스의 문단을 순서대로 이어 붙였다. 「둘 다 취한다」를 grep으로 실측 확인
  (`rollback.md` 표) — 6F-6의 `operator_profile` 계열이 한 줄도 지워지지 않았다.
- **㉯ V11 → V14 재번호**: main에 이미 V12(6F-6)가 있고 V13(PR #39)이 병합 대기라
  `git mv`로 재번호했다. **자기 실측**: 첫 커밋이 옛 경로를 pathspec에서 빠뜨려 삭제가
  누락된 것을 `git ls-tree HEAD`로 발견하고 후속 커밋으로 정정했다(`commands.md` 참고) —
  워킹트리는 처음부터 V14 하나였으므로 코드 동작 영향은 없었다.
- **㉰ CHECK 본문 등식**: `notice_notice_title_check`의 `pg_get_constraintdef` 원문을
  `outbox_state_check`·`edit_session_state_check`와 같은 관례로 `shouldBe`로 고정하는 test를
  `CleanMigrationCheckTest.kt`에 추가했다. 사후 검증으로 이번 라운드에서 실제로 두 번 안
  잡혔던 문자 클래스 축소를 이 test가 즉시 잡는 것을 확인했다(`commands.md` 참고).
