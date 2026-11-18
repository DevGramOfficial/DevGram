package org.telegram.ui.Components;

import org.json.JSONObject;
import org.telegram.messenger.DevGramLocale;
import org.telegram.messenger.DevGramProfileCustomization;
import org.telegram.messenger.DevGramProfilePolicy;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.DevGramAppearanceAccess;
import org.telegram.ui.DevGramStudioActivity;

/** A private work state owns votes; public metadata never overwrites a newer vote. */
public final class DevGramWorkshopVotesController {
    private final BaseFragment host;
    private final int account;
    private final long viewer,id;
    private final Runnable changed;
    private final Runnable policy=this::accessChanged;
    private boolean active=true,known,loading,busy,liked,favorite,social,install;
    private int likes,generation;
    private long refreshed;
    public DevGramWorkshopVotesController(BaseFragment host,long id,Runnable changed){this.host=host;account=host.getCurrentAccount();viewer=UserConfig.getInstance(account).getClientUserId();this.id=id;this.changed=changed;social=allowed("workshop.social");install=allowed("workshop.install");DevGramProfilePolicy.listen(policy);}
    private boolean current(){return active&&DevGramProfileCustomization.enabled()&&UserConfig.getInstance(account).getClientUserId()==viewer;}
    private boolean allowed(String feature){return current()&&DevGramProfilePolicy.allowed(viewer,feature);}
    private void accessChanged(){boolean s=allowed("workshop.social"),i=allowed("workshop.install");if(s==social&&i==install&&current())return;social=s;install=i;generation++;known=loading=busy=false;refreshed=0;changed.run();if(current())refresh();}
    public boolean busy(){return busy||loading;}
    public boolean known(){return known;}
    public boolean liked(){return liked;}
    public boolean favorite(){return favorite;}
    public int likes(){return likes;}
    public void release(){active=false;generation++;DevGramProfilePolicy.unlisten(policy);}
    private void accept(JSONObject result){JSONObject work=result.optJSONObject("work");if(work!=null){likes=Math.max(0,work.optInt("likes"));liked=work.optBoolean("liked");favorite=work.optBoolean("favorite");known=true;}changed.run();}
    public void refresh(){
        if(!current()||busy||loading||System.currentTimeMillis()-refreshed<15000)return;
        refreshed=System.currentTimeMillis();loading=true;int request=++generation;changed.run();
        DevGramProfilePolicy.request(account,"studio/workshop",DevGramStudioActivity.json("action","state","id",id),(result,error)->{if(!current()||request!=generation)return;loading=false;if(result!=null)accept(result);else{refreshed=0;changed.run();}});
    }
    public void toggle(boolean save){
        String feature=save?"workshop.install":"workshop.social";
        if(!current()||busy||loading||!DevGramAppearanceAccess.check(host,feature))return;
        if(!known){refresh();return;}
        boolean oldLiked=liked,oldFavorite=favorite;int oldLikes=likes,request=++generation;
        if(save)favorite=!favorite;else{liked=!liked;likes=Math.max(0,likes+(liked?1:-1));}busy=true;changed.run();
        DevGramProfilePolicy.request(account,"studio/workshop",DevGramStudioActivity.json("action",save?"favorite":"like","id",id,save?"favorite":"liked",save?favorite:liked),(result,error)->{
            if(!current()||request!=generation)return;busy=false;
            if(result!=null)accept(result);else{liked=oldLiked;favorite=oldFavorite;likes=oldLikes;changed.run();if(host.getParentActivity()!=null)BulletinFactory.of(host).createErrorBulletin(DevGramLocale.text("Не удалось выполнить действие. Проверьте доступ и соединение.")).show();}
        });
    }
}
