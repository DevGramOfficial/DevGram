package org.telegram.messenger;

import org.json.*;

/** Native row types and conditional groups from the supplied plugin. */
public final class DevGramProfileRowSpec {
    private DevGramProfileRowSpec() {}
    public static final String[] TITLES={"Ссылка с иконкой","Текст с подписью","Заголовок раздела","Промежуток","Примечание","Пункт с иконкой","Текст без фона","Эмодзи-строка","Переключатель","Счётчик нажатий","Изображение","Пиксельное полотно","Интеграция"};
    public static final int[][] GROUPS={{1,6,3},{0,8,9},{10,11,12}};
    public static final String[] GROUP_NAMES={"Текст и разделы","Ссылки и действия","Медиа и сервисы"};
    public static final String[] ACTIONS={"Ничего","Открыть ссылку","Скопировать","Поделиться","Открыть чат"};
    public static final String[] SERVICES={"Last.fm","GitHub","Steam","Яндекс.Музыка","Spotify","SoundCloud","","Discord","Свой плеер"};
    public static final String[] PROVIDERS={"lastfm","github","steam","yandex","spotify","soundcloud","","discord","activity"};
    public static final String[][] PARTS={{"Сейчас слушаю","Всего прослушиваний","Любимый исполнитель","Любимый альбом"},{"Репозитории","Звёзды","Подписчики","Подписки","На GitHub с"},{"Сейчас играет","В Steam с","Часов за две недели","Имя в Steam","Уровень Steam","Игр в библиотеке","Часов всего","Последняя игра"},{"Сейчас слушаю","Последний понравившийся трек","Понравившихся треков","Плейлисты"},{"Сейчас слушаю","Последний трек","Любимый исполнитель","Подписчиков"},{"Последний трек","Подписчиков","Треков","Лайков"},{},{"Профиль Discord","Статус","Активность","Пользовательский статус","Сейчас слушает","В Discord с","Значки","Количество серверов","Тег сервера"},{"Сейчас слушаю"}};
    public static String[] labels(){String[] values=new String[TITLES.length];for(int i=0;i<values.length;i++)values[i]=DevGramLocale.text(TITLES[i]);return values;}
    public static int type(JSONObject row){if(row.has("rowType"))return Math.max(0,Math.min(12,row.optInt("rowType")));switch(row.optString("type")){case "link":return 0;case "image":return 10;case "music":return 12;case "code":return 6;default:return 1;}}
    public static String feature(JSONObject row){return type(row)==10&&row.optInt("mediaType")==1?"profile.blocks.media.animated":feature(type(row));}
    public static String feature(int type){return type==9?"profile.blocks.counter":type==11?"profile.blocks.pixels":type==10?"profile.blocks.media":type==12?"profile.integrations":"profile.blocks";}
    public static boolean clickable(int type){return type!=2&&type!=3&&type!=4&&type!=6;}
    public static boolean shows(String group,int type){
        switch(group){
            case "title":return type!=4&&type!=6&&type!=10&&type!=3;
            case "value":return type!=12&&type!=4&&type!=6&&type!=10&&type!=11&&type!=2&&type!=3;
            case "text":return type==4||type==6;
            case "icon":return type==0||type==5||type==7||type==9;
            case "iconBack":return type==5;
            case "linkOptions":return type==0||type==5;
            case "actions":return type!=11&&type!=12&&clickable(type);
            case "divider":return type!=3&&type!=12;
            case "media":return type==10;
            case "integration":return type==12;
            case "value_color":return type==12||shows("value",type);
            case "colors":return shows("title",type)||shows("value",type)||shows("icon",type)||shows("iconBack",type);
            default:return shows("title",type)||shows("value",type)||shows("text",type)||shows("icon",type);
        }
    }
    public static boolean complete(JSONObject row){int type=type(row);if(type==3||type==11)return true;if(type==4||type==6)return !row.optString("text").trim().isEmpty();if(type==10)return !row.optString("url").isEmpty();if(type==12)return row.optInt("service")>=0&&row.optInt("service")<SERVICES.length&&row.optInt("service")!=6&&row.optString("url").matches("[A-Za-z0-9_.@-]{1,80}");if(type==0)return !row.optString("title").trim().isEmpty()&&!row.optString("url").trim().isEmpty();return !row.optString("title").trim().isEmpty();}
    public static JSONObject create(){try{return new JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("type","link").put("rowType",0).put("title","").put("text","").put("url","").put("action",1).put("longAction",0).put("showLink",true).put("divider",true).put("radius",16).put("viewSpan",32).put("height",180);}catch(Exception e){return new JSONObject();}}
    public static boolean savedCanvas(JSONObject document,JSONObject row){
        if(document==null||row==null||type(row)!=11)return false;
        String id=row.optString("id");if(id.isEmpty())return false;
        JSONArray blocks=document.optJSONArray("blocks");if(blocks==null)return false;
        for(int i=0;i<blocks.length();i++){JSONObject saved=blocks.optJSONObject(i);if(saved!=null&&id.equals(saved.optString("id"))&&type(saved)==11)return true;}
        return false;
    }
    public static void type(JSONObject row,int type){try{row.put("rowType",type).put("type",type==10?"image":type==12?"music":"text");}catch(Exception ignored){}}
    public static int icon(String name){if(name==null||!name.matches("[a-z][a-z0-9_]{0,63}"))return 0;return ApplicationLoader.applicationContext.getResources().getIdentifier(name,"drawable",ApplicationLoader.applicationContext.getPackageName());}
}
