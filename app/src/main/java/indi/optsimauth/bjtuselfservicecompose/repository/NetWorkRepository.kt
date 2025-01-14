package indi.optsimauth.bjtuselfservicecompose.repository

import androidx.lifecycle.MutableLiveData


//object NetWorkRepository {
//    private val studentAccountManagerInstance = StudentAccountManager.getInstance()
//    val gradeList: MutableLiveData<List<StudentAccountManager.Grade>> = MutableLiveData();
//
//    init {
//        val isLogin =
//            studentAccountManagerInstance.init("23211315", "Yvt@9445").thenAccept { isLogin ->
//                if (isLogin) {
//                    println("登录成功")
//                    loadGradeList()
//                } else {
//                    println("Login failed")
//                }
//            }
//
//    }
//
//    fun loadGradeList() {
//
//        var grades: List<StudentAccountManager.Grade> = mutableListOf()
//        studentAccountManagerInstance.getGrade("ln").thenAccept { grade ->
//            grades.addAll(grade)
//            gradeList.postValue(grades)
//
//        }
//            .exceptionally(throwable -> {
//            if (throwable.toString().equals("Not loginAa")) {
//                studentAccountManager.loginAa().thenAccept(aBoolean -> {
//                    if (aBoolean) {
//                        loadGradeList();
//                    }
//                });
//            } else if (throwable.toString().equals("Not login")) {
//                studentAccountManager.loginAa().thenAccept(aBoolean -> {
//                    if (aBoolean) {
//                        loadGradeList();
//                    }
//                });
//            } else {
//                gradeList.postValue(null);
//            }
//            return null;
//        });
//        studentAccountManager.getGrade("lr").thenAccept(grade -> {
//            grades.addAll(grade);
//            gradeList.postValue(grades);
//        })
//        .exceptionally(throwable -> {
//            if (throwable.toString().equals("Not loginAa")) {
//                studentAccountManager.loginAa().thenAccept(aBoolean -> {
//                    if (aBoolean) {
//                        loadGradeList();
//                    }
//                });
//            } else if (throwable.toString().equals("Not login")) {
//                studentAccountManager.loginAa().thenAccept(aBoolean -> {
//                    if (aBoolean) {
//                        loadGradeList();
//                    }
//                });
//            } else {
//                gradeList.postValue(null);
//            }
//            return null;
//        });
//
//    }
//}

