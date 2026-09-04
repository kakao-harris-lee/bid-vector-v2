# commands — M1 / 1B

실행 명령과 종료 코드. 출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이다(`evidence-pack`
SKILL). 라운드 이력 절과 자기 검사 하네스는 만들지 않는다 — 그 기록은 git log 와 리뷰
verdict 가 갖는다.

## C-0 — 리뷰 range (1A 정정 문면 승계)

Codex 리뷰 요청 시점의 전체 diff 는 아래 명령이 낸다. **range 자체가 slice 산출물의 정의는
아니다** — `scope.md` 의 「하네스 레인 변경」 절이 하네스 경로를 가른다(evidence-pack SKILL
2026-09-04).

```
git diff --stat 66c1ab79af4c5a68145811a9e87008dfdb10da3c..HEAD
```

---

_이 아래는 Phase 3 구현이 시작되면서 명령 실행 직후 즉시 append 한다. 지금은 비어 있다._
