# M4/4B-8 commands

acceptance: `./gradlew --no-build-cache --no-daemon clean check`(scope.md) — 마이그레이션
없음(D-4B8-4·우회 (4), SQL·스키마 무변경)이라 migration-reviewer 불요. `ml-engine` job은 이
slice와 소스 비중첩이라 Kotlin `check` 전건이 정본이다.

## 1차 실행 — RED→GREEN 전환 직후(2026-09-16, 구현+test 커밋 전 working tree)

- cmd: `./gradlew --no-build-cache --no-daemon clean check`
- exit: 1
- 핵심 결과: 두 실패. ① `:workflow:detekt` — `OpportunityAnalysis.resolvePolicies`의 4항
  `&&` 조건이 `ComplexCondition`(허용 상한 3) 위반. ② `:app:test`
  `ArchitectureGateTest` — `normalizeCategoryKey`가 `java.util.Locale.ROOT` 필드를
  참조(Kotlin `String.lowercase()`의 컴파일 결과)해 domain 모듈 환경 축 누출 게이트에
  걸림(`member-effects.properties`가 관련 `String` 메서드를 이미 `forbidden`으로 분류해
  둔 표면).

## 수정 — 두 위반 해소

- `OpportunityAnalysis.resolvePolicies`를 중첩 `if`로 재구성(스마트캐스트 보존, 조건 수
  3 이하로 분할).
- `normalizeCategoryKey`를 `String.lowercase()`(무인자, Locale 참조) 대신
  `raw.trim().map(Char::lowercaseChar).joinToString("")`(`Character.toLowerCase(Char)`,
  Locale 미참조)로 재작성.
- cmd: `./gradlew --no-daemon --no-build-cache :procurement:test --tests "bidvector.procurement.BusinessCategoryTest" :workflow:detekt :app:test --tests "bidvector.app.architecture.ArchitectureGateTest"`
- exit: 0
- 핵심 결과: 표적 재검증 통과(BUILD SUCCESSFUL) — 두 위반 모두 해소 확인.

## 2차 실행 — 전건(2026-09-16, 구현+test 커밋 전 working tree, 정정 반영)

- cmd: `./gradlew --no-build-cache --no-daemon clean check`
- exit: 0
- 핵심 결과: 전 모듈 `check` 통과. `procurement` 171 tests(0 failed) · `workflow`
  254 tests(0 failed) · `adapters` 517 tests(0 failed) · `app` 129 tests(0 failed) —
  4B-7 종결 시점 대비 `workflow` +5(대상 라벨 규칙표·변이 test)·`adapters` +1(정규화 일치
  test)·`procurement` +7(`BusinessCategoryTest` 신설).

## 크기 게이트(verifier r1 F-4)

evidence(430줄) 대 산출물(코드·게이트 303줄)이 1.32배 초과 — 정규화 규칙표(26 입력 대조)·
대상 라벨 규칙표·(2b) 목록이 산출물(순수 함수 몇 개)보다 큰 소형 slice의 구조적 초과다.
중복이던 것은 줄였다: 이 절 위에 있던 「영향 범위 사전 실행」(클래스별 부분 test 재실행,
아래 전건과 수치 중복)을 삭제했다 — 전건이 정본이라는 이 문서의 첫 줄과 실제로도
일치시킨다. `rollback.md`의 절차·공유 파일 논증도 같은 라운드에서 압축했다.

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 15개 경로 개별 인자> reports/evidence/m4/4b8/`
- exit: 0(매치 있음)
- 핵심 결과: 매치 둘은 전부 기존 코드다. ① `JdbcCompetitionSampleSourceTest.kt`의 DB
  접속 실패 test(위협 모델 (8), 4B-7이 이미 심어 둔 test)에서 `PGSimpleDataSource`의
  인증 정보 필드 하나가 패턴에 걸린다 — 이 slice는 그 줄을 만들지 않았다(같은 파일에
  새 test를 추가했을 뿐, 4B-7 commands.md가 같은 매치를 이미 기록). ② `KonepsCollectionExecutors.kt`
  의 기존 헬퍼 함수·변수 이름 여럿(패딩·provenance 비교 축 관련)이 스캔 어휘 한 단어와
  우연히 겹친다 — 실제 값이 아니라 이름 자체의 부분 문자열 일치이고, 이 slice가 건드린
  두 줄(`CategoryCode(` → `CategoryCode.of(` 전환)과는 무관한 자리다. `reports/evidence/m4/4b8/`
  자체는 매치 0(위 「비밀값 스캔 어휘 축어 금지」 규약 준수 — 이 문서도 어휘를 간접
  표현으로만 적는다).

## rollback 실측

`rollback.md` 참고 — 임시 clone(`--no-hardlinks`)에서 ①~⑥ 전부 실행. ①~③(목록 추출·역적용·
대조) exit 0, ④(모듈별 compile) exit 0(BUILD SUCCESSFUL), ⑥(되돌린 트리 루트 `check` 전건)
exit 0(BUILD SUCCESSFUL, 355 tasks) — 되돌린 트리가 4B-7 종결 시점과 같은 형태로 정상
동작함을 확인했다.

## evidence 커밋 HEAD 재실측(패턴 어휘 자기참조 방지, 2026-09-16 규약)

- cmd: `./gradlew --no-daemon --no-build-cache clean check`(evidence 문서 다섯 커밋
  `d457040` HEAD)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — evidence 문서 다섯 커밋 뒤에도 전 모듈 `check` 그대로
  통과, 이 문서들이 비밀값 스캔 게이트를 스스로 깨지 않는다. N-5(4B-7 commands.md와 같은
  성격) — 이 결과를 적는 커밋 자체의 재실측은 다음 라운드가 있으면 그때 확인한다.

## verifier r1 F-1·F-3·F-4·F-6 정정 커밋(`86ba432`) HEAD 재실측

- cmd: `./gradlew --no-daemon --no-build-cache clean check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — evidence만 고친 커밋(코드 무변경) 뒤에도 전 모듈 `check`
  그대로 통과. N-5 한계(이 기록 자체를 담는 커밋의 재실측은 다음 라운드로 미룸)는 위와
  같은 성격으로 등재.

## 2026-09-16 종결 커밋 HEAD 재실측 (팀장)
- cmd: `./gradlew --no-build-cache --no-daemon clean check` (HEAD `b458a3d` — milestone-4 종결 문단·scope `head_sha`·checklist 승인 절)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 346 tasks. `origin/main` 은 base `845e29b` 그대로 — 흡수 병합 없음.
