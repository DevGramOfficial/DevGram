package org.telegram.messenger.devgram.icons;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.devgram.icons.InstallIconPackBottomSheet;

public final class IconPackIntents {
    private static final int PICK_PACK=9261,PICK_IMAGE=9262;
    private static final ExecutorService io=Executors.newSingleThreadExecutor();
    private static Consumer<Image> imageCallback;
    private static Uri pending;
    private static boolean opening;
    private IconPackIntents(){}
    public static boolean isPackName(String name){return name!=null&&name.toLowerCase(Locale.ROOT).endsWith(".dgicons");}
    public static final class Image {public File file;public String name,extension;public int width,height;}
    public static void pickPack(Activity activity){if(activity==null)return;activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),PICK_PACK);}
    public static void pickImage(Activity activity,boolean files,Consumer<Image> callback){if(activity==null)return;imageCallback=callback;Intent i=new Intent(files?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_GET_CONTENT).setType(files?"*/*":"image/*").addCategory(Intent.CATEGORY_OPENABLE);activity.startActivityForResult(i,PICK_IMAGE);}
    public static boolean onActivityResult(int code,int result,Intent data){
        if(code!=PICK_PACK&&code!=PICK_IMAGE)return false;
        Consumer<Image> callback=imageCallback;if(code==PICK_IMAGE)imageCallback=null;
        if(result==Activity.RESULT_OK&&data!=null&&data.getData()!=null){if(code==PICK_PACK)openUri(data.getData());else if(callback!=null)copyImage(data.getData(),callback);}else if(code==PICK_IMAGE&&callback!=null)callback.accept(null);return true;
    }
    public static boolean handleIntent(Intent intent){
        if(intent==null||!Intent.ACTION_VIEW.equals(intent.getAction())||intent.getData()==null)return false;
        Uri uri=intent.getData();String name=name(uri);
        if(!isPackName(name))return false;pending=uri;AndroidUtilities.runOnUIThread(IconPackIntents::flushPending,500);return true;
    }
    public static void flushPending(){if(pending==null||opening||!IconManager.INSTANCE.ready()||SharedConfig.appLocked||SharedConfig.isWaitingForPasscodeEnter)return;BaseFragment f=LaunchActivity.getSafeLastFragment();if(f==null||f.getParentActivity()==null)return;Uri uri=pending;pending=null;openUri(uri);}
    public static void openUri(Uri uri){
        if(!isPackName(name(uri))){showError(new IOException("EXTENSION"));return;}
        io.execute(()->{try{File f=copy(uri,IconPackStorage.MAX_TOTAL,".dgicons");AndroidUtilities.runOnUIThread(()->open(f,true));}catch(Exception e){showError(e);}});
    }
    public static void open(File file,boolean deleteAfter){
        if(opening)return;opening=true;
        IconManager.INSTANCE.preview(file,p->{opening=false;if(deleteAfter)file.delete();BaseFragment f=LaunchActivity.getSafeLastFragment();if(f==null||f.getParentActivity()==null){p.close();return;}f.showDialog(new InstallIconPackBottomSheet(f,p));},()->{opening=false;if(deleteAfter)file.delete();});
    }
    public static void previewFailed(){opening=false;}
    public static String name(Uri uri){
        if(uri==null)return "";if("file".equals(uri.getScheme()))return new File(uri.getPath()).getName();
        try(Cursor c=ApplicationLoader.applicationContext.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}
        return uri.getLastPathSegment()==null?"":uri.getLastPathSegment();
    }
    private static File temp(String suffix)throws IOException {File dir=new File(ApplicationLoader.applicationContext.getCacheDir(),"dg_icon_imports");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("STORAGE_ERROR");return new File(dir,"selected-"+UUID.randomUUID()+suffix);}
    private static File copy(Uri uri,long limit,String suffix)throws Exception {
        File to=temp(suffix);try(InputStream in="file".equals(uri.getScheme())?new FileInputStream(uri.getPath()):ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(to)){
            if(in==null)throw new IOException("INVALID_IMAGE");byte[] b=new byte[16384];long size=0;int n;while((n=in.read(b))!=-1){size+=n;if(size>limit)throw new IOException("FILE_TOO_LARGE");out.write(b,0,n);}return to;
        }catch(Exception e){to.delete();throw e;}
    }
    public static void copyImage(Uri uri,Consumer<Image> callback){io.execute(()->{File file=null;try{
        String name=name(uri),lower=name.toLowerCase(Locale.ROOT);String ext=lower.lastIndexOf('.')>=0?lower.substring(lower.lastIndexOf('.')):".png";
        if(!IconPackStorage.imageName("icon"+ext))throw new IOException("INVALID_IMAGE");file=copy(uri,IconPackStorage.MAX_FILE,ext);deliverImage(file,name,ext,callback);
    }catch(Exception e){if(file!=null)file.delete();showError(e);AndroidUtilities.runOnUIThread(()->callback.accept(null));}});}
    public static void svgText(String text,Consumer<Image> callback){io.execute(()->{File file=null;try{byte[] bytes=text.getBytes(StandardCharsets.UTF_8);if(bytes.length>IconPackStorage.MAX_FILE)throw new IOException("FILE_TOO_LARGE");file=temp(".svg");try(OutputStream out=new FileOutputStream(file)){out.write(bytes);}deliverImage(file,"clipboard.svg",".svg",callback);}catch(Exception e){if(file!=null)file.delete();showError(e);AndroidUtilities.runOnUIThread(()->callback.accept(null));}});}
    private static void deliverImage(File file,String name,String ext,Consumer<Image> callback)throws Exception {
        if(!IconManager.INSTANCE.validateImage(file,ext))throw new IOException("INVALID_IMAGE");Image image=new Image();image.file=file;image.name=name;image.extension=ext;
        if(!ext.equals(".svg")){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);image.width=o.outWidth;image.height=o.outHeight;}
        AndroidUtilities.runOnUIThread(()->callback.accept(image));
    }
    public static void showError(Exception error){previewFailed();FileLog.e(error);AndroidUtilities.runOnUIThread(()->{BaseFragment f=LaunchActivity.getSafeLastFragment();if(f==null||f.getParentActivity()==null)return;String key=error.getMessage()==null?"":error.getMessage();String text;
        switch(key){
            case "EXTENSION":text="Выберите файл набора с расширением .dgicons.";break;
            case "MISSING_METADATA":text="В наборе отсутствует metadata.json.";break;
            case "INVALID_METADATA":text="Описание набора иконок повреждено.";break;
            case "METADATA_TOO_LARGE":text="Описание набора слишком большое.";break;
            case "TOO_MANY_FILES":text="В наборе слишком много файлов.";break;
            case "ARCHIVE_TOO_LARGE":text="Архив набора слишком большой.";break;
            case "FILE_TOO_LARGE":text="Один из файлов набора слишком большой.";break;
            case "COMPRESSION_RATIO":text="Не удалось распаковать набор: слишком высокая степень сжатия.";break;
            case "INVALID_IMAGE":text="Не удалось открыть изображение. Поддерживаются SVG, PNG, WEBP и JPEG.";break;
            case "STORAGE_ERROR":text="Не удалось сохранить набор. Проверьте свободное место.";break;
            default:text="Не удалось открыть набор иконок. Проверьте содержимое файла.";
        }
        f.showDialog(new AlertDialog.Builder(f.getParentActivity(),f.getResourceProvider()).setTitle(DevGramLocale.text("Наборы иконок")).setMessage(DevGramLocale.text(text)).setPositiveButton(DevGramLocale.text("ОК"),null).create());});}
}
