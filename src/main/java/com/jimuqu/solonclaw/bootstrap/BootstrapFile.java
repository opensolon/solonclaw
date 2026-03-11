package com.jimuqu.solonclaw.bootstrap;

/**
 * 引导文件模型
 * <p>
 * 代表工作区中的一个引导文件，支持缺失检测
 *
 * @author SolonClaw
 */
public record BootstrapFile(
        /**
         * 文件名（不含路径）
         */
        String name,
        /**
         * 文件完整路径
         */
        String path,
        /**
         * 文件内容（如果存在）
         */
        String content,
        /**
         * 是否缺失
         */
        boolean missing
) {
    /**
     * 创建一个存在的引导文件
     */
    public static BootstrapFile of(String name, String path, String content) {
        return new BootstrapFile(name, path, content, false);
    }

    /**
     * 创建一个缺失的引导文件
     */
    public static BootstrapFile missing(String name, String path) {
        return new BootstrapFile(name, path, null, true);
    }

    /**
     * 检查是否有内容
     */
    public boolean hasContent() {
        return !missing && content != null;
    }
}
