package com.example.mr_do.planclock.util.image;

import android.graphics.Bitmap;

/**
 * Created by Mr_Do on 2018/4/4.
 * 考虑到以后可能要用框架，故现在用接口来屏蔽实现细节
 */

public interface IImageLoader {
    /**
     * 同步加载图片
     * @return 返回图片
     */
    Bitmap synLoadImage();

    /**
     * 异步加载图片
     * @return 返回图片
     */
    Bitmap asyncLoadImage();
}
