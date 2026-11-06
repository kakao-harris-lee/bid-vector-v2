# M9/adr07-pitest checklist

판정 SHA **`0b9ce7a0`**(둘째 병합 커밋 — 수정 라운드 3 + main `97ed49c4` 병합) · base `df4976cc`. 실측은 `commands.md`, 되돌림은
`rollback.md`. 앞 라운드가 이 자리에 적은 `f4a85991` 은 그 뒤에 온 산출물 커밋들보다 앞선
SHA 였다(v L-1) — 아래 §6~§9 가 라운드별로 가른다.

## 1. 계약의 결정 넷

| 결정 | 상태 | 어디에 |
| --- | --- | --- |
| D-1 순수 도메인 모듈 한정 · `check` 미결합 · 별도 task + 별도 CI job | 충족 | 대상 제약은 convention 이 `layer.domain` 과 대조해 **구성 단계에서** 끊는다(대조 C-7). `check` 와의 분리는 양쪽을 세어 실측(S-1·S-2). 진입점은 루트 `mutationCheck` 와 CI `mutation` job |
| D-1 의 열린 축: PR 마다 / main 만 / 수동 | **PR 마다** | 「안 돌린 게이트도 아무것도 막지 못한다」가 저장소의 CI 규율이고, 미룬 래칫은 게이트가 아니라 장부가 된다. 재검토는 `OPEN-ADR07-MUTATION-BUDGET` 로 등재 |
| D-2 절대값 래칫 파일 | 충족 | `config/quality/mutation-policy.properties`. **세 축**(점수·변이 수·변이 클래스 수) × 일곱 모듈 = 값 스물한 개, 전부 실측 |
| D-3 첫 산출은 비용 실측 | 충족 | `commands.md` §2 — 모듈별 core·wall·peak RSS·변이·탐지·점수·클래스. 한 모듈씩, 매 실행 전 호스트 점검 |
| D-4 ADR 0007 §5 갱신(OPEN 닫음) | 충족 | `OPEN-ADR-07` 해소 + 후속 OPEN 둘 등재 |

## 2. 계약의 acceptance 셋

| acceptance | 결과 |
| --- | --- |
| pitest task 가 대상 모듈에서 돌아 리포트 산출 | `mutationCheck` exit 0. 일곱 모듈의 `pitest` 가 **실행**(UP-TO-DATE 아님)되고 `mutations.xml` 을 냈다 |
| 래칫 위반(임계 위로 올린 값) → 실패 양성 대조 | 세 축 각각 C-1·C-2·C-3. 술어를 바꾼 커밋 뒤 경계 한 칸(83 → 84)으로 재대조 |
| CI 새 job 이 PR 에서 돈다 | **PR CI 가 정본** — 로컬에서 워크플로를 실행하지 않는다. 로컬이 댈 수 있는 것(① YAML 구문·job 다섯 ② 기존 job 무편집 ③ `mutationCheck` 가 실제로 초록)은 전부 댔다 |
| 로컬 pitest 는 한 모듈씩 · 실행 전 메모리 확인 | 지켰다. 비용표는 모듈마다 별도 실행, 매 실행 전 `pgrep`·`free`·RSS 상위 확인 |

## 3. 설계 검토(우회 열)와 그것을 막은 자리의 대응

`_workspace/m9-adr07-pitest/01_design-review.md` 의 우회 열 열 개에 대응한다.

| 우회 | 막은 자리 | 실측 |
| --- | --- | --- |
| 1 모듈에서 convention 적용을 뺀다 | `mutationCoverageGate` 양방향 등식 | C-6 |
| 2 `targetClasses`·`excludedClasses` 로 어려운 클래스를 숨긴다 | `mutations.min`·`classes.min` | C-2·C-3 |
| 3 mutator 집합을 약하게 바꾼다 | `mutations.min` | C-2(같은 축) |
| 4 `failWhenNoMutations=false` + 대상 0 | `mutations.min`(0/0 을 100% 로 접지 않는다) | 단위 test 「변이가 0 이면 점수를 100 으로 접지 않고 위반으로 낸다」 |
| 5 도구의 `mutationThreshold` 를 0 으로 둔다 | **쓰지 않는다** — 판정은 리포트에서 재계산 | 배선에 그 키가 없다. C-1 이 도구 임계 없이 실패한다 |
| 6 `pitest` 없이 낡은 리포트로 통과 | 게이트 `dependsOn(pitest)` + `pitest` `finalizedBy(게이트)` **+ 리포트를 task 지문에 넣는다**(수정 라운드) | C-5 · **R-4/R-5**(회귀 test 셋) |
| **6′** 리포트가 나빠져도 게이트가 UP-TO-DATE 로 건너뛴다 — **앞 판에서 열려 있었다** | 리포트 선언 **하나**가 경로와 지문을 함께 낸다(부재 허용 입력) | R-4·R-7 이 각 구판에서 RED · R-5·R-8 이 처방 뒤 초록. 음성 대조가 「항상 재실행」과 가른다 |
| **6″** 지문 배선이 **공유 convention 의 한 줄**이라 그 줄만 지우면 test 는 초록 | 지문용 property 를 없앴다 — 지울 둘째 줄이 **없다** | R-7 RED 선행 · 프로덕션 배선 A/B/C · 변이(지문 annotation 바꿔치움 → RED) |
| **6‴** pitest 의 `reportDir` 만 옮기고 **옛 리포트를 남긴다** — 게이트가 좋은 낡은 수를 읽는다 | 게이트의 자리를 pitest task 속성에서 파생한다 — 자리가 **한 값**이고 옮기면 함께 움직인다 | R-10 RED 선행(구판 꼴에서 거짓 초록) · R-11 양방향 통제군 · `--rerun` A/B/C · 변이(파생 → 고정 경로 ⇒ RED) |
| 7 XML 을 끄고 HTML 만 낸다 | 정책 해석이 XML 부재를 거부 | 단위 test 「출력 형식에 XML 이 없으면 거부한다」 |
| 8 CI job 을 지운다 / `continue-on-error` | **막지 않는다** — 워크플로 diff 가시성 | 경계 문장이 선언한 범위 밖 |
| 9 `threads` 를 올려 점수를 흔든다 | 정책 데이터가 고정 | `TIMED_OUT` 0 개 — 현 설정에서 흔들릴 변이가 없다 |
| 10 남의 모듈 산출물을 끌어와 점수를 올린다 | 리포트 클래스 ⊆ 그 모듈 main 산출물 | 단위 test 「리포트가 그 모듈 산출물 밖의 클래스를 담으면 위반이다」 |

**모듈을 대상 목록에 더하고 래칫만 빼는** 조용한 면제도 닫았다 — 래칫 키 부재는 정책 오류다
(단위 test 「등재된 모듈의 래칫 키가 없으면 거부한다」). 수정 라운드가 그 가족의 **둘째 자리**도
닫았다: 키를 **두고 값을 0 으로** 두는 경로(C-8).

**이 표의 6′ 이 앞 판에서 놓친 자리다.** 양성 대조 일곱이 전부 **선언된 입력**(정책 파일·빌드
스크립트)만 바꿔, 「선언 밖 리포트만 바뀐」 경로를 한 번도 지나지 않았다 — 설계 검토의 우회 6 을
「닫았다」고 적은 근거(C-5)는 **직전 대조의 실패가 task 이력을 무효화한 상태**에서 나온 초록이었다.
우회 열거는 맞았고 **그 열거를 재는 대조의 입력 축이 한 가지였던 것**이 틈이었다.

## 4. 수정 라운드가 만든 새 파일 ↔ in_scope 대조

유도 기준은 **이 브랜치가 마지막으로 합친 main 의 고정 SHA**다 — `git log --no-merges --format=
--name-only HEAD ^97ed49c4 | sort -u` = **27 경로 + evidence 넷 = 31**(수정 라운드 3 이 main 소스
하나를 더했고, 셋째 병합은 내 경로를 늘리지 않는다). 착수 base(`^df4976cc`)로 유도하면
그 사이에 합친 main 의 **남의 커밋 경로까지** 들어와 집합이 부풀고, 같은 목록을 되돌림에 쓰면
남의 변경을 지운다(그 실물이 `rollback.md` 에 있다).

31 경로 전부 계약의 in_scope 안이다 — `gradle/libs.versions.toml` · `build-logic/**` · 대상 모듈
`build.gradle.kts`(일곱) · `.github/workflows/ci.yml` · `config/quality/**` ·
`docs/adr/0007-test-pyramid-and-ratchet.md` · `docs/discovery/capability-map.md`(계약 갱신
2026-11-06) · `tools/workflow-step-refs-check.sh`(주석만) · evidence. **범위 밖 파일 0.**

열넷째 수정 경로가 **계약 갱신으로 들어온 공유 파일**이다. scope.md 의 「계약 갱신」 절이 승인
사실과 사유를 들고, rollback 쪽은 그 파일만 전제 둘(원천에 이 slice 없음 · 이 브랜치에 머지된
#133 의 추가분 없음)을 따로 쟀다.

`_workspace/m9-adr07-pitest/` 는 gitignore 아래라 커밋에 들어가지 않는다.

## 5. 알려진 제한

1. **Kotlin junk 변이가 절대 점수에 상수로 섞인다.** 거를 유일한 기성 수단이 상용(Arcmutate)이고
   그것은 외부 비용 결정이라 만들지 않고 등재했다(`OPEN-ADR07-KOTLIN-JUNK`). 내장 `FKOTLIN`
   필터는 `.kt` 의 line 0 변이만 거른다(바이트코드 실측). 귀결: 점수가 테스트 강도를 **과소**
   보고하고, 새 data class 를 더하면 그 junk 만큼 점수가 내려가 래칫이 붉을 수 있다 — 그 방향은
   미탐이 아니라 **오탐(fail-loud)** 이다.
2. **`milestone-6.md` 완료 조건(「중요 mutation 생존 0」)을 이 래칫이 대체하지 않는다.** 래칫은
   「내려가지 않는다」를 재고 완료 조건은 「0 이다」를 묻는다. 현 실측의 생존은 0 이 아니다. 그
   간극의 처리는 사용자 승인 사항이고 구현이 스스로 선언하지 않는다.
3. ~~CI 러너의 실제 소요는 재지 않았다~~ — **첫 PR CI 가 냈다.** `mutation` job **pass ·
   10분 58초**(PR #136, head `fda6bea3` 의 run). 로컬 직렬 실측 10분 11초와 같은 자리이고 job
   상한 45분에 여유가 크다. 같은 run 의 다른 job 은 `check` 53분 29초 · `container` 9분 9초 ·
   `ml-engine` 6분 59초 · `web` 5분 20초 — 전부 pass. 이 수는 **그 SHA** 의 것이고 이후 커밋
   (처방·병합·evidence)의 run 은 팀장이 push 한 뒤에 난다.
4. ~~registry 행 하나가 이 slice 밖이다~~ — **처리됨(계약 갱신 2026-11-06, 팀장 승인).**
   `docs/discovery/capability-map.md` **§14.2** 의 `OPEN-ADR-07` 행을 `OPEN-ADR-08` 선례 꼴로
   닫았고(앞선 보고의 「§12」는 틀렸다 — scope.md 「계약 갱신」의 정정 항목이 정본),
   수정 라운드에서 **후속 OPEN 둘을 §12 G6 에 행으로 세우고 머리말에 자기 증감 +2 를 적었다**
   (cr M-2, in_scope 재확장 승인). `OPEN-ADR-07` 해소는 그 +2 에 넣지 않는다 — §14.2 항목이라
   머리말의 51 에 들어 있지 않다(두 번 세지 않기 위한 명시).
5. **컴파일 실패 하네스 test 를 변이 분석에서 빼지 않았다.** 그 test 들은 변이를 하나도 죽이지
   못하지만(귀속 계수로 확인) 분석 시간이 분 단위 안이라 그대로 뒀다 — 빼려면 test 이름 패턴을
   정책에 적어야 하고 그 열거가 낡는 쪽 대가가 더 크다.
6. **낡은 `.class` 가 「산출물 밖 클래스」 단언을 넓힌다**(cr L-5, 미조치로 등재). `compiledClasses`
   가 지워진 타입의 낡은 class 파일까지 담으면 부분집합 단언이 그만큼 느슨해진다 — 오탐이 아니라
   **미탐** 방향이다. 넷째 단언은 「남의 산출물을 끌어온 경로」를 잡으려는 것이고 그 경로는 낡은
   `.class` 와 무관하므로 차단으로 보지 않았다. clean 빌드(CI)에서는 그 자리가 없다.
7. **잘린 `mutations.xml` 은 게이트의 말이 아니라 XML 파서 예외로 죽는다**(cr OQ-1, 미조치로 등재).
   부재·빈 파일은 게이트의 사유로 잡지만 중간에 끊긴 리포트는 SAX 예외 원문이 나온다. 러너에서
   실제로 나는지 미확인이고, `--max-workers=1` 이 그 확률을 낮추는 쪽이라 이번에 손대지 않았다.
8. **`Isolated Projects` 와는 맞지 않는 꼴이다**(cr OQ-2, 미조치로 등재). subproject 가
   `rootProject.tasks.named(...)` 를 설정하는 형태이고 이는 `qualityBaseline` 선례와 같은 자리다 —
   오늘은 configuration cache 만 켜져 있어 문제가 없고, 그 기능을 켜는 날 일곱 모듈이 함께 걸린다.
9. **artifact 업로드가 `if: always()` 다**(cr L-9, 미조치로 등재). 선언한 사유는 「붉을 때 무엇이
   살아남았는가」인데 초록에도 일곱 모듈 HTML 을 14일 보관한다. 비용·보관 판단이라
   `OPEN-ADR07-MUTATION-BUDGET` 이 받는다.
10. **키 꼴이 한 파일 안에서 갈린다**(cr L-7, 미조치로 등재). `mutation.modules`·`ratchet.*` 는
    이름공간이 있고 `threads`·`output.formats` 는 없다. 바꾸면 정책 키 이름이 변해 `policy.version`
    을 올려야 하므로 값싼 변경이 아니다.

11. **프로덕션 배선의 up-to-date 축을 모듈 하나(settlement)에서만 쟀다**(수정 라운드 2). 일곱
    모듈이 같은 convention 의 같은 선언을 쓰므로 모양은 같지만 「일곱 전부」를 그 축으로 재지는
    않았다 — 전건은 CI `mutation` job 이 돈다. 그 측정은 pitest 를 다시 돌리지 않고 앞 라운드가
    남긴 리포트를 입력으로 썼다(재는 것이 변이 분석이 아니라 up-to-date 판정이므로).

## 6. 수정 라운드 1 (2026-11-06) — 리뷰 지적의 처리

verifier **ready-for-review** · code-reviewer **request_changes**. 아래가 처리 대응표다.

| # | 지적 | 처리 | 실측 |
| --- | --- | --- | --- |
| cr H-1 = v M-1 | 래칫 판정 근거(`mutations.xml`)가 선언 입력 밖이라 「test 만 약해진」 경우에 게이트가 UP-TO-DATE 로 건너뛴다 — **거짓 초록** | 리포트를 **부재를 허용하는 파일 컬렉션**으로 지문에 넣는다. `@InputFile` 을 피한 사유(존재 검증이 「없다」 가지를 죽인다)는 그대로 지켜진다 | 회귀 test 셋(구판 RED 선행) + 프로덕션 배선 e2e 대조 — `commands.md` §9 |
| cr H-2 | 전건 `check` 가 판정 SHA 에서 미실측 | **팀장 판단: 로컬에서 돌리지 않는다 — PR CI 가 정본.** 대신 표적 실행이 안 도는 등재·실행 등식 게이트 셋을 돌렸다 | `buildLogicGateRegistrationGate`·`buildLogicGateExecutionGate`·`leakPatternGate` exit 0 |
| cr H-3 | CI 에서 pitest 가 모듈 병렬로 뜨는데 그 메모리를 아무도 재지 않았다 | job 에 `--max-workers=1` — 실측과 같은 직렬 | 직렬 `mutationCheck` exit 0 · 10분 11초 |
| cr M-1 | 계약·커밋 메시지가 `§12` 를 가리키는데 실제 편집은 `§14.2` | **앞 라운드에 이미 정정됐다** — 리뷰가 읽은 evidence SHA(`41168ed9`)가 정정 커밋(`e47435c4`)보다 앞선다 | scope.md·checklist 에 `§14.2` 세 자리 · `§12` 오지목 0 |
| cr M-2 | 새 OPEN 둘이 §12 활성 집계·G6 밖이고 ADR 에서 `####` 라 활성 스캔에 안 보인다 | G6 에 두 행 + 머리말 자기 증감 **+2** · ADR 에서 `###` 로 올려 해소 절 밖에 둔다 | G6 표 열 수 4(이웃과 같음) · 기존 행 무이동 |
| cr M-3 | `one-command-check.sh` 가 새 게이트 job 을 모른다 | **팀장 판단: 스크립트를 건드리지 않는다.** ADR §5 와 이 문서에 제외 근거를 적는다 — 직렬 11분 대를 로컬 한 방 검사에 넣으면 그 검사가 쓰이지 않게 된다 | — |
| cr L-1 | configuration cache 사유 문면이 Gradle 9 에서 부정확 | 세 자리의 사유를 「warm CC 에서도 정책만 바꾼 편집이 task 를 다시 돌게 한다」로 바꿨다 | — |
| cr L-2 | `policy.version` 을 아무도 읽지 않는다 | 읽어 **게이트 리포트에 적는다**(`SizeGateTask` 관례) | 두 리포트에 `policyVersion=1` |
| cr L-3 | `mutation.modules ⊆ layer.domain` 을 읽는 쪽이 재지 않는다 | coverage gate 의 **셋째 축** | C-9 |
| cr L-4 | 래칫 하한 0 을 금지하는 단언이 없다 | 세 축 모두 양수 요구 | C-8 + 단위 test |
| cr L-8 | step-refs baseline 주석의 job 수가 낡았다 | 재실측해 갱신(하한은 그대로) | `workflow-step-refs-check.sh` exit 0 · job 5 |
| cr L-5·L-7·L-9 · OQ-1·OQ-2 | — | **미조치로 등재** | §5 의 6~10 |
| v L-1 | 판정 SHA 문면이 실제와 다르다 | 두 문서 머리말을 현 판정 SHA 로 고치고 **어느 수가 어느 SHA 의 것인지 절로 가른다** | `commands.md` 머리말 |

**수정 라운드 2 는 새 파일을 만들지 않았다.** 편집한 넷(`build-logic` 셋 · ADR 하나)이 전부
앞 라운드까지의 in_scope 안이고, 지운 것은 task 의 public property 하나와 그와 함께 쓰인 import
하나다 — 경로 집합이 늘지 않았으므로 계약 갱신 대상도 없다(`git log --no-merges --format=
--name-only HEAD ^3ad4f3f6 | sort -u` 30 경로, 범위 밖 **0**).

**수정 라운드 1 이 만든 새 파일 ↔ in_scope 대조**: 새 파일 하나
(`MutationReportGateUpToDateTest.kt`, `build-logic/**` 안)이고 **범위 밖 0**. 그 test 클래스는
만드는 커밋에서 `gate-tests.properties` 에 사전순 등재됐고, 그 등재 등식을 표적 실행이 아니라
`buildLogicGateRegistrationGate` 로 확인했다. `tools/workflow-step-refs-check.sh` 는 이 라운드에
in_scope 로 승인된 경로다(주석만).

**둘째 병합(2026-11-06, 수정 라운드 2)** — main `3ad4f3f6`(#135)를 합쳤다. 겹치는 파일이
**0** 이고(내 경로 30 ∩ main 증분 14 = ∅) 합집합을 등식 셋으로 댔다(`commands.md` §11.4).
rollback 원천은 그 고정 SHA 로 다시 옮겼다.

**첫 병합(2026-11-06)** — main `e12c0485` 를 합쳤다. 겹친 세 파일의 합집합을 산술로 댔고
(`commands.md` §10) 표적 게이트 셋이 병합 뒤에도 exit 0 이다. rollback 원천은 그 고정 SHA 로
옮겼다 — main 이 `gate-tests.properties` 의 다른 블록을 바꿔, base 복원이면 남의 셋을 지운다.

## 7. 수정 라운드 2 (2026-11-06) — 지적 하나

| # | 지적 | 처리 | 실측 |
| --- | --- | --- | --- |
| v r2 M-1 | 리포트 지문이 공유 convention 의 **배선 한 줄**에 걸려 있다 — 그 줄을 지우면 TestKit 셋은 초록이고(fixture 가 두 property 를 스스로 묶는다) 프로덕션 증분은 거짓 초록으로 돌아간다 | 지문용 property 를 **없애고** 리포트 선언 자체를 부재 허용 입력으로 둔다. 경로와 지문이 같은 선언이라 **반쪽 배선이 성립하지 않는다**. `@InputFile`(존재 검증)로 바꾸지 않는 이유는 그대로다 — 그 검증이 「리포트가 없다」 가지를 죽인다 | `commands.md` §11 — RED 선행(R-7) · 처방 뒤 초록(R-8·R-9) · 프로덕션 배선 A/B/C · 변이 RED |

**이 라운드의 처방은 「술어를 더하는」 것이 아니라 「술어가 걸릴 자리를 하나로 줄이는」 것이다.**
앞 라운드는 선언을 더해 지문을 만들었고(옳았다) 그 선언이 **배선 한 줄에 의존**했다. 지금은
선언이 하나라 convention 에서 지울 수 있는 줄이 `mutationXmlReport.set(...)` 뿐이고, 그것을
지우면 값이 없어 게이트가 **즉시 실패**한다(조용한 통과가 아니다).

## 8. 수정 라운드 3 (2026-11-06) — 지적 하나

| # | 지적 | 처리 | 실측 |
| --- | --- | --- | --- |
| v r3 L-1(low, 비차단) | 게이트가 읽는 자리와 pitest 가 쓰는 자리를 convention 의 **지역 값 하나**가 묶는다 — 한쪽만 옮기고 옛 리포트를 남기면 로컬 증분 빌드가 좋은 낡은 수로 통과한다(B5) | 자리를 **도구에서 받아온다**: `pitestMutationXmlReport()` 가 pitest task 의 `reportDir` 에서 파생하고 convention 은 그 한 줄만 쓴다. Gradle 이 권하는 producer→consumer 배선이라 게이트가 산출물 소비자로 선언되고 자리를 옮기면 양쪽이 함께 움직인다 | `commands.md` §12 — RED 선행(R-10, 거동) · 양방향 통제군 · `--rerun` A/B/C · 변이 RED |

**M-1 과 같은 원칙의 둘째 적용이다** — 쓰이는 값과 그 값의 출처가 같은 것이어야 한다. M-1 은
「경로와 지문」을 한 선언으로 모았고, L-1 은 「도구가 쓰는 자리와 게이트가 읽는 자리」를 한 값으로
모았다. 둘 다 **갈라질 수 있는 둘째 자리를 없애는** 처방이고, 남는 우회는 그 한 줄을 일부러
고쳐 쓰는 것(= diff 에 보이는 편집)뿐이다.

**이 라운드의 회귀 test 는 실제 pitest plugin 을 적용한다** — 앞선 fixture 셋은 plugin 없이 task
만 등록했고, 그래서 「도구가 쓰는 자리」를 재는 축이 그 안에 없었다. 새 fixture 는 plugin 을
적용하고 `reportDir` 을 옮긴다. pitest 자체는 돌리지 않는다(`-x pitest`) — 재는 것은 분석이 아니라
**게이트가 어느 파일을 읽는가**이고, 파생 배선이 게이트를 pitest 의 소비자로 묶으므로 빼지 않으면
PIT 가 실제로 뜬다.

## 9. 셋째 병합 (2026-11-06) — main `97ed49c4`(#134)

겹치는 파일이 **하나**이고 그것이 등록부다 — 내가 `build-logic` 블록을 +4 했고 main 이 같은
파일의 **주석 문단**을 고쳤다. 등록부가 겹치면 자동 병합의 「충돌 0」은 아무 증거가 아니므로
블록별 등식·중복·정렬을 기계로 셌다(`commands.md` §13.1~§13.3): 블록 열여섯 전부 base + 양쪽
증분과 일치, 중복 0, 사전순이 아닌 블록은 세 판에 걸쳐 **같은 셋**(이 병합이 만든 것이 아니다).

**내 첫 기대가 한 번 틀렸다** — main 이 지운 주석 문면을 「병합 뒤 0」으로 기대했는데 1 이었다.
그 문면은 원천에 **둘**(두 adapter 문단) 있었고 main 이 앞의 하나만 고쳤다. main 자신의 수도 1 이라
병합이 접거나 살려낸 것이 아니다 — 술어를 고치고 수를 그대로 적는다.

모든 task 에 `--rerun` 을 걸어 다시 쟀다(§13.4). pitest 자체는 다시 돌리지 않았다 — main 증분이
변이 분석 축을 건드리지 않았고(기계 확인), 그 축의 실측은 처방 커밋에서 `--rerun` 으로 돌린
§12 가 든다. 되돌림 원천은 `97ed49c4` 로 옮겨 재실측했다.

## 10. 자기 승인 아님

이 문서는 구현 레인이 쓴 것이고 판정이 아니다. 독립 검증(verifier)과 리뷰는 다른 패스가 한다.
