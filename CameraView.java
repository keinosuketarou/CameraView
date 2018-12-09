package com.example.keita.picture4;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class CameraView extends TextureView {
    public static boolean accessGranted = true;
    private Activity activity;
    private Handler uiHandler;
    private Handler workHandler;
    private boolean active;
    private CameraManager manager;
    String cameraId;
    private CameraCharacteristics cameraInfo;
    Size previewSize;
    Size pictureSize;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder previewBuilder;
    private CameraCaptureSession previewSession;

    public CameraView(Context context)  {
        super(context);
        activity = (Activity)context;
        active = false;
        uiHandler = new Handler();
        HandlerThread thread = new HandlerThread("work");
        thread.start();
        workHandler = new Handler(thread.getLooper());
        manager = (CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
        setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){

            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height){
                startCamera();
            }
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface,int width,int height){
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface){
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface){
                stopCamera();
                return true;
            }
        });
    }
    private void startCamera(){
        try{

            cameraId = getCameraId();
            cameraInfo = manager.getCameraCharacteristics(cameraId);
            previewSize = getPreviewSize(cameraInfo);
            pictureSize = getPictureSize(cameraInfo);
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {

                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    startPreview();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                    toast("カメラのオープンに失敗しました");
                }
            },null);
        } catch (SecurityException e){
            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
            toast(e.toString());
        }

    }
    private void stopCamera(){
        if (cameraDevice != null){
            cameraDevice.close();
            cameraDevice = null;
        }

    }
    private String getCameraId(){
        try {

            for (String cameraId : manager.getCameraIdList()){
                CameraCharacteristics cameraInfo = manager.getCameraCharacteristics(cameraId);
                if (cameraInfo.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK){
                    return cameraId;
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
    private Size getPreviewSize(CameraCharacteristics characteristics){
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
        for (int i=0; i<sizes.length; i++){
            if (sizes[i].getWidth()<2000 && sizes[i].getHeight()<2000){
                return sizes[i];
            }
        }
        return sizes[0];
    }
    private Size getPictureSize(CameraCharacteristics characteristics){
        Size[] sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
        for (int i=0; i<sizes.length; i++){
            if (sizes[i].getWidth()<2000 && sizes[i].getHeight()<2000){
                return sizes[i];
            }
        }
        return null;
    }


    private void startPreview(){
        if (cameraDevice == null) return;
        active = true;
        SurfaceTexture texture = getSurfaceTexture();
        if (texture == null) return;
        texture.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());
        Surface surface = new Surface(texture);

        try {
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    previewSession = session;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    toast("プレビューセッションの生成に失敗しました");
                }
            },null);

        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }
    protected void updatePreview(){
        if (cameraDevice == null) return;
        previewBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        try {
            previewSession.setRepeatingRequest(previewBuilder.build(),null,workHandler);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        if (event.getAction() == MotionEvent.ACTION_DOWN){
            if (!active) return true;
            active = false;
            takePicture();

        }
        return true;
    }

    private void takePicture(){
        if (cameraDevice == null) return;
        try {
            ImageReader reader = ImageReader.newInstance(previewSize.getWidth(),previewSize.getHeight(),ImageFormat.JPEG,2);
            reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                QRCodeReader mQrReader;
                @Override
                public void onImageAvailable(ImageReader reader){
                    Image img = null;
                    Result result = null;
                    try {
                        img = reader.acquireLatestImage();
                        byte[] data = image2data(img);
                        savePhoto(data);

                        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data,previewSize.getWidth(),previewSize.getHeight(),0,0,previewSize.getWidth(),previewSize.getHeight(),false);
                        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                        try {
                            result = mQrReader.decode(bitmap);
                            String text = result.getText();
                            toast(text);
                        }catch (Exception e){
                            toast("not found");
                        }


                    } catch (Exception e){
                        if (img != null) img.close();
                    }


                }
            },workHandler);

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,getPhotoOrientation());
            List<Surface> outputSurfaces = new LinkedList<>();
            outputSurfaces.add(reader.getSurface());

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {

                    try {
                        session.capture(captureBuilder.build(),new CameraCaptureSession.CaptureCallback(){
                            @Override
                            public void onCaptureCompleted(CameraCaptureSession session,CaptureRequest request,TotalCaptureResult result){
                                super.onCaptureCompleted(session,request,result);
                                startPreview();


                            }
                        },workHandler);
                    } catch (CameraAccessException e){
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    toast("キャプチャーセッションの生成に失敗しました");
                    startPreview();
                }
            },workHandler);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }
    private byte[] image2data(Image image){
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        byte[] data = new byte[buffer.capacity()];
        buffer.get(data);
        return data;
    }

    private int getPhotoOrientation(){
        int displayRotation = 0;

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        if (rotation == Surface.ROTATION_0) displayRotation = 0;
        if (rotation == Surface.ROTATION_90) displayRotation = 90;
        if (rotation == Surface.ROTATION_180) displayRotation = 180;
        if (rotation == Surface.ROTATION_270) displayRotation = 270;
        int sensorOrientation = cameraInfo.get(CameraCharacteristics.SENSOR_ORIENTATION);
        return (sensorOrientation-displayRotation+360)%360;
    }
    private void savePhoto(byte[] data){
        try {
            SimpleDateFormat format = new SimpleDateFormat("'IMG'_yyyyMMdd_HHmmss'.jpg'", Locale.getDefault());
            String fileName = format.format(new Date(System.currentTimeMillis()));
            String path = Environment.getExternalStorageDirectory()+"/"+fileName;
            saveData(data,path);
            MediaScannerConnection.scanFile(getContext(),new String[]{path}, new String[]{"image/jpeg"},null);
        } catch (Exception e){
            toast(e.toString());
        }
    }
    private void saveData(byte[] w,String path) throws Exception{
        FileOutputStream out = null;
        try{
            out = new FileOutputStream(path);
            out.write(w);
            out.close();
        } catch (Exception e){
            if (out != null) out.close();
            throw e;
        }
    }
    private void toast(final String text){
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getContext(),text,Toast.LENGTH_LONG).show();
            }
        });
    }


}
