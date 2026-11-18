package org.telegram.ui.Components;

import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import org.json.JSONObject;
import org.telegram.ui.ActionBar.Theme;
import java.util.function.Supplier;

/** Bounded profile-only palette; never mutates the application's shared theme. */
public final class DevGramProfilePalette {
    private DevGramProfilePalette() {}
    public static final String[] NAMES={"key_windowBackgroundWhite","key_windowBackgroundGray","key_divider","key_listSelector","key_windowBackgroundWhiteBlackText","key_windowBackgroundWhiteGrayText","key_windowBackgroundWhiteGrayText2","key_windowBackgroundWhiteGrayText3","key_windowBackgroundWhiteGrayText4","key_windowBackgroundWhiteValueText","key_windowBackgroundWhiteBlueText","key_windowBackgroundWhiteBlueText2","key_windowBackgroundWhiteBlueText4","key_windowBackgroundWhiteLinkText","key_chat_messageLinkIn","key_chat_linkSelectBackground","key_actionBarDefault","key_actionBarDefaultIcon","key_actionBarDefaultTitle","key_actionBarDefaultSubtitle","key_actionBarDefaultSelector","key_avatar_text","key_profile_title","key_profile_status","key_windowBackgroundWhiteBlueHeader","key_chats_name","key_chats_message","key_chats_nameMessage_threeLines","key_chats_attachMessage","key_chats_actionMessage","key_glass_targetMainTopPanel","key_profile_tabSelectedText","key_profile_tabText","key_profile_tabSelectedLine","key_profile_tabSelector"};
    public static final String[] LABELS={"Фон блоков","Фон страницы","Разделители","Подсветка нажатия","Основной текст","Подписи","Подписи под значениями","Второстепенный текст","Пояснения под блоками","Значения","Выделенный текст","Выделенный текст, вариант","Кнопки действий","Ссылки в пояснениях","Ссылки в тексте","Выделение ссылки","Панель сверху","Значки панели сверху","Заголовок панели","Подзаголовок панели","Нажатие на панели","Текст на аватаре","Имя в профиле","Статус в профиле","Заголовки разделов","Название канала","Текст публикации","Автор публикации","Подписи вложений","Служебный текст канала","Фон панелей профиля","Активная вкладка","Неактивная вкладка","Выделение активной вкладки","Нажатие на вкладку"};
    public static final int[] KEYS={Theme.key_windowBackgroundWhite,Theme.key_windowBackgroundGray,Theme.key_divider,Theme.key_listSelector,Theme.key_windowBackgroundWhiteBlackText,Theme.key_windowBackgroundWhiteGrayText,Theme.key_windowBackgroundWhiteGrayText2,Theme.key_windowBackgroundWhiteGrayText3,Theme.key_windowBackgroundWhiteGrayText4,Theme.key_windowBackgroundWhiteValueText,Theme.key_windowBackgroundWhiteBlueText,Theme.key_windowBackgroundWhiteBlueText2,Theme.key_windowBackgroundWhiteBlueText4,Theme.key_windowBackgroundWhiteLinkText,Theme.key_chat_messageLinkIn,Theme.key_chat_linkSelectBackground,Theme.key_actionBarDefault,Theme.key_actionBarDefaultIcon,Theme.key_actionBarDefaultTitle,Theme.key_actionBarDefaultSubtitle,Theme.key_actionBarDefaultSelector,Theme.key_avatar_text,Theme.key_profile_title,Theme.key_profile_status,Theme.key_windowBackgroundWhiteBlueHeader,Theme.key_chats_name,Theme.key_chats_message,Theme.key_chats_nameMessage_threeLines,Theme.key_chats_attachMessage,Theme.key_chats_actionMessage,Theme.key_glass_targetMainTopPanel,Theme.key_profile_tabSelectedText,Theme.key_profile_tabText,Theme.key_profile_tabSelectedLine,Theme.key_profile_tabSelector};
    public static int group(int i){return i>=30?5:i>=25?4:i<4||i>=16&&i<=20?0:i>=10&&i<=15?2:i==21?3:1;}
    public static final String[] GROUPS={"Поверхности","Текст","Ссылки","Прочее","Канал в профиле","Вкладки медиа"};
    /** Profile-only colors must not become the theme of a destination screen. */
    public static Theme.ResourcesProvider outside(Theme.ResourcesProvider provider){
        while(provider instanceof Provider)provider=((Provider)provider).base;
        return provider;
    }
    public static final class Provider implements Theme.ResourcesProvider {
        private final Theme.ResourcesProvider base;private final Supplier<JSONObject> source;
        public Provider(Theme.ResourcesProvider base,Supplier<JSONObject> source){this.base=base;this.source=source;}
        public boolean hasColorOverride(int key){JSONObject palette=source.get().optJSONObject("profilePalette");if(palette==null)return false;for(int i=0;i<KEYS.length;i++)if(KEYS[i]==key)return palette.has(NAMES[i]);return false;}
        public int getColor(int key){return color(key,false);}public int getCurrentColor(int key){return color(key,true);}
        private int color(int key,boolean current){int slot=key==Theme.key_chats_message_threeLines?Theme.key_chats_message:key;JSONObject doc=source.get();JSONObject palette=doc.optJSONObject("profilePalette");for(int i=0;i<KEYS.length;i++)if(KEYS[i]==slot){if(doc.optInt("profileTextEnabled")==1&&(group(i)==1||group(i)==4||slot==Theme.key_profile_tabSelectedText||slot==Theme.key_profile_tabText))return 0xff000000|doc.optInt("profileTextColor",0xffffff);if(palette!=null&&palette.has(NAMES[i]))return palette.optInt(NAMES[i]);break;}return base==null?Theme.getColor(key):current?base.getCurrentColor(key):base.getColor(key);}
        public Drawable getDrawable(String key){return base==null?null:base.getDrawable(key);}public Paint getPaint(String key){return base==null?Theme.getThemePaint(key):base.getPaint(key);}public boolean isDark(){return base==null?Theme.isCurrentThemeDark():base.isDark();}public boolean hasGradientService(){return base!=null&&base.hasGradientService();}public void applyServiceShaderMatrix(int w,int h,float x,float y){if(base==null)Theme.applyServiceShaderMatrix(w,h,x,y);else base.applyServiceShaderMatrix(w,h,x,y);}public android.graphics.ColorFilter getAnimatedEmojiColorFilter(){return base==null?Theme.chat_animatedEmojiTextColorFilter:base.getAnimatedEmojiColorFilter();}public boolean isCompactMode(){return base==null?Theme.isCompactMode(null):base.isCompactMode();}
    }
}
