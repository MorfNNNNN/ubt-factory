#!/usr/bin/env bash
# Family Shield server installer for a fresh Ubuntu / Debian VPS. Run as root:
#
#   curl -fsSL https://raw.githubusercontent.com/MorfNNNNN/ubt-factory/claude/cool-gates-wyu9vs/shield/deploy/install.sh | bash -s -- familyapp1.ru
#
# Installs Docker, fetches the code to /opt/family-shield, creates deploy/.env with
# fresh secrets (kept on re-runs), starts PostgreSQL + server + Caddy (automatic HTTPS),
# sets up daily database backups and prints the public key for the app build.
# Re-run the same command to update.
set -euo pipefail

DOMAIN="${1:-${SHIELD_DOMAIN:-}}"
REPO="${SHIELD_REPO:-https://github.com/MorfNNNNN/ubt-factory.git}"
BRANCH="${SHIELD_BRANCH:-claude/cool-gates-wyu9vs}"
JAR_URL="${SHIELD_JAR_URL:-https://github.com/MorfNNNNN/ubt-factory/releases/download/server-latest/family-shield-server.jar}"
DIR="${SHIELD_DIR:-/opt/family-shield}"

say() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m!! %s\033[0m\n' "$*" >&2; }
die() { printf '\n\033[1;31mОшибка: %s\033[0m\n' "$*" >&2; exit 1; }
get_env() { sed -n "s|^$1=||p" .env | tail -n 1; }
set_env() {
  if grep -q "^$1=" .env; then sed -i "s|^$1=.*|$1=$2|" .env; else echo "$1=$2" >>.env; fi
}

# Everything runs inside main() so that bash parses the whole script before executing it:
# with `curl ... | bash` a command reading stdin would otherwise swallow the rest of the script.
main() {
  [ "$(id -u)" -eq 0 ] || die "запустите от root (или через sudo)"
  DOMAIN="${DOMAIN#https://}"; DOMAIN="${DOMAIN#http://}"; DOMAIN="${DOMAIN%%/*}"
  [ -n "$DOMAIN" ] || die "укажите домен, например: bash install.sh familyapp1.ru"

  . /etc/os-release
  case " ${ID:-} ${ID_LIKE:-} " in
    *" ubuntu "* | *" debian "*) ;;
    *) die "нужна Ubuntu или Debian, а здесь ${PRETTY_NAME:-неизвестная система}" ;;
  esac

  say "Системные пакеты"
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  apt-get install -y -qq ca-certificates curl git openssl iproute2 cron >/dev/null

  # Small VPS plans: the JVM (and the fallback Gradle build) need headroom.
  mem_mb=$(awk '/MemTotal/ {print int($2 / 1024)}' /proc/meminfo)
  if [ "$mem_mb" -lt 3000 ] && [ -z "$(swapon --show --noheadings)" ]; then
    say "Файл подкачки 2 ГБ (оперативной памяти ${mem_mb} МБ)"
    if { fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none; } &&
      chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile; then
      grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >>/etc/fstab
    else
      rm -f /swapfile
      warn "не удалось включить подкачку — продолжаю без неё"
    fi
  fi

  if ! docker compose version >/dev/null 2>&1; then
    say "Docker"
    if ! curl -fsSL https://get.docker.com | sh; then
      warn "get.docker.com недоступен — ставлю Docker из репозитория системы"
      apt-get install -y -qq docker.io >/dev/null
      apt-get install -y -qq docker-compose-v2 >/dev/null 2>&1 ||
        apt-get install -y -qq docker-compose-plugin >/dev/null 2>&1 || true
    fi
  fi
  # Docker Hub can be slow or unavailable from some networks: try Google's public mirror first.
  if [ ! -s /etc/docker/daemon.json ]; then
    mkdir -p /etc/docker
    echo '{ "registry-mirrors": ["https://mirror.gcr.io"] }' >/etc/docker/daemon.json
    systemctl restart docker 2>/dev/null || true
  fi
  systemctl enable --now docker >/dev/null 2>&1 || true
  docker compose version >/dev/null 2>&1 || die "не удалось установить Docker Compose"

  busy=$(ss -ltnpH '( sport = :80 or sport = :443 )' 2>/dev/null | grep -v docker-proxy || true)
  if [ -n "$busy" ]; then
    echo "$busy" >&2
    die "порты 80 и 443 заняты другой программой (обычно веб-сервер панели: nginx или apache2).
  Остановите её, например: systemctl disable --now nginx apache2 — и запустите установку снова."
  fi

  if command -v ufw >/dev/null && ufw status 2>/dev/null | grep -q "Status: active"; then
    ufw allow 80/tcp >/dev/null && ufw allow 443/tcp >/dev/null
  fi
  if command -v firewall-cmd >/dev/null && firewall-cmd --state >/dev/null 2>&1; then
    firewall-cmd -q --permanent --add-service=http --add-service=https && firewall-cmd -q --reload
  fi

  say "Код ($BRANCH)"
  if [ -d "$DIR/.git" ]; then
    git -C "$DIR" fetch -q --depth 1 origin "$BRANCH"
    git -C "$DIR" reset -q --hard FETCH_HEAD
  else
    git clone -q --depth 1 -b "$BRANCH" "$REPO" "$DIR"
  fi
  cd "$DIR/shield/deploy"

  say "Серверная программа"
  mkdir -p prebuilt
  if curl -fsSL --retry 3 -o prebuilt/family-shield-server.jar.part "$JAR_URL"; then
    mv prebuilt/family-shield-server.jar.part prebuilt/family-shield-server.jar
    dockerfile=server/Dockerfile.prebuilt
  else
    rm -f prebuilt/family-shield-server.jar.part
    warn "готовая сборка недоступна — собираю сервер из исходников (10–20 минут)"
    dockerfile=server/Dockerfile
  fi

  if [ ! -f .env ]; then
    (umask 077 && cp .env.example .env)
    set_env POSTGRES_PASSWORD "$(openssl rand -hex 24)"
  fi
  chmod 600 .env
  set_env SHIELD_DOMAIN "$DOMAIN"
  set_env SERVER_DOCKERFILE "$dockerfile"

  docker compose build server
  if [ -z "$(get_env ENTITLEMENT_PRIVATE_KEY)" ]; then
    say "Ключ подписи тарифов"
    keys=$(docker compose run --rm --no-deps -T server keygen </dev/null)
    set_env ENTITLEMENT_PRIVATE_KEY "$(echo "$keys" | sed -n 's|^ENTITLEMENT_PRIVATE_KEY=||p')"
    set_env ENTITLEMENT_PUBLIC_KEY "$(echo "$keys" | sed -n 's|^ENTITLEMENT_PUBLIC_KEY=||p')"
    [ -n "$(get_env ENTITLEMENT_PRIVATE_KEY)" ] || die "не удалось создать ключ подписи"
  fi

  say "Запуск"
  docker compose up -d --remove-orphans

  cat >/etc/cron.daily/family-shield-backup <<EOF
#!/bin/sh
# Daily PostgreSQL dump, 14 days kept. Copy /var/backups/family-shield off the server too.
set -e
umask 077
mkdir -p /var/backups/family-shield
cd "$DIR/shield/deploy"
docker compose exec -T db pg_dump -U shield shield </dev/null | gzip >/var/backups/family-shield/shield-\$(date +%F).sql.gz
find /var/backups/family-shield -name 'shield-*.sql.gz' -mtime +14 -delete
EOF
  chmod 755 /etc/cron.daily/family-shield-backup

  # Automatic updates: the server pulls new versions from GitHub (see update.sh).
  cat >/etc/family-shield.conf <<EOF
DIR='$DIR'
BRANCH='$BRANCH'
DOMAIN='$DOMAIN'
JAR_URL='$JAR_URL'
EOF
  install -m 755 "$DIR/shield/deploy/update.sh" /usr/local/sbin/family-shield-update
  echo '*/10 * * * * root /usr/local/sbin/family-shield-update >>/var/log/family-shield-update.log 2>&1' \
    >/etc/cron.d/family-shield-update
  /usr/local/sbin/family-shield-update --record || warn "не удалось запомнить версию для автообновления"

  server_ip=$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i < NF; i++) if ($i == "src") print $(i + 1)}' || true)
  dns_ip=$(getent ahostsv4 "$DOMAIN" | awk 'NR == 1 {print $1}' || true)
  if [ -z "$dns_ip" ]; then
    warn "домен $DOMAIN пока не указывает ни на какой сервер. Добавьте в DNS записи A для $DOMAIN и www.$DOMAIN → ${server_ip:-IP этого сервера}.
     HTTPS-сертификат будет получен автоматически, как только записи начнут работать (обычно 15 минут – несколько часов)."
  elif [ -n "$server_ip" ] && [ "$dns_ip" != "$server_ip" ]; then
    warn "домен $DOMAIN указывает на $dns_ip, а у этого сервера адрес $server_ip — проверьте запись A в DNS."
  fi

  say "Проверка"
  ok=""
  for _ in $(seq 1 36); do
    if curl -fsS --max-time 5 --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/health" >/dev/null 2>&1; then
      ok=1
      break
    fi
    sleep 5
  done
  if [ -n "$ok" ]; then
    echo "https://$DOMAIN работает, HTTPS-сертификат получен."
  elif docker compose exec -T server wget -qO- http://127.0.0.1:8080/health </dev/null >/dev/null 2>&1; then
    warn "сервер работает, но HTTPS-сертификата пока нет (ждёт DNS). Журнал: cd $DIR/shield/deploy && docker compose logs caddy"
  else
    warn "сервер пока не отвечает. Журнал: cd $DIR/shield/deploy && docker compose logs server"
  fi

  cat <<EOF

Готово.
  Сайт:      https://$DOMAIN  (политика: https://$DOMAIN/privacy, соглашение: https://$DOMAIN/terms)
  Сервер:    https://$DOMAIN/health
  Ключ для сборки приложения (shield.entitlementPublicKey):
    $(get_env ENTITLEMENT_PUBLIC_KEY)

Важно:
  • Сохраните копию $DIR/shield/deploy/.env в надёжном месте — там пароль базы и ключ подписи тарифов.
  • Резервные копии базы: /var/backups/family-shield (каждый день, 14 дней).
  • Обновления ставятся сами: сервер каждые 10 минут проверяет GitHub (журнал /var/log/family-shield-update.log).
  • Если пароль root куда-либо пересылался, смените его: passwd
EOF
}

main "$@"
