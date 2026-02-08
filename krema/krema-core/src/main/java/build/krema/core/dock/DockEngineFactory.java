package build.krema.core.dock;

import build.krema.core.dock.macos.MacOSDockEngine;
import build.krema.core.platform.Platform;

/**
 * Factory for creating platform-specific DockEngine instances.
 */
public final class DockEngineFactory {

    private static volatile DockEngine instance;

    private DockEngineFactory() {}

    public static DockEngine get() {
        if (instance == null) {
            synchronized (DockEngineFactory.class) {
                if (instance == null) {
                    instance = create();
                }
            }
        }
        return instance;
    }

    private static DockEngine create() {
        Platform platform = Platform.current();

        return switch (platform) {
            case MACOS -> new MacOSDockEngine();
            case WINDOWS -> new NoOpDockEngine(); // Windows taskbar badges are different
            case LINUX -> new NoOpDockEngine();   // Linux varies by desktop environment
            case UNKNOWN -> new NoOpDockEngine();
        };
    }

    /**
     * No-op implementation for unsupported platforms.
     */
    private static class NoOpDockEngine implements DockEngine {
        @Override
        public void setBadge(String text) {}

        @Override
        public String getBadge() {
            return "";
        }

        @Override
        public void clearBadge() {}

        @Override
        public long requestAttention(boolean critical) {
            return 0;
        }

        @Override
        public void cancelAttention(long requestId) {}

        @Override
        public boolean isSupported() {
            return false;
        }
    }
}
