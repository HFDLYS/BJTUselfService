package indi.optsimauth.bjtuselfservicecompose.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class ImageToTensorConverter {
    public static float[] convertToTensor(byte[] imageData, int width, int height) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
        bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        float[] data = new float[3 * width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 获取当前像素的颜色值
                int pixel = bitmap.getPixel(x, y);
                // 获取颜色的组成部分
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                // 将RGB值转换为浮点数并存储在数组中
                // 注意这里可能需要根据模型的输入要求进行调整，例如标准化到[0,1]或[-1,1]
                data[y * width + x] = r / 255.0f;
                data[width * height + y * width + x] = g / 255.0f;
                data[2 * width * height + y * width + x] = b / 255.0f;
            }
        }
        return data;
    }
}
