package io.pit.control.api;

import java.util.List;

public class Models {
    public static class Project {
        public String id;
        public String name;
    }
    public static class ApiKeyResp {
        public String apiKey; // public key
        public String secret; // private secret
        public String projectId;
        public String name;
    }
    public static class CreateKeyReq {
        public String projectId;
        public String name;
    }
    public static class PolicyResp {
        public String projectId;
        public Integer rpm;
        public Integer ipRpm;
        public List<String> propsAllowlist;
    }
    public static class KeyDetailResp {
        public String apiKey;
        public String secret;
        public String projectId;
        public Integer rpm;
        public Integer ipRpm;
        public List<String> propsAllowlist;
        public String piiEmail;  // allow|mask|drop
        public String piiPhone;  // allow|mask|drop
        public String piiIp;     // allow|coarse|drop
        public List<String> denyKeys;
        public List<String> maskKeys;
    }
}
