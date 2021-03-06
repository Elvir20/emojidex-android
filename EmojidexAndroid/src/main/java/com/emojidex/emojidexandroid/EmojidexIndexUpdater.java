package com.emojidex.emojidexandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.emojidex.emojidexandroid.downloader.DownloadListener;
import com.emojidex.emojidexandroid.downloader.EmojiDownloader;
import com.emojidex.emojidexandroid.downloader.arguments.IndexDownloadArguments;

/**
 * Created by kou on 16/04/01.
 */
public class EmojidexIndexUpdater
{
    static final String TAG = MainActivity.TAG + "::EmojidexIndexUpdater";

    private final Context context;
    private final int limit;
    private final SaveDataManager indexManager;

    private int downloadHandle = EmojiDownloader.HANDLE_NULL;

    public EmojidexIndexUpdater(Context context, int limit)
    {
        this.context = context;
        this.limit = limit;
        indexManager = SaveDataManager.getInstance(this.context, SaveDataManager.Type.Index);
    }

    public boolean startUpdateThread(int pageCount)
    {
        return startUpdateThread(pageCount, false);
    }

    public boolean startUpdateThread(int pageCount, boolean forceFlag)
    {
        if(     downloadHandle != EmojiDownloader.HANDLE_NULL
            ||  (!forceFlag && !checkExecUpdate())              )
        {
            Log.d(TAG, "Skip index update.");
            return false;
        }

        Log.d(TAG, "Start index update.");

        final EmojiDownloader downloader = EmojiDownloader.getInstance();
        downloadHandle = downloader.downloadIndexEmoji(
                new IndexDownloadArguments()
                    .setLimit(limit)
                    .setEndPage(pageCount)
        );

        if(downloadHandle != EmojiDownloader.HANDLE_NULL)
        {
            Emojidex.getInstance().addDownloadListener(new CustomDownloadListener());
            return true;
        }

        return false;
    }

    public int getLimit()
    {
        return limit;
    }

    private boolean checkExecUpdate()
    {
        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        final long lastUpdateTime = pref.getLong(context.getString(R.string.preference_key_last_update_time_index), 0);
        final long currentTime = System.currentTimeMillis();
        final long updateInterval = 24 * 60 * 60 * 1000;
        return (currentTime - lastUpdateTime) > updateInterval;
    }

    private class CustomDownloadListener extends DownloadListener
    {
        @Override
        public void onDownloadJson(int handle, String... emojiNames)
        {
            if(handle == downloadHandle)
            {
                indexManager.clear();

                for(String emoji : emojiNames)
                    indexManager.addLast(emoji);

                indexManager.save();
            }
        }

        @Override
        public void onFinish(int handle, boolean result)
        {
            if(handle == downloadHandle)
            {
                // Save update time.
                // If emoji download failed, execute force update next time.
                final long updateTime = result ? System.currentTimeMillis() : 0;
                final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
                final SharedPreferences.Editor prefEditor = pref.edit();
                prefEditor.putLong(context.getString(R.string.preference_key_last_update_time_index), updateTime);
                prefEditor.commit();

                downloadHandle = EmojiDownloader.HANDLE_NULL;
                Emojidex.getInstance().removeDownloadListener(this);

                Log.d(TAG, "End index update.");
            }
        }

        @Override
        public void onCancelled(int handle, boolean result)
        {
            if(handle == downloadHandle)
            {
                downloadHandle = EmojiDownloader.HANDLE_NULL;
                Emojidex.getInstance().removeDownloadListener(this);

                Log.d(TAG, "Cancel index update.");
            }
        }
    }
}
