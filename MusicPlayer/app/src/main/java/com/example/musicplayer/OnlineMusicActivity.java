package com.example.musicplayer;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.example.musicplayer.useLitepal.OnlineMusic;

import org.json.JSONArray;
import org.json.JSONObject;
import org.litepal.LitePal;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OnlineMusicActivity extends AppCompatActivity implements View.OnClickListener{

    private TextView musicCountView;
    private ListView musicListView;
    private ImageView playingImgView;
    private TextView playingTitleView;
    private TextView playingArtistView;
    private ImageView btn_playOrPause;

    private List<Music> onlinemusic_list;
    private MusicService.MusicServiceIBinder service;
    private MusicAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_localmusic);

        //初始化
        initActivity();

        // 列表项点击事件
        musicListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Music music = onlinemusic_list.get(position);
                service.addPlayList(music);
            }
        });

        //列表项中更多按钮的点击事件
        adapter.setOnMoreButtonListener(new MusicAdapter.onMoreButtonListener() {
            @Override
            public void onClick(final int i) {
                final Music music = onlinemusic_list.get(i);
                final String[] items = new String[] {"收藏到我的音乐", "添加到播放列表", "删除"};
                AlertDialog.Builder builder = new AlertDialog.Builder(OnlineMusicActivity.this);
                builder.setTitle(music.title+"-"+music.artist);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case 0:
                                MainActivity.addMymusic(music);
                                break;
                            case 1:
                                service.addPlayList(music);
                                break;
                            case 2:
                                //从列表和数据库中删除
                                onlinemusic_list.remove(i);
                                LitePal.deleteAll(OnlineMusic.class, "title=?", music.title);
                                adapter.notifyDataSetChanged();
                                musicCountView.setText("播放全部(共"+onlinemusic_list.size()+"首)");
                                break;
                        }
                    }
                });
                builder.create().show();
            }
        });

    }

    // 监听组件
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.play_all:
                service.addPlayList(onlinemusic_list);
                break;
            case R.id.player:
                Intent intent = new Intent(OnlineMusicActivity.this, PlayerActivity.class);
                startActivity(intent);
                //弹出动画
                overridePendingTransition(R.anim.bottom_in, R.anim.bottom_silent);
                break;
            case R.id.play_or_pause:
                if (service.isPlaying()){
                    service.pause();
                }
                else {
                    service.play();
                }
                break;
            case R.id.playing_list:
                showPlayList();
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onlinemusic_list.clear();
        unbindService(mServiceConnection);
    }

    // 初始化活动
    private void initActivity(){
        //初始化控件
        ImageView btn_playAll = this.findViewById(R.id.play_all);
        musicCountView = this.findViewById(R.id.play_all_title);
        musicListView = this.findViewById(R.id.music_list);
        RelativeLayout playerToolView = this.findViewById(R.id.player);
        playingImgView = this.findViewById(R.id.playing_img);
        playingTitleView = this.findViewById(R.id.playing_title);
        playingArtistView = this.findViewById(R.id.playing_artist);
        btn_playOrPause = this.findViewById(R.id.play_or_pause);
        ImageView btn_playingList = this.findViewById(R.id.playing_list);

        // 设置监听
        btn_playAll.setOnClickListener(this);
        playerToolView.setOnClickListener(this);
        btn_playOrPause.setOnClickListener(this);
        btn_playingList.setOnClickListener(this);

        //绑定播放服务
        Intent i = new Intent(this, MusicService.class);
        bindService(i, mServiceConnection, BIND_AUTO_CREATE);

        // 使用ToolBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("网易云热歌榜");
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setSupportActionBar(toolbar);

        //从数据库获取保存的本地音乐列表
        onlinemusic_list = new ArrayList<>();
        List<OnlineMusic> list = LitePal.findAll(OnlineMusic.class);
        Bitmap img;
        for (OnlineMusic s:list){
            img = BitmapFactory.decodeByteArray(s.img,0,s.img.length);
            Music m = new Music(s.songUrl, s.title, s.artist, s.duration, 0, img);
            onlinemusic_list.add(m);
        }

        // 在线音乐列表绑定适配器
        adapter = new MusicAdapter(this, R.layout.music_item, onlinemusic_list);
        musicListView.setAdapter(adapter);

        musicCountView.setText("播放全部(共"+onlinemusic_list.size()+"首)");

        //获取音乐
        getOlineMusicList();
    }


    // 显示当前正在播放的音乐
    private void showPlayList(){
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        //设计对话框的显示标题
        builder.setTitle("播放列表");

        //获取播放列表
        final List<Music> playingList = service.getPlayingList();

        if(playingList.size() > 0) {
            //播放列表有曲目，显示所有音乐
            final PlayingMusicAdapter playingAdapter = new PlayingMusicAdapter(this, R.layout.playinglist_item, playingList);
            builder.setAdapter(playingAdapter, new DialogInterface.OnClickListener() {
                //监听列表项点击事件
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    service.addPlayList(playingList.get(which));
                }
            });

            //列表项中删除按钮的点击事件
            playingAdapter.setOnDeleteButtonListener(new PlayingMusicAdapter.onDeleteButtonListener() {
                @Override
                public void onClick(int i) {
                    service.removePlayList(i);
                    playingAdapter.notifyDataSetChanged();
                }
            });
        }
        else {
            //播放列表没有曲目，显示没有音乐
            builder.setMessage("没有正在播放的音乐");
        }

        //设置该对话框是可以自动取消的，例如当用户在空白处随便点击一下，对话框就会关闭消失
        builder.setCancelable(true);

        //创建并显示对话框
        builder.create().show();
    }

    // 定义与服务的连接的匿名类
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        //绑定成功时调用
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            //绑定成功后，取得MusicSercice提供的接口
            OnlineMusicActivity.this.service = (MusicService.MusicServiceIBinder) service;

            //注册监听器
            ((MusicService.MusicServiceIBinder) service).registerOnStateChangeListener(listenr);

            Music item = ((MusicService.MusicServiceIBinder) service).getCurrentMusic();

            if (((MusicService.MusicServiceIBinder) service).isPlaying()){
                //如果正在播放音乐, 更新控制栏信息
                btn_playOrPause.setImageResource(R.drawable.zanting);
                playingTitleView.setText(item.title);
                playingArtistView.setText(item.artist);
                if (item.img != null)   playingImgView.setImageBitmap(item.img);
            }
            else if (item != null){
                //当前有可播放音乐但没有播放
                btn_playOrPause.setImageResource(R.drawable.bofang);
                playingTitleView.setText(item.title);
                playingArtistView.setText(item.artist);
                if (item.img != null)   playingImgView.setImageBitmap(item.img);
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            //断开连接时注销监听器
            service.unregisterOnStateChangeListener(listenr);
        }
    };

    // 实现监听器监听MusicService的变化，
    private MusicService.OnStateChangeListenr listenr = new MusicService.OnStateChangeListenr() {

        @Override
        public void onPlayProgressChange(Music item) {
        }

        @Override
        public void onPlay(Music item) {
            //播放状态变为播放时
            btn_playOrPause.setImageResource(R.drawable.zanting);
            playingTitleView.setText(item.title);
            playingArtistView.setText(item.artist);
            btn_playOrPause.setEnabled(true);
            if (item.img != null)   playingImgView.setImageBitmap(item.img);
        }

        @Override
        public void onPause(Music item) {
            //播放状态变为暂停时
            btn_playOrPause.setImageResource(R.drawable.bofang);
            btn_playOrPause.setEnabled(true);
        }
    };

    // 获取在线音乐
    private void getOlineMusicList() {

        //在子线程中获取音乐
        new Thread( new Runnable() {
            @Override
            public void run() {

                HttpURLConnection connection = null;
                try {
                    // 调用URL对象的openConnection方法获取HttpURLConnection的实例
                    String songUrl = "https://api.bzqll.com/music/netease/songList?key=579621905&id=3778678&limit=200&offset=0";
                    URL url = new URL(songUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    // 设置连接超时、读取超时的时间，单位为毫秒（ms）
                    connection.setConnectTimeout(80000);
                    connection.setReadTimeout(80000);
                    // getInputStream方法获取服务器返回的输入流
                    InputStream in = connection.getInputStream();
                    // 使用BufferedReader对象读取返回的数据流
                    // 按行读取，存储在StringBuider对象response中
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    //构造json数据
                    JSONObject songJSON = new JSONObject(response.toString());
                    String songdata = songJSON.getString("data");
                    JSONObject dataJSON = new JSONObject(songdata);
                    // 真正要需要的数据存储在以下songs数组中
                    JSONArray songsArray = dataJSON.getJSONArray("songs");

                    Bitmap bitmap = null;
                    for(int i=0; i < songsArray.length(); i++) {
                        JSONObject songInfo = songsArray.getJSONObject(i);
                        // 获取歌曲各类别信息
//                        String id = songInfo.getString("id");
                        String songurl = songInfo.getString("url")+"&br=128000";
                        String name = songInfo.getString("name");
                        String singer = songInfo.getString("singer");
                        URL pic = new URL(songInfo.getString("pic"));
//                        Uri lrc = Uri.parse(songInfo.getString("lrc"));

                        //如果列表中已存在同一首歌曲, 则不在获取这首歌
                        boolean flag = false;
                        for (Music m:onlinemusic_list){
                            if (m.title.equals(name))  flag = true;
                        }
                        if (flag) continue;

                        //获取图片
                        OkHttpClient client = new OkHttpClient.Builder()
                                .connectTimeout(20, TimeUnit.SECONDS)
                                .readTimeout(20, TimeUnit.SECONDS)
                                .build();
                        Request request = new Request.Builder()
                                .url(pic)
                                .build();
                        Response response1 = client.newCall(request).execute();
                        if (response1.isSuccessful()) {
                            InputStream inputStream = response1.body().byteStream();
                            bitmap = BitmapFactory.decodeStream(inputStream);
                            //压缩
                            bitmap = Bitmap.createScaledBitmap(bitmap, 250, 250, true);
                            inputStream.close();
                        }
                        
                        //实例化音乐类
                        Music song = new Music(songurl, name, singer, 0, 0, bitmap);
                        //送入主线程更新列表
                        uploadView(song);
                    }
                } catch (Exception e){
                    e.printStackTrace();

                } finally {
                    if (connection != null){
                        // 结束后，关闭连接
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }

    // 主线程更新UI
    private void uploadView(final Music music){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //切换到主线程, 添加到列表和数据库并更新UI
                onlinemusic_list.add(music);
                OnlineMusic onlineMusic = new OnlineMusic(music.songUrl, music.title, music.artist, music.duration, music.played, Utils.byteImg(music.img));
                onlineMusic.save();
                MusicAdapter adapter = (MusicAdapter) musicListView.getAdapter();
                adapter.notifyDataSetChanged();
                musicCountView.setText("播放全部(共"+onlinemusic_list.size()+"首)");
            }
        });
    }

    // 返回按钮
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}