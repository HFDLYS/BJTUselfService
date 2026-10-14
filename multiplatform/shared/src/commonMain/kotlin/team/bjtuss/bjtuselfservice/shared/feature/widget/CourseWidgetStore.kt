package team.bjtuss.bjtuselfservice.shared.feature.widget

/**
 * 把课程小组件快照交给系统小组件（iOS WidgetKit、Android AppWidget）。
 * json 为 null 表示清空（退出登录），小组件随即显示“登录后显示课程”。
 * 没有小组件的平台为空实现。
 */
internal expect fun writeCourseWidgetSnapshot(json: String?)
