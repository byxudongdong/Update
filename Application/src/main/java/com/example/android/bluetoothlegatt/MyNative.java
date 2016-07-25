package com.example.android.bluetoothlegatt;

import java.nio.MappedByteBuffer;
import java.util.Map;

/**
 * Created by byxdd on 2016/6/27 0027.
 */
public class MyNative {
    static {
        System.loadLibrary("JniTest");
    }

    //jni 直接访问变量
    public int imageIndex;
    public String mUserName;
    public int mUserAge;
    public long mMoney;

    //public native String getStringFromNative();
    public native int update_fileParse(byte[] fileName);
    public native int update_checkSetFlag(int flag);
    public native int update_getImageInfo(int index, byte[] ppVerStr, byte[] pHwInfo,
                                           byte[] pImageSize, byte[] pCrc, byte[] ppData);

    public native int wavemake(byte[] fileBytes);

    public  static void headCallback (int index, byte[] ppVerStr, byte[] pHwInfo,
                                      byte[] pImageSize, byte[] pCrc, byte[] ppData){

        System.out.println("相加的结果为"+ (index));

    }



}
