<p align="center">
  <img src=".github/assets/readme-banner.svg" alt="DevGram for Android" width="100%">
</p>

<p align="center">
  <a href="https://t.me/DevGramNews"><img src="https://img.shields.io/badge/Скачать-APK-7c3aed?style=for-the-badge&logo=telegram&logoColor=white" alt="Скачать APK"></a>
  <a href="https://docs.devgram.space"><img src="https://img.shields.io/badge/Документация-docs.devgram.space-5b21b6?style=for-the-badge" alt="Документация"></a>
  <a href="https://github.com/DevGramOfficial/DevGramSDK"><img src="https://img.shields.io/badge/Plugin-SDK-9333ea?style=for-the-badge&logo=python&logoColor=white" alt="Plugin SDK"></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 7.0+">
  <img src="https://img.shields.io/badge/Material-Design%203-6750A4?logo=materialdesign&logoColor=white" alt="Material Design 3">
  <img src="https://img.shields.io/badge/Plugin%20API-3-a855f7" alt="Plugin API 3">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--2.0-8b5cf6" alt="GPL-2.0"></a>
</p>

<p align="center">
  Современный неофициальный клиент Telegram для Android с глубокими настройками
  интерфейса, приватности, чатов и открытой системой плагинов.
</p>

> [!IMPORTANT]
> DevGram — независимый проект. Он не связан с Telegram FZ-LLC и не является официальным приложением Telegram.

## Почему DevGram

| | Возможность | Что меняется |
| --- | --- | --- |
| 🎨 | **Современный интерфейс** | Material Design 3, стеклянные элементы, системные шрифты и гибкая настройка внешнего вида. |
| 👻 | **Приватность** | Режим призрака, сохранение удалённых сообщений, история изменений и управление активностью. |
| 💬 | **Больше контроля над чатами** | Живые превью, расширенное меню сообщений, реакции, перевод, медиа, архив и папки. |
| 🧩 | **Открытые плагины** | Python 3.11, нативный Telegram API, каталог, обновления и быстрая разработка через Dev Server. |
| ✨ | **Персонализация** | Темы отдельных чатов, формы аватаров и стикеров, профиль, список диалогов и нижняя навигация. |
| 🛠️ | **Встроенные инструменты** | CameraX, HD-фото, настройка видеосообщений, AI-инструменты и офлайн-распознавание Vosk. |

## Экосистема

| Проект | Назначение |
| --- | --- |
| **[DevGram SDK](https://github.com/DevGramOfficial/DevGramSDK)** | Исходный код Python API, загрузчика `.dgplugin`, нативного Java-моста и Dev Server. |
| **[DevGram Builder](https://github.com/DevGramOfficial/DevGramBuilder)** | Создание проекта, проверка, сборка и загрузка плагинов. |
| **[Документация](https://docs.devgram.space/docs/introduction)** | Руководства по SDK, Builder, интерфейсу, хукам и формату пакета. |
| **[@DevGramNews](https://t.me/DevGramNews)** | APK, новости, обновления и важные объявления. |

## Скачать

Актуальные APK публикуются в [официальном Telegram-канале](https://t.me/DevGramNews).
Универсальная сборка поддерживает `arm64-v8a` и `armeabi-v7a`.
Минимальная версия системы — **Android 7.0 (API 24)**.

| Сборка | Назначение |
| --- | --- |
| **Release** | Стабильная версия для обычного использования. |
| **Debug** | Тестирование новых функций и разработка плагинов. |

## Сборка из исходников

<details>
<summary><b>Показать команды сборки</b></summary>

```bash
git clone --recursive https://github.com/DevGramOfficial/DevGram.git
cd DevGram

# Debug
./gradlew --no-daemon --no-parallel \
  :TMessagesProj_App:assembleAfatDebug \
  -x buildNativeDeps

# Release
./gradlew --no-daemon --no-parallel \
  :TMessagesProj_App:assembleAfatRelease \
  -x buildNativeDeps
```

Подготовка Android SDK, NDK и нативных библиотек описана в
**[BUILDING.md](BUILDING.md)**. Для собственной публикации используйте свои
Telegram API ID/hash и signing key. Не добавляйте ключи и пароли в Git.

</details>

## Создание плагина

```bash
python -m pip install --upgrade \
  "git+https://github.com/DevGramOfficial/DevGramBuilder.git"

mkdir hello-devgram && cd hello-devgram
dgb new
dgb build -a -v -nf
```

Готовый `.dgplugin` появится в `builds/`. Полный путь от первого проекта до
публикации находится в [документации DevGram Builder](https://docs.devgram.space/docs/builder).

## Участие в разработке

Сообщения об ошибках и предложения можно оставить в
[Issues](https://github.com/DevGramOfficial/DevGram/issues). Перед отправкой
уберите из логов токены, личные сообщения и другие чувствительные данные.

DevGram основан на [Telegram for Android](https://github.com/DrKLO/Telegram),
[Forkgram](https://github.com/forkgram/TelegramAndroid) и
[exteraGram](https://github.com/exteraSquad/exteraGram), а также развивает идеи
[Cherrygram](https://github.com/arslan4k1390/Cherrygram) и
[AyuGram](https://github.com/AyuGram/AyuGram4A).

Исходный код распространяется по лицензии **[GNU GPL v2](LICENSE)**.

<p align="center">
  <a href="https://github.com/DevGramOfficial">DevGramOfficial</a> ·
  <a href="https://t.me/DevGramNews">Telegram</a> ·
  <a href="https://docs.devgram.space">Документация</a>
</p>
