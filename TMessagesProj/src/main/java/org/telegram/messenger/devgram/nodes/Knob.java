package org.telegram.messenger.devgram.nodes;
public final class Knob {
    public final String name;public final int kind,min,max,start;
    public Knob(String name,int kind,int min,int max,int start){this.name=name;this.kind=kind;this.min=min;this.max=max;this.start=start;}
}
