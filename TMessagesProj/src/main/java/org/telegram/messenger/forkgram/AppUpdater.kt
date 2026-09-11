package org.telegram.messenger.forkgram

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildVars
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AppUpdater {

    private const val TITLE = "DevGram — обновление"
    private const val DESC = ""
    private const val PREFS_NAME = "AppUpdaterPrefs"
    private const val KEY_LAST_APK_PATH = "lastApkPath"

    private var downloadBroadcastReceiver: DownloadReceiver? = null
    private var lastTimestampOfCheck = 0L
    var downloadId = 0L

    @JvmStatic
    fun clearCachedInstallers(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastApkPath = prefs.getString(KEY_LAST_APK_PATH, null)

            if (lastApkPath != null) {
                val apkFile = File(lastApkPath)
                if (apkFile.exists()) {
                    apkFile.delete()
                    android.util.Log.i("Fork Client", "Deleted saved APK: $lastApkPath")
                }
                prefs.edit().remove(KEY_LAST_APK_PATH).apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("Fork Client", "Error in clearCachedInstallers", e)
        }
    }

    private fun saveApkPath(context: Context, path: String) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_APK_PATH, path)
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("Fork Client", "Error saving APK path", e)
        }
    }

    @JvmStatic
    fun saveApkPathPublic(context: Context, path: String) {
        saveApkPath(context, path)
    }

    /**
     * Java-дружелюбная точка входа: проверяет обновления DevGram (GitHub-релизы
     * firedragoq/DevGram) и сама показывает диалог установки. Зовётся из
     * LaunchActivity.checkAppUpdate — и по кнопке (manual=true), и авто при запуске.
     */
    @JvmStatic
    fun checkForDevGram(parentActivity: Activity, context: Context, manual: Boolean) {
        checkNewVersion(
            parentActivity,
            context,
            legacyCallback = { builder ->
                if (builder != null) {
                    AndroidUtilities.runOnUIThread {
                        try { builder.show() } catch (_: Exception) {}
                    }
                }
                0
            },
            modernCallback = { _ -> 0 },
            manual = manual
        )
    }

    @JvmStatic
    fun checkNewVersion(
        parentActivity: Activity,
        context: Context,
        legacyCallback: (AlertDialog.Builder?) -> Int,
        modernCallback: (TLRPC.TL_help_appUpdate?) -> Int,
        manual: Boolean = false
    ) {

        try {
            val updateInterval = MessagesController.getGlobalMainSettings().getLong("updateForkCheckInterval", 30 * 60 * 1000L)
            if (!manual && (updateInterval == 0L || System.currentTimeMillis() - lastTimestampOfCheck < updateInterval)) {
                return
            }
            if (downloadId != 0L) {
                return
            }
            lastTimestampOfCheck = System.currentTimeMillis()
            val currentVersion = BuildVars.BUILD_VERSION_STRING

            // DevGram: релизы лежат на GitHub (firedragoq/DevGram), поэтому основной путь —
            // GitHub API. Telegram-канал используем ТОЛЬКО если задан реальный
            // UPDATE_CHANNEL_USERNAME (не заглушка "a"/пусто) и есть активная сессия.
            val updateChannel = BuildVars.UPDATE_CHANNEL_USERNAME ?: ""
            if (updateChannel.length > 1 &&
                UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated()
            ) {
                checkUpdateFromTelegramChannel(parentActivity, context, legacyCallback, modernCallback, manual, currentVersion)
            } else {
                checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
            }
        } catch (e: Exception) {
            android.util.Log.e("Fork Client", "Error in checkNewVersion", e)
            if (manual) {
                Toast.makeText(context, "Ошибка проверки обновлений", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkUpdateFromTelegramChannel(
        parentActivity: Activity,
        context: Context,
        legacyCallback: (AlertDialog.Builder?) -> Int,
        modernCallback: (TLRPC.TL_help_appUpdate?) -> Int,
        manual: Boolean,
        currentVersion: String
    ) {

        val req = TLRPC.TL_contacts_resolveUsername()
        req.username = BuildVars.UPDATE_CHANNEL_USERNAME

        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req) { response, error ->
            if (error != null || response !is TLRPC.TL_contacts_resolvedPeer) {
                android.util.Log.w("Fork Client", "Failed to resolve update channel, falling back to GitHub")
                checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                return@sendRequest
            }

            val chat = response.chats.firstOrNull()
            if (chat == null) {
                android.util.Log.w("Fork Client", "Update channel not found, falling back to GitHub")
                checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                return@sendRequest
            }

            val messagesReq = TLRPC.TL_messages_getHistory()
            val inputChannel = TLRPC.TL_inputPeerChannel()
            inputChannel.channel_id = chat.id
            inputChannel.access_hash = chat.access_hash
            messagesReq.peer = inputChannel
            messagesReq.limit = 1

            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(messagesReq) { historyResponse, historyError ->
                if (historyError != null || historyResponse !is TLRPC.messages_Messages) {
                    android.util.Log.w("Fork Client", "Failed to get channel history, falling back to GitHub")
                    checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                    return@sendRequest
                }

                val message = historyResponse.messages.firstOrNull()
                if (message?.message == null) {
                    android.util.Log.w("Fork Client", "No messages in update channel, falling back to GitHub")
                    checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                    return@sendRequest
                }

                try {
                    val updateInfo = JSONObject(message.message)

                    val androidInfo = updateInfo.optJSONObject("android")
                    if (androidInfo == null) {
                        android.util.Log.w("Fork Client", "Invalid update JSON format, falling back to GitHub")
                        checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                        return@sendRequest
                    }

                    val isBeta = org.telegram.messenger.ApplicationLoader.getApplicationId().contains(".beta")
                    val releaseInfo = if (isBeta) {
                        androidInfo.optString("beta")
                    } else {
                        androidInfo.optString("release")
                    }

                    if (releaseInfo.isEmpty()) {
                        android.util.Log.w("Fork Client", "No version info found, falling back to GitHub")
                        checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                        return@sendRequest
                    }

                    val parts = releaseInfo.split(":")
                    if (parts.size != 2) {
                        android.util.Log.w("Fork Client", "Invalid version format, falling back to GitHub")
                        checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                        return@sendRequest
                    }

                    val newVersion = parts[0]
                    val fileInfo = parts[1].split("#")

                    if (fileInfo.size != 2) {
                        android.util.Log.w("Fork Client", "Invalid file info format, falling back to GitHub")
                        checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                        return@sendRequest
                    }

                    val filesChannelUsername = fileInfo[0]
                    val messageId = fileInfo[1].toIntOrNull()

                    if (messageId == null) {
                        android.util.Log.w("Fork Client", "Invalid message ID, falling back to GitHub")
                        checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                        return@sendRequest
                    }

                    lastTimestampOfCheck = System.currentTimeMillis()

                    if (compareVersions(newVersion, currentVersion) <= 0) {
                        if (manual) {
                            AndroidUtilities.runOnUIThread {
                                Toast.makeText(context, "Обновлений нет", Toast.LENGTH_SHORT).show()
                            }
                        }
                        return@sendRequest
                    }

                    // Get download URL from files channel
                    getDownloadUrlFromFilesChannel(parentActivity, context, modernCallback, newVersion, filesChannelUsername, messageId)

                } catch (e: Exception) {
                    android.util.Log.e("Fork Client", "Error parsing update info from Telegram, falling back to GitHub", e)
                    checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                }
            }
        }
    }

    private fun getDownloadUrlFromFilesChannel(
        parentActivity: Activity,
        context: Context,
        modernCallback: (TLRPC.TL_help_appUpdate?) -> Int,
        newVersion: String,
        filesChannelUsername: String,
        messageId: Int
    ) {

        val req = TLRPC.TL_contacts_resolveUsername()
        req.username = filesChannelUsername

        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req) { response, error ->
            if (error != null || response !is TLRPC.TL_contacts_resolvedPeer) {
                android.util.Log.w("Fork Client", "Failed to resolve files channel")
                return@sendRequest
            }

            val chat = response.chats.firstOrNull()
            if (chat == null) {
                android.util.Log.w("Fork Client", "Files channel not found")
                return@sendRequest
            }

            val messagesReq = TLRPC.TL_channels_getMessages()
            val inputChannel = TLRPC.TL_inputChannel()
            inputChannel.channel_id = chat.id
            inputChannel.access_hash = chat.access_hash
            messagesReq.channel = inputChannel
            messagesReq.id.add(messageId)

            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(messagesReq) { messagesResponse, messagesError ->
                if (messagesError != null || messagesResponse !is TLRPC.messages_Messages) {
                    android.util.Log.w("Fork Client", "Failed to get file message")
                    return@sendRequest
                }

                val fileMessage = messagesResponse.messages.firstOrNull()

                val document = fileMessage?.media?.document
                if (document == null) {
                    android.util.Log.w("Fork Client", "No document found in file message")
                    return@sendRequest
                }

                val fullMessage = fileMessage.message ?: ""
                val changelog = if (fullMessage.contains("Changelog:")) {
                    fullMessage.substringAfter("Changelog:").trim()
                } else {
                    fullMessage.takeIf { it.isNotEmpty() } ?: "A new version is available."
                }

                val update = TLRPC.TL_help_appUpdate()
                update.version = newVersion
                update.text = changelog
                update.entities = ArrayList()
                update.document = document
                update.url = ""
                update.can_not_skip = false

                val readReq = TLRPC.TL_channels_readHistory()
                readReq.channel = inputChannel
                readReq.max_id = messageId
                ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(readReq) { _, _ -> }

                AndroidUtilities.runOnUIThread {
                    modernCallback(update)
                }
            }
        }
    }

    /**
     * Превращает markdown-текст релиза GitHub в опрятный текст для диалога:
     *  - заголовки "## ..."/"# ..." убираются (или становятся жирными строками),
     *    строку-дубликат версии не показываем;
     *  - маркеры списков "- "/"* " → "•  ";
     *  - "**жирный**" оставляем — их превратит в bold AndroidUtilities.replaceTags.
     */
    private fun formatChangelog(raw: String?, version: String): CharSequence {
        val src = (raw ?: "").replace("\r\n", "\n").replace("\r", "\n")
        if (src.isBlank()) {
            return "Доступна новая версия DevGram."
        }
        val out = StringBuilder()
        for (lineRaw in src.split("\n")) {
            val trimmed = lineRaw.trim()
            if (trimmed.isEmpty()) {
                out.append("\n")
                continue
            }
            if (trimmed.startsWith("#")) {
                val header = trimmed.trimStart('#', ' ').trim()
                // не дублируем «DevGram X.Y.Z» — это уже в заголовке диалога
                if (header.isEmpty() ||
                    header.contains("DevGram", ignoreCase = true) ||
                    header.contains(version)
                ) {
                    continue
                }
                out.append("**").append(header).append("**").append("\n")
                continue
            }
            val bullet = Regex("^[-*+]\\s+").find(trimmed)
            if (bullet != null) {
                out.append("•  ").append(trimmed.substring(bullet.value.length).trim()).append("\n")
            } else {
                out.append(trimmed).append("\n")
            }
        }
        var result = out.toString().trim().replace(Regex("\n{3,}"), "\n\n")
        if (result.isBlank()) {
            result = "Доступна новая версия DevGram."
        }
        return AndroidUtilities.replaceTags(result)
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split(".")
        val rightParts = right.split(".")
        for (i in 0 until maxOf(leftParts.size, rightParts.size)) {
            val leftPart = leftParts.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
            val rightPart = rightParts.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
            if (leftPart != rightPart) {
                return leftPart.compareTo(rightPart)
            }
        }
        return 0
    }

    private fun checkUpdateFromGitHub(
        parentActivity: Activity,
        context: Context,
        callback: (AlertDialog.Builder?) -> Int,
        manual: Boolean,
        currentVersion: String
    ) {

        try {
            val userRepo = BuildVars.USER_REPO
            if (userRepo.isEmpty()) {
                return
            }

            httpRequest("GET", "https://api.github.com/repos/$userRepo/releases/latest") { response ->
                try {
                    if (response == null) {
                        android.util.Log.w("Fork Client", "Connection error.")
                        return@httpRequest
                    }
                    lastTimestampOfCheck = System.currentTimeMillis()

                    val root = JSONObject(response)
                    // Теги релизов у нас вида "v12.10.2" — убираем ведущий v/V,
                    // иначе compareVersions распарсит "v12" как 0 и сломает сравнение.
                    val tag = root.optString("tag_name").trim().removePrefix("v").removePrefix("V")

                    if (tag.isEmpty() || compareVersions(tag, currentVersion) <= 0) {
                        if (manual) {
                            Toast.makeText(context, "Обновлений нет", Toast.LENGTH_SHORT).show()
                        }
                        return@httpRequest
                    }

                    // New version!
                    val body = root.optString("body")
                    val assets: JSONArray = root.optJSONArray("assets") ?: run {
                        android.util.Log.w("Fork Client", "No assets in release")
                        return@httpRequest
                    }

                    val apks = (0 until assets.length())
                        .mapNotNull { assets.optJSONObject(it) }
                        .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }

                    val asset = apks.firstOrNull { it.optString("name").contains("compressed", ignoreCase = true) }
                        ?: apks.lastOrNull()
                        ?: run {
                            android.util.Log.w("Fork Client", "No apk asset in release")
                            if (manual) {
                                Toast.makeText(context, "В релизе нет APK", Toast.LENGTH_SHORT).show()
                            }
                            return@httpRequest
                        }

                    val url = asset.optString("browser_download_url").takeIf { it.isNotEmpty() } ?: run {
                        android.util.Log.w("Fork Client", "Empty download URL")
                        if (manual) {
                            Toast.makeText(context, "Пустая ссылка на загрузку", Toast.LENGTH_SHORT).show()
                        }
                        return@httpRequest
                    }

                    val builder = AlertDialog.Builder(parentActivity)
                    // Красивая шапка: анимированная иконка загрузки на акцентной подложке
                    builder.setTopAnimation(
                        R.raw.ic_download,
                        56,
                        false,
                        Theme.getColor(Theme.key_dialogTopBackground)
                    )
                    builder.setTitle("Обновление $tag")
                    builder.setMessage(formatChangelog(body, tag))
                    builder.setMessageTextViewClickable(false)
                    builder.setNegativeButton("Позже", null)
                    builder.setPositiveButton("Установить") { _, _ ->
                        try {
                            if (downloadBroadcastReceiver == null) {
                                downloadBroadcastReceiver = DownloadReceiver()
                                val intentFilter = IntentFilter()
                                intentFilter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                                intentFilter.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    context.applicationContext.registerReceiver(downloadBroadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
                                } else {
                                    context.applicationContext.registerReceiver(downloadBroadcastReceiver, intentFilter)
                                }
                                android.util.Log.d("Fork Client", "DownloadReceiver registered")
                            }

                            val dm = DownloadManagerUtil(context)
                            if (dm.checkDownloadManagerEnable()) {
                                if (downloadId != 0L) {
                                    dm.clearCurrentTask(downloadId)
                                }
                                downloadId = dm.download(url, TITLE, DESC)
                                android.util.Log.d("Fork Client", "Download started with ID: $downloadId")
                                Toast.makeText(context, "Загрузка обновления…", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Откройте «Загрузки»", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("Fork Client", "Error starting download", e)
                            Toast.makeText(context, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    callback(builder)
                } catch (e: Exception) {
                    android.util.Log.e("Fork Client", "Error processing update check", e)
                    if (manual) {
                        Toast.makeText(context, "Не удалось проверить обновления", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Fork Client", "Error in checkUpdateFromGitHub", e)
            if (manual) {
                Toast.makeText(context, "Ошибка проверки обновлений", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private const val HTTP_TIMEOUT = 10 * 1000

    private fun httpRequest(method: String, url: String, callback: (String?) -> Unit) {
        Thread {
            val result = try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.readTimeout = HTTP_TIMEOUT
                connection.connectTimeout = HTTP_TIMEOUT
                connection.requestMethod = method
                try {
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        android.util.Log.w("Fork Client", "HTTP error ${connection.responseCode}")
                        null
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                android.util.Log.e("Fork Client", "Network error", e)
                null
            }
            AndroidUtilities.runOnUIThread { callback(result) }
        }.start()
    }
}
