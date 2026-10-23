package com.fileflow.app.processing;

import static org.junit.Assert.*;
import static com.fileflow.app.processing.LocalDocumentModel.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;

public final class WordLayoutTest {
    private Span span(String text,float x,float y,float size) { return new Span(text,x,y,text.length()*size*.45f,size); }
    @Test public void paragraphsJoinContinuationsAndRespectVerticalSectionGaps() {
        Span heading=span("PROFILE",50,60,16); heading.font="Arial-Bold";
        List<WordLayout.Block> blocks=WordLayout.reconstruct(group(new ArrayList<>(Arrays.asList(heading,span("A paragraph starts here.",50,90,11),span("It continues on this line.",50,104,11),span("Another paragraph starts.",50,140,11),span("Its continuation.",50,154,11)))),Collections.emptyList());
        assertEquals(3,blocks.size()); assertEquals("Heading1",blocks.get(0).heading); assertEquals(2,blocks.get(1).lines.size()); assertEquals(2,blocks.get(2).lines.size());
    }
    @Test public void independentColumnsKeepColumnReadingOrderWithoutFalseTables() {
        List<Span> spans=new ArrayList<>();
        for(int i=0;i<4;i++){Span left=span("LEFT prose continues in its column.",50,90+i*14,10);left.width=210;spans.add(left);Span right=span("RIGHT unrelated prose in its column.",330,90+i*14,10);right.width=220;spans.add(right);}
        List<WordLayout.Block> blocks=WordLayout.reconstruct(group(spans),Collections.emptyList());
        assertEquals(2,blocks.size()); assertNull(blocks.get(0).rows); assertNull(blocks.get(1).rows);
        assertTrue(text(blocks.get(0).lines.get(3).spans).startsWith("LEFT")); assertTrue(text(blocks.get(1).lines.get(0).spans).startsWith("RIGHT"));
    }
    @Test public void longUniformStreamsHaveBoundedEditableParagraphsWithoutInventedHeadings() {
        List<Span> spans=new ArrayList<>();for(int i=0;i<30;i++)spans.add(span("Uniform source line without structural evidence",50,50+i*14,11));
        List<WordLayout.Block> blocks=WordLayout.reconstruct(group(spans),Collections.emptyList());
        assertEquals(3,blocks.size()); for(WordLayout.Block block:blocks){assertNull(block.heading);assertTrue(block.lines.size()<=12);}
    }
    @Test public void staggeredColumnsDoNotRequireMatchingBaselines() {
        List<Span> spans=new ArrayList<>();for(int i=0;i<4;i++){Span left=span("LEFT text",50,90+i*14,10);left.width=210;spans.add(left);Span right=span("RIGHT text",330,96+i*14,10);right.width=210;spans.add(right);}
        List<WordLayout.Block> blocks=WordLayout.reconstruct(group(spans),Collections.emptyList());assertEquals(2,blocks.size());assertTrue(text(blocks.get(0).lines.get(3).spans).startsWith("LEFT"));assertTrue(text(blocks.get(1).lines.get(0).spans).startsWith("RIGHT"));
    }
    @Test public void actualNativeWordPackageContainsStylesColorsListsPageGeometryAndEditableText() throws Exception {
        LocalWordDocument.Page page=new LocalWordDocument.Page();page.width=612;page.height=792;
        Span title=span("Example title",50,60,20);title.font="ABCDEF+Arial-BoldMT";title.color="123456";
        Span body=span("Unicode Caf\u00e9 \u0395\u03bb\u03bb\u03b7\u03bd\u03b9\u03ba\u03ac",50,100,11);body.font="Arial-ItalicMT";body.underline=true;
        page.lines=group(new ArrayList<>(Arrays.asList(title,body,span("This line continues the paragraph.",50,114,11),span("\u2022 Editable item",50,150,11))));
        byte[] bytes=LocalWordDocument.create(Collections.singletonList(page));OfficeOpenXml.validateDocx(bytes);
        String xml=part(bytes,"word/document.xml");
        assertTrue(xml.contains("w:framePr"));assertTrue(xml.contains("w:pStyle w:val=\"Heading1\"")||xml.contains("w:pStyle w:val=\"Title\""));
        assertTrue(xml.contains("w:color w:val=\"123456\""));assertTrue(xml.contains("<w:u w:val=\"single\"/>"));assertTrue(xml.contains("<w:numPr>"));
        assertTrue(xml.contains("w:w=\"12240\" w:h=\"15840\""));assertTrue(xml.contains("Unicode Caf\u00e9"));assertTrue(xml.contains("w:ascii=\"Arial\""));
        assertTrue(part(bytes,"word/numbering.xml").contains("w:numFmt w:val=\"bullet\""));
        assertTrue(part(bytes,"docProps/custom.xml").contains("Font binaries are not embedded"));
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
    private String part(byte[] bytes,String name) throws Exception {
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(entry.getName().equals(name)){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=zip.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8");}}
        throw new AssertionError("Missing "+name);
    }
}
