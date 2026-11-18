package org.telegram.messenger.devgram.icons;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.List;

/** Grants SystemUI read-only access to rasterized notification assets, never to an archive directory. */
public final class IconPackProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "image/png";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"r".equals(mode))throw new SecurityException("Read-only icon provider");
        List<String> path=uri.getPathSegments();if(path.size()!=3||!path.get(0).equals("icon")||!IconPackStorage.validId(path.get(1))||!path.get(2).matches("[A-Za-z0-9_]+"))throw new FileNotFoundException("Invalid icon URI");
        try{return ParcelFileDescriptor.open(IconManager.INSTANCE.notificationFile(path.get(1),path.get(2)),ParcelFileDescriptor.MODE_READ_ONLY);}catch(Exception e){try{return ParcelFileDescriptor.open(IconManager.INSTANCE.defaultNotificationFile(),ParcelFileDescriptor.MODE_READ_ONLY);}catch(Exception unavailable){throw new FileNotFoundException("Icon not available");}}
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
