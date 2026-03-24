package com.jimuqu.claw.channel.weixin.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.jimuqu.claw.agent.workspace.AgentWorkspaceService;
import com.jimuqu.claw.channel.weixin.model.WeixinAccount;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责持久化微信账号信息与长轮询游标。
 */
public class WeixinAccountStoreService {
    /** 微信状态根目录。 */
    private final File rootDir;
    /** 账号目录。 */
    private final File accountsDir;
    /** 游标目录。 */
    private final File cursorsDir;
    /** 账号索引文件。 */
    private final File indexFile;
    /** 写锁对象。 */
    private final Object lock = new Object();

    /**
     * 创建微信账号存储服务。
     *
     * @param workspaceService 工作区服务
     */
    public WeixinAccountStoreService(AgentWorkspaceService workspaceService) {
        this.rootDir = workspaceService.resolveWithinWorkspace("./channels/weixin", "channels/weixin");
        this.accountsDir = FileUtil.mkdir(new File(rootDir, "accounts"));
        this.cursorsDir = FileUtil.mkdir(new File(rootDir, "cursors"));
        this.indexFile = new File(rootDir, "accounts.json");
    }

    /**
     * 将原始账号标识规范化为安全文件名。
     *
     * @param rawAccountId 原始账号标识
     * @return 规范化账号标识
     */
    public String normalizeAccountId(String rawAccountId) {
        String normalized = StrUtil.blankToDefault(rawAccountId, "").trim().replaceAll("[^0-9A-Za-z._-]", "-");
        return StrUtil.blankToDefault(normalized, "weixin-account");
    }

    /**
     * 保存一个微信账号。
     *
     * @param account 账号信息
     */
    public void saveAccount(WeixinAccount account) {
        if (account == null || StrUtil.isBlank(account.getAccountId())) {
            throw new IllegalArgumentException("微信账号信息不能为空");
        }
        synchronized (lock) {
            account.setSavedAt(System.currentTimeMillis());
            FileUtil.writeUtf8String(JSONUtil.toJsonStr(account), accountFile(account.getAccountId()));
            registerAccountId(account.getAccountId());
        }
    }

    /**
     * 读取一个微信账号。
     *
     * @param accountId 账号标识
     * @return 账号信息；不存在则返回 null
     */
    public WeixinAccount loadAccount(String accountId) {
        if (StrUtil.isBlank(accountId)) {
            return null;
        }
        synchronized (lock) {
            File file = accountFile(accountId);
            if (!file.exists()) {
                return null;
            }
            return JSONUtil.toBean(FileUtil.readUtf8String(file), WeixinAccount.class);
        }
    }

    /**
     * 返回全部已记录账号标识。
     *
     * @return 账号标识列表
     */
    public List<String> listAccountIds() {
        synchronized (lock) {
            if (!indexFile.exists()) {
                return new ArrayList<String>();
            }
            List<String> ids = JSONUtil.toList(FileUtil.readUtf8String(indexFile), String.class);
            return ids == null ? new ArrayList<String>() : ids;
        }
    }

    /**
     * 返回全部已保存账号。
     *
     * @return 账号列表
     */
    public List<WeixinAccount> listAccounts() {
        List<WeixinAccount> accounts = new ArrayList<WeixinAccount>();
        for (String accountId : listAccountIds()) {
            WeixinAccount account = loadAccount(accountId);
            if (account != null) {
                accounts.add(account);
            }
        }
        return accounts;
    }

    /**
     * 持久化长轮询游标。
     *
     * @param accountId 账号标识
     * @param cursor 游标
     */
    public void saveCursor(String accountId, String cursor) {
        if (StrUtil.isBlank(accountId)) {
            return;
        }
        synchronized (lock) {
            FileUtil.writeUtf8String(JSONUtil.createObj().set("get_updates_buf", StrUtil.blankToDefault(cursor, "")).toString(), cursorFile(accountId));
        }
    }

    /**
     * 读取长轮询游标。
     *
     * @param accountId 账号标识
     * @return 游标；不存在则返回空字符串
     */
    public String loadCursor(String accountId) {
        if (StrUtil.isBlank(accountId)) {
            return "";
        }
        synchronized (lock) {
            File file = cursorFile(accountId);
            if (!file.exists()) {
                return "";
            }
            return JSONUtil.parseObj(FileUtil.readUtf8String(file)).getStr("get_updates_buf", "");
        }
    }

    /**
     * 注册账号到索引文件。
     *
     * @param accountId 账号标识
     */
    public void registerAccountId(String accountId) {
        synchronized (lock) {
            List<String> ids = listAccountIds();
            if (!ids.contains(accountId)) {
                ids.add(accountId);
                FileUtil.writeUtf8String(JSONUtil.toJsonStr(ids), indexFile);
            }
        }
    }

    private File accountFile(String accountId) {
        return new File(accountsDir, normalizeAccountId(accountId) + ".json");
    }

    private File cursorFile(String accountId) {
        return new File(cursorsDir, normalizeAccountId(accountId) + ".json");
    }
}
