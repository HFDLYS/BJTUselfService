package team.bjtuss.bjtuselfservice.kmp.widget

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import team.bjtuss.bjtuselfservice.kmp.R
import team.bjtuss.bjtuselfservice.shared.feature.widget.CourseWidgetDisplayModel
import team.bjtuss.bjtuselfservice.shared.feature.widget.CourseWidgetState
import team.bjtuss.bjtuselfservice.shared.feature.widget.WidgetDayPlan
import team.bjtuss.bjtuselfservice.shared.feature.widget.WidgetOccurrence
import team.bjtuss.bjtuselfservice.shared.feature.widget.WidgetUpNext
import team.bjtuss.bjtuselfservice.shared.feature.widget.WidgetWeekLayout
import team.bjtuss.bjtuselfservice.shared.feature.widget.detailLine
import team.bjtuss.bjtuselfservice.shared.feature.widget.isStaggered
import team.bjtuss.bjtuselfservice.shared.feature.widget.relativeDayPrefix
import team.bjtuss.bjtuselfservice.shared.feature.widget.roomAndTeacher
import team.bjtuss.bjtuselfservice.shared.feature.widget.timeRange
import team.bjtuss.bjtuselfservice.shared.feature.widget.widgetClock
import team.bjtuss.bjtuselfservice.shared.feature.widget.widgetDayName
import team.bjtuss.bjtuselfservice.shared.feature.widget.widgetMonthDay
import team.bjtuss.bjtuselfservice.shared.feature.widget.widgetRemainingText
import team.bjtuss.bjtuselfservice.shared.feature.widget.widgetWeekday

/** 小组件的版式尺寸档位，对应 iOS 的 systemSmall/Medium/Large。 */
enum class CourseWidgetSize { SMALL, MEDIUM, LARGE }

/**
 * 用 RemoteViews 拼出与 iOS 小组件（CourseWidgets.swift / WeekCourseWidget.swift）一致的版式。
 * 主题色都走 values / values-night 资源，由桌面在自己的深浅色配置下解析；
 * 只有课程类别色块的文字色是代码里设的，Android 12+ 同样按资源设置，旧系统按当前深浅色取值。
 */
internal class CourseWidgetRenderer(private val context: Context) {
    private val packageName = context.packageName

    /**
     * 版式缩放：iOS 小组件尺寸固定（小号 158pt、中号 338×158pt、大号 338×354pt），Android 格子大小因桌面而异。
     * 以 iOS 尺寸为基准按实际宽高等比缩放字号，比例与 iOS 一致；限制在 0.9–1.25 之间，避免过大或过小。
     */
    private var scale = 1f
    private var widthDp = 338f

    private fun setCanvas(family: CourseWidgetSize, width: Float, height: Float) {
        widthDp = width
        val (baseWidth, baseHeight) = when (family) {
            CourseWidgetSize.SMALL -> 158f to 158f
            CourseWidgetSize.MEDIUM -> 338f to 158f
            CourseWidgetSize.LARGE -> 338f to 354f
        }
        scale = minOf(width / baseWidth, height / baseHeight).coerceIn(0.9f, 1.25f)
    }

    private fun views(@LayoutRes layout: Int) = RemoteViews(packageName, layout)

    /**
     * 桌面在布局 id 相同时会把新的 RemoteViews “重放”到已有视图上，addView 会在旧内容后面继续追加；
     * 所以根容器每次先清空。
     */
    fun frame(build: (RemoteViews) -> Unit): RemoteViews = views(R.layout.widget_frame).also {
        it.removeAllViews(R.id.widget_content)
        build(it)
    }

    private fun RemoteViews.add(child: RemoteViews) = addView(R.id.widget_content, child)
    private fun RemoteViews.spacer() = add(views(R.layout.widget_spacer))

    /** 按版式缩放设置字号（dp：卡片大小固定，跟随卡片缩放而不是系统字体，避免溢出）。 */
    private fun RemoteViews.size(id: Int, base: Float) =
        setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, base * scale)

    /** 单行文字放不下时缩小字号（最多缩到 [minFactor]），对应 iOS 的 minimumScaleFactor。 */
    private fun RemoteViews.fit(id: Int, text: String, base: Float, width: Float, minFactor: Float = 0.7f) =
        setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, fittedSize(listOf(text), base, width, minFactor))

    /** 几段并列文字共用一个字号：取都能放下的那个（两栏标签、课名大小一致才协调）。 */
    private fun fittedSize(texts: List<String>, base: Float, width: Float, minFactor: Float = 0.7f): Float {
        val full = base * scale
        val needed = texts.maxOfOrNull { textWidth(it, full) } ?: 0f
        return if (needed <= width) full else maxOf(full * minFactor, full * width / needed)
    }

    private fun gapDp(base: Float): Int = dp(base * scale)

    // MARK: 公共状态

    /** 没有可显示的课表时统一的提示卡片。 */
    fun message(state: CourseWidgetState): RemoteViews? {
        val (title, detail) = when (state) {
            CourseWidgetState.SignedOut -> "暂无课表" to "打开 App 同步课表后显示"
            CourseWidgetState.Stale -> "课表需要更新" to "打开 App 刷新课表"
            is CourseWidgetState.Ready -> return null
        }
        return frame { root ->
            root.add(views(R.layout.widget_message).apply {
                setTextViewText(R.id.message_title, title)
                setTextViewText(R.id.message_detail, detail)
            })
        }
    }

    /** 所有卡片统一的头部：左边“[明天] 日期 星期”（星期红色），右边教学周胶囊。 */
    private fun header(
        plan: WidgetDayPlan,
        prefix: String? = null,
        detail: String? = null,
        compact: Boolean = false,
        showsDate: Boolean = true,
    ) = views(R.layout.widget_header).apply {
        // 比 iOS 的 subheadline/footnote 各大 1：Android 卡片更大，日期周数要一眼能看清。
        val titleSize = if (compact) 14f else 16f
        val title = listOfNotNull(prefix, widgetMonthDay(plan.date).takeIf { showsDate }).joinToString(" ")
        setTextViewText(R.id.header_title, title)
        setViewVisibility(R.id.header_title, if (title.isEmpty()) View.GONE else View.VISIBLE)
        setTextViewText(R.id.header_weekday, widgetWeekday(plan.date))
        size(R.id.header_title, titleSize)
        size(R.id.header_weekday, titleSize)
        if (detail != null) {
            setTextViewText(R.id.header_detail, detail)
            size(R.id.header_detail, 12.5f)
            setViewVisibility(R.id.header_detail, View.VISIBLE)
        }
        plan.week?.let { week ->
            val badge = (if (compact) 12f else 14f) * scale
            setTextViewText(R.id.header_badge, weekBadge(week, badge))
            setViewPadding(R.id.header_badge, dp(badge * 0.55f), dp(badge * 0.12f), dp(badge * 0.55f), dp(badge * 0.12f))
            setViewVisibility(R.id.header_badge, View.VISIBLE)
        }
    }

    /** “第 5 周”：数字加大加粗，与 iOS WeekBadge 同比例。 */
    private fun weekBadge(week: Int, size: Float): CharSequence = SpannableStringBuilder().apply {
        fun part(text: String, factor: Float, bold: Boolean) {
            val start = length
            append(text)
            setSpan(AbsoluteSizeSpan(dp(size * factor), false), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (bold) setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        part("第 ", 0.8f, false)
        part("$week", 1.25f, true)
        part(" 周", 0.8f, false)
    }

    /** 日期下方的状态行（“今天已上完”）；与头部的距离用上内边距表示（RemoteViews 改不了外边距）。 */
    private fun statusLine(text: String) = views(R.layout.widget_status_line).apply {
        setTextViewText(R.id.status_text, text)
        size(R.id.status_text, 11.5f)
        setViewPadding(R.id.status_text, 0, gapDp(5f), 0, 0)
    }

    private fun rest(model: CourseWidgetDisplayModel, compact: Boolean) = views(R.layout.widget_rest).apply {
        setTextViewText(R.id.rest_title, model.restTitle)
        size(R.id.rest_title, if (compact) 17f else 20f)
        size(R.id.rest_enjoy, if (compact) 15f else 17f)
        size(R.id.rest_next, 11.5f)
        val next = model.nextDay
        val first = next?.occurrences?.firstOrNull()
        if (next != null && first != null) {
            val whenText = "${widgetDayName(next.date, model.now.date)} ${widgetClock(first.session.startMinute)}"
            setTextViewText(R.id.rest_next, if (compact) "下次上课 $whenText" else "下次上课 · $whenText ${first.session.name}")
            setViewVisibility(R.id.rest_next, View.VISIBLE)
        }
    }

    private fun empty(text: String) = views(R.layout.widget_empty).apply {
        setTextViewText(R.id.empty_text, text)
        size(R.id.empty_text, 13.5f)
    }

    private fun RemoteViews.tintBar(id: Int, color: Int, alpha: Int = 255) {
        setInt(id, "setColorFilter", color)
        if (alpha != 255) setInt(id, "setImageAlpha", alpha)
    }

    private fun accent(occurrence: WidgetOccurrence): Int = (0xFF000000 or occurrence.session.color).toInt()

    private fun dimmed(child: RemoteViews) = views(R.layout.widget_dim).apply { addView(R.id.dim_container, child) }

    private fun group(child: RemoteViews, topGap: Float) = views(R.layout.widget_group).apply {
        setViewPadding(R.id.group_container, 0, gapDp(topGap), 0, 0)
        addView(R.id.group_container, child)
    }

    // MARK: 临近课程

    fun upNext(model: CourseWidgetDisplayModel, family: CourseWidgetSize, width: Float, height: Float): RemoteViews {
        setCanvas(family, width, height)
        return if (family == CourseWidgetSize.SMALL) upNextSmall(model) else upNextMedium(model)
    }

    private fun upNextHeader(model: CourseWidgetDisplayModel, compact: Boolean): RemoteViews {
        val upNext = model.upNext
        val day = model.nextDay
        if (!model.isRestPeriod && upNext != null && upNext.isFutureDay && day != null) {
            val prefix = relativeDayPrefix(day.date, model.now.date)
            return header(day, prefix = prefix, compact = compact, showsDate = !(compact && prefix != null))
        }
        return header(model.today, compact = compact)
    }

    /** 小号：头部 + 大字课程块 + 下一节标题行，整体在剩余空间里略偏上居中。 */
    private fun upNextSmall(model: CourseWidgetDisplayModel) = frame { root ->
        root.add(upNextHeader(model, compact = true))
        val upNext = model.upNext
        when {
            model.isRestPeriod -> {
                root.spacer(); root.add(rest(model, compact = true)); root.spacer()
            }
            upNext != null -> {
                if (upNext.isFutureDay) root.add(statusLine(model.todayStatus))
                root.add(group(detailedBlock(upNext.primary, model), 9f))
                upNext.secondary?.let { secondary ->
                    root.add(group(views(R.layout.widget_block_title).apply {
                        tintBar(R.id.bar, accent(secondary))
                        setTextViewText(R.id.block_name, secondary.session.name)
                        fit(R.id.block_name, secondary.session.name, 15f, textWidthSmall())
                        setTextViewText(R.id.block_time, secondary.timeRange())
                        size(R.id.block_time, 12f)
                    }, 9f))
                }
                root.spacer()
            }
            else -> {
                root.spacer()
                root.add(empty(if (model.today.occurrences.isEmpty()) "今天没有课" else "今天已上完"))
                root.spacer()
            }
        }
    }

    /** 小号课程块文字可用宽度：内边距 32、色条 3、间距 6。 */
    private fun textWidthSmall() = widthDp - 32f - 3f - 6f

    private fun detailedBlock(occurrence: WidgetOccurrence, model: CourseWidgetDisplayModel) =
        views(R.layout.widget_block_detailed).apply {
            tintBar(R.id.bar, accent(occurrence))
            setTextViewText(R.id.block_name, occurrence.session.name)
            size(R.id.block_name, 17f)
            val detail = occurrence.session.roomAndTeacher()
            setTextViewText(R.id.block_detail, detail)
            fit(R.id.block_detail, detail, 12.5f, textWidthSmall())
            setViewVisibility(R.id.block_detail, if (detail.isEmpty()) View.GONE else View.VISIBLE)
            setTextViewText(R.id.block_time, occurrence.timeRange())
            size(R.id.block_time, 12.5f)
            if (occurrence.isOngoing(model.now)) {
                val remaining = "还剩 ${widgetRemainingText(occurrence.minutesUntilEnd(model.now))}"
                setTextViewText(R.id.block_remaining, remaining)
                fit(R.id.block_remaining, remaining, 11.5f, textWidthSmall())
                setTextColor(R.id.block_remaining, accent(occurrence))
                setViewVisibility(R.id.block_remaining, View.VISIBLE)
            }
        }

    /** 中号：左“当前/下一节”、右“接下来”两栏，大字课名 + 地点 + 时间。 */
    private fun upNextMedium(model: CourseWidgetDisplayModel) = frame { root ->
        val upNext = if (model.isRestPeriod) null else model.upNext
        val tight = upNext?.isFutureDay == true
        root.add(upNextHeader(model, compact = false))
        when {
            model.isRestPeriod -> {
                root.spacer(); root.add(rest(model, compact = false)); root.spacer()
            }
            upNext != null -> {
                if (upNext.isFutureDay) root.add(statusLine(model.todayStatus))
                val columns = views(R.layout.widget_columns)
                val dayName = widgetDayName(upNext.primary.date, model.now.date)
                val firstTitle = primaryTitle(upNext, model)
                val secondTitle = if (upNext.isFutureDay) "${dayName}第二节" else "接下来"
                val secondary = upNext.secondary
                val shown = listOfNotNull(upNext.primary, secondary)
                val small = if (tight) 13f else 15f
                val sizes = ColumnSizes(
                    label = fittedSize(listOf(firstTitle, secondTitle), small, columnLabelWidth()),
                    name = fittedSize(shown.map { it.session.name }, 20f, columnTextWidth()),
                    detail = fittedSize(shown.map { it.session.place.ifEmpty { it.session.teacher } }, small, columnTextWidth(), 0.8f),
                    time = fittedSize(shown.map { it.timeRange() }, small, columnTextWidth(), 0.8f),
                )
                columns.addView(R.id.column_left, column(firstTitle, upNext.primary, tight, sizes))
                columns.addView(
                    R.id.column_right,
                    if (secondary != null) column(secondTitle, secondary, tight, sizes)
                    else views(R.layout.widget_column_empty).apply {
                        setTextViewText(R.id.column_label, secondTitle)
                        setTextViewTextSize(R.id.column_label, TypedValue.COMPLEX_UNIT_DIP, sizes.label)
                        setTextViewText(R.id.column_text, if (upNext.isFutureDay) "${dayName}只有一节课" else "今天没有更多课了")
                        size(R.id.column_text, small)
                    },
                )
                // 头部与两栏之间至少 iOS 的 12（跨天时 6）；格子比 iOS 高出来的部分在两栏上下均分。
                root.spacer()
                root.add(group(columns, if (tight) 6f else 12f))
                root.spacer()
            }
            else -> {
                root.spacer()
                root.add(empty(if (model.today.occurrences.isEmpty()) "今天没有课，好好休息" else "今天已上完"))
                root.spacer()
            }
        }
    }

    /** 中号半栏宽度：内边距 32、分隔线及两侧间距 14.5。 */
    private fun columnLabelWidth() = (widthDp - 32f - 14.5f) / 2f

    /** 半栏里课程文字宽度：再减色条 4 与间距 8。 */
    private fun columnTextWidth() = columnLabelWidth() - 12f

    private fun primaryTitle(upNext: WidgetUpNext, model: CourseWidgetDisplayModel): String = when {
        upNext.isFutureDay -> "${widgetDayName(upNext.primary.date, model.now.date)}第一节"
        upNext.primary.isOngoing(model.now) ->
            "当前 · 还剩 ${widgetRemainingText(upNext.primary.minutesUntilEnd(model.now), short = true)}"
        else -> "下一节"
    }

    private class ColumnSizes(val label: Float, val name: Float, val detail: Float, val time: Float)

    private fun column(title: String, occurrence: WidgetOccurrence, tight: Boolean, sizes: ColumnSizes) =
        views(R.layout.widget_column).apply {
        setTextViewText(R.id.column_label, title)
        setTextViewTextSize(R.id.column_label, TypedValue.COMPLEX_UNIT_DIP, sizes.label)
        tintBar(R.id.bar, accent(occurrence))
        setTextViewText(R.id.column_name, occurrence.session.name)
        setTextViewTextSize(R.id.column_name, TypedValue.COMPLEX_UNIT_DIP, sizes.name)
        val detail = occurrence.session.place.ifEmpty { occurrence.session.teacher }
        setTextViewText(R.id.column_detail, detail)
        setTextViewTextSize(R.id.column_detail, TypedValue.COMPLEX_UNIT_DIP, sizes.detail)
        setViewVisibility(R.id.column_detail, if (detail.isEmpty()) View.GONE else View.VISIBLE)
        setTextViewText(R.id.column_time, occurrence.timeRange())
        setTextViewTextSize(R.id.column_time, TypedValue.COMPLEX_UNIT_DIP, sizes.time)
        // 栏标签与课程之间：iOS 为 8（跨天 4），XML 里已有 4。
        setViewPadding(R.id.column_body, 0, gapDp(if (tight) 0f else 4f), 0, 0)
    }

    // MARK: 今日课程

    /**
     * 能放几节按实际高度估算：iOS 中/大号固定放 2/6 节，Android 格子高度差别大
     * （Pixel 2 行约 200dp，荣耀 4×2 约 150dp），避免大片留白或溢出。
     */
    fun today(model: CourseWidgetDisplayModel, family: CourseWidgetSize, width: Float, heightDp: Float): RemoteViews {
        setCanvas(family, width, heightDp)
        return frame { root -> todayContent(root, model, family == CourseWidgetSize.LARGE, heightDp) }
    }

    private fun todayContent(root: RemoteViews, model: CourseWidgetDisplayModel, large: Boolean, heightDp: Float) {
        if (model.isRestPeriod) {
            root.add(header(model.today))
            root.spacer(); root.add(rest(model, compact = false)); root.spacer()
            return
        }
        // 内边距 32 + 头部约 24（+ 脚注 18）；两行式一行连间距约 50dp，单行式约 27dp（均随版式缩放）。
        val rowHeight = 50f * scale
        val compactHeight = 27f * scale
        val headerHeight = 24f * scale
        val bareRows = ((heightDp - 32f - headerHeight) / rowHeight).toInt()
        val footerRows = ((heightDp - 32f - headerHeight - 18f * scale) / rowHeight).toInt().coerceIn(2, if (large) 7 else 4)
        val compactCapacity = ((heightDp - 32f - headerHeight - 18f * scale) / compactHeight).toInt().coerceIn(3, if (large) 12 else 6)
        val nextDay = model.nextDay
        val showsNextDay = model.remainingToday.isEmpty() && nextDay != null
        val plan = if (showsNextDay) nextDay!! else model.today
        val occurrences = plan.occurrences
        val firstActive = if (showsNextDay) 0
        else occurrences.indexOfFirst { !it.isFinished(model.now) }.let { if (it < 0) occurrences.size else it }
        val pending = occurrences.size - firstActive
        // 中号只差一个“已上完 N 节”脚注就能用两行式时，宁可省掉脚注，也不换成单行紧凑行。
        val dropFinishedFooter = !large && pending in (footerRows + 1)..bareRows
        val capacity = if (dropFinishedFooter) bareRows else footerRows
        val compact = pending > capacity
        val tight = showsNextDay && !large
        val limit = if (compact) compactCapacity - (if (tight) 1 else 0) else capacity
        val visible = when {
            !large -> occurrences.drop(firstActive).take(limit)
            occurrences.size <= limit -> occurrences
            else -> {
                val start = maxOf(0, minOf(firstActive, occurrences.size - limit))
                occurrences.subList(start, minOf(start + limit, occurrences.size))
            }
        }
        val visibleIds = visible.map { it.id }.toSet()
        val hiddenUpcoming = occurrences.filter { it.id !in visibleIds && !it.isFinished(model.now) }
        val hiddenFinished = occurrences.count { it.id !in visibleIds && it.isFinished(model.now) }

        root.add(header(
            plan,
            prefix = if (showsNextDay) relativeDayPrefix(plan.date, model.now.date) else null,
            detail = if (occurrences.isEmpty()) null else "共 ${occurrences.size} 节",
        ))
        if (showsNextDay) root.add(statusLine(model.todayStatus))
        if (occurrences.isEmpty()) {
            root.spacer(); root.add(empty("今天没有课，好好休息")); root.spacer()
            return
        }
        if (visible.isEmpty()) {
            root.spacer(); root.add(empty("今天已上完"))
        }
        visible.forEachIndexed { index, occurrence ->
            val row = if (compact) compactRow(occurrence, model) else todayRow(occurrence, model, large, tight)
            val gap = if (index == 0) (if (compact || tight) 5f else 8f) else (if (compact) 4f else 6f)
            root.add(group(if (occurrence.isFinished(model.now)) dimmed(row) else row, gap))
        }
        if (hiddenUpcoming.isNotEmpty() || (hiddenFinished > 0 && !dropFinishedFooter)) {
            root.add(group(views(R.layout.widget_hidden_summary).apply {
                val first = hiddenUpcoming.firstOrNull()
                setTextViewText(R.id.summary_upcoming, when {
                    first == null -> ""
                    hiddenUpcoming.size == 1 -> "还有 1 节未显示 · ${widgetClock(first.session.startMinute)} ${first.session.name}"
                    else -> "还有 ${hiddenUpcoming.size} 节未上"
                })
                setTextViewText(R.id.summary_finished, if (hiddenFinished > 0) "已上完 $hiddenFinished 节" else "")
                size(R.id.summary_upcoming, 11.5f)
                size(R.id.summary_finished, 11.5f)
            }, 5f))
        }
        // 大号今天放完后还有空位：用轻量单行预告下一个有课日。
        if (large && !showsNextDay && nextDay != null && nextDay.occurrences.isNotEmpty()) {
            var used = 32f + headerHeight + visible.size * (if (compact) compactHeight else rowHeight)
            if (hiddenUpcoming.isNotEmpty() || hiddenFinished > 0) used += 18f * scale
            val room = ((heightDp - used - 30f * scale) / (20f * scale)).toInt()
            if (room >= 1) {
                val rows = nextDay.occurrences.take(room)
                root.add(group(views(R.layout.widget_next_day_header).apply {
                    val name = widgetDayName(nextDay.date, model.now.date)
                    setTextViewText(R.id.preview_day, if ("周" in name) name else "$name ${widgetWeekday(nextDay.date)}")
                    val total = nextDay.occurrences.size
                    setTextViewText(R.id.preview_count,
                        if (total > rows.size) "共 $total 节 剩余 ${total - rows.size} 节未显示" else "共 $total 节")
                    size(R.id.preview_day, 12.5f)
                    size(R.id.preview_count, 11.5f)
                }, 4f))
                rows.forEach { occurrence ->
                    root.add(views(R.layout.widget_next_day_row).apply {
                        tintBar(R.id.bar, accent(occurrence), alpha = 178)
                        setTextViewText(R.id.row_name, occurrence.session.name)
                        setTextViewText(R.id.row_detail, occurrence.session.roomAndTeacher())
                        setTextViewText(R.id.row_time, occurrence.timeRange())
                        size(R.id.row_name, 12.5f)
                        size(R.id.row_detail, 11.5f)
                        size(R.id.row_time, 11.5f)
                    })
                }
            }
        }
        root.spacer()
    }

    private fun todayRow(occurrence: WidgetOccurrence, model: CourseWidgetDisplayModel, roomy: Boolean, tight: Boolean) =
        views(R.layout.widget_today_row).apply {
            val color = accent(occurrence)
            val ongoing = occurrence.isOngoing(model.now)
            tintBar(R.id.row_background, color, alpha = if (ongoing) 56 else 31)
            tintBar(R.id.bar, color)
            val vertical = gapDp(if (roomy) 6f else if (tight) 2f else 4f)
            setViewPadding(R.id.row_content, 0, vertical, dp(8), vertical)
            setTextViewText(R.id.row_name, occurrence.session.name)
            // 课名可用宽度：内边距 32、色条与间距 12、右侧时间约 46、标签约 40。
            val badges = (if (ongoing) 1 else 0) + (if (occurrence.session.isLab || occurrence.session.kind == "exam") 1 else 0)
            fit(R.id.row_name, occurrence.session.name, 15f, widthDp - 32f - 12f - 8f - 46f * scale - badges * 40f * scale, 0.8f)
            if (ongoing) {
                setViewVisibility(R.id.row_badge_ongoing, View.VISIBLE)
                setTextColor(R.id.row_badge_ongoing, color)
                size(R.id.row_badge_ongoing, 9.5f)
            }
            val kindBadge = when {
                occurrence.session.isLab -> "实验"
                occurrence.session.kind == "exam" -> "考试"
                else -> null
            }
            if (kindBadge != null) {
                setTextViewText(R.id.row_badge_kind, kindBadge)
                size(R.id.row_badge_kind, 9.5f)
                setViewVisibility(R.id.row_badge_kind, View.VISIBLE)
            }
            setTextViewText(R.id.row_detail, occurrence.session.detailLine())
            size(R.id.row_detail, 12.5f)
            setTextViewText(R.id.row_start, widgetClock(occurrence.session.startMinute))
            setTextViewText(R.id.row_end, widgetClock(occurrence.session.endMinute))
            size(R.id.row_start, 12.5f)
            size(R.id.row_end, 12.5f)
        }

    private fun compactRow(occurrence: WidgetOccurrence, model: CourseWidgetDisplayModel) =
        views(R.layout.widget_compact_row).apply {
            val color = accent(occurrence)
            val ongoing = occurrence.isOngoing(model.now)
            tintBar(R.id.row_background, color, alpha = if (ongoing) 56 else 26)
            tintBar(R.id.bar, color)
            setTextViewText(R.id.row_name, occurrence.session.name)
            size(R.id.row_name, 14f)
            if (ongoing) {
                setViewVisibility(R.id.row_badge_ongoing, View.VISIBLE)
                setTextColor(R.id.row_badge_ongoing, color)
                size(R.id.row_badge_ongoing, 9.5f)
            }
            setTextViewText(R.id.row_detail, occurrence.session.roomAndTeacher())
            size(R.id.row_detail, 11.5f)
            setTextViewText(R.id.row_start, widgetClock(occurrence.session.startMinute))
            size(R.id.row_start, 12.5f)
            if (ongoing) setNightAwareTextColor(this, R.id.row_start, R.color.widget_text_primary)
        }

    // MARK: 本周课表

    /** [heightDp] 为小组件可用高度，用来估算行高，决定错峰色块下移多少、时间标签是否省略下课时间。 */
    fun week(model: CourseWidgetDisplayModel, widthDp: Float, heightDp: Float): RemoteViews = frame { root ->
        // 周视图格子文字按行高单独计算；头部与其它卡片同一比例（以 iOS 大号为基准）。
        setCanvas(CourseWidgetSize.LARGE, widthDp, heightDp)
        val layout = WidgetWeekLayout(model)
        root.add(header(model.today))
        val gridViews = views(R.layout.widget_week_grid)
        val rowCount = layout.rows.size.coerceAtLeast(1)
        // 头部约 24dp、上下内边距 28dp、日期行 30dp、行间距 3dp。
        val rowHeight = ((heightDp - 24 - 32 - 6 - 30 - 3f * rowCount) / rowCount).coerceAtLeast(20f)
        // 内边距 32、时间轴 30、列间距 3。
        val columnWidth = ((widthDp - 32 - 30 - 3f * layout.days.size) / layout.days.size.coerceAtLeast(1)).coerceAtLeast(24f)

        val axis = views(R.layout.widget_week_axis).apply {
            setTextViewText(R.id.week_month, "${model.now.month.ordinal + 1}月")
        }
        layout.rows.forEach { slot ->
            axis.addView(R.id.week_axis, views(R.layout.widget_week_slot_label).apply {
                val range = layout.slots[slot]
                setTextViewText(R.id.slot_index, "${slot + 1}")
                setTextViewText(R.id.slot_start, widgetClock(range.first))
                setTextViewText(R.id.slot_end, widgetClock(range.last))
                if (rowHeight < 34) setViewVisibility(R.id.slot_end, View.GONE)
            })
        }
        gridViews.addView(R.id.week_grid, axis)

        layout.days.forEachIndexed { column, day ->
            val isToday = column == layout.todayColumn
            val columnViews = views(R.layout.widget_week_column).apply {
                setTextViewText(R.id.day_weekday, listOf("一", "二", "三", "四", "五", "六", "日")[day.date.dayOfWeek.ordinal])
                setTextViewText(R.id.day_date, "${day.date.day}")
                if (isToday) {
                    setInt(R.id.week_column, "setBackgroundResource", R.drawable.widget_today_column)
                    setTextColor(R.id.day_weekday, context.getColor(R.color.widget_weekday))
                    setNightAwareTextColor(this, R.id.day_date, R.color.widget_text_primary)
                    setTextViewTextSize(R.id.day_weekday, TypedValue.COMPLEX_UNIT_DIP, 12f)
                }
            }
            val blocks = layout.blocks.filter { it.column == column }
            weekSegments(blocks.map { WeekSegment(it.firstRow, it.lastRow, it) }, 0, rowCount - 1, model, rowHeight, columnWidth)
                .forEach { columnViews.addView(R.id.week_column_body, it) }
            gridViews.addView(R.id.week_grid, columnViews)
        }
        if (layout.blocks.isEmpty()) {
            gridViews.addView(R.id.week_overlay, views(R.layout.widget_week_empty).apply {
                setTextViewText(R.id.rest_title, if (model.isRestPeriod) model.restTitle else "本周没有课")
            })
        }
        root.add(gridViews)
    }

    private data class WeekSegment(
        val first: Int,
        val last: Int,
        val block: team.bjtuss.bjtuselfservice.shared.feature.widget.WidgetWeekBlock,
    )

    /**
     * 把一列的色块排成竖向片段：空白按行数占权重；互相重叠的课合成一组，组内按“道”横向并排，
     * 每道再递归排竖向片段。LinearLayout 权重只能写在 XML 里，所以各跨行数都有一份布局。
     */
    private fun weekSegments(
        segments: List<WeekSegment>,
        from: Int,
        to: Int,
        model: CourseWidgetDisplayModel,
        rowHeight: Float,
        columnWidth: Float,
    ): List<RemoteViews> {
        val result = mutableListOf<RemoteViews>()
        var cursor = from
        val sorted = segments.sortedBy { it.first }
        var index = 0
        while (index < sorted.size) {
            // 合并与当前组有重叠的后续色块。
            var groupLast = sorted[index].last
            val group = mutableListOf(sorted[index])
            index++
            while (index < sorted.size && sorted[index].first <= groupLast) {
                groupLast = maxOf(groupLast, sorted[index].last)
                group += sorted[index]
                index++
            }
            val groupFirst = group.first().first
            if (groupFirst > cursor) result += gap(groupFirst - cursor)
            val span = groupLast - groupFirst + 1
            if (group.size == 1) {
                result += weekBlock(group.single(), span, model, rowHeight, columnWidth)
            } else {
                val spanViews = views(spanLayout(span))
                group.groupBy { it.block.lane }.toSortedMap().values.forEach { lane ->
                    val laneViews = views(R.layout.widget_week_lane)
                    val laneCount = group.maxOf { it.block.laneCount }
                    weekSegments(lane, groupFirst, groupLast, model, rowHeight, (columnWidth - 3f * (laneCount - 1)) / laneCount)
                        .forEach { laneViews.addView(R.id.week_lane, it) }
                    spanViews.addView(R.id.week_span, laneViews)
                }
                result += spanViews
            }
            cursor = groupLast + 1
        }
        if (cursor <= to) result += gap(to - cursor + 1)
        return result
    }

    private fun gap(rows: Int) = views(gapLayout(rows))

    private fun weekBlock(
        segment: WeekSegment,
        span: Int,
        model: CourseWidgetDisplayModel,
        rowHeight: Float,
        columnWidth: Float,
    ): RemoteViews {
        val occurrence = segment.block.occurrence
        val finished = occurrence.isFinished(model.now)
        val ongoing = occurrence.isOngoing(model.now)
        val kind = occurrence.session.kind.ifEmpty { if (occurrence.session.isLab) "lab" else "unknown" }
        return views(blockLayout(span, finished)).apply {
            setInt(R.id.week_block_body, "setBackgroundResource", blockBackground(kind, ongoing))
            setTextViewText(R.id.week_block_name, occurrence.session.name)
            setTextViewText(R.id.week_block_place, occurrence.session.place)
            val narrow = segment.block.laneCount > 1
            val nameSize = if (narrow) 9f else 10.5f
            val placeSize = if (narrow) 8f else 9f
            setTextViewTextSize(R.id.week_block_name, TypedValue.COMPLEX_UNIT_DIP, nameSize)
            setTextViewTextSize(R.id.week_block_place, TypedValue.COMPLEX_UNIT_DIP, placeSize)
            // RemoteViews 里文字超高会被拦腰裁掉（iOS 会按整行截断）：按色块高度和列宽算出能放几行，
            // 先给课名，剩下的给地点；放不下一整行的地点干脆不显示。
            val shift = if (occurrence.session.isStaggered) minOf(rowHeight * 0.125f, 6f) else 0f
            val inner = span * rowHeight + (span - 1) * 3f - 3f - shift - 8f
            val textWidth = columnWidth - 3f - 6f
            val nameLineHeight = nameSize * 1.18f
            val placeLineHeight = placeSize * 1.18f
            val totalNameLines = (inner / nameLineHeight).toInt().coerceAtLeast(1)
            val nameLines = estimatedLines(occurrence.session.name, nameSize, textWidth).coerceIn(1, minOf(4, totalNameLines))
            val placeLines = ((inner - nameLines * nameLineHeight - 2f) / placeLineHeight).toInt().coerceIn(0, 3)
            setInt(R.id.week_block_name, "setMaxLines", nameLines)
            setInt(R.id.week_block_place, "setMaxLines", placeLines.coerceAtLeast(1))
            setViewVisibility(R.id.week_block_place,
                if (occurrence.session.place.isEmpty() || placeLines == 0) View.GONE else View.VISIBLE)
            setNightAwareTextColor(this, R.id.week_block_name, kindContentColor(kind))
            setNightAwareTextColor(this, R.id.week_block_place, kindContentColor(kind))
            // 错峰课不多占一行：顶边微微下移，底边越过行间空隙一半（与 iOS 周视图一致）。
            if (occurrence.session.isStaggered) {
                setViewPadding(R.id.week_block, dp(1.5f), dp(1.5f + shift), dp(1.5f), 0)
            }
        }
    }

    /** 粗估一段文字要占几行。 */
    private fun estimatedLines(text: String, size: Float, width: Float): Int =
        kotlin.math.ceil(textWidth(text, size) / width.coerceAtLeast(1f)).toInt().coerceAtLeast(1)

    /** 粗估单行宽度（dp）：按 Roboto/思源黑体的大致字宽，宁宽勿窄，保证不溢出。 */
    private fun textWidth(text: String, size: Float): Float =
        text.sumOf { char ->
            val em = when {
                char == ' ' -> 0.28
                char == '·' || char == ':' || char == '.' || char == ',' -> 0.32
                char == '-' || char == '(' || char == ')' -> 0.38
                char.isDigit() -> 0.58
                char in 'A'..'Z' -> 0.68
                char.code < 128 -> 0.56
                else -> 1.0
            }
            em * size
        }.toFloat()

    private fun setNightAwareTextColor(views: RemoteViews, id: Int, @ColorRes color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(id, "setTextColor", color)
        } else {
            views.setTextColor(id, context.getColor(color))
        }
    }

    private fun dp(value: Int): Int = dp(value.toFloat())
    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private val gapLayouts = intArrayOf(
            R.layout.widget_week_gap_1, R.layout.widget_week_gap_2, R.layout.widget_week_gap_3,
            R.layout.widget_week_gap_4, R.layout.widget_week_gap_5, R.layout.widget_week_gap_6, R.layout.widget_week_gap_7,
        )
        private val spanLayouts = intArrayOf(
            R.layout.widget_week_span_1, R.layout.widget_week_span_2, R.layout.widget_week_span_3,
            R.layout.widget_week_span_4, R.layout.widget_week_span_5, R.layout.widget_week_span_6, R.layout.widget_week_span_7,
        )
        private val blockLayouts = intArrayOf(
            R.layout.widget_week_block_1, R.layout.widget_week_block_2, R.layout.widget_week_block_3,
            R.layout.widget_week_block_4, R.layout.widget_week_block_5, R.layout.widget_week_block_6, R.layout.widget_week_block_7,
        )
        private val dimBlockLayouts = intArrayOf(
            R.layout.widget_week_block_dim_1, R.layout.widget_week_block_dim_2, R.layout.widget_week_block_dim_3,
            R.layout.widget_week_block_dim_4, R.layout.widget_week_block_dim_5, R.layout.widget_week_block_dim_6,
            R.layout.widget_week_block_dim_7,
        )

        private fun gapLayout(rows: Int) = gapLayouts[(rows - 1).coerceIn(0, 6)]
        private fun spanLayout(rows: Int) = spanLayouts[(rows - 1).coerceIn(0, 6)]
        private fun blockLayout(rows: Int, dim: Boolean) =
            (if (dim) dimBlockLayouts else blockLayouts)[(rows - 1).coerceIn(0, 6)]

        private fun blockBackground(kind: String, ongoing: Boolean): Int = when (kind) {
            "required" -> if (ongoing) R.drawable.widget_block_required_ongoing else R.drawable.widget_block_required
            "limited" -> if (ongoing) R.drawable.widget_block_limited_ongoing else R.drawable.widget_block_limited
            "elective" -> if (ongoing) R.drawable.widget_block_elective_ongoing else R.drawable.widget_block_elective
            "pe" -> if (ongoing) R.drawable.widget_block_pe_ongoing else R.drawable.widget_block_pe
            "lab" -> if (ongoing) R.drawable.widget_block_lab_ongoing else R.drawable.widget_block_lab
            "exam" -> if (ongoing) R.drawable.widget_block_exam_ongoing else R.drawable.widget_block_exam
            else -> if (ongoing) R.drawable.widget_block_unknown_ongoing else R.drawable.widget_block_unknown
        }

        private fun kindContentColor(kind: String): Int = when (kind) {
            "required" -> R.color.widget_kind_required_content
            "limited" -> R.color.widget_kind_limited_content
            "elective" -> R.color.widget_kind_elective_content
            "pe" -> R.color.widget_kind_pe_content
            "lab" -> R.color.widget_kind_lab_content
            "exam" -> R.color.widget_kind_exam_content
            else -> R.color.widget_kind_unknown_content
        }
    }
}
