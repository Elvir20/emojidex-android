package com.emojidex.emojidexandroid;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;

import com.emojidex.emojidexandroid.downloader.DownloadListener;
import com.emojidex.emojidexandroid.downloader.EmojiDownloader;
import com.emojidex.emojidexandroid.downloader.arguments.ImageDownloadArguments;

/**
 * Created by kou on 15/04/20.
 */
public class SealDownloader {
    private final Activity parentActivity;

    private ProgressDialog dialog = null;
    private boolean canceled = false;
    private DialogInterface.OnDismissListener onDismissListener = null;

    private int downloadHandle;

    /**
     * Construct object.
     * @param activity  Parent activity.
     */
    public SealDownloader(Activity activity)
    {
        parentActivity = activity;
    }

    /**
     * Download seal for send.
     * @param name Emoji name.
     */
    public void downloadForSend(String name)
    {
        download(name, R.string.send_seal_dialog_title);
    }

    /**
     * Download seal for save.
     * @param name Emoji name.
     */
    public void downloadForSave(String name)
    {
        download(name, R.string.save_seal_dialog_title);
    }

    /**
     * Download seal.
     * @param name  Emoji name.
     * @param titleId Dialog title.
     */
    public void download(String name, int titleId)
    {
        canceled = false;

        // Download seal.
        final EmojiDownloader downloader = EmojiDownloader.getInstance();
        downloadHandle = downloader.downloadImages(
                true,
                new ImageDownloadArguments(name)
                    .setFormat(EmojiFormat.toFormat(parentActivity.getString(R.string.emoji_format_seal)))
        )[0];

        // If already downloaded.
        if(downloadHandle == EmojiDownloader.HANDLE_NULL)
        {
            onDismissListener.onDismiss(null);
            return;
        }

        Emojidex.getInstance().addDownloadListener(new CustomDownloadListener());

        // Show progress dialog.
        dialog = new ProgressDialog(parentActivity);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setTitle(titleId);
        dialog.setMessage(parentActivity.getString(R.string.send_seal_dialog_message));

        dialog.setButton(
                DialogInterface.BUTTON_NEGATIVE,
                parentActivity.getString(R.string.send_seal_dialog_cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }
        );

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                downloader.cancelDownload(downloadHandle);
                canceled = true;
            }
        });

        dialog.setOnDismissListener(onDismissListener);

        dialog.show();
    }

    /**
     * Set listener called when downloader finish.
     * @param listener  OnDismissListener.
     */
    public void setOnDismissListener(DialogInterface.OnDismissListener listener)
    {
        onDismissListener = listener;
    }

    /**
     * Check download is canceled.
     * @return  true when download is canceled.
     */
    public boolean isCanceled()
    {
        return canceled;
    }


    /**
     * Custom download listener.
     */
    private class CustomDownloadListener extends DownloadListener
    {
        @Override
        public void onFinish(int handle, boolean result)
        {
            if(handle == downloadHandle)
            {
                if(dialog != null)
                {
                    dialog.dismiss();
                    dialog = null;
                }

                Emojidex.getInstance().removeDownloadListener(this);
            }
        }

        @Override
        public void onCancelled(int handle, boolean result)
        {
            if(handle == downloadHandle)
            {
                Emojidex.getInstance().removeDownloadListener(this);
            }
        }
    }
}
