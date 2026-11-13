# M11 / gates-adapters-2 — rollback

## 원천과 전제

- **원천**: 이 브랜치가 병합한 **main 커밋 `8562b610`** — 고정 SHA 이고 움직이는 `origin/main` 이 아니다.
  착수 base 는 `94ee445b` 였고 **머지 차례의 main 병합(`3e6d042e`)이 원천을 여기로 옮겼다** — 앞선 넷(11A·11E·11B·11D)이 먼저 머지된 뒤다.
- **전제 1 — 그 SHA 에 이 slice 가 없다.** `git merge-base --is-ancestor 2e69d346 8562b610` → **거짓**
  (첫 산출물 커밋 미포함). 실측했다.
- **전제 2 — 공유 파일.** 되돌리는 경로 가운데 다른 레인도 만지는 것은 `docs/discovery/capability-map.md` ·
  `config/quality/{architecture-policy,gate-tests}.properties` 셋이다. main 을 병합했으므로 원천이 그 병합이
  들여온 main 커밋이고, 복원은 **내 줄만 걷고 main 의 줄은 남긴다**(아래 표지 계수가 그것을 센다).
- **전제 3 — 원천을 바꿨으므로 셋째 단언을 잰다: 복원 대상 blob 이 착수 base 와 새 원천에서 같은가.**
  `git diff --name-only 94ee445b 8562b610 -- <복원 목록>` → **빈 출력이 아니다.** 갈린 둘은
  `config/quality/gate-tests.properties` · `docs/discovery/capability-map.md` 이고, 앞선 네 slice
  (11A·11E·11B·11D)가 그 둘을 만졌기 때문이다. **그래서 「내 표지 0 · 남의 표지 보존」을 새 원천
  기준으로 다시 셌다** — 앞 라운드처럼 공짜로 성립하지 않는다. 아래 표의 「남」 행 넷이 그 자리이고,
  핵은 **(k)~(n) 네 절과 11A 의 등재가 복원 뒤에도 남는가**다.
- **갈음 금지**: ④⑤⑥ 은 「HEAD 가 초록이다」로도 「트리가 같다」로도 갈음하지 않았다. 복원한 트리에서
  실제로 돌렸고, 이 라운드는 `:adapters:test`·`:build-logic:test` 가 **적중이 아니라 실행**됐다.

## 복원 목록 — 기계 산출

`git diff --name-only 8562b610..<마지막 산출물 커밋> | grep -v '^reports/evidence/m11/gates-adapters-2/'`
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
2. **「내가 커밋 안 해도 경로가 들어오는가」**: `git log --no-merges --format= --name-only 8562b610..3e6d042e`
   (장부 제외) ↔ 같은 diff 집합 → `comm -3` **빈 출력**, 양쪽 **13**(실측).
3. **복원이 목록 밖을 건드렸는가**: 복원 뒤 clone 의 `git status --porcelain --untracked-files=no` 가 낸
   **12 경로** ↔ 위 목록 → 수가 같고(12 = D 1 + M 11) 목록 밖 **0**.

## 복원 명령

```
git restore --source=8562b610 --staged --worktree -- <위 12 경로를 개별 인자로>
```

range revert 가 아니다. 경로 한정이고, 경로는 개별 인자다. 신설 파일(`BytecodeConstantPool.kt`)은 원천에
없으므로 같은 명령이 **삭제**로 처리한다(아래 ② 의 `D` 하나).

## ①~⑥ 실측

**실측 HEAD: `3e6d042e572c19d734f7b35af6fc735e2f6a1e2e`**(이 slice 의 **마지막 산출물 커밋** = main 병합
커밋). 버릴 clone(`--no-hardlinks`)에서 돌렸다. 병합이 마지막 산출물 커밋이므로 실측 자리가 그 커밋이다.

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **0** |
| ② | 복원이 움직인 것의 계수 | **13** = `D` 2(신설 helper·그 test 가 사라짐) + `M` 11. 복원 목록과 수가 같고 **목록 밖은 0** |
| ③ | 복원 뒤 `git diff 8562b610 -- <13 경로>` | **빈 출력(0 바이트)** — 그 경로들의 트리가 원천과 **동일**하다 |
| ④ | 복원 트리 컴파일 | ⑥ 의 실행 안에서 돌았다 — 433 중 **278 executed** · 155 from cache. 적중이 아니다 |
| ⑤ | 복원 트리 test | **`:adapters:test`·`:build-logic:test` 둘 다 실행**(적중 아님). 실패 0 |
| ⑥ | 복원 트리 **게이트** | `./gradlew --no-daemon check` → **BUILD SUCCESSFUL**(32분 35초 · 실행된 task **299**) |

**④⑤ 에 적중으로 비는 자리가 없다** — 복원 트리에서 compile·test 가 실제로 실행됐으므로 「트리
동일성으로 갈음」을 쓰지 않았다.

## 「내 표지 0 · 남의 표지 보존」 계수

완료는 종료 코드가 아니라 이 두 계수로 센다.

| 표지 | 복원 전 | 복원 후 | 판정 |
| --- | --- | --- | --- |
| 내 — `source.scoped-packages` 의 축 수 | 14 | **8** | 원천과 같다 |
| 내 — capability map `(o) M11/11C` 절 | 1 | **0** | 사라짐 |
| 내 — `policy.version` | 15 | **14** | 원천 값 |
| 내 — `gate-tests.properties` 의 `BytecodeConstantPoolTest` | 1 | **0** | 사라짐 |
| 내 — `BytecodeConstantPool.kt` 존재 | 1 | **0** | 사라짐 |
| **남 — capability map `(k)`·`(l)`·`(m)`·`(n)` 절 수** | 4 | **4** | **보존 — 셋째 단언의 핵.** 앞선 네 slice 의 종결 등재가 내 복원에 쓸려 나가지 않는다 |
| **남 — `OpeningListResumeTotalTest` 언급(11A 가 더한 주석 + 등재)** | 2 | **2** | 보존 |
| 남 — `gate-tests.properties` 등재 항목 수 | 558 | **557** | **원천과 같다**(원천판 계수 557) — 내 항목 하나만 빠진다 |
| 남 — capability map 줄 수 | 4348 | **4322** | **원천과 같다**(원천판 계수 4322) |

## 되돌림 뒤 남는 것

장부(`reports/evidence/m11/gates-adapters-2/*.md`)는 복원 대상이 아니라 그대로 남는다. 거동을 담지 않고,
지우면 「무엇을 왜 되돌렸는가」가 사라진다. capability map §12.3 **(o) 는 복원된다** — 그 절이 적는 종결
넷과 신설 OPEN 하나가 함께 사라지므로 `OPEN-M10-SCOPED-PACKAGES-COVERAGE` 와 10C 등재 ⓐⓑⓒ 가 **열린
상태로 되돌아온다**. 그것이 되돌림의 뜻이다.

## 유효성 — 실측 HEAD 와 판정 SHA 사이에 되돌림 대상이 움직였는가

verifier 가 대조할 것은 「실측 HEAD == 판정 SHA」가 **아니다**(장부 커밋은 언제나 뒤에 온다). 보아야 하는
것은 그 사이에 **복원 목록 안의 경로가 움직였는가**다.

```
git diff --name-only 3e6d042e..<판정 SHA> -- <위 12 경로를 개별 인자로>
git rev-list --merges 3e6d042e..<판정 SHA>
```

→ 둘 다 빈 출력이면 이 실측은 유효하다. 이 slice 의 장부 커밋은 그 **12 경로**를 건드리지 않고(장부를
목록에서 뺀 이유가 그것이다), main 병합은 **마지막 산출물 커밋보다 앞**에 있다. 한 줄이라도 나오면
미검증이고 다시 잰다.

**전칭식 — 신설 파일 예외.** 위 두 출력이 비지 않더라도, 나온 경로가 전부 **원천 `8562b610` 에 없는 신설
파일**이면 유효하다. 열거와 원천 부재 확인은 verifier 가 한다.
