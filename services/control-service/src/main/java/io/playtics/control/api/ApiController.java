package io.playtics.control.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final ControlService svc;
    public ApiController(ControlService svc) { this.svc = svc; }

    @PostMapping("/projects")
    public Models.Project upsertProject(@RequestBody Models.Project req) {
        return svc.upsertProject(req.id, req.name);
    }

    @GetMapping("/projects")
    public List<Models.Project> listProjects() {
        return svc.listProjects();
    }

    @PostMapping("/keys")
    public Models.ApiKeyResp createKey(@RequestBody Models.CreateKeyReq req) {
        return svc.createKey(req.projectId, req.name);
    }

    @GetMapping("/keys/{apiKey}")
    public ResponseEntity<Models.KeyDetailResp> getKey(@PathVariable String apiKey) {
        var r = svc.getKey(apiKey);
        if (r == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(r);
    }

    @GetMapping("/keys")
    public List<Models.KeyDetailResp> listKeys() {
        return svc.listKeys();
    }

    public static class UpdatePolicyReq {
        public Integer rpm; public Integer ipRpm; public List<String> propsAllowlist;
        public String piiEmail; public String piiPhone; public String piiIp;
        public List<String> denyKeys; public List<String> maskKeys;
    }

    @PutMapping("/keys/{apiKey}/policy")
    public ResponseEntity<Models.KeyDetailResp> updatePolicy(@PathVariable String apiKey, @RequestBody UpdatePolicyReq req) {
        var r = new Models.KeyDetailResp();
        r.rpm = req.rpm; r.ipRpm = req.ipRpm; r.propsAllowlist = req.propsAllowlist; r.piiEmail = req.piiEmail; r.piiPhone = req.piiPhone; r.piiIp = req.piiIp; r.denyKeys = req.denyKeys; r.maskKeys = req.maskKeys;
        var out = svc.updatePolicy(apiKey, r);
        if (out == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(out);
    }
}
