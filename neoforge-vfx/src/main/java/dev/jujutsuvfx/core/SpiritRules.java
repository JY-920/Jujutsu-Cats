package dev.jujutsuvfx.core;

/** Fixed vanilla appearance allowlist: geometry only, never the source mob's AI or loot. */
public final class SpiritRules {
    public static final int ASSAULT=0, AIR=1, GUARD=2, LIMIT=2;
    public static final String[] SKINS={"wolf","spider","ravager","phantom","bat","bee","iron_golem","polar_bear"};
    private static final int[][] POOLS={{0,1,2},{3,4,5},{6,7,2}};
    public record Spec(int skin,int role,int palette,float height) {}
    public static final float MAX_HEALTH=30;
    public static float loadedHealth(float value){return Float.isFinite(value)?Math.max(0,Math.min(MAX_HEALTH,value)):MAX_HEALTH;}
    private SpiritRules() {}
    public static int bounded(int value,int max){return Math.max(0,Math.min(max,value));}
    public static float unit(float value){return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    public static Spec choose(int role,int choice,int palette,float size){
        role=bounded(role,2);int[] pool=POOLS[role];
        return new Spec(pool[Math.floorMod(choice,pool.length)],role,Math.floorMod(palette,3),
                (role==GUARD?2f:role==AIR?.65f:.85f)+unit(size)*(role==GUARD?1f:role==AIR?.60f:.95f));
    }
    public static float scale(float height,float baseHeight){return Math.max(.35f,Math.min(3.5f,unitHeight(height)/Math.max(.25f,baseHeight)));}
    private static float unitHeight(float h){return Float.isFinite(h)?Math.max(.5f,Math.min(3,h)):1;}
    public static boolean milestone(float health,float max,int bit,int done){return max>0&&health>0&&Float.isFinite(health)&&health<=max*(bit==1?.5f:.1f)&&(done&bit)==0;}
    public static final int PRESSURE_PERIOD=96;
    public static int attackRole(int roll){return Math.floorMod(roll,2)==0?ASSAULT:AIR;}
    public static boolean strikingWindow(int tick,int offset){return Math.floorMod(tick+offset,PRESSURE_PERIOD)<24;}
    /** Replace hue using source luminance; multiplying RGB cannot recolor green emissive eyes. */
    public static int colorizeAbgr(int pixel,int palette){
        int r=pixel&255,g=pixel>>8&255,b=pixel>>16&255,c=tint(palette);
        float l=(r*.2126f+g*.7152f+b*.0722f)/255f;
        float shade=.16f+.84f*l;
        return (pixel&0xff000000)|((int)((c&255)*shade)<<16)|((int)((c>>8&255)*shade)<<8)|(int)((c>>16&255)*shade);
    }
    public static int tint(int palette){return switch(bounded(palette,2)){case 1->0x52C6B1;case 2->0xD3B6F4;default->0xA65FE3;};}
}
