package eduard.wink.amazfitmusicplayer;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import clc.sliteplugin.flowboard.AbstractPlugin;
import clc.sliteplugin.flowboard.ISpringBoardHostStub;

public class MainActivity extends AbstractPlugin implements MediaPlayer.OnCompletionListener, MediaButtonIntentReceiver.Callbacks {

    private boolean isActive = false;
    private Context mContext;
    private View mView;

    private MediaPlayer mediaPlayer;
    //Timer handles automatically play next song
    private Timer timer;
    //Handler checks for Timeout
    private Handler handler = null;
    //Timer for AirPods 4x Tap
    private Timer airPodTimer;

    private List<String> directories;

    private ListView folderList;
    LinearLayout mainLayout, layoutVolume;
    private Button folderBtn, playBtn, nextBtn, prevBtn, playmodeBtn;
    private TextView txtSongName, txtVolume;
    private Boolean folderListOpen = false;

    private MediaButtonIntentReceiver mMediaButtonReceiver = null;
    private static MediaButtonIntentReceiver.Callbacks mediaButtonsCallback;

    private WidgetSettings widgetSettings;

    //Static variables which MainActivity reads
    private static boolean isRunning = false;
    private static boolean isMusicPlaying = false;
    private static boolean isButtonsDisabled = false;
    private static int currentVolume;
    private static String currentSongName = "";

    private Boolean isTimerRunning;
    private int currentSongId;
    private int currentPlaymode;
    private int playmode;
    private List<File> playlist;

    //For Airpods 4x Tap
    private String lastHeadphonesTouch = "";


    /*
     * Check if headset disconnect - if so pause the player and service
     */
    private BroadcastReceiver mNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(Constants.TAG, "MainActivity Headset disconnects");
            if( mediaPlayer.isPlaying() ) {
                pauseSong();
                stopMediaPlayerService();
            }
        }
    };


    /*
     *
     * Override and listener methods
     *
     */
    @SuppressLint("CommitPrefEdits")
    @Override
    public View getView(Context paramContext) {
        // Save Activity variables
        this.mContext = paramContext;

        Log.d(Constants.TAG, "MainActivity getView");

        this.mView = LayoutInflater.from(paramContext).inflate(R.layout.activity_main, null);

        widgetSettings = new WidgetSettings(Constants.TAG, mContext);
        widgetSettings.reload();

        initView();
        initButtons();

        initMusicFiles();

        return this.mView;

    }

    private boolean checkForInactivity() {
        Log.d(Constants.TAG, "MainActivity checkForInactivity");
        if (isMusicPlaying) {
            return false;
        } else {
            Log.i(Constants.TAG, "MainActivity checkForInactivity stopped (timeout)");
            if (isRunning)
                stopMediaPlayerService();
            return true;
        }
    }

    private void restartInactivityTimer() {
        Log.d(Constants.TAG, "MainActivity restartInactivityTimer");
        if (handler == null) {
            handler = new Handler();
        } else {
            handler.removeCallbacksAndMessages(null);
        }

        //If there is no music playing for SERVICE_TIMEOUT seconds the service will stop
        final Runnable r = new Runnable() {
            public void run() {
                if (!checkForInactivity()) {
                    handler.postDelayed(this, Constants.SERVICE_TIMEOUT);
                }
            }
        };

        handler.postDelayed(r, Constants.SERVICE_TIMEOUT);
    }

    //This Timer is for playing automatically next Song when current is over
    private void cancelTimer() {
        Log.d(Constants.TAG, "MainActivity cancelTimer");
        if (timer != null) {
            timer.cancel();
            isTimerRunning = false;
        }
    }

    //This Timer is for playing automatically next Song when current is over
    private void newTimer() {
        Log.d(Constants.TAG, "MainActivity newTimer");
        timer = new Timer();
        isTimerRunning = true;
    }
    /*
     * Override and listener methods end
     */


    /*
    *
    * Init methods
    *
    */
    private void startCommand() {
        Log.d(Constants.TAG, "MainActivity startCommand");
        isRunning = true;

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        mContext.registerReceiver(mNoisyReceiver, filter);

        if (Constants.AIRPODS_QUAD_VOL) {
            airPodTimer = new Timer();
        }

        currentPlaymode = widgetSettings.get(Constants.SAVE_LAST_PLAYMODE, Constants.PLAYMODE_DEFAULT);
        updatePlaymodeButton();

        restartInactivityTimer();
        initMediaButtonIntentReceiver();
        initVolume();
        initLastSong();
    }

    private void stopCommand() {
        Log.d(Constants.TAG, "MainActivity stopCommand");
        //stopMediaPlayerService();
        pauseSong();
        //widgetSettings.set(Constants.SAVE_LAST_POSITION, 0);
        try {
            mContext.unregisterReceiver(mNoisyReceiver);
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
        } catch (IllegalArgumentException e) {
            Log.e(Constants.TAG, "MainActivity stopCommand IllegalArgumentException: " + e.toString());
        }
    }

    public void onCompletion(MediaPlayer _mediaPlayer) {
        stopMediaPlayerService();
    }

    private void initMusicFiles() {

        Log.d(Constants.TAG, "MainActivity initMusicFiles");

        directories = getAllDirectories();

        if (musicExists(new File(Constants.PARENT_DIR))) {
            initFolderList();
            if (!isRunning)
                initLastSong();
        } else {
            handleNoMusicExists();
        }
    }

    private void initView() {

        Log.d(Constants.TAG, "MainActivity initView");

        folderList = this.mView.findViewById(R.id.folder_list);
        folderList.setVisibility(View.GONE);


        txtSongName = this.mView.findViewById(R.id.txt_song);
        txtVolume = this.mView.findViewById(R.id.txtVol);

        layoutVolume = this.mView.findViewById(R.id.layoutVolume);
        mainLayout = this.mView.findViewById(R.id.mainLayout);
        layoutVolume.setVisibility(View.GONE);
    }

    private void initButtons() {

        Log.d(Constants.TAG, "MainActivity initButtons");

        folderBtn = this.mView.findViewById(R.id.btn_folder);
        playBtn = this.mView.findViewById(R.id.btn_play);
        nextBtn = this.mView.findViewById(R.id.btn_next);
        prevBtn = this.mView.findViewById(R.id.btn_prev);

        Button setVolumeBtn = this.mView.findViewById(R.id.btn_set_volume);
        playmodeBtn = this.mView.findViewById(R.id.btn_playmode);
        Button volDownBtn = this.mView.findViewById(R.id.btn_vol_down);
        Button volUpBtn = this.mView.findViewById(R.id.btn_vol_up);
        Button closeVolBtn = this.mView.findViewById(R.id.btn_close_vol);


        String lastFolder = widgetSettings.get(Constants.SAVE_LAST_PLAYLIST, "");
        if (!lastFolder.equals("")) {
            File folderFile = new File(Constants.PARENT_DIR+lastFolder);
            if (folderFile.exists()) {
                folderBtn.setText(lastFolder.toUpperCase());
            } else {
                folderBtn.setText(Constants.ALL_SONGS);
            }
        }

        folderBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCloseFolderList();
            }
        });

        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isRunning) {
                    updateSongName();
                    updatePlayButton();
                    playBtnClicked();
                } else {
                    playBtnClicked();
                }
            }
        });

        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isRunning) {
                    nextBtnClicked();
                }
            }
        });

        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isRunning) {
                    prevBtnClicked();
                }
            }
        });


        setVolumeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isRunning) {
                    updateVolumeLabel();
                    mainLayout.setVisibility(View.GONE);
                    layoutVolume.setVisibility(View.VISIBLE);
                }
            }
        });

        playmodeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isRunning) {
                    changePlaymode();
                    changePlaymode(currentPlaymode);
                }
            }
        });


        volDownBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isRunning) {
                    volDownBtnClicked();
                }
            }
        });

        volUpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isRunning) {
                    volUpBtnClicked();
                }
            }
        });

        closeVolBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                layoutVolume.setVisibility(View.GONE);
                mainLayout.setVisibility(View.VISIBLE);
            }
        });
    }

    private void initFolderList() {

        Log.d(Constants.TAG, "MainActivity initFolderList");

        final ArrayList<String> directorieStrings = new ArrayList<>();

        //init directory List
        directorieStrings.add(Constants.ALL_SONGS);
        directorieStrings.addAll(directories);
        directorieStrings.add(Constants.CLOSE_FOLDER_LIST);

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(
                mContext,
                R.layout.list_white_text, R.id.list_text,
                directorieStrings );

        folderList.setAdapter(arrayAdapter);

        folderList.setSelector(R.drawable.folder_list_item);

        folderList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                if (!directorieStrings.get(i).equals(Constants.CLOSE_FOLDER_LIST)) {
                    if (!isRunning) {
                        updateSongName();
                        updatePlayButton();
                    }
                    changePlaylist(directorieStrings.get(i));
                }
                openCloseFolderList();
            }
        });

        folderBtn.setSelected(true);
    }

    private List<String> getAllDirectories() {

        Log.d(Constants.TAG, "MainActivity getAllDirectories");

        File parentDir = new File(Constants.PARENT_DIR);
        ArrayList<String> directories = new ArrayList<>();
        File[] files = parentDir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                if (musicExists(file)) {
                    directories.add(file.getName());
                }
            }
        }

        //sort directories by name
        Collections.sort(directories);

        return directories;
    }

    private boolean musicExists(File parentDir) {
        Log.d(Constants.TAG, "MainActivity musicExists parentDir: " + parentDir.toString());

        File[] files = parentDir.listFiles();
        for (File file : files) {
            if(file.getName().toLowerCase().endsWith(".mp3") || file.getName().toLowerCase().endsWith(".m4a")){
                return true;
            } else if (file.isDirectory()) {
                if (musicExists(file)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleNoMusicExists() {
        Log.d(Constants.TAG, "MainActivity handleNoMusicExists");

        widgetSettings.remove(Constants.SAVE_LAST_SONG_ID);
        widgetSettings.remove(Constants.SAVE_LAST_PLAYLIST);
        widgetSettings.remove(Constants.SAVE_LAST_POSITION);
        playBtn.setEnabled(false);
        nextBtn.setEnabled(false);
        prevBtn.setEnabled(false);
        txtSongName.setText(Constants.NO_MUSIC_FILES_EXISTS);
        folderBtn.setText(Constants.NO_MUSIC);
        folderBtn.setEnabled(false);
        isButtonsDisabled = true;
    }

    private void handleMusicExists() {
        Log.d(Constants.TAG, "MainActivity handleMusicExists");

        playBtn.setEnabled(true);
        nextBtn.setEnabled(true);
        prevBtn.setEnabled(true);
        txtSongName.setText(currentSongName);
        folderBtn.setText(Constants.ALL_SONGS);
        folderBtn.setEnabled(true);
        isButtonsDisabled = false;
    }

    private void initVolume() {
        Log.d(Constants.TAG, "MainActivity initVolume");
        currentVolume = widgetSettings.get(Constants.SAVE_LAST_VOLUME, 3);
        setVolume(currentVolume);
    }


    private void initMediaButtonIntentReceiver() {
        Log.d(Constants.TAG, "MainActivity initMediaButtonIntentReceiver");
        if (mMediaButtonReceiver == null) {
            mMediaButtonReceiver = new MediaButtonIntentReceiver();
            IntentFilter mediaFilter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
            mContext.registerReceiver(mMediaButtonReceiver, mediaFilter);
            mediaButtonsCallback = this;
            mMediaButtonReceiver.registerClient(mediaButtonsCallback);
        }
    }


    private void initLastSong() {
        Log.d(Constants.TAG, "MainActivity initLastSong");
        String lastPlaylist = widgetSettings.get(Constants.SAVE_LAST_PLAYLIST, Constants.PLAYLIST_DEFAULT);
        int lastPlaymode = widgetSettings.get(Constants.SAVE_LAST_PLAYMODE, Constants.PLAYMODE_DEFAULT);
        int lastSong = widgetSettings.get(Constants.SAVE_LAST_SONG_ID, 0);

        //Log.d(Constants.TAG, "MainActivity initLastSong lastPlaylist: " + lastPlaylist + " \\ lastSong: " + lastSong);

        changePlaymode(lastPlaymode);

        //Log.d(Constants.TAG, "MainActivity initLastSong #1");

        handlePlaylistChange(lastPlaylist);

        //Log.d(Constants.TAG, "MainActivity initLastSong #2");

        //Log.d(Constants.TAG, "MainActivity initLastSong playlist.size: " + playlist.size());

        if (playlist.size() > lastSong) {
            currentSongId = lastSong;
        } else {
            currentSongId = 0;
        }

        if (playlist.size() <= currentSongId) {
            currentSongName = Constants.NO_MUSIC_FILES_EXISTS;
            currentSongId = -1;
        } else {
            currentSongName = playlist.get(currentSongId).getName();
        }
        if (isButtonsDisabled)
            handleMusicExists();
        updateSongName();
        updatePlayButton();
        updateVolumeLabel();
    }
    /*
    * Init methods end
    */


    /*
    * MediaPlayer methods
    */
    private void changePlaymode() {
        Log.d(Constants.TAG, "MainActivity changePlaymode()");
        if (currentPlaymode + 1 < Constants.PLAYMODE_LIST.length) {
            currentPlaymode = Constants.PLAYMODE_LIST[currentPlaymode+1];
        } else {
            currentPlaymode = Constants.PLAYMODE_LIST[0];
        }
        updatePlaymodeButton();
        changePlaymode(currentPlaymode);
    }

    /*
    * MediaPlayer methods end
    */


    /*
     *
     * SONG PLAYER HELPER FUNCTIONS
     *
     */
    private void stopMediaPlayerService() {
        Log.d(Constants.TAG, "MainActivity stopMediaPlayerService");
        if (isTimerRunning)
            cancelTimer();
        if (isRunning)
            stopCommand();
        isRunning = false;
        try {
            mContext.unregisterReceiver(mMediaButtonReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(Constants.TAG, "MainActivity stopMediaPlayerService IllegalArgumentException: " + e.toString());
        }
        mMediaButtonReceiver = null;

    }

    //Try to set the playlist - if folder not exist it will play all songs
    private void handlePlaylistChange(String directory) {
        Log.d(Constants.TAG, "MainActivity handlePlayListChange");
        if (directory.toLowerCase().equals("all")) {
            playlist = getAllSongsInDirectory(new File(Constants.PARENT_DIR));
        } else {
            File lastDir = new File(Constants.PARENT_DIR+directory);
            if (lastDir.exists()) {
                playlist = getAllSongsInDirectory(lastDir);
            } else {
                playlist = getAllSongsInDirectory(new File(Constants.PARENT_DIR));
            }
        }
    }


    //Change or restart a playlist
    private void mediaPlayerChangePlaylist(String directory) {
        Log.d(Constants.TAG, "MainActivity mediaPlayerChangePlaylist");
        handlePlaylistChange(directory);
        // Save your string in SharedPref
        widgetSettings.set(Constants.SAVE_LAST_PLAYLIST, directory);

        if (currentPlaymode == Constants.PLAYMODE_RANDOM)
            currentSongId = getNextSong();
        else
            currentSongId = 0;

        playSong(currentSongId, 0);
    }


    private void changePlaymode(int newMode) {
        Log.d(Constants.TAG, "MainActivity changePlayMode newMode: " + newMode);
        playmode = newMode;
        //Log.d(Constants.TAG, "MainActivity changePlayMode #1");
        widgetSettings.set(Constants.SAVE_LAST_PLAYMODE, newMode);
        //Log.d(Constants.TAG, "MainActivity changePlayMode #2");
        if (isMusicPlaying) {
            //Log.d(Constants.TAG, "MainActivity changePlayMode #3");
            checkForNextSong();
        }
        //Log.d(Constants.TAG, "MainActivity changePlayMode #4");
    }

    //This method will start playing the given song
    private void playSong(int songId, int seek) {
        Log.d(Constants.TAG, "MainActivity playSong");
        if (!playlist.isEmpty()) {
            if (playlist.size() <= songId) {
                songId = 0;
            }

            widgetSettings.set(Constants.SAVE_LAST_SONG_ID, songId);

            currentSongName = playlist.get(songId).getName();
            File song = playlist.get(songId);

            if (!isRunning)
                startCommand();

            isMusicPlaying = true;
            try {
                mediaPlayer.reset();
                mediaPlayer.setDataSource(song.getPath());
                mediaPlayer.prepare();
            } catch (IOException e) {
                Log.e(Constants.TAG, "MainActivity playSong IOException: " + e.toString());
            } catch (IllegalStateException e) {
                Log.e(Constants.TAG, "MainActivity playSong IllegalStateException: " + e.toString());
            }

            mediaPlayer.seekTo(seek);
            mediaPlayer.start();
            checkForNextSong();
            if (isActive)
                this.refreshView();

        } else {
            Log.e(Constants.TAG, "MainActivity Folder/Playlist is empty");
        }
    }

    private void pauseSong() {
        Log.d(Constants.TAG, "MainActivity pauseSong");
        if (isMusicPlaying) {
            if (isTimerRunning)
                widgetSettings.set(Constants.SAVE_LAST_POSITION, mediaPlayer.getCurrentPosition());
            else
                widgetSettings.set(Constants.SAVE_LAST_POSITION, 0);
            cancelTimer();
            isMusicPlaying = false;
            mediaPlayer.pause();
            if (isActive)
                this.refreshView();
        }
    }

    private void resumeSong() {
        Log.d(Constants.TAG, "MainActivity resumeSong");
        if (!isMusicPlaying) {
            int lastPos = widgetSettings.get(Constants.SAVE_LAST_POSITION, 0);
            int lastSong = widgetSettings.get(Constants.SAVE_LAST_SONG_ID, 0);
            playSong(lastSong, lastPos);
        }
    }

    private void setVolume(int volume) {
        Log.d(Constants.TAG, "MainActivity setVolume");
        if (volume < 0) {
            volume = 0;
        } else if (volume > Constants.MAX_VOLUME) {
            volume = Constants.MAX_VOLUME;
        }
        currentVolume = volume;
        widgetSettings.set(Constants.SAVE_LAST_VOLUME, currentVolume);
        float log1=(float)(Math.log(Constants.MAX_VOLUME-volume)/Math.log(Constants.MAX_VOLUME));
        mediaPlayer.setVolume(1-log1, 1-log1);
        if (isActive)
            this.refreshView();
    }


    //returns -1 if the playlist is over or unknown playmode
    private int getNextSong() {
        Log.d(Constants.TAG, "MainActivity getNextSong playlist.size: " + playlist.size());
        int nextSong = -1;
        if (playmode == Constants.PLAYMODE_DEFAULT) {
            if (playlist.size() > currentSongId+1) {
                nextSong = currentSongId+1;
            }
        } else if (playmode == Constants.PLAYMODE_REPEAT_ALL) {
            if (playlist.size() > currentSongId+1) {
                nextSong = currentSongId+1;
            } else {
                nextSong = 0;
            }
        } else if (playmode == Constants.PLAYMODE_REPEAT_ONE) {
            nextSong = currentSongId;
        } else if (playmode == Constants.PLAYMODE_RANDOM) {
            for(nextSong = new Random().nextInt(playlist.size());nextSong==currentSongId;){
				nextSong = new Random().nextInt(playlist.size());
			}
        }
        Log.d(Constants.TAG, "MainActivity getNextSong nextSong: " + nextSong);
        return nextSong;
    }

    private int getPrevSong() {
        Log.d(Constants.TAG, "MainActivity getPrevSong");
        int prevSong = 0;
        if (playmode == Constants.PLAYMODE_REPEAT_ONE) {
            prevSong = currentSongId;
        } else if (playmode == Constants.PLAYMODE_RANDOM) {
           for(prevSong = new Random().nextInt(playlist.size());prevSong==currentSongId;){
				prevSong = new Random().nextInt(playlist.size());
			}
        } else {
            if (currentSongId == 0) {
                prevSong = playlist.size()-1;
            }
            if (currentSongId-1 > 0 ) {
                prevSong = currentSongId-1;
            }
        }
        return prevSong;
    }

    //if playmode is default the mediaplayer will stop after the current song
    private void checkForNextSong() {
        Log.d(Constants.TAG, "MainActivity checkForNextSong");
        cancelTimer();
        widgetSettings.set(Constants.SAVE_LAST_POSITION, 0);
        newTimer();
        if (getNextSong() != -1) {
            playNext();
        } else {
            stopPlaylist();
        }
    }

    //play next song if the current one is over
    //Will automatically be called if there is a next song in playlist
    private void playNext() {
        Log.d(Constants.TAG, "MainActivity playNext");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                currentSongId = getNextSong();
                if (currentSongId < 0) {
                    currentSongId = 0;
                }
                playSong(currentSongId, 0);
            }
        },mediaPlayer.getDuration()-mediaPlayer.getCurrentPosition()+Constants.TIME_BETWEEN_SONG);
    }

    // Playlist is finished (in default mode)
    private void stopPlaylist() {
        Log.d(Constants.TAG, "MainActivity stopPlaylist");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                currentSongId = 0;
                currentSongName = playlist.get(currentSongId).getName();
                mediaPlayer.seekTo(0);
                widgetSettings.set(Constants.SAVE_LAST_SONG_ID, currentSongId);
                //widgetSettings.set(Constants.SAVE_LAST_POSITION, 0);
                pauseSong();
            }
        },mediaPlayer.getDuration()-mediaPlayer.getCurrentPosition());
    }

    //Collects all mp3 files in given folder and it's subfolder
    private List<File> getAllSongsInDirectory(File parentDir) {
        ArrayList<File> inFiles = new ArrayList<>();
        File[] files = parentDir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                inFiles.addAll(getAllSongsInDirectory(file));
            } else {
                if(file.getName().toLowerCase().endsWith(".mp3") || file.getName().toLowerCase().endsWith(".m4a")){
                    inFiles.add(file);
                }
            }
        }

        //Sort songs by name
        if (inFiles.size() > 1) {
            Collections.sort(inFiles, new FileNameComparator());
        }
        return inFiles;
    }
    /*
     * SONG PLAYER HELPER FUNCTIONS END
     */



    /*
     *
     * Buttons methods
     *
     */
    private void nextBtnClicked() {
        restartInactivityTimer();
        if (!playlist.isEmpty()) {
            currentSongId = getNextSong();
            if (currentSongId == -1) {
                currentSongId = 0;
                Log.i(Constants.TAG, "MainActivity restart playlist");
            }
            playSong(currentSongId, 0);
        }
    }

    private void prevBtnClicked() {
        restartInactivityTimer();
        if (!playlist.isEmpty()) {
            currentSongId = getPrevSong();
            playSong(currentSongId, 0);
        }
    }

    private void playBtnClicked() {
        restartInactivityTimer();
        if (isMusicPlaying) {
            pauseSong();
        } else {
            resumeSong();
        }
    }

    private void volUpBtnClicked() {
        setVolume(currentVolume+1);
    }

    private void volDownBtnClicked() {
        setVolume(currentVolume-1);
    }


    @Override
    public void headsetButtonClicked(String btn) {
        if (Constants.AIRPODS_QUAD_VOL) {
            if (Arrays.asList(Constants.AIRPODS_LEFT).contains(btn) || Arrays.asList(Constants.AIRPODS_RIGHT).contains(btn)) {
                checkAirPods4Tap(btn);
            } else {
                handleHeadsetButton(btn);
            }
        } else {
            handleHeadsetButton(btn);
        }
    }

    private void handleHeadsetButton(String btn) {
        switch (btn) {
            case Constants.HEADSET_NEXT:
                nextBtnClicked();
                break;
            case Constants.HEADSET_PREV:
                prevBtnClicked();
                break;
            case Constants.HEADSET_PAUSE:
            case Constants.HEADSET_STOP:
                pauseSong();
                break;
            case Constants.HEADSET_PLAY:
                resumeSong();
                break;
            case Constants.HEADSET_VOL_UP:
                volUpBtnClicked();
                break;
            case Constants.HEADSET_VOL_DOWN:
                volDownBtnClicked();
                break;
        }
    }
    /*
     * Buttons methods end
     */


    /*
     * AirPods method
     */
    private void checkAirPods4Tap(String btn) {
        if (btn.equals(lastHeadphonesTouch)) {
            //4x AirPod tap detected
            cancelAirPodTimer();
            if (Arrays.asList(Constants.AIRPODS_LEFT).contains(btn)) {
                volDownBtnClicked();
            } else if (Arrays.asList(Constants.AIRPODS_RIGHT).contains(btn)) {
                volUpBtnClicked();
            }
        } else {
            //It's a fast tap which is not the same as the first one - so the first tap will be ignored
            if (!lastHeadphonesTouch.equals("")) {
                cancelAirPodTimer();
            }
            lastHeadphonesTouch = btn;
            airPodTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    //Time for 4x Tap is over do normal double tap action
                    handleHeadsetButton(lastHeadphonesTouch);
                    cancelAirPodTimer();
                }
            }, Constants.AIRPODS_QUAD_DELAY);
        }
    }
    private void cancelAirPodTimer() {
        airPodTimer.cancel();
        lastHeadphonesTouch = "";
        airPodTimer = new Timer();
    }
    /*
     * AirPods method end
     */



    /*
    *
    * UI Methods
    *
     */
    private void updateSongName() {

        Log.d(Constants.TAG, "MainActivity updateSongName");

        getHost().runTaskOnUI(MainActivity.this, new Runnable() {

                @Override
                public void run() {
                    String songName = currentSongName;
                    txtSongName.setText(songName);
                    txtSongName.setSelected(true);
                }
            });

            String songName = currentSongName;

            //If the songName isn't loaded yet retry in 1 sec
            if (songName.equals("") && isActive) {
                Handler handler = new Handler();

                final Runnable r = new Runnable() {
                    public void run() {
                        updateSongName();
                    }
                };

                handler.postDelayed(r, 1000);
            }
    }

    private void updateVolumeLabel() {
        getHost().runTaskOnUI(MainActivity.this, new Runnable() {

            @SuppressLint("SetTextI18n")
            @Override
            public void run() {

                txtVolume.setText(Integer.toString(currentVolume));

            }
        });

    }

    //will play a playlist from first song
    private void changePlaylist(String newPlaylist) {
        mediaPlayerChangePlaylist(newPlaylist);

        getHost().runTaskOnUI(MainActivity.this, new Runnable() {

            @Override
            public void run() {
                String playlist = widgetSettings.get(Constants.SAVE_LAST_PLAYLIST, "");
                folderBtn.setText(playlist.toUpperCase());

            }
        });

    }

    private void updatePlayButton() {
        getHost().runTaskOnUI(MainActivity.this, new Runnable() {

            @Override
            public void run() {

                if (isMusicPlaying) {
                    playBtn.setBackgroundResource(R.drawable.pause_button);
                } else {
                    playBtn.setBackgroundResource(R.drawable.play_button);
                }

            }
        });
    }

    //Playmode will not be updated from service, so no need for "runOnUIiThread
    private void updatePlaymodeButton() {
        if (currentPlaymode == Constants.PLAYMODE_DEFAULT) {
            playmodeBtn.setBackgroundResource(R.drawable.default_button);
        } else if (currentPlaymode == Constants.PLAYMODE_REPEAT_ALL) {
            playmodeBtn.setBackgroundResource(R.drawable.repeat_all_button);
        } else if (currentPlaymode == Constants.PLAYMODE_REPEAT_ONE) {
            playmodeBtn.setBackgroundResource(R.drawable.repeat_button);
        } else if (currentPlaymode == Constants.PLAYMODE_RANDOM) {
            playmodeBtn.setBackgroundResource(R.drawable.shuffle_button);
        }
    }


    private void openCloseFolderList() {
        if (folderListOpen) {
            folderList.setVisibility(View.GONE);
            folderBtn.setVisibility(View.VISIBLE);
            folderListOpen = false;
        } else {
            folderList.setVisibility(View.VISIBLE);
            folderBtn.setVisibility(View.GONE);
            folderListOpen = true;
        }
    }
    /*
    * UI Methods end
    */


    private void refreshView() {
        Log.d(Constants.TAG, "MainActivity refreshView");
        if (!isRunning)
            initMusicFiles();
        else {
            updateSongName();
            updatePlayButton();
            updateVolumeLabel();
        }
    }


    /*
     * Widget active/deactivate state management
     */

    // On widget show
    private void onShow() {
        Log.d(Constants.TAG, "MainActivity onShow");
        // If view loaded (and was inactive)
        if (this.mView != null && !this.isActive) {
                this.refreshView();
        }

        // Save state
        this.isActive = true;
    }

    // On widget hide
    private void onHide() {
        Log.d(Constants.TAG, "MainActivity onHide");
        // Save state
        this.isActive = false;
    }


    // Events for widget hide
    @Override
    public void onInactive(Bundle paramBundle) {
        super.onInactive(paramBundle);
        this.onHide();
    }
    @Override
    public void onPause() {
        super.onPause();
        this.onHide();
    }
    @Override
    public void onStop() {
        super.onStop();
        this.onHide();
    }

    // Events for widget show
    @Override
    public void onActive(Bundle paramBundle) {
        super.onActive(paramBundle);
        this.onShow();
    }
    @Override
    public void onResume() {
        super.onResume();
        this.onShow();
    }


    /*
     * Below where are unchanged functions that the widget should have
     */

    // Return the icon for this page, used when the page is disabled in the app list. In this case, the launcher icon is used
    @Override
    public Bitmap getWidgetIcon(Context paramContext) {
        return ((BitmapDrawable) this.mContext.getResources().getDrawable(R.mipmap.ic_launcher_round)).getBitmap();
    }


    // Return the launcher intent for this page. This might be used for the launcher as well when the page is disabled?
    @Override
    public Intent getWidgetIntent() {
        //Intent localIntent = new Intent();
        //localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        //localIntent.setAction("android.intent.action.MAIN");
        //localIntent.addCategory("android.intent.category.LAUNCHER");
        //localIntent.setComponent(new ComponentName(this.mContext.getPackageName(), "com.huami.watch.deskclock.countdown.CountdownListActivity"));
        return new Intent();
    }


    // Return the title for this page, used when the page is disabled in the app list. In this case, the app name is used
    @Override
    public String getWidgetTitle(Context paramContext) {
        return this.mContext.getResources().getString(R.string.app_name);
    }


    // Save springboard host
    private ISpringBoardHostStub host = null;

    // Returns the springboard host
    public ISpringBoardHostStub getHost() {
        return this.host;
    }

    // Called when the page is loading and being bound to the host
    @Override
    public void onBindHost(ISpringBoardHostStub paramISpringBoardHostStub) {
        // Log.d(widget.TAG, "onBindHost");
        //Store host
        this.host = paramISpringBoardHostStub;
    }


    // Not sure what this does, can't find it being used anywhere. Best leave it alone
    @Override
    public void onReceiveDataFromProvider(int paramInt, Bundle paramBundle) {
        super.onReceiveDataFromProvider(paramInt, paramBundle);
    }


    // Called when the page is destroyed completely (in app mode). Same as the onDestroy method of an activity
    @Override
    public void onDestroy() {
        stopMediaPlayerService();
        super.onDestroy();
    }

}
