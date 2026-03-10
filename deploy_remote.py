#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
远程部署脚本
使用 paramiko 库进行 SSH 连接和文件传输
"""
import sys
import os
import io

# 设置 UTF-8 编码输出
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

try:
    import paramiko
except ImportError:
    print("错误: 需要安装 paramiko 库")
    print("请执行: pip install paramiko")
    sys.exit(1)

# 配置
HOST = "156.225.28.65"
PORT = 22
USER = "root"
PASSWORD = "qrmwNIKZ7693"
LOCAL_JAR = "target/solonclaw.jar"
REMOTE_DIR = "/root/solonclaw"

def deploy():
    print(f"正在连接到 {HOST}...")

    # 创建 SSH 客户端
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())

    try:
        # 连接
        ssh.connect(HOST, PORT, USER, PASSWORD)
        print("[OK] 连接成功")

        # 创建 SFTP 客户端
        sftp = ssh.open_sftp()

        # 确保远程目录存在
        try:
            sftp.stat(REMOTE_DIR)
        except IOError:
            print(f"创建远程目录: {REMOTE_DIR}")
            sftp.mkdir(REMOTE_DIR)

        # 上传 JAR 文件
        remote_jar = f"{REMOTE_DIR}/solonclaw.jar"
        print(f"正在上传: {LOCAL_JAR} -> {remote_jar}")
        sftp.put(LOCAL_JAR, remote_jar)
        print("[OK] 上传成功")

        # 重启服务
        print("\n正在重启服务...")
        commands = [
            f"cd {REMOTE_DIR}",
            "pkill -f solonclaw.jar || true",
            "nohup java -jar solonclaw.jar > solonclaw.log 2>&1 &",
            "sleep 3",
            "ps aux | grep solonclaw.jar | grep -v grep"
        ]

        for cmd in commands:
            print(f"$ {cmd}")
            stdin, stdout, stderr = ssh.exec_command(cmd)
            output = stdout.read().decode()
            error = stderr.read().decode()
            if output:
                print(output)
            if error:
                print(f"错误: {error}")

        # 测试健康检查
        print("\n正在进行健康检查...")
        stdin, stdout, stderr = ssh.exec_command("curl -s http://localhost:12345/api/health")
        health = stdout.read().decode()
        print(f"健康检查响应: {health}")

        sftp.close()
        ssh.close()

        print("\n[OK] 部署完成！")
        print(f"访问地址: http://{HOST}:12345")

    except Exception as e:
        print(f"[ERROR] 部署失败: {e}")
        ssh.close()
        sys.exit(1)

if __name__ == "__main__":
    if not os.path.exists(LOCAL_JAR):
        print(f"错误: 找不到 JAR 文件: {LOCAL_JAR}")
        print("请先执行: mvn package -DskipTests")
        sys.exit(1)

    deploy()
