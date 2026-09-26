# M6/6A-2a — 명령과 종료 코드

정본: `scope.md` acceptance. 출력 전문은 싣지 않는다(핵심 결과 한 줄) — 감사자는 명령을 다시 돌린다.
**마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트다**(evidence 는 자기 마지막 커밋의
post-state 를 담을 수 없다).

## RED — 구현 전

## 2026-09-26T00:00:00Z
- cmd: `./gradlew --no-daemon :app:compileTestKotlin`
- exit: 1
- 핵심 결과: 새 test 둘이 `lockManagementSurface`·`productionApplication`·actuator 좌표 부재로 컴파일 불가(RED)
