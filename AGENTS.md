# FlashWall 项目专属规则

通用规则见：`E:\@imFile-Download\AI-Useful-Prompt\通用开发工作规则.md`。

- 本项目是 Android Gradle 项目，应用模块位于 `app`，使用项目自带 Gradle Wrapper。
- 这是壁纸相关应用；修改图片、壁纸、定时任务或后台行为时，注意 Android 生命周期、权限和后台限制。
- 默认不替我执行 Android Studio/Gradle 构建；修改完成后提醒：“已经修改完，可以去 as 构建了”。
- 不要提交 `local.properties`、签名文件、密钥、个人壁纸缓存或设备运行数据。
