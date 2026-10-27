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
