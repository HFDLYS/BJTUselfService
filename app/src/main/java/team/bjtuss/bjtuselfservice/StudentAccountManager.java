package team.bjtuss.bjtuselfservice;


import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import team.bjtuss.bjtuselfservice.utils.Network;
import team.bjtuss.bjtuselfservice.utils.Network.WebCallback;
import team.bjtuss.bjtuselfservice.web.MisDataManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import okhttp3.OkHttpClient;

public class StudentAccountManager {
    private final MutableLiveData<StudentInfo> stuInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isAaLoginLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isMisLoginLiveData = new MutableLiveData<>(false);
    // 永续cookie的客户端
    private OkHttpClient client = new OkHttpClient.Builder()
            .cookieJar(new Network.InMemoryCookieJar())
            .build();
    private StudentInfo stuInfo;
    private String stuId = null;
    private String password = null;
    private boolean isMisLogin = false;
    private boolean isAaLogin = false;

    private boolean isXsmisLogin = false;
    private StudentAccountManager() {
    }

    // 单例模式
    public static StudentAccountManager getInstance() {
        return Holder.INSTANCE;
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

    // 初始化登录 或 重载登录
    public CompletableFuture<Boolean> init(String stuId, String password) {
        this.stuId = stuId;
        this.password = password;
        return checkIsLogin().thenCompose(isLogin -> {
            if (isLogin) {
                return CompletableFuture.completedFuture(true);
            } else {
                return attemptLoginWithRetry(stuId, password, 2);
            }
        });
    }

    private CompletableFuture<Boolean> attemptLoginWithRetry(String stuId, String password, int retries) {
        CompletableFuture<Boolean> loginFuture = new CompletableFuture<>();
        MisDataManager.login(client, stuId, password, new WebCallback<String>() {
            @Override
            public void onResponse(String code) {
                setStudentInfoFromCode(code, stuId);
                loginFuture.complete(true);
            }

            @Override
            public void onFailure(int code) {
                if (retries > 0 && (code == 0)) {
                    System.out.println("Login failed, retrying...");
                    attemptLoginWithRetry(stuId, password, retries - 1).whenComplete((result, ex) -> {
                        if (ex != null) {
                            loginFuture.completeExceptionally(ex);
                        } else {
                            loginFuture.complete(result);
                        }
                    });
                } else {
                    System.out.println("Login failed after retries.");
                    loginFuture.complete(false);
                }
            }
        });
        return loginFuture;
    }

    public void clearCookie() {
        client = new OkHttpClient.Builder()
                .cookieJar(new Network.InMemoryCookieJar()) // 使用新的空的cookieJar
                .build();
    }

    private void setStudentInfoFromCode(String code, String stuId) {
        String[] parts = code.split(";");
        setStudentInfo(parts[0], stuId, parts[2], parts[1]);
        setMisLogin(true);
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

    public CompletableFuture<Boolean> loginXsMis() {
        return checkIsLogin().thenCompose(isLogin -> {
            if (isLogin) {
                return attemptXsMisLogin();
            } else {
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    private CompletableFuture<Boolean> attemptXsMisLogin() {
        CompletableFuture<Boolean> loginFuture = new CompletableFuture<>();
        MisDataManager.xsmislogin(client, new WebCallback<String>() {
            @Override
            public void onResponse(String code) {
                setXsmisLogin(true);
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
                return CompletableFuture.completedFuture(true);
            } else {
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    // 获得成绩
    public CompletableFuture<List<Grade>> getGrade(String ctype) {
        CompletableFuture<List<Grade>> gradeFuture = new CompletableFuture<>();
        if (isAaLogin) {
            MisDataManager.getGrade(client, new WebCallback<List<Grade>>() {
                @Override
                public void onResponse(List<Grade> resp) {
                    List<Grade> grades = new ArrayList<>(resp);
                    gradeFuture.complete(grades);
                }

                @Override
                public void onFailure(int code) {
                    if (code == 0) {
                        gradeFuture.completeExceptionally(new Exception("No connection"));
                    } else if (code == 1) {
                        gradeFuture.completeExceptionally(new Exception("Rate limit exceeded"));
                    }
                }
            }, ctype);
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
                public void onFailure(int code) {
                    if (code == 0) {
                        Future.completeExceptionally(new Exception("No connection"));
                    } else if (code == 1) {
                        Future.completeExceptionally(new Exception("Rate limit exceeded"));
                    }
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
            if (isXsmisLogin) {
                MisDataManager.getStatus(client, new WebCallback<Status>() {
                    @Override
                    public void onResponse(Status resp) {
                        statusFuture.complete(resp);
                    }

                    @Override
                    public void onFailure(int code) {
                        statusFuture.complete(new Status("0", "0", "0"));
                    }
                });
            } else {
                statusFuture.complete(new Status("0", "0", "0"));
            }
        });
        return statusFuture;
    }

    public CompletableFuture<List<List<Course>>> getCourseList(boolean isCurrentTerm) {
        CompletableFuture<List<List<Course>>> Future = new CompletableFuture<>();
        checkIsLogin().thenAccept(isLogin -> {
            if (isLogin) {
                MisDataManager.getCourse(client, isCurrentTerm, new WebCallback<List<List<Course>>>() {
                    @Override
                    public void onResponse(List<List<Course>> resp) {
                        Future.complete(resp);
                    }

                    @Override
                    public void onFailure(int code) {
                        if (code == 0) {
                            Future.completeExceptionally(new Exception("No connection"));
                        } else if (code == 1) {
                            Future.completeExceptionally(new Exception("Rate limit exceeded"));
                        }
                    }
                });
            } else {
                Future.completeExceptionally(new Exception("Not loginAa"));
            }
        });
        return Future;
    }

    public CompletableFuture<Map<String, List<Integer>>> getClassroom() {
        CompletableFuture<Map<String, List<Integer>>> Future = new CompletableFuture<>();
        checkIsLogin().thenAccept(isLogin -> {
            if (isLogin) {
                MisDataManager.getClassroom(client, new WebCallback<Map<String, List<Integer>>>() {
                    @Override
                    public void onResponse(Map<String, List<Integer>> resp) {
                        Future.complete(resp);
                    }

                    @Override
                    public void onFailure(int code) {
                        if (code == 0) {
                            Future.completeExceptionally(new Exception("No connection"));
                        } else if (code == 1) {
                            Future.completeExceptionally(new Exception("Rate limit exceeded"));
                        }
                    }
                });
            } else {
                Future.completeExceptionally(new Exception("Not loginAa"));
            }
        });
        return Future;
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

    public void setXsmisLogin(boolean isMis) {
        isXsmisLogin = isMis;
    }

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
        public String tag;
        public String detail;

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
        public String EcardBalance;
        public String NetBalance;

        public Status(String NewMailCount, String EcardBalance, String NetBalance) {
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

    public static class Course {
        public String CourseId;
        public String CourseName;
        public String CourseTeacher;
        public String CourseTime;
        public String CoursePlace;

        public Course(String CourseId, String CourseName, String CourseTeacher, String CourseTime, String CoursePlace) {
            this.CourseId = CourseId;
            this.CourseName = CourseName;
            this.CourseTeacher = CourseTeacher;
            this.CourseTime = CourseTime;
            this.CoursePlace = CoursePlace;
        }
    }
}


//class LoginFormState {
//    @Nullable
//    private Integer usernameError;
//    @Nullable
//    private Integer passwordError;
//    private boolean isDataValid;
//
//    LoginFormState(@Nullable Integer usernameError, @Nullable Integer passwordError) {
//        this.usernameError = usernameError;
//        this.passwordError = passwordError;
//        this.isDataValid = false;
//    }
//
//    LoginFormState(boolean isDataValid) {
//        this.usernameError = null;
//        this.passwordError = null;
//        this.isDataValid = isDataValid;
//    }
//
//    @Nullable
//    Integer getUsernameError() {
//        return usernameError;
//    }
//
//    @Nullable
//    Integer getPasswordError() {
//        return passwordError;
//    }
//
//    boolean isDataValid() {
//        return isDataValid;
//    }
//}

class LoginResult {
    @Nullable
    private LoggedInUserView success;
    @Nullable
    private Integer error;

    LoginResult(@Nullable Integer error) {
        this.error = error;
    }

    LoginResult(@Nullable LoggedInUserView success) {
        this.success = success;
    }

    @Nullable
    LoggedInUserView getSuccess() {
        return success;
    }

    @Nullable
    Integer getError() {
        return error;
    }
}

class LoggedInUserView {
    private String displayName;
    //... other data fields that may be accessible to the UI

    LoggedInUserView(String displayName) {
        this.displayName = displayName;
    }

    String getDisplayName() {
        return displayName;
    }
}


