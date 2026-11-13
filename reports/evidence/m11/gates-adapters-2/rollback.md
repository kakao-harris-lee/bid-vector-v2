# M11 / gates-adapters-2 — rollback

## 원천과 전제

- **원천**: 이 브랜치가 병합한 **main 커밋 `32dead40`** — 고정 SHA 이고 움직이는 `origin/main` 이 아니다.
  착수 base 는 `94ee445b` 였고 main 병합(`1406f85d`)이 원천을 옮겼다.
- **전제 1 — 그 SHA 에 이 slice 가 없다.** `git merge-base --is-ancestor 2e69d346 32dead40` → **거짓**
  (첫 산출물 커밋 미포함). 실측했다.
- **전제 2 — 공유 파일.** 되돌리는 경로 가운데 다른 레인도 만지는 것은 `docs/discovery/capability-map.md` ·
  `config/quality/{architecture-policy,gate-tests}.properties` 셋이다. main 을 병합했으므로 원천이 그 병합이
  들여온 main 커밋이고, 복원은 **내 줄만 걷고 main 의 줄은 남긴다**(아래 표지 계수가 그것을 센다).
- **전제 3 — 원천을 바꿨으므로 셋째 단언을 잰다: 복원 대상 blob 이 착수 base 와 새 원천에서 같은가.**
  `git diff --name-only 94ee445b 32dead40 -- <복원 목록>` → **빈 출력 = 둘이 같다.** 그 머지(PR #153)가
  만진 것은 `README.md`·`milestone-10.md`·`milestone-11.md` 셋이고 **복원 목록과 교집합이 0** 이기
  때문이다. 그래서 「내 표지 0 · 남의 표지 보존」 계수를 새 원천 기준으로 다시 셀 필요가 없었고,
  그래도 아래 표는 **새 원천에서 다시 센 값**이다.
- **갈음 금지**: ④⑤⑥ 은 「HEAD 가 초록이다」로도 「트리가 같다」로도 갈음하지 않았다. 복원한 트리에서
  실제로 돌렸고, 이 라운드는 `:adapters:test`·`:build-logic:test` 가 **적중이 아니라 실행**됐다.

## 복원 목록 — 기계 산출

`git diff --name-only 32dead40..<마지막 산출물 커밋> | grep -v '^reports/evidence/m11/gates-adapters-2/'`
→ **13 경로**(r1 이 `BytecodeConstantPoolTest.kt` 를 더했다).

```
adapters/src/test/kotlin/bidvector/adapters/architecture/BytecodeConstantPool.kt
adapters/src/test/kotlin/bidvector/adapters/architecture/BytecodeConstantPoolTest.kt
adapters/src/test/kotlin/bidvector/adapters/architecture/ScopedAllowedRootsParityTest.kt
adapters/src/test/kotlin/bidvector/adapters/event/EventAdapterDependencyTest.kt
adapters/src/test/kotlin/bidvector/adapters/extraction/ExtractionAdapterDependencyTest.kt
adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsAdapterDependencyTest.kt
adapters/src/test/kotlin/bidvector/adapters/ml/MlAdapterDependencyTest.kt
adapters/src/test/kotlin/bidvector/adapters/notification/NotificationAdapterDependencyTest.kt
adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceAdapterDependencyTest.kt
build-logic/src/test/kotlin/bidvector/buildlogic/SourceReferencePolicyTest.kt
config/quality/architecture-policy.properties
config/quality/gate-tests.properties
docs/discovery/capability-map.md
```

**`build-logic` 경로가 목록에 있다** — 착수 계약의 out_scope 가 `build-logic/**` 무접촉이었으나 모집단
확장이 그 모듈 test 의 표본 하나를 거짓으로 만들었다(`checklist.md` 계약 갱신 (2)).

**이 slice 의 장부는 목록에서 뺐다.** 그래야 측정 뒤의 장부 커밋이 복원 목록 안 파일을 또 움직이지 않는다
(그러면 재측정이다). 좁은 목록으로도 유효하다 — 장부는 되돌릴 거동을 담지 않는다.

**복원 목록 등식 — 빈 등식을 피해 세 축을 잰다.**

1. **문서 ↔ 기계**: 위 블록의 **12 줄** ↔ 그 `git diff --name-only` 출력 → `comm -3` **양방향 빈 출력**.
2. **「내가 커밋 안 해도 경로가 들어오는가」**: `git log --no-merges --format= --name-only 32dead40..b4fb3117`
   (장부 제외) ↔ 같은 diff 집합 → `comm -3` **빈 출력**, 양쪽 **12**.
3. **복원이 목록 밖을 건드렸는가**: 복원 뒤 clone 의 `git status --porcelain --untracked-files=no` 가 낸
   **12 경로** ↔ 위 목록 → 수가 같고(12 = D 1 + M 11) 목록 밖 **0**.

## 복원 명령

```
git restore --source=32dead40 --staged --worktree -- <위 12 경로를 개별 인자로>
```

range revert 가 아니다. 경로 한정이고, 경로는 개별 인자다. 신설 파일(`BytecodeConstantPool.kt`)은 원천에
없으므로 같은 명령이 **삭제**로 처리한다(아래 ② 의 `D` 하나).

## ①~⑥ 실측 — **r1 이 복원 목록을 바꿔 재측정 대기다**

> **이 절의 수는 r0 의 12 경로에 대한 것이고 현재 목록은 13 경로다.** r1 이
> `BytecodeConstantPoolTest.kt` 를 더했으므로 ② 의 계수(`D 1 + M 11`)가 더는 맞지 않는다
> (새 목록에서는 `D 2 + M 11`이 된다). **재측정은 머지 차례에 한 라운드로 한다**(팀장 지시:
> 11A 가 먼저 머지되면 main 병합 · §12.3 글자 재배정 · 전건 `check`(test 층 강제) · rollback
> 재측정을 함께). 아래는 **그때 버릴 값**이고, 재측정 전까지 이 절은 **미검증**이다.
>
> 바뀌지 않는 것 셋: 원천(`32dead40`) · 전제 1·3 · 복원 명령의 꼴. r1 이 더한 경로는 원천에 없는
> **신설 파일**이라 같은 명령이 삭제로 처리한다.


**실측 HEAD: `b4fb31179dfe991f21cbd13f93e3616906ed3a6f`**(이 slice 의 **마지막 산출물 커밋**).
버릴 clone(`--no-hardlinks`)에서 돌렸다. main 병합(`1406f85d`)은 그 커밋보다 **앞**에 있으므로 실측
자리는 병합 커밋이 아니라 이 커밋이다.

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **0** |
| ② | 복원이 움직인 것의 계수 | **12** = `D` 1(신설 helper 사라짐) + `M` 11. 복원 목록과 수가 같고 **목록 밖은 0** |
| ③ | 복원 뒤 `git diff 32dead40 -- <12 경로>` | **빈 출력(0 바이트)** — 그 경로들의 트리가 원천과 **동일**하다 |
| ④ | 복원 트리 컴파일 | ⑥ 의 실행 안에서 돌았다. 이 라운드는 적중이 아니다 — 433 중 **281 executed** · 152 from cache |
| ⑤ | 복원 트리 test | **`:adapters:test`·`:build-logic:test` 둘 다 실행**(적중 아님). 실패 0 |
| ⑥ | 복원 트리 **게이트** | `./gradlew --no-daemon check` → **BUILD SUCCESSFUL**(36분 16초 · 실행된 task **302** · `FAILED`/`FAILURE` **0**) |

**④⑤ 에 적중으로 비는 자리가 없다** — 10C 라운드와 달리 이 복원 트리에서 compile·test 가 실제로 실행됐다.
그래서 「트리 동일성으로 갈음」을 쓸 필요가 없었다.

## 「내 표지 0 · 남의 표지 보존」 계수

완료는 종료 코드가 아니라 이 두 계수로 센다.

| 표지 | 복원 전 | 복원 후 | 판정 |
| --- | --- | --- | --- |
| 내 — `source.scoped-packages` 의 축 수 | 14 | **8** | 원천과 같다(집합 등식까지 대조) |
| 내 — `internal val ALLOWED_ROOTS` 를 가진 파일 수 | 14 | **8** | 여섯이 `private`·옛 심볼명으로 돌아감 |
| 내 — `BytecodeConstantPool.kt` 존재 | 1 | **0** | 사라짐 |
| 내 — capability map `(k) M11/11C` 절 | 1 | **0** | 사라짐 |
| 내 — capability map 의 `OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` 언급 | 2 | **0** | 사라짐 |
| 내 — `policy.version` | 15 | **14** | 원천 값 |
| 내 — build-logic 표본 `adapters.snapshot` | 1 | **0**(옛 표본 `adapters.persistence` 가 **1** 로 돌아옴) | 되돌아감 |
| 남 — capability map `(j) M10/10D` 절 | 1 | **1** | 보존 |
| 남 — capability map 줄 수 | 4233 | **4206** | **원천과 같다**(같은 명령으로 원천판을 세어 대조: 4206) |
| 남 — `gate-tests.properties` 등재 항목 수 | 556 | **556** | **원천과 같다**(원천판 계수 556). **이 행은 보존의 증거가 약하다** — 그 파일에 대한 내 편집이 주석뿐이라 항목 수가 애초에 움직이지 않는다. 이 파일의 보존을 실제로 지는 것은 ③(복원 뒤 원천과 0 바이트 차이)이다 |
| 남 — `milestone-11.md`(main 이 더한 파일) | 1 | **1** | 보존 — 복원 목록 밖이라 손대지 않는다 |

## 되돌림 뒤 남는 것

장부(`reports/evidence/m11/gates-adapters-2/*.md`)는 복원 대상이 아니라 그대로 남는다. 거동을 담지 않고,
지우면 「무엇을 왜 되돌렸는가」가 사라진다. capability map §12.3 **(k) 는 복원된다** — 그 절이 적는 종결
넷과 신설 OPEN 하나가 함께 사라지므로 `OPEN-M10-SCOPED-PACKAGES-COVERAGE` 와 10C 등재 ⓐⓑⓒ 가 **열린
상태로 되돌아온다**. 그것이 되돌림의 뜻이다.

## 유효성 — 실측 HEAD 와 판정 SHA 사이에 되돌림 대상이 움직였는가

verifier 가 대조할 것은 「실측 HEAD == 판정 SHA」가 **아니다**(장부 커밋은 언제나 뒤에 온다). 보아야 하는
것은 그 사이에 **복원 목록 안의 경로가 움직였는가**다.

```
git diff --name-only b4fb3117..<판정 SHA> -- <위 12 경로를 개별 인자로>
git rev-list --merges b4fb3117..<판정 SHA>
```

→ 둘 다 빈 출력이면 이 실측은 유효하다. 이 slice 의 장부 커밋은 그 **12 경로**를 건드리지 않고(장부를
목록에서 뺀 이유가 그것이다), main 병합은 **마지막 산출물 커밋보다 앞**에 있다. 한 줄이라도 나오면
미검증이고 다시 잰다.

**전칭식 — 신설 파일 예외.** 위 두 출력이 비지 않더라도, 나온 경로가 전부 **원천 `32dead40` 에 없는 신설
파일**이면 유효하다. 열거와 원천 부재 확인은 verifier 가 한다.
