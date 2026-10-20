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

public class MisDataManager {
    private static final long DATA_REQUEST_DELAY_MS = 500L;
    private static final String SSO_LOGIN_PAGE =
            "https://sso.bjtu.edu.cn/sso/login" +
                    "?service=https%3A%2F%2Fmis.bjtu.edu.cn%2Fhome%2F" +
                    "&pageName=login&serverContextPath=" +
                    "&serviceName=%E7%BB%9F%E4%B8%80%E8%BA%AB%E4%BB%BD%E8%AE%A4%E8%AF%81%E7%B3%BB%E7%BB%9F";
    private static final Pattern SM2_PUBLIC_KEY_PATTERN =
            Pattern.compile("\"publicKey\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SSO_ERROR_PATTERN =
            Pattern.compile("var error = (\\{.*?\\});", Pattern.DOTALL);

    /**
     * Native login against the new sso.bjtu.edu.cn page.
     *
     * The page itself posts the same hidden fields and an SM2-encrypted
     * password. Reproducing that request here keeps the original app UX:
     * the user only sees the app's own student-id/password dialog.
     */
    public static void loginSso(OkHttpClient client, String stuId, String stuPasswd,
                                WebCallback<String> loginCallback) {
        Request pageRequest = new Request.Builder()
                .url(SSO_LOGIN_PAGE)
                .header("Host", "sso.bjtu.edu.cn")
                .build();
        client.newCall(pageRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                loginCallback.onFailure(0);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        loginCallback.onFailure(1);
                        return;
                    }
                    String pageHtml = response.body().string();
                    Document doc = Jsoup.parse(pageHtml);
                    Element form = doc.selectFirst("form#loginForm");
                    Matcher matcher = SM2_PUBLIC_KEY_PATTERN.matcher(pageHtml);
                    if (form == null || !matcher.find()) {
                        Log.e("LoginError", "SSO page has no login form or SM2 key");
                        loginCallback.onFailure(1);
                        return;
                    }
                    // The page embeds the key inside a JSON string, where "/" may
                    // be escaped as "\/". Base64 must never see that backslash.
                    String publicKey = matcher.group(1).replace("\\/", "/");
                    FormBody.Builder formBody = new FormBody.Builder();
                    boolean hasUsername = false;
                    boolean hasPassword = false;
                    for (Element input : form.select("input")) {
                        String name = input.attr("name");
                        if (name.isEmpty()) {
                            continue;
                        }
                        String value = input.attr("value");
                        if ("username".equals(name)) {
                            value = stuId;
                            hasUsername = true;
                        } else if ("password".equals(name)) {
                            try {
                                value = Sm2Crypto.encrypt(stuPasswd, publicKey);
                            } catch (Exception e) {
                                Log.e("LoginError", "SM2 encryption failed", e);
                                loginCallback.onFailure(1);
                                return;
                            }
                            hasPassword = true;
                        }
                        formBody.add(name, value);
                    }
                    if (!hasUsername) {
                        formBody.add("username", stuId);
                    }
                    if (!hasPassword) {
                        try {
                            formBody.add("password", Sm2Crypto.encrypt(stuPasswd, publicKey));
                        } catch (Exception e) {
                            Log.e("LoginError", "SM2 encryption failed", e);
                            loginCallback.onFailure(1);
                            return;
                        }
                    }

                    String action = form.attr("action");
                    String postUrl = action.startsWith("http")
                            ? action
                            : "https://sso.bjtu.edu.cn" + (action.startsWith("/") ? action : "/" + action);
                    Request loginRequest = new Request.Builder()
                            .url(postUrl)
                            .header("Referer", SSO_LOGIN_PAGE)
                            .header("Origin", "https://sso.bjtu.edu.cn")
                            .header("User-Agent",
                                    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                                            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                            .post(formBody.build())
                            .build();

                    client.newCall(loginRequest).enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            loginCallback.onFailure(0);
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response loginResponse) {
                            try {
                                if (loginResponse.body() == null) {
                                    loginCallback.onFailure(1);
                                    return;
                                }
                                String finalUrl = loginResponse.request().url().toString();
                                String body = loginResponse.body().string();
                                Log.d("Login", "SSO final URL: " + finalUrl);

                                if (finalUrl.contains("mis.bjtu.edu.cn/home")
                                        || body.contains("class=\"name_right\"")) {
                                    Document home = Jsoup.parse(body);
                                    Element name = home.selectFirst(".name_right > h3 > a");
                                    Element id = home.selectFirst(".name_right .nr_con span:contains(身份)");
                                    Element department = home.selectFirst(".name_right .nr_con span:contains(部门)");
                                    if (name == null || id == null || department == null) {
                                        Log.e("LoginError", "MIS home has no identity fields");
                                        loginCallback.onFailure(1);
                                        return;
                                    }
                                    String nameStr = name.text().split("，")[0];
                                    String idStr = id.text().replace("身份：", "");
                                    String departmentStr = department.text().replace("部门：", "");
                                    loginCallback.onResponse(nameStr + ";" + idStr + ";" + departmentStr);
                                } else {
                                    boolean badCredentials = false;
                                    boolean captcha = false;
                                    Matcher errorMatcher = SSO_ERROR_PATTERN.matcher(body);
                                    if (errorMatcher.find()) {
                                        try {
                                            JSONObject error = new JSONObject(errorMatcher.group(1));
                                            int code = error.optInt("code");
                                            String message = error.optString("msg", "");
                                            Log.e("LoginError", "SSO error " + code + ": " + message);
                                            if (code == 40400
                                                    || message.contains("账号或密码错误")
                                                    || message.contains("用户名或密码错误")
                                                    || message.contains("密码错误")) {
                                                badCredentials = true;
                                            }
                                            if (message.contains("验证码")
                                                    || message.contains("滑块")
                                                    || message.contains("验证")) {
                                                captcha = true;
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    }
                                    if (badCredentials) {
                                        loginCallback.onFailure(1);
                                    } else if (captcha) {
                                        Log.e("LoginError", "SSO requires verification");
                                        loginCallback.onFailure(2);
                                    } else {
                                        Log.e("LoginError", "SSO login failed at " + finalUrl);
                                        loginCallback.onFailure(1);
                                    }
                                }
                            } catch (Exception e) {
                                Log.e("LoginError", "SSO response parse failed", e);
                                loginCallback.onFailure(1);
                            } finally {
                                loginResponse.close();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e("LoginError", "SSO page parse failed", e);
                    loginCallback.onFailure(1);
                } finally {
                    response.close();
                }
            }
        });
    }

    public static void login(OkHttpClient client, String stuId, String stuPasswd, WebCallback loginCallback) {
        loginSso(client, stuId, stuPasswd, loginCallback);
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
                                            courseTime = child.select("div[style^=max-width]").first().text().split("周")[0] + "周";
                                            courseTime = courseTime.replace(" ", "")
                                                    .replace("\n", "");
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
                                    List<Integer> classroomList = new ArrayList<>();
                                    int dayColumnStart = 1 + 7 * index;
                                    for (int j = 0; j < 7; j++) {
                                        int colIndex = dayColumnStart + j;
                                        if (colIndex >= cols.size()) {
                                            // Keep exactly seven slots even when the
                                            // school page has fewer columns than expected.
                                            classroomList.add(0);
                                            continue;
                                        }
                                        Element col = cols.get(colIndex);
                                        String style = col.attr("style");
                                        switch (style) {
                                            case "background-color: #e46868":
                                                classroomList.add(1);
                                                break;
                                            case "background-color: #9e6868":
                                                classroomList.add(2);
                                                break;
                                            case "background-color: #394ed6":
                                                classroomList.add(3);
                                                break;
                                            case "background-color: #77bf6d":
                                                classroomList.add(4);
                                                break;
                                            case "background-color: #d8cc56":
                                                classroomList.add(5);
                                                break;
                                            case "background-color: #fff":
                                            default:
                                                classroomList.add(0);
                                                break;
                                        }
                                    }
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
