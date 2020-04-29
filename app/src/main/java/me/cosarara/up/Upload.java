package me.cosarara.up;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

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

public class Upload extends IntentService {

    private static final String EXTRA_PENDING = "me.cosarara.up.extra.PENDING";
    private static final String EXTRA_URI = "me.cosarara.up.extra.URI";
    private static final String EXTRA_MIME = "me.cosarara.up.extra.MIME";
    private static final String EXTRA_UP_URL = "me.cosarara.up.extra.UP_URL";

    private final OkHttpClient client = new OkHttpClient();

    public Upload() {
        super("Upload");
    }

    public static void startUpload(Context context, Uri uri, String mime, PendingIntent pend) {
        Intent intent = new Intent(context, Upload.class);
        intent.putExtra(EXTRA_URI, uri);
        intent.putExtra(EXTRA_MIME, mime);
        intent.putExtra(EXTRA_PENDING, pend);
        context.startService(intent);
    }

    /*
    // java seriously sucks
    private void copyStream(InputStream in, OutputStream out) throws IOException {
        int length;
        byte[] bytes = new byte[1024];

        // copy data from input stream to output stream
        while ((length = in.read(bytes)) != -1) {
            out.write(bytes, 0, length);
        }
    }

    // like seriously is this not a standard library function?
    private String streamToString(InputStream in) {
        Scanner s = new Scanner(in).useDelimiter("\\A");
        String result = s.hasNext() ? s.next() : "";
        return result;
    }
    */

    private String upload(Uri uri, String mime) throws Exception {
        final InputStream uriStream = getContentResolver().openInputStream(uri);
        //ByteArrayOutputStream buffer_stream = new ByteArrayOutputStream();
        //this.copyStream(uriStream, buffer_stream);

        Log.i("upload", "mime: " + mime);
        if (mime == null) {
            mime = "application/octet-stream";
        }
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

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, file_part)
                .build();

        Request request = new Request.Builder()
                .url(api_url)
                .post(requestBody)
                .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful())
            throw new IOException("Unexpected code " + response);

        String s = response.body().string();
        Log.d("POST", s);

/*
        HttpURLConnection con = (HttpURLConnection) api_url.openConnection();

        try {
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", mime);
            con.setDoOutput(true);
            con.setChunkedStreamingMode(0);

            OutputStream out = new BufferedOutputStream(con.getOutputStream());
            this.copyStream(uriStream, out);

            InputStream in = new BufferedInputStream(con.getInputStream());
            String s = streamToString(in);

 */

            Log.i("upload", "json result: "+s);
            JSONObject j = new JSONObject(s);
            JSONObject r = j.getJSONObject("result");
            if (r == null) {
                throw new Exception("no result: "+s);
            }
            String url = r.getString("url");
            return url;
        //} finally {
        //    con.disconnect();
        //}
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            PendingIntent reply = intent.getParcelableExtra(EXTRA_PENDING);
            final Uri uri = intent.getParcelableExtra(EXTRA_URI);
            final String mime = intent.getParcelableExtra(EXTRA_MIME);
            try {
                try {
                    Log.i("upload", "upload started");
                    String up_url = this.upload(uri, mime);

                    Log.i("upload", "upload finished "+up_url);

                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(up_url));
                    startActivity(i);

                    //Intent result = new Intent();
                    //result.putExtra(EXTRA_UP_URL, up_url);
                    //reply.send(this, 0, result);
                } catch (Exception exc) {
                    Log.e("upload", "sth", exc);
                    reply.send(2);
                }
            } catch (PendingIntent.CanceledException exc) {
                Log.e("upload", "reply cancelled", exc);
            }
        }
    }
}
