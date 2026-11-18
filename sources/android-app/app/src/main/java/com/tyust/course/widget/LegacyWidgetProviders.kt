package com.tyust.course.widget

/**
 * 兼容壳（只做继承，不含逻辑）。
 *
 * 历史原因：1.0.9.14 及更早版本的包名是 `com.tyust.course`，桌面小组件的组件全名是
 * `com.buct.course/com.tyust.course.widget.ScheduleWidget`。用户桌面上已经添加的实例，
 * 系统里记录的就是这个全名；一旦类改名，旧实例会直接失效（表现为"组件无法加载/应用未安装"）。
 *
 * 所以这两个类名必须保留。真正的实现在 `com.buct.course.widget` 下，新代码也用那一套；
 * 这里只负责让老实例继续能收到 onUpdate。
 */
class ScheduleWidget : com.buct.course.widget.ScheduleWidget()

/** 今日课表小组件的兼容壳，理由同上。 */
class ScheduleDayWidget : com.buct.course.widget.ScheduleDayWidget()
