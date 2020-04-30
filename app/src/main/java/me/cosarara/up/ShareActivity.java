package me.cosarara.up;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.Util;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

public class ShareActivity extends AppCompatActivity {
    public void toClipboard(String url) { // copy&pasted because I can't be arsed with fucking android
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("URL", url);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(this, "copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private final OkHttpClient client = new OkHttpClient();
    private Handler mHandler;

    private void doUpload(Intent intent) throws Exception {
        String mime = intent.getType();
        final TextView textView = findViewById(R.id.textView);

        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

        //textView.setText("step 2: "+uri);
        if (uri == null) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text == null) {
                textView.setText("Couldn't find any data!");
                return;
            }
            textView.setText("Plain text not implemented yet, sorry!");
            return;
        }

        if (mime.equals("image/*")) {
            Log.i("upload", "trying to improve mime: " + mime + " from URL " + uri.toString());
            // try to get something better
            String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            MimeTypeMap map = MimeTypeMap.getSingleton();
            if (extension != null && map != null) {
                String new_mime = map.getMimeTypeFromExtension(extension);
                Log.i("upload", "new mime: " + new_mime);
                if (new_mime != null) {
                    mime = new_mime;
                }
            }
        }
        if (mime.equals("image/*")) {
            // https://stackoverflow.com/a/10194912
            Log.i("upload", "trying to improve mime... again");
            Cursor cursor = this.getContentResolver().query(uri,
                    new String[] { MediaStore.MediaColumns.MIME_TYPE },
                    null, null, null);

            if (cursor != null && cursor.moveToNext())
            {
                mime = cursor.getString(0);
                Log.i("upload", "last attempt at MIME: " + mime);
            }
        }

        final InputStream uriStream = getContentResolver().openInputStream(uri);
        Log.i("upload", "mime: " + mime);
        final MediaType mediaType = MediaType.parse(mime);

        // https://stackoverflow.com/questions/25367888/upload-binary-file-with-okhttp-from-resources
        RequestBody file_part = new RequestBody() {
            @Override
            public MediaType contentType() {
                return mediaType;
            }

            @Override
            public long contentLength() {
                try {
                    return uriStream.available();
                } catch (IOException e) {
                    return 0;
                }
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                Source source = null;
                try {
                    source = Okio.source(uriStream);
                    sink.writeAll(source);
                } finally {
                    Util.closeQuietly(source);
                }
            }
        };


        //buffer_stream.toByteArray().toRequestBody();
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        URL api_url = new URL(sharedPreferences.getString("upload_url", getResources().getString(R.string.default_url)));
        String filename = uri.getLastPathSegment();
        Log.i("upload", "filename "+filename);

        String form_name = sharedPreferences.getString("parameter", "file");
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(form_name, filename, file_part);

        String api_key = sharedPreferences.getString("api_key", null);
        if (api_key != null && !api_key.isEmpty()) {
            builder.addFormDataPart("key", api_key);
        }

        RequestBody requestBody = builder.build();

        Request request = new Request.Builder()
                .url(api_url)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                try {
                    uriStream.close();
                } catch (IOException ec) {
                }
                final String mMessage = e.toString();
                //Log.e(LOG_TAG, mMessage); // no need inside run()
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.e("upload", "http failure "+mMessage);
                        textView.setText(mMessage); // must be inside run()
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                uriStream.close();
                final String s = response.body().string();
                String turl = "";
                String extractor = sharedPreferences.getString("extractor", "teknik");
                Log.i("upload" , "extractor: "+extractor);
                if (extractor.equals("teknik")) {
                    try {
                        JSONObject j = new JSONObject(s);
                        JSONObject r = j.getJSONObject("result");
                        if (r == null) {
                            throw new JSONException("no result: " + s);
                        }
                        turl = r.getString("url");
                    } catch (JSONException e) {
                        Log.e("upload", "json problem", e);
                    }
                } else if (extractor.equals("upste")) {
                    try {
                        JSONObject j = new JSONObject(s);
                        turl = j.getString("url");
                    } catch (JSONException e) {
                        Log.e("upload", "json problem", e);
                    }
                } else if (extractor.equals("pomf")) {
                    try {
                        JSONObject j = new JSONObject(s);
                        Log.i("upload" , "json parsed");
                        JSONArray a = j.getJSONArray("files");
                        Log.i("upload" , "files array ok");
                        JSONObject r = a.getJSONObject(0);
                        Log.i("upload" , "files object ok");

                        if (r == null) {
                            throw new JSONException("no result: " + s);
                        }
                        turl = r.getString("url");
                        Log.i("upload" , "string ok "+turl);
                    } catch (JSONException e) {
                        Log.e("upload", "json problem", e);
                    }
                } else if (extractor.equals("plain")) {
                    turl = s;
                } else if (extractor.equals("regex")) {
                    Pattern p = Pattern.compile(sharedPreferences.getString("regex", "\"url\": ?\"([^\"]+)\""));
                    Matcher m = p.matcher(s);
                    turl = m.group(1);
                }

                final String url = sharedPreferences.getString("prepend", "") + turl;

                if (url.equals("")) {
                    Log.e("upload" , "URL not extracted "+s);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            textView.setText("couldn't get an URL, attach logcat or something\n"+s);
                        }
                    });
                    return;
                }

                Log.i("upload", "http ok! "+url);

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText(url); // must be inside run()

                        UploadedDb dbHelper = new UploadedDb(ShareActivity.this);
                        SQLiteDatabase db = dbHelper.getWritableDatabase();
                        ContentValues values = new ContentValues();
                        values.put("url", url);
                        long newRowId = db.insert("uploaded", null, values);
                        db.close();

                        Log.i("upload", "sent broadcast");
                        Intent intent = new Intent("updated");
                        sendBroadcast(intent);

                        if (sharedPreferences.getBoolean("clipboard", true)) {
                            toClipboard(url);
                        }

                        if (sharedPreferences.getBoolean("open_browser", true)) {
                            Intent i = new Intent(Intent.ACTION_VIEW);
                            i.setData(Uri.parse(url));
                            //i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            finish();
                            startActivity(i);
                        } else {
                            finish();
                        }
                    }
                });
            }
        });

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("upload", "creating share activity...");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);
        mHandler = new Handler(Looper.getMainLooper());

        Intent intent = getIntent();
        String action = intent.getAction();
        String mime = intent.getType();

        //Bundle bundle = intent.getExtras();
        //for (String key : bundle.keySet()){
        //    Log.d("upload", "Extra " + key + " -> " + bundle.get(key));
        //}
        Log.d("upload", "Data " + intent.getDataString());

        final TextView textView = findViewById(R.id.textView);
        textView.setText("uploading... " + mime);

        if (Intent.ACTION_SEND.equals(action) && mime != null) {
            try {
                doUpload(intent);
            } catch (Exception e) {
                Log.e("upload", "something bad", e);
                textView.setText("oops! " + e);
            }
        } else {
            Log.i("upload", "unknown share action");
            textView.setText("weird share action");
        }

    }
}
