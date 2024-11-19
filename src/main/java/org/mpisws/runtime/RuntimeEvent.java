package org.mpisws.runtime;

import java.util.HashMap;
import java.util.Map;

public class RuntimeEvent {

    private RuntimeEventType type;
    private Long threadId;
    private Map<String, Object> params;

    public RuntimeEvent(RuntimeEventType type, Long threadId, Map<String, Object> params) {
        this.type = type;
        this.threadId = threadId;
        this.params = params;
    }

    public RuntimeEvent(RuntimeEventType type, Long threadId) {
        this.type = type;
        this.threadId = threadId;
        this.params = new HashMap<>();
    }

    public RuntimeEventType getType() {
        return type;
    }

    public Long getThreadId() {
        return threadId;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setType(RuntimeEventType type) {
        this.type = type;
    }

    public void setThreadId(Long threadId) {
        this.threadId = threadId;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public Object getParam(String key) {
        return params.get(key);
    }

    public void setParam(String key, Object value) {
        params.put(key, value);
    }

    public Object getParamAs(String key, Class<?> clazz) {
        return clazz.cast(params.get(key));
    }


    public static class Builder {
        private RuntimeEventType type;
        private Long threadId;
        private Map<String, Object> params;

        public Builder type(RuntimeEventType type) {
            this.type = type;
            return this;
        }

        public Builder threadId(Long threadId) {
            this.threadId = threadId;
            return this;
        }

        public Builder params(Map<String, Object> params) {
            this.params = params;
            return this;
        }

        public Builder param(String key, Object value) {
            if (params == null) {
                params = new HashMap<>();
            }
            params.put(key, value);
            return this;
        }

        public RuntimeEvent build() {
            return new RuntimeEvent(type, threadId, params);
        }
    }
}
