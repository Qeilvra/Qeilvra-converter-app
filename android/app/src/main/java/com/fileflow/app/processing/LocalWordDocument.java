package com.fileflow.app.processing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static com.fileflow.app.processing.LocalDocumentModel.*;
import static com.fileflow.app.processing.OfficeOpenXml.*;

/** Editable OOXML writer for native positioned paragraphs, independent of the browser implementation. */
final class LocalWordDocument {
    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
    static final String PACKAGE = "http://schemas.openxmlformats.org/package/2006/relationships";
    static final class Image { byte[] png; float x, y, width, height; }
    static final class Page {
        float width, height; List<Line> lines; List<Image> images = new ArrayList<>();
        List<WordLayout.Rule> rules = new ArrayList<>(); List<WordLayout.Block> blocks;
    }
    private final Set<String> fonts = new LinkedHashSet<>();
    private final StringBuilder numbering = new StringBuilder();
    private int numberId;
    private static int twips(float value) { return Math.round(value * 20); }
    static byte[] create(List<Page> pages) throws IOException { return new LocalWordDocument().write(pages); }
    private byte[] write(List<Page> pages) throws IOException {
        for (Page page : pages) if (page.blocks == null) page.blocks = WordLayout.reconstruct(page.lines, page.rules);
        java.util.Map<String,Integer> repeated = new java.util.HashMap<>();
        for (Page page : pages) {
            Set<String> values = new LinkedHashSet<>();
            for (WordLayout.Block block : page.blocks) if (block.rows == null && block.heading == null && (block.y <= page.height*.07f || block.y >= page.height*.93f)) values.add(key(block));
            for (String value : values) repeated.put(value,repeated.getOrDefault(value,0)+1);
        }
        for (Page page : pages) for (WordLayout.Block block : page.blocks) {
            if (block.rows != null || block.heading != null || (block.y > page.height * .07f && block.y < page.height * .93f)) continue;
            if (repeated.getOrDefault(key(block),0) >= 2) block.role = block.y < page.height / 2 ? "header" : "footer";
        }
        StringBuilder body = new StringBuilder(), relationships = new StringBuilder(), types = new StringBuilder();
        List<Image> images = new ArrayList<>(); List<String> headers = new ArrayList<>(), footers = new ArrayList<>();
        for (int p = 0; p < pages.size(); p++) {
            Page page = pages.get(p); StringBuilder header = new StringBuilder(), footer = new StringBuilder();
            for (WordLayout.Block block : page.blocks) {
                if (block.rows != null) body.append(table(block));
                else if ("header".equals(block.role)) header.append(paragraph(block));
                else if ("footer".equals(block.role)) footer.append(paragraph(block));
                else body.append(paragraph(block));
            }
            for (Image image : page.images) { images.add(image); body.append(image(image,images.size())); }
            headers.add(header.length() == 0 ? "<w:p/>" : header.toString()); footers.add(footer.length() == 0 ? "<w:p/>" : footer.toString());
            String id = Integer.toString(p+1);
            relationships.append("<Relationship Id=\"header").append(id).append("\" Type=\"").append(REL).append("header\" Target=\"header").append(id).append(".xml\"/><Relationship Id=\"footer").append(id).append("\" Type=\"").append(REL).append("footer\" Target=\"footer").append(id).append(".xml\"/>");
            types.append("<Override PartName=\"/word/header").append(id).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml\"/><Override PartName=\"/word/footer").append(id).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml\"/>");
            float left = page.width, top = page.height * .1f, right = 0;
            for (Line line : page.lines) { left = Math.min(left,WordLayout.left(line)); right = Math.max(right,WordLayout.right(line)); }
            for (WordLayout.Block block : page.blocks) if (block.role == null) top = Math.min(top,block.y - block.lines.get(0).size);
            String section = "<w:sectPr><w:headerReference w:type=\"default\" r:id=\"header"+id+"\"/><w:footerReference w:type=\"default\" r:id=\"footer"+id+"\"/><w:type w:val=\"nextPage\"/><w:pgSz w:w=\""+twips(page.width)+"\" w:h=\""+twips(page.height)+"\"/><w:pgMar w:top=\""+twips(Math.max(0,top))+"\" w:right=\""+twips(Math.max(0,page.width-right))+"\" w:bottom=\"200\" w:left=\""+twips(Math.max(0,left))+"\" w:header=\"0\" w:footer=\"0\"/></w:sectPr>";
            body.append(p+1 == pages.size() ? "<w:p><w:pPr><w:spacing w:after=\"0\" w:line=\"20\" w:lineRule=\"exact\"/></w:pPr></w:p>"+section : "<w:p><w:pPr><w:spacing w:after=\"0\" w:line=\"20\" w:lineRule=\"exact\"/>"+section+"</w:pPr></w:p>");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            put(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"png\" ContentType=\"image/png\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/><Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/><Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/><Override PartName=\"/docProps/custom.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.custom-properties+xml\"/>"+types+"</Types>");
            put(zip,"_rels/.rels","<Relationships xmlns=\""+PACKAGE+"\"><Relationship Id=\"doc\" Type=\""+REL+"officeDocument\" Target=\"word/document.xml\"/><Relationship Id=\"custom\" Type=\""+REL+"custom-properties\" Target=\"docProps/custom.xml\"/></Relationships>");
            put(zip,"word/document.xml","<w:document xmlns:w=\""+W+"\" xmlns:r=\""+REL.substring(0,REL.length()-1)+"\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:body>"+body+"</w:body></w:document>");
            put(zip,"word/styles.xml","<w:styles xmlns:w=\""+W+"\"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\"/><w:sz w:val=\"22\"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:before=\"0\" w:after=\"0\"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style><w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/><w:basedOn w:val=\"Normal\"/></w:style><w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:outlineLvl w:val=\"0\"/></w:pPr></w:style><w:style w:type=\"paragraph\" w:styleId=\"Heading2\"><w:name w:val=\"heading 2\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:outlineLvl w:val=\"1\"/></w:pPr></w:style></w:styles>");
            put(zip,"word/numbering.xml","<w:numbering xmlns:w=\""+W+"\">"+numbering+"</w:numbering>");
            String limitations = "Font binaries are not embedded. Requested font families: "+String.join(", ",fonts)+". Standard PDF fonts are mapped to Arial, Times New Roman or Courier New. Other fonts are requested by family and may be substituted by Word. Paragraphs/headings/tables are inferred. Uniform text has uncertain paragraph boundaries. Complex vectors, clipping, merged cells and bidirectional layout are not fully reconstructed.";
            put(zip,"docProps/custom.xml","<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/custom-properties\" xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\"><property fmtid=\"{D5CDD505-2E9C-101B-9397-08002B2CF9AE}\" pid=\"2\" name=\"FileFlowConversionReport\"><vt:lpwstr>"+escapeXml(limitations)+"</vt:lpwstr></property></Properties>");
            for (int p = 0; p < pages.size(); p++) { put(zip,"word/header"+(p+1)+".xml","<w:hdr xmlns:w=\""+W+"\">"+headers.get(p)+"</w:hdr>"); put(zip,"word/footer"+(p+1)+".xml","<w:ftr xmlns:w=\""+W+"\">"+footers.get(p)+"</w:ftr>"); }
            for (int i = 0; i < images.size(); i++) { zip.putNextEntry(new ZipEntry("word/media/image"+(i+1)+".png")); zip.write(images.get(i).png); zip.closeEntry(); relationships.append("<Relationship Id=\"image").append(i+1).append("\" Type=\"").append(REL).append("image\" Target=\"media/image").append(i+1).append(".png\"/>"); }
            relationships.append("<Relationship Id=\"styles\" Type=\"").append(REL).append("styles\" Target=\"styles.xml\"/><Relationship Id=\"numbering\" Type=\"").append(REL).append("numbering\" Target=\"numbering.xml\"/>");
            put(zip,"word/_rels/document.xml.rels","<Relationships xmlns=\""+PACKAGE+"\">"+relationships+"</Relationships>");
        }
        return output.toByteArray();
    }
    private static String key(WordLayout.Block block) { StringBuilder value = new StringBuilder(); for (Line l : block.lines) value.append(text(l.spans)); return (block.y < 100 ? "h:" : "f:")+value.toString().replaceAll("\\d+","#"); }
    private String paragraph(WordLayout.Block block) throws IOException {
        Line first = block.lines.get(0); float leading = block.lines.size() > 1 ? (block.lines.get(block.lines.size()-1).y-first.y)/(block.lines.size()-1) : Math.max(block.leading,first.size*1.12f);
        StringBuilder xml = new StringBuilder("<w:p><w:pPr>");
        if (block.heading != null) xml.append("<w:pStyle w:val=\"").append(block.heading).append("\"/>");
        xml.append("<w:framePr w:w=\"").append(twips(block.width+3)).append("\" w:x=\"").append(twips(block.x)).append("\" w:y=\"").append(twips(Math.max(0,first.y-leading+first.size*.26f))).append("\" w:hAnchor=\"page\" w:vAnchor=\"page\" w:wrap=\"none\" w:hSpace=\"0\" w:vSpace=\"0\" w:anchorLock=\"true\"/>");
        int strip = 0;
        java.util.regex.Matcher marker = WordLayout.MARKER.matcher(text(first.spans));
        if (block.list != null && marker.find()) {
            strip = marker.end(); int id = ++numberId; String symbol = marker.group(1); boolean bullet = "bullet".equals(block.list);
            int start = bullet ? 1 : Integer.parseInt(symbol.substring(0,symbol.length()-1));
            int advance=twips(prefixAdvance(first.spans,strip));
            numbering.append("<w:abstractNum w:abstractNumId=\"").append(id).append("\"><w:multiLevelType w:val=\"singleLevel\"/><w:lvl w:ilvl=\"0\"><w:start w:val=\"").append(start).append("\"/><w:numFmt w:val=\"").append(bullet ? "bullet" : "decimal").append("\"/><w:lvlText w:val=\"").append(escapeXml(bullet ? symbol : "%1"+symbol.charAt(symbol.length()-1))).append("\"/><w:lvlJc w:val=\"left\"/><w:pPr><w:ind w:left=\"").append(advance).append("\" w:hanging=\"").append(advance).append("\"/></w:pPr><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\"/><w:sz w:val=\"").append(Math.round(first.size*2)).append("\"/></w:rPr></w:lvl></w:abstractNum><w:num w:numId=\"").append(id).append("\"><w:abstractNumId w:val=\"").append(id).append("\"/></w:num>");
            xml.append("<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"").append(id).append("\"/></w:numPr>");
        }
        xml.append("<w:spacing w:before=\"0\" w:after=\"0\" w:line=\"").append(twips(leading)).append("\" w:lineRule=\"exact\"/><w:jc w:val=\"left\"/></w:pPr>");
        for (int i=0;i<block.lines.size();i++) {
            if(i>0) xml.append("<w:r><w:br/></w:r>");
            int continuation=0;
            if(i>0 && block.list!=null && Math.abs(block.lines.get(i).spans.get(0).x-block.x)<=2) {
                java.util.regex.Matcher spaces=PatternHolder.LEADING_SPACE.matcher(text(block.lines.get(i).spans)); if(spaces.find())continuation=spaces.end();
            }
            xml.append(runs(block.lines.get(i).spans,i==0?strip:continuation));
        }
        return xml.append("</w:p>").toString();
    }
    private static float prefixAdvance(List<Span> spans,int characters) {
        int remaining=characters; float start=spans.get(0).x,edge=start;
        for(Span span:spans){int take=Math.min(remaining,span.text.length());edge=span.x+span.width*take/Math.max(1,span.text.length());remaining-=take;if(remaining<=0)break;}
        return Math.max(1,edge-start);
    }
    private static final class PatternHolder { static final java.util.regex.Pattern LEADING_SPACE=java.util.regex.Pattern.compile("^\\s+"); }
    private String runs(List<Span> spans, int strip) throws IOException {
        StringBuilder xml = new StringBuilder(); Span previous = null;
        for (int i=0;i<spans.size();) {
            Span first = spans.get(i); StringBuilder value = new StringBuilder(); int end=i;
            while(end<spans.size()) {
                Span span=spans.get(end); if(!span.font.equals(first.font)||Math.abs(span.size-first.size)>.1f||!span.color.equals(first.color)||span.underline!=first.underline) break;
                if(previous!=null && span.x-previous.x-previous.width>span.size*.15f && !previous.text.endsWith(" ")&&!span.text.startsWith(" ")) value.append(' ');
                value.append(span.text); previous=span; end++;
            }
            String full=value.toString(); String content=full.substring(Math.min(strip,full.length())); strip=Math.max(0,strip-full.length());
            if(!content.isEmpty()) {
                String font=WordLayout.font(first.font); fonts.add(first.font+" -> "+font);
                xml.append("<w:r><w:rPr><w:rFonts w:ascii=\"").append(escapeXml(font)).append("\" w:hAnsi=\"").append(escapeXml(font)).append("\" w:cs=\"").append(escapeXml(font)).append("\" w:eastAsia=\"").append(escapeXml(font)).append("\"/>");
                xml.append(WordLayout.bold(first)?"<w:b/>":"<w:b w:val=\"0\"/>").append(WordLayout.italic(first)?"<w:i/>":"<w:i w:val=\"0\"/>");
                xml.append("<w:color w:val=\"").append(first.color).append("\"/><w:sz w:val=\"").append(Math.max(2,Math.round(first.size*2))).append("\"/>");
                if(first.underline)xml.append("<w:u w:val=\"single\"/>");
                xml.append("</w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(content)).append("</w:t></w:r>");
            }
            i=end;
        }
        return xml.toString();
    }
    private String table(WordLayout.Block block) throws IOException {
        if (block.grid != null) return gridTable(block);
        float pad=Math.max(0,block.rows.get(0).get(0).get(0).x-block.x);
        float size=block.lines.get(0).size,top=Math.max(0,block.lines.get(0).y-block.y-size*1.2f+size*.26f-(block.bordered?block.borderSize/2:0));
        StringBuilder xml = new StringBuilder("<w:tbl><w:tblPr><w:tblpPr w:horzAnchor=\"page\" w:vertAnchor=\"page\" w:tblpX=\"");
        xml.append(twips(block.x+pad)).append("\" w:tblpY=\"").append(twips(block.y)).append("\"/><w:tblW w:w=\"").append(twips(block.width)).append("\" w:type=\"dxa\"/><w:tblBorders>");
        for(String side:new String[]{"top","left","bottom","right","insideH","insideV"})xml.append("<w:").append(side).append(" w:val=\"").append(block.bordered?"single":"nil").append("\" w:sz=\"").append(Math.max(1,Math.round(block.borderSize*8))).append("\" w:color=\"").append(block.borderColor).append("\"/>");
        xml.append("</w:tblBorders><w:tblLayout w:type=\"fixed\"/><w:tblCellMar><w:top w:w=\"").append(twips(top)).append("\" w:type=\"dxa\"/><w:bottom w:w=\"0\" w:type=\"dxa\"/><w:left w:w=\"").append(twips(pad)).append("\" w:type=\"dxa\"/><w:right w:w=\"0\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr><w:tblGrid>");
        List<Float> widths = new ArrayList<>(); List<List<Span>> first=block.rows.get(0);
        for(int c=0;c<first.size();c++){float start=c==0?block.x:first.get(c).get(0).x-pad; float end=c+1<first.size()?first.get(c+1).get(0).x-pad:block.x+block.width; widths.add(Math.max(1,end-start)); xml.append("<w:gridCol w:w=\"").append(twips(widths.get(c))).append("\"/>");}
        xml.append("</w:tblGrid>");
        for(int r=0;r<block.rows.size();r++) {
            float height=r+1<block.lines.size()?block.lines.get(r+1).y-block.lines.get(r).y:r>0?block.lines.get(r).y-block.lines.get(r-1).y:block.lines.get(r).size*1.5f;
            xml.append("<w:tr><w:trPr><w:cantSplit/><w:trHeight w:val=\"").append(twips(height)).append("\" w:hRule=\"exact\"/></w:trPr>");
            for(int c=0;c<block.rows.get(r).size();c++)xml.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(twips(widths.get(c))).append("\" w:type=\"dxa\"/><w:vAlign w:val=\"top\"/></w:tcPr><w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"0\" w:line=\"").append(twips(block.lines.get(r).size*1.2f)).append("\" w:lineRule=\"exact\"/></w:pPr>").append(runs(block.rows.get(r).get(c),0)).append("</w:p></w:tc>");
            xml.append("</w:tr>");
        }
        return xml.append("</w:tbl>").toString();
    }
    private String gridTable(WordLayout.Block block) throws IOException {
        WordGrid.Cell firstCell=block.grid.get(0).cells.get(0);
        float tablePad=firstCell.lines.isEmpty()?0:Math.max(0,firstCell.lines.get(0).spans.get(0).x-block.x);
        StringBuilder xml=new StringBuilder("<w:tbl><w:tblPr><w:tblpPr w:horzAnchor=\"page\" w:vertAnchor=\"page\" w:tblpX=\"");
        xml.append(twips(block.x+tablePad)).append("\" w:tblpY=\"").append(twips(block.y)).append("\"/><w:tblW w:w=\"").append(twips(block.width)).append("\" w:type=\"dxa\"/><w:tblBorders>");
        for(String side:new String[]{"top","left","bottom","right","insideH","insideV"})xml.append("<w:").append(side).append(" w:val=\"single\" w:sz=\"").append(Math.max(1,Math.round(block.borderSize*8))).append("\" w:color=\"").append(block.borderColor).append("\"/>");
        xml.append("</w:tblBorders><w:tblLayout w:type=\"fixed\"/></w:tblPr><w:tblGrid>");
        for(float width:block.widths)xml.append("<w:gridCol w:w=\"").append(twips(width)).append("\"/>");
        xml.append("</w:tblGrid>");
        for(int r=0;r<block.grid.size();r++) {
            WordGrid.Row row=block.grid.get(r);xml.append("<w:tr><w:trPr><w:cantSplit/><w:trHeight w:val=\"").append(twips(row.height)).append("\" w:hRule=\"exact\"/></w:trPr>");
            float rowLeading=block.lines.get(0).size*1.2f;
            for(WordGrid.Cell candidate:row.cells)if(!candidate.lines.isEmpty())rowLeading=Math.max(rowLeading,candidate.lines.size()>1?(candidate.lines.get(candidate.lines.size()-1).y-candidate.lines.get(0).y)/(candidate.lines.size()-1):candidate.lines.get(0).size*1.2f);
            for(int c=0;c<block.widths.size();) {
                WordGrid.Cell cell=null;int origin=r;
                for(int i=0;i<=r;i++)for(WordGrid.Cell candidate:block.grid.get(i).cells)if(candidate.column==c&&i+candidate.rowSpan>r){cell=candidate;origin=i;}
                if(cell==null)throw new IOException("Invalid recovered Word table grid.");
                float width=0,left=block.x;for(int i=0;i<c;i++)left+=block.widths.get(i);for(int i=c;i<c+cell.columnSpan;i++)width+=block.widths.get(i);
                Line first=cell.lines.isEmpty()?null:cell.lines.get(0);float size=first==null?block.lines.get(0).size:first.size;
                float leading=rowLeading;
                float top=first==null?0:Math.max(0,first.y-block.grid.get(origin).y-leading+size*.26f-block.borderSize/2),pad=first==null?0:Math.max(0,first.spans.get(0).x-left);
                xml.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(twips(width)).append("\" w:type=\"dxa\"/><w:gridSpan w:val=\"").append(cell.columnSpan).append("\"/>");
                if(cell.rowSpan>1)xml.append("<w:vMerge w:val=\"").append(origin==r?"restart":"continue").append("\"/>");
                xml.append("<w:tcMar><w:top w:w=\"").append(twips(top)).append("\" w:type=\"dxa\"/><w:bottom w:w=\"0\" w:type=\"dxa\"/><w:left w:w=\"").append(twips(pad)).append("\" w:type=\"dxa\"/><w:right w:w=\"0\" w:type=\"dxa\"/></w:tcMar><w:vAlign w:val=\"top\"/></w:tcPr><w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"0\" w:line=\"").append(twips(leading)).append("\" w:lineRule=\"exact\"/></w:pPr>");
                if(origin==r)for(int i=0;i<cell.lines.size();i++){if(i>0)xml.append("<w:r><w:br/></w:r>");xml.append(runs(cell.lines.get(i).spans,0));}
                xml.append("</w:p></w:tc>");c+=cell.columnSpan;
            }
            xml.append("</w:tr>");
        }
        return xml.append("</w:tbl>").toString();
    }
    private static String image(Image image,int id) {
        long width=Math.max(1,Math.round(image.width*12700)),height=Math.max(1,Math.round(image.height*12700));
        return "<w:p><w:pPr><w:spacing w:after=\"0\" w:line=\"20\" w:lineRule=\"exact\"/></w:pPr><w:r><w:drawing><wp:anchor distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\" simplePos=\"0\" relativeHeight=\"0\" behindDoc=\"0\" locked=\"1\" layoutInCell=\"0\" allowOverlap=\"1\"><wp:simplePos x=\"0\" y=\"0\"/><wp:positionH relativeFrom=\"page\"><wp:posOffset>"+Math.round(image.x*12700)+"</wp:posOffset></wp:positionH><wp:positionV relativeFrom=\"page\"><wp:posOffset>"+Math.round(image.y*12700)+"</wp:posOffset></wp:positionV><wp:extent cx=\""+width+"\" cy=\""+height+"\"/><wp:wrapNone/><wp:docPr id=\""+id+"\" name=\"Image "+id+"\"/><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic><pic:nvPicPr><pic:cNvPr id=\"0\" name=\"Image\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"image"+id+"\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\""+width+"\" cy=\""+height+"\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r></w:p>";
    }
}
