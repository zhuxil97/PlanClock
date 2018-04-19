package com.example.mr_do.planclock.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by Mr_Do on 2018/4/18.
 */

public class IOUtil {
    public static void close(InputStream in){
        if(in == null)
            return;
        try {
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void close(OutputStream out){
        if(out == null)
            return;
        try {
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
