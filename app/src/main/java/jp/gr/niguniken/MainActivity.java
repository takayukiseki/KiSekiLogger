package jp.gr.niguniken;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // センサーマネージオブジェクト
    private SensorManager mSensorManager;

    // センサーオブジェクト
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private Sensor mMagnetic_Field;
    private Sensor mGravity;

    // センサーの利用可否
    private boolean mAccelerometer_Available = false;
    private boolean mGravity_Available = false;
    private boolean mGyroscope_Available = false;
    private boolean mMagnetic_Field_Available = false;

    // センサーの値
    private float[] mGravity_Values = new float[3];
    private float[] mMagnetic_Field_Values = new float[3];
    private float[] mOrientation_Values = new float[3];

    // 方位計算用
    private static final int MATRIX_SIZE = 16;
    private float[] mOrientation_RotationMatrix_In = new float[MATRIX_SIZE];
    private float[] mOrientation_RotationMatrix_Out = new float[MATRIX_SIZE];
    private float[] mOrientation_InclinationMatrix = new float[MATRIX_SIZE];

    // 画面コントロール
    TextView txtFileName;
    TextView txtNow;
    TextView txtOrientation_Azimuth_Radian;
    TextView txtOrientation_Pitch_Radian;
    TextView txtOrientation_Roll_Radian;
    TextView txtAccelerometer_X;
    TextView txtAccelerometer_Y;
    TextView txtAccelerometer_Z;
    TextView txtGravity_X;
    TextView txtGravity_Y;
    TextView txtGravity_Z;
    TextView txtGyroscope_X;
    TextView txtGyroscope_Y;
    TextView txtGyroscope_Z;
    TextView txtMagnetic_Field_X;
    TextView txtMagnetic_Field_Y;
    TextView txtMagnetic_Field_Z;

    // CSV出力タイマー
    private Timer mTimer;
    private TimerTask mTimerTask = null;

    // 現在作成中のCSVファイルパス
    private String mCSVFilePath;

    // Gmail起動時のリクエストコード
    private final int UNIKEN_GMAIL_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 画面のコントロールを取得
        getView();

        // センサーマネージャーを取得
        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);

        // センサーマネージャーからセンサーオブジェクトを取得
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mMagnetic_Field = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // CSV保存パスを画面に表示（起動前は参考情報）
        TextView txt = (TextView)findViewById(R.id.txtFilePath);
        txt.setText(Environment.getExternalStorageDirectory().getPath() + "/uniken_<入力ファイル名>_<yyyymmddhhmmss>.csv");

        // センサー一覧を取得し、使用可能なセンサーに「利用可能」を表記
        List<Sensor> lstSensor = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        StringBuilder strOther_Sensor = new StringBuilder();
        for (Sensor sensor: lstSensor) {
            switch (sensor.getType()){
                case Sensor.TYPE_ACCELEROMETER:
                    // 加速度センサーあり
                    mAccelerometer_Available = true;
                    ((TextView)findViewById(R.id.txtAccelerometer_Available)).setText(R.string.sensor_available_ok);
                    break;

                case Sensor.TYPE_MAGNETIC_FIELD:
                    // 磁界センサーあり
                    mMagnetic_Field_Available = true;
                    ((TextView)findViewById(R.id.txtMagnetic_Field_Available)).setText(R.string.sensor_available_ok);
                    break;

                case Sensor.TYPE_GYROSCOPE:
                    // 角速度センサーあり
                    mGyroscope_Available = true;
                    ((TextView)findViewById(R.id.txtGyroscope_Available)).setText(R.string.sensor_available_ok);
                    break;

                case Sensor.TYPE_GRAVITY:
                    // 重力センサーあり
                    mGravity_Available = true;
                    ((TextView)findViewById(R.id.txtGravity_Available)).setText(R.string.sensor_available_ok);
                    break;
            }
        }

        if (mGravity_Available & mMagnetic_Field_Available){
            // 重力センサーと磁界センサーが使用可能な場合には、方位センサーも使用可能
            ((TextView)findViewById(R.id.txtOrientation_Available)).setText(R.string.sensor_available_ok);
        }

        // 初期化ボタンのクリックイベントを定義
        Button btnFileName_Initialize = (Button)findViewById(R.id.btnFileName_Initialize);
        btnFileName_Initialize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 初期化ボタンが押され場合、ファイル名を初期値に戻す
                txtFileName.setText(R.string.file_name_default);
            }
        });

        // トグルボタンのクリックイベントを定義
        ToggleButton btn = (ToggleButton)findViewById(R.id.tglStart);
        btn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            // トグルボタンがクリックされたときのハンドラ
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {

                    // Timerをインスタンス化
                    mTimer = new Timer();
                    mTimerTask = new MainTimerTask();
                    mTimer.schedule(mTimerTask, 500, 200);

                    // ファイル名につけるタイムスタンプ取得
                    Date now = new Date(System.currentTimeMillis());
                    DateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");

                    // CSV保存パス作成　SDカードにuniken_<タイムスタンプ>.csv
                    mCSVFilePath = Environment.getExternalStorageDirectory().getPath() + "/uniken_" + txtFileName.getText() + "_" + formatter.format(now) + ".csv";

                    // CSV保存パスを画面に表示
                    TextView txt = (TextView)findViewById(R.id.txtFilePath);
                    txt.setText(mCSVFilePath);

                    try {
                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(mCSVFilePath, false), "SJIS"));
                        // csvファイルにヘッダー作成
                        bw.write("datetime,X_acceleration,Y_acceleration,Z_acceleration,X_gravity,Y_gravity,Z_gravity,X_angular_velocity,Y_angular_velocity,Z_angular_velocity,X_magnetic_field,Y_magnetic_field,Z_magnetic_field,azimuth,pitch,roll");
                        bw.newLine();
                        bw.flush();
                        bw.close();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    // TimerをOFF
                    mTimer.cancel();

                    // メール送信のチェック
                    Switch swhMail = (Switch)findViewById(R.id.swhSend_Mail);
                    if (swhMail.isChecked()){
                        // 停止時にGmailでメール送信がONの場合は、CSVファイルをメールに添付
                        Intent intent = new Intent();
                        intent.setAction(Intent.ACTION_SEND);
                        intent.putExtra(Intent.EXTRA_SUBJECT, "");
                        intent.putExtra(Intent.EXTRA_TEXT, "");
                        // Gmailにファイルの添付
                        Uri attachments = Uri.parse("file://" + mCSVFilePath);
                        intent.putExtra(Intent.EXTRA_STREAM, attachments);
                        intent.setType("application/*");
                        intent.setPackage("com.google.android.gm");
                        try {
                            // Gmail起動
                            startActivityForResult(intent, UNIKEN_GMAIL_REQUEST);

                        } catch (android.content.ActivityNotFoundException ex) {
                            ex.printStackTrace();
                        }
                    }
                    else {
                        // Gmailを起動しない場合は、即座にファイル削除のチェック
                        Switch swhDelete = (Switch)findViewById(R.id.swhFile_Delete);
                        if (swhDelete.isChecked()){
                            // 停止時にCSVファイル削除がONの場合は、CSVファイルを削除
                            File file = new File(mCSVFilePath);
                            file.delete();
                        }
                    }
                }
            }
        });
    }

    public class MainTimerTask extends TimerTask{
        @Override
        public void run() {

            try {
                // ファイル作成クラス
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(mCSVFilePath, true), "SJIS"));

                // csvファイル作成
                bw.write(txtNow.getText().toString());
                bw.write(",");
                bw.write(txtAccelerometer_X.getText().toString());
                bw.write(",");
                bw.write(txtAccelerometer_Y.getText().toString());
                bw.write(",");
                bw.write(txtAccelerometer_Z.getText().toString());
                bw.write(",");
                bw.write(txtGravity_X.getText().toString());
                bw.write(",");
                bw.write(txtGravity_Y.getText().toString());
                bw.write(",");
                bw.write(txtGravity_Z.getText().toString());
                bw.write(",");
                bw.write(txtGyroscope_X.getText().toString());
                bw.write(",");
                bw.write(txtGyroscope_Y.getText().toString());
                bw.write(",");
                bw.write(txtGyroscope_Z.getText().toString());
                bw.write(",");
                bw.write(txtMagnetic_Field_X.getText().toString());
                bw.write(",");
                bw.write(txtMagnetic_Field_Y.getText().toString());
                bw.write(",");
                bw.write(txtMagnetic_Field_Z.getText().toString());
                bw.write(",");
                bw.write(txtOrientation_Azimuth_Radian.getText().toString());
                bw.write(",");
                bw.write(txtOrientation_Pitch_Radian.getText().toString());
                bw.write(",");
                bw.write(txtOrientation_Roll_Radian.getText().toString());
                bw.newLine();
                bw.flush();
                bw.close();

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // タイムスタンプを表示
    private void setDateTimeNow(){
        Date now = new Date(System.currentTimeMillis());
        DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
        txtNow.setText(formatter.format(now));
    }

    // 画面の出力コントロールを取得
    private void getView(){
        txtFileName                     = (TextView)findViewById(R.id.txtFileName);
        txtNow                           = (TextView)findViewById(R.id.txtDateTime);
        txtOrientation_Azimuth_Radian = (TextView)findViewById(R.id.txtOrientation_Azimuth_Radian);
        txtOrientation_Pitch_Radian   = (TextView)findViewById(R.id.txtOrientation_Pitch_Radian);
        txtOrientation_Roll_Radian    = (TextView)findViewById(R.id.txtOrientation_Roll_Radian);
        txtAccelerometer_X             = (TextView)findViewById(R.id.txtAccelerometer_X);
        txtAccelerometer_Y             = (TextView)findViewById(R.id.txtAccelerometer_Y);
        txtAccelerometer_Z             = (TextView)findViewById(R.id.txtAccelerometer_Z);
        txtGravity_X                    = (TextView)findViewById(R.id.txtGravity_X);
        txtGravity_Y                    = (TextView)findViewById(R.id.txtGravity_Y);
        txtGravity_Z                    = (TextView)findViewById(R.id.txtGravity_Z);
        txtGyroscope_X                  = (TextView)findViewById(R.id.txtGyroscope_X);
        txtGyroscope_Y                  = (TextView)findViewById(R.id.txtGyroscope_Y);
        txtGyroscope_Z                  = (TextView)findViewById(R.id.txtGyroscope_Z);
        txtMagnetic_Field_X            = (TextView)findViewById(R.id.txtMagnetic_Field_X);
        txtMagnetic_Field_Y            = (TextView)findViewById(R.id.txtMagnetic_Field_Y);
        txtMagnetic_Field_Z            = (TextView)findViewById(R.id.txtMagnetic_Field_Z);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case UNIKEN_GMAIL_REQUEST:
                // ファイル削除のチェック
                Switch swhDelete = (Switch)findViewById(R.id.swhFile_Delete);
                if (swhDelete.isChecked()){
                    // 停止時にCSVファイル削除がONの場合は、CSVファイルを削除
                    File file = new File(mCSVFilePath);
                    file.delete();
                }
                break;

            default:
                break;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event){

        // トグルボタンをチェック
        ToggleButton btn = (ToggleButton)findViewById(R.id.tglStart);

        if (btn.isChecked()){

            // 方位センサー計算フラグ
            boolean calcOrientation = false;

            // タイムスタンプを表示
            setDateTimeNow();

            switch (event.sensor.getType()){

                case Sensor.TYPE_ACCELEROMETER:
                    // 加速度センサーの場合
                    txtAccelerometer_X.setText(String.valueOf(Math.floor(event.values[0] * 100) / 100));
                    txtAccelerometer_Y.setText(String.valueOf(Math.floor(event.values[1] * 100) / 100));
                    txtAccelerometer_Z.setText(String.valueOf(Math.floor(event.values[2] * 100) / 100));
                    break;

                case Sensor.TYPE_MAGNETIC_FIELD:
                    // 磁界センサーの場合
                    mMagnetic_Field_Values = event.values.clone();
                    txtMagnetic_Field_X.setText(String.valueOf(Math.floor(event.values[0] * 100) / 100));
                    txtMagnetic_Field_Y.setText(String.valueOf(Math.floor(event.values[1] * 100) / 100));
                    txtMagnetic_Field_Z.setText(String.valueOf(Math.floor(event.values[2] * 100) / 100));

                    // 磁界センサーの場合は、方位センサー再計算
                    calcOrientation = true;
                    break;

                case Sensor.TYPE_GYROSCOPE:
                    // 角速度センサーの場合
                    txtGyroscope_X.setText(String.valueOf(Math.floor(event.values[0] * 100) / 100));
                    txtGyroscope_Y.setText(String.valueOf(Math.floor(event.values[1] * 100) / 100));
                    txtGyroscope_Z.setText(String.valueOf(Math.floor(event.values[2] * 100) / 100));
                    break;

                case Sensor.TYPE_GRAVITY:
                    // 重力センサーの場合
                    mGravity_Values = event.values.clone();
                    txtGravity_X.setText(String.valueOf(Math.floor(event.values[0] * 100) / 100));
                    txtGravity_Y.setText(String.valueOf(Math.floor(event.values[1] * 100) / 100));
                    txtGravity_Z.setText(String.valueOf(Math.floor(event.values[2] * 100) / 100));

                    // 重力センサーの場合は、方位センサー再計算
                    calcOrientation = true;
                    break;
            }

            if (mAccelerometer_Available & mMagnetic_Field_Available & calcOrientation){
                // 方位センサー計算
                SensorManager.getRotationMatrix(mOrientation_RotationMatrix_In, mOrientation_InclinationMatrix, mGravity_Values, mMagnetic_Field_Values);
                SensorManager.remapCoordinateSystem(mOrientation_RotationMatrix_In, SensorManager.AXIS_X, SensorManager.AXIS_Y, mOrientation_RotationMatrix_Out);
                SensorManager.getOrientation(mOrientation_RotationMatrix_Out, mOrientation_Values);

                float Azimuth_Radian = mOrientation_Values[0];
                txtOrientation_Azimuth_Radian.setText(String.valueOf(Math.floor(Azimuth_Radian * 100) / 100));
                float Pitch_Radian = mOrientation_Values[1];
                txtOrientation_Pitch_Radian.setText(String.valueOf(Math.floor(Pitch_Radian * 100) / 100));
                float Roll_Radian = mOrientation_Values[2];
                txtOrientation_Roll_Radian.setText(String.valueOf(Math.floor(Roll_Radian * 100) / 100));

                // 参考：Degree角への変換
//                float Azimuth_Degree = (float)Math.floor(Math.toDegrees(Azimuth_Radian));
//                float Pitch_Degree = (float)Math.floor(Math.toDegrees(Pitch_Radian));
//                float Roll_Degree = (float)Math.floor(Math.toDegrees(Roll_Radian));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy){

    }

    @Override
    protected void onResume(){
        super.onResume();

        // SensorEventを観測するリスナを登録
        if (mAccelerometer_Available) mSensorManager.registerListener(this, mAccelerometer, mSensorManager.SENSOR_DELAY_NORMAL);
        if (mGyroscope_Available)  mSensorManager.registerListener(this, mGyroscope, mSensorManager.SENSOR_DELAY_NORMAL);
        if (mMagnetic_Field_Available)  mSensorManager.registerListener(this, mMagnetic_Field, mSensorManager.SENSOR_DELAY_NORMAL);
        if (mGravity_Available)  mSensorManager.registerListener(this, mGravity, mSensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // バックグラウンドでも稼働し続ける
    }
}
