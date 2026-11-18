package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.*;
import java.util.ArrayList;

/** Element sheets follow the reference editor: live sliders, visual choices and grouped help. */
final class DevGramProfileEditSheet {
    final DevGramProfileEditor editor;
    final ProfileActivity host;
    String page;
    final Context context;
    final LinearLayout body;
    private BottomSheet sheet;
    private ScrollView scroll;
    private final java.util.ArrayDeque<String> history = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Integer> scrollHistory = new java.util.ArrayDeque<>();
    private final ArrayList<Runnable> bindings = new ArrayList<>();
    private final Runnable accessChanged = () -> { for(Runnable binding:bindings)binding.run(); };
    DevGramProfileEditSheet(DevGramProfileEditor editor,String page) {
        this.editor=editor;this.host=editor.host;this.page=page;context=host.getParentActivity();
        body=DevGramStudioControls.column(context);body.setPadding(0,AndroidUtilities.dp(4),0,AndroidUtilities.dp(24));
    }
    String t(String s){return DevGramLocale.text(s);}
    private int color(int key){return Theme.getColor(key,host.getResourceProvider());}
    private String title(){if(page.startsWith("color:"))return "Выбор цвета";if(page.startsWith("gradient:"))return "Градиент";if(page.startsWith("fade:"))return "Затухание";switch(page){case "text":return "Текст профиля";case "avatar":return "Аватар";case "frame":return "Рамка аватара";case "name":return "Имя";case "thought":return "Мысль";case "banner":return "Шапка и баннер";case "background":return "Фон профиля";case "layout":return "Своя компоновка";case "cards":return "Настроить блоки";case "buttons":return "Кнопки действий";case "rows":return "Порядок и видимость строк";default:return "Оформление профиля";}}
    boolean isShowing() { return sheet != null && sheet.isShowing(); }
    void navigate(String next) {
        if (!next.equals(page)) { history.push(page); scrollHistory.push(scroll == null ? 0 : scroll.getScrollY()); }
        page=next; rebuild();
    }
    private void rebuild() { rebuild(false); }
    private void rebuild(boolean preservePosition) {
        int position = preservePosition && scroll != null ? scroll.getScrollY() : 0;
        bindings.clear(); body.removeAllViews();
        if(!history.isEmpty())row("Назад","",this::back);
        build();
        if(sheet!=null&&sheet.getTitleView()!=null)sheet.getTitleView().setText(t(title()));
        if(scroll!=null)scroll.post(()->scroll.scrollTo(0,position));
    }
    private void back(){if(history.isEmpty())sheet.dismiss();else{page=history.pop();int position=scrollHistory.pop();rebuild();scroll.post(()->scroll.scrollTo(0,position));}}
    void show(){
        rebuild();
        scroll=new ScrollView(context){@Override protected void onMeasure(int width,int height){
            int available=AndroidUtilities.displaySize.y-AndroidUtilities.statusBarHeight-AndroidUtilities.dp(100);
            super.onMeasure(width,DevGramStudioControls.sheetHeightSpec(height,Math.min(available,(int)(AndroidUtilities.displaySize.y*.60f))));
        }};
        scroll.setFillViewport(false);scroll.addView(body);scroll.setClipToPadding(false);
        sheet=new BottomSheet(context,true,host.getResourceProvider()){
            @Override public void onBackPressed(){if(history.isEmpty())super.onBackPressed();else back();}
        };
        sheet.setTitle(t(title()));sheet.setCustomView(scroll);sheet.setDimBehindAlpha(45);
        DevGramProfilePolicy.listen(accessChanged);
        host.showDialog(sheet,d->{DevGramProfilePolicy.unlisten(accessChanged);if(editor.elementSheet==this)editor.elementSheet=null;});
        sheet.fixNavigationBarKey(Theme.key_dialogBackground);
    }
    private void header(String text){TextView view=DevGramStudioControls.text(context,text,14,true,host.getResourceProvider());view.setTextColor(color(Theme.key_windowBackgroundWhiteBlueText));view.setPadding(AndroidUtilities.dp(21),AndroidUtilities.dp(16),AndroidUtilities.dp(21),AndroidUtilities.dp(6));body.addView(view);}
    private void help(String summary,String detail){TextView view=DevGramStudioControls.text(context,"",12,false,host.getResourceProvider());view.setText(t(summary)+"  ⓘ");view.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));view.setPadding(AndroidUtilities.dp(21),AndroidUtilities.dp(4),AndroidUtilities.dp(21),AndroidUtilities.dp(10));view.setOnClickListener(v->{view.setText(t(detail));});body.addView(view);}
    private void row(String title,String value,Runnable action){
        LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(AndroidUtilities.dp(21),AndroidUtilities.dp(12),AndroidUtilities.dp(18),AndroidUtilities.dp(12));
        TextView label=DevGramStudioControls.text(context,title,15,false,host.getResourceProvider());row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        TextView right=DevGramStudioControls.text(context,"",13,false,host.getResourceProvider());right.setText(value.isEmpty()?"›":t(value)+"  ›");right.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));row.addView(right);row.setMinimumHeight(AndroidUtilities.dp(48));row.setBackground(Theme.createSelectorDrawable(color(Theme.key_listSelector),2));row.setOnClickListener(v->action.run());body.addView(row);
    }
    private void row(CharSequence title,String value,Runnable action){row("",value,action);LinearLayout view=(LinearLayout)body.getChildAt(body.getChildCount()-1);((TextView)view.getChildAt(0)).setText(title);}
    private void slider(String key,String title,int low,int high,int fallback,String unit){
        DevGramStudioControls.Slider view=new DevGramStudioControls.Slider(context,host.getResourceProvider());
        String feature=DevGramProfileDocument.FEATURES.get(key);
        Runnable bind=()->view.bind(DevGramAppearanceAccess.label(host,feature,t(title)),editor.draft.document.optInt(key,fallback),low,high,unit,
                DevGramAppearanceAccess.allowed(host,feature),n->{if(DevGramProfilePolicy.allowed(editor.uid,feature))editor.put(key,n);},()->{},()->DevGramAppearanceAccess.check(host,feature));
        bindings.add(bind);bind.run();body.addView(view,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(82)));
    }
    private void themeSlider(String key,String title,int low,int high){
        DevGramStudioControls.Slider view=new DevGramStudioControls.Slider(context,host.getResourceProvider());String feature=DevGramProfileStyle.feature(key,1);
        Runnable bind=()->view.bind(DevGramAppearanceAccess.label(host,feature,t(title)),theme().get(key),low,high,"%",DevGramAppearanceAccess.allowed(host,feature),n->{if(DevGramAppearanceAccess.allowed(host,feature))editor.setTheme(key,n);},()->{},()->DevGramAppearanceAccess.check(host,feature));
        bindings.add(bind);bind.run();body.addView(view,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(82)));
    }
    private DevGramProfileStyle.Style theme(){return editor.draft.theme==null?DevGramProfileStyle.raw(editor.draft.target(host.getCurrentAccount())):editor.draft.theme;}
    private void choices(String key,String[] labels,boolean basic){
        LinearLayout holder=DevGramStudioControls.column(context);body.addView(holder);
        Runnable bind=()->{
            holder.removeAllViews();CharSequence[] accessible=new CharSequence[labels.length];
            for(int i=0;i<labels.length;i++)accessible[i]=DevGramAppearanceAccess.label(host,basic?DevGramProfileStyle.feature(key,i):key.equals("layout")&&i==3?"profile.layout.advanced":DevGramProfileDocument.FEATURES.get(key),t(labels[i]));
            holder.addView(DevGramStudioControls.choices(context,key,labels,accessible,basic?theme().get(key):editor.draft.document.optInt(key),theme(),host.getResourceProvider(),i->{
                String feature=basic?DevGramProfileStyle.feature(key,i):key.equals("layout")&&i==3?"profile.layout.advanced":DevGramProfileDocument.FEATURES.get(key);
                if(!DevGramAppearanceAccess.check(host,feature))return;
                if(key.equals("nameFont"))editor.remove("nameFontFile");
                if(basic)editor.setTheme(key,i);else editor.put(key,i);if(key.equals("nameEffect"))rebuild(true);else accessChanged.run();
            }));
        };bindings.add(bind);bind.run();
    }
    private void colors(String key,String title){
        String feature=DevGramProfileDocument.FEATURES.get(key);
        LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(AndroidUtilities.dp(52));
        row.setPadding(AndroidUtilities.dp(21),AndroidUtilities.dp(8),AndroidUtilities.dp(21),AndroidUtilities.dp(8));
        row.setBackground(Theme.createSelectorDrawable(color(Theme.key_listSelector),2));
        TextView label=DevGramStudioControls.text(context,title,15,false,host.getResourceProvider());row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        TextView value=DevGramStudioControls.text(context,"",12,false,host.getResourceProvider());value.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));row.addView(value);
        View dot=new View(context);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(AndroidUtilities.dp(26),AndroidUtilities.dp(26));params.leftMargin=AndroidUtilities.dp(12);row.addView(dot,params);
        Runnable bind=()->{
            label.setText(DevGramAppearanceAccess.label(host,feature,t(title)));
            int rgb=editor.draft.document.optInt(key,key.equals("thoughtColor")||key.equals("nickColor")||key.equals("cardLightColor")?0xffffff:key.equals("cardColor")||key.equals("buttonColor")?0x293450:key.equals("thoughtBackground")?0x293450:key.endsWith("ShadowColor")?0:key.endsWith("Color3")?0x8e74e2:key.endsWith("Color2")?0x5077cf:0x101526);
            value.setText((editor.draft.document.has(key)||key.startsWith("thought")||key.startsWith("nameShadow"))?String.format(java.util.Locale.US,"#%06X",rgb):t("Как в Telegram"));
            android.graphics.drawable.GradientDrawable background=DevGramStudioControls.surface(0xff000000|rgb,color(Theme.key_divider));background.setCornerRadius(AndroidUtilities.dp(13));dot.setBackground(background);
        };
        bindings.add(bind);bind.run();row.setOnClickListener(v->{if(DevGramAppearanceAccess.check(host,feature))navigate("color:"+key);});body.addView(row);
    }
    private void toggle(String key,String title,boolean fallback){
        org.telegram.ui.Cells.TextCheckCell cell=new org.telegram.ui.Cells.TextCheckCell(context,host.getResourceProvider());
        String feature=DevGramProfileDocument.FEATURES.get(key);
        Runnable bind=()->cell.setTextAndCheck(DevGramAppearanceAccess.label(host,feature,t(title)),editor.draft.document.optInt(key,fallback?1:0)==1,true);
        bindings.add(bind);bind.run();cell.setOnClickListener(v->{if(DevGramAppearanceAccess.check(host,feature)){int next=editor.draft.document.optInt(key,fallback?1:0)==1?0:1;editor.put(key,next);if(next==1&&key.equals("cardGlass"))editor.put("cardVolume",0);if(next==1&&key.equals("cardVolume"))editor.put("cardGlass",0);rebuild(true);}});body.addView(cell);
    }
    private void segments(String key,String[] labels,int fallback){
        String feature=DevGramProfileDocument.FEATURES.get(key);
        CharSequence[] accessible=new CharSequence[labels.length];for(int i=0;i<labels.length;i++)accessible[i]=DevGramAppearanceAccess.label(host,feature,t(labels[i]));
        body.addView(DevGramStudioControls.pills(context,labels,accessible,editor.draft.document.optInt(key,fallback),host.getResourceProvider(),i->{if(DevGramAppearanceAccess.check(host,feature)){editor.put(key,i);rebuild(true);}}));
    }
    private void field(String key,String title,int maximum,boolean multiline){
        String feature=DevGramProfileDocument.FEATURES.get(key);header(title);
        EditText edit=new EditText(context);edit.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));edit.setHintTextColor(color(Theme.key_windowBackgroundWhiteGrayText));edit.setTextSize(16);edit.setSingleLine(!multiline);edit.setMaxLines(multiline?4:1);edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(maximum)});edit.setHint(t(title));edit.setText(editor.draft.document.optString(key));edit.setPadding(AndroidUtilities.dp(21),AndroidUtilities.dp(8),AndroidUtilities.dp(21),AndroidUtilities.dp(8));
        Runnable bind=()->{edit.setEnabled(DevGramAppearanceAccess.allowed(host,feature));edit.setHint(DevGramAppearanceAccess.label(host,feature,t(title)));};bindings.add(bind);bind.run();edit.addTextChangedListener(new TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}@Override public void onTextChanged(CharSequence s,int start,int before,int count){if(DevGramAppearanceAccess.allowed(host,feature))editor.put(key,s.toString());}@Override public void afterTextChanged(Editable s){}});body.addView(edit);
        row(title,"",()->DevGramAppearanceAccess.check(host,feature));
    }
    private void build(){
        switch(page){
            case "text":textPalette();break;
            case "avatar":
                header("Форма");choices("shape",new String[]{"Круг","Скруглённый квадрат","Суперэллипс","Квадрат","Шестиугольник","Пятиугольник","Звезда","Сердце","Цветок","Своя"},true);
                slider("avatarRadius","Скругление углов",0,64,18,"");slider("avatarRound","Округлость",0,100,0,"%");
                row("Нарисовать свою форму","",()->editor.sheet("shape"));
                header("Вид");slider("avatarAlpha","Прозрачность",0,100,100,"%");slider("avatarDim","Затемнение",0,100,0,"%");slider("avatarFade","Растушёвка краёв",0,100,0,"%");slider("avatarFadeRadius","Радиус растушёвки",0,100,50,"%");
                help("Выбирайте форму по рисунку.","Выбранная форма сразу применяется к аватару в профиле. Настройки рамки открываются отдельно через меню аватара.");
                header("Расположение");row("Своя компоновка","",()->editor.sheet("layout"));break;
            case "frame":
                header("Рамка аватара");choices("frame",new String[]{"Без рамки","Тонкая обводка","Градиентная рамка","Орбитальная рамка","Звёздная рамка"},true);
                header("Своя рамка");row("Студия рамок","",()->editor.advanced("layers"));row("Мои проекты","",()->editor.advanced("files"));row("Мастерская","",editor::workshop);break;
            case "name":
                header("Начертание");slider("nameSize","Размер",50,200,100,"%");
                choices("nameFont",new String[]{"Стандарт","Тонкий","С засечками","Моноширинный","Курсив","Узкий"},false);
                row("Свои шрифты","",()->editor.advanced("fonts"));
                header("Цвет");toggle("nameColorEnabled","Свой цвет имени",editor.draft.document.optInt("nickColor")!=0);if(editor.draft.document.optInt("nameColorEnabled",editor.draft.document.optInt("nickColor")!=0?1:0)==1)colors("nickColor","Цвет имени");
                header("Свечение");toggle("nameGlow","Свечение имени",false);if(editor.draft.document.optInt("nameGlow")==1){slider("nameGlowRadius","Радиус",0,40,12,"");slider("nameGlowStrength","Сила",0,100,100,"%");}
                header("Эффекты");choices("nameEffect",new String[]{"Нет","Пульс","Градиент","Шиммер","Радуга","Неон","Огонь","Лёд"},false);
                int effect=editor.draft.document.optInt("nameEffect");if(effect!=0)slider("nameSpeed","Скорость",10,300,100,"%");
                if(effect==2){colors("nickColor","Первый цвет градиента");slider("nickAlpha","Прозрачность первого цвета",0,100,100,"%");colors("nickColor2","Второй цвет градиента");slider("nickAlpha2","Прозрачность второго цвета",0,100,100,"%");}
                if(effect>=2&&effect!=5)slider("nameAngle","Направление",0,360,0,"°");
                toggle("nameShadow","Тень имени",false);if(editor.draft.document.optInt("nameShadow")==1){colors("nameShadowColor","Цвет тени");slider("nameShadowRadius","Размытие",0,40,6,"");slider("nameShadowStrength","Сила",0,100,70,"%");slider("nameShadowX","Сдвиг вбок",-20,20,2,"");slider("nameShadowY","Сдвиг вниз",-20,20,2,"");}
                help("Тень заменяет свечение.","Чтобы увидеть мягкое свечение в цвет имени, выключите тень. Эффекты замирают при экономии батареи или отключении анимаций.");
                row("Мысль","",()->editor.sheet("thought"));row("Имя и сообщения","",()->editor.advanced("messages"));break;
            case "thought":
                field("thoughtText","Текст мысли",200,true);help("Пустой текст скрывает облачко.","Мысль — отдельное облачко возле аватара. Оно видно пользователям DevGram и не меняет значок роли или текст сообщений.");
                header("Цвета");colors("thoughtColor","Цвет текста");slider("thoughtTextAlpha","Прозрачность текста",0,100,100,"%");colors("thoughtBackground","Цвет облачка");slider("thoughtAlpha","Прозрачность облачка",0,100,100,"%");
                header("Тень");toggle("thoughtShadow","Тень облачка",false);if(editor.draft.document.optInt("thoughtShadow")==1){colors("thoughtShadowColor","Цвет тени");slider("thoughtShadowRadius","Размытие",0,40,6,"");slider("thoughtShadowStrength","Сила",0,100,70,"%");slider("thoughtShadowX","Сдвиг вбок",-20,20,0,"");slider("thoughtShadowY","Сдвиг вниз",-20,20,3,"");}
                header("Начертание");toggle("thoughtFontCopy","Как у имени",true);if(editor.draft.document.optInt("thoughtFontCopy",1)!=1)choices("thoughtFont",new String[]{"Стандарт","Тонкий","С засечками","Моноширинный","Курсив","Узкий"},false);break;
            case "banner":case "background":
                surface(page.equals("banner")?"banner":"page");
                header("Узор и частицы");choices("pattern",new String[]{"Без узора","Точки","Звёздная пыль","Волны","Сетка"},true);
                if(page.equals("banner"))headerPresets();
                else row("Настроить блоки","",()->editor.sheet("cards"));break;
            case "layout":referenceLayout();break;
            case "cards":
                toggle("cardEnabled","Перекрасить блоки",editor.draft.document.has("cardColor"));
                if(editor.draft.document.optInt("cardEnabled",editor.draft.document.has("cardColor")?1:0)==1){
                    header("Цвет и прозрачность");colors("cardColor","Цвет блоков");slider("cardAlpha","Плотность",0,100,100,"%");
                    header("Эффекты поверхности");toggle("cardGlass","Жидкое стекло",false);
                    if(editor.draft.document.optInt("cardGlass")==1){
                        slider("cardGlassBlur","Мутность стекла",2,40,12,"");slider("cardGlassBend","Преломление у краёв",0,100,40,"%");slider("cardGlassThick","Толщина грани",0,24,8,"");slider("cardGlassLight","Свет по краю",0,200,100,"%");
                        help("Размывает фон и подчёркивает края карточки.","Стекло размывает только фон за карточкой, сохраняя чёткий текст. Преломление поддерживается с Android 13, размытие — с Android 12. На старых версиях остаётся выбранный цвет.");
                    }else{
                        slider("cardBlur","Размытие за блоками",0,50,0,"");toggle("cardVolume","Объём",false);
                        if(editor.draft.document.optInt("cardVolume")==1){
                            header("Настройки объёма");slider("cardLightAngle","Направление",0,360,135,"°");colors("cardLightColor","Цвет света");toggle("cardSideLight","Боковой свет",false);if(editor.draft.document.optInt("cardSideLight")==1)slider("cardCenterBrightness","Яркость центра",0,100,100,"%");
                            slider("cardLightRadius","Радиус",0,1500,100," dp");slider("cardLightDistance","Расстояние",0,500,0,"");slider("cardLightStrength","Сила",0,100,65,"%");
                            help("От мягкого света до контрастной обводки.","Направление выбирает освещённую сторону. Радиус задаёт мягкость перехода, расстояние смещает свет от центра к краю. Боковой свет отдельно подчёркивает контур.");
                        }
                    }
                    header("Форма");toggle("cardJoin","Соединить ближайшие блоки",false);toggle("cardRadiusEnabled","Своё скругление",false);if(editor.draft.document.optInt("cardRadiusEnabled")==1)slider("cardRadius","Скругление блоков",0,30,12,"");
                }
                header("Свои строки");row("Добавить строку","",()->editor.addBlock(null));row("Видимость строк","",()->editor.sheet("rows"));row("Кнопки действий","",()->editor.sheet("buttons"));break;
            case "buttons":
                header("Цвет");toggle("buttonCopyCards","Копировать цвет блоков",true);
                if(editor.draft.document.optInt("buttonCopyCards",1)!=1){colors("buttonColor","Цвет кнопок");slider("buttonAlpha","Плотность",0,100,100,"%");slider("buttonBlur","Размытие за кнопками",0,50,0,"");}
                help("Отдельное оформление кнопок профиля.","Кнопки под шапкой могут копировать цвет и эффекты карточек либо использовать собственный цвет, прозрачность и размытие фона.");
                header("Форма кнопок");choices("buttonShape",new String[]{"Как в Telegram","Капсулы","Скруглённые","Круглые"},false);
                row("Своя компоновка","",()->editor.sheet("part:buttons"));break;
            case "rows": rows();break;
            case "shape":
                header("Нарисовать свою форму");
                DevGramShapePad pad=new DevGramShapePad(context,editor.draft.document.optJSONArray("customShape"),points->{if(DevGramAppearanceAccess.allowed(host,"avatar.shape")){if(points.length()<3){editor.remove("customShape");if(theme().get("shape")==9)editor.setTheme("shape",0);}else{editor.put("customShape",points);editor.setTheme("shape",9);}}});
                Runnable shapeAccess=()->pad.setAccess(DevGramAppearanceAccess.allowed(host,"avatar.shape"),()->DevGramAppearanceAccess.check(host,"avatar.shape"));bindings.add(shapeAccess);shapeAccess.run();
                body.addView(pad,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(300)));row("Применить форму","",()->{if(DevGramAppearanceAccess.check(host,"avatar.shape"))pad.apply();});row("Сбросить","",()->{if(DevGramAppearanceAccess.check(host,"avatar.shape"))pad.reset();});
                help("Удерживайте палец, затем нарисуйте контур аватара.","Круг показывает границу обычного аватара. Контур замыкается автоматически: не нужно возвращаться точно в начало. Прокрутка листа не стирает рисунок.");break;
            default:
                if(page.startsWith("part:"))part(page.substring(5));
                else if(page.startsWith("color:")){
                    String key=page.substring(6);header("Выбор цвета");
                    DevGramColorPickerView picker=new DevGramColorPickerView(context,host.getResourceProvider());picker.setColor(0xff000000|editor.draft.document.optInt(key,0x5077cf));
                    body.addView(DevGramStudioControls.swatches(context,editor.draft.document.optInt(key),host.getResourceProvider(),value->{if(DevGramAppearanceAccess.allowed(host,DevGramProfileDocument.FEATURES.get(key))){picker.setColor(0xff000000|value);editor.put(key,value);}}));
                    picker.setListener(value->{if(DevGramAppearanceAccess.allowed(host,DevGramProfileDocument.FEATURES.get(key)))editor.put(key,value&0xffffff);});body.addView(picker);
                    row("Как в Telegram","",()->{editor.remove(key);back();});row("Готово","",this::back);
                }else if(page.startsWith("gradient:"))surfaceEffect(page.substring(9),false);
                else if(page.startsWith("fade:"))surfaceEffect(page.substring(5),true);
        }
    }
    private boolean referenceSectionOpen;
    private TextView referenceLabel(CharSequence title){TextView text=new TextView(context);text.setText(title);text.setTextSize(14);text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));text.setPadding(AndroidUtilities.dp(24),AndroidUtilities.dp(12),AndroidUtilities.dp(24),AndroidUtilities.dp(4));body.addView(text);referenceSectionOpen=false;return text;}
    private void referenceSection(String title){LinearLayout group=new LinearLayout(context);group.setOrientation(LinearLayout.VERTICAL);group.setPadding(AndroidUtilities.dp(24),AndroidUtilities.dp(24),AndroidUtilities.dp(24),AndroidUtilities.dp(10));int accent=Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);TextView label=new TextView(context);label.setText(t(title));label.setTextSize(22);label.setTypeface(android.graphics.Typeface.create("sans-serif-black",0));label.setTextColor(accent);group.addView(label,new LinearLayout.LayoutParams(-1,-2));View line=new View(context);line.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(accent,85));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(2));p.topMargin=AndroidUtilities.dp(10);group.addView(line,p);body.addView(group);referenceSectionOpen=true;}
    private void referenceRow(String title,String value,Runnable action){DevGramReferenceHeaderRow row=new DevGramReferenceHeaderRow(context,false);row.setTextAndValue(t(title),t(value.isEmpty()?"Открыть ›":value),false);DevGramReferenceHeaderRow.decorateAction(row);row.setOnClickListener(v->action.run());body.addView(row);referenceSectionOpen=false;}
    private void referenceHelp(String summary,String detail){
        boolean inline=body.getChildCount()>0&&body.getChildAt(body.getChildCount()-1) instanceof DevGramReferenceHeaderRow;LinearLayout help=new LinearLayout(context);help.setGravity(Gravity.TOP);help.setPadding(inline?0:AndroidUtilities.dp(24),0,inline?0:AndroidUtilities.dp(16),AndroidUtilities.dp(2));TextView text=new TextView(context);text.setText(t(summary));text.setTextSize(13);text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));text.setPadding(0,AndroidUtilities.dp(6),AndroidUtilities.dp(8),0);help.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        TextView question=new TextView(context);question.setText("?");question.setTextSize(18);question.setGravity(Gravity.CENTER);int accent=Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);question.setTextColor(accent);android.graphics.drawable.GradientDrawable circle=new android.graphics.drawable.GradientDrawable();circle.setShape(1);circle.setStroke(AndroidUtilities.dp(1),androidx.core.graphics.ColorUtils.setAlphaComponent(accent,110));circle.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(accent,18));question.setBackground(new android.graphics.drawable.InsetDrawable(circle,AndroidUtilities.dp(8),AndroidUtilities.dp(8),AndroidUtilities.dp(8),AndroidUtilities.dp(8)));question.setContentDescription(t("Подробнее: ")+t(summary));question.setOnClickListener(v->new org.telegram.ui.ActionBar.AlertDialog.Builder(context,host.getResourceProvider()).setTitle(t("Результат настройки")).setMessage(t(detail)).setPositiveButton(t("Понятно"),null).show());help.addView(question,new LinearLayout.LayoutParams(AndroidUtilities.dp(44),AndroidUtilities.dp(44)));
        if(inline)((DevGramReferenceHeaderRow)body.getChildAt(body.getChildCount()-1)).setHelp(help);else body.addView(help);referenceSectionOpen=false;
    }
    private JSONObject headerConfig(){return editor.draft.document.has("headerMode")?DevGramHeaderLayout.config(editor.draft.document):DevGramHeaderLayout.custom(editor.draft.document);}
    private void headerChanged(){editor.draft.dirty=true;host.devgramRefreshEditor();accessChanged.run();}
    private void headerPut(String key,double value){if(!DevGramAppearanceAccess.check(host,"profile.layout.advanced"))return;DevGramHeaderLayout.set(editor.draft.document,key,value);headerChanged();}
    private void headerPresets(){
        referenceSection("Компоновка шапки");
        DevGramHeaderLayoutCards cards=new DevGramHeaderLayoutCards(context,DevGramHeaderLayout.displayMode(editor.draft.document),mode->{
            if(!DevGramAppearanceAccess.check(host,mode==4?"profile.layout.advanced":"profile.layout")){rebuild(true);return;}
            if(mode==4){JSONObject seed=DevGramHeaderLayout.custom(editor.draft.document);DevGramHeaderLayout.replace(editor.draft.document,seed);}else editor.put("headerMode",mode);
            headerChanged();rebuild(true);
        },host.getResourceProvider());body.addView(cards);
        Runnable bindCards=()->{cards.setSelected(DevGramHeaderLayout.displayMode(editor.draft.document));cards.setLocked(!DevGramAppearanceAccess.allowed(host,"profile.layout"),!DevGramAppearanceAccess.allowed(host,"profile.layout.advanced"));};bindings.add(bindCards);bindCards.run();
        if(DevGramHeaderLayout.displayMode(editor.draft.document)==4)referenceRow("Собрать свою компоновку","части шапки",()->editor.sheet("layout"));
        referenceHelp("Своя расстановка элементов шапки.","Аватар, имя, статус и кнопки располагаются согласно выбранной компоновке. Собственную расстановку можно изменить в отдельном редакторе; параметры хранятся отдельно для этой компоновки.");
    }
    private void headerCheck(String title,String key,boolean local){
        DevGramReferenceHeaderRow view=new DevGramReferenceHeaderRow(context,true);referenceSectionOpen=false;
        Runnable bind=()->view.setTextAndCheck(DevGramAppearanceAccess.label(host,"profile.layout.advanced",t(title)),local?MessagesController.getGlobalMainSettings().getBoolean("devgramHeaderAnchorAlways",false):headerConfig().optInt(key)==1,true);
        bindings.add(bind);bind.run();view.setOnClickListener(v->{if(!DevGramAppearanceAccess.check(host,"profile.layout.advanced"))return;if(local){boolean next=!MessagesController.getGlobalMainSettings().getBoolean("devgramHeaderAnchorAlways",false);view.setChecked(next);MessagesController.getGlobalMainSettings().edit().putBoolean("devgramHeaderAnchorAlways",next).apply();host.devgramRefreshEditor();accessChanged.run();}else{view.setChecked(headerConfig().optInt(key)!=1);headerPut(key,view.isChecked()?1:0);}});body.addView(view);
    }
    private void headerChoice(String title,String key,String[] labels,int[] values){
        int current=headerConfig().optInt(key),selected=0;for(int i=0;i<values.length;i++)if(values[i]==current)selected=i;
        if(!referenceSectionOpen)referenceLabel(DevGramAppearanceAccess.label(host,"profile.layout.advanced",t(title)));referenceSectionOpen=false;
        DevGramHeaderSegments segments=new DevGramHeaderSegments(context,labels,selected,index->headerPut(key,values[index]));body.addView(segments);
        Runnable bind=()->{int value=headerConfig().optInt(key),index=0;for(int i=0;i<values.length;i++)if(values[i]==value)index=i;segments.bind(index,DevGramAppearanceAccess.allowed(host,"profile.layout.advanced"));};bindings.add(bind);bind.run();
    }
    private void headerSlider(String title,String key,int low,int high,String unit){
        int initial=(int)Math.round(headerConfig().optDouble(key));TextView label=referenceLabel(DevGramAppearanceAccess.label(host,"profile.layout.advanced",t(title)+": "+initial+" "+unit));
        SeekBarView seek=new SeekBarView(context,host.getResourceProvider());seek.setReportChanges(true);body.addView(seek,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(48)));boolean[] dragging={false};
        Runnable bind=()->{int current=(int)Math.round(headerConfig().optDouble(key));label.setText(DevGramAppearanceAccess.label(host,"profile.layout.advanced",t(title)+": "+current+" "+unit));boolean allowed=DevGramAppearanceAccess.allowed(host,"profile.layout.advanced");seek.setEnabled(allowed);seek.setAlpha(allowed?1:.35f);if(!dragging[0])seek.setProgress((current-low)/(float)(high-low));};bindings.add(bind);bind.run();
        label.setOnClickListener(v->{if(!DevGramAppearanceAccess.allowed(host,"profile.layout.advanced"))DevGramAppearanceAccess.check(host,"profile.layout.advanced");});
        seek.setDelegate(new SeekBarView.SeekBarViewDelegate(){public void onSeekBarDrag(boolean stop,float progress){headerPut(key,Math.round(low+progress*(high-low)));}public void onSeekBarPressed(boolean pressed){dragging[0]=pressed;}public CharSequence getContentDescription(){return label.getText();}});
    }
    private void referenceLayout(){
        help("Двумя пальцами меняйте размер и поворот элемента.","Коснитесь элемента шапки одним пальцем и добавьте второй. Разводите пальцы для увеличения, сближайте для уменьшения, поворачивайте для вращения. Один палец перемещает элемент; привязка к другому элементу сохраняется.");
        referenceSection("Части шапки");String[] keys={"avatar","name","status","buttons","title"},labels={"Аватар","Имя","Статус","Кнопки","Звание"};JSONObject config=headerConfig();
        for(int i=0;i<keys.length;i++){String nativeKey=keys[i],key=DevGramHeaderLayout.PARTS[i];boolean anchor=config.optInt("anchor_"+key+"_to")!=0,offset=config.optDouble(key+"_x")!=0||config.optDouble(key+"_y")!=0||config.optDouble(key+"_rotate")!=0||config.optDouble(key+"_scale_x",100)!=100||config.optDouble(key+"_scale_y",100)!=100;referenceRow(labels[i],anchor?offset?"привязка и смещение":"привязка":offset?"смещение":"штатно",()->editor.sheet("part:"+nativeKey));}
        referenceHelp("Своя позиция каждой части шапки.","Аватар, имя, статус и кнопки можно расположить отдельно, выбрав привязку, смещение и размер каждого элемента.");
        referenceSection("Общее");headerCheck("Пересчитывать привязку всегда","",true);
        referenceHelp("Положение сохраняется при прокрутке.","Фиксация сохраняет выбранную расстановку элементов после открытия профиля. Постоянное обновление позволяет ей следовать за изменениями шапки при прокрутке.");
        headerChoice("Крепление имени","name_anchor",new String[]{"По центру","Слева","Справа"},new int[]{0,1,2});referenceHelp("Откуда расширяется имя.","При центральной опоре имя расширяется в обе стороны. При опоре слева текст расширяется вправо.");
        referenceSection("Кнопки действий");headerCheck("Повернуть кнопки","vertical_actions",false);headerCheck("Не растягивать иконки и текст","actions_plain_content",false);
        headerSlider("Масштаб содержимого по горизонтали","actions_content_scale_x",10,300,"%");headerSlider("Масштаб содержимого по вертикали","actions_content_scale_y",10,300,"%");
        referenceHelp("Наклон кнопок или их содержимого.","Поворот панели наклоняет все кнопки вместе. Отдельный поворот содержимого меняет угол значков, подписей и счётчиков.");
        referenceSection("Запись компоновки");referenceRow("Конфиг JSON","правка целиком",this::headerJson);
        referenceRow("Вернуть штатную компоновку","",()->{if(!DevGramAppearanceAccess.check(host,"profile.layout"))return;editor.remove("headerConfig");editor.put("headerMode",0);MessagesController.getGlobalMainSettings().edit().putBoolean("devgramHeaderAnchorAlways",false).apply();headerChanged();rebuild(true);});((DevGramReferenceHeaderRow)body.getChildAt(body.getChildCount()-1)).textView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        referenceHelp("Другие увидят вашу расстановку.","После сохранения оформления выбранное расположение шапки будет видно посетителям профиля с DevGram.");
    }
    private void headerJson(){
        if(!DevGramAppearanceAccess.check(host,"profile.layout.advanced"))return;EditText input=new EditText(context);input.setText(DevGramHeaderLayout.export(editor.draft.document));input.setTextSize(13);input.setMinLines(7);input.setMaxLines(14);input.setGravity(Gravity.TOP);input.setTypeface(android.graphics.Typeface.MONOSPACE);input.setHorizontallyScrolling(true);input.setSelectAllOnFocus(false);
        org.telegram.ui.ActionBar.AlertDialog dialog=new org.telegram.ui.ActionBar.AlertDialog.Builder(context,host.getResourceProvider()).setTitle(t("Конфиг компоновки")).setView(input).setPositiveButton(t("Применить"),null).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).create();dialog.show();dialog.getButton(org.telegram.ui.ActionBar.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{JSONObject config=DevGramHeaderLayout.parse(input.getText().toString());if(!DevGramAppearanceAccess.allowed(host,"profile.layout.advanced"))return;DevGramHeaderLayout.replace(editor.draft.document,config);headerChanged();dialog.dismiss();rebuild(true);}catch(Exception invalid){input.setError(t("Неверный конфиг компоновки"));}});
    }
    private void part(String key){
        String part=key.equals("buttons")?"actions":key;referenceSection("Привязка");
        String[] all={"Штатное место","Экран","Мысль","Аватар","Имя","Статус","Кнопки","Звание"};java.util.ArrayList<String> labels=new java.util.ArrayList<>();java.util.ArrayList<Integer> targets=new java.util.ArrayList<>();int self=java.util.Arrays.asList(DevGramHeaderLayout.PARTS).indexOf(part)+3;
        for(int i=0;i<all.length;i++)if(i!=self){labels.add(all[i]);targets.add(i);}int[] values=new int[targets.size()];for(int i=0;i<values.length;i++)values[i]=targets.get(i);
        headerChoice("Привязать к","anchor_"+part+"_to",labels.toArray(new String[0]),values);
        String[] horizontal={"Слева","По центру","Справа"},vertical={"Сверху","По центру","Снизу"};int[] edges={0,1,2};
        headerChoice("Точка цели по горизонтали","anchor_"+part+"_to_x",horizontal,edges);headerChoice("Точка цели по вертикали","anchor_"+part+"_to_y",vertical,edges);
        headerChoice("Точка части по горизонтали","anchor_"+part+"_from_x",horizontal,edges);headerChoice("Точка части по вертикали","anchor_"+part+"_from_y",vertical,edges);
        headerSlider("Отступ по горизонтали","anchor_"+part+"_x",-600,600," dp");headerSlider("Отступ по вертикали","anchor_"+part+"_y",-600,600," dp");
        referenceHelp("Положение относительно другого элемента.","Привязка задаёт, возле какой точки другого элемента находится выбранная часть шапки. Смещение позволяет отодвинуть её в нужную сторону.");
        referenceSection("Смещение");headerSlider("Сдвиг по горизонтали",part+"_x",-45,45,"%");headerSlider("Сдвиг по вертикали",part+"_y",-45,45,"%");headerSlider("Поворот",part+"_rotate",-180,180,"°");headerSlider("Масштаб по горизонтали",part+"_scale_x",10,300,"%");headerSlider("Масштаб по вертикали",part+"_scale_y",10,300,"%");
        referenceHelp("Сдвиг элемента и его масштаб.","Смещение перемещает элемент по горизонтали и вертикали. Масштаб увеличивает или уменьшает его. Пропорции положения сохраняются на экранах разной ширины.");
    }
    private void surface(String prefix){
        header(prefix.equals("banner")?"Тип баннера":"Тип фона");
        String[] labels={"Как в Telegram","Один цвет","Статичный градиент","Фото, видео и GIF","Северное сияние","Живой шёлк"};
        CharSequence[] accessible=new CharSequence[labels.length];for(int i=0;i<labels.length;i++)accessible[i]=DevGramAppearanceAccess.label(host,DevGramProfileDocument.pageFeature(i),t(labels[i]));
        body.addView(DevGramStudioControls.choices(context,"background",labels,accessible,editor.draft.document.optInt(prefix+"Mode"),theme(),host.getResourceProvider(),i->{
            if(i==3&&DevGramProfileMedia.any(host.getUserConfig().getClientUserId())||DevGramAppearanceAccess.check(host,DevGramProfileDocument.pageFeature(i))){editor.put(prefix+"Mode",i);rebuild(true);}
        }));
        int mode=editor.draft.document.optInt(prefix+"Mode");
        if(mode==1)colors(prefix+"Color","Основной цвет");
        if(mode==2||mode>=4){header("Градиент");colors(prefix+"Color","Первый цвет");colors(prefix+"Color2","Второй цвет");row("Настроить градиент","",()->navigate("gradient:"+prefix));}
        if(mode==3){header("Тип медиа");
            row(DevGramAppearanceAccess.label(host,"profile.media",t("Статичный фон")),"",()->{if(DevGramAppearanceAccess.check(host,"profile.media"))host.showAsSheet(new DevGramProfileBgActivity(editor.draft,prefix,0));});
            row(DevGramAppearanceAccess.label(host,"profile.media.animated",t("Анимированный фон")),"",()->{if(DevGramAppearanceAccess.check(host,"profile.media.animated"))host.showAsSheet(new DevGramProfileBgActivity(editor.draft,prefix,1));});
        }
        JSONObject selectedVideo=DevGramProfileMedia.resolve(editor.draft.document,host.getUserConfig().getClientUserId(),prefix);
        if(mode==3&&selectedVideo!=null&&selectedVideo.optString("kind").equals("video")){
            header("Звук видео");
            org.telegram.ui.Cells.TextCheckCell sound=new org.telegram.ui.Cells.TextCheckCell(context,host.getResourceProvider());
            Runnable bindSound=()->sound.setTextAndCheck(DevGramAppearanceAccess.label(host,"profile.media.animated",t("Включить звук")),editor.draft.document.optInt(prefix+"Sound")==1,false);bindings.add(bindSound);bindSound.run();
            sound.setOnClickListener(v->{if(!DevGramAppearanceAccess.check(host,"profile.media.animated"))return;boolean enabled=editor.draft.document.optInt(prefix+"Sound")!=1;String other=prefix.equals("banner")?"page":"banner";try{editor.draft.document.put(prefix+"Sound",enabled?1:0);if(enabled){editor.draft.document.put(other+"Sound",0);if(!editor.draft.document.has(prefix+"Volume")||editor.draft.document.optInt(prefix+"Volume")==0)editor.draft.document.put(prefix+"Volume",20);}editor.draft.dirty=true;host.devgramRefreshEditor();accessChanged.run();}catch(Exception ignored){}});body.addView(sound);
            help("Звук только у одного видео.","Звук можно включить либо у баннера, либо у фона. При включении одного второй остаётся без звука. Громкость сохраняется для текущего оформления.");
        }
        if(mode>0){
            header("Вид");slider(prefix+"Alpha","Непрозрачность",0,100,100,"%");slider(prefix+"Dim","Затемнение",0,100,0,"%");
            slider(prefix.equals("banner")?"blur":"pageBlur","Размытие фона",0,prefix.equals("banner")?20:32,0,"");
            row("Затухание","",()->navigate("fade:"+prefix));
        }
        if(prefix.equals("banner")){toggle("bannerBlend","Плавный переход",false);if(editor.draft.document.optInt("bannerBlend")==1)slider("bannerBlendRadius","Длина перехода",2,60,16,"%");}
        help("Баннер и фон настраиваются отдельно.","Баннер оформляет шапку, а фон — страницу под ней. У каждого свои цвета, медиа, прозрачность и размытие.");
    }
    private void surfaceEffect(String prefix,boolean fade){
        String effect=fade?"Fade":"Gradient",feature=fade?"profile.opacity":"profile.gradient";
        header(fade?"Вид затухания":"Вид градиента");
        segments(prefix+effect+"Type",fade?new String[]{"Нет","Линейное","Радиальное"}:new String[]{"Линейный","Радиальный"},0);
        if(fade&&editor.draft.document.optInt(prefix+effect+"Type")==0)return;
        if(!fade){colors(prefix+"Color","Первый цвет");colors(prefix+"Color2","Второй цвет");toggle(prefix+"Use3","Третий цвет",false);if(editor.draft.document.optInt(prefix+"Use3")==1)colors(prefix+"Color3","Цвет третьей точки");}
        DevGramSurfacePad pad=new DevGramSurfacePad(context,host.getCurrentAccount(),editor.uid,prefix,fade,
                ()->DevGramProfileDocument.effective(editor.draft.document,editor.uid),DevGramAppearanceAccess.allowed(host,feature),host.getResourceProvider(),
                (x,y,angle,spread)->{
                    if(!DevGramAppearanceAccess.allowed(host,feature))return;
                    try{editor.draft.document.put(prefix+effect+"CenterX",x).put(prefix+effect+"CenterY",y).put(prefix+effect+"Angle",angle).put(prefix+effect+"Spread",spread);editor.draft.dirty=true;host.devgramRefreshEditor();for(Runnable binding:bindings)binding.run();}catch(Exception ignored){}
                },()->DevGramAppearanceAccess.check(host,feature));
        body.addView(pad,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(210)));bindings.add(()->pad.available(DevGramAppearanceAccess.allowed(host,feature)));
        if(editor.draft.document.optInt(prefix+effect+"Type")== (fade?1:0))slider(prefix+effect+"Angle",fade?"Направление":"Поворот",0,360,0,"°");
        slider(prefix+effect+"Spread",fade?"Размер":"Размах",20,200,100,"%");
        slider(prefix+effect+"CenterX","Центр по горизонтали",0,100,50,"%");slider(prefix+effect+"CenterY","Центр по вертикали",0,100,50,"%");
        help("Удерживайте центр или конец линии и перетаскивайте, чтобы настроить эффект.",fade?"Затухание плавно скрывает часть изображения. Направление определяет сторону исчезновения, размер — ширину перехода, центр — положение видимой области.":"Направление меняет расположение цветов градиента. Размах расширяет или сужает переход. Для радиального градиента изменение центра перемещает цветовое пятно.");
    }
    private void textPalette(){
        toggle("profileTextEnabled","Свой цвет текста",false);
        if(editor.draft.document.optInt("profileTextEnabled")==1){header("Цвет текста");paletteColor(null,"Цвет текста",Theme.key_windowBackgroundWhiteBlackText);help("Общая палитра текста профиля.","Общий цвет применяется к тексту профиля. В палитре ниже можно отдельно изменить подписи, значения, ссылки и кнопки.");}
        int last=-1;for(int i=0;i<DevGramProfilePalette.NAMES.length;i++){int group=DevGramProfilePalette.group(i);if(group!=last){header(DevGramProfilePalette.GROUPS[group]);last=group;}paletteColor(DevGramProfilePalette.NAMES[i],DevGramProfilePalette.LABELS[i],DevGramProfilePalette.KEYS[i]);}
        row("Вернуть цвета темы","",()->new org.telegram.ui.ActionBar.AlertDialog.Builder(context,host.getResourceProvider()).setTitle(t("Вернуть цвета темы")).setMessage(t("Все цвета палитры будут сняты.")).setPositiveButton(t("Вернуть"),(d,w)->{if(DevGramAppearanceAccess.check(host,"profile.palette")){editor.remove("profilePalette");editor.remove("profileTextColor");editor.put("profileTextEnabled",0);rebuild(true);}}).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).show());
    }
    private void paletteColor(String key,String title,int themeKey){
        JSONObject palette=editor.draft.document.optJSONObject("profilePalette");boolean selected=key==null?editor.draft.document.has("profileTextColor"):palette!=null&&palette.has(key);int current=key==null?editor.draft.document.optInt("profileTextColor",-1):selected?palette.optInt(key):Theme.getColor(themeKey);
        row(title,selected?String.format(java.util.Locale.US,"#%08X",current):t("Как в теме"),()->{
            if(!DevGramAppearanceAccess.check(host,"profile.palette"))return;
            DevGramColorPickerView picker=new DevGramColorPickerView(context,host.getResourceProvider(),key!=null);picker.setColor(current);final int[] chosen={current};picker.setListener(value->chosen[0]=value);
            new org.telegram.ui.ActionBar.AlertDialog.Builder(context,host.getResourceProvider()).setTitle(t(title)).setView(picker).setPositiveButton(t("Применить"),(d,w)->{if(!DevGramAppearanceAccess.allowed(host,"profile.palette"))return;if(key==null)editor.put("profileTextColor",chosen[0]);else{JSONObject next=DevGramProfileDocument.copy(editor.draft.document.optJSONObject("profilePalette")==null?new JSONObject():editor.draft.document.optJSONObject("profilePalette"));try{next.put(key,chosen[0]);editor.put("profilePalette",next);}catch(Exception ignored){}}rebuild(true);}).setNeutralButton(t("Как в теме"),(d,w)->{if(!DevGramAppearanceAccess.allowed(host,"profile.palette"))return;if(key==null)editor.remove("profileTextColor");else{JSONObject next=DevGramProfileDocument.copy(editor.draft.document.optJSONObject("profilePalette")==null?new JSONObject():editor.draft.document.optJSONObject("profilePalette"));next.remove(key);editor.put("profilePalette",next);}rebuild(true);}).setNegativeButton(DevGramLocale.getString(R.string.Cancel),null).show();
        });
    }
    private boolean rowHidden(String key){JSONArray hidden=editor.draft.document.optJSONArray("hiddenRows");if(hidden!=null)for(int i=0;i<hidden.length();i++){String item=hidden.optString(i);if(item.equals(key)||item.equals("blocks")&&key.startsWith("block:")||item.equals("integrations")&&key.startsWith("integration:"))return true;}return false;}
    private void rows(){
        header("Комментарии профиля");
        toggle("commentsEnabled","Показывать блок комментариев",false);
        slider("commentsCount","Комментариев в блоке",1,10,3,"");
        help("Посетители смогут обсуждать профиль.","Комментарии появятся после сохранения оформления. Владелец может удалять записи и блокировать комментаторов.");
        row("Открыть обсуждение","",()->DevGramProfileCommentsSheet.show(host,editor.draft.target(host.getCurrentAccount())));

        java.util.ArrayList<String> keys=host.devgramAllRowKeys();keys.add("shared_media");keys.add("balance");
        header("Порядок строк");help("Зажмите строку и перетащите её на профиле.","Долгое нажатие поднимает строку. Перетащите её выше или ниже и отпустите. Скрытые строки можно вернуть переключателями ниже.");
        for(String key:keys){String label=host.devgramRowLabel(key);
            org.telegram.ui.Cells.TextCheckCell toggle=new org.telegram.ui.Cells.TextCheckCell(context,host.getResourceProvider());
            Runnable bind=()->toggle.setTextAndCheck(DevGramAppearanceAccess.label(host,"profile.rows",label),!rowHidden(key),true);bindings.add(bind);bind.run();
            toggle.setOnClickListener(v->{
                if(!DevGramAppearanceAccess.check(host,"profile.rows"))return;
                java.util.LinkedHashSet<String> hidden=new java.util.LinkedHashSet<>();JSONArray old=editor.draft.document.optJSONArray("hiddenRows");if(old!=null)for(int j=0;j<old.length();j++)hidden.add(old.optString(j));
                if(rowHidden(key)){hidden.remove(key);String prefix=key.startsWith("block:")?"block:":key.startsWith("integration:")?"integration:":"";String group=prefix.equals("block:")?"blocks":"integrations";if(!prefix.isEmpty()&&hidden.remove(group))for(String other:keys)if(other.startsWith(prefix)&&!other.equals(key))hidden.add(other);}else hidden.add(key);
                JSONArray next=new JSONArray();for(String item:hidden)next.put(item);editor.put("hiddenRows",next);accessChanged.run();
            });body.addView(toggle);
        }
    }
}
