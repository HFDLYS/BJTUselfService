package com.hfdlys.bjtuselfservice.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;



public class Utils {
    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName);
             OutputStream os = new FileOutputStream(file)) {
            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
        }

        return file.getAbsolutePath();
    }

    public static String calculate(String expression) {
        // 使用正则表达式验证输入格式：数字、操作符、数字、等号
        if (!expression.matches("\\d+[+\\-*]\\d+=")) {
            return null;
        }

        try {
            expression = expression.substring(0, expression.length() - 1);

            String[] parts = expression.split("(?<=\\d)(?=[+\\-*])|(?<=[+\\-*])(?=\\d)");
            long num1 = Long.parseLong(parts[0]);
            long num2 = Long.parseLong(parts[2]);
            char operator = parts[1].charAt(0);

            // 根据操作符执行计算
            long result;
            switch (operator) {
                case '+':
                    result = num1 + num2;
                    break;
                case '-':
                    result = num1 - num2;
                    break;
                case '*':
                    result = num1 * num2;
                    break;
                default:
                    return null;
            }

            return String.valueOf(result);
        } catch (Exception e) {
            return null;
        }
    }

    public class StudentInfo {
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

    public class InMemoryCookieJar implements CookieJar {
        private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            cookieStore.put(url.host(), cookies);
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> cookies = cookieStore.get(url.host());
            return cookies != null ? cookies : new ArrayList<Cookie>();
        }
    }
}
