package com.hfdlys.bjtuselfservice.web;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Headers;
import okhttp3.FormBody;
import okhttp3.RequestBody;
import okhttp3.Call;
import okhttp3.Callback;

import org.json.JSONException;
import org.json.JSONObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.hfdlys.bjtuselfservice.Model;
import com.hfdlys.bjtuselfservice.StudentAccountManager;
import com.hfdlys.bjtuselfservice.StudentAccountManager.Status;
import com.hfdlys.bjtuselfservice.utils.ImageToTensorConverter;
import com.hfdlys.bjtuselfservice.utils.Utils;

public class NetworkDataManager {
    public static void login(OkHttpClient client,String stuId, String stuPasswd, WebCallback loginCallback) {
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
                                .url("https://cas.bjtu.edu.cn/captcha/image/" + value + "/")
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
                                Model model = Model.getInstance();
                                float[] tensor = ImageToTensorConverter.convertToTensor(img, 130, 42);
                                String captcha = model.predict(tensor);
                                String ans = Utils.calculate(captcha);
                                if (ans == null) {
                                    loginCallback.onFailure(1);
                                    return;
                                }
                                String nextUrl = url.substring("https://cas.bjtu.edu.cn".length());
                                FormBody formBody = new FormBody.Builder()
                                        .add("csrfmiddlewaretoken", csrfmiddlewaretoken)
                                        .add("captcha_0", value)
                                        .add("captcha_1", ans)
                                        .add("loginname", stuId)
                                        .add("password", stuPasswd)
                                        .add("next", nextUrl)
                                        .build();
                                Request loginRequest = new Request.Builder()
                                        .url("https://cas.bjtu.edu.cn/auth/login/?next=" + nextUrl)
                                        .header("Host", "cas.bjtu.edu.cn")
                                        .header("Referer", "https://cas.bjtu.edu.cn/auth/login/")
                                        .header("Origin", "https://cas.bjtu.edu.cn")
                                        .header("Content-Type", "application/x-www-form-urlencoded")
                                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                                        .post(formBody)
                                        .build();
                                client.newCall(loginRequest).enqueue(new Callback() {
                                    @Override
                                    public void onFailure(Call call, IOException e) {
                                        loginCallback.onFailure(0);
                                    }

                                    @Override
                                    public void onResponse(Call call, Response response) throws IOException {
                                        String misUrl = response.request().url().toString();
                                        Request misRequest = new Request.Builder()
                                                .url(misUrl)
                                                .header("Host", "mis.bjtu.edu.cn")
                                                .build();
                                        client.newCall(misRequest).enqueue(new Callback() {
                                            @Override
                                            public void onFailure(Call call, IOException e) {
                                                loginCallback.onFailure(0);
                                            }

                                            @Override
                                            public void onResponse(Call call, Response response) throws IOException {

                                                if (response.request().url().toString().equals("https://mis.bjtu.edu.cn/home/")) {
                                                    Document doc = Jsoup.parse(response.body().string());
                                                    Element name = doc.selectFirst(".name_right > h3 > a");
                                                    String nameStr = name.text().split("，")[0];
                                                    Element id = doc.selectFirst(".name_right .nr_con span:contains(身份)");
                                                    String idStr = id.text().replace("身份：", "");
                                                    Element department = doc.selectFirst(".name_right .nr_con span:contains(部门)");
                                                    String departmentStr = department.text().replace("部门：", "");
                                                    loginCallback.onResponse(nameStr + ";" + idStr + ";" + departmentStr);
                                                    return;
                                                } else {
                                                    loginCallback.onFailure(1);
                                                }
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
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
                            OkHttpClient tt = client;
                            loginCallback.onResponse(response.request().url().toString());
                        } else {
                            loginCallback.onFailure(1);
                        }
                    }
                });
            }
        });
    }

    public static void getGrade(OkHttpClient client, WebCallback ResCallback) {
        Request request = new Request.Builder()
                .url("https://aa.bjtu.edu.cn/score/scores/stu/view/")
                .header("Host", "aa.bjtu.edu.cn")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                ResCallback.onFailure(0);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<StudentAccountManager.Grade> gradeList = new ArrayList<>();
                Document doc = Jsoup.parse(response.body().string());
                Element table = doc.selectFirst("table");
                Elements rows = table.select("tr");
                rows.remove(0);
                for (Element row : rows) {
                    Elements cols = row.select("td");
                    String year = cols.get(1).text().replace("\n","").replace("\t","").replace(" ","");
                    String courseName = cols.get(2).text().replace("\n","").replace("\t","").replace(" ","");
                    String courseGPA = cols.get(3).text().replace("\n","").replace("\t","").replace(" ","");
                    String courseScore = cols.get(4).text().replace("\n","").replace("\t","").replace(" ","");
                    String teacher = cols.get(6).text().replace("\n","").replace("\t","").replace(" ","");
                    StudentAccountManager.Grade grade = new StudentAccountManager.Grade(courseName, teacher, courseScore, courseGPA, year);
                    gradeList.add(grade);
                }
                ResCallback.onResponse(gradeList);
            }
        });
    }

    public static void getStatus(OkHttpClient client, WebCallback<Status> ResCallback) {
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
                JSONObject jsonObject = null;
                try {
                    jsonObject = new JSONObject(response.body().string());
                    String netFee = jsonObject.getString("net_fee");
                    double ecardYuer = jsonObject.getDouble("ecard_yuer");
                    String newmailCount = jsonObject.getString("newmail_count");
                    Status status = new Status(newmailCount, ecardYuer, netFee);
                    ResCallback.onResponse(status);
                } catch (JSONException e) {
                    ResCallback.onFailure(1);
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public interface WebCallback<T> {
        void onResponse(T obj);
        void onFailure(int errcode);
    }
}
