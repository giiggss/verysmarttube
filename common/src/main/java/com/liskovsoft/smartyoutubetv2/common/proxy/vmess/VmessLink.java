package com.liskovsoft.smartyoutubetv2.common.proxy.vmess;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Parses a v2rayN-style vmess:// link.
 *
 * <p>Two formats are supported:
 * <ul>
 *     <li>base64-encoded JSON object: v2rayN / v2rayNG share links.</li>
 *     <li>plain base64 bytes of "uuid:address:port" (older v2ray-core format).</li>
 * </ul>
 */
public final class VmessLink {
    public String uuid;
    public String address;
    public int port;
    public String security;   // aes-128-gcm, chacha20-poly1305, auto, none, zero, 2022-blake3-...
    public String alterId;
    public String network;    // tcp, ws, h2, grpc...
    public String path;       // ws/h2/grpc path
    public String host;       // ws/h2 Host / SNI
    public String fingerprint; // uTls fingerprint
    public String sni;
    public String tls;        // "tls" when enabled
    public String remark;     // friendly name
    public String grpcServiceName;

    private VmessLink() {
    }

    public static boolean isVmessLink(String text) {
        return text != null && text.trim().startsWith("vmess://");
    }

    /**
     * @param text raw vmess:// link (with or without scheme prefix)
     * @return parsed profile, or {@code null} if malformed
     */
    public static VmessLink parse(String text) {
        if (!isVmessLink(text)) {
            return null;
        }

        String payload = text.trim().substring("vmess://".length()).trim();
        // Strip optional fragment-like remark suffix (e.g. #name) — usually not present in vmess
        int frag = payload.indexOf('#');
        if (frag >= 0) {
            payload = payload.substring(0, frag);
        }

        byte[] raw;
        try {
            raw = Base64.decode(payload, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            // URL-safe variant (some clients)
            try {
                raw = Base64.decode(payload.replace('-', '+').replace('_', '/'), Base64.DEFAULT);
            } catch (IllegalArgumentException e2) {
                return null;
            }
        }
        if (raw == null || raw.length == 0) {
            return null;
        }

        String decoded;
        try {
            decoded = new String(raw, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            decoded = new String(raw, StandardCharsets.UTF_8);
        }

        VmessLink link = tryParseJson(decoded);
        if (link == null) {
            link = tryParseLegacy(decoded);
        }
        return link;
    }

    private static VmessLink tryParseJson(String decoded) {
        try {
            JSONObject o = new JSONObject(decoded);
            VmessLink link = new VmessLink();
            link.address = o.optString("add", o.optString("host"));
            link.port = o.optInt("port");
            link.uuid = o.optString("id", o.optString("uuid"));
            link.security = o.optString("scy", o.optString("security", "auto"));
            link.alterId = o.optString("aid", o.optString("alterId", "0"));
            link.network = o.optString("net", o.optString("network", "tcp"));

            // try-catch each to be tolerant of partial malformed links
            link.path = firstNonEmpty(o.optString("path"), o.optString("p"));
            link.host = firstNonEmpty(o.optString("host"), o.optString("h"), o.optString("peo"));
            link.tls = firstNonEmpty(o.optString("tls"), o.optString("type"));
            link.sni = o.optString("sni");
            link.fingerprint = o.optString("fp", o.optString("fingerprint"));
            link.remark = o.optString("ps", o.optString("remark", o.optString("name")));
            link.grpcServiceName = o.optString("serviceName");

            if (link.address == null || link.address.isEmpty() || link.port <= 0 || link.uuid == null || link.uuid.isEmpty()) {
                return null;
            }
            if (link.network != null && link.network.equals("ws")) {
                // resolve host/path "properly", e.g. "path": "/ws?ed=2048"
            }
            return link;
        } catch (JSONException e) {
            return null;
        }
    }

    private static VmessLink tryParseLegacy(String decoded) {
        String[] parts = decoded.split(":");
        if (parts.length < 2) {
            return null;
        }
        VmessLink link = new VmessLink();
        link.uuid = parts[0];
        link.address = parts[1];
        if (parts.length >= 3) {
            try {
                link.port = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        link.security = "auto";
        link.alterId = "0";
        link.network = "tcp";
        if (link.address == null || link.address.isEmpty() || link.port <= 0 || link.uuid == null || link.uuid.isEmpty()) {
            return null;
        }
        return link;
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    public boolean isTlsEnabled() {
        return tls != null && !tls.isEmpty() && !tls.equalsIgnoreCase("none") && !tls.equalsIgnoreCase("false");
    }

    public String displayName() {
        if (remark != null && !remark.isEmpty()) {
            return remark;
        }
        return (uuid != null ? uuid.substring(0, Math.min(8, uuid.length())) : "") + "@" + address + ":" + port;
    }

    @Override
    public String toString() {
        return "VmessLink{" +
                "uuid='" + uuid + '\'' +
                ", address='" + address + '\'' +
                ", port=" + port +
                ", security='" + security + '\'' +
                ", network='" + network + '\'' +
                ", tls=" + isTlsEnabled() +
                '}';
    }
}