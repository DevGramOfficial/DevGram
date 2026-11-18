package org.telegram.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import java.util.*;
import java.util.function.Consumer;

/** Reference comments window: inline branches, right-hand hearts and fixed reply composer. */
public final class DevGramProfileCommentsSheet {
    private final BaseFragment host;
    private final long profile, viewer;
    private final int account;
    private final Theme.ResourcesProvider resources;
    private final LinkedHashMap<Long,JSONObject> roots = new LinkedHashMap<>();
    private final Map<Long,LinkedHashMap<Long,JSONObject>> threads = new HashMap<>();
    private final Map<Long,Long> threadCursor = new HashMap<>();
    private final Set<Long> threadMore = new HashSet<>(), threadLoading = new HashSet<>();
    private JSONArray bans = new JSONArray();
    private BottomSheet sheet;
    private EditText input;
    private TextView replyNote, blockedNote, send;
    private LinearLayout inputBar;
    private LibraryAdapter adapter;
    private JSONObject answering;
    private boolean closed,busy,loading,enabled,blocked,owner,more;
    private long cursor;
    private int generation;
    private String error;
    private final ArrayList<Dialog> children = new ArrayList<>();
    private final Runnable policy = () -> { if (!valid() && sheet != null) sheet.dismiss(); else if(adapter!=null)adapter.notifyDataSetChanged(); };

    private DevGramProfileCommentsSheet(BaseFragment host,long profile) {
        this.host=host;this.profile=profile;account=host.getCurrentAccount();viewer=host.getUserConfig().getClientUserId();resources=host.getResourceProvider();
    }
    public static void show(BaseFragment host,long profile) {
        if(host==null||host.getParentActivity()==null)return;
        DevGramProfileCommentsSheet controller=new DevGramProfileCommentsSheet(host,profile);
        if(controller.valid())controller.open();
    }
    private static int dp(float value){return AndroidUtilities.dp(value);}
    private String t(String value){return DevGramLocale.text(value);}
    private Context context(){return host.getParentActivity();}
    private boolean valid(){return !closed&&host.getParentActivity()!=null&&host.getCurrentAccount()==account&&UserConfig.getInstance(account).getClientUserId()==viewer&&DevGramProfileCustomization.enabled()&&DevGramProfilePolicy.allowed(profile,"profile.comments");}
    private int color(int key){return Theme.getColor(key,resources);}
    private TextView words(String text,int size,boolean bold){TextView view=DevGramStudioControls.text(context(),"",size,bold,resources);view.setText(text);return view;}
    private JSONObject fields(String action){return DevGramStudioActivity.json("action",action,"profile",profile);}

    private void open(){
        LinearLayout body=DevGramStudioControls.column(context());
        GradientDrawable background=new GradientDrawable();background.setColor(color(Theme.key_windowBackgroundWhite));background.setCornerRadii(new float[]{dp(14),dp(14),dp(14),dp(14),0,0,0,0});body.setBackground(background);
        LinearLayout title=new LinearLayout(context());title.setGravity(Gravity.CENTER_VERTICAL);title.setPadding(dp(16),dp(14),dp(8),dp(10));
        title.addView(words(t("Комментарии"),18,true),new LinearLayout.LayoutParams(0,-2,1));
        ImageView options=new ImageView(context());options.setImageResource(R.drawable.ic_ab_other);options.setScaleType(ImageView.ScaleType.CENTER);options.setColorFilter(color(Theme.key_windowBackgroundWhiteGrayIcon));options.setContentDescription(t("Действия с комментариями"));options.setOnClickListener(v->menu());title.addView(options,LayoutHelper.createLinear(36,36));body.addView(title);
        RecyclerListView list=new RecyclerListView(context(),resources){@Override protected void onMeasure(int w,int h){super.onMeasure(w,DevGramStudioControls.sheetHeightSpec(h,.5f));}};
        list.setLayoutManager(new LinearLayoutManager(context()));list.setPadding(0,0,0,dp(8));list.setClipToPadding(true);list.setAdapter(adapter=new LibraryAdapter());body.addView(list,LayoutHelper.createLinear(-1,-2));
        replyNote=words("",13,false);replyNote.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));replyNote.setPadding(dp(16),dp(8),dp(16),0);replyNote.setVisibility(View.GONE);replyNote.setOnClickListener(v->{answering=null;updateInput();});body.addView(replyNote);
        inputBar=new LinearLayout(context());inputBar.setGravity(Gravity.CENTER_VERTICAL);inputBar.setPadding(dp(12),dp(8),dp(8),dp(10));
        input=new EditText(context());input.setTextSize(15);input.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));input.setHintTextColor(color(Theme.key_windowBackgroundWhiteHintText));input.setHint(t("Комментарий…"));input.setBackground(null);input.setMaxLines(4);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});inputBar.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        send=words(t("Отправить"),15,true);send.setPadding(dp(12),dp(8),dp(12),dp(8));send.setTextColor(color(Theme.key_windowBackgroundWhiteBlueText));send.setOnClickListener(v->send());inputBar.addView(send);body.addView(inputBar);
        blockedNote=words("",13,false);blockedNote.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));blockedNote.setGravity(Gravity.CENTER);blockedNote.setPadding(dp(16),dp(14),dp(16),dp(16));body.addView(blockedNote);
        sheet=new BottomSheet.Builder(context(),true,resources).setCustomView(body).create();sheet.fixNavigationBarKey(Theme.key_windowBackgroundWhite);
        DevGramProfilePolicy.listen(policy);host.showDialog(sheet,dialog->{closed=true;generation++;DevGramProfilePolicy.unlisten(policy);for(Dialog child:new ArrayList<>(children))child.dismiss();children.clear();});
        load(true);
    }
    private void child(AlertDialog dialog){if(!valid())return;children.add(dialog);dialog.setOnDismissListener(d->children.remove(dialog));dialog.show();}
    private String name(JSONObject item){long uid=item.optLong("uid");org.telegram.tgnet.TLRPC.User user=host.getMessagesController().getUser(uid);JSONObject author=item.optJSONObject("author");return user!=null?UserObject.getUserName(user):author!=null&&!author.optString("name").isEmpty()?author.optString("name"):t("Пользователь");}
    private String time(long seconds){long elapsed=System.currentTimeMillis()/1000-seconds;if(seconds<=0||elapsed<0)return "";if(elapsed<60)return t("только что");if(elapsed<3600)return elapsed/60+t(" мин");if(elapsed<86400)return elapsed/3600+t(" ч");if(elapsed<172800)return t("вчера");return new java.text.SimpleDateFormat("d MMM",DevGramLocale.isRussian()?new Locale("ru"):Locale.US).format(new Date(seconds*1000));}
    private void openUser(JSONObject item){if(!valid())return;long uid=item.optLong("uid");JSONObject author=item.optJSONObject("author");String username=author==null?"":author.optString("username").replaceFirst("^@","");sheet.dismiss();if(username.matches("[A-Za-z0-9_]{5,32}"))Browser.openUrl(context(),"https://t.me/"+username);else if(host.getMessagesController().getUser(uid)!=null){Bundle args=new Bundle();args.putLong("user_id",uid);host.presentFragment(new ProfileActivity(args));}else Toast.makeText(context(),t("Не удалось открыть профиль"),Toast.LENGTH_SHORT).show();}
    private void reply(JSONObject item){if(!valid()||blocked||!enabled)return;answering=item;updateInput();input.requestFocus();AndroidUtilities.showKeyboard(input);}
    private void updateInput(){
        boolean writable=enabled&&!blocked;inputBar.setVisibility(writable?View.VISIBLE:View.GONE);blockedNote.setVisibility(writable||loading||error!=null?View.GONE:View.VISIBLE);blockedNote.setText(t(blocked?"Вы не можете оставлять комментарии в этом профиле":"Комментарии в этом профиле выключены"));
        send.setEnabled(!busy&&!loading);send.setAlpha(busy||loading?.5f:1);
        replyNote.setVisibility(answering!=null&&writable?View.VISIBLE:View.GONE);if(answering!=null)replyNote.setText(t("Ответ ")+name(answering)+t(" · отменить"));
    }
    private void send(){if(!valid()||busy||loading||!enabled||blocked||!DevGramAppearanceAccess.check(host,"profile.comments"))return;String text=input.getText().toString().trim();if(text.isEmpty())return;JSONObject target=answering;JSONObject request=fields("add");try{request.put("text",text).put("parent",target==null?0:target.optLong("id"));}catch(Exception ignored){}
        post(request,ok->{if(ok){input.setText("");answering=null;updateInput();if(target!=null){long root=target.optLong("root",0);thread(root==0?target.optLong("id"):root,true);}}});}
    private void load(boolean reset){if(!valid()||busy||loading)return;loading=true;error=null;int request=++generation;JSONObject fields=fields("get");try{fields.put("cursor",reset?0:cursor);}catch(Exception ignored){}render();
        DevGramProfilePolicy.request(account,"studio/comments",fields,(value,failure)->{if(!valid()||request!=generation)return;loading=false;if(value==null)error=failure;else accept(value,reset,true);render();});}
    private void accept(JSONObject value,boolean reset,boolean pagination){
        enabled=value.optBoolean("enabled");blocked=value.optBoolean("blocked");owner=value.optBoolean("owner");bans=value.optJSONArray("bans");if(bans==null)bans=new JSONArray();
        if(reset){roots.clear();threads.clear();threadMore.clear();threadCursor.clear();}
        JSONArray rows=value.optJSONArray("comments");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)roots.put(row.optLong("id"),row);}
        for(String key:new String[]{"changed","changedRoot"}){JSONObject row=value.optJSONObject(key);if(row==null)continue;long parent=row.optLong("root");if(parent==0)roots.put(row.optLong("id"),row);else if(threads.containsKey(parent))threads.get(parent).put(row.optLong("id"),row);}
        if(!enabled)roots.clear();if(pagination){cursor=value.optLong("next");more=value.optBoolean("more");}updateInput();
    }
    private void post(JSONObject fields,Consumer<Boolean> done){if(!valid()||busy||loading)return;busy=true;updateInput();int request=++generation;
        DevGramProfilePolicy.request(account,"studio/comments",fields,(value,failure)->{if(!valid()||request!=generation)return;busy=false;if(value==null)Toast.makeText(context(),t("Не удалось выполнить действие. Попробуйте ещё раз."),Toast.LENGTH_LONG).show();else accept(value,false,false);render();if(done!=null)done.accept(value!=null);});}
    private void thread(long root,boolean reset){if(!valid()||threadLoading.contains(root))return;threadLoading.add(root);int request=generation;JSONObject fields=fields("get");try{fields.put("root",root).put("cursor",reset?0:threadCursor.getOrDefault(root,0L));}catch(Exception ignored){}render();
        DevGramProfilePolicy.request(account,"studio/comments",fields,(value,failure)->{threadLoading.remove(root);if(!valid())return;if(request!=generation){render();return;}if(value==null){Toast.makeText(context(),t("Не удалось загрузить комментарии"),Toast.LENGTH_SHORT).show();}else{LinkedHashMap<Long,JSONObject> rows=reset?new LinkedHashMap<>():threads.getOrDefault(root,new LinkedHashMap<>());JSONArray incoming=value.optJSONArray("comments");if(incoming!=null)for(int i=0;i<incoming.length();i++){JSONObject row=incoming.optJSONObject(i);if(row!=null&&row.optLong("id")!=root)rows.put(row.optLong("id"),row);}threads.put(root,rows);threadCursor.put(root,value.optLong("next"));if(value.optBoolean("more"))threadMore.add(root);else threadMore.remove(root);}render();});}
    private void moderate(JSONObject comment){if(!valid()||busy)return;boolean mine=comment.optLong("uid")==viewer;boolean canBlock=owner&&!mine;String user=name(comment);
        AlertDialog.Builder menu=new AlertDialog.Builder(context(),resources).setTitle(t("Комментарий")).setMessage(t(canBlock?"Удалить комментарий или заблокировать автора?":"Удалить комментарий?"));
        if(owner||mine)menu.setPositiveButton(t("Удалить"),(d,w)->{JSONObject request=fields("delete");try{request.put("comment",comment.optLong("id"));}catch(Exception ignored){}post(request,null);});
        if(canBlock)menu.setNeutralButton(t("Заблокировать"),(d,w)->child(new AlertDialog.Builder(context(),resources).setTitle(t("Заблокировать комментатора")+" «"+user+"»?").setMessage(t("Его комментарии удалятся, новые он оставить не сможет.")).setPositiveButton(t("Заблокировать"),(x,y)->block(comment.optLong("uid"),true)).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).create()));
        menu.setNegativeButton(DevGramLocale.getString(R.string.Cancel),null);child(menu.create());
    }
    private void block(long uid,boolean value){JSONObject fields=fields("block");try{fields.put("user",uid).put("blocked",value);}catch(Exception ignored){}post(fields,ok->{if(ok)load(true);});}
    private void menu(){ArrayList<String> labels=new ArrayList<>();labels.add(t("Обновить"));if(owner)labels.add(t("Заблокированные комментаторы"));child(new AlertDialog.Builder(context(),resources).setItems(labels.toArray(new String[0]),(d,w)->{if(w==0)load(true);else bans();}).create());}
    private void bans(){if(bans.length()==0){Toast.makeText(context(),t("Нет заблокированных комментаторов"),Toast.LENGTH_SHORT).show();return;}String[] names=new String[bans.length()];for(int i=0;i<names.length;i++){org.telegram.tgnet.TLRPC.User user=host.getMessagesController().getUser(bans.optLong(i));names[i]=user==null?bans.optString(i):UserObject.getUserName(user);}child(new AlertDialog.Builder(context(),resources).setTitle(t("Разблокировать комментатора")).setItems(names,(d,w)->block(bans.optLong(w),false)).create());}

    private static final class Entry { final String key,signature;final JSONObject comment;final long root;final String label;Entry(String key,JSONObject comment,long root,String label){this.key=key;this.comment=comment==null?null:DevGramProfileDocument.copy(comment);this.root=root;this.label=label;signature=comment==null?label:this.comment.toString();} }
    private void render(){if(adapter==null)return;updateInput();ArrayList<Entry> entries=new ArrayList<>();ArrayList<JSONObject> rows=new ArrayList<>(roots.values());rows.sort((a,b)->Long.compare(b.optLong("id"),a.optLong("id")));
        for(JSONObject row:rows){if(row.optBoolean("deleted")&&row.optInt("replies")==0)continue;long id=row.optLong("id");entries.add(new Entry("c"+id,row,0,null));LinkedHashMap<Long,JSONObject> replies=threads.get(id);if(replies!=null){ArrayList<JSONObject> nested=new ArrayList<>(replies.values());nested.sort((a,b)->Long.compare(a.optLong("id"),b.optLong("id")));for(JSONObject reply:nested)entries.add(new Entry("c"+reply.optLong("id"),reply,id,null));}
            if(row.optInt("replies")>0)entries.add(new Entry("t"+id,null,id,t(replies==null?"Показать ответы":"Скрыть ответы")+" · "+row.optInt("replies")));
            if(threadMore.contains(id))entries.add(new Entry("m"+id,null,id,t("Загрузить ещё")));if(threadLoading.contains(id))entries.add(new Entry("s"+id,null,0,t("Загружаю…")));
        }
        if(loading||error!=null||entries.isEmpty())entries.add(new Entry("state",null,0,t(loading?"Загружаю…":error!=null?"Не удалось загрузить комментарии":"Пока нет комментариев")));
        if(more&&!loading)entries.add(new Entry("more",null,0,t("Загрузить ещё")));
        adapter.replace(entries);
    }
    private final class LibraryAdapter extends RecyclerListView.SelectionAdapter {
        ArrayList<Entry> entries=new ArrayList<>();
        void replace(ArrayList<Entry> next){ArrayList<Entry> old=entries;DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback(){public int getOldListSize(){return old.size();}public int getNewListSize(){return next.size();}public boolean areItemsTheSame(int a,int b){return old.get(a).key.equals(next.get(b).key);}public boolean areContentsTheSame(int a,int b){return old.get(a).signature.equals(next.get(b).signature);}});entries=next;diff.dispatchUpdatesTo(this);}
        public boolean isEnabled(RecyclerView.ViewHolder holder){return false;}public int getItemCount(){return entries.size();}public int getItemViewType(int p){return entries.get(p).comment==null?1:0;}
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type){View view=type==0?new CommentRow():words("",14,false);view.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new RecyclerListView.Holder(view);}
        public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){Entry entry=entries.get(position);if(holder.itemView instanceof CommentRow){((CommentRow)holder.itemView).bind(entry);return;}TextView text=(TextView)holder.itemView;text.setText(entry.label);text.setPadding(dp(16+(entry.root>0?26:0)),dp(12),dp(16),dp(12));text.setTextColor(color(entry.key.startsWith("t")||entry.key.startsWith("m")?Theme.key_windowBackgroundWhiteBlueText:Theme.key_windowBackgroundWhiteGrayText));text.setOnClickListener(v->{if(entry.key.equals("more"))load(false);else if(entry.key.startsWith("t")){if(threads.remove(entry.root)==null)thread(entry.root,true);else{threadMore.remove(entry.root);render();}}else if(entry.key.startsWith("m"))thread(entry.root,false);else if(error!=null)load(true);});}
    }
    private final class CommentRow extends LinearLayout {
        final View branch;
        final LinearLayout content,details;
        final BackupImageView avatar;
        final TextView person,date,replyTo,message,answer;
        final DevGramCommentLikeView like;
        JSONObject value;
        CommentRow(){super(context());setOrientation(HORIZONTAL);branch=new View(context());branch.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color(Theme.key_windowBackgroundWhiteGrayText),90));LinearLayout.LayoutParams edge=new LinearLayout.LayoutParams(dp(2),-1);edge.setMargins(dp(11),0,dp(11),dp(10));addView(branch,edge);
            content=new LinearLayout(context());content.setOrientation(HORIZONTAL);addView(content,new LinearLayout.LayoutParams(0,-2,1));
            avatar=new BackupImageView(context());avatar.setRoundRadius(dp(20));content.addView(avatar,LayoutHelper.createLinear(40,40,0,0,12,0));
            details=DevGramStudioControls.column(context());content.addView(details,new LinearLayout.LayoutParams(0,-2,1));LinearLayout heading=new LinearLayout(context());heading.setGravity(Gravity.CENTER_VERTICAL);
            person=new TextView(context()){@Override protected void onDraw(android.graphics.Canvas canvas){int[] colors=DevGramNickColor.of(value==null?0:value.optLong("uid"));int next=colors==null?color(Theme.key_windowBackgroundWhiteBlackText):colors[0];if(getCurrentTextColor()!=next)setTextColor(next);super.onDraw(canvas);}};person.setTypeface(AndroidUtilities.bold());person.setTextSize(13);person.setSingleLine();person.setEllipsize(TextUtils.TruncateAt.END);heading.addView(person,new LinearLayout.LayoutParams(0,-2,1));
            date=words("",12,false);date.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));date.setPadding(dp(6),0,0,0);heading.addView(date);details.addView(heading);
            replyTo=words("",12,false);replyTo.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));replyTo.setSingleLine();replyTo.setEllipsize(TextUtils.TruncateAt.END);details.addView(replyTo);
            message=words("",15,false);message.setPadding(0,dp(2),0,0);details.addView(message);
            answer=words(t("Ответить"),12,false);answer.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));answer.setPadding(0,dp(6),dp(8),dp(2));details.addView(answer);
            like=new DevGramCommentLikeView(context(),color(Theme.key_windowBackgroundWhiteGrayText));content.addView(like);
            avatar.setOnClickListener(v->openUser(value));person.setOnClickListener(v->openUser(value));answer.setOnClickListener(v->reply(value));setOnLongClickListener(v->{if(value!=null&&(owner||value.optLong("uid")==viewer)){moderate(value);return true;}return false;});
            like.setOnClickListener(v->{if(!valid()||busy||loading||value==null||value.optBoolean("deleted"))return;JSONObject request=fields("like");try{request.put("comment",value.optLong("id")).put("liked",!value.optBoolean("liked"));}catch(Exception ignored){}DevGramProfileCommentsSheet.this.post(request,null);});
        }
        void bind(Entry entry){value=entry.comment;boolean nested=entry.root>0;branch.setVisibility(nested?View.VISIBLE:View.GONE);content.setPadding(dp(nested?10:16),dp(8),dp(16),dp(8));ViewGroup.LayoutParams size=avatar.getLayoutParams();size.width=size.height=dp(nested?32:40);avatar.setLayoutParams(size);avatar.setRoundRadius(dp(nested?16:20));
            long uid=value.optLong("uid");org.telegram.tgnet.TLRPC.User user=host.getMessagesController().getUser(uid);AvatarDrawable fallback=new AvatarDrawable();if(user!=null){fallback.setInfo(user);avatar.setForUserOrChat(user,fallback);}else{fallback.setInfo(uid,name(value),"");avatar.setImageDrawable(fallback);}person.setText(name(value));date.setText(time(value.optLong("created")));message.setText(value.optBoolean("deleted")?t("Комментарий удалён"):value.optString("text"));
            long reply=value.optLong("replyUid");replyTo.setVisibility(reply>0?View.VISIBLE:View.GONE);org.telegram.tgnet.TLRPC.User target=host.getMessagesController().getUser(reply);replyTo.setText(t("в ответ ")+(target==null?Long.toString(reply):UserObject.getUserName(target)));like.state(value.optInt("likes"),value.optBoolean("liked"));like.setVisibility(value.optBoolean("deleted")?View.GONE:View.VISIBLE);like.setContentDescription(t("Лайки")+": "+value.optInt("likes"));answer.setVisibility(value.optBoolean("deleted")?View.GONE:View.VISIBLE);
        }
    }
}
