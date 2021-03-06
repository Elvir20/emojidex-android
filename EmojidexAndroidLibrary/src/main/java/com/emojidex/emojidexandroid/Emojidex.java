package com.emojidex.emojidexandroid;

import android.content.Context;
import android.util.Log;

import com.emojidex.emojidexandroid.animation.updater.AnimationUpdater;
import com.emojidex.emojidexandroid.downloader.DownloadListener;
import com.emojidex.emojidexandroid.downloader.EmojiDownloader;
import com.emojidex.emojidexandroid.imageloader.ImageLoadListener;
import com.emojidex.emojidexandroid.imageloader.ImageLoader;
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

    private final AnimationUpdaterManager animationUpdaterManager = new AnimationUpdaterManager();

    private Context context = null;
    private EmojiManager emojiManager;
    private EmojiFormat defaultFormat;
    private EmojidexUser user;

    private UpdateInfo updateInfo;
    private MojiCodesManager mojiCodesManager;

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

        updateInfo = new UpdateInfo();
        updateInfo.load(this.context);

        VersionManager.getInstance().initialize(this.context);

        emojiManager = new EmojiManager(this.context);
        emojiManager.add(EmojidexFileUtils.getLocalEmojiJsonUri());

        getEmojiDownloader().initialize(this.context, emojiManager);

        defaultFormat = EmojiFormat.toFormat(this.context.getResources().getString(R.string.emoji_format_default));
        user = new EmojidexUser();

        EmojidexFileUtils.deleteFiles(EmojidexFileUtils.getTemporaryRootUri());

        mojiCodesManager = MojiCodesManager.getInstance();
        mojiCodesManager.initialize(this.context);

        Log.d(TAG, "Initialize complete.");
    }

    /**
     * Set emojidex user.
     * @param username      User name.
     * @param authtoken     Auth token.
     */
    public void setUser(String username, String authtoken)
    {
        user = new EmojidexUser(username, authtoken);
    }

    /**
     * Get emojidex user.
     * @return      Emojidex user.
     */
    public EmojidexUser getUser()
    {
        return user;
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

    public void addImageLoadListener(ImageLoadListener listener)
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        ImageLoader.getInstance().addListener(listener);
    }

    public void removeImageLoadListener(ImageLoadListener listener)
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        ImageLoader.getInstance().removeListener(listener);
    }

    /**
     * Update emojidex database.
     * @return      Download handle array.
     */
    public Collection<Integer> update()
    {
        return UpdateManager.update();
    }

    /**
     * Reload emojidex.
     */
    public void reload()
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        emojiManager.reset();
        emojiManager.add(EmojidexFileUtils.getLocalEmojiJsonUri());

        mojiCodesManager.reload();
    }

    /**
     * Delete all cache files in local storage.
     * @return      true if delete cache succeeded.
     */
    public boolean deleteLocalCache()
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        // Delete cache directory.
        boolean result = EmojidexFileUtils.deleteFiles(EmojidexFileUtils.getLocalRootUri());

        // Reload emojidex database.
        reload();
        ImageLoader.getInstance().clearCache();
        updateInfo.reset();

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
        return emojify(text, null);
    }

    /**
     * Normal text encode to emojidex text.
     * @param text              Normal text.
     * @param format            Image format.
     *                          If value is null, use default format.
     * @return                  Emojidex text.
     */
    public CharSequence emojify(CharSequence text, EmojiFormat format)
    {
        return emojify(text, format, true);
    }

    /**
     * Normal text encode to emojidex text.
     * @param text              Normal text.
     * @param format            Image format.
     *                          If value is null, use default format.
     * @param autoDownload      Auto download flag.
     *                          If true, auto download emoji when find unknown emoji.
     * @return                  Emojidex text.
     */
    public CharSequence emojify(CharSequence text, EmojiFormat format, boolean autoDownload)
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return TextConverter.emojify(
                text,
                (format != null) ? format : getDefaultFormat(),
                autoDownload
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
     * Replace utf string to emojidex code.
     * @param text      Source text.
     * @return          Result text.
     */
    public CharSequence codify(CharSequence text)
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return TextConverter.codify(text);
    }

    /**
     * Replace emojidex code to utf string.
     * @param text      Source text.
     * @return          Result text.
     */
    public CharSequence mojify(CharSequence text)
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return TextConverter.mojify(text);
    }

    /**
     * Add animation updater.
     * @param updater   Animation updater.
     */
    public void addAnimationUpdater(AnimationUpdater updater)
    {
        animationUpdaterManager.regist(updater);
    }

    /**
     * Remove animation updater.
     * @param updater   Animation updater.
     */
    public void removeAnimationUpdater(AnimationUpdater updater)
    {
        animationUpdaterManager.unregist(updater);
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

        return emojiManager.getEmoji(name);
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

        return emojiManager.getEmoji(codes);
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

        return emojiManager.getEmojiList(category);
    }

    /**
     * Ger all emoji list.
     * @return  All emoji list.
     */
    public List<Emoji> getAllEmojiList()
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return emojiManager.getAllEmojiList();
    }

    /**
     * Get UTF emoji list.
     * @return      UTF emoji list.
     */
    public List<Emoji> getUTFEmojiList()
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return emojiManager.getUTFEmojiList();
    }

    /**
     * Get extended emoji list.
     * @return      Extended emoji list.
     */
    public List<Emoji> getExtendedEmojiList()
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return emojiManager.getExtendedEmojiList();
    }

    /**
     * Get other emoji list.
     * @return      Other emoji list.
     */
    public List<Emoji> getOtherEmojiList()
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return emojiManager.getOtherEmojiList();
    }

    /**
     * Get category name list.
     * @return  Category name list.
     */
    public Collection<String> getCategoryNames()
    {
        if( !isInitialized() )
            throw new EmojidexIsNotInitializedException();

        return emojiManager.getCategoryNames();
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

    /**
     * Get update information.
     * @return      Update information.
     */
    public UpdateInfo getUpdateInfo()
    {
        return updateInfo;
    }

    /**
     * Get moji codes.
     * @return      Moji codes.
     */
    public MojiCodes getMojiCodes()
    {
        return mojiCodesManager.getMojiCodes();
    }
}
