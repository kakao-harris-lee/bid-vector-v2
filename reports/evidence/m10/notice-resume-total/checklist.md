# M10 / notice-resume-total — 리뷰 요청 조건과 범위 대조

## 리뷰 요청 조건

- [x] 구현 diff 가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 개별 인자>` **빈 출력**,
      **양성 대조 1회**(in_scope 파일 하나를 건드려 잡히는지 확인하고 **비파괴 절삭**으로 복원 — `checkout --` 금지)
- [x] acceptance 가 exit 0 으로 `commands.md` 에 기록 — CI Kotlin job 명령 그대로(`./gradlew --no-daemon check`),
      부분 게이트로 갈음하지 않았다
- [x] test·lint·type·architecture·contract 는 그 전건에 들어 있다
- [x] fixture 변경 **없음** · 정책 version 변경 **없음**(이 slice 는 정책 데이터를 만지지 않는다)
- [x] 알려진 제한과 rollback 기록 — 아래와 `rollback.md`
- [x] 비밀값 스캔 — 패턴 파일 참조형으로 in_scope 와 이 장부에 대해 실행, **매치 없음**(exit 1 = 통과).
      패턴으로 못 잡는 축은 육안 — 이 slice 가 만지는 값은 정수 회계뿐이다

## 알려진 제한

1. **닫히는 범위는 배치 경계뿐이다**(D-5) — **한 배치 안**에서 뒤 쪽이 총수를 줄이면 어댑터 누산기가 마지막
   값을 쥐고 use case 는 그 결과만 받는다. 쪽 상한을 넘지 않는 정상 슬롯은 배치가 하나라 이 보호가 뜻을
   갖지 않는다. 낙찰 갈래와 **같은** 열린 판이다.
2. 공유 누산기는 여전히 「마지막 값」이다(D-1 승인 경계). 두 갈래가 같은 한 자리를 부르므로 그것을 최댓값으로
   올리는 날 걷을 자리는 하나다.
3. 이미 **0 으로 적힌 과거 회계 행은 고치지 않는다**(소급 없음) — 다음 수집부터 넓은 값이 적힌다.
4. `CollectNoticesUseCaseTest.kt` 가 **495 줄**로 500 줄 한도에 가깝다. 다음에 이 파일에 test 를 더하는 slice 는
   크기 회피용 기계적 분할이 아니라 **축 기준**으로 파일을 갈라야 한다.
5. **변이 실행이 같은 worktree 의 다음 전건을 한 번 붉힌다**(산출물 결함 아님 — 로컬 전용). 판정과 근거 셋은
   `commands.md` 의 전건 2차 절이 갖는다. 처방은 변이 라운드 뒤 `:workflow:cpdCheck --rerun`.

## 범위 대조

- `git diff --name-only <base>..HEAD` ↔ in_scope: **일치**, in_scope 밖 **0**.
- 하네스(`CLAUDE.md`·`.claude/**`) 커밋 **0**(scope.md 「하네스 레인 변경」 절과 같은 값).
- out_scope 단언: `SourceAccountingTally.kt`·`procurement/**`·`adapters/**`·`app/**` diff **빈 출력**(D-1).
- production 변경은 **use case 한 자리**이고 나머지는 이동·test·장부다.
- `bid-vector` symlink·다른 worktree·운영 host·DB·외부 API **무접촉**. 실행한 것은 Gradle task 뿐이다.
- 수정 라운드가 만든 새 파일: 산출물 쪽 **하나**(`ClaimedSourceTotal.kt` — 계약 in_scope 의 「새 파일 하나」),
  그 밖의 신규는 이 slice 의 장부뿐이다.

## 크기 게이트 — 초과, 판정에 올린다

자기를 세는 수치라 **기준을 명시한다**: 분자는 `<base>..HEAD` 의 추가 줄 바이트(diff 머리 제외 · evidence 제외)
**7,814**, 분모는 이 장부 넷의 바이트 **20,440** = **2.62 배**. 계약(`scope.md`, 5,215 — 착수 커밋이 세운 것이라
이 레인이 줄이지 않는다)을 빼면 이 레인이 쓴 셋은 15,225 = **1.95 배**.

분모가 작은 사유는 산출물이 **production 한 자리 + 이동 + test 하나**라는 것이고, 부푼 자리는 `rollback.md` 의
필수 절(원천·전제·목록 등식·①~⑥·표지 표·유효성 술어)이라 그 아래로 줄이면 규격이 요구하는 사실이 빠진다.
이 문단이 그 뒤로 분자를 몇 줄 늘리므로 **확정 정본은 verifier 보고와 PR 조치 코멘트**다.

## OPEN 처분

`OPEN-M9-NOTICE-LIST-RESUME-TOTAL` **닫힘** — capability map §12.3 **(g)** 에 등재. 신설 OPEN 없음.
