package com.liskovsoft.smartyoutubetv2.common.proxy.vmess;

import android.util.Log;

import libbox.BoxService;
import libbox.Libbox;

/**
 * Real sing-box core bridge backed by the official {@code libbox} gobind
 * bindings ({@code SagerNet/sing-box/experimental/libbox}, see engine tag v1.11.15).
 *
 * <p>Registered as the default engine by {@link VmessProxyManager} via its
 * {@code file()libbox} dependency. This class does <b>not</b> use reflection so it
 * needs {@code libbox.aar} on the classpath at compile time; {@code common/build.gradle}
 * pulls it in when present under {@code common/libs/libbox.aar}.
 *
 * <p>Contract (verified against v1.11.15 gobind): the engine starts a sing-box core in
 * <b>proxy mode</b> (local SOCKS/HTTP inbounds, no TUN, no VpnService permission),
 * exactly as produced by {@link SingBoxVmessConfig}. The {@link LibboxPlatformInterface}
 * passed to {@code Libbox.newService} is a best-effort no-op: the vmess config contains
 * no TUN inbound, so platform callbacks should rarely fire.
 */
public final class LibboxSingBoxEngine implements SingBoxEngine {
    private static final String TAG = LibboxSingBoxEngine.class.getSimpleName();

    private BoxService mService;
    private boolean mRunning;

    @Override
    public String start(String configJson) {
        try {
            Libbox.touch();
        } catch (Throwable ignore) {
            // optional native warm-up; ignore
        }

        if (mService != null) {
            stop();
        }

        try {
            mService = Libbox.newService(configJson, new LibboxPlatformInterface());
            mService.start();
            mRunning = true;
            return null;
        } catch (Throwable e) {
            Log.e(TAG, "sing-box start failed", e);
            stopQuietly(mService);
            mService = null;
            mRunning = false;
            return "sing-box start error: " + e.getMessage();
        }
    }

    @Override
    public void stop() {
        stopQuietly(mService);
        mService = null;
        mRunning = false;
    }

    @Override
    public boolean isRunning() {
        return mRunning;
    }

    private static void stopQuietly(BoxService service) {
        if (service == null) {
            return;
        }
        try {
            service.close();
        } catch (Throwable ignore) {
            // best effort
        }
    }
}