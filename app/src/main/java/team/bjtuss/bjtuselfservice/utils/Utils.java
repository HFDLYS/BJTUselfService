package team.bjtuss.bjtuselfservice.utils;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;


public class Utils {
    private static final Map<String, Integer> gradeToScoreMap = new HashMap<>();

    static {
        gradeToScoreMap.put("A", 95);
        gradeToScoreMap.put("A-", 87);
        gradeToScoreMap.put("B+", 83);
        gradeToScoreMap.put("B", 79);
        gradeToScoreMap.put("B-", 76);
        gradeToScoreMap.put("C+", 73);
        gradeToScoreMap.put("C", 69);
        gradeToScoreMap.put("C-", 66);
        gradeToScoreMap.put("D+", 63);
        gradeToScoreMap.put("D", 60);
        gradeToScoreMap.put("F", 30);
    }

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

    private static String scoreToGrade(int score) {
        if (score >= 90) return "A";
        else if (score >= 85) return "A-";
        else if (score >= 81) return "B+";
        else if (score >= 78) return "B";
        else if (score >= 75) return "B-";
        else if (score >= 71) return "C+";
        else if (score >= 68) return "C";
        else if (score >= 65) return "C-";
        else if (score >= 61) return "D+";
        else if (score == 60) return "D";
        else return "F";
    }

    public static String convertAndFormatGradeScore(String input) {
        try {
            int score = Integer.parseInt(input);
            String grade = scoreToGrade(score);
            return grade + "," + score;
        } catch (NumberFormatException e) {
            if (gradeToScoreMap.containsKey(input)) {
                int score = gradeToScoreMap.get(input);
                return input + "," + score;
            } else {
                return "-,-";
            }
        }
    }

    public static int calculateGradeColor(double grade) {
        int red = 0;
        int green = 0;

        if (grade >= 60) {
            double proportion = (double) (grade - 60) / 40;
            green = (int) (255 * proportion);
            red = 255 - green;
        } else {
            green = 255;
        }
        return 0xFF000000 | (red << 16) | (green << 8);
    }

    public static String generateRandomColor(String courseId, boolean isDarkMode) {
        long seed = courseId.hashCode();
        Random random = new Random(seed);

        int r, g, b;
        int base1 = 20;
        int base2 = 180;
        if (isDarkMode) {
            // 为暗色模式生成深色
            r = base1 + random.nextInt(66);
            g = base1 + random.nextInt(66);
            b = base1 + random.nextInt(66);
        } else {
            // 为亮色模式生成浅色
            r = base2 + random.nextInt(66);
            g = base2 + random.nextInt(66);
            b = base2 + random.nextInt(66);
        }

        return String.format("#%02X%02X%02X", r, g, b);
    }
}
