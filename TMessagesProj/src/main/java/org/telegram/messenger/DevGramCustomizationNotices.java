package org.telegram.messenger;

import org.json.*;
import org.telegram.ui.DevGramNoticesSheet;
import org.telegram.ui.Components.DevGramNoticePopup;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/** One foreground inbox per account; muted popup categories still retain all unread events. */
public final class DevGramCustomizationNotices {
    public static final String[] SECTIONS={"act","news","rest"};
    public static final class Item {
        public final long id,ts,who,subject;
        public final int section,amount,coverMessage;
        public final String code,src,target,body,headline,articleUrl,cover,nativeArticle;
        Item(JSONObject raw){id=raw.optLong("id");ts=raw.optLong("created");who=raw.optLong("actor");subject=raw.optLong("subject");amount=raw.optInt("amount",1);code=raw.optString("code");src=raw.optString("src");target=raw.optString("target");body=raw.optString("body");headline=raw.optString("title");articleUrl=raw.optString("articleUrl");cover=raw.optString("cover");nativeArticle=raw.optString("nativeArticle");coverMessage=raw.optInt("coverMessage");section=Arrays.asList(SECTIONS).indexOf(raw.optString("section"));}
        public String title(){if(!headline.isEmpty())return headline;String label;switch(code){case "profile_like":label="Лайкнули профиль";break;case "profile_comment":case "comment":label="Новый комментарий профиля";break;case "work_like":label="Лайкнули работу";break;case "work_comment":label="Новый комментарий к работе";break;case "canvas":label="Рисуют на полотне";break;case "work_approved":label="Работа опубликована";break;case "work_rejected":label="Работа не прошла проверку";break;case "news":label="Новости DevGram";break;default:label="Уведомление";}return DevGramLocale.text(label);}
        public String text(){if(code.equals("canvas"))return DevGramLocale.text("Точек: ")+amount;return body;}
        public boolean fresh(){return section>=0&&id>read[section];}
    }
    private static final LinkedHashMap<Long,Item> items=new LinkedHashMap<>();
    private static final CopyOnWriteArraySet<Runnable> listeners=new CopyOnWriteArraySet<>();
    private static final long[] read=new long[3],pendingRead=new long[3],next=new long[3];
    private static final int[] counts=new int[3];
    private static final boolean[] more={true,true,true};
    private static int account=-1,generation;
    private static long viewer,newest,lastAsked;
    private static boolean started,busy,primed,policyBound,lastAllowed;
    private static final int[] pendingSections=new int[3];
    private static String error;
    private static final Runnable tick=new Runnable(){public void run(){if(!started)return;if(allowed()&&!ApplicationLoader.mainInterfacePaused)refresh(false);AndroidUtilities.runOnUIThread(this,20000);}};
    private static final Runnable policy=()->{boolean next=allowed();if(started&&lastAllowed!=next){lastAllowed=next;if(!next){generation++;busy=false;items.clear();Arrays.fill(counts,0);DevGramNoticePopup.hide();changed();}else refresh(true);}};
    private DevGramCustomizationNotices(){}
    public static void wake(int selected){long uid=UserConfig.getInstance(selected).getClientUserId();if(uid==0)return;if(account!=selected||viewer!=uid){generation++;items.clear();Arrays.fill(read,0);Arrays.fill(pendingRead,0);Arrays.fill(pendingSections,0);Arrays.fill(counts,0);Arrays.fill(next,0);Arrays.fill(more,true);primed=false;busy=false;newest=lastAsked=0;error=null;account=selected;viewer=uid;DevGramNoticePopup.hide();}if(!policyBound){policyBound=true;DevGramProfilePolicy.listen(policy);}if(!started){started=true;AndroidUtilities.runOnUIThread(tick,20000);}lastAllowed=allowed();if(lastAllowed)refresh(false);}
    public static boolean allowed(){return account>=0&&UserConfig.selectedAccount==account&&viewer>0&&UserConfig.getInstance(account).getClientUserId()==viewer&&DevGramProfileCustomization.enabled()&&DevGramProfilePolicy.allowed(viewer,"profile.notices");}
    public static int account(){return account;}public static long viewer(){return viewer;}public static boolean loading(){return busy&&!primed;}public static String error(){return error;}public static boolean busy(){return busy;}
    public static void listen(Runnable callback){listeners.add(callback);}public static void forget(Runnable callback){listeners.remove(callback);}
    private static void changed(){for(Runnable listener:listeners)try{listener.run();}catch(Exception e){FileLog.e(e);}}
    public static int unread(){return counts[0]+counts[1]+counts[2];}public static int unread(int section){return section>=0&&section<3?counts[section]:0;}
    public static ArrayList<Item> of(int section){ArrayList<Item> selected=new ArrayList<>();for(Item item:items.values())if(item.section==section)selected.add(item);selected.sort((a,b)->Long.compare(b.id,a.id));return selected;}
    public static boolean more(int section){return more[section];}
    private static JSONObject json(Object... values){JSONObject result=new JSONObject();try{for(int i=0;i+1<values.length;i+=2)result.put((String)values[i],values[i+1]);}catch(Exception ignored){}return result;}
    public static void refresh(boolean force){if(!allowed()||busy||!force&&primed&&android.os.SystemClock.elapsedRealtime()-lastAsked<15000)return;lastAsked=android.os.SystemClock.elapsedRealtime();request(json("action","get","limit",50),-1,false);}
    public static void section(int section,boolean older){if(!allowed()||section<0||section>=3)return;if(busy){pendingSections[section]=older?2:1;return;}long before=older?next[section]:0;request(json("action","get","section",SECTIONS[section],"before",before,"limit",50),section,older);}
    public static void markRead(int section){if(!allowed()||section<0||section>=3)return;long cursor=0;for(Item item:of(section))cursor=Math.max(cursor,item.id);if(cursor<=read[section])return;pendingRead[section]=Math.max(pendingRead[section],cursor);pumpRead();}
    private static void pumpRead(){if(busy||!allowed())return;for(int i=0;i<3;i++)if(pendingRead[i]>read[i]){long cursor=pendingRead[i];pendingRead[i]=0;request(json("action","read","section",SECTIONS[i],"cursor",cursor,"limit",50),i,false);return;}for(int i=0;i<3;i++)if(pendingSections[i]>0){int next=pendingSections[i];pendingSections[i]=0;section(i,next==2);return;}}
    private static void request(JSONObject body,int section,boolean older){busy=true;int request=++generation;changed();DevGramProfilePolicy.request(account,"studio/notices",body,(result,failure)->{if(request!=generation)return;busy=false;if(!allowed())return;if(result==null){error=failure;changed();return;}error=null;long previous=newest;Item popup=null;JSONArray incoming=result.optJSONArray("items");JSONObject reads=result.optJSONObject("read"),unread=result.optJSONObject("counts");for(int i=0;i<3;i++){if(reads!=null)read[i]=Math.max(read[i],reads.optLong(SECTIONS[i]));if(unread!=null)counts[i]=unread.optInt(SECTIONS[i]);}if(incoming!=null)for(int i=0;i<incoming.length();i++){JSONObject row=incoming.optJSONObject(i);if(row==null)continue;Item item=new Item(row);if(item.id<=0||item.section<0)continue;items.put(item.id,item);newest=Math.max(newest,item.id);if(section<0&&primed&&item.id>previous&&item.fresh()&&pops(item.src)&&(popup==null||item.id>popup.id))popup=item;}
        reconcileNews(result.optJSONArray("newsIds"));
        if(section>=0&&body.optString("action").equals("get")){more[section]=result.optBoolean("more");long cursor=result.optLong("next");next[section]=older||next[section]==0?cursor:cursor==0?next[section]:Math.min(next[section],cursor);}while(items.size()>1000){Long oldest=Collections.min(items.keySet());items.remove(oldest);}primed=true;changed();if(popup!=null&&!ApplicationLoader.mainInterfacePaused)DevGramNoticePopup.show(account,popup);pumpRead();});}
    private static void reconcileNews(JSONArray live) {
        if (live == null) return;
        HashSet<Long> keep = new HashSet<>(); for (int i=0;i<live.length();i++) keep.add(live.optLong(i));
        Iterator<Map.Entry<Long,Item>> iterator = items.entrySet().iterator();
        while (iterator.hasNext()) { Map.Entry<Long,Item> entry=iterator.next(); if (entry.getValue().section==1&&!keep.contains(entry.getKey())) { DevGramNoticePopup.remove(entry.getKey()); iterator.remove(); } }
    }
    public static void removed(int selected, long id, String draft) {
        long owner=UserConfig.getInstance(selected).getClientUserId();String key="devgramNativeNewsDraft_"+selected+"_"+owner;
        try { JSONObject cached=new JSONObject(MessagesController.getGlobalMainSettings().getString(key,"{}")); if(!draft.isEmpty()&&draft.equals(cached.optString("id")))MessagesController.getGlobalMainSettings().edit().remove(key).apply(); }catch(Exception ignored){}
        if(selected!=account||viewer!=owner)return;
        generation++;busy=false;Item previous=items.remove(id);if(previous!=null&&previous.fresh())counts[1]=Math.max(0,counts[1]-1);
        DevGramNoticePopup.remove(id);changed();refresh(true);
    }
    public static boolean full(){return MessagesController.getGlobalMainSettings().getBoolean("devgramNoticeFull",false);}
    public static void full(boolean value){MessagesController.getGlobalMainSettings().edit().putBoolean("devgramNoticeFull",value).apply();}
    public static boolean pops(String source){return MessagesController.getGlobalMainSettings().getBoolean("devgramNoticePop_"+source,true);}
    public static void pops(String source,boolean value){MessagesController.getGlobalMainSettings().edit().putBoolean("devgramNoticePop_"+source,value).apply();}
}
