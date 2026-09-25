# M6/6F-9 commands

명령과 종료 코드만 남긴다(출력 전문 없음 — 감사자는 명령을 다시 돌린다). 실측은 전부 **버릴 worktree·임시 clone**에서 했고 `base` 는 `git merge-base HEAD origin/main`(= `f6ebc047`, PR #45 머지 커밋)이다.
산출물 실측 HEAD 는 `rollback.md` 첫머리가 적는다. **마지막 evidence HEAD 의 `check` 결과 정본은 verifier 와 완료 보고다**(evidence 가 자기 마지막 커밋의 post-state 를 담을 수 없다).

## acceptance — CI job 명령 그대로 (캐시 우회, 버릴 worktree)

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `./gradlew --no-daemon check --rerun-tasks` | 0 | BUILD SUCCESSFUL · 348 태스크 전부 실행(캐시 0) |
| `./gradlew --no-daemon qualityBaseline` | 0 | BUILD SUCCESSFUL |
| `./tools/one-command-check.sh` | 0 | Kotlin·Python 두 job 초록(pytest 958 passed + 1 passed) |

실측은 `bfbdef1d`(r1 의 마지막 산출물 커밋, merge-base `f6ebc047`)의 버릴 worktree 에서 2026-09-25 에 했다 — 아래 되돌림 실측도 같은 worktree 다. 세 명령은 앞 라운드에서도 같은 exit 0 였다(앞 실측을 옮기지 않고 이 HEAD 에서 다시 돌린 값이다).

## RED 실측 — 구현 전에 실패를 본 새 test

| test | 실측 |
|---|---|
| `BusinessDivisionTest` · `WatchCategoriesAssemblyTest` | 구현 전 컴파일 실패(`Unresolved reference`) — 타입·커널이 없다 |
| `NoticeWatchSubjectPortTest` 새 셋 | 구현 전 행동 수준 FAILED 3(집합·공사 텍스트 조각) → 포트가 커널을 부른 뒤 초록 |
| **r1** 대분류 값 획득 축 게이트 | 위반 표본 셋만 심고 **바꾸기 전 게이트**(멤버 열거 술어)로 음성 단언을 돌렸다: `:app:test --tests *CollectionArchitectureGateCatchesViolationsTest` exit 1 — enum 상수(MV1)·`Enum.valueOf`(MV4) 표기를 잡지 못한다(FAILED 2). 새 술어 셋으로 바꾼 뒤 초록 |
| **r1** `RequestCategoryCodeWireTest`·`OpportunitySampleSupplyDivisionTest` | 구현(잠금) 전에는 class 자체가 없었다 — 잠금 세기는 아래 변이 MLW 가 잰다(잠금 제거 = 초록 확인은 무의미하므로 운반 단계 변이로 잰다) |

나머지 새 test 는 구현과 같은 커밋에서 처음 초록으로 돌았다 — 그 잠금은 아래 변이가 잰다(수정 전 초록 → 적용 후 RED).

## 변이 실측 — 각 변이는 적용 전 `git diff --numstat` 로 확인, 실행 뒤 `git checkout -- .` 로 원복, 버릴 worktree

**수정 전(변이 적용 전 트리) 아래 test 는 전부 초록**이었다(같은 명령, exit 0).

| 변이 | 적용(+/−) | 명령 | exit | RED 된 test |
|---|---|---|---|---|
| M1a 대분류 매핑 제거 — `canonicalize` 가 관측의 대분류를 옮기지 않음 | +1/−1 | `:procurement:test --tests *BusinessClassificationCanonicalizeTest` | 1 | 용역 항목 · 「대분류는 수집 오퍼레이션 값만 쓴다」 · 공사 항목 |
| M1b 대분류 매핑 제거 — 어댑터가 관측에 싣지 않음 | +1/−1 | `:adapters:test --tests *KonepsSourceDivisionTest` | 1 | 셋 전부(넷 모두 · URL 경로 · 응답 라벨) |
| M1c 대분류를 URL 경로 문자열로 지음(생성 인자를 폴백으로 남긴 채) | +1/−1 | `:app:test --tests *CollectionArchitectureGateTest` · `:adapters:test --tests *KonepsSourceDivisionTest` | 1 | 게이트 둘(「문자열에서 대분류를 만드는 호출은 허용 쌍뿐이다」 · 「허용 쌍은 관측과 같다」). **행동 test 는 초록** — 폴백이 값을 지켜 못 잡는다; 이 변이를 잡는 것은 호출 그래프 게이트뿐 |
| M2 용역구분을 업무구분 라벨 칸에 섞음 | +1/−1 | `:procurement:test --tests *BusinessClassificationCanonicalizeTest` | 1 | 용역 항목 · 라벨 = 분류명뿐 · 같은 원천 쌍 · 계약 키 재지정 |
| M3 주공종을 코드 칸에(코드를 지어냄) | +1/−0 | 위와 같음 | 1 | 「공사 항목 — 주공종은 이름만 싣고 코드를 지어내지 않는다」 |
| M4a V17 쓰기 누락 — `UPDATE_NOTICE` 의 `SET` 과 바인딩에서 `main_construction_type` 을 함께 뺌 | +1/−1 · +1/−1 | `:adapters:test --tests *NoticeBusinessClassificationPersistenceTest` | 1 | 「갱신 — … 열마다 따로」 · 「갱신 — 값이 바뀌면 새 값이 이긴다」 |
| M4b 병합 존재 가드에서 `service_division` 을 뺌 | +1/−1 | 위와 같음 | 1 | 같은 두 test |
| M4c 바인딩 순서에서 `service_division` 과 `main_construction_type` 을 뒤바꿈(보조) | +1/−1 | 위와 같음 | 1 | 여섯(삽입·갱신 둘·존재 가드·공사·복원) |
| M5 감시 집합에 빈 값 — 커널이 빈·공백 값을 걸러내지 않음 | +1/−1 | `:strategy:test --tests *WatchCategoriesAssemblyTest` | 1 | 「빈 값이 섞여도 …」 · 「빈 값과 공백류만 … 경계 표본 전수」 |
| M6 키 리터럴을 어댑터에 삽입 | +2/−0 | `:app:test --tests *CollectionArchitectureGateTest` | 1 | 「원시 키 리터럴은 계약 행을 실은 파일 클래스 밖 … 없다」 · 「허용 클래스는 관측과 같다」 |
| **MV1**(r1) 배선이 경로에서 enum 상수로 대분류를 지음(설정 값 무시) | +10/−1 | `:app:test --tests *CollectionArchitectureGateTest` | 1 | 셋(위반 규칙 · 타입 멤버 쌍 == 관측 · 값 획득 쌍 == 관측) |
| **MV2**(r1) 타입 companion 에 파생 멤버를 더하고 배선이 그것을 부름 | +3/−0 · +10/−1 | 위와 같음 | 1 | 같은 셋 — 멤버 이름을 열거하지 않으므로 새 이름도 쌍이 된다 |
| **MV4**(r1) `java.lang.Enum.valueOf(BusinessDivision::class.java, …)` | +10/−1 | 위와 같음 | 1 | 셋(위반 규칙 · 타입 멤버 쌍 · **클래스 객체 참조자 == 허용**) |
| **MLW**(r1) `RequestMapping` 의 업종 코드 fact 를 늘 결측으로 | +1/−1 | `:adapters:test --tests *RequestCategoryCodeWireTest` · `:app:test --tests *CollectionArchitectureGateTest` | 1 | 「(가) 용역 공고의 공공조달분류 번호가 … 그대로 실린다」(앞 판에서는 이 변이가 초록이었다 — verifier r1 F-2) |

## 정적 확인

| 확인 | 명령 | 결과 |
|---|---|---|
| in_scope 대조(A·M) | `git diff --name-status <base>..HEAD` 각 경로를 `scope.md` in_scope 항목 glob 에 대조(일회용 스크립트) | 밖 **0**. r1 이 더한 신규 여섯도 대조했다(게이트 규칙 파일 하나·표본 셋·`adapters/.../ml` test 하나·`workflow/.../evaluation` test 하나 — 앞 둘은 기존 glob, `ml` test 는 r1 계약 갱신이 더한 항목) |
| r1 게이트 술어 교체 흔적 | `grep -rn 'division-parse' --include=*.kt --include=*.properties .` | 0 — 낡은 정책 키 이름이 코드·정책 파일에 남지 않았다(evidence 문서는 교체 사실 자체를 적으므로 대상 밖) |
| 관심 업종 커널의 production 호출자 | `grep -rn "assembleWatchCategories(" --include=*.kt adapters/src/main workflow/src/main app/src/main` | 포트 한 곳 |
| 설정 행 생성자 | `grep -rn "KonepsOperationProperties(" --include=*.kt app/src/main` | 기본 표 두 행뿐(나머지는 바인더) |
| raw 행을 읽어 정규화를 재생하는 코드 | `grep -rn "FROM raw_observation" --include=*.kt adapters/src/main` | 0 |
| 역방향 `file:line`(편집한 승인 문서) | `grep -rn 'data-dictionary\.md:[0-9]' --include=*.md --include=*.kt --include=*.properties .` · `grep -n '^#### 6.3.4' docs/discovery/data-dictionary.md` | 인용 최대 행 < 삽입 지점 — 밀린 인용 0. `policy-values.md` 의 `file:line` 인용 0 |
| 하네스 레인 | `git log --oneline <base>..HEAD -- CLAUDE.md .claude/` | 빈 출력(`scope.md` 「하네스 레인 변경」 = 없음 유효) |
| 비밀값 스캔 | `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m6/6f9/` · 같은 패턴을 `git diff <base>..HEAD -U0` 의 추가 줄에 | evidence exit 1(매치 없음). 추가 줄 매치 1건 = 기존 관례의 test 상수(`shouldNotContain` 대상 변수)이고 비밀값 아님. 육안: 키·개인정보·공고 원문 없음 |
| clean-tree | `git status --porcelain -- <in_scope 개별 인자>` | 빈 출력 + 양성 대조 1회(줄 하나 덧붙여 1건 잡힘 확인 → `head -n` 절삭으로 복원, `checkout --` 미사용) |

## 되돌림 실측 — 임시 clone (`rollback.md` ①~⑥)

| 단계 | 명령 | exit·결과 |
|---|---|---|
| ① restore | `git restore --source=<base> --staged --worktree -- <목록 60개>` | 0 · 삭제 18 · 변경 42 |
| ② 공유 파일 hunk 격리 | `git diff <sha>~1..<sha> -- <파일> \| git apply -R`(최신 → 과거) | 열두 번 전부 0, conflict 0 |
| ③ 트리 동일성 | `git diff <base> -- <목록>` · 공유 파일 넷 · `git diff --name-only <base>` 전체 | 앞 둘 빈 출력, 전체에서 남는 것은 `milestone-6.md` 와 이 slice 의 evidence 뿐이다(둘 다 restore 대상 아님) |
| ④ compile | `./gradlew --no-daemon compileKotlin compileTestKotlin` | 0 |
| ⑤ test | `./gradlew --no-daemon test` | 0 |
| ⑥ 게이트 | `./gradlew --no-daemon check` | 0 |
| 적용된 DB 갈래 | 임시 Postgres 에 V1~V17 적용 → `ALTER TABLE notice DROP COLUMN` 셋 | 0 · 열 3 → 0 · `notice` CHECK 16 → 13 |
