package org.telegram.ui.Components;

import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import org.json.JSONObject;
import org.telegram.ui.ActionBar.Theme;
import java.util.function.Supplier;

/** Bounded profile-only palette; never mutates the application's shared theme. */
public final class DevGramProfilePalette {
    private DevGramProfilePalette() {}
    public static final String[] NAMES={"key_windowBackgroundWhite","key_windowBackgroundGray","key_divider","key_listSelector","key_windowBackgroundWhiteBlackText","key_windowBackgroundWhiteGrayText","key_windowBackgroundWhiteGrayText2","key_windowBackgroundWhiteGrayText3","key_windowBackgroundWhiteGrayText4","key_windowBackgroundWhiteValueText","key_windowBackgroundWhiteBlueText","key_windowBackgroundWhiteBlueText2","key_windowBackgroundWhiteBlueText4","key_windowBackgroundWhiteLinkText","key_chat_messageLinkIn","key_chat_linkSelectBackground","key_actionBarDefault","key_actionBarDefaultIcon","key_actionBarDefaultTitle","key_actionBarDefaultSubtitle","key_actionBarDefaultSelector","key_avatar_text","key_profile_title","key_profile_status","key_windowBackgroundWhiteBlueHeader","key_chats_name","key_chats_message","key_chats_nameMessage_threeLines","key_chats_attachMessage","key_chats_actionMessage","key_glass_targetMainTopPanel","key_profile_tabSelectedText","key_profile_tabText","key_profile_tabSelectedLine","key_profile_tabSelector","key_windowBackgroundWhiteGrayIcon"};
    public static final String[] LABELS={"Фон блоков","Фон страницы","Разделители","Подсветка нажатия","Основной текст","Подписи","Подписи под значениями","Второстепенный текст","Пояснения под блоками","Значения","Выделенный текст","Выделенный текст, вариант","Кнопки действий","Ссылки в пояснениях","Ссылки в тексте","Выделение ссылки","Панель сверху","Значки панели сверху","Заголовок панели","Подзаголовок панели","Нажатие на панели","Текст на аватаре","Имя в профиле","Статус в профиле","Заголовки разделов","Название канала","Текст публикации","Автор публикации","Подписи вложений","Служебный текст канала","Фон панелей профиля","Активная вкладка","Неактивная вкладка","Выделение активной вкладки","Нажатие на вкладку","Значки строк"};
    public static final int[] KEYS={Theme.key_windowBackgroundWhite,Theme.key_windowBackgroundGray,Theme.key_divider,Theme.key_listSelector,Theme.key_windowBackgroundWhiteBlackText,Theme.key_windowBackgroundWhiteGrayText,Theme.key_windowBackgroundWhiteGrayText2,Theme.key_windowBackgroundWhiteGrayText3,Theme.key_windowBackgroundWhiteGrayText4,Theme.key_windowBackgroundWhiteValueText,Theme.key_windowBackgroundWhiteBlueText,Theme.key_windowBackgroundWhiteBlueText2,Theme.key_windowBackgroundWhiteBlueText4,Theme.key_windowBackgroundWhiteLinkText,Theme.key_chat_messageLinkIn,Theme.key_chat_linkSelectBackground,Theme.key_actionBarDefault,Theme.key_actionBarDefaultIcon,Theme.key_actionBarDefaultTitle,Theme.key_actionBarDefaultSubtitle,Theme.key_actionBarDefaultSelector,Theme.key_avatar_text,Theme.key_profile_title,Theme.key_profile_status,Theme.key_windowBackgroundWhiteBlueHeader,Theme.key_chats_name,Theme.key_chats_message,Theme.key_chats_nameMessage_threeLines,Theme.key_chats_attachMessage,Theme.key_chats_actionMessage,Theme.key_glass_targetMainTopPanel,Theme.key_profile_tabSelectedText,Theme.key_profile_tabText,Theme.key_profile_tabSelectedLine,Theme.key_profile_tabSelector,Theme.key_windowBackgroundWhiteGrayIcon};
    public static int group(int i){return i>=35?6:i>=30?5:i>=25?4:i<4||i>=16&&i<=20?0:i>=10&&i<=15?2:i==21?3:1;}
    public static final String[] GROUPS={"Поверхности","Текст","Ссылки","Прочее","Канал в профиле","Вкладки медиа","Значки строк"};
    /** Profile-only colors must not become the theme of a destination screen. */
    public static Theme.ResourcesProvider outside(Theme.ResourcesProvider provider){
        while(provider instanceof Provider)provider=((Provider)provider).base;
        return provider;
    }
    public static class Provider implements Theme.ResourcesProvider {
        private final Theme.ResourcesProvider base;private final Supplier<JSONObject> source;
        private final Paint dividerPaint = new Paint();
        public Provider(Theme.ResourcesProvider base,Supplier<JSONObject> source){this.base=base;this.source=source;}
        public Integer customCardColor(){JSONObject d=source.get();if(d.optInt("cardEnabled",d.has("cardColor")?1:0)!=1)return null;int alpha=Math.round(Math.max(0,Math.min(100,d.optInt("cardAlpha",100)))*2.55f);return alpha<<24|(d.optInt("cardColor",0x293450)&0xffffff);}
        public int actionContentColor(int fallback){
            JSONObject d=source.get();
            if(d.optInt("buttonTextEnabled")==1)return 0xff000000|(d.optInt("buttonTextColor",0xffffff)&0xffffff);
            if(hasColorOverride(Theme.key_windowBackgroundWhiteBlueText4))return getColor(Theme.key_windowBackgroundWhiteBlueText4);
            if(d.optInt("profileTextEnabled")==1)return 0xff000000|d.optInt("profileTextColor",0xffffff);
            return fallback;
        }
        public boolean hasColorOverride(int key){JSONObject palette=source.get().optJSONObject("profilePalette");if(palette==null)return false;for(int i=0;i<KEYS.length;i++)if(KEYS[i]==key)return palette.has(NAMES[i]);return false;}
        public static Integer blockTextColor(JSONObject doc){
            return doc!=null&&doc.optInt("cardTextEnabled")==1?0xff000000|(doc.optInt("cardTextColor",0xffffff)&0xffffff):null;
        }
        private static boolean blockTextKey(int key){
            return key==Theme.key_windowBackgroundWhiteBlackText||key==Theme.key_windowBackgroundWhiteGrayText
                    ||key==Theme.key_windowBackgroundWhiteGrayText2||key==Theme.key_windowBackgroundWhiteGrayText3
                    ||key==Theme.key_windowBackgroundWhiteGrayText4||key==Theme.key_windowBackgroundWhiteValueText
                    ||key==Theme.key_windowBackgroundWhiteBlueText||key==Theme.key_windowBackgroundWhiteBlueText2
                    ||key==Theme.key_windowBackgroundWhiteBlueHeader||key==Theme.key_windowBackgroundWhiteLinkText
                    ||key==Theme.key_chat_messageLinkIn||key==Theme.key_chats_name||key==Theme.key_chats_message
                    ||key==Theme.key_chats_nameMessage_threeLines||key==Theme.key_chats_attachMessage||key==Theme.key_chats_actionMessage;
        }
        public int getColor(int key){return color(key,false);}public int getCurrentColor(int key){return color(key,true);}
        private int color(int key,boolean current){
            int slot=key==Theme.key_chats_message_threeLines?Theme.key_chats_message:key;
            JSONObject doc=source.get(),palette=doc.optJSONObject("profilePalette");
            for(int i=0;i<KEYS.length;i++)if(KEYS[i]==slot){
                // Explicit element colors remain editable while a shared color is enabled.
                if(palette!=null&&palette.has(NAMES[i]))return palette.optInt(NAMES[i]);
                Integer blockColor=blockTextColor(doc);
                if(blockColor!=null&&blockTextKey(slot))return blockColor;
                if(doc.optInt("profileTextEnabled")==1&&(group(i)==1||group(i)==4||slot==Theme.key_profile_tabSelectedText||slot==Theme.key_profile_tabText||slot==Theme.key_windowBackgroundWhiteGrayIcon))return 0xff000000|doc.optInt("profileTextColor",0xffffff);
                break;
            }
            return base==null?Theme.getColor(key):current?base.getCurrentColor(key):base.getColor(key);
        }
        public Drawable getDrawable(String key){return base==null?null:base.getDrawable(key);}public Paint getPaint(String key){if(Theme.key_paint_divider.equals(key)){dividerPaint.setColor(getColor(Theme.key_divider));return dividerPaint;}return base==null?Theme.getThemePaint(key):base.getPaint(key);}public boolean isDark(){return base==null?Theme.isCurrentThemeDark():base.isDark();}public boolean hasGradientService(){return base!=null&&base.hasGradientService();}public void applyServiceShaderMatrix(int w,int h,float x,float y){if(base==null)Theme.applyServiceShaderMatrix(w,h,x,y);else base.applyServiceShaderMatrix(w,h,x,y);}public android.graphics.ColorFilter getAnimatedEmojiColorFilter(){return base==null?Theme.chat_animatedEmojiTextColorFilter:base.getAnimatedEmojiColorFilter();}public boolean isCompactMode(){return base==null?Theme.isCompactMode(null):base.isCompactMode();}
    }

    /** One provider per recycled cell; binding a stable row key cannot recolor its neighbours. */
    public static final class RowProvider extends Provider {
        private final Supplier<JSONObject> document;
        private String rowKey;
        public RowProvider(Theme.ResourcesProvider base,Supplier<JSONObject> document){super(base,document);this.document=document;}
        public void bind(String key){rowKey=key;}
        public JSONObject style(){JSONObject rows=document.get().optJSONObject("rowColors");return rows==null||rowKey==null?null:rows.optJSONObject(rowKey);}
        public Integer override(String field){JSONObject style=style();return style!=null&&style.has(field)?0xff000000|(style.optInt(field)&0xffffff):null;}
        private Integer override(int key){
            if(key==Theme.key_windowBackgroundWhite)return override("background");
            if(key==Theme.key_windowBackgroundWhiteGrayIcon)return override("icon");
            if(Provider.blockTextKey(key)||key==Theme.key_chats_message_threeLines||key==Theme.key_windowBackgroundWhiteBlueText4)return override("text");
            return null;
        }
        @Override public int getColor(int key){Integer color=override(key);return color==null?super.getColor(key):color;}
        @Override public int getCurrentColor(int key){Integer color=override(key);return color==null?super.getCurrentColor(key):color;}
        @Override public Integer customCardColor(){Integer color=override("background");return color==null?super.customCardColor():color;}
    }
    public static Integer rowColor(Theme.ResourcesProvider provider,String field){return provider instanceof RowProvider?((RowProvider)provider).override(field):null;}
    public static int rowColor(Theme.ResourcesProvider provider,String field,int fallback){Integer color=rowColor(provider,field);return color==null?fallback:color;}
    /** Decorative defaults inherit the shared profile, never another slot in the same row. */
    public static int inheritedColor(Theme.ResourcesProvider provider,int key){
        while(provider instanceof RowProvider)provider=((Provider)provider).base;
        return provider==null?Theme.getColor(key):provider.getColor(key);
    }
}
