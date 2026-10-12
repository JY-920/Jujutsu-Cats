package dev.jujutsuvfx.core;

public final class RiftShapeTest {
    public static void main(String[] args) {
        require(RiftShape.width(-1) == 0 && RiftShape.width(1) == 0, "tips must close");
        for (int i = -1000; i <= 1000; i++) {
            float y = i / 1000f;
            require(Float.isFinite(RiftShape.width(y)) && RiftShape.width(y) >= 0, "finite silhouette");
        }
        for (float duration : new float[]{2, 30, 300}) {
            require(RiftShape.envelope(-1, duration) == 0, "not born");
            require(RiftShape.envelope(0, duration) == 0, "no opening pop");
            require(RiftShape.envelope(duration, duration) == 0, "fully closed at expiry");
            require(RiftShape.envelope(duration + 1, duration) == 0, "expired");
            require(RiftShape.envelope(1, duration) == 1, "fully open");
            for (int i = 0; i < 1000; i++) {
                float e = RiftShape.envelope(duration * i / 1000, duration);
                require(e >= 0 && e <= 1 && Float.isFinite(e), "bounded animation");
            }
        }
        System.out.println("Rift shape: closed tips, finite geometry and lifecycle checks passed.");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
