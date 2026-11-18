package org.telegram.ui.devgram.icons;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.devgram.icons.IconManager;
import org.telegram.messenger.devgram.icons.IconPack;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;
import java.util.ArrayList;

final class IconImageViewer {
    static void open(BaseFragment host,IconPack pack,int resource){
        BaseFragment target=host==null?LaunchActivity.getSafeLastFragment():host;
        if(target==null||target.isFinished||target.getParentActivity()==null)return;
        IconManager.INSTANCE.previewImage(pack,resource,file->{
            if(target.isFinished||target.getParentActivity()==null||LaunchActivity.getSafeLastFragment()!=target)return;
            if(file==null){BulletinFactory.of(target).createErrorBulletin(IconUi.t("Не удалось открыть изображение")).show();return;}
            ArrayList<Object> photos=new ArrayList<>();photos.add(new MediaController.PhotoEntry(0,0,0,file.getAbsolutePath(),0,false,0,0,0));
            PhotoViewer viewer=PhotoViewer.getInstance();if(viewer.isVisible())return;
            viewer.setParentActivity(target);viewer.openPhotoForSelect(photos,0,PhotoViewer.SELECT_TYPE_NO_SELECT,false,new PhotoViewer.EmptyPhotoViewerProvider(){
                @Override public boolean allowCaption(){return false;}
                @Override public boolean canCaptureMorePhotos(){return false;}
                @Override public boolean allowSendingSubmenu(){return false;}
            },null);
        });
    }
}
