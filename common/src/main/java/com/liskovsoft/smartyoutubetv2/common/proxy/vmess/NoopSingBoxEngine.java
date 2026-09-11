package com.liskovsoft.smartyoutubetv2.common.proxy.vmess;

/**
 * Fallback engine used when the official libbox (sing-box core) is not linked
 * into the build. Lets the SmartTube build succeed and the UI run; reports a
 * clear message when the user tries to start the vmess proxy.
 */
public final class NoopSingBoxEngine implements SingBoxEngine {
    public static final String ERROR_NOT_LINKED =
            "sing-box core (libbox) is not linked into this build. "
                    + "Compile the official libbox from SagerNet/sing-box (experimental/libbox) "
                    + "and place the AAR into common/libs/libbox.aar.";

    @Override
    public String start(String configJson) {
        return ERROR_NOT_LINKED;
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isRunning() {
        return false;
    }
}