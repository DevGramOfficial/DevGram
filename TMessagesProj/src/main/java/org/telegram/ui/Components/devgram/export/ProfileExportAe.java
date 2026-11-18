package org.telegram.ui.Components.devgram.export;

import java.util.List;
import java.util.Locale;

public final class ProfileExportAe {
    private ProfileExportAe() {
    }

    public static String script(String str, int i, int i2, int i3, int i4, String str2, List<ProfileExportPlan.Sheet> list) {
        int iMax = Math.max(1, i);
        int iMax2 = Math.max(1, i2);
        float[] fArrRgb = rgb(str2);
        StringBuilder sb = new StringBuilder(2048);
        sb.append("// DevGram: сборка послойного снимка профиля.\n// Положить рядом со слоями и запустить: File > Scripts > Run Script File.\n(function () {\n    var here = File($.fileName).parent;\n    if (!app.project) {\n        app.newProject();\n    }\n    app.beginUndoGroup(\"DevGram\");\n    var comp = app.project.items.addComp(");
        sb.append(text(str));
        sb.append(", ");
        sb.append(iMax);
        sb.append(", ");
        sb.append(iMax2);
        sb.append(", 1, ");
        sb.append(seconds(i4));
        sb.append(", ");
        sb.append(Math.max(1, i3));
        sb.append(");\n    comp.bgColor = [");
        sb.append(number(fArrRgb[0]));
        sb.append(", ");
        sb.append(number(fArrRgb[1]));
        sb.append(", ");
        sb.append(number(fArrRgb[2]));
        sb.append("];\n    var shelf = app.project.items.addFolder(");
        sb.append(text(str));
        sb.append(");\n    function bring(file) {\n        var source = new File(here.fsName + \"/\" + file);\n        if (!source.exists) {\n            return null;\n        }\n        var item = app.project.importFile(new ImportOptions(source));\n        item.parentFolder = shelf;\n        return item;\n    }\n    function fill(file, name, x, y, w, h) {\n        var item = bring(file);\n        if (!item) {\n            return;\n        }\n        var layer = comp.layers.add(item);\n        layer.name = name;\n        var k = Math.max(w / item.width, h / item.height) * 100;\n        layer.property(\"Scale\").setValue([k, k]);\n        layer.property(\"Position\").setValue([x + w / 2, y + h / 2]);\n    }\n    function put(file, name, x, y, w, h) {\n        var item = bring(file);\n        if (!item) {\n            return;\n        }\n        var layer = comp.layers.add(item);\n        layer.name = name;\n        layer.property(\"Position\").setValue([x + w / 2, y + h / 2]);\n    }\n");
        int size = list == null ? 0 : list.size();
        for (int i5 = 0; i5 < size; i5++) {
            ProfileExportPlan.Sheet sheet = list.get(i5);
            boolean z = sheet.video.length() > 0;
            sb.append(z ? "    fill(" : "    put(");
            sb.append(text(z ? sheet.video : sheet.name + ".png"));
            sb.append(", ");
            sb.append(text(sheet.name));
            sb.append(", ");
            sb.append(sheet.x);
            sb.append(", ");
            sb.append(sheet.y);
            sb.append(", ");
            sb.append(sheet.width);
            sb.append(", ");
            sb.append(sheet.height);
            sb.append(");\n");
        }
        sb.append("    comp.openInViewer();\n    app.endUndoGroup();\n})();\n");
        return sb.toString();
    }

    private static int parseColor(String text){try{return (int)Long.parseLong(text.replace("#",""),16);}catch(Exception ignored){return 0xff000000;}}
    private static String seconds(int i) {
        return number(Math.max(1, i) / 1000.0f);
    }

    static float[] rgb(String str) {
        int iColor = parseColor(str);
        return new float[]{((iColor >> 16) & 255) / 255.0f, ((iColor >> 8) & 255) / 255.0f, (iColor & 255) / 255.0f};
    }

    private static String number(float f) {
        return String.format(Locale.US, "%.4f", Float.valueOf(f));
    }

    static String text(String str) {
        if (str == null) {
            str = "";
        }
        StringBuilder sb = new StringBuilder(str.length() + 8);
        sb.append('\"');
        for (int i = 0; i < str.length(); i++) {
            char cCharAt = str.charAt(i);
            if (cCharAt == '\"' || cCharAt == '\\') {
                sb.append('\\');
                sb.append(cCharAt);
            } else if (cCharAt == '\n' || cCharAt == '\r') {
                sb.append(' ');
            } else {
                sb.append(cCharAt);
            }
        }
        sb.append('\"');
        return sb.toString();
    }
}
