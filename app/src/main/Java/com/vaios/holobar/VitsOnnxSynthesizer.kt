package com.vaios.holobar;

import ai.onnxruntime.*;
import android.content.Context;
import android.util.Log;

import java.io.*;
import java.nio.*;
import java.util.*;

public class VitsOnnxSynthesizer implements AutoCloseable {
    private static final String TAG = "VitsOnnxSynthesizer";
    private final Tokenizer tokenizer;
    private final OrtEnvironment env;
    private final OrtSession session;

    public VitsOnnxSynthesizer(Context context, String modelFileName) throws OrtException, IOException {
        this.tokenizer = new Tokenizer(context);
        this.env = OrtEnvironment.getEnvironment();
        byte[] modelBytes = readBytesFromAsset(context, modelFileName);
        this.session = env.createSession(modelBytes, new OrtSession.SessionOptions());
    }

    private byte[] readBytesFromAsset(Context context, String fileName) throws IOException {
        try (InputStream inputStream = context.getAssets().open(fileName)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        }
    }

    public float[] tts(String text, long speakerId) throws OrtException {
        List<Integer> inputs = tokenizer.textToIds(text);
        long[] inputArray = new long[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            inputArray[i] = inputs.get(i).longValue();
        }
        long[] inputLengths = new long[]{inputArray.length};
        float[] scales = new float[]{0.667f, 1.0f, 0.8f};
        long[] sid = new long[]{speakerId};

        OnnxTensor inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputArray), new long[]{1, inputArray.length});
        OnnxTensor inputLengthsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputLengths), new long[]{1});
        OnnxTensor scalesTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(scales), new long[]{3});
        OnnxTensor sidTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(sid), new long[]{1});

        Map<String, OnnxTensor> ortInputs = new HashMap<>();
        ortInputs.put("input", inputTensor);
        ortInputs.put("input_lengths", inputLengthsTensor);
        ortInputs.put("scales", scalesTensor);
        ortInputs.put("sid", sidTensor);

        try (OrtSession.Result result = session.run(ortInputs)) {
            float[][][] audio = (float[][][]) result.get(0).getValue();
            return audio[0][0];
        }
    }

    @Override
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
            if (env != null) {
                env.close();
            }
        } catch (OrtException e) {
            Log.e(TAG, "Error closing ONNX session or environment", e);
        }
    }
}