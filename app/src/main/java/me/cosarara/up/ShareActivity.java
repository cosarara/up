package me.cosarara.up;

import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

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
            // try to get something better
            String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (extension != null) {
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
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

        URL api_url = new URL("https://api.teknik.io/v1/Upload");
        String filename = uri.getLastPathSegment();
        Log.i("upload", "filename "+filename);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, file_part)
                .build();

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
                final String url = turl;

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

                        toClipboard(url);

                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(url));
                        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(i);
                    }
                });
            }
        });

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);
        mHandler = new Handler(Looper.getMainLooper());

        Intent intent = getIntent();
        String action = intent.getAction();
        String mime = intent.getType();

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
            textView.setText("weird share action");
        }

    }
}
