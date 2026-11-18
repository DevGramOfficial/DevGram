package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.*;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Community workshop. Publication/install are explicit user actions; viewing is public. */
public class DevGramWorkshopActivity extends DevGramSettingsFragment {
    private final DevGramStudioActivity.Draft draft;
    private final long workId;
    private UniversalRecyclerView list;
    private DevGramStudioPreview preview;
    private JSONArray works = new JSONArray(), comments = new JSONArray(), clans = new JSONArray();
    private JSONObject work;
    private int offset, next = -1, likes;
    private boolean loading, top, showClans;
    private DevGramWorkshopBrowserView browser;
    private DevGramWorkshopDetailView detail;
    private android.widget.LinearLayout detailContainer;
    private String initialScope="profile";
    private String initialSection="home",initialKind="profile";
    private boolean workLiked,workFavorite,stateLoaded;
    private DevGramWorkshopVotesController votes;
    private boolean released,mutationBusy,pendingLoaded;
    private int readGeneration,mutationGeneration;
    private long viewer;
    private boolean current(){return !released&&DevGramProfileCustomization.enabled()&&getUserConfig().getClientUserId()==viewer;}
    private void voteChanged(){if(votes!=null&&votes.known()){workLiked=votes.liked();workFavorite=votes.favorite();likes=votes.likes();stateLoaded=true;}if(list!=null)list.adapter.update(true);if(detail!=null)detail.votes(workLiked,workFavorite,likes,votes!=null&&votes.busy());}
    private final Runnable live = this::policyChanged;
    private void policyChanged() {
        if (!DevGramProfileCustomization.enabled()) {
            readGeneration++;mutationGeneration++;loading=mutationBusy=false;
            dismissCurrentDialog();
            if (preview != null) preview.release();
            if (browser != null) browser.release();
            if (fragmentView != null && !isFinishing()) DevGramAccessNavigation.close(this);
        } else {if (list != null) list.adapter.update(true);if(detail!=null&&work!=null)bindDetail();}
    }
    @Override public boolean onFragmentCreate() { return DevGramProfileCustomization.enabled() && super.onFragmentCreate(); }
    public DevGramWorkshopActivity(DevGramStudioActivity.Draft draft) { this(draft, 0); }
    public static DevGramWorkshopActivity openWork(long id){return new DevGramWorkshopActivity(new DevGramStudioActivity.Draft(),id);}
    public static DevGramWorkshopActivity forSection(DevGramStudioActivity.Draft draft,String section,String kind) { DevGramWorkshopActivity activity=new DevGramWorkshopActivity(draft);activity.initialSection=section;activity.initialKind=kind;return activity; }
    private DevGramWorkshopActivity(DevGramStudioActivity.Draft draft, long id) { this.draft = draft; workId = id;initialScope=draft.scope; }
    private String t(String text) { return DevGramLocale.text(text); }

    @Override public View createView(Context context) {
        viewer=getUserConfig().getClientUserId();
        DevGramProfilePolicy.listen(live);
        if(workId>0)votes=new DevGramWorkshopVotesController(this,workId,this::voteChanged);
        if(!draft.dirty&&draft.document.length()==0){
            draft.document=DevGramProfileDocument.raw(draft.target(currentAccount));draft.theme=DevGramProfileStyle.raw(draft.target(currentAccount));
            DevGramProfileDocument.load(currentAccount,draft.subject,(result,error)->{if(result!=null&&!draft.dirty)draft.document=DevGramProfileDocument.editable(result.optJSONObject("document"));});
        }
        actionBar.setBackButtonImage(R.drawable.ic_ab_back); actionBar.setTitle(t("Мастерская"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() { @Override public void onItemClick(int id) { if (id == -1 && (browser==null||!browser.back(true))) finishFragment(); } });
        if(workId==0){
            android.widget.FrameLayout root=new android.widget.FrameLayout(context);root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray,resourceProvider));
            browser=new DevGramWorkshopBrowserView(context,this,this::openWork,()->DevGramWorkshopPublishSheet.show(this,draft,null));browser.setScope(initialScope);browser.navigate(initialSection,initialKind);browser.setBottomInset(Math.max(getBottomInset(),AndroidUtilities.navigationBarHeight));
            if(initialAuthor>0)browser.showAuthor(initialAuthor);
            root.addView(browser,LayoutHelper.createFrame(-1,-1,android.view.Gravity.FILL,0,(AndroidUtilities.statusBarHeight+ActionBar.getCurrentActionBarHeight())/AndroidUtilities.density,0,0));
            actionBar.setAddToContainer(false);root.addView(actionBar,LayoutHelper.createFrame(-1,-2,android.view.Gravity.TOP));
            actionBar.createMenu().addItem(7710,R.drawable.msg_add).setOnClickListener(v->DevGramWorkshopPublishSheet.show(this,draft,null));
            actionBar.createMenu().addItem(7711,R.drawable.msg_groups).setOnClickListener(v->showClanDirectory());
            return fragmentView=root;
        }
        android.widget.FrameLayout root=new android.widget.FrameLayout(context);
        detailContainer=DevGramStudioControls.column(context);
        detailContainer.setPadding(0,AndroidUtilities.statusBarHeight+ActionBar.getCurrentActionBarHeight(),0,Math.max(getBottomInset(),AndroidUtilities.navigationBarHeight));
        detail=new DevGramWorkshopDetailView(context,this,id->click(UItem.asButton(id,""),null,0,0,0),this::openWork,this::openAuthor);
        detailContainer.addView(detail,new LinearLayout.LayoutParams(-1,-1));root.addView(detailContainer,new android.widget.FrameLayout.LayoutParams(-1,-1));
        actionBar.setTitle(t("Мастерская"));actionBar.setAddToContainer(false);root.addView(actionBar,LayoutHelper.createFrame(-1,-2,android.view.Gravity.TOP));
        actionBar.createMenu().addItem(7712,R.drawable.msg_report).setContentDescription(t("Пожаловаться"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();else if(id==7712)click(UItem.asButton(13,""),null,0,0,0);}});
        fragmentView=root;if(work!=null)bindDetail();refresh();return root;
    }
    @Override public void onInsets(int left,int top,int right,int bottom){
        super.onInsets(left,top,right,bottom);
        if(detailContainer!=null)detailContainer.setPadding(left,top+ActionBar.getCurrentActionBarHeight(),right,bottom);
        if(browser!=null){
            android.widget.FrameLayout.LayoutParams layout=(android.widget.FrameLayout.LayoutParams)browser.getLayoutParams();
            int header=top+ActionBar.getCurrentActionBarHeight();
            // The status bar height can arrive after createView on a cold start.
            // Keep search and destination tabs below the actual edge-to-edge header.
            if(layout!=null&&(layout.topMargin!=header||layout.leftMargin!=left||layout.rightMargin!=right)){
                layout.topMargin=header;layout.leftMargin=left;layout.rightMargin=right;browser.setLayoutParams(layout);
            }
            browser.setBottomInset(bottom);
        }
    }
    private void openAuthor(){if(work==null)return;DevGramWorkshopActivity authors=forSection(draft,"new",work.optString("kind","profile"));authors.initialAuthor=work.optLong("uid");authors.initialScope=work.optString("scope","profile");presentFragment(authors);}
    private void bindDetail(){if(detail==null||work==null)return;if(sourceSheet!=null)sourceSheet.close();sourceSheet=null;detail.bind(work,comments,workLiked,workFavorite,likes,votes!=null&&votes.busy(),container->{sourceSheet=new DevGramWorkshopSourcesSheet(this,work,resourceProvider);container.addView(sourceSheet.content(true));});}
    private void openWork(JSONObject item){
        if(item.has("work")&&item.has("status")){
            DevGramStudioPreview shot=new DevGramStudioPreview(getParentActivity(),currentAccount,item.optLong("uid"));shot.set(item.optJSONObject("document"),new DevGramProfileStyle.Style(item.optJSONObject("theme")));shot.setFrameOnly(item.optString("kind").equals("frame"));
            android.widget.FrameLayout reviewPreview=new android.widget.FrameLayout(getParentActivity());reviewPreview.addView(shot,LayoutHelper.createFrame(-1,300));
            AlertDialog.Builder dialog=new AlertDialog.Builder(getParentActivity(),resourceProvider);dialog.setTitle(item.optString("title"));dialog.setView(reviewPreview);
            dialog.setPositiveButton(t("Одобрить"),(d,w)->review(item,true));dialog.setNegativeButton(t("Отклонить"),(d,w)->input("Причина отклонения",500,reason->review(item,false,reason)));showDialog(dialog.create(),d->shot.release());return;
        }
        DevGramWorkshopActivity detail=new DevGramWorkshopActivity(draft,item.optLong("id"));
        if(item.optString("state").equals("pending")){detail.work=item;detail.stateLoaded=true;}
        presentFragment(detail);
    }
    private void review(JSONObject item,boolean approve){review(item,approve,"");}
    private void review(JSONObject item,boolean approve,String reason){DevGramProfilePolicy.request(currentAccount,"studio/workshop",DevGramStudioActivity.json("action","review","id",item.optLong("work"),"submission",item.optLong("id"),"approved",approve,"reason",reason),(result,error)->{if(result==null)BulletinFactory.of(this).createErrorBulletin(t("Не удалось выполнить действие. Проверьте доступ и соединение.")).show();else if(browser!=null)browser.load(false);});}
    private void fill(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (workId == 0) {
            items.add(UItem.asButton(1, t("Сортировка"), t(top ? "Популярные" : "Новые")));
            items.add(UItem.asButton(2, t(showClans ? "Оформления" : "Теги кланов")));
            if (showClans) {
                for (int i = 0; i < clans.length(); i++) { JSONObject clan = clans.optJSONObject(i); items.add(UItem.asButton(3000 + i, "[" + clan.optString("tag") + "]", String.valueOf(clan.optInt("count")))); }
            } else for (int i = 0; i < works.length(); i++) {
                JSONObject item = works.optJSONObject(i);
                items.add(UItem.asButton(100 + i, item.optString("title"), "♥ " + item.optInt("likes") + " · " + item.optInt("comments")));
            }
            if (!loading && (showClans ? clans.length() : works.length()) == 0) items.add(UItem.asShadow(t("В мастерской пока нет работ")));
            if (next >= 0 && !showClans) items.add(UItem.asButton(3, t("Загрузить ещё")));
            items.add(UItem.asButton(4, t(loading ? "Загружаю…" : "Обновить")));
        }
    }
    private long initialAuthor;
    private void showClanDirectory(){
        presentFragment(new DevGramClanTagsActivity());
    }
    private DevGramWorkshopSourcesSheet sourceSheet;
    private void showSources(){if(work==null||!current())return;if(sourceSheet!=null)sourceSheet.close();sourceSheet=new DevGramWorkshopSourcesSheet(this,work,resourceProvider);sourceSheet.show();}
    private String likeKey() { return "devgramWorkLike_" + getUserConfig().getClientUserId() + "_" + workId; }
    private void click(UItem item, View view, int position, float x, float y) {
        if (loading||mutationBusy||!current()) return;
        if (workId == 0) {
            if (item.id >= 3000) {
                JSONObject clan = clans.optJSONObject(item.id - 3000); if (clan == null) return;
                AlertDialog.Builder d = new AlertDialog.Builder(getParentActivity(), resourceProvider); d.setTitle("[" + clan.optString("tag") + "]");
                d.setMessage(t("Участников") + ": " + clan.optInt("count")); d.setPositiveButton(t("Понятно"), null); showDialog(d.create());
            } else if (item.id >= 100) {
                JSONObject selected = works.optJSONObject(item.id - 100); if (selected != null) presentFragment(new DevGramWorkshopActivity(draft, selected.optLong("id")));
            } else if (item.id == 1) { top = !top; offset = 0; works = new JSONArray(); refresh(); }
            else if (item.id == 2) { showClans = !showClans; refresh(); }
            else if (item.id == 3 && next >= 0) { offset = next; refresh(); }
            else if (item.id == 4) { offset = 0; works = new JSONArray(); refresh(); }
            return;
        }
        if (item.id >= 1000) {
            if (!DevGramAppearanceAccess.check(this, "workshop.social")) return;
            JSONObject comment = comments.optJSONObject(item.id - 1000); if (comment == null) return;
            if (comment.optLong("uid") != getUserConfig().getClientUserId() && work.optLong("uid") != getUserConfig().getClientUserId() && !DevGramBadges.hasDeveloperFeatures(getUserConfig().getClientUserId())) return;
            confirm("Удалить комментарий?", () -> mutate("delete_comment", DevGramStudioActivity.json("comment", comment.optLong("id")), ignored -> refresh()));
        } else if (item.id == 10 && DevGramAppearanceAccess.check(this, "workshop.install")) {
            DevGramWorkshopDestinationSheet.show(this,subject->{
            if(detail!=null)detail.applying(true);
            mutate("install",DevGramStudioActivity.json("apply",true,"subject",subject),result->{
                if(!result.optBoolean("applied")){BulletinFactory.of(this).createErrorBulletin(t("Не удалось сохранить оформление")).show();return;}
                DevGramProfileDocument.acceptWorkshop(currentAccount,result);
                if(draft.target(currentAccount)==subject){draft.document=DevGramProfileDocument.raw(subject);draft.theme=DevGramProfileStyle.raw(subject);draft.dirty=false;if(draft.onChanged!=null)draft.onChanged.run();if(draft.onSaved!=null)draft.onSaved.run();}
                if(detail!=null)detail.applying(false);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,t("Оформление установлено")).show();
            });});
        } else if (item.id == 11 && DevGramAppearanceAccess.check(this, "workshop.social")) {
            if(votes!=null)votes.toggle(false);
        } else if (item.id == 12 && DevGramAppearanceAccess.check(this, "workshop.social")) {
            input("Комментарий", 1000, text -> mutate("comment", DevGramStudioActivity.json("text", text), result -> refresh()));
        } else if (item.id == 13) input("Причина жалобы", 500, text -> mutate("report", DevGramStudioActivity.json("text", text), result -> BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, t("Жалоба отправлена")).show()));
        else if (item.id == 14) confirm("Удалить публикацию?", () -> mutate("delete", new JSONObject(), result -> finishFragment()));
        else if(item.id==15&&DevGramAppearanceAccess.check(this,"workshop.install")){if(votes!=null)votes.toggle(true);}
        else if(item.id==16){AndroidUtilities.addToClipboard("https://t.me/DevGramSettings?s=workshop__"+workId);BulletinFactory.of(this).createCopyLinkBulletin().show();}
        else if(item.id==17)exportFrame();
        else if(item.id==18)DevGramWorkshopPublishSheet.show(this,draft,work);
        else if(item.id==19)showSources();
    }
    private void acceptState(JSONObject value){workLiked=value.optBoolean("liked");workFavorite=value.optBoolean("favorite");stateLoaded=true;likes=value.optInt("likes",likes);if(list!=null)list.adapter.update(true);}
    private void refresh() {
        if(!current())return;
        if(browser!=null){browser.load(false);return;}
        if(work!=null&&work.optString("state").equals("pending")){
            if(!pendingLoaded&&!loading){loading=true;int read=++readGeneration;DevGramProfilePolicy.request(currentAccount,"studio/workshop",DevGramStudioActivity.json("action","state","id",workId),(result,error)->{
                if(!current()||read!=readGeneration)return;loading=false;
                if(result!=null&&result.optJSONObject("work")!=null){work=result.optJSONObject("work");pendingLoaded=true;if(preview!=null)preview.set(work.optJSONObject("document"),new DevGramProfileStyle.Style(work.optJSONObject("theme")));}
                if(list!=null)list.adapter.update(true);if(detail!=null)bindDetail();
            });}
            if(list!=null)list.adapter.update(true);if(detail!=null&&work!=null)bindDetail();return;
        }
        if(votes!=null)votes.refresh();
        if (loading) return; loading = true;final int read=++readGeneration;live.run();
        String route = workId > 0 ? "studio/works/" + workId + ".json" : showClans ? "studio/clans.json" : "studio/workshop.json?sort=" + (top ? "top" : "new") + "&offset=" + offset;
        DevGramProfilePolicy.executeWorkshop(() -> {
            JSONObject result = null; try { result = DevGramProfilePolicy.http("GET", route, null); } catch (Exception ignored) {}
            final JSONObject value = result;
            AndroidUtilities.runOnUIThread(() -> {
                if(!current()||read!=readGeneration)return;
                loading = false;
                if (value != null && value.optBoolean("ok")) {
                    if (workId > 0) {
                        work = value.optJSONObject("work"); comments = value.optJSONArray("comments"); if(votes==null||!votes.known())likes=value.optInt("likes"); if (comments == null) comments = new JSONArray();

                    } else if (showClans) { clans = value.optJSONArray("clans"); if (clans == null) clans = new JSONArray(); }
                    else { JSONArray received = value.optJSONArray("works"); if (received != null) for (int i = 0; i < received.length(); i++) works.put(received.optJSONObject(i)); next = value.isNull("next") ? -1 : value.optInt("next", -1); }
                } else if (getParentActivity() != null) BulletinFactory.of(this).createErrorBulletin(t("Не удалось загрузить мастерскую")).show();
                if(detail!=null&&work==null)detail.status("Не удалось загрузить мастерскую");live.run();
            });
        });
    }
    private void mutate(String action, JSONObject data, Consumer<JSONObject> done) {
        if(!current()||mutationBusy)return;mutationBusy=true;int request=++mutationGeneration;
        final String required=action.equals("install")||action.equals("favorite")?"workshop.install":action.equals("like")||action.equals("comment")||action.equals("delete_comment")?"workshop.social":null;
        try { data.put("action", action).put("id", workId); } catch (Exception ignored) {}
        DevGramProfilePolicy.request(currentAccount, "studio/workshop", data, (result, error) -> {
            if(!current()||request!=mutationGeneration)return;mutationBusy=false;if(detail!=null)detail.applying(false);
            if(required!=null&&!DevGramAppearanceAccess.allowed(this,required))return;
            if (result == null) BulletinFactory.of(this).createErrorBulletin(t("Не удалось выполнить действие. Проверьте доступ и соединение.")).show(); else done.accept(result);
        });
    }
    private void input(String title, int maximum, Consumer<String> done) {
        LinearLayout box = new LinearLayout(getParentActivity()); box.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        EditText edit = new EditText(getParentActivity()); edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider)); edit.setTextSize(16); edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(maximum)}); box.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog.Builder d = new AlertDialog.Builder(getParentActivity(), resourceProvider); d.setTitle(t(title)); d.setView(box);
        d.setPositiveButton(t("Готово"), (dialog, w) -> { String text = edit.getText().toString().trim(); if (!text.isEmpty()) done.accept(text); });
        d.setNegativeButton(DevGramLocale.getString(R.string.Cancel), null); showDialog(d.create());
    }
    private void confirm(String title, Runnable action) { AlertDialog.Builder d = new AlertDialog.Builder(getParentActivity(), resourceProvider); d.setTitle(t(title)); d.setPositiveButton(t("Удалить"), (dialog, w) -> action.run()); d.setNegativeButton(DevGramLocale.getString(R.string.Cancel), null); showDialog(d.create()); }
    private byte[] frameExport;
    private void exportFrame(){
        if(work==null||!DevGramAppearanceAccess.check(this,"profile.export"))return;
        JSONObject packed=DevGramStudioActivity.json("format","devgram-frame-1","document",work.optJSONObject("document"));frameExport=packed.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);intent.setType("application/json");intent.putExtra(android.content.Intent.EXTRA_TITLE,"DevGram-frame-"+workId+".dgframe");getParentActivity().startActivityForResult(intent,7643);
    }
    @Override public void onActivityResultFragment(int request,int result,android.content.Intent data){super.onActivityResultFragment(request,result,data);if(sourceSheet!=null)sourceSheet.result(request,result,data);if(request!=7643||result!=android.app.Activity.RESULT_OK||data==null||data.getData()==null||frameExport==null)return;byte[] bytes=frameExport;frameExport=null;Utilities.globalQueue.postRunnable(()->{boolean saved=false;try(java.io.OutputStream stream=ApplicationLoader.applicationContext.getContentResolver().openOutputStream(data.getData())){if(stream!=null){stream.write(bytes);saved=true;}}catch(Exception ignored){}boolean ok=saved;AndroidUtilities.runOnUIThread(()->{if(getParentActivity()!=null){if(ok)BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,t("Файл сохранён")).show();else BulletinFactory.of(this).createErrorBulletin(t("Не удалось сохранить файл")).show();}});});}
    @Override public boolean onBackPressed(boolean invoked){if(browser!=null&&browser.back(invoked))return false;return super.onBackPressed(invoked);}
    @Override public void onResume(){super.onResume();if(browser!=null)browser.setPreviewsActive(true);if(detail!=null)detail.setPreviewsActive(true);}
    @Override public void onPause(){if(browser!=null)browser.setPreviewsActive(false);if(detail!=null)detail.setPreviewsActive(false);super.onPause();}
    @Override public void onFragmentDestroy() { released=true;if(detail!=null)detail.release();if(sourceSheet!=null)sourceSheet.close();readGeneration++;mutationGeneration++;if(votes!=null)votes.release();DevGramProfilePolicy.unlisten(live); if(browser!=null)browser.release();if (preview != null) preview.release();frameExport=null;super.onFragmentDestroy(); }
}
