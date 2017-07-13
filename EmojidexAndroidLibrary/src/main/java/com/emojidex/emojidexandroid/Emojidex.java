package com.emojidex.emojidexandroid;

import android.content.Context;
import android.util.Log;

import com.emojidex.emojidexandroid.downloader.DownloadListener;
import com.emojidex.emojidexandroid.downloader.EmojiDownloader;
import com.emojidex.emojidexandroidlibrary.R;

import java.util.Collection;
import java.util.List;

/**
 * Created by kou on 14/10/03.
 */
public class Emojidex {
    private static final String TAG = "EmojidexLibrary";

    public static final String SEPARATOR = ":";

    private static final Emojidex INSTANCE = new Emojidex();

    private Context context = null;
    private EmojiManager manager;
    private EmojiFormat defaultFormat;

    /**
     * Get singleton instance.
     * @return  Singleton instance.
     */
    public static Emojidex getInstance() { return INSTANCE; }

    /**
     * Private constructor.
     */
    private Emojidex()
    {
        // nop
    }

    /**
     * Initialize emojidex.
     * @param context   Context of application.
     */
    public void initialize(Context context)
    {
        Log.d(TAG, "Initialize start.");
        if(isInitialized())
        {
            Log.d(TAG, "Already initialized.");
            return;
        }

        this.context = context.getApplicationContext();
        EmojidexFileUtils.initialize(this.context);
        VersionManager.getInstance().optimize(this.context);
        getEmojiDownloader().initialize(this.context);
        manager = new EmojiManager(this.context);
        manager.add(EmojidexFileUtils.getLocalJsonUri());
        defaultFormat = EmojiFormat.toFormat(this.context.getResources().getString(R.string.emoji_format_default));

        Log.d(TAG, "Initialize complete.");
    }

    /**
     * Add download event listener.
     * @param listener  Download event listener.
     */
    public void addDownloadListener(DownloadListener listener)
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        getEmojiDownloader().addListener(listener);
    }

    /**
     * Remove download event listener.
     * @param listener  Download event listener.
     */
    public void removeDownloadListener(DownloadListener listener)
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        getEmojiDownloader().removeListener(listener);
    }

    /**
     * Reload emojidex.
     */
    public void reload()
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        manager.reset();
        manager.add(EmojidexFileUtils.getLocalJsonUri());
    }

    /**
     * Delete all cache files in local storage.
     * @return      true if delete cache succeeded.
     */
    public boolean deleteLocalCache()
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        boolean result = EmojidexFileUtils.deleteFiles(EmojidexFileUtils.getLocalRootUri());
        reload();
        getEmojiDownloader().reload();
        Log.d(TAG, "Delete all cache files in local storage.");
        return result;
    }

    /**
     * Normal text encode to emojidex text.
     * @param text  Normal text.
     * @return      Emojidex text.
     */
    public CharSequence emojify(CharSequence text)
    {
        return emojify(text, true);
    }

    /**
     * Normal text encode to emojidex text.
     * @param text              Normal text.
     * @param autoDeEmojify     If true, execute deEmojify before emojify.
     * @return          Emojidex text.
     */
    public CharSequence emojify(CharSequence text, boolean autoDeEmojify)
    {
        return emojify(text, autoDeEmojify, true);
    }

    /**
     * Normal text encode to emojidex text.
     * @param text              Normal text.
     * @param autoDeEmojify     If true, execute deEmojify before emojify.
     * @param useImage          If true, use phantom-emoji image.
     * @return                  Emojidex text.
     */
    public CharSequence emojify(CharSequence text, boolean autoDeEmojify, boolean useImage)
    {
        return emojify(text, autoDeEmojify, useImage, defaultFormat);
    }

    /**
     * Normal text encode to emojidex text.
     * @param text              Normal text.
     * @param autoDeEmojify     If true, execute deEmojify before emojify.
     * @param useImage          If true, use phantom-emoji image.
     * @param format            Image format.
     * @return                  Emojidex text.
     */
    public CharSequence emojify(CharSequence text, boolean autoDeEmojify, boolean useImage, EmojiFormat format)
    {
        return emojify(text, autoDeEmojify, useImage, format, new EmojiFormat[]{format});
    }

    /**
     * Normal text encode to emojidex text.
     * @param text              Normal text.
     * @param autoDeEmojify     If true, execute deEmojify before emojify.
     * @param useImage          If true, use phantom-emoji image.
     * @param format            Image format.
     * @param downloadFormats   Download formats.
     *                          If emoji is not found, auto download emoji.
     * @return                  Emojidex text.
     */
    public CharSequence emojify(CharSequence text, boolean autoDeEmojify, boolean useImage, EmojiFormat format, EmojiFormat[] downloadFormats)
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return TextConverter.emojify(
                autoDeEmojify ? deEmojify(text) : text,
                useImage,
                format,
                downloadFormats
        );
    }

    /**
     * Emojidex text decode to normal text.
     * @param text  Emojidex text.
     * @return      Normal text.
     */
    public CharSequence deEmojify(CharSequence text)
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return TextConverter.deEmojify(text);
    }

    /**
     * Get initialized flag.
     * @return  true if Emojidex object is initialized.
     */
    public boolean isInitialized()
    {
        return context != null;
    }

    /**
     * Get emoji from emoji name.
     * @param name  Emoji name.
     * @return      Emoji.(If emoji is not found, return null.)
     */
    public Emoji getEmoji(String name)
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return manager.getEmoji(name);
    }

    /**
     * Get emoji from emoji codes.
     * @param codes     Emoji codes.
     * @return          Emoji.(If emoji is not found, return null.)
     */
    public Emoji getEmoji(List<Integer> codes)
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return manager.getEmoji(codes);
    }

    /**
     * Get emoji list from category name.
     * @param category  Category name.
     * @return          Emoji list.(If emoji list is not found, return null.)
     */
    public List<Emoji> getEmojiList(String category)
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return manager.getEmojiList(category);
    }

    /**
     * Ger all emoji list.
     * @return  All emoji list.
     */
    public List<Emoji> getAllEmojiList()
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return manager.getAllEmojiList();
    }

    /**
     * Get category name list.
     * @return  Category name list.
     */
    public Collection<String> getCategoryNames()
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return manager.getCategoryNames();
    }

    /**
     * Get default format for device.
     * @return  Default format for device.
     */
    public EmojiFormat getDefaultFormat()
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();
        
        return defaultFormat;
    }

    /**
     * Get emoji downloader.
     * @return      Emoji downloader.
     */
    public EmojiDownloader getEmojiDownloader()
    {
        return EmojiDownloader.getInstance();
    }
}
