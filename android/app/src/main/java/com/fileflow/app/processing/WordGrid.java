package com.fileflow.app.processing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.fileflow.app.processing.LocalDocumentModel.*;

/** Ruled table topology, independent of Android runtime and text-alignment guesses. */
final class WordGrid {
    static final class Cell { int column, columnSpan, rowSpan; final List<Line> lines = new ArrayList<>(); }
    static final class Row { float y, height; final List<Cell> cells = new ArrayList<>(); }
    private static final float EPS = 1.5f;
    private WordGrid() { }
    static WordLayout.Block detect(List<Line> source, int start, List<WordLayout.Rule> rules) {
        Line first = source.get(start);
        List<WordLayout.Rule> horizontal = new ArrayList<>(), vertical = new ArrayList<>(), tops = new ArrayList<>();
        for (WordLayout.Rule r : rules) {
            if (r.height < .3f && r.width > 20) {
                horizontal.add(r);
                if (r.y < first.y && first.y-r.y <= first.size*3 && r.x <= WordLayout.left(first)+EPS && r.x+r.width >= WordLayout.right(first)-EPS) tops.add(r);
            }
            if (r.width < .3f && r.height > 5) vertical.add(r);
        }
        tops.sort((a,b) -> Float.compare(b.y,a.y));
        for (WordLayout.Rule top : tops) {
            float x=top.x, edge=x+top.width, bottom=Float.MAX_VALUE; boolean hasLeft=false,hasRight=false;
            for(WordLayout.Rule v:vertical) if(v.y<=top.y+EPS && v.y+v.height>first.y) {
                if(Math.abs(v.x-x)<=EPS){hasLeft=true;bottom=Math.min(bottom,v.y+v.height);}
                if(Math.abs(v.x-edge)<=EPS){hasRight=true;bottom=Math.min(bottom,v.y+v.height);}
            }
            if(!hasLeft||!hasRight)continue;
            boolean closed=false;
            for(WordLayout.Rule h:horizontal)if(Math.abs(h.y-bottom)<=EPS&&h.x<=x+EPS&&h.x+h.width>=edge-EPS)closed=true;
            if(!closed)continue;
            List<WordLayout.Rule> hs=new ArrayList<>(),vs=new ArrayList<>();
            List<Float> xx=new ArrayList<>(Arrays.asList(x,edge)), yy=new ArrayList<>(Arrays.asList(top.y,bottom));
            for(WordLayout.Rule h:horizontal)if(h.y>=top.y-EPS&&h.y<=bottom+EPS&&h.x>=x-EPS&&h.x+h.width<=edge+EPS){hs.add(h);yy.add(h.y);}
            for(WordLayout.Rule v:vertical)if(v.x>=x-EPS&&v.x<=edge+EPS&&v.y>=top.y-EPS&&v.y+v.height<=bottom+EPS){vs.add(v);xx.add(v.x);}
            List<Float> xs=unique(xx),ys=unique(yy);int columns=xs.size()-1,rows=ys.size()-1;
            if(columns<2||columns>12||rows<2||rows>250||rows*columns>1000)continue;
            int end=start;while(end<source.size()&&source.get(end).y<bottom-EPS)end++;
            List<Line> lines=source.subList(start,end);boolean valid=true;
            for(Line l:lines)for(Span s:l.spans)if(s.x<x-EPS||s.x+s.width>edge+EPS)valid=false;
            if(!valid)continue;
            int[] parents=new int[rows*columns];for(int i=0;i<parents.length;i++)parents[i]=i;
            for(int r=0;r<rows;r++)for(int c=0;c<columns;c++) {
                int id=r*columns+c;
                if(c+1<columns&&!covered(vs,xs.get(c+1),ys.get(r),ys.get(r+1),true))join(parents,id,id+1);
                if(r+1<rows&&!covered(hs,ys.get(r+1),xs.get(c),xs.get(c+1),false))join(parents,id,id+columns);
            }
            Map<Integer,List<Integer>> groups=new LinkedHashMap<>();
            for(int i=0;i<parents.length;i++)groups.computeIfAbsent(root(parents,i),k->new ArrayList<>()).add(i);
            List<Row> grid=new ArrayList<>();
            for(int r=0;r<rows;r++){Row row=new Row();row.y=ys.get(r);row.height=ys.get(r+1)-row.y;grid.add(row);}
            for(List<Integer> ids:groups.values()) {
                int r0=rows,r1=0,c0=columns,c1=0;
                for(int id:ids){r0=Math.min(r0,id/columns);r1=Math.max(r1,id/columns);c0=Math.min(c0,id%columns);c1=Math.max(c1,id%columns);}
                if(ids.size()!=(r1-r0+1)*(c1-c0+1)){valid=false;break;}
                Cell cell=new Cell();cell.column=c0;cell.columnSpan=c1-c0+1;cell.rowSpan=r1-r0+1;
                for(Line l:lines){Line part=new Line();part.y=l.y;
                    for(Span s:l.spans)if(s.y>ys.get(r0)&&s.y<=ys.get(r1+1)&&s.x+s.width/2>=xs.get(c0)&&s.x+s.width/2<xs.get(c1+1)){part.spans.add(s);part.size=Math.max(part.size,s.size);}
                    if(!part.spans.isEmpty()){part.cells.add(part.spans);cell.lines.add(part);}
                }
                grid.get(r0).cells.add(cell);
            }
            if(!valid)continue;
            for(Row row:grid)row.cells.sort((a,b)->Integer.compare(a.column,b.column));
            WordLayout.Block block=new WordLayout.Block();block.rows=new ArrayList<>();block.grid=grid;block.widths=new ArrayList<>();block.lines.addAll(lines);
            block.x=x;block.y=top.y;block.width=edge-x;block.bordered=true;block.borderColor=top.color;block.borderSize=top.thickness;
            for(int c=0;c<columns;c++)block.widths.add(xs.get(c+1)-xs.get(c));
            return block;
        }
        return null;
    }
    private static List<Float> unique(List<Float> values){values.sort(Float::compare);List<Float> result=new ArrayList<>();for(float v:values)if(result.isEmpty()||v-result.get(result.size()-1)>EPS)result.add(v);return result;}
    private static int root(int[] parents,int id){while(parents[id]!=id){parents[id]=parents[parents[id]];id=parents[id];}return id;}
    private static void join(int[] p,int a,int b){p[root(p,b)]=root(p,a);}
    private static boolean covered(List<WordLayout.Rule> rules,float at,float low,float high,boolean vertical){
        List<float[]> ranges=new ArrayList<>();for(WordLayout.Rule r:rules)if(Math.abs((vertical?r.x:r.y)-at)<=EPS)ranges.add(new float[]{vertical?r.y:r.x,vertical?r.y+r.height:r.x+r.width});
        ranges.sort((a,b)->Float.compare(a[0],b[0]));float reach=low;for(float[] range:ranges){if(range[0]>reach+EPS)break;reach=Math.max(reach,range[1]);}return reach>=high-EPS;
    }
}
