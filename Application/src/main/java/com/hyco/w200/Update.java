package com.hyco.w200;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.android.bluetoothlegatt.MyNative;
import com.example.android.bluetoothlegatt.tUpdate_info;
import com.hyco.w200.R;

import java.io.FileInputStream;
import java.io.IOException;

public class Update extends Activity {

    MyNative myNative = new MyNative();
    private Button start;
    String fileInput;
    String fileOutput;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);
        start = (Button) findViewById( R.id.startButton);
    }

    public void startButton(View v)
    {
        Log.i("开始转换文件","开始转换文件");
        //读SD中的文件
        try{
            String filePath = FilesOpt.getSdCardPath() + "/image_W16_15_20160606_c.hyc";
            try {
                imageNum = myNative.update_fileParse(filePath.getBytes());
            }catch (Exception  e) {
                Log.i("升级文件不存在：", "请放入升级文件");
                sendMessage(6);
            }

            Log.i("升级文件个数",String.valueOf(imageNum));

            fin = new FileInputStream(filePath);
            int filedataLenTotal = fin.available();
            Log.i("文件字节数",String.valueOf(filedataLenTotal));
            buffer = new byte[98];
        } catch(Exception e){
            e.printStackTrace();
        }

        int ret = myNative.update_getImageInfo(imageIndex, Update_info.ppVer_Str,
                Update_info.hw_info,
                Update_info.image_size,
                Update_info.image_crc,
                Update_info.image_data);

        myNative.wavemake();

    }

    String newtime;
    FilesOpt filesOpt = new FilesOpt();
    public int imageIndex = 0,imageNum=0 ,update_step=0;
    FileInputStream fin = null;
    tUpdate_info Update_info = new tUpdate_info();
    Boolean updateFlag = false;
    byte [] buffer = null;

    Runnable sendData = new Runnable()
    {
        @Override
        public void run()
        {
            //读SD中的文件
            try{
                String filePath = filesOpt.getSdCardPath() + "/image_W16_15_20160606_c.hyc";
                try {
                    imageNum = myNative.update_fileParse(filePath.getBytes());
                }catch (Exception  e) {
                    Log.i("升级文件不存在：", "请放入升级文件");
                    //sendMessage(6);
                }

                Log.i("升级文件个数",String.valueOf(imageNum));

                fin = new FileInputStream(filePath);
                int filedataLenTotal = fin.available();
                Log.i("文件字节数",String.valueOf(filedataLenTotal));
                buffer = new byte[98];
            } catch(Exception e){
                e.printStackTrace();
            }
            byte[] bytes = filesOpt.wakeupData;        //写入发送数据

            if(imageNum <1) {
                //sendMessage(6);
                Log.i("没有升级文件：", "没有升级文件");
                updateFlag = false;
            }
            else {
                int ret = myNative.update_getImageInfo(imageIndex, Update_info.ppVer_Str,
                        Update_info.hw_info,
                        Update_info.image_size,
                        Update_info.image_crc,
                        Update_info.image_data);
//                while (updateFlag)
//                {
//                    //发送唤醒
//                    if (update_step == 0) {
//                        Log.i("唤醒设备：", "wait...");
//                        WriteComm( bytes, bytes.length);
//                        try {
//                            Thread.sleep(100);
//                        } catch (InterruptedException e) {
//                            Log.i("等待延时：", "wait...");
//                        }
//                    }
//                    //Log.i("升级流程切换：", "wait...");
//                    update_step = update_Switch();
//
//                    if (update_step == 5) {
//                        //升级完成后
//                        updateFlag = false;
//                        sendMessage(0);
//                        break;
//                    }
//
//                    //updateFlag = false;
//                }
            }

            if(imageNum >0) {
                try {
                    fin.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Log.i("使能按键：", "wait...");
            update_step = 0;
            start.setClickable(true);
        }
    };

    int WriteComm(byte[] bytes,int length){

        return 0;
    }

    int update_Switch(){
        return 0;
    }

    public void sendMessage(int what)
    {
        Message message = new Message();
        message.what = what;
        handler.sendMessage(message);
    }

    final Handler handler = new Handler()
    {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch(msg.what){
                case 0:
                    update_step = 0;
                    //getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    Toast.makeText(getApplicationContext(), "升级成功！！！", Toast.LENGTH_SHORT).show();
                    break;

            }
        }
    };
}
