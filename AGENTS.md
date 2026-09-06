# FlashWall 项目专属规则

通用规则见：`E:\@imFile-Download\AI-Useful-Prompt\通用开发工作规则.md`。

- 本项目是 Android Gradle 项目，应用模块位于 `app`，使用项目自带 Gradle Wrapper。
- 这是壁纸相关应用；修改图片、壁纸、定时任务或后台行为时，注意 Android 生命周期、权限和后台限制。
- 默认不替我执行 Android Studio/Gradle 构建；修改完成后提醒：“已经修改完，可以去 as 构建了”。
- 不要提交 `local.properties`、签名文件、密钥、个人壁纸缓存或设备运行数据。
- Kotlin/Jetpack Compose 修改必须优先使用小范围补丁，避免整段重写或在函数中间盲目插入代码；修改前先确认目标函数、`if/when/lambda` 和 `@Composable` 的作用域。
- Kotlin 代码修改后必须检查受影响区域的 `{}`、`()`、`[]` 是否成对闭合，并确认页面函数没有被意外嵌套到其他函数或 lambda 内；遇到 `Expecting '}'`、`Unresolved reference` 等连锁错误时，先检查前方括号和作用域，再处理表面报错。
- 未执行 Gradle 构建时，也必须完成静态结构检查（括号配对、函数声明位置、`git diff --check`），并在交付说明中明确未进行编译验证。
