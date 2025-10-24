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
        // Basic config validation server-side to prevent bad payloads stored
        String verr = validateConfig(req.config);
        if (verr != null && !verr.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", verr));
        }
        ExperimentEntity e = repo.findById(req.id).orElseGet(ExperimentEntity::new);
        e.id = req.id; e.projectId = req.projectId; e.name = req.name; e.status = req.status==null?"draft":req.status; e.salt = req.salt==null?"":req.salt;
        e.configJson = req.config==null?"{}":new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(req.config).toString();
        if (e.createdAt == null) e.createdAt = Instant.now(); e.updatedAt = Instant.now();
        repo.save(e);
        return ResponseEntity.ok(Map.of("ok",true));
    }

    @GetMapping("/experiments")
    public Object list(@RequestParam String projectId,
                       @RequestParam(required=false) String status,
                       @RequestParam(required=false) Integer page,
                       @RequestParam(required=false) Integer size){
        if (page==null || size==null) {
            if (status!=null && !status.isEmpty()) return repo.findByProjectIdAndStatus(projectId, status);
            return repo.findByProjectId(projectId);
        }
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(0,page), Math.max(1,size));
        var pg = repo.pageBy(projectId, (status==null||status.isEmpty())?null:status, pageable);
        return java.util.Map.of("items", pg.getContent(), "total", pg.getTotalElements());
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

    // Export all experiments (admin)
    @GetMapping("/experiments/export")
    public List<Map<String,Object>> exportAll(@RequestParam String projectId){
        List<ExperimentEntity> list = repo.findByProjectId(projectId);
        List<Map<String,Object>> out = new ArrayList<>();
        for (ExperimentEntity e : list){
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", e.id); m.put("projectId", e.projectId); m.put("name", e.name); m.put("status", e.status); m.put("salt", e.salt);
            try { m.put("config", new com.fasterxml.jackson.databind.ObjectMapper().readValue(e.configJson==null?"{}":e.configJson, Map.class)); } catch (Exception ex){ m.put("config", Map.of()); }
            out.add(m);
        }
        return out;
    }

    // Minimal schema validation. Keep in sync with UI checks.
    private String validateConfig(Map<String, Object> cfg){
        if (cfg == null) return null; // allow empty config
        Object vars = cfg.get("variants");
        if (!(vars instanceof List)) return "variants required (array)";
        List<?> vs = (List<?>) vars; if (vs.isEmpty()) return "variants must not be empty"; if (vs.size() < 2) return "at least 2 variants required";
        int sum = 0; java.util.HashSet<String> names = new java.util.HashSet<>();
        for (Object o : vs){
            if (!(o instanceof Map)) return "variant must be object";
            Map<?,?> m = (Map<?,?>) o;
            Object name = m.get("name"); if (!(name instanceof String) || ((String)name).isEmpty()) return "variant.name required";
            if (name instanceof String) { String nn = ((String) name).trim(); if (names.contains(nn)) return "variant.name must be unique"; names.add(nn); }
            Object w = m.get("weight"); if (w != null && !(w instanceof Number)) return "variant.weight must be number";
            if (w instanceof Number) { int wi = ((Number) w).intValue(); if (wi < 0) return "variant.weight must be >= 0"; sum += wi; } else { sum += 1; }
        }
        if (sum <= 0) return "sum of weights must be > 0";
        // Optional targeting
        Object tg = cfg.get("targeting");
        if (tg != null){
            if (!(tg instanceof Map)) return "targeting must be object";
            Map<?,?> t = (Map<?,?>) tg;
            Object plat = t.get("platform"); if (plat != null && !(plat instanceof List)) return "targeting.platform must be array";
            Object ctry = t.get("countries"); if (ctry != null && !(ctry instanceof List)) return "targeting.countries must be array";
            Object minv = t.get("appVersionMin"); if (minv != null && !(minv instanceof String)) return "targeting.appVersionMin must be string";
            Object maxv = t.get("appVersionMax"); if (maxv != null && !(maxv instanceof String)) return "targeting.appVersionMax must be string";
            if (minv instanceof String && maxv instanceof String){
                if (compareSemver((String) minv, (String) maxv) > 0) return "appVersionMin must be <= appVersionMax";
            }
        }
        // Optional metrics
        Object metrics = cfg.get("metrics");
        if (metrics != null){
            if (!(metrics instanceof Map)) return "metrics must be object";
            Map<?,?> m = (Map<?,?>) metrics;
            Object primary = m.get("primary"); if (primary != null && !(primary instanceof String)) return "metrics.primary must be string";
            Object secondary = m.get("secondary"); if (secondary != null && !(secondary instanceof List)) return "metrics.secondary must be array";
        }
        return null;
    }

    // Very small semver comparator: compares dot-separated integers, ignores qualifiers.
    private int compareSemver(String a, String b){
        String[] as = a.split("\\."); String[] bs = b.split("\\.");
        int n = Math.max(as.length, bs.length);
        for (int i=0;i<n;i++){
            int ai = i<as.length ? parseIntSafe(as[i]) : 0;
            int bi = i<bs.length ? parseIntSafe(bs[i]) : 0;
            if (ai != bi) return ai - bi;
        }
        return 0;
    }
    private int parseIntSafe(String s){ try { return Integer.parseInt(s.replaceAll("[^0-9]","")); } catch(Exception e){ return 0; } }
}
