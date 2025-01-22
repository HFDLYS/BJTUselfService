package team.bjtuss.bjtuselfservice;

import static team.bjtuss.bjtuselfservice.utils.Utils.assetFilePath;

import android.content.Context;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

public class CaptchaModel {
    private static Module module;
    private static CaptchaModel instance;
    private char[] charset = {' ', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '-', '*', '='};
    private int n_class = charset.length;
    private Context context;

    private CaptchaModel(Context context) {
        this.context = context;
        try {
            module = Module.load(assetFilePath(context, "model.pt"));
            Log.i("Model", "Model loaded successfully");

        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }

    public static void init(Context context) {
        instance = new CaptchaModel(context);
    }

    public static CaptchaModel getInstance() {
        return instance;
    }

    private String decode(int[] preds) {

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < preds.length; i++) {
            if (i == 0 || (i > 0 && preds[i] != preds[i - 1])) {
                if (preds[i] != 0) {

                    result.append(charset[preds[i]]);
                }
            }
        }
        return result.toString();
    }

    public String predict(float[] data) {
        Tensor inputTensor = Tensor.fromBlob(data, new long[]{1, 3, 42, 130});
        IValue inputs = IValue.from(inputTensor);
        Tensor outputTensor = module.forward(inputs).toTensor();

        int batchSize = (int) outputTensor.shape()[0];
        int height = (int) outputTensor.shape()[2];

        float[] tensorData = outputTensor.getDataAsFloatArray();

        int[] argmaxIndices = new int[batchSize * height]; // 存储每个位置的argmax索引

        for (int i = 0; i < 8; i++) {
            float maxVal = Float.MIN_VALUE;
            int maxIdx = 0;
            for (int j = 0; j < 15; j++) {
                float val = tensorData[i * 15 + j];
                if (val > maxVal) {
                    maxVal = val;
                    maxIdx = j;
                }
            }
            if (maxIdx >= charset.length) {
                maxIdx = charset.length - 1;
            }
            argmaxIndices[i] = maxIdx;
        }

        return decode(argmaxIndices);
    }
}
