# TOOLS.md - 本地笔记

Skills 负责定义“工具怎么工作”，这个文件记录的是“你的这台机器/环境里有哪些特殊约定”。

## 这里适合写什么

例如：

- 摄像头名称和位置
- SSH 主机和别名
- 偏好的 TTS 声音
- 房间或设备昵称
- 本机特有的路径、目录、命令约定
- 任何和当前环境强绑定的说明

## 例子

```markdown
### Cameras

- living-room → 主区域，180° 广角
- front-door → 入口，带移动侦测

### SSH

- home-server → 192.168.1.100，user: admin

### TTS

- Preferred voice: "Nova"
- Default speaker: Kitchen speaker
```

## 为什么要单独放在这里

Skills 是可复用的共享能力，而你的环境是你自己的。把它们分开，更新 skill 时就不会覆盖你的本地笔记，也能避免把本地基础设施信息误带出去。

---

把所有能帮你更高效完成工作的环境细节写在这里。这是你的备忘单。
