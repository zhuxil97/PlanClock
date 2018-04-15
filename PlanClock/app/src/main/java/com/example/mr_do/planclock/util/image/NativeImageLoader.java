package com.example.mr_do.planclock.util.image;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.LruCache;

import com.example.mr_do.planclock.util.log.LogUtil;
import com.jakewharton.disklrucache.DiskLruCache;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by Mr_Do on 2018/4/4.
 * 自己实现的图片加载类
 */

public class NativeImageLoader implements IImageLoader {

    private interface IImageCache {
        /**
         * 放入缓存
         * @param url 用于取出图片的标志
         * @param bitmap 要放入缓存的图片
         */
        void put(String url, Bitmap bitmap);

        /**
         * 从缓存中取出
         * @param url 图片的标志,这里建议用url进行标志
         */
        Bitmap get(String url, int reqWidth, int reqHeight) throws IOException;
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

    //图片压缩内部类
    private class NativeImageResizer implements IImageResizer {
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
    private class MemoryCache implements IImageCache{
        private Context mContext;
        private LruCache<String, Bitmap> mMemoryCache;
        public MemoryCache(Context context){
            mContext = context;
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
        public void put(String url, Bitmap bitmap) {
            if(mMemoryCache.get(url)==null){
                mMemoryCache.put(url, bitmap);
            }
        }

        @Override
        public Bitmap get(String url, int reqWidth, int reqHeight) {
            return mMemoryCache.get(url);
        }
    }

    //图片缓存实现类,磁盘缓存
    private class DiskCache implements IImageCache{
        private static final long DISK_CACHE_SIZE = 1024*1024*50;
        private static final int DISK_CACHE_INDEX = 0;
        private DiskLruCache mDiskLruCache;
        private Context mContext;
        private File diskCacheDir;
        private IImageResizer mImageResizer;
        private boolean mIsDiskLruCacheCreated;
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

        public void init(String url){

        }

        @Override
        public void put(String url, Bitmap bitmap) {

        }

        public void cancel(){

        }

        @Override
        public Bitmap get(String url, int reqWidth, int reqHeight) throws IOException {
            if(mDiskLruCache == null)
                return null;
            Bitmap bitmap = null;
            String key = hashKeyFormUrl(url);
            DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
            if(snapshot != null){
                FileInputStream fileInputStream = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
                FileDescriptor fileDescriptor = fileInputStream.getFD();
                bitmap = mImageResizer.decodeSampledBitmapFromFileDescriptor(fileDescriptor, reqWidth, reqHeight);
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
            LogUtil.logI("URL: ",url);
            LogUtil.logI("CacheKey", cacheKey);
            return  cacheKey;
        }

        private String byteToHexString(byte[] digest) {
            StringBuilder sb = new StringBuilder();
            for(int i=0;i<digest.length;i++){
                String hex = Integer.toHexString(0xFF & digest[i]);
                if(hex.length() == 1){
                    sb.append('0');
                    sb.append(hex);
                }
            }
            return sb.toString();
        }
    }

    //图片缓存实现类,双缓存
    private class DoubleCache implements IImageCache{
        private DiskLruCache mDiskLruCache;
        private Context mContext;

        public DoubleCache(Context context){
            mContext = context;
            final int maxMemory = (int)(Runtime.getRuntime().maxMemory()/1024);
            final int cacheSize = maxMemory/8;

        }

        @Override
        public void put(String url, Bitmap bitmap) {

        }

        @Override
        public Bitmap get(String url, int reqWidth, int reqHeight) {

            return null;
        }
    }

    private NativeImageLoader(){

    }

    /**
     * 单例模式
     * @return 返回一个ImageLoader单例
     */
    public static NativeImageLoader getImageLoader(){
        return SingletonHolder.imageLoader;
    }

    /**
     * 用于形成线程安全的单例模式的静态内部类
     */
    private static class SingletonHolder{
        private static final NativeImageLoader imageLoader = new NativeImageLoader();
    }

    @Override
    public Bitmap synLoadImage() {
        return null;
    }

    @Override
    public Bitmap asyncLoadImage() {
        return null;
    }
}
