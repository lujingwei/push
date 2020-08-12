package com.upyun.push;


import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.orhanobut.hawk.Hawk;
import com.upyun.hardware.AsyncRun;
import com.upyun.hardware.AudioEncoder;
import com.upyun.hardware.Config;
import com.upyun.hardware.PushClient;
import com.upyun.hardware.UConstant;

import java.io.IOException;
import java.text.DecimalFormat;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MainActivity extends Activity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "MainActivity";
    private SurfaceRenderView surface;
    private PushClient mClient;
    private ImageView mBtToggle;
    private ImageView mBtSetting;
    private ImageView mBtconvert;
    private ImageView mImgFlash;
    private TextView mStreamInfo;
    private Config config;
    private static final int REQUEST_CODE_PERMISSION_CAMERA = 100;
    private static final int REQUEST_CODE_PERMISSION_RECORD_AUDIO = 101;

    private Switch st_noise;

    private PushClient.RtmpPublishListener mRtmpPublishListener = new PushClient.RtmpPublishListener() {
        @Override
        public void onRtmpPublishInfo(final int bitrate, final double fps, final long totalSize) {
            AsyncRun.run(new Runnable() {
                @Override
                public void run() {
                    DecimalFormat df = new DecimalFormat("######0.0");
                    StringBuilder sb = new StringBuilder();
                    sb.append("url:  " + mClient.getConfig().url + "\r\n");
                    sb.append("bitrate:  " + bitrate + "kbps\r\n");
                    sb.append("fps:  " + df.format(fps) + "fps\r\n");
                    sb.append("totalsize:  " + totalSize + "KB\r\n");
//            Log.e(TAG, "bitrate:" + bitrate + "kbps, fps:" + df.format(fps) + "fps, totalSize:" + totalSize + "KB");
                    mStreamInfo.setText(sb);
                }
            });
        }
    };
    private Dialog loadingDialog;
    private MainActivity mContext;
    private AlertDialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        surface = (SurfaceRenderView) findViewById(R.id.sv_camera);
        mBtToggle = (ImageView) findViewById(R.id.bt_toggle);
        mBtSetting = (ImageView) findViewById(R.id.bt_setting);
        mBtconvert = (ImageView) findViewById(R.id.bt_convert);
        mImgFlash = (ImageView) findViewById(R.id.img_flash);
        st_noise = (Switch) findViewById(R.id.st_noise);
        st_noise.setOnCheckedChangeListener(this);
        mBtToggle.setOnClickListener(this);
        mBtSetting.setOnClickListener(this);
        mBtconvert.setOnClickListener(this);
        mImgFlash.setOnClickListener(this);
        mImgFlash.setEnabled(true);
        mStreamInfo = (TextView) findViewById(R.id.tv_streaminfo);

        mClient = new PushClient(this, surface, mHandler);
        mClient.setReconnectEnable(true);//开启自动重连
        mClient.setAdjustBitEnable(true);
        mClient.setOnRtmpPublishListener(mRtmpPublishListener);
        initUrl();
        // check permission for 6.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermission();
        }
        showLoading("初始化中...");
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                loadDismiss();
            }
        }, 5000);
    }

    @Override
    protected void onStart() {
        super.onStart();
        config = MyApplication.getInstance().config;
        if (config != null) {
            mClient.setConfig(config);
        }

        changeSurfaceSize(surface, mClient.getConfig());
    }
    private void initUrl() {
        String url = Hawk.get("Url");
        Log.i("log", "=======" + url);
        Config config = Config.getInstance();
        if (!TextUtils.isEmpty(url)) {
            config.url = url;
            mClient.setConfig(config);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_toggle:
                String url = mClient.getConfig().url;
                if (TextUtils.isEmpty(url)) {
                    showInputDialog();
                    return;
                }
                String str = "";
                if (mClient.isStart()) {
                    mClient.stopPush();
                    Log.e(TAG, "stop");
                    str = "正在关闭...";
//                    mBtToggle.setText("start");
                } else {
                    try {
                        mClient.startPush();
                        Log.e(TAG, "start");
//                        mBtToggle.setText("stop");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    str = "正在开启中...";
                }
                showLoading(str);
                break;
            case R.id.bt_setting:
                mClient.stopPush();
                mBtToggle.setImageResource(R.mipmap.start);
                startActivity(new Intent(this, SetUrlActivity.class));
                break;

            case R.id.bt_convert:
                boolean converted = mClient.covertCamera();
                if (converted) {
                    mImgFlash.setEnabled(!mImgFlash.isEnabled());
                }
                break;

            case R.id.img_flash:
                mClient.toggleFlashlight();
                break;
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == UConstant.MSG_STREAM_START) {
                mBtToggle.setImageResource(R.mipmap.stop);
            } else if (msg.what == UConstant.MSG_STREAM_STOP) {
                mBtToggle.setImageResource(R.mipmap.start);
                mStreamInfo.setText("");
            }
            loadDismiss();
        }
    };

    private void showInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View inflate = LayoutInflater.from(this).inflate(R.layout.layout_input_dialog, null);
        final EditText eInput = (EditText) inflate.findViewById(R.id.et_input);
        Button bt = (Button) inflate.findViewById(R.id.bt_dialog);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String trim = eInput.getText().toString().trim();
                if (TextUtils.isEmpty(trim)) {
                    Toast.makeText(mContext, "请输入正确的域名地址", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    if (trim.startsWith("rtmp")) {
                        trim += "/live/" + (int) ((Math.random() * 9 + 1) * 100000);
                    } else {
                        trim = "rtmp://" + trim + "/live/" + (int) ((Math.random() * 9 + 1) * 100000);
                    }
                    Config config = mClient.getConfig();
                    config.url = trim;
                    MyApplication.getInstance().config = config;
                    Hawk.put("Url", trim);
                    Hawk.put("oldUrl", eInput.getText().toString().trim());
                    alertDialog.dismiss();
                }
            }
        });
        builder.setView(inflate);
        builder.setCancelable(true);
        alertDialog = builder.show();
    }

    private void showLoading(String str) {
        loadingDialog = LoadingDialog.createLoadingDialog(mContext, str);
        LoadingDialog.showDialog(loadingDialog);
    }

    private void loadDismiss() {
        if (loadingDialog != null) {
            LoadingDialog.closeDialog(loadingDialog);
        }
    }

    private void changeSurfaceSize(SurfaceRenderView surface, Config config) {
        int width = 1280;
        int height = 720;

        switch (config.resolution) {
            case HIGH:
                width = 1280;
                height = 720;
                break;
            case NORMAL:
                width = 640;
                height = 480;
                break;
            case LOW:
                width = 320;
                height = 240;
                break;
        }

//        ViewGroup.LayoutParams lp = surface.getLayoutParams();
//
//        lp.width = height;
//        lp.height = width;
//        surface.setLayoutParams(lp);
        surface.setVideoSize(height, width);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mClient != null) {
            mClient.stopPush();
            mClient.setReconnectEnable(false);//关闭自动重连
        }
    }

    // check permission
    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_PERMISSION_CAMERA);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_PERMISSION_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "granted camera permission");
            } else {
                Log.e(TAG, "no camera permission");
            }
        } else if (requestCode == REQUEST_CODE_PERMISSION_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "granted record audio permission");
            } else {
                Log.d(TAG, "no record audio permission");
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.st_noise:
                AudioEncoder.NOISE = isChecked;
                break;
        }
    }
}

