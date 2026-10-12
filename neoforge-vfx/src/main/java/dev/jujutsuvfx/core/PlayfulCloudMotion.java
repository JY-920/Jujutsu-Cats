package dev.jujutsuvfx.core;

/** Loader-independent combo timing and continuous, articulated weapon choreography. */
public final class PlayfulCloudMotion {
    public static final float ROD_LENGTH = .94f, GRIP = .25f, CHAIN_LENGTH = .235f;
    public static final int COMBO_WINDOW = 25, MIN_SWING_TICKS = 7, CHARGE_TICKS = 16;
    public static final int HEAVY_HIT_TICK = 4, HEAVY_RECOVERY = 20;
    private PlayfulCloudMotion() {}

    /** Owned by one actor, never by an ItemStack or by the shared item singleton. */
    public static final class Combo {
        private long started = Long.MIN_VALUE;
        private int step = -1;
        private boolean hit;
        public boolean begin(long tick) {
            if (started != Long.MIN_VALUE && tick >= started && tick - started < MIN_SWING_TICKS) return false;
            step = started != Long.MIN_VALUE && tick >= started && tick - started <= COMBO_WINDOW
                    && step < 3 ? (step + 1) % 3 : 0;
            started = tick; hit = false;
            return true;
        }
        public void heavy(long tick) { started = tick; step = 3; hit = false; }
        public boolean hitOnce() { if (hit) return false; hit = true; return true; }
        public int step() { return Math.max(0, step); }
        public long started() { return started; }
    }

    // Angles are deliberately unwrapped: the heavy strike must rotate through, not take
    // the shortest Euler path and reverse direction halfway through its swing.
    public record Pose(float x, float y, float z, float pitch, float yaw, float roll,
                       float bend1, float twist1, float bend2, float twist2, float body) {
        public Pose blend(Pose b, float t) {
            t = clamp(t);
            return new Pose(mix(x,b.x,t),mix(y,b.y,t),mix(z,b.z,t),mix(pitch,b.pitch,t),
                    mix(yaw,b.yaw,t),mix(roll,b.roll,t),mix(bend1,b.bend1,t),
                    mix(twist1,b.twist1,t),mix(bend2,b.bend2,t),mix(twist2,b.twist2,t),mix(body,b.body,t));
        }
    }
    private record Key(float t, Pose pose) {}
    public static final Pose IDLE = p(.47f,-.36f,-.70f,-67,-10,-12,151,-13,-148,18,0);
    public static final Pose CHARGE = p(.48f,-.04f,-.63f,-28,23,-38,112,24,-96,-25,-24);
    private static final Key[][] TRACKS = {
            {k(0,IDLE),k(.13f,p(.58f,-.24f,-.65f,-48,26,-42,148,-18,-133,27,-18)),
             k(.32f,p(-.13f,-.17f,-.99f,-86,-36,46,18,-30,-12,15,26)),
             k(.53f,p(-.23f,-.28f,-.92f,-79,-43,62,-18,-12,28,8,32)),k(1,IDLE)},
            {k(0,IDLE),k(.14f,p(.01f,-.25f,-.69f,-58,-28,48,125,22,-142,-18,22)),
             k(.33f,p(.63f,-.20f,-.98f,-82,31,-51,-14,16,12,-14,-29)),
             k(.53f,p(.68f,-.34f,-.87f,-72,40,-68,25,10,-24,-12,-35)),k(1,IDLE)},
            {k(0,IDLE),k(.21f,p(.39f,.13f,-.60f,-14,2,15,124,-26,-104,24,-8)),
             k(.40f,p(.25f,-.57f,-1.04f,-113,-4,5,5,-4,8,3,10)),
             k(.59f,p(.28f,-.63f,-.95f,-119,-6,3,-20,-12,24,-7,12)),k(1,IDLE)},
            {k(0,CHARGE),k(.13f,p(.64f,.03f,-.63f,-22,38,-72,64,28,-38,-28,-28)),
             k(.29f,p(.05f,-.39f,-1.08f,-100,-20,-195,3,12,-8,5,36)),
             k(.56f,p(.52f,-.48f,-.87f,-80,24,-320,65,-20,-50,12,-16)),
             k(1,p(IDLE.x,IDLE.y,IDLE.z,IDLE.pitch,IDLE.yaw,IDLE.roll-360,
                     IDLE.bend1,IDLE.twist1,IDLE.bend2,IDLE.twist2,0))}
    };
    public static int duration(int step) { return step == 3 ? 14 : 10; }
    public static Pose sample(int step, float ticks) {
        if (ticks < 0 || ticks >= duration(step)) return IDLE;
        float t = ticks / duration(step);
        Key[] track = TRACKS[Math.max(0, Math.min(3,step))];
        for (int i=1;i<track.length;i++) if (t <= track[i].t) {
            Key a=track[i-1],b=track[i];
            return a.pose.blend(b.pose,smooth((t-a.t)/(b.t-a.t)));
        }
        return IDLE;
    }
    public static Pose charge(float ticks) { return IDLE.blend(CHARGE,smooth(ticks/12)); }
    public static float trail(int step,float ticks) {
        float t=ticks/duration(step);
        return smooth((t-.16f)/.10f)*(1-smooth((t-.48f)/.19f));
    }
    public static float heavyPower(int ticks) { return .65f+.35f*clamp(ticks/(float)CHARGE_TICKS); }
    public static float clamp(float t) { return Math.max(0,Math.min(1,t)); }
    public static float smooth(float t) { t=clamp(t);return t*t*(3-2*t); }
    private static float mix(float a,float b,float t) { return a+(b-a)*t; }
    private static Key k(float t,Pose p) { return new Key(t,p); }
    private static Pose p(float x,float y,float z,float pitch,float yaw,float roll,float b1,float t1,float b2,float t2,float body) {
        return new Pose(x,y,z,pitch,yaw,roll,b1,t1,b2,t2,body);
    }
}
