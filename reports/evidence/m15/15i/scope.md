# M15 / 15I — 설정 여정의 test·게이트 잔여 넷

```yaml
milestone: M15
slice: 15i
base_sha: ff8e0cad42c5c114ee30b42ca4318252bbf35c5f
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - web/e2e/journey/settings.spec.ts
  - web/e2e/journey/privateValues.ts
  - web/e2e/journey/teardown.ts
  - web/e2e/journey/teardown.test.ts
  - web/vite.config.ts
  - web/config/gate-tests.txt
  - config/quality/leak-patterns.txt
  - config/quality/leak-pattern-baseline.txt
  - docs/discovery/capability-map.md
  - reports/evidence/m15/15i/
out_of_scope:
  - Kotlin 평가 코드와 EvaluationCommitRunnerTest (= OPEN 행의 ⓐ — 다른 레인이 같은 시각에 그 파일을 편집한다)
  - 가입 경화(signup hardening)
  - 그 밖의 M15 잔여·신설 OPEN
acceptance_commands:
  - cd web && npm ci                       # W-1
  - cd web && npm run lint                 # W-2
  - cd web && npm run typecheck            # W-3
  - cd web && npm run gate:src-tree        # W-4
  - cd web && npm run api:types:check      # W-5
  - cd web && npm run test                 # W-6
  - cd web && npm run gate:tests           # W-7
  - cd web && npm run build                # W-8
  - cd web && npm run gate:boundary-canary # W-9
  - cd web && npx playwright install chromium  # W-10
  - cd web && npm run test:e2e             # W-11
  - ./gradlew --no-daemon :app:bootJar     # W-12
  - cd web && npm run test:e2e:journey     # W-13
  - ./gradlew --no-daemon check --continue # check job (leakPatternGate 가 이 job 에 걸린다)
rollback: reports/evidence/m15/15i/rollback.md (in_scope 경로 한정 restore — range revert 가 아니다)
```

## 무엇을 닫는가

`docs/discovery/capability-map.md` 의 `OPEN-M15-TEST-RESIDUE` 행이 든 다섯 중 **넷**이다. ⓐ 는
out_of_scope 다(위 표).

- **ⓑ** 설정 여정의 예외 칸 양성 대조 목록이 술어의 수집 줄과 **한 방향으로만** 묶여 있다 —
  술어가 속성을 잃으면 붉지만 다섯째를 **얻으면** 아무것도 붉지 않는다.
- **ⓒ** 여정 teardown 의 걸음 감싸개가 던진 것의 **메시지만** 남기고 스택·`cause` 를 버린다.
- **ⓓ** 같은 여정 ⑤ 의 네 속성 루프가 **첫 붉음에서 멈춰** 나머지 속성이 그 실행에서 미측정이다.
- **ⓔ** 비밀값 스캔 정책 파일에 사업자등록번호 꼴이 없다(M15/15C 알려진 제한 10 — 「지금은 더하지
  않는다」가 M15 종결의 결정이었고, 더하는 날 합성 표본을 위한 allowlist 가 함께 와야 한다).

## 설계 결정

### D-15I-1 — ⓑ 는 **목록을 가져오지 않고 등식으로** 닫는다

양성 대조가 술어의 목록을 **순회하면** 대조가 공허해진다(속성이 느는 변이가 대조의 모집단도
함께 늘려 조용해진다 — 그것이 그 목록을 일부러 두 번 적은 사유였다). 그래서 **심는 목록은 이
spec 의 리터럴 그대로** 두고, 그 리터럴과 술어가 실제로 훑는 모집단 사이에 **집합 등식 단언**을
하나 세운다. 두 방향이 모두 붉는다 — 술어가 잃으면 심기 루프가, 얻으면 등식이.

등식의 오른쪽(술어의 모집단)은 **술어가 실제로 쓰는 값 한 자리**여야 한다. 그래서
`privateValues.ts` 의 선택자와 수집 줄을 같은 상수에서 **구성**으로 만든다(열거를 두 자리에
두면 등식이 한쪽만 잡는다).

### D-15I-2 — ⓒ 는 문면과 진단을 **가른다**

실패 머리말(`failure`)은 한 줄로 두고(라벨 + 메시지), 스택과 `cause` 사슬은 **별도 칸**(`detail`)
으로 보존해 단언 문면의 아래에 붙인다. 사슬은 깊이 상한과 순환 가드를 갖는다 — 사슬이 자기를
가리키는 날 teardown 이 무한히 돌지 않는다.

그 감싸개를 **spec 밖의 모듈**(`web/e2e/journey/teardown.ts`)로 옮긴다. 여정 레인에서는 teardown
이 **던지는 경로가 초록 실행에 없어** 이 고침이 실측되지 않는다 — 단위 test 로 잠가야 그 고침이
거동으로 선다. 그래서 `web/vite.config.ts` 의 vitest `include` 에 `e2e/journey/**/*.test.ts` 를
더하고, 그 파일을 `web/config/gate-tests.txt` 에 등재한다(W-7 이 「그 파일이 실제로 돌았는가」를
실행 결과로 단언한다 — 등재하지 않으면 뒷날 `exclude` 한 줄로 조용히 빠진다).

### D-15I-3 — ⓓ 는 **모아서 한 번** 단언한다

네 속성을 모두 돌려 「심었을 때의 문제 목록」과 「되돌린 뒤의 문제 목록」을 **쌍으로 모으고**,
마지막에 전체 목록 하나를 기대값과 맞댄다. 둘째 속성이 깨져도 셋째·넷째가 측정된다.

### D-15I-4 — ⓔ 는 **기존 allowlist 기계**를 쓴다

게이트가 이미 가진 허용 목록은 `config/quality/leak-pattern-baseline.txt` 이고 키는
`경로#정규화한 줄 내용의 SHA-256` 이다(좌표가 아니다). 새 패턴이 만드는 **합성 표본 매치**는 그
파일에 등재한다 — 병렬 기계를 새로 만들지 않는다.

패턴 파일에는 **주석을 넣지 않는다**: `tools/db-rehearsal.sh` 가 같은 파일을 `grep -E -f` 로
쓰므로 `#` 줄이 「`#` 를 담은 모든 줄」에 매치하는 패턴이 된다.

## 게이트 술어 변경 — 설계 검토 (Phase 2.5, ⓔ 전용)

ⓔ 만 게이트 술어를 바꾼다. ⓑ·ⓒ·ⓓ 는 test 층이다.

**(0) 위협 모델 경계.** 이 게이트가 방어하는 것은 **`reports/evidence/` 아래 문서에 비밀값·raw
식별자가 새로 등장하는 것**이다(스캔 루트가 그 트리이고 `scope.md` 는 육안 리뷰 대상으로
제외된다 — `LeakPatternGateTask` KDoc 이 그 선택의 정본이다). 방어하지 않는 것: 소스 트리, 저장소
밖, 실행 중 로그, 그리고 **이미 등재된 매치**. 이 slice 는 그 경계를 **넓히지도 좁히지도 않고**
어휘 축 하나를 더한다.

**(1) 열거인가 구성인가.** 패턴 파일 자체는 어휘 열거다 — 그 성질은 이 slice 가 바꾸지 않는다
(`leak-patterns.txt` 는 2026-09 부터 열거였고, 이 slice 는 그 열거에 한 줄을 더한다). 구성으로
닫히는 것은 **적용 범위**다: 스캔 대상은 트리 전수이고 제외는 파일 이름 집합 하나이며, 허용은
내용 해시 키다. 그래서 「패턴을 피해 가는 표기」는 열려 있고(경계 밖), 「등재되지 않은 새 매치가
조용히 통과하는 것」은 닫혀 있다.

**(2) 우회 다섯.**
1. 꼴을 바꿔 적는다(자리 구분을 빼거나 공백·전각으로) → **막지 않는다.** 저장되는 꼴(숫자 열
   자리)은 금액·공고번호와 구별되지 않아 패턴으로 잡으면 오탐이 트리 전체를 덮는다. 경계 밖.
2. `scope.md` 에 적는다 → **막지 않는다**(4E 관례로 제외된 파일, 육안 리뷰 대상). 실측에서 오늘
   유일한 evidence 쪽 제외 대상 매치가 그 파일에 있다.
3. 패턴 파일에서 그 줄을 지운다 → 막지 못한다(게이트 자신을 바꾸는 행위 = 리뷰 소관, 경계 밖).
   단, `parseLeakPatterns` 가 **빈 패턴 목록을 명시적으로 실패**시키므로 파일을 통째로 비우는
   꼴은 닫혀 있다.
4. baseline 에 미리 키를 넣어 두고 그 줄을 쓴다 → 막지 못한다(같은 사유, diff 에 남는다).
5. 스캔 루트 밖(소스 트리·`docs/`)에 적는다 → **막지 않는다**(경계 밖 — 루트는 이 slice 가
   바꾸지 않는다). 소스 트리 전수 스캔을 하지 않는 사유는 게이트 KDoc 이 든다.
6. 매치 줄을 파일 이름만 바꿔 옮긴다 → **막는다.** 키에 경로가 들어 있어 다른 파일에서는 새 키다.

**(2b) 값 획득 축 — 이 slice 가 새로 내놓는 public 표면.**

| 새 표면 | 밖에 허락하는 것 | 판정 |
| --- | --- | --- |
| `privateValues.ts` 의 훑는 속성 모집단 상수(export) | 여정 spec 이 **읽는다**. 쓰기가 아니고 술어의 동작을 바꾸지 못한다 | 닫는다 — 읽기 전용 상수, 소비자는 같은 디렉터리의 spec 둘뿐 |
| `teardown.ts` 의 걸음 감싸개·진단 함수(export) | 임의의 던지는 함수를 감싸 사유 문자열로 바꾼다. 네트워크·DB·파일 권한을 새로 주지 않는다(받은 함수만 부른다) | 닫는다 — 순수 변환, e2e 하네스 밖 소비자 0 |
| `leak-patterns.txt` 의 새 줄 | 게이트가 **더 많이** 실패하게 한다(허용을 넓히지 않는다) | 닫는다 — 방향이 한쪽이다 |
| `leak-pattern-baseline.txt` 의 새 키 하나 | 그 **한 파일의 그 한 줄**을 통과시킨다(내용 해시 + 경로) | 경계로 처리 — 아래에서 실측한다 |

「경계로 처리」 행의 실측: 등재한 키가 **다른 파일의 같은 줄**을 통과시키지 않는 것과, 그 파일의
**다른 줄**을 통과시키지 않는 것을 양성 대조로 잰다(commands.md ⓔ 절).

**(3) 과잉·미달.** 과잉 없음 — 패턴 한 줄, 허용 한 줄이다. 미달: 저장 꼴(자리 구분 없는 숫자
열 자리)은 이 패턴이 영영 못 본다(위 우회 1). 그 축은 **화면 쪽 술어**(`privateValues.ts` 의
축어 축)가 이미 들고 있고, evidence 문서 축에서는 등재하지 않는다 — 알려진 제한.

## 하네스 레인 변경

`git log --oneline ff8e0cad..HEAD -- CLAUDE.md .claude/` → **없음**.

(비어 있어도 이 절은 상시 둔다. 리뷰 요청 시점에 다시 돌려 갱신한다. 목록이 생기면 그 커밋들은
slice 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다는 것을 여기 적는다.)

## 알려진 제한

1. **ⓐ 는 닫지 않는다** — 다른 레인이 같은 시각에 `EvaluationCommitRunnerTest` 를 편집한다. OPEN
   행은 ⓐ 를 열린 채로 남긴다.
2. **ⓑ 의 등식은 「술어가 그 상수를 쓴다」를 전제한다.** 뒷날 누가 상수를 두고 속성 하나를
   `getAttribute` 로 **직접** 더 읽으면 등식은 조용하다. 그 자리를 구조로 닫으려면 수집 줄이
   상수 순회 하나뿐이어야 하고, 이 slice 는 그 모양으로 두되 **열거가 아니라는 주장을 하지
   않는다**(수집 줄을 늘리는 것은 리뷰 소관이다).
3. **ⓔ 의 패턴은 자리 구분이 있는 꼴만 본다**(위 (3)).
4. **`tools/db-rehearsal.sh`(CI `container` job S-23c)도 이 패턴 파일을 읽는다.** 그 job 은 로컬에서
   돌리지 않았다(도커 이미지 빌드·trivy·compose 전건). 영향이 0 인 근거: DB 가 사업자번호를
   **자리 구분 없는 열 자리**로 저장하고(계약 값 타입), 그 job 이 백업 전에 쓰는 행은
   `operator_strategy` 뿐이다. 실제 판정은 PR 의 CI 가 낸다.
5. **`prettier` 는 이 저장소에 없다** — 의존에도 CI 에도 없고 포맷 게이트는 `npm run lint`
   (eslint)다. 그래서 「prettier 가 아직 붉은가」에 대한 답은 「그 게이트가 이 저장소에 없다」이고,
   이 slice 는 포맷 변경을 자기 파일 밖으로 넓히지 않았다.
