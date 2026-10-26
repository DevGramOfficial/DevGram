package org.telegram.messenger.devgram.player;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/** Общий HTTP-клиент плеера (тексты песен, живые обложки). */
public final class PlayerHttp {

    private static volatile OkHttpClient client;

    private PlayerHttp() {
    }

    public static OkHttpClient client() {
        if (client == null) {
            synchronized (PlayerHttp.class) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .build();
                }
            }
        }
        return client;
    }
}
