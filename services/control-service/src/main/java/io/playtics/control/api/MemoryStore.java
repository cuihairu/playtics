package io.playtics.control.api;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryStore {
    private final Map<String, Models.Project> projects = new ConcurrentHashMap<>();
    private final Map<String, Key> keys = new ConcurrentHashMap<>(); // apiKey -> Key

    public static class Key {
        public String apiKey;
        public String secret;
        public String projectId;
        public String name;
        public int rpm = 600;
        public int ipRpm = 300;
        public List<String> propsAllowlist = List.of();
        // PII override (nullable means use gateway defaults)
        public String piiEmail;  // allow|mask|drop
        public String piiPhone;  // allow|mask|drop
        public String piiIp;     // allow|coarse|drop
        public List<String> denyKeys; // sensitive keys to block
        public List<String> maskKeys; // keys to mask
    }

    public Models.Project upsertProject(String id, String name) {
        Models.Project p = new Models.Project(); p.id = id; p.name = name;
        projects.put(id, p);
        return p;
    }
    public Collection<Models.Project> listProjects() { return projects.values(); }

    public Key createKey(String projectId, String name) {
        Key k = new Key();
        k.apiKey = gen("pk_");
        k.secret = gen("sk_");
        k.projectId = projectId; k.name = name;
        keys.put(k.apiKey, k);
        return k;
    }
    public Key getKey(String apiKey) { return keys.get(apiKey); }

    public Key updatePolicy(String apiKey, Integer rpm, Integer ipRpm, List<String> allowlist,
                            String piiEmail, String piiPhone, String piiIp,
                            List<String> denyKeys, List<String> maskKeys) {
        Key k = keys.get(apiKey);
        if (k == null) return null;
        if (rpm != null) k.rpm = rpm;
        if (ipRpm != null) k.ipRpm = ipRpm;
        if (allowlist != null) k.propsAllowlist = allowlist;
        if (piiEmail != null) k.piiEmail = piiEmail;
        if (piiPhone != null) k.piiPhone = piiPhone;
        if (piiIp != null) k.piiIp = piiIp;
        if (denyKeys != null) k.denyKeys = denyKeys;
        if (maskKeys != null) k.maskKeys = maskKeys;
        return k;
    }

    public Collection<Key> listKeys() { return keys.values(); }

    private static String gen(String prefix) {
        byte[] b = new byte[12]; new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder(prefix);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
