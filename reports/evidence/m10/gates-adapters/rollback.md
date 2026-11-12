# M10 / gates-adapters — rollback

## 원천과 전제

- **원천**: 이 브랜치가 병합한 **main 커밋 `67ff54b1f349ea917e50137b91ded8df73a491f1`** — 고정 SHA 이고
  움직이는 `origin/main` 이 아니다. 착수 base 는 `8062dcd7` 였고 main 병합이 원천을 옮겼다.
- **전제 1 — 그 SHA 에 이 slice 가 없다.** `git merge-base --is-ancestor 7b02b3cc <원천>` → **거짓**(첫 산출물
  커밋 미포함). 실측했다.
- **전제 2 — 공유 파일.** 되돌리는 경로 가운데 다른 레인도 만지는 것은 `docs/discovery/capability-map.md` ·
  `config/quality/{gate-tests,architecture-policy}.properties` 셋이다. main 을 **병합했으므로** 원천이 그 병합
  커밋이고, 복원은 **내 줄만 걷고 main 의 줄은 남긴다**(아래 표지 계수가 그것을 센다).
- **전제 3 — 원천을 바꿨으므로 셋째 단언을 잰다: 복원 대상 blob 이 착수 base 와 새 원천에서 같은가.**
  `git diff --name-status 8062dcd7 <원천> -- <14 경로>` → **둘이 다르다**:
  `config/quality/gate-tests.properties`(main 이 app test 항목 하나를 더했다) ·
  `docs/discovery/capability-map.md`(main 이 §12.3 (f)·(h) 를 더했다). 그래서 「내 표지 0 · 남의 표지 보존」
  계수를 **새 원천 기준으로 다시 셌다** — 그 둘에서 **main 의 표지가 복원 뒤에도 남는가**가 이 단언의 핵이고,
  아래 표의 「남」 행 넷이 그 자리다.
- **갈음 금지**: ④⑤⑥ 은 「HEAD 가 초록이다」로도 「트리가 같다」로도 갈음하지 않았다. 복원한 트리에서 실제로
  돌렸다.

## 복원 목록 — 기계 산출

`git diff --name-only <원천>..<마지막 산출물 커밋> | grep -v '^reports/evidence/m10/gates-adapters/'` → **14 경로**.

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

**이 slice 의 장부 넷은 목록에서 뺐다.** 그래야 측정 뒤의 장부 커밋이 복원 목록 안 파일을 또 움직이지
않는다(그러면 재측정이다). 좁은 목록으로도 유효하다 — 장부는 되돌릴 거동을 담지 않는다.

**복원 목록 등식 — 세 축을 잰다.** 같은 명령의 출력을 자기와 견주면 빈 등식이 나오고, 빈 등식은 건전성의
증거가 아니다. 그래서 잰 것은 다음 셋이다.

1. **문서 ↔ 기계**: 위 블록의 **14 줄** ↔ 그 `git diff --name-only` 출력 → `comm -3` **양방향 빈 출력**.
   문서가 기계 산출을 베껴 적은 것이 아니라 **같은 집합**임을 뜻한다.
2. **「내가 커밋 안 해도 경로가 들어오는가」**: `git log --no-merges --format= --name-only <원천>..<마지막
   산출물 커밋> | sort -u`(장부 제외) ↔ 같은 diff 집합 → `comm -3` **빈 출력**, 양쪽 **14**. 그 범위의 내
   커밋은 **아홉**이고 병합 커밋은 **하나**(`--no-merges` 가 뺀다 — 그 병합이 원천을 들여온 커밋이다). 내
   커밋 밖에서 들어온 경로가 없으므로 이 목록은 「되돌릴 것」과 「내가 만든 것」이 같다.
3. **복원이 목록 밖을 건드렸는가**: 복원 뒤 clone 의 `git status --porcelain --untracked-files=no` 가 낸
   **14 경로** ↔ 위 목록 → `comm -3` **빈 출력**.

## 복원 명령

```
git restore --source=67ff54b1f349ea917e50137b91ded8df73a491f1 --staged --worktree -- <위 14 경로를 개별 인자로>
```

range revert 가 아니다. 경로 한정이고, 경로는 개별 인자다.

## ①~⑥ 실측

**실측 HEAD: `854a2e12`**(이 slice 의 **마지막 산출물 커밋**). 버릴 clone(`--no-hardlinks`)에서 돌렸다.
main 병합은 그 커밋보다 **앞**에 있으므로 실측 자리는 병합 커밋이 아니라 이 커밋이다.

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **0** |
| ② | 복원이 움직인 것의 계수 | **14** = `D` 1(새 class 사라짐) + `A` 1(옛 class 되살아남) + `M` 12. 복원 목록과 수가 같고 **목록 밖은 0**(위 등식 3) |
| ③ | 복원 뒤 `git diff <원천> -- <14 경로>` | **빈 출력(0 바이트)** — 그 경로들의 트리가 원천과 **동일**하다 |
| ④ | 복원 트리 컴파일 | ⑥ 의 실행 안에서 돌았다 — **열한 모듈의 `compileTestKotlin` 이 전부 FROM-CACHE**(복원 트리의 컴파일 입력이 원천과 같으니 적중이 맞다. 적중은 실행이 아니므로 실행은 ⑤⑥ 쪽에서 따로 센다) |
| ⑤ | 복원 트리 test | 같은 실행 — `:adapters:test`·`:app:test`·`:build-logic:test` **실행**(나머지 여덟 모듈은 FROM-CACHE). 로그에 `ScopedAllowedRootsParityTest` 언급 **0** — 복원 트리에는 그 class 가 없고 옛 class 가 그 자리에 있다 |
| ⑥ | 복원 트리 **게이트** | `./gradlew --no-daemon check` → **BUILD SUCCESSFUL**(37m 9s · 433 actionable 중 **278 executed** · 게이트 task **172** 실행 · `FAILED`/`FAILURE` **0** · `leakPatternGate`·루트 `:check` 실행) |

## 「내 표지 0 · 남의 표지 보존」 계수

완료는 종료 코드가 아니라 이 두 계수로 센다. **새 원천 기준으로 다시 센 값**이다(전제 3).

| 표지 | 복원 전 | 복원 후 | 판정 |
| --- | --- | --- | --- |
| 내 — `architecture/` 디렉터리의 파일 | 1 | **0** | 사라짐 |
| 내 — `internal val ALLOWED_ROOTS` 를 가진 파일 | 8 | **1**(evaluation 만, 원천 상태) | 일곱이 `private` 로 돌아감 |
| 내 — capability map 의 `OPEN-M10-SCOPED-PACKAGES-COVERAGE` | 1 | **0** | 사라짐 |
| 내 — capability map `(i) M10/10C` 절 | 1 | **0** | 사라짐 |
| 내 — `ScopedAllowedRootsParityTest` 를 담은 파일 전수 | 7 | **3** | 남은 셋은 **이 slice 의 장부**(scope·commands·rollback)이고 복원 대상이 아니다 — 위 「되돌림 뒤 남는 것」 |
| 남 — 옛 class `evaluation/…ParityTest.kt` | 0 | **1** | 되살아남 |
| 남 — capability map 줄 수 | 4157 | **4142** | **원천과 같다**(같은 명령으로 원천판을 세어 대조) — main 의 (f)·(h) 를 포함한 본문이 보존됐다 |
| 남 — capability map `(d)`·`(f)`·`(h)` 절 수 | 3 | **3** | **main 이 더한 둘이 복원 뒤에도 남는다**(전제 3 의 핵) |
| 남 — `gate-tests.properties` 의 `OpenApiBidCaseContractTest`(main 이 더한 줄) | 1 | **1** | 보존 |
| 남 — `gate.tests.adapters` 등재 항목 수 | 같다 | **같다** | 내 항목 하나가 빠지고 옛 항목 하나가 돌아와 수가 같다(같은 분해로 양쪽을 세어 대조) |
| 남 — `EvaluationAdapterDependencyTest.kt` 줄 수 | 499 | **499** | `sizeGate` 한도에 한 줄 남은 그 파일이 양쪽에서 같다(치환이라 줄이 움직이지 않는다) |

## 되돌림 뒤 남는 것

장부 넷(`reports/evidence/m10/gates-adapters/*.md`)은 복원 대상이 아니라 그대로 남는다. 거동을 담지 않고,
지우면 「무엇을 왜 되돌렸는가」가 사라진다. capability map §12.3 (i) 는 **복원된다** — 그 절이 적는 종결
가운데 ①은 되돌려지고, ②·③ 의 종결 등재(선행 머지가 닫은 것)도 함께 사라지므로 **그 둘의 장부 공백이
되돌아온다**. 그 공백은 거동이 아니라 장부의 상태이고, 되돌림이 필요한 상황이라면 다음 장부 라운드가
그 둘만 다시 등재한다.

## 유효성 — 실측 HEAD 와 판정 SHA 사이에 되돌림 대상이 움직였는가

verifier 가 대조할 것은 「실측 HEAD == 판정 SHA」가 **아니다**(장부 커밋은 언제나 뒤에 온다). 보아야 하는
것은 그 사이에 **복원 목록 안의 경로가 움직였는가**다.

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- <위 14 경로를 개별 인자로>
git rev-list --merges <실측 HEAD>..<판정 SHA>
```

→ 둘 다 빈 출력이면 이 실측은 유효하다. 이 slice 의 장부 커밋은 그 **14 경로**를 건드리지 않고(장부를
목록에서 뺀 이유가 그것이다), main 병합은 **마지막 산출물 커밋보다 앞**에 있다(병합이 뒤에 오면 실측
자리가 그 병합 커밋이 된다). 한 줄이라도 나오면 미검증이고 다시 잰다.
