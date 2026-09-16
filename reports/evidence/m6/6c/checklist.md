# M6/6C — checklist.md

## 판단이 필요했던 자리

1. **`docker/.dockerignore` → `docker/ml-serving.Dockerfile.dockerignore`.** scope.md 의
   in_scope 는 `docker/.dockerignore`를 적었지만, Docker(BuildKit)는 context root 의
   `.dockerignore` 또는 `<컨텍스트 상대 Dockerfile 경로>.dockerignore`만 읽는다 —
   `docker/.dockerignore`는 어떤 경우에도 적용되지 않는다(실측: 두 경로를 만들어 각각
   빌드해 전자는 제외 파일이 컨텍스트에 그대로 들어오고, 후자만 제외됨을 확인). 컨텍스트
   제외가 안 되는 무효 파일을 만드는 것은 위협 모델 (f) 방어를 이름만 있고 실체가 없는
   것으로 만든다 — 실제로 동작하는 이름(`docker/ml-serving.Dockerfile.dockerignore`, 여전히
   `docker/` 아래)으로 썼다. 빌드 컨텍스트 실측(commands.md, `transferring context: 6.69kB`)
   으로 효과를 확인했다.
2. **`config/quality/image-hygiene-policy.properties` 신설(scope.md in_scope 목록에 없음).**
   위생 게이트의 임계값(금지 패키지 목록·크기 상한·non-root UID 하한)을 스크립트에 리터럴로
   박지 않기 위해 새 정책 파일을 만들었다 — v2-지침서 §5 「매직 넘버 금지: 정책 값은
   versioned policy 데이터」를 따른 것이고, 이 저장소의 다른 게이트들도 전부 이 관례를
   쓴다(`size-policy.properties` 등). `tools/image-hygiene-check.sh`(in_scope) 를 지지하는
   부속 파일로 판단해 진행했다 — 계약 갱신을 요청하지 않았다(작고 첨가적이며 다른 게이트
   구조를 바꾸지 않는다).
3. **이미지 크기 게이트 지표 — `docker image inspect .Size`(`docker save | wc -c`와 바이트
   단위로 일치 확인) vs `docker images` 사람이 읽는 SIZE 열.** 같은 로컬 이미지에 대해 후자가
   476MB, 전자가 113.7MB 를 보고했다(실측, 2026-09-16 — buildx 가 만드는 attestation
   매니페스트를 `docker images` 가 합산하는 것으로 보인다). 실제 런타임 크기(`docker save`
   바이트 수와 일치)를 잰다고 판단해 전자를 채택했다 — 위생 게이트가 attestation 오버헤드를
   "이미지가 커졌다"로 오판하지 않게 한다.
4. **compose 의 postgres 서비스는 host 포트를 publish 하지 않는다(scope.md 는 포트 정책을
   명시하지 않았다).** 이 개발 환경에 이미 다른 프로젝트의 postgres 컨테이너가 5432 를 쓰고
   있어 고정 host 포트 매핑이 이 머신에서 곧바로 충돌했다(실측). 6B·`RealServerIntegrationTest`
   모두 컨테이너 자체(compose 네트워크 또는 Testcontainers 가 만드는 별도 인스턴스)에 붙지
   host 포트가 필요 없어, host publish 를 빼는 쪽으로 판단했다. `ml-serving`은 로컬 수동
   확인 편의를 위해 50051 을 그대로 열어 뒀다(이 환경에서 충돌 없음 실측).
5. **`RealServerIntegrationTest`의 패키지를 `bidvector.adapters.ml` → `bidvector.adapters.contract`
   로 재배치(구현 중 발견 → 정지·보고 → 팀장 결정 D-6C-8).** 기존 게이트 둘
   (`MlGateRegistrationTest`: `ml` 패키지의 모든 `*Test.kt`는 `gate-tests.properties` 등재
   필수 / `gateExecutionGate`: 등재된 클래스는 기본 `check`에서 skip 0 필수)이 환경
   조건부로 항상 건너뛸 수 있어야 하는 test(D-6C-4)와 동시에 만족될 수 없었다. 게이트
   술어를 약화시키는 두 안(조건부 허용 키 신설, 등재 예외 목록)은 검토했으나 채택하지
   않았다 — 채택안은 scope.md·`OPEN-6C-CONDITIONAL-GATE-TEST` 참고. 이 파일은 구현 레인이
   직접 결정하지 않고 팀장에게 보고해 계약 갱신으로 받았다.
6. **`manySampleBidPredictionRequest`/`reserveDrawObservation` 신설(실 서버가 처음으로 겪는
   입력 형태).** 기존 fake servicer consumer test 의 공용 fixture(`testBidPredictionRequest()`,
   표본 1건, `reserveDraw` 없음)를 그대로 실 서버에 보내면 `Unmeasurable(InsufficientSamples)`
   였다(실측, 두 단계: ① `reserveDraw` 미설정 → 전 표본 `NO_RESERVE_DRAW`로 제외 ②
   `reserveDraw`를 채우자 `Predicted`로 성립). 실 엔진의 표본 하한(`reserve.min_reserve_records`
   =8·`bid_ratio.min_samples`=3·`reserve.expected_price_count`=15)을 만족하는 별도 fixture를
   새로 짰다 — 값 계산 축이고 이 slice 가 잡는 소비자 규칙 축이 아니라서 도메인 규칙을
   바꾸지 않는다(엔진 코드 무편집, `ml-engine/src/**` out_of_scope 유지).

## (2b) 새 public 표면 여부

- `docker/probe/liveness.py`·`readiness.py` — 조회 전용 RPC(`GetModelMetadata`) 둘만 호출.
  스크립트 전수 확인, 쓰기 RPC 0건. scope.md (2b) 표 「닫는다」 그대로.
- `tools/one-command-check.sh` — CI job 명령을 그대로 옮겼을 뿐 새 플래그·축약 없음. 「닫는다」.
- `RealServerIntegrationTest`의 gateway 생성 — `src/test` 소스셋 한정, `@EnabledIfSystemProperty`
  로 기본 실행에서 분리. `GrpcBidPredictionGateway`·`MlCallPolicyData`·`testMlCallPolicy` 등은
  전부 기존 public/internal 표면 재사용이고 이 slice 가 새 public 함수·타입을 어댑터에 더하지
  않았다(AST 확인 — `adapters/src/main/kotlin`에는 편집이 없다, `git status` 로 실측). 「경계로
  처리」— production 표면 증가 0.
- `docker/compose.yaml`의 postgres 자격증명 — 파일에 리터럴 0, `${VAR:?...}` 참조만
  (commands.md 비밀값 스캔 실측). 「경계로 처리」.
- `config/quality/image-hygiene-policy.properties`(신규) — 이 파일이 여는 것은 「이미지
  위생 게이트가 참조하는 정책 값」뿐이고, 다른 모듈이 소비할 수 있는 도메인 타입이나 함수가
  아니다. production 표면과 무관.

## 알려진 제한

- `OPEN-5E-EMBEDDING-MODEL` — 6C 는 닫지 않는다(D-6C-6, 기존).
- `OPEN-6C-IMAGE-VULN-SCAN` — SBOM·CVE 스캔은 6E(D-6C-5, 기존).
- `OPEN-6C-MULTIARCH` — arm64/amd64 멀티아키 빌드는 배포 대상이 정해질 때(기존).
- `OPEN-6C-CONDITIONAL-GATE-TEST`(신설, scope.md) — `adapters.ml` 안에 환경 조건부 test 가
  실제로 필요해지면 그때 설계 검토로 게이트 술어 개정을 받는다. 지금은 패키지 분리로 피했다.
- compose 의 `ml-serving` 서비스는 host 포트 50051 을 그대로 연다(판단 4) — CI 러너에서
  드물게 다른 job 과 충돌할 수 있다(이 slice 의 CI job 은 그 포트를 다른 무엇도 쓰지 않아
  이번 실행에서는 문제가 없었다). 충돌이 관측되면 host publish 를 완전히 빼는 쪽으로 정리한다.
- 이미지 base·postgres 이미지 다이제스트는 2026-09-16 pull 시점 값으로 고정했다(재현성) —
  상류가 그 태그를 재빌드하면 다이제스트가 바뀌어 위생 게이트의 라벨 대조가 실패할 수 있다
  (그 경우 Dockerfile 의 다이제스트를 재실측해 갱신하는 것이 정상 대응이지 게이트 결함이
  아니다).

## OPEN 처분 확인 (scope.md 대비)

| 식별자 | scope.md 처분 | 이 slice 실측 |
| --- | --- | --- |
| `OPEN-5E2-CROSSLANG-REAL-SERVER` | 이 slice ④로 종결 예정 | **종결** — `RealServerIntegrationTest` 4건, 실 컨테이너 대상 통과(commands.md) |
| `OPEN-5E-EMBEDDING-MODEL` | 유지, 6C 밖 | 유지(무편집) |
| `OPEN-5E-JOB-PERSISTENCE`·`OPEN-5E-JOB-QUEUE-BOUND` | 6B | 유지(무편집, compose 의 postgres 가 자리만 만듦) |
| `OPEN-6C-IMAGE-VULN-SCAN`(신설) | D-6C-5, 6E | 신설 그대로 |
| `OPEN-6C-MULTIARCH`(신설) | 6E | 신설 그대로 |
| `OPEN-6C-CONDITIONAL-GATE-TEST`(신설, 팀장) | 잔여 | 신설 그대로 |
| `OPEN-ADR-09` | 닫힘(운영자 결정) | 이 slice 밖(6A 소관), 무변경 |
