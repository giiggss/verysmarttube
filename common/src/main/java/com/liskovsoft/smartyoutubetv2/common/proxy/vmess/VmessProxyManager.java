package com.liskovsoft.smartyoutubetv2.common.proxy.vmess;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.proxy.PasswdInetSocketAddress;
import com.liskovsoft.smartyoutubetv2.common.proxy.Proxy;
import com.liskovsoft.smartyoutubetv2.common.proxy.ProxyManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

/**
 * Orchestrates the built-in vmess proxy feature.
 *
 * <p>Workflow:
 * <ol>
 *     <li>User pastes a {@code vmess://} link.</li>
 *     <li>{@link VmessLink} parses it.</li>
 *     <li>{@link SingBoxVmessConfig} builds a sing-box JSON with a local SOCKS/HTTP inbound
 *         on loopback (no TUN / no VpnService).</li>
 *     <li>The engine ({@link SingBoxEngine}) starts sing-box.</li>
 *     <li>{@link ProxyManager} is pointed to the local SOCKS port so the whole app
 *         (settings, search, playback via OkHttp/ExoPlayer) routes through the node.</li>
 * </ol>
 */
public class VmessProxyManager {
    private static final String VMESS_LINK = "vmess_proxy_link";
    private static final String VMESS_ENABLED = "vmess_proxy_enabled";
    private static final String VMESS_LAST_PORT = "vmess_proxy_last_port";

    /**
     * Global engine, settable once at startup. Default auto-detects libbox at runtime
     * via reflection (no compile-time dependency). If the official libbox AAR is bundled,
     * the real core runs; if not, it returns a clear "not linked" error. Build-specific
     * code may override via {@link #registerEngine(SingBoxEngine)}.
     */
    private static SingBoxEngine sEngine = new LibboxSingBoxEngine();

    private final Context mContext;
    private final AppPrefs mPrefs;
    private final ProxyManager mProxyManager;

    public VmessProxyManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = AppPrefs.instance(mContext);
        mProxyManager = new ProxyManager(mContext);
    }

    /**
     * Register the real sing-box engine globally. Call once from app startup.
     */
    public static void registerEngine(SingBoxEngine engine) {
        sEngine = engine != null ? engine : new NoopSingBoxEngine();
    }

    public String getVmessLink() {
        return mPrefs.getString(VMESS_LINK, "");
    }

    public void setVmessLink(String link) {
        mPrefs.putString(VMESS_LINK, link == null ? "" : link.trim());
    }

    public boolean isEnabled() {
        return mPrefs.getBoolean(VMESS_ENABLED, false);
    }

    /**
     * Enable/disable the vmess proxy.
     *
     * @param link raw vmess:// link (used when enabling)
     * @return an error message, or {@code null} on success
     */
    public String setEnabled(boolean enabled, String link) {
        if (enabled) {
            if (link != null && !link.isEmpty()) {
                setVmessLink(link);
            }

            String stored = getVmessLink();
            VmessLink parsed = VmessLink.parse(stored);
            if (parsed == null) {
                return "Invalid vmess:// link. Please paste a valid v2rayN-style link.";
            }

            int socksPort = SingBoxVmessConfig.DEFAULT_LOCAL_SOCKS_PORT;
            int httpPort = SingBoxVmessConfig.DEFAULT_LOCAL_HTTP_PORT;
            try {
                // Build config against the local SOCKS port, hand local port to ProxyManager below.
                String configJson = SingBoxVmessConfig.build(parsed, socksPort, httpPort);

                String error = sEngine.start(configJson);
                if (error != null) {
                    mPrefs.putBoolean(VMESS_ENABLED, false);
                    return error;
                }

                // Route the whole app through the local SOCKS proxy.
                mProxyManager.saveProxyInfoToPrefs(
                        new Proxy(Proxy.Type.SOCKS,
                                PasswdInetSocketAddress.createUnresolved(SingBoxVmessConfig.LOCAL_ADDRESS, socksPort, "", "")),
                        true);
                mProxyManager.configureSystemProxy();

                mPrefs.putString(VMESS_LINK, stored);
                mPrefs.putBoolean(VMESS_ENABLED, true);
                return null;
            } catch (Exception e) {
                mPrefs.putBoolean(VMESS_ENABLED, false);
                return e.getMessage() == null ? String.valueOf(e) : e.getMessage();
            }
        } else {
            // Stop engine and disconnect from the local proxy.
            sEngine.stop();
            mProxyManager.saveProxyInfoToPrefs(null, false);
            mProxyManager.configureSystemProxy();
            mPrefs.putBoolean(VMESS_ENABLED, false);
            return null;
        }
    }
}