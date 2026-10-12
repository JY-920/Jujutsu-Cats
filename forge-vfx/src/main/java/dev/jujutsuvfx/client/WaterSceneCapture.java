package dev.jujutsuvfx.client;

/** Shared scratch image for water, void and slash refraction, consumed before the next capture. */
final class WaterSceneCapture {
    private static final SceneSnapshot SCENE = new SceneSnapshot();
    static int color(){return SCENE.color();}
    static int depth(){return SCENE.depth();}
    static boolean available(){return SCENE.available();}
    static boolean capture(){return SCENE.capture();}
    static void release(){SCENE.release();}
}
