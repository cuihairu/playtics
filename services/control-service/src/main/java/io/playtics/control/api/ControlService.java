package io.playtics.control.api;

import io.playtics.control.jpa.*;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ControlService {
    private final ProjectRepo projectRepo;
    private final ApiKeyRepo keyRepo;

    public ControlService(ProjectRepo projectRepo, ApiKeyRepo keyRepo) {
        this.projectRepo = projectRepo; this.keyRepo = keyRepo;
    }

    public Models.Project upsertProject(String id, String name) {
        ProjectEntity p = new ProjectEntity(); p.id = id; p.name = name; projectRepo.save(p);
        Models.Project out = new Models.Project(); out.id = p.id; out.name = p.name; return out;
    }

    public List<Models.Project> listProjects() {
        return projectRepo.findAll().stream().map(pe -> { Models.Project m = new Models.Project(); m.id = pe.id; m.name = pe.name; return m; }).collect(Collectors.toList());
    }

    public Models.ApiKeyResp createKey(String projectId, String name) {
        ApiKeyEntity e = new ApiKeyEntity();
        e.apiKey = gen("pk_"); e.secret = gen("sk_"); e.projectId = projectId; e.name = name; e.rpm = 600; e.ipRpm = 300;
        keyRepo.save(e);
        Models.ApiKeyResp out = new Models.ApiKeyResp(); out.apiKey = e.apiKey; out.secret = e.secret; out.projectId = e.projectId; out.name = e.name; return out;
    }

    public Models.KeyDetailResp getKey(String apiKey) {
        return keyRepo.findById(apiKey).map(this::toResp).orElse(null);
    }

    public List<Models.KeyDetailResp> listKeys() {
        return keyRepo.findAll().stream().map(this::toResp).collect(Collectors.toList());
    }

    public Models.KeyDetailResp updatePolicy(String apiKey, Models.KeyDetailResp req) {
        return keyRepo.findById(apiKey).map(e -> {
            if (req.rpm != null) e.rpm = req.rpm;
            if (req.ipRpm != null) e.ipRpm = req.ipRpm;
            if (req.propsAllowlist != null) e.propsAllowlist = String.join(",", req.propsAllowlist);
            if (req.piiEmail != null) e.piiEmail = req.piiEmail;
            if (req.piiPhone != null) e.piiPhone = req.piiPhone;
            if (req.piiIp != null) e.piiIp = req.piiIp;
            if (req.denyKeys != null) e.denyKeys = String.join(",", req.denyKeys);
            if (req.maskKeys != null) e.maskKeys = String.join(",", req.maskKeys);
            keyRepo.save(e);
            return toResp(e);
        }).orElse(null);
    }

    private Models.KeyDetailResp toResp(ApiKeyEntity e) {
        Models.KeyDetailResp r = new Models.KeyDetailResp();
        r.apiKey = e.apiKey; r.secret = e.secret; r.projectId = e.projectId; r.rpm = e.rpm; r.ipRpm = e.ipRpm;
        r.propsAllowlist = split(e.propsAllowlist);
        r.piiEmail = e.piiEmail; r.piiPhone = e.piiPhone; r.piiIp = e.piiIp;
        r.denyKeys = split(e.denyKeys); r.maskKeys = split(e.maskKeys);
        return r;
    }

    private static List<String> split(String s) {
        if (s == null || s.isBlank()) return null;
        String[] parts = s.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) { String t = p.trim(); if (!t.isEmpty()) out.add(t); }
        return out;
    }

    private static String gen(String prefix) {
        byte[] b = new byte[12]; new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder(prefix);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
