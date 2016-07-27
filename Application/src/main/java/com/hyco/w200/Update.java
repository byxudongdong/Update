package com.hyco.w200;

import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.bluetoothlegatt.*;
import com.hyco.w200.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class Update extends Activity {

    Thread mthread;
    private TextView mDataField;
    private TextView textView;
    MyNative myNative = new MyNative();
    private MyProgress myProgress = null;
    private Button start;
    String fileInput;
    String fileOutput;
    public static Boolean receiveDataFlag = false;

    int audioMaxVolumn;//  最大音量
    int audioCurrentVolumn;// 当前音量

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);
        start = (Button) findViewById( R.id.startButton);

        mDataField = (TextView) findViewById(R.id.data_value);

        textView = (TextView)findViewById(R.id.textView);

        am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        audioMaxVolumn = am.getStreamMaxVolume(AudioTrack_Manager);
        audioCurrentVolumn = am.getStreamVolume(AudioTrack_Manager);

        start_play();
        startRecord();
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
        getHw_version = false;
        Log.i("开始升级", "button onClick");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        start.setClickable(false);

        //int ret = update_fileParse(fileName);

        updateFlag = true;
        mthread = new Thread(sendData, "Update");
        mthread.start();

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
                String filePath = filesOpt.getSdCardPath() + "/image_w200_20160726.hyc";
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
            byte[] bytes = {0x11,0x40, 0x02, 0x53,(byte)0xEE, 0x43, 0x2A};        //写入发送数据

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

                while (updateFlag)
                {
                    //发送唤醒
                    if (update_step == 0) {
                        Log.i("唤醒设备：", "wait...");
                        WriteComm( bytes, bytes.length);
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Log.i("等待延时：", "wait...");
                        }
                    }
                    //Log.i("升级流程切换：", "wait...");
                    update_step = update_Switch();

                    if (update_step == 5) {
                        //升级完成后
                        updateFlag = false;
                        sendMessage(0);
                        break;
                    }

                    //updateFlag = false;
                }
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
        int wavelen =0;
        byte[] wavedata = new byte[48000*2];
        int count = myNative.wavemake(bytes,length,wavedata,wavelen);
//        writeDateTOFile(wavedata);//往文件中写入裸数据
//        copyWaveFile(AudioName, NewAudioName);//给裸数据加上头文件
//        Log.i("生成WAV","生成WAV");
        start_scan(wavedata);
        return 0;
    }

    //AudioName裸音频数据文件
    private static final String AudioName = "/sdcard/temp.raw";
    //NewAudioName可播放的音频文件
    private static final String NewAudioName = "/sdcard/new.wav";
    /**
     * 这里将数据写入文件，但是并不能播放，因为AudioRecord获得的音频是原始的裸音频，
     * 如果需要播放就必须加入一些格式或者编码的头信息。但是这样的好处就是你可以对音频的 裸数据进行处理，比如你要做一个爱说话的TOM
     * 猫在这里就进行音频的处理，然后重新封装 所以说这样得到的音频比较容易做一些音频的处理。
     */
    private void writeDateTOFile(byte[] data) {
        // new一个byte数组用来存一些字节数据，大小为缓冲区大小
        byte[] audiodata = new byte[48000*2];
        FileOutputStream fos = null;
        int readsize = 0;
        try {
            File file = new File(AudioName);
            if (file.exists()) {
                file.delete();
            }
            fos = new FileOutputStream(file);// 建立一个可存取字节的文件
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (true) {
                try {
                    fos.write(data);
                } catch (IOException e) {
                    e.printStackTrace();
                }

        }
        try {
            fos.close();// 关闭写入流
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    int sampleRateInHz = 44100;
    // 这里得到可播放的音频文件
    private void copyWaveFile(String inFilename, String outFilename) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = sampleRateInHz;
        int channels = 2;
        long byteRate = 16 * sampleRateInHz * channels / 8;
        byte[] data = new byte[48000*2];
        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);
            while (in.read(data) != -1) {
                out.write(data);
            }
            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    int update_Switch(){
        //startTime = System.currentTimeMillis();  //開始時間
        switch (update_step)
        {
            case UPDATE_STEP_SEND_REQUEST:
                //发送升级请求
                Log.i("发送升级请求：", "发送升级请求");
                sendMessage( 2 );
                receiveDataFlag = false;
                //while(!receiveDataFlag)
            {
                update_sendUpdateReq();
                try {
                    Thread.currentThread().sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            update_sendSize = 0;
            update_step++;
            startTime = System.currentTimeMillis();  //開始時間
            break;
            case UPDATE_STEP_SEND_IMAGE:
                Log.i("发送升级文件：", "发送升级文件"+String.valueOf(update_sendSize)
                        + ":"+String.valueOf(filedataLen) );
                sendMessage( 3 );
                /* 发送升级数据 */
                if( update_sendSize >= filedataLen) {
                    update_step = UPDATE_STEP_WAIT_CRC_RES;
                    break;
                }
                update_sendLen = update_sendImageData();
                if( update_sendLen < 1 ) {
                    startTime = System.currentTimeMillis();  //開始時間
                    update_step = UPDATE_STEP_WAIT_CRC_RES;
                    break;
                }
                startTime = System.currentTimeMillis();  //開始時間
                update_step++;
                break;
            case UPDATE_STEP_WAIT_REQUEST_RES:
                consumingTime = System.currentTimeMillis();
                if ((consumingTime - startTime) >= 1500)
                {
			        /* 超时重发 */
                    Log.i("发送升级请求：", "超时重发");
                    update_step = UPDATE_STEP_SEND_REQUEST;
                }
                break;
            case UPDATE_STEP_WAIT_IMAGE_RES:
                /* 等待升级请求和升级数据回应 */
                consumingTime = System.currentTimeMillis();
                if ((consumingTime - startTime) >= 800)
                {
			        /* 超时重发 */
                    Log.i("发送升级文件：", "超时重发");
                    update_step = UPDATE_STEP_SEND_IMAGE;
                }
                break;
            case UPDATE_STEP_WAIT_CRC_RES:
                /* 等待升级请求和升级数据回应 */
                consumingTime = System.currentTimeMillis();
                if ((consumingTime - startTime) >= 5000)
                {
                    /* 超时 */
                    /* 重启，认为升级成功 */
                    //mySetRecvInfo("升级完成");
                    Log.i("升级完成：", "升级完成");
                    update_step = UPDATE_STEP_CRC_RES_RECV;
                }
                break;
            case UPDATE_STEP_CRC_RES_RECV:
                Log.i("CRC校验正确", "升级完成");
                Log.i("升级完成", "升级完成");
                //升级成功
                break;
        }
        return update_step;
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

    /* 封包起始和结尾字节 */
    byte    COMM_PAKET_START_BYTE    = 0x40;
    byte    COMM_PAKET_END_BYTE		=(byte)	(0x2A);
    byte    COMM_PAKET1_END_BYTE		=(byte)	(0xA2);
    /* 收发类型 */
    byte	COMM_TRANS_TYPE_SEND		=	(0x53);	/* 'S'---send */
    byte	COMM_TRANS_TYPE_RESP		=	(0x52);	/* 'R'---response */

    byte	COMM_CMD_TYPE_UPDATE		=(byte)	(0xD0);	//软件升级
    byte	COMM_CMD_TYPE_VOICE		=(byte)	(0xD1);	//语音数据传输
    byte	COMM_CMD_TYPE_DONGLE_SN	=(byte)	(0xD2);	//dongle序列号z
    byte	COMM_CMD_TYPE_TOUCH		=	(byte)0xDF;	//touch数据
    byte	COMM_CMD_TYPE_VERSION		=	(byte)(0xE0);	//R11版本信息

    void update_sendUpdateReq()
    {
        byte temp[] = new byte[32];
        int requestId;
        int len;
        //supportCipher = false;
	/* 已发送数据大小 */
        update_sendSize = 0;
        len = 0;
        //发送升级请求，并等待回应
        //requestId = UPDATE_REQUEST_ID;
        //memcpy(&temp[len], &requestId, 2);
        temp[len] = (byte)0xFF;
        temp[len+1] = (byte)0xFF;
        len += 2;
        //memcpy(&temp[len], &tUpdate_info.hw_info, 4);
        System.arraycopy(Update_info.hw_info,0,temp,len,4);
        len += 4;
        //memcpy(&temp[len], &tUpdate_info.image_size, 4);
        System.arraycopy(Update_info.image_size,0,temp,len,4);
        len += 4;
        //memcpy(&temp[len], &tUpdate_info.image_crc, 4);
        System.arraycopy(Update_info.image_crc,0,temp,len,4);
        len += 4;
        //comm_send(COMM_TRANS_TYPE_SEND, COMM_CMD_TYPE_UPDATE, &temp[1], len-1);//只为唤醒目标机
        //Delay(50);
        comm_send(COMM_TRANS_TYPE_SEND, COMM_CMD_TYPE_UPDATE, temp, len);
    }

    int update_sendImageData()
    {
        byte UPDATE_SEND_PAKET_SIZE	=	(92);//(100)//(112)//(32)//(12)//(14)//(64)//
        byte[] temp = new byte[UPDATE_SEND_PAKET_SIZE+2];
        int imageReadLen = 0;
        int index;

        index = (update_sendSize/UPDATE_SEND_PAKET_SIZE)+1;
        //memcpy(&temp[0], &index, sizeof(U16));
        temp[0] = (byte) (index >> 8 * 0 & 0xFF);
        temp[1] = (byte) (index >> 8 * 1 & 0xFF);

        imageReadLen = update_readImageData(temp, update_sendSize, UPDATE_SEND_PAKET_SIZE);
        if (imageReadLen <= 0)
        {
		/* 升级数据发送完成 */
            return 0;
        }

        comm_send(COMM_TRANS_TYPE_SEND, COMM_CMD_TYPE_UPDATE, temp, imageReadLen+2);

        return imageReadLen;
    }

    /*-------------------------------------------------------------------------
* 函数: update_readImageData
* 说明: 读取image数据
* 参数: ptUpdataInfo
* 返回: 读取大小
-------------------------------------------------------------------------*/
    int update_readImageData(byte pData[], int offset, int len)
    {
        int readLen;
        byte[] senddata=new byte[98];

        if (offset >= filedataLen)
        {
            //readLen = 0;
            return -1;
        }
        else if ((offset+len) > filedataLen)
        {
            readLen = filedataLen - offset;
        }
        else
        {
            readLen = len;
        }
//        try {
//            readLen = fin.read(senddata,offset ,98);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        System.arraycopy(Update_info.image_data,offset,senddata,0,readLen);
        System.arraycopy(senddata , 0, pData, 2 , readLen);

        return readLen;
    }
/*-------------------------------------------------------------------------
* 函数: comm_send
* 说明: 发送
* 参数: pData---数据buffer
		len-----条码长度
* 返回: HY_OK------发送成功
		HY_ERROR---发送失败
-------------------------------------------------------------------------*/
    Boolean comm_send(byte transType, byte cmd, byte[] pData, int len)
    {
        byte i;
        byte[] temp = new byte[len + 6];
        int sum=0;

        if (pData==null) return false;

        temp[0] = COMM_PAKET_START_BYTE;
        temp[1] = (byte)(len+2);
        temp[2] = (byte)transType;
        temp[3] = (byte)cmd;
        //memcpy(&temp[4], pData, len);
        System.arraycopy(pData,0,temp,4,len);
        temp[len+5] = COMM_PAKET_END_BYTE;
        for(i=0; i<len+3; i++)
        {
            sum += temp[i+1];
        }
        temp[len+4] = (byte)sum;
        //Log.i("调用特征值写：", "wait...");
        byte[] temp1 = new byte[ len+7];
        temp1[0] = 0x11;
        System.arraycopy(temp,0,temp1,1,len+6);
        WriteComm( temp1, len+7);

        return true;
    }

    Boolean getHw_version = false;
    byte[][] Hw_version = new byte[6][64];
    int HW_index = 0;
    int Hw_dataindex = 6;
    public int DecodeData(byte[] decodedata,int datalen)
    {
        byte[] data = decodedata;
        receiveDataFlag = true;
        PrintLog.printHexString("接收到data*****************", data);
        displayData(PrintLog.returnHexString(data));
        sendMessage(1);
        if(getHw_version && data != null)
        {
            if(data[0]  == 0x40)
            {
                HW_index = 0;
            }
            System.arraycopy(data,0,Hw_version[Hw_dataindex-1],HW_index,data.length);
            HW_index = HW_index + data.length;
            if( data[data.length -1] == 0x2A)
            {
                System.arraycopy(Hw_version[Hw_dataindex-1], 4, Hw_version[Hw_dataindex-1], 0,HW_index-7);
                HW_index = 0;
                Hw_dataindex --;
                if(Hw_dataindex <=0)
                {
                    Hw_dataindex =6;
                    //getHw_version = false;
                    Log.i("版本信息接受完毕","版本信息接受完毕");
                    textView.setText(new String(Hw_version[0] ) +"\n"
                            +new String(Hw_version[1] ) +"\n"
                            +new String(Hw_version[2]) +"\n"
                            +new String(Hw_version[3]) +"\n"
                            +new String(Hw_version[4]) +"\n"
                            +new String(Hw_version[5]));

                }
            }

        }else if(!getHw_version && data != null)
        {
            updateReceive_respons(data, data[1]);
        }
        return 0;
    }

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

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    AudioManager am;
    private int AudioTrack_Manager = AudioManager.STREAM_SYSTEM;
    private Thread PlayAudioThread = null;// 播放音频线程
    private AudioTrack trackplayer;
    public void start_play() {
        // 根据采样率，采样精度，单双声道来得到frame的大小。
        int bufsize = AudioTrack.getMinBufferSize(48000,//
                AudioFormat.CHANNEL_CONFIGURATION_STEREO,// 双声道
                AudioFormat.ENCODING_PCM_16BIT);// 一个采样点16比特-2个字节

        am.setStreamVolume(AudioTrack_Manager, audioMaxVolumn *4 / 5,
                0);
        trackplayer = new AudioTrack(AudioTrack_Manager, 48000,
                AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                AudioFormat.ENCODING_PCM_16BIT, bufsize, AudioTrack.MODE_STREAM);//
        //mThreadExitFlag = false;
        trackplayer.play();
//        PlayAudioThread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                start_scan();
//            }
//        });
//        PlayAudioThread.start();
    }

    /**
     * 播放指令数据
     */
    public void start_scan(byte[] byte_damo) {

        try {
            trackplayer.write(byte_damo, 0, byte_damo.length);// 往track中写数据
        } catch (Exception e) {
            PlayAudioThread.interrupt();
        }

    }

    private AudioRecord audioRecord = null;
    private Thread recordingThread = null;
    private void startRecord() {
        createAudioRecord();
        audioRecord.startRecording();
        //isRecording = true;
        recordingThread = new Thread(new Runnable() {
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    private int recBufSize = 0;
    private static int frequency = 44100;
    private static int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;// 单声道
    private static int EncodingBitRate = AudioFormat.ENCODING_PCM_16BIT; //音频数据格式：脉冲编码调制（PCM）每个样品16位
    /**
     *启动录音
     */
    public void createAudioRecord() {
        recBufSize = AudioRecord.getMinBufferSize(frequency,
                channelConfiguration, EncodingBitRate);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency,
                channelConfiguration, EncodingBitRate, recBufSize);
        System.out.println("AudioRecord成功");
    }

    /**
     * 录音数据解析
     */
    private void writeAudioDataToFile() {
        byte data[] = new byte[recBufSize];
        int read = 0;
        while (true) {
            read = audioRecord.read(data, 0, recBufSize);
            if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                simple_c2java(data);
            }
        }
    }

    /**
     * 获取mic录音数据同步解析；
     * @param byte_source
     */
    int s;
    private byte[] code_data;
    private String code_msg = "";
    public void simple_c2java(byte[] byte_source) {
        code_data = new byte[320];
        s = myNative.cToJava(byte_source, byte_source.length, code_data);
        if (s > 0) {
            byte[] send_data = new byte[s];
            System.arraycopy(code_data, 0, send_data, 0, s);
            code_msg = sendData(send_data);
            if (code_msg.length() > 0) {
                handler.sendEmptyMessage(0x001);
            }
        }
    }

    /**
     * 录音数据解析结果
     * @param data
     * @param flag
     * @return
     */
    public boolean insert_detect_success_flag = false;//能否收到w200数据
    public boolean responses_detect_Data_flag = false;// 设备确认
    private int reversePolarity = -1;
    public String sendData(byte[] data) {
        final StringBuilder stringBuilder = new StringBuilder();
        if (data != null && data.length > 0) {
            for (byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));//表示以十六进制形式输出,02表示不足两位，前面补0输出；出过两位，不影响

            Log.d("数据解析结果", stringBuilder.toString() +"////" + reversePolarity);
            if (stringBuilder.toString().contains("53 01 ")
                    && stringBuilder.toString().startsWith("40")
                    && stringBuilder.toString().endsWith("2A ")) {
                if (Util.checkCurrentNumber(stringBuilder.toString())) {
                    String ss = Util.getCurrentNumberString(stringBuilder
                            .toString());
                    stringBuilder.delete(0, stringBuilder.length());
                    insert_detect_success_flag = true;
                    return ss.length() > 0 ? ss : "";
                }
            } else if (stringBuilder.toString().startsWith("40 03 52 0D")) {
                String ss = Util.getPowerNumberString(stringBuilder.toString());
                stringBuilder.delete(0, stringBuilder.length());
                return "当前电量：" + ss;
            } else if (stringBuilder.toString().startsWith("40 04 52 A0")) {
                if(Util.checkCurrentNumber(stringBuilder.toString())){
                    responses_detect_Data_flag = true;
                    insert_detect_success_flag = true;
//					if(adaptation_flag){
//						if(reversePolarity == 0){
//							SaveShardMessage.change_track_flag = true;
//						}else{
//							SaveShardMessage.change_track_flag = false;
//						}
//					}
                    stringBuilder.delete(0, stringBuilder.length());
                    return "检测到W200设备";
                }
            }
        }
        return "";
    }

    /**
     * 这里提供一个头信息。插入这些信息就可以得到可以播放的文件。
     * 为我为啥插入这44个字节，这个还真没深入研究，不过你随便打开一个wav
     * 音频的文件，可以发现前面的头文件可以说基本一样哦。每种格式的文件都有
     * 自己特有的头文件。
     */
    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels, long byteRate)
            throws IOException {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = 16; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }
}
