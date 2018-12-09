package com.example.keita.picture4;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private final static int REQUEST_PERMISSIONS = 0;
    private final static String[] PERMISSIONS = {Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE};


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        checkPermissions();

    }

    private void checkPermissions(){
        if (isGranted()){
            initContentView();

        }
        else{
            ActivityCompat.requestPermissions(this,PERMISSIONS,REQUEST_PERMISSIONS);
        }
    }

    private boolean isGranted(){
        for (int i = 0; i < PERMISSIONS.length; i++){
            if (PermissionChecker.checkSelfPermission(MainActivity.this,PERMISSIONS[i]) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,String permissions[],int[] results){
        if (requestCode == REQUEST_PERMISSIONS){
            if (isGranted()){
                initContentView();
            }
        }
        else {
            super.onRequestPermissionsResult(requestCode,permissions,results);
        }
    }

    private void initContentView(){
        setContentView(new CameraView(this));

    }



}
