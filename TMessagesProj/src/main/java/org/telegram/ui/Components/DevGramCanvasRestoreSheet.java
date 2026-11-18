package org.telegram.ui.Components;

import android.view.View;
import android.widget.*;
import java.util.UUID;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.ui.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Cells.TextSettingsCell;

/** The reference recovery picker: only detached drawings, with move semantics. */
public final class DevGramCanvasRestoreSheet {
    private final BaseFragment host;
    private final DevGramStudioActivity.Draft draft;
    private final JSONObject block;
    private final Runnable changed;
    private final long viewer,profile;
    private boolean busy;
    private DevGramCanvasRestoreSheet(BaseFragment host,DevGramStudioActivity.Draft draft,JSONObject block,Runnable changed){this.host=host;this.draft=draft;this.block=block;this.changed=changed;viewer=host.getUserConfig().getClientUserId();profile=draft.target(host.getCurrentAccount());}
    public static void show(BaseFragment host,DevGramStudioActivity.Draft draft,JSONObject block,Runnable changed){new DevGramCanvasRestoreSheet(host,draft,block,changed).load();}
    private String t(String text){return DevGramLocale.text(text);}
    private boolean valid(){return host.getParentActivity()!=null&&host.getUserConfig().getClientUserId()==viewer&&DevGramProfileCustomization.enabled()&&DevGramCommunityProfiles.owner(profile)==viewer&&DevGramProfilePolicy.allowed(profile,"profile.blocks.pixels");}
    private JSONObject fields(String action){JSONArray standing=new JSONArray();JSONArray rows=draft.document.optJSONArray("blocks");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&DevGramProfileRowSpec.type(row)==11)standing.put(row.optString("id"));}return DevGramStudioActivity.json("action",action,"profile",profile,"standing",standing);}
    private void failure(){if(valid())BulletinFactory.of(host).createErrorBulletin(t("Не удалось вернуть полотно. Обновите список и попробуйте снова.")).show();}
    private void load(){if(!valid()||busy)return;busy=true;DevGramProfilePolicy.request(host.getCurrentAccount(),"studio/rows/canvases",fields("list"),(result,error)->{busy=false;if(!valid())return;if(result==null){failure();return;}build(result.optJSONArray("canvases"));});}
    private void build(JSONArray items){LinearLayout body=DevGramStudioControls.column(host.getParentActivity());BottomSheet sheet=new BottomSheet(host.getParentActivity(),false,host.getResourceProvider());int count=0;boolean occupied=false;
        if(items!=null)for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;if(item.optString("block").equals(block.optString("id")))occupied=item.optInt("dots")>0;if(item.optBoolean("standing")||item.optInt("dots")<=0)continue;count++;TextSettingsCell row=new TextSettingsCell(host.getParentActivity(),host.getResourceProvider());long elapsed=Math.max(0,System.currentTimeMillis()/1000-item.optLong("ts"));String when=elapsed<3600?t("только что"):elapsed<86400?elapsed/3600+t(" ч назад"):elapsed/86400+t(" дн назад");row.setTextAndValue(t("Точек: ")+item.optInt("dots"),when,true);boolean[] overwrite={false};row.setOnClickListener(v->{if(!valid()||busy)return;sheet.dismiss();JSONObject request=fields("adopt");try{request.put("source",item.optString("block")).put("block",block.optString("id")).put("nonce",UUID.randomUUID().toString());}catch(Exception ignored){}Runnable move=()->adopt(request);if(overwrite[0])host.showDialog(new AlertDialog.Builder(host.getParentActivity(),host.getResourceProvider()).setTitle(t("Заменить рисунок на этом блоке?")).setMessage(t("Текущий рисунок будет заменён выбранным полотном.")).setPositiveButton(t("Вернуть"),(d,w)->{try{request.put("replace",true);}catch(Exception ignored){}move.run();}).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).create());else move.run();});row.setTag(overwrite);body.addView(row);}
        for(int i=0;i<body.getChildCount();i++){Object tag=body.getChildAt(i).getTag();if(tag instanceof boolean[])((boolean[])tag)[0]=occupied;}
        TextView note=DevGramStudioControls.text(host.getParentActivity(),count==0?"Потерянных полотен нет. Здесь появляются те, чей блок удалили: рисунок остаётся на сервере, и его можно вернуть на новый блок.":"Выбранное полотно переедет на этот блок вместе со всеми точками. Прежнее место освободится: рисунок один, и лежать в двух местах он не может.",14,false,host.getResourceProvider());note.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText,host.getResourceProvider()));note.setPadding(AndroidUtilities.dp(22),AndroidUtilities.dp(16),AndroidUtilities.dp(22),AndroidUtilities.dp(24));body.addView(note);ScrollView scroll=new ScrollView(host.getParentActivity());scroll.addView(body);sheet.setTitle(t("Вернуть полотно"));sheet.setCustomView(scroll);sheet.fixNavigationBarKey(Theme.key_dialogBackground);host.showDialog(sheet);
    }
    private void adopt(JSONObject request){if(!valid()||busy)return;busy=true;DevGramProfilePolicy.request(host.getCurrentAccount(),"studio/rows/canvases",request,(result,error)->{busy=false;if(!valid())return;if(result==null){failure();return;}if(changed!=null)changed.run();BulletinFactory.of(host).createSimpleBulletin(R.raw.contact_check,t("Полотно вернулось")).show();});}
}
