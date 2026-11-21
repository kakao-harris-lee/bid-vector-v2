# M15 / 15A — 실행 명령과 종료 코드

> 명령만 남긴다(출력 전문 금지). 감사자는 명령을 다시 돌린다.
> 마지막 HEAD 의 게이트 결과 정본은 **verifier 와 PR 조치 코멘트**다 — evidence 는 자기 마지막
> 커밋의 post-state 를 담을 수 없다(`evidence-pack` 규격).

## 착수 실측 (base `99f2fb25`)

- cmd: `git rev-parse 99f2fb25` · `git merge-base --is-ancestor 99f2fb25 HEAD`
- exit: 0
- 핵심 결과: base 40자가 `99f2fb25989078d7ab1640de0dc8e399f5d1688d` 이고 HEAD 의 조상이다.

- cmd: `ls adapters/src/main/resources/db/migration | sort -V | tail -1`
- exit: 0
- 핵심 결과: `V27__execution_row_release.sql` — V28 이 다음 번호다(계약 배정과 같다).

## RED — V28 이관 갈래 셋

- cmd: `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.persistence.AccountScopedMigrationTest'`
- exit: 1
- 핵심 결과: 5 tests, 5 failed — `No migration with a target version 28 could be found`.

## GREEN — V28 + 마이그레이션 래칫 다섯

- cmd: `./gradlew --no-daemon :adapters:test --tests '…AccountScopedMigrationTest' --tests '…CleanMigration*' --tests '…MigrationLockTimeoutTest'`
- exit: 0
- 핵심 결과: AccountScopedMigrationTest 5 · CleanMigration* 22 · MigrationLockTimeoutTest 4 — 실패·건너뜀 0.

## 컴파일 — 계정 인자의 전수성 확인

- cmd: `./gradlew --no-daemon :workflow:compileKotlin` → `:app:compileKotlin`
- exit: 0
- 핵심 결과: 출하 소스 전건. 포트 서명 변경이 멈춰 세운 자리를 전부 고쳤다는 뜻이다.

- cmd: `./gradlew --no-daemon :adapters:compileTestKotlin :workflow:compileTestKotlin` → `:app:compileTestKotlin`
- exit: 0
- 핵심 결과: test 전건. 「계정을 빠뜨린 호출」이 남아 있지 않다 — 그것이 서명에 계정을 둔 이유다.

## acceptance — Kotlin `check` 전건

- cmd: `./gradlew --no-daemon check`
- exit: (아래 「마지막 HEAD」 참고)
- 핵심 결과: (아래 「마지막 HEAD」 참고)

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m15/15a/`
- exit: 1 (매치 없음 = 통과)
- 핵심 결과: 패턴 파일 참조형으로 돌렸다 — 어휘를 이 문서에 축어로 적지 않는다.
  Telegram id·사업자 정보는 패턴으로 못 잡으므로 육안 확인을 병기한다: 이 slice 의 diff 에
  외부 식별자·실 계정 값이 0 이고, test 가 심는 값은 전부 `fixture-`·`합성`·`e2e-` 접두 합성값이다.

## 마지막 HEAD

evidence 를 편집하는 커밋의 post-state 는 이 문서가 담을 수 없다. 마지막 HEAD 의 `check`
결과는 **verifier 리포트와 PR 조치 코멘트**가 정본이다(`evidence-pack` 규격).
