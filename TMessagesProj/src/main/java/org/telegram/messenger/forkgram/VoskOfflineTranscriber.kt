package org.telegram.messenger.forkgram

import org.telegram.messenger.DevGramAiClient
import org.telegram.messenger.DevGramConfig
import org.telegram.messenger.DevGramVoiceRecognizer
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer
import java.util.function.Consumer

// DevGram: офлайн-транскрайбер на встроенном Vosk-движке (DevGramVoiceRecognizer) для фреймворка
// ForkOfflineTranscribe. Отдаёт частичные результаты (onProgress) и финальный (onFinal),
// с опциональной ИИ-постобработкой текста через DevGramAiClient.
class VoskOfflineTranscriber : OfflineTranscriber {

    override fun capabilities(): org.opentranscribe.api.TranscriberCapabilities? = null

    override fun requestTranscription(
        audioFilePath: String,
        languageHint: String?,
        onProgress: Consumer<String>,
        onFinal: BiConsumer<String?, Exception?>
    ): TranscriptionCancellable {
        val cancelled = AtomicBoolean(false)
        val language = DevGramConfig.getRecognitionLanguage()
        val transcript = StringBuilder()

        fun appendSegment(segment: String?): String {
            val clean = segment?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
            if (clean.isNotEmpty()) {
                if (transcript.isNotEmpty()) transcript.append(' ')
                transcript.append(clean)
            }
            return transcript.toString()
        }

        Thread({
            try {
                DevGramVoiceRecognizer.getInstance().recognize(
                    audioFilePath, language,
                    object : DevGramVoiceRecognizer.RecognitionCallback {
                        override fun onChunk(text: String?) {
                            if (!cancelled.get() && !text.isNullOrEmpty()) {
                                // onResult содержит очередную завершённую фразу, а не всю
                                // расшифровку. UI должен получать накопленный текст.
                                onProgress.accept(appendSegment(text))
                            }
                        }

                        override fun onCompleted(text: String?) {
                            if (!cancelled.get()) {
                                // onFinalResult — только оставшийся хвост после всех onResult.
                                finish(appendSegment(text), cancelled, onFinal)
                            }
                        }

                        override fun onError(e: Exception?) {
                            if (!cancelled.get()) {
                                onFinal.accept(null, e ?: Exception("Vosk error"))
                            }
                        }

                        override fun onNotDownloaded(lang: String?) {
                            if (!cancelled.get()) {
                                onFinal.accept(null, IOException("Модель не скачана: $lang"))
                            }
                        }

                        override fun onNotSupported(lang: String?) {
                            if (!cancelled.get()) {
                                onFinal.accept(null, IOException("Язык не поддерживается: $lang"))
                            }
                        }
                    })
            } catch (e: Exception) {
                if (!cancelled.get()) {
                    onFinal.accept(null, e)
                }
            }
        }, "DevGramVosk").start()

        return object : TranscriptionCancellable {
            override fun cancel() {
                cancelled.set(true)
            }
        }
    }

    // Финализация: при включённой ИИ-постобработке прогоняем текст через DevGramAiClient, иначе отдаём как есть.
    private fun finish(text: String, cancelled: AtomicBoolean, onFinal: BiConsumer<String?, Exception?>) {
        if (text.isNotEmpty()
            && DevGramConfig.isRecognitionAiPostProcessing()
            && DevGramAiClient.isConfigured()
        ) {
            val prompt =
                "Ты редактор расшифровок голосовых сообщений. Исправь ошибки распознавания речи, " +
                "расставь пунктуацию, раздели на предложения, сохрани исходный смысл и язык. " +
                "Не добавляй ничего от себя. Верни только исправленный текст:\n\n$text"
            DevGramAiClient.generate(prompt) { result, error ->
                if (cancelled.get()) return@generate
                if (error == null && !result.isNullOrBlank()) {
                    onFinal.accept(result.trim(), null)
                } else {
                    onFinal.accept(text, null)
                }
            }
        } else {
            onFinal.accept(text, null)
        }
    }
}
