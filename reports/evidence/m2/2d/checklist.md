# M2/2D — checklist.md (verifier ready-for-review 점검)

CLAUDE.md 운영자 지시(2026-09-04) — 코드 slice 는 Codex 심판 대상이 아니다. 이 문서는
milestone-2.md 의 「Codex approve + 사용자 승인」을 **「verifier ready-for-review + 사용자
승인」**으로 읽는 근거를 든다.

## 리뷰 요청 조건

- [x] 구현 diff가 커밋되어 base/head 고정 — base `458dce7` / head 커밋은
      `git log --oneline 458dce7..HEAD -- <in_scope 경로>`로 확인. `git status --porcelain
      -- <in_scope 경로 개별 인자>` 결과 없음(commands.md 「clean-tree 게이트」). 양성
      대조(`contracts/buf.yaml` 한 줄 추가 → 잡힘 → 복원)로 판정 자체를 검증했다.
- [x] scope.md의 acceptance_commands(S-0~S-7) 전부 exit 0으로 commands.md에 기록됨.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `./gradlew --no-build-cache
      clean check`(격리 worktree 포함 2회) · Kotlin `*Contract*` test 113개 · Python
      pytest 95개(3회 반복) · `contractGate` · breaking mutation 스크립트.
- [x] 변경된 fixture와 정책 version의 근거가 기록됨 — `contract-policy.properties
      policy.version=1`(신설), `max.message.bytes` 계산 근거는 파일 자체 주석 +
      commands.md 재현 절차, breaking mutation 11종의 실측 결과는
      `contracts/testdata/breaking/expected.tsv`.
- [x] 알려진 제한과 rollback 방법이 기록됨 — 아래 「알려진 제한」 + `rollback.md`.
- [x] secret 스캔 통과 — commands.md 「secret 스캔」 절, 매치 0건(evidence 디렉터리 +
      diff 양쪽).

## 게이트가 실제로 잡는다는 증거(milestone-2.md 완료 조건)

commands.md 「회귀 확인」 6종(mutation 빈 집합·against 무효값·max_message_bytes 불일치·
unknown enum 거부 제거·도구 버전 리터럴 어긋남·ml-contract 무단 소스)이 전부 의도한
게이트/test 에서 실패로 잡혔고, 원복 후 재확인이 전부 exit 0 이다.

## 알려진 제한

1. **buf 가 못 잡는 mutation — 없음(실측으로 닫힘).** 조사 노트 02 가 `optional` 제거의
   buf 검출 여부를 미확인으로 남겼으나, buf 1.72.0 STANDARD 규칙집합이 cardinality 변경으로
   직접 잡음을 실측 확인했다(`contracts/testdata/breaking/expected.tsv`). 별도 대체 검출
   수단은 불필요하다.
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

## 판단이 갈린 지점

- **`tools/**`(저장소 루트)가 scope.md 의 in_scope 경로 목록에 없다** — 같은 문서의
  acceptance S-6과 「이 slice 가 하는 일」 ⑧이 정확히 `tools/contract-crosslang-smoke.sh`
  경로를 지정하므로 누락으로 판단해 진행했다. scope.md 는 세션 모델이 단독 저작하므로
  이 구현 레인이 직접 고치지 않고 여기 기록한다.
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
