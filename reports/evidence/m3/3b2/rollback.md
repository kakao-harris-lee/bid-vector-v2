# M3/3B-2 — rollback.md

base `0e83d6e84a5279352aae1584e3849aa355a991c8`. 경로 한정 되돌림(evidence-pack 2026-09-04 규격) —
range revert 가 아니라 in_scope 경로만 base 상태로 되돌린다. **아래 명령은 2026-09-08 임시 clone
(`git clone --no-hardlinks`)에서 실제로 실행해 exit code 와 D/M 수를 실측했다**(dry-run 아님).

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

실측: exit 0.

## 수정 파일(base 로 restore)

```
git restore --source=0e83d6e84a5279352aae1584e3849aa355a991c8 --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsPageUriBuilder.kt \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsRawItemMapper.kt \
  procurement/src/main/kotlin/bidvector/procurement/FieldContract.kt \
  procurement/src/main/kotlin/bidvector/procurement/RawObservation.kt \
  procurement/src/main/kotlin/bidvector/procurement/CollectionPolicy.kt \
  procurement/src/main/kotlin/bidvector/procurement/DetailFetch.kt \
  procurement/src/main/kotlin/bidvector/procurement/Ports.kt \
  procurement/src/test/kotlin/bidvector/procurement/CollectionPolicyTest.kt \
  procurement/src/test/kotlin/bidvector/procurement/DetailFetchTest.kt \
  config/quality/gate-tests.properties
```

실측: exit 0. `config/quality/gate-tests.properties`는 공유 파일(3B rollback 의 절차와 같은 자리) —
base 자체가 이미 3A·3B 상태를 담고 있어 이 restore 한 번으로 「3B-2 가 넣은 줄만 제거, 3B 가 넣은
줄은 보존」이 정확히 실현된다(3B-2 는 base 위에 줄을 **추가만** 했다).

## 검증(임시 clone 실측, 2026-09-08)

```
git diff --cached 0e83d6e84a5279352aae1584e3849aa355a991c8 -- <위 in_scope 경로 전부>
```

실측: **출력 0 byte** — 위 두 명령 적용 뒤 in_scope 경로가 base 와 바이트 단위로 같다.

```
git diff --cached 0e83d6e84a5279352aae1584e3849aa355a991c8 -- CLAUDE.md .claude/
```

실측: 출력 0 byte — 하네스 경로는 이 되돌림이 건드리지 않는다(애초에 diff 가 없다).

되돌린 상태를 커밋한 뒤 `adapters/src/main/kotlin/bidvector/adapters/koneps/` 디렉터리 파일 수를
셌다 — **10개**로 3B 종결 시점(base)과 같다. 삭제 대상 `PortsTest.kt`는 파일 자체가 없어졌음을
`ls`(exit 1, "No such file")로 확인했다 — 신설 파일이므로 삭제가 정상 결과다.

## 되돌리지 않는 것

- `CLAUDE.md`·`.claude/**`(하네스 레인) — 이 slice 의 range 안에 하네스 커밋이 없다(scope.md
  「하네스 레인 변경」 절 확인).
- `milestone-3.md`·`reports/evidence/m3/3a/policy-values.md`·`fixtures/**` — 다른 레인 소유,
  이 slice 가 편집하지 않았으므로 되돌릴 대상도 아니다.
- `reports/evidence/m3/3b2/**`(이 evidence 자체) — 감사 기록은 보존한다(되돌리지도 고치지도 않는다).

## 예상 복구 시간

위 두 명령을 순서대로 실행하는 데 실측 수 초(임시 clone 기준). 실제 저장소에서는 명령 실행 뒤
`git commit`으로 되돌림을 확정하는 절차가 추가된다.

## 검증 방법(운영 시)

1. 위 두 명령 실행.
2. `git status --porcelain -- <in_scope 경로>` — 출력이 「원복으로 인한 변경」만 보여야 한다
   (D/M 표시, base 와 diff 가 0 이 아니라 **HEAD 와의** diff 이므로 정상).
3. `git diff <base> -- <in_scope 경로>` 가 빈 출력인지 확인(이 문서의 임시 clone 실측과 같은 절차).
4. `./gradlew :procurement:test :adapters:test --tests 'bidvector.adapters.koneps.*'`로 3A·3B
   상태(P-9·D-3B2-5 이전)가 여전히 green 인지 확인.
