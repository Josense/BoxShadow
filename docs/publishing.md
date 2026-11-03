# 发布 BoxShadowLayout

模块的 Maven 坐标由 group、artifactId 和 version 决定。1.0.0 使用 `io.github.josense:boxshadow:1.0.0`。

1. 运行 `./gradlew :demo:assembleDebug :boxshadow:packageDistribution`。可直接分享 `dist/boxshadow-1.0.0-maven.zip`。
2. 发布到 Maven Central 时，在本机或 CI 的机密环境变量中提供 ASCII-armored PGP 私钥 `BOXSHADOW_SIGNING_KEY` 和对应口令 `BOXSHADOW_SIGNING_PASSWORD`。将对应公钥发布到 Sonatype 支持的公钥服务器。[签名要求](https://central.sonatype.org/publish/requirements/gpg/)
3. 执行 `./gradlew :boxshadow:packageCentralBundle`。将 `boxshadow/build/central/boxshadow-1.0.0-central.zip` 上传到已验证命名空间的 Central Portal，检查校验结果后发布。[Portal 上传说明](https://central.sonatype.org/publish/publish-portal-upload/)
4. 用独立工程从 `mavenCentral()` 解析该版本并构建 APK，再更新 README 默认安装方式。

## GitHub 远程安装

README 的在线安装地址指向 GitHub 标签 `maven-1.0.0`。该标签保存解压后的 Maven 目录，`main` 保存对应源码。后续版本使用新的版本号、分发包和 `maven-<版本>` 标签，保留已发布版本的文件。发版后用独立工程从新标签地址构建，再更新 README 中的坐标与 URL。
