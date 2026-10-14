#!/usr/bin/env bash
set -euo pipefail

SSH_HOST="${SSH_HOST:-nedbaevx}"
REMOTE_DIR="${REMOTE_DIR:-maxbridge}"
PLATFORM="${PLATFORM:-linux/amd64}"
IMAGE="maxbridge:latest"

cd "$(dirname "$0")"

REMOTE_COMPOSE='if docker compose version >/dev/null 2>&1; then docker compose "$@"; else docker-compose "$@"; fi'
remote_compose() {
  ssh "$SSH_HOST" "cd '$REMOTE_DIR' && export APP_UID=\$(id -u) APP_GID=\$(id -g) && set -- $* && $REMOTE_COMPOSE"
}

case "${1:-deploy}" in
  logs) remote_compose logs -f --tail=100; exit 0 ;;
  stop) remote_compose down; exit 0 ;;
  deploy) ;;
  *) echo "Использование: $0 [deploy|logs|stop]" >&2; exit 1 ;;
esac

echo "==> Сборка образа $IMAGE ($PLATFORM)"
docker build --platform "$PLATFORM" -t "$IMAGE" .

echo "==> Подготовка $SSH_HOST:$REMOTE_DIR"
ssh "$SSH_HOST" "mkdir -p '$REMOTE_DIR/data'"
scp -q docker-compose.yml "$SSH_HOST:$REMOTE_DIR/docker-compose.yml"

if ssh "$SSH_HOST" "test -f '$REMOTE_DIR/data/config.xml'"; then
  echo "==> config.xml на сервере уже есть, не трогаю"
elif [ -f config.xml ]; then
  echo "==> Копирую config.xml на сервер"
  scp -q config.xml "$SSH_HOST:$REMOTE_DIR/data/config.xml"
  ssh "$SSH_HOST" "chmod 600 '$REMOTE_DIR/data/config.xml'"
else
  echo "!! Нет config.xml ни локально, ни на сервере ($REMOTE_DIR/data/config.xml) — бот не запустится" >&2
  exit 1
fi

echo "==> Отправка образа на сервер"
docker save "$IMAGE" | gzip | ssh "$SSH_HOST" 'gunzip | docker load'

echo "==> Запуск"
remote_compose up -d --no-build --force-recreate
remote_compose ps

echo "==> Готово. Логи: ./deploy.sh logs"
