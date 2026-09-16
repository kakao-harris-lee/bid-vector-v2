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
   조건부로 항상 건너뛸 수 있어야 하는 test(D-6C-4)와 동시에 만족될 수 없었다. 구현
   레인이 제시한 선택지 셋(A. 게이트에 조건부 허용 키 신설 B. 등재 요구 예외 목록 신설
   C. 패키지 재배치) 중 팀장은 **C 를 채택하고 A·B 를 명시적으로 거부**했다 — A·B 는
   둘 다 게이트 술어에 "조건부 허용" 문을 만드는데, 그 문이 한 번 열리면 **어떤 test 든
   자기를 조건부로 선언해 영원히 안 돌 수 있게 된다**(오탐을 닫으려다 미탐을 여는
   방향, 하네스 2026-09-04 게이트 술어 조항과 상충). 이 slice 자체가 "게이트 술어는
   문자열이 아니라 구조로"를 세우는 자리라 그 술어를 여기서 약화시키면 앞뒤가 맞지
   않는다는 것이 거부 근거다. C 는 회피가 아니라 올바른 재분류다 — `CrossLangSmokeTest`
   (교차 언어 스모크)가 이미 같은 이유로 `adapters.contract`에 있고, 이 test 는 그
   컨테이너 판이다. `build-logic`·`gate-tests.properties`·`MlGateRegistrationTest` 는
   전부 무편집(팀장 지시대로 확인, `git diff f3ac571 -- config/quality/gate-tests.properties`
   빈 결과). 이 결정은 구현 레인이 직접 하지 않고 팀장에게 보고해 계약 갱신
   (scope.md D-6C-8·`OPEN-6C-CONDITIONAL-GATE-TEST`)으로 받았다.

판단 1·2 는 이후 팀장의 계약 갱신 (3)이 scope.md in_scope 목록에 명시 반영했다
(`image-hygiene-policy.properties` 추가, dockerignore 파일명 정정) — 구현 레인의 판단이
그대로 계약에 채택됐다.
6. **`manySampleBidPredictionRequest`/`reserveDrawObservation` 신설(실 서버가 처음으로 겪는
   입력 형태).** 기존 fake servicer consumer test 의 공용 fixture(`testBidPredictionRequest()`,
   표본 1건, `reserveDraw` 없음)를 그대로 실 서버에 보내면 `Unmeasurable(InsufficientSamples)`
   였다(실측, 두 단계: ① `reserveDraw` 미설정 → 전 표본 `NO_RESERVE_DRAW`로 제외 ②
   `reserveDraw`를 채우자 `Predicted`로 성립). 실 엔진의 표본 하한(`reserve.min_reserve_records`
   =8·`bid_ratio.min_samples`=3·`reserve.expected_price_count`=15)을 만족하는 별도 fixture를
   새로 짰다 — 값 계산 축이고 이 slice 가 잡는 소비자 규칙 축이 아니라서 도메인 규칙을
   바꾸지 않는다(엔진 코드 무편집, `ml-engine/src/**` out_of_scope 유지).

## 수정 라운드 1 — verifier r1 · code-reviewer 처분

verifier r1 not-ready(HIGH 2·MEDIUM 4·LOW 6) · code-reviewer 머지 가능(MEDIUM 1·LOW 2).
HIGH 둘은 D-6C-9(scope.md 계약 갱신 (4))로 처방이 계약에 이미 고정됐다 — 여기는 처리
여부와 표적 재검증 결과만 정리한다.

| ID | 출처 | severity | 처분 | 근거 |
| --- | --- | --- | --- | --- |
| F-1 | verifier | HIGH | **수정** — pid 1 실측(`docker top`)으로 교체 | D-6C-9, commands.md 변이 재현+시정 |
| F-2 | verifier | HIGH | **수정** — layer 접두 대조로 교체, 라벨은 보조 | D-6C-9, commands.md 변이 재현+시정 |
| F-3 | verifier | MEDIUM | **수정** — 중복 키 매치 수 검사(1 아니면 exit 2) | 같은 커밋(게이트 술어 자리라 표적 재검증 대상) |
| F-4 | verifier(+reviewer MEDIUM 같은 자리) | MEDIUM | **수정** — 스캔 경로에 `ci.yml` 추가·최종 test 경로로 정정·DB 접속 값을 파일 리터럴 대신 실행 시점 생성으로 | commands.md·ci.yml |
| F-5 | verifier | MEDIUM | **수정** — rollback.md 시작 트리를 HEAD 기준으로 재작성(아래 rollback.md 참고) | 이 라운드 마지막에 최종 HEAD 로 재작성·재실측 |
| F-6 | verifier | MEDIUM | **수정** — `qualityBaseline` 단계 추가(CI 축어 일치) + `container` job 에 S-20 step 신설 | one-command-check.sh·ci.yml |
| F-7 | verifier | LOW | **수정** — transport 실패·envelope 거부·`NOT_READY` 셋을 서로 다른 문면으로(종료 코드 계약은 그대로 1) | docker/probe/readiness.py, 실측 셋(READY·transport·NOT_READY) |
| F-8 | verifier | LOW | **등재**(알려진 제한) — 다른 이름 벤더링은 다섯 이름 열거를 비껴간다, 기존 CI S-1b 승계 한계 | 알려진 제한 절 |
| F-9 | verifier | LOW | **등재**(알려진 제한) — 로컬 단독 반복 실행 시 낡은 이미지, CI 경로는 S-21 선행이라 무해 | 알려진 제한 절 |
| F-10 | verifier | LOW | **등재**(알려진 제한) — 도메인 계약만 증명, 서버 내부 경로 결합은 하지 않음(verifier 가 이미 직접 RPC 로 확정) | 알려진 제한 절 |
| F-11 | verifier | LOW(장부) | **처리 없음** — 동결 핀 지연은 팀장 하네스 후보 메모(m6-lane-map) 소관, 산출물 무관 | verifier 리포트 자체가 최종 정본 |
| F-12 | verifier | LOW(호스트) | **처리 없음** — 다른 레인이 지운 이미지, verifier 가 재빌드해 재측정 완료 | verifier 리포트 |
| reviewer MEDIUM | reviewer | MEDIUM | **수정** — F-4 와 같은 자리(위 F-4 처분과 동일) | commands.md |
| reviewer LOW(죽은 COPY) | reviewer | LOW | **수정** — builder stage `COPY ml-engine/policy` 제거(uv build 무참조, grep 확인) | docker/ml-serving.Dockerfile |
| reviewer LOW(임시 wheel 정리) | reviewer | LOW | **수정** — `mktemp -d` + `trap ... EXIT` 로 스크립트 종료 시 항상 정리 | tools/one-command-check.sh, 실패 종료 포함 실측 |

## 수정 라운드 2 — verifier r2 처분(r1 HIGH 둘은 닫힘, 수정 라운드가 새 HIGH 둘을 만듦)

r2 판정: r1 HIGH(F-1·F-2)는 닫혔다(더 어려운 변이에도 버팀). **그런데 r1 을 닫은 커밋이
새 HIGH 둘을 냈다** — 하네스 「low 를 닫은 커밋이 high 를 낳았다」계열의 재발. D-6C-10 처방을
그대로 반영한다.

| ID | severity | 처분 | 근거 |
| --- | --- | --- | --- |
| R2-1 | HIGH | **수정** — 빈 값 거부 복원 + kind(수치/목록)별 모양 검증 + CRLF 절삭. 수치 비교를 `elif`에서 뽑아 독립 `if`(`_check_at_most`)로 | `_policy_value`가 F-3 수정에서 값 모양 검증을 지웠던 것을 되살림, 변이 넷 재현+시정 |
| R2-2 | HIGH | **수정** — S-20 을 `container` job → `check` job 으로 이동(그 job 이 이미 `buf`+`fetch-depth: 0`을 가짐) | `container` job 에 그 전제가 없어 S-20 이 반드시 실패하고 뒤 S-21~S-25 전부가 CI 미실행이었다 |
| R2-3 | MEDIUM | **수정** — `docker run --security-opt no-new-privileges`(위생 게이트)·`security_opt: [no-new-privileges:true]`(compose) | `sleep 1` 한 점 표집은 표집 뒤 상승하는 entrypoint 를 놓친다. 능력 자체를 차단해 표집 시점과 무관하게 만든다 |
| R2-4 | MEDIUM | **수정** — `base.image.layers`(목록) → `base.image.repo`+`base.image.digest`(다이제스트 하나) + 게이트가 `docker buildx imagetools inspect`로 그때그때 layer 체인 파생 | 목록 자체를 변이 이미지 것으로 바꿔치면 통과했다 — 바꿔치기 표면을 다이제스트 하나로 줄인다 |
| R2-5 | LOW | **수정** — 「행 수 ≠ 1」이 아니라 **행마다 uid** 를 검사 | 프로세스 여러 개가 되는 날에도 root 자식이 조용히 통과하지 않게 |
| R2-6 | LOW | **수정** — rollback.md 잔여 절의 명령 이름을 `git status --porcelain` → `git diff --name-status f3ac571`로 정정 | 관측 자체는 맞았고 명령 이름이 틀렸다 |
| R2-7 | LOW | **등재**(실질 이탈 아님, r1 판정 유지) — wheel `-o` 인자가 `mktemp -d`라 CI 고정 경로와 다르다. r1 MEDIUM(임시 정리) 시정의 의도된 결과 | verifier r2 도 같은 결론(정리 동작 실측 확인, 이탈 아님) |
| R2-8 | LOW | **수정** — 게이트 시작부에 `command -v jq` 로 명시적 선결조건 확인(없으면 exit 2, 오귀속 방지) | `jq` 가 `ubuntu-latest` 선탑재라 지금은 서지만 미선언 의존이었다 — 없으면 (2) 절이 엉뚱한 사유로 떨어졌다 |

### R2-3 능력 차단 실측(출하 서고 변이 실패)

시간차 setuid 변이(pid 1 은 시작부터 끝까지 하나 — 5초 뒤 자기 자신을 setuid-root 실행
파일로 `execve`하는 2단계 재현, r2 의 실측 형태 그대로):

| 조건 | t=1s(1단계) | t=7s(exec 뒤 2단계) |
| --- | --- | --- |
| `no-new-privileges` 없이 | euid 10001 | **euid 0(상승 성공)** |
| `no-new-privileges` 로 | euid 10001 | euid 10001(**상승 실패**) |

같은 변이 이미지에 위생 게이트(항상 `no-new-privileges`를 건다)를 그대로 돌리면 exit 0 —
**출하 이미지가 정상 기동하는 것과 같은 이유로**(능력이 차단돼 상승 자체가 안 된다) 통과한다.
구판(플래그 없이 한 점만 표집)을 이 이미지에 시뮬레이션하면 `docker top`이 t≈1s 에 표집돼
그대로 통과했을 것 — r2 가 지목한 정확한 맹점을 재현했다. 출하 이미지(`bidvector/ml-serving:local`)
는 이 옵션 아래 정상 기동해 S-22·S-23(compose healthcheck)이 그대로 통과한다(commands.md).

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
  상류가 그 태그를 재빌드하면 다이제스트가 바뀐다. **수정 라운드 2(R2-4, D-6C-10 ②) 이후**:
  위생 게이트는 정책의 `base.image.digest`(Dockerfile 의 FROM 과 같은 값) 하나만 갖고
  `docker buildx imagetools inspect`로 layer 체인을 **그때그때** 파생한다 — layer 목록을
  정책에 다시 적지 않으므로 "정책과 이미지가 몰래 같이 바뀌는" 경로가 없다. `FROM`을
  바꾸면 이 값도 같이 바꾸고, 이미지 라벨(보조 정보)도 사람이 리뷰에서 일치를 확인한다.
  **새 알려진 제한(R2-4 대가)**: 이 파생에 **레지스트리 네트워크 접근이 필요**하다 — 접근이
  없는 오프라인 환경에서는 이 축이 판정 불가로 실패한다(fail-closed, 조용한 통과는 아니다).
  CI 러너(`ubuntu-latest`)와 이 개발 환경 둘 다 접근이 있어 실측 확인했다.
- (수정 라운드 1 신설, F-8 승계) 금지 패키지 판정은 다섯 **이름**을 실제 import 해 잡는다
  (우회 (2) 닫힘 — 같은 이름의 dist-info 없는 사본도 잡는다, verifier r1 MUT-C1 재확인).
  **다른 이름으로 벤더링한 사본**(예: `sqlalchemy`를 `_vendored_sqlalchemy`로 복사)은 이
  다섯 이름 열거를 비껴간다 — 기존 CI S-1b(`ml-engine` job)도 같은 다섯 이름을 쓰는 승계된
  한계라 이 slice 가 새로 만든 구멍이 아니다. 열거를 벗어나는 임의 벤더링까지 잡으려면
  런타임 site-packages 전체 스캔이 필요한데 그 정책 설계는 6C 범위 밖이다.
- (수정 라운드 1 신설, F-9 승계) `RealServerIntegrationTest`의 컨테이너 자기 확인은
  요청한 이미지 태그(`bidvector/ml-serving:local`)만 단언한다 — Testcontainers 의 기본
  pull 정책이 로컬에 이미 있는 같은 태그를 그대로 쓰므로, 로컬에서 이미지 재빌드 없이
  이 test 만 반복 실행하면 낡은 이미지로 통과할 수 있다. CI `container` job 은 항상
  S-21(빌드)이 이 test(S-24)보다 먼저 돌아 매 실행마다 이미지가 최신이므로 CI 경로는
  무해하다 — 로컬 단독 실행 시의 트레이드오프로 등재한다.
- (수정 라운드 1 신설, F-10 승계) 옛 `featureSchemaVersion` 음성 대조 test 는
  `MlUnavailableReason.UnsupportedSchema`라는 **결과값**만 단언한다 — 그 값은 서버의
  `FAILURE_CODE_UNSUPPORTED_SCHEMA` 거부와 `ResponseMapping.mapSuccess`의 클라이언트
  집행 분기 둘 다에서 나올 수 있어 test 만으로는 어느 경로인지 안 갈린다. verifier r1가
  직접 RPC(스키마 필드만 다른 요청 한 쌍)로 확인한 결과 **서버 쪽 거부가 표본 처리보다
  먼저** 일어난다(`serving/prediction.py`의 `_validate` 순서와 일치). 이 test 는 도메인
  계약("옛 스키마는 거부된다")만 증명하면 충분하다고 보고 어느 내부 경로인지 노출하는
  추가 단언은 더하지 않는다 — 서버 내부 구현(out_of_scope, `ml-engine/src/**`)에 test 를
  결합시키는 쪽이 더 나쁘다고 판단했다.

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
