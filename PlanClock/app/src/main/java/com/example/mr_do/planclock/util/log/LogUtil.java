package com.example.mr_do.planclock.util.log;

import android.util.Log;

/**
 * Created by Mr_Do on 2018/4/5.
 */

public class LogUtil{
    private static final ILogger mLogger = new Logger();
    private static final boolean showLog = true;
    private LogUtil(){

    }

    public static void logI(String tag, String message){
        if(showLog)
            mLogger.logInformation(tag, message);
    }

    public static void logE(String tag, String message){
        if(showLog)
            mLogger.logError(tag, message);
    }

    private interface ILogger{
        /**
         * 输出通知
         * @param tag 标志
         * @param message 信息
         */
        void logInformation(String tag, String message);

        /**
         * 输出错误
         * @param tag 标志
         * @param message 错误信息
         */
        void logError(String tag, String message);
    }

    private static class Logger implements ILogger{
        @Override
        public void logInformation(String tag, String message) {
            Log.v(tag, message);
        }

        @Override
        public void logError(String tag, String message) {
            Log.e(tag, message);
        }
    }
}
