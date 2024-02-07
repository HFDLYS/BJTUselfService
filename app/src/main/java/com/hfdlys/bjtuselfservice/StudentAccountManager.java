package com.hfdlys.bjtuselfservice;



import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.hfdlys.bjtuselfservice.web.NetworkDataManager;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class StudentAccountManager {
    private static StudentAccountManager instance;
    public static class StudentInfo {
        public String stuName;
        public String stuClass;
        public String stuDepartment;
        public String stuId;
        public StudentInfo(String stuName, String stuId, String stuDepartment, String stuClass) {
            this.stuName = stuName;
            this.stuClass = stuClass;
            this.stuDepartment = stuDepartment;
            this.stuId = stuId;
        }
    }
    public static class Grade {
        public String courseName;
        public String courseTeacher;
        public String courseScore;
        public String courseGPA;
        public String courseYear;
        public Grade(String courseName, String courseTeacher, String courseScore, String courseGPA, String courseYear) {
            this.courseName = courseName;
            this.courseTeacher = courseTeacher;
            this.courseScore = courseScore;
            this.courseGPA = courseGPA;
            this.courseYear = courseYear;
        }
        public Grade() {}

    }
    private StudentInfo stuInfo;
    private String stuId;
    private String password;
    private boolean isAaLogin = false;
    private MutableLiveData<StudentInfo> stuInfoLiveData = new MutableLiveData<>();

    // 永续cookie的客户端
    private OkHttpClient client = new OkHttpClient.Builder()
            .cookieJar(new CookieJar() {
                private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();
                @Override
                public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
                    List<Cookie> oldCookies = cookieStore.get(url.host());
                    if (oldCookies != null) {
                        HashMap<String, Cookie> newCookies = new HashMap<>();
                        for (Cookie cookie : oldCookies) {
                            newCookies.put(cookie.name(), cookie);
                        }
                        for (Cookie cookie : cookies) {
                            newCookies.put(cookie.name(), cookie);
                        }
                        cookieStore.put(url.host(), new ArrayList<>(newCookies.values()));
                    } else {
                        cookieStore.put(url.host(), cookies);
                    }
                }

                @NonNull
                @Override
                public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
                    List<Cookie> cookies = cookieStore.get(url.host());
                    return cookies != null ? cookies : new ArrayList<Cookie>();
                }
            })
            .build();
    private StudentAccountManager() {
    }
    public CompletableFuture<Boolean> checkIsLogin() {
        CompletableFuture<Boolean> loginFuture = new CompletableFuture<>();
        NetworkDataManager.WebCallback<String> loginCallback = new NetworkDataManager.WebCallback<String>() {
            @Override
            public void onResponse(String code) {
                loginFuture.complete(true);
            }
            @Override
            public void onFailure(int code) {
                loginFuture.complete(false);
            }
        };

        NetworkDataManager.checkCookie(client, loginCallback);

        return loginFuture;
    }
    public static StudentAccountManager getInstance() {
        if (instance == null) {
            instance = new StudentAccountManager();
            instance.setStudentInfo("陌生人", "1008611", "不晓得", "未知");
        }
        return instance;
    }
    // 初始化登录 或 重载登录
    public CompletableFuture<Boolean> init(String stuId, String password) {
        CompletableFuture<Boolean> loginFuture = new CompletableFuture<>();
        checkIsLogin().thenAccept(isLogin -> {
            if (isLogin) {
                loginFuture.complete(true);
            } else {
                this.stuId = stuId;
                this.password = password;
                NetworkDataManager.login(client, stuId, password, new NetworkDataManager.WebCallback<String>() {
                    @Override
                    public void onResponse(String code) {
                        setStudentInfo(code.split(";")[0], stuId, code.split(";")[2], code.split(";")[1]);
                        loginAa().thenAccept(isAa -> {
                            if (isAa) {
                                loginFuture.complete(true);
                            } else {
                                loginFuture.complete(false);
                            }
                        });
                    }
                    public void onFailure(int code) {
                        NetworkDataManager.login(client, stuId, password, new NetworkDataManager.WebCallback<String>() {
                            @Override
                            public void onResponse(String code) {
                                setStudentInfo(code.split(";")[0], stuId, code.split(";")[2], code.split(";")[1]);
                                loginAa().thenAccept(isAa -> {
                                    if (isAa) {
                                        loginFuture.complete(true);
                                    } else {
                                        loginFuture.complete(false);
                                    }
                                });
                            }
                            public void onFailure(int code) {
                                loginFuture.complete(false);
                            }
                        });
                    }
                });
            }
        });
        return loginFuture;
    }

    public CompletableFuture<Boolean> loginAa() {
        CompletableFuture<Boolean> loginFuture = new CompletableFuture<>();
        checkIsLogin().thenAccept(isLogin -> {
            if (isLogin) {
                NetworkDataManager.aaLogin(client, new NetworkDataManager.WebCallback<String>() {
                    @Override
                    public void onResponse(String code) {
                        isAaLogin = true;
                        loginFuture.complete(true);
                    }
                    @Override
                    public void onFailure(int code) {
                        isAaLogin = false;
                        loginFuture.complete(false);
                    }
                });
            } else {
                loginFuture.complete(false);
            }
        });
        return loginFuture;
    }

    public CompletableFuture<List<Grade>> getGrade() {
        CompletableFuture<List<Grade>> gradeFuture = new CompletableFuture<>();
        checkIsLogin().thenAccept(isLogin -> {
            if (isLogin) {
                NetworkDataManager.getGrade(client, new NetworkDataManager.WebCallback<List<Grade>>() {
                    @Override
                    public void onResponse(List<Grade> resp) {
                        List<Grade> grades = new ArrayList<>();
                        for (Grade grade : resp) {
                            grades.add(grade);
                        }
                        gradeFuture.complete(grades);
                    }
                    @Override
                    public void onFailure(int code) {
                        gradeFuture.complete(new ArrayList<Grade>());
                    }
                });
            } else {
                gradeFuture.complete(new ArrayList<Grade>());
            }
        });
        return gradeFuture;
    }



    public boolean isAaAccess() {
        return isAaLogin;
    }

    public String getStuId() {
        return stuInfo.stuId;
    }
    public String getStuName() {
        return stuInfo.stuName;
    }
    public MutableLiveData<StudentInfo> getStudentInfo() {
        return stuInfoLiveData;
    }
    public void setStudentInfo(String Name, String Id, String Department, String Class) {
        stuInfo = new StudentInfo(Name, Id, Department, Class);
        stuInfoLiveData.postValue(stuInfo);
    }
}
