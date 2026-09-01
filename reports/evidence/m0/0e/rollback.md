# 되돌림 절차 — M0 / 0E

**되돌릴 flag·route·writer 는 없다** — M0 는 문서 slice 라 애플리케이션 코드·설정·스키마·DB write·외부
호출이 이 range 에 하나도 없다(`scope.md` out_of_scope). 되돌림은 **git revert 뿐**이고 런타임 부작용이
없다. **예상 복구 시간 10분** — revert 2분 + 아래 검증 5분 + 여유.

**전체 되돌림**은 `git revert --no-commit` 에 커밋을 **역순으로** 주고 한 커밋으로 닫는다. 커밋 집합과
레인별 분해는 `commands.md` **C-0**·**C-7a** 가 낸다 — **SHA 를 여기 열거하지 않는다**(늘 때마다 낡는다).
`14686db..HEAD` 는 **세 레인**(spec-writer·fixture-curator·하네스)을 담으므로 한 레인만 되돌리려면 그
레인의 pathspec 으로 커밋을 뽑는다.

**부분 되돌림의 결합 셋.** ① `capability-map.md` **§14** 는 개정 셋과 ADR 「상태」 줄이 만든 사실을
참조하므로 **§14 만 남기면 낡는다.** ② `fixtures/manifest.yaml` 접촉 커밋을 따로 빼는 경우의 짝은
`commands.md` 「`C-7b` 매치의 처리」가 갖는다. ③ **계약 필드**를 세운 커밋을 되돌리면 그 case 의 계약이
사라져 **M1 소비 테스트가 기대값 전체를 비교**하게 되므로 같은 case 의 `not_covered` 와 함께 다룬다.

**검증**은 `commands.md` **C-1~C-3**(registry) · **C-11**(계약 불변) · **C-15**(mutation 스윕)과 fixture
해시 대조를 다시 돌려 revert 전 값과 같은지 본다. 갈리면 부분 되돌림이 위 결합 하나를 깬 것이다.
