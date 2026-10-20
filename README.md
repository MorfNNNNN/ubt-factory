# Семейный щит — контент-фабрика

> **Приложение «Семейный щит» для RuStore** — в каталоге [`shield/`](shield/README.md):
> Android-приложение (защита пожилых от мошенников + родительский контроль),
> сервер с сквозным шифрованием и документы для публикации.

Бесплатный конвейер образовательного контента о цифровой безопасности семьи
(защита родителей от телефонных мошенников, безопасность детей).
Материалы готовятся по рекомендациям Банка России. Без рекламы и без ссылок
в подписях (режим no-promo по умолчанию).

## Архитектура

```
topics.json (банк 20 авторских тем)
   │  briefs.py — детерминированная генерация сценариев (без ИИ)
   ▼
D1 (Cloudflare) — очередь scripts, квитанции posts
   │  run.py забирает slim-сценарий и обогащает подписями из банка
   ▼
render.py — Piper TTS (голос CC0) + Pillow + FFmpeg → 1080x1920 MP4, слайды, обложка
   ▼
publish.py — Composio (YouTube, Instagram Reels, Facebook, Pinterest, LinkedIn)
             + прямые адаптеры (Telegram, Discord, Bluesky, Mastodon)
```

Планировщик: GitHub Actions, ежедневно 19:00 Asia/Barnaul (`0 12 * * *`).
Форматы по дням ротирует `factory/plan.py` (детерминированно от даты):
Reels / фото / карусель / история в Instagram, видео / фото в Facebook,
обложка / слайд / карусель в Pinterest; иногда комбо Reels + История.
Медиа хранятся в Workers KV воркера `connectorlab-core` (TTL 7 дней) и
раздаются публичными URL для платформ, которым нужен URL (Instagram и др.).

## Запуск (GitHub Actions)

Рендер стартует сам по расписанию (безопасный render-only). Для паузы — переменная
`FACTORY_ENABLED=false`. Для публикации (Settings → Variables и Secrets):
- `WORKER_URL` — URL воркера (по умолчанию уже вшит в код)
- `DEFAULT_PROJECT=ubt-family-shield-ru`
- `RELEASE_ENABLED=true` — разрешает реальную публикацию (иначе только рендер)
- `COMPOSIO_USER_ID` — id пользователя Composio
- секрет `COMPOSIO_API_KEY` — публикация через подключённые аккаунты

Секреты (Settings → Secrets):
- `COMPOSIO_API_KEY` — публикация через подключённые аккаунты
- опционально: `TELEGRAM_BOT_TOKEN`+`TELEGRAM_CHAT_ID`, `DISCORD_WEBHOOK_URL`,
  `BSKY_HANDLE`+`BSKY_APP_PASSWORD`, `MASTODON_TOKEN`

Аутентификация CI → воркер: GitHub OIDC (`id-token: write`), секреты воркера
в репозитории не хранятся.

## Локально

```
python -m pip install -r factory/requirements.txt
python -m unittest discover tests   # 11 тестов, без сети
python factory/briefs.py factory/topics.json /tmp/out  # линт банка тем
python factory/render.py script.json /tmp/out          # рендер одного видео
```

## Правила

- Публикация только при `RELEASE_ENABLED=true` и только по аккаунтам с
  `meta.publish_enabled=true` + `meta.adapter_verified=true` в D1.
- Пауза аккаунта автоматически после 3 сбоев за сутки.
- Неоднозначные результаты не повторяются вслепую — сверка по квитанциям.
- Лицензии: код репозитория MIT; голос Piper CC0; шрифт Montserrat OFL.
