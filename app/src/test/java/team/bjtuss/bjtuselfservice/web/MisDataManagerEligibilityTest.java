package team.bjtuss.bjtuselfservice.web;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MisDataManagerEligibilityTest {
    @Test
    public void mainProgramParserUsesMainProgramInsteadOfOtherPrograms() {
        String html = "<table><tbody>"
                + "<tr><td><a href=\"/training/training/program/stuview/6000/\">"
                + "[6000] 辅修专业-辅修-2026级</a></td></tr>"
                + "<tr><td><a href=\"/training/training/program/stuview/6449/\">"
                + "[6449] 计算机科学与技术学院-2001 数据科学与大数据技术（数据智能）-主修-2025级"
                + "</a></td></tr>"
                + "</tbody></table>";

        assertEquals(2025, MisDataManager.parseMainTrainingProgramGradeYear(html));
    }

    @Test(expected = IllegalArgumentException.class)
    public void mainProgramParserFailsWhenMainProgramIsMissing() {
        String html = "<table><tbody><tr><td>"
                + "<a href=\"/training/training/program/stuview/6000/\">"
                + "[6000] 辅修专业-辅修-2026级</a>"
                + "</td></tr></tbody></table>";

        MisDataManager.parseMainTrainingProgramGradeYear(html);
    }

    @Test
    public void classParserUsesClassInsteadOfDisplayedGrade() {
        String html = "<table><tbody>"
                + "<tr><th colspan=\"8\">培养信息</th></tr>"
                + "<tr><th>年级</th><td><p>2024级</p></td>"
                + "<th>班级</th><td><p>大数据2501</p></td></tr>"
                + "</tbody></table>";

        assertEquals(2025, MisDataManager.parseClassEnrollmentYear(html));
    }

    @Test(expected = IllegalArgumentException.class)
    public void classParserFailsWhenClassSuffixIsMissing() {
        String html = "<table><tbody>"
                + "<tr><th colspan=\"8\">培养信息</th></tr>"
                + "<tr><th>班级</th><td><p>大数据实验班</p></td></tr>"
                + "</tbody></table>";

        MisDataManager.parseClassEnrollmentYear(html);
    }
}
