package org.genshin.emojidexandroid;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by nazuki on 2013/08/28.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmojiData
{
    @JsonProperty("moji")           private String moji;
//    @JsonProperty("alto")           private String[] alto;
    @JsonProperty("name")           private String name;
//    @JsonProperty("name-ja")        private String nameJa;
//    @JsonProperty("emoticon")       private String emoticon;
    @JsonProperty("category")       private String category;
//    @JsonProperty("unicode")        private String unicode;
//    @JsonProperty("attribution")    private String attribution;
//    @JsonProperty("contributor")    private String contributor;
//    @JsonProperty("url")            private String url;

    private Drawable icon = null;
    private int code;

    /**
     * Initialize EmojiData object.
     * @param res
     * @param dir
     * @param code
     */
    public void initialize(Resources res, String dir, int code)
    {
        // Load icon image.
        try
        {
            InputStream is = res.getAssets().open(dir + name + ".png");
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            icon = new BitmapDrawable(res, bitmap);
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }

        // Set emoji code.
        this.code = code;
    }

    /**
     * Get unicode character.
     * @return  Unicode character.
     */
    public String getMoji()
    {
        return moji;
    }

    /**
     * Get category name.
     * @return  Category name.
     */
    public String getCategory()
    {
        return category;
    }

    /**
     * Get icon image.
     * @return      Icon image.
     */
    public Drawable getIcon()
    {
        return icon;
    }

    /**
     * Get emoji code.
     * @return      Emoji code.
     */
    public int getCode()
    {
        return code;
    }
}