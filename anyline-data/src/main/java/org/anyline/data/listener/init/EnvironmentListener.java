/*
 * Copyright 2006-2023 www.anyline.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.anyline.data.listener.init;

import org.anyline.data.datasource.DatasourceHolder;
import org.anyline.data.listener.DatasourceLoader;
import org.anyline.data.runtime.RuntimeHolder;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.event.AppLoadEndEvent;
import org.noear.solon.core.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("anyline.listener.data.environment")
public class EnvironmentListener implements EventListener<AppLoadEndEvent> {
    public static Logger log = LoggerFactory.getLogger(EnvironmentListener.class);
    @Inject(required = false)
    private static Map<String, DatasourceLoader> loaders = new HashMap<>();

    @Override
    public void onEvent(AppLoadEndEvent event) {
        AppContext context = Solon.context();
        DatasourceHolder.init();
        RuntimeHolder.init();
        List<String> ds = new ArrayList<>();
        for(DatasourceLoader loader:loaders.values()){
            ds.addAll(loader.load(context));
        }
    }
}
