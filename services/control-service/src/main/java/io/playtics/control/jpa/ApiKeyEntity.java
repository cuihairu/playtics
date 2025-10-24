package io.playtics.control.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {
    @Id
    public String apiKey;
    public String secret;
    public String projectId;
    public String name;
    public Integer rpm;
    public Integer ipRpm;
    public String propsAllowlist; // comma-separated
    public String piiEmail;  // allow|mask|drop
    public String piiPhone;  // allow|mask|drop
    public String piiIp;     // allow|coarse|drop
    public String denyKeys;  // comma-separated
    public String maskKeys;  // comma-separated
}
