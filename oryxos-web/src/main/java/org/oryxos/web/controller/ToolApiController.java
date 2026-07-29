package org.oryxos.web.controller;

import org.oryxos.core.model.ToolDefinition;
import org.oryxos.core.port.ToolCatalog;
import org.oryxos.web.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools")
public class ToolApiController {
    private final ToolCatalog tools;

    public ToolApiController(ToolCatalog tools) {
        this.tools = tools;
    }

    @GetMapping
    public ApiResponse<java.util.List<ToolDefinition>> list() {
        return ApiResponse.ok(tools.definitions());
    }
}
