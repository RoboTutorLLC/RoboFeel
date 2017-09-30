package mayank.example.rtcamera;

/**
 * Created by mayank on 5/9/17.
 */

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;


public class EmotionInference {

    private static final String TAG = EmotionInference.class.getName();

    //private static final String MODEL_FILE = "file:///android_asset/frozen_rt.pb";
    //private static final String MODEL_FILE = "file:///android_asset/common_emotions.pb";

    //private static final String MODEL_FILE = "file:///android_asset/normalized_common_emotions.pb";

    private static final String MODEL_FILE = "file:///android_asset/bcdf_emotions.pb";

    //private static final String INPUT_NODE = "input"; // for frozen_rt
    //private static final String OUTPUT_NODE = "output"; // for frozen_rt
    //private static final String INPUT_NODE = "dense_1_input"; // for common_emotions & normalized
    //private static final String OUTPUT_NODE = "output_node0";  // for common_emotions & normalized

    private static final String INPUT_NODE = "dense_4_input"; // for common_emotions
    private static final String OUTPUT_NODE = "output_node0";  // for common_emotions


    private static final String[] outputNodes = {OUTPUT_NODE};
    //private static final String[] outputNodes = {"output"};
    private static final long[] INPUT_SIZE = {1, 136};

    //private static final int OUTPUT_S IZE = 6; // for frozen_rt

    //private static final int OUTPUT_SIZE = 5; // for common_emotions and normalized

    private static final int OUTPUT_SIZE = 4;

    private static EmotionInference emotionInferenceInstance;
    private static AssetManager assetManager;

    static {
        System.loadLibrary("tensorflow_inference");
    }

    private TensorFlowInferenceInterface inferenceInterface;

    public EmotionInference(final Context context) {
        this.assetManager = context.getAssets();
        inferenceInterface = new TensorFlowInferenceInterface(context.getAssets(), MODEL_FILE);
    }

    public static EmotionInference getInstance(final Context context) {
        if (emotionInferenceInstance == null) {
            emotionInferenceInstance = new EmotionInference(context);
        }
        return emotionInferenceInstance;
    }

    public float[] getEmotionProb(float[] input) {

        /*Log.d(TAG, "Inside get emotion probability function!") ;
        for (float f : input) {
            Log.d("TAG", "f = " + f);
        }*/

        //float[] result = new float[OUTPUT_SIZE];

        //float[] result = {0, 0, 0, 0, 0}; //for common emotions and normalized

        float[] result = {0, 0, 0, 0};

        float[] res = {0};

        try {
            inferenceInterface.feed(INPUT_NODE, input, INPUT_SIZE); // INPUT_SIZE is an int[] of expected shape, input is a float[] with the input data
            inferenceInterface.run(outputNodes);
            inferenceInterface.fetch(OUTPUT_NODE, result); // output is a pre allocated float[] in the size of the expected output vector
            inferenceInterface.fetch(OUTPUT_NODE, res); // output is a pre allocated float[] in the size of the expected output vector
        } catch (Exception e) {
            Log.d(TAG, "Inside exception");
            Log.d(TAG, "Exception e = " + e.toString());
        }


        return result;

    }
}