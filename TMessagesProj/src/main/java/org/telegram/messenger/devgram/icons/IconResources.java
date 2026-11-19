package org.telegram.messenger.devgram.icons;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;

/** Resource interception, matching exteraGram's ExteraResources and preserving original assets. */
public final class IconResources extends Resources {
    final Resources source;
    IconResources(Resources source) {super(source.getAssets(),source.getDisplayMetrics(),source.getConfiguration());this.source=source;}
    @Override public Drawable getDrawableForDensity(int id,int density,Theme theme) throws NotFoundException {
        IconObserver.INSTANCE.log(id);
        int swiftgram = org.telegram.ui.Components.DevGramSwiftgramIcons.resolve(id);
        if (swiftgram != id) return source.getDrawableForDensity(swiftgram, density, theme);
        Drawable custom=IconManager.INSTANCE.getDrawable(id,density,this);
        return custom!=null?custom:source.getDrawableForDensity(IconManager.INSTANCE.baseIcon(id),density,theme);
    }
    @Override public Drawable getDrawable(int id,Theme theme) throws NotFoundException{return getDrawableForDensity(id,0,theme);}
    @Override public Drawable getDrawable(int id) throws NotFoundException{return getDrawableForDensity(id,0,null);}
}
