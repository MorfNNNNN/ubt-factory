# Чек-лист запуска

## Состояние кода (на 20.10.2026)

Готово и проверено автоматически:
- общий модуль, сервер, Android-приложение собираются; Android Lint — 0 ошибок;
- release-APK собирается с R8 (≈4,8 МБ);
- тесты: распознавание мошенничества (на корпусе из 55 сообщений — 30/30
  мошеннических найдено, 0 ложных тревог из 25), детские риски, ссылки,
  номера, СМС-коды, правила экранного времени, DNS-кодек, шифрование и
  сопряжение, тарифы и подписи, интеграционные тесты сервера, проверка
  покупок RuStore на мок-сервере, базовые тесты Room;
- серверный jar запускается, `/health` и регистрация отвечают.

**Не проверено** (в среде разработки не было эмулятора и телефонов) — нужно
пройти вручную на реальных устройствах до публикации:
- полный сценарий «взрослый создаёт семью → телефон ребёнка/родителя
  сканирует QR → правила применяются → предупреждения приходят»;
- работа служб на разных прошивках (MIUI/HyperOS, EMUI/HarmonyOS, One UI,
  ColorOS) — особенно автозапуск и ограничения батареи;
- фильтр звонков и наложение на экран звонка; VPN-фильтр с реальными
  браузерами; блокировка приложений и защита настроек;
- покупка через RuStore Pay в песочнице.

## 1. Юридическое
- [ ] ИП или ООО; уведомление в Роскомнадзор как оператор персональных данных.
- [ ] Заполнить реквизиты в `docs/PRIVACY.md` и `docs/TERMS.md` — сайт
      показывает их по адресам https://familyapp1.ru/privacy и /terms
      (после правки — повторный запуск установщика).
- [ ] Указать e-mail поддержки при сборке (`shield.supportEmail`).

## 2. Сервер
- [x] Домен `familyapp1.ru` и VPS FirstVDS (188.120.246.123).
- [ ] DNS (панель FirstVDS): записи A `familyapp1.ru` и `www.familyapp1.ru` →
      188.120.246.123.
- [ ] На сервере от root:
      `curl -fsSL https://raw.githubusercontent.com/MorfNNNNN/ubt-factory/claude/cool-gates-wyu9vs/shield/deploy/install.sh | bash -s -- familyapp1.ru`
- [ ] Проверить `https://familyapp1.ru/health`, `/privacy`, `/terms`.
- [ ] Публичный ключ из вывода установщика (или
      `https://familyapp1.ru/v1/billing/public-key`) — в сборку приложения
      (`shield.entitlementPublicKey`). Ключ на сервере после этого **не
      менять**, иначе установленные приложения перестанут принимать тариф.
- [ ] Скопировать `/opt/family-shield/shield/deploy/.env` в надёжное место
      (пароль БД и приватный ключ подписи тарифов).
- [ ] Сменить пароль root (`passwd`), лучше перейти на вход по SSH-ключу.
- [ ] RuStore Консоль → API RuStore → создать ключ (доступ к платежам и
      подпискам) → `RUSTORE_KEY_ID`, `RUSTORE_PRIVATE_KEY` в `.env`.
- [ ] RuStore Консоль → Push-уведомления → проект →
      `RUSTORE_PUSH_PROJECT_ID`, `RUSTORE_PUSH_SERVICE_TOKEN` в `.env`;
      project ID — также в сборку (`shield.rustorePushProjectId`).
- [ ] После правки `.env`: `cd /opt/family-shield/shield/deploy && docker compose up -d`.
- [ ] По желанию: `THREAT_FEEDS` — списки доменов для категорий фильтра
      (проверьте лицензию каждого списка).
- [ ] Резервные копии базы делаются ежедневно в `/var/backups/family-shield` —
      периодически копируйте их за пределы сервера.

## 3. Консоль RuStore
- [ ] Создать приложение с пакетом `ru.ubt.familyshield`, получить
      console app ID → `shield.rustoreConsoleAppId`.
- [ ] Создать товары и подписки из `docs/MONETIZATION.md`.
- [ ] Заполнить карточку по `docs/RUSTORE_LISTING.md`, загрузить скриншоты и
      видео для модерации.

## 4. Подпись и сборка релиза
- [ ] Создать ключ подписи (`keytool -genkeypair -v -keystore shield.jks
      -alias shield -keyalg RSA -keysize 4096 -validity 10000`), хранить
      **резервную копию** — без него нельзя выпускать обновления.
- [ ] `shield/keystore.properties`:
      ```
      shield.storeFile=/путь/shield.jks
      shield.storePassword=…
      shield.keyAlias=shield
      shield.keyPassword=…
      shield.serverUrl=https://familyapp1.ru
      shield.entitlementPublicKey=…
      shield.rustoreConsoleAppId=…
      shield.rustorePushProjectId=…
      shield.supportEmail=…
      ```
- [ ] `./gradlew :app:assembleRelease` (или `bundleRelease` для AAB).

## 5. Тестирование перед публикацией
- [ ] 3–5 телефонов разных производителей, Android 8–15.
- [ ] Сценарии из раздела «Не проверено» выше.
- [ ] Покупки в песочнице RuStore: подписка, разовая покупка, восстановление,
      истечение подписки (функции должны закрыться).
- [ ] Удаление приложения и повторная установка: восстановление покупок,
      повторное подключение к семье.

## 6. После запуска
- [ ] Следить за отзывами и падениями (RuStore Консоль).
- [ ] Регулярно обновлять словари мошеннических схем (`ScamLexicon`) — схемы
      меняются; корпус `shared/src/test/resources/scam_corpus.tsv` защищает от
      регрессий.
- [ ] Модерация жалоб на номера: порог публикации — 3 жалобы с разных
      устройств (`numberReportThreshold`).
