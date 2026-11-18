package org.telegram.ui.Components.devgram.frame;

import java.util.ArrayList;
import java.util.List;

/** Native port of the user-supplied Custom Profile frame core; bounded data only. */
public final class FrameNodeType {
    public static final int AVATAR = 2;
    public static final int BAKE = 72;
    public static final String[] CATEGORIES = {Loc.t("Источники", "Sources"), Loc.t("Украшения", "Decorations"), Loc.t("Частицы", "Particles"), Loc.t("Цвет", "Color"), Loc.t("Расположение", "Placement"), Loc.t("Сборка", "Assembly"), Loc.t("Вывод", "Output"), Loc.t("Эффекты", "Effects")};
    public static final int CAT_COLOR = 3;
    public static final int CAT_EFFECT = 7;
    public static final int CAT_INPUT = 0;
    public static final int CAT_LAYOUT = 5;
    public static final int CAT_OUTPUT = 6;
    public static final int CAT_PARTICLE = 2;
    public static final int CAT_SHAPE = 1;
    public static final int CAT_TRANSFORM = 4;
    public static final int COLOR = 3;
    public static final int EVENT = 55;
    public static final int EVENT_BLOCK = 0;
    public static final int EVENT_MODE = 1;
    public static final String[] EVENT_MODES;
    public static final int EVENT_QUEUE = 2;
    public static final String[] EVENT_QUEUES;
    public static final int EVENT_SIGNAL = 1;
    public static final int EVENT_SIGNAL_KNOB = 3;
    public static final int EVENT_TOGGLE = 0;
    public static final int FADE = 31;
    public static final int FLOW = 26;
    public static final int FRAME = 60;
    public static final int GLOW = 70;
    public static final int GRAVITY = 22;
    public static final int IMAGE = 1;
    public static final int JITTER = 25;
    private static final List<Kind> KINDS;
    public static final int KNOB_BLOCK = 5;
    public static final int KNOB_CHOICE = 1;
    public static final int KNOB_COLOR = 2;
    public static final int KNOB_IMAGE = 3;
    public static final int KNOB_NUMBER = 4;
    public static final int KNOB_PIN_BASE = 100;
    public static final int KNOB_RANGE = 0;
    public static final int KNOB_SIGNAL = 6;
    public static final int LIVE = 56;
    public static final String[] LIVE_WAVES;
    public static final int MARK = 12;
    public static final int PANEL = 6;
    public static final int PARTICLES = 14;
    public static final int PATTERN = 11;
    public static final int PIN = 51;
    public static final int PIN_COLOR = 3;
    public static final int PIN_EITHER = 7;
    public static final int PIN_FRAME = 5;
    public static final int PIN_IMAGE = 1;
    public static final int PIN_LAYER = 4;
    public static final int PIN_NUMBER = 6;
    public static final int PIN_SIGNAL = 8;
    public static final String[] PIN_SPOTS;
    public static final int PLACE = 40;
    public static final int RIM = 10;
    public static final int ROLE_ACROSS = 1;
    public static final int ROLE_DOWN = 2;
    public static final int ROLE_PLAIN = 0;
    public static final int ROLE_TURN = 3;
    public static final int SCATTER = 20;
    public static final int SIZE = 42;
    public static final int SPIN = 23;
    public static final int STACK = 50;
    public static final int STICKER = 13;
    public static final int TINT = 30;
    public static final int TRANSFORM = 43;
    public static final int TURBULENCE = 21;
    public static final int TURN = 41;
    public static final int TWINKLE = 24;
    public static final int VALUE = 5;
    public static final int VIEW = 61;

    private static boolean either(int i) {
        return i == 4 || i == 5 || i == 6 || i == 1 || i == 3 || i == 7;
    }

    public static boolean fillsCard(int i) {
        return i == 61 || i == 6 || i == 60;
    }

    public static boolean isKnobPin(int i) {
        return i >= 100;
    }

    public static int knobOfPin(int i) {
        return i - 100;
    }

    public static int knobPin(int i) {
        return i + 100;
    }

    private static Knob[] knobs(Knob... knobArr) {
        return knobArr;
    }

    private static Pin[] pins(Pin... pinArr) {
        return pinArr;
    }

    public static boolean showsBranch(int i) {
        return i == 61 || i == 60;
    }

    static {
        String[] strArr = {Loc.t("Переключатель", "Toggle"), Loc.t("Сигнал", "Signal")};
        EVENT_MODES = strArr;
        String[] strArr2 = {Loc.t("По порядку", "In order"), Loc.t("Случайно", "At random")};
        EVENT_QUEUES = strArr2;
        String[] strArr3 = {Loc.t("Туда-сюда", "Swing"), Loc.t("По кругу", "Round"), Loc.t("Скачком", "Step"), Loc.t("Толчком", "Push")};
        LIVE_WAVES = strArr3;
        String[] strArr4 = {Loc.t("середина", "center"), Loc.t("сверху", "top"), Loc.t("снизу", "bottom"), Loc.t("слева", "left"), Loc.t("справа", "right"), Loc.t("слева сверху", "top left"), Loc.t("справа сверху", "top right"), Loc.t("слева снизу", "bottom left"), Loc.t("справа снизу", "bottom right")};
        PIN_SPOTS = strArr4;
        KINDS = new ArrayList();
        add(1, 0, Loc.t("Картинка", "Image"), Loc.t("лента, спрайт или своя картинка", "ribbon, sprite or your own image"), pins(new Pin[0]), 1, knobs(knob(Loc.t("Файл", "File"), 3, 0, 0, 0)));
        add(2, 0, Loc.t("Аватарка", "Avatar"), Loc.t("копия аватара как слой рамки", "a copy of the avatar as a frame layer"), pins(new Pin[0]), 4, knobs(knob(Loc.t("Размер", "Size"), 0, 10, 400, 100), across(Loc.t("Слева направо", "Left to right"), 0), down(Loc.t("Сверху вниз", "Top to bottom"), 0), turning(Loc.t("Поворот", "Rotation"), 0)));
        add(5, 0, Loc.t("Значение", "Value"), Loc.t("число для ручки любого узла", "number for any node's knob"), pins(new Pin[0]), 6, knobs(knob(Loc.t("Число", "Number"), 4, 0, 0, 0)));
        add(56, 0, Loc.t("Живое число", "Live number"), Loc.t("отпускает число ходить: размах, скорость, ход", "sets a number in motion: swing, speed, shape"), pins(pin(Loc.t("Число", "Number"), 6)), 6, true, knobs(knob(Loc.t("Размах", "Swing"), 4, 0, 0, 0), knob(Loc.t("Скорость", "Speed"), 0, 1, FrameSpec.LIVE_SPEED_MAX, 60), knob(Loc.t("Ход", "Shape"), 1, 0, strArr3.length - 1, 0, strArr3), knob(Loc.t("Сдвиг", "Offset"), 0, 0, 100, 0)));
        add(6, 0, Loc.t("Интерфейс", "Interface"), Loc.t("джойстик вместо ползунка", "joystick instead of a slider"), pins(new Pin[0]), 6, knobs(knob(Loc.t("Вбок", "Sideways"), 4, 0, 0, 0), knob(Loc.t("Вниз", "Down"), 4, 0, 0, 0)));
        add(3, 0, Loc.t("Цвет", "Color"), Loc.t("оттенок для украшения", "tint for a decoration"), pins(new Pin[0]), 3, knobs(knob(Loc.t("Цвет", "Color"), 2, 0, 0, -1)));
        add(10, 1, Loc.t("Обод", "Rim"), Loc.t("лента по краю аватара", "ribbon along the avatar's edge"), pins(pin(Loc.t("Картинка", "Image"), 1)), 4, knobs(knob(Loc.t("Толщина", "Thickness"), 0, 1, 80, 10), knob(Loc.t("Отступ", "Offset"), 0, -20, 60, 0), turning(Loc.t("Сдвиг", "Shift"), 0), turning(Loc.t("Вращение", "Spin"), 0), knob(Loc.t("Без стыка", "No seam"), 1, 0, 1, 0, new String[]{Loc.t("нет", "no"), Loc.t("да", "yes")})));
        add(11, 1, Loc.t("Узор", "Pattern"), Loc.t("спрайт повторяется по краю", "sprite repeats along the edge"), pins(pin(Loc.t("Картинка", "Image"), 1)), 4, knobs(knob(Loc.t("Размер", "Size"), 0, 10, 400, 90), knob(Loc.t("Сколько", "Count"), 0, 1, 64, 24), knob(Loc.t("Отступ", "Offset"), 0, -20, 60, 3), turning(Loc.t("Сдвиг", "Shift"), 0), turning(Loc.t("Вращение", "Spin"), 0)));
        add(12, 1, Loc.t("Метка", "Mark"), Loc.t("одно украшение на краю", "one decoration on the edge"), pins(pin(Loc.t("Картинка", "Image"), 1)), 4, knobs(knob(Loc.t("Размер", "Size"), 0, 10, 400, 100), knob(Loc.t("Отступ", "Offset"), 0, -20, 60, 0), turning(Loc.t("Место", "Place"), 0), turning(Loc.t("Вращение", "Spin"), 0)));
        add(13, 1, Loc.t("Наклейка", "Sticker"), Loc.t("стоит там, где повесили", "stays where you put it"), pins(pin(Loc.t("Картинка", "Image"), 1)), 4, knobs(knob(Loc.t("Размер", "Size"), 0, 10, 400, 100), across(Loc.t("Слева направо", "Left to right"), 0), down(Loc.t("Сверху вниз", "Top to bottom"), 0), turning(Loc.t("Поворот", "Rotation"), 0)));
        add(14, 1, Loc.t("Частицы", "Particles"), Loc.t("облако вокруг аватара", "cloud around the avatar"), pins(pin(Loc.t("Картинка", "Image"), 1)), 4, knobs(knob(Loc.t("Сколько", "Count"), 0, 1, 64, 14), knob(Loc.t("Размер", "Size"), 0, 10, 400, 80), knob(Loc.t("Ширина облака", "Cloud width"), 0, 0, 200, 40), knob(Loc.t("Длина пути", "Path length"), 0, 2, 400, 90), knob(Loc.t("Скорость", "Speed"), 0, 10, 400, 100)));
        add(26, 2, Loc.t("Направление", "Direction"), Loc.t("куда летят частицы", "where the particles fly"), pins(pin(Loc.t("Слой", "Layer"), 4)), 4, knobs(knob(Loc.t("Куда", "Where"), 1, 0, 2, 0, new String[]{Loc.t("в одну сторону", "one way"), Loc.t("от центра", "from center"), Loc.t("к центру", "to center")}), turning(Loc.t("Угол", "Angle"), 0)));
        add(20, 2, Loc.t("Разлёт", "Scatter"), Loc.t("веер от общего направления", "a fan from the shared direction"), pins(pin(Loc.t("Слой", "Layer"), 4)), 4, knobs(knob(Loc.t("Разлёт", "Scatter"), 0, 0, 180, 30)));
        add(21, 2, Loc.t("Турбулентность", "Turbulence"), Loc.t("частицы вьются на лету", "particles swirl as they fly"), pins(pin(Loc.t("Слой", "Layer"), 4)), 4, knobs(knob(Loc.t("Сила", "Strength"), 0, 0, 100, 35)));
        add(22, 2, Loc.t("Тяжесть", "Gravity"), Loc.t("тянет вниз с ускорением", "pulls down with acceleration"), pins(pin(Loc.t("Слой", "Layer"), 4)), 4, knobs(knob(Loc.t("Тяжесть", "Gravity"), 0, -100, 100, 40)));
        add(23, 2, Loc.t("Вращение", "Spin"), Loc.t("крутит саму частицу", "spins the particle itself"), pins(pin(Loc.t("Слой", "Layer"), 4)), 4, knobs(turning(Loc.t("Поворот", "Rotation"), 0), turning(Loc.t("За жизнь", "Over lifetime"), 180)));
        add(24, 2, Loc.t("Мерцание", "Twinkle"), Loc.t("частица перемигивает", "the particle blinks"), pins(pin(Loc.t("Слой", "Layer"), 4)), 4, knobs(knob(Loc.t("Глубина", "Depth"), 0, 0, 100, 40)));
        add(25, 2, Loc.t("Разнобой", "Jitter"), Loc.t("у каждой частицы свой путь и размер", "each particle has its own path and size"), pins(pin(Loc.t("Слой", "Layer"), 4)), 4, knobs(knob(Loc.t("Разнобой", "Jitter"), 0, 0, 100, 45)));
        add(30, 3, Loc.t("Оттенок", "Tint"), Loc.t("красит украшение", "colors the decoration"), pins(pin(Loc.t("Слой", "Layer"), 4), pin(Loc.t("Цвет", "Color"), 3)), 4, knobs(knob(Loc.t("Цвет", "Color"), 2, 0, 0, -1)));
        add(31, 3, Loc.t("Прозрачность", "Transparency"), Loc.t("просвечивает украшение насквозь", "makes the decoration see-through"), pins(pin(Loc.t("Слой", "Layer"), 4)), 4, knobs(knob(Loc.t("Видно", "Visible"), 0, 0, 100, 100)));
        add(40, 4, Loc.t("Сдвиг", "Shift"), Loc.t("двигает по краю и от края", "moves along and away from the edge"), pins(pin(Loc.t("Слой", "Layer"), 4)), 4, knobs(turning(Loc.t("Сдвиг", "Shift"), 0), knob(Loc.t("Отступ", "Offset"), 0, -20, 60, 0)));
        add(41, 4, Loc.t("Поворот", "Rotation"), Loc.t("доворачивает украшение", "turns the decoration further"), pins(pin(Loc.t("Слой", "Layer"), 4)), 4, knobs(turning(Loc.t("Поворот", "Rotation"), 0)));
        add(42, 4, Loc.t("Размер", "Size"), Loc.t("умножает размер: 100 — как есть", "multiplies size: 100 — as is"), pins(pin(Loc.t("Слой", "Layer"), 4)), 4, knobs(knob(Loc.t("Размер", "Size"), 0, 10, 400, 100)));
        add(43, 4, Loc.t("Трансформация", "Transform"), Loc.t("место, поворот и размер разом", "place, rotation and size at once"), pins(pin(Loc.t("Слой", "Layer"), 4)), 4, knobs(turning(Loc.t("По краю", "Along edge"), 0), knob(Loc.t("От края", "From edge"), 0, -20, 60, 0), across(Loc.t("Слева направо", "Left to right"), 0), down(Loc.t("Сверху вниз", "Top to bottom"), 0), turning(Loc.t("Поворот", "Rotation"), 0), knob(Loc.t("Размер", "Size"), 0, 10, 400, 100)));
        add(50, 5, Loc.t("Слои", "Layers"), Loc.t("стопка украшений; после неё правят всё сразу", "a stack of decorations; after it you edit everything at once"), pins(pin(Loc.t("Слой", "Layer"), 4, true)), 5, knobs(new Knob[0]));
        add(55, 5, Loc.t("Событие", "Event"), Loc.t("выбирает одно из нескольких: по тумблеру или по сигналу", "picks one of several: by a toggle or by a signal"), pins(pin(Loc.t("Выключено", "Off"), 7), pin(Loc.t("Включено", "On"), 7, true)), 7, knobs(knob(Loc.t("Переключатель", "Toggle"), 5, 0, 0, 0), knob(Loc.t("Лад", "Mode"), 1, 0, strArr.length - 1, 0, strArr), knob(Loc.t("Очередь", "Queue"), 1, 0, strArr2.length - 1, 0, strArr2), knob(Loc.t("Сигнал", "Signal"), 6, 0, 0, 0)));
        add(51, 5, Loc.t("Закрепить", "Pin"), Loc.t("объекты держатся точки главного", "objects hold on to a spot of the main one"), pins(pin(Loc.t("Главный", "Main"), 4), pin(Loc.t("Объект", "Object"), 4, true)), 4, knobs(knob(Loc.t("Точка", "Spot"), 1, 0, strArr4.length - 1, 0, strArr4)));
        add(72, 5, Loc.t("Запечка", "Bake"), Loc.t("запекает эффекты в одну текстуру — без лагов", "bakes effects into one texture — no lag"), pins(pin(Loc.t("Слой", "Layer"), 4)), 4, knobs(knob(Loc.t("Старые версии", "Old versions"), 1, 0, 1, 0, new String[]{Loc.t("нет", "no"), Loc.t("да", "yes")})));
        add(70, 7, Loc.t("Свечение", "Glow"), Loc.t("ореол света из цветов самой картинки", "a halo of light from the image's own colors"), pins(pin(Loc.t("Слой", "Layer"), 4)), 4, knobs(knob(Loc.t("Сила", "Strength"), 0, 0, 100, 50), knob(Loc.t("Радиус", "Radius"), 0, 0, 100, 50)));
        add(60, 6, Loc.t("Рамка", "Frame"), Loc.t("то, что видно в профиле", "what shows in the profile"), pins(pin(Loc.t("Вход", "Input"), 5)), 0, knobs(new Knob[0]));
        add(61, 6, Loc.t("Просмотр", "Preview"), Loc.t("показывает свою ветку", "shows its own branch"), pins(pin(Loc.t("Вход", "Input"), 5)), 0, knobs(new Knob[0]));
    }

    public static final class Knob {
        public final int kind;
        public final int max;
        public final int min;
        public final String name;
        public final String[] options;
        public final int role;
        public final int start;

        Knob(String str, int i, int i2, int i3, int i4, String[] strArr, int i5) {
            this.name = str;
            this.kind = i;
            this.min = i2;
            this.max = i3;
            this.start = i4;
            this.options = strArr;
            this.role = i5;
        }

        public int clamp(int i) {
            int i2 = this.kind;
            return (i2 == 2 || i2 == 3 || i2 == 4 || i2 == 5) ? i : Style.clamp(i, this.min, this.max);
        }
    }

    public static final class Pin {
        public final int kind;
        public final boolean many;
        public final String name;

        Pin(String str, int i, boolean z) {
            this.name = str;
            this.kind = i;
            this.many = z;
        }
    }

    public static final class Kind {
        public final int category;
        public final int gives;
        public final String hint;
        public final Knob[] knobs;
        public final String name;
        public final Pin[] pins;
        public final boolean signal;
        public final int type;

        Kind(int i, int i2, String str, String str2, Pin[] pinArr, int i3, boolean z, Knob[] knobArr) {
            this.type = i;
            this.category = i2;
            this.name = str;
            this.hint = str2;
            this.pins = pinArr;
            this.gives = i3;
            this.signal = z;
            this.knobs = knobArr;
        }

        public int knobs() {
            return this.knobs.length;
        }

        public int pins() {
            return this.pins.length;
        }

        public boolean many() {
            Pin[] pinArr = this.pins;
            return pinArr.length > 0 && pinArr[pinArr.length - 1].many;
        }
    }

    private FrameNodeType() {
    }

    public static Kind of(int i) {
        int i2 = 0;
        while (true) {
            List<Kind> list = KINDS;
            if (i2 >= list.size()) {
                return null;
            }
            if (list.get(i2).type == i) {
                return list.get(i2);
            }
            i2++;
        }
    }

    public static List<Kind> all() {
        return KINDS;
    }

    public static List<Kind> of(int i, List<Kind> list) {
        list.clear();
        int i2 = 0;
        while (true) {
            List<Kind> list2 = KINDS;
            if (i2 >= list2.size()) {
                return list;
            }
            if (list2.get(i2).category == i) {
                list.add(list2.get(i2));
            }
            i2++;
        }
    }

    public static String name(int i) {
        Kind kindOf = of(i);
        return kindOf == null ? "" : kindOf.name;
    }

    public static boolean givesLayer(int i) {
        Kind kindOf = of(i);
        return kindOf != null && kindOf.gives == 4;
    }

    public static boolean editsLayer(int i) {
        Kind kindOf = of(i);
        return kindOf != null && kindOf.gives == 4 && kindOf.pins.length > 0 && kindOf.pins[0].kind == 4;
    }

    public static boolean makesLayer(int i) {
        Kind kindOf = of(i);
        if (kindOf == null || kindOf.gives != 4) {
            return false;
        }
        return kindOf.pins.length == 0 || kindOf.pins[0].kind == 1;
    }

    public static int roleOf(Kind kind, int i) {
        if (kind == null || i < 0 || i >= kind.knobs()) {
            return 0;
        }
        return kind.knobs[i].role;
    }

    public static boolean numericKnob(Kind kind, int i) {
        if (kind == null || i < 0 || i >= kind.knobs() || (kind.gives == 6 && kind.pins.length == 0)) {
            return false;
        }
        int i2 = kind.knobs[i].kind;
        return i2 == 0 || i2 == 1 || i2 == 4;
    }

    public static int outKind(Kind kind, int i) {
        if (kind == null) {
            return 0;
        }
        if (i == 1 && kind.signal) {
            return 8;
        }
        return kind.gives;
    }

    public static int outCount(Kind kind) {
        if (kind == null || kind.gives == 0) {
            return 0;
        }
        return kind.signal ? 2 : 1;
    }

    public static boolean fits(int i, int i2, int i3) {
        return fits(i, 0, i2, i3);
    }

    public static boolean fits(int i, int i2, int i3, int i4) {
        Kind kindOf = of(i);
        if (kindOf == null) {
            return false;
        }
        if (outKind(kindOf, i2) == 8) {
            Kind kindOf2 = of(i3);
            return kindOf2 != null && pinKind(kindOf2, i4) == 8;
        }
        return fitsKind(i, i3, i4);
    }

    private static boolean fitsKind(int i, int i2, int i3) {
        int iPinKind;
        Kind kindOf = of(i);
        Kind kindOf2 = of(i2);
        if (kindOf == null || kindOf2 == null || kindOf.gives == 0 || (iPinKind = pinKind(kindOf2, i3)) == 0) {
            return false;
        }
        if (iPinKind == kindOf.gives) {
            return true;
        }
        if (iPinKind == 7 || kindOf.gives == 7) {
            return either(iPinKind) && either(kindOf.gives);
        }
        if ((iPinKind == 5 || iPinKind == 4) && kindOf.gives == 1) {
            return true;
        }
        return (iPinKind == 5 || iPinKind == 4) && (kindOf.gives == 5 || kindOf.gives == 4);
    }

    public static int pinKind(Kind kind, int i) {
        if (kind != null && i >= 0) {
            if (isKnobPin(i)) {
                int iKnobOfPin = knobOfPin(i);
                if (iKnobOfPin < 0 || iKnobOfPin >= kind.knobs() || kind.knobs[iKnobOfPin].kind != 6) {
                    return numericKnob(kind, iKnobOfPin) ? 6 : 0;
                }
                return 8;
            }
            if (kind.pins.length == 0) {
                return 0;
            }
            if (i < kind.pins.length) {
                return kind.pins[i].kind;
            }
            Pin pin = kind.pins[kind.pins.length - 1];
            if (pin.many) {
                return pin.kind;
            }
        }
        return 0;
    }

    public static String pinName(Kind kind, int i) {
        if (kind != null && i >= 0 && kind.pins.length != 0) {
            if (i < kind.pins.length) {
                return kind.pins[i].name;
            }
            Pin pin = kind.pins[kind.pins.length - 1];
            if (pin.many) {
                return pin.name;
            }
        }
        return "";
    }

    private static void add(int i, int i2, String str, String str2, Pin[] pinArr, int i3, Knob[] knobArr) {
        add(i, i2, str, str2, pinArr, i3, false, knobArr);
    }

    private static void add(int i, int i2, String str, String str2, Pin[] pinArr, int i3, boolean z, Knob[] knobArr) {
        KINDS.add(new Kind(i, i2, str, str2, pinArr, i3, z, knobArr));
    }

    static void registerExternal(Kind kind) {
        if (kind == null) {
            return;
        }
        int i = 0;
        while (true) {
            List<Kind> list = KINDS;
            if (i < list.size()) {
                if (list.get(i).type == kind.type) {
                    list.set(i, kind);
                    return;
                }
                i++;
            } else {
                list.add(kind);
                return;
            }
        }
    }

    private static Pin pin(String str, int i) {
        return new Pin(str, i, false);
    }

    private static Pin pin(String str, int i, boolean z) {
        return new Pin(str, i, z);
    }

    private static Knob knob(String str, int i, int i2, int i3, int i4) {
        return new Knob(str, i, i2, i3, i4, null, 0);
    }

    private static Knob knob(String str, int i, int i2, int i3, int i4, String[] strArr) {
        return new Knob(str, i, i2, i3, i4, strArr, 0);
    }

    private static Knob steered(String str, int i, int i2) {
        return new Knob(str, 4, 0, 0, i, null, i2);
    }

    private static Knob turning(String str, int i) {
        return steered(str, i, 3);
    }

    private static Knob across(String str, int i) {
        return steered(str, i, 1);
    }

    private static Knob down(String str, int i) {
        return steered(str, i, 2);
    }
}
