package org.telegram.ui.Components.devgram.frame;

import org.telegram.ui.Components.devgram.frame.FrameNodeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native port of the user-supplied Custom Profile frame core; bounded data only. */
public final class FrameNodeDoc {
    public static final int CLONE_SHIFT = 60;
    private static final int LOOK = 3;
    private static final int LOOK_MIDDLE = 3;
    private static final int LOOK_SHIFT = 50;
    private static final float LOOK_SPREAD = 1.8857143f;
    private static final int LOOK_WIDE = 2;
    public static final int MAX_NODES = 512;
    public static final int MAX_TEXT = 524288;
    public static final int STEP_X = 250;
    public static final int STEP_Y = 150;
    private final List<Node> nodes;
    private final List<Wire> wires;
    private final Map<Integer,Node> index=new HashMap<>();
    private int indexedSize=-1;
    private Map<Integer,Integer> wiredPins;

    public static final class Node {
        public final int id;
        public boolean open;
        public final String[] texts;
        public final int type;
        public final int[] values;
        public int x;
        public int y;

        Node(int i, int i2, int i3, int i4, boolean z, int[] iArr, String[] strArr) {
            this.id = i;
            this.type = i2;
            this.x = i3;
            this.y = i4;
            this.open = z;
            this.values = iArr;
            this.texts = strArr;
        }

        public int value(int i) {
            if (i < 0) {
                return 0;
            }
            int[] iArr = this.values;
            if (i >= iArr.length) {
                return 0;
            }
            return iArr[i];
        }

        public String text(int i) {
            String str;
            if (i >= 0) {
                String[] strArr = this.texts;
                return (i >= strArr.length || (str = strArr[i]) == null) ? "" : str;
            }
            return "";
        }

        Node copy() {
            return new Node(this.id, this.type, this.x, this.y, this.open, (int[]) this.values.clone(), (String[]) this.texts.clone());
        }
    }

    public static final class Wire {
        public final int from;
        public final int out;
        public final int pin;
        public final int to;

        Wire(int i, int i2, int i3, int i4) {
            this.from = i;
            this.out = Math.max(0, i2);
            this.to = i3;
            this.pin = i4;
        }
    }

    private FrameNodeDoc(List<Node> list, List<Wire> list2) {
        this.nodes = list;
        this.wires = list2;
    }

    public static FrameNodeDoc empty() {
        return new FrameNodeDoc(new ArrayList(), new ArrayList());
    }

    public List<Node> nodes() {
        return this.nodes;
    }

    public List<Wire> wires() {
        return this.wires;
    }

    public int count() {
        return this.nodes.size();
    }

    public Node node(int i) {
        if(indexedSize!=nodes.size()){index.clear();for(Node node:nodes)index.put(node.id,node);indexedSize=nodes.size();}
        return index.get(i);
    }

    public int first(int i) {
        for (int i2 = 0; i2 < this.nodes.size(); i2++) {
            if (this.nodes.get(i2).type == i) {
                return this.nodes.get(i2).id;
            }
        }
        return 0;
    }

    public int input(int i, int i2) {
        for (int i3 = 0; i3 < this.wires.size(); i3++) {
            Wire wire = this.wires.get(i3);
            if (wire.to == i && wire.pin == i2) {
                return wire.from;
            }
        }
        return 0;
    }

    public int inputOut(int i, int i2) {
        for (int i3 = 0; i3 < this.wires.size(); i3++) {
            Wire wire = this.wires.get(i3);
            if (wire.to == i && wire.pin == i2) {
                return wire.out;
            }
        }
        return 0;
    }

    public int pins(int i) {
        Node node = node(i);
        FrameNodeType.Kind kindOf = node == null ? null : FrameNodeType.of(node.type);
        if (kindOf == null) {
            return 0;
        }
        if (!kindOf.many() || fixed(node)) {
            return kindOf.pins();
        }
        int iPins = kindOf.pins() - 2;
        if(wiredPins==null){wiredPins=new HashMap<>();for(Wire wire:wires)if(!FrameNodeType.isKnobPin(wire.pin))wiredPins.put(wire.to,Math.max(wiredPins.getOrDefault(wire.to,-1),wire.pin));}
        iPins=Math.max(iPins,wiredPins.getOrDefault(i,-1));
        return Math.min(iPins + 2, 100);
    }

    private static boolean fixed(Node node) {
        return node.type == 55 && node.value(1) == 0;
    }

    public int outs(int i) {
        Node node = node(i);
        FrameNodeType.Kind kindOf = node == null ? null : FrameNodeType.of(node.type);
        if (kindOf == null || kindOf.gives == 0) {
            return 0;
        }
        return node.type == 51 ? pins(i) : FrameNodeType.outCount(kindOf);
    }

    public int filled(int i) {
        int i2 = 0;
        for (int i3 = 0; i3 < this.wires.size(); i3++) {
            if (this.wires.get(i3).to == i) {
                i2++;
            }
        }
        return i2;
    }

    public int output(int i) {
        for (int i2 = 0; i2 < this.wires.size(); i2++) {
            if (this.wires.get(i2).from == i) {
                return this.wires.get(i2).to;
            }
        }
        return 0;
    }

    /* JADX WARN: Code restructure failed: missing block: B:24:0x005e, code lost:
    
        return false;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private boolean reaches(int from,int to) {
        java.util.HashSet<Integer> seen=new java.util.HashSet<>();
        java.util.ArrayDeque<Integer> pending=new java.util.ArrayDeque<>();pending.add(from);
        while(!pending.isEmpty()&&seen.size()<=MAX_NODES){int next=pending.removeFirst();if(next==to)return true;if(!seen.add(next))continue;for(Wire wire:wires)if(wire.from==next)pending.add(wire.to);}
        return false;
    }

    public int nextId() {
        int iMax = 0;
        for (int i = 0; i < this.nodes.size(); i++) {
            iMax = Math.max(iMax, this.nodes.get(i).id);
        }
        return iMax + 1;
    }

    public int add(int i, int i2, int i3) {
        FrameNodeType.Kind kindOf = FrameNodeType.of(i);
        if (kindOf == null || this.nodes.size() >= 512) {
            return 0;
        }
        int iKnobs = kindOf.knobs();
        int[] iArr = new int[iKnobs];
        String[] strArr = new String[kindOf.knobs()];
        for (int i4 = 0; i4 < iKnobs; i4++) {
            iArr[i4] = kindOf.knobs[i4].start;
            strArr[i4] = "";
        }
        int iNextId = nextId();
        this.nodes.add(new Node(iNextId, i, i2, i3, false, iArr, strArr));
        return iNextId;
    }

    public List<Integer> duplicate(List<Integer> list) {
        wiredPins=null;
        ArrayList arrayList = new ArrayList();
        if (list != null && !list.isEmpty()) {
            HashMap map = new HashMap();
            for (int i = 0; i < list.size(); i++) {
                Node node = node(list.get(i).intValue());
                if (node != null) {
                    int iAdd = add(node.type, node.x + 60, node.y + 60);
                    if (iAdd == 0) {
                        break;
                    }
                    Node node2 = node(iAdd);
                    System.arraycopy(node.values, 0, node2.values, 0, Math.min(node.values.length, node2.values.length));
                    System.arraycopy(node.texts, 0, node2.texts, 0, Math.min(node.texts.length, node2.texts.length));
                    node2.open = node.open;
                    map.put(Integer.valueOf(node.id), Integer.valueOf(iAdd));
                    arrayList.add(Integer.valueOf(iAdd));
                }
            }
            ArrayList arrayList2 = new ArrayList(this.wires);
            for (int i2 = 0; i2 < arrayList2.size(); i2++) {
                Wire wire = (Wire) arrayList2.get(i2);
                Integer num = (Integer) map.get(Integer.valueOf(wire.to));
                if (num != null) {
                    Integer num2 = (Integer) map.get(Integer.valueOf(wire.from));
                    if(wires.size()<8192)this.wires.add(new Wire(num2 == null ? wire.from : num2.intValue(), wire.out, num.intValue(), wire.pin));
                }
            }
        }
        return arrayList;
    }

    public void drop(int i) {
        indexedSize=-1;wiredPins=null;
        for (int size = this.nodes.size() - 1; size >= 0; size--) {
            if (this.nodes.get(size).id == i) {
                this.nodes.remove(size);
            }
        }
        for (int size2 = this.wires.size() - 1; size2 >= 0; size2--) {
            Wire wire = this.wires.get(size2);
            if (wire.from == i || wire.to == i) {
                this.wires.remove(size2);
            }
        }
    }

    public void move(int i, int i2, int i3) {
        Node node = node(i);
        if (node != null) {
            node.x = i2;
            node.y = i3;
        }
    }

    public void toggle(int i) {
        Node node = node(i);
        if (node != null) {
            node.open = !node.open;
        }
    }

    public void set(int i, int i2, int i3) {
        Node node = node(i);
        FrameNodeType.Kind kindOf = node == null ? null : FrameNodeType.of(node.type);
        if (kindOf == null || i2 < 0 || i2 >= node.values.length) {
            return;
        }
        node.values[i2] = kindOf.knobs[i2].clamp(i3);
    }

    public void set(int i, int i2, String str) {
        Node node = node(i);
        if (node == null || i2 < 0 || i2 >= node.texts.length) {
            return;
        }
        String[] strArr = node.texts;
        if (str == null) {
            str = "";
        }
        strArr[i2] = str;
    }

    public boolean link(int i, int i2, int i3) {
        return link(i, 0, i2, i3);
    }

    public boolean link(int i, int i2, int i3, int i4) {
        wiredPins=null;
        Node node = node(i);
        Node node2 = node(i3);
        if (node == null || node2 == null || i == i3 || i4 < 0 || i4 >= 200 || i2 < 0 || i2 >= outs(i) || !FrameNodeType.fits(node.type, i2, node2.type, i4) || reaches(i3, i)) {
            return false;
        }
        for (int size = this.wires.size() - 1; size >= 0; size--) {
            Wire wire = this.wires.get(size);
            if (wire.to == i3 && wire.pin == i4) {
                this.wires.remove(size);
            }
        }
        if (this.wires.size() >= 8192) return false;
        this.wires.add(new Wire(i, i2, i3, i4));
        return true;
    }

    public void cut(int i, int i2) {
        wiredPins=null;
        for (int size = this.wires.size() - 1; size >= 0; size--) {
            Wire wire = this.wires.get(size);
            if (wire.to == i && wire.pin == i2) {
                this.wires.remove(size);
            }
        }
    }

    public void swap(int i, int i2, int i3) {
        wiredPins=null;
        if (i2 == i3) {
            return;
        }
        for (int i4 = 0; i4 < this.wires.size(); i4++) {
            Wire wire = this.wires.get(i4);
            if (wire.to == i) {
                if (wire.pin == i2) {
                    this.wires.set(i4, new Wire(wire.from, wire.out, i, i3));
                } else if (wire.pin == i3) {
                    this.wires.set(i4, new Wire(wire.from, wire.out, i, i2));
                }
            }
        }
    }

    public FrameNodeDoc swap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return this;
        }
        FrameNodeDoc frameNodeDocCopy = copy();
        for (int i = 0; i < frameNodeDocCopy.nodes.size(); i++) {
            String[] strArr = frameNodeDocCopy.nodes.get(i).texts;
            for (int i2 = 0; i2 < strArr.length; i2++) {
                String str = strArr[i2];
                String str2 = str == null ? null : map.get(str);
                if (str2 != null) {
                    strArr[i2] = str2;
                }
            }
        }
        return frameNodeDocCopy;
    }

    public List<String> sources() {
        ArrayList arrayList = new ArrayList();
        for (int i = 0; i < this.nodes.size(); i++) {
            for (String str : this.nodes.get(i).texts) {
                if (str != null && str.length() > 0 && !FrameRuntime.isBlank(str) && !arrayList.contains(str) && looksLikeSource(str)) {
                    arrayList.add(str);
                }
            }
        }
        return arrayList;
    }

    private static boolean looksLikeSource(String str) {
        return str.startsWith("asset:");
    }

    public FrameNodeDoc copy() {
        ArrayList arrayList = new ArrayList();
        for (int i = 0; i < this.nodes.size(); i++) {
            arrayList.add(this.nodes.get(i).copy());
        }
        ArrayList arrayList2 = new ArrayList();
        for (int i2 = 0; i2 < this.wires.size(); i2++) {
            Wire wire = this.wires.get(i2);
            arrayList2.add(new Wire(wire.from, wire.out, wire.to, wire.pin));
        }
        return new FrameNodeDoc(arrayList, arrayList2);
    }

    public String encode() {
        try {
            JSONArray jSONArray = new JSONArray();
            for (int i = 0; i < this.nodes.size(); i++) {
                Node node = this.nodes.get(i);
                JSONObject jSONObject = new JSONObject();
                jSONObject.put("id", node.id);
                jSONObject.put("t", node.type);
                jSONObject.put("x", node.x);
                jSONObject.put("y", node.y);
                if (node.open) {
                    jSONObject.put("o", true);
                }
                JSONArray jSONArray2 = new JSONArray();
                for (int i2 = 0; i2 < node.values.length; i2++) {
                    jSONArray2.put(node.values[i2]);
                }
                jSONObject.put("v", jSONArray2);
                JSONArray jSONArray3 = new JSONArray();
                boolean z = false;
                for (int i3 = 0; i3 < node.texts.length; i3++) {
                    String str = node.texts[i3] == null ? "" : node.texts[i3];
                    jSONArray3.put(str);
                    z |= str.length() > 0;
                }
                if (z) {
                    jSONObject.put("s", jSONArray3);
                }
                jSONArray.put(jSONObject);
            }
            JSONArray jSONArray4 = new JSONArray();
            for (int i4 = 0; i4 < this.wires.size(); i4++) {
                Wire wire = this.wires.get(i4);
                JSONArray jSONArray5 = new JSONArray();
                jSONArray5.put(wire.from);
                jSONArray5.put(wire.to);
                jSONArray5.put(wire.pin);
                jSONArray5.put(wire.out);
                jSONArray4.put(jSONArray5);
            }
            JSONObject jSONObject2 = new JSONObject();
            jSONObject2.put("nodes", jSONArray);
            jSONObject2.put("look", 3);
            jSONObject2.put("wires", jSONArray4);
            JSONArray packages=FramePackageTypes.used(nodes);if(packages.length()>0)jSONObject2.put("catalog",packages);
            String string = jSONObject2.toString();
            return string.length() > 524288 ? "" : string;
        } catch (Throwable th) {
            
            return "";
        }
    }

    public static FrameNodeDoc parse(String source) {
        FrameNodeDoc result=empty();
        if(source==null||source.length()>MAX_TEXT)return result;
        try{
            JSONObject object=new JSONObject(source);FramePackageTypes.registerAll(object.optJSONArray("catalog"));JSONArray nodes=object.getJSONArray("nodes"),wires=object.optJSONArray("wires");
            if(nodes.length()>MAX_NODES||wires!=null&&wires.length()>8192)return empty();
            java.util.HashSet<Integer> ids=new java.util.HashSet<>();
            for(int i=0;i<nodes.length();i++){
                JSONObject item=nodes.getJSONObject(i);int id=item.getInt("id"),type=item.getInt("t");FrameNodeType.Kind kind=FrameNodeType.of(type);
                if(id<=0||id>65535||!ids.add(id)||kind==null)return empty();
                int[] values=new int[kind.knobs()];String[] texts=new String[kind.knobs()];JSONArray v=item.optJSONArray("v"),text=item.optJSONArray("s");
                if(v!=null&&v.length()>values.length||text!=null&&text.length()>texts.length)return empty();
                for(int k=0;k<values.length;k++){values[k]=kind.knobs[k].clamp(v==null?kind.knobs[k].start:v.optInt(k,kind.knobs[k].start));texts[k]=text==null?"":text.optString(k,"");if(texts[k].length()>1024)return empty();}
                result.nodes.add(new Node(id,type,Style.clamp(item.optInt("x"),-10000,10000),Style.clamp(item.optInt("y"),-10000,10000),item.optBoolean("o"),values,texts));
            }
            java.util.HashSet<String> destinations=new java.util.HashSet<>();
            java.util.HashMap<Integer,Integer> degree=new java.util.HashMap<>();
            java.util.HashMap<Integer,Integer> pinOutputs=new java.util.HashMap<>();
            java.util.HashMap<Integer,java.util.ArrayList<Integer>> downstream=new java.util.HashMap<>();
            for(Node n:result.nodes)degree.put(n.id,0);
            if(wires!=null)for(int i=0;i<wires.length();i++){JSONArray w=wires.getJSONArray(i);int to=w.getInt(1),pin=w.getInt(2);if(pin>=0&&pin<100)pinOutputs.put(to,Math.max(pinOutputs.getOrDefault(to,2),Math.min(100,pin+2)));}
            if(wires!=null)for(int i=0;i<wires.length();i++){
                JSONArray w=wires.getJSONArray(i);if(w.length()<3||w.length()>4)return empty();
                int from=w.getInt(0),to=w.getInt(1),pin=w.getInt(2),out=w.optInt(3);
                Node a=result.node(from),b=result.node(to);
                if(a==null||b==null||from==to||pin<0||pin>=200||out<0||out>=(a.type==51?pinOutputs.getOrDefault(from,2):result.outs(from))||!destinations.add(to+":"+pin)||!FrameNodeType.fits(a.type,out,b.type,pin))return empty();
                result.wires.add(new Wire(from,out,to,pin));degree.put(to,degree.get(to)+1);
                downstream.computeIfAbsent(from,id->new java.util.ArrayList<>()).add(to);
            }
            java.util.ArrayDeque<Integer> ready=new java.util.ArrayDeque<>();
            for(java.util.Map.Entry<Integer,Integer> item:degree.entrySet())if(item.getValue()==0)ready.add(item.getKey());
            int visited=0;while(!ready.isEmpty()){int id=ready.removeFirst();visited++;java.util.List<Integer> next=downstream.get(id);if(next!=null)for(int target:next){int d=degree.get(target)-1;degree.put(target,d);if(d==0)ready.add(target);}}
            if(visited!=result.nodes.size())return empty();
            return result;
        }catch(Exception error){return empty();}
    }
}
