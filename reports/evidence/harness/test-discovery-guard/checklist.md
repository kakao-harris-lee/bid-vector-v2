# checklist.md — harness/test-discovery-guard (verifier ready-for-review 점검)

CLAUDE.md 운영자 지시(2026-09-04) — 코드 slice 는 Codex 심판 대상이 아니다. 완료 조건은
verifier ready-for-review + 사용자 승인(scope.md 지위 문단).

## 리뷰 요청 조건

- [x] 구현 diff 가 커밋되어 base/head 고정 — base `7581106`, head `6752791`. 구현 커밋
      `c7aaf5c`(게이트 본체)·`fe7d133`(breaking-mutations.sh B-1~B-4)·`94dac38`(detekt
      수정)·`6752791`(evidence). `git status --porcelain -- <in_scope 경로 개별 인자>`
      결과 없음(commands.md 에 셋 커밋 반영 후 실측 — 이 문서 작성 시점 기준 이 파일들
      자체는 아직 미커밋이라 별도 커밋으로 뒤따른다).
- [x] scope.md 의 acceptance_commands(S-0~S-6) 전부 실행·기록됨 — S-2~S-6 은 exit 0.
      S-0/S-1 은 procurement 의 out_of_scope 위반 1건 때문에 exit 1 이었으나(아래 「게이트가
      잡은 실제 위반(범위 밖)」), 3A 커밋 `b72b712` 뒤 S-0 재실행이 **exit 0**(head
      `9baef6b`) — 정본 acceptance 가 초록으로 해소됐다.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `build-logic:test`·
      `build-logic:detekt`·`contractGate`·`qualityBaseline` 전부 통과(S-0/S-1 실행 중
      확인). 유일한 미통과는 procurement(out_of_scope) 의 `testShapeGate`.
- [x] 변경된 fixture 와 정책 version 의 근거가 기록됨 — `test-shape-policy.properties
      policy.version=1`(신설). `contract-policy.properties` 는 무변경(F-21~F-23 은 스크립트
      판정 구조 수정이지 정책 값 수정이 아니다). `contracts/testdata/breaking/expected.tsv`
      내용 불변(S-4 실측).
- [x] 알려진 제한과 rollback 방법이 기록됨 — 아래 「알려진 제한」 + `rollback.md`(임시
      clone 실측 포함).
- [x] secret 스캔 통과 — commands.md 「secret 스캔」 절. 매치 4건 전부 `KtTokens`/
      `minimumTokenCount` 오탐, 실제 secret 없음(육안 확인 병기).

## 게이트가 실제로 잡는다는 증거

- **음성(신규 위반을 잡는다)** — `PredictionContractTest.kt`(2B 원 사례 test)를 일시
  식 본문으로 바꿔 `:adapters:testShapeGate` exit 1 실측, 원복 후 exit 0(commands.md).
- **양성(현재 트리에서 정당한 형태는 통과한다)** — `TestShapesTest` 16종(위반 4·정당 3·
  factory 2·suspend 2·private 2·비대상·복수위반) + S-0/S-1 실행에서 `testShapeGate`/
  `buildLogicTestShapeGate` 가 procurement 를 제외한 8 모듈(shared-kernel·qualification·
  strategy·decision·settlement·workflow·adapters·app) + build-logic 자신에서 전부 통과.
- **실행 단언은 알려진 제한 1(아래)로 이월** — `gate.tests.build-logic` 미등재(3A
  in_scope) 라 `buildLogicGateExecutionGate` 가 아직 `TestShapesTest` 의 실행을 강제하지
  않는다. S-2 가 실행을 직접 보인다.
- **F-21~F-23 재현** — `breaking-mutations-selftest.sh` 가 buf 호출 없이 세 함수를
  직접 실측(commands.md S-5).

## 게이트가 잡은 실제 위반(범위 밖)

| 항목 | 값 |
| --- | --- |
| 파일 | `procurement/src/test/kotlin/bidvector/procurement/AccountingTest.kt`(**verifier r1 F-6** — `file:line` 대신 함수 이름으로 인용한다, 3A 수정 즉시 줄 번호가 낡는다) |
| 패턴 | `` @Test fun `property — 음이 아닌 세 값의 합을 received 로 주면 항상 성립한다`() = runBlocking { checkAll(...) { ... } } `` — 식 본문이라 반환 타입이 `Unit` 이 아님(2B 원 사례와 동일 패턴) |
| 커밋 | `b8d4c4e`(M3/3A 레인) — 이 slice 의 base_sha(`7581106`) **이후** 신규. preflight §4.4(현 트리 실측 0건)의 전제가 이 커밋 이후로는 더 이상 사실이 아니다 |
| 담당 레인 | M3/3A(procurement 소유). 이 slice 는 out_of_scope 라 직접 수정하지 않았다 — team-lead 에게 즉시 보고(2026-09-07)하고 3A 레인의 한 줄 수정(`runBlocking { }` 을 블록 본문으로, `=` 제거)을 요청했다 |
| 해소 조건 | 3A 가 위 함수를 블록 본문으로 고친 커밋이 들어온 뒤 `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`(S-0) 재실행이 exit 0. 재실행은 team-lead 지시로 진행 |
| 귀속 증거 | `./gradlew --no-build-cache clean check -x :procurement:testShapeGate`(HEAD `4a73268`) exit 0 — 그 1건을 빼면 전부 초록(commands.md). 이 명령은 acceptance 판정을 대신하지 않는 귀속용이다 |
| **해소** | 3A 커밋 `b72b712`(「F-1 blocker — COL-06 property test 를 JUnit 이 discover 하게 정정」) — S-0 재실행 **exit 0**(head `9baef6b`, commands.md 「S-0 재실행」). 정본 acceptance 가 처음으로 초록 |

이 발견은 게이트의 결함이 아니라 **설계 의도대로 작동한 증거**다(2B 에서 25/27
가짜 초록을 낸 것과 같은 패턴을 처음 실전 코드에서 잡았다) — 다만 그로 인해
S-0/S-1(공유 트리 전체 `clean check`)은 3A 가 고칠 때까지 exit 1 로 남는다.

## verifier r1 일괄 수정 (`_workspace/harness-test-shape/03_verifier_report.md` §7, ready-for-review·low 7·info 1)

한 커밋으로 처리(2026-09-02 차단 문턱 규칙 — 재검증 없이 다음 라운드로). F-1·F-6 은
알려진 제한 문면 갱신(위 두 절), F-2·F-3 은 술어 불변·알려진 제한 등재(아래 5·6),
F-4·F-5 는 장부층(scope.md·rollback.md, 값 대신 명령/재실측), F-7 은 코드 라벨 변경
(`files=` → `scanned=`, `TestShapeGateTask.kt`) + S-2·S-3 재실행(둘 다 exit 0,
commands.md), F-8 은 `breaking-mutations.sh` 주석 한 줄(`COMPILE_ERROR_TYPE` 전역 전제).
F-2·F-3 은 **술어를 건드리지 않았다** — 건드리면 표적 재검증 대상이 된다.

## 알려진 제한

1. **`gate.tests.build-logic` 에 `TestShapesTest` 미등재**(scope.md 착수 시 알려진
   제한 1과 동일, A-8) — `gate-tests.properties` 는 3A in_scope. 3A 종결 뒤 한 줄 병합
   대상. 그때까지 S-2 가 실행을 직접 보인다.
2. **typealias·import alias 로 숨긴 test 어노테이션은 못 본다**(우회 (4), 착수 시 알려진
   제한 2 확장 — **verifier r1 F-1**) — `typealias TA = Test` 뿐 아니라 `import
   org.junit.jupiter.api.Test as TT` + `@TT` 도 같은 이유(PSI 짧은 이름 매칭)로 통과한다
   (실측: probe worktree, `:settlement:testShapeGate` 위반 0). 저자 의도 우회로 경계
   밖 — 완화는 `test.shape.method-annotations` 정책 목록에 별칭을 추가하는 편집.
3. **S-1 은 3A 사유로 붉을 수 있다는 착수 시 서술이 실측으로 갱신됨** — 원 서술은
   「3A 의 미커밋 파일」을 가정했으나, 실제로는 3A 의 **커밋된**(`b8d4c4e`) test
   코드 자체가 원인이다(상세는 위 「게이트가 잡은 실제 위반(범위 밖)」). S-0(격리
   worktree, HEAD 기준)도 커밋된 코드는 그대로 담기 때문에 같은 이유로 붉다 —
   「S-0 이 정본」이라는 서술은 판정 신뢰성(다른 레인의 *미커밋* 파일에 영향받지
   않음)에는 여전히 유효하지만, 「S-0 은 초록일 것」이라는 기대는 3A 종결 전까지
   성립하지 않는다.
4. **S-3 문면과 대소문자 구분 grep 의 불일치**(commands.md 에 상세) — scope.md 가 지정한
   루트 task 이름 `buildLogicTestShapeGate`(대문자 T, 기존 `buildLogicSizeGate` 등 관례
   준수)는 대소문자 구분 `grep -c 'testShapeGate'` 패턴과 정확히 일치하지 않는다(실측
   9, 문면은 10). exit 0(매치 존재)으로 acceptance 자체는 충족되나, 「9+루트=10」 증거는
   보조 대소문자 무시 명령(`grep -ci`, 실측 10)으로 별도 확인했다. 명명 규칙(scope.md
   가 지정)과 acceptance 문구(같은 문서) 사이의 사소한 불일치이며 기능 결함은 아니다.
5. **`@TestFactory fun x() = Unit`(식 본문·명시 타입 없음)은 잡히지 않는다**(**verifier r1
   F-2**) — factory 축은 **명시** 반환 타입만 본다(`typeReference?.text == "Unit"`). 이
   형태는 위협 (A)(조용한 미실행) 밖이다 — JUnit 이 `DynamicNode` 아닌 값을 받으면
   런타임에 시끄럽게 실패하므로 가짜 초록이 아니다. 수정 불요, 완화가 필요해지면 factory
   축도 식 본문을 위반으로 잡도록 A-2 와 대칭시킨다.
6. **`@Test fun x(): kotlin.Unit { }` 이 오탐된다**(**verifier r1 F-3**) — `typeReference.text`
   가 `"kotlin.Unit"`(FQN)이면 `"Unit"` 과 문자열이 달라 「블록 본문이지만 명시 반환
   타입이 Unit 이 아니다」로 잘못 위반 처리한다. 방향이 안전측(A-2 「오탐이 미탐보다
   낫다」)이라 차단 아님 — 고치지 않는다(**술어를 건드리면 표적 재검증 대상**, 2026-09-04
   규칙). 완화가 필요해지면 허용 이름 집합(`Unit`·`kotlin.Unit`)을 정책 키로 둔다.

## 종결 — 운영자 승인 2026-09-07

- verifier r1 `ready-for-review`(`_workspace/harness-test-shape/03_verifier_report.md`, head `a4d5c46` 기준 판정 유지) + low 7·info 1 일괄
  커밋 `e703873` 뒤 **운영자 승인**(「하네스 slice test-discovery-guard 승인」, 2026-09-07). 완료 조건(verifier ready-for-review + 사용자 승인) 충족.
- 승인 시점 in_scope 커밋: `git log --oneline 7581106..e703873 -- build-logic config/quality/test-shape-policy.properties contracts/tools contracts/testdata/breaking reports/evidence/harness/test-discovery-guard reports/evidence/m2/2d/checklist.md docs/discovery/capability-map.md`.
- **승인과 무관하게 열린 것 하나**: 공유 트리의 S-0/S-1 은 3A 소유 `AccountingTest` 의 식 본문 `@Test`(「게이트가 잡은 실제 위반(범위 밖)」 절)가 고쳐질 때까지
  exit 1 — 해소는 3A 레인 커밋 + S-0 재실행 한 줄(commands.md). 이 slice 의 결함이 아니라 게이트의 첫 실측 검출이다.
- `gate.tests.build-logic` 에 `TestShapesTest` 등재는 3A 종결 뒤 한 줄 병합(알려진 제한 1) — 그때까지 실행 증거는 S-2.
