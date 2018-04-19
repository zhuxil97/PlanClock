package com.example.mr_do.planclock.util.image;

import android.graphics.Bitmap;
import android.widget.ImageView;

/**
 * Created by Mr_Do on 2018/4/4.
 * 考虑到以后可能要用框架，故现在用接口来屏蔽实现细节
 */

public interface IImageLoader {
    interface ShowView{
        String getViewTag(int key);
        void setViewTag(int key, String value);
        void bindView(Bitmap bitmap);

    }
    /**
     * 异步从网络加载图片到view上
     * @param uri 图片地址
     * @param reqWidth 需要的宽度
     * @param reqHeight 需要的高度
     * @param showView 要绑定的view
     */
    void asyncLoadImageFromWeb(String uri, int reqWidth, int reqHeight, ShowView showView) throws Exception;


    /**
     * 异步从磁盘加载图片到view上
     * @param uri 图片地址
     * @param reqWidth 需要的宽度
     * @param reqHeight 需要的高度
     * @param showView 要绑定的view
     */
    void asyncLoadImageFromDisk(String uri, int reqWidth, int reqHeight, ShowView showView);
}
