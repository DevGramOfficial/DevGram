package org.telegram.ui.Components.devgram.frame;

import org.json.JSONObject;

/** Canvas palette roles from the supplied frame studio; independent of profile colors. */
public final class FrameNodeSkin {
    public static final int ACCENT = -13397780;
    public static final int MODE_CLIENT = 0;
    public static final int MODE_CUSTOM = 3;
    public static final int MODE_DARK = 1;
    public static final int MODE_LIGHT = 2;
    public static final int MODE_SERVER = 4;
    public static final int ROLES = 25;
    public final int back;
    public final int bar;
    public final int barFill;
    public final int card;
    public final boolean dark;
    public final int dead;
    public final int dim;
    public final int faint;
    public final int grid;
    public final int line;
    public final int note;
    public final int noteOff;
    public final int pad;
    public final int padCross;
    public final int padEdge;
    public final int padEdgeOff;
    public final int padOff;
    public final int press;
    public final int pressSoft;
    private final int[] roles;
    public final int row;
    public final int shadow;
    public final int shadowOff;
    public final int skin;
    public final int text;
    public final int title;
    public final int titleOff;
    public static final String[] KEYS = {"back", "grid", "card", "dead", "shadow", "shadowOff", "title", "titleOff", "note", "noteOff", "row", "line", "text", "dim", "faint", "bar", "barFill", "press", "pressSoft", "pad", "padOff", "padEdge", "padEdgeOff", "padCross", "skin"};
    public static final String[] NAMES = {Loc.t("Подложка", "Background"), Loc.t("Сетка", "Grid"), Loc.t("Карточка", "Card"), Loc.t("Полоса снятого узла", "Off node stripe"), Loc.t("Тень под узлом", "Shadow under node"), Loc.t("Тень под снятым", "Shadow under off node"), Loc.t("Название узла", "Node title"), Loc.t("Название снятого", "Off node title"), Loc.t("Подпись под названием", "Subtitle under title"), Loc.t("Подпись снятого", "Off node subtitle"), Loc.t("Строка", "Row"), Loc.t("Черта между строками", "Line between rows"), Loc.t("Значение", "Value"), Loc.t("Значение приглушённое", "Dimmed value"), Loc.t("Значение тихое", "Faint value"), Loc.t("Дорожка значения", "Value track"), Loc.t("Заполнение", "Fill"), Loc.t("Подпись на заполнении", "Label on fill"), Loc.t("Значение на заполнении", "Value on fill"), Loc.t("Поле управления", "Control field"), Loc.t("Поле у снятого", "Off control field"), Loc.t("Обвод управления", "Control outline"), Loc.t("Обвод у снятого", "Off control outline"), Loc.t("Перекрестье", "Crosshair"), Loc.t("Манекен", "Mannequin")};
    public static final FrameNodeSkin DARK = new FrameNodeSkin(new int[]{-15395302, 587202559, -14473940, -11447983, -14211289, -14606047, -1, -8880760, -7105645, -10525586, -15132391, -13882324, -2630686, -8880760, -10525586, -9671572, -2500135, -16777216, -15132391, -13684424, -14342356, -11907239, -13354945, -12960186, -10787725});
    public static final FrameNodeSkin LIGHT = new FrameNodeSkin(new int[]{-920586, 570425344, -1, -3617582, -4012082, -2433566, -15395044, -6643544, -10722708, -5722699, -1183757, -2301979, -14210511, -7959403, -5722699, -2893859, -9538433, -1, -920586, -1644051, -1052172, -4801339, -2893859, -3683117, -4801081});

    private FrameNodeSkin(int[] iArr) {
        this.roles = iArr;
        int i = iArr[0];
        this.back = i;
        this.grid = iArr[1];
        this.card = iArr[2];
        this.dead = iArr[3];
        this.shadow = iArr[4];
        this.shadowOff = iArr[5];
        this.title = iArr[6];
        this.titleOff = iArr[7];
        this.note = iArr[8];
        this.noteOff = iArr[9];
        this.row = iArr[10];
        this.line = iArr[11];
        this.text = iArr[12];
        this.dim = iArr[13];
        this.faint = iArr[14];
        this.bar = iArr[15];
        this.barFill = iArr[16];
        this.press = iArr[17];
        this.pressSoft = iArr[18];
        this.pad = iArr[19];
        this.padOff = iArr[20];
        this.padEdge = iArr[21];
        this.padEdgeOff = iArr[22];
        this.padCross = iArr[23];
        this.skin = iArr[24];
        this.dark = darkColor(i);
    }

    public int role(int i) {
        if (i < 0 || i >= 25) {
            return 0;
        }
        return this.roles[i];
    }

    public FrameNodeSkin with(int i, int i2) {
        if (i < 0 || i >= 25) {
            return this;
        }
        int[] iArr = new int[25];
        System.arraycopy(this.roles, 0, iArr, 0, 25);
        iArr[i] = i2;
        return new FrameNodeSkin(iArr);
    }

    public static FrameNodeSkin of(int i, FrameNodeSkin frameNodeSkin, boolean z) {
        if (i == 1) {
            return DARK;
        }
        if (i == 2) {
            return LIGHT;
        }
        if (i == 3) {
            return frameNodeSkin == null ? DARK : frameNodeSkin;
        }
        return z ? DARK : LIGHT;
    }

    public String encode() {
        JSONObject jSONObject = new JSONObject();
        for (int i = 0; i < 25; i++) {
            try {
                jSONObject.put(KEYS[i], Style.hex(this.roles[i]));
            } catch (Throwable th) {
                
            }
        }
        return jSONObject.toString();
    }

    public static FrameNodeSkin parse(String str) {
        if (str != null && str.length() != 0) {
            try {
                JSONObject jSONObject = new JSONObject(str);
                int[] iArr = new int[25];
                boolean z = false;
                for (int i = 0; i < 25; i++) {
                    String strOptString = jSONObject.optString(KEYS[i], "");
                    iArr[i] = Style.color(strOptString, DARK.roles[i]);
                    z |= strOptString.length() > 0;
                }
                if (z) {
                    return new FrameNodeSkin(iArr);
                }
                return null;
            } catch (Throwable th) {
                
            }
        }
        return null;
    }

    public static boolean darkColor(int i) {
        return (((((i >>> 16) & 255) * 299) + (((i >>> 8) & 255) * 587)) + ((i & 255) * 114)) / 1000 < 128;
    }

    public static String name(int i) {
        if (i == 1) {
            return Loc.t("тёмная", "dark");
        }
        if (i == 2) {
            return Loc.t("светлая", "light");
        }
        return i == 3 ? Loc.t("своя", "custom") : Loc.t("как в клиенте", "as in client");
    }
}
