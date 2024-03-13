package org.anyline.metadata.differ;

import org.anyline.metadata.Function;
import org.anyline.metadata.Function;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 表或列之间的对比结果
 */
public class FunctionsDiffer implements MetadataDiffer {
    private List<Function> adds = new ArrayList<>();
    private List<Function> drops = new ArrayList<>();
    private List<Function> updates = new ArrayList<>();

    public boolean isEmpty(){
        return adds.isEmpty() && drops.isEmpty() && updates.isEmpty();
    }
    public static FunctionsDiffer compare(LinkedHashMap<String, Function> origins, LinkedHashMap<String, Function> dests){
        FunctionsDiffer differ = new FunctionsDiffer();
        List<Function> adds = new ArrayList<>();
        List<Function> drops = new ArrayList<>();
        List<Function> updates = new ArrayList<>();

        if(null != origins){
            origins = new LinkedHashMap<>();
        }
        if(null == dests){
            dests = new LinkedHashMap<>();
        }
        for(String key:origins.keySet()){
            Function origin = origins.get(key);
            Function dest = dests.get(key);
            if(null == dest){
                //新表不存在
                drops.add(origins.get(origin));
            }else {
                if(!origin.equals(dest)){
                    origin.setUpdate(dest, false, false);
                    updates.add(origin);
                }
            }
        }
        for(String key:dests.keySet()){
            if(!origins.containsKey(key)){
                adds.add(dests.get(key));
            }
        }
        differ.setAdds(adds);
        differ.setDrops(drops);
        differ.setUpdates(updates);
        return differ;
    }

    public List<Function> getAdds() {
        return adds;
    }

    public void setAdds(List<Function> adds) {
        this.adds = adds;
    }

    public List<Function> getDrops() {
        return drops;
    }

    public void setDrops(List<Function> drops) {
        this.drops = drops;
    }

    public List<Function> getUpdates() {
        return updates;
    }

    public void setUpdates(List<Function> updates) {
        this.updates = updates;
    }
}
