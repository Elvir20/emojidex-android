package com.emojidex.emojidexandroid;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.RadioButton;
import android.widget.ViewFlipper;

import com.emojidex.emojidexandroid.animation.EmojidexAnimationDrawable;
import com.emojidex.emojidexandroid.animation.updater.AnimationUpdater;
import com.emojidex.emojidexandroid.comparator.EmojiComparator;
import com.emojidex.emojidexandroid.downloader.DownloadListener;
import com.emojidex.emojidexandroid.imageloader.ImageLoadListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Created by kou on 13/08/11.
 */
public class EmojidexIME extends InputMethodService {
    static final String TAG = MainActivity.TAG + "::EmojidexIME";
    static EmojidexIME currentInstance = null;

    private Emojidex emojidex;
    private EmojiFormat keyFormat;

    private InputMethodManager inputMethodManager = null;
    private int showIMEPickerCode = 0;
    private int showSearchWindowCode = 0;
    private int showFilterWindowCode = 0;
    private EmojidexSubKeyboardView subKeyboardView = null;
    private Keyboard.Key keyEnter = null;
    private int keyEnterIndex;
    private int imeOptions;

    private View layout;
    private HorizontalScrollView categoryScrollView;
    private Button categoryAllButton;
    private Button myEmojiButton;

    private ViewFlipper keyboardViewFlipper;
    private boolean swipeFlag = false;

    private HistoryManager historyManager;
    private FavoriteManager favoriteManager;
    private SaveDataManager searchManager;
    private SaveDataManager indexManager;
    private SaveDataManager myEmojiManager;
    private KeyboardViewManager keyboardViewManager;

    private UserData userdata;

    private String currentCategory = null;

    private EmojidexIndexUpdater indexUpdater = null;
    private EmojidexMyEmojiUpdater myEmojiUpdater = null;

    private final HashMap<String, List<Emoji>> categorizedEmojies = new HashMap<String, List<Emoji>>();

    private final CustomDownloadListener downloadListener = new CustomDownloadListener();
    private final CustomImageLoadListener imageLoadListener = new CustomImageLoadListener();
    private final CustomAnimationUpdater animationUpdater = new CustomAnimationUpdater();

    private EmojiComparator.SortType currentSortType;
    private boolean standardOnly = false;
    private boolean r18Visibility;

    /**
     * Construct EmojidexIME object.
     */
    public EmojidexIME()
    {
        setTheme(R.style.IMETheme);
    }

    @Override
    public void onInitializeInterface() {
        deleteCache();

        // Get InputMethodManager object.
        inputMethodManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        showIMEPickerCode = getResources().getInteger(R.integer.ime_keycode_show_ime_picker);
        showSearchWindowCode = getResources().getInteger(R.integer.ime_keycode_show_search_window);
        showFilterWindowCode = getResources().getInteger(R.integer.ime_keycode_show_filter_window);

        // Initialize Emojidex object.
        new CacheAnalyzer().analyze(this);

        emojidex = Emojidex.getInstance();
        emojidex.initialize(this);

        keyFormat = EmojiFormat.toFormat(getString(R.string.emoji_format_key));

        // Create PreferenceManager.
        historyManager = HistoryManager.getInstance(this);
        favoriteManager = FavoriteManager.getInstance(this);
        searchManager = SaveDataManager.getInstance(this, SaveDataManager.Type.Search);
        indexManager = SaveDataManager.getInstance(this, SaveDataManager.Type.Index);
        myEmojiManager = SaveDataManager.getInstance(this, SaveDataManager.Type.MyEmoji);

        // Initialize user data.
        userdata = UserData.getInstance();
        userdata.init(this);

        currentSortType = getSortType();
        standardOnly = isStandardOnly();
    }

    @Override
    public View onCreateInputView() {
        // Create IME layout.
        layout = getLayoutInflater().inflate(R.layout.ime, null);

        // Get all category button.
        categoryAllButton = (Button)layout.findViewById(R.id.ime_category_button_all);
        myEmojiButton = (Button)layout.findViewById(R.id.ime_category_button_my_emoji);

        createCategorySelector();
        createKeyboardView();
        createSubKeyboardView();
        setMyEmojiButtonVisibility();

        // Sync user data.
        historyManager.loadFromUser();
        favoriteManager.loadFromUser();

        return layout;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        // Load save data.
        if( !restarting )
        {
            historyManager.load();
            searchManager.load();
            indexManager.load();
            myEmojiManager.load();
            favoriteManager.load();
        }

        // Set current instance.
        currentInstance = this;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);

        // Get ime options.
        if( (info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0 )
            imeOptions = EditorInfo.IME_ACTION_NONE;
        else
            imeOptions = info.imeOptions;

        // Set enter key parameter.
        if(keyEnter == null)
            return;

        switch(imeOptions)
        {
            case EditorInfo.IME_ACTION_NONE:
                keyEnter.icon = getResources().getDrawable(R.drawable.ime_key_enter);
                keyEnter.label = null;
                break;
            default:
                keyEnter.icon = null;
                keyEnter.iconPreview = null;
                keyEnter.label = getTextForImeAction(imeOptions);
                break;
        }

        // Redraw keyboard view.
        subKeyboardView.invalidateKey(keyEnterIndex);

        // Initialize.
        initStartCategory();

        // Emoji download.
        indexUpdater = new EmojidexIndexUpdater(this, EmojidexKeyboard.create(this).getKeyCountMax());
        indexUpdater.startUpdateThread(2);
        
        new EmojidexUpdater(this).startUpdateThread();

        if (userdata.getUsername() != null && !userdata.getUsername().equals("")) {
            myEmojiUpdater = new EmojidexMyEmojiUpdater(this, userdata.getUsername());
            myEmojiUpdater.startUpdateThread(false);
        }
    }

    @Override
    public void onFinishInput() {
        currentInstance = null;
        super.onFinishInput();
    }

    @Override
    public void onFinishInputView(boolean finishingInput)
    {
        historyManager.save();
        favoriteManager.save();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideWindow();
    }

    @Override
    public void showWindow(boolean showInput)
    {
        super.showWindow(showInput);

        // Regist download listener.
        emojidex.addDownloadListener(downloadListener);
        emojidex.addImageLoadListener(imageLoadListener);
    }

    @Override
    public void hideWindow()
    {
        if( keyboardViewManager == null || !keyboardViewManager.getCurrentView().closePopup() )
        {
            currentCategory = null;
            super.hideWindow();
            stopAnimation();
        }

        // Unregist download listener.
        emojidex.removeDownloadListener(downloadListener);
        emojidex.removeImageLoadListener(imageLoadListener);
    }

    /**
     * Create category selector.
     */
    private void createCategorySelector()
    {
        final CategoryManager categoryManager = CategoryManager.getInstance();
        categoryManager.initialize(this);

        // Create category buttons and add to IME layout.
        final ViewGroup categoriesView = (ViewGroup)layout.findViewById(R.id.ime_categories);

        for(final String categoryName : emojidex.getCategoryNames())
        {
            categoryManager.add(categoryName, categoryName);
        }

        final int categoryCount = categoryManager.getCategoryCount();
        for(int i = 0;  i < categoryCount;  ++i)
        {
            // Create button.
            final RadioButton newButton = new RadioButton(this);

            newButton.setText(categoryManager.getCategoryText(i));
            newButton.setContentDescription(categoryManager.getCategoryId(i));
            newButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onClickCategoryButton(v);
                }
            });

            // Add button to IME layout.
            categoriesView.addView(newButton);
        }

        // Create categories scroll buttons.
        categoryScrollView = (HorizontalScrollView)layout.findViewById(R.id.ime_category_scrollview);
    }

    /**
     * Create main KeyboardView object and add to IME layout.
     */
    private void createKeyboardView()
    {
        // Add KeyboardViewFlipper to IME layout.
        keyboardViewFlipper = (ViewFlipper)layout.findViewById(R.id.keyboard_viewFlipper);

        // Create KeyboardViewManager.
        keyboardViewManager = new KeyboardViewManager(this, new CustomOnKeyboardActionListener(), new CustomOnTouchListener());

        for(View view : keyboardViewManager.getViews())
            keyboardViewFlipper.addView(view);
    }

    /**
     * Create sub KeyboardView object and add to IME layout.
     */
    private void createSubKeyboardView()
    {
        // Create KeyboardView.
        subKeyboardView = new EmojidexSubKeyboardView(this, null, R.attr.subKeyboardViewStyle);
        subKeyboardView.setOnKeyboardActionListener(new CustomOnKeyboardActionListener());
        subKeyboardView.setPreviewEnabled(false);

        // Create Keyboard and set to KeyboardView.
        Keyboard keyboard = new Keyboard(this, R.xml.sub_keyboard);
        subKeyboardView.setKeyboard(keyboard);

        // Add KeyboardView to IME layout.
        ViewGroup targetView = (ViewGroup)layout.findViewById(R.id.ime_sub_keyboard);
        targetView.addView(subKeyboardView);

        // Get enter key object.
        final int[] enterCodes = { KeyEvent.KEYCODE_ENTER };
        final List<Keyboard.Key> keys = keyboard.getKeys();
        final int count = keys.size();
        for(keyEnterIndex = 0;  keyEnterIndex < count;  ++keyEnterIndex)
        {
            final Keyboard.Key key = keys.get(keyEnterIndex);
            if( Arrays.equals(key.codes, enterCodes) )
            {
                keyEnter = key;
                break;
            }
        }
    }

    /**
     * Initialize start category.
     */
    private void initStartCategory()
    {
        // Load start category from preference.
        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        final String key = getString(R.string.preference_key_start_category);
        final String defaultCategory = getString(R.string.ime_category_id_index);
        final String searchCategory = getString(R.string.ime_category_id_search);
        final String startCategory = pref.getString(key, defaultCategory);

        // If start category is "search", always initialize keyboard.
        if(startCategory.equals(searchCategory))
            currentCategory = null;

        // If current category is not null, skip initialize.
        if(currentCategory != null)
            return;

        // Initialize scroll position.
        categoryScrollView.scrollTo(0, 0);

        // Search category.
        final ViewGroup categoriesView = (ViewGroup)layout.findViewById(R.id.ime_categories);
        final int childCount = categoriesView.getChildCount();
        for(int i = 0;  i < childCount;  ++i)
        {
            final Button button = (Button)categoriesView.getChildAt(i);
            if(button.getContentDescription().equals(startCategory))
            {
                pref.edit().putString(key, defaultCategory).commit();
                button.performClick();
                return;
            }
        }

        // If start category is not found, use category "all".
        pref.edit().putString(key, defaultCategory).commit();
        categoryAllButton.performClick();
    }

    /**
     * move to the next keyboard view
     * @param direction left or down
     */
    public void moveToNextKeyboard(String direction)
    {
        if (direction.equals("left"))
        {
            keyboardViewFlipper.setInAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.right_in));
            keyboardViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.left_out));
        }
        else
        {
            keyboardViewFlipper.setInAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.down_in));
            keyboardViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.up_out));
        }
        keyboardViewManager.next();
        keyboardViewFlipper.showNext();
        initAnimation();

        getIndexMore();

        Log.d(TAG, "keyboard page : " + (keyboardViewManager.getCurrentPage()+1) + " / " + keyboardViewManager.getPageCount());
    }

    /**
     * move to the prev keyboard view
     * @param direction right or up
     */
    public void moveToPrevKeyboard(String direction)
    {
        if (direction.equals("right"))
        {
            keyboardViewFlipper.setInAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.left_in));
            keyboardViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.right_out));
        }
        else
        {
            keyboardViewFlipper.setInAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.up_in));
            keyboardViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.down_out));
        }
        keyboardViewManager.prev();
        keyboardViewFlipper.showPrevious();
        initAnimation();

        getIndexMore();

        Log.d(TAG, "keyboard page : " + (keyboardViewManager.getCurrentPage()+1) + " / " + keyboardViewManager.getPageCount());
    }

    public void getIndexMore()
    {
        if(     !currentCategory.equals("index")
            ||  ((keyboardViewManager.getCurrentPage()+1) != keyboardViewManager.getPageCount())    )
            return;

        indexUpdater.startUpdateThread(keyboardViewManager.getCurrentPage() + 2, true);
    }

    /**
     * When click category button.
     * @param v     Clicked button.
     */
    public void onClickCategoryButton(View v)
    {
        final String categoryName = v.getContentDescription().toString();
        Log.d(TAG, "Click category button : category = " + categoryName);
        changeCategory(categoryName);
    }

    /**
     * Change category.
     * @param category  Category.
     */
    public void changeCategory(String category)
    {
        changeCategory(category, 0);
    }

    /**
     * Change category.
     * @param category      Category.
     * @param defaultPage   Default page number.
     */
    public void changeCategory(String category, int defaultPage)
    {
        if (category == null) return;
        if (currentSortType == null) currentSortType = EmojiComparator.SortType.SCORE;
        if (category.equals(currentCategory) &&
                currentSortType.equals(getSortType()) && standardOnly == isStandardOnly()) return;

        currentCategory = category;
        currentSortType = getSortType();
        standardOnly = isStandardOnly();
        r18Visibility = userdata.isLogined() && userdata.isR18();
        keyboardViewManager.setR18Visibility(r18Visibility);

        if(category.equals(getString(R.string.ime_category_id_history)))
        {
            final List<String> emojiNames = historyManager.getEmojiNames();
            keyboardViewManager.initializeFromName(emojiNames, defaultPage, false);
        }
        else if(category.equals(getString(R.string.ime_category_id_favorite)))
        {
            final List<String> emojiNames = favoriteManager.getEmojiNames();
            keyboardViewManager.initializeFromName(emojiNames, defaultPage, false);
        }
        else if(category.equals(getString(R.string.ime_category_id_search)))
        {
            final List<String> emojiNames = searchManager.getEmojiNames();
            keyboardViewManager.initializeFromName(emojiNames, defaultPage, standardOnly);
        }
        else if(category.equals(getString(R.string.ime_category_id_index)))
        {
            final List<String> emojiNames = indexManager.getEmojiNames();
            final List<Emoji> emojies = new ArrayList<Emoji>(emojiNames.size());
            for(String name : emojiNames)
            {
                final Emoji emoji = emojidex.getEmoji(name);
                if(emoji != null) {
                    if ((!standardOnly || emoji.isStandard()) && (r18Visibility || !emoji.isR18())) emojies.add(emoji);
                }
            }

            // Sort.
            Collections.sort(emojies, new EmojiComparator(currentSortType));

            keyboardViewManager.initialize(emojies, defaultPage);
        }
        else if (category.equals(getString(R.string.ime_category_id_my_emoji)))
        {
            final List<String> emojiNames = myEmojiManager.getEmojiNames();
            final List<Emoji> emojies = new ArrayList<Emoji>(emojiNames.size());
            for(String name : emojiNames)
            {
                final Emoji emoji = emojidex.getEmoji(name);
                if(emoji != null) emojies.add(emoji);
            }

            // Sort.
            Collections.sort(emojies, new EmojiComparator(currentSortType));

            keyboardViewManager.initialize(emojies, defaultPage);
        }
        else
        {
            final List<Emoji> emojies = getCategorizedEmojies(category);

            // Sort.
            Collections.sort(emojies, new EmojiComparator(currentSortType));

            keyboardViewManager.initialize(emojies, defaultPage);
        }

        initAnimation();
    }

    private List<Emoji> getCategorizedEmojies(String category)
    {
        List<Emoji> emojies = categorizedEmojies.get(category);
        if(emojies == null)
        {
            // Copy emoji list.
            final List<Emoji> src = category.equals(getString(R.string.ime_category_id_all))
                    ? emojidex.getAllEmojiList()
                    : emojidex.getEmojiList(category);

            emojies = src != null
                    ? new ArrayList<Emoji>(src)
                    : new ArrayList<Emoji>();

            // Add emoji list.
            categorizedEmojies.put(category, emojies);
        }

        if (standardOnly || !r18Visibility) {
            List<Emoji> removeEmojies = new ArrayList<>();
            for (Emoji emoji : emojies) {
                if ((standardOnly && !emoji.isStandard()) || (!r18Visibility && emoji.isR18())) removeEmojies.add(emoji);
            }
            emojies.removeAll(removeEmojies);
        }

        return emojies;
    }

    /**
     * Re-draw key.
     * @param emojiNames    Emoji name array.
     */
    void invalidate(String... emojiNames)
    {
        if(keyboardViewManager == null)
            return;

        final EmojidexKeyboardView view = keyboardViewManager.getCurrentView();
        final Keyboard keyboard = view.getKeyboard();
        final List<Keyboard.Key> keys = keyboard.getKeys();

        for(String emojiName : emojiNames)
        {
            for(int i = 0; i < keys.size(); ++i)
            {
                if(keys.get(i).popupCharacters.equals(emojiName))
                {
                    keyboardViewManager.getCurrentView().invalidateKey(i);

                    break;
                }
            }
        }
    }

    /**
     * Reload search result tab.
     */
    void reloadSearchResult()
    {
        searchManager.load();
        if (currentCategory == null) return;

        if(currentCategory.equals(getString(R.string.ime_category_id_search)))
        {
            final String category = currentCategory;
            currentCategory = null;
            changeCategory(category);
        }
    }

    /**
     * Reload ime.
     */
    void reload()
    {
        // Clear cache.
        categorizedEmojies.clear();

        // Reload category.
        if(keyboardViewManager == null)
            return;

        final String category = currentCategory;
        currentCategory = null;
        changeCategory(category, keyboardViewManager.getCurrentPage());
    }

    /**
     * Delete the cache files from the default cache directory.
     */
    private void deleteCache()
    {
        File cacheDir = getCacheDir();
        if (cacheDir == null) return;

        File[] list = cacheDir.listFiles();
        for (File f : list) f.delete();
    }

    /**
     * Commit emoji to current input connection..
     * @param emoji     Emoji.
     */
    void commitEmoji(Emoji emoji)
    {
        getCurrentInputConnection().commitText(emoji.toEmojidexString(), 1);
        historyManager.addFirst(emoji.getCode());
    }

    void changeKeyboard(Emoji emoji) {
        // If current category is not have emoji, change category.
        final String emojiCategory = emoji.getCategory();
        if(     !currentCategory.equals(getString(R.string.all_category))
            &&  !currentCategory.equals(emojiCategory) )
        {
            final ViewGroup categoriesView = (ViewGroup)layout.findViewById(R.id.ime_categories);
            final int count = categoriesView.getChildCount();
            for(int i = 0;  i < count;  ++i)
            {
                Button button = (Button)categoriesView.getChildAt(i);
                if(emojiCategory.equals(button.getContentDescription()))
                {
                    button.performClick();
                    break;
                }
            }
        }

        // Set page.
        keyboardViewFlipper.setInAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.right_in));
        keyboardViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.left_out));
        keyboardViewManager.setPage(emoji.getCode());
        keyboardViewFlipper.showNext();
        initAnimation();
    }

    private void initAnimation()
    {
        if(keyboardViewManager.getCurrentView().hasAnimationEmoji())
            startAnimation();
        else
            stopAnimation();
    }

    private void startAnimation()
    {
        emojidex.addAnimationUpdater(animationUpdater);
    }

    private void stopAnimation()
    {
        emojidex.removeAnimationUpdater(animationUpdater);
    }

    /**
     * Get standard emoji only preference
     * @return true or false
     */
    private boolean isStandardOnly() {
        SharedPreferences pref = getSharedPreferences(FilterActivity.PREF_NAME, Context.MODE_PRIVATE);
        return pref.getBoolean(getString(R.string.preference_key_standard_only), false);
    }

    /**
     * Get sort type preference
     * @return sort type
     */
    private EmojiComparator.SortType getSortType() {
        if (userdata.isLogined() && userdata.isSubscriber())
        {
            SharedPreferences pref = getSharedPreferences(FilterActivity.PREF_NAME, Context.MODE_PRIVATE);
            int sortType = pref.getInt(getString(R.string.preference_key_sort_type), EmojiComparator.SortType.SCORE.getValue());
            return EmojiComparator.SortType.fromInt(sortType);
        }
        else
        {
            return EmojiComparator.SortType.SCORE;
        }
    }

    /**
     * set my_emoji button visibility.
     */
    public void setMyEmojiButtonVisibility()
    {
        if (userdata.isLogined())
            myEmojiButton.setVisibility(View.VISIBLE);
        else
            myEmojiButton.setVisibility(View.GONE);
    }

    /**
     * Custom DownloadListener.
     */
    private class CustomDownloadListener extends DownloadListener
    {
        @Override
        public void onDownloadJson(int handle, String... emojiNames)
        {
            reload();
        }
    }

    /**
     * Custom image load listener.
     */
    private class CustomImageLoadListener implements ImageLoadListener
    {
        @Override
        public void onLoad(int handle, EmojiFormat format, String emojiName)
        {
            if(keyFormat.equals(format))
                invalidate(emojiName);
        }
    }


    /**
     * Custom OnKeyboardActionListener.
     */
    private class CustomOnKeyboardActionListener implements KeyboardView.OnKeyboardActionListener
    {
        @Override
        public void onPress(int primaryCode) {
            // nop
        }

        @Override
        public void onRelease(int primaryCode) {
            // nop
        }

        @Override
        public void onKey(int primaryCode, int[] keyCodes) {
            if (swipeFlag)
            {
                swipeFlag = false;
                return;
            }

            final List<Integer> codes = new ArrayList<Integer>();
            for(int i = 0;  i < keyCodes.length && keyCodes[i] != -1;  ++i)
                codes.add(keyCodes[i]);

            // Input show ime picker or default keyboard.
            if (primaryCode == showIMEPickerCode)
            {
                boolean hasDefaultIME = false;
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(EmojidexIME.this);
                final String idNothing = getString(R.string.preference_entryvalue_default_keyboard_nothing);
                final String defaultIME = prefs.getString(getString(R.string.preference_key_default_keyboard), idNothing);

                if( !defaultIME.equals(idNothing) ) {
                    for (InputMethodInfo info : inputMethodManager.getEnabledInputMethodList()) {
                        if (info.getId().equals(defaultIME))
                            hasDefaultIME = true;
                    }
                }

                if (hasDefaultIME)
                    switchInputMethod(defaultIME);
                else
                    inputMethodManager.showInputMethodPicker();
            }
            else if (primaryCode == showSearchWindowCode)
            {
                showSearchWindow();
            }
            else if (primaryCode == showFilterWindowCode)
            {
                showFilterWindow();
            }
            else
            {
                // Input emoji.
                final Emoji emoji = emojidex.getEmoji(codes);
                if(emoji != null)
                {
                    commitEmoji(emoji);
                }
                // Input enter key.
                else if(primaryCode == KeyEvent.KEYCODE_ENTER && imeOptions != EditorInfo.IME_ACTION_NONE)
                {
                    getCurrentInputConnection().performEditorAction(imeOptions);
                }
                // Input other.
                else
                {
                    sendDownUpKeyEvents(primaryCode);
                }
            }
        }

        @Override
        public void onText(CharSequence text) {
            // nop
        }

        @Override
        public void swipeLeft() {
            // nop
        }

        @Override
        public void swipeRight() {
            // nop
        }

        @Override
        public void swipeDown() {
            // nop
        }

        @Override
        public void swipeUp() {
            // nop
        }

        /**
         * Show emoji search window.
         */
        private void showSearchWindow() {
            final Intent intent = new Intent(EmojidexIME.this, SearchActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        /**
         * Show emoji filter window.
         */
        private void showFilterWindow() {
            final Intent intent = new Intent(EmojidexIME.this, FilterActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    /**
     * Custom OnGestureListener.
     */
    private class CustomOnGestureListener implements GestureDetector.OnGestureListener
    {
        @Override
        public boolean onDown(MotionEvent e) {
            // nop
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {
            // nop
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // nop
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            // nop
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            // nop
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float disX = e1.getX() - e2.getX();
            float disY = e1.getY() - e2.getY();
            if ((Math.abs(disX) < 100) && (Math.abs(disY) < 50))
                return true;

            // closing the popup window.
            EmojidexKeyboardView view = keyboardViewManager.getCurrentView();
            view.closePopup();

            // left or right
            if (Math.abs(disX) > Math.abs(disY))
            {
                if (disX > 0)
                    moveToNextKeyboard("left");
                else
                    moveToPrevKeyboard("right");
            }
            // up or down
            else
            {
                if (disY > 0)
                    moveToNextKeyboard("down");
                else
                    moveToPrevKeyboard("up");
            }
            swipeFlag = true;
            return false;
        }
    }

    /**
     * Custom OnTouchListener.
     */
    private class CustomOnTouchListener implements View.OnTouchListener
    {
        private final GestureDetector detector;

        /**
         * Construct object.
         */
        public CustomOnTouchListener()
        {
            detector = new GestureDetector(getApplicationContext(), new CustomOnGestureListener());
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return detector.onTouchEvent(event);
        }
    }

    /**
     * Custom emojidex animation updater.
     */
    private class CustomAnimationUpdater implements AnimationUpdater
    {
        @Override
        public void update()
        {
            keyboardViewManager.getCurrentView().invalidateAnimation();
        }

        @Override
        public Collection<EmojidexAnimationDrawable> getDrawables()
        {
            return keyboardViewManager.getCurrentView().getAnimationDrawables();
        }
    }
}
