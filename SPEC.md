# MyWallpaperChanger Design Spec

## 1. Project Goal
High-performance Android Wallpaper Changer for Xiaomi 13 (MIUI/HyperOS).

## 2. Core Features
- **Live Wallpaper Engine**: WallpaperService based.
- **Index Support**: Room DB for 30,000+ images.
- **Union Management**: Select multiple folders; union of all images.
- **True Random**: SQL `ORDER BY RANDOM()`.
- **Toast Tagging**: Double tap screen to show "标记成功~".
- **Batch Move**: Physically move (cut) marked images.

## 3. Tech Stack
- Kotlin, Compose, Room, Coil, SAF.
