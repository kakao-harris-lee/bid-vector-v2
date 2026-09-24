# M6/6F-8 commands

명령과 종료 코드만 남긴다(출력 전문 없음 — 감사자는 명령을 다시 돌린다). 실측은 전부 **버릴 worktree**(`git worktree add --detach`)에서 했고 `base` 는 `git merge-base HEAD origin/main`(= `74616367`,
PR #44 머지 커밋). 산출물 실측 HEAD 는 `rollback.md` 첫머리와 아래 각 표가 적는다. **마지막 evidence HEAD 의 `check` 결과 정본은 verifier 와 완료 보고다**(evidence 가 자기 마지막 커밋의 post-state 를 담을 수 없다).

## acceptance — CI job 명령 그대로 (캐시 우회, 버릴 worktree)

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `./gradlew --no-daemon check --rerun-tasks` | 0 | BUILD SUCCESSFUL, 전 task 재실행, test 전건 초록(skip 은 전부 기존 `RealServerIntegrationTest`, 이 slice 무관) |
| `./gradlew --no-daemon qualityBaseline` | 0 | BUILD SUCCESSFUL |
| `./tools/one-command-check.sh` | 0 | 「Kotlin 전건 + Python 전건 통과」 |

실측 트리 = 산출물 `b10ba803`(수정 라운드 2 — D-6F8-11·13 반영, 이 slice 의 마지막 산출물 커밋) + 이 evidence 파일들(트리 동일성 — 파일 셋을 그대로 얹은 버릴 worktree)이다. `check --rerun-tasks` 는 348 task 전부 실행(348 executed). evidence 편집 커밋마다 그 HEAD 에서 `check` 를 다시 돌리고(누출 스캔 어휘 재귀 방지), 마지막 HEAD 의 결과 정본은 verifier 와 완료 보고다.

실패 이력(검증이 작동했다는 증거): 같은 acceptance 를 산출물 커밋 `c5c13fde` 에서 돌렸을 때 `check` exit 1 — `:app:sizeGate` 함수 50줄 한도(첫 `dependencies {}` 람다 52줄).
별도 블록으로 옮긴 `66589eda` 에서 위 셋 exit 0.
수정 라운드 2: `:procurement:ktlintCheck` 가 산출물 `9d63f922` 의 새 test 파일에서 exit 1(서식 — 줄 길이·개행) → 서식 커밋 `b10ba803` 에서 exit 0.

## 변이 실측 (나) — 검토 뒤 수정분 (각 변이는 적용 전 `git diff --numstat` 로 확인, 실행 뒤 `git reset --hard` 로 원복, 버릴 worktree)

**수정 전 초록 재현** — 수정 전 트리(`ea67a8c5`)에서 아래 변이는 전부 통과했다(게이트·test 의 사각):

| 변이 | 적용(+/−) | 명령 | exit |
|---|---|---|---|
| M1 use case 가 이웃 패키지 `workflow.collectionx` 의 헬퍼(`RawNoticeObservation` 확장, 계약 경유 `valueOf`)를 부름 | +2/−0 · 새 파일 +8 | `:app:test --tests bidvector.app.architecture.* :workflow:test` | 0 |
| M4a 배선이 `System.err.println` 으로 URL 인코딩된 키를 출력 | +1/−0 | `:app:test --tests *CollectionRunnerE2ETest* --tests bidvector.app.architecture.*` | 0 |
| M5 러너의 「오늘」 기준을 `OPENING_DATE_ZONE`(KST) 에서 UTC 로 | +1/−1 | `:app:test --tests *CollectionWiringTest* --tests *CollectionRunnerE2ETest*` | 0 |

**수정 후 RED**(산출물 `d6d8d0a0` — I2·G4·G5 는 `45904275`, 그 사이는 `CanonicalizeNeverThrowsTest` 적대적 값 표 네 줄뿐):

| 변이 | 적용(+/−) | 명령 | exit | RED 된 test |
|---|---|---|---|---|
| M1 (위와 같음) | +2/−0 · 새 파일 +8 | `:app:test --tests bidvector.app.architecture.* :workflow:test` | 1 | `CollectionArchitectureGateTest` 「원문 키 접근 타입 참조와 통과 전용 멤버 접근은 … 모듈 전체」 · 「허용 참조자·접근자 집합은 관측 집합과 같다」 |
| M4a (위와 같음) | +1/−0 | `:app:test --tests *CollectionRunnerE2ETest* --tests bidvector.app.architecture.*` | 1 | E2E 「로그에는 서비스 키도 … 표준 출력·표준 오류 전부에 키가 없다」 · 「… 멱등」 |
| M5 (위와 같음) | +1/−1 | `:app:test --tests *CollectionWiringTest* --tests *CollectionRunnerE2ETest*` | 1 | `CollectionWiringTest` 「오늘은 KST 달력일이다 …」 |
| I1 차수 형식 위반을 접지 않고 던짐(try/catch 제거) | +1/−6 | `:procurement:test --tests *CanonicalizeNeverThrowsTest*` | 1 | 「차수가 비어 있지 않지만 세 자리 숫자가 아니면 IDENTIFIER 탈락이다」 · 계약 전수 × 값 표 |
| I1 (위와 같음) | +1/−6 | `:workflow:test --tests *CollectNoticesUseCaseTest*` | 1 | 「차수 형식이 어긋난 항목은 IDENTIFIER 탈락으로 세고 … 다음 항목을 계속 처리한다」 |
| I1 (위와 같음) | +1/−6 | `:app:test --tests *CollectionRunnerE2ETest*` | 1 | `initializationError`(첫 실행이 던져 컨텍스트가 죽음 — 러너 exit 0 단언 이전) |
| I2 음수 금액을 파싱 단계에서 접지 않음 | +0/−1 | `:procurement:test --tests *CanonicalizeNeverThrowsTest*` | 1 | 「금액이 음수면 NUMERIC 탈락이다」 · 전수 표 |
| I3 낙찰하한율 접기(try/catch) 제거 | +1/−9 | 위와 같음 | 1 | 「낙찰하한율이 음수이거나 표현할 수 없으면 필드 부재다」 · 전수 표 |
| I4 공백 업무구분 코드 접기 제거 | +0/−1 | 위와 같음 | 1 | 「업무구분 코드가 공백뿐이면 업무구분 부재다」 · 전수 표 |
| I5 공백 공고번호를 없는 것으로 보지 않음 | +1/−1 | 위와 같음 | 1 | 「공백뿐인 공고번호·차수는 번호 없음 탈락이다」 · 전수 표 |
| G2 러너·배선 밖 app 클래스가 수집 use case 를 참조 | 새 파일 +7 | `:app:test --tests *CollectionArchitectureGateTest*` | 1 | 「수집 use case 를 참조하는 production 클래스는 허용 집합뿐이다」 |
| G3 app 이웃 클래스가 `RawNoticeObservation.sourceText` 를 꺼냄 | 새 파일 +5 | 위와 같음 | 1 | 「원문 키 접근 타입 참조와 통과 전용 멤버 접근은 … 모듈 전체」 · 「허용 … 관측 집합과 같다」 |
| G5 workflow 이웃 패키지가 `resolveAmount` 를 직접 부름 | 새 파일 +14 | 위와 같음 | 1 | 같은 두 test(원문 해석 함수의 파일 클래스 참조) |
| R3 전송 실패 사유에 예외 메시지를 다시 실음 | +1/−1 | `:adapters:test --tests *KonepsServiceKeyLeakTest*` | 1 | 「전송 실패의 사유는 예외 클래스 이름뿐이다」 |
| WDUP 중복 업종 검사 제거 | +0/−3 | `:app:test --tests *CollectionWiringTest*` | 1 | 「같은 업종을 두 번 적으면 조립 시점에 기동 실패다」 |
| WURL 기본 URL 검사 제거 | +0/−1 | 위와 같음 | 1 | 「KONEPS 기본 URL 이 평문 http 로 외부 호스트를 가리키면 기동 실패다」 |

**초록인 변이 둘(한계의 실측 — checklist 알려진 제한 23·(2b) 표)**:

| 변이 | 적용(+/−) | 명령 | exit | 뜻 |
|---|---|---|---|---|
| M2b 공고명 키를 `buildString` 으로 조립 + 리소스 파일에 적음 | 새 파일 +5 · +1 | `:app:test --tests *CollectionArchitectureGateTest*` | 0 | 상수 풀 리터럴 게이트는 **보조** 잠금이다 — 런타임 조립·리소스 파일은 못 본다(주 잠금은 M1 이 RED 인 모듈 전체 규칙) |
| G4 workflow 이웃 패키지가 `canonicalize` 를 두 번째로 부름 | 새 파일 +11 | `:app:test --tests bidvector.app.architecture.*` | 0 | 같은 함수의 재호출이라 「변환 지점 하나」는 유지 — 경계로 처리(app 쪽은 기존 호출 쌍 규칙이 막는다) |

**D-6F8-7 전수 스윕의 RED 실측** — 수정 전(`Canonicalize.kt`·`AmountResolutionOutcome.kt` 무수정) 트리에서 새 test 만 얹어 `:procurement:test --tests *CanonicalizeNeverThrowsTest*`: exit 1, 10 test 중 6 실패,
전수 표가 던진 자리는 공고번호(공백) · 차수(형식 위반 다수) · 금액 키 넷의 음수 · 낙찰하한율(음수·극단 지수) · 업무구분 코드(공백) — checklist 5b.

**`canonicalize` 경로의 `!!`·`checkNotNull`·`requireNotNull`·`.getValue(`·`.single(`·`error(`**:
`grep -nE '!!|checkNotNull|requireNotNull|\.getValue\(|\.single\(|\berror\(' procurement/src/main/kotlin/bidvector/procurement/{Canonicalize,AmountResolutionOutcome,DateTimeInterpretation,NoticeId,BusinessCategory,Agency,NoticeTitle,ResolvedBaseAmount}.kt shared-kernel/src/main/kotlin/bidvector/sharedkernel/{NoticeRound,Rate}.kt`
(주석 줄 제외) → `error(` 둘뿐(통화·출처, checklist 5b 9·10번 — 정책 구성 오류).

## 변이 실측 (다) — 수정 라운드 2: 빈 값 규칙(D-6F8-11)·반사 표면 봉쇄(D-6F8-13 F2-2) (산출물 `1b5b704c` 에서 실측, 버릴 worktree — 적용 전 `git diff --numstat` 확인, 실행 뒤 `git reset --hard`)

**RED 먼저** — 수정 전 트리에서 새 test 만 얹어 돌렸다: `:procurement:test --tests *CanonicalizeBlankValuesTest*` exit 1(7 test 중 5 실패, 「계약 전수 × 공백 값」 표 offender 55 = 필드 열한 개 × 공백 값 다섯 —
운영 정책 여섯(금액 후보 넷·일시 둘) + test 정책 다섯) · `:workflow:test --tests *CollectNoticesUseCaseTest*` exit 1(16 중 1 실패) · 반사 봉쇄는 production 에 대해 RED(`:app:test --tests bidvector.app.architecture.CollectionArchitecture*`
exit 1, 28 중 2 실패 — 러너 파일 클래스가 `kotlin.reflect.KClass` 를 참조).

| 변이 | 적용(+/−) | 명령 | exit | RED 된 test |
|---|---|---|---|---|
| B1 빈 일시 수정 되돌림(`instantResolutionOf` 를 `instantFrom` 직접 호출로) | +2/−2 | `:procurement:test --tests *CanonicalizeBlankValuesTest*` | 1 | 7 중 3 — 「빈 마감·개찰 일시는 그 필드만 null」 · 「계약 전수 × 공백 값」 · 「실수집이 낸 형태」 |
| B1 (위와 같음) | +2/−2 | `:workflow:test --tests *CollectNoticesUseCaseTest*` | 1 | 「빈 문자열인 옵션 일시·금액 항목은 탈락이 아니라 정규화된다」 |
| B1 (위와 같음) | +2/−2 | `:app:test --tests *CollectionRunnerE2ETest*` | 1 | 5 중 3 — 「첫 실행 …」 · 「빈 문자열인 옵션 항목은 탈락하지 않고 마감이 null 인 공고로 저장된다」 · 「형식이 어긋난 차수 항목 …」(DATE_TIME 계수 어긋남) |
| B2 빈 금액 후보 건너뜀 제거 | +1/−1 | `:procurement:test --tests *CanonicalizeBlankValuesTest*` | 1 | 7 중 2 — 「공백뿐인 금액 후보는 건너뛴다」 · 「계약 전수 × 공백 값」 |
| B3 빈 업무구분 라벨 부재 처리 제거 | +0/−1 | 위와 같음 | 1 | 7 중 1 — 「업무구분 라벨이 공백뿐이면 라벨만 없다」 |
| F2 리플렉션 우회(이웃 `workflow.collectionx` 가 원문 타입을 이름 붙이지 않고 `getMethod`·`invoke` 로 항목 원문을 꺼내고 use case 가 그 결과로 항목을 거른다) — **수정 전**(`9d63f922`) | +1/−0 · 새 파일 +3 | `:workflow:test :app:test --tests bidvector.app.architecture.*` | **0** | (없음 — 게이트의 사각, 전체 `check` 초록이라던 verifier r2 F2-2 의 재현) |
| F2 (위와 같음) — **수정 후**(`1b5b704c`) | +1/−0 · 새 파일 +3 | 위와 같음 | 1 | 75 중 2 — 「workflow·app production 은 리플렉션 API 를 참조하지 않고 Class 는 이름 조회만 한다」(`ItemTextKt -> java.lang.reflect.Method`) · 「허용 참조자·허용 Class 멤버는 관측 집합과 같다」 |

B1~B3 는 수정한 세 자리를 **하나씩** 되돌려 각각 그 자리의 test 가 RED 임을 잰 것이다(같은 수정이 다른 계층의 test 를 함께 RED 로 만드는 것도 표에 있다 — 세 계층 잠금). 반사 표면 규칙의 양성 대조는 규칙 자체를 fixture 루트에 적용하는
`CollectionArchitectureGateCatchesViolationsTest`(네 길이 잡히고 이름 조회만 하는 `CleanNameLookup` 은 안 잡힘)가 갖는다 — 이 표의 F2 는 그 게이트가 production 에서 실제로 물리는 것을 재는 별개 실측이다.

## 변이 실측 (가) — 아홉 (scope.md acceptance 의 여섯 + 공고명 둘 + 배포물 하나, 산출물 HEAD `66589eda` 에서 실측)

버릴 worktree 에서 변이를 적용하고 `git diff --no-index --numstat` 로 **적용을 확인한 뒤** RED 명령을 돌렸다(변이마다 원본 복원). 전부 RED, 사유는 표의 test.

| 변이 | 적용(+/−) | RED 명령 | exit | RED 된 test |
|---|---|---|---|---|
| use case 가 `canonicalize` 옆에서 원시 키를 직접 추출(`valueOf`) | +2/−0 | `:app:test --tests *CollectionArchitectureGateTest` | 1 | 「use case 는 원문 필드를 직접 읽지 않는다」 · 「procurement 참조 집합은 허용 집합과 같다」 |
| 공고명 키 리터럴을 어댑터에 박기 | +2/−0 | 위와 같음 | 1 | 「공고명 원시 키 리터럴은 … 정책 클래스 밖 상수 풀에 없다」 |
| 정규화 탈락 한 건을 세지 않고 버리기 | +0/−2 | `:workflow:test --tests *CollectNoticesUseCaseTest` | 1 | 「회계 등식」 · 「정규화에서 탈락한 항목도 원문은 먼저 저장된다」 |
| 쿼터 소진 뒤에도 계속 부르기 | +1/−2 | 위와 같음 | 1 | 「쿼터 소진은 실행을 멈춘다」(소스 호출 계수) |
| 러너 기본 켜짐(`@ConditionalOnProperty` 제거) | +0/−1 | `:app:test --tests *CollectionWiringTest` | 1 | 「속성이 없으면 러너도 수집 빈도 없다」 · 「mode 가 once 가 아니면 … 러너는 없다」 |
| 서비스 키를 전송 실패 메시지에 싣기 | +2/−1 | `:adapters:test --tests *KonepsServiceKeyLeakTest` | 1 | 「전송 계층의 실패 메시지에도 키가 없다」 |
| 서비스 키(요청 URI)를 러너 실패 원인에 싣기 | +1/−1 | `:app:test --tests *CollectionRunnerTest` | 1 | 「전송 계층 예외의 메시지에 요청 URI 가 실려 있어도 원인 코드는 클래스 이름뿐이다」 |
| 공고명 조립이 항상 `null` | +1/−1 | `:procurement:test --tests *NoticeTitleCanonicalizeTest` | 1 | 「운영 정책 계약이 공고명을 나른다」 외 |
| 공고명 계약 행의 키를 바꿈 | +1/−1 | `:procurement:test --tests *NoticeTitleCanonicalizeTest --tests *CollectionPolicyTest` | 1 | 「운영 정책 계약이 공고명을 나른다」 · 「필드 계약은 승인된 채택분만 등재한다」 |
| 배포물에서 kotlin-reflect 의존 제거 | +0/−1 | `:app:test --tests *BootJarRuntimeClasspathTest` | 1 | 「배포물에 kotlin-reflect 가 들어 있다」 |

## 값 획득 표면 컴파일 probe (app test 소스셋에 심은 임시 파일, 커밋 안 함)

`./gradlew :app:compileTestKotlin` — 다섯 시도 전부 컴파일 거부, 사유는 **가시성을 가르는 문구**다(`it is internal in`·`it is private in` — 접근 불가의 다른 사유가 아님):
`CollectionRangePolicyData(...)` internal · `CollectionRange(...)` private · `CollectionSourceName(...)` private · `CollectionSourceName.copy(...)` private ·
`KonepsFieldContractRegistry.valueIn(...)` internal.

## 배포 jar 명령 형태 스모크 (버릴 Postgres 컨테이너 · mock KONEPS · 실호출 0)

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `java -jar app/build/libs/app.jar`(환경변수만, `SPRING_MAIN_WEB_APPLICATION_TYPE=none`) — **수정 전** | 1 | 부팅 직후 `NoClassDefFoundError: kotlin/reflect/jvm/ReflectJvmMapping` — 기존 결함 발견(checklist 5항 13번) |
| 같은 명령 — **수정 후**(`c5c13fde`) | 0 | 슬롯 6(조회일 3 × 업종 2) 전부 완료 · `notice` 12행 전부 공고명 보유 · `collection_run` 6행 · 원문 12행 · 전체 로그에 서비스 키·DB 자격 0회 |

컨테이너는 스모크 뒤 `docker rm -f -v` 로 폐기했다(개발 DB `bid-vector-v2-dev` 는 건드리지 않았다).

## 비밀값 스캔 — 패턴 파일 참조형

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m6/6f8/` | 1 | 매치 없음(통과) |
| 같은 패턴 파일로 이 slice 가 바꾼 코드 경로 전체 | 0 | 매치는 전부 세 부류 — 명시적 가짜 표본 값(test 가 누출 부재를 재려고 심은 표식 문자열), 컨테이너 test 전용 자격 문자열(기존 test 와 같은 관례), 설정 속성·타입의 이름. 실제 값 없음(육안). `leakPatternGate` 의 스캔 대상은 `reports/evidence` 뿐이라 코드 경로 매치는 게이트가 아니다 |

Telegram id·사업자 정보는 패턴 스캔으로 못 잡아 육안 확인: evidence 에 키·공고 원문·개인정보 없음(스모크 표본 문자열 외).

## 역방향 좌표 (편집한 승인 문서 `data-dictionary.md`)

| 명령 | 결과 |
|---|---|
| `grep -rnE "data-dictionary(\.md)?:[0-9]+" -o --include=*.md --include=*.kt --include=*.properties --include=*.yaml --include=*.py .`(작업 노트 제외) | 인용 좌표 셋(그 문서의 삽입 지점보다 **위쪽** 줄) — 밀리지 않는다. 삽입 줄 번호와 인용 줄 번호를 함께 확인했다 |
| `grep -rnE "policy-values[^ ]{0,12}:[0-9]+" --include=*.md --include=*.kt --include=*.properties --include=*.yaml .`(승인 문서 `policy-values.md` 에 줄을 넣은 뒤) | 0건 — 밀릴 좌표 없음 |
| 수정 라운드 2 가 편집한 파일 — `grep -rnE "<stem>(\.[a-z]+)?:[0-9]+" --include=*.md --include=*.kt --include=*.properties --include=*.yaml --include=*.py .`(작업 노트 제외), stem = `architecture-policy`·`gate-tests`·`Canonicalize`·`AmountResolutionOutcome`·`CollectionRunner`·`CollectionArchitectureRules`·`ArchitecturePolicy`·`CollectionArchitectureGateTest`·`CanonicalizeBlankValuesTest` | 전부 0건 — 밀릴 좌표 없음. 이 slice 의 evidence 를 가리키는 `m6/6f8/…:줄` 인용도 0건 |

## 하네스 레인 변경

`git log --oneline <base>..HEAD -- CLAUDE.md .claude/` — 빈 출력(없음). `scope.md` 상시 절 그대로.

## clean-tree 게이트

`git status --porcelain -- <scope.md in_scope 경로, 개별 인자>` — 마지막 evidence 커밋 뒤 빈 출력 + 양성 대조 1회(in_scope 파일 끝에 줄을 덧붙여 잡히는지 확인 후, 사본으로 **비파괴 복원**, `checkout --` 미사용):


clean-tree 실측: 빈 출력(0줄) → 양성 대조로 in_scope 파일 하나에 줄을 덧붙이자 1줄(` M …`) → 사본으로 복원 뒤 다시 0줄.

## rollback 실측

`rollback.md` 참조(①~⑥ — 실측 HEAD 는 그 문서 첫머리가 적는다).
