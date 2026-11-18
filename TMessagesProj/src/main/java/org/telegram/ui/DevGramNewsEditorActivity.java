package org.telegram.ui;

import android.content.Context;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import org.telegram.ui.iv.RichEditor;
import java.util.ArrayList;
import java.util.UUID;

/** Native publishing desk; writing uses the actual Telegram RichEditor without a web service. */
public final class DevGramNewsEditorActivity extends DevGramSettingsFragment {
    private long viewer;
    private boolean closed, busy, more;
    private int offset;
    private String error;
    private JSONArray articles = new JSONArray();
    private UniversalRecyclerView list;
    private final Runnable policy = () -> { if (list != null && !authorized()) finishFragment(); };
    private static String t(String value) { return DevGramLocale.text(value); }
    private static int dp(float value) { return AndroidUtilities.dp(value); }
    private int color(int key) { return Theme.getColor(key, resourceProvider); }
    private boolean authorized() { return !closed && viewer > 0 && getUserConfig().getClientUserId() == viewer && DevGramProfileCustomization.enabled() && DevGramBadges.hasDeveloperFeatures(viewer) && DevGramProfilePolicy.allowed(viewer,"profile.notices"); }
    private String cacheKey() { return "devgramNativeNewsDraft_" + currentAccount + "_" + viewer; }
    private static JSONObject json(Object... values) { JSONObject object = new JSONObject(); try { for (int i = 0; i + 1 < values.length; i += 2) object.put((String) values[i], values[i + 1]); } catch (Exception ignored) {} return object; }
    @Override public View createView(Context context) {
        viewer = getUserConfig().getClientUserId(); actionBar.setBackButtonImage(R.drawable.ic_ab_back); actionBar.setTitle(t("Новости DevGram"));
        actionBar.createMenu().addItem(1,R.drawable.msg_retry).setContentDescription(t("Обновить"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() { @Override public void onItemClick(int id) { if (id == -1) finishFragment(); else if (id == 1) refresh(); } });
        list = new UniversalRecyclerView(this,this::fill,this::click,null); fragmentView = new DevGramSettingsLayout(context,actionBar,list,resourceProvider); DevGramProfilePolicy.listen(policy); refresh(); return fragmentView;
    }
    private void update() { if (list != null && list.adapter != null) list.adapter.update(true); }
    private TextView words(String value,int size,boolean bold) { return DevGramStudioControls.text(getParentActivity(),value,size,bold,resourceProvider); }
    private JSONObject local() { try { return new JSONObject(MessagesController.getGlobalMainSettings().getString(cacheKey(),"{}")); } catch (Exception ignored) { return new JSONObject(); } }
    private void saveLocal(JSONObject value) { MessagesController.getGlobalMainSettings().edit().putString(cacheKey(),value.toString()).apply(); }
    private void clearLocal(String id) { if (local().optString("id").equals(id)) MessagesController.getGlobalMainSettings().edit().remove(cacheKey()).apply(); }
    private void fill(ArrayList<UItem> items,UniversalAdapter adapter) {
        if (!authorized()) { items.add(UItem.asShadow(t("Писать новости могут только разработчики"))); return; }
        LinearLayout hero = DevGramStudioControls.column(getParentActivity()); hero.setPadding(dp(22),dp(22),dp(22),dp(16));
        TextView brand = words("DEVGRAM",12,true); brand.setTextColor(color(Theme.key_windowBackgroundWhiteBlueText)); hero.addView(brand);
        TextView title = words(t("Статьи для ваших читателей"),26,true); title.setPadding(0,dp(12),0,dp(10)); hero.addView(title);
        TextView note = words(t("Встроенный редактор Telegram: оформление текста, изображения, видео, таблицы, списки и другие вставки."),14,false); note.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText)); hero.addView(note); items.add(UItem.asCustom(hero));
        items.add(UItem.asButton(2,R.drawable.msg_edit,t(local().has("document")?"Продолжить черновик":"Написать статью")).setEnabled(!busy));
        items.add(UItem.asShadow(t("Черновик сохраняется автоматически. Статья появится у читателей только после подтверждения публикации.")));
        items.add(UItem.asHeader(t("Мои статьи")));
        if (error != null) { items.add(UItem.asButton(3,R.drawable.msg_retry,t("Не удалось загрузить статьи. Повторить"))); return; }
        if (articles.length() == 0) items.add(UItem.asShadow(t(busy?"Загружаю…":"Здесь появятся ваши черновики и опубликованные статьи.")));
        for (int i=0;i<articles.length();i++) { JSONObject article=articles.optJSONObject(i); if(article==null)continue; LinearLayout card=DevGramStudioControls.column(getParentActivity()); card.setPadding(dp(22),dp(16),dp(22),dp(16)); card.setBackground(Theme.createRoundRectDrawable(dp(16),color(Theme.key_windowBackgroundWhite))); TextView heading=words(article.optString("title"),20,true); card.addView(heading); TextView preview=words(article.optString("description"),14,false); preview.setMaxLines(3); preview.setEllipsize(android.text.TextUtils.TruncateAt.END); preview.setPadding(0,dp(8),0,dp(8)); preview.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText)); card.addView(preview); TextView state=words(t(article.optLong("notice")>0?"Уже опубликовано в новостях":"Черновик"),12,true); state.setTextColor(color(Theme.key_windowBackgroundWhiteBlueText)); card.addView(state); TextView remove=words(t(article.optLong("notice")>0?"Удалить новость":"Удалить черновик"),14,true);remove.setTextColor(color(Theme.key_text_RedRegular));remove.setPadding(0,dp(16),0,dp(8));remove.setEnabled(!busy);remove.setOnClickListener(v->delete(article));card.addView(remove); card.setOnClickListener(v->load(article.optString("id"))); items.add(UItem.asCustom(card)); }
        if(offset>0)items.add(UItem.asButton(4,t("Предыдущие статьи")).setEnabled(!busy)); if(more)items.add(UItem.asButton(5,t("Следующие статьи")).setEnabled(!busy));
    }
    private void click(UItem item,View view,int position,float x,float y) { if(!authorized()||busy)return; if(item.id==2){JSONObject cached=local();if(cached.has("document"))open(cached);else open(json("id",UUID.randomUUID().toString(),"revision",0));}else if(item.id==3)refresh();else if(item.id==4){offset=Math.max(0,offset-20);refresh();}else if(item.id==5){offset+=20;refresh();} }
    private void delete(JSONObject article){
        if(!authorized()||busy)return;boolean published=article.optLong("notice")>0;
        new AlertDialog.Builder(getParentActivity(),resourceProvider).setTitle(t(published?"Удалить новость?":"Удалить черновик?"))
            .setMessage(t(published?"Новость исчезнет у всех читателей. Статья и её черновик будут удалены. Отменить удаление нельзя.":"Черновик будет удалён с устройства и сервера. Отменить удаление нельзя."))
            .setPositiveButton(t("Удалить"),(d,w)->{if(!authorized()||busy)return;busy=true;update();
                DevGramProfilePolicy.request(currentAccount,"studio/notices/native",json("action","delete","id",article.optString("id"),"revision",article.optInt("revision"),"confirm",true),(result,failure)->{
                    busy=false;if(!authorized()||getParentActivity()==null)return;
                    if(result==null){update();fail("Не удалось удалить. Обновите список и попробуйте ещё раз.");refresh();return;}
                    clearLocal(article.optString("id"));DevGramCustomizationNotices.removed(currentAccount,Math.max(result.optLong("notice"),article.optLong("notice")),article.optString("id"));offset=0;refresh();
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,t(published?"Новость удалена":"Черновик удалён")).show();
                });
            }).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).show();
    }
    private void refresh(){if(!authorized()||busy)return;busy=true;error=null;update();DevGramProfilePolicy.request(currentAccount,"studio/notices/native",json("action","list","offset",offset),(result,failure)->{busy=false;if(!authorized()||getParentActivity()==null)return;if(result==null)error=failure==null?"failed":failure;else{JSONArray values=result.optJSONArray("items");articles=values==null?new JSONArray():values;more=result.optBoolean("more");}update();});}
    private void load(String id){if(!authorized()||busy)return;JSONObject cached=local();if(id.equals(cached.optString("id"))){open(cached);return;}busy=true;DevGramProfilePolicy.request(currentAccount,"studio/notices/native",json("action","load","id",id),(result,failure)->{busy=false;if(!authorized()||getParentActivity()==null)return;if(result==null){fail("Не удалось загрузить статью");return;}try{result.put("owner",true);}catch(Exception ignored){}DevGramNativeNews.prepare(currentAccount,result,(rich,error)->{if(!authorized()||getParentActivity()==null)return;if(rich==null)fail(error);else openReady(result,rich);});});}
    private void open(JSONObject state){if(!authorized())return;if(!state.has("document")){openReady(state,null);return;}busy=true;DevGramNetworkQueue.postRunnable(()->{TL_iv.RichMessage rich=DevGramNativeNews.decode(state.optJSONObject("document"));AndroidUtilities.runOnUIThread(()->{busy=false;if(!authorized()||getParentActivity()==null)return;if(rich==null)fail("Не удалось открыть статью. Обновите DevGram.");else openReady(state,rich);});});}
    private final class Draft implements RichEditor.StandaloneDelegate {
        JSONObject state;
        String title;
        boolean published;
        RichEditor editor;
        Draft(JSONObject state){this.state=DevGramProfileDocument.copy(state);JSONObject doc=state.optJSONObject("document");title=doc==null?"":doc.optString("title");}
        public boolean allowed(){return authorized();}
        public void onDraft(TL_iv.RichMessage rich){if(published)return;if(rich==null){clearLocal(state.optString("id"));return;}try{JSONObject document=DevGramNativeNews.encode(rich,title);state.put("document",document);saveLocal(state);}catch(Exception e){if(editor!=null&&editor.getParentActivity()!=null)BulletinFactory.of(editor).createErrorBulletin(t("Статья слишком большая для сохранения")).show();}}
        public void onPreview(TL_iv.RichMessage rich){if(allowed())DevGramNativeNews.show(editor,rich,title.isEmpty()?t("Предпросмотр статьи"):title,state.optString("id"));}
        public void onClosed(){if(published||!allowed()||!state.has("document"))return;JSONObject snapshot=DevGramProfileDocument.copy(state);DevGramProfilePolicy.request(currentAccount,"studio/notices/native",json("action","save","id",snapshot.optString("id"),"revision",snapshot.optInt("revision"),"document",snapshot.optJSONObject("document")),(result,error)->{if(!allowed())return;if(result!=null){JSONObject latest=local();if(latest.optString("id").equals(snapshot.optString("id"))){try{latest.put("revision",result.optInt("revision"));}catch(Exception ignored){}saveLocal(latest);}refresh();}else if(getParentActivity()!=null)fail("Черновик сохранён на устройстве. Синхронизация пока недоступна.");});}
        public void onSubmit(RichEditor source,TL_iv.RichMessage rich){if(!allowed())return;EditTextBoldCursor field=new EditTextBoldCursor(getParentActivity());field.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));field.setTextSize(18);field.setSingleLine();field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)});JSONObject doc=state.optJSONObject("document");field.setText(title.isEmpty()&&doc!=null?doc.optString("title"):title);field.setPadding(dp(22),dp(16),dp(22),dp(16));new AlertDialog.Builder(getParentActivity(),resourceProvider).setTitle(t("Заголовок новости")).setView(field).setMessage(t("Она появится в колокольчике → Новости у всех пользователей DevGram.")).setPositiveButton(t("Опубликовать"),(d,w)->{if(!allowed())return;if(field.getText().toString().trim().isEmpty()){BulletinFactory.of(source).createErrorBulletin(t("Добавьте заголовок статьи")).show();return;}title=field.getText().toString().trim();onDraft(rich);publish(this,rich);}).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).show();}
    }
    private void openReady(JSONObject state,TL_iv.RichMessage rich){Draft draft=new Draft(state);RichEditor editor=rich==null?new RichEditor((CharSequence)""):new RichEditor(rich);draft.editor=editor;editor.setResourceProvider(resourceProvider);editor.setStandaloneDelegate(draft);presentFragment(editor);}
    private void publish(Draft draft,TL_iv.RichMessage rich){if(!authorized())return;draft.editor.setStandaloneSending(true);JSONObject state=draft.state;DevGramProfilePolicy.request(currentAccount,"studio/notices/native",json("action","stage","id",state.optString("id"),"revision",state.optInt("revision"),"document",state.optJSONObject("document")),(result,error)->{if(!authorized())return;if(result==null){publishError(draft);return;}try{state.put("revision",result.optInt("revision"));}catch(Exception ignored){}saveLocal(state);String stage=result.optString("stage");JSONArray assets=result.optJSONArray("assets");if(assets==null){publishError(draft);return;}DevGramNativeNews.sendMedia(currentAccount,rich,stage,assets,failure->{if(!authorized())return;if(failure!=null){publishError(draft);return;}poll(draft,stage,0);});});}
    private void poll(Draft draft,String stage,int attempt){if(!authorized())return;if(attempt>=40){publishError(draft);return;}DevGramProfilePolicy.request(currentAccount,"studio/notices/native",json("action","status","stage",stage),(result,error)->{if(!authorized())return;if(result==null){publishError(draft);return;}if(!result.optBoolean("ready")){AndroidUtilities.runOnUIThread(()->poll(draft,stage,attempt+1),3000);return;}DevGramProfilePolicy.request(currentAccount,"studio/notices/publish",json("id",draft.state.optString("id"),"stage",stage),(published,error2)->{if(!authorized())return;if(published==null){publishError(draft);return;}draft.published=true;clearLocal(draft.state.optString("id"));draft.editor.finishStandalonePublication();DevGramCustomizationNotices.refresh(true);refresh();if(getParentActivity()!=null)BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,t("Статья опубликована")).show();});});}
    private void publishError(Draft draft){draft.editor.setStandaloneSending(false);if(draft.editor.getParentActivity()!=null)BulletinFactory.of(draft.editor).createErrorBulletin(t("Не удалось опубликовать статью. Черновик сохранён, попробуйте ещё раз.")).show();}
    private void fail(String value){if(getParentActivity()!=null)BulletinFactory.of(this).createErrorBulletin(t(value)).show();}
    @Override public void onResume(){super.onResume();if(list!=null)refresh();}
    @Override public void onFragmentDestroy(){closed=true;DevGramProfilePolicy.unlisten(policy);super.onFragmentDestroy();}
}
