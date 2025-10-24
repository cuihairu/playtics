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

    private static String gen(String prefix) {
        byte[] b = new byte[12]; new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder(prefix);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
