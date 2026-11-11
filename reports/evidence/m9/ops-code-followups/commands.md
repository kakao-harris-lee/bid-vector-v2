# M9/ops-code-followups commands

착수 base `6e6f953c` · 브랜치 `chore/m9-ops-code-followups`. PR #144 수정 라운드에서 main
`c21adb2a` 를 병합했으므로 **rollback 원천은 `c21adb2a`** 다(§2a · `rollback.md`).

## 1. 커밋

스테이징은 경로 개별 인자로만 — `git add <경로들> && git commit … -- <같은 경로들>`(`-A`·`-a`·`.` 없음).

| 커밋 | 무엇 |
| --- | --- |
| `73940c72` | E2E 분할 — test 파일 · 새 fixture 파일 |
| `815eb5b9` | `awardExitCodeOf` KDoc(cr r7 M-2) |
| `b988e7f5` | 원 slice evidence 정정(cr r7 LOW-B·LOW-C) |
| `2719bb32` | 이 slice 의 scope.md |
| `3425bcc6` | commands·checklist·rollback (첫 판정 대상) |
| `3615fc10` | main `c21adb2a` 병합(§2a) |
| 아래 둘 | PR #144 지적 반영 — 코드 KDoc 한 문단 · evidence 문면 |

## 2. acceptance — CI Kotlin job 의 명령 그대로

`./gradlew --no-daemon check` (`bv-build.sh` 경유, 자리 하나) — **exit 0**,
`BUILD SUCCESSFUL in 38m 2s`, 433 actionable(270 executed · 127 from cache · 36 up-to-date),
로그에 `FAILED` **0 회**. 부분 게이트로 갈음하지 않았다.

돈 것 가운데 이 slice 가 거는 축: 열한 모듈의 `sizeGate`·`gateRegistrationGate`·
`gateExecutionGate`·`unusedImportGate`·`typeShapeGate`·`testShapeGate`, 그리고 `leakPatternGate`
(§6)와 `cpdCheck`.

## 2a. 병합 — main `c21adb2a`(#143 gates-build-followups), 겹침 0

PR #144 가 BEHIND 라 팀장 지시로 **SHA 를 고정해** 병합했다(`git merge c21adb2a`, 움직이는
`origin/main` 이 아니다). 충돌 **0**이고, 「충돌 0 이 곧 합집합 정확」은 아니므로 넷을 따로 쟀다.

| 검산 | 실측 |
| --- | --- |
| #143 쪽 파일이 축어로 왔는가 | `git diff c21adb2a..HEAD -- config/quality/ adapters/ build-logic/ reports/evidence/m9/gates-build*` **빈 출력** |
| 내 산출물이 병합으로 움직였는가 | `git diff 3425bcc6..HEAD -- app/ reports/evidence/m9/ops-code*` **빈 출력** |
| properties 중복 키 | `gate-tests` 키 16 · `architecture-policy` 키 162 — **중복 0**(두 파일 다) |
| 등재 계수가 접히지 않았는가 | `gate.tests.adapters` **208 → 209**(#143 의 새 class 하나) · `gate.tests.app` **133 → 133** · 나머지 열한 키 불변 |

**경로 겹침이 0 이다** — #143 은 `adapters/`·`build-logic/`·`config/quality/` 둘·자기 evidence 를
만졌고 내 in_scope 와 교집합이 없다(`git diff --name-only 6e6f953c..c21adb2a -- <내 in_scope>` 빈 출력).
그래서 손 해소가 없고, 합집합 함정(같은 본문을 접기·키 두 번)이 들어설 자리도 없다. 마지막 행이 중요한
이유: `app` 등재 수가 **133 그대로**라 §4 의 「133 = 133」 등식이 병합 뒤에도 성립한다.

병합 뒤 `compileTestKotlin`(전 모듈) **exit 0** — 전건 `check` 는 §2 다.

## 3. ① 분할이 test 를 보존했는가 — 네 축

- **이름 집합**: 분할 전후 `@Test` 함수 이름을 뽑아 정렬해 `diff` — **차이 없음, 다섯**. 전·후 목록을
  각자 파일로 떠서 비교했고 한쪽을 다른 쪽에서 재생하지 않았다.
- **실제로 돌았는가**: `:app:test` 의 JUnit XML 이 그 class 를 `tests="5" skipped="0" failures="0"
  errors="0"` 로 적는다. 「가르고 나서 안 돌린다」가 그 자리에서 보인다 — 그 class 는
  `gate-tests.properties` 에 이미 등재돼 있어 `gateExecutionGate` 도 같은 사실을 단언한다.
- **`@Order` 가 살아 있는가**: XML 의 `testcase` 가 **실행 순서**로 적히고, 그 순서가 `@Order` 1→5
  그대로다(개정 축 → 변한 표 → outbox 실패 → relay → 역할 모집단). 이 순서가 `@BeforeEach` 의 표
  비우기에 짐을 지우는 그 결합이다 — class 를 갈랐다면 여기서 깨진다.
- **크기**: `sizeGate` 리포트에서 test 파일 **347**, 새 fixture 파일 **201**(한도 500, 술어 `> limit`).
  한도 초과 파일 **0**. 이제 저장소 최대 파일은 이 slice 의 것이 아니다.

**가시성 — 이 slice 가 넓힌 표면은 둘뿐이다**: 새 `CommitRunE2EFixture`(internal)와 지역 fake
`AlwaysBidNowMl`(base 의 파일 private → internal). 파일이 갈리면 file-private 로는 보이지 않아 분할에
필요한 변경이고, test 소스셋 안이다. 그 둘이 여는 것과 그 비용은 checklist 알려진 제한 ②가 든다.

## 4. 새 파일이 등재를 요구하지 않는다 — 양방향

`gateRegistrationGate` 의 app 리포트: `population=133` · `excluded=0` · 위반 줄 없음.
`gate.tests.app` 의 등재 수를 따로 세면 **133** — 모집단과 같다(등식이 양쪽으로 성립).
새 fixture class 와 지역 fake 는 리포트의 모집단에도, 등재 목록에도 **없다**.

근거는 선례가 아니라 **게이트 자신의 규칙**이다: 모집단은 컴파일된 class 가운데 추상·인터페이스를
빼고 **discovery 애노테이션이 붙은 메서드를 선언하거나 물려받는 것**이다(`GateRegistration.census`).
`@Test` 가 하나도 없는 class 는 그 술어에 걸리지 않는다. 그래서 `config/quality/gate-tests.properties`
는 이 slice 에서 **한 줄도 바뀌지 않았다**(§7).

## 5. ② KDoc 변경의 거동이 0 인가 — 측정과 음성 대조

- **변경 줄의 성질**: 그 파일의 `git diff` 가 낸 변경 줄 **열넷이 전부 KDoc 본문**이다(` * ` 또는 빈
  ` *`). 코드 줄은 하나도 없다.
- **명령 스트림**: `javap -p -c` 출력(줄 번호 표 제외 정규화)이 base 와 **같다** — 121 줄, `diff` 빈
  출력.
- **음성 대조 — 비교가 공허하지 않다**: 두 class 파일의 `sha256` 은 **다르다**(크기는 같다). `javap -l`
  의 그 함수 첫 줄 번호가 **넷 밀렸다** — 더한 KDoc 네 줄만큼이다. 즉 내 쪽 class 는 **낡은 산출물이
  아니라 편집된 소스로 다시 컴파일된 것**이고, 위의 「명령 스트림 동일」은 그래서 사실을 잰다.
- 이 대조가 **술어 선택도 고친다**: 「class 해시 동일」을 썼다면 KDoc 한 줄만 더해도 붉어진다(줄 번호
  표가 바뀐다). 거동을 재는 자리는 해시가 아니라 명령 스트림이다.

base 쪽 class 는 버릴 clone 을 base `6e6f953c` 로 체크아웃해 `:app:compileKotlin`(exit 0)으로 얻었다.

## 6. 비밀값 스캔

`grep -rniE -f config/quality/leak-patterns.txt` 축의 정본은 CI 의 누출 게이트다(그 어휘를 여기 축어로
적지 않는다). `leakPatternGate` 는 **§2 의 전건 `check` 안에서 돌았고**(task 1회) 그 빌드가 exit 0 이다 —
그 실행의 트리는 `2719bb32` 라 이 디렉터리의 scope.md 도 스캔 입력이었다. 뒤따르는 evidence 커밋은 같은
디렉터리만 만지고, **마지막 HEAD 의 재실측 정본은 verifier 와 PR 조치 코멘트**다.

이 라운드가 더한 문자열에 외부 값이 없다 — 더한 것은 KDoc·주석·evidence 문면과, test fixture 의
**이미 있던** 고정값(컨테이너 이미지 이름 · test 전용 자리지킴 자격값)을 옮긴 것뿐이다.

## 7. clean-tree 와 하네스 레인 변경

- `git status --porcelain -- <in_scope 경로 개별 인자>`(**인자 여섯** — 게이트의 규정 술어가 in_scope
  이고, 그 여섯은 복원 목록 다섯 + 이 slice 의 evidence 디렉터리다) **빈 출력**. 양성 대조 1회 —
  새 fixture 파일에 개행 하나를 붙이면 그 파일 한 줄이 나오고 `truncate -s <원래 바이트>` 로 되돌리면
  다시 빈 출력이며 바이트 수가 같다(비파괴 절삭, `checkout --` 를 쓰지 않았다 — 미커밋 산출물을 함께
  지운다).
- `git diff --name-only 6e6f953c..HEAD -- .claude/ config/ build-logic/ .github/` **빈 출력**.
  하네스·정책·빌드 로직·CI 를 건드리지 않았다.
