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
    public Object listKeys(@RequestParam(value = "q", required = false) String q,
                           @RequestParam(value = "projectId", required = false) String projectId,
                           @RequestParam(value = "page", required = false) Integer page,
                           @RequestParam(value = "size", required = false) Integer size) {
        if (page == null && size == null && (q == null && projectId == null)) {
            return svc.listKeys();
        }
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 50 : Math.max(1, size);
        return svc.searchKeys(projectId, q, p, s);
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

    @DeleteMapping("/keys/{apiKey}")
    public ResponseEntity<Map<String,Object>> deleteKey(@PathVariable String apiKey) {
        boolean ok = svc.deleteKey(apiKey);
        return ok ? ResponseEntity.ok(Map.of("deleted", true)) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<Map<String,Object>> deleteProject(@PathVariable String projectId) {
        long n = svc.deleteProject(projectId);
        return ResponseEntity.ok(Map.of("deleted","project","keys_deleted", n));
    }
}
