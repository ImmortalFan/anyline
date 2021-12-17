package org.anyline.office.docx.entity.html;

import com.sun.org.apache.xml.internal.security.keys.keyresolver.implementations.SingleKeyResolver;
import org.anyline.util.BasicUtil;
import org.anyline.util.BeanUtil;
import org.apache.commons.collections.map.AbstractMapDecorator;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.util.*;

public class Table {
    private String clazz;
    private List<Tr> trs = new ArrayList<>();
    private String header = null; //复杂的头表直接设置html
    private String footer = null;
    private Map<String,String> styles = new HashMap();
    private List<Integer> mergeRows = new ArrayList<>(); //根据内容合并行依据
    private Map<Integer, List<Integer>> refs = new HashMap<>(); //
    private List<Integer[]> mergeCols = new ArrayList<>();//根据内容合并列，开始列,合并数量
    private Element src;

    public Table(){}

    /**
     * 构造表格
     * @param rows 行数量
     * @param cols 列数量
     */
    public Table(int rows, int cols){
        trs = new ArrayList<>();
        for(int r=0; r<rows; r++){
            Tr tr = new Tr();
            tr.setTable(this);
            for(int c=0; c<cols; c++){
                Td td = new Td();
                tr.addTd(td);
            }
            trs.add(tr);
        }
    }
    public Td getTd(int row, int col){
        return trs.get(row).getTd(col);
    }
    public List<Tr> getTrs() {
        return trs;
    }

    public void setTrs(List<Tr> trs) {
        this.trs = trs;
    }

    public Map<String, String> getStyles() {
        return styles;
    }

    public void setStyles(Map<String, String> styles) {
        this.styles = styles;
    }
    public Tr getTr(int index){
        return trs.get(index);
    }

    public Table setTd(int rows, int cols, Td td){
        trs.get(rows).setTd(cols, td);
        return this;
    }

    public Element getSrc() {
        return src;
    }

    public void setSrc(Element src) {
        this.src = src;
    }

    /**
     * 添加行
     * @param tr tr
     * @return table
     */
    public Table addTr(Tr tr){
        trs.add(tr);
        tr.setTable(this);
        return this;
    }

    /**
     * 创建html
     * @param box 是否需要table标签
     * @return html
     */
    public String build(boolean box){
        merge();
        StringBuilder builder = new StringBuilder();
        int rows = trs.size();
        int cols = 0;
        if(rows>0){
            cols = trs.get(0).getTds().size();
        }
        if(box){
            builder.append("<table");
            if(null != clazz){
                builder.append(" class='").append(clazz).append("'");
            }
            if (null != styles && !styles.isEmpty()) {
                builder.append(" style='");
                for(String key:styles.keySet()){
                    builder.append(key).append(":").append(styles.get(key)).append(";");
                }
                builder.append("'");
            }
            builder.append(">");
        }
        if(null != header){
            builder.append(header);
        }
        for(Tr tr:trs){
            tr.build(builder);
            builder.append("\r\n");
        }
        if(null != footer){
            builder.append(footer);
        }
        if(box){
            builder.append("</table>");
        }
        return builder.toString();
    }
    /**
     * 根据内容是否相同，在右侧qty范围内检测需要合并的列数量
     * @param td 单元格
     * @param qty 检测范围
     * @return colspan 需要合并列的数量
     */
    private int checkColspan(Td td, int qty){
        int colspan= 1;
        int[] index = td.index();
        int end = index[1] + qty;
        String value = td.getText();
        if(null != value){
            for(int i=index[1]+1; i<=end; i++){
                Td cur = getTd(index[0], i);
                if(value.equals(cur.getText())){
                    colspan ++;
                }
            }
        }
        return colspan;
    }

    /**
     * 执行合并列
     */
    private void exeMergeCol(){
        int rows = trs.size();
        for(int r=0; r<rows; r++) {
            Tr tr = getTr(r);
            for(Integer[] mergeCol:mergeCols){
                int mergeIndex = mergeCol[0];
                int mergeQty = mergeCol[1];
                //如果所有值相同则合并
                for(int i=mergeIndex; i<mergeIndex+mergeQty; i++){
                    Td td = getTd(r,mergeIndex);
                    int colspan =checkColspan(td, mergeQty);
                    if(colspan > 1){
                        td.setColspan(colspan);
                        td.merge();
                    }
                }
            }
        }
    }

    /**
     * 根据内容是否相同检测需要合并行的数量
     * @param td 单元格
     * @return rowspan
     */
    private int checkRowspan(Td td){
        int qty= 1;
        int r = td.getRowIndex();
        int c = td.getColIndex();
        String value = td.getText();
        if(null != value) {
            int size = trs.size();
            for (int i = r + 1; i < size; i++) {
                Td cur = getTd(i, c);
                String cvalue = cur.getText();
                if (value.equals(cvalue)) {
                    List<Integer> curRefs = refs.get(c);
                    boolean refMerge = true; //依赖列是否已合并
                    if (null != curRefs) {
                        for (Integer refIndex : curRefs) {
                            Td prev = getTd(i, refIndex); //同一行的 依赖列
                            if(!prev.isRemove()){
                                refMerge = false;
                                break;
                            }
                        }
                        if(refMerge) {
                            qty++;
                        }
                    }
                }else{
                    break;
                }
            }
        }
        return qty;
    }

    /**
     * 执行合并行
     */
    private void exeMergeRow(){
        int rows = trs.size();
        for(int r=0; r<rows; r++) {
            Tr tr = getTr(r);
            for(int col:mergeRows) {
                Td td = tr.getTd(col);
                if(!td.isRemove()){
                    int rowspan = checkRowspan(td);
                    if(rowspan > 1){
                        td.setRowspan(rowspan);
                        td.merge();
                    }
                }
            }
        }
    }
    //根据内容合并
    private void merge(){
        exeMergeRow();
        exeMergeCol();
    }
    /**
     * 设置需要合并行的列下标
     * @param cols 依据列1,2,3(1,2) 第1,2,3列值相同时合并行,第3列合并的前提是第1,2列已合并
     * @return Table
     */
    public Table setMergeRow(String ... cols){
        for(String col:cols){
            List<Integer> refs = new ArrayList<>();
            int c = -1;
            if(col.contains("(")){
                col = col.trim();
                String[] ref = col.substring(col.indexOf("(")+1, col.length()-1).split(",");
                for(int i=0; i<ref.length; i++){
                    refs.add(Integer.parseInt(ref[i]));
                }
                col = col.substring(0,col.indexOf("("));
            }
            c = Integer.parseInt(col);
            mergeRows.add(c);
            this.refs.put(c, refs);
        }
        return this;
    }

    /**
     * 设置需要合并行的列
     * @param cols cols
     * @return Table
     */
    public Table setMergeRow(Integer ... cols){
        mergeRows = Arrays.asList(cols);
        return this;
    }

    /**
     * 设置需要合并的列(根据内容)
     * @param start 开始
     * @param qty 右侧合并范围
     * @return Table
     */
    public Table setMergeCol(int start, int qty){
        Integer[] merge = new Integer[2];
        merge[0] = start;
        merge[1] = qty;
        mergeCols.add(merge);
        return this;
    }

    /**
     * 单元格文本
     * @param row row
     * @param col col
     * @return String
     */
    public String getText(int row, int col){
        Td td = getTd(row, col);
        return td.getText();
    }
    public Table setText(int row, int col, String text){
        Td td = getTd(row, col);
        if(null != text) {
            td.setText(text);
        }
        return this;
    }

    public String getClazz() {
        return clazz;
    }

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getFooter() {
        return footer;
    }

    public void setFooter(String footer) {
        this.footer = footer;
    }
}
