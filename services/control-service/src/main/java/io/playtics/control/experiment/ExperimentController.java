package io.playtics.control.experiment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ExperimentController {
    private final ExperimentRepo repo;
    public ExperimentController(ExperimentRepo repo){ this.repo = repo; }

    public static class UpsertReq {
        public String id; public String projectId; public String name; public String status; public String salt; public Map<String, Object> config; }

    @PostMapping("/experiments")
    public ResponseEntity<Map<String,Object>> upsert(@RequestBody UpsertReq req){
        if (req.id == null || req.projectId == null) return ResponseEntity.badRequest().body(Map.of("error","missing id/projectId"));
        ExperimentEntity e = repo.findById(req.id).orElseGet(ExperimentEntity::new);
        e.id = req.id; e.projectId = req.projectId; e.name = req.name; e.status = req.status==null?"draft":req.status; e.salt = req.salt==null?"":req.salt;
        e.configJson = req.config==null?"{}":new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(req.config).toString();
        if (e.createdAt == null) e.createdAt = Instant.now(); e.updatedAt = Instant.now();
        repo.save(e);
        return ResponseEntity.ok(Map.of("ok",true));
    }

    @GetMapping("/experiments")
    public List<ExperimentEntity> list(@RequestParam String projectId, @RequestParam(required=false) String status){
        if (status!=null && !status.isEmpty()) return repo.findByProjectIdAndStatus(projectId, status);
        return repo.findByProjectId(projectId);
    }

    @PostMapping("/experiments/{id}/publish")
    public ResponseEntity<Map<String,Object>> publish(@PathVariable String id){
        return setStatus(id, "running");
    }

    @PostMapping("/experiments/{id}/pause")
    public ResponseEntity<Map<String,Object>> pause(@PathVariable String id){
        return setStatus(id, "paused");
    }

    @DeleteMapping("/experiments/{id}")
    public ResponseEntity<Map<String,Object>> delete(@PathVariable String id){
        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    private ResponseEntity<Map<String,Object>> setStatus(String id, String st){
        return repo.findById(id).map(e->{ e.status=st; e.updatedAt=Instant.now(); repo.save(e); return ResponseEntity.ok(Map.of("ok",true)); }).orElse(ResponseEntity.notFound().build());
    }

    // Public config endpoint for SDKs
    @GetMapping("/config/{projectId}")
    public List<Map<String,Object>> config(@PathVariable String projectId){
        List<ExperimentEntity> list = repo.findByProjectIdAndStatus(projectId, "running");
        List<Map<String,Object>> out = new ArrayList<>();
        for (ExperimentEntity e : list){
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", e.id); m.put("salt", e.salt==null?"":e.salt); m.put("name", e.name);
            try { m.put("config", new com.fasterxml.jackson.databind.ObjectMapper().readValue(e.configJson==null?"{}":e.configJson, Map.class)); } catch (Exception ex){ m.put("config", Map.of()); }
            out.add(m);
        }
        return out;
    }
}
