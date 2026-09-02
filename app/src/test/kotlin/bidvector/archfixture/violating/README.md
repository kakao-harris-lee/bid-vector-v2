이 트리는 **일부러 위반한 코드**다. `milestone-1.md` 「완료 조건」이 요구하는 「금지 import 와 순환 의존을
일부러 넣은 test fixture가 실제로 실패」의 실물이며 `ArchitectureGateCatchesViolationsTest` 가
소비한다. test source set 에만 있어 production classpath 에 오르지 않는다.

패키지 이름은 `architecture-policy.properties` 의 모듈 이름을 그대로 쓴다 — 같은 규칙 값에
루트만 바꿔 넣기 때문이다. 여기서 이름을 바꾸면 그 규칙의 음성 단언이 조용히 비게 되므로,
각 단언은 심어 둔 타입 이름이 위반 목록에 있는지까지 확인한다.
