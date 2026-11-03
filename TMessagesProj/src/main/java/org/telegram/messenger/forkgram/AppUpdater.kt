package org.telegram.messenger.forkgram

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
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
    private const val KEY_DOWNLOAD_ID = "downloadId"

    private var downloadBroadcastReceiver: DownloadReceiver? = null
    private var lastTimestampOfCheck = 0L
    var downloadId = 0L

    @JvmStatic
    fun clearCachedInstallers(context: Context) {
        // PackageManager читает метаданные каждого APK, поэтому не задерживаем запуск UI.
        // Удаляем только установщики самого DevGram: старые/уже установленные и дубликаты.
        // Один самый новый APK оставляем, если пользователь ещё не завершил установку.
        val appContext = context.applicationContext
        Thread({
            try {
                pruneCachedInstallers(appContext, 0L, null)
            } catch (e: Exception) {
                android.util.Log.e("DevGram", "Error in clearCachedInstallers", e)
            }
        }, "DevGramApkCleanup").start()
    }

    private fun saveApkPath(context: Context, path: String) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_APK_PATH, path)
                .apply()
        } catch (e: Exception) {
            android.util.Log.e("DevGram", "Error saving APK path", e)
        }
    }

    private fun rememberDownloadedApk(context: Context, id: Long): File? {
        if (id <= 0L) return null
        return try {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) return null
                val index = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                if (index < 0) return null
                val localUri = cursor.getString(index) ?: return null
                val uri = Uri.parse(localUri)
                val path = if (uri.scheme == "file") uri.path else localUri.removePrefix("file://")
                if (path.isNullOrEmpty()) return null
                val file = File(path)
                if (!file.exists()) return null
                saveApkPath(context, file.absolutePath)
                file
            }
        } catch (e: Exception) {
            android.util.Log.e("DevGram", "Unable to remember downloaded APK", e)
            null
        }
    }

    @JvmStatic
    fun onDownloadFinished(context: Context, id: Long) {
        rememberDownloadedApk(context, id)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_DOWNLOAD_ID).apply()
        if (downloadId == id) downloadId = 0L
    }

    private data class CachedInstaller(val file: File, val versionCode: Long, val versionName: String)

    private fun installedVersionCode(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    } catch (_: Exception) {
        0L
    }

    private fun normalizeVersion(value: String?): String {
        return (value ?: "").trim().removePrefix("v").removePrefix("V")
    }

    private fun readInstaller(context: Context, file: File): CachedInstaller? {
        if (!file.isFile || !file.name.endsWith(".apk", true)) return null
        return try {
            val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
            if (info.packageName != context.packageName) return null
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
            CachedInstaller(file, code, info.versionName ?: "")
        } catch (_: Exception) {
            null
        }
    }

    private fun installerCandidates(context: Context): List<File> {
        val result = LinkedHashSet<File>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_LAST_APK_PATH, null)?.let { result.add(File(it)) }
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles()?.forEach {
            if (it.isFile && it.name.endsWith(".apk", true)) result.add(it)
        }
        return result.toList()
    }

    private fun matchesExpected(installer: CachedInstaller, expectedCode: Long, expectedName: String?): Boolean {
        if (expectedCode > 0L) return installer.versionCode == expectedCode
        val wantedName = normalizeVersion(expectedName)
        return wantedName.isEmpty() || normalizeVersion(installer.versionName) == wantedName
    }

    /**
     * Чистит накопившиеся APK обновлений и возвращает уже скачанный ожидаемый установщик.
     * expectedCode/expectedName пустые при фоновой уборке после запуска приложения.
     */
    @Synchronized
    private fun pruneCachedInstallers(context: Context, expectedCode: Long, expectedName: String?): File? {
        val installedCode = installedVersionCode(context)
        val candidates = installerCandidates(context)
        val valid = ArrayList<CachedInstaller>()

        for (file in candidates) {
            val installer = readInstaller(context, file)
            if (installer == null) {
                // В собственной папке обновлений битый/чужой файл с нашим служебным именем
                // больше не нужен. Остальные пользовательские APK не трогаем.
                if (file.parentFile == context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) &&
                    file.name.startsWith("DevGram", true)) {
                    file.delete()
                }
                continue
            }
            if (installer.versionCode <= installedCode) {
                if (file.delete()) android.util.Log.i("DevGram", "Deleted installed/old APK: ${file.absolutePath}")
            } else {
                valid.add(installer)
            }
        }

        val expected = valid
            .filter { matchesExpected(it, expectedCode, expectedName) }
            .maxWithOrNull(compareBy<CachedInstaller> { it.versionCode }.thenBy { it.file.lastModified() })

        // При обычной уборке оставляем только самый новый незавершённый установщик. При поиске
        // конкретного релиза оставляем только его, чтобы беты больше не копились гигабайтами.
        val keep = expected ?: if (expectedCode <= 0L && normalizeVersion(expectedName).isEmpty()) {
            valid.maxWithOrNull(compareBy<CachedInstaller> { it.versionCode }.thenBy { it.file.lastModified() })
        } else null
        for (installer in valid) {
            if (installer.file != keep?.file && installer.file.delete()) {
                android.util.Log.i("DevGram", "Deleted obsolete APK: ${installer.file.absolutePath}")
            }
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (keep != null && keep.file.exists()) {
            prefs.edit().putString(KEY_LAST_APK_PATH, keep.file.absolutePath).apply()
            return keep.file
        }
        prefs.edit().remove(KEY_LAST_APK_PATH).apply()
        return null
    }

    private fun openInstaller(context: Context, apk: File): Boolean {
        if (!apk.exists()) return false
        return try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, context.packageName + ".provider", apk)
            } else {
                Uri.fromFile(apk)
            }
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            android.util.Log.e("DevGram", "Unable to open cached APK", e)
            false
        }
    }

    /**
     * Java-дружелюбная точка входа: проверяет обновления DevGram (GitHub-релизы
     * DevGramOfficial/DevGram) и сама показывает диалог установки. Зовётся из
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
            if (downloadId == 0L) {
                downloadId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getLong(KEY_DOWNLOAD_ID, 0L)
            }
            val updateInterval = MessagesController.getGlobalMainSettings().getLong("updateForkCheckInterval", 30 * 60 * 1000L)
            if (!manual && (updateInterval == 0L || System.currentTimeMillis() - lastTimestampOfCheck < updateInterval)) {
                return
            }
            if (downloadId != 0L) {
                // Загрузка уже была запущена. Если она реально идёт — сообщаем и выходим,
                // иначе (завершена/упала/удалена) сбрасываем id, чтобы проверка снова работала.
                val status = queryDownloadStatus(context, downloadId)
                if (status == DownloadManager.STATUS_RUNNING ||
                    status == DownloadManager.STATUS_PENDING ||
                    status == DownloadManager.STATUS_PAUSED
                ) {
                    if (manual) {
                        AndroidUtilities.runOnUIThread {
                            Toast.makeText(context, "Обновление уже скачивается…", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return
                }
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    rememberDownloadedApk(context, downloadId)
                }
                downloadId = 0L
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().remove(KEY_DOWNLOAD_ID).apply()
            }
            lastTimestampOfCheck = System.currentTimeMillis()
            val currentVersion = BuildVars.BUILD_VERSION_STRING

            // DevGram: бета-канal (только для поддержавших) — отдельный путь через api.devgram.space.
            // Проверяем доступ (значок) и наличие токена; иначе тихо откатываемся на основной канал.
            if (org.telegram.messenger.DevGramConfig.updateChannel == 1 &&
                org.telegram.messenger.DevGramBeta.hasAccess(UserConfig.selectedAccount) &&
                org.telegram.messenger.DevGramBeta.hasToken()
            ) {
                checkUpdateFromBeta(parentActivity, context, legacyCallback, manual)
                return
            }

            // Основной канал: релизы на GitHub (DevGramOfficial/DevGram). Telegram-канал —
            // ТОЛЬКО если задан реальный UPDATE_CHANNEL_USERNAME и есть активная сессия.
            val updateChannel = BuildVars.UPDATE_CHANNEL_USERNAME ?: ""
            if (updateChannel.length > 1 &&
                UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated()
            ) {
                checkUpdateFromTelegramChannel(parentActivity, context, legacyCallback, modernCallback, manual, currentVersion)
            } else {
                checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
            }
        } catch (e: Exception) {
            android.util.Log.e("DevGram", "Error in checkNewVersion", e)
            if (manual) {
                Toast.makeText(context, "Ошибка проверки обновлений", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Если этот релиз уже скачан, повторно открываем системную установку. В сеть идём только когда
    // подходящего APK действительно нет или файл повреждён.
    private fun installOrDownload(context: Context, url: String, expectedCode: Long, expectedName: String?) {
        val appContext = context.applicationContext
        Toast.makeText(context, "Проверяю скачанное обновление…", Toast.LENGTH_SHORT).show()
        Thread({
            val cached = pruneCachedInstallers(appContext, expectedCode, expectedName)
            AndroidUtilities.runOnUIThread {
                if (cached != null && openInstaller(appContext, cached)) {
                    Toast.makeText(appContext, "Обновление уже скачано", Toast.LENGTH_SHORT).show()
                } else {
                    startApkDownload(appContext, url, expectedCode, expectedName)
                }
            }
        }, "DevGramApkReuse").start()
    }

    // Общий запуск скачивания APK через DownloadManager (переиспользуется main/beta).
    private fun startApkDownload(context: Context, url: String, expectedCode: Long, expectedName: String?) {
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
        }
        val dm = DownloadManagerUtil(context)
        if (dm.checkDownloadManagerEnable()) {
            if (downloadId != 0L) dm.clearCurrentTask(downloadId)
            val suffix = if (expectedCode > 0L) expectedCode.toString() else
                normalizeVersion(expectedName).replace(Regex("[^0-9A-Za-z._-]"), "_").ifEmpty { "latest" }
            downloadId = dm.download(url, TITLE, DESC, "DevGram-update-$suffix.apk")
            if (downloadId > 0L) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_DOWNLOAD_ID, downloadId).apply()
                Toast.makeText(context, "Загрузка обновления…", Toast.LENGTH_SHORT).show()
            } else {
                downloadId = 0L
                Toast.makeText(context, "Не удалось начать загрузку", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Откройте «Загрузки»", Toast.LENGTH_SHORT).show()
        }
    }

    // Бета-канал: манифест и APK с api.devgram.space (доступ по токену+значку — проверка на сервере).
    private fun checkUpdateFromBeta(
        parentActivity: Activity,
        context: Context,
        callback: (AlertDialog.Builder?) -> Int,
        manual: Boolean
    ) {
        val curCode: Long = try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong()
        } catch (e: Exception) { 0L }

        httpRequest("GET", "https://api.devgram.space/api/beta/latest") { response ->
            try {
                if (response == null) {
                    if (manual) AndroidUtilities.runOnUIThread { Toast.makeText(context, "Ошибка сети", Toast.LENGTH_SHORT).show() }
                    return@httpRequest
                }
                lastTimestampOfCheck = System.currentTimeMillis()
                val root = JSONObject(response)
                if (!root.optBoolean("available")) {
                    if (manual) AndroidUtilities.runOnUIThread { Toast.makeText(context, "Беты пока нет", Toast.LENGTH_SHORT).show() }
                    return@httpRequest
                }
                val betaCode = root.optLong("versionCode", 0)
                if (betaCode <= curCode) {
                    if (manual) AndroidUtilities.runOnUIThread { Toast.makeText(context, "У вас последняя бета", Toast.LENGTH_SHORT).show() }
                    return@httpRequest
                }
                val vn = root.optString("versionName")
                val log = root.optString("changelog")
                val uid = UserConfig.getInstance(UserConfig.selectedAccount).clientUserId
                val token = org.telegram.messenger.DevGramConfig.getBetaToken()
                if (token.isEmpty()) {
                    if (manual) AndroidUtilities.runOnUIThread { Toast.makeText(context, "Активируйте бету в настройках", Toast.LENGTH_SHORT).show() }
                    return@httpRequest
                }
                val url = "https://api.devgram.space/api/beta/download?uid=$uid&token=" +
                        java.net.URLEncoder.encode(token, "UTF-8")

                val builder = AlertDialog.Builder(parentActivity)
                builder.setTopAnimation(R.raw.ic_download, 56, false, Theme.getColor(Theme.key_dialogTopBackground))
                builder.setTitle("Бета $vn")
                builder.setMessage(formatChangelog(log, vn))
                builder.setMessageTextViewClickable(false)
                builder.setNegativeButton("Позже", null)
                builder.setPositiveButton("Установить") { _, _ ->
                    try { installOrDownload(context, url, betaCode, vn) }
                    catch (e: Exception) { Toast.makeText(context, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
                callback(builder)
            } catch (e: Exception) {
                android.util.Log.e("DevGram", "Beta check error", e)
                if (manual) AndroidUtilities.runOnUIThread { Toast.makeText(context, "Не удалось проверить бету", Toast.LENGTH_SHORT).show() }
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
                android.util.Log.w("DevGram", "Failed to resolve update channel, falling back to GitHub")
                checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                return@sendRequest
            }

            val chat = response.chats.firstOrNull()
            if (chat == null) {
                android.util.Log.w("DevGram", "Update channel not found, falling back to GitHub")
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
                    android.util.Log.w("DevGram", "Failed to get channel history, falling back to GitHub")
                    checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                    return@sendRequest
                }

                val message = historyResponse.messages.firstOrNull()
                if (message?.message == null) {
                    android.util.Log.w("DevGram", "No messages in update channel, falling back to GitHub")
                    checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                    return@sendRequest
                }

                try {
                    val updateInfo = JSONObject(message.message)

                    val androidInfo = updateInfo.optJSONObject("android")
                    if (androidInfo == null) {
                        android.util.Log.w("DevGram", "Invalid update JSON format, falling back to GitHub")
                        checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                        return@sendRequest
                    }

                    val isBeta = org.telegram.messenger.ApplicationLoader.getApplicationId().contains(".beta")
                    val stableInfo = if (isBeta) {
                        androidInfo.optString("beta")
                    } else {
                        androidInfo.optString("release")
                    }
                    val optionalInfo = if (manual && isBeta) androidInfo.optString("optional") else ""
                    val releaseInfo = when {
                        optionalInfo.isEmpty() -> stableInfo
                        stableInfo.isEmpty() -> optionalInfo
                        compareVersions(optionalInfo.substringBefore(":"), stableInfo.substringBefore(":")) > 0 -> optionalInfo
                        else -> stableInfo
                    }

                    if (releaseInfo.isEmpty()) {
                        android.util.Log.w("DevGram", "No version info found, falling back to GitHub")
                        checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                        return@sendRequest
                    }

                    val parts = releaseInfo.split(":")
                    if (parts.size != 2) {
                        android.util.Log.w("DevGram", "Invalid version format, falling back to GitHub")
                        checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                        return@sendRequest
                    }

                    val newVersion = parts[0]
                    val fileInfo = parts[1].split("#")

                    if (fileInfo.size != 2) {
                        android.util.Log.w("DevGram", "Invalid file info format, falling back to GitHub")
                        checkUpdateFromGitHub(parentActivity, context, legacyCallback, manual, currentVersion)
                        return@sendRequest
                    }

                    val filesChannelUsername = fileInfo[0]
                    val messageId = fileInfo[1].toIntOrNull()

                    if (messageId == null) {
                        android.util.Log.w("DevGram", "Invalid message ID, falling back to GitHub")
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
                    android.util.Log.e("DevGram", "Error parsing update info from Telegram, falling back to GitHub", e)
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
                android.util.Log.w("DevGram", "Failed to resolve files channel")
                return@sendRequest
            }

            val chat = response.chats.firstOrNull()
            if (chat == null) {
                android.util.Log.w("DevGram", "Files channel not found")
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
                    android.util.Log.w("DevGram", "Failed to get file message")
                    return@sendRequest
                }

                val fileMessage = messagesResponse.messages.firstOrNull()

                val document = fileMessage?.media?.document
                if (document == null) {
                    android.util.Log.w("DevGram", "No document found in file message")
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
                        android.util.Log.w("DevGram", "Connection error.")
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
                        android.util.Log.w("DevGram", "No assets in release")
                        return@httpRequest
                    }

                    val apks = (0 until assets.length())
                        .mapNotNull { assets.optJSONObject(it) }
                        .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }

                    val asset = apks.firstOrNull { it.optString("name").contains("compressed", ignoreCase = true) }
                        ?: apks.lastOrNull()
                        ?: run {
                            android.util.Log.w("DevGram", "No apk asset in release")
                            if (manual) {
                                Toast.makeText(context, "В релизе нет APK", Toast.LENGTH_SHORT).show()
                            }
                            return@httpRequest
                        }

                    val url = asset.optString("browser_download_url").takeIf { it.isNotEmpty() } ?: run {
                        android.util.Log.w("DevGram", "Empty download URL")
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
                            installOrDownload(context, url, 0L, tag)
                        } catch (e: Exception) {
                            android.util.Log.e("DevGram", "Error starting download", e)
                            Toast.makeText(context, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    callback(builder)
                } catch (e: Exception) {
                    android.util.Log.e("DevGram", "Error processing update check", e)
                    if (manual) {
                        Toast.makeText(context, "Не удалось проверить обновления", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DevGram", "Error in checkUpdateFromGitHub", e)
            if (manual) {
                Toast.makeText(context, "Ошибка проверки обновлений", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Статус загрузки по id (DownloadManager.STATUS_*), либо -1 если её уже нет. */
    private fun queryDownloadStatus(context: Context, id: Long): Int {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (c != null && c.moveToFirst()) {
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                } else {
                    -1
                }
            }
        } catch (e: Exception) {
            -1
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
                        android.util.Log.w("DevGram", "HTTP error ${connection.responseCode}")
                        null
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                android.util.Log.e("DevGram", "Network error", e)
                null
            }
            AndroidUtilities.runOnUIThread { callback(result) }
        }.start()
    }
}
