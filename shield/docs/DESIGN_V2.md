# «Семейный щит» v2 — дизайн-система

Цель: интерфейс уровня топовых банковских и семейных приложений РФ. Тёплый, взрослый,
понятный пожилым, без «шаблонного Material».

## Жёсткие правила
1. **Никаких эмодзи** — ни в UI-строках, ни в уведомлениях, ни в именах по умолчанию.
   Вместо них: векторные глифы Material Icons Rounded на градиентной плитке (`IconTile`),
   3D-иллюстрации (`Illustration`), 3D-аватары (`AvatarImage`), Lottie (`LottieView`).
   Типографские знаки (₽, «», —, №) — можно.
2. Только компоненты дизайн-системы (`ui/components/Visuals.kt`, `Avatars.kt`, `Components.kt`)
   и токены темы (`ui/theme/Theme.kt`: `Brand`, `Gradients`, `Manrope`, `MaterialTheme.typography`).
   Не задавать цвета «на месте», кроме прозрачности белого на градиентах.
3. Поведение и логика экранов не меняются при редизайне — только представление.
4. Тексты — живой русский, коротко, без канцелярита. Кнопки — глаголом («Добавить телефон»).
5. **Никаких сгенерированных ИИ изображений людей.** Люди — только векторные портреты `AvatarArt`
   (стиль Avataaars), предметы — 3D-иконки 3dicons (`Art3D`, CC0). Каждую новую картинку проверять
   крупно на светлом и тёмном фоне до того, как она попадёт в приложение.

## Токены
- Шрифт: Manrope (400–800), задан во всей типографике. Заголовки экранов — `headlineLarge`/`headlineMedium`
  (ExtraBold), карточки — `titleMedium`/`titleLarge`, текст — `bodyMedium`.
- Фон экранов: `Brand.Canvas` (#F3F7F5, задан как `background`). Карточки белые с мягкой зелёной тенью.
- Радиусы: карточки `shapes.large` (26), крупные герои `shapes.extraLarge` (34), кнопки `shapes.medium` (18).
- Градиенты (`Gradients.*`): Emerald — основное/безопасно; Hero — шапки; Ocean — геолокация;
  Sky — дети/время; Violet — веб/приложения/пожилые; Coral — звонки/сообщения; Danger — тревога/SOS;
  Sunrise — внимание/батарея/лекарства; Gold — премиум/награды; Night — сон; Slate — служебное.

## Компоненты
| Компонент | Назначение |
|---|---|
| `HeroCard(title, subtitle, gradient, art, footer)` | Шапка главных экранов: градиент + иллюстрация/Lottie справа |
| `SoftCard {}` / `SectionCard` | Белая карточка с тенью |
| `IconTile(icon, gradient, size)` | Иконка на градиентной плитке (замена эмодзи) |
| `TileRow(title, subtitle, icon, gradient, trailing, onClick)` | Строка меню/настроек |
| `StatTile(value, label, icon, gradient)` | Метрика: число + подпись |
| `SectionHeader(title, action)` | Заголовок секции с действием справа |
| `AvatarImage(value, role, size, ring)` / `AvatarStack` / `AvatarPicker` | 3D-портреты семьи |
| `Illustration(R.drawable.ill_*)` | 3D-иллюстрации |
| `LottieView(R.raw.lottie_*)` | Анимации |
| `PrimaryButton` (градиент) / `SecondaryButton` | Кнопки |
| `StatusDot(text, color)` | Статус «онлайн», «защищён» |
| `AlertType.visual()`, `MemberRole.visual()`, `FeatureIcons.*` | Готовые пары иконка+градиент |

### Иллюстрации (`res/drawable-nodpi`)
`ill_mark_shield` (знак), портреты `avatar_*` (13 шт., Avataaars, см. `AvatarArt`),
3D-объекты `ic3d_*` (67 шт., 3dicons, см. `Art3D`; фон вырезан, края проверены на тёмном фоне).
Лицензии — `assets/licenses/`.

### Lottie (`res/raw`)
`lottie_shield_active` (щит активен, волны) · `lottie_sos_pulse` (SOS) · `lottie_scanning` (радар-проверка) ·
`lottie_success_check` (успех, 1 раз) · `lottie_alert_siren` (тревога) · `lottie_heart_beat` («Я в порядке») ·
`lottie_location_ping` (геолокация) · `lottie_lock_secure` (приватность, 1 раз) · `lottie_confetti` (праздник, 1 раз) ·
`lottie_devices_connect` (подключение телефонов) · `lottie_loading_dots` (загрузка).

## Направление по экранам
- **Главная родителя**: `HeroCard` «Вся семья под защитой» (Lottie `shield_active`) или тревожный (Danger,
  `alert_siren`) если есть срочные события; ряд `StatTile` (заблокировано звонков, экранное время ребёнка,
  «я в порядке»); карточки членов семьи с крупным `AvatarImage`, статусом (`StatusDot`), батареей, местом.
- **События**: карточки с `IconTile` по `AlertType.visual()`, бейдж важности, «почему мы так советуем».
- **Пожилой (простой режим)**: огромные плитки-кнопки с градиентом и глифом (не эмодзи), SOS — Danger
  с `lottie_sos_pulse`, «Я в порядке» — Coral с `lottie_heart_beat`.
- **Ребёнок**: `HeroCard` Sky с остатком времени (кольцевой прогресс), задания и награды, SOS.
- **Онбординг**: слайды с иллюстрациями/Lottie на мятном фоне, крупный заголовок, точки-индикатор.
- **Пейвол**: `HeroCard` Gold/Hero со знаком, список преимуществ с `IconTile`, тарифы карточками.
- **Пустые состояния**: Lottie или иллюстрация + заголовок + действие.
