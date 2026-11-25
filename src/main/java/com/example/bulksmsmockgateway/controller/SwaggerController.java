package com.example.bulksmsmockgateway.controller;

import com.example.bulksmsmockgateway.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;

@Controller
public class SwaggerController {
    private final ObjectNode contract;

    public SwaggerController(@Value("${sandbox.openapi-spec}") Resource source) throws IOException {
        contract = source.exists() ? loadContract(source) : null;
    }

    private static ObjectNode loadContract(Resource source) throws IOException {
        ObjectNode contract;
        try (var input = source.getInputStream()) {
            contract = (ObjectNode) YAMLMapper.builder().build().readTree(input);
        }
        contract.putArray("servers").addObject()
                .put("url", "/v1")
                .put("description", "BulkSMS sandbox");
        ((ObjectNode) contract.path("info"))
                .put("title", "BulkSMS Sandbox API")
                .put("description", "Test the BulkSMS API against this gateway. Use the sandbox token ID and secret in Authorize. Outbound messages are delivered to the configured test inbox.");
        ((ObjectNode) contract.at("/components/securitySchemes/basicAuth"))
                .put("description", "Use BULKSMS_TOKEN_ID as the username and BULKSMS_TOKEN_SECRET as the password.");
        return contract;
    }

    @GetMapping("/swagger-ui.html")
    String swaggerUi() {
        return "redirect:/swagger-ui/index.html";
    }

    @GetMapping(value = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    ObjectNode openApi() {
        if (contract == null) {
            throw ApiException.missing("Set SANDBOX_OPENAPI_SPEC to a readable OpenAPI file, or add bulksms-api-specification-docs.yaml to the project root and rebuild.");
        }
        return contract;
    }
}
