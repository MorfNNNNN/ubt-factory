# «Семейный щит» 2.1.0: домен, сервер и публикация в RuStore

Инструкция для Claude, который управляет Chrome владельца через расширение Claude in Chrome (обычный
чат claude.ai или Claude Desktop). Браузер уже залогинен в личный кабинет FirstVDS и в RuStore Console.

Эта страница: https://github.com/MorfNNNNN/ubt-factory/blob/claude/cool-gates-wyu9vs/shield/docs/PUBLISH_HANDOFF.md

Пароли и ключи только вводятся в браузере: не записывать их в файлы, не коммитить, не пересказывать в
чате. Перед любыми платными действиями в FirstVDS и перед отправкой на модерацию — спросить владельца.

## Что нужно под рукой
- Сборка: `family-shield-2.1.0.apk` (versionCode 3) и `family-shield-2.1.0.aab` — владелец скачал их
  из чата Claude Code, обычно лежат в «Загрузках». В git сборок нет. Подпись: CN=Family Shield, SHA-256
  `807fcffbb8177fac8e1e27267f18af2d01c4ab73e31433ad6b7f2cd8ec36eb95` — тот же ключ, что у 2.0.0.
- Тексты карточки: https://github.com/MorfNNNNN/ubt-factory/blob/claude/cool-gates-wyu9vs/shield/docs/RUSTORE_LISTING.md
- Графика (скачать по ссылкам, если её ещё нет на компьютере):
  - иконка: https://raw.githubusercontent.com/MorfNNNNN/ubt-factory/claude/cool-gates-wyu9vs/shield/store/icon-512.png
  - скриншоты: `https://raw.githubusercontent.com/MorfNNNNN/ubt-factory/claude/cool-gates-wyu9vs/shield/store/screenshots/rustore-01.jpg`
    и так же `rustore-02.jpg` … `rustore-10.jpg`.
- Сервер, подробности и типовые проблемы: https://github.com/MorfNNNNN/ubt-factory/blob/claude/cool-gates-wyu9vs/shield/docs/SERVER_HANDOFF.md
- Выбор файла в окне «Открыть» расширение сделать не может: когда консоль просит файл (APK, иконку,
  скриншоты), попросить владельца выбрать его и продолжить после этого.

## Шаг 1. Домен familyapp1.ru — выполнен
28.10.2026 09:25 UTC: зона создана, `familyapp1.ru` и `www.familyapp1.ru` указывают на `188.120.246.123`
(проверено через GitHub, Google DNS и Cloudflare DNS). Ниже — как это делается, если зону придётся
создавать заново.
1. Личный кабинет FirstVDS → DNSmanager (Товары → Виртуальные серверы → «Инструкция» → DNSmanager).
2. «Доменные имена» → «Создать»: домен `familyapp1.ru`, тип master, IP `188.120.246.123`.
3. Проверить A-записи: `familyapp1.ru.` и `www.familyapp1.ru.` → `188.120.246.123`.
4. Через 5–30 минут открыть `https://dns.google/resolve?name=familyapp1.ru&type=A` — в ответе должен
   быть `188.120.246.123`.

## Шаг 2. Сервер (VPS 188.120.246.123)
SSH из-за рубежа не работает: 28.10.2026 с серверов GitHub соединение на порт 22 открывается, но
приветствие SSH не приходит (порты 80 и 443 при этом отвечают сразу). Из России SSH работает, поэтому
проще всего — с компьютера владельца: в Windows открыть PowerShell, в macOS — «Терминал», выполнить
`ssh root@188.120.246.123`, ввести пароль и дальше команду из п. 1. Запасной вариант — веб-консоль VPS
в панели FirstVDS (VMmanager → сервер → «Консоль»/VNC) под root.
1. Выполнить установщик:
   ```
   curl -fsSL https://raw.githubusercontent.com/MorfNNNNN/ubt-factory/claude/cool-gates-wyu9vs/shield/deploy/install.sh | bash -s -- familyapp1.ru
   ```
   Если в веб-консоль неудобно вставлять длинный текст, то же самое короче:
   `curl -fsSL https://clck.ru/3WcBfb | bash -s familyapp1.ru`
2. Дождаться конца (5–15 минут): установщик ставит Docker, скачивает готовый сервер, запускает базу,
   сервер и Caddy с HTTPS и включает автообновление с GitHub каждые 10 минут.
3. Проверка в браузере после шага 1:
   - `https://familyapp1.ru/health` → `{"status":"ok",…}`
   - `https://familyapp1.ru/privacy` и `https://familyapp1.ru/terms` открываются.
   Если HTTPS не поднялся — в консоли сервера: `cd /opt/family-shield/shield/deploy && docker compose logs --tail 50 caddy`.

## Шаг 3. RuStore Console (console.rustore.ru)
Перед началом: аккаунт разработчика подтверждён; для платных функций в консоли подключена монетизация.
1. Открыть приложение «Семейный щит» (пакет `ru.ubt.familyshield`) или создать новое.
2. Загрузить сборку 2.1.0. Предпочтительно APK: AAB загружать, только если консоль не требует передать
   ей ключ подписи.
3. Карточка — тексты из `RUSTORE_LISTING.md`: название, краткое и полное описание, «Что нового в 2.1.0»,
   ключевые слова (если есть поле). Категория «Инструменты» (или «Родителям»), возраст 0+, «бесплатно с
   покупками».
4. Графика: иконка `icon-512.png`, скриншоты `rustore-01…10.jpg` строго по порядку.
5. Ссылки: политика `https://familyapp1.ru/privacy`, соглашение `https://familyapp1.ru/terms`, сайт
   `https://familyapp1.ru`. Они должны открываться (шаги 1–2), иначе модерация отклонит приложение.
6. FAQ — 10 вопросов из `RUSTORE_LISTING.md`, если в консоли есть такой блок.
7. Подписки и покупки — ID должны совпасть точно, иначе оплата в приложении не заработает:

   | ID | Тип | Цена |
   |---|---|---|
   | `shield_premium_month` | подписка, 1 месяц | 349 ₽ |
   | `shield_premium_year` | подписка, 12 месяцев, пробный период 7 дней | 2 490 ₽ |
   | `shield_family_max_month` | подписка, 1 месяц | 549 ₽ |
   | `shield_family_max_year` | подписка, 12 месяцев | 3 990 ₽ |
   | `shield_premium_lifetime` | постоянная покупка | 6 990 ₽ |
   | `shield_pack_elder` | постоянная покупка | 1 490 ₽ |
   | `shield_pack_kids` | постоянная покупка | 1 490 ₽ |

8. Комментарий для модерации — раздел «Обоснование чувствительных разрешений» из `RUSTORE_LISTING.md`.
9. Остановиться перед «Отправить на модерацию»: показать владельцу заполненную карточку и отправить
   только после его подтверждения.

## Шаг 4. После публикации
- Новые ключи RuStore для сервера (проверка покупок и пуш-уведомления) — в
  `/opt/family-shield/shield/deploy/.env` на сервере: `RUSTORE_KEY_ID`, `RUSTORE_PRIVATE_KEY`,
  `RUSTORE_PUSH_PROJECT_ID`, `RUSTORE_PUSH_SERVICE_TOKEN`, затем `docker compose up -d` в той же папке.
- Отозвать ключ API RuStore, который пересылался в чате, и сменить пароли VPS и кабинета FirstVDS.

## Текст для запуска (вставить в чат Claude с включённым Claude in Chrome)
> Открой в моём Chrome инструкцию
> https://github.com/MorfNNNNN/ubt-factory/blob/claude/cool-gates-wyu9vs/shield/docs/PUBLISH_HANDOFF.md
> и выполни шаги 1–3: домен и сервер в личном кабинете FirstVDS, затем публикация в RuStore Console.
> Пароли нигде не сохраняй. Когда нужен файл с компьютера — скажи, я выберу его сам.
> Перед отправкой на модерацию покажи мне заполненную карточку и жди моего подтверждения.
