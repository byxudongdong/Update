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

import com.example.android.bluetoothlegatt.*;
import com.hyco.w200.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Update extends Activity {

    MyNative myNative = new MyNative();
    private MyProgress myProgress = null;
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
            String filePath = FilesOpt.getSdCardPath() + "/image_w200_20160726.hyc";
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
        byte[] write_bytes = new byte[FilesOpt.byteArrayToInt(Update_info.image_size)];
        System.arraycopy(Update_info.image_data,0,write_bytes,0,FilesOpt.byteArrayToInt(Update_info.image_size));

        try {
            FilesOpt.writeBytesSdcardFile( FilesOpt.getSdCardPath() + "/image_W200.bin", write_bytes);
        } catch (IOException e) {
            Log.i("解读文件失败","解读文件失败");
            e.printStackTrace();
        }
        //FileToCRCUtil.main( FilesOpt.getSdCardPath() + "/image_W200.bin");  //耗时
        //AES.main();
        //myNative.wavemake(write_bytes);

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

    public int update_sendLen=0,filedataLen=0,updateIdex= 0;
    public long startTime=0,consumingTime=0;  //開始時間

    final int UPDATE_REQUEST_ID	 =	(int)	(0xFFFF);
    final int UPDATE_CRC_RESP_ID 	=(int) (0xFFFE);

    final byte UPDATE_REQUST_OK			=		(0x00);//升级请求被接受
    final byte UPDATE_REJECT_REASON_HW_ERR		=	(0x01);//硬件版本错误
    final byte UPDATE_REJECT_REASON_SIZE_ERR	=	(0x02);//升级包大小错误(超过限制)

    int     UPDATE_SEND_PAKET_SIZE  = 92;

    final int UPDATE_STEP_SEND_REQUEST	=	0;
    final int UPDATE_STEP_WAIT_REQUEST_RES=	1;

    final int UPDATE_STEP_SEND_IMAGE		=	2;
    final int UPDATE_STEP_WAIT_IMAGE_RES	=	3;

    final int UPDATE_STEP_WAIT_CRC_RES	=	4;
    final int UPDATE_STEP_CRC_RES_RECV 	=	5;


    Boolean supportCipher = false;
    private int update_sendSize;
    void updateReceive_respons(byte[] pdata, int len)
    {
        int index, offset;
        int respons;
        int ret;

        offset = 4;
        //memcpy(&index, &pdata[offset], sizeof(U16));
        index = (pdata[offset] & 0xFF) | (pdata[offset+1] & 0x00FF)<<8 ;
        offset += 2;
        switch (index)
        {
            case UPDATE_REQUEST_ID:
                Log.i("解析升级请求数据....","解析升级请求数据");
                if (len > 3) ret = myNative.update_checkSetFlag(1);
                else ret = myNative.update_checkSetFlag(0);
                if (ret == 0)
                {
                    if (pdata[offset] == UPDATE_REJECT_REASON_HW_ERR)
                    {
				        /* 硬件版本错误 */
                        Log.i("硬件版本错误....","重新发送升级请求");
                        imageIndex++;
                        if (imageIndex >= imageNum) imageIndex = 0;
                    }
                    ret = myNative.update_getImageInfo(imageIndex, Update_info.ppVer_Str,
                            Update_info.hw_info,
                            Update_info.image_size,
                            Update_info.image_crc,
                            Update_info.image_data);
                    filedataLen = UpdateOpt.byteArrayToInt(Update_info.image_size);
                    Log.i("更换升级文件：=",String.valueOf(imageIndex) +":" + String.valueOf(filedataLen));
                    return;
                }
		        /* 接收升级请求回应 */
                switch(pdata[offset])
                {
                    case UPDATE_REQUST_OK:
			        /* 升级请求被接受 */
                        Log.i("请求被接收....","请求被接收");
                        try {
                            Thread.currentThread().sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        update_step++;
                        if (len > 3)
                        {
                            Log.i("芯片支持OAD....","芯片支持OAD");
                            supportCipher = true;
                        }
                        else
                        {
                            Log.i("不支持OAD....","");
                            supportCipher = false;
                        }

                        break;
                    case UPDATE_REJECT_REASON_HW_ERR:
			        /* 硬件版本错误 */
                        Log.i("硬件版本错误....","硬件版本错误");
                        imageIndex++;
                        if (imageIndex >= imageNum) imageIndex = 0;
                        ret = myNative.update_getImageInfo(imageIndex,Update_info.ppVer_Str,
                                Update_info.hw_info,
                                Update_info.image_size,
                                Update_info.image_crc,
                                Update_info.image_data);
                        filedataLen = UpdateOpt.byteArrayToInt(Update_info.image_size);
                        Log.i("更换升级文件：=",String.valueOf(imageIndex) +":" + String.valueOf(filedataLen));
                        break;
                    case UPDATE_REJECT_REASON_SIZE_ERR:
			        /* 升级包大小错误(超过限制) */
                        Log.i("升级包大小错误(超过限制)","超过限制");
                        break;
                }
                break;
            case UPDATE_CRC_RESP_ID:
		    /* 收到CRC校验回应 */
                if (pdata[offset] == 0)
                {
			        /* CRC校验正确 */
                    //mySetRecvInfo("CRC校验正确");
                    Log.i("CRC校验正确","CRC校验正确");
                    update_step = UPDATE_STEP_CRC_RES_RECV;
                }
                else
                {
			        /* 校验值错误，重发升级请求，重新升级 */
                    Log.i("校验值错误，重发升级请求，重新升级","校验值错误");
                    update_step = UPDATE_STEP_SEND_REQUEST;
                    update_sendSize = 0;

                }
                break;
            default:
		    /* 升级数据包回应 */
                if(pdata[offset] == 0 && index == (update_sendSize/UPDATE_SEND_PAKET_SIZE)+1 )
                {
			        /* 数据包被正确接收 */
                    update_sendSize += update_sendLen;
                    int persent = (int)Math.floor(100*((double)update_sendSize/filedataLen));
                    myProgress.setProgress(persent);
                    if (update_sendSize >= filedataLen)
                    {
				        /* 数据包发送完成，等待CRC校验结果 */
                        Log.i("数据包发送完成，等待CRC校验结果","等待CRC校验");
                        update_step = UPDATE_STEP_WAIT_CRC_RES;
                        break;
                    }
                }
                else
                {
                    Log.i("数据包接收错误，重发","数据包接收错误");
			        /* 数据包接收错误，重发 */
                }
                update_step--;
                break;
        }

    }
}
