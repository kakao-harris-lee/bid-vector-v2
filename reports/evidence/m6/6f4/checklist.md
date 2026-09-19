# M6/6F-4 — checklist.md

## 리뷰 요청 조건 (CLAUDE.md 기준)

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 경로들
      개별 인자>` 결과 없음(clean-tree, 아래 확인 명령). 양성 대조: `Sql.kt`에 임시로 한
      줄을 덧붙여 같은 명령이 `M` 한 줄을 잡는 것을 확인한 뒤 `head -n <원래 줄수>`로
      비파괴 절삭 복원(`checkout --` 미사용).
      ```
      git status --porcelain -- \
        adapters/src/main/resources/db/migration/V11__notice_title.sql \
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
        strategy/src/main/kotlin/bidvector/strategy/Text.kt \
        strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt \
        reports/evidence/m6/6f4/
      ```
      결과: 빈 출력(`checklist.md`·`rollback.md` 커밋 전 시점 기준 — 이 둘을 커밋한 뒤
      재확인한다).
- [x] scope.md의 acceptance_commands 전건이 exit 0으로 `commands.md`에 기록됨 — HEAD
      `6f8466f1`(base_sha 갱신값 `ede5d5b`) 기준.
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

## 수정 라운드가 만든 새 파일 ↔ in_scope 대조

착수 계약(`37b837e`)에는 없었고 팀장 계약 갱신 (2)(`830252d`)에서 `procurement/`·`strategy/`가
in_scope에 편입된 뒤 이 구현 라운드가 실제로 만든 신설 파일 셋:

| 신설 파일 | in_scope 등재 근거 |
| --- | --- |
| `procurement/src/main/kotlin/bidvector/procurement/NoticeTitle.kt` | scope.md in_scope 「`procurement/` — canonical fact와 조립 커맨드의 공고명 슬롯(기본값 null, D-6F4-9)」 |
| `strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt` | scope.md in_scope 「감시 텍스트 조립 순수 함수와 그 test」 |
| `adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeFindRoundTripTest.kt`(신설 아님, 기존 파일 수정) | scope.md in_scope 「`CleanMigrationColumnTest.kt`·`CleanMigrationCheckTest.kt`·`NoticeReconstructionTest.kt` **등** 스키마 대조 test」의 「등」이 포괄 — 값 왕복 test는 기존에 이 파일이 담당하던 자리다 |

셋 모두 계약 문면 안에 있다 — scope.md 갱신 없이 조용히 넓어진 파일은 없다.

## clean-tree 재확인 (evidence 커밋 이후)

`checklist.md`·`rollback.md` 커밋 뒤 `git status --porcelain -- reports/evidence/m6/6f4/`가
빈 출력이어야 한다 — verifier가 재확인한다(이 문서 자신이 자기 커밋 이후 상태를 담을 수
없다는 것은 evidence-pack 스킬의 「낡는 좌표」 절과 같은 이유다).
