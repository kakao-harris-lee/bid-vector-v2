# M6/6F-8 commands

명령과 종료 코드만 남긴다(출력 전문 없음 — 감사자는 명령을 다시 돌린다). 실측은 전부 **버릴 worktree**(`git worktree add --detach`)에서 했고 산출물 HEAD 는
`66589eda`(이 slice 의 마지막 산출물 커밋), `base` 는 `git merge-base HEAD origin/main`(= `74616367`, PR #44 머지 커밋). **마지막 evidence HEAD 의 `check` 결과 정본은
verifier 와 완료 보고다**(evidence 가 자기 마지막 커밋의 post-state 를 담을 수 없다).

## acceptance — CI job 명령 그대로 (2026-09-24, 캐시 우회)

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `./gradlew --no-daemon check --rerun-tasks` | 0 | BUILD SUCCESSFUL, 전 task 재실행, test 전건 초록(skip 은 전부 기존 `RealServerIntegrationTest`, 이 slice 무관) |
| `./gradlew --no-daemon qualityBaseline` | 0 | BUILD SUCCESSFUL |
| `./tools/one-command-check.sh` | 0 | 「Kotlin 전건 + Python 전건 통과」 |

실패 이력(검증이 작동했다는 증거): 같은 acceptance 를 산출물 커밋 `c5c13fde` 에서 돌렸을 때 `check` exit 1 — `:app:sizeGate` 함수 50줄 한도(첫 `dependencies {}` 람다 52줄).
별도 블록으로 옮긴 `66589eda` 에서 위 셋 exit 0.

## 변이 실측 — 아홉 (scope.md acceptance 의 여섯 + 공고명 둘 + 배포물 하나)

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

## 하네스 레인 변경

`git log --oneline <base>..HEAD -- CLAUDE.md .claude/` — 빈 출력(없음). `scope.md` 상시 절 그대로.

## clean-tree 게이트

`git status --porcelain -- <scope.md in_scope 경로, 개별 인자>` — 마지막 evidence 커밋 뒤 빈 출력 + 양성 대조 1회(in_scope 파일 끝에 줄을 덧붙여 잡히는지 확인 후, 사본으로 **비파괴 복원**, `checkout --` 미사용):
아래 「clean-tree 실측」 줄이 그 결과다.

## rollback 실측

`rollback.md` 참조(①~⑥, `실측 HEAD: 66589eda`).
