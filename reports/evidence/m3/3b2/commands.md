# M3/3B-2 — commands.md

base `0e83d6e84a5279352aae1584e3849aa355a991c8` · head `938e7297decdf13bb37f8c0045d293bf0dcf5ebb`
(위 두 커밋: 3A 좁은 확장 `c53fe19`, 어댑터 구현 `938e729`).

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

## secret 스캔 (evidence-pack 리뷰 요청 조건)
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m3/3b2/`
- exit: 1(매치 없음 = 통과)
- cmd: `git diff <base>..HEAD -- 'adapters/**' 'procurement/**' 'config/**' | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
- exit: 1(매치 없음 = 통과)
- 육안 확인: 커밋 diff·test fixture 의 사업자등록번호·전화번호·업체명은 전부 `SYN-`/`SRC-` 합성값이다
  (문서 원문 샘플 값을 옮기지 않았다).

## clean-tree 게이트(경로 개별 인자·양성 대조, 2026-09-08T06:59Z 실측)
- cmd: `git status --porcelain -- <in_scope 경로 10개, 개별 인자>`
- exit: 0, **출력 없음**(`git status --porcelain` 는 exit code 가 항상 0 이므로 판정은 출력 유무다 — 전부 커밋됨)
- 양성 대조: evidence 파일에 한 줄을 추가해 같은 명령으로 `M reports/evidence/m3/3b2/scope.md` 가
  실제로 잡히는지 확인한 뒤 `git checkout --`로 원상복구(「빈 출력이 게이트 부재가 아니라 실제로
  깨끗함」의 증거) — 원복 뒤 재확인 결과 다시 출력 없음.
