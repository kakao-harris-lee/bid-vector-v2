# M3/3B-2 — rollback.md

base `0e83d6e84a5279352aae1584e3849aa355a991c8`. 경로 한정 되돌림(evidence-pack 2026-09-04 규격) —
range revert 가 아니라 in_scope 경로만 base 상태로 되돌린다. **verifier r2 G-3** — 이전 판은 F-3·F-8·
F-6 수정 라운드가 고친 `KonepsOpenApiNoticeSource.kt`·`Accounting.kt`·`AccountingTest.kt` 셋을
restore 목록에서 빠뜨려, 되돌린 트리가 `:adapters:compileKotlin` **exit 1**(`walkKonepsNoticePages`
인자 수 불일치·`defaultKonepsItemMapper` 미해결 참조)로 남았다. 목록은 `git diff --name-status
<base>..HEAD -- adapters procurement config` 로 기계 산출했다(손으로 다시 쓰지 않는다) — **파일이
늘면 이 명령을 다시 돌리고 아래 두 목록을 갱신한다.**

## 신설 파일(삭제)

```
git rm -- \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsIdentifierMasking.kt \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsLicenseLimitDocumentSource.kt \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsOpeningResultSource.kt \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsOperationDescriptor.kt \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsSourceConfig.kt \
  adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsIdentifierMaskingTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsLicenseLimitDocumentSourceTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsOpeningResultSourceTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsOperationDescriptorTest.kt \
  procurement/src/test/kotlin/bidvector/procurement/PortsTest.kt
```

실측(임시 clone, 2026-09-08): exit 0.

## 수정 파일(base 로 restore)

```
git restore --source=0e83d6e84a5279352aae1584e3849aa355a991c8 --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsOpenApiNoticeSource.kt \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsPageUriBuilder.kt \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsRawItemMapper.kt \
  procurement/src/main/kotlin/bidvector/procurement/Accounting.kt \
  procurement/src/main/kotlin/bidvector/procurement/FieldContract.kt \
  procurement/src/main/kotlin/bidvector/procurement/RawObservation.kt \
  procurement/src/main/kotlin/bidvector/procurement/CollectionPolicy.kt \
  procurement/src/main/kotlin/bidvector/procurement/DetailFetch.kt \
  procurement/src/main/kotlin/bidvector/procurement/Ports.kt \
  procurement/src/test/kotlin/bidvector/procurement/AccountingTest.kt \
  procurement/src/test/kotlin/bidvector/procurement/CollectionPolicyTest.kt \
  procurement/src/test/kotlin/bidvector/procurement/DetailFetchTest.kt \
  config/quality/gate-tests.properties
```

실측(임시 clone, 2026-09-08): exit 0. `config/quality/gate-tests.properties`는 공유 파일(3B
rollback 의 절차와 같은 자리) — base 자체가 이미 3A·3B 상태를 담고 있어 이 restore 한 번으로
「3B-2 가 넣은 줄만 제거, 3B 가 넣은 줄은 보존」이 정확히 실현된다(3B-2 는 base 위에 줄을
**추가만** 했다).

`KonepsOpenApiNoticeSource.kt`(F-4 수정으로 `itemMapper` 명시 인자가 붙은 파일)·`Accounting.kt`·
`AccountingTest.kt`(F-3·F-8 확장) 셋을 이번에 목록에 더했다 — G-3 가 지목한 누락분이다.

## 검증(임시 clone 실측, 2026-09-08 — 두 명령 적용 뒤 exit 0 만이 아니라 컴파일·test 까지 확인)

```
git diff --cached 0e83d6e84a5279352aae1584e3849aa355a991c8 -- <위 in_scope 경로 전부>
```

실측: **출력 0 byte** — 위 두 명령 적용 뒤 in_scope 경로가 base 와 바이트 단위로 같다.

```
git diff --cached 0e83d6e84a5279352aae1584e3849aa355a991c8 -- CLAUDE.md .claude/
```

실측: 출력 0 byte — 하네스 경로는 이 되돌림이 건드리지 않는다(애초에 diff 가 없다).

```
git status --short   # 커밋 전 staged 상태 실측
```

실측: 삭제 10(`D`)·수정 13(`M`), 그 외 0. `git commit` 뒤 `git status --short` 재확인 — 출력 없음
(트리가 깨끗하다).

```
./gradlew :adapters:compileKotlin :procurement:compileKotlin
```

**G-3 가 실측으로 exit 1 을 낸 자리** — 이번 clone 에서 재실측: **exit 0**(누락 셋을 복원한
결과). 컴파일 산출물에 `walkKonepsNoticePages` 인자 수 불일치·`defaultKonepsItemMapper` 미해결
참조 없음.

```
./gradlew :adapters:test :procurement:test
```

되돌린 트리에서 실측: **exit 0**. `KonepsOpenApiNoticeSourceTest` 20·`ServiceKeyTest` 3·
`KonepsAdapterDependencyTest` 1(3B 상태로 복귀, koneps 패키지에 3B-2 신규 클래스 없음)·
procurement 는 3A corpus 실행자 test 포함 전부 green. 실패·건너뜀 0.

되돌린 상태를 커밋한 뒤 `adapters/src/main/kotlin/bidvector/adapters/koneps/` 디렉터리 파일 수를
셌다 — **10개**로 3B 종결 시점(base)과 같다. 삭제 대상 열 파일은 `ls`(exit 1, "No such file")로
부재를 확인했다 — 신설 파일이므로 삭제가 정상 결과다.

## 되돌리지 않는 것

- `CLAUDE.md`·`.claude/**`(하네스 레인) — 이 slice 의 range 안에 하네스 커밋이 없다(scope.md
  「하네스 레인 변경」 절 확인).
- `milestone-3.md`·`reports/evidence/m3/3a/policy-values.md`·`fixtures/**` — 다른 레인 소유,
  이 slice 가 편집하지 않았으므로 되돌릴 대상도 아니다.
- `reports/evidence/m3/3b2/**`(이 evidence 자체) — 감사 기록은 보존한다(되돌리지도 고치지도 않는다).
- `docs/discovery/capability-map.md`·`docs/discovery/ux-journey-research.md`(scope.md `59e4227`
  in_scope 선언, 문서 레인 소유) — 등재된 `OPEN`은 이 slice 가 만든 지식이라 코드를 걷어도
  남아야 한다(되돌리면 다음 착수가 같은 것을 다시 발견한다).

## 예상 복구 시간

위 두 명령을 순서대로 실행하는 데 실측 수 초(임시 clone 기준, `git` 명령만). 컴파일·test 재확인을
포함하면 Gradle 데몬 기준 분 단위(모듈 둘의 `compileKotlin`+`test`). 실제 저장소에서는 명령 실행
뒤 `git commit`으로 되돌림을 확정하는 절차가 추가된다.

## 검증 방법(운영 시)

1. 위 두 명령(삭제·restore) 실행.
2. `git status --porcelain -- <in_scope 경로>` — 출력이 「원복으로 인한 변경」만 보여야 한다
   (D/M 표시, base 와 diff 가 0 이 아니라 **HEAD 와의** diff 이므로 정상).
3. `git diff <base> -- <in_scope 경로>` 가 빈 출력인지 확인(이 문서의 임시 clone 실측과 같은 절차).
4. `./gradlew :adapters:compileKotlin :procurement:compileKotlin` **exit 0** 확인 — G-3 가 지목한
   자리, exit 0 만으로 성공을 적지 않는다(exit 코드는 컴파일이 도달했다는 뜻일 뿐이다. 아래 5 도
   반드시 실행한다).
5. `./gradlew :procurement:test :adapters:test --tests 'bidvector.adapters.koneps.*'`로 3A·3B
   상태(P-9·D-3B2-5 이전)가 여전히 green 인지 확인.
