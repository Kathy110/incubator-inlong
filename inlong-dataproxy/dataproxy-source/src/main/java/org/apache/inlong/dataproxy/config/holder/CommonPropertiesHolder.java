/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.dataproxy.config.holder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.ClassUtils;
import org.apache.flume.Context;
import org.apache.inlong.dataproxy.config.loader.ClassResourceCommonPropertiesLoader;
import org.apache.inlong.dataproxy.config.loader.CommonPropertiesLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * CommonPropertiesHolder
 */
public class CommonPropertiesHolder {

    public static final Logger LOG = LoggerFactory.getLogger(CommonPropertiesHolder.class);
    public static final String KEY_COMMON_PROPERTIES = "common-properties-loader";
    public static final String DEFAULT_LOADER = ClassResourceCommonPropertiesLoader.class.getName();

    private static Map<String, String> props;

    /**
     * init
     */
    private static void init() {
        synchronized (KEY_COMMON_PROPERTIES) {
            if (props == null) {
                props = new ConcurrentHashMap<>();
                String loaderClassName = System.getenv(KEY_COMMON_PROPERTIES);
                loaderClassName = (loaderClassName == null) ? DEFAULT_LOADER : loaderClassName;
                try {
                    Class<?> loaderClass = ClassUtils.getClass(loaderClassName);
                    Object loaderObject = loaderClass.getDeclaredConstructor().newInstance();
                    if (loaderObject instanceof CommonPropertiesLoader) {
                        CommonPropertiesLoader loader = (CommonPropertiesLoader) loaderObject;
                        props.putAll(loader.load());
                        LOG.info("loaderClass:{},properties:{}", loaderClassName, props);
                    }
                } catch (Throwable t) {
                    LOG.error("Fail to init CommonPropertiesLoader,loaderClass:{},error:{}",
                            loaderClassName, t.getMessage());
                    LOG.error(t.getMessage(), t);
                }

            }
        }
    }

    /**
     * get props
     * 
     * @return the props
     */
    public static Map<String, String> get() {
        if (props != null) {
            return props;
        }
        init();
        return props;
    }

    /**
     * Gets value mapped to key, returning defaultValue if unmapped.
     * 
     * @param  key          to be found
     * @param  defaultValue returned if key is unmapped
     * @return              value associated with key
     */
    public static String getString(String key, String defaultValue) {
        return get().getOrDefault(key, defaultValue);
    }

    /**
     * Gets value mapped to key, returning null if unmapped.
     * 
     * @param  key to be found
     * @return     value associated with key or null if unmapped
     */
    public static String getString(String key) {
        return get().get(key);
    }

    /**
     * getStringFromContext
     * 
     * @param  context
     * @param  key
     * @param  defaultValue
     * @return
     */
    public static String getStringFromContext(Context context, String key, String defaultValue) {
        String value = context.getString(key);
        value = (value != null) ? value : props.getOrDefault(key, defaultValue);
        return value;
    }
}
