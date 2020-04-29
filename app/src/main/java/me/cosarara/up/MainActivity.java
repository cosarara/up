package me.cosarara.up;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    protected void removeRecord(int id) {
        UploadedDb dbHelper = new UploadedDb(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String selection = "_id=?";
        String[] selectionArgs = { ""+id };
        int deletedRows = db.delete("uploaded", selection, selectionArgs);
    }

    public class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder> {
        private ArrayList<Item> mDataset;
        private View.OnClickListener mClick;
        private View.OnLongClickListener mLongClick;


        public class MyViewHolder extends RecyclerView.ViewHolder {
            public TextView textView;
            public MyViewHolder(TextView v) {
                super(v);
                textView = v;
            }
        }

        public MyAdapter(ArrayList<Item> myDataset, View.OnClickListener clistener) {
            mDataset = myDataset;
            mClick = clistener;
        }

        // Create new views (invoked by the layout manager)
        @Override
        public MyAdapter.MyViewHolder onCreateViewHolder(ViewGroup parent,
                                                         int viewType) {
            // create a new view
            TextView v = (TextView) LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);

            v.setOnClickListener(mClick);

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
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("URL", url);
                    clipboard.setPrimaryClip(clip);

                    Toast.makeText(MainActivity.this, "copied to clipboard", Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
        }

        public void removeAt(int position) {
            removeRecord(mDataset.get(position).id);
            mDataset.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, mDataset.size());
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        UploadedDb dbHelper = new UploadedDb(this);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String[] projection = {
                "_id",
                "url"
        };
        Cursor cursor = db.query("uploaded", projection, "", null,
                null, null, "_id DESC");
        //final ArrayList<String> urls = new ArrayList<>();
        final ArrayList<Item> urls = new ArrayList<>();
        while(cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
            String url = cursor.getString(cursor.getColumnIndexOrThrow("url"));
            Item item = new Item(id, url);
            urls.add(item);
        }
        cursor.close();

        final RecyclerView recyclerView = findViewById(R.id.list);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        View.OnClickListener clickListener = new View.OnClickListener() {
            public void onClick(View v) {
                int itemPosition = recyclerView.getChildLayoutPosition(v);
                String url = urls.get(itemPosition).url;
                Toast.makeText(MainActivity.this, url, Toast.LENGTH_SHORT).show();
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        };
        final MyAdapter adapter = new MyAdapter(urls, clickListener);
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
    }
}
