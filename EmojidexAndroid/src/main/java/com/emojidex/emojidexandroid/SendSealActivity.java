package com.emojidex.emojidexandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by kou on 14/05/19.
 */
public class SendSealActivity extends Activity {
    static final String TAG = MainActivity.TAG + "::SendSealActivity";

    public static final String EXTRA_EMOJI_NAME = "EmojiName";
    public static final String EXTRA_PACKAGE_NAME = "PackageName";

    private final Intent sendIntent = new Intent(Intent.ACTION_SEND);
    private EmojiDownloader downloader = null;
    private ProgressDialog dialog = null;

    private boolean isCanceled = false;
    private Uri sealUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize intent.
        final String targetPackageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        sendIntent.setType("image/png");
        sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        sendIntent.setPackage(targetPackageName);

        // Error check.
        final PackageManager pm = getPackageManager();
        final List<ResolveInfo> resolveInfos = pm.queryIntentActivities(sendIntent, PackageManager.MATCH_DEFAULT_ONLY);

        if(resolveInfos.isEmpty())
        {
            Log.d(TAG, "Intent send failed.(Target package name = " + sendIntent.getPackage() + ")");

            // Create error dialog.
            // TODO Set error detail.
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Error");
            builder.setMessage(R.string.message_unsupported_seal);
            builder.setPositiveButton(R.string.close, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    // Close activity if closed error dialog.
                    SendSealActivity.this.finish();
                }
            });

            // Show dialog.
            builder.create().show();

            return;
        }

        // Send seal.
        sendSeal();
    }

    /**
     * Send seal.
     */
    private void sendSeal()
    {
        final String emojiName = getIntent().getStringExtra(EXTRA_EMOJI_NAME);

        // Download seal.
        final String url = "https://www.emojidex.com/api/v1/emoji/" + emojiName + "?detailed=true";
        final EmojiFormat[] formats = {EmojiFormat.toFormat(getString(R.string.emoji_format_seal))};
        downloader = new EmojiDownloader(this);
        downloader.setListener(new CustomDownloadListener());
        downloader.add(url, formats, PathUtils.getRemoteRootPathDefault() + "/emoji");

        // Show downloading dialog.
        dialog = new ProgressDialog(this);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setTitle(R.string.send_seal_dialog_title);
        dialog.setMessage(getString(R.string.send_seal_dialog_message));

        dialog.setButton(
                DialogInterface.BUTTON_NEGATIVE,
                getString(R.string.send_seal_dialog_cancel),
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
                downloader.cancel();
                isCanceled = true;
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if(isCanceled)
                {
                    Log.d(TAG, "Intent send canceled.(Target package name = " + sendIntent.getPackage() + ")");
                }
                else if(createTemporaryFile(emojiName))
                {
                    sendIntent();
                }
                else
                {
                    final AlertDialog.Builder alertDialog = new AlertDialog.Builder(SendSealActivity.this);
                    alertDialog.setMessage(R.string.send_seal_not_found);
                    alertDialog.setPositiveButton(R.string.send_seal_not_found_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            sendIntent();
                            finish();
                        }
                    });
                    alertDialog.show();

                    return;
                }
                finish();
            }
        });

        dialog.show();
    }

    /**
     * Create temporary file.
     * @param emojiName     Emoji name.
     * @return              true if seal exists.
     */
    private boolean createTemporaryFile(String emojiName)
    {
        final Emoji emoji = Emojidex.getInstance().getEmoji(emojiName);
        final File temporaryFile = new File(getExternalCacheDir(), "tmp" + System.currentTimeMillis() + ".png");
        boolean result = true;

        // If file not found, use default format.
        File file = new File(emoji.getImageFilePath(EmojiFormat.toFormat(getString(R.string.emoji_format_seal))));
        if( !file.exists() )
        {
            file = new File(emoji.getImageFilePath(EmojiFormat.toFormat(getString(R.string.emoji_format_default))));
            result = false;
        }

        // Create temporary file.
        try
        {
            // Load bitmap.
            final FileInputStream is = new FileInputStream(file);
            final Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();

            // Change background color to white.
            final Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
            newBitmap.eraseColor(Color.WHITE);
            final Canvas canvas = new Canvas(newBitmap);
            canvas.drawBitmap(bitmap, 0, 0, null);

            // Save temporary file.
            final FileOutputStream os = new FileOutputStream(temporaryFile);
            newBitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }

        sealUri = Uri.fromFile(temporaryFile);
        return result;
    }

    /**
     * Send intent.
     */
    private void sendIntent() {
        sendIntent.putExtra(Intent.EXTRA_STREAM, sealUri);
        startActivity(sendIntent);
        Log.d(TAG, "Intent send succeeded.(Target package name = " + sendIntent.getPackage() + ")");
    }


    /**
     * Custom download listener.
     */
    private class CustomDownloadListener extends DownloadListener
    {
        @Override
        public void onPostOneJsonDownload(String source, String destination) {
            super.onPostOneJsonDownload(source, destination);

            // Replace whitespace to underbar.
            final File file = new File(destination);
            final ArrayList<JsonParam> emojies = JsonParam.readFromFile(file);
            for(JsonParam emoji : emojies)
            {
                emoji.name = emoji.name.replaceAll(" ", "_");
            }
            JsonParam.writeToFile(file, emojies);
        }

        @Override
        public void onFinish() {
            super.onFinish();

            if(dialog != null)
            {
                dialog.dismiss();
                dialog = null;
            }
        }
    }
}
