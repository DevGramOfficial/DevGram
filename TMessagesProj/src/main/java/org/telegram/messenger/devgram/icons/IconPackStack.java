package org.telegram.messenger.devgram.icons;
import java.util.*;
/** Resolution order shared by the list, resource loader and notification icons. */
public final class IconPackStack {
    private IconPackStack(){}
    public static Map<String,IconPack> owners(List<String> order,Map<String,IconPack> installed){
        Map<String,IconPack> result=new HashMap<>();
        for(String id:order){IconPack pack=installed.get(id);if(pack!=null&&!pack.isBase())for(String name:pack.icons.keySet())result.putIfAbsent(name,pack);}
        return Collections.unmodifiableMap(result);
    }
}
