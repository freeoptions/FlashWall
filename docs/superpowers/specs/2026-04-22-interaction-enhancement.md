# 桌面双击标记交互增强设计 (防误触与自定义参考图)

## 1. 目标
解决用户在桌面滑页（桌面1滑到桌面2）时容易误触发标记功能的问题，并优化区域编辑器的背景显示，支持用户指定一张截图作为参考背景，提高对齐精度。

## 2. 详细设计

### 2.1 跨页误触拦截 (Same-Page Lock)
通过监听桌面页面的偏移状态，确保双击的两次点击发生在同一屏幕页面上。

- **核心逻辑**:
  - 在 `MyWallpaperService` 中通过 `onOffsetsChanged(xOffset: Float, ...)` 实时更新当前页面位置 `lastXOffset`。
  - 在 `onTouchEvent` 处理点击时：
    - 记录第一次点击时的 `offset1 = lastXOffset`。
    - 记录第二次点击时的 `offset2 = lastXOffset`。
    - 增加判定条件：`Math.abs(offset1 - offset2) < 0.001f`。如果偏移量发生变化，则不判定为有效双击。
- **配置项**:
  - `Key`: `disable_mark_on_swipe` (Boolean, Default: `true`)
  - `Label`: "仅在同一桌面页生效"
  - `Description`: "防止左右滑页时意外触发标记功能"

### 2.2 自定义参考底图 (Reference Image)
取消自动读取系统壁纸（解决权限和黑屏问题），让用户手动指定一张“完美对齐”的桌面截图。

- **配置项**:
  - `Key`: `hotspot_reference_uri` (String, Default: "")
- **UI 变更**:
  - 设置页增加“选择参考截图”按钮。
  - 使用 `ActivityResultContracts.OpenDocument` 选择图片。
  - 持久化 URI 权限（`takePersistableUriPermission`）。
- **编辑器变更**:
  - `HotspotEditorDialog` 优先加载 `hotspot_reference_uri`。
  - 如果 URI 为空，则回退到当前已有的 5 列图标模拟遮罩。

### 2.3 数据导出增强
- 导出 JSON 中包含上述新增配置项：`disable_mark_on_swipe` 和 `hotspot_reference_uri`。

## 3. 验证计划
1. **防误触测试**: 在桌面第 1 页点击一下，迅速滑到第 2 页点击一下，确认不会触发标记。
2. **正常触发测试**: 在同一页内双击，确认标记功能依然灵敏。
3. **参考图测试**: 选一张有图标的桌面截图，进入编辑器，确认背景能正确显示该截图且位置对应。
4. **开关测试**: 关闭“仅在同一桌面页生效”开关，确认滑页点击是否能像以前一样触发（可选回归测试）。
