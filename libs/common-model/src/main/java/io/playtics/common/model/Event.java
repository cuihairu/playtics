package io.playtics.common.model;

import java.util.Map;

public class Event {
    public String eventId;
    public String eventName;
    public String projectId;
    public String userId;     // optional
    public String deviceId;
    public String sessionId;  // optional
    public long tsClient;     // epoch millis
    public Long tsServer;     // epoch millis, nullable
    public String platform;
    public String appVersion;
    public String country;
    public Map<String, Object> props;
}
