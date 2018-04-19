package com.example.mr_do.planclock;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import com.example.mr_do.planclock.util.IOUtil;
import com.example.mr_do.planclock.util.image.IImageLoader;
import com.example.mr_do.planclock.util.image.ImageRecyclerView;
import com.example.mr_do.planclock.util.image.NativeImageLoader;
import com.example.mr_do.planclock.util.image.NewImageView;
import com.example.mr_do.planclock.util.log.LogUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private ArrayList<String> mBitmapUrls;
    private static final String uri = "http://t2.hddhhn.com/uploads/tu/201804/9999/b8cd152a12.jpg";
    private ImageRecyclerView mRecyclerView;
    private IImageLoader mImageLoader;
    private int mScreenWidth = 0;
    private ImageAdapter mAdapter;

    private int getScreenWidth(){
        DisplayMetrics outMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(outMetrics);
        return outMetrics.widthPixels;
    }

    private static class MyHandler extends Handler{

    }

    private MyHandler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mScreenWidth = getScreenWidth();
        mBitmapUrls = new ArrayList<>();
        mRecyclerView = findViewById(R.id.recycle_view);
        mImageLoader = NativeImageLoader.getImageLoader(getApplicationContext());
        mRecyclerView.setLayoutManager(new GridLayoutManager(this,3));
        mAdapter = new ImageAdapter();
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setOnIdleListener(mListener);
        mHandler = new MyHandler();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                getImageUrls();
            }
        });
        thread.start();

    }

    private void getImageUrls(){
        HttpURLConnection httpURLConnection = null;
        InputStream inputStream = null;
        try {
            String urlUse = URLEncoder.encode("福利","UTF-8");
            String urlString = "http://gank.io/api/data/"+urlUse+"/1000/1";
            URL url = new URL(urlString);
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.connect();
            inputStream = httpURLConnection.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while((line = bufferedReader.readLine())!=null){
                stringBuilder.append(line);
            }
            String pattern = "(http.*?(jpg|jpeg))";
            Pattern r = Pattern.compile(pattern);
            Matcher matcher = r.matcher(stringBuilder.toString());
            while(matcher.find()){
                mBitmapUrls.add(matcher.group());
            }
            if(mBitmapUrls!=null&&mBitmapUrls.size()>0){
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            IOUtil.close(inputStream);
            if(httpURLConnection!=null)
                httpURLConnection.disconnect();
        }
    }

    private class ImageHolder extends RecyclerView.ViewHolder{
        private NewImageView showView;
        public ImageHolder(View itemView) {
            super(itemView);
            showView = itemView.findViewById(R.id.image_item);
            showView.getLayoutParams().width = (mScreenWidth-6)/3;
        }

        public void bindView(int position){
            if(!mBitmapUrls.get(position).equals(showView.getViewTag(R.id.image_loader_uri))){
                showView.setImageDrawable(null);
                showView.setViewTag(R.id.image_loader_uri, mBitmapUrls.get(position));
            }
        }

        public boolean isEmptyView(){
            return showView.getDrawable()==null;
        }

        public String getUrl(){
            return showView.getViewTag(R.id.image_loader_uri);
        }

        public NewImageView getView(){
            return showView;
        }
    }

    private ImageRecyclerView.OnIdleListener mListener = new ImageRecyclerView.OnIdleListener() {
        @Override
        public void onIdle(ImageRecyclerView imageRecyclerView) {
            int childCount = imageRecyclerView.getChildCount();
            for(int i=childCount-1;i>=0;i--){
                ImageHolder holder = (ImageHolder) imageRecyclerView.getChildViewHolder(imageRecyclerView.getChildAt(i));
                if(holder.isEmptyView()){
                    LogUtil.logE("EMPTY",i+" "+holder.getUrl());
                    try {
                        mImageLoader.asyncLoadImageFromWeb(holder.getUrl(),120,150,holder.getView());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    private class ImageAdapter extends RecyclerView.Adapter<ImageHolder>{

        @Override
        public ImageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_image,parent,false);
            return new ImageHolder(view);
        }

        @Override
        public void onBindViewHolder(ImageHolder holder, int position) {
            holder.bindView(position);
        }

        @Override
        public int getItemCount() {
            return mBitmapUrls.size();
        }

        @Override
        public int getItemViewType(int position) {
            return position%48;
        }
    }
}
