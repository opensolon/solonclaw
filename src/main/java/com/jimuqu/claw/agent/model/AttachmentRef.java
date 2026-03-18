package com.jimuqu.claw.agent.model;

/**
 * 描述一条消息中保存到本地的附件引用信息。
 */
public class AttachmentRef {
    /** 附件类别，例如图片、音频或文件。 */
    private String type;
    /** 附件展示名称。 */
    private String name;
    /** 附件或附件元数据在本地的保存路径。 */
    private String path;

    /**
     * 创建一个空的附件引用对象。
     */
    public AttachmentRef() {
    }

    /**
     * 按完整字段创建附件引用对象。
     *
     * @param type 附件类别
     * @param name 附件名称
     * @param path 本地路径
     */
    public AttachmentRef(String type, String name, String path) {
        this.type = type;
        this.name = name;
        this.path = path;
    }

    /**
     * 返回附件类别。
     *
     * @return 附件类别
     */
    public String getType() {
        return type;
    }

    /**
     * 设置附件类别。
     *
     * @param type 附件类别
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 返回附件名称。
     *
     * @return 附件名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置附件名称。
     *
     * @param name 附件名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 返回附件本地路径。
     *
     * @return 本地路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 设置附件本地路径。
     *
     * @param path 本地路径
     */
    public void setPath(String path) {
        this.path = path;
    }
}
