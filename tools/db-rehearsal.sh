#!/usr/bin/env bash
# M6/6B-2 D-3 — 백업·복원·되돌림 리허설. 두 축을 **각각** 실측한다.
#
#   (i)  마이그레이션 되돌림 = **forward-fix**. 임시 V18(열 추가)을 적용한 뒤 임시
#        V19(DROP COLUMN)로 되돌리고, 스키마·데이터가 돌아오되 **이력에는 V18·V19 가
#        남는 것**을 단언한다. 남는 것이 forward-fix 의 성질이다 — 되돌림은 이력을 지우는
#        것이 아니라 앞으로 한 발 더 가는 것이다(Flyway community 에 undo 는 없다).
#   (ii) 데이터 손실 = **백업 복원**. 쓰기 왕복이 남긴 값을 복원본에서 지우고(TRUNCATE),
#        없어진 것을 확인한 뒤 같은 백업으로 다시 복원해 값이 돌아옴을 단언한다.
#
# **자기 생성 자원만 만지고 파괴한다**(D-6B2-3). 원본은 인자로 받은 compose 파일의
# 서비스로만 지목하고(전역 이름 조회를 쓰지 않는다) **읽기만 한다** — 원본에 대한 파괴
# 명령이 이 스크립트에 없다. 쓰기와 파괴는 전부 이 실행이 만든 컨테이너 안에서 일어나고,
# 그 컨테이너는 `--network none` 에 호스트 포트 publish 가 없으며 이 실행의 표식을 라벨로
# 단다. 시작 전에 만들 이름이 호스트의 기존 컨테이너 이름과 교차 0 임을 확인한다.
#
# Flyway 호출은 **production 과 같은 조건**으로 한다 — 앱 이미지가 이미 풀어 둔
# `/application/lib` 를 그대로 classpath 로 쓰므로 Flyway·방언·JDBC 드라이버가 출하본과
# 같은 바이트이고, 설정도 `PersistenceWiring.migrate()` 와 같이 전부 기본값이다
# (validateOnMigrate · cleanDisabled · baselineOnMigrate=false). 앱을 기동하지 않는 이유는
# 둘이다: 기동은 비싸고, bootJar 안에 봉인된 `classpath:db/migration` 에는 임시 V18·V19 를
# 넣을 수 없다. Flyway CLI 는 이 저장소에 없고 flyway-core 에는 main 이 없다.
#
# 종료 코드: 2 도구·전제 부재 · 3 사용법·대상 오류 · 1 단언 실패.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MIGRATION_SRC="$REPO_ROOT/adapters/src/main/resources/db/migration"
PROBE_COLUMN="rehearsal_probe"
LABEL_KEY="bidvector.rehearsal"

_die() { echo "db-rehearsal: $1" >&2; exit "${2:-1}"; }
_step() { echo "-- db-rehearsal: $1"; }

[ "$#" -eq 2 ] || {
  echo "usage: tools/db-rehearsal.sh <compose-file> <postgres-service>" >&2
  exit 3
}
COMPOSE_FILE="$1"
SERVICE="$2"

for tool in docker jq javac sha256sum openssl; do
  command -v "$tool" >/dev/null 2>&1 || _die "$tool 이 필요하다" 2
done

WORK="$(mktemp -d)"
RESTORE_CONTAINER=""
RUN_ID=""
_cleanup() {
  if [ -n "$RESTORE_CONTAINER" ]; then
    # 파괴 대상이 **이 실행의 라벨을 단 컨테이너**인지 한 번 더 확인하고 지운다.
    if [ "$(docker inspect -f "{{index .Config.Labels \"$LABEL_KEY\"}}" "$RESTORE_CONTAINER" 2>/dev/null || true)" = "$RUN_ID" ]; then
      docker rm -f -v "$RESTORE_CONTAINER" >/dev/null 2>&1 || true
    fi
  fi
  rm -rf "$WORK"
}
trap _cleanup EXIT

# ---- 0. 사전 단언(D-6B2-3) ---------------------------------------------------------
PROJECT="$(docker compose -f "$COMPOSE_FILE" config --format json | jq -r '.name')"
[ -n "$PROJECT" ] && [ "$PROJECT" != "null" ] \
  || _die "compose 파일이 프로젝트 이름을 들지 않는다 — top-level name: 이 필요하다" 3

SOURCE="$(docker compose -f "$COMPOSE_FILE" ps -q "$SERVICE" 2>/dev/null || true)"
[ -n "$SOURCE" ] || _die "compose 프로젝트 '$PROJECT' 에 동작 중인 '$SERVICE' 가 없다" 3
[ "$(printf '%s\n' "$SOURCE" | wc -l)" -eq 1 ] || _die "'$SERVICE' 가 하나가 아니다" 3

# 원본이 **이 compose 파일에서 뜬 것인지**를 구조로 단언한다. 프로젝트 **이름** 대조로는 닫히지
# 않는다 — 이름은 `COMPOSE_PROJECT_NAME`·`-p` 로 자유롭게 갈리고 D-6B2-5 ② 가 worktree 분리에
# 바로 그 축을 권하므로, 이름이 같다는 사실은 남의 환경을 배제하지 못한다. compose 가 컨테이너에
# 박는 `com.docker.compose.project.config_files` 는 **그것을 만든 파일의 경로**다. 그래서 이름을
# 어떻게 바꿔 띄웠든 자기 파일로 뜬 것만 통과하고, 다른 저장소·다른 compose 파일의 컨테이너는
# 이름이 무엇이든 거부된다(2026-10-05 verifier M-2).
COMPOSE_REAL="$(realpath "$COMPOSE_FILE")" \
  || _die "compose 파일 경로를 풀지 못했다: $COMPOSE_FILE" 3

_origin_matches() {
  local line
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    if [ "$(realpath -m "$line" 2>/dev/null || printf '%s' "$line")" = "$COMPOSE_REAL" ]; then
      return 0
    fi
  done <<EOF
$(printf '%s' "$1" | tr ',' '\n')
EOF
  return 1
}

SOURCE_ORIGIN="$(docker inspect --type container -f '{{json .Config.Labels}}' "$SOURCE" \
  | jq -r --arg k com.docker.compose.project.config_files \
      'if (type == "object") and has($k) then .[$k] else "" end')" \
  || _die "원본 컨테이너의 라벨을 읽지 못했다" 3
[ -n "$SOURCE_ORIGIN" ] \
  || _die "원본 컨테이너에 compose 출처 라벨이 없다 — compose 가 만든 서비스가 아니다" 3
_origin_matches "$SOURCE_ORIGIN" \
  || _die "원본 '$SERVICE' 를 만든 compose 파일이 인자와 다르다 — 남의 환경이다" 3

RUN_ID="6b2-$(date +%Y%m%d%H%M%S)-$$"
RESTORE_CONTAINER="${PROJECT}-rehearsal-${RUN_ID}"
existing="$(docker ps -a --format '{{.Names}}' | wc -l)"
if docker ps -a --format '{{.Names}}' | grep -Fxq "$RESTORE_CONTAINER"; then
  _die "만들려는 이름이 기존 컨테이너와 겹친다: $RESTORE_CONTAINER" 3
fi
_step "사전 단언 — 원본 $PROJECT/$SERVICE(이 compose 파일이 만든 것 · 읽기 전용) · 만들 이름 교차 0(기존 ${existing}개) · 표식 $RUN_ID"

IMAGE="$(docker compose -f "$COMPOSE_FILE" config --format json | jq -r --arg s "$SERVICE" '.services[$s].image')"
APP_IMAGE="$(docker compose -f "$COMPOSE_FILE" config --format json | jq -r '.services.app.image')"
[ -n "$IMAGE" ] && [ "$IMAGE" != "null" ] || _die "'$SERVICE' 의 이미지를 읽지 못했다" 3
docker image inspect "$APP_IMAGE" >/dev/null 2>&1 \
  || _die "앱 이미지 '$APP_IMAGE' 가 없다 — ':app:bootJar' 와 app.Dockerfile 빌드를 먼저 돌린다" 2

SUPERUSER="$(docker exec "$SOURCE" printenv POSTGRES_USER)"
SOURCE_DB="$(docker exec "$SOURCE" printenv POSTGRES_DB)"

_src_psql() {
  docker exec -i "$SOURCE" psql -X -q -t -A -v ON_ERROR_STOP=1 -U "$SUPERUSER" -d "$SOURCE_DB" -c "$1"
}
_tgt_psql() {
  docker exec -i "$RESTORE_CONTAINER" psql -X -q -t -A -v ON_ERROR_STOP=1 -U "$SUPERUSER" -d "$1" -c "$2"
}

# ---- 1. 백업 ----------------------------------------------------------------------
"$SCRIPT_DIR/db-backup.sh" "$SOURCE" "$WORK/backup"

leak_scan="$(grep -rniE -f "$REPO_ROOT/config/quality/leak-patterns.txt" "$WORK/backup" 2>/dev/null || true)"
leak_hits="$(printf '%s\n' "$leak_scan" | grep -c . || true)"
[ "$leak_hits" -eq 0 ] || _die "백업 산출물이 누출 패턴에 걸린다(${leak_hits}건)" 1
_step "백업 산출물 누출 스캔 0건"

# ---- 2. 복원(등식 셋) --------------------------------------------------------------
"$SCRIPT_DIR/db-restore.sh" "$WORK/backup" "$RESTORE_CONTAINER" "$IMAGE" restore_1 "$RUN_ID"

# ---- 3. Flyway 호출 자리 ------------------------------------------------------------
JVM="$WORK/jvm"
mkdir -p "$JVM/classes" "$JVM/migrations/db/migration"
cp "$MIGRATION_SRC"/V*.sql "$JVM/migrations/db/migration/"
cat > "$JVM/RehearsalFlyway.java" <<'JAVA'
import org.flywaydb.core.Flyway;

/** PersistenceWiring.migrate() 와 같은 설정(전부 기본값)으로 Flyway 를 부른다. */
public final class RehearsalFlyway {
    public static void main(String[] args) {
        Flyway flyway = Flyway.configure()
                .dataSource(args[1], args[2], System.getenv("REHEARSAL_PGPASSWORD"))
                .locations("classpath:db/migration")
                .load();
        switch (args[0]) {
            case "validate" -> {
                flyway.validate();
                System.out.println("validate ok");
            }
            case "migrate" -> System.out.println("migrate applied=" + flyway.migrate().migrationsExecuted);
            default -> {
                System.err.println("unknown command " + args[0]);
                System.exit(2);
            }
        }
    }
}
JAVA

flyway_jar="$(docker run --rm --entrypoint sh "$APP_IMAGE" -c 'ls /application/lib/flyway-core-*.jar' | tr -d '\r')" \
  || _die "앱 이미지에서 flyway-core 를 찾지 못했다" 2
[ "$(printf '%s\n' "$flyway_jar" | wc -l)" -eq 1 ] || _die "앱 이미지의 flyway-core 가 하나가 아니다" 2
docker run --rm --entrypoint sh "$APP_IMAGE" -c "cat $flyway_jar" > "$JVM/flyway-core.jar" \
  || _die "flyway-core 를 꺼내지 못했다" 2
javac -nowarn -cp "$JVM/flyway-core.jar" -d "$JVM/classes" "$JVM/RehearsalFlyway.java" \
  || _die "Flyway 호출자를 컴파일하지 못했다" 1
chmod -R a+rX "$JVM"

# 복원 컨테이너의 netns 를 공유해 127.0.0.1 로 붙는다 — 호스트에도, 다른 네트워크에도
# 포트가 열리지 않는다. 공식 이미지의 pg_hba 가 루프백을 trust 로 두므로 자격 값이 없다.
_flyway() {
  docker run --rm --network "container:$RESTORE_CONTAINER" \
    --security-opt no-new-privileges:true -v "$JVM:/jvm:ro" \
    --entrypoint java "$APP_IMAGE" \
    -cp "/application/lib/*:/jvm/classes:/jvm/migrations" \
    RehearsalFlyway "$1" "jdbc:postgresql://127.0.0.1:5432/$2" "$SUPERUSER"
}

_flyway validate restore_1 >/dev/null || _die "복원본에서 Flyway validate 가 실패했다" 1
_step "복원본 Flyway validate 통과 — 이력 $(_tgt_psql restore_1 'select count(*) from flyway_schema_history')행 top $(_tgt_psql restore_1 'select version from flyway_schema_history order by installed_rank desc limit 1')"

# ---- 4. (i) forward-fix 되돌림 ------------------------------------------------------
cat > "$JVM/migrations/db/migration/V18__rehearsal.sql" <<SQL
ALTER TABLE notice ADD COLUMN $PROBE_COLUMN TEXT;
SQL
_flyway migrate restore_1 >/dev/null || _die "임시 V18 적용이 실패했다" 1
[ "$(_tgt_psql restore_1 "select count(*) from information_schema.columns where table_name='notice' and column_name='$PROBE_COLUMN'")" = "1" ] \
  || _die "V18 이 열을 더하지 않았다" 1

cat > "$JVM/migrations/db/migration/V19__rehearsal_forward_fix.sql" <<SQL
ALTER TABLE notice DROP COLUMN $PROBE_COLUMN;
SQL
_flyway migrate restore_1 >/dev/null || _die "forward-fix V19 적용이 실패했다" 1
_flyway validate restore_1 >/dev/null || _die "forward-fix 뒤 validate 가 실패했다" 1

[ "$(_tgt_psql restore_1 "select count(*) from information_schema.columns where table_name='notice' and column_name='$PROBE_COLUMN'")" = "0" ] \
  || _die "forward-fix 뒤에도 열이 남아 있다" 1
applied="$(_tgt_psql restore_1 "select string_agg(version, ',' order by installed_rank) from flyway_schema_history where version in ('18','19')")"
[ "$applied" = "18,19" ] || _die "되돌린 뒤 이력에 V18·V19 가 남아 있지 않다: '$applied'" 1

rm -rf "$WORK/after-fix"
"$SCRIPT_DIR/db-backup.sh" "$RESTORE_CONTAINER" "$WORK/after-fix" restore_1 >/dev/null
"$SCRIPT_DIR/db-restore.sh" --compare "$WORK/backup/manifest.json" "$WORK/after-fix/manifest.json" flywayHistory >/dev/null \
  || _die "forward-fix 뒤 스키마·데이터가 원래대로 돌아오지 않았다" 1
_step "(i) forward-fix — 열 사라짐·이력에 V18·V19 남음(top $(_tgt_psql restore_1 'select version from flyway_schema_history order by installed_rank desc limit 1'))·이력 밖 측정 전부 일치"

# ---- 5. (ii) 데이터 손실 축 ---------------------------------------------------------
Q_STRATEGY="select coalesce(candidate_limit::text, 'null') || '/' || revision::text from operator_strategy where id = 1"
source_strategy="$(_src_psql "$Q_STRATEGY")"
[ -n "$source_strategy" ] \
  || _die "원본 operator_strategy 가 비어 있다 — 데이터 손실 축은 쓰기 왕복(S-23b) 뒤에 돌아야 한다" 1
case "$source_strategy" in
  null/*) _die "원본 candidate_limit 이 비어 있다 — 쓰기 왕복이 선행하지 않았다" 1 ;;
esac
[ "$(_tgt_psql restore_1 "$Q_STRATEGY")" = "$source_strategy" ] \
  || _die "복원본의 전략 값이 원본과 다르다" 1

_tgt_psql restore_1 "truncate operator_strategy, operator_strategy_revision" >/dev/null
[ -z "$(_tgt_psql restore_1 "$Q_STRATEGY")" ] || _die "TRUNCATE 뒤에도 전략 행이 남아 있다" 1
rm -rf "$WORK/after-loss"
"$SCRIPT_DIR/db-backup.sh" "$RESTORE_CONTAINER" "$WORK/after-loss" restore_1 >/dev/null
if "$SCRIPT_DIR/db-restore.sh" --compare "$WORK/backup/manifest.json" "$WORK/after-loss/manifest.json" flywayHistory >/dev/null 2>&1; then
  _die "데이터를 지웠는데 등식이 통과했다 — 판정이 아무것도 재지 않는다" 1
fi
_step "(ii) 음성 대조 — 전략 두 표를 지우자 등식이 붉어진다"

"$SCRIPT_DIR/db-restore.sh" "$WORK/backup" "$RESTORE_CONTAINER" "$IMAGE" restore_2 "$RUN_ID"
[ "$(_tgt_psql restore_2 "$Q_STRATEGY")" = "$source_strategy" ] \
  || _die "다시 복원했는데 전략 값이 돌아오지 않았다" 1
_step "(ii) 양성 — 같은 백업으로 다시 복원하자 candidate_limit/revision 이 돌아왔다"

echo "== 리허설 통과 — 복원 등식 10가지 · forward-fix · 데이터 손실 왕복 =="
