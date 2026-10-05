package org.telegram.messenger;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * DevGram: реквизиты для доната (карты + Tonkeeper). Берутся с сервера
 * (api.devgram.space/api/requisites, НЕ Firebase) и кэшируются локально. Владелец меняет их
 * через @officialdevgram_bot (/req). До первого ответа сервера — показываем кэш или дефолт.
 */
public class DevGramRequisites {

    public static final String URL = "https://api.devgram.space/api/requisites";
    private static final String PREFS = "devgram_requisites";
    private static final String KEY_JSON = "json";

    public static class Card {
        public final String id, bank, number, holder, logo;
        public Card(String id, String bank, String number, String holder, String logo) {
            this.id = id; this.bank = bank; this.number = number; this.holder = holder; this.logo = logo;
        }
    }

    public static class Data {
        public final List<Card> cards;
        public final String tonkeeper;
        public Data(List<Card> cards, String tonkeeper) {
            this.cards = cards; this.tonkeeper = tonkeeper;
        }
    }

    // дефолт на случай пустого кэша/оффлайна (совпадает с прежними хардкод-реквизитами)
    private static Data defaults() {
        List<Card> cards = new ArrayList<>();
        cards.add(new Card("alfa", "Альфа-Банк", "2200150522613204", "", ""));
        cards.add(new Card("tbank", "Т-Банк", "2200396111123788", "", ""));
        return new Data(cards, "UQD9m9PQ5BfQa_sAA09HqJ2WLfmFDoyPxsZ8kSEyD-lXMYsx");
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }

    // Текущие реквизиты: из кэша, иначе дефолт. Синхронно, без сети.
    public static Data load() {
        String json = prefs().getString(KEY_JSON, "");
        if (!json.isEmpty()) {
            Data d = parse(json);
            if (d != null && !d.cards.isEmpty()) {
                return d;
            }
        }
        return defaults();
    }

    // Обновить с сервера в фоне; onDone вызывается в UI-потоке (можно null).
    public static void refresh(Runnable onDone) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL(URL).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "DevGram");
                if (c.getResponseCode() == 200) {
                    java.io.InputStream in = c.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) bo.write(buf, 0, n);
                    in.close();
                    String json = bo.toString("UTF-8");
                    if (parse(json) != null) {
                        prefs().edit().putString(KEY_JSON, json).apply();
                    }
                }
                c.disconnect();
            } catch (Throwable ignore) {
            }
            if (onDone != null) {
                AndroidUtilities.runOnUIThread(onDone);
            }
        });
    }

    private static Data parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            List<Card> cards = new ArrayList<>();
            JSONArray arr = o.optJSONArray("cards");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    String num = c.optString("number", "").trim();
                    if (num.isEmpty()) continue;
                    cards.add(new Card(c.optString("id", ""), c.optString("bank", ""), num, c.optString("holder", ""), c.optString("logo", "")));
                }
            }
            String ton = o.optJSONObject("tonkeeper") != null
                    ? o.optJSONObject("tonkeeper").optString("address", "") : "";
            return new Data(cards, ton);
        } catch (Throwable e) {
            return null;
        }
    }

    // Логотип карты по URL: грузим один раз, кэшируем в памяти, ставим в ImageView.
    private static final java.util.HashMap<String, android.graphics.Bitmap> LOGO_CACHE = new java.util.HashMap<>();

    public static void applyLogo(android.widget.ImageView iv, String url) {
        if (iv == null || url == null || url.isEmpty()) {
            return;
        }
        android.graphics.Bitmap cached;
        synchronized (LOGO_CACHE) {
            cached = LOGO_CACHE.get(url);
        }
        if (cached != null) {
            iv.setColorFilter(null);
            iv.setImageBitmap(cached);
            return;
        }
        iv.setTag(url);
        Utilities.globalQueue.postRunnable(() -> {
            android.graphics.Bitmap bmp = null;
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "DevGram");
                if (c.getResponseCode() == 200) {
                    java.io.InputStream in = c.getInputStream();
                    bmp = android.graphics.BitmapFactory.decodeStream(in);
                    in.close();
                }
                c.disconnect();
            } catch (Throwable ignore) {
            }
            if (bmp == null) {
                return;
            }
            final android.graphics.Bitmap fbmp = bmp;
            synchronized (LOGO_CACHE) {
                LOGO_CACHE.put(url, fbmp);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (url.equals(iv.getTag())) {
                    iv.setColorFilter(null);
                    iv.setImageBitmap(fbmp);
                }
            });
        });
    }

    // Иконка банка по названию (известные — свои ассеты, иначе generic карта).
    public static int bankIcon(String bank, boolean dark) {
        String b = bank == null ? "" : bank.toLowerCase();
        if (b.contains("альфа") || b.contains("alfa")) {
            return dark ? R.drawable.devgram_bank_alfa_dark : R.drawable.devgram_bank_alfa;
        }
        if (b.contains("сбер") || b.contains("sber")) {
            return dark ? R.drawable.devgram_bank_sber_dark : R.drawable.devgram_bank_sber;
        }
        if (b.contains("втб") || b.contains("vtb")) {
            return dark ? R.drawable.devgram_bank_vtb_dark : R.drawable.devgram_bank_vtb;
        }
        if (b.contains("т-банк") || b.contains("тбанк") || b.contains("тинь") || b.contains("tbank") || b.contains("tinkoff")) {
            return dark ? R.drawable.devgram_bank_tbank_dark : R.drawable.devgram_bank_tbank;
        }
        return R.drawable.msg_payment_card; // generic — для произвольного банка без логотипа
    }
}
