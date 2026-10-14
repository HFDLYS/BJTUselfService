package team.bjtuss.bjtuselfservice.shared.feature.assignment

import team.bjtuss.bjtuselfservice.shared.domain.homework.stableKey
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkUiState
import team.bjtuss.bjtuselfservice.shared.feature.homework.homeworkDetailToMarkdown
import team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabUiState
import team.bjtuss.bjtuselfservice.shared.feature.citel.CitelState
import team.bjtuss.bjtuselfservice.shared.feature.citel.citelDateText
import team.bjtuss.bjtuselfservice.shared.feature.citel.submissionStatusText

internal fun aggregateAssignmentMarkdown(item: AggregatedAssignment, homework: HomeworkUiState,
    physical: PhyVlabUiState, citel: CitelState): String {
    item.homework?.let { task ->
        val selected = homework.selectedHomework?.stableKey() == task.stableKey()
        return homeworkDetailToMarkdown(task, homework.detail.takeIf { selected },
            homework.submittedAttachments.takeIf { selected }.orEmpty())
    }
    return buildString {
        val title = item.physical?.title ?: item.citel?.title.orEmpty()
        appendLine("# $title\n")
        appendLine("- 来源：${item.source.label}")
        appendLine("- 课程：${item.courseName}")
        item.physical?.let { task ->
            val detail = physical.assignmentDetail.takeIf { physical.selectedActivity?.id == task.id && physical.selectedActivity?.courseId == task.courseId }
            appendLine("- 开放时间：${task.openText ?: "未提供"}")
            appendLine("- 截止时间：${task.dueText ?: "未提供"}")
            appendLine("- 提交状态：${detail?.submissionStatus?.ifBlank { null } ?: if (task.completed) "已提交" else "未提交"}")
            detail?.gradeText?.let { appendLine("- 评分：$it") }
            appendLine("\n## 作业要求\n")
            appendLine(detail?.description?.ifBlank { null } ?: "详情尚未加载。")
            detail?.feedbackText?.let { appendLine("\n## 反馈\n\n$it") }
            detail?.submittedFiles?.takeIf { it.isNotEmpty() }?.let { files ->
                appendLine("\n## 已提交文件\n")
                files.forEach { appendLine("- ${it.fileName}") }
            }
        }
        item.citel?.let { task ->
            val selected = citel.selectedTask?.id == task.id && citel.selectedTask?.courseId == task.courseId
            appendLine("- 类型：${if (task.programming) "编程作业" else "实验报告"}")
            appendLine("- 开放时间：${task.openTime?.let(::citelDateText) ?: "未提供"}")
            appendLine("- 截止时间：${task.dueTime?.let(::citelDateText) ?: "未提供"}")
            task.discountTime?.let { appendLine("- 折扣开始：${citelDateText(it)}") }
            appendLine("- 提交状态：${if (selected) citel.programmingResult?.displayStatus ?: citel.submission?.status ?: task.submissionStatusText() else task.submissionStatusText()}")
            task.grade?.let { appendLine("- 评分：$it") }
            appendLine("\n[在网页中查看作业要求](${task.url})")
            if (selected) citel.submission?.files?.takeIf { it.isNotEmpty() }?.let { files ->
                appendLine("\n## 已提交文件\n")
                files.forEach { appendLine("- ${it.name}") }
            }
        }
    }
}
