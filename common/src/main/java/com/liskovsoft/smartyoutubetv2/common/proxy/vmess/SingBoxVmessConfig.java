package com.liskovsoft.smartyoutubetv2.common.proxy.vmess;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Builds a sing-box JSON configuration for a single vmess node.
 *
 * <p>The generated config exposes a <b>local SOCKS + HTTP proxy</b> on loopback
 * (no TUN device), so it does <b>not</b> require a VpnService dialog. The local
 * port can then be handed to {@link com.liskovsoft.smartyoutubetv2.common.proxy.ProxyManager}
 * so the whole app (general settings, search, video playback via OkHttp/ExoPlayer)
 * routes through the vmess node.
 */
public final class SingBoxVmessConfig {
    public static final String LOCAL_ADDRESS = "127.0.0.1";
    public static final int DEFAULT_LOCAL_SOCKS_PORT = 1088;
    public static final int DEFAULT_LOCAL_HTTP_PORT = 1089;

    private SingBoxVmessConfig() {
    }

    /**
     * @param link          parsed vmess node
     * @param localSocksPort local SOCKS inbound port (loopback)
     * @param localHttpPort  local HTTP inbound port (loopback)
     */
    public static String build(VmessLink link, int localSocksPort, int localHttpPort) throws JSONException {
        JSONObject root = new JSONObject();

        JSONObject log = new JSONObject();
        log.put("level", "warn");
        log.put("timestamp", true);
        root.put("log", log);

        root.put("inbounds", new JSONArray().put(socksInbound(localSocksPort)).put(httpInbound(localHttpPort)));
        root.put("outbounds", new JSONArray().put(directOutbound()).put(vmessOutbound(link)));
        root.put("route", route());

        return root.toString(4);
    }

    private static JSONObject socksInbound(int port) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", "socks");
        o.put("tag", "socks-in");
        o.put("listen", LOCAL_ADDRESS);
        o.put("listen_port", port);
        o.put("sniff", true);
        return o;
    }

    private static JSONObject httpInbound(int port) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", "http");
        o.put("tag", "http-in");
        o.put("listen", LOCAL_ADDRESS);
        o.put("listen_port", port);
        o.put("sniff", true);
        return o;
    }

    private static JSONObject directOutbound() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", "direct");
        o.put("tag", "direct");
        return o;
    }

    private static JSONObject vmessOutbound(VmessLink link) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", "vmess");
        o.put("tag", "vmess-out");
        o.put("server", link.address);
        o.put("server_port", link.port);

        // sing-box vmess outbound keeps uuid / security / alter_id at the top level.
        o.put("uuid", link.uuid);
        o.put("security", firstNonEmptyTLS(link.security, "auto"));
        o.put("alter_id", parseAlterId(link));

        // transport
        if (link.network != null && !link.network.isEmpty()) {
            o.put("transport", transport(link));
        }

        // tls
        if (link.isTlsEnabled()) {
            JSONObject tls = new JSONObject();
            tls.put("enabled", true);
            tls.put("server_name", firstNonEmpty(link.host, link.sni, link.address));
            if (link.fingerprint != null && !link.fingerprint.isEmpty()) {
                JSONObject utls = new JSONObject();
                utls.put("enabled", true);
                utls.put("fingerprint", link.fingerprint);
                tls.put("utls", utls);
            }
            o.put("tls", tls);
        }
        return o;
    }

    private static JSONObject transport(VmessLink link) throws JSONException {
        JSONObject t = new JSONObject();
        String net = link.network.toLowerCase();
        switch (net) {
            case "ws":
            case "websocket": {
                JSONObject ws = new JSONObject();
                ws.put("path", link.path == null || link.path.isEmpty() ? "/" : link.path);
                JSONObject headers = new JSONObject();
                if (link.host != null && !link.host.isEmpty()) {
                    headers.put("Host", link.host);
                }
                if (headers.length() > 0) {
                    ws.put("headers", headers);
                }
                t.put("type", "ws");
                t.put("ws", ws);
                break;
            }
            case "grpc": {
                t.put("type", "grpc");
                if (link.grpcServiceName != null && !link.grpcServiceName.isEmpty()) {
                    t.put("service_name", link.grpcServiceName);
                }
                break;
            }
            case "h2":
            case "http": {
                t.put("type", "http");
                JSONObject request = new JSONObject();
                request.put("path", link.path == null ? "/" : link.path);
                if (link.host != null && !link.host.isEmpty()) {
                    JSONArray headers = new JSONArray().put(new JSONObject().put("Host", new JSONArray().put(link.host)));
                    request.put("headers", headers);
                }
                t.put("request", request);
                break;
            }
            case "quic": {
                t.put("type", "quic");
                t.put("method", "none");
                break;
            }
            case "tcp":
            default:
                t.put("type", "tcp");
                if (link.host != null && !link.host.isEmpty()) {
                    JSONObject header = new JSONObject();
                    header.put("type", "http");
                    JSONObject request = new JSONObject();
                    JSONArray headers = new JSONArray().put(new JSONObject().put("Host", new JSONArray().put(link.host)));
                    request.put("headers", headers);
                    header.put("request", request);
                    t.put("header", header);
                }
                break;
        }
        return t;
    }

    private static JSONObject route() throws JSONException {
        JSONObject route = new JSONObject();

        // sentinel: dump everything through vmess; keep private/loopback direct
        JSONArray rules = new JSONArray();

        // Avoid proxying loopback/local
        JSONObject localRule = new JSONObject();
        localRule.put("outbound", "direct");
        localRule.put("ip_cidr", new JSONArray().put("127.0.0.0/8").put("10.0.0.0/8").put("172.16.0.0/12").put("192.168.0.0/16").put("169.254.0.0/16").put("fe80::/10").put("fc00::/7"));
        rules.put(localRule);

        JSONObject youTube = new JSONObject();
        youTube.put("outbound", "vmess-out");
        youTube.put("domain_suffix", new JSONArray().put("youtube.com").put("googlevideo.com").put("ytimg.com").put("ytstatic.google.com"));
        rules.put(youTube);

        route.put("rules", rules);

        // route.final is a string (the outbound tag) — everything not matched by a
        // rule defaults to the vmess node.
        route.put("final", "vmess-out");

        return route;
    }

    private static int parseAlterId(VmessLink link) {
        try {
            return Integer.parseInt(firstNonEmptyTLS(link.alterId, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String firstNonEmptyTLS(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private static String firstNonEmpty(String... values) {
        return firstNonEmptyTLS(values);
    }
}