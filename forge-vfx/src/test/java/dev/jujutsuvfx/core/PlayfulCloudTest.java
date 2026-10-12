package dev.jujutsuvfx.core;

/** Run without Minecraft: packet-independent combo state, continuous poses, real closed mesh bounds. */
public final class PlayfulCloudTest {
    public static void main(String[] args) {
        var c=new PlayfulCloudMotion.Combo();
        check(c.begin(0)&&c.step()==0,"first stroke");
        check(!c.begin(0)&&!c.begin(6)&&c.step()==0,"hit + swing packets must not count twice");
        check(c.hitOnce()&&!c.hitOnce(),"one impact/impulse per swing");
        check(c.begin(7)&&c.step()==1&&c.hitOnce(),"reverse stroke");
        check(c.begin(14)&&c.step()==2,"third stroke");
        check(c.begin(23)&&c.step()==0,"combo wraps");
        check(c.begin(50)&&c.step()==0,"idle resets combo");
        c.heavy(60);check(c.step()==3&&!c.begin(62),"heavy cannot restart itself");
        check(c.begin(85)&&c.step()==0,"normal combo resumes after heavy");
        check(c.begin(0)&&c.step()==0,"time reset cannot keep old combo");
        var independent=new PlayfulCloudMotion.Combo();
        check(independent.begin(4)&&independent.step()==0,"two actors do not share combo state");
        check(PlayfulCloudMotion.heavyPower(-1)==.65f&&PlayfulCloudMotion.heavyPower(72000)==1,"charge bounded");
        for(int step=0;step<4;step++)for(float t=0;t<=PlayfulCloudMotion.duration(step);t+=.025f) {
            var p=PlayfulCloudMotion.sample(step,t);
            check(Float.isFinite(p.x()+p.y()+p.z()+p.pitch()+p.yaw()+p.roll()+p.bend1()+p.bend2()+p.twist1()+p.twist2()),"finite pose");
            check(Math.abs(p.x())<1.1&&Math.abs(p.y())<.9&&p.z()<-.5&&p.z()>-1.2,"wrist stays in bounded hand space");
            float a=PlayfulCloudMotion.trail(step,t);check(a>=0&&a<=1,"bounded trail");
        }
        for(int step=0;step<4;step++) {
            var p=PlayfulCloudMotion.sample(step,PlayfulCloudMotion.duration(step)-.001f);
            var idle=PlayfulCloudMotion.IDLE;
            check(Math.abs(p.x()-idle.x())<.001&&Math.abs(p.y()-idle.y())<.001,"visible recovery returns to rest");
            check(Math.abs(Math.sin(Math.toRadians(p.roll()-idle.roll())))<.001,"rotation returns without snapping");
        }
        check(PlayfulCloudMesh.ROD.size()>100&&PlayfulCloudMesh.LINK.size()==48,"3D rod and open chain links");
        float ymin=100,ymax=-100,zmin=100,zmax=-100;
        for(var q:PlayfulCloudMesh.ROD)for(var v:new PlayfulCloudMesh.Vertex[]{q.a(),q.b(),q.c(),q.d()}) {
            check(Float.isFinite(v.x()+v.y()+v.z()+v.nx()+v.ny()+v.nz()),"finite mesh");
            ymin=Math.min(ymin,v.y());ymax=Math.max(ymax,v.y());zmin=Math.min(zmin,v.z());zmax=Math.max(zmax,v.z());
        }
        check(Math.abs((ymax-ymin)-PlayfulCloudMotion.ROD_LENGTH)<.00001,"rig and geometry use same rod length");
        check(zmax-zmin>.10,"weapon has thickness, not a crossed sprite");
        System.out.println("Playful Cloud: combo isolation, debounce, charge bounds, recovery and 3D geometry passed.");
    }
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
}
