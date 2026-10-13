package team.bjtuss.bjtuselfservice.shared.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把学校响应的解码与 HTML/JSON/正则解析放到后台线程。
 *
 * 各页面的同步协程由 Compose 作用域启动，默认跑在主线程；iOS 实测同步期间主线程近半时间在
 * 解析校历、物理在线、培养方案等大页面，首页滑动因此掉帧。block 必须是纯计算（只读入参、
 * 不碰 Cookie/会话等共享可变状态），网络请求本身仍留在调用方的调度器上，保持原有的串行顺序。
 */
internal suspend fun <T> parseOffMain(block: () -> T): T = withContext(Dispatchers.Default) { block() }
