# Android XML View 阴影方案对照

调研日期为 2026-11-03。以下比较以项目公开文档和源码为依据，未对第三方制品执行安装、兼容性或性能测试。

| 项目 | 适合的场景 | 与本库的差异 |
| --- | --- | --- |
| Xigong93/BoxShadowLayout | 在 XML 中通过 blur、spread、offset 和 inset 描述盒子阴影，是本次找到的最直接对照 | 源码使用一个 shadowDrawable，通过 shadowInset 在内、外效果之间选择，并修改父 ViewGroup 的 clipChildren 以允许阴影溢出。我们的容器能在同一层独立配置内外阴影，并在测量时给外阴影预留空间。[项目及源码](https://github.com/Xigong93/BoxShadowLayout/tree/main/boxShadowLayout) |
| lihangleo2/ShadowLayout | XML 卡片，支持四角单独设置、外阴影偏移、按边隐藏、selector 和 ripple | 文档公开一个扩散参数 hl_shadowLimit，没有列出独立的 blur、spread 和 inset 属性。pressed 是状态选择器。[作者的属性表](https://github.com/lihangleo2/ShadowLayout#attributes) |
| fornewid/neumorphism | 拟物化凹凸控件，支持明暗双色、光源方向、独立圆角及 flat、pressed、basin 三种形态 | 由光源和 elevation 描述视觉效果。neumorph_inset 是预防阴影裁切的留白。作者把项目定位为实验性示例。[项目用法](https://github.com/fornewid/neumorphism#usage) |

本库可在 README 中强调同一 XML 容器的内外阴影独立配置、负 spread 和外阴影留白。如果设计要求阴影覆盖邻近元素而不占布局空间，预留留白的行为也需要纳入选型。仅凭这批样本不能判断本库是唯一支持这些特性的实现。
