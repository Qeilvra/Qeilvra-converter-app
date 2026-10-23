package com.fileflow.app.processing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import static com.fileflow.app.processing.LocalDocumentModel.*;

/** Word-specific geometry analysis. Does not change the Excel detector. No Android UI/runtime calls. */
final class WordLayout {
    static final Pattern MARKER = Pattern.compile("^\\s*([\u2022\u25cf\u25a0\u25e6\u2023\u2043\u2219\uf0b7-]|\\d{1,3}[.)])\\s+");
    static final class Block {
        final List<Line> lines = new ArrayList<>();
        List<List<List<Span>>> rows;
        float x, y, width, leading;
        String heading, list, role;
        String borderColor = "000000"; float borderSize = .75f;
        boolean bordered;
    }
    static float left(Line line) { float value = Float.MAX_VALUE; for (Span s : line.spans) value = Math.min(value, s.x); return value; }
    static float right(Line line) { float value = 0; for (Span s : line.spans) value = Math.max(value, s.x + s.width); return value; }
    static boolean bold(Span s) { return s.font.toLowerCase(java.util.Locale.ROOT).matches(".*(bold|black|heavy|semibold|demi).*"); }
    static boolean italic(Span s) { return s.font.toLowerCase(java.util.Locale.ROOT).matches(".*(italic|oblique).*"); }
    static String list(Line line) { java.util.regex.Matcher match = MARKER.matcher(text(line.spans)); return match.find() ? (Character.isDigit(match.group(1).charAt(0)) ? "number" : "bullet") : null; }
    static final class Rule { float x, y, width, height, thickness; String color; }
    static Block table(List<Line> lines, int start, List<Rule> rules) {
        Line first = lines.get(start); int columns = first.cells.size();
        if (columns < 2 || columns > 8) return null;
        int end = start + 1;
        while (end < lines.size()) {
            Line line = lines.get(end); boolean aligned = line.cells.size() == columns;
            if (aligned) for (int c = 0; c < columns; c++) if (Math.abs(line.cells.get(c).get(0).x - first.cells.get(c).get(0).x) > Math.max(5, first.size * .6f)) aligned = false;
            if (!aligned || line.y - lines.get(end - 1).y > Math.max(45, first.size * 3)) break;
            end++;
        }
        int numeric = 0, borders = 0; boolean shortCells = true;
        for (int i = start; i < end; i++) {
            boolean hasNumber = false;
            for (List<Span> cell : lines.get(i).cells) {
                String value = text(cell).trim(); shortCells &= value.length() <= 48;
                hasNumber |= value.matches("[\\s$\u20ac\u00a3=+\\-\\d.,/%:()]+");
            }
            if (hasNumber) numeric++;
        }
        for (Rule rule : rules) if (rule.height < .3f && rule.width >= right(first) - left(first) - first.size * 2 && rule.y >= first.y - first.size * 2 && rule.y <= lines.get(end - 1).y + first.size) borders++;
        if (end - start < 2 || (borders < 2 && (end - start < 3 || !shortCells || numeric < Math.ceil((end - start - 1) / 2f)))) return null;
        Block block = new Block(); block.rows = new ArrayList<>(); block.x = left(first); block.y = first.y - first.size; block.bordered = borders >= 2;
        for (int i = start; i < end; i++) { block.lines.add(lines.get(i)); block.rows.add(lines.get(i).cells); block.width = Math.max(block.width, right(lines.get(i)) - block.x + 5); }
        if (block.bordered) for (Rule rule : rules) if (rule.height < .3f && rule.width >= block.width - first.size * 2 && rule.y >= first.y - first.size * 2 && rule.y <= lines.get(end - 1).y + first.size) {
            block.x = Math.min(block.x, rule.x); block.width = Math.max(block.width, rule.x + rule.width - block.x); block.y = Math.min(block.y, rule.y);
            block.borderColor = rule.color; block.borderSize = rule.thickness;
        }
        return block;
    }
    static List<List<Line>> regions(List<Line> lines, int depth) {
        List<List<Line>> result = new ArrayList<>();
        if (depth >= 3 || lines.size() < 3) { result.add(lines); return result; }
        float cut = 0; int best = 1;
        for (Line source : lines) for (int c = 1; c < source.cells.size(); c++) {
            List<Span> cell = source.cells.get(c - 1); Span last = cell.get(cell.size() - 1);
            float edge = last.x + last.width, next = source.cells.get(c).get(0).x;
            if (next - edge < Math.max(24, source.size * 2)) continue;
            float candidate = (edge + next) / 2; int count = cutScore(lines,candidate);
            if (count > best) { best = count; cut = candidate; }
        }
        for (int i=0;i<lines.size();i++) for(int j=i+1;j<Math.min(lines.size(),i+8);j++) {
            Line a=lines.get(i),b=lines.get(j); if(Math.abs(a.y-b.y)>Math.max(a.size,b.size)*3)break;
            float candidate=left(b)-right(a)>=24?(right(a)+left(b))/2:left(a)-right(b)>=24?(right(b)+left(a))/2:0;
            int count=candidate==0?0:cutScore(lines,candidate); if(count>best){best=count;cut=candidate;}
        }
        if (cut == 0) { result.add(lines); return result; }
        List<Line> a = new ArrayList<>(), b = new ArrayList<>();
        for (Line line : lines) {
            boolean crossing = false; for (Span s : line.spans) crossing |= s.x < cut && s.x + s.width > cut;
            if (crossing) {
                if (!a.isEmpty()) result.addAll(regions(a, depth + 1)); if (!b.isEmpty()) result.addAll(regions(b, depth + 1));
                a = new ArrayList<>(); b = new ArrayList<>(); List<Line> full = new ArrayList<>(); full.add(line); result.add(full);
            } else {
                Line l = new Line(), r = new Line(); l.y = r.y = line.y;
                for (Span s : line.spans) { Line target = s.x < cut ? l : r; target.spans.add(s); target.size = Math.max(target.size, s.size); }
                if (!l.spans.isEmpty()) { l.cells.add(l.spans); a.add(l); } if (!r.spans.isEmpty()) { r.cells.add(r.spans); b.add(r); }
            }
        }
        if (!a.isEmpty()) result.addAll(regions(a, depth + 1)); if (!b.isEmpty()) result.addAll(regions(b, depth + 1)); return result;
    }
    private static int cutScore(List<Line> lines,float candidate) {
        int leftCount=0,rightCount=0;float leftMin=Float.MAX_VALUE,rightMin=Float.MAX_VALUE,leftMax=-Float.MAX_VALUE,rightMax=-Float.MAX_VALUE;
        for(Line line:lines){boolean a=false,b=false,crossing=false;for(Span s:line.spans){a|=s.x+s.width<candidate-8;b|=s.x>candidate+8;crossing|=s.x<candidate&&s.x+s.width>candidate;}
            if(crossing)continue;if(a){leftCount++;leftMin=Math.min(leftMin,line.y);leftMax=Math.max(leftMax,line.y);}if(b){rightCount++;rightMin=Math.min(rightMin,line.y);rightMax=Math.max(rightMax,line.y);}}
        return Math.min(leftMax,rightMax)-Math.max(leftMin,rightMin)>=lines.get(0).size?Math.min(leftCount,rightCount):0;
    }
    static List<Block> reconstruct(List<Line> lines, List<Rule> rules) {
        Map<Float,Integer> sizes = new HashMap<>();
        for (Line line : lines) for (Span span : line.spans) { float size = Math.round(span.size * 2) / 2f; sizes.put(size, sizes.getOrDefault(size,0) + span.text.length()); }
        float body = sizes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(11f);
        List<Block> blocks = new ArrayList<>(); List<Line> pending = new ArrayList<>();
        for (int i = 0; i < lines.size();) {
            Block table = table(lines,i,rules);
            if (table == null) pending.add(lines.get(i++));
            else { paragraphs(pending,body,blocks); pending.clear(); blocks.add(table); i += table.lines.size(); }
        }
        paragraphs(pending,body,blocks); return blocks;
    }
    private static void paragraphs(List<Line> pending, float body, List<Block> blocks) {
        for (List<Line> region : regions(pending,0)) {
            if (region.isEmpty()) continue;
            List<Float> gaps = new ArrayList<>();
            for (int i = 1; i < region.size(); i++) { float gap = region.get(i).y - region.get(i-1).y; if (gap >= body * .8f && gap <= body * 1.8f) gaps.add(gap); }
            gaps.sort(Float::compare); float leading = gaps.isEmpty() ? body * 1.2f : gaps.get(gaps.size()/2);
            Block current = null;
            for (int i = 0; i < region.size(); i++) {
                Line line = region.get(i), previous = i > 0 ? region.get(i-1) : null;
                String value = text(line.spans).trim(); float gap = previous == null ? Float.MAX_VALUE : line.y - previous.y;
                boolean allBold = true; for (Span span : line.spans) allBold &= bold(span);
                String list = list(line), heading = null;
                if (list == null && value.length() <= 120 && value.split("\\s+").length <= 14 && (line.size >= body * 1.18f || (allBold && line.size >= body * 1.12f && gap >= leading * 1.25f) || (allBold && line.size >= body && gap >= leading * 1.35f && value.length() < 65 && value.matches("[^a-z]*[A-Z][^a-z]*")))) heading = line.size >= body * 1.8f ? "Title" : line.size >= body * 1.18f ? "Heading1" : "Heading2";
                float indent = previous == null ? 0 : left(line) - left(previous);
                boolean hanging = current != null && current.list != null && indent >= -2 && indent <= body * 3;
                boolean longBlock = current != null && (current.lines.size() >= 12 || (current.lines.size() >= 6 && text(previous.spans).trim().matches(".*[.!?]$")));
                if (current == null || longBlock || heading != null || current.heading != null || list != null || gap > leading * 1.35f || Math.abs(line.size - previous.size) > 1 || (Math.abs(indent) > Math.max(4, body * .5f) && !hanging)) {
                    current = new Block(); current.x = left(line); current.y = line.y; current.width = right(line) - current.x; current.leading = leading; current.heading = heading; current.list = list; blocks.add(current);
                }
                current.lines.add(line); current.x = Math.min(current.x,left(line)); current.width = Math.max(current.width,right(line)-current.x);
            }
        }
    }
    static String font(String name) {
        String clean = name.replaceFirst("^[A-Z]{6}\\+", "").replaceAll("(?i)[,-]?(bolditalic|boldoblique|bold|italic|oblique|regular|semibold|light|medium)(PSMT|MT)?$", "").replaceFirst("(?i)(PSMT|MT)$", "");
        switch (clean.replaceAll("[ _-]", "").toLowerCase(java.util.Locale.ROOT)) {
            case "helvetica": case "helveticaneue": case "arial": case "sansserif": return "Arial";
            case "times": case "timesnewroman": case "timesnewromanps": case "serif": return "Times New Roman";
            case "courier": case "couriernew": case "couriernewps": case "monospace": return "Courier New";
            default: return clean.isEmpty() ? "Arial" : clean;
        }
    }
}
