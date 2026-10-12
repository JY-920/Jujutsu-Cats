package dev.jujutsuvfx.core;

/** Per-entry quota. Pending blades reserve slots until their actual sweep either contacts or expires. */
public final class CutCadence {
    public static final int MIN_CONTACTS=5,WINDOW_TICKS=100;
    private final long entered;
    private final int quota,interval;
    private long next;
    private int contacts,reserved;

    public CutCadence(long now){this(now,MIN_CONTACTS,20);}
    public CutCadence(long now,int quota,int interval){entered=now;next=now;this.quota=quota;this.interval=interval;}
    private long deadline(long domainEnd){return Math.min(entered+WINDOW_TICKS,domainEnd);}
    public boolean urgent(long now,long domainEnd){return contacts<quota&&deadline(domainEnd)-now<=6;}
    public int demand(long now,long domainEnd){
        if(now>=domainEnd)return 0;
        if(contacts>=quota)return now>=next&&reserved==0?1:0;
        int missing=Math.max(0,quota-contacts-reserved);
        if(missing==0||now<next&&!urgent(now,domainEnd))return 0;
        return urgent(now,domainEnd)?missing:Math.min(missing,2);
    }
    public void scheduled(int count,long now,long domainEnd){
        reserved+=count;
        int missing=Math.max(0,quota-contacts-reserved);
        long available=deadline(domainEnd)-now-SlashBlade.LEAD_TICKS-SlashBlade.STRIKE_TICKS-1;
        next=now+(missing>0?Math.max(1L,Math.min((long)interval,available/(missing+1))):(long)interval);
    }
    public void contacted(){contacts=Math.min(quota,contacts+1);}
    public void resolved(){reserved=Math.max(0,reserved-1);}
}
