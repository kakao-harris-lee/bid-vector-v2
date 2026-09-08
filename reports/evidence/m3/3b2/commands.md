# M3/3B-2 — commands.md

base `0e83d6e84a5279352aae1584e3849aa355a991c8`. **head 는 이 문서를 담은 커밋 자신**(`git rev-parse
HEAD`, verifier r1 L-2 수정 — 리뷰 시점마다 바뀌는 값을 이 문서에 박지 않는다, 3B checklist.md 와
같은 규약). 구현 레인 커밋은 `git log --oneline 0e83d6e8..HEAD -- procurement/ adapters/
config/quality/gate-tests.properties reports/evidence/m3/3b2/`로 확인한다.

## 2026-09-08T06:52:00Z — S-0(worktree add + clean check, 임시 clone 실측)
- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: 348 actionable tasks, 348 executed, BUILD SUCCESSFUL. 완료 뒤 `git worktree remove --force` +
  디렉터리 삭제로 잔여 없음 확인.

## 2026-09-08T06:54:30Z — S-1(clean check, 로컬)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: 전 모듈(procurement·adapters·shared-kernel·qualification·decision·strategy·settlement·
  workflow·app·build-logic) BUILD SUCCESSFUL. `cpdCheck`·`detekt`·`ktlintMainSourceSetCheck`·
  `ktlintTestSourceSetCheck` 포함 — 3라운드 수정(CPD 중복 제거·detekt LoopWithTooManyJumpStatements/
  ReturnCount/MaxLineLength·ktlint 포맷) 뒤 green.

## 2026-09-08T06:57:10Z — S-2(koneps 시나리오 전부)
- cmd: `./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL. 3B 기존 `KonepsOpenApiNoticeSourceTest`(20 test)·
  `KonepsAdapterDependencyTest`(1)·`ServiceKeyTest` 편집 없이 green + 3B-2 신규
  `KonepsOperationDescriptorTest`(7)·`KonepsIdentifierMaskingTest`(6)·
  `KonepsOpeningResultSourceTest`(5)·`KonepsLicenseLimitDocumentSourceTest`(5) 전부 green.

## 2026-09-08T06:57:40Z — S-3(adapters moduleDependencyGate)
- cmd: `./gradlew :adapters:moduleDependencyGate`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL.

## 2026-09-08T06:58:00Z — S-3b(KonepsAdapterDependencyTest 단독)
- cmd: `./gradlew :adapters:test --tests '*KonepsAdapterDependencyTest*'`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — koneps 패키지의 domain import 는 procurement·shared-kernel 뿐(3B-2
  신규 파일 5종 포함 재검사).

## 2026-09-08T06:58:20Z — S-3c(procurement:test, 3A corpus 27/27 포함)
- cmd: `./gradlew :procurement:test`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL. `CollectionPolicyTest`(16, 개찰 축 12행 포함 스물세 개 등재 검증)·
  `FieldContractTest`(9)·`DetailFetchTest`(7)·`DecideQualificationFetchTest`(3, 신규)·
  `PortsTest`(2, 신규) 전부 green — 3A 기존 계약 행·항등식 불변.

## 2026-09-08T06:58:35Z — S-4(qualityBaseline)
- cmd: `./gradlew qualityBaseline`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, `build/reports/quality-baseline/quality-baseline.md` 갱신.

## secret 스캔 (evidence-pack 리뷰 요청 조건, 2026-09-08T07:37Z 재실측)
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m3/3b2/`
- exit: 0, 매치는 이 절 자신(제목·스캔 명령 인용문)뿐 — 패턴 문자열이 자기 문서를 스캔하며 자기를
  맞히는 자기참조 매치다(하네스 이력의 「누출 검사 판독 규칙」과 같은 표면). 그 매치를 뺀 재확인
  (`grep -v "cmd: \`grep\|cmd: \`git diff"`)은 exit 0·매치 1건(이 섹션 제목 자체)뿐이고, 실제
  자격 증명·값은 없다(육안 확인).
- cmd: `git diff <base>..HEAD -- 'adapters/**' 'procurement/**' 'config/**' | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
- exit: 1(매치 없음 = 통과 — 코드·설정 diff 자체에는 자기참조 표면이 없다)
- 육안 확인: 커밋 diff·test fixture 의 사업자등록번호·전화번호·업체명은 전부 `SYN-`/`SRC-` 합성값이다
  (문서 원문 샘플 값을 옮기지 않았다). `lmtGrpNo`/`lmtSno` 등 F-1 회귀 test 의 값도 전부 합성 순번이다.

## clean-tree 게이트(경로 개별 인자·양성 대조, 2026-09-08T06:59Z 실측)
- cmd: `git status --porcelain -- <in_scope 경로 10개, 개별 인자>`
- exit: 0, **출력 없음**(`git status --porcelain` 는 exit code 가 항상 0 이므로 판정은 출력 유무다 — 전부 커밋됨)
- 양성 대조: evidence 파일에 한 줄을 추가해 같은 명령으로 `M reports/evidence/m3/3b2/scope.md` 가
  실제로 잡히는지 확인한 뒤 `git checkout --`로 원상복구(「빈 출력이 게이트 부재가 아니라 실제로
  깨끗함」의 증거) — 원복 뒤 재확인 결과 다시 출력 없음.

## 판정 로직 변경(F-1·F-4·F-5·F-7) 뒤 acceptance 전건 재실행 — 2026-09-08T07:37Z, head `5a92afd`

| id | 명령 | exit |
| --- | --- | --- |
| S-0 | `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` | 0 (348 tasks, 348 executed) |
| S-1 | `./gradlew --no-build-cache clean check` | 0 |
| S-2 | `./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'` | 0 |
| S-3 | `./gradlew :adapters:moduleDependencyGate` | 0 |
| S-3b | `./gradlew :adapters:test --tests '*KonepsAdapterDependencyTest*'` | 0 |
| S-3c | `./gradlew :procurement:test` | 0 |
| S-4 | `./gradlew qualityBaseline` | 0 |

test 수(JUnit XML 실측, 실패·건너뜀 0): `KonepsOpenApiNoticeSourceTest` 20(3B 기존, 편집 없이
불변)·`KonepsIdentifierMaskingTest` 7(+1, F-5 회귀)·`KonepsLicenseLimitDocumentSourceTest` 7(+2,
F-1·F-2)·`KonepsOpeningResultSourceTest` 8(+3, F-1·F-2 ×2)·`KonepsOperationDescriptorTest` 7(불변)·
`CollectionPolicyTest` 17(+1, F-5)·`PortsTest` 2·`DecideQualificationFetchTest` 3(F-7 로 게이트
등재). `procurement:gateExecutionGate` 재확인 — 신규 두 class 포함 green.

## F-3·F-8(3A Accounting.kt 좁은 확장) 뒤 acceptance 전건 재실행 — 2026-09-08T07:58Z

| id | 명령 | exit |
| --- | --- | --- |
| S-0 | `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` | 0 (348 tasks, 348 executed) |
| S-1 | `./gradlew --no-build-cache clean check` | 0 |
| S-2 | `./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'` | 0 |
| S-3 | `./gradlew :adapters:moduleDependencyGate` | 0 |
| S-3b | `./gradlew :adapters:test --tests '*KonepsAdapterDependencyTest*'` | 0 |
| S-3c | `./gradlew :procurement:test` | 0 |
| S-4 | `./gradlew qualityBaseline` | 0 |

`:procurement:gateExecutionGate` 별도 재확인 — 0. test 수(JUnit XML 실측, 실패·건너뜀 0):
`AccountingTest` 12(+2, F-3·F-8 항등식 무관·음수 거부)·`KonepsIdentifierMaskingTest` 7(불변,
assertion 만 정정 — unknownFieldCount/maskingFailureCount 분리)·`KonepsOpeningResultSourceTest`
9(+1, F-3·F-8 종단 test). secret 스캔·clean-tree 게이트 재확인(자기참조 매치만, 판독 규칙
그대로 적용) — 실측 결과 이전 라운드와 동일.
