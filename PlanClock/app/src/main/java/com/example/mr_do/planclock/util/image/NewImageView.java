package com.example.mr_do.planclock.util.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;

/**
 * Created by Mr_Do on 2018/4/18.
 */

public class NewImageView extends android.support.v7.widget.AppCompatImageView implements IImageLoader.ShowView {


    public NewImageView(Context context) {
        super(context);
    }

    public NewImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NewImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public String getViewTag(int key) {
        return (String) getTag(key);
    }

    @Override
    public void setViewTag(int key, String value) {
        setTag(key, value);
    }

    @Override
    public void bindView(Bitmap bitmap) {
        setImageBitmap(bitmap);
    }
}
