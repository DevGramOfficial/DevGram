package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.*;

/** A tour of the actual editor: anchored spotlight, navigation, gesture demo and drag exercise. */
final class DevGramProfileGuide {
    private static DevGramProfileGuide current;
    private final DevGramProfileEditor editor;
    private Overlay overlay;
    private View guideCard;
    private int step, retries;
    private boolean busy;
    private long opened;
    private final Runnable update = this::refresh;
    private static final String[] TITLES={"DevGram: оформление профиля","Режим правки","Крестик и галочка","Нажмите на элемент","Форма аватара","Вид аватара","Своя форма","Рамка аватара","Рамка вокруг аватара","Имя и шапка","Баннер","Фон профиля","Фон страницы","Порядок строк","Попробуйте сами","Свои строки","Лайки и комментарии","Студия рамок","Узлы и полотно","Чужие работы","Своя работа","Чаты и энергосбережение","Сохранения и диагностика","Пробуйте"};
    private static final String[] BODIES={
        "DevGram позволяет оформить фон и баннер, аватар, имя, карточки и сообщения. Этот гайд пройдёт по редактору главами и откроет нужные настройки. Его можно закрыть в любой момент.",
        "Палитра в шапке профиля включает правку. Мы уже включили её: изменения видны прямо на этой странице и пока остаются черновиком.",
        "Крестик отменяет всю текущую правку. Галочка открывает сохранение и отправку в мастерскую. Пока вы не сохранили оформление, другие пользователи его не увидят.",
        "Нажимайте на сам элемент: аватар, имя, шапку, кнопки или строку. У каждого есть своё меню. Сейчас откроем оформление аватара.",
        "Очертание выбирается по рисункам. Круг, скруглённый квадрат, многоугольники, звезда, сердце и цветок сразу видны на вашем аватаре. Закрытые варианты отмечены замком.",
        "Ползунки меняют прозрачность, затемнение и мягкость краёв. Лист остаётся открытым, а результат виден на профиле за ним.",
        "Удерживайте палец на полотне, затем нарисуйте свою форму. Контур замкнётся автоматически и сохранится вместе с оформлением. Дальше вернёмся к аватару и посмотрим рамку.",
        "У аватара есть отдельная рамка. Она открывается вторым пунктом меню аватара. Откроем её настройки.",
        "Выбирайте готовую рамку по превью или собирайте свою в студии из слоёв, узоров, наклеек, частиц и свечения. Проекты и избранные рамки доступны рядом.",
        "Нажатие на имя открывает оформление имени и шапки. Шрифт, цвет и подпись можно менять отдельно от баннера. Сейчас откроем шапку.",
        "В шапке выбираются медиа, цвет и градиент, затемнение и компоновка. Своя компоновка открывает отдельные настройки аватара, имени, статуса и кнопок.",
        "Меню строки содержит фон всей страницы и настройки карточек. У фона и шапки свои разделы. Откроем фон.",
        "Фон задаёт атмосферу страницы. Цвет, прозрачность, форма и эффекты карточек настраиваются отдельно в разделе блоков. Следите за контрастом текста.",
        "Строки профиля переставляются долгим нажатием. Зажмите строку, перенесите её выше или ниже и отпустите. Изменённый порядок появится в черновике.",
        "Попробуйте перенести любую строку профиля. Гайд продолжится после изменения порядка. Если строк сейчас нет или функция закрыта, упражнение можно пропустить.",
        "Через меню строки откройте каталог с превью: текст, ссылки, переключатель, счётчик, изображение, пиксельное полотно и интеграции. В редакторе строки задаются иконка, цвета, действия и видимость. Штатные строки можно скрыть и вернуть позже.",
        "Кнопка справа от настроек показывает лайки профилю. Откройте мастерскую, чтобы посмотреть работы и их собственные лайки. Это отдельные счётчики.",
        "В студии рамок откройте «Добавить узел». Каталог содержит источники изображений, слои, частицы, свечение, геометрию и живые значения. Найдите нужный узел по названию или категории и выберите его, чтобы открыть параметры.",
        "Узлы соединяются прямо на полотне. Выход изображения, слоя и живого числа имеет свой тип: соединяйте совместимые разъёмы. Нажмите на узел, чтобы изменить параметры, а двумя пальцами меняйте масштаб полотна. Кнопки отмены и возврата сохраняют историю правки.",
        "В мастерской отдельно собраны профили и рамки. Ищите работы, смотрите популярное, новое и лучшее за период, открывайте авторов и сохраняйте понравившееся.",
        "Свою работу можно отправить в мастерскую с названием и превью. До одобрения она видна в ваших работах. Обновление не заменяет публичную версию до проверки.",
        "Оформление сообщений проверяется на живом образце пузырька. Выключение анимаций и энергосбережение оставляют статичные эффекты. Доступы проверяются и при показе оформления.",
        "Проекты хранят варианты оформления. Импорт и экспорт позволяют обмениваться файлами, изображением и слоями. Диагностика показывает состояние оформления и связи с сервером.",
        "Вы остались в режиме правки. Нажимайте на элементы профиля, пробуйте настройки и мастерскую. Крестик отменит черновик, галочка позволит сохранить оформление."};
    private static final String[] TARGETS = {
        "capsule", "capsule", "capsule", "avatar", "Форма", "Прозрачность", "Применить форму",
        "avatar", "Студия рамок", "name", "Компоновка шапки", "row", "Тип фона", "row", "row",
        "Добавить блок", "buttons", "Добавить узел", "Добавить узел", "", "Опубликовать в мастерской",
        "Шрифт сообщений", "Мои проекты", "capsule"
    };

    private DevGramProfileGuide(DevGramProfileEditor editor) { this.editor = editor; }
    private static int dp(float n) { return AndroidUtilities.dp(n); }
    private String t(String text) { return DevGramLocale.text(text); }
    private String seenKey() { return "devgramProfileGuideSeen_v3_" + editor.uid; }

    static void start(DevGramProfileEditor editor) {
        if (current != null) stop(current.editor);
        current = new DevGramProfileGuide(editor);
        current.enter();
    }
    static void stop(DevGramProfileEditor editor) {
        if (current == null || current.editor != editor) return;
        AndroidUtilities.cancelRunOnUIThread(current.update);
        current.detach(); current = null;
    }
    static void reordered(DevGramProfileEditor editor) {
        if (current != null && current.editor == editor && current.step == 14) current.next();
    }
    static boolean ownsTouch(DevGramProfileEditor editor, float x, float y) {
        if (current == null || current.editor != editor || current.overlay == null) return false;
        int[] origin = new int[2], point = new int[2];
        editor.host.getFragmentView().getLocationOnScreen(origin);
        current.overlay.getLocationOnScreen(point);
        return current.overlay.claims(x + origin[0] - point[0], y + origin[1] - point[1]);
    }
    private void detach() {
        if (overlay != null) AndroidUtilities.removeFromParent(overlay);
        overlay = null; guideCard = null;
    }
    private boolean matches(CharSequence label, String key) {
        if (label == null) return false;
        String actual = label.toString().trim(), expected = t(key);
        return actual.equalsIgnoreCase(expected) || actual.startsWith(expected + ":")
                || actual.startsWith(expected + " ") || actual.equals("🔒 " + expected);
    }
    private View find(View view, String key) {
        if (view == overlay || key.isEmpty() || view.getVisibility() != View.VISIBLE) return null;
        CharSequence label = view instanceof TextView ? ((TextView) view).getText() : view.getContentDescription();
        if (matches(label, key)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), key);
                if (found != null) return found;
            }
        }
        // RecyclerView has no view to highlight until the target item is attached.
        // Scroll its adapter item into view, then resolve the spotlight next frame.
        if (view instanceof UniversalRecyclerView) {
            UniversalRecyclerView list = (UniversalRecyclerView) view;
            for (int i = 0; i < list.adapter.getItemCount(); i++) {
                UItem item = list.adapter.getItem(i);
                if (item != null && matches(item.text, key)) {
                    // Native cells expose their text to accessibility without being TextViews.
                    androidx.recyclerview.widget.RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(i);
                    if (holder != null && holder.itemView.getVisibility() == View.VISIBLE) return holder.itemView;
                    if (!list.isLayoutRequested()) list.layoutManager.scrollToPositionWithOffset(i, dp(100));
                    break;
                }
            }
        }
        return null;
    }
    private View focus(View parent) {
        String key = TARGETS[step];
        if (key.equals("row")) {
            java.util.ArrayList<View> rows = editor.host.devgramEditorRowViews();
            return rows.isEmpty() ? null : rows.get(0);
        }
        if (key.equals("avatar") || key.equals("name") || key.equals("buttons") || key.equals("capsule")) return editor.guideControl(key);
        return find(parent, key);
    }
    private void enter() {
        if (current != this) return;
        busy = true; detach(); retries = 0;
        editor.dismissEditorSheet();
        switch (step) {
            case 4: case 5: editor.sheet("avatar"); break;
            case 6: editor.sheet("shape"); break;
            case 8: editor.sheet("frame"); break;
            case 10: editor.sheet("banner"); break;
            case 12: editor.sheet("background"); break;
            case 15: editor.advanced("blocks"); break;
            case 17: editor.advanced("layers"); break;
            case 18: editor.advanced("nodes"); break;
            case 19: editor.guideWorkshop(); break;
            case 20: case 22: editor.advanced("files"); break;
            case 21: editor.advanced("messages"); break;
        }
        AndroidUtilities.cancelRunOnUIThread(update);
        AndroidUtilities.runOnUIThread(update, 450);
    }
    private void refresh() {
        if (current != this) return;
        if (!DevGramProfileCustomization.enabled() || editor.host.devgramEditor != editor) { stop(editor); return; }
        View candidate = editor.guideRoot();
        if (!(candidate instanceof FrameLayout) || !candidate.isAttachedToWindow() || candidate.getWidth() == 0) {
            if (retries++ < 20) AndroidUtilities.runOnUIThread(update, 150);
            return;
        }
        FrameLayout parent = (FrameLayout) candidate;
        View target = focus(parent);
        if (overlay == null || overlay.getParent() != parent) {
            detach(); render(parent, target); busy = false;
            MessagesController.getGlobalMainSettings().edit().putBoolean(seenKey(), true).apply();
        } else overlay.spot = target;
        if (target != null && retries++ < 3) target.requestRectangleOnScreen(new Rect(0, 0, target.getWidth(), target.getHeight()), false);
        if (overlay != null) { overlay.requestLayout(); overlay.invalidate(); }
        AndroidUtilities.runOnUIThread(update, 180);
    }
    private void render(FrameLayout parent, View focus) {
        opened = SystemClock.uptimeMillis();
        overlay = new Overlay(parent, focus);
        LinearLayout card = DevGramStudioControls.column(parent.getContext()); guideCard = card;
        card.setPadding(dp(18), dp(14), dp(18), dp(6));
        card.setBackground(DevGramStudioControls.surface(Theme.getColor(Theme.key_windowBackgroundWhite, editor.host.getResourceProvider()), Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, editor.host.getResourceProvider())));
        card.setElevation(dp(12));
        String chapter = step < 2 ? "Начало" : step < 17 ? "Правка" : step < 19 ? "Рамки" : step < 21 ? "Мастерская" : step == 21 ? "Вне профиля" : "Сохранения";
        TextView counter = DevGramStudioControls.text(parent.getContext(), chapter, 11, true, editor.host.getResourceProvider());
        counter.setText(t(chapter) + " · " + (step + 1) + " / " + TITLES.length);
        counter.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, editor.host.getResourceProvider()));
        LinearLayout heading = new LinearLayout(parent.getContext()); heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(counter, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = DevGramStudioControls.text(parent.getContext(), "✕", 18, false, editor.host.getResourceProvider());
        close.setGravity(Gravity.CENTER); close.setContentDescription(t("Закрыть гайд")); close.setOnClickListener(v -> stop(editor));
        heading.addView(close, LayoutHelper.createLinear(36, 32)); card.addView(heading);
        TextView title = DevGramStudioControls.text(parent.getContext(), TITLES[step], 19, true, editor.host.getResourceProvider());
        card.addView(title, LayoutHelper.createLinear(-1, -2, 0, 6, 0, 6));
        ScrollView scroll = new ScrollView(parent.getContext()) {
            @Override protected void onMeasure(int width, int height) {
                int maximum = overlay == null ? dp(160) : overlay.maximumBodyHeight();
                super.onMeasure(width, MeasureSpec.makeMeasureSpec(maximum, MeasureSpec.AT_MOST));
            }
        };
        TextView text = DevGramStudioControls.text(parent.getContext(), BODIES[step], 13, false, editor.host.getResourceProvider());
        text.setLineSpacing(dp(2), 1); scroll.addView(text);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(parent.getContext());
        addAction(actions, step == 0 ? "Закрыть гайд" : "Назад", () -> { if (step == 0) stop(editor); else { step--; enter(); } }, false);
        addAction(actions, step == 23 ? "Готово" : step == 14 ? "Пропустить упражнение" : "Далее", this::next, true);
        card.addView(actions);
        counter.setOnClickListener(v -> chapters());
        overlay.addView(card, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT));
        parent.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        card.setAlpha(0); card.animate().alpha(1).setDuration(180).start();
        text.announceForAccessibility(t(TITLES[step]) + ". " + t(BODIES[step]));
    }
    private void addAction(LinearLayout row, String label, Runnable action, boolean accent) {
        TextView button = DevGramStudioControls.text(row.getContext(), label, 13, accent, editor.host.getResourceProvider());
        button.setGravity(Gravity.CENTER); button.setMinimumHeight(dp(44));
        if (accent) button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, editor.host.getResourceProvider()));
        button.setOnClickListener(v -> action.run()); row.addView(button, new LinearLayout.LayoutParams(0, dp(44), 1));
    }
    private void chapters() {
        String[] labels = {t("Начало"), t("Правка"), t("Рамки"), t("Мастерская"), t("Вне профиля"), t("Сохранения")};
        int[] starts = {0, 2, 17, 19, 21, 22};
        org.telegram.ui.ActionBar.AlertDialog.Builder dialog = new org.telegram.ui.ActionBar.AlertDialog.Builder(editor.host.getParentActivity(), editor.host.getResourceProvider());
        dialog.setTitle(t("Гайд по оформлению")); dialog.setItems(labels, (d, index) -> { step = starts[index]; enter(); });
        editor.host.showDialog(dialog.create());
    }
    private void next() {
        if (busy || current != this) return;
        if (step == 23) { stop(editor); return; }
        step++; enter();
    }
    private final class Overlay extends FrameLayout {
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect rect = new Rect();
        private final RectF hole = new RectF();
        private final Path shade = new Path();
        private View spot;
        private final RectF safe = new RectF(), target = new RectF();
        private final Rect visibleFrame = new Rect(), tabBounds = new Rect();
        private final int[] position = new int[2], rootPosition = new int[2];
        private TextView compact;
        private boolean compactMode, placed;
        private float cardX=Float.NaN,cardY=Float.NaN;
        private final Runnable collapse=()->{if(step==13||step==14){compactMode=true;guideCard.setVisibility(GONE);compact.setVisibility(VISIBLE);requestLayout();}};
        Overlay(FrameLayout parent, View spot) { super(parent.getContext()); this.spot = spot; setWillNotDraw(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        int maximumBodyHeight(){return Math.max(dp(32),Math.min(dp(180),(int)safe.height()-dp(190)));}
        private boolean actuallyVisible(View view){
            if(view==null||!view.isShown())return false;
            for(View current=view;current!=null;){if(current.getAlpha()<.05f)return false;android.view.ViewParent parent=current.getParent();current=parent instanceof View?(View)parent:null;}
            return true;
        }
        private void avoidTabs(View view){
            if(view==this||!actuallyVisible(view))return;
            if(view instanceof MainTabsLayout&&view.getGlobalVisibleRect(tabBounds)){
                float top=tabBounds.top-position[1];if(tabBounds.bottom-position[1]>=getMeasuredHeight()-dp(160)&&top>safe.top)safe.bottom=Math.min(safe.bottom,top);return;
            }
            if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)avoidTabs(group.getChildAt(i));}
        }
        private void updateSafeBounds(){
            getLocationOnScreen(position);View root=getRootView();root.getLocationOnScreen(rootPosition);
            safe.set(0,0,getMeasuredWidth(),getMeasuredHeight());
            int left=0,top=AndroidUtilities.statusBarHeight,right=0,bottom=AndroidUtilities.navigationBarHeight;
            android.view.WindowInsets insets=getRootWindowInsets();
            if(insets!=null){
                if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());android.graphics.Insets ime=insets.getInsets(android.view.WindowInsets.Type.ime());left=bars.left;top=bars.top;right=bars.right;bottom=Math.max(bottom,Math.max(bars.bottom,ime.bottom));}
                else{left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=Math.max(bottom,insets.getSystemWindowInsetBottom());}
            }
            safe.left=Math.max(safe.left,rootPosition[0]+left-position[0]);safe.top=Math.max(safe.top,rootPosition[1]+top-position[1]);
            safe.right=Math.min(safe.right,rootPosition[0]+root.getWidth()-right-position[0]);safe.bottom=Math.min(safe.bottom,rootPosition[1]+root.getHeight()-bottom-position[1]);
            getWindowVisibleDisplayFrame(visibleFrame);if(visibleFrame.width()>0&&visibleFrame.height()>0){safe.left=Math.max(safe.left,visibleFrame.left-position[0]);safe.top=Math.max(safe.top,visibleFrame.top-position[1]);safe.right=Math.min(safe.right,visibleFrame.right-position[0]);safe.bottom=Math.min(safe.bottom,visibleFrame.bottom-position[1]);}
            if(editor.host.getParentActivity()!=null&&root==editor.host.getParentActivity().getWindow().getDecorView())avoidTabs(root);
            safe.inset(dp(18),dp(12));
            safe.left=Math.max(0,Math.min(safe.left,getMeasuredWidth()-1));safe.right=Math.max(safe.left+1,Math.min(safe.right,getMeasuredWidth()));
            safe.top=Math.max(0,Math.min(safe.top,getMeasuredHeight()-1));safe.bottom=Math.max(safe.top+1,Math.min(safe.bottom,getMeasuredHeight()));
        }
        @Override protected void onMeasure(int width,int height){
            setMeasuredDimension(MeasureSpec.getSize(width),MeasureSpec.getSize(height));updateSafeBounds();
            if(guideCard!=null){int cardWidth=Math.max(1,Math.min(dp(320),(int)safe.width()));guideCard.measure(MeasureSpec.makeMeasureSpec(cardWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,(int)safe.height()),MeasureSpec.AT_MOST));}
            if(compact!=null)compact.measure(MeasureSpec.makeMeasureSpec(Math.min(dp(240),(int)safe.width()),MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(Math.max(1,(int)safe.height()),MeasureSpec.AT_MOST));
        }
        @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
            updateSafeBounds();
            if(guideCard!=null)guideCard.layout(0,0,guideCard.getMeasuredWidth(),guideCard.getMeasuredHeight());
            if(compact!=null){int w=compact.getMeasuredWidth(),h=compact.getMeasuredHeight();int x=(int)safe.right-w,y=Math.max((int)safe.top,(int)safe.bottom-h-dp(22));compact.layout(x,y,x+w,y+h);}
            positionCard(false);
        }
        private boolean focusBounds(){
            if(spot==null||!actuallyVisible(spot)||!spot.getGlobalVisibleRect(rect))return false;
            getLocationOnScreen(position);target.set(rect);target.offset(-position[0],-position[1]);return target.intersect(safe);
        }
        private void positionCard(boolean animate){
            if(guideCard==null||compactMode||guideCard.getMeasuredHeight()==0)return;
            float w=guideCard.getMeasuredWidth(),h=guideCard.getMeasuredHeight(),x,y;
            if(focusBounds()&&step>=2){
                x=Math.max(safe.left,Math.min(target.centerX()-w/2,safe.right-w));
                float below=safe.bottom-target.bottom-dp(20),above=target.top-safe.top-dp(20);boolean putBelow=target.centerY()<safe.centerY();
                if(putBelow&&below<h&&above>below)putBelow=false;else if(!putBelow&&above<h&&below>above)putBelow=true;
                y=putBelow?target.bottom+dp(20):target.top-dp(20)-h;
            }else{x=safe.centerX()-w/2;y=safe.centerY()-h/2;}
            y=Math.max(safe.top,Math.min(y,safe.bottom-h));
            if(Math.abs(cardX-x)<1&&Math.abs(cardY-y)<1)return;cardX=x;cardY=y;
            guideCard.animate().cancel();if(placed&&animate)guideCard.animate().x(x).y(y).setDuration(160).start();else{guideCard.setX(x);guideCard.setY(y);guideCard.setAlpha(1);}placed=true;
        }
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();
            if(compact==null){compact=DevGramStudioControls.text(getContext(),"",13,true,editor.host.getResourceProvider());compact.setText(t(TITLES[step])+"  ▴");compact.setSingleLine(true);compact.setEllipsize(android.text.TextUtils.TruncateAt.END);compact.setPadding(dp(16),dp(11),dp(16),dp(11));compact.setBackground(DevGramStudioControls.surface(Theme.getColor(Theme.key_windowBackgroundWhite,editor.host.getResourceProvider()),Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,editor.host.getResourceProvider())));compact.setContentDescription(t("Развернуть подсказку"));compact.setElevation(dp(12));compact.setVisibility(GONE);compact.setOnClickListener(v->{compactMode=false;compact.setVisibility(GONE);guideCard.setVisibility(VISIBLE);placed=false;requestLayout();});addView(compact,new FrameLayout.LayoutParams(-2,-2));}
            if(step==13||step==14)postDelayed(collapse,3000);
        }
        @Override protected void onDetachedFromWindow(){removeCallbacks(collapse);if(guideCard!=null)guideCard.animate().cancel();super.onDetachedFromWindow();}

        private boolean hit(View view, float x, float y) {
            if (view == null || !view.isShown()) return false;
            int[] p = new int[2], o = new int[2]; view.getLocationOnScreen(p); getLocationOnScreen(o);
            return x >= p[0] - o[0] && x < p[0] - o[0] + view.getWidth() && y >= p[1] - o[1] && y < p[1] - o[1] + view.getHeight();
        }
        boolean claims(float x, float y) { return hit(guideCard, x, y) || hit(compact,x,y) || step != 14 && !hit(spot, x, y); }
        @Override public boolean onTouchEvent(MotionEvent event) { return claims(event.getX(), event.getY()); }
        @Override protected void onDraw(Canvas canvas) {
            positionCard(true);
            boolean visible=focusBounds();hole.set(target);hole.inset(-dp(5),-dp(5));
            shade.rewind(); shade.setFillType(Path.FillType.EVEN_ODD); shade.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
            if (visible) shade.addRoundRect(hole, dp(12), dp(12), Path.Direction.CW);
            ink.setStyle(Paint.Style.FILL); ink.setColor(0x65000000); canvas.drawPath(shade, ink);
            if (!visible) return;
            if(!compactMode&&guideCard!=null){float connectorX=Math.max(guideCard.getX()+dp(24),Math.min(hole.centerX(),guideCard.getX()+guideCard.getWidth()-dp(24)));float start,end;if(guideCard.getY()>=hole.bottom){start=hole.bottom+dp(5);end=guideCard.getY()-dp(5);}else{start=hole.top-dp(5);end=guideCard.getY()+guideCard.getHeight()+dp(5);}if((guideCard.getY()>=hole.bottom&&end>start)||(guideCard.getY()<hole.top&&end<start)){ink.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,editor.host.getResourceProvider()));ink.setStrokeWidth(dp(1.5f));ink.setAlpha(160);canvas.drawLine(connectorX,start,connectorX,end,ink);}}

            float time = (SystemClock.uptimeMillis() - opened) / 1000f;
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, editor.host.getResourceProvider());
            ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(dp(2)); ink.setColor(accent); ink.setAlpha(190 + (int)(65 * Math.sin(time * 4)));
            canvas.drawRoundRect(hole, dp(12), dp(12), ink);
            float x = hole.right - dp(12), y = hole.bottom + dp(14);
            if (step == 13 || step == 14) {
                float phase = time % 3.2f;
                y = hole.centerY() - (phase < .8f ? 0 : Math.min(1, (phase - .8f) / 1.4f) * dp(64));
                x = hole.centerX();
                if (step == 13 && phase >= .8f) { ink.setStrokeWidth(dp(2)); canvas.drawLine(dp(16), y - dp(20), getWidth() - dp(16), y - dp(20), ink); }
            }
            ink.setStyle(Paint.Style.FILL); ink.setAlpha(55); canvas.drawCircle(x, y, dp(15 + 3 * (float)Math.sin(time * 5)), ink);
            ink.setAlpha(255); canvas.drawCircle(x, y, dp(5), ink);
            postInvalidateOnAnimation();
        }
    }
}
