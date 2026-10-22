package com.fileflow.server.service;

import com.fileflow.server.config.ProcessingProperties;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CapabilityService {
    private final ProcessingProperties properties;
    private final ProcessRunner runner;
    private Map<String, Boolean> cached;
    private long checkedAt;

    public CapabilityService(ProcessingProperties properties, ProcessRunner runner) {
        this.properties = properties;
        this.runner = runner;
    }

    public synchronized Map<String, Object> capabilities() {
        // Cache probes: a public health request must not spawn unbounded engine processes.
        if (cached == null || System.nanoTime() - checkedAt > 60_000_000_000L) {
            Map<String, Boolean> dependencies = new LinkedHashMap<>();
            boolean pdfToWord = false;
            try {
                List<String> template = OfficeConversionService.parseTemplate(properties.pdfToDocxCommand(), Path.of("source.pdf"), Path.of("result.docx"));
                int module = template.indexOf("-m");
                List<String> probe = module >= 0 && module + 1 < template.size()
                        ? new ArrayList<>(template.subList(0, module + 2)) : new ArrayList<>(List.of(template.getFirst()));
                probe.add(module >= 0 ? "--help" : "--version");
                pdfToWord = runner.available(probe);
            } catch (ProcessingException invalidConfiguration) { /* report unavailable */ }
            dependencies.put("pdf-to-docx", pdfToWord);
            dependencies.put("docx-to-pdf", runner.available(List.of(properties.libreOfficeCommand(), "--version")));
            dependencies.put("qpdf", runner.available(List.of(properties.qpdfCommand(), "--version")));
            cached = dependencies;
            checkedAt = System.nanoTime();
        }
        List<String> operations = new ArrayList<>(List.of("pdf-compress", "pdf-tables"));
        if (cached.get("pdf-to-docx")) operations.add("pdf-to-docx");
        if (cached.get("docx-to-pdf")) operations.add("docx-to-pdf");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", operations.size() == 4 ? "ready" : "degraded");
        result.put("maxFileBytes", properties.maxFileBytes());
        result.put("maxPages", properties.maxPages());
        result.put("operations", operations);
        result.put("dependencies", Map.copyOf(cached));
        result.put("dependencyCheck", "engine-startup-probe-cached-60-seconds");
        result.put("retention", "temporary-workspace-cleanup-attempted-after-request");
        return result;
    }

    public void require(String operation) {
        Object available = capabilities().get("operations");
        if (!(available instanceof List<?> operations) || !operations.contains(operation)) {
            String label = "pdf-to-docx".equals(operation) ? "PDF to Word" : "Word to PDF";
            throw new ProcessingException(503, label + " is unavailable because its conversion engine failed the dependency check. The service operator must install or repair that engine.");
        }
    }
}
