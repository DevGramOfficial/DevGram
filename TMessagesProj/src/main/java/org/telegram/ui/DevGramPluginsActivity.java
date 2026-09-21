/*
 * DevGram: менеджер плагинов — карточный список (иконка, имя, версия/автор, описание,
 * тумблер вкл/выкл, удаление). Кнопка «i» сверху справа открывает «Систему плагинов».
 */

package org.telegram.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramPlugins;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DevGramPluginsActivity extends BaseFragment {

    private UniversalRecyclerView listView;
    private final ArrayList<String[]> rows = new ArrayList<>();

    private static final int BTN_SYSTEM = 100; // кнопка «i» — система плагинов
    private static final int BTN_CATALOG = 101; // каталог плагинов

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Плагины");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == BTN_SYSTEM) {
                    presentFragment(new DevGramPluginSystemActivity());
                }
            }
        });
        actionBar.createMenu().addItem(BTN_SYSTEM, R.drawable.msg_info); // «i» — система плагинов

        FrameLayout content = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = content;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        rows.clear();
        List<String> list = DevGramPlugins.listPlugins();
        for (String s : list) {
            rows.add(s.split("\u001f", -1));
        }

        items.add(UItem.asButton(BTN_CATALOG, R.drawable.devgram_plugins, "Каталог плагинов", "Найти и установить"));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Плагины (" + rows.size() + ")"));
        if (rows.isEmpty()) {
            items.add(UItem.asShadow("Плагинов нет."));
        } else {
            for (String[] p : rows) {
                items.add(UItem.asCustom(createPluginCard(getContext(), p)));
            }
        }
    }

    private View createPluginCard(Context context, String[] p) {
        final String id = p.length > 0 ? p[0] : "";
        String name = p.length > 1 && !p[1].isEmpty() ? p[1] : id;
        String ver = p.length > 2 ? p[2] : "";
        String author = p.length > 3 ? p[3] : "";
        boolean enabled = p.length > 4 && "1".equals(p[4]);
        final String filename = p.length > 5 ? p[5] : "";
        String desc = p.length > 6 ? p[6] : "";
        String iconUrl = p.length > 7 ? p[7] : "";

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        // фон карточки: обычный «cell», но если сливается с фоном экрана (AMOLED) — чуть осветляем
        int base = Theme.getColor(Theme.key_windowBackgroundWhite);
        int listBg = Theme.getColor(Theme.key_windowBackgroundGray);
        int cardColor = base == listBg ? androidx.core.graphics.ColorUtils.blendARGB(base, 0xFFFFFFFF, 0.06f) : base;
        card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), cardColor));
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(8));

        // шапка: иконка + (имя/мета) + тумблер
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.devgram_cat_general);
        icon.setColorFilter(0xFFFFFFFF);
        icon.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        icon.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(11), Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)));
        // скруглённый клип, чтобы своя аватарка плагина не вылезала за уголки
        icon.setClipToOutline(true);
        icon.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), AndroidUtilities.dp(11));
            }
        });
        if (!iconUrl.isEmpty()) {
            loadPluginIcon(icon, iconUrl);
        }
        header.addView(icon, LayoutHelper.createLinear(42, 42));

        LinearLayout mid = new LinearLayout(context);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView nameTv = new TextView(context);
        nameTv.setText(name);
        nameTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTv.setTypeface(AndroidUtilities.bold());
        mid.addView(nameTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        TextView metaTv = new TextView(context);
        StringBuilder meta = new StringBuilder();
        if (!ver.isEmpty()) {
            meta.append("v").append(ver);
        }
        if (!author.isEmpty()) {
            if (meta.length() > 0) {
                meta.append("  •  ");
            }
            meta.append(author);
        }
        metaTv.setText(meta.toString());
        metaTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        metaTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        mid.addView(metaTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));
        header.addView(mid, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 12, 0, 8, 0));

        Switch sw = new Switch(context);
        sw.setDrawIconType(1);
        sw.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        sw.setChecked(enabled, false);
        header.addView(sw, LayoutHelper.createLinear(37, 40, Gravity.CENTER_VERTICAL));

        // Switch в Telegram сам по тапу не переключается — тогглим по клику на всю строку-шапку.
        final boolean[] on = {enabled};
        header.setOnClickListener(v -> {
            on[0] = !on[0];
            sw.setChecked(on[0], true);
            DevGramPlugins.setEnabled(id, on[0]);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });

        card.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // описание (скрывается в компактном виде)
        if (!desc.isEmpty() && !DevGramPlugins.flag("compact_view", false)) {
            TextView d = new TextView(context);
            d.setText(desc);
            d.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            d.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            card.addView(d, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 4, 0));
        }

        // имя файла (только в режиме разработчика)
        if (DevGramPlugins.flag("dev_mode", false) && !filename.isEmpty()) {
            TextView fn = new TextView(context);
            fn.setText("📄 " + filename);
            fn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            fn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            card.addView(fn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        }

        // действия: [Настройки] ... [Удалить]
        final String pname = name;
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        // Настройки плагина — иконка-шестерёнка (только если у плагина есть настройки)
        if (DevGramPlugins.hasSettings(id)) {
            ImageView setBtn = iconButton(context, R.drawable.outline_profile_settings,
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), "Настройки плагина");
            setBtn.setOnClickListener(v -> presentFragment(new DevGramPluginSettingsActivity(id, pname)));
            actions.addView(setBtn, LayoutHelper.createLinear(38, 38));
        }

        // Поделиться — открыть меню пересылки и отправить файл плагина
        ImageView shareBtn = iconButton(context, R.drawable.filled_share,
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), "Поделиться плагином");
        shareBtn.setOnClickListener(v -> sharePlugin(filename, pname));
        actions.addView(shareBtn, LayoutHelper.createLinear(38, 38, 0f, Gravity.CENTER_VERTICAL, 4, 0, 0, 0));

        View spacer = new View(context);
        actions.addView(spacer, LayoutHelper.createLinear(0, 1, 1f));

        TextView del = new TextView(context);
        del.setText("Удалить");
        del.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        del.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        del.setTypeface(AndroidUtilities.bold());
        del.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        del.setOnClickListener(v -> {
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
            b.setTitle("Удалить плагин");
            b.setMessage("Удалить «" + pname + "»?");
            b.setPositiveButton("Удалить", (d2, w) -> {
                DevGramPlugins.delete(id, filename);
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(b.create());
        });
        actions.addView(del, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        card.addView(actions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        // внешний контейнер с отступами между карточками
        LinearLayout outer = new LinearLayout(context);
        outer.setPadding(AndroidUtilities.dp(9), AndroidUtilities.dp(5), AndroidUtilities.dp(9), AndroidUtilities.dp(3));
        outer.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return outer;
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BTN_CATALOG) {
            presentFragment(new DevGramPluginCatalogActivity());
            return;
        }
        // остальные карточки обрабатывают клики сами (тумблер/удаление)
    }

    // Круглая иконка-кнопка с ripple.
    private ImageView iconButton(Context context, int resId, int tint, String description) {
        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setImageResource(resId);
        iv.setColorFilter(new android.graphics.PorterDuffColorFilter(tint, android.graphics.PorterDuff.Mode.SRC_IN));
        iv.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        iv.setContentDescription(description);
        int p = AndroidUtilities.dp(8);
        iv.setPadding(p, p, p, p);
        return iv;
    }

    // Поделиться файлом плагина: открываем системное меню отправки (в т.ч. переслать в чат Telegram).
    private void sharePlugin(String filename, String pname) {
        try {
            if (filename == null || filename.isEmpty()) {
                return;
            }
            java.io.File file = new java.io.File(DevGramPlugins.pluginsDir(), filename);
            if (!file.exists()) {
                org.telegram.ui.Components.BulletinFactory.of(this)
                        .createErrorBulletin("Файл плагина не найден").show();
                return;
            }
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            // единый функционал: .dgplugin — это архив, .py/.plugin — исходник
            intent.setType(filename.endsWith(".dgplugin") ? "application/octet-stream" : "text/x-python");
            android.net.Uri uri;
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                uri = androidx.core.content.FileProvider.getUriForFile(getParentActivity(),
                        org.telegram.messenger.ApplicationLoader.getApplicationId() + ".provider", file);
                intent.setFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                uri = android.net.Uri.fromFile(file);
            }
            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            intent.putExtra(android.content.Intent.EXTRA_SUBJECT, pname);
            getParentActivity().startActivity(android.content.Intent.createChooser(intent, "Поделиться плагином"));
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
            org.telegram.ui.Components.BulletinFactory.of(this)
                    .createErrorBulletin("Не удалось поделиться").show();
        }
    }

    /**
     * Аватарка плагина по URL: сначала пробуем кеш, иначе качаем в фоне,
     * кладём в кеш и ставим в ImageView на UI-потоке. При ошибке остаётся дефолтная иконка.
     */
    private void loadPluginIcon(final ImageView iv, final String url) {
        final Context ctx = getContext();
        if (ctx == null) return;
        java.io.File dir = new java.io.File(ctx.getCacheDir(), "devgram_icons");
        dir.mkdirs();
        final java.io.File cache = new java.io.File(dir, Integer.toHexString(url.hashCode()) + ".img");
        if (cache.exists() && cache.length() > 0) {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(cache.getAbsolutePath());
            if (bmp != null) {
                applyIcon(iv, bmp);
                return;
            }
        }
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "DevGram");
                java.io.InputStream in = c.getInputStream();
                java.io.File tmp = new java.io.File(cache.getAbsolutePath() + ".tmp");
                java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                c.disconnect();
                tmp.renameTo(cache);
                final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(cache.getAbsolutePath());
                if (bmp != null) {
                    AndroidUtilities.runOnUIThread(() -> applyIcon(iv, bmp));
                }
            } catch (Throwable e) {
                org.telegram.messenger.FileLog.e(e);
            }
        }).start();
    }

    private void applyIcon(ImageView iv, android.graphics.Bitmap bmp) {
        iv.setColorFilter(null);
        iv.setPadding(0, 0, 0, 0);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackground(null);
        iv.setImageBitmap(bmp);
    }

    @Override
    public void onResume() {
        super.onResume();
        // применить изменения флагов (компактный вид / режим разработчика) при возврате
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}
