package org.anyline.entity.operator;

import java.util.List;

public interface Compare {
    public Compare setValue(Object value);
    public Compare addValue(Object value);
    public Compare setValues(List<Object> values);
    public boolean compare(Object value);
}
