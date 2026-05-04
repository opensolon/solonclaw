package com.jimuqu.solon.claw.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.DownloadedFile;

/** Dashboard SPA 页面入口。 */
@Controller
public class DashboardPageController {
    private final DashboardAuthService authService;

    public DashboardPageController(DashboardAuthService authService) {
        this.authService = authService;
    }

    @Mapping("/")
    public DownloadedFile index(Context context) {
        return renderIndex(context);
    }

    @Mapping("/index.html")
    public DownloadedFile indexHtml(Context context) {
        return renderIndex(context);
    }

    @Mapping("/status")
    public DownloadedFile status(Context context) {
        return renderIndex(context);
    }

    @Mapping("/login")
    public DownloadedFile login(Context context) {
        return renderIndex(context);
    }

    @Mapping("/chat")
    public DownloadedFile chat(Context context) {
        return renderIndex(context);
    }

    @Mapping("/sessions")
    public DownloadedFile sessions(Context context) {
        return renderIndex(context);
    }

    @Mapping("/analytics")
    public DownloadedFile analytics(Context context) {
        return renderIndex(context);
    }

    @Mapping("/models")
    public DownloadedFile models(Context context) {
        return renderIndex(context);
    }

    @Mapping("/memory")
    public DownloadedFile memory(Context context) {
        return renderIndex(context);
    }

    @Mapping("/logs")
    public DownloadedFile logs(Context context) {
        return renderIndex(context);
    }

    @Mapping("/gateways")
    public DownloadedFile gateways(Context context) {
        return renderIndex(context);
    }

    @Mapping("/channels")
    public DownloadedFile channels(Context context) {
        return renderIndex(context);
    }

    @Mapping("/agents")
    public DownloadedFile agents(Context context) {
        return renderIndex(context);
    }

    @Mapping("/terminal")
    public DownloadedFile terminal(Context context) {
        return renderIndex(context);
    }

    @Mapping("/files")
    public DownloadedFile files(Context context) {
        return renderIndex(context);
    }

    @Mapping("/workspace")
    public DownloadedFile workspace(Context context) {
        return renderIndex(context);
    }

    @Mapping("/cron")
    public DownloadedFile cron(Context context) {
        return renderIndex(context);
    }

    @Mapping("/skills")
    public DownloadedFile skills(Context context) {
        return renderIndex(context);
    }

    @Mapping("/config")
    public DownloadedFile config(Context context) {
        return renderIndex(context);
    }

    @Mapping("/env")
    public DownloadedFile env(Context context) {
        return renderIndex(context);
    }

    private DownloadedFile renderIndex(Context context) {
        String html = loadIndexHtml();
        if (html == null) {
            context.status(503);
            return new DownloadedFile(
                            "text/plain;charset=UTF-8",
                            "Dashboard frontend not built".getBytes(StandardCharsets.UTF_8),
                            "error.txt")
                    .asAttachment(false);
        }

        String rendered =
                authService.canRevealToken(context) ? authService.injectToken(html) : html;
        return new DownloadedFile(
                        "text/html;charset=UTF-8",
                        rendered.getBytes(StandardCharsets.UTF_8),
                        "index.html")
                .asAttachment(false);
    }

    private String loadIndexHtml() {
        File devFile = new File(System.getProperty("user.dir"), "web/dist/index.html");
        if (devFile.exists()) {
            return FileUtil.readUtf8String(devFile);
        }

        InputStream stream = getClass().getClassLoader().getResourceAsStream("static/index.html");
        if (stream == null) {
            return null;
        }

        return IoUtil.read(stream, StandardCharsets.UTF_8);
    }
}
