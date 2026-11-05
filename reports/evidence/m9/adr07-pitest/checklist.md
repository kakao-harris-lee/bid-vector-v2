# M9/adr07-pitest checklist

판정 SHA **`f4a85991`** · base `df4976cc`. 실측은 `commands.md`, 되돌림은 `rollback.md`.

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
| 6 `pitest` 없이 낡은 리포트로 통과 | 게이트 `dependsOn(pitest)` + `pitest` `finalizedBy(게이트)` | C-5(리포트 부재가 **죽은 가지가 아님**을 실측) |
| 7 XML 을 끄고 HTML 만 낸다 | 정책 해석이 XML 부재를 거부 | 단위 test 「출력 형식에 XML 이 없으면 거부한다」 |
| 8 CI job 을 지운다 / `continue-on-error` | **막지 않는다** — 워크플로 diff 가시성 | 경계 문장이 선언한 범위 밖 |
| 9 `threads` 를 올려 점수를 흔든다 | 정책 데이터가 고정 | `TIMED_OUT` 0 개 — 현 설정에서 흔들릴 변이가 없다 |
| 10 남의 모듈 산출물을 끌어와 점수를 올린다 | 리포트 클래스 ⊆ 그 모듈 main 산출물 | 단위 test 「리포트가 그 모듈 산출물 밖의 클래스를 담으면 위반이다」 |

**모듈을 대상 목록에 더하고 래칫만 빼는** 조용한 면제도 닫았다 — 래칫 키 부재는 정책 오류다
(단위 test 「등재된 모듈의 래칫 키가 없으면 거부한다」).

## 4. 수정 라운드가 만든 새 파일 ↔ in_scope 대조

`git log --no-merges --format= --name-only HEAD ^df4976cc | sort -u` = **24 경로 + evidence**.
전부 계약의 in_scope 안이다 — `gradle/libs.versions.toml` · `build-logic/**` · 대상 모듈
`build.gradle.kts`(일곱) · `.github/workflows/ci.yml` · `config/quality/**` ·
`docs/adr/0007-test-pyramid-and-ratchet.md` · `docs/discovery/capability-map.md`(계약 갱신
2026-11-06) · evidence. **범위 밖 파일 0.**

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
3. **CI 러너의 실제 소요는 재지 않았다.** 로컬 실측(core 합 11분 24초)에서 상한 45분을 잡았을
   뿐이고, 러너의 단일 코어 속도는 이 호스트와 다르다. 첫 PR CI 가 그 수를 낸다.
4. ~~registry 행 하나가 이 slice 밖이다~~ — **해소(계약 갱신 2026-11-06, 팀장 승인).**
   `docs/discovery/capability-map.md` §12 의 `OPEN-ADR-07` 행을 `OPEN-ADR-08` 선례 꼴로 닫았다.
   **남는 것 하나**: 후속 OPEN 둘(`OPEN-ADR07-KOTLIN-JUNK`·`OPEN-ADR07-MUTATION-BUDGET`)은 그
   표에 **자기 행을 갖지 않는다** — 승인 범위가 「한 행만」이라 닫는 행이 이름으로 가리키고 전문은
   ADR 0007 §5 가 든다. 그 둘을 표의 행으로 세우는 것은 장부 slice 의 일이다.
5. **컴파일 실패 하네스 test 를 변이 분석에서 빼지 않았다.** 그 test 들은 변이를 하나도 죽이지
   못하지만(귀속 계수로 확인) 분석 시간이 분 단위 안이라 그대로 뒀다 — 빼려면 test 이름 패턴을
   정책에 적어야 하고 그 열거가 낡는 쪽 대가가 더 크다.

## 6. 자기 승인 아님

이 문서는 구현 레인이 쓴 것이고 판정이 아니다. 독립 검증(verifier)과 리뷰는 다른 패스가 한다.
