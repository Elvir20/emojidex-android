package com.emojidex.emojidexandroid;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;


public class SearchActivity extends Activity {
    private SaveDataManager searchManager;
    private Handler addButtonHandler;
    private Handler invalidateHandler;

    private EditText editText;
    private GridLayout resultLayout;

    private String category;
    private ProgressDialog loadingDialog = null;
    private EmojiDownloader downloader = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.window_search);

        switchIME();
        initializeContentView();

        // Initialize fields.
        searchManager = new SaveDataManager(this, SaveDataManager.Type.Search);
        searchManager.load();

        addButtonHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                addButton((String)msg.obj);
            }
        };

        invalidateHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                ((View)msg.obj).invalidate();
            }
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Have no menu.
        return false;
    }

    @Override
    protected void onDestroy() {
        searchManager.save();
        resetIME();
        super.onDestroy();
    }

    /**
     * Initialize content view.
     */
    private void initializeContentView()
    {
        // Edit text.
        editText = (EditText)findViewById(R.id.search_edit_text);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if(actionId == EditorInfo.IME_ACTION_SEARCH)
                {
                    searchEmoji();
                    return true;
                }

                return false;
            }
        });

        // Show result space.
        resultLayout = (GridLayout)findViewById(R.id.search_result_layout);

        // Search button.
        findViewById(R.id.search_action).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchEmoji();
            }
        });

        // Close button.
        findViewById(R.id.search_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Category spinner.
        final Spinner categorySpinner = (Spinner)findViewById(R.id.search_category_spinner);
        final HashMap<String, String> categoryMap = new HashMap<String, String>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        adapter.add(getString(R.string.ime_category_text_all));

        final CategoryManager categoryManager = CategoryManager.getInstance();
        final int categoryCount = categoryManager.getCategoryCount();
        for(int i = 0;  i < categoryCount;  ++i)
        {
            final String id = categoryManager.getCategoryId(i);
            final String text = categoryManager.getCategoryText(i);
            categoryMap.put(text, id);
            adapter.add(text);
        }
        categorySpinner.setAdapter(adapter);
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                final String selected = (String) categorySpinner.getSelectedItem();
                category = categoryMap.get(selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                category = null;
            }
        });
        categorySpinner.setBackground(getResources().getDrawable(R.drawable.ime_search_spinner_background));
    }

    /**
     * Switch input method.
     */
    private void switchIME()
    {
        final InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);

        boolean hasDefaultIME = false;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String idNothing = getString(R.string.preference_entryvalue_default_keyboard_nothing);
        final String defaultIME = prefs.getString(getString(R.string.preference_key_default_keyboard), idNothing);

        if( !defaultIME.equals(idNothing) )
        {
            for(InputMethodInfo info : inputMethodManager.getEnabledInputMethodList())
            {
                if(info.getId().equals(defaultIME))
                    hasDefaultIME = true;
            }
        }

        if(hasDefaultIME)
            EmojidexIME.currentInstance.switchInputMethod(defaultIME);
        else
            inputMethodManager.showInputMethodPicker();
    }

    /**
     * Reset input method.
     */
    private void resetIME()
    {
        final InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.showInputMethodPicker();
    }

    /**
     * Search emoji.
     */
    private void searchEmoji()
    {
        // Skip if already search.
        if(loadingDialog != null)
            return;

        // Skip if search text is empty.
        final String searchText = editText.getText().toString();
        if(searchText.isEmpty())
            return;

        // Clear result.
        resultLayout.removeAllViews();

        // Search emoji.
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                final String url = "https://www.emojidex.com/api/v1/search/emoji?detailed=true&code_cont=" + searchText + (category == null ? "" : "&categories[]=" + category);

                final LinkedHashSet<EmojiFormat> formats = new LinkedHashSet<EmojiFormat>();
                formats.add(EmojiFormat.toFormat(getString(R.string.emoji_format_default)));
                formats.add(EmojiFormat.toFormat(getString(R.string.emoji_format_key)));
                formats.add(EmojiFormat.toFormat(getString(R.string.emoji_format_seal)));

                downloader = new EmojiDownloader(SearchActivity.this);
                downloader.setListener(new CustomDownloadListener());

                downloader.add(
                        url,
                        formats.toArray(new EmojiFormat[formats.size()]),
                        PathUtils.getRemoteRootPathDefault() + "/emoji"
                );
            }
        }, 1000);

        // Create loading dialog.
        createLoadingDialog();
    }

    /**
     * Create loading dialog.
     */
    private void createLoadingDialog()
    {
        // Create dialog.
        loadingDialog = new ProgressDialog(SearchActivity.this);
        loadingDialog.setCanceledOnTouchOutside(false);
        loadingDialog.setTitle(getString(R.string.search_dialog_title));
        loadingDialog.setMessage(getString(R.string.search_dialog_message));

        // Add cancel button.
        loadingDialog.setButton(
                DialogInterface.BUTTON_NEGATIVE,
                getString(R.string.search_dialog_cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }
        );
        loadingDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                downloader.cancel();
                downloader = null;
                loadingDialog = null;
            }
        });

        // Set dim amount settings.
        final Window window = loadingDialog.getWindow();
        window.setDimAmount(0.6f);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        // Show dialog.
        loadingDialog.show();
    }

    /**
     * Add emoji button to result area.
     * @param emojiName     Emoji name.
     */
    private void addButton(final String emojiName)
    {
        final Emojidex emojidex = Emojidex.getInstance();
        final EmojiFormat emojiFormat = EmojiFormat.toFormat(getString(R.string.emoji_format_key));
        final ImageView button = new ImageView(this);
        button.setScaleType(ImageView.ScaleType.CENTER);

        // Set drawable.
        final BitmapDrawable drawable = emojidex.getEmoji(emojiName).getDrawable(emojiFormat);
        final float drawableSize = getResources().getDimension(R.dimen.ime_search_emoji_button_icon_size);
        drawable.setTargetDensity((int) (drawable.getBitmap().getDensity() * drawableSize / drawable.getIntrinsicWidth()));
        button.setImageDrawable(drawable);

        // Set size.
        final float buttonSize = getResources().getDimension(R.dimen.ime_search_emoji_button_size);
        final ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams((int)buttonSize, (int)buttonSize);
        button.setLayoutParams(lp);

        // Set click event.
        final Drawable background = getResources().getDrawable(R.drawable.ime_search_emoji_button_background_checked);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(v.getBackground() == null)
                {
                    v.setBackground(background);
                    searchManager.addFirst(emojiName);
                }
                else
                {
                    v.setBackground(null);
                    searchManager.remove(emojiName);
                }
            }
        });

        // Add to grid layout.
        if(resultLayout.getChildCount() == 0)
        {
            resultLayout.setColumnCount((int)(resultLayout.getWidth() / buttonSize));
        }
        resultLayout.addView(button);
    }



    /**
     * Custom download listener.
     */
    private class CustomDownloadListener extends DownloadListener
    {
        private final ArrayList<String> emojiNames = new ArrayList<String>();

        @Override
        public void onPostOneJsonDownload(String source, String destination) {
            super.onPostOneJsonDownload(source, destination);

            final File file = new File(destination);
            final ArrayList<JsonParam> emojies = JsonParam.readFromFile(file);
            emojiNames.ensureCapacity(emojies.size());
            for(JsonParam emoji : emojies)
            {
                // Convert emoji name.
                emoji.name = emoji.name.replaceAll(" ", "_");

                // Add emoji name.
                emojiNames.add(emoji.name);
            }
            JsonParam.writeToFile(file, emojies);
        }

        @Override
        public void onPostAllJsonDownload(EmojiDownloader downloader) {
            super.onPostAllJsonDownload(downloader);

            // If downloader has download task, update emojidex database.
            if(downloader.hasDownloadTask())
                Emojidex.getInstance().reload();

            // Add emoji button to result space.
            for(String emojiName : emojiNames)
            {
                final Message message = addButtonHandler.obtainMessage();
                message.obj = emojiName;
                addButtonHandler.sendMessage(message);
            }

            // Save shared preferences.
            final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(SearchActivity.this);
            final SharedPreferences.Editor prefEditor = pref.edit();
            prefEditor.putString(
                    getString(R.string.preference_key_start_category),
                    getString(R.string.ime_category_id_search)
            );
            prefEditor.commit();
            if(loadingDialog != null)
            {
                loadingDialog.dismiss();
                loadingDialog = null;
            }
        }

        @Override
        public void onPostOneEmojiDownload(String emojiName) {
            final Emoji emoji = Emojidex.getInstance().getEmoji(emojiName);

            if(emoji == null)
                return;

            emoji.reloadImage();

            final int index = emojiNames.indexOf(emojiName);
            final View view = resultLayout.getChildAt(index);
            final Message message = invalidateHandler.obtainMessage();
            message.obj = view;
            invalidateHandler.sendMessage(message);
        }
    }
}