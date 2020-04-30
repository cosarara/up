package me.cosarara.up;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    public void toClipboard(String url) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("URL", url);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(this, "copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    protected void removeRecord(int id) {
        UploadedDb dbHelper = new UploadedDb(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String selection = "_id=?";
        String[] selectionArgs = { ""+id };
        int deletedRows = db.delete("uploaded", selection, selectionArgs);
        db.close();
    }

    public class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder> {
        private ArrayList<Item> mDataset;
        //private View.OnClickListener mClick;
        //private View.OnLongClickListener mLongClick;

        public class MyViewHolder extends RecyclerView.ViewHolder {
            public TextView textView;
            public MyViewHolder(TextView v) {
                super(v);
                textView = v;
            }
        }

        //public MyAdapter(ArrayList<Item> myDataset, View.OnClickListener clistener) {
        public MyAdapter(ArrayList<Item> myDataset) {
            mDataset = myDataset;
            //mClick = clistener;
        }

        // Create new views (invoked by the layout manager)
        @Override
        public MyAdapter.MyViewHolder onCreateViewHolder(ViewGroup parent,
                                                         int viewType) {
            // create a new view
            TextView v = (TextView) LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);

            //v.setOnClickListener(mClick);

            MyViewHolder vh = new MyViewHolder(v);
            return vh;
        }

        // Replace the contents of a view (invoked by the layout manager)
        @Override
        public void onBindViewHolder(MyViewHolder holder, final int position) {
            holder.textView.setText(mDataset.get(position).url);
            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    //removeAt(position);
                    String url = mDataset.get(position).url;
                    toClipboard(url);
                    return true;
                }
            });
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String url = mDataset.get(position).url;
                    Toast.makeText(MainActivity.this, url, Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(url));
                    startActivity(i);
                }
            });
        }

        public void removeAt(int position) {
            removeRecord(mDataset.get(position).id);
            mDataset.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, mDataset.size());
        }

        public void reset(ArrayList<Item> new_data) {
            mDataset = new_data;
            notifyDataSetChanged();
        }

        // Return the size of your dataset (invoked by the layout manager)
        @Override
        public int getItemCount() {
            return mDataset.size();
        }
    }

    final class Item {
        String url;
        int id;
        public Item(int i, String u) {url=u; id=i;}
    }

    private ArrayList<Item> getUrls() {
        UploadedDb dbHelper = new UploadedDb(this);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String[] projection = {
                "_id",
                "url"
        };
        Cursor cursor = db.query("uploaded", projection, "", null,
                null, null, "_id DESC");
        final ArrayList<Item> urls = new ArrayList<>();
        while(cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
            String url = cursor.getString(cursor.getColumnIndexOrThrow("url"));
            Item item = new Item(id, url);
            urls.add(item);
        }
        cursor.close();
        db.close();
        return urls;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final ArrayList<Item> urls = getUrls();

        final RecyclerView recyclerView = findViewById(R.id.list);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        final MyAdapter adapter = new MyAdapter(urls);
        recyclerView.setAdapter(adapter);

        ItemTouchHelper ith = new ItemTouchHelper(
            new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT ) {
                public boolean onMove(RecyclerView recyclerView,
                                               RecyclerView.ViewHolder viewHolder,
                                               RecyclerView.ViewHolder target) {
                    return false;
                }
                public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                    // remove from adapter
                    final int pos = viewHolder.getAdapterPosition();
                    adapter.removeAt(pos);
                }
            });
        ith.attachToRecyclerView(recyclerView);

        // let ShareActivity make us reload
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent intent) {
                String action = intent.getAction();
                if (action.equals("updated")) {
                    Log.i("upload", "got broadcast");
                    adapter.reset(getUrls());
                }
            }
        };
        registerReceiver(broadcastReceiver, new IntentFilter("updated"));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.settings:
                //openSettings();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
