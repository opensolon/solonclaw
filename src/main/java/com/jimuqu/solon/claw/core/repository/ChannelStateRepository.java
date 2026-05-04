package com.jimuqu.solon.claw.core.repository;

import com.jimuqu.solon.claw.core.enums.PlatformType;
import java.util.List;

/** 渠道轻量状态仓储。 */
public interface ChannelStateRepository {
    /** 读取单条状态值。 */
    String get(PlatformType platform, String scopeKey, String stateKey) throws Exception;

    /** 写入或覆盖单条状态值。 */
    void put(PlatformType platform, String scopeKey, String stateKey, String stateValue)
            throws Exception;

    /** 删除单条状态值。 */
    void delete(PlatformType platform, String scopeKey, String stateKey) throws Exception;

    /** 列出某个 scope 下的全部状态项。 */
    List<StateItem> list(PlatformType platform, String scopeKey) throws Exception;

    /** 状态项。 */
    class StateItem {
        private final String stateKey;
        private final String stateValue;
        private final long updatedAt;

        public StateItem(String stateKey, String stateValue, long updatedAt) {
            this.stateKey = stateKey;
            this.stateValue = stateValue;
            this.updatedAt = updatedAt;
        }

        public String getStateKey() {
            return stateKey;
        }

        public String getStateValue() {
            return stateValue;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }
    }
}
