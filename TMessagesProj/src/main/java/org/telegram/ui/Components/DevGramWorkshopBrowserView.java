package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.PopupMenu;
import android.text.TextUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import org.telegram.ui.ProfileActivity;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.DevGramStudioActivity;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Workshop navigation and discovery. Feed previews stay still; opening a work enables its bounded animated preview. */
public final class DevGramWorkshopBrowserView extends FrameLayout {
    private final BaseFragment host;
    private final Consumer<JSONObject> open;
    private final Runnable publish;
    private final LinearLayout rail, content, filters;
    private final RecyclerView gallery;
    private final ScrollView railScroll;
    private final ArrayList<Entry> entries = new ArrayList<>();
    private final GalleryAdapter adapter = new GalleryAdapter();
    private android.animation.ValueAnimator railAnimation;
    private static final int HEADING=0, INFO=1, ACTION=2, WORK=3, AUTHOR=4, FEATURED=5, SHELF=6, AUTHOR_HEAD=7;
    private static final class Entry {
        final int type;final String title;final JSONObject work;final Runnable action;
        Entry(int type,String title,JSONObject work,Runnable action){this.type=type;this.title=title;this.work=work;this.action=action;}
    }
    private final View scrim;
    private final EditText search;
        private final ArrayList<View> railLabels=new ArrayList<>();
    private String scope="profile";
    private static final String[] SCOPES={"profile","channel","group","forum"};
    private String section="home",kind="profile",period="week",query="",railSection="home";
    private String searchOrigin;
    private long author;
    private int generation,offset,next=-1;
    private boolean previewsActive;
    public void setPreviewsActive(boolean active){previewsActive=active;for(DevGramWorkshopPreviewView preview:new ArrayList<>(previews))preview.setActive(active);for(DevGramWorkshopAuthorView head:new ArrayList<>(authorHeads))head.setActive(active);}
    private boolean expanded,loading,attached,released,suppressSearch,customizationActive=DevGramProfileCustomization.enabled();
    private final java.util.ArrayDeque<Spot> history=new java.util.ArrayDeque<>();
    private final java.util.HashSet<DevGramWorkshopPreviewView> previews=new java.util.HashSet<>();
    private final java.util.HashSet<DevGramWorkshopAuthorView> authorHeads=new java.util.HashSet<>();
    private final java.util.HashSet<Long> resolving=new java.util.HashSet<>();
    private long policyRevision=DevGramProfilePolicy.revision();
    private int authorGeneration;
    private JSONObject authorInfo;
    private static final class Spot {
        final String section,kind,period,query,rail,searchOrigin,scope;final long author;final android.os.Parcelable scroll;
        Spot(DevGramWorkshopBrowserView v){scope=v.scope;section=v.section;kind=v.kind;period=v.period;query=v.query;rail=v.railSection;searchOrigin=v.searchOrigin;author=v.author;scroll=v.gallery.getLayoutManager().onSaveInstanceState();}
    }
    private android.os.Parcelable restoreScroll;
    private final Runnable searchTask=()->load(false);
    private final Runnable policies=this::updatePolicies;
    private final java.util.HashSet<WorkHolder> bound = new java.util.HashSet<>();

    public DevGramWorkshopBrowserView(Context context,BaseFragment host,Consumer<JSONObject> open,Runnable publish){
        super(context);this.host=host;this.open=open;this.publish=publish;setBackgroundColor(color(Theme.key_windowBackgroundGray));
        content=DevGramStudioControls.column(context);addView(content,LayoutHelper.createFrame(-1,-1,Gravity.FILL,52,0,0,0));
        search=new EditText(context);search.setSingleLine(true);search.setTextSize(15);search.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));search.setHintTextColor(color(Theme.key_windowBackgroundWhiteGrayText));search.setHint(t("Поиск по названию и автору"));search.setPadding(dp(16),0,dp(16),0);search.setBackground(DevGramStudioControls.surface(color(Theme.key_windowBackgroundWhite),0));content.addView(search,LayoutHelper.createLinear(-1,46,12,10,12,6));
        search.addTextChangedListener(new TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int a,int c,int f){}@Override public void afterTextChanged(Editable s){}@Override public void onTextChanged(CharSequence s,int a,int b,int c){searchChanged(s.toString());}});
        filters=DevGramStudioControls.column(context);content.addView(filters);
        gallery=new RecyclerView(context);gallery.setClipToPadding(false);gallery.setPadding(dp(8),dp(4),dp(8),dp(24));
        GridLayoutManager grid=new GridLayoutManager(context,2);grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup(){@Override public int getSpanSize(int position){return position<entries.size()&&entries.get(position).type==WORK?1:2;}});
        gallery.setLayoutManager(grid);gallery.setAdapter(adapter);gallery.setItemAnimator(null);gallery.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);content.addView(gallery,new LinearLayout.LayoutParams(-1,0,1));
        scrim=new View(context);scrim.setBackgroundColor(0x55000000);scrim.setVisibility(GONE);scrim.setOnClickListener(v->expand(false));addView(scrim,LayoutHelper.createFrame(-1,-1));
        rail=DevGramStudioControls.column(context);rail.setPadding(dp(4),dp(10),dp(4),dp(8));rail.setBackgroundColor(color(Theme.key_windowBackgroundWhite));railScroll=new ScrollView(context);railScroll.setFillViewport(true);railScroll.setVerticalScrollBarEnabled(false);railScroll.setBackgroundColor(color(Theme.key_windowBackgroundWhite));railScroll.setElevation(dp(6));railScroll.addView(rail);addView(railScroll,LayoutHelper.createFrame(52,-1,Gravity.LEFT));
        buildRail();buildFilters();
        if(DevGramBadges.isSignedIn())DevGramPlugins.fetchModerators(m->{if(!released)buildRail();});
    }
    private int dp(float value){return AndroidUtilities.dp(value);}
    private int color(int key){return Theme.getColor(key,host.getResourceProvider());}
    private String t(String value){return DevGramLocale.text(value);}
    private TextView text(String value,int size,boolean bold){return DevGramStudioControls.text(getContext(),value,size,bold,host.getResourceProvider());}
    public void setScope(String value){scope=java.util.Arrays.asList(SCOPES).contains(value)?value:"profile";}
    public void navigate(String target,String kind){section=target;this.kind=kind;author=target.equals("mine")?host.getUserConfig().getClientUserId():0;railSection=target;syncRail();buildRail();buildFilters();if(attached)load(false);}
    public void showAuthor(long uid){if(uid<=0)return;if(attached)mark();author=uid;authorInfo=null;section="new";query="";setSearch("");railSection="authors";buildRail();buildFilters();if(attached)load(false);}
    private void searchChanged(String value){if(suppressSearch)return;String nextQuery=value.trim();if(nextQuery.equals(query))return;if(query.isEmpty()&&!nextQuery.isEmpty()){mark();if(section.equals("home")||section.equals("authors"))searchOrigin=section;}query=nextQuery;generation++;loading=false;if(searchOrigin!=null){section=query.isEmpty()?searchOrigin:"new";if(query.isEmpty())searchOrigin=null;author=0;railSection=section.equals("home")?"home":section.equals("authors")?"authors":kind.equals("frame")?"frames":"profiles";buildRail();buildFilters();}AndroidUtilities.cancelRunOnUIThread(searchTask);AndroidUtilities.runOnUIThread(searchTask,350);}
    private void mark(){if(history.size()>=24)history.removeFirst();history.addLast(new Spot(this));}
    public boolean back(boolean invoked){if(expanded){if(invoked)expand(false);return true;}if(history.isEmpty())return false;if(invoked){Spot spot=history.removeLast();scope=spot.scope;section=spot.section;kind=spot.kind;period=spot.period;query=spot.query;author=spot.author;railSection=spot.rail;authorInfo=null;setSearch(query);searchOrigin=spot.searchOrigin;restoreScroll=spot.scroll;buildRail();buildFilters();load(false);}return true;}
    private void setSearch(String value){searchOrigin=null;suppressSearch=true;search.setText(value);suppressSearch=false;AndroidUtilities.cancelRunOnUIThread(searchTask);}
    public void setBottomInset(int bottom){gallery.setPadding(dp(8),dp(4),dp(8),Math.max(dp(24),bottom+dp(12)));}
    public void refresh(){load(false);}

    private void syncRail(){if(author>0&&!section.equals("mine")){railSection="authors";return;}if(section.equals("popular")||section.equals("new")||section.equals("best")||section.equals("rising")||section.equals("day"))railSection=kind.equals("frame")?"frames":"profiles";else railSection=section;}
    private void buildRail(){
        rail.removeAllViews();railLabels.clear();
        railItem("menu","Мастерская",R.drawable.msg_customize,()->expand(!expanded));
        railItem("home","Главная",R.drawable.msg_photos,()->pick("home","profile"));
        railItem("profiles","Профили",R.drawable.msg_contact,()->pick("popular","profile"));
        railItem("frames","Рамки",R.drawable.msg_photo_settings,()->pick("popular","frame"));
        railItem("authors","Топ авторов",R.drawable.msg_groups,()->pick("authors",kind));
        railItem("mine","Мои работы",R.drawable.msg_gallery,()->pick("mine",kind));
        railItem("liked","Мне нравится",R.drawable.msg_input_like,()->pick("liked",kind));
        railItem("favorites","Избранное",R.drawable.msg_premium_badge,()->pick("favorites",kind));
        railItem("my_reports","Мои жалобы",R.drawable.msg_report,()->host.presentFragment(new org.telegram.ui.DevGramWorkshopMyReportsActivity()));
        if(DevGramBadges.hasDeveloperFeatures(host.getUserConfig().getClientUserId())){
            railItem("review","На проверке",R.drawable.msg_secret,()->pick("review",kind));
        }
        if(!DevGramBadges.isSignedIn()||DevGramPlugins.canSeeModeration(host.getUserConfig().getClientUserId()))railItem("reports","Жалобы",R.drawable.msg_report,()->org.telegram.ui.DevGramWorkshopReportsActivity.open(host));
    }
    private void railItem(String id,String title,int icon,Runnable action){
        boolean selected=id.equals(railSection);
        LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),0,dp(10),0);row.setBackground(DevGramStudioControls.surface(selected?androidx.core.graphics.ColorUtils.setAlphaComponent(color(Theme.key_windowBackgroundWhiteBlueText),26):Color.TRANSPARENT,0));
        ImageView image=new ImageView(getContext());image.setImageResource(icon);image.setColorFilter(color(selected?Theme.key_windowBackgroundWhiteBlueText:Theme.key_windowBackgroundWhiteGrayText));row.addView(image,LayoutHelper.createLinear(24,24));
        TextView label=text(title,13,selected);label.setSingleLine(true);label.setVisibility(expanded?VISIBLE:GONE);row.addView(label,LayoutHelper.createLinear(-2,-2,12,0,0,0));railLabels.add(label);row.setContentDescription(t(title));row.setSelected(selected);row.setOnClickListener(v->action.run());rail.addView(row,LayoutHelper.createLinear(-1,46,0,2,0,2));
    }
    private void expand(boolean value){expanded=value;scrim.setVisibility(value?VISIBLE:GONE);if(railAnimation!=null)railAnimation.cancel();railAnimation=android.animation.ValueAnimator.ofInt(railScroll.getLayoutParams().width,dp(value?196:52));railAnimation.setDuration(320);railAnimation.addUpdateListener(v->{ViewGroup.LayoutParams lp=railScroll.getLayoutParams();lp.width=(int)v.getAnimatedValue();railScroll.setLayoutParams(lp);});railAnimation.start();for(View label:railLabels)label.setVisibility(value?VISIBLE:GONE);}
    private void pick(String target,String type){mark();expand(false);section=target;kind=type;query="";author=target.equals("mine")?host.getUserConfig().getClientUserId():0;authorInfo=null;railSection=target.equals("popular")||target.equals("new")||target.equals("best")||target.equals("rising")||target.equals("day")?(type.equals("frame")?"frames":"profiles"):target;setSearch("");buildRail();buildFilters();load(false);}
    private void buildFilters(){
        filters.removeAllViews();
        int scopeIndex=java.util.Arrays.asList(SCOPES).indexOf(scope);
        filters.addView(DevGramStudioControls.pills(getContext(),new String[]{"Профиль","Канал","Группа","Форум"},Math.max(0,scopeIndex),host.getResourceProvider(),i->{mark();scope=SCOPES[i];buildFilters();load(false);}));
        if(section.equals("home"))return;
        if(section.equals("authors")){filters.addView(DevGramStudioControls.pills(getContext(),new String[]{"По лайкам","Лучшие авторы"},period.equals("ratio")?1:0,host.getResourceProvider(),i->{period=i==0?"week":"ratio";buildFilters();load(false);}));return;}
        filters.addView(DevGramStudioControls.pills(getContext(),new String[]{"Профили","Рамки"},kind.equals("frame")?1:0,host.getResourceProvider(),i->{kind=i==0?"profile":"frame";syncRail();buildRail();buildFilters();load(false);}));
        if(!section.equals("mine")&&!section.equals("liked")&&!section.equals("favorites")&&!section.equals("review")){
            String label=section.equals("new")?"Новое":section.equals("best")?"Лучшее":section.equals("rising")?"Взлетают":section.equals("day")?"Работы дня":"Популярное";
            TextView sort=text("",14,true);sort.setText(t(label)+"  ▾");sort.setGravity(Gravity.CENTER);sort.setTextColor(color(Theme.key_windowBackgroundWhiteBlueText));sort.setPadding(dp(12),dp(10),dp(12),dp(10));
            if(section.equals("best"))sort.setText(t("Лучшее")+" · "+t(period.equals("day")?"За сегодня":period.equals("month")?"За месяц":"За неделю")+"  ▾");
            sort.setOnClickListener(v->sortMenu(sort));filters.addView(sort,LayoutHelper.createLinear(-1,-2));
        }
    }
    private void sortMenu(View anchor){
        PopupMenu menu=new PopupMenu(getContext(),anchor);
        String[] labels={"Популярное","Новое","За сегодня","За неделю","За месяц","Взлетают","Работы дня"};
        String[] sorts={"popular","new","best","best","best","rising","day"};
        for(int i=0;i<labels.length;i++)menu.getMenu().add(0,i,i,t(labels[i]));
        menu.setOnMenuItemClickListener(item->{mark();int i=item.getItemId();section=sorts[i];if(i>=2&&i<=4)period=new String[]{"day","week","month"}[i-2];buildFilters();load(false);return true;});menu.show();
    }
    public void load(boolean more){
        if(!attached||released||!DevGramProfileCustomization.enabled()||more&&loading)return;
        if(!more){offset=0;next=-1;clearGallery();if(restoreScroll==null)gallery.scrollToPosition(0);authorGeneration++;if(author>0)authorHead(authorSnapshot());}else if(next<0)return;else offset=next;
        int request=++generation;loading=true;
        if(more)removePagingAction();info("Загружаю мастерскую…");
        if(section.equals("home")||section.equals("authors")){
            String route=section.equals("home")?"studio/workshop/home.json?scope="+scope:"studio/workshop/authors.json?mode="+(period.equals("ratio")?"ratio":"likes");
            DevGramProfilePolicy.executeWorkshop(()->{JSONObject result=null;try{result=DevGramProfilePolicy.http("GET",route,null);}catch(Exception ignored){}JSONObject value=result;AndroidUtilities.runOnUIThread(()->receive(request,value,more));});
        }else{
            if(!more&&author>0)loadAuthor(author,authorGeneration);
            JSONObject data=DevGramStudioActivity.json("section",section,"kind",kind,"period",period.equals("ratio")?"week":period,"q",query,"author",author,"offset",offset,"scope",scope);
            DevGramProfilePolicy.request(host.getCurrentAccount(),"studio/workshop/browse",data,(result,error)->receive(request,result,more));
        }
    }
    private void receive(int request,JSONObject result,boolean more){
        if(request!=generation||!attached)return;loading=false;
        if(!more){clearGallery();if(author>0)authorHead(authorSnapshot());}else removeProgress();
        if(result==null||!result.optBoolean("ok")){info("Не удалось загрузить мастерскую");action("Попробовать снова",()->{if(more){removePagingAction();removeProgress();}load(more);});return;}
        if(section.equals("home")){
            JSONObject shelves=result.optJSONObject("shelves");
            for(String type:new String[]{"profile","frame"}){
                title(type.equals("profile")?"Профиль дня":"Рамка дня");
                JSONObject shelf=shelves==null?null:shelves.optJSONObject(type),featured=shelf==null?null:shelf.optJSONObject("featured");
                if(featured!=null)append(new Entry(FEATURED,null,featured,null));else info(type.equals("profile")?"Профиль дня появится после первой публикации":"Рамка дня появится после первой публикации");
                title(type.equals("profile")?"Профили":"Рамки");
                JSONArray records=shelf==null?null:shelf.optJSONArray("works");
                if(records!=null&&records.length()>0)shelf(records);else info("Новые работы появятся здесь после проверки");
                action("Смотреть все",()->pick("popular",type));
            }
            title("Взлетают");JSONArray rising=result.optJSONArray("rising");if(rising!=null&&rising.length()>0)shelf(rising);else info("Новые работы появятся здесь после проверки");
            title("Топ авторов");JSONArray authors=result.optJSONArray("authors");if(authors!=null&&authors.length()>0)authorRows(authors);else info("Пока некого показать");
            action("Все авторы",()->pick("authors",kind));
            action("Опубликовать работу",publish);action("Мои работы",()->pick("mine",kind));
        }else if(section.equals("authors"))renderAuthors(result.optJSONArray("authors"));
        else{
            JSONArray records=result.optJSONArray(section.equals("review")?"submissions":"works");
            if(!more)title(section.equals("mine")?"Мои работы":section.equals("liked")?"Мне нравится":section.equals("favorites")?"Избранное":section.equals("review")?"На проверке":kind.equals("frame")?"Рамки":"Профили");
            if(records==null||records.length()==0){if(!more)empty();}
            else cards(records);
            next=result.isNull("next")?-1:result.optInt("next",-1);
            if(next>=0)action("Загрузить ещё",()->load(true));
        }
        if(restoreScroll!=null){gallery.getLayoutManager().onRestoreInstanceState(restoreScroll);restoreScroll=null;}
    }
    private void shelf(JSONArray works){append(new Entry(SHELF,null,DevGramStudioActivity.json("works",works),null));}
    private JSONObject authorSnapshot(){return authorInfo!=null?authorInfo:DevGramStudioActivity.json("uid",author,"loading",true,"document",DevGramProfileDocument.get(author,host.getCurrentAccount()));}
    private void authorHead(JSONObject value){append(new Entry(AUTHOR_HEAD,null,value,null));}
    private void loadAuthor(long uid,int request){
        DevGramProfilePolicy.executeWorkshop(()->{JSONObject result=null;try{result=DevGramProfilePolicy.http("GET","studio/workshop/author/"+uid+".json",null);}catch(Exception ignored){}JSONObject received=result;AndroidUtilities.runOnUIThread(()->{
            if(!attached||released||uid!=author||request!=authorGeneration)return;
            if(received==null||!received.optBoolean("ok")){if(authorInfo==null)authorError(uid,request);return;}
            authorInfo=received.optJSONObject("author");if(authorInfo==null)return;
            int index=-1;for(int i=0;i<entries.size();i++)if(entries.get(i).type==AUTHOR_HEAD){index=i;break;}
            if(index<0){entries.add(0,new Entry(AUTHOR_HEAD,null,authorInfo,null));adapter.notifyItemInserted(0);}else{entries.set(index,new Entry(AUTHOR_HEAD,null,authorInfo,null));adapter.notifyItemChanged(index);}
        });});
    }
    private void authorError(long uid,int request){if(uid==author&&request==authorGeneration)action("Повторить загрузку автора",()->loadAuthor(uid,request));}
    private void clearGallery(){
        for(WorkHolder holder:new ArrayList<>(bound))holder.clear();
        for(DevGramWorkshopPreviewView preview:new ArrayList<>(previews))preview.release();previews.clear();
        for(DevGramWorkshopAuthorView head:new ArrayList<>(authorHeads))head.release();authorHeads.clear();
        entries.clear();adapter.notifyDataSetChanged();
    }
    private void append(Entry entry){entries.add(entry);adapter.notifyItemInserted(entries.size()-1);}
    private void removePagingAction(){if(!entries.isEmpty()&&entries.get(entries.size()-1).type==ACTION){int at=entries.size()-1;entries.remove(at);adapter.notifyItemRemoved(at);}}
    private void removeProgress(){if(!entries.isEmpty()&&entries.get(entries.size()-1).type==INFO){int at=entries.size()-1;entries.remove(at);adapter.notifyItemRemoved(at);}}
    private void title(String title){append(new Entry(HEADING,title,null,null));}
    private void info(String title){append(new Entry(INFO,title,null,null));}
    private void empty(){info("Здесь пока нет работ. Создайте своё оформление и отправьте его в мастерскую.");if(section.equals("mine"))action("Опубликовать работу",publish);}
    private void action(String title,Runnable click){append(new Entry(ACTION,title,null,click));}
    private void cards(JSONArray records){for(int i=0;i<records.length();i++){JSONObject work=records.optJSONObject(i);if(work!=null)append(new Entry(WORK,null,work,null));}}
    private String authorName(long uid){TLRPC.User user=host.getMessagesController().getUser(uid);return user==null?t("Автор")+" #"+uid:UserObject.getUserName(user);}
    private String personName(JSONObject value){long uid=value.optLong("uid");TLRPC.User user=host.getMessagesController().getUser(uid);if(user!=null)return UserObject.getUserName(user);String name=value.optString("name");return name.isEmpty()?authorName(uid):name;}
    private void resolve(JSONObject value,Runnable bound){long uid=value.optLong("uid");if(host.getMessagesController().getUser(uid)!=null)return;String username=value.optString("username");if(username.isEmpty()||!resolving.add(uid))return;host.getMessagesController().getUserNameResolver().resolve(username,peer->{resolving.remove(uid);if(attached&&!released&&peer!=null&&peer==uid)bound.run();});}
    private void openProfile(JSONObject value){long uid=value.optLong("uid");TLRPC.User user=host.getMessagesController().getUser(uid);if(user!=null){android.os.Bundle args=new android.os.Bundle();args.putLong("user_id",uid);host.presentFragment(new ProfileActivity(args));return;}String username=value.optString("username");if(username.isEmpty()){BulletinFactory.of(host).createErrorBulletin(t("Не удалось открыть профиль автора")).show();return;}host.getMessagesController().getUserNameResolver().resolve(username,peer->{if(!attached||released)return;if(peer!=null&&peer==uid)openProfile(value);else BulletinFactory.of(host).createErrorBulletin(t("Не удалось открыть профиль автора")).show();});}
    private DevGramWorkshopPreviewView preview(JSONObject work){DevGramWorkshopPreviewView view=new DevGramWorkshopPreviewView(getContext(),host.getCurrentAccount(),work,false);view.setActive(previewsActive);previews.add(view);return view;}
    private void releasePreview(DevGramWorkshopPreviewView view){if(view!=null){previews.remove(view);view.release();}}
    private final class WorkHolder extends RecyclerView.ViewHolder {
        final LinearLayout card;
        final FrameLayout previewBox;
        final TextView title,by,stats;
        DevGramWorkshopPreviewView preview;
        JSONObject work;
        WorkHolder(){this(false);}
        WorkHolder(boolean horizontal){
            super(DevGramStudioControls.column(getContext()));card=(LinearLayout)itemView;
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(horizontal?dp(150):-1,-2);lp.setMargins(dp(4),dp(5),dp(4),dp(5));card.setLayoutParams(lp);
            card.setPadding(0,0,0,dp(12));card.setBackground(DevGramStudioControls.surface(color(Theme.key_windowBackgroundWhite),0));
            previewBox=new FrameLayout(getContext()){@Override protected void onMeasure(int width,int height){super.onMeasure(width,MeasureSpec.makeMeasureSpec(Math.round(MeasureSpec.getSize(width)*476f/211),MeasureSpec.EXACTLY));}};card.addView(previewBox,new LinearLayout.LayoutParams(-1,-2));
            title=text("",14,true);title.setGravity(Gravity.CENTER);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(dp(12),0,dp(10),0);card.addView(title);
            by=text("",11,false);by.setGravity(Gravity.CENTER);by.setSingleLine(true);by.setEllipsize(android.text.TextUtils.TruncateAt.END);by.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));by.setPadding(dp(12),dp(4),dp(10),0);card.addView(by);
            stats=text("",12,true);stats.setGravity(Gravity.CENTER);stats.setTextColor(color(Theme.key_windowBackgroundWhiteBlueText));stats.setPadding(dp(12),dp(8),dp(10),0);card.addView(stats);
            card.setOnClickListener(v->{if(work!=null&&DevGramProfileCustomization.enabled())open.accept(work);});
        }
        void bind(JSONObject value){
            clear();work=value;bound.add(this);
            preview=preview(work);previewBox.addView(preview,new FrameLayout.LayoutParams(-1,-1));
            title.setText(work.optString("title"));JSONObject person=work.optJSONObject("author");if(person==null)person=DevGramStudioActivity.json("uid",work.optLong("uid"));String name=personName(person);by.setText(name);JSONObject identity=person;resolve(person,()->{if(work==value)by.setText(personName(identity));});by.setOnClickListener(v->showAuthor(value.optLong("uid")));
            String state=work.optString("state"),pending=work.optJSONObject("submission")==null?"":work.optJSONObject("submission").optString("status");
            stats.setText(section.equals("review")||state.equals("pending")||pending.equals("pending")?t("На проверке"):pending.equals("rejected")?t("Нужны изменения"):"♡ "+work.optInt(section.equals("best")?"period_likes":"likes")+"   ↓ "+work.optInt("installs"));
            card.setContentDescription(work.optString("title")+", "+name);
        }
        void clear(){if(preview!=null){releasePreview(preview);previewBox.removeAllViews();preview=null;}work=null;bound.remove(this);}
    }
    private final class GalleryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount(){return entries.size();}
        @Override public int getItemViewType(int position){return entries.get(position).type;}
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type){
            if(type==WORK)return new WorkHolder();
            if(type==SHELF)return new ShelfHolder();
            if(type==FEATURED)return new FeaturedHolder();
            if(type==AUTHOR)return new AuthorHolder();
            if(type==AUTHOR_HEAD)return new HeadHolder();
            TextView row=text("",type==HEADING?17:type==AUTHOR?15:14,type!=INFO);RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.setMargins(dp(4),dp(4),dp(4),dp(4));row.setLayoutParams(lp);row.setPadding(dp(12),dp(14),dp(12),dp(14));
            if(type==ACTION){row.setGravity(Gravity.CENTER);row.setTextColor(color(Theme.key_windowBackgroundWhiteBlueText));}
            if(type==INFO)row.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));
            if(type==ACTION||type==AUTHOR)row.setBackground(DevGramStudioControls.surface(color(Theme.key_windowBackgroundWhite),0));
            return new RecyclerView.ViewHolder(row){};
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){
            Entry entry=entries.get(position);if(holder instanceof WorkHolder){((WorkHolder)holder).bind(entry.work);return;}
            if(holder instanceof ShelfHolder){((ShelfHolder)holder).bind(entry.work.optJSONArray("works"));return;}
            if(holder instanceof FeaturedHolder){((FeaturedHolder)holder).bind(entry.work);return;}
            if(holder instanceof AuthorHolder){((AuthorHolder)holder).bind(entry.work);return;}
            if(holder instanceof HeadHolder){((HeadHolder)holder).bind(entry.work);return;}
            TextView row=(TextView)holder.itemView;row.setText(entry.type==AUTHOR?entry.title:t(entry.title));row.setOnClickListener(entry.action==null?null:v->{if(DevGramProfileCustomization.enabled())entry.action.run();});row.setClickable(entry.action!=null);
        }
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder){if(holder instanceof WorkHolder)((WorkHolder)holder).clear();if(holder instanceof FeaturedHolder)((FeaturedHolder)holder).clear();if(holder instanceof HeadHolder)((HeadHolder)holder).clear();if(holder instanceof ShelfHolder)((ShelfHolder)holder).clear();super.onViewRecycled(holder);}
    }
    private final class ShelfHolder extends RecyclerView.ViewHolder {
        final RecyclerView row;JSONArray records=new JSONArray();
        ShelfHolder(){super(new RecyclerView(getContext()));row=(RecyclerView)itemView;row.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));row.setPadding(0,0,0,dp(4));row.setLayoutManager(new LinearLayoutManager(getContext(),RecyclerView.HORIZONTAL,false));row.setNestedScrollingEnabled(false);row.setItemAnimator(null);row.setAdapter(new RecyclerView.Adapter<WorkHolder>(){
            @Override public int getItemCount(){return records.length();}
            @Override public WorkHolder onCreateViewHolder(ViewGroup parent,int type){return new WorkHolder(true);}
            @Override public void onBindViewHolder(WorkHolder holder,int position){holder.bind(records.optJSONObject(position));}
            @Override public void onViewRecycled(WorkHolder holder){holder.clear();}
        });}
        void bind(JSONArray values){clear();records=values==null?new JSONArray():values;row.getAdapter().notifyDataSetChanged();row.scrollToPosition(0);}
        void clear(){for(int i=0;i<row.getChildCount();i++){RecyclerView.ViewHolder holder=row.getChildViewHolder(row.getChildAt(i));if(holder instanceof WorkHolder)((WorkHolder)holder).clear();}records=new JSONArray();row.getRecycledViewPool().clear();}
    }
    private final class FeaturedHolder extends RecyclerView.ViewHolder {
        final LinearLayout card,info;final FrameLayout image;DevGramWorkshopPreviewView shot;
        FeaturedHolder(){super(new LinearLayout(getContext()));card=(LinearLayout)itemView;card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(4),dp(6),dp(4),dp(6));card.setLayoutParams(new RecyclerView.LayoutParams(-1,dp(200)));image=new FrameLayout(getContext());card.addView(image,new LinearLayout.LayoutParams(0,-1,1.18f));info=DevGramStudioControls.column(getContext());info.setGravity(Gravity.CENTER_VERTICAL);info.setPadding(dp(12),0,dp(4),0);card.addView(info,new LinearLayout.LayoutParams(0,-1,.82f));}
        void bind(JSONObject work){clear();shot=preview(work);image.addView(shot,LayoutHelper.createFrame(-1,-1));TextView title=text("",17,true);title.setText(work.optString("title"));title.setMaxLines(3);title.setEllipsize(TextUtils.TruncateAt.END);info.addView(title);JSONObject identity=work.optJSONObject("author");if(identity==null)identity=DevGramStudioActivity.json("uid",work.optLong("uid"));TextView by=text("",12,false);by.setText(personName(identity));by.setMaxLines(2);by.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));info.addView(by,LayoutHelper.createLinear(-1,-2,0,8,0,0));by.setOnClickListener(v->showAuthor(work.optLong("uid")));TextView likes=text("♡ "+work.optInt("likes")+"   ↓ "+work.optInt("installs"),12,true);likes.setTextColor(color(Theme.key_windowBackgroundWhiteBlueText));info.addView(likes,LayoutHelper.createLinear(-1,-2,0,10,0,0));TextView inspect=text("Открыть работу",13,true);inspect.setTextColor(color(Theme.key_windowBackgroundWhiteBlueText));info.addView(inspect,LayoutHelper.createLinear(-1,-2,0,10,0,0));card.setOnClickListener(v->open.accept(work));}
        void clear(){releasePreview(shot);shot=null;image.removeAllViews();info.removeAllViews();}
    }
    private final class HeadHolder extends RecyclerView.ViewHolder {
        DevGramWorkshopAuthorView head;
        HeadHolder(){super(new FrameLayout(getContext()));RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.setMargins(dp(4),dp(8),dp(4),dp(10));itemView.setLayoutParams(lp);}
        void bind(JSONObject value){clear();head=new DevGramWorkshopAuthorView(getContext(),host,value,()->openProfile(value));head.setActive(previewsActive);authorHeads.add(head);((FrameLayout)itemView).addView(head,LayoutHelper.createFrame(-1,-2));resolve(value,()->{if(head!=null&&head.isAttachedToWindow())head.person();});}
        void clear(){if(head!=null){authorHeads.remove(head);head.release();head=null;}((FrameLayout)itemView).removeAllViews();}
    }
    private final class AuthorHolder extends RecyclerView.ViewHolder {
        final TextView rank,name,facts,score;final BackupImageView avatar;JSONObject value;
        AuthorHolder(){super(new LinearLayout(getContext()));LinearLayout row=(LinearLayout)itemView;row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(10),dp(10),dp(10));row.setBackground(DevGramStudioControls.surface(color(Theme.key_windowBackgroundWhite),12));RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.setMargins(dp(4),dp(2),dp(4),dp(2));row.setLayoutParams(lp);rank=text("",15,true);rank.setTextColor(color(Theme.key_windowBackgroundWhiteBlueText));rank.setGravity(Gravity.CENTER);row.addView(rank,LayoutHelper.createLinear(24,-2));avatar=new BackupImageView(getContext());avatar.setRoundRadius(dp(20));row.addView(avatar,LayoutHelper.createLinear(40,40,6,0,0,0));LinearLayout identity=DevGramStudioControls.column(getContext());name=text("",14,true);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);identity.addView(name);facts=text("",11,false);facts.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));facts.setMaxLines(2);identity.addView(facts);row.addView(identity,LayoutHelper.createLinear(0,-2,1f,10,0,6,0));score=text("",13,true);score.setGravity(Gravity.CENTER);score.setPadding(dp(8),dp(5),dp(8),dp(5));score.setBackground(DevGramStudioControls.surface(color(Theme.key_windowBackgroundGray),8));row.addView(score,LayoutHelper.createLinear(-2,-2));row.setOnClickListener(v->{if(value!=null)showAuthor(value.optLong("uid"));});}
        void bind(JSONObject record){value=record;rank.setText(String.valueOf(record.optInt("place")));person();facts.setText(t("Работ")+": "+record.optInt("works")+" · ♡ "+record.optInt("likes"));score.setText(period.equals("ratio")&&section.equals("authors")?String.format(java.util.Locale.US,"%.1f",record.optDouble("ratio")):String.valueOf(record.optInt("likes")));resolve(record,()->{if(value==record)person();});}
        void person(){name.setText(personName(value));TLRPC.User user=host.getMessagesController().getUser(value.optLong("uid"));AvatarDrawable placeholder=new AvatarDrawable();if(user!=null){placeholder.setInfo(user);avatar.setForUserOrChat(user,placeholder);}else{placeholder.setInfo(value.optLong("uid"),personName(value),null);avatar.setImageDrawable(placeholder);}}
    }
    private void renderAuthors(JSONArray authors){title("Топ авторов");if(authors==null||authors.length()==0){info("Пока некого показать");return;}authorRows(authors);}
    private void authorRows(JSONArray authors){for(int i=0;i<authors.length();i++){JSONObject value=authors.optJSONObject(i);if(value==null)continue;value=DevGramProfileDocument.copy(value);try{value.put("place",i+1);}catch(Exception ignored){}append(new Entry(AUTHOR,null,value,null));}}
    private void updatePolicies(){
        boolean wasActive=customizationActive;customizationActive=DevGramProfileCustomization.enabled();
        if(!customizationActive){generation++;loading=false;clearGallery();info("Кастомизация профилей отключена");return;}
        if(!wasActive){load(false);return;}
        for(DevGramWorkshopAuthorView head:new ArrayList<>(authorHeads))head.refreshStyle();
        if(policyRevision!=DevGramProfilePolicy.revision()){policyRevision=DevGramProfilePolicy.revision();authorInfo=null;load(false);}
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;DevGramProfilePolicy.listen(policies);load(false);}
    @Override protected void onDetachedFromWindow(){attached=false;generation++;authorGeneration++;loading=false;DevGramProfilePolicy.unlisten(policies);AndroidUtilities.cancelRunOnUIThread(searchTask);if(railAnimation!=null)railAnimation.cancel();super.onDetachedFromWindow();}
    public void release(){released=true;generation++;authorGeneration++;loading=false;AndroidUtilities.cancelRunOnUIThread(searchTask);clearGallery();}
}
