package com.fileflow.server.service;

import com.fileflow.server.config.ProcessingProperties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.apache.pdfbox.cos.COSDictionary;
import org.springframework.stereotype.Service;

@Service
public class PdfCompressionService {
    public enum Level {
        LOW(2400, .88f, 220), BALANCED(1800, .75f, 160), HIGH(1200, .58f, 110);
        final int maxDimension;
        final float quality;
        final int dpi;
        Level(int maxDimension, float quality, int dpi) { this.maxDimension = maxDimension; this.quality = quality; this.dpi = dpi; }
    }

    public record Result(byte[] bytes, boolean reduced, int imagesOptimized) { }
    private final ProcessingProperties properties;
    private final ProcessRunner runner;

    public PdfCompressionService(ProcessingProperties properties, ProcessRunner runner) {
        this.properties = properties;
        this.runner = runner;
    }

    public Result compress(byte[] original, Level level) {
        byte[] candidate;
        int optimized = 0;
        long deadline = System.nanoTime() + properties.timeout().toNanos();
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(original), MemoryUsageSetting.setupTempFileOnly());
             ByteArrayOutputStream output = new BoundedOutputStream()) {
            if (document.isEncrypted()) throw new ProcessingException("Password-protected PDFs are not supported.");
            if (document.getNumberOfPages() > properties.maxPages()) throw new ProcessingException("This PDF exceeds the configured page processing limit.");
            document.getDocumentInformation().setCustomMetadataValue("FileFlow", null);
            Set<COSDictionary> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            for (PDPage page : document.getPages()) optimized += optimizeResources(document, page.getResources(), level, visited, 0, deadline);
            document.save(output);
            candidate = output.toByteArray();
        } catch (IOException error) {
            throw new ProcessingException("The PDF could not be safely compressed.", error);
        }
        byte[] qpdf = optimizeStructure(candidate, deadline);
        if (qpdf.length < candidate.length) candidate = qpdf;
        if (candidate.length >= original.length) return new Result(original, false, 0);
        validate(candidate);
        return new Result(candidate, true, optimized);
    }

    private int optimizeResources(PDDocument document, PDResources resources, Level level, Set<COSDictionary> visited, int depth, long deadline) throws IOException {
        if (resources == null || depth > 20 || !visited.add(resources.getCOSObject())) return 0;
        int count = 0;
        for (COSName name : resources.getXObjectNames()) {
            if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) throw new ProcessingException(504, "PDF compression exceeded the processing time limit.");
            PDXObject object = resources.getXObject(name);
            if (object instanceof PDFormXObject form) {
                count += optimizeResources(document, form.getResources(), level, visited, depth + 1, deadline);
                continue;
            }
            if (!(object instanceof PDImageXObject image) || image.isStencil() || image.getWidth() < 300 || image.getHeight() < 300) continue;
            // Never decode giant embedded bitmaps simply because their compressed PDF is small.
            if ((long) image.getWidth() * image.getHeight() > 16_000_000) continue;
            BufferedImage source = image.getImage();
            if (source == null || source.getColorModel().hasAlpha()) continue;
            double scale = Math.min(1d, (double) level.maxDimension / Math.max(source.getWidth(), source.getHeight()));
            BufferedImage prepared = scale < .98 ? resize(source, scale) : source;
            PDImageXObject replacement = JPEGFactory.createFromImage(document, prepared, level.quality, level.dpi);
            if (replacement.getCOSObject().getLength() < image.getCOSObject().getLength()) {
                resources.put(name, replacement);
                count++;
            }
        }
        return count;
    }

    private static BufferedImage resize(BufferedImage source, double scale) {
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        return target;
    }

    private byte[] optimizeStructure(byte[] input, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining < 100_000_000L) return input;
        try (JobWorkspace workspace = JobWorkspace.create()) {
            Path source = workspace.resolve("source.pdf");
            Path output = workspace.resolve("optimized.pdf");
            Files.write(source, input);
            runner.run(List.of(properties.qpdfCommand(), "--object-streams=generate", "--stream-data=compress", "--recompress-flate", source.toString(), output.toString()), workspace.root(), "PDF structure optimization", Duration.ofNanos(remaining));
            if (!Files.isRegularFile(output) || Files.size(output) > 100L * 1024 * 1024) return input;
            return Files.readAllBytes(output);
        } catch (Exception unavailable) {
            return input;
        }
    }

    private static void validate(byte[] bytes) {
        try (PDDocument ignored = PDDocument.load(bytes)) {
            if (ignored.getNumberOfPages() == 0) throw new ProcessingException("The compressed PDF has no pages.");
        } catch (IOException error) {
            throw new ProcessingException("The compressed PDF failed validation.", error);
        }
    }
}
