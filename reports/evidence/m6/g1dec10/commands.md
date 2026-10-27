# M6/G1-DEC10 — 실행 명령과 종료 코드

acceptance(`./gradlew --no-daemon check`)는 Phase 4 에 돈다. 이 절은 Phase 1(설계) 의 실측이다.
**마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트**다(evidence 는 자기 마지막
커밋의 post-state 를 담을 수 없다).

## 2026-10-28T03:39+09:00 — rollback 전제 단언 (base 고정)

- cmd: `git merge-base --is-ancestor <착수 커밋> 23837b19`
- exit: 1
- 핵심 결과: base 가 이 slice 의 착수 커밋을 담지 않는다 — 공유 문서 복원 전제 성립.

## 2026-10-28T03:39+09:00 — base 가 HEAD 의 조상인지

- cmd: `git merge-base --is-ancestor 23837b19 HEAD`
- exit: 0
- 핵심 결과: 조상이다.

## 2026-10-28T03:50+09:00 — main 클래스 컴파일 (코드 지도 측정의 전건)

- cmd: `~/.local/bin/bv-build.sh ./gradlew --no-daemon classes`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 34 actionable tasks(15 executed · 19 from cache).

## 2026-10-28T03:52+09:00 — 코드 지도 census (바이트코드)

- cmd: `dec10-census.sh <repo> <out>` — 모듈별 main 클래스를 `javap -p -c` 로 떠서 축 ①(소유자가
  `FloorRate` 인 멤버 접근) · 축 ②(반환·필드 타입이 `FloorRate` 인 획득) · 참고 축(`FloorRateOrigin`)을
  센다. 스크립트는 scratchpad(산출물 아님).
- exit: 0
- 핵심 결과: 축 ① 17 참조/15 호출자 · 축 ② 17 참조/16 호출자 · origin 21, main 클래스 13 디렉터리
  (전체 참조 84,365)에서. 자리별 목록은 `design.md` §0.

## 2026-10-28T03:48+09:00 — census 측정 도구의 양성 대조

- cmd: 같은 스크립트를 **다른 레인의 컴파일된 트리**(7C④ clone)의 `workflow` 모듈에 적용
- exit: 0
- 핵심 결과: 축 ①·② 각 1 자리(`workflow.evaluation.PredictionFactsKt` → `FloorRate.getRate` ·
  `Notice.getFloorRate`)로 **잡힌다** — 도구가 빈 집합을 내는 상태가 아님을 확인했다. 같은
  스크립트가 그 트리의 `decision` 모듈에서는 0 을 냈고, 그것은 그 base 에 DEC-05 메뉴 커널이
  없기 때문이다(측정 대상이 아니라 대조용 트리다).

## 2026-10-28 — 구현 라운드

### RED 선행 (게이트 커널)

- cmd: `./gradlew --no-daemon :decision:test --tests '…PublishedFloorTrustTest'` (골격 — 게이트가
  전부 부재를 내고 출하 밴드가 `[0, 1]` 인 상태)
- exit: 1
- 핵심 결과: 12 중 **10 거동 실패**(컴파일 오류가 아니다). 구현 뒤 같은 명령 exit 0.

### acceptance — `check` 전건

- cmd: `./gradlew --no-daemon check` (CI `check` job 의 명령 그대로)
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL in 25m 13s`, 392 actionable tasks.
- 앞선 실패 둘(둘 다 **sizeGate**, 수정 후 위 초록): `app/build.gradle.kts` 람다 56줄 ·
  `EvaluateCandidatesUseCase.analyzeAndJudge` 51줄. 실패 이력을 남긴다 — 그 게이트가 실제로 돌았다는 증거다.
- `web`·`ml-engine`·`container` job 은 입력 변경 0 이라 생략한다(`openapi/`·`web/`·`ml-engine/`·
  `docker/`·`.github/` 경로 변경 없음 — `rollback.md` 의 복원 목록이 그 사실의 기계 산출이다).

### 변이 — 바꿔치우기, 표적 test `--rerun`

통제군은 **같은 HEAD 의 전건 `check` 초록**이다. 적용 실패 시 비영 종료로 멈추는 스크립트로 돌렸고,
여섯 전부 **의도한 test** 에서 RED 다(컴파일 오류 0 — 각 로그에 `^e:` 없음).

| 변이 | 바꾼 것 | 표적 | exit |
| --- | --- | --- | --- |
| M1 | 게이트가 밴드를 보지 않고 전부 통과 | `PublishedFloorTrustTest` · `BidMenuFloorTrustTest` | 1 |
| M2 | 통과 값의 `copy` 를 모듈 밖으로 연다 | `FloorTrustCompileFailureHarnessTest` (fixture `6b`) | 1 |
| M3 | margin 이 개연 불가·심판 아님에 **원값을 쓴다** | `PredictionFactsFloorTrustTest` (9 중 3) | 1 |
| M4 | 집계 줄에서 사유 필드를 뺀다 | `EvaluationCommitExitCodeTest` (8 중 3) | 1 |
| M5 | ⓑ 허용 목록에서 한 쌍을 **뺀다** | `FloorTrustGateTest` ⓑ | 1 |
| M6 | ⓑ 허용 목록에 없는 쌍을 **더한다** | `FloorTrustGateTest` ⓑ | 1 |

M5·M6 이 등식의 **양방향**이다(누락 · 낡은 항목). M3 에서 `1.00000` test 가 초록으로 남는 이유는
`checklist.md` 알려진 제한 ⑧ 이 적는다. 변이 뒤 작업 트리 잔여 0.

### 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m6/g1dec10/`
- exit: 1 (= 매치 없음 = 통과)

### 낡는 좌표 — 역방향 파급 실측

- cmd: 편집한 파일의 축약형 역참조를 세고(`grep -rnoE '<stem>\.md:[0-9]+'`) 삽입 줄 번호와 대조
- exit: 0
- 핵심 결과: `capability-map.md` 는 1734 뒤에 15줄이 들어가 그 아래 역참조 **22 건(11 파일)** 이 +15
  밀린다 — 전부 다른 slice 의 evidence 라 in_scope 밖이고 `checklist.md` 알려진 제한 ⑤ 로 등재한다.
  `milestone-6.md`·`acceptance-trace.md` 는 역참조 0 건, 두 `policy-values.md` 와
  `CollectionPolicy.kt` 는 줄 수 불변(같은 줄 치환) 또는 역참조 0 건이다.

### clean-tree 판정 (양성 대조 포함)

- cmd: `git status --porcelain -- <in_scope 개별 인자>` → 미추적 evidence 한 건 외 없음
- 양성 대조: in_scope 파일 하나에 줄을 심어 `M` 으로 **잡히는 것**을 확인하고 **비파괴 절삭**
  (`head -n <원래 줄수>`)으로 되돌렸다 — `checkout --` 는 쓰지 않는다(미커밋 편집을 함께 지운다).
