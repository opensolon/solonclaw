package com.jimuqu.solon.claw.support.update;

import cn.hutool.core.io.FileUtil;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.noear.snack4.ONode;

/** 自更新重启器，在独立 JVM 中完成 jar 替换与重启。 */
public class SelfUpdateLauncher {
    public static void main(String[] args) throws Exception {
        if (args == null || args.length < 4) {
            return;
        }

        File targetJar = new File(args[0]);
        File downloadedJar = new File(args[1]);
        File argsFile = new File(args[2]);
        File logFile = new File(args[3]);
        FileUtil.mkParentDirs(logFile);
        append(logFile, "SelfUpdateLauncher start: " + new java.util.Date() + "\n");

        Thread.sleep(4000L);

        boolean replaced = false;
        for (int i = 0; i < 30; i++) {
            try {
                Files.move(
                        downloadedJar.toPath(),
                        targetJar.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                replaced = true;
                break;
            } catch (Exception ignored) {
                try {
                    Files.copy(
                            downloadedJar.toPath(),
                            targetJar.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(downloadedJar.toPath());
                    replaced = true;
                    break;
                } catch (Exception innerIgnored) {
                    Thread.sleep(1000L);
                }
            }
        }

        if (!replaced) {
            append(logFile, "SelfUpdateLauncher failed to replace target jar\n");
            return;
        }

        List<String> command = new ArrayList<String>();
        command.add(
                new File(
                                new File(System.getProperty("java.home"), "bin"),
                                System.getProperty("os.name", "").toLowerCase().contains("win")
                                        ? "java.exe"
                                        : "java")
                        .getAbsolutePath());
        command.add("-jar");
        command.add(targetJar.getAbsolutePath());

        if (argsFile.isFile()) {
            ONode argsNode = ONode.ofJson(FileUtil.readUtf8String(argsFile));
            for (int i = 0; i < argsNode.size(); i++) {
                String value = argsNode.get(i).getString();
                if (value != null) {
                    command.add(value);
                }
            }
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(targetJar.getParentFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        builder.start();
        append(logFile, "SelfUpdateLauncher restarted application with updated jar\n");
    }

    private static void append(File file, String text) {
        try {
            FileUtil.appendUtf8String(text, file);
        } catch (Exception ignored) {
        }
    }
}
