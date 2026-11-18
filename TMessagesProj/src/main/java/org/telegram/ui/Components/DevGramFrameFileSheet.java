package org.telegram.ui.Components;

import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.devgram.frame.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/** Reference frame-file window: live preview, author, source, install and private favorites. */
public final class DevGramFrameFileSheet {
    private final BaseFragment host;
    private final FrameArchive.Imported archive;
    private final Consumer<JSONObject> draft;
    private final long viewer;
    private final ArrayList<File> temporary=new ArrayList<>();
    private final Map<String,String> mapping=new HashMap<>();
    private BottomSheet sheet;
    private TextView install,favorite,status;
    private DevGramStudioPreview preview;
    private boolean closed,busy,kept,saved;
    private final Runnable policy=()->{if(!valid()&&sheet!=null)sheet.dismiss();};
    private DevGramFrameFileSheet(BaseFragment host,FrameArchive.Imported archive,Consumer<JSONObject> draft){this.host=host;this.archive=archive;this.draft=draft;viewer=host.getUserConfig().getClientUserId();}
    public static void show(BaseFragment host,File file){if(!access(host))return;long viewer=host.getUserConfig().getClientUserId();DevGramNetworkQueue.postRunnable(()->{FrameArchive.Imported archive=null;try(InputStream input=new FileInputStream(file)){if(file.length()>FrameArchive.MAX)throw new IOException();archive=FrameArchive.read(input);}catch(Exception|OutOfMemoryError ignored){}FrameArchive.Imported ready=archive;AndroidUtilities.runOnUIThread(()->{if(host.getParentActivity()==null||host.getUserConfig().getClientUserId()!=viewer||LaunchActivity.getSafeLastFragment()!=host)return;if(ready==null){BulletinFactory.of(host).createErrorBulletin(DevGramLocale.text("Файл рамки не читается")).show();return;}show(host,ready,null);});});}
    public static void show(BaseFragment host,FrameArchive.Imported archive,Consumer<JSONObject> draft){if(access(host))new DevGramFrameFileSheet(host,archive,draft).prepare();}
    private static boolean access(BaseFragment host){return host!=null&&host.getParentActivity()!=null&&DevGramProfileCustomization.enabled()&&DevGramAppearanceAccess.check(host,"profile.import")&&DevGramAppearanceAccess.check(host,"avatar.nodes");}
    private boolean valid(){return !closed&&host.getParentActivity()!=null&&host.getUserConfig().getClientUserId()==viewer&&DevGramProfileCustomization.enabled()&&DevGramProfilePolicy.allowed(viewer,"profile.import")&&DevGramProfilePolicy.allowed(viewer,"avatar.nodes");}
    private String t(String text){return DevGramLocale.text(text);}
    private void prepare(){DevGramNetworkQueue.postRunnable(()->{boolean ok=true;try{for(Map.Entry<String,byte[]> e:archive.textures.entrySet()){if(!valid()){cleanup();return;}File file=ImageLoader.getHttpFilePath(DevGramProfileTextures.imageUrl(e.getKey()),e.getKey().substring(e.getKey().lastIndexOf('.')+1));if(!file.exists()){File parent=file.getParentFile();if(parent!=null)parent.mkdirs();try(FileOutputStream output=new FileOutputStream(file)){output.write(e.getValue());}temporary.add(file);}}}catch(Exception ignored){ok=false;}boolean ready=ok;AndroidUtilities.runOnUIThread(()->{if(!valid()||LaunchActivity.getSafeLastFragment()!=host){cleanup();return;}if(ready)open();else {cleanup();BulletinFactory.of(host).createErrorBulletin(t("Картинки рамки не сохранились")).show();}});});}
    private void cleanup(){DevGramNetworkQueue.postRunnable(()->{for(File file:temporary)file.delete();});}
    private void open(){LinearLayout body=DevGramStudioControls.column(host.getParentActivity());preview=new DevGramStudioPreview(host.getParentActivity(),host.getCurrentAccount(),viewer);preview.setFrameOnly(true);preview.set(DevGramStudioActivity.json("frameGraph",json(archive.graph.encode())),DevGramProfileStyle.raw(viewer));body.addView(preview,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(200)));
        if(!archive.author.trim().isEmpty()){TextView author=DevGramStudioControls.text(host.getParentActivity(),"",14,true,host.getResourceProvider());author.setText(archive.author);author.setGravity(Gravity.CENTER);author.setPadding(0,0,0,AndroidUtilities.dp(8));if(archive.author.matches("@[A-Za-z0-9_]{5,32}"))author.setOnClickListener(v->Browser.openUrl(host.getParentActivity(),"https://t.me/"+archive.author.substring(1)));body.addView(author);}
        status=DevGramStudioControls.text(host.getParentActivity(),"",13,false,host.getResourceProvider());int count=FrameNodeBuild.spec(archive.graph).layers().size();String noun=count%10==1&&count%100!=11?"украшение":count%10>=2&&count%10<=4&&(count%100<12||count%100>14)?"украшения":"украшений";status.setText(count+" "+t(noun)+t(" · нынешняя рамка сохранится в проектах"));status.setGravity(Gravity.CENTER);status.setPadding(AndroidUtilities.dp(24),0,AndroidUtilities.dp(24),AndroidUtilities.dp(16));body.addView(status);
        install=button(body,"Установить",true);install.setOnClickListener(v->send(false));
        if(archive.source.matches("tg://work\\?id=[A-Za-z0-9_-]{1,80}")||archive.source.startsWith("https://api.devgram.space/")||archive.source.startsWith("https://devgram.space/")){TextView source=button(body,"Источник",false);source.setOnClickListener(v->Browser.openUrl(host.getParentActivity(),archive.source));}
        favorite=button(body,"В избранное",false);favorite.setOnClickListener(v->send(true));sheet=new BottomSheet.Builder(host.getParentActivity(),false,host.getResourceProvider()).setTitle(archive.name.trim().isEmpty()?t("Рамка из файла"):archive.name).setCustomView(body).create();sheet.fixNavigationBarKey(Theme.key_windowBackgroundWhite);DevGramProfilePolicy.listen(policy);host.showDialog(sheet,d->{closed=true;DevGramProfilePolicy.unlisten(policy);preview.release();if(!kept)cleanup();});
    }
    private TextView button(LinearLayout body,String label,boolean primary){TextView text=DevGramStudioControls.text(host.getParentActivity(),label,15,true,host.getResourceProvider());text.setGravity(Gravity.CENTER);text.setTextColor(Theme.getColor(primary?Theme.key_featuredStickers_buttonText:Theme.key_windowBackgroundWhiteBlueText,host.getResourceProvider()));if(primary)text.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10),Theme.getColor(Theme.key_featuredStickers_addButton,host.getResourceProvider())));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(48));p.setMargins(AndroidUtilities.dp(16),0,AndroidUtilities.dp(16),AndroidUtilities.dp(8));body.addView(text,p);return text;}
    private JSONObject json(String encoded){try{return new JSONObject(encoded);}catch(Exception e){return new JSONObject();}}
    private void send(boolean save){if(!valid()||busy||save&&saved||!DevGramAppearanceAccess.check(host,"avatar.projects",sheet==null?null:sheet.getContainer()))return;busy=true;install.setEnabled(false);favorite.setEnabled(false);status.setText(t("Подготавливаю рамку…"));upload(new ArrayList<>(archive.textures.entrySet()),0,0,save);}
    private void fail(String error){busy=false;if(!valid())return;install.setEnabled(true);favorite.setEnabled(!saved);status.setText(t("project_limit".equals(error)?"В проектах нет места. Удалите ненужные рамки и повторите установку.":"Не удалось установить рамку. Проверьте доступ и соединение."));}
    private void upload(ArrayList<Map.Entry<String,byte[]>> assets,int at,long decoded,boolean save){if(!valid())return;if(at==assets.size()){JSONObject document=DevGramStudioActivity.json("v",1,"frameGraph",json(archive.graph.swap(mapping).encode()));String title=archive.name.trim().isEmpty()?t("Рамка из файла"):archive.name.substring(0,Math.min(80,archive.name.length()));DevGramProfilePolicy.request(host.getCurrentAccount(),"studio/frames/file",DevGramStudioActivity.json("action",save?"favorite":draft==null?"install":"draft","title",title,"author",archive.author,"source",archive.source,"document",document),(result,error)->{if(result!=null&&!save&&draft==null&&host.getUserConfig().getClientUserId()==viewer)DevGramProfileDocument.acceptWorkshop(host.getCurrentAccount(),result);if(!valid())return;if(result==null){fail(error);return;}kept=true;busy=false;if(save){saved=true;favorite.setText(t("Отложено"));favorite.setEnabled(false);install.setEnabled(true);status.setText(t("Рамка сохранена в избранном"));}else{if(draft!=null)draft.accept(result.optJSONObject("document"));sheet.dismiss();BulletinFactory.of(host).createSimpleBulletin(R.raw.contact_check,t("Рамка поставлена")).show();}});return;}
        Map.Entry<String,byte[]> entry=assets.get(at);DevGramProfilePolicy.request(host.getCurrentAccount(),"studio/textures/upload",DevGramStudioActivity.json("kind","frame","data",android.util.Base64.encodeToString(entry.getValue(),android.util.Base64.NO_WRAP)),(result,error)->{if(!valid())return;if(result==null){fail(error);return;}int frames=result.optInt("frames",1);long size=(long)result.optInt("width")*result.optInt("height")*4*(frames>1?frames+3L:1);if(size<=0||decoded+size>64L*1024*1024){fail("frame_size");return;}mapping.put(entry.getKey(),result.optString("source"));upload(assets,at+1,decoded+size,save);});
    }
}
