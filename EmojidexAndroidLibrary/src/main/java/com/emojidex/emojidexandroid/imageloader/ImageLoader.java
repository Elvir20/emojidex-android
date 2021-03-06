package com.emojidex.emojidexandroid.imageloader;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.LruCache;

import com.emojidex.emojidexandroid.EmojiFormat;
import com.emojidex.emojidexandroid.Emojidex;
import com.emojidex.emojidexandroid.EmojidexFileUtils;
import com.emojidex.emojidexandroid.downloader.DownloadListener;
import com.emojidex.emojidexandroid.downloader.arguments.ImageDownloadArguments;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by kou on 16/12/21.
 */
public class ImageLoader
{
    private static final ImageLoader INSTANCE = new ImageLoader();

    private final Map<String, ImageParamEx> imageParams = new HashMap<String, ImageParamEx>();

    private Context context = null;
    private Resources res = null;
    private BitmapCache bitmapCache = null;

    private final ArrayList<ImageLoadListener> listeners = new ArrayList<ImageLoadListener>();
    private final DownloadListener downloadListener;

    private final TaskManager taskManager = new TaskManager();
    private final ArrayList<ImageLoadArguments> idleArgumentsArray = new ArrayList<ImageLoadArguments>();

    /**
     * Get singleton instance.
     * @return
     */
    public static ImageLoader getInstance()
    {
        return INSTANCE;
    }

    /**
     * Private constructor.
     */
    private ImageLoader()
    {
        downloadListener = new DownloadListener(){
            @Override
            public void onDownloadImages(int handle, EmojiFormat format, String... emojiNames)
            {
                loadStart();
            }
        };
    }

    /**
     * Initialize ImageLoader.
     * @param c
     */
    public void initialize(Context c)
    {
        if( !isInitialized() )
            Emojidex.getInstance().addDownloadListener(downloadListener);

        context = c;
        res = context.getResources();

        final int memClass = ((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
        final int cacheSize = 1024 * 1024 * memClass / 8;

        bitmapCache = new BitmapCache(cacheSize);
    }

    /**
     * Check ImageLoader is initialized.
     * @return
     */
    public boolean isInitialized()
    {
        return context != null;
    }

    /**
     * Clear cache.
     */
    public void clearCache()
    {
        if(bitmapCache != null)
            bitmapCache.evictAll();
    }

    /**
     * Add image load listener.
     * @param listener      Imaeg load listener.
     */
    public void addListener(ImageLoadListener listener)
    {
        if( !listeners.contains(listener) )
            listeners.add(listener);
    }

    /**
     * Remove image load listener.
     * @param listener      Image load listener.
     */
    public void removeListener(ImageLoadListener listener)
    {
        listeners.remove(listener);
    }

    /**
     * Load image.
     * @param name          Emoji name.
     * @param format        Emoji format.
     * @param autoDownload  Auto download image when image is old or not found.
     * @return
     */
    public ImageParam load(String name, EmojiFormat format, boolean autoDownload)
    {
        if(autoDownload)
        {
            Emojidex.getInstance().getEmojiDownloader().downloadImages(
                    new ImageDownloadArguments(name)
                        .setFormat(format)
            );
        }

        ImageParamEx param = isAlreadyLoading(name, format);
        if(param != null && !param.isDummy)
            return param.imageParam;

        // Load image.
        final ImageLoadArguments arguments =
                new ImageLoadArguments(res, name)
                        .setFormat(format);
        if(autoDownload)
        {
            if( !idleArgumentsArray.contains(arguments) )
                idleArgumentsArray.add(arguments);
            loadStart();
        }
        else
            taskManager.regist(arguments);

        // Regist image param.
        if(param == null)
        {
            final String key = createCacheKey(name, format);
            param = createDummyImageParam(format);
            imageParams.put(key, param);
            bitmapCache.put(key + 0, param.imageParam.frames[0].bitmap);
        }
        return param.imageParam;
    }

    /**
     * Reload image.
     * @param result    Image load result.
     */
    void reload(ImageLoadTask.LoadResult result)
    {
        final ImageParam newParam = result.param;
        final ImageLoadArguments arg = result.arguments;

        final String key = createCacheKey(arg.getEmojiName(), arg.getFormat());
        final ImageParamEx param = isAlreadyLoading(arg.getEmojiName(), arg.getFormat());
        for(int i = 0;  i < newParam.frames.length;  ++i)
        {
            if(i >= param.imageParam.frames.length)
            {
                final Bitmap old = bitmapCache.put(
                        key + i,
                        newParam.frames[i].bitmap
                );
                if(old != null)
                    old.recycle();
                continue;
            }

            // Overwrite and replace bitmap.
            final Bitmap oldBitmap = param.imageParam.frames[i].bitmap;
            final Bitmap newBitmap = newParam.frames[i].bitmap;

            final int[] pixels = new int[newBitmap.getWidth() * newBitmap.getHeight()];
            final IntBuffer buffer = IntBuffer.wrap(pixels);

            buffer.position(0);
            newBitmap.copyPixelsToBuffer(buffer);

            buffer.position(0);
            oldBitmap.copyPixelsFromBuffer(buffer);

            newParam.frames[i].bitmap.recycle();
            newParam.frames[i].bitmap = param.imageParam.frames[i].bitmap;
        }

        param.imageParam = newParam;
        param.isDummy = !result.succeeded;
    }

    void finishTask(int handle)
    {
        taskManager.finishTask(handle);
    }

    void notifyToListener(int handle, EmojiFormat format, String emojiName)
    {
        for(ImageLoadListener listener : listeners)
        {
            listener.onLoad(handle, format, emojiName);
        }
    }

    /**
     * Create cache key.
     * @param name
     * @param format
     * @return
     */
    private String createCacheKey(String name, EmojiFormat format)
    {
        return format.getResolution() + "/" + name;
    }

    /**
     * Check already loading.
     * @param name
     * @param format
     * @return
     */
    private ImageParamEx isAlreadyLoading(String name, EmojiFormat format)
    {
        final String key = createCacheKey(name, format);
        final ImageParamEx param = imageParams.get(key);
        if(param == null)
            return null;
        for(int i = 0;  i < param.imageParam.frames.length;  ++i)
            if(bitmapCache.get(key + i) == null)
                return null;
        return param;
    }

    /**
     * Create dummy image parameter.
     * @param format    Emoji format.
     * @return          Dummy image parameter.
     */
    private ImageParamEx createDummyImageParam(EmojiFormat format)
    {
        final ImageParamEx param = new ImageParamEx();
        param.imageParam = new ImageParam();
        final ImageParam.Frame frame = new ImageParam.Frame();
        frame.bitmap = ImageLoadUtils.loadDummyBitmap(res, format);

        param.imageParam.frames = new ImageParam.Frame[1];
        param.imageParam.frames[0] = frame;
        param.isDummy = true;

        return param;
    }

    /**
     * Start load images if file exists.
     */
    private void loadStart()
    {
        final Iterator<ImageLoadArguments> it = idleArgumentsArray.iterator();
        while(it.hasNext())
        {
            final ImageLoadArguments arguments = it.next();
            if(EmojidexFileUtils.existsLocalFile(EmojidexFileUtils.getLocalEmojiUri(arguments.getEmojiName(), arguments.getFormat())))
            {
                taskManager.regist(arguments);
                it.remove();
            }
        }
    }

    /**
     * LruCache for bitmap.
     */
    private class BitmapCache extends LruCache<String, Bitmap>
    {
        public BitmapCache(int maxSize)
        {
            super(maxSize);
        }

        @Override
        protected int sizeOf(String key, Bitmap value)
        {
            return value.getRowBytes() * value.getHeight();
        }
    }

    /**
     * Image parameter ex.
     */
    private class ImageParamEx
    {
        public ImageParam imageParam;
        public boolean isDummy;
    }
}
