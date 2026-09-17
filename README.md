<p align="center">
  <img src="TMessagesProj/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="112" alt="DevGram logo">
</p>

<h1 align="center">DevGram for Android</h1>

<p align="center">
  Современный неофициальный клиент Telegram с расширенными настройками интерфейса,
  приватности, чатов и встроенными инструментами DevGram.
</p>

<p align="center">
  <a href="https://t.me/DevGramNews"><img src="https://img.shields.io/badge/Скачать-APK-6C2BD9?style=for-the-badge&logo=android&logoColor=white" alt="Скачать APK"></a>
  <a href="https://t.me/DevGramNews"><img src="https://img.shields.io/badge/Telegram-@DevGramNews-229ED9?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram канал"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/Лицензия-GPL--2.0-8A2BE2?style=for-the-badge" alt="GPL-2.0"></a>
</p>

> [!IMPORTANT]
> DevGram — независимый проект. Он не связан с Telegram FZ-LLC и не является официальным приложением Telegram.

## Что внутри

- **Обновлённый интерфейс** — Material Design 3, стеклянные элементы, гибкие формы аватаров, папок и стикеров.
- **Настройки чатов** — живые превью, действия по двойному нажатию, расширенное меню сообщений, управление реакциями и медиа.
- **Приватность** — режим призрака, сохранение удалённых сообщений и истории изменений.
- **Перевод** — Telegram, Google, Yandex и DeepL, выбор языка и формальности.
- **AI Chat** — несколько совместимых сервисов, роли, история и генерация ответа из сообщения.
- **Медиа** — CameraX, настройка камеры кружков, HD-фото, управление воспроизведением и офлайн-распознавание Vosk.
- **Темы** — отдельные темы для разных чатов, включая установленные пользователем темы.
- **Экосистема DevGram** — значки, каталог плагинов, модерация и дополнительные инструменты команды.

## Скачать

Готовые универсальные APK для `arm64-v8a`, `armeabi-v7a`, `x86` и `x86_64`
публикуются в канале [@DevGramNews](https://t.me/DevGramNews).

| Сборка | Для чего |
|---|---|
| **Release** | Рекомендуемая стабильная сборка для обычной установки |
| **Debug** | Сборка для тестирования и быстрой проверки новых функций |

Минимальная версия системы — **Android 7.0 (API 24)**.

Новости, обновления и обсуждение: [@DevGramNews](https://t.me/DevGramNews).

## Сборка из исходников

```bash
git clone --recursive https://github.com/DevGramOfficial/DevGram.git
cd DevGram
./gradlew --no-daemon --no-parallel \
  :TMessagesProj_App:assembleAfatDebug \
  -x buildNativeDeps
```

Release-вариант:

```bash
./gradlew --no-daemon --no-parallel \
  :TMessagesProj_App:assembleAfatRelease \
  -x buildNativeDeps
```

Подробная подготовка SDK, NDK и нативных библиотек описана в [BUILDING.md](BUILDING.md).

Для собственной публикации используйте свои Telegram API ID/hash и signing key. Приватные ключи и пароли
следует хранить в `local.properties`, а не добавлять в Git.

## Благодарности

DevGram основан на исходном коде [Telegram for Android](https://github.com/DrKLO/Telegram),
[Forkgram](https://github.com/forkgram/TelegramAndroid) и развивает идеи интерфейса
[exteraGram](https://github.com/exteraSquad/exteraGram).

Исходный код распространяется по лицензии [GNU GPL v2](LICENSE).
