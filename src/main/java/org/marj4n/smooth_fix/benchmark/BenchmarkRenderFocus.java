package org.marj4n.smooth_fix.benchmark;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/** Benchmark acceptance only: never activates a window or alters Dynamic FPS. */
public final class BenchmarkRenderFocus {
    private BenchmarkRenderFocus() {}

    public static boolean valid(MinecraftClient client) {
        if (!client.isOnThread() || !client.isWindowFocused()) return false;
        long handle = client.getWindow().getHandle();
        return handle != 0
                && GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE
                && GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_FALSE;
    }
}
