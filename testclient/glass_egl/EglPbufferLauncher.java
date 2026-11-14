import org.lwjgl.opengl.GLContext;

/** Headless compatibility context for real native Minecraft buffer checks. */
public final class EglPbufferLauncher {
    private static native String create();
    private static native void destroy();
    public static void main(String[] args)throws Exception {
        System.load(System.getProperty("egl.bridge"));
        System.out.println(create());
        try {
            GLContext.useContext(EglPbufferLauncher.class);
            Class.forName(args[0]).getMethod("main",String[].class).invoke(null,(Object)new String[0]);
        } finally {
            GLContext.useContext(null);destroy();
        }
        System.out.println("EGL native pipeline launcher PASS");
    }
}
