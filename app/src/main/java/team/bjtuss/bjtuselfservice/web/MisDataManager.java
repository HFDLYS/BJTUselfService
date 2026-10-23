package team.bjtuss.bjtuselfservice.web;

import static team.bjtuss.bjtuselfservice.utils.Utils.convertAndFormatGradeScore;

import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import team.bjtuss.bjtuselfservice.StudentAccountManager;
import team.bjtuss.bjtuselfservice.entity.CourseEntity;
import team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity;
import team.bjtuss.bjtuselfservice.entity.GradeEntity;
import team.bjtuss.bjtuselfservice.utils.Network.WebCallback;
import team.bjtuss.bjtuselfservice.utils.Utils;
import team.bjtuss.bjtuselfservice.utils.CourseWeeks;

public class MisDataManager {
    private static final long DATA_REQUEST_DELAY_MS = 500L;

    public static void login(OkHttpClient client, String stuId, String stuPasswd, WebCallback<String> loginCallback) {
        MisSsoLogin.login(client, stuId, stuPasswd, loginCallback);
    }

    public static void checkCookie(OkHttpClient client, WebCallback loginCallback) {
        Request request = new Request.Builder()
                .url("https://mis.bjtu.edu.cn/home/")
                .header("Host", "mis.bjtu.edu.cn")
                .header("Referer", "https://mis.bjtu.edu.cn/home/")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                loginCallback.onFailure(0);

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.request().url().toString().equals("https://mis.bjtu.edu.cn/home/")) {
                    loginCallback.onResponse(response.request().url().toString());
                } else {
                    loginCallback.onFailure(1);
                }
                response.close();
            }
        });
    }

    public static void aaLogin(OkHttpClient client, WebCallback loginCallback) {
        Request request = new Request.Builder()
                .url("https://mis.bjtu.edu.cn/module/module/10/")
                .header("Host", "mis.bjtu.edu.cn")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                loginCallback.onFailure(0);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                Document doc = Jsoup.parse(response.body().string());
                String url = doc.selectFirst("form[id=redirect]").attr("action");
                Request request = new Request.Builder()
                        .url(url + '?')
                        .header("Referer", "https://mis.bjtu.edu.cn/module/module/10/")
                        .build();
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        loginCallback.onFailure(0);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.request().url().toString().equals("https://aa.bjtu.edu.cn/notice/item/")) {
                            loginCallback.onResponse(response.request().url().toString());
                        } else {
                            loginCallback.onFailure(1);
                        }
                        response.close();
                    }
                });
            }
        });
    }

    public static void bksyLogin(OkHttpClient client, WebCallback loginCallback) {
        Request request = new Request.Builder()
                .url("https://mis.bjtu.edu.cn/module/module/104/")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                loginCallback.onFailure(0);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.request().url().toString().equals("https://bksy.bjtu.edu.cn/login_introduce_t.html")) {
                    client.newCall(new Request.Builder()
                            .url("https://bksycenter.bjtu.edu.cn/NoMasterJumpPage.aspx?URL=jwcZhjx&amp;FPC=page:jwcZhjx")
                            .build()).enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            loginCallback.onFailure(0);
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            String url = response.request().url().toString();
                            if (response.request().url().toString().equals("http://123.121.147.7:88/ve/back/core/main/qx_kl.jsp")) {
                                loginCallback.onResponse(response.request().url().toString());
                            } else {
                                loginCallback.onFailure(1);
                            }
                            response.close();
                        }
                    });
                } else {
                    loginCallback.onFailure(1);
                }
                response.close();
            }
        });
    }

    public static void getGrade(OkHttpClient client, WebCallback ResCallback, String ctype) {
        Request request = new Request.Builder()
                .url("https://aa.bjtu.edu.cn/score/scores/stu/view/?page=1&perpage=500&ctype=" + ctype)
                .header("Host", "aa.bjtu.edu.cn")
                .build();
        try {
            Thread.sleep(DATA_REQUEST_DELAY_MS / 5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ResCallback.onFailure(0);
            return;
        }
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                ResCallback.onFailure(0);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    List<GradeEntity> gradeList = new ArrayList<>();
                    Document doc = Jsoup.parse(response.body().string());
                    if (doc.selectFirst("table") == null) {
                        ResCallback.onFailure(1);
                        return;
                    }
                    Element table = doc.selectFirst("table");
                    Elements rows = table.select("tr");
                    rows.remove(0);
                    for (Element row : rows) {
                        Elements cols = row.select("td");
                        String year = cols.get(1).text().replace("\n", "").replace("\t", "").replace(" ", "");
                        String courseName = cols.get(2).text().replace("\n", "").replace("\t", "").replace(" ", "");
                        String courseGPA = cols.get(3).text().replace("\n", "").replace("\t", "").replace(" ", "");
                        Element detail = cols.get(7).selectFirst("span[data-content]");
                        if (courseGPA.isEmpty()) {
                            courseGPA = "0.0";
                        }
                        String courseScore = cols.get(4).text().replace("\n", "").replace("\t", "").replace(" ", "");
                        try {
                            courseScore = convertAndFormatGradeScore(courseScore);
                        } catch (Exception e) {
                            courseScore = "***";
                        }
                        String teacher = cols.get(6).text().replace("\n", "").replace("\t", "").replace(" ", "");
                        String tag = cols.get(1).text().replace("\n", "").replace("\t", "").replace(" ", "");
                        GradeEntity grade = new GradeEntity(courseName, teacher, courseScore, courseGPA, year);
                        grade.tag = tag;
                        if (detail != null) {
                            String dataContent = detail.attr("data-content");
                            Document contentDoc = Jsoup.parse(dataContent);
                            Element divElement = contentDoc.selectFirst("div[style='width:200px;line-height:25px;']");
                            if (divElement != null) {
                                grade.detail = divElement.html().replace("\t", "").replace(" ", "").replace("<br>", "");
                            }
                        } else {
                            grade.detail = "";
                        }
                        gradeList.add(grade);
                    }
                    ResCallback.onResponse(gradeList);
                } catch (IOException e) {
                    ResCallback.onFailure(1);
                } finally {
                    response.close();
                }
            }
        });
    }

    public static void getExamSchedule(OkHttpClient client, WebCallback<List<ExamScheduleEntity>> ResCallback) {
        Request request = new Request.Builder()
                .url("https://aa.bjtu.edu.cn/examine/examplanstudent/stulist/")
                .header("Host", "aa.bjtu.edu.cn")
                .build();
        try {
            Thread.sleep(DATA_REQUEST_DELAY_MS / 5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ResCallback.onFailure(0);
            return;
        }
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                ResCallback.onFailure(0);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    Document doc = Jsoup.parse(response.body().string());
                    Element table = doc.selectFirst("tbody");
                    if (table == null) {
                        ResCallback.onFailure(1);
                        return;
                    }
                    Elements rows = table.select("tr");
                    List<ExamScheduleEntity> examScheduleList = new ArrayList<>();
                    for (Element row : rows) {
                        Elements cols = row.select("td");
                        String type = cols.get(1).text();
                        String CourseName = cols.get(2).text();
                        String ExamTime = cols.get(3).text();
                        String ExamStatus = cols.get(4).text();
                        String Detail = cols.get(5).text();


                        examScheduleList.add(new ExamScheduleEntity(type, CourseName, ExamTime, ExamStatus, Detail));
                    }
                    ResCallback.onResponse(examScheduleList);
                } catch (IOException e) {
                    ResCallback.onFailure(1);
                } finally {
                    response.close();
                }
            }
        });
    }

    public static void getCourse(OkHttpClient client, Boolean isChecked, WebCallback<List<List<CourseEntity>>> ResCallback) {
        String url = !isChecked ? "https://aa.bjtu.edu.cn/course_selection/courseselect/stuschedule/" : "https://aa.bjtu.edu.cn/course_selection/courseselecttask/schedule/";
        Request request = new Request.Builder()
                .url(url)
                .header("Host", "aa.bjtu.edu.cn")
                .build();
        try {
            Thread.sleep(DATA_REQUEST_DELAY_MS / 5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ResCallback.onFailure(0);
            return;
        }
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                ResCallback.onFailure(0);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    boolean isCurrentTerm = isChecked;

                    Map<String, String> courseId2Teacher;
                    courseId2Teacher = getTeachers(client);

                    Document doc = Jsoup.parse(response.body().string());
                    Element table = doc.selectFirst("table");
                    if (table == null) {
                        ResCallback.onFailure(1);
                        return;
                    }
                    Elements rows = table.select("tr");
                    List<List<CourseEntity>> courseList = new ArrayList<>();
                    rows.remove(0);
                    int rowNumber = 0;
                    for (Element row : rows) {
                        int columnNumber = 0;
                        rowNumber++;
                        Elements cols = row.select("td");
                        cols.remove(0);
                        courseList.add(null);
                        for (Element col : cols) {
                            columnNumber++;
                            if (col.text().isEmpty()) {
                                courseList.add(null);
                            } else {
                                List<CourseEntity> courses = new ArrayList<>();
                                for (Element child : col.children()) {
                                    try {
                                        String rawIdAndName;
                                        if (!isChecked) {
                                            rawIdAndName = child.html();
                                        } else {
                                            rawIdAndName = child.select("span").first().html();
                                        }
                                        String[] idAndNameParts = rawIdAndName.split("<br>", 2);
                                        String courseId = Jsoup.parse(idAndNameParts[0]).text().trim();

                                        String courseName;
                                        if (!isChecked) {
                                            courseName = Jsoup.parse(idAndNameParts[1]).select("span").first().text().trim();
                                        } else {
                                            courseName = Jsoup.parse(idAndNameParts[1]).text().trim();
                                        }

                                        // 解析课程时间
                                        String courseTime;
                                        try {
                                            courseTime = CourseWeeks.extract(child.select("div[style^=max-width]").first().text());
                                        } catch (Exception e) {
                                            courseTime = "未知";
                                        }

                                        // 解析课程教师
                                        String courseTeacher;
                                        try {
                                            courseTeacher = child.select("div[style^=max-width] i").first().text();
                                        } catch (Exception e) {
                                            String courseNameWithKxh = courseName.split(" ")[0] + " " + courseId.split(" ")[1].substring(1, 3);
                                            courseTeacher = courseId2Teacher.get(courseNameWithKxh);
                                            courseTeacher = courseTeacher == null ? "?" : courseTeacher;
                                        }

                                        // 解析课程地点
                                        String coursePlace;
                                        try {
                                            coursePlace = child.select("span.text-muted").first().text()
                                                    .replace(" ", "")
                                                    .replace("\n", "");
                                        } catch (Exception e) {
                                            coursePlace = "未知";
                                        }


                                        //
                                        courses.add(new CourseEntity(
                                                courseId,
                                                courseName,
                                                courseTeacher,
                                                (rowNumber - 1) * 8 + columnNumber,
                                                courseTime,
                                                coursePlace,
                                                isCurrentTerm));
                                    } catch (Exception e) {
                                        continue;
                                    }
                                }
                                courseList.add(courses);
                            }
                        }
                    }
                    ResCallback.onResponse(courseList);
                } catch (IOException e) {
                    ResCallback.onFailure(1);
                } finally {
                    response.close();
                }
            }
        });
    }

    public static void getClassroom(OkHttpClient client, WebCallback<Map<String, List<Integer>>> ResCallback) {
        String url = "https://aa.bjtu.edu.cn/classroom/timeholdresult/room_view/";
        try {
            Thread.sleep(DATA_REQUEST_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ResCallback.onFailure(0);
            return;
        }
        Request request = new Request.Builder()
                .url(url)
                .header("Host", "aa.bjtu.edu.cn")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                ResCallback.onFailure(0);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                String url = response.request().url().toString();
                Log.d("ClassroomMap", "入口页响应: code=" + response.code() + ", url=" + url);
                response.close();
                if (url.contains("https://aa.bjtu.edu.cn/classroom/timeholdresult/room_view/?zc=")) {
                    int nowWeek = Integer.parseInt(url.split("zc=")[1].split("&")[0]);
                    url += "&page=1&perpage=500";
                    try {
                        Thread.sleep(DATA_REQUEST_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        ResCallback.onFailure(0);
                        return;
                    }
                    Request request = new Request.Builder()
                            .url(url)
                            .header("Host", "aa.bjtu.edu.cn")
                            .build();
                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            Log.d(
                                    "ClassroomMap",
                                    "数据页响应: code=" + response.code() + ", url=" + response.request().url()
                            );
                            try {
                                if (response.body() == null) {
                                    ResCallback.onFailure(1);
                                    return;
                                }
                                Document doc = Jsoup.parse(response.body().string());
                                Element table = doc.selectFirst("table");
                                if (table == null) {
                                    ResCallback.onFailure(1);
                                    return;
                                }
                                Map<String, List<Integer>> classroomMap = new HashMap<>();
                                classroomMap.put("nowWeek", List.of(nowWeek));
                                Elements rows = table.select("tr");
                                Calendar calendar = Calendar.getInstance();
                                int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
                                int index = (dayOfWeek == Calendar.SUNDAY) ? 6 : (dayOfWeek - 2);
                                for (int i = 2; i < rows.size(); i++) {
                                    Element row = rows.get(i);
                                    Elements cols = row.select("td");
                                    if (cols.isEmpty()) {
                                        continue;
                                    }
                                    Element colName = cols.get(0);
                                    String classroomName = colName.text();
                                    classroomName = classroomName.split(" ")[0];
                                    List<Integer> classroomList = parseClassroomSlots(cols, index);
                                    classroomMap.put(classroomName, classroomList);
                                }
                                ResCallback.onResponse(classroomMap);
                            } catch (Exception e) {
                                Log.e("ClassroomMap", "Failed to parse classroom table", e);
                                ResCallback.onFailure(1);
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            ResCallback.onFailure(0);
                        }
                    });
                } else {
                    ResCallback.onFailure(1);
                }
            }
        });
    }

    public static void getStatus(OkHttpClient client, WebCallback<StudentAccountManager.Status> ResCallback) {
        Request request = new Request.Builder()
                .url("https://mis.bjtu.edu.cn/osys_ajax_wrap/")
                .header("Host", "mis.bjtu.edu.cn")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                ResCallback.onFailure(0);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject jsonObject = new JSONObject(response.body().string());
                    String netFee = jsonObject.getString("net_fee");
                    String ecardYuer = jsonObject.getString("ecard_yuer");
                    String newmailCount = jsonObject.getString("newmail_count");

                    StudentAccountManager.Status status = new StudentAccountManager.Status(newmailCount, ecardYuer, netFee);
                    ResCallback.onResponse(status);
                } catch (Exception e) {
                    ResCallback.onFailure(1);
                }
                response.close();
            }
        });
    }

    // Missing cells and unrecognized styles must never be recommended as free rooms.
    static List<Integer> parseClassroomSlots(Elements cols, int dayIndex) {
        List<Integer> slots = new ArrayList<>();
        Pattern colorPattern = Pattern.compile("(?:^|;)\\s*background-color\\s*:\\s*([^;]+)", Pattern.CASE_INSENSITIVE);
        for (int slot = 0; slot < 7; slot++) {
            int column = 1 + 7 * dayIndex + slot;
            int status = -1;
            if (column >= 1 && column < cols.size()) {
                Matcher color = colorPattern.matcher(cols.get(column).attr("style"));
                if (color.find()) {
                    switch (color.group(1).trim().toLowerCase(java.util.Locale.ROOT)) {
                        case "#fff":
                        case "#ffffff": status = 0; break;
                        case "#e46868": status = 1; break;
                        case "#9e6868": status = 2; break;
                        case "#394ed6": status = 3; break;
                        case "#77bf6d": status = 4; break;
                        case "#d8cc56": status = 5; break;
                        default: break;
                    }
                }
            }
            slots.add(status);
        }
        return slots;
    }

    private static Map<String, String> getTeachers(OkHttpClient client) throws IOException {
        String url = "https://aa.bjtu.edu.cn/course_selection/courseselectabsent/absent_list/";
        Request request = new Request.Builder()
                .url(url)
                .header("Host", "aa.bjtu.edu.cn")
                .build();
        Response response = client.newCall(request).execute();
        Document doc = Jsoup.parse(response.body().string());
        Element table = doc.selectFirst("table");
        if (table == null) {
            return new HashMap<>();
        }
        Elements rows = table.select("tr");
        rows.remove(0);
        rows.remove(0);
        Map<String, String> courseName2Teacher = new HashMap<>();

        for (Element row : rows) {
            try {
                Elements cols = row.select("td");
                String courseName = cols.get(0).text();
                if (courseName.split(" ").length == 3) {
                    courseName = courseName.split(" ")[0] + " " + courseName.split(" ")[1];
                }
                String teacher = cols.get(1).text();
                courseName2Teacher.put(courseName, teacher);
            } catch (Exception e) {
                courseName2Teacher.put("未知课程", "未知教师");

            }

        }


        return courseName2Teacher;
    }
}
