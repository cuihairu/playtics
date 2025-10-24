package io.playtics.control.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final MemoryStore store;
    public ApiController() { this.store = new MemoryStore(); }

    @PostMapping("/projects")
    public Models.Project upsertProject(@RequestBody Models.Project req) {
        return store.upsertProject(req.id, req.name);
    }

    @GetMapping("/projects")
    public List<Models.Project> listProjects() {
        return store.listProjects().stream().collect(Collectors.toList());
    }

    @PostMapping("/keys")
    public Models.ApiKeyResp createKey(@RequestBody Models.CreateKeyReq req) {
        var k = store.createKey(req.projectId, req.name);
        Models.ApiKeyResp resp = new Models.ApiKeyResp();
        resp.apiKey = k.apiKey; resp.secret = k.secret; resp.projectId = k.projectId; resp.name = k.name;
        return resp;
    }

    @GetMapping("/keys/{apiKey}")
    public ResponseEntity<Models.KeyDetailResp> getKey(@PathVariable String apiKey) {
        var k = store.getKey(apiKey);
        if (k == null) return ResponseEntity.notFound().build();
        Models.KeyDetailResp resp = new Models.KeyDetailResp();
        resp.apiKey = k.apiKey; resp.secret = k.secret; resp.projectId = k.projectId;
        resp.rpm = k.rpm; resp.ipRpm = k.ipRpm; resp.propsAllowlist = k.propsAllowlist;
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/keys")
    public List<Models.KeyDetailResp> listKeys() {
        return store.listKeys().stream().map(k -> {
            Models.KeyDetailResp r = new Models.KeyDetailResp();
            r.apiKey = k.apiKey; r.secret = k.secret; r.projectId = k.projectId; r.rpm = k.rpm; r.ipRpm = k.ipRpm; r.propsAllowlist = k.propsAllowlist; return r;
        }).collect(Collectors.toList());
    }

    public static class UpdatePolicyReq { public Integer rpm; public Integer ipRpm; public List<String> propsAllowlist; }

    @PutMapping("/keys/{apiKey}/policy")
    public ResponseEntity<Models.KeyDetailResp> updatePolicy(@PathVariable String apiKey, @RequestBody UpdatePolicyReq req) {
        var k = store.updatePolicy(apiKey, req.rpm, req.ipRpm, req.propsAllowlist);
        if (k == null) return ResponseEntity.notFound().build();
        Models.KeyDetailResp resp = new Models.KeyDetailResp();
        resp.apiKey = k.apiKey; resp.secret = k.secret; resp.projectId = k.projectId; resp.rpm = k.rpm; resp.ipRpm = k.ipRpm; resp.propsAllowlist = k.propsAllowlist;
        return ResponseEntity.ok(resp);
    }
}
