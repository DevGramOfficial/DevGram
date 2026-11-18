package org.telegram.ui.Components.devgram.frame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native port of the user-supplied Custom Profile frame core; bounded data only. */
public final class FrameSpec {
    public static final int COLOR_UNSET = 0;
    public static final FrameSpec EMPTY = new FrameSpec(Collections.EMPTY_LIST);
    public static final int FIELD_LIMIT = 200;
    public static final int FLOW_COURSE = 0;
    public static final int FLOW_IN = 2;
    static final int FLOW_MAX = 2;
    public static final int FLOW_OUT = 1;
    public static final int F_AT = 3;
    public static final int F_CHAOS = 16;
    public static final int F_COURSE = 12;
    public static final int F_FIELD = 13;
    public static final int F_GLOW = 21;
    public static final int F_GLOW_RADIUS = 22;
    public static final int F_GRAVITY = 17;
    static final int F_MAX = 23;
    public static final int F_NONE = 0;
    public static final int F_OFFSET = 2;
    public static final int F_ORBIT = 20;
    public static final int F_REPEAT = 5;
    public static final int F_SCALE = 4;
    public static final int F_SCATTER = 14;
    public static final int F_SPEED = 11;
    public static final int F_SPIN = 6;
    public static final int F_SPREAD = 10;
    public static final int F_SWIRL = 15;
    public static final int F_TURN = 7;
    public static final int F_TWINKLE = 19;
    public static final int F_TWIST = 18;
    public static final int F_VISIBLE = 23;
    public static final int F_WIDTH = 1;
    public static final int F_X = 8;
    public static final int F_Y = 9;
    public static final int GRAVITY_LIMIT = 100;
    public static final int LIVE_SPAN_LIMIT = 4000;
    public static final int LIVE_SPEED_MAX = 600;
    public static final int LIVE_SPEED_MIN = 1;
    public static final int MAX_LAYERS = 200;
    public static final int MODE_MARK = 2;
    static final int MODE_MAX = 4;
    public static final int MODE_PARTICLES = 4;
    public static final int MODE_STAMP = 1;
    public static final int MODE_STICKER = 3;
    public static final int MODE_STRIP = 0;
    public static final int OFFSET_LIMIT = 200;
    public static final int ORBIT_LIMIT = 360;
    public static final int PART_LIMIT = 100;
    public static final float PLACE_MAX = 1.5f;
    public static final float PLACE_MIN = -0.5f;
    public static final int QUEUE_MAX = 32;
    public static final int REPEAT_MAX = 64;
    public static final int REPEAT_MIN = 1;
    public static final int SCALE_KNOB_MAX = 400;
    public static final int SCALE_MAX = 2000;
    public static final int SCALE_MIN = 10;
    public static final int SCATTER_LIMIT = 180;
    public static final int SPEED_MAX = 400;
    public static final int SPEED_MIN = 10;
    public static final int SPIN_LIMIT = 180;
    public static final int SPREAD_MAX = 400;
    public static final int SPREAD_MIN = 2;
    public static final int TWIST_LIMIT = 720;
    static final int WAVE_MAX = 3;
    public static final int WAVE_PUSH = 3;
    public static final int WAVE_ROUND = 1;
    public static final int WAVE_STEP = 2;
    public static final int WAVE_SWING = 0;
    public static final int WHEN_MAX = 3;
    public static final int WIDTH_MAX = 200;
    public static final int WIDTH_MIN = 1;
    private Boolean avatarUsed;
    private volatile Long cachedSignature;
    private Boolean eventUsed;
    private final List<Layer> layers;
    private Boolean liveUsed;

    private FrameSpec(List<Layer> list) {
        this.layers = list;
    }

    public List<Layer> layers() {
        return this.layers;
    }

    public boolean isEmpty() {
        return this.layers.isEmpty();
    }

    public Set<String> assets() {
        LinkedHashSet linkedHashSet = new LinkedHashSet();
        for (int i = 0; i < this.layers.size(); i++) {
            Layer layer = this.layers.get(i);
            linkedHashSet.add(layer.src);
            if (layer.corner.length() > 0) {
                linkedHashSet.add(layer.corner);
            }
        }
        return Collections.unmodifiableSet(linkedHashSet);
    }

    public static FrameSpec parse(String str) {
        Layer layerBuild;
        if (str == null || str.trim().length() == 0) {
            return EMPTY;
        }
        ArrayList arrayList = new ArrayList();
        try {
            JSONArray jSONArray = new JSONArray(str);
            boolean z = false;
            for (int i = 0; i < jSONArray.length() && arrayList.size() < 200; i++) {
                JSONObject jSONObjectOptJSONObject = jSONArray.optJSONObject(i);
                if (jSONObjectOptJSONObject != null && (layerBuild = read(jSONObjectOptJSONObject)) != null) {
                    if (layerBuild.animated) {
                        if (z) {
                            layerBuild = new Builder(layerBuild).animated(false).build();
                        } else {
                            z = true;
                        }
                    }
                    arrayList.add(layerBuild);
                }
            }
            return arrayList.isEmpty() ? EMPTY : new FrameSpec(Collections.unmodifiableList(arrayList));
        } catch (Throwable unused) {
            return EMPTY;
        }
    }

    public String encode() {
        JSONArray jSONArray = new JSONArray();
        for (int i = 0; i < this.layers.size() && jSONArray.length() < 200; i++) {
            try {
                jSONArray.put(this.layers.get(i).write());
            } catch (Throwable unused) {
            }
        }
        return jSONArray.toString();
    }

    public boolean usesAvatar() {
        Boolean bool = this.avatarUsed;
        if (bool != null) {
            return bool.booleanValue();
        }
        boolean z = false;
        int i = 0;
        while (true) {
            if (i >= layers().size()) {
                break;
            }
            if (FrameRuntime.isAvatar(layers().get(i).src)) {
                z = true;
                break;
            }
            i++;
        }
        this.avatarUsed = Boolean.valueOf(z);
        return z;
    }

    public boolean usesEvents() {
        Boolean bool = this.eventUsed;
        if (bool != null) {
            return bool.booleanValue();
        }
        boolean z = false;
        int i = 0;
        while (true) {
            if (i >= layers().size()) {
                break;
            }
            if (!layers().get(i).whens.isEmpty()) {
                z = true;
                break;
            }
            i++;
        }
        this.eventUsed = Boolean.valueOf(z);
        return z;
    }

    public boolean usesLive() {
        Boolean bool = this.liveUsed;
        if (bool != null) {
            return bool.booleanValue();
        }
        boolean z = false;
        for (int i = 0; i < layers().size(); i++) {
            if (!layers().get(i).alive.isEmpty() || layers().get(i).queue != null) {
                z = true;
                break;
            }
        }
        this.liveUsed = Boolean.valueOf(z);
        return z;
    }

    public static FrameSpec of(List<Layer> list) {
        if (list == null || list.isEmpty()) {
            return EMPTY;
        }
        ArrayList arrayList = new ArrayList();
        boolean z = false;
        for (int i = 0; i < list.size() && arrayList.size() < 200; i++) {
            Layer layerBuild = list.get(i);
            if (layerBuild != null) {
                if (layerBuild.animated) {
                    if (z) {
                        layerBuild = new Builder(layerBuild).animated(false).build();
                    } else {
                        z = true;
                    }
                }
                arrayList.add(layerBuild);
            }
        }
        return arrayList.isEmpty() ? EMPTY : new FrameSpec(Collections.unmodifiableList(arrayList));
    }

    public static String sanitize(String str) {
        return parse(str).encode();
    }

    public static String publishJson(String str) {
        return publishJson(str, null);
    }

    public static String publishJson(String str, Map<String, String> map) {
        FrameSpec frameSpecSwap = swap(parse(str), map);
        ArrayList arrayList = new ArrayList();
        for (int i = 0; i < frameSpecSwap.layers.size(); i++) {
            Layer layerBuild = frameSpecSwap.layers.get(i);
            if (shareable(layerBuild.src)) {
                if (!shareable(layerBuild.corner)) {
                    layerBuild = new Builder(layerBuild).corner("").build();
                }
                arrayList.add(layerBuild);
            }
        }
        return of(arrayList).encode();
    }

    public static FrameSpec swap(FrameSpec frameSpec, Map<String, String> map) {
        if (frameSpec == null) {
            return EMPTY;
        }
        if (frameSpec.isEmpty() || map == null || map.isEmpty()) {
            return frameSpec;
        }
        ArrayList arrayList = new ArrayList();
        for (int i = 0; i < frameSpec.layers.size(); i++) {
            Layer layerBuild = frameSpec.layers.get(i);
            String strPick = pick(map, layerBuild.src);
            String strPick2 = pick(map, layerBuild.corner);
            if (!strPick.equals(layerBuild.src) || !strPick2.equals(layerBuild.corner)) {
                layerBuild = new Builder(layerBuild).src(strPick).corner(strPick2).build();
            }
            arrayList.add(layerBuild);
        }
        return of(arrayList);
    }

    private static String pick(Map<String, String> map, String str) {
        String str2 = map.get(str);
        return (str2 == null || str2.length() == 0) ? str : str2;
    }

    public static boolean shareable(String str) {
        if (str == null) {
            return false;
        }
        if (FrameRuntime.isBlank(str) || FrameRuntime.isAvatar(str)) {
            return true;
        }
        String lowerCase = str.trim().toLowerCase(Locale.US);
        return lowerCase.matches("asset:[a-f0-9]{64}\\.(png|jpg|gif|webp)");
    }

    public long signature() {
        Long l = this.cachedSignature;
        if (l != null) {
            return l.longValue();
        }
        long j = 17;
        for (int i = 0; i < this.layers.size(); i++) {
            Layer layer = this.layers.get(i);
            long jHashCode = (((((((((((((((((((((((((((((((((((((((((((((((((((((((((((j * 31) + layer.src.hashCode()) * 31) + layer.corner.hashCode()) * 31) + layer.mode) * 31) + Math.round(layer.at * 10000.0f)) * 31) + layer.offset) * 31) + layer.width) * 31) + layer.scale) * 31) + layer.repeat) * 31) + layer.spin) * 31) + layer.tint) * 31) + (layer.animated ? 1L : 0L)) * 31) + (layer.round ? 1L : 0L)) * 31) + (layer.seamless ? 1L : 0L)) * 31) + Math.round(layer.x * 10000.0f)) * 31) + Math.round(layer.y * 10000.0f)) * 31) + layer.turn) * 31) + layer.spread) * 31) + layer.speed) * 31) + layer.course) * 31) + layer.field) * 31) + layer.flow) * 31) + layer.scatter) * 31) + layer.swirl) * 31) + layer.chaos) * 31) + layer.gravity) * 31) + layer.twist) * 31) + layer.twinkle) * 31) + layer.glow) * 31) + layer.glowRadius) * 31) + layer.visible;
            for (int i2 = 0; i2 < layer.extras.size(); i2++) {
                Effect effect = layer.extras.get(i2);
                jHashCode = (jHashCode * 31) + effect.type;
                for (int i3 = 0; i3 < layer.extras.get(i2).knobs.length; i3++) {
                    jHashCode = (jHashCode * 31) + effect.knobs[i3];
                }
            }
            for (int i4 = 0; i4 < layer.alive.size(); i4++) {
                Alive alive = layer.alive.get(i4);
                jHashCode = (((((((((jHashCode * 31) + alive.field) * 31) + alive.span) * 31) + alive.speed) * 31) + alive.wave) * 31) + alive.phase;
            }
            if (layer.queue != null) {
                jHashCode = (((((((((((jHashCode * 31) + layer.queue.branch) * 31) + layer.queue.count) * 31) + layer.queue.speed) * 31) + layer.queue.wave) * 31) + layer.queue.phase) * 31) + (layer.queue.random ? 1L : 0L);
            }
            long jHashCode2 = (((jHashCode * 31) + (layer.bake ? 1L : 0L)) * 31) + (layer.bakeCompat ? 1L : 0L);
            for (int i5 = 0; i5 < layer.whens.size(); i5++) {
                jHashCode2 = (((jHashCode2 * 31) + layer.whens.get(i5).block.hashCode()) * 31) + (layer.whens.get(i5).on ? 1L : 0L);
            }
            j = (((jHashCode2 * 31) + layer.orbit) * 31) + (layer.off ? 1L : 0L);
        }
        this.cachedSignature = Long.valueOf(j);
        return j;
    }

    private static List<When> readWhens(JSONObject jSONObject) {
        JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("whens");
        ArrayList arrayList = new ArrayList();
        if (jSONArrayOptJSONArray == null) {
            String strOptString = jSONObject.optString("when", "");
            if (strOptString.length() == 0) {
                return null;
            }
            arrayList.add(new When(strOptString, jSONObject.optBoolean("when_on", false)));
            return arrayList;
        }
        for (int i = 0; i < jSONArrayOptJSONArray.length() && arrayList.size() < 3; i++) {
            JSONObject jSONObjectOptJSONObject = jSONArrayOptJSONArray.optJSONObject(i);
            String strOptString2 = jSONObjectOptJSONObject == null ? "" : jSONObjectOptJSONObject.optString("b", "");
            if (strOptString2.length() > 0) {
                arrayList.add(new When(strOptString2, jSONObjectOptJSONObject.optBoolean("on", false)));
            }
        }
        if (arrayList.isEmpty()) {
            return null;
        }
        return arrayList;
    }

    private static Queue readQueue(JSONObject jSONObject) {
        int iOptInt;
        JSONObject jSONObjectOptJSONObject = jSONObject.optJSONObject("queue");
        if (jSONObjectOptJSONObject != null && (iOptInt = jSONObjectOptJSONObject.optInt("n", 0)) > 1) {
            return new Queue(jSONObjectOptJSONObject.optInt("i", 0), iOptInt, jSONObjectOptJSONObject.optInt("v", 0), jSONObjectOptJSONObject.optInt("w", 0), jSONObjectOptJSONObject.optInt("p", 0), jSONObjectOptJSONObject.optBoolean("r", false));
        }
        return null;
    }

    private static List<Alive> readAlive(JSONObject jSONObject) {
        int iOptInt;
        JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("live");
        if (jSONArrayOptJSONArray == null || jSONArrayOptJSONArray.length() == 0) {
            return null;
        }
        ArrayList arrayList = new ArrayList();
        for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
            JSONObject jSONObjectOptJSONObject = jSONArrayOptJSONArray.optJSONObject(i);
            if (jSONObjectOptJSONObject != null && (iOptInt = jSONObjectOptJSONObject.optInt("f", 0)) > 0 && iOptInt <= 23) {
                arrayList.add(new Alive(iOptInt, Style.clamp(jSONObjectOptJSONObject.optInt("s", 0), -4000, LIVE_SPAN_LIMIT), jSONObjectOptJSONObject.optInt("v", 60), jSONObjectOptJSONObject.optInt("w", 0), jSONObjectOptJSONObject.optInt("p", 0)));
            }
        }
        if (arrayList.isEmpty()) {
            return null;
        }
        return arrayList;
    }

    private static List<Effect> readEffects(JSONObject jSONObject) {
        JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("fx");
        if (jSONArrayOptJSONArray == null || jSONArrayOptJSONArray.length() == 0) {
            return null;
        }
        ArrayList arrayList = new ArrayList();
        for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
            JSONObject jSONObjectOptJSONObject = jSONArrayOptJSONArray.optJSONObject(i);
            if (jSONObjectOptJSONObject != null) {
                JSONArray jSONArrayOptJSONArray2 = jSONObjectOptJSONObject.optJSONArray("k");
                int length = jSONArrayOptJSONArray2 == null ? 0 : jSONArrayOptJSONArray2.length();
                int[] iArr = new int[length];
                for (int i2 = 0; i2 < length; i2++) {
                    iArr[i2] = jSONArrayOptJSONArray2.optInt(i2, 0);
                }
                arrayList.add(new Effect(jSONObjectOptJSONObject.optInt("t", 0), iArr));
            }
        }
        if (arrayList.isEmpty()) {
            return null;
        }
        return arrayList;
    }

    private static Layer read(JSONObject jSONObject) {
        int iOptInt;
        String strTrim = jSONObject.optString("src", "").trim();
        if (strTrim.length() != 0 && (iOptInt = jSONObject.optInt("mode", 0)) >= 0 && iOptInt <= 4) {
            return new Builder().src(strTrim).mode(iOptInt).corner(jSONObject.optString("corner", "")).at((float) jSONObject.optDouble("at", 0.0d)).offset(jSONObject.optInt("offset", 0)).width(jSONObject.optInt("width", 24)).scale(jSONObject.optInt("scale", 100)).repeat(jSONObject.optInt("repeat", 12)).spin(jSONObject.optInt("spin", 0)).tint(Style.color(jSONObject.optString("tint", ""), 0)).animated(jSONObject.optBoolean("animated", false)).round(jSONObject.optBoolean("round", false)).seamless(jSONObject.optBoolean("seamless", false)).x((float) jSONObject.optDouble("x", 0.5d)).y((float) jSONObject.optDouble("y", 0.5d)).turn(jSONObject.optInt("turn", 0)).spread(jSONObject.optInt("spread", 90)).speed(jSONObject.optInt("speed", 100)).course(jSONObject.optInt("course", 0)).field(jSONObject.optInt("field", 40)).flow(jSONObject.optInt("flow", 0)).scatter(jSONObject.optInt("scatter", 0)).swirl(jSONObject.optInt("swirl", 0)).chaos(jSONObject.optInt("chaos", 0)).gravity(jSONObject.optInt("gravity", 0)).twist(jSONObject.optInt("twist", 0)).twinkle(jSONObject.optInt("twinkle", 0)).orbit(jSONObject.optInt("orbit", 0)).glow(jSONObject.optInt("glow", 0)).glowRadius(jSONObject.optInt("glowRadius", 50)).extras(readEffects(jSONObject)).visible(jSONObject.optInt("vis", 100)).alive(readAlive(jSONObject)).queue(readQueue(jSONObject)).bake(jSONObject.optBoolean("bake", false)).bakeCompat(jSONObject.optBoolean("bake_compat", false)).whens(readWhens(jSONObject)).off(jSONObject.optBoolean("off", false)).build();
        }
        return null;
    }

    public static final class Effect {
        public final int[] knobs;
        public final int type;

        public Effect(int i, int[] iArr) {
            this.type = i;
            this.knobs = iArr == null ? new int[0] : iArr;
        }
    }

    public static final class When {
        public final String block;
        public final boolean on;

        public When(String str, boolean z) {
            this.block = str == null ? "" : str;
            this.on = z;
        }
    }

    public static final class Alive {
        public final int field;
        public final int phase;
        public final int span;
        public final int speed;
        public final int wave;

        public Alive(int i, int i2, int i3, int i4, int i5) {
            this.field = i;
            this.span = i2;
            this.speed = Style.clamp(i3, 1, FrameSpec.LIVE_SPEED_MAX);
            this.wave = (i4 < 0 || i4 > 3) ? 0 : i4;
            this.phase = Style.clamp(i5, 0, 100);
        }
    }

    public static final class Queue {
        public final int branch;
        public final int count;
        public final int phase;
        public final boolean random;
        public final int speed;
        public final int wave;

        public Queue(int i, int i2, int i3, int i4, int i5, boolean z) {
            int iClamp = Style.clamp(i2, 1, 32);
            this.count = iClamp;
            this.branch = Style.clamp(i, 0, iClamp - 1);
            this.speed = Style.clamp(i3, 0, FrameSpec.LIVE_SPEED_MAX);
            this.wave = (i4 < 0 || i4 > 3) ? 0 : i4;
            this.phase = Style.clamp(i5, 0, 100);
            this.random = z;
        }
    }

    public static final class Layer {
        public final List<Alive> alive;
        public final boolean animated;
        public final float at;
        public final boolean bake;
        public final boolean bakeCompat;
        public final int chaos;
        public final String corner;
        public final int course;
        public final List<Effect> extras;
        public final int field;
        public final int flow;
        public final int glow;
        public final int glowRadius;
        public final int gravity;
        public final int mode;
        public final boolean off;
        public final int offset;
        public final int orbit;
        public final Queue queue;
        public final int repeat;
        public final boolean round;
        public final int scale;
        public final int scatter;
        public final boolean seamless;
        public final int speed;
        public final int spin;
        public final int spread;
        public final String src;
        public final int swirl;
        public final int tint;
        public final int turn;
        public final int twinkle;
        public final int twist;
        public final int visible;
        public final List<When> whens;
        public final int width;
        public final float x;
        public final float y;

        Layer(String str, String str2, int i, float f, int i2, int i3, int i4, int i5, int i6, int i7, boolean z, boolean z2, boolean z3, float f2, float f3, int i8, int i9, int i10, int i11, int i12, int i13, int i14, int i15, int i16, int i17, int i18, int i19, int i20, boolean z4, int i21, int i22, int i23, List<Effect> list, List<Alive> list2, Queue queue, boolean z5, boolean z6, List<When> list3) {
            List<Effect> listUnmodifiableList;
            List<Alive> listUnmodifiableList2;
            List<When> listUnmodifiableList3;
            this.src = str;
            this.corner = str2;
            this.mode = i;
            this.at = f;
            this.offset = i2;
            this.width = i3;
            this.scale = i4;
            this.repeat = i5;
            this.spin = i6;
            this.tint = i7;
            this.animated = z;
            this.round = z2;
            this.seamless = z3;
            this.x = f2;
            this.y = f3;
            this.turn = i8;
            this.spread = i9;
            this.speed = i10;
            this.course = i11;
            this.field = i12;
            this.flow = i13;
            this.scatter = i14;
            this.swirl = i15;
            this.chaos = i16;
            this.gravity = i17;
            this.twist = i18;
            this.twinkle = i19;
            this.orbit = i20;
            this.off = z4;
            this.glow = i21;
            this.glowRadius = i22;
            if (list == null) {
                listUnmodifiableList = Collections.EMPTY_LIST;
            } else {
                listUnmodifiableList = Collections.unmodifiableList(new ArrayList(list));
            }
            this.extras = listUnmodifiableList;
            if (list2 == null) {
                listUnmodifiableList2 = Collections.EMPTY_LIST;
            } else {
                listUnmodifiableList2 = Collections.unmodifiableList(new ArrayList(list2));
            }
            this.alive = listUnmodifiableList2;
            this.queue = queue;
            this.visible = Style.clamp(i23, 0, 100);
            this.bake = z5;
            this.bakeCompat = z6;
            if (list3 == null) {
                listUnmodifiableList3 = Collections.EMPTY_LIST;
            } else {
                listUnmodifiableList3 = Collections.unmodifiableList(new ArrayList(list3));
            }
            this.whens = listUnmodifiableList3;
        }

        JSONObject write() throws Exception {
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("src", this.src);
            int i = this.mode;
            if (i != 0) {
                jSONObject.put("mode", i);
            }
            if (this.corner.length() > 0) {
                jSONObject.put("corner", this.corner);
            }
            float f = this.at;
            if (f != 0.0f) {
                jSONObject.put("at", f);
            }
            int i2 = this.offset;
            if (i2 != 0) {
                jSONObject.put("offset", i2);
            }
            jSONObject.put("width", this.width);
            if (this.mode != 0) {
                jSONObject.put("scale", this.scale);
            }
            if (this.mode == 1) {
                jSONObject.put("repeat", this.repeat);
            }
            int i3 = this.orbit;
            if (i3 != 0) {
                jSONObject.put("orbit", i3);
            }
            int i4 = this.spin;
            if (i4 != 0) {
                jSONObject.put("spin", i4);
            }
            int i5 = this.tint;
            if (i5 != 0) {
                jSONObject.put("tint", Style.hex(i5));
            }
            if (this.animated) {
                jSONObject.put("animated", true);
            }
            if (this.round) {
                jSONObject.put("round", true);
            }
            if (this.seamless) {
                jSONObject.put("seamless", true);
            }
            float f2 = this.x;
            if (f2 != 0.5f || this.y != 0.5f) {
                jSONObject.put("x", f2);
                jSONObject.put("y", this.y);
            }
            int i6 = this.turn;
            if (i6 != 0) {
                jSONObject.put("turn", i6);
            }
            if (this.mode == 4) {
                jSONObject.put("repeat", this.repeat);
                jSONObject.put("spread", this.spread);
                jSONObject.put("speed", this.speed);
                jSONObject.put("field", this.field);
                int i7 = this.course;
                if (i7 != 0) {
                    jSONObject.put("course", i7);
                }
                jSONObject.put("flow", this.flow);
                jSONObject.put("scatter", this.scatter);
                jSONObject.put("swirl", this.swirl);
                jSONObject.put("chaos", this.chaos);
                jSONObject.put("gravity", this.gravity);
                jSONObject.put("twist", this.twist);
                jSONObject.put("twinkle", this.twinkle);
            }
            int i8 = this.glow;
            if (i8 != 0) {
                jSONObject.put("glow", i8);
                jSONObject.put("glowRadius", this.glowRadius);
            }
            if (!this.extras.isEmpty()) {
                JSONArray jSONArray = new JSONArray();
                for (Effect effect : this.extras) {
                    JSONObject jSONObject2 = new JSONObject();
                    jSONObject2.put("t", effect.type);
                    JSONArray jSONArray2 = new JSONArray();
                    for (int i9 : effect.knobs) {
                        jSONArray2.put(i9);
                    }
                    jSONObject2.put("k", jSONArray2);
                    jSONArray.put(jSONObject2);
                }
                jSONObject.put("fx", jSONArray);
            }
            if (!this.alive.isEmpty()) {
                JSONArray jSONArray3 = new JSONArray();
                for (Alive alive : this.alive) {
                    JSONObject jSONObject3 = new JSONObject();
                    jSONObject3.put("f", alive.field);
                    jSONObject3.put("s", alive.span);
                    jSONObject3.put("v", alive.speed);
                    if (alive.wave != 0) {
                        jSONObject3.put("w", alive.wave);
                    }
                    if (alive.phase != 0) {
                        jSONObject3.put("p", alive.phase);
                    }
                    jSONArray3.put(jSONObject3);
                }
                jSONObject.put("live", jSONArray3);
            }
            int i10 = this.visible;
            if (i10 != 100) {
                jSONObject.put("vis", i10);
            }
            if (this.queue != null) {
                JSONObject jSONObject4 = new JSONObject();
                jSONObject4.put("i", this.queue.branch);
                jSONObject4.put("n", this.queue.count);
                jSONObject4.put("v", this.queue.speed);
                if (this.queue.wave != 0) {
                    jSONObject4.put("w", this.queue.wave);
                }
                if (this.queue.phase != 0) {
                    jSONObject4.put("p", this.queue.phase);
                }
                if (this.queue.random) {
                    jSONObject4.put("r", true);
                }
                jSONObject.put("queue", jSONObject4);
            }
            if (this.off) {
                jSONObject.put("off", true);
            }
            if (this.bake) {
                jSONObject.put("bake", true);
            }
            if (!this.whens.isEmpty()) {
                jSONObject.put("when", this.whens.get(0).block);
                jSONObject.put("when_on", this.whens.get(0).on);
                if (this.whens.size() > 1) {
                    JSONArray jSONArray4 = new JSONArray();
                    for (When when : this.whens) {
                        JSONObject jSONObject5 = new JSONObject();
                        jSONObject5.put("b", when.block);
                        jSONObject5.put("on", when.on);
                        jSONArray4.put(jSONObject5);
                    }
                    jSONObject.put("whens", jSONArray4);
                }
            }
            if (this.bakeCompat) {
                jSONObject.put("bake_compat", true);
            }
            return jSONObject;
        }
    }

    public static final class Builder {
        private List<Alive> alive;
        private boolean animated;
        private float at;
        private boolean bake;
        private boolean bakeCompat;
        private int chaos;
        private String corner;
        private int course;
        private List<Effect> extras;
        private int field;
        private int flow;
        private int glow;
        private int glowRadius;
        private int gravity;
        private int mode;
        private boolean off;
        private int offset;
        private int orbit;
        private Queue queue;
        private int repeat;
        private boolean round;
        private int scale;
        private int scatter;
        private boolean seamless;
        private int speed;
        private int spin;
        private int spread;
        private String src;
        private int swirl;
        private int tint;
        private int turn;
        private int twinkle;
        private int twist;
        private int visible;
        private List<When> whens;
        private int width;
        private float x;
        private float y;

        public Builder() {
            this.src = "";
            this.corner = "";
            this.mode = 0;
            this.width = 24;
            this.scale = 100;
            this.repeat = 12;
            this.tint = 0;
            this.x = 0.5f;
            this.y = 0.5f;
            this.spread = 90;
            this.speed = 100;
            this.field = 40;
            this.flow = 0;
            this.glowRadius = 50;
            this.visible = 100;
        }

        public Builder(Layer layer) {
            this.src = "";
            this.corner = "";
            this.mode = 0;
            this.width = 24;
            this.scale = 100;
            this.repeat = 12;
            this.tint = 0;
            this.x = 0.5f;
            this.y = 0.5f;
            this.spread = 90;
            this.speed = 100;
            this.field = 40;
            this.flow = 0;
            this.glowRadius = 50;
            this.visible = 100;
            if (layer != null) {
                this.src = layer.src;
                this.corner = layer.corner;
                this.mode = layer.mode;
                this.at = layer.at;
                this.offset = layer.offset;
                this.width = layer.width;
                this.scale = layer.scale;
                this.repeat = layer.repeat;
                this.spin = layer.spin;
                this.tint = layer.tint;
                this.animated = layer.animated;
                this.round = layer.round;
                this.seamless = layer.seamless;
                this.x = layer.x;
                this.y = layer.y;
                this.turn = layer.turn;
                this.spread = layer.spread;
                this.speed = layer.speed;
                this.course = layer.course;
                this.field = layer.field;
                this.flow = layer.flow;
                this.scatter = layer.scatter;
                this.swirl = layer.swirl;
                this.chaos = layer.chaos;
                this.gravity = layer.gravity;
                this.twist = layer.twist;
                this.twinkle = layer.twinkle;
                this.orbit = layer.orbit;
                this.off = layer.off;
                this.glow = layer.glow;
                this.glowRadius = layer.glowRadius;
                ArrayList arrayList = null;
                this.extras = (layer.extras == null || layer.extras.isEmpty()) ? null : new ArrayList(layer.extras);
                this.alive = (layer.alive == null || layer.alive.isEmpty()) ? null : new ArrayList(layer.alive);
                this.queue = layer.queue;
                this.visible = layer.visible;
                this.bake = layer.bake;
                if (layer.whens != null && !layer.whens.isEmpty()) {
                    arrayList = new ArrayList(layer.whens);
                }
                this.whens = arrayList;
            }
        }

        public Builder src(String str) {
            this.src = str == null ? "" : str.trim();
            return this;
        }

        public Builder corner(String str) {
            this.corner = str == null ? "" : str.trim();
            return this;
        }

        public Builder mode(int i) {
            if (i < 0 || i > 4) {
                i = 0;
            }
            this.mode = i;
            return this;
        }

        public Builder at(float f) {
            this.at = FrameContour.wrap(f);
            return this;
        }

        public Builder offset(int i) {
            this.offset = Style.clamp(i, -200, 200);
            return this;
        }

        public Builder width(int i) {
            this.width = Style.clamp(i, 1, 200);
            return this;
        }

        public Builder scale(int i) {
            this.scale = Style.clamp(i, 10, FrameSpec.SCALE_MAX);
            return this;
        }

        public Builder repeat(int i) {
            this.repeat = Style.clamp(i, 1, 64);
            return this;
        }

        public Builder spin(int i) {
            this.spin = Style.clamp(i, -180, 180);
            return this;
        }

        public Builder tint(int i) {
            this.tint = i;
            return this;
        }

        public Builder animated(boolean z) {
            this.animated = z;
            return this;
        }

        public Builder round(boolean z) {
            this.round = z;
            return this;
        }

        public Builder x(float f) {
            this.x = place(f);
            return this;
        }

        public Builder y(float f) {
            this.y = place(f);
            return this;
        }

        public Builder turn(int i) {
            this.turn = i % FrameSpec.TWIST_LIMIT;
            return this;
        }

        public Builder spread(int i) {
            this.spread = Style.clamp(i, 2, 400);
            return this;
        }

        public Builder speed(int i) {
            this.speed = Style.clamp(i, 10, 400);
            return this;
        }

        public Builder course(int i) {
            int i2 = i % FrameSpec.ORBIT_LIMIT;
            if (i2 < 0) {
                i2 += FrameSpec.ORBIT_LIMIT;
            }
            this.course = i2;
            return this;
        }

        public Builder field(int i) {
            this.field = Style.clamp(i, 0, 200);
            return this;
        }

        public Builder flow(int i) {
            if (i < 0 || i > 2) {
                i = 0;
            }
            this.flow = i;
            return this;
        }

        public Builder scatter(int i) {
            this.scatter = Style.clamp(i, 0, 180);
            return this;
        }

        public Builder swirl(int i) {
            this.swirl = Style.clamp(i, 0, 100);
            return this;
        }

        public Builder chaos(int i) {
            this.chaos = Style.clamp(i, 0, 100);
            return this;
        }

        public Builder gravity(int i) {
            this.gravity = Style.clamp(i, -100, 100);
            return this;
        }

        public Builder orbit(int i) {
            this.orbit = i % FrameSpec.TWIST_LIMIT;
            return this;
        }

        public Builder twist(int i) {
            this.twist = Style.clamp(i, -720, FrameSpec.TWIST_LIMIT);
            return this;
        }

        public Builder twinkle(int i) {
            this.twinkle = Style.clamp(i, 0, 100);
            return this;
        }

        public Builder off(boolean z) {
            this.off = z;
            return this;
        }

        public Builder glow(int i) {
            this.glow = Math.max(0, i);
            return this;
        }

        public Builder glowRadius(int i) {
            this.glowRadius = Style.clamp(i, 0, 100);
            return this;
        }

        public Builder visible(int i) {
            this.visible = Style.clamp(i, 0, 100);
            return this;
        }

        public Builder queue(Queue queue) {
            this.queue = queue;
            return this;
        }

        public Builder alive(List<Alive> list) {
            this.alive = (list == null || list.isEmpty()) ? null : new ArrayList(list);
            return this;
        }

        public Builder alive(Alive alive) {
            if (alive != null && alive.field > 0 && alive.field <= 23) {
                if (this.alive == null) {
                    this.alive = new ArrayList();
                }
                for (int size = this.alive.size() - 1; size >= 0; size--) {
                    if (this.alive.get(size).field == alive.field) {
                        this.alive.remove(size);
                    }
                }
                this.alive.add(alive);
            }
            return this;
        }

        public Builder extras(List<Effect> list) {
            this.extras = (list == null || list.isEmpty()) ? null : new ArrayList(list);
            return this;
        }

        public Builder addExtra(int i, int[] iArr) {
            if (this.extras == null) {
                this.extras = new ArrayList();
            }
            this.extras.add(new Effect(i, iArr));
            return this;
        }

        public Builder bake(boolean z) {
            this.bake = z;
            return this;
        }

        public Builder whens(List<When> list) {
            this.whens = (list == null || list.isEmpty()) ? null : new ArrayList(list);
            return this;
        }

        public Builder when(String str, boolean z) {
            if (str != null && str.length() != 0) {
                if (this.whens == null) {
                    this.whens = new ArrayList();
                }
                for (int size = this.whens.size() - 1; size >= 0; size--) {
                    if (this.whens.get(size).block.equals(str)) {
                        this.whens.remove(size);
                    }
                }
                if (this.whens.size() < 3) {
                    this.whens.add(new When(str, z));
                }
            }
            return this;
        }

        public Builder bakeCompat(boolean z) {
            this.bakeCompat = z;
            return this;
        }

        private static float place(float f) {
            if (Float.isNaN(f) || Float.isInfinite(f)) {
                return 0.5f;
            }
            return Style.clampF(f, -0.5f, 1.5f);
        }

        public Builder seamless(boolean z) {
            this.seamless = z;
            return this;
        }

        public Layer build() {
            return new Layer(this.src, this.corner, this.mode, this.at, this.offset, this.width, this.scale, this.repeat, this.spin, this.tint, this.animated, this.round, this.seamless, this.x, this.y, this.turn, this.spread, this.speed, this.course, this.field, this.flow, this.scatter, this.swirl, this.chaos, this.gravity, this.twist, this.twinkle, this.orbit, this.off, this.glow, this.glowRadius, this.visible, this.extras, this.alive, this.queue, this.bake, this.bakeCompat, this.whens);
        }
    }
}
