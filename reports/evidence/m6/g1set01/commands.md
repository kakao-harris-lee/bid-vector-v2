# M6/G1-SET01 commands — 돌린 것과 그 결과

**승인 전 일괄(D-G1SET01-3) 뒤 판.** r3 통과 뒤 KDoc 한 커밋(주석만)을 더하고 main `56bb7474`(#84) 위로 rebase 해 base 를
다시 고정했다. 아래 SHA 는 그 rebase 뒤 이름이고, r2 측정이 쓴 이름과의 대응은 §1-b 에 있다. r2(D-G1SET01-2) 는 rebase
없이 산출물 커밋 둘을 더하고 다시 쟀다. r1(D-G1SET01-1, 2026-10-23) 의 경로: 이력 재작성 → 산출물 배치 → main `54e2c5e2` 위로 rebase → 마지막
산출물 커밋에서 전체 재측정. 착수 세션의 측정 기록(첫 전건 `check` 실패와 그 처방)은 `checklist.md` §4 에
있고, 이 문서의 수치는 전부 r1 뒤 트리의 것이다.

산출물 커밋(10): `0c827959`(커널·test·등재) · `e3fe8960`(추적표·capability map) · `06e5a4c4`(아키텍처 게이트
수정) · `9775979e`(OPEN 신설) · `cd59251d`(OPEN 에 운영자 결정 인용) · `4173df9c`·`fdf7c91f`(r1) · `f9194115`·`1373c28a`
(r2) · **`7b34b4ee`**(r3 KDoc — **마지막 산출물 커밋**). 판정 SHA 는 그 뒤 evidence 커밋이며 팀장 보고에 적는다.

호스트 규율: 무거운 명령은 전부 `flock ~/.local/bid-vector-build.lock` 아래. 점검 셋(`pgrep` Gradle ·
`free -m` · `ps` 상위)은 빌드와 **별도 호출로 먼저** 봤고, 잠금을 잡은 뒤 available·swap free 를 다시 찍고
6GB·2GB 미만이면 중단하는 래퍼로 감쌌다(전 회차 통과, swap free 최저 3.3GB). 00:30~03:00 KST 빌드 0.
6G DB · run-state · collection worktree 무접촉.

## 0. 이력 재작성 (CR H-2)

원 HEAD 를 로컬 백업 ref 로 남기고(push 0) `a61ddff1..HEAD` 를 tree filter 로 재작성했다 — 착수 커밋 KDoc 의
실 상호 한 곳을 가상 상호로 치환, 빈 커밋은 제거(`--prune-empty`). 상호 목록은 **저장소 밖** 레인 scratchpad
파일에만 있다.

| 술어 | 결과 |
|---|---|
| `git log -p bee7c36f..HEAD \| grep -c -F -f <목록>` | **0**(재작성 전 2 — 착수 커밋의 `+` 와 수정 커밋의 `-`) |
| 재작성 전후 최종 트리 | `git diff <백업> HEAD` **빈 출력** |
| 치환 바이트 | 첫 시도는 perl 의 UTF-8 이중 인코딩으로 깨진 글자를 중간 커밋에 넣었다 — 버리고 바이트 치환으로 다시 했다. 깨진 글자 계수 **0** |
| 커밋 수 | 12 → 11(KDoc 수정 커밋이 빈 커밋이 되어 빠졌다), squash 0 |

`bee7c36f..a61ddff1` 의 두 커밋은 main 의 것(이 slice 아님)이라 재작성 대상이 아니고 적중도 0 이다.
rebase 뒤 `git log -p 54e2c5e2..HEAD`·`git log -p 56bb7474..HEAD`(r3) 적중도 **0**.

## 1. rebase — `a61ddff1` → `54e2c5e2`

충돌 0. 공유 파일 셋(`gate-tests.properties` · `capability-map.md` · `acceptance-trace.md`)은 main 쪽에도 변경이
있었으나 hunk 가 겹치지 않았다. 확인: 이 slice 의 `+`/`-` 줄 집합이 rebase 전후로 같다(settlement·evidence 는
diff 동일, 공유 파일 셋은 정렬한 변경 줄 해시 동일). 추적표 집계 숫자(ⓐ 16 · ⓑ 2 · ⓒ 43)와 활성 OPEN 46 은
main 쪽 변경으로 움직이지 않았다.

## 1-b. rebase — `54e2c5e2` → `56bb7474` (D-G1SET01-3)

충돌 0. main 쪽 변경과 겹치는 파일은 `gate-tests.properties`·`capability-map.md` 둘이고 hunk 는 겹치지 않았다(합집합).
이 slice 의 변경 줄 집합은 rebase 전후로 같다(settlement diff **빈 출력** · 공유 파일 셋 정렬 변경 줄 해시 동일).
의존 게이트 면제 수(`AppHttpDependencyGateTest` 62 → 65)는 #84 자신의 app 클래스 셋 몫이고, 이 slice 는 app
클래스를 더하지 않아 바꿀 수가 없다 — 그 게이트는 아래 찬 `check` 가 잰다. 원 HEAD 는 로컬 ref
`backup/set01-pre-rebase-r3`. 이름 대응: `dee96d41`(r2 마지막 산출물) = `1373c28a` · `14178b50` = `07169465`.

## 2. 표적 게이트 — 산출물 커밋 전(r1 · r2 같은 명령)

```sh
flock ~/.local/bid-vector-build.lock ./gradlew --no-daemon :settlement:ktlintFormat \
  :settlement:ktlintMainSourceSetCheck :settlement:ktlintTestSourceSetCheck :settlement:detekt \
  :settlement:sizeGate :settlement:typeShapeGate :settlement:domainApiTypeGate \
  :settlement:domainSourceReferenceGate :settlement:testShapeGate :settlement:gateRegistrationGate \
  :settlement:test --rerun :app:test --tests '*ArchitectureGateTest*' --continue
```

→ r1 **exit 0** · 46초 · `:settlement:test` 46 · 실패 0. r2 **exit 0** · 46초 · `:settlement:test` **51 test · 실패 0**
(16·5·5·11·14), `:app:test` 필터 · 실패 0. `internal` 가시성 진단 조각 `it is internal in` 은 r1 첫 실행에서
실측됐다(fixture 4~7 음성이 그 조각으로 통과).

## 3. 변이 — 버릴 clone(`dee96d41`, 현 이름 `1373c28a`)

```sh
flock ~/.local/bid-vector-build.lock ./gradlew --no-daemon -q :settlement:test --rerun   # 회차마다
```

통제군(`--rerun`, 실제 실행) **51 · 실패 0** — 열의 처음과 끝. 변이 26 회차 전부 붉다(24 는 test RED, N6·N8 둘은
`allWarningsAsErrors` 로 main 컴파일 실패), 복원 확인 전 회차 0. 표는 `checklist.md` §2. 이것이 `dee96d41` 에서 `:settlement:test` 를 **캐시 없이 실행한** 자리다(아래 §4 의
전건 `check` 에서는 up-to-date 였다).

**r3 에서 다시 돌리지 않은 근거**(D-G1SET01-3): 변이가 재는 입력은 `settlement` 소스·`shared-kernel`·빌드 로직이다.
`git diff dee96d41 7b34b4ee -- settlement` 은 `CompanyName.kt` 의 KDoc 줄뿐이고(주석이 아닌 변경 줄 **0**), main
`54e2c5e2..56bb7474` 는 `shared-kernel`·`build-logic`·`settlement`·Gradle 설정을 건드리지 않았다(경로 한정 diff 0).

## 4. acceptance — `.github/workflows/ci.yml` 의 job 명령 그대로

### Kotlin `check` job — `7b34b4ee`, **찬 clone**

```sh
./gradlew --no-daemon check --no-build-cache   # 새로 clone 한 디렉터리, flock 아래
```

→ **exit 0** · BUILD SUCCESSFUL **11분 36초** · 365 task **전부 실행**(FROM-CACHE 0 · up-to-date 0) · 잠금 23:02:47 ·
HEAD 양 끝 `7b34b4ee`. `:settlement:test` **51 · 실패 0**(16·5·5·11·14) · `:app:test` 677 testcase · `leakPatternGate`
실행. 명령은 CI 그대로에 `--no-build-cache` 하나를 더했다 — 같은 입력의 캐시 출력을 측정으로 세지 않기 위해서다(새
clone 이라 build 디렉터리도 없다). r2 의 `dee96d41` 측정(9분 23초, `:settlement:test` up-to-date)은 이 측정으로 갈음한다.

### `container` job — 재생기로 전부 (verifier M-3)

생략 근거였던 path-limited diff 는 의존 그래프를 보지 않았다 — `workflow` 가 `settlement` 에 project 의존하고
app → workflow 라 **`app.jar` 가 `BOOT-INF/lib/settlement.jar` 를 싣는다**. 그래서 job 을 COL-03 레인의 재생기
(ci.yml `container` job 의 `run:` step 을 순서대로, GitHub 기본 셸로, `if:` 를 평가하며)로 마지막 산출물 커밋의 clean
clone 에서 돌렸다(r3 은 `7b34b4ee`).

→ **JOB success · exit 0** · `7b34b4ee`(r3 rebase 뒤 — app 입력이 바뀌었다) · 23:42:50~23:47:25. r2 의 `dee96d41`
재생(22:19~22:23)도 같은 결과였다. `run:` step **23 전부 success**, `uses:` **4** 는 러너
전용(미실행 — checkout · setup-java · setup-gradle · SBOM 보관) — 합 27. 재생한 `app.jar` 의 `settlement.jar` 에 r2 의
`Won`·`Lost` 클래스가 든 것을 r3 재생에서도 확인했다. (r1 재생 문면의 「run 24」·「app 만 새 ID」는 오기였다 — 검증자 L-2r2.)

이미지 태그 넷(`bidvector/{app,web,proxy,ml-serving}:local`)은 시작 ID 를 기록하고 끝에 그 ID 로 되돌렸다 — 재생이
**`app`·`web` 둘**을 새 ID 로 만들었고(`proxy`·`ml-serving` 은 같은 ID) 복원 뒤 넷 다 시작 ID 와 같다. compose
프로젝트는 파일에 고정된 `bidvector-v2` 라 6G DB 컨테이너(별도, compose 밖)와 겹치지 않는다.

### `web` · `ml-engine` 을 생략한 근거

```sh
git diff --name-only 56bb7474..7b34b4ee -- web ml-engine contracts openapi .github docker   # 빈 출력
```

두 job 의 입력은 그 디렉터리 안에서 닫힌다(Gradle 산출물을 싣지 않는다) — 이 slice 의 diff 는 `settlement/**` ·
등재 장부 · capability map · 추적표 · 이 evidence 뿐이다.

## 5. evidence 위생

- `leakPatternGate` 는 `check` 에 들어 있다(위 실행에서 초록). 이 evidence 를 담은 커밋에서의 재실행 결과는 PR
  조치 코멘트에 둔다 — evidence 에 넣으려는 시도가 커밋을 또 낳는다.
- 이 slice 가 편집하는 파일을 가리키는 `file:line` 좌표 **0**(산출물·evidence·공유 파일 셋 grep).
- 크기 게이트: evidence 넷 **약 70KB**(계약 `scope.md` 포함) ≤ 산출물 추가분 **약 76KB**(`settlement` 신규·변경
  파일 약 67.1KB + 공유 파일 셋의 `+` 줄 약 9.2KB, base `56bb7474`). r1 에서 checklist 의 재측정 이력 문단을 걷어내 경계에서 내려왔다.
- 비밀값 스캔 어휘: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m6/g1set01` **0**.

## 6. 돌리지 않은 것

- `qualityBaseline` — 측정이지 게이트가 아니다(CI 가 실패시키지 않는다).
- `./gradlew --stop` — 다른 레인의 daemon 까지 멈춘다. 자기 호출은 전부 `--no-daemon` 이고 레인 끝에
  `pgrep -af GradleDaemon` 으로 자기 것이 없음을 본다.
- DB·외부 호출 — 순수 커널이라 없다(container job 의 compose·리허설은 그 job 이 스스로 세우고 지운다).
