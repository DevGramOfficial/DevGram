package org.telegram.ui;

import android.graphics.Matrix;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.*;
import java.util.ArrayList;

/** In-place profile editing. Drafts belong to this screen; public caches never hold them. */
final class DevGramProfileEditor {
    final ProfileActivity host;
    final DevGramStudioActivity.Draft draft;
    final long uid;
    private final org.json.JSONObject initialDocument;
    private final DevGramProfileStyle.Style initialTheme;
    private final boolean initialDirty;
    private final Runnable previousChanged,previousSaved;
    private final FrameLayout root;
    private final DevGramProfileEditBar capsule;
    private boolean saving, closed, dismissBarGesture;
    private DevGramProfileSlots slots;
    DevGramProfileExport exporter;
    private java.util.function.Consumer<Boolean> previousExport;
    private boolean skipInitialGuide;
    void exportChrome(boolean hide){capsule.setVisibility(hide?View.INVISIBLE:View.VISIBLE);assistantButton.setVisibility(hide?View.INVISIBLE:View.VISIBLE);}
    void showSlots(){if(closed||saving||slots!=null||host.getUserConfig().getClientUserId()!=uid)return;DevGramProfileGuide.stop(this);slots=DevGramProfileSlots.show(this);}
    void slotsClosed(DevGramProfileSlots value){if(slots==value)slots=null;}
    android.graphics.Bitmap slotCapture(){
        if(closed||root.getWidth()<=0||root.getHeight()<=0)return null;
        int barVisibility=capsule.getVisibility(),aiVisibility=assistantButton.getVisibility();
        try{float scale=Math.min(1f,1200f/Math.max(root.getWidth(),root.getHeight()));android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(Math.max(1,Math.round(root.getWidth()*scale)),Math.max(1,Math.round(root.getHeight()*scale)),android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(image);canvas.scale(scale,scale);capsule.setVisibility(View.INVISIBLE);assistantButton.setVisibility(View.INVISIBLE);root.draw(canvas);return image;}catch(Throwable error){FileLog.e(error);return null;}finally{capsule.setVisibility(barVisibility);assistantButton.setVisibility(aiVisibility);}
    }
    private final DevGramProfileExitPrompt exitPrompt = new DevGramProfileExitPrompt();
    private float downX, downY;
    private float startX, startY;
    private String target, dragKey, movementPrefix;
    private boolean anchoredMovement, gestureRelease;
    private DevGramHeaderGesture transformGesture;
    private int firstPointer, secondPointer;
    private String transformPart;
    private View anchor;
    private View pendingRow;
    private String pendingKey;
    private DevGramProfileRowDrag rowDrag;
    private final TextView assistantButton;
    private org.telegram.ui.ActionBar.INavigationLayout[] childSheet;
    DevGramProfileEditSheet elementSheet;
    private final Runnable longPress = () -> { if (pendingRow != null) startRowDrag(pendingRow, pendingKey); };
    private final Matrix localToRoot = new Matrix(), rootToLocal = new Matrix();
    private final float[] touchPoint = new float[2];
    private final Runnable policy = this::onPolicyChanged;
    private void onPolicyChanged() { if (!closed) { if (!DevGramProfileCustomization.enabled() || draft.subject<0&&!DevGramProfilePolicy.allowed(uid,"profile.communities")) { host.dismissCurrentDialog(); close(); } else host.devgramRefreshEditor(); } }

    DevGramProfileEditor(ProfileActivity host,DevGramStudioActivity.Draft incoming) {
        this.host = host; uid = host.getUserConfig().getClientUserId(); root = (FrameLayout) host.getFragmentView();
        draft=incoming==null?new DevGramStudioActivity.Draft():incoming;draft.subject=host.devgramSubject();
        if(incoming==null){draft.document = DevGramProfileDocument.raw(draft.target(host.getCurrentAccount())); draft.theme = DevGramProfileStyle.raw(draft.target(host.getCurrentAccount()));}
        initialDocument=DevGramProfileDocument.copy(draft.document);initialTheme=draft.theme;initialDirty=draft.dirty;
        previousChanged=draft.onChanged;previousSaved=draft.onSaved;previousExport=draft.onExport;draft.onExport=layers->{if(layers&&DevGramAppearanceAccess.check(host,"profile.export.layers"))DevGramProfileExport.start(this,4,5000);else if(!layers)DevGramProfileExport.show(this);};
        draft.onChanged = () -> { if (!closed) host.devgramRefreshEditor(); };
        draft.onSaved = () -> { if (!closed) host.devgramRefreshEditor(); };
        capsule = new DevGramProfileEditBar(root.getContext(), host.getResourceProvider(), this::cancel, this::save,
                () -> DevGramWorkshopPublishSheet.show(host, draft, null));
        root.addView(capsule, LayoutHelper.createFrame(93, 36, Gravity.TOP | Gravity.LEFT, 10,
                AndroidUtilities.statusBarHeight / AndroidUtilities.density + org.telegram.ui.ActionBar.ActionBar.getCurrentActionBarHeight() / AndroidUtilities.density + 10, 0, 0));
        assistantButton = DevGramStudioControls.text(root.getContext(), "✦", 23, true, host.getResourceProvider());
        assistantButton.setGravity(Gravity.CENTER);
        assistantButton.setContentDescription(t("Создать оформление с ИИ"));
        assistantButton.setBackground(DevGramStudioControls.surface(Theme.getColor(Theme.key_windowBackgroundWhite, host.getResourceProvider()), Theme.getColor(Theme.key_divider, host.getResourceProvider())));
        assistantButton.setElevation(AndroidUtilities.dp(6));
        assistantButton.setOnClickListener(view -> assistant());
        root.addView(assistantButton, LayoutHelper.createFrame(36, 36, Gravity.TOP | Gravity.RIGHT, 0,
                AndroidUtilities.statusBarHeight / AndroidUtilities.density + org.telegram.ui.ActionBar.ActionBar.getCurrentActionBarHeight() / AndroidUtilities.density + 10, 14, 0));
        DevGramProfilePolicy.listen(policy);
        DevGramProfileDocument.load(host.getCurrentAccount(), draft.subject, (result, error) -> {
            if (!closed && !draft.dirty && result != null) { draft.document = DevGramProfileDocument.editable(result.optJSONObject("document")); host.devgramRefreshEditor(); }
        });
        host.devgramRefreshEditor();
        if(draft.pendingExport!=null){boolean layers=draft.pendingExport;draft.pendingExport=null;skipInitialGuide=true;root.postDelayed(()->{if(!closed&&draft.onExport!=null)draft.onExport.accept(layers);},700);}
    }
    private String t(String text) { return DevGramLocale.text(text); }
    private boolean inside(View view, float x, float y) {
        if (view == null || !view.isShown() || view.getAlpha() < .1f) return false;
        localToRoot.reset(); View node = view;
        for (int depth = 0; node != root && depth < 16; depth++) {
            if (!(node.getParent() instanceof View)) return false;
            View parent = (View) node.getParent();
            localToRoot.postConcat(node.getMatrix());
            localToRoot.postTranslate(node.getLeft() - parent.getScrollX(), node.getTop() - parent.getScrollY());
            node = parent;
        }
        if (node != root || !localToRoot.invert(rootToLocal)) return false;
        touchPoint[0] = x; touchPoint[1] = y; rootToLocal.mapPoints(touchPoint);
        return touchPoint[0] >= 0 && touchPoint[1] >= 0 && touchPoint[0] <= view.getWidth() && touchPoint[1] <= view.getHeight();
    }
    private void prepareMovement(String part, JSONObject config) {
        anchoredMovement = host.devgramEditorAnchorActive(part, config);
        movementPrefix = (anchoredMovement ? "anchor_" : "") + part;
        startX = (float) config.optDouble(movementPrefix + "_x"); startY = (float) config.optDouble(movementPrefix + "_y");
    }
    private void move(JSONObject config, float dx, float dy) throws Exception {
        config.put(movementPrefix + "_x", DevGramHeaderGeometry.dragOffset(startX, dx, root.getWidth(), AndroidUtilities.density, anchoredMovement));
        config.put(movementPrefix + "_y", DevGramHeaderGeometry.dragOffset(startY, dy, root.getWidth(), AndroidUtilities.density, anchoredMovement));
    }
    private void dragTo(float x, float y) {
        if (target == null || target.equals("banner") || !DevGramProfilePolicy.allowed(uid, "profile.layout.advanced")) return;
        JSONObject config = DevGramHeaderLayout.custom(draft.document);
        try { move(config, x - downX, y - downY); DevGramHeaderLayout.replace(draft.document, config); draft.dirty = true; host.devgramRefreshEditor(); } catch (Exception ignored) {}
    }
    private boolean headerTouch(int action, float x, float y) {
        if (target == null) return false;
        boolean moved = Math.hypot(x - downX, y - downY) > AndroidUtilities.dp(8);
        if (action == MotionEvent.ACTION_MOVE && moved) dragTo(x, y);
        else if (action == MotionEvent.ACTION_UP) {
            if (moved) dragTo(x, y); else elementMenu(target, anchor);
            target = null;
        } else if (action == MotionEvent.ACTION_CANCEL) target = null;
        return true;
    }
    private void updateTransform(MotionEvent event) {
        if (transformGesture == null || !DevGramProfilePolicy.allowed(uid, "profile.layout.advanced")) return;
        int first = event.findPointerIndex(firstPointer), second = event.findPointerIndex(secondPointer);
        if (first < 0 || second < 0) return;
        transformGesture.update(event.getX(first), event.getY(first), event.getX(second), event.getY(second));
        JSONObject config = DevGramHeaderLayout.custom(draft.document);
        try {
            config.put(transformPart + "_scale_x", transformGesture.scaleX).put(transformPart + "_scale_y", transformGesture.scaleY).put(transformPart + "_rotate", transformGesture.rotation);
            move(config, transformGesture.panX, transformGesture.panY);
            DevGramHeaderLayout.replace(draft.document, config); draft.dirty = true; host.devgramRefreshEditor();
        } catch (Exception ignored) {}
    }
    private boolean transformTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (gestureRelease) {
            if (action == MotionEvent.ACTION_DOWN) gestureRelease = false;
            else { if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) gestureRelease = false; return true; }
        }
        if (transformGesture != null) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_UP && (event.getPointerId(event.getActionIndex()) == firstPointer || event.getPointerId(event.getActionIndex()) == secondPointer)) {
                if (action == MotionEvent.ACTION_POINTER_UP) updateTransform(event);
                transformGesture = null; target = null; gestureRelease = action == MotionEvent.ACTION_POINTER_UP; return true;
            }
            if (!DevGramProfilePolicy.allowed(uid, "profile.layout.advanced")) { transformGesture = null; target = null; gestureRelease = true; return true; }
            int first = event.findPointerIndex(firstPointer), second = event.findPointerIndex(secondPointer);
            if (first < 0 || second < 0) { transformGesture = null; target = null; gestureRelease = true; return true; }
            if (action == MotionEvent.ACTION_MOVE) updateTransform(event);
            return true;
        }
        if (action != MotionEvent.ACTION_POINTER_DOWN || event.getPointerCount() != 2) return false;
        if (rowDrag != null) { finishDrag(); target = null; gestureRelease = true; return true; }
        String selected = target;
        if (selected == null || selected.equals("banner")) {
            selected = null;
            for (String name : DevGramHeaderGeometry.PARTS) if (inside(host.devgramEditorElement(name), event.getX(0), event.getY(0))) { selected = name; break; }
        }
        if (selected == null) { if ("banner".equals(target)) { cancelPendingDrag(); target = null; gestureRelease = true; return true; } return false; }
        if (!DevGramAppearanceAccess.check(host, "profile.layout.advanced")) { cancelPendingDrag(); target = null; gestureRelease = true; return true; }
        float x1 = event.getX(0), y1 = event.getY(0), x2 = event.getX(1), y2 = event.getY(1);
        if (Math.hypot(x2 - x1, y2 - y1) < AndroidUtilities.dp(12)) { target = null; gestureRelease = true; return true; }
        cancelPendingDrag(); clanGesture = null; host.devgramEditorCancelTouch();
        transformPart = selected.equals("buttons") ? "actions" : selected;
        JSONObject config = DevGramHeaderLayout.custom(draft.document); prepareMovement(transformPart, config);
        transformGesture = new DevGramHeaderGesture(x1, y1, x2, y2, (float) config.optDouble(transformPart + "_scale_x", 100), (float) config.optDouble(transformPart + "_scale_y", 100), (float) config.optDouble(transformPart + "_rotate"));
        firstPointer = event.getPointerId(0); secondPointer = event.getPointerId(1); target = null;
        DevGramStudioControls.haptic(root, android.view.HapticFeedbackConstants.VIRTUAL_KEY); return true;
    }
    private org.telegram.ui.ActionBar.SimpleTextView clanGesture;
    private boolean clanTouch(MotionEvent event){
        View view=host.devgramEditorElement("name");if(!(view instanceof org.telegram.ui.ActionBar.SimpleTextView)||!view.isShown()||view.getAlpha()<.1f)return false;
        org.telegram.ui.ActionBar.SimpleTextView name=(org.telegram.ui.ActionBar.SimpleTextView)view;int[] point=new int[2];name.getLocationOnScreen(point);
        float x=(event.getRawX()-point[0])/Math.max(.01f,name.getScaleX()),y=(event.getRawY()-point[1])/Math.max(.01f,name.getScaleY());
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&name.hitDevGramClanTag(x,y))clanGesture=name;
        if(clanGesture==null)return false;MotionEvent copy=MotionEvent.obtain(event);copy.setLocation(x,y);try{clanGesture.onTouchEvent(copy);}finally{copy.recycle();}
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)clanGesture=null;return true;
    }
    boolean dispatch(MotionEvent event) {
        if (closed) return false;
        if (saving) return true;
        if (host.getUserConfig().getClientUserId() != uid) return false;
        if (transformTouch(event)) return true;
        float x = event.getX(), y = event.getY();
        if(DevGramProfileGuide.ownsTouch(this,x,y))return false;
        if (dismissBarGesture) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) dismissBarGesture = false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && capsule.expanded() && !inside(capsule, x, y)) {
            capsule.expand(false); dismissBarGesture = true; return true;
        }
        if(clanTouch(event))return true;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = x; downY = y; target = null; anchor = null;
            cancelPendingDrag();
            if (inside(capsule,x,y) || inside(assistantButton,x,y) || y < AndroidUtilities.statusBarHeight + AndroidUtilities.dp(48)) return false;
            pendingRow=host.devgramEditorRowViewAt(x,y);pendingKey=host.devgramEditorRowKey(pendingRow);
            if(pendingKey!=null){root.postDelayed(longPress,240);return false;}
            String[] names = DevGramHeaderGeometry.PARTS;
            for (String name : names) {
                View view = host.devgramEditorElement(name);
                if (inside(view,x,y)) { target = name; anchor = view; JSONObject header=DevGramHeaderLayout.custom(draft.document);String part=name.equals("buttons")?"actions":name;prepareMovement(part,header); return true; }
            }
            if (y < host.devgramEditorHeaderBottom() && inside(host.devgramEditorElement("banner"),x,y)) { target="banner"; anchor=host.devgramEditorElement("banner"); return true; }
            return false;
        }
        if (rowDrag != null) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) rowDrag.update(y);
            else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                DevGramProfileRowDrag drag = rowDrag; rowDrag = null;
                drag.finish(event.getActionMasked() == MotionEvent.ACTION_UP);
            }
            return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL||event.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.hypot(x-downX,y-downY)>android.view.ViewConfiguration.get(root.getContext()).getScaledTouchSlop())cancelPendingDrag();
        return headerTouch(event.getActionMasked(), x, y);
    }
    boolean startRowDrag(View row, String key) {
        if (rowDrag != null) return true;
        if (row == null || key == null || !DevGramAppearanceAccess.check(host, "profile.rows")) return false;
        cancelPendingDrag(); host.devgramEditorCancelTouch(); target = null;
        host.devgramFinishRowAnimations();
        rowDrag = new DevGramProfileRowDrag(this, root, row, key, downY);
        return true;
    }
    private void cancelPendingDrag() { root.removeCallbacks(longPress); pendingRow = null; pendingKey = null; }
    private void finishDrag() {
        cancelPendingDrag();
        if (rowDrag != null) { DevGramProfileRowDrag drag = rowDrag; rowDrag = null; drag.finish(false); }
    }
    View guideControl(String key) {
        return "capsule".equals(key) ? capsule : "assistant".equals(key) ? assistantButton : host.devgramEditorElement(key);
    }
    View guideRoot() {
        if (childSheet != null && childSheet.length > 0 && childSheet[0] != null) return childSheet[0].getView().getRootView();
        // Keep native chapter/confirmation dialogs above the tour, rather than moving the tour into them.
        if (host.visibleDialog instanceof org.telegram.ui.ActionBar.BottomSheet && host.visibleDialog.isShowing() && host.visibleDialog.getWindow() != null) return host.visibleDialog.getWindow().getDecorView();
        return root;
    }
    void dismissEditorSheet() {
        host.dismissCurrentDialog();
        if (childSheet != null && childSheet.length > 0 && childSheet[0] != null) {
            org.telegram.ui.ActionBar.BaseFragment child = childSheet[0].getLastFragment();
            if (child != null) child.finishFragment();
        }
        childSheet = null;
    }
    boolean reorder(String from, String to,boolean after) {
        ArrayList<String> visible = host.devgramEditorRows();
        if (!DevGramProfilePolicy.allowed(uid,"profile.rows") || !visible.contains(from) || !visible.contains(to) || from.equals(to)) return false;
        ArrayList<String> keys=new ArrayList<>(),available=host.devgramAllRowKeys();JSONArray old=draft.document.optJSONArray("rows");
        if(old!=null)for(int i=0;i<old.length();i++){String key=old.optString(i);if(available.contains(key)&&!keys.contains(key))keys.add(key);}
        for(String key:visible)if(!keys.contains(key))keys.add(key);for(String key:available)if(!keys.contains(key))keys.add(key);
        ArrayList<String> original=new ArrayList<>(keys);keys.remove(from);int destination=keys.indexOf(to)+(after?1:0);keys.add(destination,from);if(keys.equals(original))return false;JSONArray array=new JSONArray();for(String key:keys)array.put(key);put("rows",array);DevGramProfileGuide.reordered(this);return true;
    }
    void put(String key,Object value) {
        try { draft.document.put(key,value); draft.dirty=true; host.devgramRefreshEditor(); } catch(Exception ignored) {}
    }
    void setTheme(String key,int value) { draft.theme=(draft.theme==null?DevGramProfileStyle.raw(uid):draft.theme).with(key,value); draft.dirty=true; host.devgramRefreshEditor(); }
    void remove(String key) {
        // Zero explicitly resets a legacy cloud nickname color as well.
        if (key.equals("nickColor") || key.equals("nickColor2")) { put(key, 0); return; }
        draft.document.remove(key); draft.dirty=true; host.devgramRefreshEditor();
    }
    private ItemOptions options(View anchor) { return ItemOptions.makeOptions(host,anchor==null?capsule:anchor); }
    void elementMenu(String name,View anchor) {
        ItemOptions menu=options(anchor);
        if(name.equals("avatar")){
            menu.add(R.drawable.msg_edit,t("Оформление аватара"),()->sheet("avatar"));
            menu.add(R.drawable.msg_palette,t("Рамка аватара"),()->sheet("frame"));
            menu.add(R.drawable.msg_message,t("Мысль у аватара"),()->sheet("thought"));
        }else if(name.equals("name")||name.equals("status")){
            menu.add(R.drawable.msg_edit,t("Оформление имени"),()->sheet("name"));
            menu.add(R.drawable.msg_groups,t("Тег клана"),()->host.presentFragment(new DevGramClanTagsActivity()));
            menu.add(R.drawable.msg_photo_settings,t("Шапка профиля"),()->sheet("banner"));
        }else if(name.equals("buttons")){
            menu.add(R.drawable.msg_palette,t("Цвет кнопок"),()->sheet("buttons"));
            menu.add(R.drawable.msg_colors,t("Настроить блоки"),()->sheet("cards"));
            menu.add(R.drawable.msg_photo_settings,t("Шапка и баннер"),()->sheet("banner"));
        }else if(name.equals("title")){
            menu.add(R.drawable.msg_photo_settings,t("Шапка и баннер"),()->sheet("banner"));
            menu.add(R.drawable.msg_edit,t("Имя"),()->sheet("name"));
        }else{
            menu.add(R.drawable.msg_theme,t("Фон профиля"),()->sheet("background"));
            menu.add(R.drawable.msg_palette,t("Цвет текста профиля"),()->sheet("text"));
            menu.add(R.drawable.msg_customize,t("Видимость строк"),()->sheet("rows"));
            menu.add(R.drawable.msg_add,t("Добавить строку"),()->addBlock(null));
        }
        menu.show();
    }
    private JSONObject block(String id){JSONArray blocks=draft.document.optJSONArray("blocks");if(blocks!=null)for(int i=0;i<blocks.length();i++){JSONObject row=blocks.optJSONObject(i);if(row!=null&&id.equals(row.optString("id","legacy"+i)))return row;}return null;}
    void addBlock(String after){
        if(!DevGramAppearanceAccess.check(host,"profile.blocks"))return;
        JSONArray blocks=draft.document.optJSONArray("blocks");if(blocks==null)blocks=new JSONArray();
        if(blocks.length()>=64){BulletinFactory.of(host).createErrorBulletin(t("Не больше 64 блоков")).show();return;}
        JSONObject row=DevGramProfileRowSpec.create();blocks.put(row);put("blocks",blocks);
        String key="block:"+row.optString("id");if(after!=null)reorder(key,after,true);
        editBlock(row.optString("id"));
    }
    private void deleteBlock(String id){
        if(!DevGramAppearanceAccess.check(host,"profile.blocks"))return;
        JSONArray old=draft.document.optJSONArray("blocks"),next=new JSONArray();if(old!=null)for(int i=0;i<old.length();i++){JSONObject row=old.optJSONObject(i);if(row!=null&&!id.equals(row.optString("id","legacy"+i)))next.put(row);}
        for(String field:new String[]{"rows","hiddenRows"}){JSONArray source=draft.document.optJSONArray(field),kept=new JSONArray();if(source!=null)for(int i=0;i<source.length();i++)if(!source.optString(i).equals("block:"+id))kept.put(source.optString(i));put(field,kept);}put("blocks",next);
    }
    void rowMenu(String key,View view){
        ItemOptions menu=options(view);boolean custom=key!=null&&key.startsWith("block:");String id=custom?key.substring(6):null;JSONObject row=custom?block(id):null;
        if(custom)menu.add(R.drawable.msg_edit,t("Изменить строку"),()->editBlock(id));
        else menu.add(R.drawable.msg_palette,t("Цвет текста профиля"),()->sheet("text"));
        menu.add(R.drawable.msg_customize,t("Видимость строк"),()->sheet("rows"));
        if(row!=null&&DevGramProfileRowSpec.type(row)==12)menu.add(R.drawable.msg_palette,t("Дизайн блока"),()->{if(DevGramAppearanceAccess.check(host,"profile.blocks.code"))host.presentFragment(new DevGramBlockDesignActivity(draft,row,host::devgramRefreshEditor));});
        menu.add(R.drawable.msg_add,t("Добавить строку"),()->addBlock(key));
        menu.add(R.drawable.msg_colors,t("Настроить блоки"),()->sheet("cards"));
        menu.add(R.drawable.msg_theme,t("Фон профиля"),()->sheet("background"));
        if(custom)menu.add(R.drawable.msg_delete,t("Удалить блок"),()->new AlertDialog.Builder(host.getParentActivity(),host.getResourceProvider()).setTitle(t("Удалить строку?")).setPositiveButton(t("Удалить"),(d,w)->deleteBlock(id)).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).show());
        else if(key!=null)menu.add(R.drawable.msg_secret,t("Скрыть строку"),()->{if(!DevGramAppearanceAccess.check(host,"profile.rows"))return;JSONArray hidden=draft.document.optJSONArray("hiddenRows");if(hidden==null)hidden=new JSONArray();boolean exists=false;for(int i=0;i<hidden.length();i++)if(key.equals(hidden.optString(i)))exists=true;if(!exists)hidden.put(key);put("hiddenRows",hidden);});
        menu.show();
    }
    private void editBlock(String id){
        JSONArray blocks=draft.document.optJSONArray("blocks");if(blocks==null)return;
        for(int i=0;i<blocks.length();i++)if(id.equals(blocks.optJSONObject(i).optString("id","legacy"+i))){final int index=i;
            DevGramStudioItemSheet.show(host,draft,"blocks",index,host::devgramRefreshEditor,operation->{
                JSONArray next=new JSONArray();int destination=Math.max(0,Math.min(blocks.length()-1,index+(operation==0?-1:1)));
                for(int j=0;j<blocks.length();j++){if(operation==2&&j==index)continue;next.put(operation<2?blocks.opt(j==index?destination:j==destination?index:j):blocks.opt(j));}
                if(operation==2)for(String key:new String[]{"rows","hiddenRows"}){JSONArray old=draft.document.optJSONArray(key),kept=new JSONArray();if(old!=null)for(int j=0;j<old.length();j++)if(!old.optString(j).equals("block:"+id))kept.put(old.optString(j));put(key,kept);}
                if(operation<2&&index!=destination)reorder("block:"+id,"block:"+blocks.optJSONObject(destination).optString("id","legacy"+destination),operation==1);
                put("blocks",next);
            });return;
        }
    }
    void menu(View view) {
        options(view).add(R.drawable.msg_photo_settings,t("Шапка и баннер"),()->sheet("banner"))
                .add(R.drawable.msg_theme,t("Фон профиля"),()->sheet("background"))
                .add(R.drawable.msg_customize,t("Своя компоновка"),()->sheet("layout"))
                .add(R.drawable.msg_colors,t("Настроить блоки"),()->sheet("cards"))
                .add(R.drawable.msg_discussion,t("Имя"),()->sheet("name"))
                .add(R.drawable.msg_discussion,t("Мысль"),()->sheet("thought"))
                .add(R.drawable.msg_discussion,t("Имя и сообщения"),()->advanced("messages"))
                .add(R.drawable.msg_gallery,t("Музыка и активность"),()->advanced("music"))
                .add(R.drawable.msg_link,t("Внешние профили"),()->advanced("integrations"))
                .add(R.drawable.msg_share,t("Экспорт профиля"),()->DevGramProfileExport.show(this))
                .add(R.drawable.msg_photos,t("Мои профили"),this::showSlots)
                .add(R.drawable.msg_settings,t("Проекты и файлы"),()->advanced("files"))
                .add(R.drawable.msg_gallery,t("Мастерская"),this::workshop)
                .add(R.drawable.msg_customize,t("Создать оформление с ИИ"),this::assistant)
                .add(R.drawable.msg_help,t("Гайд по оформлению"),this::guide)
                .add(R.drawable.msg_secret,t("Доступ к функциям"),()->host.presentFragment(new DevGramProfilePolicyActivity())).show();
    }
    void sheet(String page) {
        if (elementSheet != null && elementSheet.isShowing()) elementSheet.navigate(page);
        else { elementSheet = new DevGramProfileEditSheet(this,page); elementSheet.show(); }
    }
    void advanced(String page) {
        if(page.equals("layers")||page.equals("nodes")){editorSheet(new DevGramFrameGraphActivity(draft));return;}
        if(page.equals("messages")){editorSheet(new DevGramMessageBubbleActivity(draft));return;}
        if(page.equals("music")){editorSheet(DevGramProfileStyleActivity.music(draft));return;}
        editorSheet(DevGramStudioActivity.forDraft(page,draft));
    }
    void guideWorkshop() { editorSheet(new DevGramWorkshopActivity(draft)); }
    private void editorSheet(org.telegram.ui.ActionBar.BaseFragment fragment) {
        org.telegram.ui.ActionBar.BaseFragment.BottomSheetParams params = new org.telegram.ui.ActionBar.BaseFragment.BottomSheetParams();
        final org.telegram.ui.ActionBar.INavigationLayout[][] owner={null};
        params.onDismiss=()->{if(childSheet==owner[0])childSheet=null;};
        owner[0]=host.showAsSheet(fragment,params);childSheet=owner[0];
    }
    void workshop() { host.presentFragment(new DevGramWorkshopActivity(draft)); }
    void assistant() { DevGramProfileDesignAssistant.show(host,draft); }
    void guide() { DevGramProfileGuide.start(this); }
    void startGuideWhenReady(boolean force){if(skipInitialGuide)return;if(!force&&MessagesController.getGlobalMainSettings().getBoolean("devgramProfileGuideSeen_v3_"+uid,false))return;root.postDelayed(()->{if(!closed&&host.devgramEditor==this)guide();},600);}
    void save() { save(null); }
    private void save(Runnable afterSave) {
        if (saving || closed || host.getUserConfig().getClientUserId() != uid) return;
        if (!draft.dirty) { close(); if (afterSave != null) afterSave.run(); return; }
        saving = true; capsule.setBusy(true);
        Runnable saveDocument = () -> DevGramProfileDocument.save(host.getCurrentAccount(), draft.subject, draft.document, draft.theme, (result, error) -> {
            saving = false;
            if (closed || host.getUserConfig().getClientUserId() != uid) return;
            capsule.setBusy(false);
            if (result == null) { BulletinFactory.of(host).createErrorBulletin(t("Не удалось сохранить оформление")).show(); return; }
            draft.document = DevGramProfileDocument.editable(result.optJSONObject("document")); draft.dirty = false;
            DevGramProfileSlots.saveIfInitialized(this);
            DevGramMusicPresence.configure(host.getCurrentAccount(), draft.document.optInt("activity") == 1);
            close();
            if (previousSaved != null) previousSaved.run();
            BulletinFactory.of(host).createSimpleBulletin(R.raw.contact_check, t("Оформление сохранено")).show();
            if (afterSave != null) afterSave.run();
        });
        if (draft.subject < 0 || draft.theme == null) saveDocument.run(); else DevGramProfileStyle.save(host.getCurrentAccount(), draft.theme.effective(uid), (style, error) -> {
            if (closed || host.getUserConfig().getClientUserId() != uid) return;
            if (error != null) { saving = false; capsule.setBusy(false); BulletinFactory.of(host).createErrorBulletin(t("Не удалось сохранить оформление")).show(); }
            else { draft.theme = style; saveDocument.run(); }
        });
    }
    boolean canLeaveWithoutPrompt() { return !saving && !draft.dirty; }
    void cancel() { requestExit(null); }
    void requestExit(Runnable afterExit) {
        if (saving || closed) return;
        if (!draft.dirty) { close(); if (afterExit != null) afterExit.run(); return; }
        exitPrompt.show(host, () -> save(afterExit), () -> {
            if (closed || saving || host.getUserConfig().getClientUserId() != uid) return;
            draft.document = DevGramProfileDocument.copy(initialDocument); draft.theme = initialTheme; draft.dirty = initialDirty;
            if (previousChanged != null) previousChanged.run();
            close(); if (afterExit != null) afterExit.run();
        });
    }
    void close() { if(closed)return;closed=true;if(exporter!=null)exporter.cancel();draft.onExport=previousExport;if(slots!=null)slots.dismiss(false);exitPrompt.dismiss();transformGesture=null;gestureRelease=false;clanGesture=null;finishDrag();host.devgramFinishRowAnimations();DevGramProfilePolicy.unlisten(policy);DevGramProfileGuide.stop(this);root.removeView(capsule);root.removeView(assistantButton);draft.onChanged=previousChanged;draft.onSaved=previousSaved;host.devgramEditor=null;host.devgramRefreshEditor();host.devgramRefreshClanTags(); }
}
