package dev.jujutsuvfx.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.config.VfxConfig;
import dev.jujutsuvfx.core.YutaGetoBeat;
import dev.jujutsuvfx.entity.GetoCat;
import dev.jujutsuvfx.entity.YutaCat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Rika / Geto finale in the Blue/Purple pipeline: the scene is captured, a half-resolution HDR
 * emission-absorption volume is marched against the scene depth, then one full-screen composite adds
 * refraction, lensing around the curse vortex, pink scene lighting, glow, grading and the flash.
 * Everything is derived from the server-owned screenplay tag; there are no client timers.
 */
public final class YutaGetoClashEffects {
    private static final SceneSnapshot SCENE = new SceneSnapshot();
    private static ShaderInstance volume, composite;
    private static TextureTarget energy;
    private static boolean floatEnergy;
    private YutaGetoClashEffects() {}

    public static void setVolume(ShaderInstance shader) { volume = shader; if (shader == null) release(); }
    public static void setComposite(ShaderInstance shader) { composite = shader; if (shader == null) release(); }
    /** The geometry pass keeps only crisp lightning and debris while this volume is available. */
    static boolean ready() { return volume != null && composite != null; }
    static void release() {
        SCENE.release();
        if (energy != null) { energy.destroyBuffers(); energy = null; }
    }

    /** Screenplay intensities for one frame; all values are bounded, smooth and monotonic within a phase. */
    record Look(float rikaCharge, float getoCharge, float rikaBeam, float getoBeam, float contact, float pressure,
                float burst, float aftermath, float flash, float grade, float time, float getoScale, float contactTone) {
        Look(float rikaCharge, float getoCharge, float rikaBeam, float getoBeam, float contact, float pressure,
             float burst, float aftermath, float flash, float grade, float time) {
            this(rikaCharge, getoCharge, rikaBeam, getoBeam, contact, pressure, burst, aftermath, flash, grade, time, 1, 0);
        }
        boolean visible() { return rikaCharge + getoCharge + rikaBeam + getoBeam + contact + aftermath > .002f || burst > 0 && burst < 1.05f; }
    }

    static Look look(int phase, float age) {
        if (!Float.isFinite(age) || age < 0) age = 0;
        float rc = 0, gc = 0, rb = 0, gb = 0, contact = 0, pressure = 0, burst = 0, after = 0, flash = 0, grade = 0, time;
        switch (phase) {
            case YutaGetoBeat.VOW -> {
                float g = smooth(age, 20, 70); rc = .45f * g; gc = .40f * g; grade = .45f * g; time = age;
            }
            case YutaGetoBeat.CHARGE -> {
                float g = smooth(age, 0, 100); rc = .45f + .55f * g; gc = .40f + .60f * g; grade = .45f + .3f * g; time = 70 + age;
            }
            case YutaGetoBeat.CLASH -> {
                float p = smooth(age, 0, 90), release = smooth(age, 0, 8);
                rc = gc = 1; rb = gb = release; contact = release * (.55f + .45f * p); pressure = p; grade = .75f;
                // Ignition flash when the two discharges first meet.
                flash = .55f * smooth(age, 4, 6) * (float) Math.exp(-Math.max(0, age - 6) / 3);
                time = 180 + age;
            }
            case YutaGetoBeat.BREAKTHROUGH -> {
                float collapse = 1 - smooth(age, 11, 32), end = 1 - smooth(age, 29, 40);
                rc = end; rb = end; gc = collapse; gb = collapse; contact = collapse; pressure = 1; grade = .75f * end;
                burst = age >= 11 ? Math.min(1, (age - 11) / 34f) : 0;
                flash = age >= 11 ? (float) Math.exp(-(age - 11) / 4) : 0;
                time = 270 + age;
            }
            case YutaGetoBeat.AFTERMATH -> {
                burst = Math.min(1.2f, (age + 29) / 34f); after = 1 - smooth(age, 0, 65); time = 310 + age;
            }
            default -> time = age;
        }
        return new Look(rc, gc, rb, gb, contact, pressure, burst, after, flash, grade, time / 20f);
    }

    /** Geto's own ranged vortex (blast or Maximum): curse orb, twisted discharge and a dark impact burst. */
    static Look vortex(float age, double length, double scale) {
        float fade = dev.jujutsuvfx.core.GetoVortexMotion.fade(age);
        int fire = dev.jujutsuvfx.core.GetoVortexMotion.FIRE;
        float release = dev.jujutsuvfx.core.GetoVortexMotion.phase(age, fire, fire + 8);
        float charge = dev.jujutsuvfx.core.GetoVortexMotion.radius(age) / 2.75f * (1 - .6f * release);
        double reach = dev.jujutsuvfx.core.GetoVortexMotion.reach(age, length);
        float beam = length > .05 ? (float) Math.min(1, reach / length) : 0;
        float impact = (float) (age - fire - length / dev.jujutsuvfx.core.GetoVortexMotion.SPEED);
        float contact = impact >= 0 ? smooth(impact, 0, 2) * (1 - smooth(impact, 4, 18)) : 0;
        return new Look(0, charge * fade, 0, beam * fade, contact * fade, .7f, 0, 0, 0, 0, age / 20f, (float) scale, 1);
    }

    static Look held(int kind,float age,double length){
        float charge=smooth(age,0,70),fade=smooth(age,0,10);
        float beam=length>.05?(float)Math.min(1,dev.jujutsuvfx.core.SustainedBeam.reach(age,length)/length):0;
        return new Look(kind==1?(.15f+.85f*charge)*fade:0,kind==0?(.15f+.85f*charge)*fade:0,
                kind==1?beam:0,kind==0?beam:0,0,.7f,0,0,0,0,age/20,1,kind==0?1:0);
    }
    static Look playerClash(float age){
        float fade=1-smooth(age,12,20),burst=age>=12?Math.min(1.1f,(age-12)/24):0;
        float flash=age>=12?(float)Math.exp(-(age-12)/3):.25f*(float)Math.exp(-age/3);
        // Beam uniforms encode front distance, not opacity. Never fade them back toward a caster.
        float beam=age<12?1:0;
        return new Look(fade,fade,beam,beam,fade,.7f,burst,0,flash,0,age/20+9,1,0);
    }
    private record Job(Look look, Vec3 rika, Vec3 geto, Vec3 middle, Vec3 burst, float visibility, int serial, double distance, java.util.List<Vec3> path) {
        Job(Look look,Vec3 rika,Vec3 geto,Vec3 middle,float visibility,int serial,double distance){this(look,rika,geto,middle,geto,visibility,serial,distance,java.util.List.of());}
        Job(Look look,Vec3 rika,Vec3 geto,Vec3 middle,Vec3 burst,float visibility,int serial,double distance){this(look,rika,geto,middle,burst,visibility,serial,distance,java.util.List.of());}
    }

    public static void render(RenderLevelStageEvent event) {
        if (!ready()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        java.util.List<Job> jobs = new java.util.ArrayList<>();
        CompoundTag best = null; double nearest = 192 * 192;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof YutaCat cat) || !cat.isAlive()) continue;
            CompoundTag state = cat.duelState();
            if (!YutaGetoFinaleRenderer.active(state)) continue;
            // Geto dies at the breakthrough; the aftermath keeps playing from the published anchors.
            boolean peer = mc.level.getEntity(state.getInt("Peer")) instanceof GetoCat geto && geto.isAlive();
            if (!peer && state.getInt("Phase") < YutaGetoBeat.AFTERMATH) continue;
            double distance = camera.distanceToSqr(YutaGetoFinaleRenderer.anchor(state, "M"));
            if (distance < nearest) { nearest = distance; best = state; }
        }
        if (best != null) {
            int phase = best.getInt("Phase");
            float age = mc.level.getGameTime() - best.getLong("Start") + partial;
            Look look = look(phase, age);
            Vec3 rika = best.contains("LX") ? YutaGetoFinaleRenderer.anchor(best, "L") : YutaGetoFinaleRenderer.anchor(best, "R").add(0, 2.25, 0);
            Vec3 geto = best.contains("EX") ? YutaGetoFinaleRenderer.anchor(best, "E") : YutaGetoFinaleRenderer.anchor(best, "G").add(0, 2.2, 0);
            Vec3 middle = YutaGetoFinaleRenderer.anchor(best, "M");
            float visibility = 1 - smooth((float) Math.sqrt(nearest), 120, 190);
            if (look.visible() && finite(rika) && finite(geto) && finite(middle) && rika.distanceToSqr(geto) <= 128 * 128 && visibility > .001f)
                jobs.add(new Job(look, rika, geto, middle, visibility, best.getInt("Serial"), nearest));
        }
        for (PlayerUzumakiRenderer.Visual v : PlayerUzumakiRenderer.visuals(partial, camera)) {
            Vec3 axis = v.direction().normalize();
            if (!finite(v.origin()) || !finite(axis) || axis.lengthSqr() < .5) continue;
            Look look = v.sustained()?held(v.kind(),v.age(),v.length()):vortex(v.age(), v.length(), v.scale());
            double distance = camera.distanceToSqr(v.origin());
            // A first-person caster sees the discharge, not the inside of the curse orb at their hands.
            float near = 1 - smooth((float) Math.sqrt(distance), 2.2f, 5f);
            float sourceFade=1-near*(v.sustained()&&v.age()>=dev.jujutsuvfx.core.SustainedBeam.CHARGE?1f:.85f);
            if (near > 0) look = new Look(look.rikaCharge()*sourceFade, look.getoCharge()*sourceFade, look.rikaBeam(), look.getoBeam(), look.contact(), look.pressure(), 0, 0, 0, 0,
                    look.time(), look.getoScale(), look.contactTone());
            float visibility = 1 - smooth((float) Math.sqrt(distance), 110, 180);
            if (!look.visible() || visibility <= .001f) continue;
            Vec3 end=v.sustained()&&v.path().size()>1?v.path().get(v.path().size()-1):v.origin().add(axis.scale(Math.max(.01,v.length())));
            jobs.add(new Job(look, v.kind()==1?v.origin():v.origin().subtract(axis), v.kind()==1?v.origin().subtract(axis):v.origin(), end,end,visibility,
                    Math.floorMod(v.origin().hashCode(), 997), distance,v.path()));
        }
        for(PlayerUzumakiRenderer.Clash c:PlayerUzumakiRenderer.clashes(partial,camera)){
            double distance=camera.distanceToSqr(c.contact());float visibility=1-smooth((float)Math.sqrt(distance),110,180);
            if(visibility>.001f){
                Look clash=playerClash(c.age());
                for(int kind=0;kind<2;kind++){
                    var path=kind==1?c.rikaPath():c.getoPath();if(path.size()<2)continue;
                    Vec3 origin=path.get(0),end=path.get(path.size()-1);
                    float charge=clash.rikaCharge()*smooth((float)camera.distanceTo(origin),2.2f,5f);
                    float beam=clash.rikaBeam();
                    Look side=new Look(kind==1?charge:0,kind==0?charge:0,kind==1?beam:0,kind==0?beam:0,0,.7f,0,0,0,0,clash.time(),1,kind==0?1:0);
                    if(side.visible())jobs.add(new Job(side,c.rika(),c.geto(),end,end,visibility,c.id(),distance,path));
                }
                // Contact and explosion share the original collision centre; neither beam becomes a chord.
                Look contact=new Look(0,0,0,0,clash.contact(),clash.pressure(),clash.burst(),clash.aftermath(),clash.flash(),0,clash.time());
                jobs.add(new Job(contact,c.rika(),c.geto(),c.contact(),c.contact(),visibility,c.id(),distance));
            }
        }
        if (jobs.isEmpty()) { if (energy != null) release(); return; }
        // Distant effects first; at most three full-screen passes per frame.
        jobs.sort((a, b) -> Double.compare(b.distance(), a.distance()));
        for (int i = Math.max(0, jobs.size() - 3); i < jobs.size(); i++) {
            Job j = jobs.get(i);
            draw(event, j.look(), j.rika().subtract(camera), j.geto().subtract(camera), j.middle().subtract(camera), j.burst().subtract(camera), j.visibility(), j.serial(),j.path().stream().map(p->p.subtract(camera)).toList());
        }
    }

    private static void draw(RenderLevelStageEvent event, Look look, Vec3 rika, Vec3 geto, Vec3 middle, Vec3 burst, float visibility, int serial,java.util.List<Vec3> path) {
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        Matrix4f inverse = new Matrix4f(projection).invert();
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        float[] clear = new float[4]; GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clear);
        try (DomainRenderer.State ignored = new DomainRenderer.State(); SceneSnapshot.Target target = new SceneSnapshot.Target()) {
            if (!SCENE.capture()) return;
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int width = Math.max(1, (viewport[2] + 1) / 2), height = Math.max(1, (viewport[3] + 1) / 2);
            try (SceneSnapshot.Target original = new SceneSnapshot.Target()) {
                if (energy == null || energy.width != width || energy.height != height) allocate(width, height);
                energy.bindWrite(true);
                RenderSystem.clearColor(0, 0, 0, 0); RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
                volume.setSampler("SceneDepth", SCENE.depth());
                uniforms(volume, look, inverse, rika, geto, middle, burst, visibility, serial,path);
                quad(volume);
            }
            composite.setSampler("SceneColor", SCENE.color());
            composite.setSampler("SceneDepth", SCENE.depth());
            composite.setSampler("EnergyLayer", energy.getColorTextureId());
            uniforms(composite, look, inverse, rika, geto, middle, burst, visibility, serial,path);
            composite.safeGetUniform("ViewProjection").set(projection);
            composite.safeGetUniform("FlashEnabled").set(VfxConfig.flashScale());
            composite.safeGetUniform("Flash").set(look.flash() * visibility);
            composite.safeGetUniform("Grade").set(look.grade() * visibility);
            composite.safeGetUniform("Distortion").set(Minecraft.getInstance().options.screenEffectScale().get().floatValue());
            quad(composite);
        } finally {
            RenderSystem.clearColor(clear[0], clear[1], clear[2], clear[3]);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    /** RGBA16F keeps the HDR plasma; drivers that refuse it fall back to RGBA8 with a 1/4 encoding. */
    private static void allocate(int width, int height) {
        if (energy != null) energy.destroyBuffers();
        energy = new EffectTarget(width, height, false, Minecraft.ON_OSX);
        energy.setFilterMode(GL11.GL_NEAREST);
        floatEnergy = false;
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            for (int i = 0; i < 8 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {}
            RenderSystem.bindTexture(energy.getColorTextureId()); GL11.glBindTexture(GL11.GL_TEXTURE_2D, energy.getColorTextureId());
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, energy.frameBufferId);
            floatEnergy = GL11.glGetError() == GL11.GL_NO_ERROR
                    && GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
            if (!floatEnergy) {
                RenderSystem.bindTexture(energy.getColorTextureId()); GL11.glBindTexture(GL11.GL_TEXTURE_2D, energy.getColorTextureId());
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read); GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            RenderSystem.activeTexture(active); GL13.glActiveTexture(active);
            RenderSystem.bindTexture(texture); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }
    }

    private static void uniforms(ShaderInstance shader, Look look, Matrix4f inverse, Vec3 rika, Vec3 geto, Vec3 middle, Vec3 burst, float visibility, int serial,java.util.List<Vec3> path) {
        shader.safeGetUniform("BeamPathCount").set((float)path.size());
        double pathLength=0;for(int i=1;i<path.size();i++)pathLength+=path.get(i).distanceTo(path.get(i-1));
        shader.safeGetUniform("BeamPathLength").set((float)pathLength);
        for(int i=0;i<dev.jujutsuvfx.core.BeamPath.MAX_POINTS;i++){
            Vec3 p=path.isEmpty()?Vec3.ZERO:path.get(Math.min(i,path.size()-1));
            shader.safeGetUniform("BeamNode"+i).set((float)p.x,(float)p.y,(float)p.z);
        }
        shader.safeGetUniform("InverseViewProjection").set(inverse);
        shader.safeGetUniform("RikaPos").set((float) rika.x, (float) rika.y, (float) rika.z);
        shader.safeGetUniform("GetoPos").set((float) geto.x, (float) geto.y, (float) geto.z);
        shader.safeGetUniform("ContactPos").set((float) middle.x, (float) middle.y, (float) middle.z);
        shader.safeGetUniform("BurstPos").set((float) burst.x, (float) burst.y, (float) burst.z);
        shader.safeGetUniform("Time").set(look.time());
        shader.safeGetUniform("RikaCharge").set(look.rikaCharge());
        shader.safeGetUniform("GetoCharge").set(look.getoCharge());
        shader.safeGetUniform("RikaBeam").set(look.rikaBeam());
        shader.safeGetUniform("GetoBeam").set(look.getoBeam());
        shader.safeGetUniform("Contact").set(look.contact());
        shader.safeGetUniform("Pressure").set(look.pressure());
        shader.safeGetUniform("Burst").set(look.burst());
        shader.safeGetUniform("Aftermath").set(look.aftermath());
        shader.safeGetUniform("Visibility").set(visibility);
        shader.safeGetUniform("Seed").set((float) Math.floorMod(serial * 7919, 997));
        shader.safeGetUniform("EnergyScale").set(floatEnergy ? 1f : 4f);
        shader.safeGetUniform("GetoScale").set(look.getoScale());
        shader.safeGetUniform("ContactTone").set(look.contactTone());
    }

    private static void quad(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader); RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.disableCull(); RenderSystem.disableBlend();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(-1, -1, 1).setUv(0, 0).setColor(-1); b.addVertex(1, -1, 1).setUv(1, 0).setColor(-1);
        b.addVertex(1, 1, 1).setUv(1, 1).setColor(-1); b.addVertex(-1, 1, 1).setUv(0, 1).setColor(-1);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    static float smooth(float t, float a, float b) { float x = Math.max(0, Math.min(1, (t - a) / (b - a))); return x * x * (3 - 2 * x); }
}
