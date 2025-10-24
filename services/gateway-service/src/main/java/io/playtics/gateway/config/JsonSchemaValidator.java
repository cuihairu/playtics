package io.playtics.gateway.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Set;

@Component
public class JsonSchemaValidator {
    private final ObjectMapper om;
    private final JsonSchema schema;

    public JsonSchemaValidator(ObjectMapper om) {
        this.om = om;
        try {
            ClassPathResource res = new ClassPathResource("schemas/playtics-event-schema.json");
            try (InputStream is = res.getInputStream()) {
                JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
                this.schema = factory.getSchema(is);
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to load JSON schema", e);
        }
    }

    public String validate(Object eventObj) {
        try {
            JsonNode node = om.valueToTree(eventObj);
            Set<ValidationMessage> errors = schema.validate(node);
            if (errors == null || errors.isEmpty()) return null;
            // return first error message
            return errors.iterator().next().getMessage();
        } catch (Exception e) {
            return "invalid_json";
        }
    }
}
