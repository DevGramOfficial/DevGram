package org.telegram.ui.Components.devgram.frame;

import org.telegram.ui.Components.devgram.frame.FrameNodeDoc;
import org.telegram.ui.Components.devgram.frame.FrameNodeType;
import org.telegram.ui.Components.devgram.frame.FrameSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native port of the user-supplied Custom Profile frame core; bounded data only. */
public final class FrameNodeBuild {
    private static final int EVENT_CHAIN = 8;
    private static final Map<Integer, int[]> FIELDS;
    private static int assumedBranch;
    private static int assumedEvent;
    private static final List<FrameSpec.Alive> pending;
    private static final Map<String, Boolean> assumed = new HashMap();
    private static final Map<Integer, String> SAID = new HashMap();

    private FrameNodeBuild() {
    }

    public static synchronized FrameSpec spec(FrameNodeDoc frameNodeDoc) {
        if (frameNodeDoc == null) {
            return FrameSpec.EMPTY;
        }
        int iFirst = frameNodeDoc.first(60);
        return iFirst <= 0 ? FrameSpec.EMPTY : branch(frameNodeDoc, iFirst);
    }

    public static synchronized FrameSpec branch(FrameNodeDoc frameNodeDoc, int i) {
        if (frameNodeDoc == null || i <= 0) {
            return FrameSpec.EMPTY;
        }
        ArrayList arrayList = new ArrayList();
        collect(frameNodeDoc, frameNodeDoc.input(i, 0), frameNodeDoc.inputOut(i, 0), arrayList, 0);
        if (arrayList.isEmpty()) {
            return FrameSpec.EMPTY;
        }
        return FrameSpec.of(bake(frameNodeDoc, frameNodeDoc.node(i), arrayList));
    }

    private static List<FrameSpec.Layer> bake(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node, List<FrameSpec.Layer> list) {
        if (node == null) {
            return list;
        }
        ArrayList arrayList = new ArrayList(list.size());
        for (int i = 0; i < list.size(); i++) {
            FrameSpec.Layer layerBuild = list.get(i);
            if (!layerBuild.bake) {
                layerBuild = new FrameSpec.Builder(layerBuild).bake(true).build();
            }
            arrayList.add(layerBuild);
        }
        return arrayList;
    }

    private static void collect(FrameNodeDoc frameNodeDoc, int i, int i2, List<FrameSpec.Layer> list, int i3) {
        FrameNodeDoc.Node node = frameNodeDoc.node(i);
        FrameNodeDoc.Node nodeEventOf = node == null ? null : eventOf(frameNodeDoc, node);
        if (nodeEventOf == null) {
            one(frameNodeDoc, i, i2, list, i3);
            return;
        }
        if (nodeEventOf.value(1) == 1) {
            if (assumedEvent > 0) {
                one(frameNodeDoc, i, i2, list, i3);
                return;
            } else {
                queuedValue(frameNodeDoc, node, nodeEventOf, i, i2, list, i3);
                return;
            }
        }
        String strText = nodeEventOf.text(0);
        Map<String, Boolean> map = assumed;
        if (map.size() >= 3) {
            say(nodeEventOf.id, "условий у слоя уже " + map.size() + ": это событие взято по нынешнему положению");
            one(frameNodeDoc, i, i2, list, i3);
            return;
        }
        int i4 = 0;
        while (i4 < 2) {
            int size = list.size();
            Map<String, Boolean> map2 = assumed;
            map2.put(strText, Boolean.valueOf(i4 == 1));
            try {
                collect(frameNodeDoc, i, i2, list, i3);
                map2.remove(strText);
                stamp(list, size, strText, i4 == 1);
                i4++;
            } catch (Throwable th) {
                assumed.remove(strText);
                throw th;
            }
        }
    }

    private static void queuedValue(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node, FrameNodeDoc.Node node2, int i, int i2, List<FrameSpec.Layer> list, int i3) {
        int iSlotsOf = slotsOf(frameNodeDoc, node2);
        int[] iArrBeatOf = beatOf(frameNodeDoc, node2);
        boolean z = node2.value(2) != 0;
        for (int i4 = 0; i4 < iSlotsOf; i4++) {
            assumedEvent = node2.id;
            assumedBranch = i4;
            int firstLayer = list.size();
            try {
                collect(frameNodeDoc, i, i2, list, i3);
                assumedEvent = 0;
                FrameSpec.Queue queue = new FrameSpec.Queue(i4, iSlotsOf, iArrBeatOf[0], iArrBeatOf[1], iArrBeatOf[2], z);
                for (int size = firstLayer; size < list.size(); size++) {
                    FrameSpec.Layer layer = list.get(size);
                    if (layer.queue == null) {
                        list.set(size, new FrameSpec.Builder(layer).queue(queue).build());
                    }
                }
            } catch (Throwable th) {
                assumedEvent = 0;
                throw th;
            }
        }
    }

    static {
        HashMap map = new HashMap();
        FIELDS = map;
        map.put(10, new int[]{1, 2, 3, 6, 0});
        map.put(11, new int[]{4, 5, 2, 3, 6});
        map.put(12, new int[]{4, 2, 3, 6});
        map.put(13, new int[]{4, 8, 9, 7});
        map.put(2, new int[]{4, 8, 9, 7});
        map.put(14, new int[]{5, 4, 13, 10, 11});
        map.put(26, new int[]{0, 12});
        map.put(20, new int[]{14});
        map.put(21, new int[]{15});
        map.put(22, new int[]{17});
        map.put(23, new int[]{7, 18});
        map.put(24, new int[]{19});
        map.put(25, new int[]{16});
        map.put(70, new int[]{21, 22});
        map.put(31, new int[]{23});
        map.put(40, new int[]{3, 2});
        map.put(41, new int[]{7});
        map.put(42, new int[]{4});
        map.put(43, new int[]{3, 2, 8, 9, 7, 4});
        pending = new ArrayList();
    }

    private static FrameNodeDoc.Node eventOf(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node) {
        FrameNodeDoc.Node nodeHunt;
        FrameNodeType.Kind kindOf = FrameNodeType.of(node.type);
        if (kindOf == null) {
            return null;
        }
        int iPins = frameNodeDoc.pins(node.id);
        for (int i = 0; i < iPins; i++) {
            int iPinKind = FrameNodeType.pinKind(kindOf, i);
            if ((iPinKind == 1 || iPinKind == 3) && (nodeHunt = hunt(frameNodeDoc, frameNodeDoc.node(frameNodeDoc.input(node.id, i)), 0)) != null) {
                return nodeHunt;
            }
        }
        for (int i2 = 0; i2 < kindOf.knobs(); i2++) {
            FrameNodeDoc.Node nodeHunt2 = hunt(frameNodeDoc, frameNodeDoc.node(frameNodeDoc.input(node.id, FrameNodeType.knobPin(i2))), 0);
            if (nodeHunt2 != null) {
                return nodeHunt2;
            }
        }
        return null;
    }

    private static FrameNodeDoc.Node hunt(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node, int i) {
        FrameNodeDoc.Node nodeHunt;
        if (node != null && i <= 8) {
            FrameNodeDoc.Node nodeLeads = leads(node);
            if (nodeLeads != null) {
                return (nodeLeads.value(1) != 1 || (nodeHunt = hunt(frameNodeDoc, frameNodeDoc.node(frameNodeDoc.input(nodeLeads.id, FrameNodeType.knobPin(3))), i + 1)) == null) ? nodeLeads : nodeHunt;
            }
            if (node.type != 56) {
                return null;
            }
            int i2 = i + 1;
            FrameNodeDoc.Node nodeHunt2 = hunt(frameNodeDoc, frameNodeDoc.node(frameNodeDoc.input(node.id, 0)), i2);
            if (nodeHunt2 != null) {
                return nodeHunt2;
            }
            FrameNodeType.Kind kindOf = FrameNodeType.of(node.type);
            for (int i3 = 0; kindOf != null && i3 < kindOf.knobs(); i3++) {
                FrameNodeDoc.Node nodeHunt3 = hunt(frameNodeDoc, frameNodeDoc.node(frameNodeDoc.input(node.id, FrameNodeType.knobPin(i3))), i2);
                if (nodeHunt3 != null) {
                    return nodeHunt3;
                }
            }
        }
        return null;
    }

    private static FrameNodeDoc.Node leads(FrameNodeDoc.Node node) {
        if (node == null || node.type != 55 || (node.value(1) != 1 && (assumed.containsKey(node.text(0)) || node.text(0).length() <= 0))) {
            return null;
        }
        return node;
    }

    private static int slotsOf(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node) {
        return Math.min(32, Math.max(1, frameNodeDoc.pins(node.id) - 1));
    }

    private static int[] beatOf(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node) {
        String str;
        int iKnobPin = FrameNodeType.knobPin(3);
        FrameNodeDoc.Node node2 = frameNodeDoc.node(frameNodeDoc.input(node.id, iKnobPin));
        if (node2 == null || node2.type != 56 || frameNodeDoc.inputOut(node.id, iKnobPin) != 1) {
            int i = node.id;
            if (node2 == null) {
                str = "не пришёл";
            } else {
                str = node2.type != 56 ? "не от живого числа" : "из выхода числа, а не сигнала";
            }
            say(i, "толчка нет: провод ".concat(str));
            return new int[]{0, 0, 0};
        }
        int iOwn = own(frameNodeDoc, node2, 1);
        int iOwn2 = own(frameNodeDoc, node2, 2);
        say(node.id, "толчок " + iOwn + "/мин, ход " + iOwn2);
        return new int[]{iOwn, iOwn2, own(frameNodeDoc, node2, 3)};
    }

    private static void say(int i, String str) {
        if (str.equals(SAID.put(Integer.valueOf(i), str))) {
            return;
        }
        if (SAID.size()>512) SAID.clear();
    }

    private static void stamp(List<FrameSpec.Layer> list, int i, String str, boolean z) {
        for (int size = list.size() - 1; size >= i; size--) {
            FrameSpec.Layer layer = list.get(size);
            if (clashes(layer, str, z)) {
                list.remove(size);
            } else {
                list.set(size, new FrameSpec.Builder(layer).when(str, z).build());
            }
        }
    }

    private static boolean clashes(FrameSpec.Layer layer, String str, boolean z) {
        if (layer != null && layer.whens != null && str != null) {
            for (int i = 0; i < layer.whens.size(); i++) {
                FrameSpec.When when = layer.whens.get(i);
                if (str.equals(when.block) && when.on != z) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void one(FrameNodeDoc frameNodeDoc, int i, int i2, List<FrameSpec.Layer> list, int i3) {
        FrameNodeDoc.Node node = frameNodeDoc.node(i);
        if (node == null || i3 > 512) {
            return;
        }
        if (list.size() >= 200) {
            say(i, "слоёв больше предела в 200: часть видов не уместилась");
            return;
        }
        if (node.type == 50) {
            int iPins = frameNodeDoc.pins(i);
            for (int i4 = 0; i4 < iPins; i4++) {
                collect(frameNodeDoc, frameNodeDoc.input(i, i4), frameNodeDoc.inputOut(i, i4), list, i3 + 1);
            }
            return;
        }
        if (node.type == 51) {
            pinned(frameNodeDoc, node, i2, list, i3);
            return;
        }
        if (node.type == 55) {
            event(frameNodeDoc, node, list, i3);
            return;
        }
        if (node.type == 1) {
            FrameSpec.Layer layerSticker = sticker(node.text(0));
            if (layerSticker != null) {
                list.add(layerSticker);
                return;
            }
            return;
        }
        if (FrameNodeType.makesLayer(node.type)) {
            pending.clear();
            FrameSpec.Layer layerShape = shape(frameNodeDoc, node);
            List<FrameSpec.Alive> listTakeAlive = takeAlive();
            if (layerShape != null) {
                if (listTakeAlive != null) {
                    layerShape = new FrameSpec.Builder(layerShape).alive(listTakeAlive).build();
                }
                list.add(layerShape);
                return;
            }
            return;
        }
        if (FrameNodeType.editsLayer(node.type)) {
            int firstLayer = list.size();
            collect(frameNodeDoc, frameNodeDoc.input(i, 0), frameNodeDoc.inputOut(i, 0), list, i3 + 1);
            boolean zFeedsGroup = feedsGroup(frameNodeDoc, frameNodeDoc.input(i, 0), 0);
            for (int size = firstLayer; size < list.size(); size++) {
                pending.clear();
                FrameSpec.Layer layerEdit = edit(frameNodeDoc, node, list.get(size), zFeedsGroup);
                List<FrameSpec.Alive> listTakeAlive2 = takeAlive();
                if (listTakeAlive2 != null) {
                    FrameSpec.Builder builder = new FrameSpec.Builder(layerEdit);
                    for (int i5 = 0; i5 < listTakeAlive2.size(); i5++) {
                        FrameSpec.Alive alive = listTakeAlive2.get(i5);
                        if (zFeedsGroup && alive.field == 7) {
                            alive = new FrameSpec.Alive(20, alive.span, alive.speed, alive.wave, alive.phase);
                        }
                        builder.alive(alive);
                    }
                    layerEdit = builder.build();
                }
                list.set(size, layerEdit);
            }
        }
    }

    private static void event(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node, List<FrameSpec.Layer> list, int i) {
        if (node.value(1) == 1) {
            queued(frameNodeDoc, node, list, i);
            return;
        }
        String strText = node.text(0);
        if (strText == null || strText.length() == 0) {
            return;
        }
        int i2 = 0;
        while (i2 < 2) {
            int size = list.size();
            collect(frameNodeDoc, frameNodeDoc.input(node.id, i2), frameNodeDoc.inputOut(node.id, i2), list, i + 1);
            stamp(list, size, strText, i2 == 1);
            i2++;
        }
    }

    private static void queued(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node, List<FrameSpec.Layer> list, int i) {
        int iSlotsOf = slotsOf(frameNodeDoc, node);
        int[] iArrBeatOf = beatOf(frameNodeDoc, node);
        int i2 = 0;
        int i3 = iArrBeatOf[0];
        int i4 = iArrBeatOf[1];
        int i5 = iArrBeatOf[2];
        boolean z = node.value(2) != 0;
        while (i2 < iSlotsOf) {
            int firstLayer = list.size();
            collect(frameNodeDoc, frameNodeDoc.input(node.id, i2), frameNodeDoc.inputOut(node.id, i2), list, i + 1);
            int i6 = i5;
            FrameSpec.Queue queue = new FrameSpec.Queue(i2, iSlotsOf, i3, i4, i6, z);
            for (int size = firstLayer; size < list.size(); size++) {
                FrameSpec.Layer layer = list.get(size);
                if (layer.queue == null) {
                    list.set(size, new FrameSpec.Builder(layer).queue(queue).build());
                }
            }
            i2++;
            i5 = i6;
        }
    }

    private static void pinned(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node, int i, List<FrameSpec.Layer> list, int i2) {
        if (i <= 0) {
            collect(frameNodeDoc, frameNodeDoc.input(node.id, 0), frameNodeDoc.inputOut(node.id, 0), list, i2 + 1);
            return;
        }
        ArrayList arrayList = new ArrayList();
        int i3 = i2 + 1;
        collect(frameNodeDoc, frameNodeDoc.input(node.id, 0), frameNodeDoc.inputOut(node.id, 0), arrayList, i3);
        int size = list.size();
        collect(frameNodeDoc, frameNodeDoc.input(node.id, i), frameNodeDoc.inputOut(node.id, i), list, i3);
        if (arrayList.isEmpty() || list.size() == size) {
            return;
        }
        float[] fArrSpot = spot((FrameSpec.Layer) arrayList.get(0), knob(frameNodeDoc, node, 0));
        while (size < list.size()) {
            FrameSpec.Layer layer = list.get(size);
            list.set(size, new FrameSpec.Builder(layer).x((layer.x + fArrSpot[0]) - 0.5f).y((layer.y + fArrSpot[1]) - 0.5f).build());
            size++;
        }
    }

    private static float[] spot(FrameSpec.Layer layer, int i) {
        float f;
        if (layer.mode == 0) {
            f = ((layer.offset + layer.width) / 84.0f) + 0.5f;
        } else {
            f = (((layer.width * layer.scale) / 100.0f) / 84.0f) / 2.0f;
        }
        float f2 = layer.x;
        float f3 = layer.y;
        switch (i) {
            case 1:
                return new float[]{f2, f3 - f};
            case 2:
                return new float[]{f2, f3 + f};
            case 3:
                return new float[]{f2 - f, f3};
            case 4:
                return new float[]{f2 + f, f3};
            case 5:
                return new float[]{f2 - f, f3 - f};
            case 6:
                return new float[]{f2 + f, f3 - f};
            case 7:
                return new float[]{f2 - f, f3 + f};
            case 8:
                return new float[]{f2 + f, f3 + f};
            default:
                return new float[]{f2, f3};
        }
    }

    private static boolean feedsGroup(FrameNodeDoc frameNodeDoc, int i, int i2) {
        FrameNodeDoc.Node node = frameNodeDoc.node(i);
        if (node == null || i2 > 512) {
            return false;
        }
        if (node.type == 50) {
            return true;
        }
        if (FrameNodeType.editsLayer(node.type)) {
            return feedsGroup(frameNodeDoc, frameNodeDoc.input(i, 0), i2 + 1);
        }
        return false;
    }

    private static int knob(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node, int i) {
        return wired(frameNodeDoc, frameNodeDoc.node(frameNodeDoc.input(node.id, FrameNodeType.knobPin(i))), node, i, 0);
    }

    static int knobValue(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node, int i) {
        if (frameNodeDoc == null || node == null) {
            return 0;
        }
        try {
            return knob(frameNodeDoc, node, i);
        } finally {
            pending.clear();
        }
    }

    private static int wired(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node, FrameNodeDoc.Node node2, int i, int i2) {
        if (node == null || i2 > 8) {
            return node2.value(i);
        }
        if (node.type == 5) {
            return node.value(0);
        }
        if (node.type == 6) {
            return node.value((FrameNodeType.roleOf(FrameNodeType.of(node2.type), i)==2?1:0));
        }
        if (node.type == 56) {
            live(frameNodeDoc, node2, i, node);
            return wired(frameNodeDoc, frameNodeDoc.node(frameNodeDoc.input(node.id, 0)), node2, i, i2 + 1);
        }
        if (node.type == 55) {
            if (assumed.isEmpty() && assumedEvent == 0) {
                say(node.id, "число взято намертво у узла " + node2.id + ": проход сборки не разведён по этому событию");
            }
            return wired(frameNodeDoc, frameNodeDoc.node(frameNodeDoc.input(node.id, branchOf(node))), node2, i, i2 + 1);
        }
        return node2.value(i);
    }

    private static int own(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node, int i) {
        return wired(frameNodeDoc, frameNodeDoc.node(frameNodeDoc.input(node.id, FrameNodeType.knobPin(i))), node, i, 0);
    }

    private static boolean at(String str) {
        if (str == null || str.length() == 0) {
            return false;
        }
        Boolean bool = assumed.get(str);
        if (bool != null) {
            return bool.booleanValue();
        }
        return false;
    }

    private static List<FrameSpec.Alive> takeAlive() {
        List<FrameSpec.Alive> list = pending;
        if (list.isEmpty()) {
            return null;
        }
        ArrayList arrayList = new ArrayList(list);
        list.clear();
        return arrayList;
    }

    private static void live(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node, int i, FrameNodeDoc.Node node2) {
        int[] iArr = FIELDS.get(Integer.valueOf(node.type));
        int i2 = (iArr == null || i < 0 || i >= iArr.length) ? 0 : iArr[i];
        if (i2 == 0) {
            return;
        }
        pending.add(new FrameSpec.Alive(i2, own(frameNodeDoc, node2, 0), own(frameNodeDoc, node2, 1), own(frameNodeDoc, node2, 2), own(frameNodeDoc, node2, 3)));
    }

    private static FrameSpec.Layer sticker(String str) {
        if (str == null || str.length() == 0) {
            return null;
        }
        return new FrameSpec.Builder().src(str).animated(FrameRuntime.isAnimated(str)).mode(3).width(18).scale(100).x(0.5f).y(0.5f).build();
    }

    private static FrameSpec.Layer shape(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node) {
        if (node.type == 2) {
            return avatar(frameNodeDoc, node);
        }
        String strPicture = picture(frameNodeDoc, node);
        if (strPicture.length() == 0) {
            return null;
        }
        FrameSpec.Builder builderAnimated = new FrameSpec.Builder().src(strPicture).animated(FrameRuntime.isAnimated(strPicture));
        switch (node.type) {
            case 10:
                return builderAnimated.mode(0).width(knob(frameNodeDoc, node, 0)).offset(knob(frameNodeDoc, node, 1)).at(knob(frameNodeDoc, node, 2) / 100.0f).spin(knob(frameNodeDoc, node, 3)).seamless(knob(frameNodeDoc, node, 4) != 0).build();
            case 11:
                return builderAnimated.mode(1).width(24).scale(knob(frameNodeDoc, node, 0)).repeat(knob(frameNodeDoc, node, 1)).offset(knob(frameNodeDoc, node, 2)).at(knob(frameNodeDoc, node, 3) / 100.0f).spin(knob(frameNodeDoc, node, 4)).build();
            case 12:
                return builderAnimated.mode(2).width(24).scale(knob(frameNodeDoc, node, 0)).offset(knob(frameNodeDoc, node, 1)).at(knob(frameNodeDoc, node, 2) / 100.0f).spin(knob(frameNodeDoc, node, 3)).build();
            case 13:
                return builderAnimated.mode(3).width(18).scale(knob(frameNodeDoc, node, 0)).x((knob(frameNodeDoc, node, 1) / 100.0f) + 0.5f).y((knob(frameNodeDoc, node, 2) / 100.0f) + 0.5f).turn(knob(frameNodeDoc, node, 3)).build();
            case 14:
                return builderAnimated.mode(4).width(10).repeat(knob(frameNodeDoc, node, 0)).scale(knob(frameNodeDoc, node, 1)).field(knob(frameNodeDoc, node, 2)).spread(knob(frameNodeDoc, node, 3)).speed(knob(frameNodeDoc, node, 4)).build();
            default:
                return null;
        }
    }

    private static FrameSpec.Layer edit(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node, FrameSpec.Layer layer, boolean z) {
        FrameSpec.Builder builder = new FrameSpec.Builder(layer);
        int i = node.type;
        if (i == 30) {
            return builder.tint(color(frameNodeDoc, node)).build();
        }
        if (i == 31) {
            return builder.visible((layer.visible * knob(frameNodeDoc, node, 0)) / 100).build();
        }
        if (i == 70) {
            return builder.glow(knob(frameNodeDoc, node, 0)).glowRadius(knob(frameNodeDoc, node, 1)).build();
        }
        if (i == 72) {
            return builder.bake(true).bakeCompat(knob(frameNodeDoc, node, 0) != 0).build();
        }
        switch (i) {
            case 20:
                return builder.scatter(layer.scatter + knob(frameNodeDoc, node, 0)).build();
            case 21:
                return builder.swirl(layer.swirl + knob(frameNodeDoc, node, 0)).build();
            case 22:
                return builder.gravity(layer.gravity + knob(frameNodeDoc, node, 0)).build();
            case 23:
                return builder.turn(layer.turn + knob(frameNodeDoc, node, 0)).twist(layer.twist + knob(frameNodeDoc, node, 1)).build();
            case 24:
                return builder.twinkle(layer.twinkle + knob(frameNodeDoc, node, 0)).build();
            case FrameNodeType.JITTER /* 25 */:
                return builder.chaos(layer.chaos + knob(frameNodeDoc, node, 0)).build();
            case FrameNodeType.FLOW /* 26 */:
                return builder.flow(knob(frameNodeDoc, node, 0)).course(layer.course + knob(frameNodeDoc, node, 1)).build();
            default:
                switch (i) {
                    case 40:
                        return builder.at(layer.at + (knob(frameNodeDoc, node, 0) / 100.0f)).offset(layer.offset + knob(frameNodeDoc, node, 1)).build();
                    case FrameNodeType.TURN /* 41 */:
                        return rotate(layer, knob(frameNodeDoc, node, 0), z);
                    case FrameNodeType.SIZE /* 42 */:
                        return resize(layer, knob(frameNodeDoc, node, 0), z);
                    case FrameNodeType.TRANSFORM /* 43 */:
                        return transform(frameNodeDoc, node, layer, z);
                    default:
                        return external(frameNodeDoc, node, layer);
                }
        }
    }

    private static FrameSpec.Layer external(FrameNodeDoc doc,FrameNodeDoc.Node node,FrameSpec.Layer layer){
        if((node.type&0x40000000)==0)throw new IllegalArgumentException("Unknown native frame effect: "+node.type);FrameNodeType.Kind kind=FrameNodeType.of(node.type);if(kind==null)return layer;int[] values=new int[kind.knobs()];for(int i=0;i<values.length;i++)values[i]=knob(doc,node,i);return new FrameSpec.Builder(layer).addExtra(node.type,values).build();
    }

    private static FrameSpec.Layer transform(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node, FrameSpec.Layer layer, boolean z) {
        FrameSpec.Layer layerResize = resize(rotate(layer, knob(frameNodeDoc, node, 4), z), knob(frameNodeDoc, node, 5), z);
        return new FrameSpec.Builder(layerResize).at(layerResize.at + (knob(frameNodeDoc, node, 0) / 100.0f)).offset(layerResize.offset + knob(frameNodeDoc, node, 1)).x(layerResize.x + (knob(frameNodeDoc, node, 2) / 100.0f)).y(layerResize.y + (knob(frameNodeDoc, node, 3) / 100.0f)).build();
    }

    private static FrameSpec.Layer resize(FrameSpec.Layer layer, int i, boolean z) {
        float f = i / 100.0f;
        FrameSpec.Builder builder = new FrameSpec.Builder(layer);
        if (layer.mode == 0) {
            builder.width(Math.round(layer.width * f));
        } else {
            builder.scale(Math.round(layer.scale * f));
        }
        if (!z) {
            return builder.build();
        }
        builder.offset(Math.round(layer.offset * f));
        if (layer.mode == 3) {
            builder.x(((layer.x - 0.5f) * f) + 0.5f).y(((layer.y - 0.5f) * f) + 0.5f);
        } else if (layer.mode == 4) {
            builder.field(Math.round(layer.field * f)).spread(Math.round(layer.spread * f));
        }
        return builder.build();
    }

    private static FrameSpec.Layer rotate(FrameSpec.Layer layer, int i, boolean z) {
        FrameSpec.Builder builder = new FrameSpec.Builder(layer);
        if (!z) {
            return builder.turn(layer.turn + i).build();
        }
        return builder.orbit(layer.orbit + i).build();
    }

    private static String picture(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node) {
        FrameNodeDoc.Node nodeBehind = behind(frameNodeDoc, frameNodeDoc.node(frameNodeDoc.input(node.id, 0)), 0);
        if (nodeBehind != null && nodeBehind.type == 1) {
            return nodeBehind.text(0);
        }
        return "";
    }

    private static FrameNodeDoc.Node behind(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node, int i) {
        return (node == null || i > 8 || node.type != 55) ? node : behind(frameNodeDoc, frameNodeDoc.node(frameNodeDoc.input(node.id, branchOf(node))), i + 1);
    }

    private static int branchOf(FrameNodeDoc.Node node) {
        if (node.value(1) != 1) {
            return at(node.text(0)) ? 1 : 0;
        }
        if (node.id == assumedEvent) {
            return assumedBranch;
        }
        return 0;
    }

    private static FrameSpec.Layer avatar(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node) {
        return new FrameSpec.Builder().src(FrameRuntime.AVATAR).mode(3).width(Math.round(84.0f)).scale(knob(frameNodeDoc, node, 0)).x((knob(frameNodeDoc, node, 1) / 100.0f) + 0.5f).y((knob(frameNodeDoc, node, 2) / 100.0f) + 0.5f).turn(knob(frameNodeDoc, node, 3)).build();
    }

    private static int color(FrameNodeDoc frameNodeDoc, FrameNodeDoc.Node node) {
        FrameNodeDoc.Node nodeBehind = behind(frameNodeDoc, frameNodeDoc.node(frameNodeDoc.input(node.id, 1)), 0);
        if (nodeBehind != null && nodeBehind.type == 3) {
            return nodeBehind.value(0);
        }
        return knob(frameNodeDoc, node, 0);
    }

    public static FrameNodeDoc of(FrameSpec frameSpec) {
        int size;
        int iShapeOf;
        FrameNodeDoc frameNodeDocEmpty = FrameNodeDoc.empty();
        int iAdd = frameNodeDocEmpty.add(60, 750, 0);
        int iAdd2 = frameNodeDocEmpty.add(61, 1000, 0);
        int iAdd3 = frameNodeDocEmpty.add(50, 500, 0);
        frameNodeDocEmpty.link(iAdd3, iAdd, 0);
        frameNodeDocEmpty.link(iAdd3, iAdd2, 0);
        if (frameSpec != null && !frameSpec.isEmpty()) {
            int i = 0;
            for (int i2 = 0; i2 < frameSpec.layers().size(); i2++) {
                FrameSpec.Layer layer = frameSpec.layers().get(i2);
                if (!layer.off && (iShapeOf = shapeOf(frameNodeDocEmpty, layer, (size = (i - ((frameSpec.layers().size() - 1) / 2)) * FrameNodeDoc.STEP_Y))) > 0) {
                    int iAdd4 = frameNodeDocEmpty.add(1, -250, size);
                    frameNodeDocEmpty.set(iAdd4, 0, layer.src);
                    frameNodeDocEmpty.link(iAdd4, iShapeOf, 0);
                    frameNodeDocEmpty.link(edits(frameNodeDocEmpty, layer, iShapeOf, size), iAdd3, i);
                    i++;
                }
            }
        }
        return frameNodeDocEmpty;
    }

    private static int shapeOf(FrameNodeDoc frameNodeDoc, FrameSpec.Layer layer, int i) {
        int i2 = layer.mode;
        if (i2 == 0) {
            int iAdd = frameNodeDoc.add(10, 0, i);
            frameNodeDoc.set(iAdd, 0, layer.width);
            frameNodeDoc.set(iAdd, 1, layer.offset);
            frameNodeDoc.set(iAdd, 2, Math.round(layer.at * 100.0f));
            frameNodeDoc.set(iAdd, 3, layer.spin);
            frameNodeDoc.set(iAdd, 4, layer.seamless ? 1 : 0);
            return iAdd;
        }
        if (i2 == 1) {
            int iAdd2 = frameNodeDoc.add(11, 0, i);
            frameNodeDoc.set(iAdd2, 0, layer.scale);
            frameNodeDoc.set(iAdd2, 1, layer.repeat);
            frameNodeDoc.set(iAdd2, 2, layer.offset);
            frameNodeDoc.set(iAdd2, 3, Math.round(layer.at * 100.0f));
            frameNodeDoc.set(iAdd2, 4, layer.spin);
            return iAdd2;
        }
        if (i2 == 2) {
            int iAdd3 = frameNodeDoc.add(12, 0, i);
            frameNodeDoc.set(iAdd3, 0, layer.scale);
            frameNodeDoc.set(iAdd3, 1, layer.offset);
            frameNodeDoc.set(iAdd3, 2, Math.round(layer.at * 100.0f));
            frameNodeDoc.set(iAdd3, 3, layer.spin);
            return iAdd3;
        }
        if (i2 == 3) {
            int iAdd4 = frameNodeDoc.add(13, 0, i);
            frameNodeDoc.set(iAdd4, 0, layer.scale);
            frameNodeDoc.set(iAdd4, 1, Math.round((layer.x - 0.5f) * 100.0f));
            frameNodeDoc.set(iAdd4, 2, Math.round((layer.y - 0.5f) * 100.0f));
            frameNodeDoc.set(iAdd4, 3, layer.turn);
            return iAdd4;
        }
        if (i2 != 4) {
            return 0;
        }
        int iAdd5 = frameNodeDoc.add(14, 0, i);
        frameNodeDoc.set(iAdd5, 0, layer.repeat);
        frameNodeDoc.set(iAdd5, 1, layer.scale);
        frameNodeDoc.set(iAdd5, 2, layer.field);
        frameNodeDoc.set(iAdd5, 3, layer.spread);
        frameNodeDoc.set(iAdd5, 4, layer.speed);
        return iAdd5;
    }

    private static int chainExtra(FrameNodeDoc frameNodeDoc, int i, FrameSpec.Effect effect, int i2, int i3) {
        int iAdd = frameNodeDoc.add(effect.type, i3 * 125, i2 + (i3 * 50));
        for (int i4 = 0; i4 < effect.knobs.length; i4++) {
            frameNodeDoc.set(iAdd, i4, effect.knobs[i4]);
        }
        frameNodeDoc.link(i, iAdd, 0);
        return iAdd;
    }

    private static int edits(FrameNodeDoc frameNodeDoc, FrameSpec.Layer layer, int i, int i2) {
        int iChain;
        int i3;
        if (layer.tint != 0) {
            i3 = 1;
            iChain = chain(frameNodeDoc, i, 30, i2, 1, layer.tint, 0);
        } else {
            iChain = i;
            i3 = 0;
        }
        if (layer.glow != 0) {
            int i4 = i3 + 1;
            iChain = chain(frameNodeDoc, iChain, 70, i2, i4, layer.glow, layer.glowRadius);
            i3 = i4;
        }
        for (int i5 = 0; i5 < layer.extras.size(); i5++) {
            i3++;
            iChain = chainExtra(frameNodeDoc, iChain, layer.extras.get(i5), i2, i3);
        }
        if (layer.bakeCompat) {
            int i6 = i3 + 1;
            iChain = chain(frameNodeDoc, iChain, 72, i2, i6, 1, 0);
            i3 = i6;
        }
        if (layer.mode != 4) {
            return iChain;
        }
        if (layer.flow != 0 || layer.course != 0) {
            int i7 = i3 + 1;
            iChain = chain(frameNodeDoc, iChain, 26, i2, i7, layer.flow, layer.course);
            i3 = i7;
        }
        if (layer.scatter != 0) {
            int i8 = i3 + 1;
            iChain = chain(frameNodeDoc, iChain, 20, i2, i8, layer.scatter, 0);
            i3 = i8;
        }
        if (layer.swirl != 0) {
            int i9 = i3 + 1;
            iChain = chain(frameNodeDoc, iChain, 21, i2, i9, layer.swirl, 0);
            i3 = i9;
        }
        if (layer.gravity != 0) {
            int i10 = i3 + 1;
            iChain = chain(frameNodeDoc, iChain, 22, i2, i10, layer.gravity, 0);
            i3 = i10;
        }
        if (layer.turn != 0 || layer.twist != 0) {
            int i11 = i3 + 1;
            iChain = chain(frameNodeDoc, iChain, 23, i2, i11, layer.turn, layer.twist);
            i3 = i11;
        }
        if (layer.twinkle != 0) {
            int i12 = i3 + 1;
            iChain = chain(frameNodeDoc, iChain, 24, i2, i12, layer.twinkle, 0);
            i3 = i12;
        }
        return layer.chaos != 0 ? chain(frameNodeDoc, iChain, 25, i2, i3 + 1, layer.chaos, 0) : iChain;
    }

    private static int chain(FrameNodeDoc frameNodeDoc, int i, int i2, int i3, int i4, int i5, int i6) {
        int iAdd = frameNodeDoc.add(i2, i4 * 125, i3 + (i4 * 50));
        frameNodeDoc.set(iAdd, 0, i5);
        FrameNodeType.Kind kindOf = FrameNodeType.of(i2);
        if (kindOf != null && kindOf.knobs() > 1) {
            frameNodeDoc.set(iAdd, 1, i6);
        }
        frameNodeDoc.link(i, iAdd, 0);
        return iAdd;
    }
}
