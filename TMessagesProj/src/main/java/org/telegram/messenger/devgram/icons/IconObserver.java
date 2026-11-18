package org.telegram.messenger.devgram.icons;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;
import java.util.*;

/** Weak per-screen sets, identical to the reference picker's resource observer. */
public final class IconObserver {
    public static final IconObserver INSTANCE=new IconObserver();
    private final WeakHashMap<BaseFragment,Set<Integer>> sources=new WeakHashMap<>();
    private IconObserver(){}
    public void log(int id){
        if(!IconManager.INSTANCE.ready()||IconManager.INSTANCE.editingId()==null)return;
        BaseFragment f=LaunchActivity.getSafeLastFragment();if(f==null)return;
        String name=IconManager.INSTANCE.resourceName(id);if(IconManager.INSTANCE.blacklisted(name))return;
        synchronized(sources){sources.computeIfAbsent(f,k->new HashSet<>()).add(id);}
    }
    public List<Integer> used(){BaseFragment f=LaunchActivity.getSafeLastFragment();if(f==null)return Collections.emptyList();synchronized(sources){Set<Integer> ids=sources.get(f);return ids==null?Collections.emptyList():new ArrayList<>(ids);}}
    public void clear(){synchronized(sources){sources.clear();}}
}
