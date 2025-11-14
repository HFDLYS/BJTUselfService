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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import team.bjtuss.bjtuselfservice.CaptchaModel;
import team.bjtuss.bjtuselfservice.StudentAccountManager;
import team.bjtuss.bjtuselfservice.entity.CourseEntity;
import team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity;
import team.bjtuss.bjtuselfservice.entity.GradeEntity;
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository;
import team.bjtuss.bjtuselfservice.utils.ImageToTensorConverter;
import team.bjtuss.bjtuselfservice.utils.Network.WebCallback;
import team.bjtuss.bjtuselfservice.utils.Utils;

public class MisDataManager {
    public static void login(OkHttpClient client, String stuId, String stuPasswd, WebCallback loginCallback) {

        Request request = new Request.Builder()
                .url("https://mis.bjtu.edu.cn/auth/sso/?next=/")
                .header("Host", "mis.bjtu.edu.cn")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                loginCallback.onFailure(0);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String url = response.request().url().toString();
                Request ttRequest = new Request.Builder()
                        .url(url)
                        .header("Host", "cas.bjtu.edu.cn")
                        .header("Referer", "https://mis.bjtu.edu.cn/auth/sso/?next=/")
                        .build();
                client.newCall(ttRequest).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        loginCallback.onFailure(0);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        Document doc = Jsoup.parse(response.body().string());
                        String value = doc.select("input#id_captcha_0").attr("value");
                        String csrfmiddlewaretoken = doc.select("input[name=csrfmiddlewaretoken]").attr("value");
                        Request captchaRequest = new Request.Builder()
                                .url("https://cas.bjtu.edu.cn/image/" + value + "/")
                                .build();

                        client.newCall(captchaRequest).enqueue(new Callback() {
                            @Override
                            public void onFailure(Call call, IOException e) {
                                loginCallback.onFailure(0);
                            }

                            @Override
                            public void onResponse(Call call, Response response) throws IOException {
                                if (!response.isSuccessful()) {
                                    loginCallback.onFailure(1);
                                    return;
                                }
                                byte[] img = response.body().bytes();
                                //神经网络推理
                                CaptchaModel captchaModel = CaptchaModel.getInstance();
                                float[] tensor = ImageToTensorConverter.convertToTensor(img, 130, 42);
                                String captcha = captchaModel.predict(tensor);
                                String ans = Utils.calculate(captcha);
                                if (ans == null) {
                                    loginCallback.onFailure(1);
                                    return;
                                }
                                String nextUrl = url.substring("https://cas.bjtu.edu.cn/auth/login/?next=".length());
                                FormBody formBody = new FormBody.Builder()
                                        .add("csrfmiddlewaretoken", csrfmiddlewaretoken)
                                        .add("captcha_0", value)
                                        .add("captcha_1", ans)
                                        .add("loginname", stuId)
                                        .add("password", stuPasswd)
                                        .build();
                                Request loginRequest = new Request.Builder()
                                        .url("https://cas.bjtu.edu.cn/auth/login/?next=" + nextUrl)
                                        .header("Referer", url)
                                        .header("Origin", "https://cas.bjtu.edu.cn")
                                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36 Edg/142.0.0.0")
                                        .post(formBody)
                                        .build();


                                client.newCall(loginRequest).enqueue(new Callback() {
                                    @Override
                                    public void onFailure(Call call, IOException e) {
                                        Log.e("LoginError", "Request failed", e);
                                        Log.e("LoginError", "URL: " + call.request().url());
                                        Log.e("LoginError", "Error type: " + e.getClass().getName());
                                        Log.e("LoginError", "Error message: " + e.getMessage());

                                        // 如果是 StreamResetException，打印更多信息
                                        if (e instanceof okhttp3.internal.http2.StreamResetException) {
                                            Log.e("LoginError", "HTTP/2 Stream Reset Error");
                                        }

                                        loginCallback.onFailure(0);
                                    }

                                    @Override
                                    public void onResponse(Call call, Response response) throws IOException {
                                        try {
                                            // OkHttp 会自动跟随重定向到 mis.bjtu.edu.cn/home/
                                            String finalUrl = response.request().url().toString();
                                            String body = response.body().string();

                                            Log.d("Login", "Final URL: " + finalUrl);
                                            Log.d("Login", "Response code: " + response.code());

                                            if (finalUrl.contains("mis.bjtu.edu.cn/home/")) {
                                                Document doc = Jsoup.parse(body);
                                                Element name = doc.selectFirst(".name_right > h3 > a");
                                                if (name == null) {
                                                    loginCallback.onFailure(1);
                                                    return;
                                                }
                                                String nameStr = name.text().split("，")[0];
                                                Element id = doc.selectFirst(".name_right .nr_con span:contains(身份)");
                                                String idStr = id.text().replace("身份：", "");
                                                Element department = doc.selectFirst(".name_right .nr_con span:contains(部门)");
                                                String departmentStr = department.text().replace("部门：", "");
                                                loginCallback.onResponse(nameStr + ";" + idStr + ";" + departmentStr);
                                            } else {
                                                loginCallback.onFailure(1);
                                            }
                                        } finally {
                                            response.close();
                                        }
                                    }
                                });
                                response.close();
                            }
                        });
                        response.close();
                    }
                });
                response.close();
            }
        });
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
                if (url.contains("https://aa.bjtu.edu.cn/classroom/timeholdresult/room_view/?zc=")) {
                    int nowWeek = Integer.parseInt(url.split("zc=")[1].split("&")[0]);
                    url += "&page=1&perpage=500";
                    Request request = new Request.Builder()
                            .url(url)
                            .header("Host", "aa.bjtu.edu.cn")
                            .build();
                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            try {
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
                                    Element colName = cols.get(0);
                                    String classroomName = colName.text();
                                    classroomName = classroomName.split(" ")[0];
                                    List<Integer> classroomList = new ArrayList<>();
                                    for (int j = 0; j < 7; j++) {
                                        Element col = cols.get(1 + 7 * index + j);
                                        String style = col.attr("style");
                                        switch (style) {
                                            case "background-color: #fff":
                                                classroomList.add(0);
                                                break;
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
                                        }
                                    }
                                    classroomMap.put(classroomName, classroomList);
                                }
                                ResCallback.onResponse(classroomMap);
                            } catch (IOException e) {
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
                response.close();
            }
        });
    }

    public static void xsmislogin(OkHttpClient client, WebCallback ResCallback) {
        Request request = new Request.Builder()
                .url("https://xsmis.bjtu.edu.cn/v4/user/cas_login/?next=client%2Fhome")
                .header("Host", "xsmis.bjtu.edu.cn")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                ResCallback.onFailure(0);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String content = response.body().string();
                String nextUrl;
                try {
                    JSONObject temp = new JSONObject(content);
                    nextUrl = temp.getString("url");
                } catch (JSONException e) {
                    ResCallback.onFailure(1);
                    return;
                }
                Request request = new Request.Builder()
                        .url(nextUrl)
                        .build();
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        ResCallback.onFailure(0);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                        CookieJar cookieJar = client.cookieJar();

                        if (response.request().url().toString().equals("https://xsmis.bjtu.edu.cn/#/client/home")) {
                            ResCallback.onResponse(response.request().url().toString());
                        } else {
                            ResCallback.onFailure(1);
                        }
                        response.close();
                    }
                });
                response.close();
            }
        });
    }

    public static void getStatus(OkHttpClient client, WebCallback<StudentAccountManager.Status> ResCallback) {
        String authorization = client.cookieJar().loadForRequest(Objects.requireNonNull(HttpUrl.parse("https://xsmis.bjtu.edu.cn"))).get(1).value();
        Request request = new Request.Builder()
                .url("https://xsmis.bjtu.edu.cn/v4/people/appdata/")
                .header("Host", "xsmis.bjtu.edu.cn")
                .header("Authorization", "token " + authorization)
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
                    JSONArray data = jsonObject.getJSONArray("data");
                    String netFee = "0";
                    String ecardYuer = "0";
                    String newmailCount = "0";
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        if (item.getString("tag").equals("ecard")) {
                            ecardYuer = item.getString("count");
                        } else if (item.getString("tag").equals("web_count")) {
                            netFee = item.getString("count");
                        } else if (item.getString("tag").equals("email")) {
                            newmailCount = item.getString("count");
                        }
                    }
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
