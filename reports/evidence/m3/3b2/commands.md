# M3/3B-2 — commands.md

base `0e83d6e84a5279352aae1584e3849aa355a991c8`. **head 는 이 문서를 담은 커밋 자신**(`git rev-parse
HEAD`, verifier r1 L-2 수정 — 리뷰 시점마다 바뀌는 값을 이 문서에 박지 않는다, 3B checklist.md 와
같은 규약). 구현 레인 커밋은 `git log --oneline 0e83d6e8..HEAD -- procurement/ adapters/
config/quality/gate-tests.properties reports/evidence/m3/3b2/`로 확인한다.

**이 절은 마지막 상태 하나만 싣는다**(verifier r2 L-5 — 라운드 이력 절 축적 금지, 2026-08-30
규격). 아래 acceptance 표는 G-1·G-2·G-3·G-4 수정 뒤 최종 재실행 결과다. 표적 재검증 대상 목록·
스테이징 규율·병렬 레인 경계 확인·완료 조건은 `checklist.md`가 정본이다(중복 금지 — 여기서
반복하지 않는다).

## Acceptance 전건

| id | 명령 | exit |
| --- | --- | --- |
| S-0 | `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` | 0 (348 tasks, 348 executed) |
| S-1 | `./gradlew --no-build-cache clean check` | 0 |
| S-2 | `./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'` | 0 |
| S-3 | `./gradlew :adapters:moduleDependencyGate` | 0 |
| S-3b | `./gradlew :adapters:test --tests '*KonepsAdapterDependencyTest*'` | 0 |
| S-3c | `./gradlew :procurement:test` | 0 |
| S-4 | `./gradlew qualityBaseline` | 0 |

`:procurement:gateExecutionGate` 별도 재확인 — exit 0.

test 수(JUnit XML 실측, `--no-build-cache clean check` 직후 재확인, 실패·건너뜀 0): koneps — 3B
기존 `KonepsOpenApiNoticeSourceTest` 20(편집 없이 불변)·`ServiceKeyTest` 3·
`KonepsAdapterDependencyTest` 1, 3B-2 신규·확장 `KonepsOpeningResultSourceTest` 15·
`KonepsIdentifierMaskingTest` 7·`KonepsLicenseLimitDocumentSourceTest` 8·
`KonepsOperationDescriptorTest` 7. procurement — `CollectionPolicyTest` 20(§1.7 12행 +
license-limit 2행 등재분 포함, G-4 반영)·`FieldContractTest` 9·`DetailFetchTest` 7·
`AccountingTest` 14(G-1 `rowIdentifierIndeterminate` 회귀분 포함)·`DecideQualificationFetchTest`
3·`PortsTest` 2. 3A corpus 27/27 은 3A/3C 소관 test 가 재고(`fixtures/**`는 out_of_scope 라 이
slice 가 편집하지 않았다) — `:procurement:test` 통과가 그 test 를 함께 재실행한다.

## secret 스캔(evidence-pack 리뷰 요청 조건)

- cmd: `git diff <base>..HEAD -- 'adapters/**' 'procurement/**' 'config/**' | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
- exit: 1(매치 없음 = 통과) — **이것이 정본 스캔이다.** 코드·설정 diff 자체에 자격 증명·비밀
  패턴 매치가 없다는 뜻이다.
- 이 evidence 문서 자신(`reports/evidence/m3/3b2/`)을 같은 패턴으로 훑으면 매치가 나오는데,
  전부 스캔 명령 문자열·이 절 제목 인용문에 패턴 리터럴(`secret`·`token` 등)이 들어 있어
  스스로를 맞히는 자기참조다(verifier r2 L-3 — 이전 판은 이 매치 수를 셈에 넣었으나 무엇을
  센 수인지 밝히지 않았다). 코드·설정 diff 스캔(위)이 실제 노출 여부의 정본이다.
- 육안 확인: 커밋 diff·test fixture 의 사업자등록번호·전화번호·업체명은 전부 `SYN-`/`SRC-`
  합성값이다(문서 원문 샘플 값을 옮기지 않았다). `lmtGrpNo`/`lmtSno` 등 F-1 회귀 test 값도
  전부 합성 순번이다.

## clean-tree 게이트(경로 개별 인자·양성 대조)

- cmd: `git status --porcelain -- <in_scope 경로 10개, 개별 인자>`
- exit: 0, **출력 없음**(`git status --porcelain`는 exit code 가 항상 0 이므로 판정은 출력
  유무다 — 전부 커밋됨)
- 양성 대조: evidence 파일에 한 줄을 추가해 같은 명령으로 `M reports/evidence/m3/3b2/scope.md`가
  실제로 잡히는지 확인한 뒤 `git checkout --`로 원상복구(「빈 출력이 게이트 부재가 아니라 실제로
  깨끗함」의 증거) — 원복 뒤 재확인 결과 다시 출력 없음.

## rollback 실측(G-3 해소)

정본은 `rollback.md` — 누락됐던 세 파일(`KonepsOpenApiNoticeSource.kt`·`Accounting.kt`·
`AccountingTest.kt`)을 restore 목록에 더한 뒤 임시 clone 에서 삭제·restore·commit·
`compileKotlin`·`test` 까지 전부 exit 0 을 실측했다(exit 0 만으로 성공을 적지 않는다는 지시대로
컴파일·test 산출물까지 확인). 되돌린 트리의 `adapters/koneps` 디렉터리 파일 수 10 = base 와 일치,
koneps test 클래스는 3B 기존 셋(20/3/1)만 남고 실패·건너뜀 0.
