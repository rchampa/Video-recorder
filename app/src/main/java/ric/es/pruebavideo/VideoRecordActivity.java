package ric.es.pruebavideo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class VideoRecordActivity extends FragmentActivity implements SurfaceHolder.Callback {

    public static final int MAX_VIDEO_WIDTH = 1280;
    public static final float ASPECT = 16f / 9f;
    private static final int TECLA_BACK = 999;
    private static final int NO_VIDEO = 998;


    @InjectView(R.id.fl_video)
    FixedAspectRatioFrameLayout fl_video;


    @InjectView(R.id.bt_rec)
    ImageView ibRec;
    @InjectView(R.id.bt_stop)
    ImageView ibStop;


    @InjectView(R.id.surface)
    SurfaceView surface;



    boolean gpsLock = false, recording = false;


    private Camera camera;
    private Pair<Integer, Integer> recResolution;
    private SurfaceHolder holder;
    private boolean surfaceHolderCreated = false;
    private Pair<Integer, Integer> surfaceSize;
    private MediaRecorder recorder;
    private String videoFile = "";



    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.eje_calzada_rec);

        ButterKnife.inject(this);

//        ibRec.setEnabled(false);
        ibStop.setEnabled(false);

        holder = surface.getHolder();
        holder.addCallback(this);
        // Necessary for Android pre-11
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        recorder = new MediaRecorder();

    }



    @Override
    public void onBackPressed() {
        if(!recording){
            super.onBackPressed();
            return;
        }
        onClickStop(ibStop);
    }

    public Dialog onCreateDialog(int id) {
        if (id == TECLA_BACK) {
            return new AlertDialog.Builder(this).setTitle("Salir")
                    .setMessage(R.string.salir_mensaje_advertencia_video)
                    .setPositiveButton(R.string.aceptar, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            endRecord();
                        }
                    }).setNegativeButton(R.string.cancelar, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    }).create();
        }
        if (id == NO_VIDEO) {
            return new AlertDialog.Builder(this).setTitle(R.string.video)
                    .setMessage(R.string.error_video)
                    .setPositiveButton(R.string.aceptar, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            onClickStop(null);
                        }
                    }).create();
        }
        return super.onCreateDialog(id);
    }


    public void onClickRec(final View v) {

        if(recording){
            Toast.makeText(VideoRecordActivity.this, "Ya estás grabando", Toast.LENGTH_SHORT).show();
            return;
        }

        ibStop.setEnabled(true);
        ibRec.setEnabled(false);
        ibRec.setImageResource(R.drawable.record_pressed);
        ibStop.setImageResource(R.drawable.stop_normal);


//        new File("video").mkdirs();

        if (surfaceHolderCreated) {

            String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/MyFolder/";
            File dir = new File(path);
            if(!dir.exists())
                dir.mkdirs();
            String myfile = path + "prueba" + ".mp4";

            initRecorder(myfile);
            recorder.start();
        } else {
            showDialog(NO_VIDEO);
            return;
        }

        recording = true;

    }

    public void onClickStop(final View v) {
        showDialog(TECLA_BACK);
    }

    private void endRecord() {
        if (recording) {

            recorder.stop();
            // Refresh media database to make newly created file available to other programs
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(videoFile))));
            recording = false;
        }
        finish();
    }

    private void startVideoCam() {
        if (!surfaceHolderCreated) {
            return;
        }
        try {
            try {
                camera = Camera.open();
            } catch (RuntimeException r) {
                camera = null;
            }

            if (camera == null) {
                try {
                    camera = Camera.open(0);
                } catch (RuntimeException r) {
                    showDialog(NO_VIDEO);
                    return;
                }
            }

            camera.setPreviewDisplay(holder);
            setCameraDisplayOrientation(this, 0, camera);

            // Parameters param = camera.getParameters();
            Camera.Parameters params = camera.getParameters();

            Camera.Size cs = getOptimalRecordSize(MAX_VIDEO_WIDTH, ASPECT, params);
            if (cs == null || surfaceSize == null) {
                noVideoAvailable();
                return;
            }
            Log.d("Mobility", "Selected camera resolution: " + cs.width + " x " + cs.height);
            params.setPictureSize(cs.width, cs.height);
            cs = getOptimalPreviewSize(surfaceSize.first, surfaceSize.second, params);
            params.setPreviewSize(cs.width, cs.height);
            camera.setParameters(params);

            camera.startPreview();
        } catch (IOException e) {
            Log.d("CAMERA", e.getMessage());
        }

    }

    private void noVideoAvailable() {
        showDialog(NO_VIDEO);
    }


    @Override
    public void surfaceCreated(final SurfaceHolder holder) {
        surfaceHolderCreated = true;
    }

    @Override
    public void surfaceDestroyed(final SurfaceHolder holder) {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
        surfaceSize = new Pair<Integer, Integer>(width, height);
        startVideoCam();
    }

    /**
     * Takes care of all the mediarecorder settings
     *
     * @param path Path + filename without extension for video file
     */
    private void initRecorder(final String path) {
        videoFile = path;
        try {
            // Solves a -9 return bug in some Android versions
            camera.lock();
            camera.unlock();

            //http://joerg-richter.fuyosoft.com/?p=127

            recorder.setCamera(camera);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            //https://support.google.com/youtube/answer/1722171?hl=en
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                recorder.setProfile(CamcorderProfile.get(0, CamcorderProfile.QUALITY_1080P));
//                recorder.setVideoEncodingBitRate((1024 * 5) * 1024);
            }
            recorder.setPreviewDisplay(holder.getSurface());
            recorder.setOutputFile(videoFile);
            recorder.setMaxDuration((int) TimeUnit.HOURS.toMillis(2));

            recorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            showDialog(NO_VIDEO);
        } catch (IOException e) {
            e.printStackTrace();
            showDialog(NO_VIDEO);
        }
        catch (Exception e){
            e.printStackTrace();
            showDialog(NO_VIDEO);
        }

    }

    private Camera.Size getOptimalPreviewSize(final int width, final int height, final Camera.Parameters parameters) {
        Camera.Size result = null;
        float dr = Float.MAX_VALUE;
        float recordAspect = (float) width / (float) height;

        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        for (Camera.Size size : sizes) {
            float previewAspect = (float) size.width / (float) size.height;
            if (Math.abs(previewAspect - recordAspect) <= dr && size.width <= width && size.height <= height) {
                dr = Math.abs(previewAspect - recordAspect);
                result = size;
            }
        }

        return result;
    }

    private Camera.Size getOptimalRecordSize(final int maxWidth, float aspect, final Camera.Parameters parameters) {
        Camera.Size result = null;

        List<Camera.Size> sizes = parameters.getSupportedPictureSizes();
        for (Camera.Size size : sizes) {
            int horCalculated = (int) (size.height * aspect);
            if (size.width == horCalculated && size.width <= maxWidth) {
                if (result != null) {
                    if (size.width > result.width) {
                        result = size;
                    }
                } else {
                    result = size;
                }
            }
        }
        if (result != null) {
            recResolution = new Pair<Integer, Integer>(result.width, result.height);
        }
        return result;
    }

    private void setCameraDisplayOrientation(final Activity activity, final int cameraId, final Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

}
