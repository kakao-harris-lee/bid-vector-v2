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
      `e47901ac`(base_sha 3차 갱신값 `128f9cd3`, main 흡수 — 6F-5-a·V13) 기준, 충돌
      해소(`CleanMigrationCheckTest.kt`, 「둘 다 취한다」) 뒤 재실측.
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
6. **이 브랜치의 커밋 `7e6ead90`은 단독 체크아웃하면 깨진다**(verifier LOW-1 실측) —
   `V11__notice_title.sql`·`V14__notice_title.sql`이 그 커밋에서 동시에 존재해 Flyway가
   같은 `ADD COLUMN notice_title`을 두 번 실행, `column "notice_title" of relation "notice"
   already exists`로 adapters DB test 8건이 전건 실패한다. 최종 HEAD와 병합 결과에는
   영향이 없다(다음 커밋에서 정정됨, `commands.md` 참고) — `git bisect`가 이 range를
   지날 때만 주의가 필요하다.

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

## 크기 게이트 재실측(경로 기준, HEAD `e47901ac`/base `128f9cd3`)

- **evidence**: `find reports/evidence/m6/6f4/ -type f | xargs wc -l` = scope 176 +
  checklist 104 + commands 72 + rollback 108 = **460줄**.
- **산출물**: `git diff --shortstat 128f9cd3..e47901ac -- . ':!reports/evidence'
  ':!milestone-6.md' ':!.claude'` = 16 files changed, **540 insertions(+), 22 deletions(-)**
  (562줄 변경).

evidence(460) < 산출물(540, 562) — **게이트를 충족한다.** 여유 80줄(insertions 기준).

evidence(533) < 산출물(540, 562) — **게이트를 충족한다.** 여유는 insertions 기준 7줄,
총 변경 기준 29줄 — r2 대비 좁아졌다(라운드 이력 절 제거로 evidence가 줄었어야 할 자리에
사실 정정 서술이 그만큼 늘어서 순변화는 작다). **여유가 한 자릿수라 다음 라운드는 이
gate를 다시 재기 전에는 evidence를 늘리지 않는다.**
