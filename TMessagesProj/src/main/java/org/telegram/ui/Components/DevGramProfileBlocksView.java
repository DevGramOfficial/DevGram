package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.DevGramProfileDocument;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.Theme;
import java.util.ArrayList;

/** Public native profile cards with the bounded reference component/CSS/BlockScript renderer. */
public class DevGramProfileBlocksView extends LinearLayout {
    private final Theme.ResourcesProvider resources;
    private final int account;
    private JSONObject document;
    private String signature;
    private long uid;
    private boolean rebuild,editing;
    private boolean imageCard;
    private int imageRadius;
    private final android.view.ViewOutlineProvider imageOutline = new android.view.ViewOutlineProvider() {
        @Override public void getOutline(View view, android.graphics.Outline outline) {
            int left=view.getPaddingLeft(),right=view.getWidth()-view.getPaddingRight();
            if(right<=left||view.getHeight()<=0){outline.setEmpty();return;}
            outline.setRoundRect(left,0,right,view.getHeight(),imageRadius);
        }
    };
    public void setEditing(boolean value){if(editing!=value){editing=value;rebuild=true;}}
    @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event){return editing||super.onInterceptTouchEvent(event);}
    private boolean motion() {
        if (!org.telegram.messenger.MessagesController.getGlobalMainSettings().getBoolean("devgramProfileMotion", true)) return false;
        android.os.PowerManager power = (android.os.PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        return (power == null || !power.isPowerSaveMode()) && android.provider.Settings.Global.getFloat(getContext().getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1) > 0;
    }
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (uid != 0 && isShown() && hasWindowFocus()) DevGramProfileDocument.refresh(uid);
            postDelayed(this, 30000);
        }
    };
    public DevGramProfileBlocksView(Context context, Theme.ResourcesProvider resources) {
        this(context,org.telegram.messenger.UserConfig.selectedAccount,resources);
    }
    public DevGramProfileBlocksView(Context context,int account,Theme.ResourcesProvider resources) {
        super(context); this.resources = resources; setOrientation(VERTICAL);
        this.account=account;
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
    }

    public void set(long uid, JSONObject document) {
        document=DevGramProfileDocument.effective(document,uid);
        String next = document.toString() + Theme.isCurrentThemeDark() + motion();
        JSONObject eventGraph=document.optJSONObject("frameGraph");JSONArray eventNodes=eventGraph==null?null:eventGraph.optJSONArray("nodes");
        if(eventNodes!=null)for(int n=0;n<eventNodes.length();n++){JSONObject event=eventNodes.optJSONObject(n);JSONArray values=event==null?null:event.optJSONArray("s");if(event!=null&&event.optInt("t")==55&&values!=null)next+=org.telegram.messenger.DevGramFrameEvents.checked(account,uid,values.optString(0));}
        if (uid == this.uid && next.equals(signature) && !rebuild) return;
        this.uid = uid; this.document = DevGramProfileDocument.copy(document); signature = next;
        build();
    }
    private void build() {
        release(); removeAllViews(); rebuild = false;
        if (document == null) return;
        boolean nativeRows=false;JSONArray inspect=document.optJSONArray("blocks");if(inspect!=null)for(int i=0;i<inspect.length();i++)if(inspect.optJSONObject(i)!=null&&inspect.optJSONObject(i).has("rowType")){nativeRows=true;break;}
        layoutRows(nativeRows);
        JSONArray blocks = document.optJSONArray("blocks"), integrations = document.optJSONArray("integrations");
        int color = document.optInt("cardColor");
        if(transparentRows())setBackgroundColor(Color.TRANSPARENT);
        else if (document.has("cardColor")) setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), (document.optInt("cardAlpha", 100) * 255 / 100 << 24) | color));
        else setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resources));
        if(document.optBoolean("_commentsOnly")){addView(new DevGramProfileCommentsView(getContext(),account,uid,document.optInt("commentsCount",3),resources,editing),new LinearLayout.LayoutParams(-1,-2));return;}
        if (!document.optString("clan").isEmpty()) addText("[" + document.optString("clan") + "]", true);
        if (!document.optString("badge").isEmpty()) addText(document.optString("badge"), true);
        JSONObject playing = document.optJSONObject("nowPlaying");
        if (playing != null && !hidden("music") && System.currentTimeMillis() / 1000 - playing.optLong("updated") < 150) {
            JSONObject track=DevGramProfileDocument.copy(playing),label=new JSONObject();
            try{track.put("state","playing").put("playing",true);label.put("title",DevGramLocale.text("Сейчас слушает"));}catch(Exception ignored){}
            DevGramMusicCardView card=new DevGramMusicCardView(getContext(),8,label,track,System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime(),resources,false,()->{},()->{});
            card.setLyricsTarget(account,uid,"");
            addView(card,LayoutHelper.createLinear(-1,-2));
        }
        if (blocks != null && !hidden("blocks")) for (int i = 0; i < Math.min(64, blocks.length()); i++) {
            JSONObject block = blocks.optJSONObject(i); if (block == null) continue;
            String type = block.optString("type"), title = block.optString("title"), text = block.optString("text"), url = block.optString("url");
            if(block.has("rowType")&&block.optString("css").isEmpty()&&block.optString("script").isEmpty()&&block.optString("components").isEmpty()){
                if((editing||org.telegram.messenger.DevGramProfileRowSpec.complete(block))&&(!block.optBoolean("ownOnly")||org.telegram.messenger.UserConfig.getInstance(account).getClientUserId()==org.telegram.messenger.DevGramCommunityProfiles.owner(uid))){
                    DevGramNativeProfileRow nativeRow=new DevGramNativeProfileRow(getContext(),account,uid,block,document,resources,editing);
                    if(imageCard)nativeRow.fillImageCard();
                    addView(nativeRow,new LinearLayout.LayoutParams(-1,-2));
                }
                continue;
            }
            String blockId=block.optString("id","legacy"+i);boolean event=false;JSONObject graph=document.optJSONObject("frameGraph");JSONArray nodes=graph==null?null:graph.optJSONArray("nodes");
            if(nodes!=null)for(int n=0;n<nodes.length();n++){JSONObject node=nodes.optJSONObject(n);JSONArray strings=node==null?null:node.optJSONArray("s"),values=node==null?null:node.optJSONArray("v");if(node!=null&&node.optInt("t")==55&&(values==null||values.optInt(1)==0)&&strings!=null&&blockId.equals(strings.optString(0))){event=true;break;}}
            if(event){org.telegram.ui.Cells.TextCheckCell toggle=new org.telegram.ui.Cells.TextCheckCell(getContext(),resources);toggle.setTextAndCheck(title.isEmpty()?DevGramLocale.text("Переключатель рамки"):title,org.telegram.messenger.DevGramFrameEvents.checked(account,uid,blockId),false);toggle.setOnClickListener(v->{if(!editing)org.telegram.messenger.DevGramFrameEvents.set(account,uid,blockId,!org.telegram.messenger.DevGramFrameEvents.checked(account,uid,blockId));});addView(toggle,new LinearLayout.LayoutParams(-1,-2));title="";}
            if (type.equals("code") || !block.optString("css").isEmpty() || !block.optString("script").isEmpty()||!block.optString("components").isEmpty()) { addCode(block); continue; }
            if (!title.isEmpty()) addText(title, true);
            if (type.equals("image") && !url.isEmpty()) {
                DevGramProfileImageView image = new DevGramProfileImageView(getContext()); image.setCrop(block); image.setRoundRadius(imageCard?0:AndroidUtilities.dp(12));image.setAspectFit(false);
                image.getImageReceiver().setAllowStartAnimation(block.optInt("mediaType")==1&&new DevGramProfilePainter().motion());image.getImageReceiver().setAutoRepeat(1);image.setImage(org.telegram.messenger.DevGramProfileTextures.imageUrl(url), block.optInt("mediaType")==1?"1200_800_g":"1200_800", null); addView(image, LayoutHelper.createLinear(-1, Math.max(80, Math.min(480, block.optInt("height", 180))), 0, imageCard?0:6, 0, imageCard?0:10));
                image.setOnClickListener(v -> open(url));
            }
            if (!text.isEmpty()) addText(text, false);
            if (!url.isEmpty() && (type.equals("link") || type.equals("music"))) {
                TextView link = addText(type.equals("music") ? DevGramLocale.text("Слушать") : url, false);
                link.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resources)); link.setOnClickListener(v -> open(url));
            }
        }
        if (integrations != null && !hidden("integrations")) for (int i = 0; i < Math.min(7, integrations.length()); i++) {
            JSONObject integration = integrations.optJSONObject(i); if (integration == null) continue;
            TextView link = addText(integration.optString("provider") + "  ·  " + integration.optString("handle"), true);
            link.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resources));
            link.setOnClickListener(v -> open(integrationUrl(integration)));
            TextView details = addText("", false); details.setVisibility(GONE);
            removeView(link); removeView(details);
            LinearLayout row = new LinearLayout(getContext()); row.setOrientation(HORIZONTAL); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            BackupImageView avatar = new BackupImageView(getContext()); avatar.setRoundRadius(AndroidUtilities.dp(22)); avatar.setVisibility(GONE);
            row.addView(avatar, LayoutHelper.createLinear(44, 44, 0, 0, 12, 0));
            LinearLayout texts = new LinearLayout(getContext()); texts.setOrientation(VERTICAL); texts.addView(link, LayoutHelper.createLinear(-1, -2)); texts.addView(details, LayoutHelper.createLinear(-1, -2));
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1)); addView(row, LayoutHelper.createLinear(-1, -2));
            String expected = signature;
            org.telegram.messenger.DevGramProfileIntegrations.load(uid, cards -> {
                if (!expected.equals(signature) || !org.telegram.messenger.DevGramProfilePolicy.allowed(uid, "profile.integrations")) return;
                for (int j = 0; j < cards.length(); j++) {
                    JSONObject card = cards.optJSONObject(j);
                    if (card == null || !card.optString("provider").equals(integration.optString("provider")) || !card.optString("handle").equals(integration.optString("handle"))) continue;
                    if (!card.optString("status").equals("ok")) return;
                    link.setText(integration.optString("provider") + "  ·  " + card.optString("name", integration.optString("handle")));
                    String image = card.optString("avatar");
                    if (image.startsWith("https://")) { avatar.setImage(image, "100_100", null); avatar.setVisibility(VISIBLE); }
                    String text = card.optString("bio", card.optString("state"));
                    if (card.has("repositories")) text = DevGramLocale.text("Репозитории") + ": " + card.optInt("repositories") + " · " + DevGramLocale.text("Подписчики") + ": " + card.optInt("followers");
                    if (!text.isEmpty()) { details.setText(text); details.setVisibility(VISIBLE); }
                }
            });
        }
    }
    private boolean imageOnly() {
        JSONArray rows=document.optJSONArray("blocks");
        if(rows==null||rows.length()!=1)return false;
        JSONObject row=rows.optJSONObject(0);
        return row!=null&&org.telegram.messenger.DevGramProfileRowSpec.type(row)==10
                &&row.optString("css").isEmpty()&&row.optString("script").isEmpty()&&row.optString("components").isEmpty()
                &&(row.has("rowType")||row.optString("title").isEmpty()&&row.optString("text").isEmpty());
    }
    private void layoutRows(boolean nativeRows) {
        imageCard=imageOnly();boolean painted=DevGramProfileSurfaces.painted(document);
        int side=imageCard?0:AndroidUtilities.dp(nativeRows?(painted?16:0):(painted?32:16));
        int vertical=imageCard||nativeRows?0:AndroidUtilities.dp(8);
        setPadding(side,vertical,side,vertical);
        if(imageCard){
            JSONObject row=document.optJSONArray("blocks").optJSONObject(0);
            int fallback=painted?(document.optInt("cardRadiusEnabled")==1?document.optInt("cardRadius",12):12):(nativeRows?16:12);
            imageRadius=AndroidUtilities.dp(Math.max(0,Math.min(48,row.optInt("radius",fallback))));
        }
        setClipToOutline(imageCard);
        setOutlineProvider(imageCard?imageOutline:android.view.ViewOutlineProvider.BACKGROUND);
        invalidateOutline();
    }
    public boolean hasImageCardOutline(){return imageCard;}
    public boolean transparentRows(){JSONArray rows=document==null?null:document.optJSONArray("blocks");if(rows==null||rows.length()!=1)return false;JSONObject row=rows.optJSONObject(0);if(row==null||!row.has("rowType"))return false;int type=org.telegram.messenger.DevGramProfileRowSpec.type(row);return type==3||type==4||type==6;}
    private boolean hidden(String key) {
        JSONArray hidden = document.optJSONArray("hiddenRows");
        if (hidden != null) for (int i = 0; i < hidden.length(); i++) if (key.equals(hidden.optString(i))) return true;
        return false;
    }
    private TextView addText(String text, boolean title) {
        TextView view = new TextView(getContext()); view.setText(text); view.setTextSize(title ? 16 : 14);
        if (document != null && document.has("cardColor")) {
            int background = 0xff000000 | document.optInt("cardColor");
            view.setTextColor(androidx.core.graphics.ColorUtils.calculateLuminance(background) < .4 ? Color.WHITE : 0xff181818);
        } else view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resources));
        if (title) view.setTypeface(AndroidUtilities.bold()); else view.setTextIsSelectable(true);
        view.setLineSpacing(AndroidUtilities.dp(2), 1); view.setPadding(0, AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7));
        addView(view, LayoutHelper.createLinear(-1, -2)); return view;
    }
    private void open(String link) { if (link.startsWith("https://") || link.startsWith("tg://")) Browser.openUrl(getContext(), link); }

    public static String integrationUrl(JSONObject item) {
        String handle = Uri.encode(item.optString("handle")), provider = item.optString("provider");
        switch (provider) {
            case "github": return "https://github.com/" + handle;
            case "discord": return "https://discord.com/users/" + handle;
            case "steam": return "https://steamcommunity.com/" + (handle.matches("[0-9]{17}") ? "profiles/" : "id/") + handle;
            case "spotify": return "https://open.spotify.com/user/" + handle;
            case "soundcloud": return "https://soundcloud.com/" + handle;
            case "yandex": return "https://music.yandex.ru/users/" + handle;
            case "lastfm": return "https://www.last.fm/user/" + handle;
            default: return "https://devgram.space";
        }
    }
    private static String html(String value) { return TextUtils.htmlEncode(value).replace("\n", "<br>"); }
    private final ArrayList<org.telegram.ui.Components.devgram.blocks.DevGramNativeBlockView> codeViews=new ArrayList<>();
    private void addCode(JSONObject block){
        org.telegram.ui.Components.devgram.blocks.DevGramNativeBlockView view=new org.telegram.ui.Components.devgram.blocks.DevGramNativeBlockView(getContext(),uid,resources);
        view.set(block);codeViews.add(view);addView(view,new LinearLayout.LayoutParams(-1,-2));
    }
    public void release(){for(org.telegram.ui.Components.devgram.blocks.DevGramNativeBlockView view:codeViews)view.release();codeViews.clear();}
    @Override protected void onDetachedFromWindow() { removeCallbacks(refresh); release(); rebuild = true; super.onDetachedFromWindow(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (rebuild) build(); removeCallbacks(refresh); postDelayed(refresh, 30000); }
}
