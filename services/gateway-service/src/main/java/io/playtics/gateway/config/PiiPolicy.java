package io.playtics.gateway.config;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PiiPolicy {
    public enum Mode { ALLOW, MASK, DROP }
    public enum IpMode { ALLOW, COARSE, DROP }

    private final Mode emailMode;
    private final Mode phoneMode;
    private final IpMode ipMode;
    private final Set<String> denyKeys;
    private final Set<String> maskKeys;

    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS = Pattern.compile("\\d");

    public PiiPolicy(Environment env) {
        String e = Binder.get(env).bind("playtics.pii.email", String.class).orElse("mask");
        String p = Binder.get(env).bind("playtics.pii.phone", String.class).orElse("mask");
        String ip = Binder.get(env).bind("playtics.pii.ip", String.class).orElse("coarse");
        this.emailMode = toMode(e);
        this.phoneMode = toMode(p);
        this.ipMode = toIpMode(ip);
        List<String> dks = Binder.get(env).bind("playtics.pii.denyKeys", List.class).orElse(Collections.emptyList());
        List<String> mks = Binder.get(env).bind("playtics.pii.maskKeys", List.class).orElse(Collections.emptyList());
        this.denyKeys = new HashSet<>(); for (Object o : dks) denyKeys.add(String.valueOf(o).toLowerCase(Locale.ROOT));
        this.maskKeys = new HashSet<>(); for (Object o : mks) maskKeys.add(String.valueOf(o).toLowerCase(Locale.ROOT));
    }

    private Mode toMode(String s) { return switch ((s==null?"":s).toLowerCase(Locale.ROOT)) { case "allow" -> Mode.ALLOW; case "drop" -> Mode.DROP; default -> Mode.MASK; }; }
    private IpMode toIpMode(String s) { return switch ((s==null?"":s).toLowerCase(Locale.ROOT)) { case "allow" -> IpMode.ALLOW; case "drop" -> IpMode.DROP; default -> IpMode.COARSE; }; }

    public boolean hasBlockedKeys(Map<String, Object> props) {
        if (props == null) return false;
        for (String k : props.keySet()) {
            if (denyKeys.contains(k.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public Map<String, Object> sanitizeProps(Map<String, Object> props) {
        if (props == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : props.entrySet()) {
            String key = e.getKey(); Object val = e.getValue();
            Object sv = sanitizeValue(key, val, 0);
            if (sv != null) out.put(key, sv);
        }
        return out;
    }

    private Object sanitizeValue(String key, Object val, int depth) {
        if (val == null) return null;
        if (depth >= 3) return null;
        if (val instanceof String) {
            String s = (String) val;
            String lk = key == null ? "" : key.toLowerCase(Locale.ROOT);
            // direct key masking
            if (maskKeys.contains(lk)) return maskAll(s);
            // email
            if (EMAIL.matcher(s).find()) {
                if (emailMode == Mode.DROP) return null;
                if (emailMode == Mode.MASK) return maskEmail(s);
            }
            // phone: detect if >=10 digits
            int digits = countDigits(s);
            if (digits >= 10) {
                if (phoneMode == Mode.DROP) return null;
                if (phoneMode == Mode.MASK) return maskPhone(s);
            }
            return s;
        }
        if (val instanceof Number || val instanceof Boolean) return val;
        if (val instanceof Map) {
            Map<?,?> m = (Map<?,?>) val;
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?,?> en : m.entrySet()) {
                if (en.getKey() == null) continue;
                Object sv = sanitizeValue(String.valueOf(en.getKey()), en.getValue(), depth+1);
                if (sv != null) out.put(String.valueOf(en.getKey()), sv);
            }
            return out;
        }
        if (val instanceof List) {
            List<?> l = (List<?>) val;
            List<Object> out = new ArrayList<>();
            int lim = Math.min(50, l.size());
            for (int i=0;i<lim;i++) {
                Object sv = sanitizeValue(key, l.get(i), depth+1);
                if (sv != null) out.add(sv);
            }
            return out;
        }
        return null;
    }

    public String sanitizeClientIp(String ip) {
        if (ip == null || ip.isEmpty()) return null;
        switch (ipMode) {
            case DROP: return null;
            case ALLOW: return ip;
            case COARSE:
            default:
                try {
                    InetAddress addr = InetAddress.getByName(ip);
                    if (addr instanceof Inet6Address) {
                        byte[] b = addr.getAddress();
                        // zero out lower 10 bytes (/48 approx)
                        for (int i = 6; i < 16; i++) b[i] = 0;
                        return InetAddress.getByAddress(b).getHostAddress();
                    } else {
                        String[] parts = ip.split("\\.");
                        if (parts.length == 4) return parts[0]+"."+parts[1]+"."+parts[2]+".0";
                        return ip;
                    }
                } catch (Exception e) { return null; }
        }
    }

    private String maskEmail(String s) {
        Matcher m = EMAIL.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String em = m.group();
            int at = em.indexOf('@');
            String masked = "***" + em.substring(at);
            m.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private int countDigits(String s) {
        int c=0; Matcher m = DIGITS.matcher(s); while (m.find()) c++; return c;
    }

    private String maskPhone(String s) {
        StringBuilder out = new StringBuilder();
        int digits = 0;
        for (int i=0;i<s.length();i++) {
            char ch = s.charAt(i);
            if (Character.isDigit(ch)) {
                digits++;
                if (digits <= Math.max(0, countDigits(s)-2)) out.append('x');
                else out.append(ch);
            } else out.append(ch);
        }
        return out.toString();
    }

    private String maskAll(String s) { return "***"; }
}
