package dev.jujutsuvfx.platform;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

/** 1.20.1 buffers are persistent and must explicitly begin/end each batch. */
public final class RenderBuffers {
    private RenderBuffers() {}
    public static BufferBuilder begin(VertexFormat.Mode mode,VertexFormat format) {
        return begin(Tesselator.getInstance().getBuilder(),mode,format);
    }
    public static BufferBuilder begin(BufferBuilder buffer,VertexFormat.Mode mode,VertexFormat format) {
        buffer.begin(mode,format);return buffer;
    }
}
