package com.hfdlys.bjtuselfservice;



import androidx.lifecycle.MutableLiveData;

import com.hfdlys.bjtuselfservice.utils.Network;
import com.hfdlys.bjtuselfservice.utils.Network.WebCallback;
import com.hfdlys.bjtuselfservice.web.MisDataManager;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class StudentAccountManager {
    private static class Holder {
        private static final StudentAccountManager INSTANCE = new StudentAccountManager();
    }
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
        public String courseCredits;
        public String courseYear;
        public Grade(String courseName, String courseTeacher, String courseScore, String courseCredits, String courseYear) {
            this.courseName = courseName;
            this.courseTeacher = courseTeacher;
            this.courseScore = courseScore;
            this.courseCredits = courseCredits;
            this.courseYear = courseYear;
        }
    }
    public static class Status {
        public String NewMailCount;
        public double EcardBalance;
        public String NetBalance;
        public Status(String NewMailCount, double EcardBalance, String NetBalance) {
            this.NewMailCount = NewMailCount;
            this.EcardBalance = EcardBalance;
            this.NetBalance = NetBalance;
        }
    }
    public static class ExamSchedule {
        public String ExamType;
        public String CourseName;
        public String ExamTimeAndPlace;
        public String ExamStatus;
        public String Detail;
        public ExamSchedule(String ExamType, String CourseName, String ExamTimeAndPlace, String ExamStatus, String Detail) {
            this.ExamType = ExamType;
            this.CourseName = CourseName;
            this.ExamTimeAndPlace = ExamTimeAndPlace;
            this.ExamStatus = ExamStatus;
            this.Detail = Detail;
        }
    }

    private StudentInfo stuInfo;
    private String stuId = null;
    private String password = null;
    private boolean isMisLogin = false;
    private boolean isAaLogin = false;
    private MutableLiveData<StudentInfo> stuInfoLiveData = new MutableLiveData<>();
    private MutableLiveData<Boolean> isAaLoginLiveData = new MutableLiveData<>(false);
    private MutableLiveData<Boolean> isMisLoginLiveData = new MutableLiveData<>(false);

    // 永续cookie的客户端
    final private OkHttpClient client = new OkHttpClient.Builder()
            .cookieJar(new Network.InMemoryCookieJar())
            .build();
    private StudentAccountManager() {
    }
    // 检测登录状态
    public CompletableFuture<Boolean> checkIsLogin() {
        CompletableFuture<Boolean> loginFuture = new CompletableFuture<>();
        WebCallback<String> loginCallback = new WebCallback<String>() {
            @Override
            public void onResponse(String code) {
                loginFuture.complete(true);
            }
            @Override
            public void onFailure(int code) {
                loginFuture.complete(false);
            }
        };

        MisDataManager.checkCookie(client, loginCallback);

        return loginFuture;
    }
    // 单例模式
    public static StudentAccountManager getInstance() {
        return Holder.INSTANCE;
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
                MisDataManager.login(client, stuId, password, new WebCallback<String>() {
                    @Override
                    public void onResponse(String code) {
                        setStudentInfo(code.split(";")[0], stuId, code.split(";")[2], code.split(";")[1]);
                        setMisLogin(true);
                        loginFuture.complete(true);
                    }
                    public void onFailure(int code) {
                        MisDataManager.login(client, stuId, password, new WebCallback<String>() {
                            @Override
                            public void onResponse(String code) {
                                setStudentInfo(code.split(";")[0], stuId, code.split(";")[2], code.split(";")[1]);
                                setMisLogin(true);
                                loginFuture.complete(true);
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
    // 登录教务系统
    public CompletableFuture<Boolean> loginAa() {
        return checkIsLogin().thenCompose(isLogin -> {
            if (isLogin) {
                return attemptAaLogin();
            } else {
                return attemptInitAndLogin();
            }
        });
    }

    private CompletableFuture<Boolean> attemptAaLogin() {
        CompletableFuture<Boolean> loginFuture = new CompletableFuture<>();
        MisDataManager.aaLogin(client, new WebCallback<String>() {
            @Override
            public void onResponse(String code) {
                setAaLogin(true);
                loginFuture.complete(true);
            }
            @Override
            public void onFailure(int code) {
                loginFuture.complete(false);
            }
        });
        return loginFuture;
    }

    private CompletableFuture<Boolean> attemptInitAndLogin() {
        if (stuId == null || password == null) {
            return CompletableFuture.completedFuture(false);
        }
        return init(stuId, password).thenCompose(isLoginMis -> {
            if (isLoginMis) {
                return attemptAaLogin();
            } else {
                return CompletableFuture.completedFuture(false);
            }
        });
    }
    // 获得成绩
    public CompletableFuture<List<Grade>> getGrade() {
        CompletableFuture<List<Grade>> gradeFuture = new CompletableFuture<>();
        if (isAaLogin) {
            MisDataManager.getGrade(client, new WebCallback<List<Grade>>() {
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
                    gradeFuture.completeExceptionally(new Exception("No connection"));
                }
            });
        } else {
            gradeFuture.completeExceptionally(new Exception("Not loginAa"));
        }
        return gradeFuture;
    }

    public CompletableFuture<List<ExamSchedule>> getExamSchedule() {
        CompletableFuture<List<ExamSchedule>> Future = new CompletableFuture<>();
        if (isAaLogin) {
            MisDataManager.getExamSchedule(client, new WebCallback<List<ExamSchedule>>() {
                @Override
                public void onResponse(List<ExamSchedule> obj) {
                    Future.complete(obj);
                }
                @Override
                public void onFailure(int errcode) {
                    Future.completeExceptionally(new Exception("No connection"));
                }
            });
        } else {
            Future.completeExceptionally(new Exception("Not loginAa"));
        }
        return Future;
    }

    // 获得基础状态
    public CompletableFuture<Status> getStatus() {
        CompletableFuture<Status> statusFuture = new CompletableFuture<>();
        checkIsLogin().thenAccept(isLogin -> {
            if (isLogin) {
                MisDataManager.getStatus(client, new WebCallback<Status>() {
                    @Override
                    public void onResponse(Status resp) {
                        statusFuture.complete(resp);
                    }
                    @Override
                    public void onFailure(int code) {
                        statusFuture.complete(new Status("0", 0D, "0"));
                    }
                });
            } else {
                statusFuture.complete(new Status("0", 0D, "0"));
            }
        });
        return statusFuture;
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
    public MutableLiveData<Boolean> getIsAaLogin() {
        return isAaLoginLiveData;
    }
    public MutableLiveData<Boolean> getIsMisLogin() {
        return isMisLoginLiveData;
    }
    public OkHttpClient getClient() {
        return client;
    }
    public void setStudentInfo(String Name, String Id, String Department, String Class) {
        stuInfo = new StudentInfo(Name, Id, Department, Class);
        stuInfoLiveData.postValue(stuInfo);
    }
    public void setAaLogin(boolean isAa) {
        isAaLogin = isAa;
        isAaLoginLiveData.postValue(isAa);
    }
    public void setMisLogin(boolean isMis) {
        isMisLogin = isMis;
        isMisLoginLiveData.postValue(isMis);
    }
}
