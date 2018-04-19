package com.example.mr_do.planclock.util.image;

import android.content.Context;
import android.graphics.Canvas;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;

/**
 * Created by Mr_Do on 2018/4/18.
 */

public class ImageRecyclerView extends RecyclerView {
    private boolean isLoadable = true;
    private OnIdleListener mOnIdleListener;
    public interface OnIdleListener{
        void onIdle(ImageRecyclerView imageRecyclerView);
    }
    public void setOnIdleListener(OnIdleListener listener){
        mOnIdleListener = listener;
    }

    public boolean isLoadable() {
        return isLoadable;
    }

    public ImageRecyclerView(Context context) {
        super(context);
    }

    public ImageRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);
        mOnIdleListener.onIdle(this);
    }

    @Override
    public void onScrollStateChanged(int state) {
        super.onScrollStateChanged(state);
        if(state == SCROLL_STATE_IDLE){
            isLoadable = true;
            if(mOnIdleListener!=null){
                mOnIdleListener.onIdle(this);
            }
        }else {
            isLoadable = false;
        }
    }


}
