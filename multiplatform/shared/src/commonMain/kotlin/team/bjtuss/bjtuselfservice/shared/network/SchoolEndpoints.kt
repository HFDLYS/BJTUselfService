package team.bjtuss.bjtuselfservice.shared.network

/**
 * 学校各业务系统端点的唯一定义。学校域名变更时只改这里；
 * 仅收录跨数据源复用的常量，单数据源专用端点仍留在各自文件。
 */
internal object SchoolEndpoints {
    /** aa 教务系统根（带斜杠，用于 startsWith 归属判断与完整 URL 前缀）。 */
    const val AA_ORIGIN = "https://aa.bjtu.edu.cn/"

    /** aa 教务系统根（不带斜杠，用于路径拼接）。 */
    const val AA_ROOT = "https://aa.bjtu.edu.cn"

    /** aa 首页/Referer。 */
    const val AA_HOME_URL = "https://aa.bjtu.edu.cn/notice/item/"

    /** 旧认证中心（CAS）。MIS 已改用 [SSO_ORIGIN]；智慧教学握手仍可能经过这里。 */
    const val CAS_ORIGIN = "https://cas.bjtu.edu.cn"

    /** 统一身份认证（MIS 当前的 OAuth 授权与登录页）。 */
    const val SSO_ORIGIN = "https://sso.bjtu.edu.cn"

    /** MIS 智慧教学模块入口（课表/作业/课件共用）。 */
    const val SMART_MODULE_URL = "https://mis.bjtu.edu.cn/module/module/28/"

    /** 物理在线。 */
    const val PHYVLAB_ORIGIN = "https://phyvlab.bjtu.edu.cn"

    /** 教学周/教室占用查询页（课表当前周与教室占用共用）。 */
    const val ROOM_VIEW_URL = "https://aa.bjtu.edu.cn/classroom/timeholdresult/room_view/"
}
