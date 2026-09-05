# Slice 계약 — M1 / 1A-b · 래칫·게이트 확장 (하네스)

```yaml
milestone: m1
slice: 1a-b-ratchet-and-gate-extension
base_sha: e0aae7f2242f3cffe8f4c32bae2ecec7a3b89b46
head_sha: 리뷰 시점의 HEAD
  # 하네스 slice 다 — 산출물이 곧 게이트 정의(build-logic/** · config/quality/**)다. 1B 위협 모델이
  # 「방어하지 않는다」로 둔 자리를 이 slice 가 편집한다는 뜻이며, 그래서 이 slice 의 검증은
  # 게이트가 「잡는가」(음성 fixture)와 「정당한 코드를 막지 않는가」(양성 = 현재 저장소 전건 초록) 둘이다.
in_scope:
  - build-logic/src/main/kotlin/bidvector/buildlogic/SizeGateTask.kt        # ① 타입 멤버 수 상한 축(PSI)
  - build-logic/src/main/kotlin/bidvector/buildlogic/**                     # ①② 새 task 가 필요하면 신설(TypeShapeGate 류) — 이름·분할은 구현 판단
  - build-logic/src/main/kotlin/bidvector.kotlin-conventions.gradle.kts    # ①②④ 배선 · expectedSourceSets 정책화
  - build-logic/src/main/kotlin/bidvector.quality-baseline.gradle.kts      # ② 래칫 baseline 을 리포트가 같은 수를 내도록
  - build-logic/build.gradle.kts                                            # ③ CPD 플러그인 의존
  - build-logic/src/test/**                                                 # ①②④ 음성·양성 test(1A 관례)
  - build.gradle.kts                                                        # ③ 조건부 — CPD 집계 task 를 루트에 매달 때만
  - gradle/libs.versions.toml                                               # ③ cpd 플러그인·PMD toolVersion 좌표
  - config/quality/size-policy.properties                                   # ①② limit.type.members=30 · ratchet.type.* baseline 값 (policy.version 승급)
  - config/quality/architecture-policy.properties                           # ④ 조건부 — testFixtures 정책 키를 여기 둘 때만
  - config/quality/duplicate-policy.properties                              # ③ 신설 — CPD language·minimumTokenCount·mode(observe|fail) 정책 데이터
  - config/quality/gate-tests.properties                                    # ④ 조건부 — 새 게이트 test 등재
  - app/src/test/kotlin/bidvector/app/architecture/**                       # ④ ArchUnit 스캔이 testFixtures 산출물을 거르게(1B-c 실측 사각)
  - app/src/test/kotlin/bidvector/app/conformance/CorpusExecutors.kt        # ⑤ 1B-c verifier r2 low ① — fixture 번호 대조 접두 안전화(한 줄)
  - shared-kernel/src/test/**                                               # ⑥ 조건부 — BaseAmount 기본값 부재 compile fixture 13(1B-c 알려진 제한) 한정
  - docs/discovery/capability-map.md                                        # 계약 갱신 — §14.2 의 낡은 milestone-1.md 좌표 4건(:80·:82·:98·:99) 인용문 정정 +
                                                                             #   OPEN-ADR-06·OPEN-1BC-TESTFIXTURES-GATE 행 해소 표기만
  - docs/adr/0007-test-pyramid-and-ratchet.md                               # 계약 갱신 — §5 해소 절에 「배선 완료」 포인터 한 줄 + 이 slice 가 낸 결정(D-11 류) 등재만
  - milestone-1.md                                                          # 계약 갱신 — 「Slice 1A-b」 항목만
  - reports/evidence/m1/1a-b/**
out_of_scope:
  - shared-kernel/src/main/** · 다른 도메인 모듈 main                       # 게이트가 재는 대상이지 편집 대상이 아니다. 새 게이트가 현 코드를 빨갛게
                                                                             #   만들면 임계를 낮추는 것이 아니라 그 사실을 보고한다(임계는 운영자 결정)
  - fixtures/** · fixtures/tools/**                                         # 스윕 종료 코드(1B-c 참고 부채)는 별도 결정 — 이 slice 는 등재만
  - CPD 실패 모드 전환                                                      # 1C 종료 시 운영자 결정(OPEN-ADR-16 해소문)
  - detekt 규칙 추가·detekt 버전 변경                                       # alpha 종속 회피(ADR 0007 OPEN-ADR-08 해소문)
  - 새 도메인 모듈·bid-vector/ symlink·_workspace/**
  - 승인 문서 편집 일체(위 승격 행·항목 밖)
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # H-0 (= 1A A-0)
  - "./gradlew --no-build-cache clean check"                                                          # H-1 — 새 게이트 셋이 check 에 매달려 현 저장소 전건 초록(양성)
  - "./gradlew :build-logic:test"                                                                     # H-2 — 새 축 음성·양성 순수 함수 test(1A A-5 승계)
  - "./gradlew :app:test --tests '*ArchitectureGate*' --tests '*Conformance*'"                        # H-3 — ArchUnit testFixtures 필터 · runner low ①
  - "./gradlew qualityBaseline"                                                                       # H-4 — 래칫 baseline 과 리포트 수치 일치
  - "./gradlew <cpd task> && test -s <cpd report xml>"                                                # H-5 — Gradle 9.6.1 스모크 = 관찰 모드 리포트 산출(OPEN-ADR-16 조건). 실패 시 (b) 후퇴 보고
  - "./gradlew :shared-kernel:test"                                                                   # H-6 — 조건부(⑥ fixture 13)
rollback: |
    **정본은 `reports/evidence/m1/1a-b/rollback.md`**(구현 레인이 scope.md 파생으로 쓴다). 경로 한정
    `git restore --source=<base> --staged --worktree -- <in_scope 경로 개별 인자>` + 신규 경로 `git rm`, 임시 clone 실측.
    **비활성화 경로는 두지 않는다** — 게이트를 끄는 스위치를 만드는 것이 곧 위협 모델의 우회다. 되돌림은 revert 만.
```

작성: 2026-09-05, 세션 모델(Fable 5.1) 단독 — 기획 문서 레인. 착수 근거: 운영자 결정 2026-09-05(OPEN-ADR-06 (a) · OPEN-ADR-16 (a),
`docs/adr/0007` §5 해소 절), `milestone-1.md` 「Slice 1A-b」, 1B-c 종결 승인과 함께 난 착수 지시(2026-09-05, r2 low ① 이월 포함).

---

## 하네스 레인 변경 (상시 절)

이 slice **자체가 하네스 레인**이다 — `build-logic/**`·`config/quality/**` 편집이 산출물이다. 그러므로 이 절이 가르는 것은
`CLAUDE.md`·`.claude/**`(오케스트레이션 하네스) 편집뿐이다: `git log --oneline e0aae7f2242f3cffe8f4c32bae2ecec7a3b89b46..HEAD -- CLAUDE.md .claude/` — 착수 시점 **없음**.

---

## 이 slice 가 하는 일

| # | 일 | 근거 결정 | 레인 |
| --- | --- | --- | --- |
| ① | **타입 멤버 수 상한 30** — `sizeGate` 에 셋째 축. Kotlin PSI **소스** 기준(`internal` 오판 없음). 「멤버」의 정의는 아래 D-1 | OPEN-ADR-06 (a), 운영자 값 30 | kotlin-implementer |
| ② | **상속 깊이·구현 인터페이스 수 래칫** — baseline(현 최대: 깊이 2 · 인터페이스 1) 대비 증가 = 실패. 값은 `size-policy.properties` 의 `ratchet.type.*` 키(정책 데이터, 매직 넘버 금지). 측정은 바이트코드(ArchUnit `ClassFileImporter`, `qualityBaseline` 과 같은 계수) — PSI 로는 상위 타입 해석이 안 된다 | OPEN-ADR-06 (a) | kotlin-implementer |
| ③ | **PMD CPD 배선(관찰 모드)** — `de.aaschmid.cpd` + `toolVersion`(PMD 7.x), `language=kotlin`, `minimumTokenCount=50`, `ignoreFailures` 로 리포트만. `check` 에 매달아 리포트 산출 자체는 강제(산출 없음 = 실패). 정책 데이터는 `duplicate-policy.properties` | OPEN-ADR-16 (a) | kotlin-implementer |
| ④ | **testFixtures 게이트 넷** — `java-test-fixtures` 적용 시 **명시 사유로 실패**(D-2), `expectedSourceSets` 하드코딩을 정책 키로, `moduleDependencyGate` 가 `testFixtures*` classpath 버킷을 검사, ArchUnit 스캔이 testFixtures 산출물을 거름(방어 심층) | `OPEN-1BC-TESTFIXTURES-GATE` | kotlin-implementer |
| ⑤ | **runner fixture 번호 대조 접두 안전화** — `contains("fixture N")` → 단어 경계 | 1B-c verifier r2 low ①(운영자 이월) | kotlin-implementer |
| ⑥ | (조건부) **compile fixture 13** — `BaseAmount(won, currency)` 가 `no value passed for parameter` 로 실패 = 기본값 부재의 회귀 test | 1B-c 알려진 제한(fixture 7 은 다른 명제) | kotlin-implementer |
| ⑦ | **낡은 좌표 4건 정정** — capability-map §14.2 의 `milestone-1.md:80·82·98·99` 를 인용문 형태로 | evidence-pack 낡는 좌표 규격, 1B-c 역방향 파급 등재 | 세션 모델(정본 문서) |

---

## 결정 — 계약이 고정하는 것 (세션 모델 판단, Phase 2.5 에서 검토)

| id | 결정 | 내용 · 근거 |
| --- | --- | --- |
| **D-1** | 「타입 멤버」의 정의 | 클래스·객체·인터페이스 **본문의 선언 수** = 프로퍼티 + 함수 + 보조 생성자 + `init` 블록 + 프로퍼티 접근자 본문은 프로퍼티에 포함(따로 세지 않음). **중첩·companion 타입은 자기 타입으로 따로 계수**(멤버 아님 — 그 자체가 mixin 팽창의 우회가 아니라 분리다, 단 파일 축이 받는다). enum entry 는 멤버가 아니다(데이터). 주 생성자 파라미터 프로퍼티(`val x` in ctor)는 프로퍼티로 센다. **바이트코드 계수(qualityBaseline 최대 20)보다 PSI 계수는 작거나 같다**(합성 멤버 없음) — 상한 30 은 그래서 보수적이다. Phase 2 가 shared-kernel 최대 타입에서 둘을 실측해 대조한다 |
| **D-2** | testFixtures 정책 | **전 모듈에서 `java-test-fixtures` 를 허용하지 않는다**(현 `expectedSourceSets={main,test}` 의 사실을 정책으로 승격, 이유를 문면에). 허용하려면 게이트 넷이 그 source set 을 어떻게 다룰지(순수성 규칙 적용/면제)를 ADR 로 먼저 정해야 하고 지금 그 요구가 없다(1B-c 가 공개 API 로 해결). `plugins.withId("java-test-fixtures")` 감지 → 명시 사유로 실패가 가장 싼 구성적 차단이며, source set 집합 비교·classpath 버킷·ArchUnit 필터는 **방어 심층**(플러그인 없이 같은 이름의 source set 을 손으로 만드는 경로를 잡는다) |
| **D-3** | 래칫의 의미 | 「증가 금지」— `ratchet.type.inheritance-depth.max`·`ratchet.type.interfaces.max` 를 현 최대값으로 두고, 어느 타입이든 초과하면 실패. **낮추는 방향은 자동**(값이 내려가면 리포트가 새 최대를 보이고 다음 slice 가 키를 내린다 — 자동 갱신 스위치는 두지 않는다, 그것이 우회다). 올리려면 ADR 0007 개정 |
| **D-4** | CPD 관찰 모드의 「강제」 | 실패시키지 않되 **리포트 산출을 `check` 가 요구**한다 — 관찰 모드가 「실행 안 함」으로 조용히 퇴화하지 않게. 실패 모드 전환은 정책 키 `mode=fail` 한 줄이며 그 편집은 운영자 결정(1C 종료 시) — 이 slice 가 스위치를 만드는 것은 위협 모델 안이다(게이트 정의 편집 = 하네스 저자, 방어 밖) |
| **D-5** | 새 축이 현 코드를 빨갛게 하면 | 임계·baseline 을 손대지 않고 **보고**한다 — 운영자가 임계를 다시 정한다(1A D-6 「수치를 자기 승인하지 않는다」). Phase 2 실측상 현 저장소는 셋 다 초록이어야 한다(최대 멤버 20 < 30, 깊이 2, 인터페이스 1) |

---

## 위협 모델 — 1A-b 고유 경계 (Phase 2.5, 세션 모델 직접)

**(0) 경계 문장.** 이 slice 의 게이트가 **방어하는 것**: 도메인·모듈 **소스** 수준에서 (a) 타입 하나가 멤버를 쌓아 커지는 팽창,
(b) 상속·인터페이스 합성(legacy mixin 패턴, ADR 0007 §1.3)으로 파일·함수 한도를 우회하는 팽창, (c) `testFixtures` 라는
게이트 사각 source set 의 도입, (d) 중복 helper 의 **관찰**(실패시키지 않음). **방어하지 않는 것**: `build-logic/**`·
`config/quality/**` 를 편집하는 저자(`milestone-1.md` 「게이트 위협 모델」 그대로 — 이 slice 자신이 그 편집이다), 리플렉션·
바이트코드 조작, CPD 가 재지 못하는 중복(토큰 50 미만 조각·의미적 중복), 관찰 모드에서의 중복 자체(1C 종료 시 결정).
**요구 축소가 아닌 이유**: `v2-지침서.md` §5 「크기와 결합도」와 ADR 0007 D-5 는 **회귀(drift)** 방어를 말하고, 적대적
저자를 상정하지 않는다 — 1B 위협 모델(운영자 결정 2026-09-03)과 같은 경계다.

**우회 경로와 막는 자리(≥5)**:
1. 멤버를 **확장 함수**로 파일 밖에 빼 타입을 작게 보이게 — 이 축의 대상이 아니다(확장 함수는 타입 멤버가 아니라 정의상 밖). 그 코드는 **파일·함수 축**이 받고, 확장 함수 남용은 결합도 축(fan-in/out, qualityBaseline)이 관찰한다. **등재**: 이 축은 mixin 합성 팽창을 겨눈다.
2. 인터페이스 다중 구현 대신 **`by` 위임** — supertype 목록에 그대로 나타나 인터페이스 수에 잡힌다(바이트코드 `rawInterfaces`). Phase 2 가 실측.
3. **중첩 클래스·companion 으로 분산** — 각 타입이 따로 계수되어 한 타입은 작아지지만 파일 축(500)이 받는다. **등재**: 의도된 분리와 우회를 게이트는 구별하지 않는다 — 리뷰 몫.
4. **sealed 계층 깊이 증가**(정당한 도메인 모델링) — 래칫이 잡는다. 정당하면 ADR 개정으로 키를 올린다(D-3). 자동 갱신 스위치는 없다.
5. `java-test-fixtures` 없이 **같은 이름의 source set 을 손으로 등록** — source set 집합 정확 비교가 잡는다(D-2 방어 심층). 다른 이름(`integrationTest`)도 같은 비교가 잡는다.
6. CPD **토큰 50 미만으로 잘라 붙이기** — 재지 못한다. **등재**(관찰 한계).
7. `-x sizeGate`·`enabled=false`·정책 키 편집 — 경계 밖(하네스 저자).

---

## 조사(Phase 2) 항목 — `_workspace/m1-1a-b/01_scout_preflight.md`

CPD 플러그인 Gradle 9.6.1 스모크(H-5 전건) · PSI 대 바이트코드 멤버 계수 대조(D-1) · 9 모듈 depth/interfaces 현재값(D-3 baseline) ·
게이트 음성 test 관례 · testFixtures 넷의 편집 지점 · 낡은 좌표 4건 · runner low ① 코드 · 스윕 종료 코드(참고).

## OPEN — 수령·신설

| id | 상태 |
| --- | --- |
| `OPEN-1BC-TESTFIXTURES-GATE` | **이 slice 가 닫는다**(④, D-2) |
| ~~`OPEN-ADR-06`~~ · ~~`OPEN-ADR-16`~~ | 결정은 났고 **배선을 이 slice 가 한다**(①②③). 배선 완료 시 ADR §5 해소 절에 포인터 |
| 스윕 종료 코드(1B-c 참고 부채) | 이 slice 밖 — 등재만. 도구를 게이트로 쓸 slice 가 붙인다 |
