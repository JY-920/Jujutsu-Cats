package dev.jujutsuvfx.core;

/** Fractional actor time. Multiple systems asking in one world tick share the same answer. */
public final class ActorTime {
    private long last=Long.MIN_VALUE;
    private float credit;
    private boolean due=true;
    public boolean step(long tick,boolean slow){
        if(!slow){last=tick;credit=0;return due=true;}
        if(tick==last)return due;
        last=tick;credit+=.25f;
        due=credit>=1;
        if(due)credit-=1;
        return due;
    }
}
