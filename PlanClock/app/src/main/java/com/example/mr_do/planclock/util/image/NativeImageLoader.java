package com.example.mr_do.planclock.util.image;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.support.annotation.NonNull;
import android.util.LruCache;
import com.example.mr_do.planclock.R;
import com.example.mr_do.planclock.util.IOUtil;
import com.example.mr_do.planclock.util.log.LogUtil;
import com.jakewharton.disklrucache.DiskLruCache;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Mr_Do on 2018/4/4.
 * 自己实现的图片加载类
 */

public class NativeImageLoader implements IImageLoader {

    private interface IImageCache {
        /**
         * 用来存放缓存元素的类
         * 因为缓存元素有可能不是Bitmap
         */
        class CacheElement{
            private Bitmap bitmap;
            private InputStream inputStream;

            public Bitmap getBitmap() {
                return bitmap;
            }

            public void setBitmap(Bitmap bitmap) {
                this.bitmap = bitmap;
            }

            public InputStream getInputStream() {
                return inputStream;
            }

            public void setInputStream(InputStream inputStream) {
                this.inputStream = inputStream;
            }
        }

        /**
         * 放入缓存
         * @param url 用于取出图片的标志
         * @param cacheElement 要放入缓存元素
         */
        void put(String url, CacheElement cacheElement);

        /**
         * 从缓存中取出
         * @param url 图片的标志,这里建议用url进行标志
         */
        Bitmap get(String url, int reqWidth, int reqHeight);
    }

    private interface IImageResizer {

        /**
         * 压缩Resource中的图片,
         * 如果需要的宽度或者高度为0,则说明不压缩
         * @param res 资源文件
         * @param resId 图片id
         * @param reqWidth 需要的宽度
         * @param reqHeight 需要的高度
         * @return 返回压缩后的图片
         */
        Bitmap decodeSampledBitmapFromResource(Resources res,
                                               int resId,
                                               int reqWidth,
                                               int reqHeight);

        /**
         * 压缩文件中的图片,
         * 如果需要的宽度或者高度为0,则说明不压缩
         * @param fd 文件
         * @param reqWidth 需要的宽度
         * @param reqHeight 需要的高度
         * @return
         */
        Bitmap decodeSampledBitmapFromFileDescriptor(FileDescriptor fd,
                                                     int reqWidth,
                                                     int reqHeight);
    }

    private interface ILoadStrategy{
        void loadToView(String uri, int reqWidth, int reqHeight, ShowView showView);
    }

    //图片压缩内部类
    private static class NativeImageResizer implements IImageResizer {

        @Override
        public Bitmap decodeSampledBitmapFromResource(Resources res, int resId, int reqWidth, int reqHeight) {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(res, resId, options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeResource(res, resId, options);
        }

        @Override
        public Bitmap decodeSampledBitmapFromFileDescriptor(FileDescriptor fd, int reqWidth, int reqHeight) {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFileDescriptor(fd, null, options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFileDescriptor(fd, null, options);
        }

        private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight){
            //如果需要的宽度或者高度为0,则说明不压缩
            if(reqWidth == 0 || reqHeight == 0){
                return 1;
            }

            final int height = options.outHeight;
            final int width = options.outWidth;

            //计算inSampleSize的策略
            int inSampleSize = 1;
            if(height > reqHeight || width > reqWidth){
                final int halfHeight = height/2;
                final int halfWidth = width/2;
                while((halfHeight/inSampleSize)>reqHeight && (halfWidth/inSampleSize)>reqWidth){
                    inSampleSize *= 2;
                }
            }
            return inSampleSize;
        }
    }

    //图片缓存实现类,内存缓存
    private static class MemoryCache implements IImageCache{
        private LruCache<String, Bitmap> mMemoryCache;
        public MemoryCache(){
            final int maxMemory = (int)(Runtime.getRuntime().maxMemory()/1024);
            final int cacheSize = maxMemory/8;
            mMemoryCache = new LruCache<String, Bitmap>(cacheSize){
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getRowBytes()*value.getHeight()/1024;
                }
            };
        }
        @Override
        public void put(String url, CacheElement cacheElement) {
            if(mMemoryCache.get(url)==null&&url!=null&&cacheElement.getBitmap()!=null){
                mMemoryCache.put(url, cacheElement.getBitmap());
            }
        }

        @Override
        public Bitmap get(String url, int reqWidth, int reqHeight) {
            return mMemoryCache.get(url);
        }
    }

    //图片缓存实现类,磁盘缓存
    private static class DiskCache implements IImageCache{
        private static final long DISK_CACHE_SIZE = 1024*1024*50;
        private static final int DISK_CACHE_INDEX = 0;
        private DiskLruCache mDiskLruCache;
        private Context mContext;
        private File diskCacheDir;
        private IImageResizer mImageResizer;
        private boolean mIsDiskLruCacheCreated = false;

        public boolean ismIsDiskLruCacheCreated(){
            return mIsDiskLruCacheCreated;
        }

        public DiskCache(Context context){
            mContext = context;
            mImageResizer = new NativeImageResizer();
            diskCacheDir = getDiskCacheDir(mContext,"bitmap");
            if(!diskCacheDir.exists()){
                diskCacheDir.mkdirs();
            }
            if(getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE){
                try {
                    mDiskLruCache = DiskLruCache.open(diskCacheDir,1,1,DISK_CACHE_SIZE);
                    mIsDiskLruCacheCreated = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void put(String url, CacheElement cacheElement) {
            if(Looper.myLooper() == Looper.getMainLooper())
                throw new RuntimeException("can not visit disk from UI Thread");
            String key = hashKeyFormUrl(url);
            OutputStream outputStream = null;
            DiskLruCache.Editor editor = null;
            try {
                editor = mDiskLruCache.edit(key);
                if(editor != null){
                    outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
                    if( writeToStream(cacheElement.getInputStream(), outputStream)){
                        editor.commit();
                    }else{
                        editor.abort();
                    }
                    mDiskLruCache.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                IOUtil.close(outputStream);
            }
        }

        private boolean writeToStream(InputStream inputStream,OutputStream outputStream) throws IOException {
            try {
                int b;
                while ((b=inputStream.read())!=-1){
                    outputStream.write(b);
                }
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }finally {
                IOUtil.close(inputStream);
                IOUtil.close(outputStream);
            }
        }


        @Override
        public Bitmap get(String url, int reqWidth, int reqHeight) {
            if(Looper.myLooper() == Looper.getMainLooper())
                throw new RuntimeException("can not visit disk from UI Thread");
            if(mDiskLruCache == null)
                return null;
            Bitmap bitmap = null;
            String key = hashKeyFormUrl(url);
            try {
                DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
                if (snapshot != null) {
                    FileInputStream fileInputStream = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
                    FileDescriptor fileDescriptor = fileInputStream.getFD();
                    bitmap = mImageResizer.decodeSampledBitmapFromFileDescriptor(fileDescriptor, reqWidth, reqHeight);
                }
            }catch (IOException e){
                e.printStackTrace();
            }
            return bitmap;
        }

        private File getDiskCacheDir(Context context, String fileName){
            boolean externalStorageAvailable = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
            final String cachePath;

            if(externalStorageAvailable){
                cachePath = context.getExternalCacheDir().getPath();
            }else{
                cachePath = context.getCacheDir().getPath();
            }
            return new File(cachePath + File.separator+fileName);
        }

        private long getUsableSpace(File path){
            if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.GINGERBREAD){
                return path.getUsableSpace();
            }
            final StatFs stats = new StatFs(path.getPath());
            return (long) stats.getBlockSize() *(long) stats.getAvailableBlocks();
        }

        private String hashKeyFormUrl(String url){
            String cacheKey;
            try {
                final MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                messageDigest.update(url.getBytes());
                cacheKey = byteToHexString(messageDigest.digest());
            } catch (NoSuchAlgorithmException e) {
                cacheKey = String.valueOf(url.hashCode());
            }
            return  cacheKey;
        }

        private String byteToHexString(byte[] digest) {
            StringBuilder sb = new StringBuilder();
            for(int i=0;i<digest.length;i++){
                String hex = Integer.toHexString(0xFF & digest[i]);
                if(hex.length() == 1){
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        }
    }

    //双缓存加载策略
    private static class DoubleCacheStrategy implements ILoadStrategy{
        private IImageResizer iImageResizer = new NativeImageResizer();
        private IImageCache mDiskCache;
        private IImageCache mMemoryCache;
        private Context mContext;
        private static final String MEMORY_TAG = "Memory_ImageLoader";
        private static final String DISK_TAG = "Disk_ImageLoader";
        private static final String WEB_TAG = "Web_ImageLoader";
        private static final String LOAD_TAG = "_ImageLoader";
        public static final int MESSAGE_POST_RESULT = 1;
        private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
        private static final int CORE_POOL_SIZE = CPU_COUNT+1;
        private static final int MAXIMUM_POOL_SIZE = CPU_COUNT*2+1;
        private static final long KEEP_ALIVE = 10L;
        private static final int IO_BUFFER_SIZE = 8*1024;
        private static final MyBlockingDeque MY_BLOCKING_DEQUE = new MyBlockingDeque();
        private static final ThreadFactory sThreadFactory = new ThreadFactory() {
            private final AtomicInteger mCount = new AtomicInteger(1);
            @Override
            public Thread newThread(@NonNull Runnable runnable) {
                return new Thread(runnable, "ImageLoader#"+mCount.getAndIncrement());
            }
        };
        public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS,
                MY_BLOCKING_DEQUE, sThreadFactory
        );

        //变成可以通过uri来判定是否相等的runnable
        private static class MyRunnable implements Runnable{
            private String uri = null;

            public MyRunnable(String uri){
                this.uri = uri;
            }

            @Override
            public void run() {

            }

            @Override
            public boolean equals(Object obj) {
                if(obj instanceof MyRunnable)
                    return ((MyRunnable)obj).uri==uri;
                else
                    return false;
            }
        }

        //变成了一个栈
        private static class MyBlockingDeque extends LinkedBlockingDeque<Runnable>{
            @Override
            public boolean offer(Runnable runnable) {
                if(contains(runnable)){
                    return true;
                }
                return super.offerFirst(runnable);
            }
        }

        private class LoadResult{
            public LoadResult(ShowView view, Bitmap bitmap, String tag) {
                this.view = view;
                this.bitmap = bitmap;
                this.tag = tag;
            }
            public ShowView view;
            public Bitmap bitmap;
            public String tag;
        }
        private Handler mMainHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                LoadResult loadResult = (LoadResult) msg.obj;
                String uri = loadResult.tag;
                if(uri.equals(loadResult.view.getViewTag(R.id.image_loader_uri))){
                    loadResult.view.bindView(loadResult.bitmap);
                }
                IImageCache.CacheElement cacheElement = new IImageCache.CacheElement();
                cacheElement.setBitmap(loadResult.bitmap);
                mMemoryCache.put(uri,cacheElement);
            }
        };

        public DoubleCacheStrategy(Context context){
            mContext = context;
            mDiskCache = new DiskCache(mContext);
            mMemoryCache = new MemoryCache();
        }
        @Override
        public void loadToView(final String uri, final int reqWidth, final int reqHeight, final ShowView showView) {
            Bitmap bitmap = mMemoryCache.get(uri,reqWidth,reqHeight);
            if(bitmap!=null) {
                if(uri.equals(showView.getViewTag(R.id.image_loader_uri))){
                    showView.bindView(bitmap);
                }
                return;
            }
            Runnable loadBitmapTask = new MyRunnable(uri) {
                @Override
                public void run() {
                    Bitmap bitmap = null;
                    LogUtil.logE("COMPARE_URI_",uri);
                    try {
                        bitmap = mDiskCache.get(uri, reqWidth, reqHeight);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if(bitmap != null) {
                        LoadResult result = new LoadResult(showView, bitmap,uri);
                        mMainHandler.obtainMessage(MESSAGE_POST_RESULT, result).sendToTarget();
                        return;
                    }
                    HttpURLConnection httpURLConnection = null;
                    InputStream is = null;
                    LoadResult result = null;
                    BufferedInputStream in = null;
                    try {
                        final URL url = new URL(uri);
                        httpURLConnection = (HttpURLConnection)url.openConnection();
                        is = httpURLConnection.getInputStream();
                        in = new BufferedInputStream(is, IO_BUFFER_SIZE);
                        IImageCache.CacheElement cacheElement = new IImageCache.CacheElement();
                        cacheElement.setInputStream(in);
                        mDiskCache.put(uri, cacheElement);
                        bitmap = mDiskCache.get(uri, reqWidth, reqHeight);
                        cacheElement.setBitmap(bitmap);
                        result = new LoadResult(showView, bitmap, uri);
                        mMainHandler.obtainMessage(MESSAGE_POST_RESULT, result).sendToTarget();
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if(httpURLConnection != null)
                            httpURLConnection.disconnect();
                        IOUtil.close(in);
                    }
                }
            };
            THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
            LogUtil.logE("SOURCE_URI_",uri);
        }
    }
    private static Context sContext;
    private static ILoadStrategy sStrategy;
    private NativeImageLoader(){}

    /**
     * 单例模式
     * @return 返回一个ImageLoader单例
     */
    public static NativeImageLoader getImageLoader(@NonNull Context context){
        sContext = context;
        sStrategy = new DoubleCacheStrategy(sContext);
        return SingletonHolder.imageLoader;
    }

    /**
     * 用于形成线程安全的单例模式的静态内部类
     */
    private static class SingletonHolder{
        private static final NativeImageLoader imageLoader = new NativeImageLoader();
    }

    @Override
    public void asyncLoadImageFromWeb(final String uri, final int reqWidth, final int reqHeight, final ShowView showView) throws Exception {
        sStrategy.loadToView(uri,reqWidth,reqHeight,showView);
    }

    @Override
    public void asyncLoadImageFromDisk(String uri, int reqWidth, int reqHeight, ShowView showView) {

    }
}
