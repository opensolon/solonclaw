package com.jimuqu.solon.claw.core.repository;

/** 全局运行时设置仓储。 */
public interface GlobalSettingRepository {
    /** 读取设置值。 */
    String get(String key) throws Exception;

    /** 写入设置值。 */
    void set(String key, String value) throws Exception;

    /** 删除设置值。 */
    void remove(String key) throws Exception;
}
