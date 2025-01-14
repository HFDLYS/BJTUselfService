package team.bjtuss.bjtuselfservice.viewmodel;

//public class GradeViewModel extends ViewModel {
//    final private MutableLiveData<List<StudentAccountManager.Grade>> gradeList = new MutableLiveData<>();
//    StudentAccountManager studentAccountManager = StudentAccountManager.getInstance();
//
//    public GradeViewModel() {
//        loadGradeList();
//    }
//
//    public LiveData<List<StudentAccountManager.Grade>> getGradeList() {
//        return gradeList;
//    }
//
//    public void loadGradeList() {
//        List<StudentAccountManager.Grade> grades = new ArrayList<>();
//        studentAccountManager.getGrade("ln").thenAccept(grade -> {
//                    grades.addAll(grade);
//                    gradeList.postValue(grades);
//                })
//                .exceptionally(throwable -> {
//                    if (throwable.toString().equals("Not loginAa")) {
//                        studentAccountManager.loginAa().thenAccept(aBoolean -> {
//                            if (aBoolean) {
//                                loadGradeList();
//                            }
//                        });
//                    } else if (throwable.toString().equals("Not login")) {
//                        studentAccountManager.loginAa().thenAccept(aBoolean -> {
//                            if (aBoolean) {
//                                loadGradeList();
//                            }
//                        });
//                    } else {
//                        gradeList.postValue(null);
//                    }
//                    return null;
//                });
//        studentAccountManager.getGrade("lr").thenAccept(grade -> {
//                    grades.addAll(grade);
//                    gradeList.postValue(grades);
//                })
//                .exceptionally(throwable -> {
//                    if (throwable.toString().equals("Not loginAa")) {
//                        studentAccountManager.loginAa().thenAccept(aBoolean -> {
//                            if (aBoolean) {
//                                loadGradeList();
//                            }
//                        });
//                    } else if (throwable.toString().equals("Not login")) {
//                        studentAccountManager.loginAa().thenAccept(aBoolean -> {
//                            if (aBoolean) {
//                                loadGradeList();
//                            }
//                        });
//                    } else {
//                        gradeList.postValue(null);
//                    }
//                    return null;
//                });
//
//    }
//
//    public MutableLiveData<Boolean> getIsAaLogin() {
//        return studentAccountManager.getIsAaLogin();
//    }
//}