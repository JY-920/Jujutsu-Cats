package dev.jujutsuvfx.client;

import com.mojang.logging.LogUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import java.util.LinkedHashSet;
import java.util.Set;

/** Reports actual driver failures. Never selects a material, quality level or replacement renderer. */
final class RenderDiagnostics {
    private static final Set<String> failures=new LinkedHashSet<>();
    private static boolean initialized;
    private static String renderer,version,lastFailure;
    private RenderDiagnostics() {}
    private static String string(int name) {
        String value=GL11.glGetString(name);return value==null?"unknown":value;
    }
    static void initialize() {
        if(initialized)return;
        renderer=string(GL11.GL_RENDERER);version=string(GL11.GL_VERSION);initialized=true;
        LogUtils.getLogger().info("Jujutsu VFX graphics: original materials only; OS={}, vendor={}, renderer={}, GL={}, GLSL={}, max texture={}, framebuffer blit={}",
                System.getProperty("os.name","unknown"),string(GL11.GL_VENDOR),renderer,version,
                string(GL20.GL_SHADING_LANGUAGE_VERSION),GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE),
                GL.getCapabilities().glBlitFramebuffer!=0);
    }
    static void fail(String reason) {
        initialize();lastFailure=reason;
        if(failures.size()<16&&failures.add(reason))LogUtils.getLogger().warn(
                "Jujutsu VFX render failure: {}. Only the failed pass is skipped; no simplified materials or automatic quality downgrade.",reason);
    }
    static String diagnostic() {
        initialize();return "完整特效（无简化模式） | "+renderer+" | "+version
                +(lastFailure==null?"":" | 最近渲染异常："+lastFailure);
    }
    static void beginReload() { failures.clear();lastFailure=null;initialize(); }
}
