package com.example.subtitle;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.text.ClipboardManager;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import viewlp.MainActivity;

/**
 * 1、实现匹配srt字幕
 * 2、设置ActivityInfo的sensor来实现横竖屏，然后进行VideoView的大小屏切换，即使横竖屏切换被禁止也能用，
 * Created by gaolei on 17/3/13.
 */
public class SubtitleActivity extends Activity implements View.OnClickListener,OnTouchListener{

	private String kedu = "0";

	private VideoView videoView ;
	TextView tvSrt, mCurrentTime,mTotalTime,resolution_switch,mediacontroller_file_name;
	ImageView mediacontroller_play_pause,switch_screen;
	private SeekBar progress_seekbar;
	private AudioManager mAM;
	private long totalDuration;
	private boolean mShowing = true, mDragging,isResolution;
	private static final int PARSE_SRT = 0;
	private static final int FADE_OUT = 1;
	private static final int SHOW_PROGRESS = 2;
	private static final int CHANGE_VIDEOVIEW_BG = 3;
	private static final int SCREEN_ORIENTATION_USER = 4;

	private static final int sDefaultTimeout = 6000;
	private RelativeLayout videoview_layout, mMediaController;
	private ListView resolution_listview;
	private boolean isPortraint = true;
	private static int LockScreen = -1;// 用于记录是否关闭屏幕旋转，0为关闭1为开启
	private int screenWidth,videoViewHeight;
	List<VideoPathObject> videopathList=new ArrayList<VideoPathObject>();

	Handler mHandler=new Handler(){
		public void handleMessage(Message msg){
			long pos;
			switch (msg.what) {
				case PARSE_SRT:
					SrtParser.showSRT(videoView,tvSrt) ;
					//每隔500ms执行一次showSRT()
					mHandler.sendEmptyMessageDelayed(0, 500);
					break;
				case FADE_OUT:
					showOrHideController();
					break;
				case SHOW_PROGRESS:
					pos = setControllerProgress();
					if (!mDragging && mShowing) {
						msg = obtainMessage(SHOW_PROGRESS);
						sendMessageDelayed(msg, 1000 - (pos % 1000));
					}

					break;
				case CHANGE_VIDEOVIEW_BG:
					videoView.setBackgroundColor(0x00000000);
					break;
			}

		}
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_subtitle);
		videoView = (VideoView)this.findViewById(R.id.videoView );
		mAM = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		screenWidth = APPApplication.screenWidth;
		videoViewHeight = screenWidth * 9 / 16;
		tvSrt = (TextView)findViewById(R.id.srt);//项目中显示字幕的控件

		//给字幕控件加上点击事件
		tvSrt.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {

				//TextView tvSrt11 = (TextView)findViewById(R.id.srt);
				SubtitleActivity.this.getEachWord(tvSrt);
				tvSrt.setMovementMethod(LinkMovementMethod.getInstance());
			}
		});


		mediacontroller_file_name= (TextView)findViewById(R.id.mediacontroller_file_name);
//		String[]splitStr=Constant.videoUrl1.split("/");
//		mediacontroller_file_name.setText(splitStr[splitStr.length-1]);
		mTotalTime = (TextView) findViewById(R.id.mediacontroller_time_total);
		mCurrentTime = (TextView) findViewById(R.id.mediacontroller_time_current);
		resolution_switch = (TextView) findViewById(R.id.resolution_switch);
		mediacontroller_play_pause = (ImageView) findViewById(R.id.mediacontroller_play_pause);
		switch_screen = (ImageView) findViewById(R.id.switch_screen);
		videoview_layout = (RelativeLayout) findViewById(R.id.videoview_layout);
		mediacontroller_play_pause.setOnClickListener(this);
		progress_seekbar = (SeekBar) findViewById(R.id.mediacontroller_seekbar);
		videoview_layout = (RelativeLayout) findViewById(R.id.videoview_layout);
		mMediaController = (RelativeLayout) findViewById(R.id.media_controller);
		resolution_listview = (ListView) findViewById(R.id.resolution_listview);
		resolution_switch.setOnClickListener(this);
		videoView.setOnTouchListener(this);
		progress_seekbar.setOnSeekBarChangeListener(mSeekListener);
		LayoutParams params = new RelativeLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, videoViewHeight);
		videoview_layout.setLayoutParams(params);
		try {
			// 1代表开启自动旋转true，0代表未开启自动旋转false
			// Settings.System.getInt(mContext.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,0);
			LockScreen = Settings.System.getInt(getContentResolver(),
					Settings.System.ACCELEROMETER_ROTATION);
		} catch (SettingNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		String rawUri = "android.resource://" + getPackageName() + "/" + R.raw.testhw;
		Uri uri = Uri.parse(Constant.videoUrl1);
		//设置视频控制器
//        videoView.setMediaController(new MediaController(this));
		//播放完成回调
		videoView.setOnCompletionListener( new MyPlayerOnCompletionListener());
		videoView.setOnPreparedListener(new OnPreparedListener() {

			//@Override
			public void onPrepared(MediaPlayer mp) {
				totalDuration=videoView.getDuration();
				if (mTotalTime != null)
					mTotalTime.setText("/"+generateTime(totalDuration));
			}
		});


		//设置视频路径
		/*videoView.setVideoURI(uri);
		//开始播放视频
		videoView.start();
		SrtParser.parseSrt(this);
		SrtParser.showSRT(videoView,tvSrt) ;

		mHandler.sendEmptyMessageDelayed(0, 500);*/

		initVideoResolution();
	}


	public void loadPath(View view) {
		EditText videoPath = (EditText)findViewById(R.id.videoPath);
		EditText zimuPath = (EditText)findViewById(R.id.zimuPath);
		//Uri uri = Uri.parse("/storage/emulated/0/privatefile/"+et.getText());
		Constant.videoUrl1 = videoPath.getText() + "";
		Constant.srtUrl1 = zimuPath.getText() + "";


		//设置视频路径
		Uri uri = Uri.parse(Constant.videoUrl1);
		videoView.setVideoURI(uri);

		File f=new File(uri.getPath());
		if(!f.exists()){
			Toast.makeText( SubtitleActivity.this, "不存在"+uri.getPath(), Toast.LENGTH_SHORT).show();
		}else{
			Toast.makeText( SubtitleActivity.this, "视频加载成功", Toast.LENGTH_SHORT).show();
		}


		//开始播放视频
		videoView.start();
		SrtParser.parseSrt(this);
		SrtParser.showSRT(videoView,tvSrt) ;

		mHandler.sendEmptyMessageDelayed(0, 500);
	}

	//选择视频文件
	public void showVideoChooser(View view) {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("*/*");
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		try {
			startActivityForResult( Intent.createChooser(intent, "Select a File to Upload"), 0);
		} catch (android.content.ActivityNotFoundException ex) {
			// Potentially direct the user to the Market with a Dialog
			Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
		}
	}

	//选择字幕文件
	public void showZimuChooser(View view) {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("*/*");
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		try {
			startActivityForResult( Intent.createChooser(intent, "Select a File to Upload"), 1);
		} catch (android.content.ActivityNotFoundException ex) {
			// Potentially direct the user to the Market with a Dialog
			Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
		}
	}

	//复制整句
	public void copyOneContent(View view) {
		TextView srt = (TextView)findViewById(R.id.srt);
		ClipboardManager cmb = (ClipboardManager) SubtitleActivity.this
				.getSystemService(Context.CLIPBOARD_SERVICE);
		cmb.setText(srt.getText());
		Toast.makeText( SubtitleActivity.this, "复制整句成功: " + srt.getText(), Toast.LENGTH_SHORT).show();
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case 0:
				if (resultCode == RESULT_OK) {
					Uri uri = data.getData();
					String path = FileUtils.getPathByUri4kitkat(this, uri);
					EditText videoPath = (EditText)findViewById(R.id.videoPath);
					videoPath.setText(path);
				}
				break;
			case 1:
				if (resultCode == RESULT_OK) {
					Uri uri = data.getData();
					String path = FileUtils.getPathByUri4kitkat(this, uri);
					EditText videoPath = (EditText)findViewById(R.id.zimuPath);
					videoPath.setText(path);
				}
				break;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}



	private void initVideoResolution(){
		VideoPathObject object1=new VideoPathObject();
		object1.videoStatus="超清";
		videopathList.add(object1);
		VideoPathObject object2=new VideoPathObject();
		object2.videoStatus="高清";
		videopathList.add(object2);
		VideoPathObject object3=new VideoPathObject();
		object3.videoStatus="标清";
		videopathList.add(object3);
		switchResolution(videopathList);
	}

	class MyPlayerOnCompletionListener implements MediaPlayer.OnCompletionListener {

		@Override
		public void onCompletion(MediaPlayer mp) {
			Toast.makeText( SubtitleActivity.this, "播放完成了", Toast.LENGTH_SHORT).show();
		}
	}

	private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
		public void onStartTrackingTouch(SeekBar bar) {
			mDragging = true;
			mHandler.removeMessages(SHOW_PROGRESS);
			mAM.setStreamMute(AudioManager.STREAM_MUSIC, true);
		}

		public void onProgressChanged(SeekBar bar, int progress,
									  boolean fromuser) {
			if (!fromuser)
				return;

			int newposition = (int)(totalDuration * progress) / 1000;

			String time = generateTime(newposition);
			videoView.seekTo(newposition);
			mCurrentTime.setText(time);
		}

		public void onStopTrackingTouch(SeekBar bar) {
			videoView.seekTo(((int)totalDuration * bar.getProgress()) / 1000);
			hideMediaController(sDefaultTimeout);
			mAM.setStreamMute(AudioManager.STREAM_MUSIC, false);
			mDragging = false;
			mHandler.sendEmptyMessageDelayed(SHOW_PROGRESS, 1000);
		}
	};
	private void switchResolution(final List<VideoPathObject> videopathList) {
		resolution_switch
				.setText(videopathList.get(videopathList.size() - 1).videoStatus);
		mediacontroller_play_pause.setImageResource(R.drawable.player_play);
		final ResolutionAdapter adapter = new ResolutionAdapter(videopathList,
				SubtitleActivity.this);
		resolution_listview.setAdapter(adapter);
		resolution_listview
				.setOnItemClickListener(new AdapterView.OnItemClickListener() {

					@Override
					public void onItemClick(AdapterView<?> arg0, View arg1,
											int position, long arg3) {
						// TODO Auto-generated method stub
//						// currentPosition = videoView.getCurrentPosition();
//						currentPosition = videoView.getCurrentPosition();
//						Log.d("gaolei", "currentPosition---------1------"
//								+ currentPosition);
						VideoPathObject pathObject = videopathList
								.get(position);
////						playVideo(pathObject.videoUrl);
//						adapter.changePosition(position);
						resolution_switch.setText(pathObject.videoStatus);
						resolution_listview.setVisibility(View.GONE);
					}
				});

	}
	public void showOrHideController() {

		if (mShowing) {
			mHandler.removeMessages(SHOW_PROGRESS);
			mHandler.removeMessages(FADE_OUT);
			mMediaController.setVisibility(View.GONE);
			resolution_listview.setVisibility(View.GONE);
//			isResolution = false;
			mShowing = false;
		} else {
			mHandler.sendEmptyMessage(SHOW_PROGRESS);
			mMediaController.setVisibility(View.VISIBLE);
			hideMediaController(sDefaultTimeout);
			mShowing = true;
		}
	}
	public void hideMediaController(int sDefaultTimeout) {
		mHandler.sendEmptyMessageDelayed(FADE_OUT, sDefaultTimeout);
	}
	private long setControllerProgress() {
		if (videoView == null || mDragging)
			return 0;

		int position = videoView.getCurrentPosition();
		if (progress_seekbar != null) {
			if (totalDuration > 0) {
				long pos = 1000L * position / totalDuration;
				// Log.d("gaolei", "progress--------------" + pos);
				progress_seekbar.setProgress((int) pos);
			}
			int percent = videoView.getBufferPercentage();
			progress_seekbar.setSecondaryProgress(percent * 10);
		}

		if (mCurrentTime != null)
			mCurrentTime.setText(generateTime(position));

		return position;
	}
	private static String generateTime(long position) {
		int totalSeconds = (int) (position / 1000);

		int seconds = totalSeconds % 60;
		int minutes = (totalSeconds / 60) % 60;
		int hours = totalSeconds / 3600;
		if (hours > 0) {
			return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes,
					seconds).toString();
		} else {
			return String.format(Locale.US, "%02d:%02d", minutes, seconds)
					.toString();
		}
	}
	private void updatePausePlay() {
		if (videoView.isPlaying()) {
			videoView.pause();
			mediacontroller_play_pause
					.setImageResource(R.drawable.player_pause);
		} else {
			videoView.start();
			mediacontroller_play_pause.setImageResource(R.drawable.player_play);
		}
	}
	public void showResolution(View view) {
		if (!isResolution) {
			resolution_listview.setVisibility(View.VISIBLE);
			isResolution = true;
		} else {
			resolution_listview.setVisibility(View.GONE);
			isResolution = false;
		}
	}
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		RelativeLayout layout_1 = (RelativeLayout) findViewById(R.id.configPath);
		if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
			layout_1.setVisibility(View.GONE);//这一句即隐藏布局LinearLayout区域
			changeToFullScreen();
			Log.d("gaolei", "ORIENTATION_LANDSCAPE-------------");
		}
		if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
			layout_1.setVisibility(View.VISIBLE);//这一句即隐藏布局LinearLayout区域
			changeToSmallScreen();
			Log.d("gaolei", "ORIENTATION_PORTRAIT-------------");
		}
	}

	public void switchScreen(View view) {
		RelativeLayout layout_1 = (RelativeLayout) findViewById(R.id.configPath);
		if (isPortraint) {
			layout_1.setVisibility(View.GONE);//这一句即隐藏布局LinearLayout区域
			handToFullScreen();
		} else {
			layout_1.setVisibility(View.VISIBLE);//这一句即隐藏布局LinearLayout区域
			handToSmallScreen();
		}
	}

	public void handToSmallScreen() {
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		changeToSmallScreen();
		/**
		 * 这里点击按钮转屏，用户5秒内不转屏幕，将自动识别当前屏幕方向
		 */
		autoSwitchScreenOrientation();
	}

	public void handToFullScreen() {
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		changeToFullScreen();
		autoSwitchScreenOrientation();
	}

	private void changeToFullScreen() {
		isPortraint = false;
		LayoutParams params = new RelativeLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, APPApplication.screenWidth);
		videoview_layout.setLayoutParams(params);
		videoView.setLayoutParams(params);

		WindowManager.LayoutParams windowparams = getWindow().getAttributes();
		windowparams.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
		getWindow().setAttributes(windowparams);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
		switch_screen.setImageResource(R.drawable.player_switch_small);

	}

	private void changeToSmallScreen() {
		isPortraint = true;
		LayoutParams params = new RelativeLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, videoViewHeight);
		videoview_layout.setLayoutParams(params);
		videoView.setLayoutParams(params);
		WindowManager.LayoutParams windowparams = getWindow().getAttributes();
		windowparams.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().setAttributes(windowparams);
		getWindow()
				.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
		switch_screen.setImageResource(R.drawable.player_switch_big);

	}

	public void autoSwitchScreenOrientation() {
//		if (LockScreen == 1) {
		/*new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
				Log.d("gaolei", "SCREEN_ORIENTATION_FULL_SENSOR");				}
		}, 5000);*/
//		}
	}

	int count = 0;
	long firClick = 0;
	long secClick = 0;

	@Override
	public boolean onTouch(View v, MotionEvent event) {

		// TODO Auto-generated method stub
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN: {
				showOrHideController();
				updatePausePlay();
				//Toast.makeText( SubtitleActivity.this, "单击事件", Toast.LENGTH_SHORT).show();
				break;
			}
		}


		//双击的功能不太好使
		/*if(MotionEvent.ACTION_DOWN == event.getAction()){
			count++;
			if(count == 1){
				showOrHideController();
				firClick = System.currentTimeMillis();

			} else if (count == 2){
				showOrHideController();
				secClick = System.currentTimeMillis();
				if(secClick - firClick < 1000){
					//双击事件
					Toast.makeText( SubtitleActivity.this, "双击事件", Toast.LENGTH_SHORT).show();
					updatePausePlay();
				}
				count = 0;
				firClick = 0;
				secClick = 0;

			}
		}*/


		return false;
	}
	@Override
	public void onClick(View view) {
		// TODO Auto-generated method stub
		switch(view.getId()){
			case R.id.mediacontroller_play_pause:
				Log.d("gaolei", "mediacontroller_play_pause");
				updatePausePlay();
				break;
			case R.id.resolution_switch:
				resolution_listview.setVisibility(View.VISIBLE);
				break;
		}
	}
	public void jumpToMain(View view){
		startActivity(new Intent(this, MainActivity.class));
	}

	/*public void onRestart(){
		Log.d("测试","重启后的刻度："+kedu);
		//progress_seekbar.setProgress(Integer.parseInt(kedu));
		if(Integer.parseInt(kedu) > 0){
			videoView.start();
			videoView.seekTo(Integer.parseInt(kedu));
			kedu = "0";
		}
		//videoView.start();
		mediacontroller_play_pause.setImageResource(R.drawable.player_play);
		super.onRestart();
	}*/

	@Override
	protected void onResume() {
		if (Integer.parseInt(kedu) > 0) {
			Log.d("测试","重启后的刻度："+kedu);
			videoView.start();
			videoView.seekTo(Integer.parseInt(kedu));
			videoView.pause();
			kedu = "0";
		}
		super.onResume();
	}

	/*public void onStop(){
		super.onStop();
		videoView.pause();
		mediacontroller_play_pause.setImageResource(R.drawable.player_pause);
	}*/

	public void onPause(){
		kedu = videoView.getCurrentPosition() + "";
		Log.d("测试","停止后的刻度：" + videoView.getCurrentPosition());
		videoView.stopPlayback();
		mediacontroller_play_pause.setImageResource(R.drawable.player_pause);
		super.onPause();
	}



	//获取单个textview的单词
	public void getEachWord(TextView textView){
		Spannable spans = (Spannable)textView.getText();
		Integer[] indices = getIndices(
				textView.getText().toString().trim(), ' ');
		int start = 0;
		int end = 0;
		// to cater last/only word loop will run equal to the length of indices.length
		for (int i = 0; i <= indices.length; i++) {
			ClickableSpan clickSpan = getClickableSpan();
			// to cater last/only word
			end = (i < indices.length ? indices[i] : spans.length());
			spans.setSpan(clickSpan, start, end,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			start = end + 1;
		}
		//改变选中文本的高亮颜色
		textView.setHighlightColor(Color.BLUE);
	}
	private ClickableSpan getClickableSpan(){
		return new ClickableSpan() {
			@Override
			public void onClick(View widget) {
				TextView tv = (TextView) widget;
				String s = tv
						.getText()
						.subSequence(tv.getSelectionStart(),
								tv.getSelectionEnd()).toString();
				Log.d("tapped on:", s);
				Log.v("test123", s);
				Toast.makeText( SubtitleActivity.this, "翻译成功: " + s, Toast.LENGTH_SHORT).show();
				ClipboardManager cmb = (ClipboardManager) SubtitleActivity.this
						.getSystemService(Context.CLIPBOARD_SERVICE);
				cmb.setText(s);
			}
			@Override
			public void updateDrawState(TextPaint ds) {
				ds.setColor(Color.WHITE);
				ds.setUnderlineText(false);
			}
		};
	}

	public static Integer[] getIndices(String s, char c) {
		int pos = s.indexOf(c, 0);
		List<Integer> indices = new ArrayList<Integer>();
		while (pos != -1) {
			indices.add(pos);
			pos = s.indexOf(c, pos + 1);
		}
		return (Integer[]) indices.toArray(new Integer[0]);
	}



}
