package dev.jujutsuvfx.core;

import java.util.LinkedHashSet;
import java.util.Set;

/** A duel may take the observer camera once; subsequent server refreshes never undo a manual exit. */
public final class CameraAutoStartPolicy {
    private final Set<Long> seen=new LinkedHashSet<>();
    public boolean seen(long duel){return seen.contains(duel);}
    public boolean claim(long duel,boolean eligible){
        if(!eligible||seen.contains(duel))return false;
        suppress(duel);return true;
    }
    public void suppress(long duel){
        if(seen.size()>=512&&!seen.contains(duel))seen.remove(seen.iterator().next());
        seen.add(duel);
    }
    public void clear(){seen.clear();}
}
