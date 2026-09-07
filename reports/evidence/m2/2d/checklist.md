# M2/2D — checklist.md (verifier ready-for-review 점검)

CLAUDE.md 운영자 지시(2026-09-04) — 코드 slice 는 Codex 심판 대상이 아니다. 이 문서는
milestone-2.md 의 「Codex approve + 사용자 승인」을 **「verifier ready-for-review + 사용자
승인」**으로 읽는 근거를 든다.

## 리뷰 요청 조건

- [x] 구현 diff가 커밋되어 base/head 고정 — base `e3ac98b`(scope.md 와 정합, verifier r1
      F-4) / head 는 commands.md 상단 참조. `git log --oneline e3ac98b..HEAD -- <in_scope
      경로>`로 확인. `git status --porcelain -- <in_scope 경로 개별 인자>` 결과 없음
      (commands.md 「clean-tree 게이트」). 양성 대조(`contracts/buf.yaml` 한 줄 추가 →
      잡힘 → 복원)로 판정 자체를 검증했다.
- [x] scope.md의 acceptance_commands(S-0~S-7) 전부 exit 0으로 commands.md에 기록됨
      (verifier r1 수정 라운드 뒤 재실행 포함).
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `./gradlew --no-build-cache
      clean check`(격리 worktree 포함) · Kotlin `*Contract*` test · Python pytest ·
      `contractGate` · breaking mutation 스크립트. 전부 verifier r1 수정 뒤 재확인.
- [x] 변경된 fixture와 정책 version의 근거가 기록됨 — `contract-policy.properties
      policy.version=1`(신설, verifier r1 F-13 로 `tool.protoc.gen.grpc.java` 추가),
      `max.message.bytes` 계산 근거는 파일 자체 주석 + commands.md 재현 절차, breaking
      mutation 11종의 실측 결과는 `contracts/testdata/breaking/expected.tsv`.
- [x] 알려진 제한과 rollback 방법이 기록됨 — 아래 「알려진 제한」 + `rollback.md`
      (verifier r1 F-5 로 rollback 대상 제외 경로를 명시).
- [x] secret 스캔 통과 — commands.md 「secret 스캔」 절. **verifier r1 F-11** — 이전 판은
      `commands.md` 자신이 grep 패턴을 인용문으로 담고 있어 evidence 디렉터리 전체 스캔이
      자기 참조로 매치 4건을 냈다(전부 `commands.md`의 명령 인용 줄, 실제 secret 아님).
      commands.md 를 제외한 재스캔과 diff 쪽 스캔은 매치 0건 — commands.md 갱신 절 참고.

## 게이트가 실제로 잡는다는 증거(milestone-2.md 완료 조건)

commands.md 「회귀 확인」 6종(mutation 빈 집합·against 무효값·max_message_bytes 불일치·
unknown enum 거부 제거·도구 버전 리터럴 어긋남·ml-contract 무단 소스)이 전부 의도한
게이트/test 에서 실패로 잡혔고, 원복 후 재확인이 전부 exit 0 이다.

**verifier r1 F-1(high) 수정 뒤 추가 확인**: `contractGate`의 generateProto 결정성 검사가
그 전에는 build cache 복원 둘을 비교해 구조적으로 실패할 수 없었다(nested `gradlew` 호출에
`--no-build-cache` 부재 — 격리 사본에서 재현하면 두 회차 모두 `FROM-CACHE`). 중첩 호출에
`--no-build-cache`를 더하고 나서는 두 회차 모두 실제 `generateProto` 실행이다(재현: 수정
전/후 대비, commands.md). 빈 산출물 집합 둘도 위반으로 낸다(산출물 경로가 바뀌어도 안전측
실패).

**F-2(medium) 수정 뒤 추가 확인**: `tools/contract-crosslang-smoke.sh`의 gradle 호출이
build cache 를 복원해 소켓을 열지 않고 exit 0 을 낼 수 있었다 — `--rerun-tasks
--no-build-cache` 추가 뒤 연속 두 번 실행 모두 실제 소켓 실행으로 확인했다.

## 알려진 제한

1. **buf 가 못 잡는 mutation — 없음(실측으로 닫힘), 단 STANDARD 규칙집합 전제.** 조사
   노트 02 가 `optional` 제거의 buf 검출 여부를 미확인으로 남겼으나, buf 1.72.0 STANDARD
   규칙집합이 cardinality 변경으로 직접 잡음을 실측 확인했다
   (`contracts/testdata/breaking/expected.tsv`). 별도 대체 검출 수단은 불필요하다.
   **verifier r1 실측(신규 우회 (10))** — `contracts/buf.yaml`의 breaking 규칙집합을
   `STANDARD`에서 `WIRE`로 낮추면 `rpc-delete`·`optional-removal` 2종이 빠져나간다(S-3
   가 9/11 로 즉시 잡지만, S-3 는 `check`/CI 밖이라 `gradlew check`만 도는 CI 는 이
   약화를 못 본다 — 알려진 잔여 위험, 고의 편집은 위협 모델 방어 대상 밖이지만 실수 편집은
   상시 게이트가 못 본다는 사실 자체는 등재해 둔다).
2. **Python 쪽 test 의 CI 자리(D-2D-4)** — 저장소 CI(`​.github/workflows/**`)는 JDK만
   세운다. `ml-engine/tests`(S-5)는 로컬 acceptance 로만 확인했고, CI 등재는 M5 5A 가
   Python 툴체인을 세울 때 승계한다.
3. **교차 언어 socket 스모크(S-6)의 상시화 자리 — M6.** D-2D-3 (a)에 따라 로컬 실측
   1회 + evidence 기록만 하고 CI 필수 게이트로 두지 않는다. 신설 `OPEN-2D-CROSSLANG-CI`.
4. **`ContractMaxPayloadTest`/`test_above_limit_sample_is_rejected_over_real_socket`의
   초과분 status 코드** — 경계에 딱 붙은 초과 표본(86B)은 서버가 `RESOURCE_EXHAUSTED`를
   보내기 전에 스트림이 리셋돼 client 가 `CANCELLED`를 받는 경우가 실측으로 확인됐다
   (Kotlin·Python 양쪽, HTTP/2 프레이밍 타이밍 경합). 두 test 모두 두 status 코드를 함께
   허용한다 — 값 동일성의 결정적 증거는 별도의 순수 크기 비교 test(`경계 쌍의 바이트
   크기가...`)가 결정적으로 든다.
5. **취소 검증(⑥, D-2D-7)의 한계** — servicer 의 `CancellationException` 직접 수신으로
   검증하되(플래그 폴링 아님), 실제 grpc/grpc#36193 버그(콜백 중 `cancelled()` False)
   자체는 재현하지 않는다 — 이 slice 의 test 는 grpc-kotlin coroutine cancellation 이
   servicer 의 계산을 실제로 중단시킨다는 것만 증명한다.
6. **`OPEN-2A-INCLUDED-BUILD` 게이트화 완료** — `contractGate` (f)가 `ml-contract/src`
   부재 + 최상위 파일 셋을 상시 단언한다(회귀 확인 6에서 실측). OPEN 자체는 닫히지 않고
   "이제 게이트가 지킨다"로 상태가 바뀐다 — 문서 갱신은 milestone-2.md 관리자 몫.
7. **max payload 는 요청 방향만 실측(verifier r1 F-10)** — `ContractMaxPayloadTest`·
   `test_above_limit_sample_is_rejected_over_real_socket`의 KDoc/docstring 이 "응답
   방향은 별도 fixture 를 만들지 않는다"고 이미 적었으나 이 문서에 등재되지 않았다.
   channel·server 양쪽에 정책 값을 걸었지만(scope.md ⑤ "양쪽 옵션"), 실제로 재는 것은
   요청 방향(server 의 수신 제한)뿐이다 — 응답 방향(channel 의 수신 제한)은 grpc-java·
   grpc-python 의 같은 강제 경로를 타므로 별도 대형 응답 fixture 를 새로 만들지 않았다.
8. **정책 파일의 legacy `file:line` 인용(verifier r1 F-12)** — `contract-policy.properties`
   가 legacy `paper_bidding.py:62`·`task_payloads.py:262`를 인용한다. 「편집 대상 파일에
   `file:line` 금지」 규격의 문면과는 어긋나지만, legacy 는 `ed4b06c` 에 고정된 read-only
   참고 저장소라 이후 legacy 편집으로 좌표가 밀릴 위험이 없다 — 세션 모델 결정으로
   인용에 `ed4b06c`를 병기하고 유지한다.
9. **`sed -i ''`(BSD 전용) 의존을 걷었다(verifier r1 F-9)** — `breaking-mutations.sh`가
   임시 파일 경유 `sed_inplace` 헬퍼로 GNU/BSD 양쪽에서 동작한다. D-2D-4 (a)로 이 스크립트
   자체는 지금 CI 밖이라 이전에도 무해했지만, 이식성 자체를 닫아 M5 5A 승계 부담을 줄인다.

## 판단이 갈린 지점

- **`tools/**`(저장소 루트)가 scope.md 의 in_scope 경로 목록에 없었다 — 정정 확인
  (verifier r1 §4).** 같은 문서의 acceptance S-6과 「이 slice 가 하는 일」 ⑧이 정확히
  `tools/contract-crosslang-smoke.sh` 경로를 지정하므로 누락으로 판단해 진행했고,
  세션 모델이 `9d9e863`으로 scope.md 의 in_scope 에 그 경로를 추가하고 리뷰 시점 range
  를 선언했다 — 이 구현 레인이 직접 고치지 않고 기록만 남긴 처리가 규율에 맞았다.
- **max_message_bytes 계산의 "표본 건수 상한" 근거** — 계약 자체에는 `CompetitionSample`
  개수 상한이 없다. legacy 의 유일한 실측치(`history_limit` 상한 500, `bid-vector`
  `app/schemas/paper_bidding.py`)를 근거로 채택했다 — 실측 트래픽이 나오면(M5) 재계산
  대상.
- **max payload 경계 test 를 in-process 가 아니라 localhost 소켓(Netty/asyncio)으로
  바꿈** — 실측: grpc-java/grpc-python 의 in-process 전송은 메시지를 marshaling 없이
  참조로 넘겨 `maxInboundMessageSize`를 강제하지 않는다. D-2D-3 이 금지하는 것은 **교차
  언어** socket 상시화이지, 같은 프로세스 안의 단일 언어 socket test 가 아니다 — 이
  구분으로 진행했다.
- **`crossLangSmokeTest`를 `check`에서 빼는 방법** — `group`을 바꾸는 시도가 실패해
  (Gradle 이 `Test` 타입 task 를 이름·group 과 무관하게 `check`에 엮음을 실측),
  `onlyIf(PresentSpec(...))`로 전제 부재 시 자체 skip 하도록 바꿨다. 컴파일된
  `PresentSpec`(build-logic)을 쓴 것은 config cache 가 스크립트 closure 의 암묵적
  캡처를 직렬화하지 못해서다(실측).
