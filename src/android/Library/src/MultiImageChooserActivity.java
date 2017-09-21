/*
 * Copyright (c) 2012, David Erosa
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following  conditions are met:
 *
 *   Redistributions of source code must retain the above copyright notice,
 *      this list of conditions and the following disclaimer.
 *   Redistributions in binary form must reproduce the above copyright notice,
 *      this list of conditions and the following  disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,  BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT  SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR  BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDIN G NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH  DAMAGE
 *
 * Code modified by Andrew Stephan for Sync OnSet
 *
 */

package th.co.snowwhite;

import java.net.URI;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import th.co.snowwhite.FakeR;
import android.app.Activity;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

public class MultiImageChooserActivity extends Activity implements OnItemClickListener,
        LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ImagePicker";

    public static final int NOLIMIT = -1;
    public static final String MAX_IMAGES_KEY = "MAX_IMAGES";
    public static final String USE_ORIGINAL = "USE_ORIGINAL";
    public static final String CREATE_THUMBNAIL = "CREATE_THUMBNAIL";
    public static final String SAVE_TO_DATADIRECTORY = "SAVE_TO_DATADIRECTORY";
    public static final String WIDTH_KEY = "WIDTH";
    public static final String HEIGHT_KEY = "HEIGHT";
    public static final String QUALITY_KEY = "QUALITY";

    private ImageAdapter ia;

    private Cursor imagecursor, actualimagecursor;
    private int image_column_index, image_column_orientation, actual_image_column_index, orientation_column_index;
    private int colWidth;

    private static final int CURSORLOADER_THUMBS = 0;
    private static final int CURSORLOADER_REAL = 1;

    private Map<String, Integer> fileNames = new HashMap<String, Integer>();

    private SparseBooleanArray checkStatus = new SparseBooleanArray();

    private int maxImages;
    private int maxImageCount;

    private Boolean useOriginal;
    private Boolean createThumbnail;
    private Boolean saveToDataDirectory;

    private int desiredWidth;
    private int desiredHeight;
    private int quality;

    private GridView gridView;
    private TextView statusBarValue;

    private final ImageFetcher fetcher = new ImageFetcher();

    private int selectedColor = 0xff32b2e1;
    private boolean shouldRequestThumb = true;

    private FakeR fakeR;

    private ProgressDialog progress;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fakeR = new FakeR(this);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(fakeR.getId("layout", "multiselectorgrid"));
        fileNames.clear();

        maxImages = getIntent().getIntExtra(MAX_IMAGES_KEY, NOLIMIT);
        useOriginal = getIntent().getBooleanExtra(USE_ORIGINAL, false);
        createThumbnail = getIntent().getBooleanExtra(CREATE_THUMBNAIL, false);
        saveToDataDirectory = getIntent().getBooleanExtra(SAVE_TO_DATADIRECTORY, false);
        desiredWidth = getIntent().getIntExtra(WIDTH_KEY, 0);
        desiredHeight = getIntent().getIntExtra(HEIGHT_KEY, 0);
        quality = getIntent().getIntExtra(QUALITY_KEY, 0);
        maxImageCount = maxImages;

        Display display = getWindowManager().getDefaultDisplay();
        int width = display.getWidth();

        colWidth = width / 4;

        gridView = (GridView) findViewById(fakeR.getId("id", "gridview"));
        gridView.setOnItemClickListener(this);
        gridView.setOnScrollListener(new OnScrollListener() {
            private int lastFirstItem = 0;
            private long timestamp = System.currentTimeMillis();

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    shouldRequestThumb = true;
                    ia.notifyDataSetChanged();
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                float dt = System.currentTimeMillis() - timestamp;
                if (firstVisibleItem != lastFirstItem) {
                    double speed = 1 / dt * 1000;
                    lastFirstItem = firstVisibleItem;
                    timestamp = System.currentTimeMillis();

                    // Limit if we go faster than a page a second
                    shouldRequestThumb = speed < visibleItemCount;
                }
            }
        });

        ia = new ImageAdapter(this);
        gridView.setAdapter(ia);

        statusBarValue = (TextView) findViewById(fakeR.getId("id", "statusbar_value"));

        LoaderManager.enableDebugLogging(false);
        getLoaderManager().initLoader(CURSORLOADER_THUMBS, null, this);
        getLoaderManager().initLoader(CURSORLOADER_REAL, null, this);
        setupHeader();
        updateAcceptButton();
        progress = new ProgressDialog(this);
        progress.setTitle(getString(fakeR.getId("string", "processing")));
        progress.setMessage(getString(fakeR.getId("string", "processing_time")));
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View view, int position, long id) {
        String name = getImageName(position);
        int rotation = getImageRotation(position);

        if (name == null) {
            return;
        }
        boolean isChecked = !isChecked(position);
        if (maxImages == 0 && isChecked) {
            isChecked = false;
            openMaxImagesReachedDialog();
        } else if (isChecked) {
            fileNames.put(name, new Integer(rotation));
            if (maxImageCount == 1) {
                this.selectClicked(null);
            } else {
                maxImages--;
                addOverlay(view);
            }
        } else {
            fileNames.remove(name);
            maxImages++;
            removeOverlay(view);
        }

        checkStatus.put(position, isChecked);
        updateSelectionCount();
        updateAcceptButton();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int cursorID, Bundle arg1) {
        CursorLoader cl = null;

        ArrayList<String> img = new ArrayList<String>();
        switch (cursorID) {

        case CURSORLOADER_THUMBS:
            img.add(MediaStore.Images.Media._ID);
            img.add(MediaStore.Images.Media.ORIENTATION);
            break;
        case CURSORLOADER_REAL:
            img.add(MediaStore.Images.Thumbnails.DATA);
            img.add(MediaStore.Images.Media.ORIENTATION);
            break;
        default:
            break;
        }

        cl = new CursorLoader(MultiImageChooserActivity.this, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                img.toArray(new String[img.size()]), null, null, "DATE_MODIFIED DESC");
        return cl;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (cursor == null) {
            // NULL cursor. This usually means there's no image database yet....
            return;
        }

        switch (loader.getId()) {
            case CURSORLOADER_THUMBS:
                imagecursor = cursor;
                image_column_index = imagecursor.getColumnIndex(MediaStore.Images.Media._ID);
                image_column_orientation = imagecursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION);
                ia.notifyDataSetChanged();
                break;
            case CURSORLOADER_REAL:
                actualimagecursor = cursor;
                String[] columns = actualimagecursor.getColumnNames();
                actual_image_column_index = actualimagecursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                orientation_column_index = actualimagecursor.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION);
                break;
            default:
                break;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (loader.getId() == CURSORLOADER_THUMBS) {
            imagecursor = null;
        } else if (loader.getId() == CURSORLOADER_REAL) {
            actualimagecursor = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }


    @Override
    protected void onPause() {
        super.onPause();
    }

    public void cancelClicked(View ignored) {
        setResult(RESULT_CANCELED);
        finish();
    }

    public void allClicked(View ignored) {
        ((TextView) getActionBar().getCustomView().findViewById(fakeR.getId("id", "actionbar_all_textview"))).setText(getString(fakeR.getId("string", "clear")));
        getActionBar().getCustomView().findViewById(fakeR.getId("id", "actionbar_all")).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // "Select All"
                deselectAllClicked(null);
            }
        });


        fileNames.clear();
        int position = 0;
        String name = null;
        int rotation = 0;
        maxImages = maxImageCount;

        if(actualimagecursor.moveToFirst()) {

            do {

                if (maxImages == 0) {
                    openMaxImagesReachedDialog();
                    break;
                }
                name = actualimagecursor.getString(actual_image_column_index);
                rotation = actualimagecursor.getInt(orientation_column_index);
                position = actualimagecursor.getPosition();
                addOverlay((View)gridView.getChildAt(position));

                fileNames.put(name, new Integer(rotation));
                checkStatus.put(position, true);
                position++;
                maxImages--;

            } while(actualimagecursor.moveToNext());
        }
        updateSelectionCount();
        updateAcceptButton();
    }

    public void deselectAllClicked(View ignored) {
        ((TextView) getActionBar().getCustomView().findViewById(fakeR.getId("id", "actionbar_all_textview"))).setText(getString(fakeR.getId("string", "all")));
        getActionBar().getCustomView().findViewById(fakeR.getId("id", "actionbar_all")).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // "Select All"
                allClicked(null);
            }
        });

        int position = 0;
        String name = null;
        int rotation = 0;
        fileNames.clear();

        if(actualimagecursor.moveToFirst()) {

            do {

                position = actualimagecursor.getPosition();
                removeOverlay((View)gridView.getChildAt(position));
                checkStatus.put(position, false);
                position++;

            } while(actualimagecursor.moveToNext());
            maxImages = maxImageCount;
        }
        updateSelectionCount();
        updateAcceptButton();
    }

    public void selectClicked(View ignored) {
        ((TextView) getActionBar().getCustomView().findViewById(fakeR.getId("id", "actionbar_done_textview"))).setEnabled(false);
        getActionBar().getCustomView().findViewById(fakeR.getId("id", "actionbar_done")).setEnabled(false);
        progress.show();
        Intent data = new Intent();
        if (fileNames.isEmpty()) {
            this.setResult(RESULT_CANCELED);
            progress.dismiss();
            finish();
        } else {
            new ResizeImagesTask().execute(fileNames.entrySet());
        }
    }


    /*********************
     * Helper Methods
     ********************/
    private void updateAcceptButton() {
        ((TextView) getActionBar().getCustomView().findViewById(fakeR.getId("id", "actionbar_done_textview")))
                .setEnabled(fileNames.size() != 0);
        getActionBar().getCustomView().findViewById(fakeR.getId("id", "actionbar_done")).setEnabled(fileNames.size() != 0);
    }

    private void updateSelectionCount() {
        int chosen = maxImageCount - maxImages;
        statusBarValue.setText(String.valueOf(chosen));
    }

    private void setupHeader() {
        // From Roman Nkk's code
        // https://plus.google.com/113735310430199015092/posts/R49wVvcDoEW
        // Inflate a "Done/Discard" custom action bar view
        /*
         * Copyright 2013 The Android Open Source Project
         *
         * Licensed under the Apache License, Version 2.0 (the "License");
         * you may not use this file except in compliance with the License.
         * You may obtain a copy of the License at
         *
         *     http://www.apache.org/licenses/LICENSE-2.0
         *
         * Unless required by applicable law or agreed to in writing, software
         * distributed under the License is distributed on an "AS IS" BASIS,
         * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
         * See the License for the specific language governing permissions and
         * limitations under the License.
         */
        LayoutInflater inflater = (LayoutInflater) getActionBar().getThemedContext().getSystemService(
                LAYOUT_INFLATER_SERVICE);
        final View customActionBarView = inflater.inflate(fakeR.getId("layout", "actionbar"), null);
        customActionBarView.findViewById(fakeR.getId("id", "actionbar_done")).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // "Done"
                selectClicked(null);
            }
        });
        customActionBarView.findViewById(fakeR.getId("id", "actionbar_all")).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // "Select All"
                allClicked(null);
            }
        });
        customActionBarView.findViewById(fakeR.getId("id", "actionbar_discard")).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Show the custom action bar view and hide the normal Home icon and title.
        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM
                | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setCustomView(customActionBarView, new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private String getImageName(int position) {
        actualimagecursor.moveToPosition(position);
        String name = null;

        try {
            name = actualimagecursor.getString(actual_image_column_index);
        } catch (Exception e) {
            return null;
        }
        return name;
    }

    private int getImageRotation(int position) {
        actualimagecursor.moveToPosition(position);
        int rotation = 0;

        try {
            rotation = actualimagecursor.getInt(orientation_column_index);
        } catch (Exception e) {
            return rotation;
        }
        return rotation;
    }

    public boolean isChecked(int position) {
        boolean ret = checkStatus.get(position);
        return ret;
    }


    /*********************
    * Nested Classes
    ********************/
    private class SquareImageView extends ImageView {
        public SquareImageView(Context context) {
            super(context);
        }

        @Override
        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec);
        }
    }


    private class ImageAdapter extends BaseAdapter {
        private final Bitmap mPlaceHolderBitmap;

        public ImageAdapter(Context c) {
            Bitmap tmpHolderBitmap = BitmapFactory.decodeResource(getResources(), fakeR.getId("drawable", "loading_icon"));
            mPlaceHolderBitmap = Bitmap.createScaledBitmap(tmpHolderBitmap, colWidth, colWidth, false);
            if (tmpHolderBitmap != mPlaceHolderBitmap) {
                tmpHolderBitmap.recycle();
                tmpHolderBitmap = null;
            }
        }

        public int getCount() {
            if (imagecursor != null) {
                return imagecursor.getCount();
            } else {
                return 0;
            }
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        // create a new ImageView for each item referenced by the Adapter
        public View getView(int pos, View convertView, ViewGroup parent) {

            if (convertView == null) {
                ImageView temp = new SquareImageView(MultiImageChooserActivity.this);
                temp.setScaleType(ImageView.ScaleType.CENTER_CROP);
                convertView = (View)temp;
            }

            ImageView imageView = (ImageView)convertView;
            imageView.setImageBitmap(null);

            final int position = pos;

            if (!imagecursor.moveToPosition(position)) {
                return imageView;
            }

            if (image_column_index == -1) {
                return imageView;
            }

            final int id = imagecursor.getInt(image_column_index);
            final int rotate = imagecursor.getInt(image_column_orientation);
            if (isChecked(pos)) {
                addOverlay(convertView);
            } else {
                removeOverlay(convertView);
            }
            if (shouldRequestThumb) {
                fetcher.fetch(Integer.valueOf(id), imageView, colWidth, rotate);
            }

            return imageView;
        }
    }


    private class ResizeImagesTask extends AsyncTask<Set<Entry<String, Integer>>, Void, ArrayList<String>> {
        private Exception asyncTaskError = null;

        private void copyExif(ExifInterface oldExif, String newPath) throws IOException
        {

            String[] attributes = new String[]
            {
                    ExifInterface.TAG_APERTURE,
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_DATETIME_DIGITIZED,
                    ExifInterface.TAG_EXPOSURE_TIME,
                    ExifInterface.TAG_FLASH,
                    ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                    ExifInterface.TAG_GPS_DATESTAMP,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_PROCESSING_METHOD,
                    ExifInterface.TAG_GPS_TIMESTAMP,
                    ExifInterface.TAG_IMAGE_LENGTH,
                    ExifInterface.TAG_IMAGE_WIDTH,
                    ExifInterface.TAG_ISO,
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.TAG_SUBSEC_TIME,
                    ExifInterface.TAG_SUBSEC_TIME_DIG,
                    ExifInterface.TAG_SUBSEC_TIME_ORIG,
                    ExifInterface.TAG_WHITE_BALANCE
            };

            ExifInterface newExif = new ExifInterface(newPath);
            for (int i = 0; i < attributes.length; i++)
            {
                String value = oldExif.getAttribute(attributes[i]);
                if (value != null)
                    newExif.setAttribute(attributes[i], value);
            }
            newExif.saveAttributes();
        }

        @Override
        protected ArrayList<String> doInBackground(Set<Entry<String, Integer>>... fileSets) {
            Set<Entry<String, Integer>> fileNames = fileSets[0];
            ArrayList<String> al = new ArrayList<String>();
            try {
                Iterator<Entry<String, Integer>> i = fileNames.iterator();
                Bitmap bmp;
                while(i.hasNext()) {

                    Entry<String, Integer> imageInfo = i.next();
                    File originalFile = new File(imageInfo.getKey());
                    File savedFile = null;
                    File thumbFile = null;

                    if(useOriginal) {

                        savedFile = this.storeOriginal(imageInfo.getKey(), originalFile.getName());

                    } else {
                        ExifInterface oldExif = new ExifInterface(imageInfo.getKey());

                        bmp = this.processBitmap(originalFile, imageInfo);
                        savedFile = this.storeImage(bmp, originalFile.getName(), false);

                        this.copyExif(oldExif, savedFile.getAbsolutePath());

                    }
                    if (createThumbnail) {

                        bmp = this.getThumbnailBitmap(originalFile.getAbsolutePath());
                        if (bmp != null) thumbFile = this.storeImage(bmp, savedFile.getName(), true);
                    }

                    al.add(Uri.fromFile(savedFile).toString());
                }

                return al;

            } catch(IOException e) {
                try {
                    asyncTaskError = e;
                    for (int i = 0; i < al.size(); i++) {
                        URI uri = new URI(al.get(i));
                        File file = new File(uri);
                        file.delete();
                    }
                } catch(Exception exception) {
                    // the finally does what we want to do
                } finally {
                    return new ArrayList<String>();
                }
            }
        }

        @Override
        protected void onPostExecute(ArrayList<String> al) {
            Intent data = new Intent();

            if (asyncTaskError != null) {
                Bundle res = new Bundle();
                res.putString("ERRORMESSAGE", asyncTaskError.getMessage());
                data.putExtras(res);
                setResult(RESULT_CANCELED, data);
            } else if (al.size() > 0) {
                Bundle res = new Bundle();
                res.putStringArrayList("MULTIPLEFILENAMES", al);
                if (imagecursor != null) {
                    res.putInt("TOTALFILES", imagecursor.getCount());
                }
                data.putExtras(res);
                setResult(RESULT_OK, data);
            } else {
                setResult(RESULT_CANCELED, data);
            }

            progress.dismiss();
            finish();
        }

        private Bitmap processBitmap(File file, Entry<String, Integer> imageInfo) throws IOException, OutOfMemoryError {
            Bitmap bmp;
            // Bitmap processing
            int rotate = imageInfo.getValue().intValue();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            int width = options.outWidth;
            int height = options.outHeight;
            float scale = calculateScale(width, height);
            if (scale < 1) {
                int finalWidth = (int)(width * scale);
                int finalHeight = (int)(height * scale);
                int inSampleSize = calculateInSampleSize(options, finalWidth, finalHeight);
                options = new BitmapFactory.Options();
                options.inSampleSize = inSampleSize;
                try {
                    bmp = this.tryToGetBitmap(file, options, rotate, true);
                } catch (OutOfMemoryError e) {
                    options.inSampleSize = calculateNextSampleSize(options.inSampleSize);
                    try {
                        bmp = this.tryToGetBitmap(file, options, rotate, false);
                    } catch (OutOfMemoryError e2) {
                        throw new IOException("Unable to load image into memory.");
                    }
                }
            } else {
                try {
                    bmp = this.tryToGetBitmap(file, null, rotate, false);
                } catch(OutOfMemoryError e) {
                    options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    try {
                        bmp = this.tryToGetBitmap(file, options, rotate, false);
                    } catch(OutOfMemoryError e2) {
                        options = new BitmapFactory.Options();
                        options.inSampleSize = 4;
                        try {
                            bmp = this.tryToGetBitmap(file, options, rotate, false);
                        } catch (OutOfMemoryError e3) {
                            throw new IOException("Unable to load image into memory.");
                        }
                    }
                }
            }
            return bmp;
        }

        private Bitmap getThumbnailBitmap(String path){
            Cursor ca = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[] { MediaStore.MediaColumns._ID }, MediaStore.MediaColumns.DATA + "=?", new String[] {path}, null);
            if (ca != null && ca.moveToFirst()) {
                int id = ca.getInt(ca.getColumnIndex(MediaStore.MediaColumns._ID));
                ca.close();
                return MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), id, MediaStore.Images.Thumbnails.MICRO_KIND, null );
            }
            ca.close();
            return null;
        }

        private Bitmap tryToGetBitmap(File file, BitmapFactory.Options options, int rotate, boolean shouldScale) throws IOException, OutOfMemoryError {
            Bitmap bmp;
            if (options == null) {
                bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
            } else {
                bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            }
            if (bmp == null) {
                throw new IOException("The image file could not be opened.");
            }
            if (options != null && shouldScale) {
                float scale = calculateScale(options.outWidth, options.outHeight);
                bmp = this.getResizedBitmap(bmp, scale);
            }
            if (rotate != 0) {
                Matrix matrix = new Matrix();
                matrix.setRotate(rotate);
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
            }
            return bmp;
        }

        /*
        * The following functions are originally from
        * https://github.com/raananw/PhoneGap-Image-Resizer
        *
        * They have been modified by Andrew Stephan for Sync OnSet
        *
        * The software is open source, MIT Licensed.
        * Copyright (C) 2012, webXells GmbH All Rights Reserved.
        */
        private File destinationFile(String fileName, boolean isThumb) {

            File destDir = null;

            if(saveToDataDirectory) {
                destDir = MultiImageChooserActivity.this.getFilesDir();
            } else {
                destDir = MultiImageChooserActivity.this.getCacheDir();
            }

            int dotIndex = fileName.lastIndexOf('.');
            String name = fileName.substring(0, dotIndex);
            String ext = fileName.substring(dotIndex);
            int destFileName = 1;
            File destFile = null;

            do {
                if(isThumb) {
                    destFile = new File(destDir, "thumb_" + name + ext);
                } else {
                    destFile = new File(destDir, "snw_photo_" + String.format("%04d", destFileName) + ext);
                }
                destFileName++;
            } while (destFile.exists());

            return destFile;

        }

        private File storeImage(Bitmap bmp, String fileName, boolean isThumb) throws IOException {

            int dotIndex = fileName.lastIndexOf('.');
            String ext = fileName.substring(dotIndex);

            File file = destinationFile(fileName, isThumb);
            OutputStream outStream = new FileOutputStream(file);

            if (ext.compareToIgnoreCase(".png") == 0) {
                bmp.compress(Bitmap.CompressFormat.PNG, quality, outStream);
            } else {
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, outStream);
            }
            outStream.flush();
            outStream.close();

            return file;
        }

        private File storeOriginal(String fullPath, String fileName) throws IOException {
            File orig = new File(fullPath);
            File file = destinationFile(fileName, false);
            FileInputStream inStream = new FileInputStream(orig);
            FileOutputStream outStream = new FileOutputStream(file);
            FileChannel inChannel = inStream.getChannel();
            FileChannel outChannel = outStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
            inStream.close();
            outStream.close();

            return file;
        }

        private Bitmap getResizedBitmap(Bitmap bm, float factor) {
            int width = bm.getWidth();
            int height = bm.getHeight();
            // create a matrix for the manipulation
            Matrix matrix = new Matrix();
            // resize the bit map
            matrix.postScale(factor, factor);
            // recreate the new Bitmap
            Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
            return resizedBitmap;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private int calculateNextSampleSize(int sampleSize) {
        double logBaseTwo = (int)(Math.log(sampleSize) / Math.log(2));
        return (int)Math.pow(logBaseTwo + 1, 2);
    }

    private float calculateScale(int width, int height) {
        float widthScale = 1.0f;
        float heightScale = 1.0f;
        float scale = 1.0f;
        if (desiredWidth > 0 || desiredHeight > 0) {
            if (desiredHeight == 0 && desiredWidth < width) {
                scale = (float)desiredWidth/width;
            } else if (desiredWidth == 0 && desiredHeight < height) {
                scale = (float)desiredHeight/height;
            } else {
                if (desiredWidth > 0 && desiredWidth < width) {
                    widthScale = (float)desiredWidth/width;
                }
                if (desiredHeight > 0 && desiredHeight < height) {
                    heightScale = (float)desiredHeight/height;
                }
                if (widthScale < heightScale) {
                    scale = widthScale;
                } else {
                    scale = heightScale;
                }
            }
        }

        return scale;
    }

    private void openMaxImagesReachedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        //builder.setTitle(getString(fakeR.getId("string", "max_1")) + " " + maxImageCount + " " + getString(fakeR.getId("string", "max_2")));
        builder.setTitle(getString(fakeR.getId("string", "max_1")));
        //builder.setMessage(getString(fakeR.getId("string", "max_3")) + " " + maxImageCount + " " + getString(fakeR.getId("string", "max_4")));
        builder.setMessage(getString(fakeR.getId("string", "max_3")));
        builder.setPositiveButton(getString(fakeR.getId("string", "done")), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void addOverlay(View view) {
        if(view == null) return;
        ImageView imageView = (ImageView)view;
        if (android.os.Build.VERSION.SDK_INT>=16) {
          imageView.setImageAlpha(128);
        } else {
          imageView.setAlpha(128);
        }
        view.setBackgroundColor(selectedColor);

    }

    private void removeOverlay(View view) {
        if(view == null) return;
        ImageView imageView = (ImageView)view;
        if (android.os.Build.VERSION.SDK_INT>=16) {
            imageView.setImageAlpha(255);
        } else {
            imageView.setAlpha(255);
        }
        view.setBackgroundColor(Color.TRANSPARENT);
    }

}
