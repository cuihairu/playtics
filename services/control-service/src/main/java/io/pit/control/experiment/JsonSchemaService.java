package io.pit.control.experiment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class JsonSchemaService {
    private final ObjectMapper om;
    private JsonSchema expSchema;

    public JsonSchemaService(ObjectMapper om) { this.om = om; }

    @PostConstruct
    public void init() throws Exception {
        // Load experiment config schema from classpath
        ClassPathResource res = new ClassPathResource("schemas/experiment-config.schema.json");
        try (InputStream is = res.getInputStream()) {
            JsonNode schemaNode = om.readTree(is);
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            this.expSchema = factory.getSchema(schemaNode);
        }
    }

    public List<String> validateExperimentConfig(Object config) {
        List<String> errors = new ArrayList<>();
        if (config == null) return errors;
        try {
            JsonNode node = om.valueToTree(config);
            Set<ValidationMessage> msgs = expSchema.validate(node);
            for (ValidationMessage m : msgs) {
                errors.add(m.getMessage());
            }
        } catch (Exception e) {
            errors.add("invalid config JSON");
        }
        return errors;
    }
}
