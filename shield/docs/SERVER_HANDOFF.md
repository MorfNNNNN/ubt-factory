# Задача: развернуть сервер «Семейного щита» на VPS и подключить домен

## Что это
«Семейный щит» — Android-приложение для RuStore (защита пожилых от мошенников и
родительский контроль). Ему нужен сервер: Ktor + PostgreSQL за Caddy (HTTPS).
Код: https://github.com/MorfNNNNN/ubt-factory, ветка `claude/cool-gates-wyu9vs`,
папка `shield/` (`shield/server` — сервер, `shield/deploy` — развёртывание).

Приложение уже собрано и обращается к **https://familyapp1.ru**: API — `/v1/…`
и `/health`, сайт с политикой и соглашением — `/privacy` и `/terms` на том же
домене.

## Доступы
- VPS FirstVDS: IP `188.120.246.123`, пользователь `root` (или `admin` с sudo),
  пароль: `<вставьте пароль>`.
- Домен `familyapp1.ru`, DNS-серверы `ns1.firstvds.ru` и `ns2.firstvds.ru`
  (записи правятся в DNSmanager FirstVDS: личный кабинет → «Товары» →
  «Виртуальные серверы» → «Инструкция» → DNSmanager).

## Что сделать
1. На сервере от root (Ubuntu/Debian) выполнить установщик:
   ```
   curl -fsSL https://raw.githubusercontent.com/MorfNNNNN/ubt-factory/claude/cool-gates-wyu9vs/shield/deploy/install.sh | bash -s -- familyapp1.ru
   ```
   Короткая ссылка на тот же файл: `curl -fsSL https://clck.ru/3WcBfb | bash -s familyapp1.ru`.
   Под `admin` — с `sudo bash`.

   Установщик: ставит Docker (зеркало `mirror.gcr.io` для Docker Hub), при
   нехватке памяти включает swap 2 ГБ, открывает 80/443 в ufw/firewalld,
   клонирует репозиторий в `/opt/family-shield`, скачивает готовый jar сервера
   из релиза GitHub `server-latest` (если недоступен — собирает из исходников),
   создаёт `/opt/family-shield/shield/deploy/.env` с паролем БД и ключом подписи
   тарифов, запускает `docker compose` (db, server, caddy), включает ежедневные
   копии базы в `/var/backups/family-shield` и автообновление с GitHub каждые
   10 минут (`/usr/local/sbin/family-shield-update`, журнал
   `/var/log/family-shield-update.log`). Повторный запуск безопасен: ключи и
   пароли сохраняются.

2. DNS (если ещё не сделано): в DNSmanager создать домен `familyapp1.ru`
   (тип master, IP `188.120.246.123`) и проверить записи A: `familyapp1.ru.` и
   `www.familyapp1.ru.` → `188.120.246.123`. HTTPS-сертификат Caddy получит сам,
   когда записи начнут работать.

3. Ключи RuStore в `/opt/family-shield/shield/deploy/.env` (из консоли RuStore):
   - `RUSTORE_KEY_ID`, `RUSTORE_PRIVATE_KEY` — «API RuStore» → ключ с доступом
     к платежам и подпискам (нужен для проверки покупок);
   - `RUSTORE_PUSH_PROJECT_ID`, `RUSTORE_PUSH_SERVICE_TOKEN` — «Push-уведомления»
     → проект.

   Затем: `cd /opt/family-shield/shield/deploy && docker compose up -d`.

## Проверка
```
cd /opt/family-shield/shield/deploy
docker compose ps                                  # db, server, caddy — Up
docker compose exec -T server wget -qO- http://127.0.0.1:8080/health
docker compose logs --tail 50 caddy                # выпуск сертификата
curl -s https://familyapp1.ru/health               # {"status":"ok",...}
curl -s https://familyapp1.ru/v1/billing/public-key
curl -sI https://familyapp1.ru/privacy             # 200
```
Готово, когда `https://familyapp1.ru/health` отвечает по HTTPS, открываются
`/privacy` и `/terms`, а приложение на телефоне создаёт семью.

## Что известно
- SSH на `188.120.246.123:22` проходит не из всех сетей: TCP-соединение
  открывается, но SSH-приветствие доходит только по части маршрутов. 28.10.2026
  проходил из Timeweb, Yandex Cloud, Hetzner, SERV.HOST, Datacheap; не проходил
  из МТС, Билайна, Selectel, Cloud.ru, GitHub Actions и облака Claude Code.
  FirstVDS с июня 2026 пишет о фильтрации SSH и HTTPS на стороне операторов и
  советует сменить сеть, например на мобильный интернет.
- После взлома VMmanager 17.10.2026 FirstVDS закрыл клиентам доступ к VMmanager,
  поэтому веб-консоли (VNC) там нет — только через поддержку. FirstVDS просит
  сменить пароли root на всех серверах.
- При обрывах SSH установщик лучше запускать в фоне:
  `curl -fsSL https://clck.ru/3WcBfb -o install.sh; nohup bash install.sh familyapp1.ru > install.log 2>&1 & tail -F install.log`.
- Если порты 80/443 заняты (например, nginx/apache панели ispmanager),
  установщик остановится с подсказкой.
- Ключ подписи тарифов (`ENTITLEMENT_*` в `.env`) после запуска не менять:
  приложение запоминает публичный ключ сервера при первом подключении.

## После
- Сменить пароль root/admin и пароль личного кабинета FirstVDS; лучше вход по
  SSH-ключу.
- Скопировать `/opt/family-shield/shield/deploy/.env` в надёжное место.
- Подробнее: `shield/README.md`, `shield/docs/LAUNCH_CHECKLIST.md`.
