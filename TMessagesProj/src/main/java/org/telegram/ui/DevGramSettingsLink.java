package org.telegram.ui;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;

import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;

// DevGram: deep-ссылки на настройки/категории (как t.me/exteraSettings у exteraGram).
// Формат: https://t.me/DevGramSettings?s=<код_экрана>[__<id_пункта>]
//   • ссылка на категорию/экран   → s=<код>            (открывает экран)
//   • ссылка на конкретный пункт   → s=<код>__<id>      (открывает экран + подсветка)
// Зажатие пункта в наших экранах → меню «Копировать ссылку» / «Поделиться ссылкой».
public final class DevGramSettingsLink {

    public static final String USERNAME = "DevGramSettings";

    // ждёт подсветки пункта после открытия экрана по ссылке (одноразово)
    public static volatile int pendingHighlight = 0;

    private DevGramSettingsLink() {}

    // ---------- построение ссылки ----------
    public static String build(String code, int itemId) {
        String s;
        if ("general".equals(code) && itemId == DevGramCategoryActivity.ID_TRANSLATE_CHAT) {
            s = "showTranslateChatButton";
        } else if ("ai".equals(code) && itemId == DevGramCategoryActivity.ID_AI_SERVICES) {
            s = "aiServices";
        } else if ("ai".equals(code) && itemId == DevGramCategoryActivity.ID_AI_HISTORY) {
            s = "aiHistory";
        } else if ("ai".equals(code) && itemId == DevGramCategoryActivity.ID_AI_REPLACE_EDITOR) {
            s = "replaceTelegramEditor";
        } else if ("ai".equals(code) && itemId == DevGramCategoryActivity.ID_AI_REPLACE_SUMMARIES) {
            s = "replaceTelegramSummaries";
        } else {
            s = code + (itemId > 0 ? "__" + itemId : "");
        }
        return "https://t.me/" + USERNAME + "?s=" + s;
    }

    // ---------- открытие по параметру s= ----------
    public static boolean open(String s) {
        if (s == null || s.isEmpty()) return false;
        if(s.regionMatches(true,0,"canvas__",0,8))return openCanvas(s);
        if(s.startsWith("community__")||s.startsWith("community_tag__")){
            if(!org.telegram.messenger.DevGramProfileCustomization.enabled())return false;
            try {
                boolean tag=s.startsWith("community_tag__");
                long subject=Long.parseLong(s.substring(tag?15:11));
                if(subject>=0)return false;
                BaseFragment last=LaunchActivity.getLastFragment();
                if(last==null)return false;
                long id=org.telegram.messenger.DevGramCommunityProfiles.chatId(subject);
                org.telegram.tgnet.TLRPC.Chat chat=org.telegram.messenger.DevGramCommunityProfiles.localChat(last.getCurrentAccount(),id);
                if(chat==null||!chat.creator)return false;
                // Our return route bypasses Telegram's ordinary link handler,
                // so close the verification browser explicitly before resuming.
                ArticleViewer browser=last.getArticleViewer();
                if(browser!=null)browser.close(false,true);
                if(tag){
                    long community=org.telegram.messenger.ChatObject.isChannel(chat)?id:-id;
                    if(last instanceof DevGramClanTagsActivity&&((DevGramClanTagsActivity)last).communityId()==community){
                        ((DevGramClanTagsActivity)last).refreshAfterVerification();
                        return true;
                    }
                    if(DevGramAppearanceAccess.check(last,"workshop.clans"))last.presentFragment(new DevGramClanTagsActivity(community));
                }else org.telegram.messenger.DevGramCommunityProfiles.openEditor(last,chat);
                return true;
            }catch(Exception ignored){return false;}
        }
        if(s.toLowerCase(java.util.Locale.ROOT).startsWith("workshop__")){
            if(!org.telegram.messenger.DevGramProfileCustomization.enabled())return false;
            try{long id=Long.parseLong(s.substring(10));if(id<=0)return false;BaseFragment last=LaunchActivity.getLastFragment();if(last==null)return false;last.presentFragment(DevGramWorkshopActivity.openWork(id));return true;}catch(Exception ignored){return false;}
        }
        String code = s;
        int itemId = 0;
        if ("showTranslateChatButton".equalsIgnoreCase(code)) {
            code = "general";
            itemId = DevGramCategoryActivity.ID_TRANSLATE_CHAT;
        } else if ("aiServices".equalsIgnoreCase(code)) {
            code = "ai";
            itemId = DevGramCategoryActivity.ID_AI_SERVICES;
        } else if ("aiHistory".equalsIgnoreCase(code)) {
            return openDirect(new DevGramAiHistoryActivity());
        } else if ("replaceTelegramEditor".equalsIgnoreCase(code)) {
            code = "ai";
            itemId = DevGramCategoryActivity.ID_AI_REPLACE_EDITOR;
        } else if ("replaceTelegramSummaries".equalsIgnoreCase(code)) {
            code = "ai";
            itemId = DevGramCategoryActivity.ID_AI_REPLACE_SUMMARIES;
        }
        int sep = s.indexOf("__");
        if (sep > 0) {
            code = s.substring(0, sep);
            try {
                itemId = Integer.parseInt(s.substring(sep + 2));
            } catch (Throwable ignore) {
            }
        }
        // LaunchActivity приводит s= к нижнему регистру — коды сравниваем без учёта регистра
        BaseFragment target = fragmentForCode(code.toLowerCase(java.util.Locale.US));
        BaseFragment last = LaunchActivity.getLastFragment();
        if (target == null || last == null) {
            if (last != null) {
                BulletinFactory.of(last).createErrorBulletin(
                        DevGramLocale.getString(R.string.DevGramNoSuchSetting)).show();
            }
            return false;
        }
        pendingHighlight = itemId;
        last.presentFragment(target);
        return true;
    }

    private static boolean openCanvas(String section){
        org.telegram.messenger.DevGramCanvasLink link=org.telegram.messenger.DevGramCanvasLink.parse(section);BaseFragment host=LaunchActivity.getLastFragment();if(link==null||host==null||!org.telegram.messenger.DevGramProfileCustomization.enabled())return false;int account=host.getCurrentAccount();long viewer=host.getUserConfig().getClientUserId();if(viewer<=0)return false;
        org.telegram.messenger.DevGramProfilePolicy.Callback callback=(result,error)->{if(LaunchActivity.getLastFragment()!=host||host.getParentActivity()==null||host.getUserConfig().getClientUserId()!=viewer||!org.telegram.messenger.DevGramProfileCustomization.enabled())return;org.json.JSONObject document=result==null?null:result.optJSONObject("document"),selected=null;org.json.JSONArray rows=document==null?null:document.optJSONArray("blocks");if(rows!=null)for(int i=0;i<rows.length();i++){org.json.JSONObject row=rows.optJSONObject(i);if(row!=null&&row.optString("id").equals(link.block)&&org.telegram.messenger.DevGramProfileRowSpec.type(row)==11&&(!row.optBoolean("ownOnly")||link.profile==viewer)){selected=org.telegram.messenger.DevGramProfileDocument.copy(row);break;}}if(selected==null||!org.telegram.messenger.DevGramProfilePolicy.allowed(link.profile,"profile.blocks.pixels")){BulletinFactory.of(host).createErrorBulletin(DevGramLocale.text("Полотно недоступно или удалено")).show();return;}try{selected.put("viewX",link.x).put("viewY",link.y).put("viewSpan",32).put("focusX",link.x).put("focusY",link.y);}catch(Exception ignored){}host.presentFragment(new DevGramPixelCanvasActivity(account,link.profile,selected));};
        if(link.profile==viewer)org.telegram.messenger.DevGramProfileDocument.load(account,callback);else org.telegram.messenger.DevGramNetworkQueue.postRunnable(()->{org.json.JSONObject value=null;try{value=org.telegram.messenger.DevGramProfilePolicy.http("GET","studio/"+link.profile+".json",null);}catch(Exception ignored){}org.json.JSONObject result=value;AndroidUtilities.runOnUIThread(()->callback.run(result,result==null?"network":null));});return true;
    }

    private static boolean openDirect(BaseFragment target) {
        BaseFragment last = LaunchActivity.getLastFragment();
        if (last == null || target == null) return false;
        last.presentFragment(target);
        return true;
    }

    private static BaseFragment fragmentForCode(String code) {
        switch (code) {
            case "main":       return new DevGramSettingsActivity();
            case "general":    return new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_GENERAL);
            case "ghost":      return new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_GHOST);
            case "spy":        return new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_SPY);
            case "filters":    return new DevGramFiltersActivity();
            case "appearance": return new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_APPEARANCE);
            case "chats":      return new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_CHATS);
            case "swipeactions": return new DevGramSwipeActionsActivity();
            case "appicon":    return new DevGramAppIconsActivity();
            case "supporter":  return new DevGramSupporterActivity();
            case "profileappearance": return org.telegram.messenger.DevGramProfileCustomization.enabled()?new DevGramProfileStyleActivity():null;
            case "workshop": return org.telegram.messenger.DevGramProfileCustomization.enabled()?new DevGramWorkshopActivity(new DevGramStudioActivity.Draft()):null;
            case "ai":         return new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_AI);
            case "navigation": return new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_NAVIGATION);
            case "other":      return new DevGramOtherActivity();
            case "locked":     return new DevGramLockedChatsActivity();
            default:           return null;
        }
    }

    // код экрана для категории DevGramCategoryActivity
    public static String codeForCategory(int category) {
        switch (category) {
            case DevGramCategoryActivity.CATEGORY_GHOST:      return "ghost";
            case DevGramCategoryActivity.CATEGORY_SPY:        return "spy";
            case DevGramCategoryActivity.CATEGORY_APPEARANCE: return "appearance";
            case DevGramCategoryActivity.CATEGORY_CHATS:      return "chats";
            case DevGramCategoryActivity.CATEGORY_AI:         return "ai";
            case DevGramCategoryActivity.CATEGORY_NAVIGATION: return "navigation";
            default:                                          return "general";
        }
    }

    // ---------- меню «копировать / поделиться» ----------
    public static boolean showLinkOptions(BaseFragment fragment, View anchor, String code, int itemId) {
        if (fragment == null || fragment.getParentActivity() == null) return false;
        final String link = build(code, itemId);
        ItemOptions.makeOptions(fragment, anchor)
                .add(R.drawable.msg_link2, DevGramLocale.getString(R.string.DevGramCopyLink), () -> {
                    AndroidUtilities.addToClipboard(link);
                    BulletinFactory.of(fragment).createCopyLinkBulletin().show();
                })
                .add(R.drawable.msg_share, DevGramLocale.getString(R.string.DevGramShareLink), () -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, link);
                        fragment.getParentActivity().startActivity(Intent.createChooser(intent,
                                DevGramLocale.getString(R.string.DevGramShareLink)));
                    } catch (Throwable ignore) {
                    }
                })
                .setGravity(Gravity.RIGHT)
                .show();
        return true;
    }

    // ---------- подсветка пункта после открытия по ссылке ----------
    public static void consumeHighlight(UniversalRecyclerView listView) {
        final int id = pendingHighlight;
        pendingHighlight = 0;
        if (id <= 0 || listView == null) return;
        AndroidUtilities.runOnUIThread(() -> {
            int pos = positionOf(listView, id);
            if (pos < 0) return;
            // сперва проматываем к пункту (highlightRow подсвечивает только видимые строки,
            // иначе анимация ждёт, пока пользователь сам долистает), затем подсвечиваем
            try {
                listView.layoutManager.scrollToPositionWithOffset(pos, AndroidUtilities.dp(80));
            } catch (Throwable ignore) {}
            AndroidUtilities.runOnUIThread(() -> listView.highlightRow(() -> positionOf(listView, id)), 160);
        }, 240);
    }

    private static int positionOf(UniversalRecyclerView listView, int id) {
        if (listView.adapter == null) return -1;
        for (int i = 0; i < listView.adapter.getItemCount(); i++) {
            UItem it = listView.adapter.getItem(i);
            if (it != null && it.id == id) return i;
        }
        return -1;
    }
}
