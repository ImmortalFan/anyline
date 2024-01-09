package org.anyline.data.listener;


import org.noear.solon.core.AppContext;

import java.util.List;

public interface DatasourceLoader {
    List<String> load(AppContext context);
}
