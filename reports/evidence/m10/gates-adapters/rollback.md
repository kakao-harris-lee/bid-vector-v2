# M10 / gates-adapters — rollback

## 원천과 전제

- **원천**: base main `8062dcd784cf81905c50a2c8706d8bbd3ea5263e`.
- **전제 1 — 그 SHA 에 이 slice 가 없다.** `git merge-base --is-ancestor 7b02b3cc <원천>` → **거짓**(첫 산출물
  커밋 미포함). 실측했다.
- **전제 2 — 공유 파일.** 되돌리는 경로 가운데 다른 레인도 만지는 것은 `docs/discovery/capability-map.md` ·
  `config/quality/{gate-tests,architecture-policy}.properties` 셋이다. 이 브랜치는 **main 을 병합하지 않았으므로**
  그 셋의 원천판 == 「내 것을 뺀 현재판」이고, 복원이 남의 줄을 지우지 않는다(아래 계수로 확인). **머지 전에
  main 을 병합하면 원천을 그 병합 커밋 SHA 로 바꿔 다시 잰다** — 움직이는 `origin/main` 은 쓰지 않는다.
- **갈음 금지**: ④⑤⑥ 은 「HEAD 가 초록이다」로 갈음하지 않았다. 복원한 트리에서 실제로 돌렸다.

## 복원 목록 — 기계 산출

`git diff --name-only <원천>..HEAD | grep -v '^reports/evidence/m10/gates-adapters/'` → **14 경로**.

```
adapters/build.gradle.kts
adapters/src/test/kotlin/bidvector/adapters/architecture/ScopedAllowedRootsParityTest.kt
adapters/src/test/kotlin/bidvector/adapters/audit/AuditAdapterDependencyTest.kt
adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationAdapterDependencyTest.kt
adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationAllowedRootsParityTest.kt
adapters/src/test/kotlin/bidvector/adapters/notice/NoticeAdapterDependencyTest.kt
adapters/src/test/kotlin/bidvector/adapters/opening/OpeningAdapterDependencyTest.kt
adapters/src/test/kotlin/bidvector/adapters/profile/ProfileAdapterDependencyTest.kt
adapters/src/test/kotlin/bidvector/adapters/qualification/QualificationAdapterDependencyTest.kt
adapters/src/test/kotlin/bidvector/adapters/relay/RelayAdapterDependencyTest.kt
adapters/src/test/kotlin/bidvector/adapters/strategy/StrategyAdapterDependencyTest.kt
config/quality/architecture-policy.properties
config/quality/gate-tests.properties
docs/discovery/capability-map.md
```

**`git mv` 의 양쪽이 다 들어 있다** — 새 경로(`architecture/…ScopedAllowedRootsParityTest.kt`)와 옛 경로
(`evaluation/…EvaluationAllowedRootsParityTest.kt`). 한쪽만 주면 복원이 반쪽이 된다.

**목록이 12 에서 14 로 늘었다.** 계약 갱신 (5) 의 좌표 치환 둘(`adapters/build.gradle.kts` ·
`EvaluationAdapterDependencyTest.kt`)이 뒤 라운드에 들어왔고, 그 커밋이 **마지막 산출물 커밋을 옮겼다** —
그래서 앞 라운드의 실측을 옮기지 않고 **새 HEAD 에서 다시 쟀다**.

**이 slice 의 장부 넷은 목록에서 뺐다.** 그래야 측정 뒤의 장부 커밋이 복원 목록 안 파일을 또 움직이지
않는다(그러면 재측정이다). 좁은 목록으로도 유효하다 — 장부는 되돌릴 거동을 담지 않는다.

**복원 목록 등식 — 두 축을 잰다.** 같은 명령의 출력을 자기와 견주면 빈 등식이 나오고, 빈 등식은 건전성의
증거가 아니다. 그래서 잰 것은 다음 둘이다.

1. **문서 ↔ 기계**: 위 블록에 적힌 12 줄 ↔ 그 `git diff --name-only` 출력 → `comm -3` **양방향 빈 출력**.
   문서가 기계 산출을 베껴 적은 것이 아니라 **같은 집합**임을 뜻한다.
2. **「내가 커밋 안 해도 경로가 들어오는가」**: `git log --no-merges --format= --name-only <원천>..HEAD | sort -u`
   ↔ 같은 diff 집합 → `comm -3` **빈 출력**. 내 커밋 **넷**(병합 커밋 **0**) 밖에서 들어온 경로가 없다 —
   그래서 이 목록은 「되돌릴 것」과 「내가 만든 것」이 같다.

## 복원 명령

```
git restore --source=8062dcd784cf81905c50a2c8706d8bbd3ea5263e --staged --worktree -- <위 14 경로를 개별 인자로>
```

range revert 가 아니다. 경로 한정이고, 경로는 개별 인자다.

## ①~⑥ 실측

**실측 HEAD: `ddba9277`**(이 slice 의 **마지막 산출물 커밋**). 버릴 clone(`--no-hardlinks`)에서 돌렸다.

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **0** |
| ② | 복원이 움직인 것의 D/M 계수 | **14** = `A` 1(옛 class 되살아남) + `D` 1(새 class 사라짐) + `M` 12. 복원 목록과 수가 같다 |
| ③ | 복원 뒤 `git diff <원천> -- <14 경로>` | **빈 출력** — 그 경로들의 트리가 원천과 **동일**하다 |
| ④ | 복원 트리 컴파일 | ⑥ 의 실행 안에서 돌았다(`:adapters:compileTestKotlin` 은 **FROM-CACHE** — 복원 트리의 입력이 원천과 같으니 적중이 맞다) |
| ⑤ | 복원 트리 test | 같은 실행 — `:adapters:test`·`:app:test`·`:build-logic:test` **실행**(`:workflow:test` 는 FROM-CACHE). 복원 트리에 `ScopedAllowedRootsParityTest` 언급 **0**, 옛 class 가 그 자리에 있다 |
| ⑥ | 복원 트리 **게이트** | `./gradlew --no-daemon check` → **BUILD SUCCESSFUL**(36m 10s · 433 actionable 중 **278 executed** · 게이트 task **172** 실행 · `FAILED`/`FAILURE` **0**) |

### ④⑤⑥ 의 갈음 — 트리 동일성 (「HEAD 초록」이 아니다)

전건 `check` 는 앞 HEAD(`6787ba70`)에서 만든 **첫 clone** 의 복원 트리에서 돌았다. 그것으로 `ddba9277` 의
복원 트리를 갈음할 수 있는 근거는 **두 복원 트리가 같은 트리라는 실측**이다 — 추측이 아니다.

| 축 | 명령 | 결과 |
| --- | --- | --- |
| index 수준 | `diff <(clone1: git ls-files -s \| sort) <(clone2: 같은 명령)` | **빈 출력** — 추적 파일의 **blob SHA 와 모드가 전수 일치** |
| 작업 트리 내용 | `diff -rq --exclude=.git --exclude=build --exclude=.gradle <clone1> <clone2>` | **빈 출력** — 파일 내용도 전수 일치 |

둘이 같아지는 이유는 우연이 아니다 — **첫 clone 은 계약 갱신 (5) 의 좌표 치환 둘이 커밋되기 전에
떠졌고**, 그 두 파일은 그 clone 에서 이미 원천 내용이었다. 그래서 12 경로 복원과 14 경로 복원이 **같은
트리**에 도달한다. 갈음의 근거는 「앞 HEAD 가 초록이었다」가 **아니라** 「되돌린 트리의 파일이 이미 실측한
트리와 같다」다.

**`reports/evidence/**` 는 `check` 안 task 의 선언 입력이다**(`leakPatternGate`, 루트 `check` 가 의존).
그래서 두 트리의 동일성은 `reports/` 까지 포함해야 성립하는데, 위 두 축이 **추적 파일 전수**를 보므로
그 조건이 함께 확인됐다. 같은 사실 때문에 **최종 acceptance 는 장부 커밋 뒤의 마지막 HEAD 에서** 돈다.

### 「내 표지 0 · 남의 표지 보존」 계수

완료는 종료 코드가 아니라 이 두 계수로 센다.

| 표지 | 복원 전 | 복원 후 | 판정 |
| --- | --- | --- | --- |
| 내 — `architecture/` 디렉터리의 파일 | 1 | **0** | 사라짐 |
| 내 — `internal val ALLOWED_ROOTS` 를 가진 파일 | 8 | **1**(evaluation 만, 원천 상태) | 일곱이 `private` 로 돌아감 |
| 내 — `OPEN-M10-SCOPED-PACKAGES-COVERAGE` | 1 | **0** | 사라짐 |
| 내 — capability map `(i) M10/10C` | 1 | **0** | 사라짐 |
| 내 — `ScopedAllowedRootsParityTest` 를 담은 파일 전수 | 4 | **1** | 남은 하나는 **이 slice 의 장부**(scope.md)이고 복원 대상이 아니다 — 아래 「되돌림 뒤 남는 것」 |
| 남 — 옛 class `evaluation/…ParityTest.kt` | 0 | **1** | 되살아남 |
| 남 — capability map 줄 수 | 4105 | **4090**(= 원천) | 남의 본문 보존 |
| 남 — capability map `(d) 하네스·6G 잔여` 절 | 1 | **1** | 보존 |
| 남 — `gate.tests.adapters` 등재 항목 수 | 209 | **209**(= 원천) | 내 항목 하나가 빠지고 옛 항목 하나가 돌아와 같다 |
| 남 — `EvaluationAdapterDependencyTest.kt` 줄 수 | 499 | **499** | `sizeGate` 한도에 한 줄 남은 그 파일이 양쪽에서 같다(치환이라 줄이 움직이지 않는다) |

## 유효성 — 실측 HEAD 와 판정 SHA 사이에 되돌림 대상이 움직였는가

verifier 가 대조할 것은 「실측 HEAD == 판정 SHA」가 **아니다**(장부 커밋은 언제나 뒤에 온다). 보아야 하는
것은 그 사이에 **복원 목록 안의 경로가 움직였는가**다.

```
git diff --name-only ddba9277..<판정 SHA> -- <위 14 경로를 개별 인자로>
```

→ 빈 출력이면 이 실측은 유효하다. 이 slice 의 장부 커밋은 그 12 경로를 **건드리지 않는다**(장부를 목록에서
뺀 이유가 그것이다). 한 줄이라도 나오면 미검증이고 다시 잰다.

## 되돌림 뒤 남는 것

장부 넷(`reports/evidence/m10/gates-adapters/*.md`)은 복원 대상이 아니라 그대로 남는다. 거동을 담지 않고,
지우면 「무엇을 왜 되돌렸는가」가 사라진다. capability map §12.3 (i) 는 **복원된다** — 그 절이 적는 종결
가운데 ①은 되돌려지고, ②·③ 의 종결 등재(선행 머지가 닫은 것)도 함께 사라지므로 **그 둘의 장부 공백이
되돌아온다**. 그 공백은 거동이 아니라 장부의 상태이고, 되돌림이 필요한 상황이라면 다음 장부 라운드가
그 둘만 다시 등재한다.
