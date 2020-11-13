package com.example.android.tflitecamerademo4;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;

public class ImageSegmentor {

  static String TAG = "deeplab";
  static int W = 128, H = 128;

  static Interpreter.Options tfliteOptions = new Interpreter.Options();
  static MappedByteBuffer tfliteModel;
  static Interpreter tflite;
  static int[] intValues = new int[W*H];
  static ByteBuffer imgData = null;
  static Bitmap result = null;
  static GpuDelegate gpuDelegate = null;
  static float[][][][] segmap = new float[1][W][H][2];
  static int frame_height = 640, frame_width = 512;

  static void init(Activity activity) throws IOException {
    tfliteModel = loadModelFile(activity);
    gpuDelegate =  new GpuDelegate();
    tfliteOptions.addDelegate(gpuDelegate);
    tfliteOptions.setNumThreads(4);
    tfliteOptions.setAllowFp16PrecisionForFp32(true);
    tflite = new Interpreter(tfliteModel, tfliteOptions);
    imgData = ByteBuffer.allocateDirect(W * H * 12);
    imgData.order(ByteOrder.nativeOrder());
    segmap = new float[1][W][H][2];
    result = Bitmap.createBitmap(frame_width, frame_height, Bitmap.Config.ARGB_8888);
  }

  ImageSegmentor(){
  }

  static void segmentFrame(Bitmap bitmap, Bitmap fgd) {
//      long t1 = SystemClock.uptimeMillis();
      convertBitmapToByteBuffer(bitmap);
//      long t2 = SystemClock.uptimeMillis();
//      Log.e(TAG, "t convert bitmap to input: "+(t2-t1));

      tflite.run(imgData, segmap);
//      long t3 = SystemClock.uptimeMillis();
//      Log.e(TAG, "t run model: "+(t3-t2));

      inputToBitmap(fgd);
//      long t4 = SystemClock.uptimeMillis();
//      Log.e(TAG, "t convet output to bit map" + (t4-t3));
  }

    static void convertBitmapToByteBuffer(Bitmap bitmap) {
        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < W; ++i) {
            for (int j = 0; j < H; ++j) {
                int val = intValues[pixel++];
                imgData.putFloat(((val >> 16) & 0xFF));
                imgData.putFloat(((val >> 8) & 0xFF));
                imgData.putFloat((val & 0xFF));
                }
        }
    }

  static boolean[][] toMap(){
      int sw = (frame_width+W-1)/W, sh = (frame_height+H-1)/H;
      boolean[][] map = new boolean[frame_height][frame_width];
      for(int i = 0; i < H; i++){
          for(int j = 0; j < W; j++){
              boolean val = true;
              if(segmap[0][i][j][1]<0.5){
                  val = false;
              }
              for(int dx = 0; dx < sh; dx++){
                  for(int dy = 0; dy < sw; dy++){
                      if(i*sh+dx<frame_height && j*sw+dy<frame_width)
                          map[i*sh+dx][j*sw+dy] = val;
                  }
              }
          }
      }
      return map;
  }


  static void inputToBitmap(Bitmap bitmap){
      // resize output of model to original size
      long t1 = SystemClock.uptimeMillis();
      boolean[][] map = toMap();
      long t2 = SystemClock.uptimeMillis();
      // convert bitmap to float array
      int[] val = new int[frame_width*frame_height];
      bitmap.getPixels(val, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
      int sz = 0, k = 3;
      int[][][] pxtmp = new int[frame_height+2*k][frame_width+2*k][4];
      int[][][] px = new int[frame_height][frame_width][4];
      int[] x = new int[4];
      int[] acc = new int[4];
      for(int i = 0; i < frame_height; i++){
          for(int o = 0; o < 3; o++){
              acc[o] = 0;
          }
          for(int j = 0; j < frame_width; j++){
              int tmp  = val[sz++];
              for(int o = 0; o < 4; o++){
                  x[o] = (tmp>>(8*(3-o)))& 0xFF;
                  acc[o] += x[o];
              }
              pxtmp[i+k][j+k][0] = x[0];
              for(int o = 1; o <= 3; o++){
                  pxtmp[i+k][j+k][o] = acc[o];
                  if(i>0){
                      pxtmp[i+k][j+k][o] += pxtmp[i-1+k][j+k][o];
                  }
              }

          }
      }
      long t3 = SystemClock.uptimeMillis();
      sz = 0;
//      ArrayList<Integer> ai = new ArrayList<>();
      for(int i = 0; i < frame_height; i++){
          for(int j = 0; j < frame_width; j++){
              if(!map[i][j]){
//                  int tmp = 0;
                  for(int o = 1; o <= 3; o++){
                      px[i][j][o] = (pxtmp[i+2*k][j+2*k][o]-pxtmp[i][j+2*k][o]-pxtmp[i+2*k][j][o]+pxtmp[i][j][o])/((2*k+1)*(2*k+1));
                  }
//                  ai.clear();
//                  for(int o = Math.max(0, sz-5); o <= Math.min(frame_height*frame_width-1, sz+5); o++){
//                      ai.add(val[o]);
//                  }
//                  Collections.sort(ai);
//                  val[sz] = ai.get(ai.size()/2);
                  val[sz] = Color.rgb(px[i][j][1], px[i][j][2], px[i][j][3]);
              }
              sz++;
          }
      }
      long t4 = SystemClock.uptimeMillis();
      Log.e("time: ", (t2-t1)+" "+(t3-t2)+" "+(t4-t3));
      result.setPixels(val, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
  }


  static MappedByteBuffer loadModelFile(Activity activity) throws IOException {
    AssetFileDescriptor fileDescriptor = activity.getAssets().openFd("deeplab_v2_128.tflite");
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  static void close() {
    tflite.close();
    tflite = null;
    gpuDelegate.close();
    gpuDelegate = null;
    tfliteModel = null;
  }

}
