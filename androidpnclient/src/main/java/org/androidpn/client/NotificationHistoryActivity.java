package org.androidpn.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.androidpn.demoapp.R;
import org.litepal.crud.DataSupport;

import java.util.ArrayList;
import java.util.List;

/**
 * 推送历史界面
 * Created by rongwenzhao on 2018/1/18.
 */

public class NotificationHistoryActivity extends Activity {

    private ListView mListView;
    private NotificationHistoryAdapter mAdapter;
    private List<NotificationHistory> mList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notification_history);
        mList = DataSupport.findAll(NotificationHistory.class);
        mListView = findViewById(R.id.list_view);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                NotificationHistory history = mList.get(position);
                Intent intent = new Intent(parent.getContext(),
                        NotificationDetailsActivity.class);
                intent.putExtra(Constants.NOTIFICATION_API_KEY, history.getApiKey());
                intent.putExtra(Constants.NOTIFICATION_TITLE, history.getTitle());
                intent.putExtra(Constants.NOTIFICATION_MESSAGE, history.getMessage());
                intent.putExtra(Constants.NOTIFICATION_URI, history.getUri());
                intent.putExtra(Constants.NOTIFICATION_IMAGE_URL, history.getImageUrl());
                startActivity(intent);
            }
        });
        mAdapter = new NotificationHistoryAdapter(this, 0, mList);
        mListView.setAdapter(mAdapter);
        //假设一个View注冊了上下文菜单。那么当长按该View时便会弹出一个浮动菜单，来供选择下一步操作。
        registerForContextMenu(mListView);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, 0, 0, "Remove");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == 0) {//Remove
            AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            int index = menuInfo.position;
            NotificationHistory history = mList.get(index);
            history.delete();
            mList.remove(index);
            mAdapter.notifyDataSetChanged();
        }
        return super.onContextItemSelected(item);
    }

    class NotificationHistoryAdapter extends ArrayAdapter<NotificationHistory> {

        public NotificationHistoryAdapter(@NonNull Context context, int resource, @NonNull List<NotificationHistory> objects) {
            super(context, resource, objects);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            NotificationHistory history = getItem(position);
            View view;
            if (convertView == null) {
                view = LayoutInflater.from(getContext()).inflate(R.layout.notification_history_item, null);
            } else {
                view = convertView;
            }

            TextView titleTextView = view.findViewById(R.id.tv_title);
            TextView timeTextView = view.findViewById(R.id.tv_time);
            titleTextView.setText(history.getTitle());
            timeTextView.setText(history.getTime());
            return view;
        }
    }
}
