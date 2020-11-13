package com.example.android.tflitecamerademo4;

import android.app.Activity;
import android.os.Bundle;

import java.io.IOException;

/** Main {@code Activity} class for the Camera app. */
public class CameraActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_camera);

      if (null == savedInstanceState) {
         getFragmentManager()
            .beginTransaction()
            .replace(R.id.container, Camera2BasicFragment.newInstance())
            .commit();
      }

      try {
          ImageSegmentor.init(this);
      } catch (IOException e) {
          e.printStackTrace();
      }

  }
}
