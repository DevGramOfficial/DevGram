package org.telegram.ui.Components.devgram.frame;

import java.util.ArrayList;
import java.util.List;

/** Layer editing follows the supplied frame studio, backed by the same typed graph. */
public final class FrameNodeLayers {
    private FrameNodeLayers() {
    }

    public static int ensure(FrameNodeDoc frameNodeDoc) {
        int iFrame;
        if (frameNodeDoc == null || (iFrame = frame(frameNodeDoc)) <= 0) {
            return 0;
        }
        int iStack = stack(frameNodeDoc);
        if (iStack > 0) {
            return iStack;
        }
        int iInput = frameNodeDoc.input(iFrame, 0);
        int iAdd = frameNodeDoc.add(50, 500, 0);
        if (iInput > 0) {
            frameNodeDoc.cut(iFrame, 0);
            frameNodeDoc.link(iInput, iAdd, 0);
        }
        frameNodeDoc.link(iAdd, iFrame, 0);
        return iAdd;
    }

    public static int frame(FrameNodeDoc frameNodeDoc) {
        if (frameNodeDoc == null) {
            return 0;
        }
        int iFirst = frameNodeDoc.first(60);
        return iFirst > 0 ? iFirst : frameNodeDoc.add(60, 750, 0);
    }

    public static int stack(FrameNodeDoc frameNodeDoc) {
        int iFirst;
        FrameNodeDoc.Node node;
        if (frameNodeDoc == null || (iFirst = frameNodeDoc.first(60)) <= 0) {
            return 0;
        }
        int iInput = frameNodeDoc.input(iFirst, 0);
        for (int i = 0; i <= 512 && (node = frameNodeDoc.node(iInput)) != null; i++) {
            if (node.type == 50) {
                return node.id;
            }
            if (!FrameNodeType.editsLayer(node.type)) {
                return 0;
            }
            iInput = frameNodeDoc.input(iInput, 0);
        }
        return 0;
    }

    public static List<Integer> shapes(FrameNodeDoc frameNodeDoc) {
        ArrayList arrayList = new ArrayList();
        int iStack = stack(frameNodeDoc);
        if (iStack <= 0) {
            int iFirst = frameNodeDoc == null ? 0 : frameNodeDoc.first(60);
            int iShapeOf = iFirst > 0 ? shapeOf(frameNodeDoc, frameNodeDoc.input(iFirst, 0), 0) : 0;
            if (iShapeOf > 0) {
                arrayList.add(Integer.valueOf(iShapeOf));
                return arrayList;
            }
        } else {
            int iPins = frameNodeDoc.pins(iStack);
            for (int i = 0; i < iPins; i++) {
                int iShapeOf2 = shapeOf(frameNodeDoc, frameNodeDoc.input(iStack, i), 0);
                if (iShapeOf2 > 0) {
                    arrayList.add(Integer.valueOf(iShapeOf2));
                }
            }
        }
        return arrayList;
    }

    private static int shapeOf(FrameNodeDoc frameNodeDoc, int i, int i2) {
        FrameNodeDoc.Node node = frameNodeDoc.node(i);
        if (node == null || i2 > 512) {
            return 0;
        }
        if (FrameNodeType.makesLayer(node.type)) {
            return node.id;
        }
        if (FrameNodeType.editsLayer(node.type)) {
            return shapeOf(frameNodeDoc, frameNodeDoc.input(i, 0), i2 + 1);
        }
        return 0;
    }

    public static int top(FrameNodeDoc frameNodeDoc, int i) {
        int iOutput;
        FrameNodeDoc.Node node;
        int i2 = 0;
        while (i2 <= 512 && (node = frameNodeDoc.node((iOutput = frameNodeDoc.output(i)))) != null && FrameNodeType.editsLayer(node.type)) {
            i2++;
            i = iOutput;
        }
        return i;
    }

    public static List<Integer> edits(FrameNodeDoc frameNodeDoc, int i) {
        FrameNodeDoc.Node node;
        ArrayList arrayList = new ArrayList();
        for (int i2 = 0; i2 <= 512 && (node = frameNodeDoc.node((i = frameNodeDoc.output(i)))) != null && FrameNodeType.editsLayer(node.type); i2++) {
            arrayList.add(Integer.valueOf(i));
        }
        return arrayList;
    }

    public static int image(FrameNodeDoc frameNodeDoc, int i) {
        FrameNodeDoc.Node node = frameNodeDoc.node(frameNodeDoc.input(i, 0));
        if (node == null || node.type != 1) {
            return 0;
        }
        return node.id;
    }

    public static String picture(FrameNodeDoc frameNodeDoc, int i) {
        FrameNodeDoc.Node node = frameNodeDoc.node(image(frameNodeDoc, i));
        return node == null ? "" : node.text(0);
    }

    public static void picture(FrameNodeDoc frameNodeDoc, int i, String str) {
        int iImage = image(frameNodeDoc, i);
        if (iImage <= 0) {
            FrameNodeDoc.Node node = frameNodeDoc.node(i);
            iImage = frameNodeDoc.add(1, node == null ? 0 : node.x - 250, node == null ? 0 : node.y);
            frameNodeDoc.link(iImage, i, 0);
        }
        frameNodeDoc.set(iImage, 0, str);
    }

    public static int add(FrameNodeDoc frameNodeDoc, int i, String str) {
        int i2;
        int iAdd;
        if (!FrameNodeType.makesLayer(i)) {
            return 0;
        }
        boolean z = stack(frameNodeDoc) <= 0 && shapes(frameNodeDoc).isEmpty();
        int iFrame = frame(frameNodeDoc);
        if (iFrame <= 0) {
            return 0;
        }
        int iEnsure = z ? 0 : ensure(frameNodeDoc);
        if (!z && iEnsure <= 0) {
            return 0;
        }
        int iFree = z ? 0 : free(frameNodeDoc, iEnsure);
        if (iFree < 0 || (iAdd = frameNodeDoc.add(i, 0, (i2 = (iFree - 1) * FrameNodeDoc.STEP_Y))) <= 0) {
            return 0;
        }
        int iAdd2 = frameNodeDoc.add(1, -250, i2);
        if (str == null) {
            str = "";
        }
        frameNodeDoc.set(iAdd2, 0, str);
        frameNodeDoc.link(iAdd2, iAdd, 0);
        if (z) {
            frameNodeDoc.link(iAdd, iFrame, 0);
            return iAdd;
        }
        frameNodeDoc.link(iAdd, iEnsure, iFree);
        return iAdd;
    }

    private static int free(FrameNodeDoc frameNodeDoc, int i) {
        int iPins = frameNodeDoc.pins(i);
        for (int i2 = 0; i2 < iPins; i2++) {
            if (frameNodeDoc.input(i, i2) == 0) {
                return i2;
            }
        }
        return -1;
    }

    public static void remove(FrameNodeDoc frameNodeDoc, int i) {
        if (frameNodeDoc.node(i) == null) {
            return;
        }
        List<Integer> listEdits = edits(frameNodeDoc, i);
        int iImage = image(frameNodeDoc, i);
        for (int i2 = 0; i2 < listEdits.size(); i2++) {
            frameNodeDoc.drop(listEdits.get(i2).intValue());
        }
        frameNodeDoc.drop(i);
        boolean shared = false;
        for (FrameNodeDoc.Wire wire : frameNodeDoc.wires()) if (wire.from == iImage) shared = true;
        if (iImage > 0 && !shared) frameNodeDoc.drop(iImage);
    }

    public static void move(FrameNodeDoc frameNodeDoc, int i, boolean z) {
        int iStack = stack(frameNodeDoc);
        int pVar = top(frameNodeDoc, i);
        if (iStack <= 0) {
            return;
        }
        int iPins = frameNodeDoc.pins(iStack);
        int i2 = -1;
        for (int i3 = 0; i3 < iPins; i3++) {
            if (frameNodeDoc.input(iStack, i3) == pVar) {
                i2 = i3;
            }
        }
        int i4 = z ? i2 - 1 : i2 + 1;
        if (i2 < 0 || i4 < 0 || i4 >= iPins) {
            return;
        }
        frameNodeDoc.swap(iStack, i2, i4);
    }

    public static int attach(FrameNodeDoc frameNodeDoc, int i, int i2) {
        if (!FrameNodeType.editsLayer(i2) || frameNodeDoc.node(i) == null) {
            return 0;
        }
        int pVar = top(frameNodeDoc, i);
        FrameNodeDoc.Node node = frameNodeDoc.node(pVar);
        int iAdd = frameNodeDoc.add(i2, node.x + 125, node.y + 50);
        if (iAdd <= 0) {
            return 0;
        }
        int iOutput = frameNodeDoc.output(pVar);
        int iPinOf = pinOf(frameNodeDoc, iOutput, pVar);
        frameNodeDoc.link(pVar, iAdd, 0);
        if (iOutput > 0 && iPinOf >= 0) {
            frameNodeDoc.link(iAdd, iOutput, iPinOf);
        }
        return iAdd;
    }

    public static void detach(FrameNodeDoc frameNodeDoc, int i) {
        FrameNodeDoc.Node node = frameNodeDoc.node(i);
        if (node == null || !FrameNodeType.editsLayer(node.type)) {
            return;
        }
        int iInput = frameNodeDoc.input(i, 0);
        int iOutput = frameNodeDoc.output(i);
        int iPinOf = pinOf(frameNodeDoc, iOutput, i);
        frameNodeDoc.drop(i);
        if (iInput <= 0 || iOutput <= 0 || iPinOf < 0) {
            return;
        }
        frameNodeDoc.link(iInput, iOutput, iPinOf);
    }

    private static int pinOf(FrameNodeDoc frameNodeDoc, int i, int i2) {
        for (int i3 = 0; i3 < frameNodeDoc.wires().size(); i3++) {
            FrameNodeDoc.Wire wire = frameNodeDoc.wires().get(i3);
            if (wire.to == i && wire.from == i2) {
                return wire.pin;
            }
        }
        return -1;
    }
}
