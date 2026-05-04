# Design System Inspired by Codex-Manager

## 1. Visual Theme & Atmosphere

Codex-Manager 的管理端不是营销页，也不是终端风格的实验界面，而是一套典型的“桌面工作台”式后台 UI。整个体验建立在浅冷色背景、玻璃化面板、左侧导航和顶部状态栏之上，信息密度中等，强调可操作性、可扫描性和稳定的日常使用感。

这套语言最重要的不是某个单一配色，而是结构秩序:

- 左侧侧栏负责全局切换
- 顶部页头负责当前页面语义与运行状态
- 中央内容区是唯一工作区
- 卡片只用来承载操作和数据，不做装饰性拼贴

视觉上它属于“轻玻璃 + 现代系统后台”路线:

- 页面底色是带蓝青渐变的浅色背景
- 主要内容放在半透明白色面板上
- 边框比阴影更重要，但两者都很克制
- 大多数元素使用 12px 到 24px 的圆角
- 颜色集中在蓝色主色、灰蓝中性色，以及少量状态色

整体气质应当是:

- 稳定
- 清爽
- 传统后台布局
- 不炫技
- 但比普通表单后台更精致

**Key Characteristics:**

- 固定左侧菜单 + 顶部页头 + 单一主工作区
- 浅蓝白渐变背景，非纯白平面
- 半透明白色玻璃卡片与导航面板
- 使用蓝色作为唯一主交互色
- 大圆角但不软塌，推荐 12px / 16px / 20px / 24px
- 文字以系统无衬线字体为主，强调可读性
- 列表、表单、统计卡统一遵守同一套容器语言
- 不使用复古终端、噪点、强烈纹理或过度装饰

## 2. Color Palette & Roles

### Primary

- **Workbench Blue** (`#2563eb`): 主操作色，用于主按钮、当前导航、强调态。
- **Deep Slate** (`#0f172a`): 主文字颜色，用于标题与主要信息。
- **Mid Slate** (`#334155`): 次级高权重文字，用于辅助标题和结构文字。

### Secondary & Accent

- **Soft Blue Tint** (`rgba(37, 99, 235, 0.10)`): 选中背景、轻强调态。
- **Hover Blue Tint** (`rgba(37, 99, 235, 0.08)`): hover 背景。
- **Focus Ring Blue** (`rgba(37, 99, 235, 0.20)`): 焦点描边和外圈。

### Surface & Background

- **Sky Background** (`#edf4ff`): 页面主背景。
- **Glass Surface** (`rgba(255, 255, 255, 0.82)`): 主卡片与主工作区容器。
- **Glass Strong** (`rgba(255, 255, 255, 0.92)`): 头部、弹层、输入容器的高可读层。
- **Panel Wash** (`#f8fbff`): 内容区内部较轻的底色。

### Neutrals & Text

- **Muted Slate** (`#64748b`): 辅助文字、说明文案、说明标签。
- **Soft Border** (`rgba(148, 163, 184, 0.24)`): 标准边框。
- **Strong Border** (`rgba(255, 255, 255, 0.72)`): 玻璃容器高光边界。

### Status

- **Success Green** (`#16a34a`)
- **Warning Amber** (`#d97706`)
- **Danger Red** (`#dc2626`)

### Gradient System

- **Background Bloom**: 页面背景由左上蓝色光斑、右上青色光斑、底部白色提亮共同构成。
- **Surface Lift**: 卡片内部使用白到浅蓝白的轻渐变，而不是纯平色。

## 3. Typography Rules

### Font Family

- **Primary / UI**: `Segoe UI`, `PingFang SC`, `Microsoft YaHei`, `Noto Sans SC`, `sans-serif`
- **Display**: 与主字体保持一致，不额外引入高风格标题字体
- **Code / Mono**: `Cascadia Mono`, `SFMono-Regular`, `Consolas`, `monospace`

### Hierarchy

| Role | Size | Weight | Line Height | Notes |
|------|------|--------|-------------|-------|
| Page Title | 28px | 600 | 1.2 | 顶部页头标题 |
| Section Title | 20px | 600 | 1.3 | 卡片区块标题 |
| Card Title | 16px | 600 | 1.35 | 统计卡、列表卡 |
| Body | 14px | 400 | 1.6 | 默认正文 |
| Body Small | 13px | 400 | 1.5 | 次级说明 |
| Label | 12px | 500 | 1.4 | 表单标签、过滤器标签 |
| Micro | 11px | 500 | 1.4 | 状态说明、表头、辅助标签 |
| Mono Small | 12px | 500 | 1.4 | 路径、模型、标识符 |

### Principles

- 用字重和颜色区分层级，不靠夸张字形。
- 标题不做全大写，不做压缩字体，不做终端风格 tracking。
- 运营型后台以可读性优先，正文保持正常密度。
- 说明文字统一使用灰蓝色，不要过暗。
- 编码、路径、模型名等技术信息用等宽字体，但不大面积铺满。

## 4. Component Stylings

### Buttons

**Primary**

- 背景: Workbench Blue (`#2563eb`)
- 文字: 白色
- 圆角: 12px
- 阴影: 轻蓝色投影
- 用于主操作，如保存、创建、确认

**Outline**

- 半透明白底
- 1px 柔和边框
- hover 时背景更白
- 用于次要操作

**Ghost**

- 默认透明
- hover 时出现轻微蓝灰底
- 常用于工具栏按钮、列表操作

**Destructive**

- 低饱和红底或淡红描边
- 明确表达危险操作

### Cards & Containers

- 主卡片使用 20px 到 24px 圆角
- 容器背景为半透明白色，带轻微模糊
- 以边框和渐变体现层级，不依赖厚重阴影
- 卡片标题区域与内容区域可分层，但不要出现过厚分隔线

### Inputs & Forms

- 输入框使用白色半透明底
- 圆角 12px
- 高度统一在 40px 左右
- focus 时使用蓝色 ring 和更明显边框
- textarea 与 input 使用同一语言

### Navigation

**Global Sidebar**

- 固定在左侧
- 宽度展开约 240px，收起约 64px
- 顶部放品牌区
- 中段为导航项
- 底部放折叠动作和系统信息

**Top Header**

- 固定在主工作区顶部
- 左侧显示当前页面标题和说明
- 右侧显示运行状态或概要信息

**Nav Item**

- 默认透明
- hover 使用浅蓝底
- 当前项使用主色实底或高亮底
- 图标和文字左右排布，收起时只保留图标

### Badges

- 统一为圆角胶囊
- 高度小，字重中等
- success / warning / danger 使用浅底 + 深字
- outline 用于中性标签

### Tables & Lists

- 列表应放在卡片或半透明容器内
- 行 hover 只做轻微底色变化
- 表头不需要重度底色，可以用更浅的冷灰蓝分隔

## 5. Layout Principles

### App Shell

标准布局必须遵守:

1. 左侧全局导航固定
2. 顶部页头固定在内容区顶部
3. 中央只有一个纵向滚动区
4. 内容宽度不要无限放大，推荐 `max-width: 1600px-1680px`

### Spacing System

- 基础单位: 4px / 8px
- 常用间距: 8px, 12px, 16px, 20px, 24px, 32px
- 大区块之间优先使用 24px 或 32px
- 卡片内边距优先 20px

### Sidebar Behavior

- 桌面端保持常驻
- 可收起，但不能影响主内容可读性
- 移动端应允许抽屉式打开与关闭

### Page Composition

每个页面应优先组织为:

1. 页面头部操作区或筛选区
2. 核心内容卡片
3. 次级详情、列表或配置区域

避免:

- 顶部大 Hero
- 装饰性 Banner
- 无意义的大面积留白
- 终端式满屏文本视觉

## 6. Depth & Elevation

| Level | Treatment | Use |
|-------|-----------|-----|
| Level 0 | 渐变背景 | 页面底层 |
| Level 1 | 玻璃侧栏 / 顶栏 | 应用框架 |
| Level 2 | 玻璃卡片 | 数据区块、表单、列表 |
| Level 3 | 高可读白色弹层 | 下拉、提示、Toast |

Depth 的实现原则:

- 先靠背景与边框
- 再靠轻微模糊与半透明
- 最后才用阴影

不要:

- 使用黑色厚投影
- 使用霓虹 glow
- 使用强噪点或终端材质

## 7. Do's and Don'ts

### Do

- 用左侧菜单组织全局页面
- 用顶部页头说明当前工作区语义
- 保持浅色背景和冷色系统
- 把信息集中到统一的卡片容器语言中
- 让统计卡、筛选区、列表区保持同一圆角和边框规则
- 保持中文界面一致性，不引入中英切换入口

### Don't

- 不要回到深色复古终端风格
- 不要保留横向顶栏作为全局主导航
- 不要使用过多全大写、压缩字和奇特展示字体
- 不要让页面像宣传页或 landing page
- 不要把每个模块都做成厚重卡片堆叠
- 不要混入多个主色

## 8. Responsive Behavior

### Breakpoints

| Name | Width | Key Changes |
|------|-------|-------------|
| Mobile | <768px | 侧栏改为抽屉，页头保留菜单按钮 |
| Tablet | 768px-1024px | 侧栏可收起，内容两列酌情折叠 |
| Desktop | 1024px-1440px | 完整侧栏 + 顶栏 + 主内容 |
| Wide | >1440px | 内容区域增宽，但不失控拉伸 |

### Responsive Rules

- 侧栏在移动端不得长期占据页面宽度
- 顶部状态卡在小屏时折叠为更少信息
- 数据卡从 4 列逐步退到 2 列、1 列
- 表单和过滤器在小屏时上下堆叠

## 9. Agent Prompt Guide

### Quick Color Reference

- Background: `#edf4ff`
- Primary: `#2563eb`
- Foreground: `#0f172a`
- Muted text: `#64748b`
- Card surface: `rgba(255,255,255,0.82)`
- Border: `rgba(148,163,184,0.24)`

### Example Component Prompts

- “创建一个传统后台布局，左侧固定菜单，右侧是顶部页头和单一内容工作区，整体采用浅蓝背景和半透明白色卡片。”
- “设计一个统计卡：24px 圆角，半透明白底，轻蓝色阴影，标题 14px，数字 28px，说明文字 12px 灰蓝色。”
- “设计一个当前导航项：蓝色实底、白字、圆角 16px，图标与文字横排。”
- “设计一个顶部状态栏：左侧页标题和说明，右侧运行状态胶囊和版本信息。”

### Iteration Guide

1. 先确保左侧导航、顶栏和内容区的骨架正确
2. 再统一卡片、按钮、输入框的圆角和边框
3. 最后再细调各页面的局部信息层级
4. 保持中文界面，不引入语言切换
